package li.velvetchat.desk

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import java.io.ByteArrayOutputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.math.sqrt

/** what `sendVoice` needs: the file, how long it runs, and the little bar chart */
class EnregistrementVocal(
    val fichier: File,
    val dureeMs: Long,
    val waveform: List<Float>,
)

/**
 * records a voice message as ogg/opus
 *
 * java sound gives you pcm and nothing else, so the codec and the container are ours: concentus
 * for opus, which is pure java and doesn't need anything installed on each pc, and the little
 * ogg writer at the bottom of this file. `LecteurVocal` already walks ogg the other way round
 * for playback
 *
 * mono at 48 khz, opus resamples internally anyway and nobody needs stereo to say "ok j'arrive"
 */
object EnregistreurVocal {

    private const val TAUX = 48_000
    private const val CANAUX = 1

    /** 20 ms per packet, 960 samples, the size everyone uses */
    private const val PAR_TRAME = TAUX / 50

    /** one bar per 100 ms, capped so a long message still fits in a readable chart */
    private const val BARRES_MAX = 100

    @Volatile
    private var ligne: TargetDataLine? = null

    val enCours: Boolean get() = ligne != null

    /**
     * opens the mic and starts recording, false if there's no input line to be had
     *
     * the device is matched by the name in the settings, and it's a prefix match rather than an
     * equality one because that name comes from webrtc's enumeration while the lines here come
     * from java sound, and the two don't spell things the same way. calls pin their device by
     * guid instead (see `AppelBureau.peripheriquesAudio`), java sound has no such thing to offer
     */
    fun demarrer(): Boolean {
        if (ligne != null) return true
        val format = AudioFormat(TAUX.toFloat(), 16, CANAUX, true, false)
        val ouverte = ouvrirLigne(format) ?: return false
        ligne = ouverte
        pcm.reset()
        ouverte.start()
        thread(name = "velvet-capture-vocal", isDaemon = true) {
            val tampon = ByteArray(PAR_TRAME * 2 * CANAUX)
            while (ligne === ouverte) {
                val lus = runCatching { ouverte.read(tampon, 0, tampon.size) }.getOrDefault(-1)
                if (lus <= 0) break
                synchronized(pcm) { pcm.write(tampon, 0, lus) }
            }
        }
        return true
    }

    private fun ouvrirLigne(format: AudioFormat): TargetDataLine? {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val voulu = Voix.entreeNom?.takeIf { it.isNotBlank() }
        if (voulu != null) {
            for (mixeur in AudioSystem.getMixerInfo()) {
                if (!mixeur.name.startsWith(voulu, true) && !voulu.startsWith(mixeur.name, true)) continue
                val ligne = runCatching {
                    AudioSystem.getMixer(mixeur).getLine(info) as TargetDataLine
                }.getOrNull() ?: continue
                if (runCatching { ligne.open(format) }.isSuccess) return ligne
            }
        }
        return runCatching {
            (AudioSystem.getLine(info) as TargetDataLine).also { it.open(format) }
        }.getOrNull()
    }

    /** raw capture, written by the capture thread, read once when we stop */
    private val pcm = ByteArrayOutputStream()

    /** throw the recording away, no file */
    fun annuler() {
        val ouverte = ligne ?: return
        ligne = null
        runCatching { ouverte.stop(); ouverte.close() }
        synchronized(pcm) { pcm.reset() }
    }

    /**
     * stops and writes the file
     *
     * null when there's nothing worth sending — no line, or a click so quick it caught no audio.
     * an empty file would still upload fine and then play silence, which is worse than nothing
     */
    fun arreter(dossier: File): EnregistrementVocal? {
        val ouverte = ligne ?: return null
        ligne = null
        runCatching { ouverte.stop(); ouverte.close() }
        val brut = synchronized(pcm) { pcm.toByteArray().also { pcm.reset() } }
        if (brut.size < PAR_TRAME * 2) return null

        val echantillons = ShortArray(brut.size / 2) { i ->
            ((brut[i * 2].toInt() and 0xFF) or (brut[i * 2 + 1].toInt() shl 8)).toShort()
        }
        val fichier = File(dossier, "vocal-${System.currentTimeMillis()}.ogg").apply {
            parentFile?.mkdirs()
        }
        fichier.writeBytes(encoderOgg(echantillons))
        return EnregistrementVocal(
            fichier = fichier,
            dureeMs = echantillons.size.toLong() * 1000 / TAUX,
            waveform = waveformDe(echantillons),
        )
    }

    /** rms per slice, normalised on the loudest one so a quiet message still draws something */
    private fun waveformDe(echantillons: ShortArray): List<Float> {
        val parBarre = (TAUX / 10).coerceAtLeast(1)
        val barres = (echantillons.size / parBarre).coerceIn(1, BARRES_MAX)
        val pas = echantillons.size / barres
        val brutes = (0 until barres).map { b ->
            var somme = 0.0
            for (i in b * pas until minOf((b + 1) * pas, echantillons.size)) {
                val v = echantillons[i].toDouble()
                somme += v * v
            }
            sqrt(somme / pas.coerceAtLeast(1)).toFloat()
        }
        val fort = brutes.maxOrNull() ?: 0f
        return if (fort <= 0f) brutes.map { 0f } else brutes.map { (it / fort).coerceIn(0f, 1f) }
    }

    /** the two bits the round trip test needs, so it can run without a microphone */
    internal fun encoderPourEssai(echantillons: ShortArray): ByteArray = encoderOgg(echantillons)

    internal fun waveformPourEssai(echantillons: ShortArray): List<Float> = waveformDe(echantillons)

    private fun encoderOgg(echantillons: ShortArray): ByteArray {
        val encodeur = OpusEncoder(TAUX, CANAUX, OpusApplication.OPUS_APPLICATION_VOIP)
        val paquets = mutableListOf<ByteArray>()
        val sortie = ByteArray(4_000)
        var i = 0
        while (i + PAR_TRAME * CANAUX <= echantillons.size) {
            val n = runCatching {
                encodeur.encode(echantillons, i, PAR_TRAME, sortie, 0, sortie.size)
            }.getOrDefault(0)
            if (n > 0) paquets += sortie.copyOf(n)
            i += PAR_TRAME * CANAUX
        }
        return Ogg.ecrire(paquets, PAR_TRAME.toLong())
    }
}

/**
 * ogg, writing side
 *
 * packets go in pages: 27 bytes of header, a table of segment lengths, then the data. a packet
 * is cut into 255 byte segments and anything shorter than 255 ends it, which is how a reader
 * knows where one stops — same rule `LecteurVocal` reads by
 *
 * two things ogg does not forgive:
 *
 *  - the checksum is ogg's own, not zlib's. same polynomial, but no reflection either way and
 *    no final complement, over the whole page with the checksum field zeroed
 *  - the first two packets get a page each, `OpusHead` then `OpusTags`, both at granule zero.
 *    put audio on the header page and decoders just refuse the stream
 *
 * get either wrong and the file opens, plays nothing, and says nothing about why. hence the
 * round trip test
 */
private object Ogg {

    fun ecrire(paquetsAudio: List<ByteArray>, echantillonsParPaquet: Long): ByteArray {
        val flux = (System.nanoTime() and 0x7FFFFFFF).toInt()
        val sortie = ByteArrayOutputStream()
        var page = 0

        sortie.write(page(enteteOpus(), 0L, flux, page++, debut = true))
        sortie.write(page(tagsOpus(), 0L, flux, page++))

        // one page per packet. 20 ms each, so pages stay small and a file cut short still plays
        // up to the cut. grouping would save a few bytes of header and nothing else
        var granule = 0L
        paquetsAudio.forEachIndexed { index, paquet ->
            granule += echantillonsParPaquet
            sortie.write(
                page(paquet, granule, flux, page++, fin = index == paquetsAudio.lastIndex),
            )
        }
        return sortie.toByteArray()
    }

    /** version 1, channel count, pre-skip, original rate, no channel mapping */
    private fun enteteOpus(): ByteArray {
        val b = ByteArrayOutputStream()
        b.write("OpusHead".toByteArray())
        b.write(1)
        b.write(1)
        // pre-skip, what the decoder throws away to make up for the encoder's lookahead. 312 is
        // what opus reports at 48 khz and what every other encoder writes
        ecrireLe16(b, 312)
        ecrireLe32(b, 48_000)
        ecrireLe16(b, 0)
        b.write(0)
        return b.toByteArray()
    }

    private fun tagsOpus(): ByteArray {
        val b = ByteArrayOutputStream()
        val fournisseur = "Velvet".toByteArray()
        b.write("OpusTags".toByteArray())
        ecrireLe32(b, fournisseur.size)
        b.write(fournisseur)
        ecrireLe32(b, 0)
        return b.toByteArray()
    }

    private fun page(
        paquet: ByteArray,
        granule: Long,
        flux: Int,
        numero: Int,
        debut: Boolean = false,
        fin: Boolean = false,
    ): ByteArray {
        val segments = mutableListOf<Int>()
        var reste = paquet.size
        while (reste >= 255) { segments += 255; reste -= 255 }
        segments += reste

        val b = ByteArrayOutputStream()
        b.write("OggS".toByteArray())
        b.write(0)
        b.write((if (debut) 0x02 else 0) or (if (fin) 0x04 else 0))
        ecrireLe64(b, granule)
        ecrireLe32(b, flux)
        ecrireLe32(b, numero)
        ecrireLe32(b, 0) // checksum, filled in once the page is complete
        b.write(segments.size)
        segments.forEach { b.write(it) }
        b.write(paquet)

        val page = b.toByteArray()
        val somme = crc(page)
        page[22] = (somme and 0xFF).toByte()
        page[23] = (somme ushr 8 and 0xFF).toByte()
        page[24] = (somme ushr 16 and 0xFF).toByte()
        page[25] = (somme ushr 24 and 0xFF).toByte()
        return page
    }

    /** polynomial 0x04c11db7, no reflection, no final complement */
    private val TABLE = IntArray(256) { n ->
        var r = n shl 24
        repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04c11db7 else r shl 1 }
        r
    }

    private fun crc(page: ByteArray): Int {
        var r = 0
        for (o in page) {
            r = (r shl 8) xor TABLE[((r ushr 24) xor (o.toInt() and 0xFF)) and 0xFF]
        }
        return r
    }

    private fun ecrireLe16(b: ByteArrayOutputStream, v: Int) {
        b.write(v and 0xFF); b.write(v ushr 8 and 0xFF)
    }

    private fun ecrireLe32(b: ByteArrayOutputStream, v: Int) {
        b.write(v and 0xFF); b.write(v ushr 8 and 0xFF)
        b.write(v ushr 16 and 0xFF); b.write(v ushr 24 and 0xFF)
    }

    private fun ecrireLe64(b: ByteArrayOutputStream, v: Long) {
        for (k in 0 until 8) b.write((v ushr (8 * k)).toInt() and 0xFF)
    }
}

package li.velvetchat.desk

import li.velvetchat.core.tr

import io.github.jaredmdobson.concentus.OpusDecoder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.thread

/**
 * playing voice messages on the desktop
 *
 * a matrix voice message is ogg/opus, the phone's (`MediaRecorder` ogg plus opus) as well as the
 * ones that came from discord. java sound reads neither: the ogg container is unpacked here by
 * hand, forty lines of a format stable since 2003, and the opus decoded by concentus, a pure java
 * decoder, so neither vlc nor ffmpeg to install on every pc in the house
 */
class SonDecode(val pcm: ByteArray, val canaux: Int) {
    val dureeMs get() = pcm.size / (2L * canaux) * 1000 / TAUX
}

private const val TAUX = 48_000

/**
 * an ogg stream's packets in order, from a single logical stream, which is what a voice message is
 */
private fun paquetsOgg(octets: ByteArray): List<ByteArray> {
    val paquets = mutableListOf<ByteArray>()
    val courant = java.io.ByteArrayOutputStream()
    var i = 0
    while (i + 27 <= octets.size) {
        require(octets[i] == 'O'.code.toByte() && octets[i + 1] == 'g'.code.toByte()) { tr("pas un fichier Ogg", "not an Ogg file") }
        val segments = octets[i + 26].toInt() and 0xFF
        var donnees = i + 27 + segments
        for (s in 0 until segments) {
            val taille = octets[i + 27 + s].toInt() and 0xFF
            courant.write(octets, donnees, taille.coerceAtMost(octets.size - donnees))
            donnees += taille
            // a segment under 255 bytes closes the packet, and 255 means there is more to come
            if (taille < 255) { paquets += courant.toByteArray(); courant.reset() }
        }
        i = donnees
    }
    return paquets
}

/** decodes an ogg/opus voice message into 16 bit pcm at 48 kHz. throws when the file is not one */
fun decoderVocal(octets: ByteArray): SonDecode {
    val paquets = paquetsOgg(octets)
    val entete = paquets.firstOrNull()?.takeIf { String(it, 0, 8.coerceAtMost(it.size)) == "OpusHead" }
        ?: error(tr("pas de l'Opus", "not Opus"))
    val canaux = entete[9].toInt().coerceIn(1, 2)
    val preSaut = (entete[10].toInt() and 0xFF) or ((entete[11].toInt() and 0xFF) shl 8)
    val decodeur = OpusDecoder(TAUX, canaux)
    val sortie = java.io.ByteArrayOutputStream()
    val trame = ShortArray(5_760 * canaux)
    for (paquet in paquets.drop(2)) {
        val n = runCatching { decodeur.decode(paquet, 0, paquet.size, trame, 0, 5_760, false) }.getOrDefault(0)
        for (k in 0 until n * canaux) {
            sortie.write(trame[k].toInt() and 0xFF)
            sortie.write(trame[k].toInt() shr 8 and 0xFF)
        }
    }
    val pcm = sortie.toByteArray()
    // the pre skip: the first samples exist to prime the decoder, not to be heard
    val debut = (preSaut * 2 * canaux).coerceAtMost(pcm.size)
    return SonDecode(pcm.copyOfRange(debut, pcm.size), canaux)
}

/**
 * one voice message plays at a time, like on the phone: starting one stops the previous.
 * `onPosition` receives the milliseconds played and `onFin` the stop signal, whether it ended or
 * was interrupted
 */
object LecteurVocal {
    @Volatile private var jeton = 0

    fun jouer(son: SonDecode, depuisMs: Long, onPosition: (Long) -> Unit, onFin: () -> Unit) {
        val moi = ++jeton
        thread(name = "lecteur-vocal", isDaemon = true) {
            val format = AudioFormat(TAUX.toFloat(), 16, son.canaux, true, false)
            val ligne = runCatching { AudioSystem.getSourceDataLine(format).apply { open(format) } }.getOrNull()
            if (ligne == null) { javax.swing.SwingUtilities.invokeLater(onFin); return@thread }
            val pas = 2 * son.canaux
            var i = (depuisMs * TAUX / 1000 * pas).toInt().coerceIn(0, son.pcm.size) / pas * pas
            ligne.start()
            try {
                while (i < son.pcm.size && jeton == moi) {
                    val n = minOf(TAUX / 20 * pas, son.pcm.size - i)
                    ligne.write(son.pcm, i, n)
                    i += n
                    val position = i.toLong() / pas * 1000 / TAUX
                    javax.swing.SwingUtilities.invokeLater { onPosition(position) }
                }
                if (jeton == moi) ligne.drain()
            } finally {
                ligne.stop()
                ligne.close()
                javax.swing.SwingUtilities.invokeLater(onFin)
            }
        }
    }

    fun arreter() { jeton++ }
}

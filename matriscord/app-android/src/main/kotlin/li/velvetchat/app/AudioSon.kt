package li.velvetchat.app

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.app.ui.EchantillonsAudio
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * turning a file somebody picked into samples, and a chosen window back into a file
 *
 * the two halves of the soundboard editor that cannot live in shared code. `MediaCodec` reads
 * whatever this particular phone can read, which is the right answer: the list differs from one
 * device to the next, and hardcoding a set of formats would refuse files that would have played
 *
 * it is a sibling of [Soundboard] rather than a method on it, and that is on purpose.
 * [Soundboard] runs inside a call, on the audio thread, with a budget of ten milliseconds per
 * buffer. this runs while somebody fills in a form. sharing a file would put a decoder that may
 * take two seconds next to a mixer that must never take two milliseconds
 */
object AudioSon {

    /**
     * decodes anything the device can read into 16 bit mono pcm
     *
     * mono by averaging the channels, like the mixer does: the soundboard adds itself to the
     * mic's stream and the capture is mono, so folding here means the waveform drawn is the
     * waveform heard. taking the left channel instead would silently drop whatever sits only on
     * the right
     *
     * no normalisation, unlike the playback path. here we are drawing a picture somebody aims
     * at, and a level lifted to voice would show a plateau where the file has a quiet passage.
     * the levelling happens later, on what gets kept
     *
     * `null` rather than an exception when it cannot be read: picking the wrong file is an
     * ordinary thing to do, and the panel has a sentence for it
     */
    suspend fun decoder(chemin: String): EchantillonsAudio? = withContext(Dispatchers.IO) {
        runCatching { decoderPcm(File(chemin)) }.getOrNull()
    }

    /**
     * writes samples out as a wav, ready to upload
     *
     * wav because we already hold the pcm: the file is a 44 byte header in front of bytes that
     * exist. an opus encoder would divide the size by five and would need a container writer,
     * and a container written by hand is how you ship a file that plays on the phone that made
     * it and nowhere else. six seconds of 48 kHz mono is 576 ko
     *
     * into the cache, like every other outgoing file: it exists between the form and the
     * upload, and nothing needs it afterwards
     */
    suspend fun ecrireWav(pcm: ShortArray, frequence: Int, dossier: File): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val sortie = File(dossier, "son-${System.currentTimeMillis()}.wav")
                sortie.parentFile?.mkdirs()
                sortie.outputStream().buffered().use { flux ->
                    val octets = pcm.size * 2
                    flux.write(enteteWav(octets, frequence))
                    val tampon = ByteBuffer.allocate(octets).order(ByteOrder.LITTLE_ENDIAN)
                    pcm.forEach { tampon.putShort(it) }
                    flux.write(tampon.array())
                }
                sortie.absolutePath
            }.getOrNull()
        }

    /**
     * the canonical 44 byte header
     *
     * written out field by field rather than pulled from a library, because it is forty four
     * bytes and a dependency for forty four bytes is a dependency to keep updated forever. the
     * two sizes are the only thing that can be wrong, and both are computed from one number
     */
    private fun enteteWav(octetsDonnees: Int, frequence: Int): ByteArray {
        val canaux = 1
        val bits = 16
        val debit = frequence * canaux * bits / 8
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + octetsDonnees)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(canaux.toShort())
            putInt(frequence)
            putInt(debit)
            putShort((canaux * bits / 8).toShort())
            putShort(bits.toShort())
            put("data".toByteArray())
            putInt(octetsDonnees)
        }.array()
    }

    private fun decoderPcm(fichier: File): EchantillonsAudio? {
        val extracteur = MediaExtractor()
        extracteur.setDataSource(fichier.absolutePath)
        try {
            val piste = (0 until extracteur.trackCount).firstOrNull { i ->
                extracteur.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            extracteur.selectTrack(piste)
            val format = extracteur.getTrackFormat(piste)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val frequence = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val canaux = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            try {
                // sized for a few seconds up front. the array grows anyway, and starting at the
                // common case saves the handful of copies that a three minute file would cost
                val sortie = ArrayList<Short>(frequence * 4)
                val info = MediaCodec.BufferInfo()
                var entreeFinie = false
                var fini = false

                while (!fini) {
                    if (!entreeFinie) {
                        val index = codec.dequeueInputBuffer(TEMPS_ATTENTE_US)
                        if (index >= 0) {
                            val tampon = codec.getInputBuffer(index)!!
                            val lus = extracteur.readSampleData(tampon, 0)
                            if (lus < 0) {
                                codec.queueInputBuffer(
                                    index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                entreeFinie = true
                            } else {
                                codec.queueInputBuffer(index, 0, lus, extracteur.sampleTime, 0)
                                extracteur.advance()
                            }
                        }
                    }

                    val index = codec.dequeueOutputBuffer(info, TEMPS_ATTENTE_US)
                    if (index >= 0) {
                        val tampon = codec.getOutputBuffer(index)!!
                        val courts = tampon.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        // the channel fold, an average and not a pick. `canaux` can be anything
                        // a container declares, so the loop steps by it rather than assuming two
                        var i = 0
                        while (i + canaux <= courts.limit()) {
                            var somme = 0
                            for (c in 0 until canaux) somme += courts.get(i + c).toInt()
                            sortie.add((somme / canaux).toShort())
                            i += canaux
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) fini = true
                    } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER && entreeFinie) {
                        // the decoder has nothing left to return and the input is closed: that is
                        // the end, even without the flag, some decoders never setting it
                        fini = true
                    }
                }

                if (sortie.isEmpty()) return null
                return EchantillonsAudio(sortie.toShortArray(), frequence)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extracteur.release()
        }
    }

    /**
     * plays the selected window, here, now, out of the phone's speaker
     *
     * an [android.media.AudioTrack] fed the samples directly rather than a `MediaPlayer` on the
     * written file: there is no written file yet, that is the point of listening before saving.
     * `USAGE_MEDIA` and not the call usage, because nobody is in a call while editing a form
     *
     * one at a time, and starting a second stops the first. the alternative is two clips over
     * each other, which is the one thing that makes a preview useless
     */
    fun ecouter(pcm: ShortArray, frequence: Int) {
        arreter()
        runCatching {
            val minimum = android.media.AudioTrack.getMinBufferSize(
                frequence,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
            )
            val piste = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(frequence)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minimum, pcm.size * 2))
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .build()
            piste.write(pcm, 0, pcm.size)
            piste.play()
            lecture = piste
        }
    }

    /** stops whatever is playing. idempotent, and called before every play and on leaving */
    fun arreter() {
        lecture?.let { runCatching { it.stop() }; runCatching { it.release() } }
        lecture = null
    }

    private var lecture: android.media.AudioTrack? = null

    /** ten milliseconds, the usual wait on a codec buffer. long enough not to spin, short enough not to stall */
    private const val TEMPS_ATTENTE_US = 10_000L
}

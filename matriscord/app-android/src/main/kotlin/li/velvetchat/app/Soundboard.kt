package li.velvetchat.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.livekit.android.audio.MixerAudioBufferCallback
import li.velvetchat.core.audio.LectureSon
import li.velvetchat.core.audio.gainDeNormalisation
import li.velvetchat.core.audio.normaliser
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * the soundboard: sending a sound effect into a call in progress
 *
 * ## why the sound goes through the mic and not through a track of its own
 *
 * the livekit android sdk publishes one single local audio track, the `LocalAudioTrack` factory
 * being `internal`, so unreachable. the only entry point is
 * `LocalAudioTrack.setAudioBufferCallback`, which lets you add samples to the mic's stream just
 * before encoding. this is not a workaround, it is the intended mechanism, and it has the advantage
 * of costing nothing on the server side (no extra track to route)
 *
 * ## the three audio processing traps, and what we do about them
 *
 * echo cancellation is not one. it subtracts the signal sent to the speaker from the signal
 * captured by the mic. our sound is injected into the capture and never went through the webrtc
 * output, so it is not in the aec's reference, which cannot cancel it
 *
 * automatic gain control is one. set for speech, it dives on a louder sound and leaves the voice
 * that follows too quiet for a good second. the remedy chosen is the cheapest: normalise the sound
 * to a voice's level at load time, so the agc has nothing to correct. cutting the agc at the moment
 * of the sound would be both more complicated and less reliable, it does not recover instantly, so
 * the timing would become a setting to find
 *
 * hearing yourself is one, and the least obvious. playing the sound on the speaker would put it
 * into the aec's reference, which would then remove it from the injected copy: the others would
 * hear nothing. hence the rule decided by the owner, local monitoring on headphones only. on
 * headphones the sound does not go back through the mic, and it is played by our own [AudioTrack],
 * outside the webrtc playback path. on speaker we play nothing locally: the others hear, you do not
 */
object Soundboard {

    /** 16 bit mono pcm, gain already applied, with its original sample rate */
    private class SonCharge(val echantillons: ShortArray, val frequence: Int)

    private val cache = HashMap<String, SonCharge>()

    /**
     * the playbacks in progress
     *
     * a list and not a single sound: two taps back to back overlap, as on the reference. it is
     * touched by two threads, the interface adding and the audio thread consuming, hence the
     * synchronisation, which costs nothing at this size
     */
    private val enCours = mutableListOf<LectureSon>()

    /**
     * the output buffer, reused from one call to the next
     *
     * `onBufferRequest` is called every ten milliseconds on the audio thread. allocating a
     * `ByteBuffer` each time would give a hundred allocations per second to collect, and the
     * garbage collector is precisely what produces crackles
     */
    private var tampon: ByteBuffer? = null
    private var melange: ShortArray = ShortArray(0)

    /** wired onto the mic track by [VocalManager] on entering the call */
    val melangeur = object : MixerAudioBufferCallback() {
        override fun onBufferRequest(
            originalBuffer: ByteBuffer,
            audioFormat: Int,
            channelCount: Int,
            sampleRate: Int,
            bytesRead: Int,
            captureTimeNs: Long,
        ): BufferResponse? {
            // the common case is "nothing to play", and it has to be free: no allocation, no copy,
            // no lock held
            synchronized(enCours) { if (enCours.isEmpty()) return null }

            // 16 bit signed: that is what webrtc's capture produces. any other format would mean
            // the platform changed under our feet, and we would rather play nothing than write
            // misread bytes into the outgoing stream
            if (audioFormat != AudioFormat.ENCODING_PCM_16BIT) return null

            val echantillons = bytesRead / 2
            if (melange.size != echantillons) melange = ShortArray(echantillons)
            java.util.Arrays.fill(melange, 0)

            synchronized(enCours) {
                enCours.removeAll { !it.melanger(melange, channelCount, sampleRate) }
            }

            val sortie = tampon?.takeIf { it.capacity() >= bytesRead } ?: ByteBuffer
                .allocateDirect(bytesRead)
                .order(ByteOrder.nativeOrder())
                .also { tampon = it }

            sortie.clear()
            sortie.asShortBuffer().put(melange)
            sortie.limit(bytesRead)
            return BufferResponse(sortie, null)
        }
    }

    /**
     * plays a sound: into the call for everybody, and locally if headphones are plugged in
     *
     * @param fichier the sound already downloaded from its `mxc://`, the decoding being done here,
     *   once, then cached by key
     */
    fun jouer(contexte: Context, cle: String, fichier: File) {
        val son = cache[cle] ?: charger(fichier)?.also { cache[cle] = it } ?: return
        synchronized(enCours) {
            // two sounds overlap, ten become mush. the bound stops a repeated tap turning the call
            // into a wall of noise
            if (enCours.size >= 4) return
            enCours += LectureSon(son.echantillons, son.frequence)
        }
        if (auCasque(contexte)) jouerLocalement(son)
    }

    /**
     * are headphones plugged in?
     *
     * the question is not cosmetic: on speaker the sound would be picked up by the mic on top of
     * the injected copy, and the others would hear it twice, offset. on headphones neither of those
     * two things happens
     */
    fun auCasque(contexte: Context): Boolean {
        val audio = contexte.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_HEARING_AID,
            )
        }
    }

    /**
     * the local monitoring, through our own [AudioTrack]
     *
     * outside the webrtc playback path, deliberately: what goes through webrtc playback enters the
     * echo cancellation's reference, which would then remove the sound from the copy injected into
     * the capture. the others would hear nothing, the opposite of what we want
     *
     * `USAGE_VOICE_COMMUNICATION` so the system routes it to the same device as the call, and not
     * to the media speaker while you are talking into a headset
     */
    private fun jouerLocalement(son: SonCharge) {
        runCatching {
            val octets = ShortArray(son.echantillons.size)
            son.echantillons.copyInto(octets)
            val piste = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(son.frequence)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(octets.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            piste.write(octets, 0, octets.size)
            piste.setNotificationMarkerPosition(octets.size)
            piste.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack?) {
                        runCatching { track?.release() }
                    }

                    override fun onPeriodicNotification(track: AudioTrack?) = Unit
                }
            )
            piste.play()
        }
    }

    /** empties the cache and stops everything: called on leaving the call */
    fun nettoyer() {
        synchronized(enCours) { enCours.clear() }
    }

    /**
     * decodes an audio file into 16 bit mono pcm, then normalises it
     *
     * mono because a sound effect has no stereo to preserve and will be spread over the capture's
     * channels anyway. the stereo to mono fold is an average, not taking the left channel: taking
     * one channel would lose whatever is only on the other
     *
     * `MediaExtractor` plus `MediaCodec` rather than `MediaPlayer`: we want the samples, not a
     * playback. it is android's only path that returns them
     */
    private fun charger(fichier: File): SonCharge? = runCatching {
        val extracteur = MediaExtractor()
        extracteur.setDataSource(fichier.absolutePath)
        val piste = (0 until extracteur.trackCount).firstOrNull { i ->
            extracteur.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: return@runCatching null
        extracteur.selectTrack(piste)
        val format = extracteur.getTrackFormat(piste)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return@runCatching null
        val frequence = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val canaux = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val sortie = ArrayList<Short>(frequence * 2)
        val info = MediaCodec.BufferInfo()
        var fini = false
        var entreeFinie = false

        while (!fini) {
            if (!entreeFinie) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val tampon = codec.getInputBuffer(index)!!
                    val lus = extracteur.readSampleData(tampon, 0)
                    if (lus < 0) {
                        codec.queueInputBuffer(
                            index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        entreeFinie = true
                    } else {
                        codec.queueInputBuffer(index, 0, lus, extracteur.sampleTime, 0)
                        extracteur.advance()
                    }
                }
            }
            val index = codec.dequeueOutputBuffer(info, 10_000)
            if (index >= 0) {
                val tampon = codec.getOutputBuffer(index)!!
                val courts = tampon.order(ByteOrder.nativeOrder()).asShortBuffer()
                val brut = ShortArray(courts.remaining())
                courts.get(brut)
                if (canaux <= 1) {
                    brut.forEach { sortie += it }
                } else {
                    var i = 0
                    while (i + canaux <= brut.size) {
                        var somme = 0
                        for (c in 0 until canaux) somme += brut[i + c]
                        sortie += (somme / canaux).toShort()
                        i += canaux
                    }
                }
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) fini = true
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER && entreeFinie) {
                // the decoder has nothing left to return and the input is closed: that is the end,
                // even without the flag, some decoders never setting it
                if (sortie.isNotEmpty()) fini = true
            }
        }
        codec.stop()
        codec.release()
        extracteur.release()

        if (sortie.isEmpty()) return@runCatching null
        val echantillons = ShortArray(sortie.size) { sortie[it] }
        val gain = gainDeNormalisation(echantillons)
        if (gain == 0f) return@runCatching null
        SonCharge(normaliser(echantillons, gain), frequence)
    }.getOrNull()
}

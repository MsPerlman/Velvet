package li.velvetchat.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer

/**
 * an incoming call's ringtone
 *
 * ## the sound file is not referenced at compile time, and deliberately
 *
 * the track is resolved by its name, at runtime (`res/raw/sonnerie`), and not by `R.raw.sonnerie`.
 * two intended consequences:
 *
 *     - the app compiles without the file, so the ringtone is content and not code, and can be changed,
 *       removed or forgotten without touching a line
 *     - its absence is not a breakage: the incoming call shows exactly the same, it is simply silent. a
 *       silent banner beats a screen refusing to open because an mp3 is missing
 *
 * ## silent mode is respected
 *
 * we do not ring when the phone is on silent or vibrate. this is not politeness: it is the most
 * explicit setting a person can put on their device, and a personal app that overrides it ends up
 * uninstalled
 *
 * the stream is `STREAM_RING`: the ringtone therefore follows the ring volume, not the media one,
 * and somebody who turned their music down must not miss their calls over it
 */
object Sonnerie {

    private var lecteur: MediaPlayer? = null

    /** true while the ringtone is playing, useful to the tests and to the screen */
    val enCours: Boolean get() = lecteur != null

    /**
     * starts the ringtone, looping. no effect when it is already ringing, when the phone is on
     * silent, or when no file is supplied
     */
    fun demarrer(context: Context) {
        if (lecteur != null) return

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        val id = context.resources.getIdentifier("sonnerie", "raw", context.packageName)
        if (id == 0) return

        lecteur = runCatching {
            MediaPlayer.create(context, id)?.apply {
                isLooping = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                start()
            }
        }.getOrNull()
    }

    /**
     * stops and releases the ringtone. idempotent: called on every disappearance of the banner,
     * including when nothing was ringing
     *
     * releasing and not simply pausing: a kept `MediaPlayer` holds an audio decoder, and a missed
     * call must leave nothing running behind it
     */
    fun arreter() {
        val l = lecteur ?: return
        lecteur = null
        runCatching { l.stop() }
        runCatching { l.release() }
    }
}

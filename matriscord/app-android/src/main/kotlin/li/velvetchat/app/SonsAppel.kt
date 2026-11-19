package li.velvetchat.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * the short sounds a call makes when you press its buttons
 *
 * why a call needs them at all: every one of these buttons does something whose result you cannot
 * see. you press mute and nothing on the screen proves the others stopped hearing you. the sound
 * is the receipt, and on a phone, held at arm's length, it is often the only one you get
 *
 * `SoundPool` and not `MediaPlayer`, which is what [Sonnerie] uses: a ringtone is one long track
 * that loops, these are seven short ones fired at the moment of a tap. `SoundPool` keeps them
 * decoded in memory and plays with no startup delay, where a `MediaPlayer` per effect would
 * allocate a decoder for half a second of sound and answer late enough to feel disconnected from
 * the finger
 *
 * the files are resolved by name at runtime, like the ringtone, for the same reason: the app
 * compiles and runs without them, and a missing effect is a silence rather than a crash
 *
 * `USAGE_ASSISTANCE_SONIFICATION` on the media stream, deliberately not the ring stream: these are
 * interface feedback, and somebody who turned their media down wants them down too. the ringtone
 * is the opposite case and stays where it is
 */
object SonsAppel {

    private var pool: SoundPool? = null

    /** resource name to the id `SoundPool` gave us */
    private val charges = mutableMapOf<String, Int>()

    private val NOMS = listOf(
        "vocal_entree",
        "vocal_sortie",
        "micro_coupe",
        "micro_ouvert",
        "son_coupe",
        "son_ouvert",
        "partage",
    )

    /**
     * loads the lot, once
     *
     * called on joining a call rather than at startup: an app that never makes a call should not
     * be carrying seven decoded sounds around
     */
    @Synchronized
    fun preparer(context: Context) {
        if (pool != null) return
        val p = runCatching {
            SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
        }.getOrNull() ?: return
        NOMS.forEach { nom ->
            val res = context.resources.getIdentifier(nom, "raw", context.packageName)
            if (res != 0) runCatching { charges[nom] = p.load(context, res, 1) }
        }
        pool = p
    }

    private fun jouer(nom: String) {
        val p = pool ?: return
        val id = charges[nom] ?: return
        // a sound that has not finished loading yet plays nothing and says so with a 0. we do not
        // wait for it: the tap has already happened, and a receipt that arrives late is worse
        // than none
        runCatching { p.play(id, 1f, 1f, 1, 0, 1f) }
    }

    fun vocalEntree() = jouer("vocal_entree")
    fun vocalSortie() = jouer("vocal_sortie")
    fun microCoupe() = jouer("micro_coupe")
    fun microOuvert() = jouer("micro_ouvert")
    fun sonCoupe() = jouer("son_coupe")
    fun sonOuvert() = jouer("son_ouvert")
    fun partage() = jouer("partage")

    /**
     * frees the decoders
     *
     * deliberately not called when a call ends, and that is not an oversight: releasing a
     * `SoundPool` stops whatever it is playing, and the last thing it plays is the sound that
     * says you left. it cut itself off. seven short effects are about seventy kilobytes, which
     * is not worth a bug
     */
    @Synchronized
    fun liberer() {
        runCatching { pool?.release() }
        pool = null
        charges.clear()
    }
}

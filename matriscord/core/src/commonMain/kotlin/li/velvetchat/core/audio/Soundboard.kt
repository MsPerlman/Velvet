package li.velvetchat.core.audio

import kotlin.math.abs
import kotlin.math.min

/**
 * the soundboard maths, kept away from the platform because none of it is android
 *
 * what a soundboard technically does: it adds samples to the mic stream before encoding. the
 * livekit sdk publishes exactly one local audio track and its factory is `internal`, so there is
 * no second track to be had and the mixing happens here
 *
 * three reasons to pull it out of the app module. it tests without a device, whereas a mixing
 * bug only makes itself heard in a two phone call. the desktop client needs exactly the same
 * thing. and the output sample rate is not known in advance, webrtc picks it per device, so the
 * resampling is a real rule rather than an integration detail
 */

/** -18 dbfs as a linear amplitude: `10 ^ (-18 / 20)` */
const val CRETE_PAROLE = 0.126f

/**
 * the gain needed to bring a sound up to the level of a voice
 *
 * the cheapest cure for the soundboard's real problem, which is not echo cancellation but
 * automatic gain control. webrtc's agc is tuned for speech, so a noticeably louder sound sends
 * it diving and the voice that follows comes out too quiet for a good second. a sound already at
 * speech level gives it nothing to correct, so we never have to turn the agc off or time its
 * recovery
 *
 * capped, and the cap is the part that matters: without it a near silent recording would get
 * multiplied by a hundred and we would bring its hiss up with it. past that the sound is too
 * quiet to rescue and it is better that it stays quiet
 *
 * returns 0 for an empty or completely silent sound, and then the caller has nothing to play
 */
fun gainDeNormalisation(
    echantillons: ShortArray,
    creteCible: Float = CRETE_PAROLE,
    gainMax: Float = 8f,
): Float {
    var crete = 0
    for (e in echantillons) {
        val v = abs(e.toInt())
        if (v > crete) crete = v
    }
    if (crete == 0) return 0f
    val creteRelative = crete / 32768f
    return min(creteCible / creteRelative, gainMax)
}

/**
 * applies the gain once, at load time
 *
 * at load and not per buffer: the mixing runs on the audio thread, called every ten
 * milliseconds, and a multiply per sample there is work redone every time for a result that
 * never changes
 */
fun normaliser(echantillons: ShortArray, gain: Float): ShortArray =
    if (gain == 1f) echantillons
    else ShortArray(echantillons.size) { i ->
        (echantillons[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
    }

/**
 * a sound currently playing, which knows how to add itself to an outgoing buffer
 *
 * mono in. a soundboard has no stereo worth preserving, and a mono sound spreads across however
 * many channels the capture wants without losing anything
 *
 * @param echantillons 16 bit mono pcm, gain already applied, see [normaliser]
 * @param frequence the sound's sample rate, as the decoder gave it
 */
class LectureSon(
    private val echantillons: ShortArray,
    private val frequence: Int,
) {
    /**
     * the playback position, in source frames, as a float
     *
     * a float because the file's rate and the capture's rate almost never line up: a 44 100 hz
     * sound mixed into a 48 000 hz capture advances 0.919 frames per output frame. rounding on
     * every buffer would accumulate audible drift over a few seconds, and worse, it would make
     * the pitch wander
     */
    private var position = 0.0

    val fini: Boolean get() = position >= echantillons.size

    /**
     * adds what is left of the sound into [sortie], interleaved across [canaux]
     *
     * adds rather than overwrites: two sounds fired one after the other have to stack, like
     * discord. the caller is what zeroes the buffer before the first one
     *
     * the interpolation is linear. for speech that would be sloppy, for a two second noise the
     * difference against proper resampling is inaudible, and a polyphase filter on the audio
     * thread would cost far more than it returns
     *
     * @return true while there is still sound to play
     */
    fun melanger(sortie: ShortArray, canaux: Int, frequenceSortie: Int): Boolean {
        if (fini || canaux <= 0 || frequenceSortie <= 0) return false
        val pas = frequence.toDouble() / frequenceSortie
        val images = sortie.size / canaux

        for (image in 0 until images) {
            if (position >= echantillons.size) break
            val i = position.toInt()
            val frac = position - i
            val a = echantillons[i].toInt()
            val b = if (i + 1 < echantillons.size) echantillons[i + 1].toInt() else 0
            val valeur = (a + (b - a) * frac).toInt()

            for (canal in 0 until canaux) {
                val index = image * canaux + canal
                // clamp rather than wrap. an overflow that wraps round produces a crack, where
                // clipping only flattens the top of the wave
                sortie[index] = (sortie[index] + valeur).coerceIn(-32768, 32767).toShort()
            }
            position += pas
        }
        return !fini
    }
}

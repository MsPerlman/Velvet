package li.velvetchat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * decoding an image, and drawing it — the one seam of the seven that closes neither with a
 * parameter nor with a change of representation, because the two platforms produce different things
 *
 * android gives a `Drawable`, which carries its own playback state and animates itself.
 * `ImageDecoder` covers gif and animated webp, apng goes through a third party library since the
 * platform will not animate it, measured on a device rather than read in documentation. the desktop
 * has no `Drawable`: skia returns a `Codec` whose frames you read one at a time
 *
 * hence an `expect class` with no visible member, which stops shared code assuming a shape. exposing
 * "a list of frames" would make android decode a whole gif up front when its `Drawable` does it
 * better as it goes
 *
 * animated decodes are deliberately not cached: playback state is part of them, so two messages
 * sharing one would be locked to the same frame and stopping one would stop the other. one decode
 * per occurrence. still images go through [cacheMedia]
 */
expect class ImageDecodee

/** true when the decode produced something that moves */
expect val ImageDecodee.animee: Boolean

/** the original dimensions in pixels. 0 when the platform does not know them */
expect val ImageDecodee.largeur: Int
expect val ImageDecodee.hauteur: Int

/**
 * the still image of a decode that animates nothing, null when it does. exposed so the caller can
 * cache it — a still image is shared between displays, an animation is not ([ImageDecodee]) — and
 * without it the bitmap would mean decoding the same bytes twice through [decoderMedia]
 */
expect val ImageDecodee.bitmapFixe: ImageBitmap?

/**
 * decodes bytes, animation included when the format carries one
 *
 * call it off the main thread: a megabyte of apng does not decode for free
 *
 * @return null when no decoder recognises the format, in which case the caller shows what it can,
 *   never a silent blank
 */
expect suspend fun decoderPourAffichage(octets: ByteArray): ImageDecodee?

/**
 * draws a decode, animated or not, honouring [contentScale] — which android accepted and then
 * ignored for a while, setting the bounds over the whole surface, so it was `FillBounds` whatever
 * you asked. invisible on a square image in a square cell, very visible once a 450 by 880 profile
 * effect came out as a wide strip. the still path obeyed, the animated one did not, same function
 */
@Composable
expect fun DessinDecode(
    image: ImageDecodee,
    contentScale: ContentScale,
    modifier: Modifier,
    /** false freezes the decode on its current frame, see [PlafondAnimations] */
    anime: Boolean,
    /**
     * the rest between two runs, 0 for none. a profile effect lasts a second and a half and
     * replayed immediately it flickers, which is tiresome beside text somebody is reading — the
     * reference spaces it out. an avatar decoration does not rest: it is small and part of the face
     */
    pauseEntreBouclesMs: Long = 0,
)

/**
 * a still image, subsampled to the width it will be drawn at. separate from [decoderPourAffichage]
 * because subsampling divides memory by the square of the factor, and a 3000 pixel screenshot shown
 * at 400 has no business taking nine megabytes
 *
 * an animated format gives up its first frame only here. a caller that wants the animation goes
 * through [decoderPourAffichage]
 */
expect fun decoderMedia(octets: ByteArray, largeurCible: Int): ImageBitmap?

/**
 * the most pixels either platform holds decoded at once. sixteen megapixels is sixty four megabytes
 * at four bytes each, already far past anything drawn here — the widest target is 1440, so a 16:9
 * image at twice that is eight. the ceiling exists so the worst case has a number
 */
internal const val SURFACE_MAX = 16_000_000L

/**
 * refuses outright what no camera produced
 *
 * the bytes reaching [decoderMedia] are not ours: `mediaBytes` carries whatever anyone posted in a
 * room, link previews whatever a remote site served. a png declaring 60000 by 60000 compresses to a
 * few kilobytes of one flat colour and asks for fourteen gigabytes decoded — a decompression bomb,
 * and sending one is a message like any other
 *
 * dimensions alone were not enough. android divided by a factor taken **from the width**, so 100
 * wide by a hundred million tall kept a factor of one and decoded whole. the desktop had no bound:
 * skia decodes everything and we redraw smaller, which its own comment calls acceptable on a
 * desktop machine — true of a photograph, not of a file built to hurt. and `Long` because
 * `60000 * 60000` overflows `Int` **negative**, so the obvious test passes what it exists to stop
 *
 * the ceiling is a hundred and fifty megapixels. the first try was a hundred, which sounded
 * generous and refused a holiday photograph: a 108 megapixel phone shoots twelve thousand by nine
 * thousand, a hundred and eight million. the test caught it
 *
 * below it nothing is refused, only scaled down, and [SURFACE_MAX] is what bounds the memory. the
 * honest limit: skia decodes the whole image before we shrink it, so the desktop peak is the
 * source's own size, six hundred megabytes at the ceiling. android pays none of that, `inSampleSize`
 * decodes straight at the reduced size
 */
internal fun surfaceRefusee(largeur: Int, hauteur: Int): Boolean =
    largeur <= 0 || hauteur <= 0 || largeur.toLong() * hauteur.toLong() > 150_000_000L

// ------------------------------------------------------------- the animation clock

/**
 * a clock ticking every frame, read inside animated draws to create the dependency compose is
 * missing: a decode that animates itself changes its content without compose knowing, and the
 * result is a frozen first frame
 *
 * shared although what it drives is not — a `Drawable` on one side, a frame index on the other,
 * both needing the same heartbeat, and a clock per platform would be two to keep in agreement
 */
internal val horlogeAnimation = mutableLongStateOf(0L)

/**
 * advances the clock, set once at the root and not per image: a loop per occurrence would wake the
 * main thread once per visible animation for work that is identical
 */
@Composable
fun PiloteAnimations() {
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { horlogeAnimation.longValue = it }
        }
    }
}

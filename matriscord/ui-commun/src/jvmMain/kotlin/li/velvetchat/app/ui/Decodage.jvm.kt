package li.velvetchat.app.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * the desktop's decode: a `Codec` whose frames you read one at a time
 *
 * nothing here animates itself. skia returns a decoder, and knowing which frame to show when is
 * [DessinDecode]'s job, which is the fundamental difference from android, where the `Drawable`
 * handles it and the ui only redraws it
 *
 * no dependency added. skia comes with compose multiplatform, and the first batch actually used it
 * rather than assuming: `Image.makeFromEncoded` for stills, `Codec.makeFromData` plus `readPixels`
 * for animation. the probe measured both
 */
actual class ImageDecodee internal constructor(
    internal val codec: Codec?,
    /**
     * the already decoded frames, filled in as they are needed
     *
     * the first batch measured that this cache is not a luxury. the probe's adversarial line
     * decoded a skia frame on every screen frame, one bitmap allocated and one image built, sixty
     * times a second per visible gif. that is the only case where it saw frames drop
     *
     * ponytail: the cache keeps every frame of a gif, so its ceiling is the gif itself (100 frames
     * at 500 by 500 is about 100 mb). bound it by a pixel budget, like [CacheImages], the day a
     * real media gets near it
     */
    internal val trames: Array<ImageBitmap?>?,
    /** each frame's duration in milliseconds, as the file declares it */
    internal val durees: IntArray?,
    internal val bitmap: ImageBitmap?,
    private val l: Int,
    private val h: Int,
) {
    internal val largeurBrute: Int get() = l
    internal val hauteurBrute: Int get() = h
}

// `trames` and not `codec`: since apng is decoded separately, an animation can arrive with no skia
// codec, its frames already all built. it is the presence of the frames that says it moves, not how
// we got them
actual val ImageDecodee.animee: Boolean get() = trames != null

actual val ImageDecodee.bitmapFixe: ImageBitmap? get() = bitmap

actual val ImageDecodee.largeur: Int get() = largeurBrute

actual val ImageDecodee.hauteur: Int get() = hauteurBrute

actual suspend fun decoderPourAffichage(octets: ByteArray): ImageDecodee? = runCatching {
    // apng first, because skia would say there is only one frame. measured: `frameCount` answers 1
    // on a 288 by 288 decoration whose `acTL` chunk is right there. without this detour every
    // avatar decoration would be frozen on the desktop, see `Apng.jvm.kt`
    if (estApngJvm(octets)) {
        decoderApng(octets)?.let { a ->
            return@runCatching ImageDecodee(
                codec = null,
                trames = Array(a.trames.size) { a.trames[it] },
                durees = a.durees,
                bitmap = null,
                l = a.largeur,
                h = a.hauteur,
            )
        }
    }
    val codec = Codec.makeFromData(Data.makeFromBytes(octets))
    // same bound as `decoderMedia`: the codec reads the header without decoding anything, so
    // the dimensions are known before the first frame. see `surfaceRefusee`
    if (surfaceRefusee(codec.width, codec.height)) return null
    val info = codec.imageInfo
    if (codec.frameCount > 1) {
        // the declared duration can be missing or zero, and every other gif writes 0 to mean "as
        // fast as possible", which would give a division by zero and a mad scroll. 100 ms is what
        // browsers have always substituted
        val durees = IntArray(codec.frameCount) { i ->
            runCatching { codec.framesInfo[i].duration }.getOrDefault(0)
                .let { if (it <= 0) 100 else it }
        }
        ImageDecodee(codec, arrayOfNulls(codec.frameCount), durees, null, info.width, info.height)
    } else {
        // the codec can decode a still image, but `Image.makeFromEncoded` does it in one line and
        // returns exactly what compose draws
        val fixe = org.jetbrains.skia.Image.makeFromEncoded(octets).toComposeImageBitmap()
        ImageDecodee(null, null, null, fixe, fixe.width, fixe.height)
    }
}.getOrNull()

@Composable
actual fun DessinDecode(
    image: ImageDecodee,
    contentScale: ContentScale,
    modifier: Modifier,
    anime: Boolean,
    pauseEntreBouclesMs: Long,
) {
    if (image.trames == null) {
        image.bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = contentScale,
                modifier = modifier,
                // high quality: a 448 px plate scaled to 140 came out blurry under the default
                // filtering, while the published file is sharp
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            )
        }
        return
    }

    val durees = image.durees!!
    // the pause is part of the cycle: past the last frame nothing is drawn for the length of the rest.
    // same mechanism as the rest, an index derived from elapsed time, rather than a separate timer
    // that would drift out of step with the drawing
    val jeu = durees.sum().coerceAtLeast(1)
    val cycle = jeu + pauseEntreBouclesMs.toInt().coerceAtLeast(0)
    // this occurrence's origin, and not an absolute instant: two gifs appearing two seconds apart
    // each have to start at their own first frame, not in the middle
    val origine = remember(image) { horlogeAnimation.longValue }
    val gele = remember(image) { mutableIntStateOf(0) }

    val index = if (anime) {
        val ecoule = ((horlogeAnimation.longValue - origine) % cycle).toInt()
        // the rest shows nothing. holding the last frame through it left a ghost of the effect on
        // the card for the whole pause, where the reference takes the layer away until the next run
        if (pauseEntreBouclesMs > 0 && ecoule >= jeu) return
        var reste = ecoule.coerceAtMost(jeu - 1)
        var i = 0
        while (i < durees.size - 1 && reste >= durees[i]) {
            reste -= durees[i]
            i++
        }
        // remembered for the freeze: without it, a gif losing its animation slot
        // (`PlafondAnimations`) would snap back to its first frame, a visible jump, when the cap
        // only asks for stillness
        SideEffect { gele.intValue = i }
        i
    } else {
        gele.intValue
    }

    val trame = remember(image, index) { image.trame(index) }
    Image(
        bitmap = trame,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.clipToBounds(),
        // same reason as for the still image: plates and decorations get scaled down, and the
        // default filtering made them mushy
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
    )
}

/**
 * frame [i], decoded on demand and kept
 *
 * skia decodes the frames this one depends on itself, a gif often writing only the pixels that
 * change, so nothing forces us to walk them in order. the cache means a second pass decodes nothing
 */
private fun ImageDecodee.trame(i: Int): ImageBitmap {
    val cache = trames!!
    cache[i]?.let { return it }
    // an apng decode arrives with all its frames already built: it has no codec, so there is
    // nothing to decode here
    val info = (codec ?: return cache.first { it != null }!!).imageInfo
    val bitmap = Bitmap().apply { allocPixels(info) }
    codec!!.readPixels(bitmap, i)
    bitmap.setImmutable()
    val image = org.jetbrains.skia.Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    cache[i] = image
    return image
}

/**
 * decodes a still image, bringing it down to the width it is drawn at
 *
 * skia has no equivalent of `inSampleSize`: it decodes, then we redraw smaller. so the peak memory
 * is the whole image's, acceptable on a desktop machine, where on a phone it is exactly what had to
 * be avoided
 *
 * the factor aims at twice the target width, like on android and for the same reason: staying sharp
 * on a dense screen without doubling the memory
 */
actual fun decoderMedia(octets: ByteArray, largeurCible: Int): ImageBitmap? = runCatching {
    val image = org.jetbrains.skia.Image.makeFromEncoded(octets)
    // past the ceiling nothing is decoded at all, see `surfaceRefusee`
    if (surfaceRefusee(image.width, image.height)) return@runCatching null

    val maximum = largeurCible.coerceAtLeast(1) * 2

    // two constraints, and the stricter one wins
    //
    // the width gives the sharpness we want. the surface bounds the memory, and it is needed
    // separately: a narrow, very tall image passes under the width maximum while weighing as
    // much as a bomb. square root because one factor acts on both sides at once
    val surface = image.width.toLong() * image.height
    val parLargeur = if (image.width > maximum) maximum.toFloat() / image.width else 1f
    val parSurface =
        if (surface > SURFACE_MAX) kotlin.math.sqrt(SURFACE_MAX.toDouble() / surface).toFloat() else 1f
    val facteur = minOf(parLargeur, parSurface)

    if (facteur >= 1f) {
        image.toComposeImageBitmap()
    } else {
        val l = (image.width * facteur).toInt().coerceAtLeast(1)
        val h = (image.height * facteur).toInt().coerceAtLeast(1)
        val cible = Surface.makeRasterN32Premul(l, h)
        cible.canvas.drawImageRect(image, Rect.makeWH(l.toFloat(), h.toFloat()))
        cible.makeImageSnapshot().toComposeImageBitmap()
    }
}.getOrNull()

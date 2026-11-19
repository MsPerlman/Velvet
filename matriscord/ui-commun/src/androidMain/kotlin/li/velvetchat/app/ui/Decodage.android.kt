package li.velvetchat.app.ui

import android.graphics.BitmapFactory
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale

/**
 * android's decode: a `Drawable`, which carries its own playback state
 *
 * two fields, only one filled at a time. `dessin` when it moves, since it animates itself once
 * started, and that is exactly why it does not get cached. `bitmap` otherwise, and the caller is
 * free to keep it
 */
actual class ImageDecodee internal constructor(
    internal val dessin: Drawable?,
    internal val bitmap: ImageBitmap?,
)

actual val ImageDecodee.animee: Boolean get() = dessin != null

actual val ImageDecodee.bitmapFixe: ImageBitmap? get() = bitmap

actual val ImageDecodee.largeur: Int
    get() = dessin?.intrinsicWidth?.coerceAtLeast(0) ?: bitmap?.width ?: 0

actual val ImageDecodee.hauteur: Int
    get() = dessin?.intrinsicHeight?.coerceAtLeast(0) ?: bitmap?.height ?: 0

/**
 * decodes through `ImageDecoder`, which knows gif and animated webp, and not apng
 *
 * the path comes from `EmojiEnLigne`, where it built up one bug at a time. three things were
 * learned on screen rather than from documentation, and none of them should get lost in the move
 *
 * `ImageDecoder` does not animate apng, and does not fail either: it returns the first frame and
 * stops there. for a decoration that is a frozen ring, and for a profile effect, whose first frame
 * is empty, it is a transparent image, so "the effect is not visible". one missing decoder, two
 * reported bugs. `FormatsAnimesTest` keeps the measurement: the day the platform learns apng it
 * will fail and this branch can go
 *
 * we take the `BitmapDrawable`'s bitmap, we do not copy it. `ImageDecoder` allocates a hardware
 * bitmap by default, which a software canvas cannot read, so copying gave an image of the right
 * size and entirely empty, without throwing. compose, on the other hand, knows how to draw a
 * hardware bitmap
 *
 * `BitmapFactory` is the fallback and not the main path: it only decodes the first frame of an
 * animated format
 */
actual suspend fun decoderPourAffichage(octets: ByteArray): ImageDecodee? {
    // the same bound as `decoderMedia`, and it took a second pass to get here
    //
    // `surfaceRefusee` had only been put on `decoderMedia`, which serves photographs. this
    // path serves decorations, effects and stickers — which arrive from a room, so from
    // elsewhere — and it had no bound at all: `ImageDecoder.decodeDrawable` allocates the full
    // size, and the fallback below called `decodeByteArray` with no options whatsoever. a bomb
    // walked through here while the door next to it was shut
    //
    // the header is enough to decide, and `inJustDecodeBounds` decodes nothing
    val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
    if (surfaceRefusee(bornes.outWidth, bornes.outHeight)) return null

    if (android.os.Build.VERSION.SDK_INT >= 28) {
        val source = android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(octets))
        val dessin = runCatching {
            android.graphics.ImageDecoder.decodeDrawable(source)
        }.getOrNull()

        if (dessin is AnimatedImageDrawable) {
            dessin.start()
            return ImageDecodee(dessin, null)
        }
        if (estApng(octets)) {
            val apng = runCatching { decodeurApng(octets) }.getOrNull()
            if (apng != null) {
                apng.start()
                return ImageDecodee(apng, null)
            }
        }
        if (dessin != null) {
            val bitmap = (dessin as? BitmapDrawable)?.bitmap?.asImageBitmap()
                ?: dessin.toBitmapCompat()
            if (bitmap != null) return ImageDecodee(null, bitmap)
        }
    }
    // the fallback subsamples too: the surface is already known from `bornes`
    var echelle = 1
    while ((bornes.outWidth.toLong() / echelle) * (bornes.outHeight.toLong() / echelle) > SURFACE_MAX) {
        echelle *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = echelle }
    val repli = BitmapFactory.decodeByteArray(octets, 0, octets.size, options) ?: return null
    return ImageDecodee(null, repli.asImageBitmap())
}

@Composable
actual fun DessinDecode(
    image: ImageDecodee,
    contentScale: ContentScale,
    modifier: Modifier,
    anime: Boolean,
    pauseEntreBouclesMs: Long,
) {
    val dessin = image.dessin
    val bitmap = image.bitmap

    if (dessin == null) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = contentScale,
                modifier = modifier,
            )
        }
        return
    }

    // an animated `Drawable` decodes its frames whether anybody is watching or not, so stopping it
    // is the only thing that makes `PlafondAnimations`'s cap real. `onDispose` stops it too,
    // because an occurrence that left the screen has nobody left to do it
    DisposableEffect(dessin, anime, pauseEntreBouclesMs) {
        val animation = dessin as? Animatable
        if (anime) {
            // one run, then the rest. the limit was only armed from the second cycle on, so a
            // file declaring endless loops, a gif or an apng with `num_plays = 0`, played without
            // stopping and the rest never came
            if (pauseEntreBouclesMs > 0) {
                (dessin as? com.github.penfeizhou.animation.FrameAnimationDrawable<*>)?.setLoopLimit(1)
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    (dessin as? android.graphics.drawable.AnimatedImageDrawable)?.repeatCount = 0
                }
            }
            if (animation?.isRunning == false) animation.start()
        } else {
            animation?.stop()
        }
        onDispose { animation?.stop() }
    }

    // **the replay, driven from here and not by the library's end of animation callback**
    //
    // what was wrong. the pause was built on `Animatable2Compat.onAnimationEnd`: run once, and on
    // the callback schedule a `start()` after the rest. measured on a profile effect, that produced
    // exactly nothing — zero pixels of difference at forty seconds and at seventy, with the rest
    // set to thirty four. the effect played once and stayed on its first frame, which is where
    // stopping rewinds it to
    //
    // the reason is in the file. the catalogue's effects are apng declaring `num_plays = 1` in
    // their `acTL` chunk: the decoder is doing what the image asks, it plays once and stops for
    // good, and a bare `start()` on a decoder that has used up its plays gives one frame and
    // stops again
    //
    // so the rest is not a property of the drawable, it is a rhythm, and compose can keep a rhythm
    // without asking anybody. every [pauseEntreBouclesMs] we rewind and start again. the effect
    // lasts a second and a half against a rest of twenty seven, so counting from the start of one
    // run rather than from the end of it shifts the beat by six per cent, which nobody can see and
    // which costs no callback, no handler and no library detail
    if (anime && pauseEntreBouclesMs > 0) {
        val cadence = dessin as? com.github.penfeizhou.animation.FrameAnimationDrawable<*>
        LaunchedEffect(dessin, pauseEntreBouclesMs) {
            while (true) {
                kotlinx.coroutines.delay(pauseEntreBouclesMs)
                val animation = dessin as? Animatable ?: break
                // the limit is re armed every time: the decoder counts its plays and keeps the
                // count, so without this the second run stops on its first frame like the file
                // asked. `stop` before `start` because a decoder halted by its limit still answers
                // `isRunning` true for a moment, and `start` on a running one is ignored
                cadence?.setLoopLimit(1)
                if (animation.isRunning) animation.stop()
                animation.start()
            }
        }
    }

    // `clipToBounds` because `Crop` deliberately overflows: without it a cropped banner would spill
    // onto its surroundings instead of being cut
    Canvas(modifier.clipToBounds()) {
        val li = dessin.intrinsicWidth
        val hi = dessin.intrinsicHeight
        if (li > 0 && hi > 0) {
            val facteur = contentScale.computeScaleFactor(
                Size(li.toFloat(), hi.toFloat()),
                size,
            )
            val large = li * facteur.scaleX
            val haut = hi * facteur.scaleY
            val x = ((size.width - large) / 2f).toInt()
            val y = ((size.height - haut) / 2f).toInt()
            dessin.setBounds(x, y, x + large.toInt(), y + haut.toInt())
        } else {
            dessin.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        }
        // the rest shows nothing, like the reference: a run that has ended would otherwise hold its
        // last frame, a ghost of the effect over the card until the next run
        val auRepos = anime && pauseEntreBouclesMs > 0 && (dessin as? Animatable)?.isRunning == false
        if (!auRepos) drawIntoCanvas { toile -> dessin.draw(toile.nativeCanvas) }

        // redraw on every screen frame. an animated `Drawable` notifies its invalidation callback,
        // but compose listens to none, and reading the clock creates the missing dependency
        //
        // the condition is `anime`, and emphatically not `isRunning`. the first version read the
        // clock "while the animation is running", which sounds smarter and turns out to be self
        // blocking: one single frame where the decoder answers false, and there is one at every
        // `start()`, which resets asynchronously, is enough for the dependency not to be recorded,
        // so for this draw never to be re evaluated again, so for it to stay frozen forever.
        // measured: an apng decoration motionless for three seconds, zero pixels of difference,
        // while the gif next to it kept turning
        if (anime) {
            @Suppress("UNUSED_EXPRESSION")
            horlogeAnimation.longValue
        }
    }
}

/**
 * decodes with subsampling, never building the full size bitmap
 *
 * a modern phone photo is 4000 px wide, and decoded as is it takes some sixty megabytes to be
 * displayed at 260 dp. the first pass reads the header only (`inJustDecodeBounds`), the second
 * decodes straight at the right scale
 *
 * the factor aims at twice the target width, which keeps the image sharp on a dense screen without
 * going to the next factor, which would double the memory
 */
actual fun decoderMedia(octets: ByteArray, largeurCible: Int): ImageBitmap? {
    val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
    // past the ceiling nothing is decoded at all, see `surfaceRefusee`
    if (surfaceRefusee(bornes.outWidth, bornes.outHeight)) return null

    val cible = largeurCible.coerceAtLeast(1) * 2
    var echelle = 1
    while (bornes.outWidth / echelle > cible) echelle *= 2

    // and, independently of the width, bound the surface actually decoded
    //
    // the factor above looks only at the width, which is enough for a photograph and not at
    // all for a tall narrow image: 100 wide by a hundred million tall kept a factor of 1 and
    // was decoded whole. the second loop always ends, since an integer division by a divisor
    // that doubles eventually gives zero
    while ((bornes.outWidth.toLong() / echelle) * (bornes.outHeight.toLong() / echelle) > SURFACE_MAX) {
        echelle *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = echelle }
    return BitmapFactory.decodeByteArray(octets, 0, octets.size, options)?.asImageBitmap()
}

/**
 * an apng is recognised by its `acTL` chunk, which always precedes the image data
 *
 * we look in the first few kilobytes and not in the whole file: the spec puts the chunk before the
 * `IDAT`, and a decoration weighs a megabyte there would be no point sweeping on every display
 */
private fun estApng(octets: ByteArray): Boolean {
    val fin = minOf(octets.size - 4, 8192)
    for (i in 8 until fin) {
        if (octets[i] == 0x61.toByte() && octets[i + 1] == 0x63.toByte() &&
            octets[i + 2] == 0x54.toByte() && octets[i + 3] == 0x4C.toByte()
        ) return true
    }
    return false
}

/**
 * an apng's animated `Drawable`
 *
 * a fresh `ByteBufferLoader` on every call: the decoder reads the source several times, and an
 * already consumed `ByteBuffer` would give an empty image on the second pass
 */
private fun decodeurApng(octets: ByteArray): com.github.penfeizhou.animation.apng.APNGDrawable =
    com.github.penfeizhou.animation.apng.APNGDrawable(
        object : com.github.penfeizhou.animation.loader.ByteBufferLoader() {
            override fun getByteBuffer(): java.nio.ByteBuffer = java.nio.ByteBuffer.wrap(octets)
        },
    ).apply {
        // we loop, whatever the header says
        //
        // an apng carries a play count (`acTL.num_plays`). the house profile effect says 1, so it
        // played once, in a second and a half, then froze on its last frame, which is empty, as it
        // often is for an effect that fades out. three seconds after opening the profile there was
        // nothing left to see, and the bug looked like a failed load. the avatar decoration says 0,
        // meaning infinite, which is why it kept turning next to it with nobody suspecting the
        // header
        //
        // zero means unlimited to the library. it is also what the reference does: its cosmetics
        // run for as long as the card is open
        setLoopLimit(0)
    }

/** the fallback for drawables that do not already carry a bitmap */
private fun Drawable.toBitmapCompat(): ImageBitmap? = runCatching {
    val l = intrinsicWidth.coerceAtLeast(1)
    val h = intrinsicHeight.coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(l, h, android.graphics.Bitmap.Config.ARGB_8888)
    val toile = android.graphics.Canvas(bitmap)
    setBounds(0, 0, l, h)
    draw(toile)
    bitmap.asImageBitmap()
}.getOrNull()

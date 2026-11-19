package li.velvetchat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import li.velvetchat.core.MatrixEngine

/**
 * the engine, available anywhere in the ui tree
 *
 * an image is rendered three levels under the conversation, and passing it down as a parameter
 * would make every intermediate composable carry a dependency it does not use.
 * `staticCompositionLocalOf` and not `compositionLocalOf`: the value never changes during a
 * screen's life, so nothing needs to recompose when it is read
 *
 * portable because `MatrixEngine` is. the engine is an interface in `core` with no line of android
 * in it, which is what lets this provider, and therefore everything that displays a media, live in
 * the shared module rather than in the android app
 */
val LocalEngine = staticCompositionLocalOf<MatrixEngine> {
    error("LocalEngine n'a pas été fourni — voir Shell()")
}

/**
 * how many animated images are allowed to run at once
 *
 * this cap has been asked for from the start, and the reason is that no continuous animation should
 * be expensive. it is not an aesthetic preference, it is the real time liquid glass that made
 * earlier projects crawl, under another name
 *
 * an animated image decodes its frames continuously whether anybody is watching or not. compose
 * does destroy what leaves the screen, but it protects nothing when eight gifs are visible at once,
 * and the picker grid shows six to eight, and an animated conversation lines up as many
 *
 * four, and the number reads like this: more than the number of gifs actually being watched at
 * once, which is one, nearly always, and enough that a grid does not look frozen while you scroll
 * it. the rest display on their current frame, visible, recognisable, simply still, and take over
 * as soon as a slot frees up
 *
 * what is not counted: emoji and cosmetics ([EmojiEnLigne]). an avatar decoration is 64 points
 * across and part of somebody's identity, and freezing it to save on a 400 point gif would be
 * paying in the wrong place
 *
 * ponytail: fixed cap. if an old device still struggles, scale it with the density rather than
 * adding a setting
 */
object PlafondAnimations {
    const val MAX = 4

    /** the keys currently running. observable, so freeing a slot wakes the ones waiting */
    internal val actifs = mutableStateListOf<Any>()
}

/**
 * asks for permission to animate, and gives it back on leaving the screen
 *
 * @return true when the animation may run. false asks for it to be stopped, not hidden
 */
@Composable
fun creneauAnime(cle: Any): Boolean {
    val actifs = PlafondAnimations.actifs

    // reading the set here is what subscribes the caller: when a neighbour leaves the screen the
    // size changes, this function is re evaluated, and the effect below claims the freed slot.
    // without that read, a frozen gif would stay frozen until it left the screen itself
    val accorde = actifs.contains(cle)
    val occupees = actifs.size

    LaunchedEffect(cle, occupees) {
        if (!actifs.contains(cle) && actifs.size < PlafondAnimations.MAX) actifs.add(cle)
    }
    DisposableEffect(cle) {
        onDispose { actifs.remove(cle) }
    }
    return accorde
}

/**
 * the in memory cache of decoded images
 *
 * without it, every scroll pass would ask for the media to be downloaded and decrypted again.
 * `mediaBytes` is not free, and an image that reloads on every pass flickers
 *
 * bounded by a pixel budget and not by an entry count. twenty thumbnails and twenty full resolution
 * photos do not occupy the same thing, and counting entries would let the heap grow without limit
 * depending on what got shared. the process was already holding 400 mb resident when this was
 * written, which is not the place to be approximate
 *
 * animated decodes do not go in, deliberately: an animated decode carries its playback state, so
 * sharing it would lock two occurrences to the same frame. see `Decodage.kt`
 */
fun cacheMedia(cle: String): ImageBitmap? = CacheImages.get(cle)

fun mettreEnCacheMedia(cle: String, image: ImageBitmap) = CacheImages.put(cle, image)

private object CacheImages {
    private const val BUDGET_PIXELS = 12_000_000
    private val entrees = LinkedHashMap<String, ImageBitmap>(0, 0.75f, true)
    private val verrou = Verrou()
    private var pixels = 0L

    fun get(cle: String): ImageBitmap? = verrou.tenir { entrees[cle] }

    fun put(cle: String, image: ImageBitmap) = verrou.tenir {
        if (entrees.put(cle, image) == null) pixels += image.width.toLong() * image.height
        // `accessOrder = true` above, so the iterator starts with the least recently read entry
        // rather than the oldest inserted one. that is the difference between evicting what nobody
        // looks at any more and evicting what somebody has been looking at for a long time
        val it = entrees.entries.iterator()
        while (pixels > BUDGET_PIXELS && it.hasNext()) {
            val e = it.next()
            if (e.key == cle) continue
            pixels -= e.value.width.toLong() * e.value.height
            it.remove()
        }
    }
}

/**
 * a lock, because `@Synchronized` does not exist in common code
 *
 * the image caches are read from the composition, the main thread, and filled from the decoding
 * thread. that is not a theoretical precaution: a `LinkedHashMap` in access order reorders its
 * links on read, so two threads merely consulting it at the same time are enough to corrupt it,
 * with neither of them writing
 *
 * `kotlin.jvm.Synchronized` and `synchronized {}` both live in `kotlin.jvm`, absent from common
 * sources, even though both of this module's targets are jvms. hence these three lines, identical
 * on both sides: the literal price of portability here
 */
internal expect class Verrou() {
    fun <R> tenir(bloc: () -> R): R
}

/**
 * the thread for waiting on the network
 *
 * `Dispatchers.IO` lives in kotlinx-coroutines' jvm sources and not in the common ones, the same
 * wall as `@Synchronized` (see [Verrou]), and for the same reason
 *
 * the distinction matters and is not a formality: `Default` has as many threads as cores, and a
 * download blocking one of them blocks a fixed share of all the app's computation. `IO` grows to
 * absorb waiting. decoding stays on `Default`, being computation rather than waiting
 */
internal expect val filAttente: kotlinx.coroutines.CoroutineDispatcher

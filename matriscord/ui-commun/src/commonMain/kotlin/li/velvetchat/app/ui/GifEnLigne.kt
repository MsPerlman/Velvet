package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext

/**
 * do we load gifs hosted elsewhere?
 *
 * a gif sent as a link lives at its provider, so displaying it tells them who is looking and when.
 * the setting exists so that stays a choice
 */
val LocalGifsEnLigne = staticCompositionLocalOf { true }

/**
 * a gif in the conversation, when the message is only its link
 *
 * the display half of the decision: the message carries the gif's address at its host, so it has to
 * be fetched to be shown. `LienGif` has already decided that this particular address is loadable,
 * and this component only loads it
 *
 * the space is reserved before the image arrives, from the file's dimensions once decoded, and from
 * a square until then. without that, scrolling back through a conversation makes the screen jump
 * every time a gif finishes downloading, and you tap on whatever just moved
 *
 * portable through the decoding seam: this path used to call `ImageDecoder` and
 * `AnimatedImageDrawable` directly, which is half the decoding written a second time next to
 * `EmojiEnLigne`'s. it now goes through [decoderPourAffichage] like everything else, and the
 * desktop got it for free
 *
 * @param onEchec called when nothing could be loaded or decoded. the caller then falls back on the
 *   message's text, so on the link: we degrade, we never hide. a gif whose host has gone leaves an
 *   address you can at least read
 */
@Composable
fun GifDuMessage(url: String, onEchec: () -> Unit) {
    val tokens = LocalTokens.current
    val densite = LocalDensity.current

    var decode by remember(url) { mutableStateOf<ImageDecodee?>(null) }
    var proportion by remember(url) { mutableStateOf(1f) }
    // the file's intrinsic width in pixels, 0 until we have it
    var largeurPx by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url) {
        val octets = octetsDistants(url)
        if (octets == null) {
            onEchec()
            return@LaunchedEffect
        }
        // `Default` and not the waiting thread: the download is over, what follows is computation
        val resultat = withContext(kotlinx.coroutines.Dispatchers.Default) {
            decoderPourAffichage(octets)
        }
        if (resultat == null) {
            onEchec()
            return@LaunchedEffect
        }
        proportion = rapport(resultat.largeur, resultat.hauteur)
        largeurPx = resultat.largeur
        decode = resultat
    }

    // the same cap as the discovery grid: a sheet of animated thumbnails is exactly what the
    // animation budget exists to bound. `DessinDecode` really does stop the decoding when the slot
    // is refused, it does not merely hide
    val tourne = creneauAnime(url)

    // its own size, capped, and not the cap for everybody
    //
    // `fillMaxWidth()` gave 240 points to any gif, so a 120 pixel thumbnail arrived blown up to
    // double, blurry, and a tiny reaction took as much room as a wide shot. the reference shows the
    // file's size while it fits and only shrinks past that
    //
    // `largeurPx` is in file pixels, so converted to points at the screen's density. treating it as
    // `dp` would make a gif three times too big on a 3x screen
    val largeur = if (largeurPx > 0) {
        with(densite) { largeurPx.toDp() }.coerceAtMost(LARGEUR_MAX_GIF)
    } else LARGEUR_MAX_GIF

    Box(
        Modifier.width(largeur)
            .aspectRatio(proportion)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised),
    ) {
        decode?.let {
            // `FillBounds` because the box already carries the file's ratio, so the constraint is
            // exact, which is what the old `setBounds` over the whole surface was doing
            DessinDecode(it, ContentScale.FillBounds, Modifier.fillMaxSize(), anime = tourne)
        }
    }
}

/**
 * the bytes at an address, or null
 *
 * `HttpURLConnection` and not a library: it is jdk, so present on both platforms, and a single GET
 * with no special headers does not justify a dependency
 */
suspend fun octetsDistants(url: String): ByteArray? = withContext(filAttente) {
    runCatching {
        val cnx = (java.net.URI.create(url).toURL().openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }
        try {
            if (cnx.responseCode != 200) null else cnx.inputStream.use { it.readBytes() }
        } finally {
            cnx.disconnect()
        }
    }.getOrNull()
}

/**
 * the maximum width of a gif in the conversation
 *
 * 240 points, the reference's measurement, and not by accident: past that a gif pushes the rest of
 * the conversation off screen and a thread becomes a strip of images
 */
private val LARGEUR_MAX_GIF = 240.dp

/** the aspect ratio, bounded so a degenerate image cannot fill the screen */
private fun rapport(largeur: Int, hauteur: Int): Float =
    if (largeur <= 0 || hauteur <= 0) 1f
    else (largeur.toFloat() / hauteur).coerceIn(0.4f, 3f)

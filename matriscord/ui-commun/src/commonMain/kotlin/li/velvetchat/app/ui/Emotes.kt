package li.velvetchat.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.core.model.CustomEmote
import li.velvetchat.core.model.EmotePack

/**
 * the current server's emoji pack, available anywhere in the tree
 *
 * supplied by `Shell`, which knows which server is open. the composer needs it to translate
 * `:blahaj:` on send, the picker to offer them, and nothing in between uses it, so passing it down
 * as a parameter would cross four composables for nothing
 */
val LocalEmotes = staticCompositionLocalOf<List<CustomEmote>> { emptyList() }

/**
 * the same emoji, grouped by server, for the picker
 *
 * two locals rather than one derived from the other: [LocalEmotes] is already flattened and
 * deduplicated, so it has lost what the sections would be rebuilt from. the other way round,
 * exposing only the packs, would make the autocomplete reflatten and rededuplicate on every
 * keystroke
 */
val LocalPacks = staticCompositionLocalOf<List<EmotePack>> { emptyList() }

/**
 * the character that holds an emoji's place in the text
 *
 * `U+FFFC OBJECT REPLACEMENT CHARACTER` is exactly what unicode provides for this, signalling that
 * a non textual object goes here. two properties that matter: it takes a single character, so every
 * style range computed on the text stays valid, and it cannot be confused with something somebody
 * typed
 */
const val MARQUE_EMOJI = '￼'

/**
 * compiled once, not per message. all three lived inside [extraireEmojis], so recompiled for every
 * message shown and the last two for every `<img>` of every message, built inside the loop.
 * compiling a regex costs far more than applying one, and on a scrolling timeline that was work
 * redone thousands of times for three objects that never change
 */
private val MOTIF_IMG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val MOTIF_SRC = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val MOTIF_ALT = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

/**
 * finds the emoji `<img>` tags in a `formatted_body` and replaces them with marks
 *
 * they come out of the html before parsing because android's html rendering can only show an image
 * by demanding a drawable immediately, through a synchronous callback, and ours live on the server
 * behind a download. there is no honest way through that path, so they are set aside and the ui
 * puts them back as inline content, where waiting is allowed
 *
 * @return the html without its images, and the ordered list of their `mxc://` addresses
 */
fun extraireEmojis(html: String): Pair<String, List<String>> {
    val adresses = mutableListOf<String>()
    val sansImages = MOTIF_IMG.replace(html) { trouve ->
        val balise = trouve.value
        val src = MOTIF_SRC.find(balise)?.groupValues?.get(1)
        // an image that is not an `mxc://` is not one of this server's emoji, so we drop it rather
        // than go and fetch an arbitrary address that came in a message. loading a url because a
        // message asked you to is announcing that you read it to whoever hosts it
        if (src == null || !src.startsWith("mxc://")) {
            val texte = MOTIF_ALT.find(balise)?.groupValues?.get(1)
            return@replace texte ?: ""
        }
        adresses += src
        MARQUE_EMOJI.toString()
    }
    return sansImages to adresses
}

/**
 * an `mxc://` image rendered inline, animated when it is animated — nothing emoji about it but the
 * name, since it also carries avatar decorations, banners and profile effects. a second one would
 * only be a second place to fix the same bugs
 *
 * what the decoding cost to learn lives in `Decodage.kt`, the other side of the seam: the apng
 * android will not animate, the hardware bitmap you must not copy, the `contentScale` to honour by
 * hand on an animated drawable. what stays here never depended on a platform — where the bytes are
 * and what to cache
 *
 * animated decodes are not cached, unlike stills: playback state is part of them, so two messages
 * sharing one would lock to the same frame and stopping one would stop the other. one decode per
 * occurrence, a few tens of kilobytes, against wrong behaviour
 */
@Composable
fun EmojiEnLigne(
    mxcUrl: String,
    /**
     * `Fit` for an emoji, `Crop` for a banner
     *
     * an emoji has to fit whole inside its box, even if that leaves space around it. a banner is a
     * background, so it has to fill, even if that means being cropped. set to `Fit` it drew at its
     * natural size in the middle of a bar it left empty on both sides
     */
    contentScale: ContentScale = ContentScale.Fit,
    /**
     * asking for a thumbnail on that side, instead of the original file
     *
     * a decoration weighs about a megabyte, and its 160 px thumbnail about fifty kilobytes. on a
     * grid of six hundred objects that is not a fine tuning, it is the difference between a page
     * that draws and a page that crawls
     *
     * the price: the thumbnail is still. the server only resizes an image, and returns the first
     * frame of an apng. that is the right trade for a picker grid, where you recognise an object
     * rather than contemplate it, and the avatar does carry the animation once it is worn
     */
    taillePx: Int? = null,
    /** see [DessinDecode]: the pause between two runs of an animated image */
    pauseEntreBouclesMs: Long = 0,
) {
    val engine = LocalEngine.current

    // the cache key carries the size. without it, a grid's thumbnail and an avatar's original would
    // replace each other depending on display order
    val cle = if (taillePx == null) mxcUrl else "$mxcUrl#$taillePx"
    var fixe by remember(cle) { mutableStateOf(cacheMedia(cle)) }
    var anime by remember(cle) { mutableStateOf<ImageDecodee?>(null) }

    LaunchedEffect(cle) {
        if (fixe != null || anime != null) return@LaunchedEffect
        val octets = runCatching {
            if (taillePx != null) {
                // fall back on the original when the server cannot thumbnail that media: a slow
                // cell beats an empty one
                engine.avatarBytes(mxcUrl, taillePx) ?: engine.emoteBytes(mxcUrl)
            } else {
                engine.emoteBytes(mxcUrl)
            }
        }.getOrNull() ?: return@LaunchedEffect

        // `Default` and not `IO`: what follows is computation and not waiting, and `IO` does not
        // exist in common code. reading the bytes is already off the main thread, that being the
        // contract of a `suspend` function on the engine
        val decode = withContext(Dispatchers.Default) {
            decoderPourAffichage(octets)
        } ?: return@LaunchedEffect

        if (decode.animee) {
            anime = decode
        } else {
            decode.bitmapFixe?.let {
                mettreEnCacheMedia(cle, it)
                fixe = it
            }
        }
    }

    val decode = anime
    val bitmap = fixe
    when {
        // `anime = true` without going through [creneauAnime]: cosmetics are not counted against
        // the cap, see [PlafondAnimations] for why
        decode != null -> DessinDecode(
            decode,
            contentScale,
            Modifier.fillMaxSize(),
            anime = true,
            pauseEntreBouclesMs = pauseEntreBouclesMs,
        )

        bitmap != null -> Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            // quality filtering. by default compose downscales an image in low quality, and a 448
            // px plate brought down to 140 came out blurry while the file itself is sharp
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
        )
    }
}

// translating `:shortcodes:` on send, and the autocomplete, moved to `Autocompletion.kt`: they now
// share a single html escaping with the `@` mentions and the `#` channel links, which rewrite the
// same text. keeping them apart would have escaped twice, or more likely every other time

/**
 * a preview text whose `:shortcodes:` are rendered as the emoji they name
 *
 * where it comes from: the direct conversation list showed `:blahaj:` in the clear where the
 * conversation itself showed the image. the shortcode is not text somebody meant to write, it is
 * the shape an emoji travels in inside the message body, and leaving it as is means showing the
 * plumbing
 *
 * why a separate function rather than `CorpsFormate`: that one starts from the `formatted_body` and
 * renders the full formatting, bold, code, links, asides. a preview wants none of it, it fits on
 * one grey line and bold in there is noise. the two paths share the inline content machinery, not
 * the formatting
 *
 * a shortcode no pack knows is left as it is. that is the right fallback: it stays readable, and it
 * tells whoever reads it that the emoji comes from a server they are not on
 */
@Composable
fun ApercuAvecEmotes(
    texte: String,
    couleur: androidx.compose.ui.graphics.Color,
    taille: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    /** left unset, so the current style's, 24 in the material theme. desktop bios want 18 */
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    val emotes = LocalEmotes.current

    // recomputed only when the text or the pack changes. a conversation list recomposes on every
    // incoming message, and one regular expression per row every time would be paid for nothing
    val (rendu, adresses) = remember(texte, emotes) {
        val trouvees = mutableListOf<String>()
        val construit = androidx.compose.ui.text.buildAnnotatedString {
            var position = 0
            for (trouve in MOTIF_RACCOURCI.findAll(texte)) {
                val url = emotes.firstOrNull { it.shortcode == trouve.groupValues[1] }?.url
                    ?: continue
                append(texte.substring(position, trouve.range.first))
                appendInlineContent("apercu-${trouvees.size}", trouve.value)
                trouvees += url
                position = trouve.range.last + 1
            }
            append(texte.substring(position))
        }
        construit to trouvees.toList()
    }

    val contenus = remember(adresses, taille) {
        adresses.mapIndexed { i, url ->
            "apercu-$i" to InlineTextContent(
                Placeholder(taille, taille, PlaceholderVerticalAlign.TextCenter),
            ) { EmojiEnLigne(url) }
        }.toMap()
    }

    androidx.compose.material3.Text(
        text = rendu,
        color = couleur,
        fontSize = taille,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        inlineContent = contenus,
        modifier = modifier,
    )
}

/** `:name:`, the same characters a shortcode accepts when typed */
private val MOTIF_RACCOURCI = Regex(""":([A-Za-z0-9_+-]+):""")

package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.ApercuLien

/** a complete http(s) address: the scheme is required, see [premierLien] */
private val MOTIF_LIEN = Regex("""https?://[^\s<>\"]+""")

/**
 * the card showing what is at the end of a link
 *
 * nothing used to be shown: a pasted address stayed an address, and finding out what it opened
 * meant following it. this is the last of the three previews this file handles, after video and
 * pdf, the link
 *
 * the phone never contacts the site. the preview comes from the homeserver, which fetches the page
 * and brings its image into its own repository. that is a real protection and not an architectural
 * detail: a link received from anybody cannot be used to harvest the ip addresses of everybody
 * reading the room. the counterpart, server side, is the private address blocklist in
 * `homeserver.yaml`, without which the option would probe the local network
 *
 * one link per message, the first. the reference shows up to five, and a message carrying several
 * is rare, while five stacked cards cost five server round trips for a message people skim
 *
 * ponytail: first link only, to widen if anybody complains about a two link message
 */
@Composable
fun ApercuDuLien(texte: String) {
    val engine = LocalEngine.current
    val tokens = LocalTokens.current

    val lien = remember(texte) { premierLien(texte) } ?: return
    var apercu by remember(lien) { mutableStateOf<ApercuLien?>(null) }

    // the engine remembers its answers, absences included, so redisplaying an already seen message
    // fires no request, and scrolling a conversation does not fire one per pass
    LaunchedEffect(lien) {
        apercu = runCatching { engine.apercuLien(lien) }.getOrNull()
    }

    val vu = apercu ?: return

    // the reference's embed: raised fill, a 1 point border, radius 4, a 4 point bar on the left,
    // 432 wide at most, 8 / 16 / 16 / 12 inside (`.embedFull`). the bar takes the embed's colour
    // there; a link carries none, so it gets the reference's neutral one
    val forme = RoundedCornerShape(4.dp)
    val navigateur = androidx.compose.ui.platform.LocalUriHandler.current
    Column(
        Modifier
            .padding(top = 6.dp)
            .widthIn(max = 432.dp)
            .clip(forme)
            .background(tokens.surfaceRaised)
            .border(1.dp, tokens.border, forme)
            .drawBehind { drawRect(tokens.textMuted.copy(alpha = 0.4f), size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
    ) {
        vu.site?.let {
            Text(
                it,
                color = tokens.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        vu.titre?.let {
            Text(
                it,
                color = tokens.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // the title is the link, like the reference
                modifier = Modifier.padding(top = 8.dp).curseurMain()
                    .clickable { runCatching { navigateur.openUri(vu.url) } },
            )
        }
        vu.description?.let {
            Text(
                it,
                color = tokens.textSecondary,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        vu.image?.let { mxc ->
            // the aspect ratio comes from the server when it gives one. without it the card would
            // resize when the image arrives and make the conversation jump, exactly the fault the
            // image dimensions already avoid on the sending side
            val ratio = ratioUtile(vu.largeurImage, vu.hauteurImage)
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .then(
                        if (ratio != null) Modifier.aspectRatio(ratio)
                        else Modifier.height(160.dp)
                    )
                    .clip(forme),
            ) {
                EmojiEnLigne(mxc, contentScale = ContentScale.Crop, taillePx = 800)
            }
        }
    }
}

/**
 * the aspect ratio, when it is usable
 *
 * discarded when a dimension is missing, zero, or gives an absurdly shaped image: a 1200 by 40
 * `og:image` would stretch the card into a thin line, and some sites serve those
 */
private fun ratioUtile(largeur: Int?, hauteur: Int?): Float? {
    if (largeur == null || hauteur == null || largeur <= 0 || hauteur <= 0) return null
    val r = largeur.toFloat() / hauteur
    return r.takeIf { it in 0.4f..4f }
}

/**
 * the first http(s) address in the text, or null
 *
 * a pattern of our own, requiring the scheme. android's `Patterns.WEB_URL` knows the real tlds, but
 * it only exists on the phone and this preview is shared by both apps. requiring `http://` or
 * `https://` replaces what that list brought: we do not guess a scheme for a bare domain, so we do
 * not send the server an address we invented
 *
 * trailing punctuation is stripped: "look at https://example.org." is not asking for a preview of
 * the address with the full stop attached
 */
private fun premierLien(texte: String): String? {
    if (texte.isBlank()) return null
    for (trouve in MOTIF_LIEN.findAll(texte)) {
        val brut = trouve.value.trimEnd('.', ',', ')', ']', '»', '"', '\'')
        run {
            // a gif link already has its own rendering, as an image, and asking for its preview
            // would show the same thing twice
            if (li.velvetchat.core.gif.LienGif.dans(brut) != null) return null
            return brut
        }
    }
    return null
}

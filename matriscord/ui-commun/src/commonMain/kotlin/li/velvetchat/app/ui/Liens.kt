package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * what happens when somebody taps an address
 *
 * links used to not be tappable, and the comment explaining it was right on the substance: making
 * them active means deciding what opens and under what confirmation, which is a security question
 * and not a styling one. this file answers the question instead of leaving it open, because a link
 * you cannot follow is a daily annoyance, and security is not a reason to do nothing, only a reason
 * to be careful
 *
 * the two dangers, and what covers them
 *
 * the deceptive link. matrix's html allows `<a href="https://trap.example">bank.example</a>`: the
 * displayed text announces one destination and the link aims at another. that is the only real
 * phishing risk inside a conversation, and [lienTrompeur] catches it by comparing the two hosts.
 * when they differ we ask, showing the real address in full
 *
 * the unexpected scheme. `ACTION_VIEW` on an `intent://` or a `javascript:` does not lead to a
 * page, it launches something else. [schemeSur] accepts `http`, `https` and `mailto` only, and the
 * rest is never opened, only shown and offered for copying
 *
 * what it does not cover, said plainly. a link whose text is not an address, "click here", goes
 * through unquestioned. that is deliberate: warning about everything amounts to warning about
 * nothing, and the reference applies the same rule. the protection is about links that claim to go
 * somewhere
 *
 * ponytail: host comparison. if unicode homographs ever become a real problem, this is where
 * punycode normalisation would go
 */

/** an address somebody just tapped, and that we do not open without asking */
data class LienACliquer(
    val url: String,
    val texteAffiche: String,
    /** false when the scheme is not one we open: we show, we do not open */
    val ouvrable: Boolean,
)

/**
 * the link waiting for confirmation
 *
 * module state, like [EnvoiImageEnCours] and for the same reason: the dialog has to be painted
 * above everything, so rendered at `Shell` level, while the tap happens deep inside a message's
 * text. routing that up through signatures would cross four composables that could not care less
 */
object LienEnAttente {
    var demande by mutableStateOf<LienACliquer?>(null)
}

/**
 * the schemes we agree to open
 *
 * an allow list and not a deny list: `intent://`, `javascript:`, `content://` and the rest do not
 * have to be enumerated to be refused. a scheme we do not know is a scheme we do not open
 */
fun schemeSur(url: String): Boolean {
    val u = url.trim()
    return u.startsWith("https://", true) ||
        u.startsWith("http://", true) ||
        u.startsWith("mailto:", true)
}

/**
 * an address's host, or null when the string does not claim one
 *
 * three traps handled, and each one has caught somebody
 *
 * - the credentials part. `http://bank.example@trap.example/` goes to the trap, and a naive read
 *     shows the reassuring one. hence splitting on the last `@`
 * - the port, which is not the host - `www.`, which is not a difference in destination
 *
 * a string containing a space or no dot claims no host: it is ordinary text, and saying null is
 * what avoids warning about "click here"
 */
fun hoteDe(valeur: String): String? {
    val sansScheme = valeur.trim().substringAfter("://", valeur.trim())
        .removePrefix("mailto:")
    val hote = sansScheme
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
        .substringBefore(':')
        .trim()
        .lowercase()
    if (hote.isEmpty() || '.' !in hote) return null
    if (hote.any { it.isWhitespace() }) return null
    return hote.removePrefix("www.")
}

/**
 * does the displayed text announce a different destination from the real one?
 *
 * @return true when we have to ask before opening
 */
fun lienTrompeur(texteAffiche: String, url: String): Boolean {
    // a target with no readable host cannot be compared, so we ask rather than assume. this happens
    // on non web schemes, which [schemeSur] already refuses
    val cible = hoteDe(url) ?: return true
    // the text claims nothing, "click here", a sentence, a word. nothing to betray
    val annonce = hoteDe(texteAffiche) ?: return false
    return !annonce.equals(cible, ignoreCase = true)
}

/**
 * completes an address spotted without a scheme
 *
 * a bare domain gets recognised as a link with no scheme in it. opening it as is fails, and
 * guessing `https` is what any address bar does. choosing `https` over `http` is not neutral
 * either, it is the one that does not quietly degrade to plaintext
 */
fun completerScheme(url: String): String =
    if (url.contains("://") || url.startsWith("mailto:", true)) url else "https://$url"

/**
 * the dialog showing where a link really goes
 *
 * rendered once, at `Shell` level. it only appears when it has something to say: an honest link
 * opens without asking, otherwise the confirmation would become a reflex people click through
 * without reading, which is exactly the opposite of the point
 */
@Composable
fun DialogueLien(onOuvrir: (String) -> Unit, onCopier: (String) -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    val demande = LienEnAttente.demande ?: return
    val fermer = { LienEnAttente.demande = null }

    Dialog(onDismissRequest = fermer) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surface)
                .padding(18.dp),
        ) {
            Text(
                if (demande.ouvrable) t.lienTrompeur
                else t.adresseInhabituelle,
                color = tokens.textPrimary,
                fontSize = tokens.typography.headingSize.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (demande.ouvrable) {
                    t.lienTexteTrompeur(demande.texteAffiche.trim())
                } else {
                    t.lienTypeInconnu
                },
                color = tokens.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            // the whole address, monospaced, over as many lines as it takes. truncating it would
            // hide precisely the deciding part, since a deceptive domain often puts the reassuring
            // name at the front
            Text(
                demande.url,
                color = tokens.textPrimary,
                fontSize = 13.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusSmall))
                    .background(tokens.surfaceRaised)
                    .padding(10.dp),
            )
            // three labels do not fit on one line, and "open anyway" came out broken into three on
            // screen. the two neutral actions stay side by side, and the committing one takes its
            // own line under a divider
            //
            // that is not only a space fix: an isolated button gets aimed at deliberately, where a
            // button at the end of a row gets touched out of habit, and habit is precisely what we
            // do not want here
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.copier,
                    color = tokens.textSecondary,
                    fontSize = tokens.typography.bodySize.sp,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.radiusSmall))
                        .curseurMain().clickable { onCopier(demande.url); fermer() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    t.annuler,
                    color = tokens.textPrimary,
                    fontSize = tokens.typography.bodySize.sp,
                    fontWeight = FontWeight.Bold,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.radiusSmall))
                        .curseurMain().clickable { fermer() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            // the risky action is at the bottom, alone, and is not the one that stands out. when
            // this dialog appears there is a doubt, and the default button for a doubt is not the
            // one that ignores it
            if (demande.ouvrable) {
                Text(
                    t.ouvrirQuandMeme,
                    color = tokens.danger,
                    fontSize = 13.sp,
                    softWrap = false,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(tokens.radiusSmall))
                        .curseurMain().clickable { onOuvrir(demande.url); fermer() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

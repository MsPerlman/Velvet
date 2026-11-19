package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.Reaction

/**
 * a message's reaction pills, shared by the desktop and the phone
 *
 * the "shine" that was asked for is a border. what shines on the reference is the pills' outline
 * and not a reflection. the gloss and the halo that used to be drawn here matched nothing and have
 * gone
 *
 * measured off the screenshot: a 32 tall pill, radius 8, a 2 point accent outline when you reacted
 * yourself, a background tinted with the accent at about 22%, an emoji around 18, the count in 14
 * semibold, then the stacked avatars of whoever reacted, 22 with an 11 overlap, and 5 between two
 * pills
 *
 * the reference's super reactions, outlined in the emoji's colour, with sparkles, have no matrix
 * equivalent, there being only one kind of reaction. nothing is invented for them
 *
 * @param onAjouter the "add a reaction" button after the last pill. null while the caller has no
 *   picker to open, and the button then does not appear rather than being dead
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LigneReactions(
    reactions: List<Reaction>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAjouter: (() -> Unit)? = null,
) {
    val t = textes()
    if (reactions.isEmpty()) return
    val tokens = LocalTokens.current
    val membres = LocalMembres.current

    // `FlowRow`: a dozen reactions overrun a message's width on the phone, so they wrap instead of
    // being clipped
    FlowRow(
        modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        reactions.forEach { r ->
            val forme = RoundedCornerShape(8.dp)
            Row(
                Modifier.height(32.dp)
                    .clip(forme)
                    .background(
                        if (r.mine) tokens.accent.copy(alpha = 0.22f).compositeOver(tokens.surface)
                        else tokens.surfaceRaised,
                    )
                    .border(2.dp, if (r.mine) tokens.accent else Color.Transparent, forme)
                    // tapping an existing pill adds or removes yours: it is the same action,
                    // exactly as the engine's contract describes it
                    .curseurMain().clickable { onToggle(r.emoji) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // a reaction with a custom emoji, imported from discord by the bridge or coming
                // from another client, arrives with an `mxc://` key, and gets rendered as an image
                if (r.emoji.startsWith("mxc://")) {
                    Box(Modifier.size(18.dp)) { EmojiEnLigne(r.emoji) }
                } else {
                    Text(r.emoji, fontSize = 16.sp, fontFamily = LocalPoliceEmoji.current)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${r.count}",
                    color = if (r.mine) tokens.textPrimary else tokens.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                // the last two people, stacked, most recent in front
                val visibles = r.par.takeLast(2)
                if (visibles.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    Box {
                        visibles.forEachIndexed { i, id ->
                            val membre = membres.firstOrNull { it.userId == id }
                            Box(Modifier.offset(x = (11 * i).dp)) {
                                Avatar(
                                    userId = id,
                                    displayName = membre?.displayName,
                                    avatarUrl = membre?.avatarUrl,
                                    taille = 22.dp,
                                    decoration = null,
                                )
                            }
                        }
                        // the offset reserves no space, so we reserve it here for the second avatar
                        Spacer(Modifier.width((22 + 11 * (visibles.size - 1)).dp))
                    }
                }
            }
        }
        onAjouter?.let { ajouter ->
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).curseurMain().clickable(onClick = ajouter),
                contentAlignment = Alignment.Center,
            ) {
                Icone(Icones.emoji, tint = tokens.textSecondary, taille = 20.dp, description = t.ajouterUneReaction)
            }
        }
    }
}

private fun Color.compositeOver(fond: Color): Color = Color(
    red = red * alpha + fond.red * (1 - alpha),
    green = green * alpha + fond.green * (1 - alpha),
    blue = blue * alpha + fond.blue * (1 - alpha),
    alpha = 1f,
)

package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.Statut

/**
 * presence on screen
 *
 * this file holds the three things that go together and would drift if they were spread out: a
 * state's colour, the dot that draws it, and the sheet that changes it
 */

/**
 * a state's colour
 *
 * the same ones as the reference, and that is not idle imitation: they are the only ones somebody
 * used to it reads without a legend. green is here, yellow is not at the screen, grey is not
 * reachable
 */
@Composable
fun couleurDe(presence: Presence): Color {
    val tokens = LocalTokens.current
    return when (presence) {
        Presence.EN_LIGNE -> tokens.colors.success.toColor()
        Presence.ABSENT -> tokens.warning
        // red, which is the reference's do not disturb colour and the danger colour here
        Presence.NE_PAS_DERANGER -> tokens.danger
        Presence.INVISIBLE, Presence.INCONNU -> tokens.textMuted
    }
}

/**
 * a state's label, as seen by yourself. see [libelleDAutrui] for the nuance
 *
 * `@Composable` because it reads the language: a state is written in the language of whoever is
 * looking at it
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun libelle(presence: Presence): String = textes().let { t ->
    when (presence) {
        Presence.EN_LIGNE -> t.statutEnLigne
        Presence.ABSENT -> t.statutAbsent
        Presence.NE_PAS_DERANGER -> t.statutNePasDeranger
        Presence.INVISIBLE -> t.statutInvisible
        Presence.INCONNU -> t.statutInconnu
    }
}

/**
 * a state's label as seen by somebody else, and the difference matters
 *
 * `offline` received from another person covers two situations nothing outside can tell apart: they
 * are not there, or they do not want anyone to know they are. showing "invisible" on somebody
 * else's status would therefore be wrong half the time, and would betray exactly what invisibility
 * protects
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun libelleDAutrui(presence: Presence): String = textes().let { t ->
    when (presence) {
        Presence.EN_LIGNE -> t.statutEnLigne
        Presence.ABSENT -> t.statutAbsent
        // that one says itself: the person published it, there is nothing to interpret
        Presence.NE_PAS_DERANGER -> t.statutNePasDeranger
        Presence.INVISIBLE -> t.statutHorsLigne
        Presence.INCONNU -> t.statutInconnu
    }
}

/**
 * the reference's status icon: a green circle for online, a moon for idle, a barred red circle for
 * do not disturb, a grey ring for offline. the hollows are real holes, whatever is behind shows
 * through, at the reference's mask proportions
 *
 * drawn here rather than composed out of boxes: the moon and the barred disc are cutouts, and
 * `BlendMode.Clear` on an offscreen layer is the only honest way to get them
 */
@Composable
fun IconeEtat(
    presence: Presence,
    taille: Dp,
    silence: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (presence == Presence.INCONNU) return
    androidx.compose.foundation.Canvas(
        modifier.size(taille).graphicsLayer {
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
        },
    ) {
        val d = size.minDimension
        when {
            (silence || presence == Presence.NE_PAS_DERANGER) && presence != Presence.INVISIBLE -> {
                drawCircle(Color(0xFFF23F43))
                drawRoundRect(
                    Color.Black,
                    topLeft = androidx.compose.ui.geometry.Offset(d * 0.25f, d * 0.4375f),
                    size = androidx.compose.ui.geometry.Size(d * 0.5f, d * 0.125f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(d * 0.0625f),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                )
            }

            presence == Presence.EN_LIGNE -> drawCircle(Color(0xFF23A55A))

            presence == Presence.ABSENT -> {
                drawCircle(Color(0xFFF0B232))
                drawCircle(
                    Color.Black,
                    radius = d * 0.375f,
                    center = androidx.compose.ui.geometry.Offset(d * 0.25f, d * 0.25f),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                )
            }

            else -> {
                drawCircle(Color(0xFF80848E))
                drawCircle(Color.Black, radius = d * 0.25f, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
            }
        }
    }
}

/**
 * the presence dot, ringed with the background it sits on
 *
 * the ring is not decorative: without it the dot merges into the avatar's edge instead of sitting
 * on it. same reason as the rail's mention badge
 *
 * an unknown state draws nothing. painting grey would assert an absence nobody observed, and on a
 * server with presence disabled everybody would look gone
 */
@Composable
fun PastillePresence(
    presence: Presence,
    taille: Dp = 12.dp,
    couleurDuFond: Color = LocalTokens.current.surface,
    modifier: Modifier = Modifier,
) {
    if (presence == Presence.INCONNU) return
    Box(
        modifier.size(taille + 4.dp).clip(CircleShape).background(couleurDuFond),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(taille).clip(CircleShape).background(couleurDe(presence)))
    }
}

/**
 * the status sheet, rebuilt from the reference
 *
 * four rows in one card, a radio button per row, a centred title: that is exactly the reference's
 * layout. the old version stacked three choices, a text field, a mute toggle and a typing toggle,
 * separated by headings, so six decisions on a screen that asks one
 *
 * do not disturb is not a presence, which is why it looks like one. matrix has three states only,
 * `online`, `unavailable`, `offline`, which is a property of the protocol and not a limitation of
 * this client. the reference gets the fourth from a separate setting: you stay reachable, but
 * nothing rings
 *
 * here that is the global mute, already in place. presenting it in the same list as the presences,
 * with the same radio button, is what makes the clone faithful: the person picks a state, not two
 * settings that combine
 *
 * what left this sheet. publishing typing went to the settings. it does not say how you declare
 * yourself, it says what the app emits on your behalf, and it has no equivalent on the reference,
 * because it is a question matrix asks and discord does not
 *
 * the header with the avatar and the nickname went too: the reference does not have it, and this
 * sheet now opens from the profile, where you have just seen both
 */
@Composable
fun FeuilleStatut(
    statutActuel: Statut,
    silenceActuel: Boolean,
    onChoisir: (Presence, String?) -> Unit,
    onSilence: (Boolean) -> Unit,
    onFermer: () -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()
    var message by remember { mutableStateOf(statutActuel.message.orEmpty()) }

    FeuilleBasse(onFermer = onFermer) {
        Text(
            t.changerLeStatut,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = tokens.espL),
        )

        Text(
            textes().statutEnLigneTitre,
            color = tokens.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = 8.dp),
        )

        // one card, with rules between the rows. the reference does not put four cards side by
        // side, it is one block, and the separators say the choices exclude each other, which a
        // stack of cards does not
        Column(
            Modifier.padding(horizontal = tokens.espXl)
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised),
        ) {
            // the reference's order, and it is not alphabetical: most present to least present. do
            // not disturb slips in third because you are still around
            val t = textes()
            val choix = listOf(
                Triple(Presence.EN_LIGNE, false, t.statutEnLigne),
                Triple(Presence.ABSENT, false, t.statutInactif),
                // a real state now, `org.matrix.msc3026.busy`, published to everybody else, on top
                // of the mute that silences notifications here. before that, the person showed as
                // online to everyone on the server
                Triple(Presence.NE_PAS_DERANGER, true, t.statutNePasDeranger),
                Triple(Presence.INVISIBLE, false, t.statutInvisible),
            )
            choix.forEachIndexed { i, (presence, silence, libelle) ->
                if (i > 0) {
                    // the rule stops short of the left edge, aligned with the text, which is what
                    // the reference does and which ties the row to its dot
                    Box(
                        Modifier.padding(start = 52.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(tokens.border),
                    )
                }
                val actif = statutActuel.presence == presence && silenceActuel == silence
                Row(
                    Modifier.fillMaxWidth()
                        .curseurMain().clickable {
                            // both, in this order: the mute first, the presence after. the other
                            // way round would flash the user bar on the old mute state during a
                            // network round trip
                            onSilence(silence)
                            onChoisir(presence, message.trim().takeIf { it.isNotBlank() })
                            onFermer()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphePresence(presence, silence)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        libelle,
                        color = tokens.textPrimary,
                        fontSize = tokens.typography.bodySize.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Radio(actif)
                }
            }
        }

        // the custom status, below and in its own card, like the reference, which shows it there
        // with its cross to clear it
        //
        // an emoji, some text, or both: that is what the reference allows, and matrix only carries
        // one string. so the emoji goes at the head of the text, which other clients display as is
        // rather than losing it. see `emojiDuStatut`
        Text(
            textes().statutPersonnalise,
            color = tokens.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(
                start = tokens.espXl, end = tokens.espXl, top = tokens.espL, bottom = 8.dp,
            ),
        )
        Row(
            Modifier.padding(horizontal = tokens.espXl)
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = message,
                onValueChange = { message = it.take(120) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = tokens.textPrimary,
                    fontSize = tokens.typography.bodySize.sp,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { champ ->
                    if (message.isEmpty()) {
                        Text(
                            textes().statutInvite,
                            color = tokens.textMuted,
                            fontSize = tokens.typography.bodySize.sp,
                        )
                    }
                    champ()
                },
            )
            if (message.isNotEmpty()) {
                Icone(
                    Icones.fermer,
                    tint = tokens.textSecondary,
                    taille = 18.dp,
                    description = t.statutEffacer,
                    modifier = Modifier.clip(CircleShape)
                        .curseurMain().clickable {
                            message = ""
                            onChoisir(statutActuel.presence, null)
                        }
                        .padding(4.dp),
                )
            }
        }

        // save the text without changing presence. without this button, writing a status would mean
        // clicking the state you already have, which makes no sense
        Box(Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espL)) {
            BoutonPlein(t.enregistrer) {
                onChoisir(statutActuel.presence, message.trim().takeIf { it.isNotBlank() })
                onFermer()
            }
        }
    }
}

/**
 * the reference's radio button: a ring, filled when selected
 *
 * drawn rather than taken from material: `RadioButton` brings its own 48 dp touch area and its
 * ripple, which shift the row and spill out of the card
 */
@Composable
private fun Radio(actif: Boolean) {
    val tokens = LocalTokens.current
    Box(
        Modifier.size(22.dp)
            .clip(CircleShape)
            .background(if (actif) tokens.accent else Color.Transparent)
            .border(
                width = if (actif) 0.dp else 2.dp,
                color = tokens.textMuted,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (actif) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
    }
}

/**
 * each state's sign, to the left of its label
 *
 * three dots and a crescent, like the reference, and the crescent is a real glyph and not an orange
 * dot, because idle and online are not distinguishable by hue alone for anybody who has trouble
 * with colours
 */
@Composable
private fun GlyphePresence(presence: Presence, silence: Boolean) {
    // the same drawing as the desktop: green circle, cut out moon, barred red disc, grey ring. the
    // phone used to put a moon emoji here for idle and filled circles for the rest, two drawings of
    // one thing that did not age together. `IconeEtat` also knows the do not disturb state
    IconeEtat(presence, 20.dp, silence)
}


/** a switch, drawn rather than borrowed from material, since the rest of the app uses none */
@Composable
fun Interrupteur(actif: Boolean) {
    val tokens = LocalTokens.current
    Box(
        Modifier.size(width = 40.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (actif) tokens.accent else tokens.surfaceRaised)
            .border(1.dp, tokens.border, RoundedCornerShape(12.dp)),
        contentAlignment = if (actif) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(horizontal = 3.dp).size(18.dp).clip(CircleShape)
                .background(if (actif) tokens.bg else tokens.textMuted)
        )
    }
}


/**
 * a round checkbox, drawn
 *
 * why a drawing and not an icon: an unchecked box is an empty ring, and there is no icon for
 * "nothing". the first version used a pin for the off state, which means nothing at all. two
 * concentric circles say the box and its state without anybody having to recognise a pictogram
 */
@Composable
fun Coche(actif: Boolean, taille: Dp = 18.dp) {
    val tokens = LocalTokens.current
    Box(
        Modifier.size(taille).clip(CircleShape)
            .background(if (actif) tokens.accent else Color.Transparent)
            .border(1.5.dp, if (actif) tokens.accent else tokens.textMuted, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (actif) Icone(Icones.valide, tint = tokens.bg, taille = taille * 0.62f)
    }
}

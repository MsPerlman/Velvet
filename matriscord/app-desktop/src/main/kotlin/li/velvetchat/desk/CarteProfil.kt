package li.velvetchat.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.app.ui.*
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.ProfilEtendu
import li.velvetchat.core.model.TagServeur

/**
 * the pieces of the desktop's profile cards, shared by your own popover, a member's card and the
 * edit profile preview. geometry measured off the references, colours from [couleursProfil]
 */

/**
 * banner, avatar ringed in the card's background, status dot, status bubble
 *
 * [decalage] is how far the avatar lifts into the banner (44 for an 80 avatar in the popover, 60
 * for 120 in the preview). the bubble is centred 15 under the banner's bottom, 8 from the ring
 */
@Composable
internal fun EnteteCarteProfil(
    userId: String,
    nom: String?,
    avatarUrl: String?,
    etendu: ProfilEtendu?,
    couleurs: CouleursProfil,
    presence: Presence,
    message: String?,
    banniere: Dp,
    avatar: Dp,
    anneau: Dp,
    decalage: Dp,
    pastille: Dp,
    silence: Boolean = false,
    gauche: Dp = 16.dp,
    boutons: (@Composable RowScope.() -> Unit)? = null,
) {
    val hautAvatar = banniere - decalage
    val cotePastille = pastille + anneau * 1.6f
    Box(Modifier.fillMaxWidth().height(hautAvatar + avatar + anneau)) {
        // with no image, the banner takes the theme's top colour, like on the reference
        Box(Modifier.fillMaxWidth().height(banniere).background(couleurs.teinteBanniere)) {
            etendu?.banniere?.let { EmojiEnLigne(it, contentScale = ContentScale.Crop) }
            boutons?.let {
                Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp), content = it)
            }
        }
        // the ring first and the avatar over it rather than clipped: the decoration overhangs the
        // circle
        Box(
            Modifier.offset(x = gauche - anneau, y = hautAvatar - anneau).size(avatar + anneau * 2)
                .clip(CircleShape).background(couleurs.fondHaut),
        )
        Box(Modifier.offset(x = gauche, y = hautAvatar)) {
            // the status is placed by the avatar itself, in a cut out notch, like the reference
            Avatar(
                userId = userId,
                displayName = nom,
                avatarUrl = avatarUrl,
                taille = avatar,
                decoration = etendu?.decoration,
                presence = presence,
                silence = silence,
            )
        }
        message?.takeIf { it.isNotBlank() }?.let {
            BulleProfil(it, couleurs, Modifier.centreEn(x = gauche + avatar + anneau + 8.dp, y = banniere + 15.dp))
        }
    }
}

/** places the content with its left edge at [x] and its vertical centre at [y] */
private fun Modifier.centreEn(x: Dp, y: Dp) = layout { mesurable, contraintes ->
    val p = mesurable.measure(contraintes.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place(x.roundToPx(), y.roundToPx() - p.height / 2) }
}

/**
 * the thought bubble: white on a light theme, with a small circle and a bump top left. a status
 * made only of emoji takes the reference's square bubble (57 by 59, emoji about 32), and a sentence
 * takes the one line pill (35 tall)
 */
@Composable
private fun BulleProfil(message: String, couleurs: CouleursProfil, modifier: Modifier) {
    val emojisSeuls = remember(message) {
        message.replace(Regex(":[A-Za-z0-9_+-]+:"), "").none { it.isLetterOrDigit() } && message.length <= 24
    }
    Box(modifier) {
        Box(Modifier.offset(x = (-2).dp, y = (-12).dp).size(8.dp).clip(CircleShape).background(couleurs.bulle))
        Box(Modifier.offset(x = 10.dp, y = (-6).dp).size(18.dp).clip(CircleShape).background(couleurs.bulle))
        if (emojisSeuls) {
            Box(
                Modifier.widthIn(min = 57.dp).height(59.dp).clip(RoundedCornerShape(15.dp))
                    .background(couleurs.bulle).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { ApercuAvecEmotes(texte = message, couleur = couleurs.texteBulle, taille = 30.sp) }
        } else {
            Box(
                Modifier.widthIn(max = 170.dp).height(35.dp).clip(RoundedCornerShape(18.dp))
                    .background(couleurs.bulle).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) { ApercuAvecEmotes(texte = message, couleur = couleurs.texteBulle, taille = 14.sp) }
        }
    }
}

/** the nickname: 20 bold, in the profile's gradient when there is one */
@Composable
internal fun NomCarteProfil(nom: String, etendu: ProfilEtendu?, couleurs: CouleursProfil, taille: Int = 20) {
    Text(
        nom,
        color = couleurs.texteFort,
        style = TextStyle(brush = degradePseudo(etendu)),
        fontSize = taille.sp,
        lineHeight = (taille + 2).sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "id • pronouns" then the tag chip (22 tall), with a chevron on your own profile */
@Composable
internal fun LigneIdentifiantProfil(
    userId: String,
    etendu: ProfilEtendu?,
    couleurs: CouleursProfil,
    chevronTag: Boolean = false,
    /** the chevron was decorative: clicking the chip now opens the tag picker */
    onClicTag: (() -> Unit)? = null,
) {
    Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            listOfNotNull(
                userId.removePrefix("@").substringBefore(':'),
                etendu?.pronoms?.takeIf { it.isNotBlank() },
            ).joinToString("  •  "),
            color = couleurs.texteFort,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        tagDe(etendu)?.let { tag ->
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.height(22.dp).clip(RoundedCornerShape(6.dp)).background(couleurs.pastille)
                    .let { m -> onClicTag?.let { m.curseurMain().clickable(onClick = it) } ?: m }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                    if (tag.emoji.startsWith("mxc://")) {
                        EmojiEnLigne(tag.emoji)
                    } else {
                        // the emoji font: without it the glyph comes out as an empty square
                        Text(tag.emoji, fontSize = 12.sp, lineHeight = 12.sp, fontFamily = LocalPoliceEmoji.current)
                    }
                }
                Spacer(Modifier.width(3.dp))
                Text(tag.texte.take(TagServeur.LONGUEUR_MAX), color = couleurs.texteFort, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (chevronTag) {
                    Spacer(Modifier.width(4.dp))
                    Icone(Icones.chevronBas, tint = couleurs.texteFort, taille = 12.dp, description = null)
                }
            }
        }
    }
}

/** an action block: radius 8, the theme's light veil */
@Composable
internal fun BlocCarte(couleurs: CouleursProfil, contenu: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(couleurs.bloc), content = contenu)
}

@Composable
internal fun FiletCarte(couleurs: CouleursProfil) {
    Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(couleurs.filet))
}

/**
 * a block row: 49 tall, a 20 icon 14 from the edge, a 14 label 6 from the icon, a chevron on the
 * right
 */
@Composable
internal fun LigneCarte(
    libelle: String,
    couleurs: CouleursProfil,
    chevron: Boolean = false,
    icone: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val survol = remember { MutableInteractionSource() }
    val survolee by survol.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(49.dp)
            .background(if (survolee) couleurs.filet else Color.Transparent)
            .hoverable(survol)
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { icone() }
        Spacer(Modifier.width(6.dp))
        // normal weight and a slight tightening: our font is wider than the reference's, where the
        // longest label fits the row whole
        Text(
            libelle,
            color = couleurs.texteDoux,
            fontSize = 14.sp,
            letterSpacing = (-0.2).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (chevron) Icone(Icones.chevronDroit, tint = couleurs.texteDoux, taille = 16.dp, description = null)
    }
}

/** the reference's id icon: a filled rounded square with the letters knocked out */
@Composable
internal fun IconeId(couleurs: CouleursProfil) {
    Box(
        Modifier.size(width = 17.dp, height = 15.dp).clip(RoundedCornerShape(3.dp)).background(couleurs.texteDoux),
        contentAlignment = Alignment.Center,
    ) {
        Text("ID", color = couleurs.fondHaut, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** a round button on a member's banner: 28, black veil, white icon */
@Composable
internal fun BoutonBanniere(icone: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icone(icone, tint = Color.White, taille = 16.dp, description = description) }
}

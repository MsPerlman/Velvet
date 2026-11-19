package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.ProfilEtendu

/**
 * choosing an appearance: banner and gradient
 *
 * gradients are customisable rather than derived, and the carrier is the extended profiles msc,
 * measured active and stable on the server
 *
 * a palette and not a free colour picker. picking from twelve pairs takes one gesture and gives a
 * result that holds up, while two colour wheels mostly give you the chance to build an unreadable
 * gradient the name no longer reads on. the same reasoning as the role tiers, prepared values
 * rather than a freedom that turns on whoever exercises it
 *
 * "auto" is offered first and puts back the gradient derived from the id, so you can return to what
 * the app picked, which a customisation rarely offers
 */
@Composable
fun FeuilleApparence(
    userId: String,
    etendu: ProfilEtendu,
    onCouleurs: (haute: String?, basse: String?) -> Unit,
    onBanniere: () -> Unit,
    onRetirerBanniere: () -> Unit,
    /**
     * the catalogue, across all servers (see `MatrixEngine.toutesLesDecorations`)
     *
     * it used to be limited to the open server, so you had to guess which one published what, and a
     * direct conversation, belonging to no server, offered none at all
     */
    inventaire: List<li.velvetchat.core.model.DecorationAvatar> = emptyList(),
    /** pick a decoration (`mxc`), or null to remove it */
    onDecoration: (String?) -> Unit = {},
    /**
     * import your own image as a decoration. null when importing is not available
     *
     * this is what makes a decoration genuinely personal: without it you can only wear what an
     * admin chose to publish, and "profile decoration" then names a server's catalogue rather than
     * something you choose
     */
    onImporterDecoration: (() -> Unit)? = null,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current

    FeuilleBasse(onFermer = onFermer) {
        Text(
            t.apparenceDuProfil,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espS),
        )
        Text(
            t.profilVisibleExplication,
            color = tokens.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = 14.dp),
        )

        Text(
            t.degrade,
            color = tokens.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = tokens.espXl, bottom = tokens.espS),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // the derived fallback, offered as a choice in its own right
                PastilleDegrade(
                    pinceau = degradeDe(userId, ProfilEtendu()),
                    actif = etendu.couleurHaute == null,
                    libelle = t.automatique,
                ) { onCouleurs(null, null) }
            }
            items(PALETTE) { couple ->
                PastilleDegrade(
                    pinceau = Brush.verticalGradient(
                        listOf(couple.first.toColor(), couple.second.toColor())
                    ),
                    actif = etendu.couleurHaute == couple.first,
                ) { onCouleurs(couple.first, couple.second) }
            }
        }

        Text(
            t.banniere,
            color = tokens.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = tokens.espXl, top = 22.dp, bottom = tokens.espS),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espXl),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f)) {
                BoutonPlein(
                    if (etendu.banniere == null) t.choisirUneImage else t.changer,
                    onClick = onBanniere,
                )
            }
            if (etendu.banniere != null) {
                Box(Modifier.weight(1f)) {
                    BoutonPlein(t.retirer, onClick = onRetirerBanniere)
                }
            }
        }
        Text(
            // the same warning as for the profile picture, in the same place: at the moment of the
            // gesture, not in a help page nobody opens
            t.banniereEnClair,
            color = tokens.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, top = 10.dp, bottom = 6.dp),
        )

        // the section disappears when there is neither a catalogue nor an import: a "decoration"
        // heading followed by a single "none" cell would announce a possibility that does not exist
        // and send people looking for the others. once you can import your own, the possibility
        // exists and the section stays
        if (inventaire.isNotEmpty() || onImporterDecoration != null) {
            Text(
                t.decoration,
                color = tokens.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = tokens.espXl, top = 22.dp, bottom = tokens.espS),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = tokens.espXl),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // "none" first: removing has to be as reachable as choosing, and to be where the
                // eye starts
                ChoixDecoration(
                    userId = userId,
                    decoration = null,
                    choisie = etendu.decoration == null,
                    onClick = { onDecoration(null) },
                )
                inventaire.forEach { d ->
                    ChoixDecoration(
                        userId = userId,
                        decoration = d.mxc,
                        choisie = etendu.decoration == d.mxc,
                        onClick = { onDecoration(d.mxc) },
                    )
                }
                // the worn decoration that is in no catalogue, imported yourself or published by a
                // server you have since left. without this cell it would show on the avatar with no
                // cell appearing ticked
                val portee = etendu.decoration
                if (portee != null && inventaire.none { it.mxc == portee }) {
                    ChoixDecoration(
                        userId = userId,
                        decoration = portee,
                        choisie = true,
                        onClick = {},
                    )
                }
                onImporterDecoration?.let { importer ->
                    Box(
                        Modifier.size(64.dp).clip(CircleShape)
                            .background(tokens.surfaceRaised)
                            .curseurMain().clickable(onClick = importer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icone(
                            Icones.ajouter,
                            tint = tokens.textSecondary,
                            taille = 22.dp,
                            description = t.importerUneDecoration,
                        )
                    }
                }
            }
            Spacer(Modifier.height(tokens.espM))
        }
    }
}

/**
 * one cell of the picker: your own avatar wearing the decoration, and not the ring on its own
 *
 * a ring shown empty does not say what it will give, since the relation between the circle and the
 * face is exactly what you are choosing. so we show the result and not the ingredient
 */
@Composable
private fun ChoixDecoration(
    userId: String,
    decoration: String?,
    choisie: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Box(
        Modifier.size(64.dp)
            .clip(CircleShape)
            .background(if (choisie) tokens.accent.copy(alpha = 0.25f) else Color.Transparent)
            .curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Avatar(
            userId = userId,
            displayName = null,
            avatarUrl = null,
            taille = 40.dp,
            decoration = decoration,
        )
    }
}

@Composable
private fun PastilleDegrade(
    pinceau: Brush,
    actif: Boolean,
    libelle: String? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Box(
        Modifier.size(56.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(pinceau)
            .curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // the tick over the gradient rather than a frame around it: an accent frame on an already
        // coloured tile adds a third colour to look at, and you stop seeing which one is selected
        if (actif) {
            Coche(true, taille = 22.dp)
        } else {
            libelle?.let {
                Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * twelve prepared pairs
 *
 * two neighbouring hues in each pair, never opposite ones: across the wheel a gradient becomes a
 * rainbow and stops working as a background, the same rule as for the derived one. and a lightness
 * that descends from top to bottom, so the name laid over it stays readable in every case
 */
private val PALETTE = listOf(
    "#5865F2" to "#2F3799",
    "#7C4DFF" to "#4527A0",
    "#B84DFF" to "#6A1B9A",
    "#EC407A" to "#880E4F",
    "#EF5350" to "#8E1B18",
    "#FF7043" to "#A0392A",
    "#FFA726" to "#A96A12",
    "#66BB6A" to "#2E6930",
    "#26A69A" to "#12665E",
    "#29B6F6" to "#0D5E86",
    "#5C6BC0" to "#2A3168",
    "#78909C" to "#3E4E56",
)

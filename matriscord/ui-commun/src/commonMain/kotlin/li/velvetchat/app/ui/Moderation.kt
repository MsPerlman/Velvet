package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.ModerationResult
import li.velvetchat.core.model.RoleTier

/**
 * every server member's role colour, ready to use
 *
 * computed once in `Shell` from the members and the tiers, then read everywhere. the timeline only
 * has sender ids, it could not find a power level on its own, and a ready made map keeps it from
 * asking the server just to colour a nickname
 */
val LocalRoleColors = staticCompositionLocalOf<Map<String, Color>> { emptyMap() }

/**
 * everybody's tier, and not only their colour
 *
 * `LocalRoleColors` only carried the hue, because colouring a nickname was all we did with it. the
 * badge needs the rest, the tier's name and its glyph, and going to fetch that from the timeline
 * would mean asking the server on every row, which is exactly what the colour map exists to avoid
 *
 * the two maps coexist rather than being merged: the colour is read on every timeline row, the tier
 * only where a badge is drawn. keeping them apart keeps the hot read as dumb as it was
 */
val LocalRoleTiers = staticCompositionLocalOf<Map<String, li.velvetchat.core.model.RoleTier>> { emptyMap() }

/**
 * the tier that applies to a power level
 *
 * the first one reached on the way down, the tiers coming back highest to lowest from the engine.
 * that is what makes an in between level, 75 say, inherit the tier below instead of having none: a
 * threshold table describes bands, not exact values
 */
fun palierDe(powerLevel: Long?, tiers: List<RoleTier>): RoleTier? =
    tiers.firstOrNull { (powerLevel ?: 0L) >= it.power }

/**
 * the moderation actions on a person
 *
 * three levels of severity, visually separated in this order: change the role, mute, kick or ban.
 * the order is not decoration, it descends towards the irreversible, and the last two are red
 *
 * every destructive action asks for a confirmation that changes the label, like deleting a message:
 * the learned gesture, tap there, is no longer enough
 *
 * the result is shown, never swallowed. a moderation action covers the space and all of its
 * channels, and it can succeed in some and fail in others. saying "done" when half of it failed
 * would be the worst possible feedback, because nothing afterwards would contradict it, the person
 * would look kicked and would carry on writing
 */
@Composable
fun FeuilleModeration(
    membre: Member,
    tiers: List<RoleTier>,
    monPouvoir: Long,
    /**
     * the server's decorative roles, and the ones this person already wears
     *
     * assigning them lives here and not on a separate screen, because here is where you already
     * came to act on somebody. the complaint was that you could create roles but not assign them,
     * and the missing gesture is one switch per role, where you are looking at the person
     */
    decoratifs: List<li.velvetchat.core.model.RoleDecoratif> = emptyList(),
    sesDecoratifs: List<String> = emptyList(),
    /** null when you have no power to write the server's state, and the section then disappears */
    onBasculerDecoratif: (suspend (String, Boolean) -> Unit)? = null,
    onPromouvoir: suspend (Long) -> ModerationResult,
    onExclure: suspend () -> ModerationResult,
    onBannir: suspend () -> ModerationResult,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    var confirme by remember { mutableStateOf<String?>(null) }
    var compte by remember { mutableStateOf<String?>(null) }

    val pouvoir = membre.role?.powerLevel ?: 0L
    val palier = palierDe(pouvoir, tiers)

    // we do not offer to act on somebody with at least as much power as you. the server would
    // refuse, and offering an action doomed to fail is worse than not offering it
    val autorise = pouvoir < monPouvoir

    fun lancer(action: suspend () -> ModerationResult) {
        scope.launch {
            runCatching { action() }
                .onSuccess { resultat ->
                    compte = if (resultat.isComplete) {
                        t.appliqueDans(resultat.applied.size)
                    } else {
                        t.appliqueRefuse(
                            resultat.applied.size,
                            resultat.refused.size,
                            resultat.refused.values.first(),
                        )
                    }
                }
                .onFailure { compte = it.message ?: t.echec }
            confirme = null
        }
    }

    FeuilleBasse(onFermer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(membre.userId, membre.displayName, membre.avatarUrl, 44.dp)
            Column(Modifier.padding(start = tokens.espM).weight(1f)) {
                Text(
                    membre.displayName ?: membre.userId,
                    color = palier?.colorArgb?.let { Color(it) } ?: tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Text(
                    listOfNotNull(palier?.name, t.pouvoirNiveau(pouvoir)).joinToString(" • "),
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                )
            }
            palier?.icon?.let { Text(it, fontSize = 16.sp) }
        }

        if (!autorise) {
            Text(
                t.pouvoirSuperieur,
                color = tokens.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = tokens.espM),
            )
            return@FeuilleBasse
        }

        // the editable tiers go up to and including your own, where the previous version stopped
        // just below it, with a comment saying the protocol would refuse, which was wrong. the spec
        // says the opposite: you can raise somebody to your own level, you cannot raise them above
        // it. synapse does exactly that
        //
        // what the mistake cost, and it is the symptom that got reported: on a small server the
        // owner is at 100 and the table carries a tier at 100. that tier, the only one anybody
        // really wants to hand out, the one that shares the administration, was therefore the only
        // one that could never be given. "you can create roles but not assign them" describes
        // precisely this filter
        tiers.filter { it.power <= monPouvoir && it.power != pouvoir }.forEach { cible ->
            LigneModeration(
                t.nommerQui(cible.name),
                couleur = cible.colorArgb?.let { Color(it) } ?: tokens.textPrimary,
            ) { lancer { onPromouvoir(cible.power) } }
        }

        // the decorative roles, as switches. ticked, unticked, no confirmation: they grant no
        // rights, and going on and coming off in one gesture is exactly their nature. confirming a
        // colour would be absurd
        //
        // optimistic on screen: the chip changes at once, and the reread after the write corrects
        // it if the server refused. waiting for the round trip would make the gesture feel
        // doubtful, and this is display state with no consequence
        if (onBasculerDecoratif != null && decoratifs.isNotEmpty()) {
            Separateur()
            var portes by remember(membre.userId, sesDecoratifs) {
                mutableStateOf(sesDecoratifs.toSet())
            }
            Text(
                t.roles,
                color = tokens.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 2.dp),
            )
            decoratifs.forEach { role ->
                val porte = role.id in portes
                Row(
                    Modifier.fillMaxWidth()
                        .curseurMain().clickable {
                            portes = if (porte) portes - role.id else portes + role.id
                            scope.launch {
                                runCatching { onBasculerDecoratif(role.id, !porte) }
                                    .onFailure {
                                        // put back as it was, and said out loud: a refused
                                        // assignment left on screen is a durable lie
                                        portes = if (porte) portes + role.id else portes - role.id
                                        compte = it.message ?: t.roleNonEnregistre
                                    }
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(11.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(role.colorArgb?.let { Color(it) } ?: tokens.textMuted)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        role.nom,
                        color = if (porte) tokens.textPrimary else tokens.textSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    // a tick, and nothing when the role is not worn. an empty box drawn next to
                    // every row would be a dozen rectangles to scan
                    if (porte) Icone(Icones.valide, tint = tokens.accent, taille = 18.dp)
                }
            }
        }

        Separateur()

        // muting is a negative power level and not a separate action: you push the person below
        // `events_default` rather than raising the room's threshold, which would mute everybody
        // under it
        if (pouvoir >= 0) {
            LigneModeration(t.rendreMuet, couleur = tokens.warning) { lancer { onPromouvoir(-1L) } }
        } else {
            LigneModeration(t.rendreLaParole, couleur = tokens.colors.success.toColor()) {
                lancer { onPromouvoir(0L) }
            }
        }

        Separateur()

        when (confirme) {
            "exclure" -> {
                LigneModeration(t.confirmerLExclusion, couleur = tokens.danger) {
                    lancer { onExclure() }
                }
                LigneModeration(t.annuler) { confirme = null }
            }
            "bannir" -> {
                LigneModeration(t.confirmerLeBannissement, couleur = tokens.danger) {
                    lancer { onBannir() }
                }
                LigneModeration(t.annuler) { confirme = null }
            }
            else -> {
                LigneModeration(t.exclureDuServeur, couleur = tokens.danger) {
                    confirme = "exclure"
                }
                LigneModeration(t.bannirDuServeur, couleur = tokens.danger) {
                    confirme = "bannir"
                }
            }
        }

        compte?.let {
            Text(
                it,
                color = tokens.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun LigneModeration(
    libelle: String,
    couleur: Color? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Text(
        libelle,
        color = couleur ?: tokens.textPrimary,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().curseurMain().clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    )
}

@Composable
private fun Separateur() {
    val tokens = LocalTokens.current
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = tokens.espXs)
            .background(tokens.border)
            .padding(top = 1.dp)
    )
}

/**
 * the server's role tiers, read only
 *
 * read only for now, and said out loud: the table is written by `:verif-bureau:verifyRoles`
 * or by another client. editing it here would need a colour picker and a glyph chooser, two jobs
 * that add nothing while the default table does the job, whereas seeing the tiers already says who
 * is in charge
 */
@Composable
fun PanneauRoles(
    tiers: List<RoleTier>,
    modifiable: Boolean = false,
    onEnregistrer: (suspend (List<RoleTier>) -> Unit)? = null,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()

    // edited locally, published in one go. a tier has no independent existence protocol side: the
    // whole table is one state event, written entirely or not at all. changing one row at a time
    // would send that many complete writes, and two quick edits would overwrite each other
    var brouillon by remember(tiers) { mutableStateOf(tiers) }
    var enEdition by remember { mutableStateOf<RoleTier?>(null) }
    var creation by remember { mutableStateOf(false) }
    var enregistrement by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val modifie = brouillon != tiers

    if (creation || enEdition != null) {
        FeuillePalier(
            palier = enEdition,
            dejaPris = brouillon.map { it.power }.toSet() - (enEdition?.power ?: -1L),
            onValider = { nouveau ->
                brouillon = (brouillon.filterNot { it.power == (enEdition?.power ?: nouveau.power) } +
                    nouveau).sortedByDescending { it.power }
                enEdition = null; creation = false
            },
            onSupprimer = enEdition?.let { cible ->
                { brouillon = brouillon.filterNot { it.power == cible.power }; enEdition = null }
            },
            onFermer = { enEdition = null; creation = false },
        )
    }

    if (tiers.isEmpty() && !modifiable) {
        Text(
            t.aucunPalierDefini,
            color = tokens.textMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(tokens.espXl),
        )
        return
    }

    Column(Modifier.padding(vertical = tokens.espS)) {
        if (brouillon.isEmpty()) {
            Text(
                t.aucunPalierDefini,
                color = tokens.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(tokens.espXl),
            )
        }
        brouillon.forEach { tier ->
            Row(
                Modifier.fillMaxWidth()
                    .let { if (modifiable) it.curseurMain().clickable { enEdition = tier } else it }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.padding(end = tokens.espM)
                        .background(
                            tier.colorArgb?.let { Color(it) } ?: tokens.textMuted,
                            RoundedCornerShape(2.dp),
                        )
                        .padding(horizontal = 2.dp, vertical = tokens.espS)
                )
                Text(
                    tier.name,
                    color = tier.colorArgb?.let { Color(it) } ?: tokens.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                tier.icon?.let { Text(it, fontSize = 15.sp) }
                Text(
                    "  ${tier.power}",
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                )
            }
        }

        // everything below is hidden from anybody without the power to write the room's state.
        // offering a button the server will refuse is worse than offering nothing
        if (!modifiable) return@Column

        Text(
            t.plusNouveauPalier,
            color = tokens.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().curseurMain().clickable { creation = true }
                .padding(horizontal = 18.dp, vertical = tokens.espM),
        )

        Text(
            t.palierExplication,
            color = tokens.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 18.dp),
        )

        erreur?.let {
            Text(
                it,
                color = tokens.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = tokens.espS),
            )
        }

        // the button only appears once something really changed: `brouillon` restarts from `tiers`
        // on every reread, so the equality says exactly "there is something to publish"
        if (modifie) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = tokens.espM)) {
                Text(
                    if (enregistrement) t.publicationEnCours else t.publierLesPaliers,
                    color = if (enregistrement) tokens.textMuted else tokens.bg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(
                            if (enregistrement) tokens.surfaceRaised else tokens.accent,
                            RoundedCornerShape(tokens.radiusMedium),
                        )
                        .curseurMain().clickable(enabled = !enregistrement) {
                            val publier = onEnregistrer ?: return@clickable
                            enregistrement = true
                            erreur = null
                            scope.launch {
                                runCatching { publier(brouillon) }
                                    .onFailure {
                                        erreur = it.message ?: t.publicationRefusee
                                        enregistrement = false
                                    }
                                    .onSuccess { enregistrement = false }
                            }
                        }
                        .padding(horizontal = tokens.espL, vertical = 10.dp),
                )
                Text(
                    t.annuler,
                    color = tokens.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.curseurMain().clickable { brouillon = tiers; erreur = null }
                        .padding(horizontal = tokens.espL, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * editing one tier: name, colour, glyph, level
 *
 * the level is the key and not the name, since it is what ties the tier to a member's real power.
 * two tiers at the same level would be indistinguishable on screen, hence the refusal
 */
@Composable
private fun FeuillePalier(
    palier: RoleTier?,
    dejaPris: Set<Long>,
    onValider: (RoleTier) -> Unit,
    onSupprimer: (() -> Unit)?,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var nom by remember { mutableStateOf(palier?.name.orEmpty()) }
    var glyphe by remember { mutableStateOf(palier?.icon.orEmpty()) }
    var niveau by remember { mutableStateOf(palier?.power?.toString() ?: "50") }
    var couleur by remember { mutableStateOf(palier?.colorArgb) }

    val niveauValide = niveau.toLongOrNull()
    val conflit = niveauValide != null && niveauValide in dejaPris
    val actif = nom.isNotBlank() && niveauValide != null && !conflit

    FeuilleBasse(onFermer = onFermer) {
        Text(
            if (palier == null) t.nouveauPalier else t.modifierNomme(palier.name),
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = 10.dp),
        )

        ChampPalier(t.nom, nom, t.moderateur) { nom = it }
        ChampPalier(t.glypheFacultatif, glyphe, "👑") { glyphe = it.take(2) }
        ChampPalier(t.niveauDePouvoir, niveau, "50") { niveau = it.filter(Char::isDigit).take(4) }

        if (conflit) {
            Text(
                t.palierOccupe(niveauValide),
                color = tokens.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, top = tokens.espXs),
            )
        }

        Text(
            t.couleur,
            color = tokens.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = tokens.espXl, top = 14.dp, bottom = 6.dp),
        )
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = tokens.espL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            COULEURS_PALIER.forEach { argb ->
                Box(
                    Modifier.padding(tokens.espXs).size(32.dp)
                        .background(Color(argb), RoundedCornerShape(16.dp))
                        .curseurMain().clickable { couleur = argb },
                    contentAlignment = Alignment.Center,
                ) {
                    if (couleur == argb) {
                        Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        Row(Modifier.padding(tokens.espXl), verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.enregistrer,
                color = if (actif) tokens.bg else tokens.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .background(
                        if (actif) tokens.accent else tokens.surfaceRaised,
                        RoundedCornerShape(tokens.radiusMedium),
                    )
                    .curseurMain().clickable(enabled = actif) {
                        onValider(
                            RoleTier(
                                power = niveauValide ?: 0L,
                                name = nom.trim(),
                                colorArgb = couleur,
                                icon = glyphe.ifBlank { null },
                            )
                        )
                    }
                    .padding(horizontal = tokens.espL, vertical = 10.dp),
            )
            onSupprimer?.let {
                Text(
                    t.supprimer,
                    color = tokens.danger,
                    fontSize = 14.sp,
                    modifier = Modifier.curseurMain().clickable(onClick = it)
                        .padding(horizontal = tokens.espL, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ChampPalier(
    libelle: String,
    valeur: String,
    exemple: String,
    onChange: (String) -> Unit,
) {
    val tokens = LocalTokens.current
    Text(
        libelle,
        color = tokens.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = tokens.espXl, top = tokens.espM, bottom = 6.dp),
    )
    Box(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espXl)
            .background(tokens.surfaceRaised, RoundedCornerShape(tokens.radiusMedium))
            .padding(horizontal = tokens.espM, vertical = tokens.espM)
    ) {
        if (valeur.isEmpty()) Text(exemple, color = tokens.textMuted, fontSize = 14.sp)
        androidx.compose.foundation.text.BasicTextField(
            value = valeur,
            onValueChange = onChange,
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                color = tokens.textPrimary,
                fontSize = 14.sp,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * the same hues as the profile palette, the first of each pair
 *
 * a palette and not a free picker, for the same reason as elsewhere: a dozen choices are made at a
 * glance, and none of them gives a role that is unreadable on the dark background
 */
private val COULEURS_PALIER = listOf(
    0xFF5865F2.toInt(), 0xFF7C4DFF.toInt(), 0xFFB84DFF.toInt(), 0xFFEC407A.toInt(),
    0xFFEF5350.toInt(), 0xFFFF7043.toInt(), 0xFFFFA726.toInt(), 0xFF66BB6A.toInt(),
    0xFF26A69A.toInt(), 0xFF29B6F6.toInt(), 0xFF5C6BC0.toInt(), 0xFF78909C.toInt(),
)


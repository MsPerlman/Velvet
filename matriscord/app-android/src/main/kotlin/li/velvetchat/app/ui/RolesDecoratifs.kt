package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import li.velvetchat.design.contraste
import li.velvetchat.design.contrasteSuffisant
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.model.Decorations
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.RoleDecoratif

/**
 * a server's decorative roles, modelled on the reference's "server roles" screen
 *
 * what was taken from the screenshots, and why each item is there:
 *
 *     - the explanation sentence at the top, centred. an empty roles screen does not say what a role is,
 *       and the reference says it in one line rather than leaving it to be guessed
 *     - the "roles, N" count above the list, which tells the section title from the screen title
 *     - the round tinted dot at the head of the row, carrying the role's colour. it is the only place a
 *       role's colour shows before giving it to somebody
 *     - "N members" as a subtitle, which answers the question you ask looking at a list of roles, which
 *       ones are in use
 *
 * what was not taken, deliberately: the `@everyone` card (we have no per role permissions, that
 * being the tiers' job, elsewhere) and "reorder". the order does decide the nickname's colour here
 * too, but a drag and drop would be coded blind on a one item list, so it joins the todo, along
 * with reordering servers
 *
 * @param creation driven by the header's "+", which lives in the top bar and not in the list. see
 *   `EcranParametresServeur`
 */
@Composable
fun PanneauRolesDecoratifs(
    decorations: Decorations,
    membres: List<Member>,
    modifiable: Boolean = false,
    creation: Boolean = false,
    onCreationTerminee: () -> Unit = {},
    onEnregistrer: (suspend (Decorations) -> Unit)? = null,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    var enEdition by remember { mutableStateOf<RoleDecoratif?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }

    /**
     * publishes, and rereads what the caller will render rather than keeping a draft
     *
     * the old version edited a local copy and published the lot at the end, with a "publish"
     * button. that was right for the tiers, where you touch several rows in a row. it is not right
     * here: the reference saves on every gesture, and a created role that only appears after a
     * second button reads as a failure
     */
    fun publier(nouvelles: Decorations, ensuite: () -> Unit = {}) {
        val ecrire = onEnregistrer ?: return
        erreur = null
        scope.launch {
            runCatching { ecrire(nouvelles) }
                .onSuccess { ensuite() }
                .onFailure { erreur = it.message ?: t.publicationRefusee }
        }
    }

    if (creation) {
        PleinEcran {
        AssistantRole(
            membres = membres,
            onFermer = onCreationTerminee,
            onCreer = { role, porteurs ->
                val attributions = decorations.parMembre.toMutableMap()
                porteurs.forEach { id ->
                    attributions[id] = (attributions[id].orEmpty() + role.id).distinct()
                }
                publier(
                    decorations.copy(
                        roles = decorations.roles + role,
                        parMembre = attributions,
                    ),
                    ensuite = onCreationTerminee,
                )
            },
        )
        }
        return
    }

    enEdition?.let { cible ->
        PleinEcran {
        EcranRole(
            role = cible,
            onValider = { modifie ->
                publier(
                    decorations.copy(
                        roles = decorations.roles.map { if (it.id == modifie.id) modifie else it },
                    ),
                    ensuite = { enEdition = null },
                )
            },
            onSupprimer = {
                publier(
                    // the deleted role's assignments go with it. the engine already filters unknown
                    // ids, but leaving them here would grow the state event on every deletion
                    decorations.copy(
                        roles = decorations.roles.filterNot { it.id == cible.id },
                        parMembre = decorations.parMembre.mapValues { (_, ids) ->
                            ids - cible.id
                        },
                    ),
                    ensuite = { enEdition = null },
                )
            },
            onFermer = { enEdition = null },
        )
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            t.rolesDecoratifsExplication,
            color = tokens.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
        )

        if (decorations.roles.isEmpty()) {
            Text(
                if (modifiable) {
                    t.aucunRoleAide
                } else {
                    t.aucunRoleDecoratif
                },
                color = tokens.textMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
            )
        } else {
            Text(
                t.rolesAvecNombre(decorations.roles.size),
                color = tokens.textSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = tokens.espXl, bottom = tokens.espS),
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espM)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surface)
            ) {
                decorations.roles.forEachIndexed { index, role ->
                    if (index > 0) {
                        Box(
                            Modifier.fillMaxWidth().padding(start = 68.dp).height(1.dp)
                                .background(tokens.border)
                        )
                    }
                    LigneRole(
                        role = role,
                        porteurs = decorations.parMembre.count { role.id in it.value },
                        modifiable = modifiable,
                    ) { enEdition = role }
                }
            }
        }

        erreur?.let {
            Text(
                it,
                color = tokens.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espM),
            )
        }

        Text(
            t.roleDistributionAide,
            color = tokens.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espL),
        )
    }
}

/** a row of the list: tinted dot, name, wearer count, chevron */
@Composable
private fun LigneRole(
    role: RoleDecoratif,
    porteurs: Int,
    modifiable: Boolean,
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val couleur = role.colorArgb?.let { Color(it) } ?: tokens.textMuted
    Row(
        Modifier.fillMaxWidth()
            .let { if (modifiable) it.clickable(onClick = onClick) else it }
            .padding(horizontal = tokens.espL, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the reference's dot: a disc filled with the role's colour, carrying a group glyph. for
        // want of a shield in the icon set it is `membres`, and the sense holds: a role is a group
        // of people
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(couleur),
            contentAlignment = Alignment.Center,
        ) {
            Icone(Icones.membres, tint = Color.White, taille = 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                role.nom,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                t.membresCompte(porteurs),
                color = tokens.textMuted,
                fontSize = 13.sp,
            )
        }
        if (modifiable) Icone(Icones.suite, tint = tokens.textMuted, taille = 16.dp)
    }
}

/**
 * the creation wizard, in two steps, where the reference has three
 *
 * theirs slots in the permissions, which our decorative roles do not have: matrix permissions are a
 * power integer, and the tier table is what dresses it. writing "step 1 of 3" to offer only two
 * would be copying a shape without its content, so the counter tells the truth
 *
 * the second step exists for a precise reason: you could create roles but not assign them. creating
 * a role then having to open each person's card to hand it out is exactly what gave that
 * impression. it is now handed out straight away, and the card stays there for later changes
 */
@Composable
private fun AssistantRole(
    membres: List<Member>,
    onFermer: () -> Unit,
    onCreer: (RoleDecoratif, Set<String>) -> Unit,
) {
    val t = textes()
    var etape by remember { mutableStateOf(1) }
    var nom by remember { mutableStateOf("") }
    var couleur by remember { mutableStateOf<Int?>(null) }
    var porteurs by remember { mutableStateOf(emptySet<String>()) }

    // back steps back before closing: leaving the wizard from the second step would lose the name
    // and the colour already entered
    androidx.activity.compose.BackHandler {
        if (etape > 1) etape-- else onFermer()
    }

    if (etape == 1) {
        EcranRole(
            role = null,
            etape = t.etapeSur(1, 2),
            libelleAction = t.suivant,
            nomInitial = nom,
            couleurInitiale = couleur,
            onValider = { brouillon ->
                nom = brouillon.nom
                couleur = brouillon.colorArgb
                etape = 2
            },
            onSupprimer = null,
            onFermer = onFermer,
        )
    } else {
        EcranMembresDuRole(
            membres = membres,
            choisis = porteurs,
            couleur = couleur,
            onBasculer = { id -> porteurs = if (id in porteurs) porteurs - id else porteurs + id },
            onFermer = { etape = 1 },
            onValider = {
                onCreer(
                    RoleDecoratif(
                        // random and not derived from the name: it is the id the assignments carry,
                        // and a rename must not take the role away from those wearing it
                        id = java.util.UUID.randomUUID().toString().take(8),
                        nom = nom.trim(),
                        colorArgb = couleur,
                    ),
                    porteurs,
                )
            },
        )
    }
}

/**
 * a role's screen: its name, its colour
 *
 * used twice, as the wizard's first step and to edit an existing role. the same screen for both,
 * because it is the same thing to fill in, and only the step counter, the button's label and the
 * presence of "delete" change
 *
 * full screen and not a bottom sheet. the reference opens it full: a two field form plus a colour
 * picker coming up over it makes two stacked surfaces, and a sheet on a sheet no longer closes in
 * the right order
 */
@Composable
private fun EcranRole(
    role: RoleDecoratif?,
    etape: String? = null,
    libelleAction: String = textes().enregistrer,
    nomInitial: String = role?.nom.orEmpty(),
    couleurInitiale: Int? = role?.colorArgb,
    onValider: (RoleDecoratif) -> Unit,
    onSupprimer: (() -> Unit)?,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var nom by remember { mutableStateOf(nomInitial) }
    var couleur by remember { mutableStateOf(couleurInitiale) }
    var choixCouleur by remember { mutableStateOf(false) }
    var confirmeSuppression by remember { mutableStateOf(false) }
    val actif = nom.isNotBlank()

    androidx.activity.compose.BackHandler(onBack = onFermer)

    Column(Modifier.fillMaxSize().background(tokens.bg).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            etape?.let {
                Text(it, color = tokens.textSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Icone(
                Icones.fermer,
                tint = tokens.textPrimary,
                taille = 22.dp,
                description = t.fermer,
                modifier = Modifier.align(Alignment.CenterStart)
                    .clip(CircleShape).clickable(onClick = onFermer).padding(tokens.espL),
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                if (role == null) t.creerUnNouveauRole else t.modifierLeRole,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = tokens.espS),
            )
            Text(
                t.roleNomEtCouleur,
                color = tokens.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 28.dp, end = 28.dp, top = tokens.espS, bottom = 22.dp),
            )

            Row(Modifier.padding(start = tokens.espXl, bottom = 6.dp)) {
                Text(t.nomDuRole, color = tokens.textSecondary, fontSize = 13.sp)
                // the reference's red asterisk: it is the only required field, and the button at
                // the bottom stays inert while it is empty
                Text(" *", color = tokens.danger, fontSize = 13.sp)
            }
            // an outlined field and not a filled one: that is what the reference does, and the
            // outline says you can write in it where a solid background reads as a button
            Box(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espL)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .border(1.dp, tokens.border, RoundedCornerShape(tokens.radiusMedium))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (nom.isEmpty()) {
                    Text(t.nouveauRoleMinuscule, color = tokens.textMuted, fontSize = 16.sp)
                }
                BasicTextField(
                    value = nom,
                    onValueChange = { nom = it.take(60) },
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 16.sp),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                t.roleExemple,
                color = tokens.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, top = tokens.espS, bottom = tokens.espXl),
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espL)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surface)
                    .clickable { choixCouleur = true }
                    .padding(horizontal = tokens.espL, vertical = tokens.espL),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.couleurDuRole,
                    color = tokens.textPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(tokens.radiusSmall))
                        .background(couleur?.let { Color(it) } ?: tokens.surfaceRaised)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    couleur?.let { hexDe(it) } ?: "aucune",
                    color = tokens.textMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.width(6.dp))
                Icone(Icones.suite, tint = tokens.textMuted, taille = 16.dp)
            }
            Text(
                t.couleurDuPremierRole,
                color = tokens.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = tokens.espXl, vertical = 10.dp),
            )

            onSupprimer?.let { supprimer ->
                Text(
                    if (confirmeSuppression) t.confirmerLaSuppression else t.supprimerCeRole,
                    color = tokens.danger,
                    fontSize = 16.sp,
                    fontWeight = if (confirmeSuppression) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth()
                        // the label changes before acting: the learned gesture is no longer enough,
                        // as everywhere else for an irreversible action
                        .clickable {
                            if (confirmeSuppression) supprimer() else confirmeSuppression = true
                        }
                        .padding(horizontal = tokens.espXl, vertical = 18.dp),
                )
            }
        }

        // the full width button at the bottom, anchored: the reference puts it there, and it is
        // what makes it reachable by thumb whatever the form's length
        Text(
            libelleAction,
            color = if (actif) Color.White else tokens.textMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(tokens.espL)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(if (actif) tokens.accent else tokens.surfaceRaised)
                .clickable(enabled = actif) {
                    onValider(
                        RoleDecoratif(
                            id = role?.id ?: "",
                            nom = nom.trim(),
                            colorArgb = couleur,
                        )
                    )
                }
                .padding(vertical = tokens.espL),
        )
    }

    if (choixCouleur) {
        FeuilleCouleurRole(
            couleur = couleur,
            onChoisir = { couleur = it },
            onFermer = { choixCouleur = false },
        )
    }
}

/**
 * the colour picker: a grid of five, then the free colour
 *
 * the free one was deliberately absent, and the reason held: a role colour is read on an imposed
 * background, so a wheel mostly offers the chance to make a role nobody can read. what was missing
 * was not the wheel, it was the means of telling. `design/Contraste.kt` is that means, and it
 * reports rather than refuses, having measured that a floor strict enough to be worth setting
 * rejects four of the eighteen colours of the grid right above it
 *
 * so the grid stays, and it stays first: it is curated, it is one tap, and it is what you want
 * nine times out of ten. the free colour is the last entry, for the tenth
 *
 * "reset" removes the colour instead of putting a default one back: a role with no colour is a
 * legitimate case, it lets the nickname take the tier's
 */
@Composable
private fun FeuilleCouleurRole(couleur: Int?, onChoisir: (Int?) -> Unit, onFermer: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    var choisie by remember { mutableStateOf(couleur) }
    var couleurLibre by remember { mutableStateOf(false) }

    if (couleurLibre) {
        // the picker already written for the profile theme, not a second one: it has the
        // hexadecimal field, the derived shades, the square and the hue slider, and a copy here
        // would be a second place to fix the same bugs
        FeuilleCouleur(
            titre = t.couleurLibre,
            couleurInitiale = choisie?.let { "#" + (it and 0xFFFFFF).toString(16).padStart(6, '0') },
            onChoisir = { hexa -> couleurOuNull(hexa)?.let { choisie = it.toArgb() } },
            onFermer = { couleurLibre = false },
        )
    }

    FeuilleBasse(onFermer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                t.choisirUneCouleur,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                t.enregistrer,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.accent)
                    .clickable { onChoisir(choisie); onFermer() }
                    .padding(horizontal = tokens.espL, vertical = 9.dp),
            )
        }

        // five per row, like the reference. the grid is built by hand rather than with a
        // `LazyVerticalGrid`: that one scrolls, and a scrolling grid inside a scrolling sheet
        // fights over the gesture
        COULEURS_ROLE.chunked(5).forEach { rangee ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                rangee.forEach { argb ->
                    Box(
                        Modifier.weight(1f).height(52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Color(argb))
                                .clickable { choisie = argb },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (choisie == argb) {
                                Icone(Icones.valide, tint = Color.White, taille = 24.dp)
                            }
                        }
                    }
                }
                // fills out the last row so the discs stay aligned on the column instead of
                // spreading
                repeat(5 - rangee.size) { Box(Modifier.weight(1f)) }
            }
        }

        // the free colour, and what it costs to read
        //
        // the ratio is shown for the colour in hand, not for the one you are about to pick: the
        // picker is a sheet of its own, and saying it here is what makes you look before opening it
        choisie?.let { argb ->
            if (!contrasteSuffisant(argb, tokens.surface.toArgb())) {
                Text(
                    t.contrasteFaible(formaterRapport(contraste(argb, tokens.surface.toArgb()))),
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = tokens.espXl, end = tokens.espXl, top = tokens.espS),
                )
            }
        }
        Text(
            t.couleurLibre,
            color = tokens.textPrimary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = tokens.espXl, end = tokens.espXl, top = 14.dp)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised)
                .clickable { couleurLibre = true }
                .padding(vertical = 13.dp),
        )
        Text(
            t.reinitialiser,
            color = tokens.textSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 14.dp)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised)
                .clickable { choisie = null }
                .padding(vertical = 13.dp),
        )
    }
}

/**
 * the second step: who to give this role to, right away
 *
 * a plain list of checkboxes. nothing requires choosing any, the button staying active on zero
 * members, because a role created in advance to be handed out later is a normal use
 */
@Composable
private fun EcranMembresDuRole(
    membres: List<Member>,
    choisis: Set<String>,
    couleur: Int?,
    onBasculer: (String) -> Unit,
    onFermer: () -> Unit,
    onValider: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    androidx.activity.compose.BackHandler(onBack = onFermer)

    Column(Modifier.fillMaxSize().background(tokens.bg).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Text(
                t.etapeSur(2, 2),
                color = tokens.textSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Icone(
                Icones.retour,
                tint = tokens.textPrimary,
                taille = 22.dp,
                description = t.retour,
                modifier = Modifier.align(Alignment.CenterStart)
                    .clip(CircleShape).clickable(onClick = onFermer).padding(tokens.espL),
            )
        }

        Text(
            t.quiPorteCeRole,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = tokens.espS),
        )
        Text(
            t.roleFacultatifAide,
            color = tokens.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = tokens.espS, bottom = tokens.espL),
        )

        LazyColumn(Modifier.weight(1f)) {
            items(membres, key = { it.userId }) { membre ->
                val coche = membre.userId in choisis
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onBasculer(membre.userId) }
                        .padding(horizontal = tokens.espXl, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(membre.userId, membre.displayName, membre.avatarUrl, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        membre.displayName ?: membre.userId,
                        color = if (coche) {
                            couleur?.let { Color(it) } ?: tokens.textPrimary
                        } else tokens.textPrimary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (coche) Icone(Icones.valide, tint = tokens.accent, taille = 20.dp)
                }
            }
        }

        Text(
            if (choisis.isEmpty()) t.creer else t.creerEtDonnerA(choisis.size),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(tokens.espL)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.accent)
                .clickable(onClick = onValider)
                .padding(vertical = tokens.espL),
        )
    }
}

/** `#RRGGBB`, to show next to the dot as the reference does */
private fun hexDe(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/**
 * the roles palette: eighteen shades, five per row
 *
 * the tiers' twelve plus six dark variants, which is the reference's structure, a bright row then
 * its darkened row. none is left to chance: all keep a readable contrast on the theme's dark
 * background, which an eyedropper would not guarantee (see `FeuilleCouleur`)
 */
private val COULEURS_ROLE = listOf(
    0xFF5865F2.toInt(), 0xFF7C4DFF.toInt(), 0xFFB84DFF.toInt(), 0xFFEC407A.toInt(),
    0xFFEF5350.toInt(),
    0xFF3C45A5.toInt(), 0xFF5335B3.toInt(), 0xFF7E32B3.toInt(), 0xFFA32B58.toInt(),
    0xFFA83A37.toInt(),
    0xFFFF7043.toInt(), 0xFFFFA726.toInt(), 0xFFFFD54F.toInt(), 0xFF66BB6A.toInt(),
    0xFF26A69A.toInt(),
    0xFF29B6F6.toInt(), 0xFF5C6BC0.toInt(), 0xFF78909C.toInt(),
)

/**
 * a surface that really covers the window
 *
 * the role form lives in a panel, itself under the settings screen's header. rendered in place, it
 * therefore showed under that bar, so you saw "decorative roles" and its "+" above "step 1 of 2",
 * which is two competing headers and a "+" that could restart the creation over itself. seen on
 * screen
 *
 * a `Dialog` with no imposed width is the only compose surface laid above everything, wherever it
 * is opened from, which is exactly the problem the viewer solved differently, by going up to the
 * `Shell`. here we cannot go up: the panel is called by an `AnimatedContent` that constrains it by
 * construction
 *
 * `dismissOnBackPress = false`: back is handled inside, where it steps back before closing. leaving
 * it to the dialog would lose the name and the colour already entered
 *
 * `internal` because searching in a room has exactly the same need, and for exactly the same
 * reason, it living under the conversation's header. two copies of this `Dialog` would be the two
 * paths problem
 */
@Composable
internal fun PleinEcran(contenu: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(LocalTokens.current.bg)) {
            contenu()
            // the image sending banner is repeated here because a `Dialog` is a separate window:
            // the shell's is painted underneath and would stay invisible. the same state, two
            // places that know how to render it, see `EnvoiImageEnCours`
            BandeauEnvoiImage()
        }
    }
}

/**
 * the contrast ratio as it is shown, one decimal and a colon
 *
 * written by hand rather than with a format string: `String.format` is a jvm call, and this file
 * is already the android one, but the sentence it feeds is in `Traduction`, which is portable. a
 * number formatted differently on the two platforms would be the kind of split nobody notices
 * until a screenshot disagrees with another
 */
private fun formaterRapport(valeur: Double): String {
    val dixiemes = kotlin.math.round(valeur * 10).toInt()
    return "${dixiemes / 10},${dixiemes % 10}:1"
}

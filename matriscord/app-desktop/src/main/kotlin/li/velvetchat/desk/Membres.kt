package li.velvetchat.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import li.velvetchat.app.ui.*
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.RoleDecoratif
import li.velvetchat.core.model.RoleTier
import li.velvetchat.core.model.Statut

/**
 * the desktop's member column
 *
 * the grouping is still the phone's (`grouperMembres`: roles by power, offline members apart, and
 * `INCONNU` never counted as absent). what changes is the reference's drawing: the "N online of M"
 * count at the top, groups in normal case, 42 point rows where the nameplate makes the background,
 * and the status message as a subtitle
 */
@Composable
fun ColonneMembres(
    membres: List<Member>,
    statuts: Map<String, Statut>,
    modifier: Modifier,
    /** the member, and the top of their row in the window, since the card opens at that height */
    onMembre: (Member, Dp) -> Unit,
) {
    val tokens = LocalTokens.current
    val groupes = remember(membres, statuts) { grouperMembres(membres, statuts.mapValues { it.value.presence }) }
    val presents = membres.count { statuts[it.userId]?.estPresent == true }
    // presence off on the server: a zero would assert nobody is there, so we only show the total
    val presenceConnue = membres.any { (statuts[it.userId]?.presence ?: Presence.INCONNU) != Presence.INCONNU }

    LazyColumn(modifier.width(Largeurs.membres).fillMaxHeight()) {
        item(key = "compte") {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (presenceConnue) {
                    PastillePresence(Presence.EN_LIGNE, taille = 10.dp, couleurDuFond = Color.Transparent)
                    Spacer(Modifier.width(6.dp))
                    Text("$presents", color = tokens.textSecondary, fontSize = 15.sp)
                    Spacer(Modifier.width(18.dp))
                }
                // the total as a ring and not as an offline dot: the reference tells them apart
                Box(Modifier.size(12.dp).border(2.dp, tokens.textSecondary, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("${membres.size}", color = tokens.textSecondary, fontSize = 15.sp)
            }
        }
        groupes.forEach { (titre, gens) ->
            item(key = "groupe-$titre") {
                Text(
                    "$titre — ${gens.size}",
                    color = tokens.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                )
            }
            items(gens, key = { it.userId }) { membre ->
                LigneMembreBureau(membre, statuts[membre.userId] ?: Statut.INCONNU) { haut -> onMembre(membre, haut) }
            }
        }
    }
}

@Composable
private fun LigneMembreBureau(membre: Member, statut: Statut, onClick: (Dp) -> Unit) {
    val tokens = LocalTokens.current
    val densite = LocalDensity.current
    val profil = LocalProfils.current[membre.userId]
    val survol = remember { MutableInteractionSource() }
    val survolee by survol.collectIsHoveredAsState()
    var haut by remember { mutableStateOf(0.dp) }

    Box(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp).height(42.dp)
            .onGloballyPositioned { haut = with(densite) { it.positionInWindow().y.toDp() } }
            // offline members dimmed, nameplate included, like the reference
            .alpha(if (statut.presence == Presence.INVISIBLE) 0.45f else 1f)
            .clip(RoundedCornerShape(8.dp))
            // white at 10% on hover, measured off the screenshot
            .background(if (survolee) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .hoverable(survol)
            .curseurMain().clickable { onClick(haut) },
    ) {
        profil?.plaque?.let {
            Box(Modifier.matchParentSize()) { EmojiEnLigne(it, contentScale = ContentScale.FillBounds) }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                userId = membre.userId,
                displayName = membre.displayName,
                avatarUrl = membre.avatarUrl,
                taille = 32.dp,
                decoration = profil?.decoration,
                presence = statut.presence,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        nomAffiche(membre.userId, membre.displayName),
                        color = LocalRoleColors.current[membre.userId] ?: tokens.textPrimary,
                        style = TextStyle(brush = degradePseudo(profil)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    BadgeTagDe(membre.userId, hauteur = 16f)
                    membre.role?.icon?.let {
                        Spacer(Modifier.width(4.dp))
                        Text(it, color = tokens.colors.warning.toColor(), fontSize = 13.sp)
                    }
                }
                statut.message?.takeIf { it.isNotBlank() }?.let {
                    ApercuAvecEmotes(texte = it, couleur = tokens.textSecondary, taille = 12.sp)
                }
            }
        }
    }
}

/**
 * a member's card, opened to the left of the column at their row's height, measured to the pixel
 * off the reference
 *
 * | what is measured        | value                                                        |
 * |-------------------------|---------------------------------------------------------------| |
 * card                    | 306, radius 8, 2 point border in the theme                    | |
 * banner buttons          | 28 point circles, black veil, 12 apart, 8 from the top        | |
 * header                  | the popover's (banner 105, avatar 80, ring 6)                 | | roles
 * | 22 point chips, a 12 dot, 12 text, a cross, 4 / 6 apart       | | "send a message to"     | 44,
 * radius 8, a 22 smiley on the right, 16 margins            |
 *
 * buttons: friend (add or remove) and overflow. roles: the decorative ones come off with the cross
 * and go on with the plus when you have the power to manage them, and the chevron collapses the
 * list. removed because velvet does not have them: badges, mutual friends and servers, the crossed
 * swords banner button, the role shield
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FicheMembreBureau(
    membre: Member,
    statut: Statut,
    palier: RoleTier?,
    decoratifs: List<RoleDecoratif>,
    rolesDisponibles: List<RoleDecoratif>,
    peutGererRoles: Boolean,
    /** null for yourself, or while the relation has not been read */
    ami: Boolean?,
    haut: Dp,
    /**
     * opened from a message: its left edge, to the right of the avatar. null means to the left of
     * the member column
     */
    gauche: Dp? = null,
    onRoles: (List<String>) -> Unit,
    onAmi: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    /** the nickname we put on this person, null when there is none */
    surnom: String? = null,
    /** null or empty clears the nickname and gives them their name back */
    onSurnom: (String?) -> Unit = {},
    /**
     * block or unblock. null on yourself, since you do not block yourself, and the row disappears
     * rather than staying grey
     */
    bloque: Boolean? = null,
    onBloquer: (Boolean) -> Unit = {},
    onFermer: () -> Unit,
) {
    val t = textes()
    val etendu = LocalProfils.current[membre.userId]
    val couleurs = remember(etendu) { couleursProfil(etendu) }
    val moi = LocalMonIdentifiant.current
    val pressePapier = copieur()
    val nom = membre.displayName ?: membre.userId.removePrefix("@").substringBefore(':')
    var brouillon by remember(membre.userId) { mutableStateOf("") }
    var bioComplete by remember(membre.userId) { mutableStateOf(true) }
    var rolesReplies by remember(membre.userId) { mutableStateOf(false) }
    var menuPlus by remember { mutableStateOf(false) }
    var menuRoles by remember { mutableStateOf(false) }
    val sansEffet = remember { MutableInteractionSource() }

    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(interactionSource = sansEffet, indication = null, onClick = onFermer),
    ) {
        // ponytail: card height assumed at about 560 to keep it inside the window, to be measured
        // if a long bio overflows
        val y = haut.coerceAtMost(maxHeight - 580.dp).coerceAtLeast(12.dp)
        val place = if (gauche != null) {
            Modifier.align(Alignment.TopStart).padding(start = gauche.coerceAtMost(maxWidth - 320.dp), top = y)
        } else {
            Modifier.align(Alignment.TopEnd).padding(end = Largeurs.membres + 16.dp, top = y)
        }
        // the card keeps its width and its frame overhangs it. the overhang is a margin around
        // the card, as on the reference's popout, so the frame does not get cut by the window
        Box(place.padding(debordsDuCadre(etendu?.cadre, 306.dp)).width(306.dp)) {
            Box(Modifier.fillMaxWidth()) {
            // the frame's background layers, under the opaque card: only what sticks out shows
            etendu?.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize(), derriere = true) }
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(couleurs.fond)
                    .border(2.dp, couleurs.bordure, RoundedCornerShape(8.dp))
                    .padding(2.dp)
                    // a click inside the card does not close it
                    .clickable(interactionSource = sansEffet, indication = null) { },
            ) {
                // the effect over the whole card, like the reference
                etendu?.effet?.let {
            // clipped to the card: the effect used to bleed onto whatever was around it
            Box(Modifier.matchParentSize().clipToBounds().zIndex(2f)) { EffetDeProfil(it) }
        }
                Column(Modifier.fillMaxWidth()) {
                    EnteteCarteProfil(
                        userId = membre.userId,
                        nom = membre.displayName,
                        avatarUrl = membre.avatarUrl,
                        etendu = etendu,
                        couleurs = couleurs,
                        presence = statut.presence,
                        message = statut.message,
                        banniere = 105.dp,
                        avatar = 80.dp,
                        anneau = 6.dp,
                        decalage = 44.dp,
                        pastille = 16.dp,
                        boutons = if (membre.userId == moi) null else {
                            {
                                ami?.let { estAmi ->
                                    BoutonBanniere(
                                        if (estAmi) Icones.amiValide else Icones.amiAjouter,
                                        if (estAmi) t.ficheRetirerAmi else t.ajouterEnAmi,
                                    ) { onAmi(!estAmi) }
                                    Spacer(Modifier.width(12.dp))
                                }
                                Box {
                                    BoutonBanniere(Icones.troisPoints, t.plus) { menuPlus = true }
                                    if (menuPlus) {
                                        Popup(onDismissRequest = { menuPlus = false }) {
                                            MenuFiche {
                                                EntreeMenu(
                                                    libelle = t.copierIdUtilisateur,
                                                    pastilleId = true,
                                                    onClick = {
                                                        pressePapier(membre.userId)
                                                        menuPlus = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        Spacer(Modifier.height(12.dp))
                        NomCarteProfil(nom, etendu, couleurs)
                        LigneIdentifiantProfil(membre.userId, etendu, couleurs)

                        // what they are playing, between the name and the bio: it is the most
                        // perishable thing on this card and the eye should hit it before
                        // settling into a paragraph somebody wrote eight months ago
                        BlocActiviteDepuisProfil(
                            userId = membre.userId,
                            brut = etendu?.activite,
                            couleurTitre = couleurs.texte,
                            couleurTexte = couleurs.texteDoux,
                            couleurIcone = couleurs.texte,
                            modifier = Modifier.padding(top = 10.dp),
                        )

                        etendu?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                            Spacer(Modifier.height(12.dp))
                            ApercuAvecEmotes(texte = bio, couleur = couleurs.texte, taille = 14.sp, maxLines = if (bioComplete) 20 else 3, lineHeight = 18.sp)
                            if (!bioComplete && (bio.lines().size > 3 || bio.length > 110)) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (bioComplete) t.reduireLaBio else t.afficherLaBioComplete,
                                    color = couleurs.texte,
                                    fontSize = 12.sp,
                                    modifier = Modifier.curseurMain().clickable { bioComplete = !bioComplete },
                                )
                            }
                        }

                        // power level 0 is everybody's: the reference does not show @everyone
                        val palierVisible = palier?.takeIf { it.power > 0 }
                        val nombre = (if (palierVisible != null) 1 else 0) + decoratifs.size
                        if (nombre > 0 || peutGererRoles) {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val limite = if (rolesReplies) 2 else Int.MAX_VALUE
                                var montres = 0
                                palierVisible?.let {
                                    PiluleRole(it.name, it.colorArgb, couleurs, onRetirer = null)
                                    montres++
                                }
                                decoratifs.forEach { role ->
                                    if (montres < limite) {
                                        PiluleRole(
                                            role.nom,
                                            role.colorArgb,
                                            couleurs,
                                            onRetirer = if (peutGererRoles) {
                                                { onRoles(decoratifs.filter { it.id != role.id }.map { it.id }) }
                                            } else {
                                                null
                                            },
                                        )
                                        montres++
                                    }
                                }
                                if (nombre > 2) {
                                    CarreRole(
                                        if (rolesReplies) Icones.chevronDroit else Icones.chevronGauche,
                                        couleurs,
                                        cadre = true,
                                    ) { rolesReplies = !rolesReplies }
                                }
                                if (peutGererRoles) {
                                    Box {
                                        CarreRole(Icones.plusGras, couleurs, cadre = false) { menuRoles = true }
                                        if (menuRoles) {
                                            Popup(onDismissRequest = { menuRoles = false }) {
                                                MenuFiche {
                                                    val libres = rolesDisponibles.filter { r -> decoratifs.none { it.id == r.id } }
                                                    if (libres.isEmpty()) {
                                                        EntreeMenu(libelle = t.aucunRoleAAjouter, actif = false, onClick = { menuRoles = false })
                                                    }
                                                    libres.forEach { r ->
                                                        EntreeMenu(
                                                            libelle = r.nom,
                                                            onClick = {
                                                                onRoles(decoratifs.map { it.id } + r.id)
                                                                menuRoles = false
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // the friend nickname, which could only be edited on the phone. set here,
                        // it replaces the name everywhere, member list, messages, direct
                        // conversations
                        if (membre.userId != moi) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "SURNOM",
                                color = couleurs.texteDoux,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(6.dp))
                            var saisie by remember(membre.userId, surnom) { mutableStateOf(surnom.orEmpty()) }
                            Box(
                                Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp))
                                    .background(couleurs.champ).padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (saisie.isEmpty()) {
                                    Text(
                                        membre.nomReel ?: nom,
                                        color = couleurs.texte.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                BasicTextField(
                                    value = saisie,
                                    onValueChange = { saisie = it.take(48) },
                                    singleLine = true,
                                    textStyle = TextStyle(color = couleurs.texteFort, fontSize = 14.sp),
                                    cursorBrush = SolidColor(couleurs.texteFort),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            // saved once the typing settles: per character, we would write account
                            // data by the dozen
                            LaunchedEffect(saisie) {
                                if (saisie.trim() == surnom.orEmpty()) return@LaunchedEffect
                                kotlinx.coroutines.delay(900)
                                onSurnom(saisie.trim().takeIf { it.isNotBlank() })
                            }
                        }

                        if (membre.userId != moi) {
                            Spacer(Modifier.height(16.dp))
                            Row(
                                Modifier.fillMaxWidth().height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(couleurs.champ)
                                    .padding(start = 16.dp, end = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(1f)) {
                                    if (brouillon.isEmpty()) {
                                        Text(
                                            t.envoyerAPersonne("@" + nom),
                                            color = couleurs.texte.copy(alpha = 0.8f),
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    BasicTextField(
                                        value = brouillon,
                                        onValueChange = { brouillon = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = couleurs.texteFort, fontSize = 14.sp),
                                        cursorBrush = SolidColor(couleurs.texteFort),
                                        modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                                            if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && brouillon.isNotBlank()) {
                                                onMessage(brouillon.trim())
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Icone(Icones.smileyPlein, tint = couleurs.texteDoux, taille = 22.dp, description = null)
                            }
                        }

                        // blocking somebody is per account and holds everywhere: two clicks, like
                        // everything that does not undo itself in one gesture
                        bloque?.let { estBloque ->
                            var confirmeBlocage by remember(membre.userId) { mutableStateOf(false) }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                when {
                                    estBloque -> t.ficheDebloquer
                                    confirmeBlocage -> t.confirmerLeBlocage
                                    else -> t.ficheBloquer
                                },
                                color = if (estBloque) couleurs.texte else ROSE_MENU,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.curseurMain().clickable {
                                    if (estBloque) {
                                        onBloquer(false)
                                    } else if (confirmeBlocage) {
                                        onBloquer(true)
                                    } else {
                                        confirmeBlocage = true
                                    }
                                },
                            )
                        }
                    }
                }
            }
            // the frame goes around the card, in two halves that overhang, see [CadreDeProfil]
            etendu?.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize()) }
            }
        }
    }
}

/**
 * a role chip: 22, radius 6, a light border, a 12 colour dot, a 12 name, and a cross when you can
 * remove it
 */
@Composable
private fun PiluleRole(libelle: String, couleur: Int?, couleurs: CouleursProfil, onRetirer: (() -> Unit)?) {
    val t = textes()
    Row(
        Modifier.height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.03f))
            .border(1.dp, couleurs.bordPastille, RoundedCornerShape(6.dp))
            .padding(start = 5.dp, end = if (onRetirer != null) 3.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(couleur?.let { Color(it) } ?: couleurs.texteDoux))
        Spacer(Modifier.width(4.dp))
        Text(
            libelle,
            color = couleurs.texteFort,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 210.dp),
        )
        onRetirer?.let {
            Box(Modifier.padding(start = 3.dp).size(14.dp).clip(CircleShape).curseurMain().clickable(onClick = it), contentAlignment = Alignment.Center) {
                Icone(Icones.croixGrasse, tint = couleurs.texteFort, taille = 10.dp, description = t.retirerLeRole)
            }
        }
    }
}

/**
 * the 22 point squares at the end of the roles: the chevron collapses, the plus adds, with no frame
 * like the reference
 */
@Composable
private fun CarreRole(icone: ImageVector, couleurs: CouleursProfil, cadre: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
            .then(if (cadre) Modifier.border(1.dp, couleurs.bordPastille, RoundedCornerShape(6.dp)) else Modifier)
            .curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icone(icone, tint = couleurs.texteFort, taille = 12.dp, description = null) }
}

/** a card's menu, in the desktop menus' style (`MenuSalon`) */
@Composable
private fun MenuFiche(contenu: @Composable () -> Unit) {
    Column(
        Modifier.width(220.dp).clip(RoundedCornerShape(8.dp)).background(FOND_MENU)
            .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp)).padding(8.dp),
    ) { contenu() }
}

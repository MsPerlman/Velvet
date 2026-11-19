package li.velvetchat.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.model.CustomEmote

/**
 * the server sheet, opened by touching its name
 *
 * reproduces the reference's mobile layout, in this order: server icon, name, status line, four
 * labelled square buttons, then grouped action cards
 *
 * why cards and not one continuous list: each card gathers actions of the same nature, consulting,
 * creating, setting. a hairline separates the rows of one card, and a gap separates the cards from
 * each other. the hierarchy therefore reads with no subheading at all, which a flat list could not
 * manage without adding three
 *
 * what does not exist is absent, not greyed out. boosts, categories, events and notification
 * settings have no equivalent here, and showing them inert would make a sheet twice as long, half
 * of which would not answer
 */
@Composable
fun MenuServeur(
    nomDuServeur: String,
    avatarUrl: String?,
    serverId: String,
    nombreMembres: Int?,
    /** how many are online. `null` when the server says nothing about presence */
    nombreEnLigne: Int? = null,
    /** the server's tag, shown here as on the reference. `null` when it has none */
    tagDuServeur: li.velvetchat.core.model.TagServeur? = null,
    onInviter: () -> Unit,
    onSalon: () -> Unit,
    onCategorie: () -> Unit,
    onNotifications: () -> Unit,
    onChercher: () -> Unit,
    onTag: () -> Unit,
    onParametres: () -> Unit,
    onMarquerLu: () -> Unit,
    onQuitter: () -> Unit,
    onFermer: () -> Unit,
    /** deletes the server for everybody. null when we are not an admin of it, which hides it */
    onSupprimer: (() -> Unit)? = null,
) {
    val t = textes()
    val tokens = LocalTokens.current
    FeuilleBasse(onFermer) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Box(Modifier.size(64.dp)) {
                if (avatarUrl != null) {
                    Avatar(serverId, nomDuServeur, avatarUrl, 64.dp)
                } else {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(tokens.radiusLarge))
                            .background(tokens.surfaceRaised),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            nomDuServeur.take(2).uppercase(),
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    }
                }
            }

            Text(
                nomDuServeur,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.padding(top = 14.dp),
            )

            // "N online, N members", and not "N members" alone
            //
            // the reference gives both, and they are two different pieces of information: how many
            // are here now, and how many are here in total. the first decides whether you write
            // straight away, and the second says the size of the place. the green dot only
            // qualifies the first, and it used to precede a member count, which suggested twelve
            // people were connected
            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                nombreEnLigne?.let {
                    Point(tokens.colors.success.toColor())
                    Text(" " + t.enLigneNombre(it), color = tokens.textSecondary, fontSize = 15.sp)
                    Text("   ", fontSize = 15.sp)
                }
                Point(tokens.textMuted)
                Text(
                    nombreMembres?.let { " " + t.membresCompte(it) } ?: " — " + t.membres.lowercase(),
                    color = tokens.textSecondary,
                    fontSize = 15.sp,
                )
            }
        }

        // four tiles, the reference's. notifications was missing, and it is the one people look for
        // most often, muting a chatty server being the first thing you do on joining it
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BoutonAction("👤", t.inviter, Modifier.weight(1f), onInviter)
            BoutonAction("🔔", t.notifications, Modifier.weight(1f), onNotifications)
            BoutonAction("＃", t.creerSalon, Modifier.weight(1f), onSalon)
            BoutonAction("⚙", t.parametres, Modifier.weight(1f), onParametres)
        }

        // the reference's groups, in its order: what you do with the list, then what you add to it,
        // then what touches its identity
        Carte {
            LigneCarte(t.marquerCommeLu, onMarquerLu)
            Separation()
            LigneCarte(t.chercherDesSalons, onChercher)
        }
        Carte {
            LigneCarte(t.creerSalon, onSalon)
            Separation()
            // it was missing entirely. the engine has been able to create a category from the start
            // (`createCategory`), the screen can show them collapsible, but nothing let you create
            // one. so a server could only be tidied from another client
            LigneCarte(t.creerUneCategorie, onCategorie)
        }
        Carte {
            LigneCarteValeur(t.tagDuServeur, tagDuServeur, onTag)
            Separation()
            LigneCarte(t.parametresDuServeur, onParametres)
        }
        // leave, the only "deletion" matrix knows. a space is not deleted for everybody, you leave
        // all its rooms and forget it, so it disappears from here. two taps: coming back needs a
        // fresh invite
        Carte {
            var confirme by remember { mutableStateOf(false) }
            Text(
                if (confirme) t.confirmerLeDepart else t.quitterLeServeur,
                color = tokens.danger,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
                    .clickable { if (confirme) onQuitter() else confirme = true }
                    .padding(horizontal = 18.dp, vertical = tokens.espL),
            )
            onSupprimer?.let { supprimer ->
                Separation()
                var confirmeSuppression by remember { mutableStateOf(false) }
                Text(
                    if (confirmeSuppression) t.confirmerLaSuppression else t.supprimerLeServeur,
                    color = tokens.danger,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { if (confirmeSuppression) supprimer() else confirmeSuppression = true }
                        .padding(horizontal = 18.dp, vertical = tokens.espL),
                )
            }
        }
        Box(Modifier.padding(bottom = 10.dp))
    }
}

/**
 * a card row that shows its value on the right, here the tag, as a badge
 *
 * the reference shows the tag itself rather than its name: it is a visual object, and describing it
 * in words ("emoji plus four letters") would be less clear than showing it
 */
@Composable
private fun LigneCarteValeur(
    libelle: String,
    tag: li.velvetchat.core.model.TagServeur?,
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tag != null) {
            BadgeTag(tag, hauteur = 20f)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            libelle,
            color = tokens.textPrimary,
            fontSize = tokens.typography.bodySize.sp,
            modifier = Modifier.weight(1f),
        )
        if (tag == null) {
            Text(t.aucun, color = tokens.textMuted, fontSize = 13.sp)
        }
    }
}

/** the coloured dot of a status line */
@Composable
private fun Point(couleur: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.size(8.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(couleur)
    )
}

/**
 * a labelled square button
 *
 * the label is under the square and not inside it: a glyph on its own is hard to guess, and a label
 * inside would mean shrinking the glyph until it became unreadable
 */
@Composable
private fun BoutonAction(
    glyphe: String,
    libelle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(58.dp)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyphe, color = tokens.textPrimary, fontSize = 22.sp)
        }
        Text(
            libelle,
            color = tokens.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** an actions card: raised background, rounded corners, rows separated by a hairline */
@Composable
private fun Carte(contenu: @Composable ColumnScope.() -> Unit) {
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised),
        content = contenu,
    )
}

@Composable
private fun LigneCarte(libelle: String, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Text(
        libelle,
        color = tokens.textPrimary,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = tokens.espL),
    )
}

/**
 * the server settings, full screen
 *
 * full screen and not a sheet: you stay in it, you navigate between several panels, and a sheet
 * filling up to the top is no longer a sheet, it is a screen not owning its name
 *
 * sections, not a sidebar. discord shows a column of sections on the left on desktop, and on a
 * phone the same information becomes a list with subheadings, each section opening full width.
 * reproducing the sidebar on 384 dp would be copying a solution without copying the problem it
 * solves
 *
 * what does not exist is not displayed. the sections velvet cannot fill yet, roles, moderation,
 * bans, are absent rather than greyed out: an inert row gets forgotten, a missing row gets asked
 * for
 */
@Composable
fun EcranParametresServeur(
    nomDuServeur: String,
    emotes: List<CustomEmote>,
    membres: List<li.velvetchat.core.model.Member>,
    paliers: List<li.velvetchat.core.model.RoleTier>,
    /**
     * the looker's power, compared with the write threshold of a state event (50 by default in
     * matrix). below it, editing the tiers is not shown at all rather than shown then refused by
     * the server
     */
    peutEditerRoles: Boolean = false,
    onEnregistrerRoles: (suspend (List<li.velvetchat.core.model.RoleTier>) -> Unit)? = null,
    /** the decorative roles, see `RoleDecoratif`: stackable, with no power */
    rolesDecoratifs: List<li.velvetchat.core.model.RoleDecoratif> = emptyList(),
    onEnregistrerDecoratifs:
        (suspend (li.velvetchat.core.model.Decorations) -> Unit)? = null,
    /** who wears what, for the "N members" counts of the role list */
    attributions: Map<String, List<String>> = emptyMap(),
    /** the server's avatar, at the top of the screen as in the reference */
    avatarDuServeur: String? = null,
    /**
     * the tag this server offers its members, `💟ERBS` and company
     *
     * `null` when it has set none, which is the default. see `TagServeur`: this is not the role
     * tier, which is computed from power. this one is chosen, it follows the person everywhere, and
     * it is visible even to non members
     */
    tagDuServeur: li.velvetchat.core.model.TagServeur? = null,
    /** the catalogue's pixel art badges, offered for the tag's emoji */
    badgesTags: List<li.velvetchat.core.model.DecorationAvatar> = emptyList(),
    /** `null` when you do not have the power to write the server's state */
    onEnregistrerTag:
        (suspend (li.velvetchat.core.model.TagServeur?) -> Unit)? = null,
    /**
     * opens the image picker for the server's icon. `null` when you do not have the power to write
     * the space's state, the dot then staying a plain shield with no pencil
     */
    onChangerIcone: (() -> Unit)? = null,
    onMembre: (li.velvetchat.core.model.Member) -> Unit,
    onChargerMembres: () -> Unit,
    onAjouterEmoji: suspend (chemin: String, nom: String, autocollant: Boolean) -> Unit,
    onRetirerEmoji: suspend (String) -> Unit,
    decorationsAvatar: List<li.velvetchat.core.model.DecorationAvatar> = emptyList(),
    monIdentifiant: String = "",
    onAjouterDecoration: suspend (chemin: String, nom: String) -> Unit = { _, _ -> },
    onRetirerDecoration: suspend (String) -> Unit = {},
    sonsSoundboard: List<li.velvetchat.core.model.SonSoundboard> = emptyList(),
    onAjouterSon: suspend (chemin: String, nom: String, emoji: String?) -> Unit = { _, _, _ -> },
    onRenommerSon: suspend (id: String, nom: String, emoji: String?) -> Unit = { _, _, _ -> },
    onRetirerSon: suspend (String) -> Unit = {},
    onInviter: () -> Unit,
    onFermer: () -> Unit,
) {
    // android's file picker, for the shared panels
    //
    // they lived here and could not be shared because of it alone. it returns a local path, the
    // file being copied into the cache, because a `content://` is only readable by this app and the
    // engine wants a file
    val contexteFichier = androidx.compose.ui.platform.LocalContext.current
    val porteeFichier = rememberCoroutineScope()
    val enAttenteFichier = remember { arrayOfNulls<(String) -> Unit>(1) }
    val lanceurFichier = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        val suite = enAttenteFichier[0]
        enAttenteFichier[0] = null
        if (uri != null && suite != null) {
            porteeFichier.launch {
                copierDansLeCache(contexteFichier, uri)?.let { suite(it) }
            }
        }
    }
    val choisirFichier: ChoisirFichier = { type, suite ->
        enAttenteFichier[0] = suite
        lanceurFichier.launch(if (type == TypeFichier.SON) "audio/*" else "image/*")
    }
    val t = textes()
    val tokens = LocalTokens.current
    var rubrique by remember { mutableStateOf<String?>(null) }
    // driven from the header: the reference puts the "+" in the top bar, not in the list. so the
    // panel cannot hold that state on its own
    var creerRole by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler {
        if (rubrique == null) onFermer() else rubrique = null
    }

    Column(Modifier.fillMaxSize().background(tokens.bg).statusBarsPadding()) {
        // the reference's header: centred title, close on the left, action on the right
        //
        // ours aligned the title left behind a text chevron. the difference is not cosmetic, a
        // centred title sits between two buttons of equal width, which leaves room for a "+" on the
        // right without the title shifting when it appears. left aligned, adding it would have
        // moved the title from one section to the next
        //
        // a cross at the root, a chevron inside a section: these are not the same gesture. close
        // the screen, or go up one level
        Box(
            Modifier.fillMaxWidth().background(tokens.surface).height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.Crossfade(
                targetState = rubrique ?: t.parametresDuServeur,
                animationSpec = tween(tokens.dureeRapide),
                label = "titre",
            ) { titre ->
                Text(
                    titre,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = tokens.typography.headingSize.sp,
                )
            }
            Icone(
                if (rubrique == null) Icones.fermer else Icones.retour,
                tint = tokens.textPrimary,
                taille = 22.dp,
                description = if (rubrique == null) t.fermer else t.retour,
                modifier = Modifier.align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .clickable { if (rubrique == null) onFermer() else rubrique = null }
                    .padding(tokens.espL),
            )
            // the "+" only exists where it leads somewhere, and only for those who can write the
            // server's state
            if (rubrique == t.rolesDecoratifs && peutEditerRoles) {
                Icone(
                    Icones.ajouter,
                    tint = tokens.textPrimary,
                    taille = 24.dp,
                    description = t.nouveauRole,
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .clickable { creerRole = true }
                        .padding(tokens.espL),
                )
            }
        }

        // the sections slide sideways, like the drawer's two levels: entering a section goes right,
        // and leaving it comes back. the movement therefore carries "i am going down / i am coming
        // back up", which a plain appearance would not say
        //
        // the outgoing one only moves back a quarter of a width: that is the parallax, and it is
        // what says which of the two surfaces is underneath
        val entree = tween<IntOffset>(tokens.dureeNormale, easing = tokens.easingEntree)
        val sortie = tween<IntOffset>(tokens.dureeNormale, easing = tokens.easingSortie)
        AnimatedContent(
            targetState = rubrique,
            transitionSpec = {
                // a non null `targetState` means entering a section; null means going back up
                if (targetState != null) {
                    slideInHorizontally(entree) { it } togetherWith
                        slideOutHorizontally(sortie) { -it / 4 }
                } else {
                    slideInHorizontally(entree) { -it / 4 } togetherWith
                        slideOutHorizontally(sortie) { it }
                }
            },
            label = "rubrique",
        ) { courante ->
            when (courante) {
                t.emoji -> GestionEmojisDuServeur(
                    emotes, onAjouterEmoji, onRetirerEmoji, choisirFichier,
                )
                t.decorations -> PanneauDecorations(
                    decorationsAvatar, monIdentifiant, onAjouterDecoration, onRetirerDecoration,
                    choisirFichier,
                )
                "Soundboard" -> PanneauSons(
                    sonsSoundboard, onAjouterSon, onRetirerSon, choisirFichier,
                    onRenommer = onRenommerSon,
                    // the three halves of the trimming, all of them android's. the panel is
                    // shared and the codecs are not: see `AudioSon`, and see `PanneauSons` for
                    // what happens on a platform that supplies none of them
                    decodeur = { chemin -> li.velvetchat.app.AudioSon.decoder(chemin) },
                    encodeur = { pcm, frequence ->
                        li.velvetchat.app.AudioSon.ecrireWav(
                            pcm, frequence, java.io.File(contexteFichier.cacheDir, "envois"),
                        )
                    },
                    onEcouter = { pcm, frequence ->
                        li.velvetchat.app.AudioSon.ecouter(pcm, frequence)
                    },
                )
                t.membres -> PanneauMembres(membres, paliers, onChargerMembres, onMembre)
                t.roles -> PanneauRoles(paliers, peutEditerRoles, onEnregistrerRoles)
                t.rolesDecoratifs -> PanneauRolesDecoratifs(
                    decorations = li.velvetchat.core.model.Decorations(
                        rolesDecoratifs,
                        attributions,
                    ),
                    membres = membres,
                    modifiable = peutEditerRoles,
                    creation = creerRole,
                    onCreationTerminee = { creerRole = false },
                    onEnregistrer = onEnregistrerDecoratifs,
                )
                t.tag -> EditionTagServeur(
                    tag = tagDuServeur,
                    emotes = emotes,
                    badges = badgesTags,
                    onEnregistrer = onEnregistrerTag,
                    onFini = { rubrique = null },
                )

                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    // the avatar and the name at the top, like the reference: that is what says
                    // which server you are setting, the day there are several
                    Column(
                        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // the shield becomes the button. that is where you go for a server's icon
                        // on the reference, you tap on it, not into a named section. the pencil in
                        // the corner says it is possible, and without it nothing would tell a
                        // clickable shield from a thumbnail
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                Modifier.size(72.dp)
                                    .clip(RoundedCornerShape(tokens.radiusLarge))
                                    .background(tokens.surfaceRaised)
                                    .then(
                                        if (onChangerIcone != null) {
                                            Modifier.clickable(onClick = onChangerIcone)
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (avatarDuServeur != null) {
                                    EmojiEnLigne(
                                        avatarDuServeur,
                                        contentScale =
                                            androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                } else {
                                    Text(
                                        nomDuServeur.take(2).uppercase(),
                                        color = tokens.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp,
                                    )
                                }
                            }
                            if (onChangerIcone != null) {
                                Box(
                                    Modifier.size(26.dp).clip(CircleShape)
                                        .background(tokens.surface)
                                        .clickable(onClick = onChangerIcone),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icone(
                                        Icones.modifier,
                                        tint = tokens.textSecondary,
                                        taille = 14.dp,
                                        description = t.changerIconeServeur,
                                    )
                                }
                            }
                        }
                        Text(
                            nomDuServeur,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }

                    Intertitre(t.identite)
                    CarteReglages {
                        LigneReglage(
                            Icones.applications,
                            t.tagDuServeur,
                            tagDuServeur?.let { "${it.emoji} ${it.texte}" } ?: t.aucun,
                        ) { rubrique = t.tag }
                    }

                    Intertitre(t.expression)
                    CarteReglages {
                        LigneReglage(Icones.emoji, t.emoji, "${emotes.size}") { rubrique = t.emoji }
                        LigneReglage(
                            Icones.applications,
                            t.decorations,
                            "${decorationsAvatar.size}",
                        ) { rubrique = t.decorations }
                        Separation()
                        LigneReglage(
                            Icones.salonVocal,
                            "Soundboard",
                            "${sonsSoundboard.size}",
                        ) { rubrique = "Soundboard" }
                    }
                    Intertitre(t.gestionDesUtilisateurs)
                    CarteReglages {
                        LigneReglage(Icones.membres, t.membres) { rubrique = t.membres }
                        Separation()
                        // two entries and not one: "roles" decides who commands, "decorative roles"
                        // who wears which title. putting them under one label would send people
                        // looking for permissions in the wrong list
                        LigneReglage(Icones.chiffrement, t.roles, "${paliers.size}") {
                            rubrique = t.roles
                        }
                        Separation()
                        LigneReglage(
                            Icones.favoris,
                            t.rolesDecoratifs,
                            "${rolesDecoratifs.size}",
                        ) { rubrique = t.rolesDecoratifs }
                        Separation()
                        LigneReglage(Icones.inviter, t.invitations, onClick = onInviter)
                    }
                    Box(Modifier.height(28.dp))
                }
            }
        }
    }
}

/**
 * a settings card: a group of rows on a raised surface, with rounded corners
 *
 * it is the reference's shape, and it does real work. our rows were laid flat on the background
 * with no visible grouping: "members", "roles" and "invites" read as a single list. the card says
 * where a group starts and ends, the subheading above says which one, and it is the only way to
 * have ten readable entries
 */
@Composable
private fun CarteReglages(contenu: @Composable ColumnScope.() -> Unit) {
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espM)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surface),
        content = contenu,
    )
}

/** the hairline between two rows of a card, offset to let the icon column through */
@Composable
private fun Separation() {
    val tokens = LocalTokens.current
    Box(
        Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(tokens.border)
    )
}

/**
 * a settings row: icon, label, optional count, chevron
 *
 * the chevron is not decorative, it is what tells a row that opens a screen from a row that toggles
 * a setting. without it the two look alike, and you have to tap to find out which one is under your
 * finger
 */
@Composable
private fun LigneReglage(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    libelle: String,
    valeur: String? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = tokens.espL, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(icone, tint = tokens.textPrimary, taille = 22.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            libelle,
            color = tokens.textPrimary,
            fontSize = tokens.typography.bodySize.sp,
            modifier = Modifier.weight(1f),
        )
        valeur?.let {
            Text(it, color = tokens.textMuted, fontSize = tokens.typography.captionSize.sp)
            Spacer(Modifier.width(8.dp))
        }
        Icone(Icones.suite, tint = tokens.textMuted, taille = 16.dp)
    }
}

/** a group's title. in normal case, like the reference, and not in capitals */
@Composable
private fun Intertitre(texte: String) {
    val tokens = LocalTokens.current
    Text(
        texte,
        color = tokens.textMuted,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, top = tokens.espXl, bottom = tokens.espS),
    )
}

/**
 * a full screen panel that closes on a drag from the edge
 *
 * three screens, the control centre, a room's details, a thread, had no `BackHandler` at all: back
 * went through them and toggled the drawer behind, so you came back to a screen you had not left.
 * it is the most visible fault of the ergonomics reported, and it counted for the system gesture as
 * well as for the button
 *
 * the drag starts from the edge, and deliberately: a drag taken anywhere would conflict with what
 * the content already does with the horizontal, a message row's drag to reply, the scrolling tabs.
 * the edge belongs to nobody, and it is already where the hand goes to come back
 *
 * the threshold is the same pair as everywhere else: a clear flick decides alone, otherwise the
 * distance settles it
 */
@Composable
fun PanneauGlissant(
    onFermer: () -> Unit,
    /**
     * `false` for a screen that already handles back itself
     *
     * the server settings, for instance, first go up one section before closing: putting a second
     * `BackHandler` over theirs would close everything at once and lose the level you were working
     * in. the drag stays useful in both cases, hence two separate settings rather than one
     */
    gereLeRetour: Boolean = true,
    modifier: Modifier = Modifier,
    contenu: @Composable () -> Unit,
) {
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    val densite = LocalDensity.current

    if (gereLeRetour) androidx.activity.compose.BackHandler(onBack = onFermer)

    val pousse = remember { androidx.compose.animation.core.Animatable(0f) }
    val seuil = with(densite) { 96.dp.toPx() }
    val lancer = with(densite) { tokens.density.flingVelocityDpS.dp.toPx() }
    val bord = with(densite) { 28.dp.toPx() }

    Box(
        modifier
            .fillMaxSize()
            .offset { IntOffset(pousse.value.roundToInt(), 0) }
            .pointerInput(seuil) {
                val vitesse = androidx.compose.ui.input.pointer.util.VelocityTracker()
                var parcouru = 0f
                var depuisLeBord = false
                detectHorizontalDragGestures(
                    onDragStart = { position ->
                        depuisLeBord = position.x <= bord
                        parcouru = 0f
                        vitesse.resetTracking()
                    },
                    onDragEnd = {
                        if (!depuisLeBord) return@detectHorizontalDragGestures
                        val v = vitesse.calculateVelocity().x
                        if (v > lancer || (v > -lancer && pousse.value > seuil)) onFermer()
                        else scope.launch { pousse.animateTo(0f, tokens.ressort()) }
                    },
                    onDragCancel = {
                        scope.launch { pousse.animateTo(0f, tokens.ressort()) }
                    },
                ) { changement, delta ->
                    if (!depuisLeBord) return@detectHorizontalDragGestures
                    parcouru += delta
                    vitesse.addPosition(
                        changement.uptimeMillis,
                        androidx.compose.ui.geometry.Offset(parcouru, 0f),
                    )
                    // never leftwards: the panel is already at the edge, and pushing it further in
                    // would mean nothing
                    scope.launch { pousse.snapTo((pousse.value + delta).coerceAtLeast(0f)) }
                }
            },
    ) {
        contenu()
    }
}

/**
 * create a room in the current server
 *
 * the name is cleaned like a matrix room name, lowercase and dashes, in the image of the `#` that
 * precedes it everywhere in the interface. doing it as you type saves having to explain afterwards
 * why "holiday photos" became something else
 */
@Composable
fun FeuilleNouveauSalon(
    nomDuServeur: String,
    /**
     * the categories to file the room in, the server's
     *
     * without this choice a category would be no use: you could create one, and no path in the app
     * put anything in it. it is the other half of categories simply not doing anything
     */
    categories: List<li.velvetchat.core.model.Categorie> = emptyList(),
    /**
     * the room's name, the category to file it in (`null` for the root), and whether it is voice
     */
    onCreer: (String, li.velvetchat.core.model.Categorie?, Boolean) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var nom by remember { mutableStateOf("") }
    var categorie by remember {
        mutableStateOf<li.velvetchat.core.model.Categorie?>(null)
    }
    var vocal by remember { mutableStateOf(false) }

    FeuilleBasse(onFermer) {
        Column(Modifier.padding(horizontal = tokens.espXl)) {
            Text(
                t.nouveauSalonDans(nomDuServeur),
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.headingSize.sp,
                modifier = Modifier.padding(bottom = tokens.espXs),
            )
            Text(
                t.visibleParTousLesMembres,
                color = tokens.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // the type first, the name after. that is the reference's order, and it is also the
            // right order of decision: you know what you want to create before you know what to
            // call it. the type cannot be changed afterwards, `m.room.create` being immutable, so
            // it had better be seen
            Row(
                Modifier.fillMaxWidth().padding(bottom = tokens.espM),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(false to t.salonTexte, true to t.salonVocal).forEach { (estVocal, titre) ->
                    val retenu = estVocal == vocal
                    Column(
                        Modifier.weight(1f)
                            .background(
                                if (retenu) tokens.accent else tokens.surfaceRaised,
                                RoundedCornerShape(tokens.radiusSmall),
                            )
                            .clickable { vocal = estVocal }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icone(
                                if (estVocal) Icones.salonVocal else Icones.salon,
                                tint = if (retenu) tokens.bg else tokens.textSecondary,
                                taille = 16.dp,
                            )
                            Box(Modifier.width(6.dp))
                            Text(
                                titre,
                                color = if (retenu) tokens.bg else tokens.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                        }
                        Text(
                            if (estVocal) t.salonVocalDetail else t.salonTexteDetail,
                            color = if (retenu) tokens.bg.copy(alpha = 0.7f) else tokens.textMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            ChampSalon(nom) { nom = it }

            if (categories.isNotEmpty()) {
                Text(
                    t.rangerDans,
                    color = tokens.textMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = tokens.espM, bottom = 6.dp),
                )
                // dots rather than a dropdown: a small server has two or three categories, and a menu
                // would ask one more gesture to show what fits on a line. "none" first, because it
                // is the common case
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (listOf(null) + categories).forEach { choix ->
                        val retenu = choix == categorie
                        Box(
                            Modifier
                                .background(
                                    if (retenu) tokens.accent else tokens.surfaceRaised,
                                    RoundedCornerShape(tokens.radiusSmall),
                                )
                                .clickable { categorie = choix }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                choix?.nom ?: t.aucune,
                                color = if (retenu) tokens.bg else tokens.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                Box(Modifier.height(tokens.espM))
            }

            // returned immediately: the room appears in the panel, the write follows
            BoutonPlein(
                if (vocal) t.creerSalonVocalNomme(nom) else t.creerSalonNomme(nom),
                actif = nom.isNotBlank(),
            ) {
                onCreer(nom, categorie, vocal)
                onFermer()
            }
            Box(Modifier.padding(bottom = tokens.espL))
        }
    }
}

@Composable
private fun ChampSalon(valeur: String, onChange: (String) -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = tokens.espM)
            .background(tokens.surfaceRaised, RoundedCornerShape(tokens.radiusMedium))
            .padding(horizontal = 14.dp, vertical = tokens.espM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(Icones.salon, tint = tokens.textMuted, taille = 18.dp)
        Box(Modifier.width(6.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = valeur,
            onValueChange = { texte ->
                onChange(
                    texte.lowercase()
                        .map { if (it.isLetterOrDigit() || it == '-') it else '-' }
                        .joinToString("")
                        .take(48)
                )
            },
            singleLine = true,
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                color = tokens.textPrimary,
                fontSize = tokens.typography.bodySize.sp,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * the server tag: one emoji, four letters
 *
 * ## what it is, and what the crown was not
 *
 * velvet showed a role tier next to the nicknames, `MEMBER`, a crown. that is something else. a
 * tier is computed from the matrix power in the room you are looking at, and says "can moderate
 * here". the reference's tag is chosen by the server, worn voluntarily by whoever wants it,
 * identical everywhere, and visible even to those who are not members. reported from outside, and
 * the distinction is right
 *
 * ## four characters
 *
 * the reference's bound, taken as is. beyond that the tag stops being a badge and becomes a second
 * name line, which is to say it makes unreadable the thing it is glued to
 *
 * ## the emoji can come from the server
 *
 * the server's custom emojis are offered next to the unicode characters: that is what the reference
 * does for its icon tags, and a server that already has its emoji has no reason to look for another
 */
@Composable
private fun EditionTagServeur(
    tag: li.velvetchat.core.model.TagServeur?,
    emotes: List<li.velvetchat.core.model.CustomEmote>,
    /**
     * the catalogue's badges, the reference's pixel art, published in the `tags` family
     *
     * a third source after the characters and the server's emojis, and the most faithful: what the
     * reference puts in its tags is an image, not a character
     */
    badges: List<li.velvetchat.core.model.DecorationAvatar>,
    onEnregistrer: (suspend (li.velvetchat.core.model.TagServeur?) -> Unit)?,
    onFini: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    var emoji by remember(tag) { mutableStateOf(tag?.emoji ?: "") }
    var texte by remember(tag) { mutableStateOf(tag?.texte ?: "") }
    var enCours by remember { mutableStateOf(false) }

    val lecture = onEnregistrer == null

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = tokens.espXl)) {
        Text(
            if (lecture) {
                t.tagDuServeurAide
            } else {
                t.tagPorteParLesMembres
            },
            color = tokens.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = tokens.espM),
        )

        // the preview at the top: it is what you are setting, so better shown than described
        if (emoji.isNotBlank() && texte.isNotBlank()) {
            Row(Modifier.padding(bottom = tokens.espM), verticalAlignment = Alignment.CenterVertically) {
                Text(t.apercu + " ", color = tokens.textMuted, fontSize = 12.sp)
                BadgeTag(
                    li.velvetchat.core.model.TagServeur(emoji, texte),
                    hauteur = 22f,
                )
            }
        }

        Text(
            t.texteMaxCaracteres(li.velvetchat.core.model.TagServeur.LONGUEUR_MAX),
            color = tokens.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (texte.isEmpty()) {
                Text("ERBS", color = tokens.textMuted, fontSize = 14.sp)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = texte,
                onValueChange = {
                    if (!lecture) {
                        texte = it.take(li.velvetchat.core.model.TagServeur.LONGUEUR_MAX)
                    }
                },
                singleLine = true,
                readOnly = lecture,
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = tokens.textPrimary,
                    fontSize = 14.sp,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            t.emoji,
            color = tokens.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = tokens.espL, bottom = 6.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EMOJIS_TAG.forEach { candidat ->
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(tokens.radiusMedium))
                        .background(
                            if (emoji == candidat) tokens.accent.copy(alpha = 0.25f)
                            else tokens.surfaceRaised
                        )
                        .clickable(enabled = !lecture) { emoji = candidat },
                    contentAlignment = Alignment.Center,
                ) { Text(candidat, fontSize = 20.sp) }
            }
            emotes.take(12).forEach { emote ->
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(tokens.radiusMedium))
                        .background(
                            if (emoji == emote.url) tokens.accent.copy(alpha = 0.25f)
                            else tokens.surfaceRaised
                        )
                        .clickable(enabled = !lecture) { emoji = emote.url }
                        .padding(9.dp),
                ) { EmojiEnLigne(emote.url) }
            }
            // the catalogue's badges, next in line: same box, same gesture. with no inner padding,
            // a badge being 64 px of pixel art, drawn to fill its whole box
            badges.forEach { badge ->
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(tokens.radiusMedium))
                        .background(
                            if (emoji == badge.mxc) tokens.accent.copy(alpha = 0.25f)
                            else tokens.surfaceRaised
                        )
                        .clickable(enabled = !lecture) { emoji = badge.mxc }
                        .padding(4.dp),
                ) { EmojiEnLigne(badge.apercu ?: badge.mxc) }
            }
        }

        if (!lecture) {
            Box(Modifier.height(tokens.espL))
            BoutonPlein(
                if (enCours) t.enregistrementEnCours else t.enregistrer,
                actif = !enCours && (
                    (emoji.isNotBlank() && texte.isNotBlank()) || tag != null
                    ),
            ) {
                enCours = true
                scope.launch {
                    // emptying both fields removes the tag: it is the gesture you naturally make to
                    // take it off, and it beats a second button
                    val nouveau = if (emoji.isBlank() || texte.isBlank()) null
                    else li.velvetchat.core.model.TagServeur(emoji, texte)
                    runCatching { onEnregistrer?.invoke(nouveau) }
                    enCours = false
                    onFini()
                }
            }
        }
        Box(Modifier.height(tokens.espEcran))
    }
}

/**
 * twelve emojis offered for a tag
 *
 * a short list rather than the full picker: a tag is chosen once per server, and opening a thousand
 * box picker for that would be disproportionate. the server's custom emojis are offered right next
 * to it, which covers the case where none of these will do
 */
private val EMOJIS_TAG = listOf(
    "\uD83D\uDC9F", "\u2B50", "\uD83D\uDD25", "\uD83C\uDF3F", "\uD83C\uDFAE", "\uD83C\uDFB5",
    "\uD83D\uDC8E", "\uD83C\uDF19", "\u2694\uFE0F", "\uD83C\uDFA8", "\uD83D\uDC3E", "\uD83C\uDF08",
)

/**
 * create a category, the reference's room grouping
 *
 * it was missing entirely, and it is the kind of hole you do not see from the code: the engine has
 * been able to create them from the start (`createCategory`, a subspace) and the panel can show
 * them collapsible (`EnteteCategorie`), but nothing created them. so a velvet server could only be
 * tidied from another matrix client
 *
 * the name is not normalised to lowercase and dashes like a room's: a category is not an address,
 * it is a subheading. the reference accepts spaces, capitals and emojis in them, and that is what
 * gives them their tone
 */
@Composable
fun FeuilleNouvelleCategorie(
    nomDuServeur: String,
    /** launched, not awaited, same reason as in [FeuilleServeur] */
    onCreer: (String) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var nom by remember { mutableStateOf("") }

    FeuilleBasse(onFermer) {
        Column(Modifier.padding(horizontal = tokens.espXl)) {
            Text(
                t.nouvelleCategorieDans(nomDuServeur),
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.headingSize.sp,
                modifier = Modifier.padding(bottom = tokens.espXs),
            )
            Text(
                t.categorieExplication,
                color = tokens.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Box(
                Modifier.fillMaxWidth().padding(vertical = tokens.espM)
                    .background(tokens.surfaceRaised, RoundedCornerShape(tokens.radiusMedium))
                    .padding(horizontal = 14.dp, vertical = tokens.espM)
            ) {
                if (nom.isEmpty()) {
                    Text(t.tagExemple, color = tokens.textMuted, fontSize = 14.sp)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = nom,
                    onValueChange = { nom = it.take(48) },
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        color = tokens.textPrimary,
                        fontSize = tokens.typography.bodySize.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BoutonPlein(t.creerLaCategorie, actif = nom.isNotBlank()) {
                onCreer(nom.trim())
                onFermer()
            }
            Box(Modifier.padding(bottom = tokens.espL))
        }
    }
}

/**
 * copies a `content://` into the cache and returns its path
 *
 * the engine uploads a file, and a `content://` is not a file, it is a door opened by the content
 * provider, readable by this app alone
 */
private suspend fun copierDansLeCache(
    contexte: android.content.Context,
    uri: android.net.Uri,
): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        // **the copy keeps the picked file's name, and the uniqueness moves to a folder.**
        //
        // it used to be `"$nom-$horodatage.$extension"`, and the form showed it: a sound picked
        // as `essai-son.wav` was proposed as `essai-son.wav-1790336167`, because the suggested
        // name is the path's stem and the stem now carried a timestamp. every import panel had
        // it, the soundboard is only where it became impossible to miss
        //
        // the timestamp still has to be somewhere, since two files picked in a row can share a
        // name. putting it one level up costs a directory and leaves the stem alone
        val brut = uri.lastPathSegment?.substringAfterLast('/')?.take(64) ?: "fichier"
        val nom = if (brut.contains('.')) brut else {
            val extension = contexte.contentResolver.getType(uri)?.substringAfterLast('/') ?: "bin"
            "$brut.$extension"
        }
        val cible = java.io.File(
            contexte.cacheDir.resolve("envois/${System.currentTimeMillis()}").apply { mkdirs() },
            nom,
        )
        contexte.contentResolver.openInputStream(uri)!!.use { entree ->
            cible.outputStream().use { sortie -> entree.copyTo(sortie) }
        }
        cible.absolutePath
    }.getOrNull()
}

package li.velvetchat.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.border
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.size
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.app.ui.*
import li.velvetchat.core.model.ModeNotification
import li.velvetchat.core.model.Profile
import li.velvetchat.core.model.Statut

/**
 * the direct messages column, in place of the channels one when you are on home
 *
 * taken off the screenshots: a rounded search field in the header, then friends, then the pinned
 * section, then "direct messages" and the list, each row carrying the avatar with its presence dot,
 * the nickname, the tag being worn, and the pin on the right
 *
 * what is not reproduced, and it is a decision already made elsewhere: nitro, the shop, the quests.
 * a good half of that column on the reference is monetisation, and the profile screen settled the
 * same question the same way. an entry opening a shop that does not exist is worth less than its
 * absence
 *
 * this is not `EcranMessages`, and that is worth saying: that screen exists and is portable, but it
 * is the phone's home, a full screen you open with its title bar and its floating card. here the
 * list is a permanent column next to the conversation. two shapes for one set of data, and what
 * gets shared is the models and the components, not the layout
 */
@Composable
fun ColonneMessagesPrivees(
    conversations: List<ConversationPrivee>,
    ouverte: String?,
    profil: Profile?,
    statut: Statut,
    decoration: String?,
    onOuvrir: (ConversationPrivee) -> Unit,
    onEpingler: (ConversationPrivee) -> Unit,
    /** what the right click offers. null leaves the rows with no menu */
    actionsDe: ((ConversationPrivee) -> ActionsConversation)? = null,
    /** the servers you are in, as id to name, for the menu's invite entry */
    serveurs: List<Pair<String, String>> = emptyList(),
    /** create a group conversation with these people. empty does nothing */
    onCreerGroupe: (List<String>) -> Unit = {},
    onProfil: () -> Unit,
    /** "shop": the cosmetics catalogue, which is to say edit profile */
    onBoutique: () -> Unit = {},
    /**
     * pending invites
     *
     * the desktop could join a server by link and nothing else: an invite arriving in the app had
     * neither a place to show nor a button to answer. here is where it goes, like on the phone,
     * above the conversations, because it is something to deal with and not something to browse
     */
    invitations: List<li.velvetchat.core.model.InvitationRecue> = emptyList(),
    onInvitation: (li.velvetchat.core.model.InvitationRecue) -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current
    var recherche by remember { mutableStateOf("") }
    var pinReplie by remember { mutableStateOf(false) }
    var nouveauGroupe by remember { mutableStateOf(false) }
    val focusRecherche = remember { androidx.compose.ui.focus.FocusRequester() }

    if (nouveauGroupe) {
        ModaleNouveauGroupe(
            // the unfiltered list: the search field narrows the conversations shown in the column,
            // and a picker that inherited it would silently hide people you had not typed
            candidats = conversations,
            onCreer = { ids -> nouveauGroupe = false; onCreerGroupe(ids) },
            onFermer = { nouveauGroupe = false },
        )
    }

    val filtrees = remember(conversations, recherche) {
        if (recherche.isBlank()) conversations
        else conversations.filter { it.nom.contains(recherche.trim(), ignoreCase = true) }
    }
    val epinglees = filtrees.filter { it.epingle }
    val autres = filtrees.filterNot { it.epingle }

    Column(Modifier.width(Largeurs.salons).fillMaxHeight().background(fondColonne(tokens.surface))) {
        // the search field fills the header at exactly the server one's height: the two columns
        // replace each other, and a header that changed height would make everything below it jump
        // at the very moment you switch view
        Box(
            Modifier.fillMaxWidth().height(ENTETE).deplaceFenetre().padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().height(32.dp)
                    .clip(RoundedCornerShape(tokens.radiusSmall))
                    .background(tokens.bg)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRecherche),
                    decorationBox = { champ ->
                        // centred and semibold while empty: on the reference it reads as a button
                        if (recherche.isEmpty()) {
                            Text(
                                t.rechercherOuLancer,
                                color = tokens.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        champ()
                    },
                )
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            item(key = "raccourcis") {
                Column {
                    LigneRaccourciMp(Icones.membres, t.amis) {}
                    LigneRaccourciMp(Icones.autocollant, t.boutique, nouveau = true, onClick = onBoutique)
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().height(1.dp).background(tokens.border))
                }
            }

            // an invitation sits among the conversations, with a dot
            //
            // it used to have its own section in capitals, its rule, and a click opened a sheet
            // that barred the bottom of the window for two buttons. reported, and rightly: what
            // is happening is that somebody wants to talk to you, and that is said the way an
            // unread message is said. the answer goes where the conversation would go
            items(invitations, key = { "inv-" + it.roomId }) { invitation ->
                val survol = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val survolee by survol.collectIsHoveredAsState()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 1.dp).height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (survolee) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .hoverable(survol)
                        .curseurMain().clickable { onInvitation(invitation) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(
                        userId = invitation.roomId,
                        displayName = invitation.nom,
                        avatarUrl = invitation.avatarUrl,
                        taille = 32.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        invitation.nom,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(tokens.unreadBadge)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text("1", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (epinglees.isNotEmpty()) {
                // "pin", collapsible by its chevron, like the reference
                item(key = "titre-pin") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp)
                            .curseurMain().clickable { pinReplie = !pinReplie },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("PIN", color = tokens.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Icone(
                            Icones.chevronBas, tint = tokens.textMuted, taille = 14.dp, description = null,
                            modifier = if (pinReplie) Modifier.rotate(-90f) else Modifier,
                        )
                    }
                }
                if (!pinReplie) items(epinglees, key = { "p-" + it.roomId }) {
                    LigneConversationPrivee(it, it.roomId == ouverte, onOuvrir, onEpingler, actionsDe, serveurs)
                }
            }

            item(key = "titre-mp") {
                // in normal case and with its plus to start a conversation, like the reference
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.messagesPrives, color = tokens.textMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Icone(
                        Icones.ajouter, tint = tokens.textMuted, taille = 16.dp, description = t.nouveauGroupe,
                        modifier = Modifier.curseurMain().clickable { nouveauGroupe = true },
                    )
                }
            }
            items(autres, key = { it.roomId }) {
                LigneConversationPrivee(it, it.roomId == ouverte, onOuvrir, onEpingler, actionsDe, serveurs)
            }
            // the mini bar floats over the bottom of the column, so there is room for it not to
            // hide the last rows
            item(key = "pied") { Spacer(Modifier.height(MINI_BARRE + 8.dp)) }
        }
    }
}

@Composable
private fun LigneRaccourciMp(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    libelle: String,
    nouveau: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().height(40.dp)
            .clip(RoundedCornerShape(tokens.radiusSmall))
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(icone, tint = tokens.textSecondary, taille = 22.dp, description = null)
        Spacer(Modifier.width(12.dp))
        Text(libelle, color = tokens.textSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (nouveau) {
            Text(
                "NOUVEAU", color = Color(0xFF1B1D22), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(DORE).padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun TitreSection(texte: String) {
    val tokens = LocalTokens.current
    Text(
        texte.uppercase(),
        color = tokens.textMuted,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun LigneConversationPrivee(
    conv: ConversationPrivee,
    actif: Boolean,
    onOuvrir: (ConversationPrivee) -> Unit,
    onEpingler: (ConversationPrivee) -> Unit,
    actionsDe: ((ConversationPrivee) -> ActionsConversation)? = null,
    serveurs: List<Pair<String, String>> = emptyList(),
) {
    val t = textes()
    val tokens = LocalTokens.current
    val survol = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val survolee by survol.collectIsHoveredAsState()
    val plaque = LocalProfils.current[conv.userId]?.plaque
    // the menu opens at the cursor and not under the row, same as a channel's
    var menu by remember { mutableStateOf<IntOffset?>(null) }
    val origine = remember { floatArrayOf(0f, 0f) }
    if (actionsDe != null) menu?.let { ou ->
        Popup(
            popupPositionProvider = AuCurseur(ou),
            onDismissRequest = { menu = null },
            properties = PopupProperties(focusable = true),
        ) {
            MenuConversation(conv, serveurs, actionsDe(conv), onFermer = { menu = null })
        }
    }
    Box(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (actif || survolee) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(survol)
            .onGloballyPositioned { val o = it.positionInWindow(); origine[0] = o.x; origine[1] = o.y }
            .pointerInput(conv.roomId) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                            val p = e.changes.first().position
                            menu = IntOffset(
                                (origine[0] + p.x).roundToInt(),
                                (origine[1] + p.y).roundToInt(),
                            )
                        }
                    }
                }
            }
            .curseurMain().clickable { onOuvrir(conv) },
    ) {
    // the person's nameplate behind the open or hovered row
    if (plaque != null && (actif || survolee)) {
        // `Crop` and not `FillBounds`. a nameplate is 448 by 84, a ratio of 5.33, and the row
        // changes width with the column: stretching it deformed them as soon as the handle was
        // dragged. cropped, it keeps its proportions and only loses what sticks out, which at so
        // close a ratio does not show
        Box(Modifier.matchParentSize()) {
            EmojiEnLigne(plaque, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
    }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(
                userId = conv.userId,
                displayName = conv.nom,
                avatarUrl = conv.avatarUrl,
                taille = 32.dp,
                decoration = LocalProfils.current[conv.userId]?.decoration,
            )
            PastillePresence(conv.presence, taille = 10.dp, couleurDuFond = tokens.surface)
        }
        Spacer(Modifier.width(12.dp))
        // the name and the tag together take what is left, and the badge is flush right
        //
        // it used to be a weighted name plus a weighted spacer, so the two shared the free space
        // evenly and the badge stopped halfway along the row, at a spot that moved with the length
        // of the name. found when the unread dot landed in the middle of a nameplate, on top of
        // the artwork, where nobody could see it. the pin had been sitting wrong for as long
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                conv.nom,
                color = if (actif || conv.nonLus > 0) tokens.textPrimary else tokens.textSecondary,
                fontWeight = if (conv.nonLus > 0) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            BadgeTagDe(conv.userId, hauteur = 16f)
        }
        when {
            // a ring the colour of the row, not decoration: the dot lands on the nameplate of the
            // open conversation, and eight pixels of dark red on that artwork read as nothing
            conv.nonLus == 0 && conv.marqueNonLu -> Box(
                Modifier.padding(horizontal = 3.dp).size(12.dp)
                    .clip(CircleShape).background(tokens.surface)
                    .padding(2.dp)
                    .clip(CircleShape).background(tokens.unreadBadge),
            )

            conv.nonLus > 0 -> Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(tokens.unreadBadge)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    conv.nonLus.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            conv.epingle -> Icone(
                Icones.epingleActif,
                tint = tokens.textMuted,
                taille = 14.dp,
                description = t.epinglee,
                modifier = Modifier.curseurMain().clickable { onEpingler(conv) },
            )
        }
    }
    }
}

/**
 * a direct conversation's header: the person's avatar and their nickname, where a channel carries
 * its hash. that is the only difference between the two headers
 */
@Composable
fun EnteteDirect(
    conv: ConversationPrivee,
    epingles: List<li.velvetchat.core.model.Message>? = null,
    onDesepingler: (li.velvetchat.core.model.Message) -> Unit = {},
    profilOuvert: Boolean = true,
    onProfil: () -> Unit = {},
    onRechercher: (String) -> Unit = {},
    alertes: suspend () -> List<li.velvetchat.core.model.AlerteSalon> = { emptyList() },
    onAlerte: (li.velvetchat.core.model.AlerteSalon) -> Unit = {},
    onAppeler: () -> Unit = {},
    onAppelerVideo: () -> Unit = onAppeler,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        // measured off the top bar screenshot: a 20 avatar and a semibold 15 name on the left, then
        // call, video, pins, profile, search and inbox on the right
        Modifier.fillMaxWidth().height(ENTETE).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(
                userId = conv.userId,
                displayName = conv.nom,
                avatarUrl = conv.avatarUrl,
                taille = 20.dp,
            )
            PastillePresence(conv.presence, taille = 8.dp, couleurDuFond = tokens.chatBg)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            conv.nom,
            color = tokens.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        BadgeTagDe(conv.userId)
        Spacer(Modifier.weight(1f))
        ActionEntete(Icones.appeler, t.lancerAppelVocal, onClick = onAppeler)
        ActionEntete(Icones.camera, t.lancerAppelVideo, onClick = onAppelerVideo)
        Box {
            var ouvertes by remember { mutableStateOf(false) }
            ActionEntete(Icones.epingleActif, t.messagesEpingles, actif = ouvertes) { ouvertes = !ouvertes }
            if (ouvertes) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopEnd,
                    offset = androidx.compose.ui.unit.IntOffset(0, 42),
                    onDismissRequest = { ouvertes = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                ) { PopoverEpingles(epingles, onDesepingler) }
            }
        }
        ActionEntete(Icones.smileyPlein, if (profilOuvert) t.masquerLeProfil else t.afficherLeProfil, actif = profilOuvert, onClick = onProfil)
        Spacer(Modifier.width(8.dp))
        ChampRechercheEntete(
            t.rechercherPersonne(conv.userId.removePrefix("@").substringBefore(':')),
            onRechercher,
        )
        Spacer(Modifier.width(8.dp))
        BoutonBoiteReception(alertes, onAlerte)
    }
}

/**
 * the person's profile, to the right of a direct conversation: banner, an 80 avatar with its state,
 * nickname, id and tag, their bio, then "see full profile" at the bottom
 *
 * "member since" is not reproduced: the protocol gives no registration date
 */
@Composable
fun PanneauProfilDirect(conv: ConversationPrivee, onVoirProfil: () -> Unit, modifier: Modifier = Modifier) {
    val t = textes()
    val etendu = LocalProfils.current[conv.userId]
    val couleurs = couleursProfil(etendu)
    // the frame and the effect were missing here, while the member card and the big card have
    // carried them from the start, so the same person did not have the same profile depending on
    // which door you came in through
    BoxWithConstraints(modifier.width(Largeurs.membres + 44.dp).fillMaxHeight()) {
    val marges = margesDuCadre(etendu?.cadre, maxWidth)
    Box(Modifier.fillMaxSize().padding(marges)) {
    etendu?.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize(), derriere = true) }
    Column(Modifier.matchParentSize().background(couleurs.fond)) {
        EnteteCarteProfil(
            userId = conv.userId,
            nom = conv.nom,
            avatarUrl = conv.avatarUrl,
            etendu = etendu,
            couleurs = couleurs,
            presence = conv.presence,
            message = null,
            banniere = 105.dp,
            avatar = 80.dp,
            anneau = 6.dp,
            decalage = 49.dp,
            pastille = 16.dp,
        )
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(10.dp))
            NomCarteProfil(conv.nom, etendu, couleurs)
            LigneIdentifiantProfil(conv.userId, etendu, couleurs)
            etendu?.bio?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = couleurs.texteFort, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Text(
            t.voirProfilComplet,
            color = couleurs.texteFort,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.07f)).curseurMain().clickable(onClick = onVoirProfil)
                .padding(vertical = 10.dp),
        )
    }
    etendu?.effet?.let {
            // clipped to the card: the effect used to bleed onto whatever was around it
            Box(Modifier.matchParentSize().clipToBounds().zIndex(2f)) { EffetDeProfil(it) }
        }
    etendu?.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize()) }
    }
    }
}

/**
 * the right click menu on a direct conversation
 *
 * same entries as the phone's sheet, in the desktop's grammar: `EntreeMenu`, rules, a submenu that
 * replaces the page rather than opening beside it. what is shared between the two platforms is
 * [ActionsConversation], so an entry added on one side has nowhere to hide on the other
 */
@Composable
private fun MenuConversation(
    conv: ConversationPrivee,
    serveurs: List<Pair<String, String>>,
    actions: ActionsConversation,
    onFermer: () -> Unit,
) {
    val t = textes()
    var choisitDuree by remember { mutableStateOf(false) }
    var choisitServeur by remember { mutableStateOf(false) }
    var choisitMode by remember { mutableStateOf(false) }
    // read here and not threaded down from the desktop: it serves only to tick the right dot, it
    // costs one call when the menu opens, and a state passed through three layers for a tick is
    // three places to forget to refresh
    val moteur = li.velvetchat.app.ui.LocalEngine.current
    var mode by remember(conv.roomId) { mutableStateOf<ModeNotification?>(null) }
    LaunchedEffect(conv.roomId) {
        mode = runCatching { moteur.modeNotification(conv.roomId) }.getOrNull()
    }
    // two clicks to block, like deleting a channel. a mis-click on the last entry of a menu is the
    // one nobody notices before it has happened
    var confirmeBloquer by remember { mutableStateOf(false) }
    fun puis(action: () -> Unit): () -> Unit = { action(); onFermer() }
    Column(
        Modifier.width(254.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FOND_MENU)
            .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        if (choisitServeur) {
            EntreeMenu(t.retour, onClick = { choisitServeur = false })
            FiletMenu()
            serveurs.forEach { (id, nomServeur) ->
                EntreeMenu(nomServeur, onClick = puis { actions.onInviterServeur(id) })
            }
            return@Column
        }
        // the notification mode, which the phone's sheet had and this menu did not draw at all
        //
        // four choices and not three: `DEFAUT` says this conversation has no opinion, so no push
        // rule is its own and it follows the account setting including when that changes. `TOUT`
        // pins it to everything. the protocol carries the distinction natively, an absent rule
        // being the default, which is why the first entry recalls what the default is worth today
        if (choisitMode) {
            EntreeMenu(t.retour, onClick = { choisitMode = false })
            FiletMenu()
            listOf(
                Triple(ModeNotification.DEFAUT, t.utiliserCategorieParDefaut, t.tousLesMessages),
                Triple(ModeNotification.TOUT, t.tousLesMessages, null),
                Triple(ModeNotification.MENTIONS, t.mentionsSeulement, null),
                Triple(ModeNotification.MUET, t.aucunMessage, null),
            ).forEach { (valeur, libelle, sousTitre) ->
                EntreeMenu(
                    libelle,
                    sousTitre = sousTitre,
                    // nothing ticked until the read came back: better no dot than a wrong one
                    radio = mode?.let { it == valeur } ?: false,
                    onClick = puis { actions.onModeNotification(valeur) },
                )
            }
            return@Column
        }
        if (choisitDuree) {
            EntreeMenu(t.retour, onClick = { choisitDuree = false })
            FiletMenu()
            DureeSourdine.entries.forEach { duree ->
                EntreeMenu(
                    when (duree) {
                        DureeSourdine.QUINZE -> t.sourdine15Min
                        DureeSourdine.UNE_HEURE -> t.sourdine1Heure
                        DureeSourdine.HUIT_HEURES -> t.sourdine8Heures
                        DureeSourdine.UN_JOUR -> t.sourdine24Heures
                        DureeSourdine.TOUJOURS -> t.sourdineToujours
                    },
                    onClick = puis { actions.onMuet(duree) },
                )
            }
            return@Column
        }

        EntreeMenu(
            if (conv.epingle) t.retirerDesEpinglees else t.epinglerEnHaut,
            onClick = puis(actions.onEpingler),
        )
        EntreeMenu(
            if (conv.nonLus > 0 || conv.marqueNonLu) t.marquerCommeLu else t.marquerCommeNonLu,
            onClick = puis(actions.onMarquerLu),
        )
        FiletMenu()
        if (!conv.groupe) EntreeMenu(t.voirLeProfil, onClick = puis(actions.onProfil))
        EntreeMenu(t.appeler, onClick = puis(actions.onAppeler))
        if (!conv.groupe) {
            EntreeMenu(t.ficheNote, onClick = puis(actions.onNote))
            EntreeMenu(t.ficheSurnom, onClick = puis(actions.onSurnom))
        }
        if (serveurs.isNotEmpty()) {
            EntreeMenu(t.inviterSurLeServeur, sousMenu = true, onClick = { choisitServeur = true })
        }
        FiletMenu()
        EntreeMenu(
            t.notifications,
            sousTitre = mode?.libelle,
            sousMenu = true,
            onClick = { choisitMode = true },
        )
        if (conv.muet) {
            EntreeMenu(t.reactiverNotifications, onClick = puis { actions.onMuet(null) })
        } else {
            EntreeMenu(t.rendreMuet, sousMenu = true, onClick = { choisitDuree = true })
        }
        FiletMenu()
        EntreeMenu(
            if (conv.groupe) t.supprimerLeGroupe else t.fermerLeMp,
            couleur = ROSE_MENU,
            onClick = puis(actions.onFermerMp),
        )
        if (!conv.groupe && conv.ami) {
            EntreeMenu(t.ficheRetirerAmi, couleur = ROSE_MENU, onClick = puis(actions.onRetirerAmi))
        }
        if (!conv.groupe) {
            EntreeMenu(
                if (confirmeBloquer) t.confirmerLaSuppression else t.ficheBloquer,
                couleur = ROSE_MENU,
                onClick = {
                    if (confirmeBloquer) puis(actions.onBloquer)() else confirmeBloquer = true
                },
            )
        }
    }
}

/**
 * a new group conversation: pick people, create
 *
 * what it does not ask for, deliberately: a name. matrix has no group direct message, what it has
 * is a private unnamed room marked `is_direct`, and the reference names a group after its members
 * too. a name here would freeze a list that changes as people come and go, and it would turn the
 * room into something that shows in a rail rather than in this column
 *
 * the plus beside "direct messages" used to put the cursor in the search field, which answers a
 * different question: the field opens a conversation with one person, this opens one with several
 *
 * two is the floor, and the button says so rather than failing on click. with one person the right
 * call is `openDirect`, which finds the conversation that already exists instead of making a
 * second one beside it
 */
@Composable
private fun ModaleNouveauGroupe(
    candidats: List<ConversationPrivee>,
    onCreer: (List<String>) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    var choisis by remember { mutableStateOf(setOf<String>()) }
    CarteModale(onFermer = onFermer) {
        Text(t.nouveauGroupe, color = CREME_SERVEUR, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            if (choisis.size >= 2) t.groupeChoisis(choisis.size) else t.groupeChoisirPersonnes,
            color = TEXTE_GRIS,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        ChoixPersonnes(
            candidats = candidats,
            choisis = choisis,
            onBasculer = { id -> choisis = if (id in choisis) choisis - id else choisis + id },
        )
        Spacer(Modifier.height(16.dp))
        BoutonDore(t.creerLeGroupe, actif = choisis.size >= 2) { onCreer(choisis.toList()) }
    }
}

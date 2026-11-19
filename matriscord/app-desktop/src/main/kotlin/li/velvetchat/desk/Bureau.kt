package li.velvetchat.desk

import li.velvetchat.core.tr

import kotlinx.coroutines.flow.first
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.input.TextFieldValue
import li.velvetchat.core.essayer
import li.velvetchat.core.model.CustomEmote
import li.velvetchat.core.model.Member
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.Categorie
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.Decorations
import li.velvetchat.core.model.Server
// a wildcard import of the shared package: the theme shortcuts (`tokens.textPrimary`, `tokens.espL`
// and the rest) are extension properties, which only resolve when imported by name. listing them
// one by one would be twenty five import lines for one idea
import li.velvetchat.app.ui.*

/**
 * the three columns, side by side instead of stacked
 *
 * that is the only real difference in shape from the phone, and it is not decoration: on mobile,
 * opening a channel replaces the list, and here both stay visible, which is precisely what a wide
 * screen is for. the components are the same, `Avatar`, the theme tokens, the icons, all from `ui-
 * commun`
 */
@Composable
fun Bureau(moteur: MatrixEngine, onDeconnexion: () -> Unit = {}) {
    val t = textes()
    val tokens = LocalTokens.current

    var serveurs by remember { mutableStateOf<List<Server>>(emptyList()) }
    var serveurOuvert by remember { mutableStateOf<String?>(null) }
    var salonOuvert by remember { mutableStateOf<String?>(null) }

    // the server's members, their presences, and what colours their nicknames. one read per open
    // server and not per channel: the list is the same for a whole space
    var membres by remember { mutableStateOf<List<li.velvetchat.core.model.Member>>(emptyList()) }
    var presences by remember {
        mutableStateOf<Map<String, li.velvetchat.core.model.Presence>>(emptyMap())
    }
    var statutsMembres by remember {
        mutableStateOf<Map<String, li.velvetchat.core.model.Statut>>(emptyMap())
    }
    var paliers by remember { mutableStateOf<List<li.velvetchat.core.model.RoleTier>>(emptyList()) }
    var decors by remember { mutableStateOf(li.velvetchat.core.model.Decorations()) }

    // cosmetics live in each person's profile and not in the server. avatar decoration, banner,
    // nameplate, tag, all of it is there, and that is what makes them displayable everywhere,
    // including for somebody whose home server you do not know
    var profils by remember {
        mutableStateOf<Map<String, li.velvetchat.core.model.ProfilEtendu>>(emptyMap())
    }

    LaunchedEffect(moteur) {
        moteur.serversFlow().collectLatest { liste ->
            serveurs = liste
            // open something on the first sync. an empty window at startup makes it look like
            // nothing arrived, when everything is there
            if (serveurOuvert == null) {
                val premier = liste.firstOrNull()
                serveurOuvert = premier?.id
                salonOuvert = premier?.channels?.firstOrNull()?.id
            }
        }
    }

    val serveur = serveurs.firstOrNull { it.id == serveurOuvert }
    val salon = serveur?.channels?.firstOrNull { it.id == salonOuvert }

    /**
     * the open room's pinned messages, for the header panel
     *
     * a flow and not a single read: a pin placed from element or from the phone has to appear here
     * without reopening the room. null while we do not know, since the panel tells "reading" from
     * "no pins", which look alike on screen
     */
    var epinglesSalon by remember(salon?.id) {
        mutableStateOf<List<li.velvetchat.core.model.Message>?>(null)
    }

    /**
     * what rereads the pins, incremented every time one is placed or removed
     *
     * `pinnedFlow` is a one pass flow: it reads the state once, emits its messages as it finds
     * them, then finishes. nothing wakes it afterwards, and nothing can, since sliding sync does
     * not carry `m.room.pinned_events` (the engine documents it, measured). seen on screen: you
     * pin, the room records it, two ids in the state verified server side, and the panel carries on
     * showing the old list
     *
     * hence this key rather than a real time promise we cannot keep: a local change rereads
     * immediately, and a change from elsewhere shows up on reopening
     */
    var versionEpingles by remember(salon?.id) { mutableStateOf(0) }
    LaunchedEffect(salon?.id, versionEpingles) {
        val id = salon?.id ?: return@LaunchedEffect
        essayer { moteur.pinnedFlow(id).collect { epinglesSalon = it } }
            .onFailure { grogner("epingles du salon illisibles", it) }
    }

    /**
     * the threads open in the room, for the header panel
     *
     * same shape as the pins and for the same reason: a thread is born from an ordinary message, so
     * the list has to grow while you are looking at it
     */
    var filsDuSalon by remember(salon?.id) {
        mutableStateOf<List<li.velvetchat.core.model.Fil>?>(null)
    }
    LaunchedEffect(salon?.id) {
        val id = salon?.id ?: return@LaunchedEffect
        essayer { moteur.filsFlow(id).collect { filsDuSalon = it } }
            .onFailure { grogner("fils du salon illisibles", it) }
    }

    /** what the open room rings for, for the header's bell */
    var modeDuSalon by remember(salon?.id) {
        mutableStateOf<li.velvetchat.core.model.ModeNotification?>(null)
    }
    LaunchedEffect(salon?.id) {
        val id = salon?.id ?: return@LaunchedEffect
        modeDuSalon = runCatching { moteur.modeNotification(id) }.getOrNull()
    }


    // the muted channels, greyed in the column. reread when you change server or setting
    var salonsMuets by remember { mutableStateOf<Set<String>>(emptySet()) }

    // the invites received. on the dedicated flow and not on the server list, which is exactly the
    // case that matters, an invite received when you have no server yet
    var invitations by remember {
        mutableStateOf<List<li.velvetchat.core.model.InvitationRecue>>(emptyList())
    }
    var invitationVisee by remember {
        mutableStateOf<li.velvetchat.core.model.InvitationRecue?>(null)
    }
    LaunchedEffect(moteur) {
        essayer { moteur.invitationsFlow().collect { invitations = it } }
            .onFailure { grogner("invitations illisibles", it) }
    }

    // who is already in each voice room. without that count an occupied room and an empty one look
    // exactly alike, and nobody joins the first
    var personnesBloquees by remember { mutableStateOf<Set<String>>(emptySet()) }
    // the whole friends list and not one `estAmi` per card: the conversation menu draws its
    // "remove friend" entry for every row, so asking per row would be one request per redraw
    var mesAmis by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(moteur) {
        personnesBloquees = runCatching { moteur.personnesBloquees().toSet() }
            .getOrDefault(emptySet())
        mesAmis = runCatching { moteur.amis().toSet() }.getOrDefault(emptySet())
    }

    // who, and not only how many: the reference lists the people under the room
    var enVocal by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val idsVocaux = serveurs.flatMap { it.channels }
        .filter { it.kind == li.velvetchat.core.model.ChannelKind.VOICE }
        .map { it.id }
    LaunchedEffect(idsVocaux) {
        if (idsVocaux.isEmpty()) {
            enVocal = emptyMap()
            return@LaunchedEffect
        }
        essayer {
            moteur.vocauxFlow(idsVocaux).collect { parSalon ->
                enVocal = parSalon
            }
        }
            .onFailure { grogner("presences vocales illisibles", it) }
    }
    LaunchedEffect(serveurOuvert, modeDuSalon) {
        salonsMuets = runCatching { moteur.salonsMuets() }.getOrDefault(salonsMuets)
    }

    // the open server's tables: they have to change with it rather than stay on the previous one's
    LaunchedEffect(serveurOuvert) {
        val id = serveurOuvert
        paliers = id?.let { runCatching { moteur.roleTiers(it) }.getOrDefault(emptyList()) }
            ?: emptyList()
        decors = id?.let { runCatching { moteur.decorations(it) }.getOrDefault(Decorations()) }
            ?: Decorations()
        val premier = serveurs.firstOrNull { it.id == id }?.channels?.firstOrNull()?.id
        membres = premier?.let { runCatching { moteur.members(it) }.getOrDefault(emptyList()) }
            ?: emptyList()
    }

    // the presences, in one request for the whole list. one per member would be a round of twenty
    // requests for one column
    //
    // a flow and not a single read. reported: somebody set to invisible on their profile still
    // showed as do not disturb in the member rail. the column read the presences once, when the
    // member list arrived, and never read them again, neither when somebody changed state nor when
    // you changed your own. `presencesFlow` emits immediately then refreshes, and never returns,
    // hence the profiles being read before it
    LaunchedEffect(membres) {
        if (membres.isEmpty()) {
            presences = emptyMap()
            statutsMembres = emptyMap()
            return@LaunchedEffect
        }
        val qui = membres.map { it.userId }
        profils = profils + runCatching { moteur.profilsEtendus(qui) }.getOrDefault(emptyMap())
        // the whole status is kept for the member column: its message makes the subtitle
        moteur.presencesFlow(qui).collect { lus ->
            statutsMembres = lus
            presences = lus.mapValues { (_, statut) -> statut.presence }
        }
    }

    val couleurs = remember(membres, paliers, decors) {
        couleursDeRole(membres, paliers, decors)
    }
    val palierDes = remember(membres, paliers) { paliersParMembre(membres, paliers) }

    // my profile and my state, for the bottom bar of the channel column
    var profil by remember { mutableStateOf<li.velvetchat.core.model.Profile?>(null) }
    var statut by remember {
        mutableStateOf(
            li.velvetchat.core.model.Statut(
                li.velvetchat.core.model.Presence.INCONNU,
            ),
        )
    }
    var monProfilEtendu by remember {
        mutableStateOf<li.velvetchat.core.model.ProfilEtendu?>(null)
    }
    LaunchedEffect(moteur) {
        runCatching { moteur.profile() }.onSuccess { profil = it }
        val id = runCatching { moteur.currentSession().userId }.getOrNull() ?: return@LaunchedEffect

        // the status comes back from what you chose, not from what the server last saw
        //
        // reported: velvetdesk came back invisible at every launch, and stayed there for
        // everybody. matrix has no invisible state, we send `offline` for it, and the server
        // marks you `offline` on its own the moment you close the app. so reading your own
        // presence back gave `offline`, which the screen reads as invisible, which meant the app
        // never asserted anything and you really were invisible. the protocol cannot tell those
        // two apart, so the choice has to be kept on our side, as the phone already does
        //
        // asserted and not only shown: without the write, synapse keeps whatever it had
        val voulu = runCatching {
            Reglages.lire("statut.presence")
                ?.let { li.velvetchat.core.model.Presence.valueOf(it) }
        }.getOrNull() ?: li.velvetchat.core.model.Presence.EN_LIGNE
        val message = Reglages.lire("statut.message")?.takeIf { it.isNotBlank() }
        statut = li.velvetchat.core.model.Statut(voulu, message, null)
        runCatching { moteur.setPresence(voulu, message) }

        runCatching { moteur.profilEtendu(id) }.onSuccess { monProfilEtendu = it }
    }

    // the member panel closes, like on the reference: it is the rightmost button in the header, and
    // the conversation takes the space back
    var membresOuverts by remember { mutableStateOf(true) }
    /** the thread open to the right of the conversation: its root message */
    var filOuvert by remember(salon?.id) { mutableStateOf<Message?>(null) }
    // "create" from the thread list: a thread with no originating message
    var nouveauFil by remember(salon?.id) { mutableStateOf(false) }
    var rechercheSalon by remember(salon?.id) { mutableStateOf<String?>(null) }
    var cibleSalon by remember(salon?.id) { mutableStateOf<Message?>(null) }

    /** the popover for your own profile, opened from the bottom bar */
    var popoverProfil by remember { mutableStateOf(false) }

    /** opened from a message's avatar: the card's left edge. null when it comes from the column */
    var ficheGauche by remember { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }

    /** the card of the member clicked in the column, and the top of their row */
    // friend nicknames, read once: the card shows them and writes them
    var surnoms by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) { surnoms = runCatching { moteur.surnoms() }.getOrDefault(emptyMap()) }
    var ficheMembre by remember {
        mutableStateOf<Pair<li.velvetchat.core.model.Member, androidx.compose.ui.unit.Dp>?>(null)
    }

    /** the edit profile modal, opened from this popover */
    var editionProfil by remember { mutableStateOf(false) }
    var parametresUtilisateur by remember { mutableStateOf(false) }
    var silence by remember { mutableStateOf(false) }
    LaunchedEffect(moteur) { runCatching { moteur.silence() }.onSuccess { silence = it } }

    // home, which is to say the direct messages. the rail switches between it and a server, and the
    // rest of the window follows. a boolean and not a third state of `serveurOuvert`: dms have no
    // server id, and inventing one would make everything that reads that variable lie
    var surAccueil by remember { mutableStateOf(false) }
    var directsBruts by remember {
        mutableStateOf<List<li.velvetchat.core.model.DirectMessage>>(emptyList())
    }
    var directOuvert by remember { mutableStateOf<String?>(null) }
    // the message sound: when the total of notifying unreads goes up, channels and dms, except in
    // do not disturb. muted channels count no notifications, so they do not ring
    val totalNotifs = serveurs.sumOf { sv -> sv.channels.sumOf { it.unread.notifications } } +
        directsBruts.sumOf { it.unread.notifications }
    var dernierTotal by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(totalNotifs) {
        val avant = dernierTotal
        dernierTotal = totalNotifs
        if (avant != null && totalNotifs > avant && !silence) Sons.message()
    }
    // the calls that ring, across every room and dm. a refused call stays refused while its marker
    // lives (the timestamp is refreshed every 45 s, so it cannot serve as a key), and restarted
    // after hanging up it rings again
    var appels by remember { mutableStateOf<Map<String, li.velvetchat.core.model.AppelEnCours>>(emptyMap()) }
    var appelsRefuses by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(appels.keys) { appelsRefuses = appelsRefuses.filter { it in appels }.toSet() }
    // a voice room does not ring. you go in when you want, like a room with an open door, and a
    // ring is for a private call, which is addressed to somebody. making the whole server ring
    // because one person walked into a voice room is the opposite of what it is
    val idsSalons = directsBruts.map { it.roomId } +
        serveurs.flatMap { it.channels }
            .filterNot { it.kind == li.velvetchat.core.model.ChannelKind.VOICE }
            .map { it.id }
    LaunchedEffect(idsSalons.toSet()) { moteur.appelsEnCoursFlow(idsSalons).collect { appels = it } }
    val moiId = remember { runCatching { moteur.currentSession().userId }.getOrNull() }
    val appelEntrant = appels.values.firstOrNull {
        it.appelant != moiId && it.salonId !in appelsRefuses && AppelBureau.etat.salonId != it.salonId
    }
    LaunchedEffect(appelEntrant?.salonId) {
        if (appelEntrant != null && !silence) Sons.sonner() else Sons.arreterSonnerie()
    }
    var salonARenommer by remember { mutableStateOf<li.velvetchat.core.model.Channel?>(null) }
    // the inbox opens the conversation, direct or inside its server
    fun ouvrirAlerte(alerte: li.velvetchat.core.model.AlerteSalon) {
        if (alerte.estDirect) {
            surAccueil = true
            directOuvert = alerte.salonId
        } else {
            val hote = serveurs.firstOrNull { s -> s.channels.any { it.id == alerte.salonId } } ?: return
            surAccueil = false
            serveurOuvert = hote.id
            salonOuvert = alerte.salonId
        }
    }

    LaunchedEffect(moteur) {
        moteur.directsFlow().collectLatest { directsBruts = it }
    }
    // the correspondents' profiles too: the nameplate behind the row, the profile panel on the
    // right
    LaunchedEffect(directsBruts.map { it.userId }.toSet()) {
        val qui = directsBruts.map { it.userId }.filter { it !in profils }
        if (qui.isNotEmpty()) profils = profils + runCatching { moteur.profilsEtendus(qui) }.getOrDefault(emptyMap())
    }
    /**
     * the profile panel to the right of a direct conversation, open by default like the reference
     */
    var profilDirectOuvert by remember { mutableStateOf(true) }
    var rechercheDirect by remember { mutableStateOf<String?>(null) }
    var cibleDirect by remember { mutableStateOf<Message?>(null) }
    var epinglesDirect by remember(directOuvert) { mutableStateOf<List<Message>?>(null) }
    var versionEpinglesDirect by remember(directOuvert) { mutableStateOf(0) }
    LaunchedEffect(directOuvert, versionEpinglesDirect) {
        val id = directOuvert ?: return@LaunchedEffect
        essayer { moteur.pinnedFlow(id).collect { epinglesDirect = it } }
            .onFailure { grogner("epingles du prive illisibles", it) }
    }

    // the presence is joined here and not in the collector, and the nuance is not cosmetic: the
    // presences arrive after the list, through a second request. reading them inside the collector
    // would freeze them at their value when the flow emitted, so at unknown, and no dot would ever
    // appear
    // the card wants a `Member`, the list has a `ConversationPrivee`. same conversion as opening
    // it from a conversation header, kept in one place since three menu entries land on it
    fun fiche(c: ConversationPrivee) =
        li.velvetchat.core.model.Member(c.userId, c.nom, c.avatarUrl, null) to 60.dp

    val directs = remember(directsBruts, presences, mesAmis, salonsMuets) {
        directsBruts.map { d ->
            ConversationPrivee(
                roomId = d.roomId,
                userId = d.userId,
                nom = d.name,
                avatarUrl = d.avatarUrl,
                apercu = d.preview,
                horodatage = d.timestamp,
                nonLus = d.unread.notifications,
                marqueNonLu = d.unread.marqueNonLu,
                epingle = d.pinned,
                groupe = d.groupe,
                ami = d.userId in mesAmis,
                muet = d.roomId in salonsMuets,
                presence = presences[d.userId]
                    ?: li.velvetchat.core.model.Presence.INCONNU,
            )
        }
    }

    // who i am, which is what tells "somebody is being talked about" from "somebody is talking to
    // me", so the gold mention from the blue one
    val moi = remember(moteur) { runCatching { moteur.currentSession().userId }.getOrNull() }
    // the relation with the member whose card is open: the banner's friend button depends on it
    var ficheAmi by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(ficheMembre?.first?.userId) {
        ficheAmi = ficheMembre?.first?.userId?.takeIf { it != moi }
            ?.let { runCatching { moteur.estAmi(it) }.getOrNull() }
    }
    // the custom emoji of every server. the desktop did not supply them, so the reaction picker
    // only offered unicode ones
    var packs by remember { mutableStateOf<List<li.velvetchat.core.model.EmotePack>>(emptyList()) }
    LaunchedEffect(moteur) { packs = runCatching { moteur.emotePacks() }.getOrDefault(emptyList()) }

    CompositionLocalProvider(
        LocalEngine provides moteur,
        LocalMonIdentifiant provides moi,
        // what colours the nicknames in the conversation and in the member column. both read the
        // same map: a role hierarchy that differed from one column to the other would be the kind
        // of gap you only see by looking at them together, which on a wide screen is the default
        LocalRoleColors provides couleurs,
        LocalRoleTiers provides palierDes,
        LocalProfils provides profils,
        LocalPacks provides packs,
        // what `#` offers. the phone supplies them the same way, and the shared autocomplete reads
        // them there rather than receiving them as a parameter through four floors
        LocalSalons provides (serveurs.firstOrNull { it.id == serveurOuvert }?.channels ?: emptyList()),
    ) {
        // one clock loop for the whole window, see `PiloteAnimations`
        PiloteAnimations()

        // the confirmation for a link whose label announces something other than its destination.
        // it lives at the root and not in the row: it is a window and not a piece of message, and
        // one per message would be one per message displayed
        val navigateur = androidx.compose.ui.platform.LocalUriHandler.current
        val pressePapier = copieur()
        DialogueLien(
            onOuvrir = { url -> runCatching { navigateur.openUri(url) } },
            onCopier = { url -> pressePapier(url) },
        )

        val porteeBureau = rememberCoroutineScope()

        // the rich presence, which is two things in a trench coat: a fake discord listening on a
        // named pipe, and a brake deciding what of it reaches the server
        //
        // right here because this is the first line that has both the engine and a scope, and
        // `DisposableEffect(Unit)` so it dies with the window rather than with whichever
        // recomposition happened to be passing
        androidx.compose.runtime.DisposableEffect(Unit) {
            RichPresence.demarrer()
            // the second door: a web page cannot open a named pipe, so discord also listens on
            // tcp 6463-6472. same protocol, same brain, different wrapper
            ServeurWs.demarrer()
            PresencePubliee.suivre(porteeBureau, moteur)
            onDispose {
                RichPresence.arreter()
                ServeurWs.arreter()
                // best effort, nobody waits. if it does not land, the five minute expiry on the
                // reader's side cleans up after us, which is its entire purpose
                porteeBureau.launch { PresencePubliee.effacer(moteur) }
            }
        }
        /** is the "create your server" modal open? */
        var creerServeur by remember { mutableStateOf(false) }
        /** the server menu's modals: which one is open, on the displayed server */
        var modaleServeur by remember { mutableStateOf<String?>(null) }
        // what a background job says when it fails. the engine shows a gesture before synapse
        // confirms it, so without this a failure would just take the tile back without a word
        var messageFugace by remember { mutableStateOf<String?>(null) }

        // true fullscreen: the app fades out, only the image is left
        //
        // not a bigger tile, not a folded column: nothing at all. somebody showing their screen
        // wants their screen read, and the rest of the app is no use during that time. escape
        // brings it all back, and the gesture also undoes itself from the bar that reappears
        // when the mouse looks for an edge
        if (AppelBureau.ecranEntier) {
            androidx.compose.runtime.CompositionLocalProvider(LocalMembres provides membres) {
                EcranAppelBureau(Modifier.fillMaxSize(), monProfil = profil)
            }
            return@CompositionLocalProvider
        }

        Box(Modifier.fillMaxSize()) {
        // the chosen wallpaper, under every column
        CoucheFond()
        Row(Modifier.fillMaxSize().background(
            if (FondEcran.actif) androidx.compose.ui.graphics.Color.Transparent else tokens.chatBg,
        )) {
            Rail(
                serveurs = serveurs,
                ouvert = if (surAccueil) null else serveurOuvert,
                surAccueil = surAccueil,
                onAccueil = { surAccueil = true },
                // one read receipt per room and per conversation: matrix has no "mark everything
                // read"
                onToutLire = {
                    porteeBureau.launch {
                        (serveurs.flatMap { it.channels }.map { it.id } + directs.map { it.roomId })
                            .forEach { runCatching { moteur.markRead(it) } }
                    }
                },
                onChoisir = { id ->
                    surAccueil = false
                    serveurOuvert = id
                    salonOuvert = serveurs.firstOrNull { it.id == id }
                        ?.channels?.firstOrNull()?.id
                },
                onAjouter = { creerServeur = true },
                directsNonLus = directs.filter { it.nonLus > 0 && !(surAccueil && it.roomId == directOuvert) },
                onDirect = { surAccueil = true; directOuvert = it.roomId },
                onReordonner = { ids ->
                    porteeBureau.launch { runCatching { moteur.setOrdreServeurs(ids) } }
                },
            )

            // the modal goes through a `Popup`, so its place in the tree does not decide what it
            // covers: written here next to the rail that opens it, it still draws over the whole
            // window
            if (creerServeur) {
                ModaleCreerServeur(
                    onCreer = { nom, image ->
                        creerServeur = false
                        porteeBureau.launch {
                            // we open the created server straight away: creating one and staying on
                            // the previous list would mean going to look for it yourself
                            runCatching { moteur.createServer(nom) }.onSuccess { cree ->
                                surAccueil = false
                                serveurOuvert = cree.id
                                salonOuvert = cree.channels.firstOrNull()?.id
                                image?.let { runCatching { moteur.setAvatarServeur(cree.id, it.absolutePath) } }
                            }.onFailure { messageFugace = it.message ?: t.creationServeurImpossible }
                        }
                    },
                    onRejoindre = { lien ->
                        val rejoint = moteur.joinServer(lien)
                        surAccueil = false
                        serveurOuvert = rejoint.server.id
                        salonOuvert = rejoint.server.channels.firstOrNull()?.id
                    },
                    onFermer = { creerServeur = false },
                )
            }

            if (surAccueil) {
                val conv = directs.firstOrNull { it.roomId == directOuvert }
                Box(Modifier.fillMaxHeight()) {
                ColonneMessagesPrivees(
                    conversations = directs,
                    ouverte = directOuvert,
                    profil = profil,
                    statut = statut,
                    decoration = monProfilEtendu?.decoration,
                    onOuvrir = { directOuvert = it.roomId },
                    // pinning goes through the `m.favourite` tag, so it is visible from other
                    // clients. this line used to do nothing at all
                    onEpingler = { conv ->
                        porteeBureau.launch {
                            runCatching { moteur.setPinned(conv.roomId, !conv.epingle) }
                            runCatching { moteur.directs() }.onSuccess { directsBruts = it }
                        }
                    },
                    serveurs = serveurs.map { it.id to it.name },
                    // a group is always created, never looked up: `m.direct` files rooms by
                    // person and not by set, so there is nothing to find. the new room is opened
                    // straight away, which is what you asked for by making it
                    onCreerGroupe = { ids ->
                        porteeBureau.launch {
                            runCatching { moteur.creerGroupeDirect(ids) }
                                .onSuccess { salle ->
                                    runCatching { moteur.directs() }
                                        .onSuccess { directsBruts = it }
                                    directOuvert = salle
                                }
                                .onFailure { grogner("creation du groupe", it) }
                        }
                    },
                    // the same contract as the phone's sheet, so an entry cannot exist on one
                    // platform and be forgotten on the other
                    actionsDe = { c ->
                        ActionsConversation(
                            onEpingler = {
                                porteeBureau.launch {
                                    runCatching { moteur.setPinned(c.roomId, !c.epingle) }
                                    runCatching { moteur.directs() }.onSuccess { directsBruts = it }
                                }
                            },
                            onMarquerLu = {
                                porteeBureau.launch {
                                    if (c.nonLus > 0 || c.marqueNonLu) {
                                        runCatching { moteur.markRead(c.roomId) }
                                    } else {
                                        runCatching { moteur.setNonLu(c.roomId, true) }
                                    }
                                    runCatching { moteur.directs() }.onSuccess { directsBruts = it }
                                }
                            },
                            // the desktop menu replaces its own page rather than opening a sheet,
                            // so there is nothing to "open" here: the submenu is drawn in place
                            // and the choice arrives through `onModeNotification`
                            onNotifications = {},
                            onModeNotification = { mode ->
                                porteeBureau.launch {
                                    runCatching { moteur.setModeNotification(c.roomId, mode) }
                                    // reread rather than corrected in place: the server holds the
                                    // rule, and a list fixed locally would lie if the write failed
                                    runCatching { moteur.directs() }.onSuccess { directsBruts = it }
                                }
                            },
                            // the profile, the note and the nickname are all fields of the card,
                            // so all three open it. 60 dp down, like opening it from a header
                            onProfil = { ficheMembre = fiche(c) },
                            onNote = { ficheMembre = fiche(c) },
                            onSurnom = { ficheMembre = fiche(c) },
                            onAppeler = { AppelBureau.rejoindre(moteur, c.roomId, c.nom) },
                            onInviterServeur = { sid ->
                                porteeBureau.launch { runCatching { moteur.invite(sid, c.userId) } }
                            },
                            onMuet = { duree ->
                                porteeBureau.launch {
                                    runCatching {
                                        if (duree == null) {
                                            moteur.setModeNotification(
                                                c.roomId,
                                                li.velvetchat.core.model.ModeNotification.DEFAUT,
                                            )
                                        } else {
                                            moteur.setSourdine(
                                                c.roomId,
                                                duree.minutes?.let {
                                                    System.currentTimeMillis() + it * 60_000L
                                                },
                                            )
                                        }
                                    }
                                    salonsMuets = runCatching { moteur.salonsMuets() }
                                        .getOrDefault(salonsMuets)
                                }
                            },
                            onFermerMp = {
                                porteeBureau.launch {
                                    // leaving and forgetting: a direct room left in `m.direct`
                                    // walks straight back into the list on the next sync
                                    runCatching { moteur.leaveChannel(c.roomId, forget = true) }
                                    if (directOuvert == c.roomId) directOuvert = null
                                    runCatching { moteur.directs() }.onSuccess { directsBruts = it }
                                }
                            },
                            onRetirerAmi = {
                                porteeBureau.launch {
                                    runCatching { moteur.setAmi(c.userId, false) }
                                    mesAmis = runCatching { moteur.amis().toSet() }
                                        .getOrDefault(mesAmis)
                                }
                            },
                            onBloquer = {
                                porteeBureau.launch {
                                    runCatching {
                                        moteur.bloquer(c.userId)
                                        personnesBloquees = moteur.personnesBloquees().toSet()
                                    }
                                }
                            },
                        )
                    },
                    onProfil = { popoverProfil = true },
                    onBoutique = { editionProfil = true },
                    invitations = invitations,
                    onInvitation = { invitationVisee = it; directOuvert = null },
                )
                PoigneeLargeur(
                    Modifier.align(Alignment.CenterEnd),
                    versLaGauche = false,
                    lire = { Largeurs.salons },
                    ecrire = { Largeurs.salons = it.coerceIn(Largeurs.SALONS_MIN, Largeurs.SALONS_MAX) },
                )
                }
                if (conv == null) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        BarreFenetreSeule()
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            // answering an invite happens here rather than over the window: the
                            // shared sheet is the phone's shape, and on a desktop it barred the
                            // bottom for two buttons. this space was empty anyway
                            val inv = invitationVisee
                            if (inv != null) {
                                FeuilleInvitationRecue(
                                    invitation = inv,
                                    compacte = true,
                                    onAccepter = {
                                        moteur.accepterInvitation(inv.roomId, inv.estEspace)
                                        // reread rather than patched by hand: the server counts
                                        invitations = runCatching { moteur.invitationsRecues() }
                                            .getOrDefault(invitations)
                                        // `directsFlow` follows anyway, this saves its turn
                                        runCatching { moteur.directs() }
                                            .onSuccess { directsBruts = it }
                                    },
                                    onRefuser = {
                                        moteur.refuserInvitation(inv.roomId)
                                        invitations = runCatching { moteur.invitationsRecues() }
                                            .getOrDefault(invitations)
                                    },
                                    onFermer = { invitationVisee = null },
                                )
                            } else {
                                Vide(t.aucuneConversationOuverte, t.choisirUneAGauche)
                            }
                        }
                    }
                } else {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        // the header is the window's title bar: dragging it moves the window, and
                        // it carries the minimise, maximise and close buttons at its end
                        Row(
                            Modifier.fillMaxWidth().height(BARRE_HAUTE).background(fondConversation()).deplaceFenetre(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                EnteteDirect(
                                    conv,
                                    epingles = epinglesDirect,
                                    onDesepingler = { m ->
                                        porteeBureau.launch {
                                            runCatching { moteur.setMessagePinned(conv.roomId, m.id, false) }
                                            versionEpinglesDirect++
                                        }
                                    },
                                    profilOuvert = profilDirectOuvert,
                                    onProfil = { profilDirectOuvert = !profilDirectOuvert },
                                    onRechercher = { rechercheDirect = it },
                                    alertes = { moteur.alertes() },
                                    onAlerte = ::ouvrirAlerte,
                                    onAppeler = { AppelBureau.rejoindre(moteur, conv.roomId, conv.nom) },
                                    onAppelerVideo = {
                                        PartageEcranBureau.demanderCamera = true
                                        AppelBureau.rejoindre(moteur, conv.roomId, conv.nom)
                                    },
                                )
                            }
                            BoutonsFenetre()
                        }
                        // the call screen belongs here too
                        //
                        // it was drawn only in the server channel branch, so a call in a direct
                        // conversation had nowhere to show anything: somebody turning their
                        // camera on, or sharing their screen, arrived and was never displayed.
                        // only the little panel at the bottom left said a call was going on. and
                        // a one to one is the call people actually make
                        val appelIci = AppelBureau.etat.salonId == conv.roomId &&
                            AppelBureau.etat.phase != AppelBureau.Phase.INACTIF
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalMembres provides membres,
                        ) {
                            if (appelIci) {
                                EcranAppelBureau(Modifier.weight(0.5f), monProfil = profil)
                            } else {
                                SceneVideo()
                            }
                        }
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(fondConversation())) {
                            Conversation(
                                moteur,
                                conv.roomId,
                                "@" + conv.nom,
                                onEpingleChangee = { versionEpinglesDirect++ },
                                onLectureChangee = {
                                    porteeBureau.launch {
                                        runCatching { moteur.directs() }
                                            .onSuccess { directsBruts = it }
                                    }
                                },
                                cible = cibleDirect,
                            ) { m, x, y ->
                                ficheGauche = x
                                ficheMembre = (membres.firstOrNull { it.userId == m.sender }
                                    ?: li.velvetchat.core.model.Member(m.sender, m.senderDisplayName, m.senderAvatarUrl, null)) to y
                            }
                        }
                        rechercheDirect?.let { requete ->
                            PanneauRecherche(
                                moteur, conv.roomId, requete, Modifier.background(fondColonne(tokens.surface)),
                                onOuvrir = { cibleDirect = it },
                            ) { rechercheDirect = null; cibleDirect = null }
                        }
                        if (profilDirectOuvert && rechercheDirect == null) {
                            PanneauProfilDirect(conv, onVoirProfil = {
                                ficheGauche = null
                                ficheMembre = li.velvetchat.core.model.Member(conv.userId, conv.nom, conv.avatarUrl, null) to 60.dp
                            })
                        }
                        }
                    }
                }
                return@Row
            }

            Box(Modifier.fillMaxHeight()) {
            // the members reach here too: the people sitting in a voice room are named, and without
            // this list they would only have their ids
            androidx.compose.runtime.CompositionLocalProvider(LocalMembres provides membres) {
            ColonneSalons(
                serveur = serveur,
                ouvert = salonOuvert,
                profil = profil,
                statut = statut,
                decoration = monProfilEtendu?.decoration,
                onChoisir = choisir@{ id ->
                    // a channel still being created only has a placeholder id, which no room on the
                    // server answers to: opening or joining it can only fail. it is a click away
                    // once the real one has replaced it, a second later
                    if (id.startsWith("brouillon:")) return@choisir
                    salonOuvert = id
                    serveur?.channels?.firstOrNull { it.id == id && it.kind == li.velvetchat.core.model.ChannelKind.VOICE }
                        ?.let { AppelBureau.rejoindre(moteur, it.id, it.name) }
                },
                onProfil = { popoverProfil = true },
                onMembres = { membresOuverts = !membresOuverts },
                onMarquerLu = { id -> porteeBureau.launch { runCatching { moteur.markRead(id) } } },
                enVocal = enVocal,
                muets = salonsMuets,
                actions = ActionsServeur(
                    onMembreVocal = { qui, x, y ->
                        ficheGauche = x
                        ficheMembre = (membres.firstOrNull { it.userId == qui }
                            ?: li.velvetchat.core.model.Member(qui, null, null, null)) to y
                    },
                    onTag = { modaleServeur = "tag" },
                    onInviter = { modaleServeur = "inviter" },
                    onParametres = { modaleServeur = "parametres" },
                    onCreerSalon = { modaleServeur = "salon" },
                    onCreerSalonVocal = { modaleServeur = "salonVocal" },
                    onReordonnerSalons = { parent, ordre ->
                        porteeBureau.launch {
                            runCatching { moteur.reordonnerSalons(parent, ordre) }
                        }
                    },
                    onDeplacerSalon = { salon, vers ->
                        porteeBureau.launch {
                            val depuis = serveur?.categories
                                ?.firstOrNull { it.nom == salon.categorie }?.id
                            runCatching {
                                moteur.deplacerSalon(serveur!!.id, salon.id, depuis, vers?.id)
                            }
                        }
                    },
                    onCreerCategorie = { modaleServeur = "categorie" },
                    onModifierSalon = { salonARenommer = it; modaleServeur = "renommer" },
                    onSupprimerSalon = { salon ->
                        val ouvert = serveur
                        // away at once, the engine hides the channel before synapse confirms
                        if (ouvert != null && salonOuvert == salon.id) {
                            salonOuvert = ouvert.channels.firstOrNull { it.id != salon.id }?.id
                        }
                        if (ouvert != null) porteeBureau.launch {
                            // the channel carries its category's name, and the child space
                            // containing it has an id, and that is the one to clear
                            val parent = ouvert.categories.firstOrNull { it.nom == salon.categorie }?.id
                            runCatching { moteur.supprimerSalon(ouvert.id, salon.id, parent) }
                                .onFailure { messageFugace = it.message ?: t.suppressionImpossible }
                        }
                    },
                    // a loop over the channels, for want of a per space push rule. a channel
                    // created afterwards keeps the default, which the menu does not claim to
                    // prevent
                    onNotifications = { mode ->
                        porteeBureau.launch {
                            serveur?.channels?.forEach {
                                runCatching { moteur.setModeNotification(it.id, mode) }
                            }
                            salonsMuets = runCatching { moteur.salonsMuets() }
                                .getOrDefault(salonsMuets)
                        }
                    },
                    // one channel, one rule. the list greys out muted channels, so the set is
                    // reread after the write rather than edited here: the server holds the rule
                    onModeSalon = { salon, mode ->
                        porteeBureau.launch {
                            runCatching { moteur.setModeNotification(salon.id, mode) }
                            salonsMuets = runCatching { moteur.salonsMuets() }
                                .getOrDefault(salonsMuets)
                        }
                    },
                    onQuitter = {
                        val id = serveur?.id
                        if (id != null) {
                            // away at once: the engine hides the server before synapse confirms,
                            // one write per channel can take minutes
                            val suivant = serveurs.firstOrNull { it.id != id }
                            serveurOuvert = suivant?.id
                            salonOuvert = suivant?.channels?.firstOrNull()?.id
                            porteeBureau.launch {
                                runCatching { moteur.leaveServer(id) }
                                    .onFailure { messageFugace = it.message ?: t.quitterServeurImpossible }
                            }
                        }
                    },
                    onSupprimer = serveur?.id?.takeIf {
                        (membres.firstOrNull { m -> m.userId == moi }?.role?.powerLevel ?: 0L) >= 100L
                    }?.let { id ->
                        {
                            val suivant = serveurs.firstOrNull { it.id != id }
                            serveurOuvert = suivant?.id
                            salonOuvert = suivant?.channels?.firstOrNull()?.id
                            porteeBureau.launch {
                                runCatching { moteur.supprimerServeur(id) }
                                    .onFailure { messageFugace = it.message ?: t.suppressionServeurImpossible }
                            }
                        }
                    },
                ),
            )
            }
            if (serveur != null) when (modaleServeur) {
                "inviter" -> ModaleInviter(moteur, serveur) { modaleServeur = null }
                "salon", "salonVocal" -> ModaleNom(
                    t.creerSalon,
                    t.nomDuSalonMaj,
                    t.creerLeSalon,
                    avecType = true,
                    vocalParDefaut = modaleServeur == "salonVocal",
                    onValider = { nom, vocal ->
                        modaleServeur = null
                        porteeBureau.launch {
                            runCatching {
                                moteur.createChannel(
                                    serveur.id,
                                    nom.lowercase().replace(' ', '-'),
                                    vocal = vocal,
                                )
                            }.onSuccess { salonOuvert = it.id }
                                .onFailure { messageFugace = it.message ?: t.creationSalonImpossible }
                        }
                    },
                ) { modaleServeur = null }
                "renommer" -> salonARenommer?.let { cible ->
                    ModaleNom(t.modifierLeSalon, t.nomDuSalonMaj, t.enregistrer, onValider = { nom, _ ->
                        modaleServeur = null
                        porteeBureau.launch { runCatching { moteur.renommerSalon(cible.id, nom.lowercase().replace(' ', '-')) } }
                    }) { modaleServeur = null }
                }
                "categorie" -> ModaleNom(t.creerUneCategorie, t.nomDeLaCategorieMaj, t.creerLaCategorie, onValider = { nom, _ ->
                    modaleServeur = null
                    porteeBureau.launch {
                        runCatching { moteur.createCategory(serveur.id, nom) }
                            .onFailure { messageFugace = it.message ?: t.creationCategorieImpossible }
                    }
                }) { modaleServeur = null }
            }
                PoigneeLargeur(
                    Modifier.align(Alignment.CenterEnd),
                    versLaGauche = false,
                    lire = { Largeurs.salons },
                    ecrire = { Largeurs.salons = it.coerceIn(Largeurs.SALONS_MIN, Largeurs.SALONS_MAX) },
                )
            }
            if (salon == null) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    BarreFenetreSeule()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Vide(t.aucunSalonOuvert, t.choisirUnAGauche)
                    }
                }
            } else {
                // the header runs above the conversation and the members, like on the reference:
                // the members start under the bar, not next to it. it is also the window's title
                // bar, dragging it moves the window and the buttons are at the end
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth().height(BARRE_HAUTE).background(fondConversation()).deplaceFenetre(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            EnteteSalon(
                                salon,
                                membresOuverts,
                                nomServeur = serveur?.name,
                                epingles = epinglesSalon,
                                fils = filsDuSalon,
                                mode = modeDuSalon,
                                // reread after writing, like for the pins: the server can refuse,
                                // and showing the requested choice rather than the one it kept
                                // would suggest a setting that does not exist
                                onMode = { choix ->
                                    porteeBureau.launch {
                                        runCatching { moteur.setModeNotification(salon.id, choix) }
                                        modeDuSalon = runCatching { moteur.modeNotification(salon.id) }
                                            .getOrNull()
                                    }
                                },
                                onOuvrirFil = { nouveauFil = false; filOuvert = it.racine },
                                onCreerFil = { filOuvert = null; nouveauFil = true },
                                onRechercher = { filOuvert = null; nouveauFil = false; rechercheSalon = it },
                                alertes = { moteur.alertes() },
                                onAlerte = ::ouvrirAlerte,
                                onDesepingler = { m ->
                                    porteeBureau.launch {
                                        runCatching { moteur.setMessagePinned(salon.id, m.id, false) }
                                        versionEpingles++
                                    }
                                },
                            ) { membresOuverts = !membresOuverts }
                        }
                        BoutonsFenetre()
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(fondConversation())) {
                            // the room's members, for the reactions' stacked avatars and the `@`
                            // autocomplete
                            androidx.compose.runtime.CompositionLocalProvider(LocalMembres provides membres) {
                            Column(Modifier.fillMaxSize()) {
                            // the call screen when you are in this room, like the reference and
                            // like the phone: the main area shows the call and the conversation
                            // stays underneath
                            //
                            // elsewhere, only what other people share. a permanent black frame
                            // would steal the space from the messages, which are what serve when
                            // nobody is sharing
                            val dansCeSalon = AppelBureau.etat.salonId == salon.id &&
                                AppelBureau.etat.phase != AppelBureau.Phase.INACTIF
                            if (dansCeSalon) {
                                EcranAppelBureau(Modifier.weight(0.5f), monProfil = profil)
                            } else {
                                SceneVideo()
                            }
                            // the conversation takes what is left: without this weight it would
                            // draw over the whole height and push the stage out
                            Box(Modifier.weight(1f)) {
                                Conversation(
                                    moteur,
                                    salon.id,
                                    "#" + salon.name,
                                    onEpingleChangee = { versionEpingles++ },
                                    onFil = { nouveauFil = false; filOuvert = it },
                                    cible = cibleSalon,
                                ) { m, x, y ->
                                    // clicking a message's avatar opens its author's card, beside
                                    // it
                                    ficheGauche = x
                                    ficheMembre = (membres.firstOrNull { it.userId == m.sender }
                                        ?: li.velvetchat.core.model.Member(m.sender, m.senderDisplayName, m.senderAvatarUrl, null)) to y
                                }
                            }
                            }
                            }
                        }
                        rechercheSalon?.takeIf { filOuvert == null && !nouveauFil }?.let { requete ->
                            PanneauRecherche(
                                moteur, salon.id, requete, Modifier.background(fondColonne(tokens.surface)),
                                onOuvrir = { cibleSalon = it },
                            ) { rechercheSalon = null; cibleSalon = null }
                        }
                        if (filOuvert != null || nouveauFil) {
                            PanneauFil(moteur, salon.id, filOuvert, Modifier.background(fondColonne(tokens.surface))) {
                                filOuvert = null; nouveauFil = false
                            }
                        }
                        if (membresOuverts && filOuvert == null && !nouveauFil && rechercheSalon == null) Box(Modifier.fillMaxHeight()) {
                            ColonneMembres(
                                membres,
                                statutsMembres,
                                Modifier.background(fondColonne(tokens.surface)),
                            ) { membre, haut -> ficheGauche = null; ficheMembre = membre to haut }
                            PoigneeLargeur(
                                Modifier.align(Alignment.CenterStart),
                                versLaGauche = true,
                                lire = { Largeurs.membres },
                                ecrire = { Largeurs.membres = it.coerceIn(Largeurs.MEMBRES_MIN, Largeurs.MEMBRES_MAX) },
                            )
                        }
                    }
                }
            }
        }

        // the mini bar floats over the rail and the channel column, or the dms, stuck to the
        // bottom: 8 from the left up to the column's right edge, on the reference. it follows the
        // dragged width. the members reach here because the call's photos come from them, and the
        // mini bar floats outside the column that already supplies them to the conversation
        androidx.compose.runtime.CompositionLocalProvider(LocalMembres provides membres) {
            PanneauVoixConnectee(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = MINI_BARRE + 8.dp)
                    .width(RAIL + Largeurs.salons - 8.dp),
            )
        }
        appelEntrant?.let { a ->
            BanniereAppelEntrant(
                appelant = a.appelant,
                lieu = serveurs.flatMap { it.channels }.firstOrNull { it.id == a.salonId }?.name?.let { "#$it" }
                    ?: directs.firstOrNull { it.roomId == a.salonId }?.nom ?: t.appel,
                onRejoindre = {
                    appelsRefuses = appelsRefuses + a.salonId
                    AppelBureau.rejoindre(moteur, a.salonId, directs.firstOrNull { it.roomId == a.salonId }?.nom)
                },
                onRefuser = { appelsRefuses = appelsRefuses + a.salonId },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            )
        }
        BarreUtilisateurBureau(
            profil = profil,
            etendu = monProfilEtendu,
            statut = statut,
            silence = silence,
            onProfil = { popoverProfil = true },
            onParametres = { parametresUtilisateur = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp)
                .width(RAIL + Largeurs.salons - 8.dp),
        )

        // over the columns and not inside them: the card overhangs the channel column to the right,
        // exactly like on the reference. drawn inside the column it would be cut at its edge
        if (popoverProfil) {
            PopoverProfil(
                moteur = moteur,
                profil = profil,
                etendu = monProfilEtendu,
                statut = statut,
                silence = silence,
                onStatut = { nouveau, nouveauSilence ->
                    statut = nouveau
                    silence = nouveauSilence
                    // kept on this machine, because the server cannot hold it: see the launch
                    // effect. without this line the choice lasts until the next restart
                    Reglages.ecrire("statut.presence", nouveau.presence.name)
                    Reglages.ecrire("statut.message", nouveau.message.orEmpty())
                    // your own row follows immediately. the presence flow will come round again,
                    // but a few seconds later, and without this shortcut you still saw yourself as
                    // do not disturb in the member column after leaving it
                    moi?.let { id -> statutsMembres = statutsMembres + (id to nouveau) }
                },
                onFermer = { popoverProfil = false },
                onModifierProfil = { editionProfil = true },
            )
        }

        ficheMembre?.let { (cible, haut) ->
            val monPouvoir = membres.firstOrNull { it.userId == moi }?.role?.powerLevel ?: 0L
            FicheMembreBureau(
                membre = cible,
                statut = statutsMembres[cible.userId] ?: li.velvetchat.core.model.Statut.INCONNU,
                palier = palierDes[cible.userId],
                decoratifs = decors.de(cible.userId),
                rolesDisponibles = decors.roles,
                // managing decorative roles: moderator at least (50), like the server settings
                peutGererRoles = serveurOuvert != null && monPouvoir >= 50,
                ami = ficheAmi,
                haut = haut,
                gauche = ficheGauche,
                surnom = surnoms[cible.userId],
                // null on yourself: you do not block yourself, and the row disappears
                bloque = if (cible.userId == profil?.userId) null
                else cible.userId in personnesBloquees,
                onBloquer = { bloquer ->
                    porteeBureau.launch {
                        runCatching {
                            if (bloquer) moteur.bloquer(cible.userId)
                            else moteur.debloquer(cible.userId)
                            // reread rather than assumed: it is account data shared between
                            // devices, and it is what counts
                            personnesBloquees = moteur.personnesBloquees().toSet()
                        }
                        ficheMembre = null
                    }
                },
                onSurnom = { nouveau ->
                    surnoms = if (nouveau == null) surnoms - cible.userId
                    else surnoms + (cible.userId to nouveau)
                    porteeBureau.launch {
                        runCatching { moteur.setSurnom(cible.userId, nouveau) }
                        // the display names come from the engine: we reread what is on screen
                        membres = runCatching { moteur.members(salonOuvert.orEmpty()) }.getOrDefault(membres)
                    }
                },
                onRoles = { ids ->
                    serveurOuvert?.let { sid ->
                        val nouvelles = decors.copy(parMembre = decors.parMembre + (cible.userId to ids))
                        decors = nouvelles
                        porteeBureau.launch { runCatching { moteur.setDecorations(sid, nouvelles) } }
                    }
                },
                onAmi = { oui ->
                    ficheAmi = oui
                    porteeBureau.launch { runCatching { moteur.setAmi(cible.userId, oui) } }
                },
                // writing from the card opens, or creates, the direct conversation, sends into it,
                // and takes you there
                onMessage = { texte ->
                    ficheMembre = null
                    porteeBureau.launch {
                        runCatching {
                            val id = moteur.openDirect(cible.userId)
                            moteur.send(id, texte)
                            surAccueil = true
                            directOuvert = id
                        }
                    }
                },
                onFermer = { ficheMembre = null },
            )
        }

        val serveurAffiche = serveur.takeIf { !surAccueil }
        if (serveurAffiche != null && (modaleServeur == "parametres" || modaleServeur == "tag")) {
            ParametresServeur(
                moteur = moteur,
                serveur = serveurAffiche,
                // the same rule as the green counter in the member column
                enLigne = statutsMembres.values.count { it.estPresent },
                sectionInitiale = if (modaleServeur == "tag") 1 else 0,
                onFermer = { modaleServeur = null },
            )
        }

        if (parametresUtilisateur) {
            ParametresUtilisateur(
                moteur = moteur,
                profil = profil,
                onModifierProfil = { editionProfil = true },
                onFermer = { parametresUtilisateur = false },
                onDeconnexion = onDeconnexion,
            )
        }

        if (editionProfil) {
            ModaleProfil(
                moteur = moteur,
                profil = profil,
                etendu = monProfilEtendu,
                onProfil = { profil = it },
                onEtendu = { monProfilEtendu = it },
                onFermer = { editionProfil = false },
            )
        }

        messageFugace?.let { texte ->
            LaunchedEffect(texte) {
                kotlinx.coroutines.delay(6_000)
                messageFugace = null
            }
            Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    texte,
                    color = tokens.textPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.background(tokens.danger, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        }
    }
}


@Composable
private fun Conversation(
    moteur: MatrixEngine,
    salonId: String,
    libelle: String,
    /**
     * says the room's pins changed right here
     *
     * the header's panel lives elsewhere and reads a flow that does not re emit, so without this
     * signal you pin from a message's menu and the panel keeps the previous list
     */
    onEpingleChangee: () -> Unit = {},
    /** says the read state changed here, so the conversation list rereads itself */
    onLectureChangee: () -> Unit = {},
    /**
     * a message's avatar was clicked: the message, then where to put the card, left and top, in the
     * window
     */
    /**
     * "create a thread" on a message: opens the thread panel, which comes into being on the first
     * reply
     */
    onFil: ((Message) -> Unit)? = null,
    /**
     * a message to reach, from a search result: the conversation scrolls up to it and highlights it
     */
    cible: Message? = null,
    onAuteur: (Message, androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp) -> Unit = { _, _, _ -> },
) {
    val t = textes()
    // typing indicators. the desktop showed none and signalled none: `frappeFlow` and
    // `signalerFrappe` were in the contract with the phone as their only client, so a conversation
    // between two devices was mute on one side
    var quiEcrit by remember(salonId) { mutableStateOf<List<String>>(emptyList()) }
    var feuilleSondage by remember(salonId) { mutableStateOf(false) }
    LaunchedEffect(salonId) {
        // guarded: on a room that is listed but not joined, this flow throws "unknown room", and a
        // decoration must never be able to take the window down
        try {
            moteur.frappeFlow(salonId).collect { identifiants ->
                quiEcrit = identifiants.map { it.removePrefix("@").substringBefore(':') }
            }
        } catch (annulation: kotlinx.coroutines.CancellationException) {
            throw annulation
        } catch (t: Throwable) {
            quiEcrit = emptyList()
        }
    }
    val tokens = LocalTokens.current
    val portee = rememberCoroutineScope()
    var messages by remember(salonId) { mutableStateOf<List<Message>>(emptyList()) }
    // `TextFieldValue` and not `String`: the autocomplete looks at what precedes the cursor, and a
    // bare string does not know where it is. that is what was missing to wire it up here
    var brouillon by remember(salonId) { mutableStateOf(TextFieldValue("")) }
    var membresDuSalon by remember(salonId) { mutableStateOf<List<Member>>(emptyList()) }
    var pack by remember(salonId) { mutableStateOf<List<CustomEmote>>(emptyList()) }
    /** the highlighted suggestion, the one enter or tab will take */
    var choix by remember(salonId) { mutableStateOf(0) }
    val salonsDuServeur = LocalSalons.current
    val reglages = LocalReglagesEmojis.current
    /** the message being replied to, or null. see the banner above the composer */
    var reponseA by remember(salonId) { mutableStateOf<Message?>(null) }
    // the message being forwarded, see [ModaleTransfert]
    var aTransferer by remember(salonId) { mutableStateOf<Message?>(null) }
    aTransferer?.let { ModaleTransfert(salonId, it, onFermer = { aTransferer = null }) }
    /** the message being edited, or null. the composer then carries its text */
    var enEdition by remember(salonId) { mutableStateOf<Message?>(null) }
    /** the composer picker's open tab (0 emoji, 1 gif, 2 stickers), or null */
    var selecteur by remember(salonId) { mutableStateOf<Int?>(null) }
    /**
     * the room's pinned ids, just what a message's menu needs to say pin or unpin. the header panel
     * needs the whole messages and takes them from the flow, and here an id is enough and rereads
     * in one go
     */
    var epingles by remember(salonId) { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(salonId) {
        epingles = runCatching { moteur.pinnedMessages(salonId) }
            .getOrDefault(emptyList()).toSet()
    }
    // editing or replying puts the cursor in the composer. found in testing: the banner appeared
    // but the typing went nowhere until you clicked in the field
    val focusComposeur = remember { androidx.compose.ui.focus.FocusRequester() }
    /** yourself: it is what decides which message the up arrow edits */
    val monIdentifiant = remember(moteur) { runCatching { moteur.currentSession().userId }.getOrNull() }

    // dictation: the composer's mic sends the voice to the nas (see `Dictee.kt`), the text is
    // written after the draft and stays editable, nothing leaves on its own. the mic is gold while
    // listening and pales during the final pass
    var dictee by remember(salonId) { mutableStateOf<DicteeBureau?>(null) }
    var finalisation by remember(salonId) { mutableStateOf(false) }

    // voice messages, which are not dictation. dictation turns what you say into text in the
    // draft, this sends the recording itself. discord has both, with two buttons, because one
    // button would have to guess which one you meant
    var enregistreVocal by remember(salonId) { mutableStateOf(false) }
    fun basculerVocal() {
        if (enregistreVocal) {
            enregistreVocal = false
            val dossier = java.io.File(System.getProperty("java.io.tmpdir"), "velvet-vocaux")
            val pris = runCatching { EnregistreurVocal.arreter(dossier) }.getOrNull() ?: return
            portee.launch {
                runCatching { moteur.sendVoice(salonId, pris.fichier.absolutePath, pris.dureeMs, pris.waveform) }
                // uploaded by now, and leaving it around would pile up every recording of the
                // session in temp
                runCatching { pris.fichier.delete() }
            }
        } else {
            enregistreVocal = runCatching { EnregistreurVocal.demarrer() }.getOrDefault(false)
        }
    }
    DisposableEffect(salonId) {
        onDispose {
            dictee?.annuler()
            // leaving the channel throws the recording away rather than sending it. nobody
            // asked for it to go, and an open line keeps the mic to itself
            if (EnregistreurVocal.enCours) EnregistreurVocal.annuler()
        }
    }
    fun basculerDictee() {
        dictee?.let { if (!finalisation) it.terminer(); return }
        val jeton = runCatching { moteur.currentSession().accessToken }.getOrNull() ?: return
        val avant = brouillon.text.let { if (it.isBlank() || it.endsWith(" ")) it else "$it " }
        fun ecrire(suite: String) { brouillon = TextFieldValue(avant + suite, TextRange((avant + suite).length)) }
        dictee = DicteeBureau(
            jeton,
            onTexte = ::ecrire,
            onFinalisation = { finalisation = true },
            onFin = { ecrire(it); dictee = null; finalisation = false },
            onErreur = { dictee = null; finalisation = false },
        ).also { it.demarrer() }
    }
    LaunchedEffect(enEdition?.id, reponseA?.id) {
        if (enEdition != null || reponseA != null) runCatching { focusComposeur.requestFocus() }
    }
    val pressePapier = copieur()
    val defilement = rememberLazyListState()

    LaunchedEffect(salonId) {
        // guarded like the typing flow: an unknown room throws here, and uncaught in a
        // `LaunchedEffect` that is Compose's error dialog and the window gone
        essayer { moteur.messagesFlow(salonId).collectLatest { messages = it } }
            .onFailure { grogner("messages du salon illisibles", it) }
    }

    // this room's members, not the server's: a direct conversation does not have the same ones, and
    // offering one inside the other would name people who will not read it
    LaunchedEffect(salonId) {
        membresDuSalon = runCatching { moteur.members(salonId) }.getOrDefault(emptyList())
        pack = runCatching {
            moteur.emotePacks().flatMap { it.emotes }.distinctBy { it.shortcode }
        }.getOrDefault(emptyList())
    }

    // what precedes the cursor and nothing else: writing in the middle of an already typed message
    // must not suggest from what comes after
    val avantCurseur = brouillon.text.take(brouillon.selection.end)
    val propositions = remember(avantCurseur, pack, membresDuSalon, salonsDuServeur) {
        suggestions(avantCurseur, pack, membresDuSalon, salonsDuServeur)
    }
    // escape closes the list without erasing what was typed. the `remember` key reopens it as soon
    // as typing resumes, since closing it for good would mean erasing the word to get it back
    var ferme by remember(avantCurseur) { mutableStateOf(false) }
    val visibles = propositions?.second?.takeIf { !ferme }
    LaunchedEffect(propositions?.first) { choix = 0 }

    fun envoyer() {
        val texte = brouillon.text.trim()
        if (texte.isEmpty()) return
        val cible = reponseA
        val edition = enEdition
        brouillon = TextFieldValue("")
        reponseA = null
        enEdition = null
        portee.launch {
            runCatching {
                // the same path as the phone: a shortcode becomes the emoji, a server shortcode
                // becomes its image, an `@` becomes a real mention. the desktop used to send raw
                // text, so a shortcode went out spelled as typed and the server's emoji drew for
                // nobody
                val sortant = li.velvetchat.core.model.composerMessage(
                    texte, pack, membresDuSalon, salonsDuServeur, reglages.teinte(),
                )
                val html = sortant.html
                when {
                    edition != null -> moteur.edit(salonId, edition.id, sortant.corps)
                    cible != null -> moteur.reply(salonId, cible.id, sortant.corps)
                    html != null -> moteur.sendHtml(
                        salonId, sortant.corps, html, sortant.mentions, sortant.mentionneToutLeSalon,
                    )
                    else -> moteur.send(salonId, sortant.corps)
                }
            }
        }
    }

    /** replaces the current fragment with the suggestion, and puts the cursor behind it */
    fun completerAvec(suggestion: Suggestion) {
        val debut = completer(avantCurseur, suggestion, reglages.teinte())
        brouillon = TextFieldValue(
            text = debut + brouillon.text.drop(brouillon.selection.end),
            selection = TextRange(debut.length),
        )
    }

    Column(Modifier.fillMaxSize()) {
        // the rows are computed, not rendered as they come. the grouping by author and the day
        // separators come from the same rule as the phone (`continueLeGroupe`, `Horodateur`): two
        // visibly different conversations for one room only show by opening them side by side,
        // which is to say all the time now that there are two clients
        val horodateur = remember { Horodateur() }
        val lignes = remember(messages, horodateur) {
            val fenetre = tokens.density.messageGroupWindowMs
            // noise events, a nickname or avatar change and the like, are not shown, like on the
            // phone and the reference. found in testing: every profile change made a "system" row.
            // a deleted message disappears too, since the reference leaves no "message deleted" row
            val visibles = messages.filterNot {
                it.body is MessageBody.Redacted || (it.body as? MessageBody.System)?.kind?.isNoise == true
            }
            visibles.mapIndexed { i, m ->
                val precedent = visibles.getOrNull(i - 1)
                val jourNeuf = precedent == null ||
                    horodateur.jourLocal(precedent.timestamp) != horodateur.jourLocal(m.timestamp)
                LigneConversation(
                    message = m,
                    groupe = !jourNeuf && continueLeGroupe(precedent, m, fenetre),
                    jour = if (jourNeuf) horodateur.jour(m.timestamp) else null,
                )
            }
        }

        // follow the conversation when you are at the bottom, never jump when you have scrolled up
        //
        // the list is reversed and its rows have keys, so compose keeps the message that was on
        // screen and a new one inserts under it, out of view, and the conversation did not follow.
        // we look at where we were when the last message changed: at the bottom (index 0, or 1 just
        // after the insertion) we go back down, and scrolled up we do not move and a badge counts
        // what arrived. a message you send yourself always scrolls back down, like on the reference
        val moi = LocalMonIdentifiant.current
        val enBas by remember { androidx.compose.runtime.derivedStateOf { defilement.firstVisibleItemIndex <= 1 } }
        var nonVus by remember(salonId) { mutableStateOf(0) }
        // you said unread, so the room stays unread
        //
        // without this the feature was decorative when used from the open room: the two effects
        // below acknowledge whatever is in front of you, and they ran a second after the menu
        // entry, putting back what it had just removed. reset on leaving the room, because coming
        // back to a conversation is reading it
        var nonLuVoulu by remember(salonId) { mutableStateOf(false) }
        val dernier = messages.lastOrNull()
        LaunchedEffect(dernier?.id) {
            if (dernier == null) return@LaunchedEffect
            if (enBas || dernier.sender == moi) {
                defilement.animateScrollToItem(0)
                nonVus = 0
                // what is in front of you is read. the open room, scrolled to the bottom, kept its
                // badge after a message arrived that was in fact displayed. scrolled up in the
                // history we mark nothing: the message has not been seen
                if (!nonLuVoulu) runCatching { moteur.markRead(salonId) }
            } else {
                nonVus++
            }
        }
        // coming back to the bottom means having read. found in testing: scrolled up while a
        // message arrived, then back down through the badge, the channel kept its count, since the
        // room was only marked read when a message arrived while you were already at the bottom
        LaunchedEffect(defilement.firstVisibleItemIndex) {
            if (defilement.firstVisibleItemIndex == 0 && !nonLuVoulu) {
                nonVus = 0
                runCatching { moteur.markRead(salonId) }
            }
        }

        // load older history when approaching the top. found in testing: the desktop never called
        // `loadMore`, so you only ever saw a room's first page and scrolling up hit the wall. same
        // rule as the phone, ten rows from the end, a hundred more. the list is reversed, so "the
        // top" is the end of the list
        //
        // one loop, and not an effect restarted by its own state. the previous version had
        // `chargeEnCours` as its key, so setting it true restarted the effect, which cancelled the
        // load it had just started, `runCatching` swallowed the cancellation, put it back to false,
        // and it all started again. the page never completed. the cancellation is now rethrown,
        // never swallowed
        LaunchedEffect(salonId) {
            while (true) {
                androidx.compose.runtime.snapshotFlow {
                    defilement.layoutInfo.totalItemsCount > 0 && run {
                        val dernier = defilement.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        dernier >= defilement.layoutInfo.totalItemsCount - 10
                    }
                }.first { it }
                val reste = try {
                    moteur.loadMore(salonId, 100)
                } catch (annulation: kotlinx.coroutines.CancellationException) {
                    throw annulation
                } catch (t: Throwable) {
                    kotlinx.coroutines.delay(2_000)
                    true
                }
                if (!reste) break
                // let the flow re emit and the list remeasure before looking again
                kotlinx.coroutines.delay(300)
            }
        }

        // reaching the target: loaded if it is already there, otherwise we scroll up page by page
        // (the search only covers the last thousand events, so ten pages is enough)
        LaunchedEffect(cible) {
            val id = cible?.id ?: return@LaunchedEffect
            repeat(12) {
                val index = lignes.asReversed().indexOfFirst { it.message.id == id }
                if (index >= 0) {
                    defilement.animateScrollToItem(index)
                    return@LaunchedEffect
                }
                runCatching { moteur.loadMore(salonId, 100) }
                kotlinx.coroutines.delay(500)
            }
        }

        // message id to the id of the one before it, for the menu's mark unread. computed once
        // for the list rather than searched per row, which would be one walk per open menu
        val precedentDe = remember(lignes) {
            buildMap {
                lignes.forEachIndexed { i, l ->
                    put(l.message.id, lignes.getOrNull(i - 1)?.message?.id)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = defilement,
            // the conversation reads from the bottom: the most recent message is the one you want
            // on arrival, and `reverseLayout` saves scrolling after every send
            reverseLayout = true,
            // about 45 px between the last message and the composer, measured on the reference. it
            // used to be 2 px and the message touched it
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
        ) {
            items(lignes.asReversed(), key = { it.message.id }) { ligne ->
                LigneMessage(
                    ligne = ligne,
                    horodateur = horodateur,
                    surligne = ligne.message.id == cible?.id,
                    estEpingle = ligne.message.id in epingles,
                    // reread after writing rather than guess: `setMessagePinned` already rereads
                    // the state so as not to wipe everybody else's pins, and the room can refuse,
                    // the power to write `m.room.pinned_events` often being reserved. assuming it
                    // worked would show "unpinned" on a refusal
                    onEpingler = {
                        portee.launch {
                            runCatching {
                                moteur.setMessagePinned(
                                    salonId, ligne.message.id, ligne.message.id !in epingles,
                                )
                            }
                            epingles = runCatching { moteur.pinnedMessages(salonId) }
                                .getOrDefault(emptyList()).toSet()
                            onEpingleChangee()
                        }
                    },
                    onAuteur = { x, y -> onAuteur(ligne.message, x, y) },
                    onMarquerNonLu = {
                        nonLuVoulu = true
                        portee.launch {
                            runCatching {
                                moteur.marquerNonLuDepuis(salonId, precedentDe[ligne.message.id])
                            }
                            onLectureChangee()
                        }
                    },
                    onMarquerLu = {
                        nonLuVoulu = false
                        portee.launch {
                            runCatching { moteur.marquerLuJusqua(salonId, ligne.message.id) }
                            onLectureChangee()
                        }
                    },
                    onCreerFil = onFil?.let { ouvrir -> { ouvrir(ligne.message) } },
                    onRepondre = { reponseA = ligne.message; enEdition = null },
                    onTransferer = { aTransferer = ligne.message },
                    onModifier = {
                        (ligne.message.body as? li.velvetchat.core.model.MessageBody.Text)?.let {
                            enEdition = ligne.message
                            reponseA = null
                            brouillon = TextFieldValue(it.text, TextRange(it.text.length))
                        }
                    },
                    onCopierTexte = {
                        (ligne.message.body as? li.velvetchat.core.model.MessageBody.Text)?.let {
                            pressePapier(it.text)
                        }
                    },
                    onCopierId = { pressePapier(ligne.message.id) },
                    onCopierLien = {
                        portee.launch {
                            runCatching { moteur.messageLink(salonId, ligne.message.id) }
                                .onSuccess {
                                    pressePapier(it)
                                }
                        }
                    },
                    onSupprimer = {
                        portee.launch {
                            runCatching { moteur.delete(salonId, ligne.message.id) }
                        }
                    },
                    onReagir = { emoji ->
                        // counted for the hover bar: its three emoji are the most used ones
                        if (!emoji.startsWith("mxc://")) reglages.noterUsage("u:$emoji")
                        portee.launch {
                            runCatching { moteur.toggleReaction(salonId, ligne.message.id, emoji) }
                        }
                    },
                    onVoter = { choix ->
                        portee.launch {
                            runCatching { moteur.votePoll(salonId, ligne.message.id, choix) }
                        }
                    },
                )
                // the separators are rendered after the row because the list is reversed: they have
                // to appear above it. the day above the unread line, like the reference
                if (ligne.message.isFirstUnread) {
                    SeparateurNouveau(Modifier.padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp))
                }
                ligne.jour?.let { SeparateurJour(it) }
            }
        }
        // the badge for messages that arrived while you were reading higher up: a click goes back
        // down
        if (nonVus > 0) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(tokens.radiusLarge))
                    .background(tokens.accent)
                    .curseurMain().clickable { portee.launch { defilement.animateScrollToItem(0) } }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.nouveauxMessages(nonVus),
                    color = tokens.bg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        // the composer's picker: over the conversation, aligned right just above the composer, like
        // the reference. the same panel as the phone. it does not close when you pick an emoji,
        // since people rarely place just one, and it does close after sending a sticker, which is a
        // whole message
        selecteur?.let { onglet ->
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 8.dp)
                    .width(490.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FOND_MENU)
                    .border(1.dp, BORD_MENU, RoundedCornerShape(12.dp)),
            ) {
                PanneauEmojis(
                    packs = LocalPacks.current,
                    hauteur = 470.dp,
                    poignee = false,
                    ongletInitial = onglet,
                    bureau = true,
                    // a chosen gif goes out as a file, like on the phone: downloaded, sent, deleted
                    panneauGif = { _ ->
                        PanneauGifBureau(onChoisir = { gif ->
                            selecteur = null
                            portee.launch {
                                runCatching {
                                    val octets = octetsDistants(gif.complet) ?: error(tr("GIF illisible", "Unreadable GIF"))
                                    val suffixe = "." + gif.complet.substringAfterLast('.', "gif").substringBefore('?').take(5)
                                    val fichier = java.io.File.createTempFile("velvet-gif", suffixe)
                                    try {
                                        fichier.writeBytes(octets)
                                        moteur.sendMedia(salonId, fichier.absolutePath)
                                    } finally {
                                        fichier.delete()
                                    }
                                }
                            }
                        })
                    },
                    onChoisir = { choix ->
                        val insere = when (choix) {
                            is ChoixEmoji.Unicode -> choix.caractere
                            is ChoixEmoji.Personnalise -> ":${choix.emote.shortcode}: "
                        }
                        val avant = brouillon.text.take(brouillon.selection.end) + insere
                        brouillon = TextFieldValue(avant + brouillon.text.drop(brouillon.selection.end), TextRange(avant.length))
                    },
                    onEffacer = {
                        val fin = brouillon.selection.end
                        if (fin > 0) {
                            // an emoji can take two code units, so we remove the whole character
                            val debut = runCatching { brouillon.text.offsetByCodePoints(fin, -1) }.getOrDefault(fin - 1)
                            brouillon = TextFieldValue(brouillon.text.removeRange(debut, fin), TextRange(debut))
                        }
                    },
                    onAutocollant = { autocollant ->
                        selecteur = null
                        portee.launch { runCatching { moteur.envoyerAutocollant(salonId, autocollant) } }
                    },
                )
            }
        }
        }

        // the reply banner, stuck above the composer: it says who you are replying to and lets you
        // drop it. without it the reply would go out with nothing on screen having announced it,
        // and that is the kind of invisible state that gets things sent to the wrong place
        reponseA?.let { cible ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(topStart = tokens.radiusMedium, topEnd = tokens.radiusMedium))
                    .background(tokens.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.reponseA(cible.senderDisplayName ?: cible.sender),
                    color = tokens.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Icone(
                    Icones.fermer,
                    tint = tokens.textMuted,
                    taille = 14.dp,
                    description = t.annulerReponse,
                    modifier = Modifier.curseurMain().clickable { reponseA = null },
                )
            }
        }

        enEdition?.let {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(topStart = tokens.radiusMedium, topEnd = tokens.radiusMedium))
                    .background(tokens.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.modificationDuMessage, color = tokens.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Icone(
                    Icones.fermer,
                    tint = tokens.textMuted,
                    taille = 14.dp,
                    description = t.annulerLaModification,
                    modifier = Modifier.curseurMain().clickable { enEdition = null; brouillon = TextFieldValue("") },
                )
            }
        }

        // the suggestion list, placed above the composer like the reference: it pushes the
        // conversation up rather than covering it
        visibles?.let { liste ->
            PanneauAutocompletion(
                fragment = propositions.first,
                declencheur = avantCurseur.getOrNull(
                    avantCurseur.length - propositions.first.length - 1,
                ) ?: ':',
                propositions = liste,
                selection = choix.coerceIn(0, liste.lastIndex),
                teinte = reglages.teinte(),
                onChoisir = { completerAvec(it) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (feuilleSondage) {
            FeuilleSondage(
                onCreer = { question, options, secret ->
                    portee.launch {
                        runCatching { moteur.createPoll(salonId, question, options, secret) }
                    }
                },
                onFermer = { feuilleSondage = false },
            )
        }

        // "X is typing", just above the composer, like the reference
        if (quiEcrit.isNotEmpty()) LigneDeFrappe(quiEcrit)

        // the composer, remeasured off the screenshots: 57 px tall, 9 px under it to the bottom of
        // the window, it had 24, radius about 8. on the right, gif, sticker, emoji, apps. the gift
        // is removed by decision, and the poll does not exist on the reference. what is not wired
        // up yet does nothing rather than lie
        // the reference's frame: a 1 point border, radius 8, which brightens while the field has
        // focus, over 0.2 s
        var composeurFocalise by remember { mutableStateOf(false) }
        val bordComposeur by androidx.compose.animation.animateColorAsState(
            if (composeurFocalise) tokens.textMuted.copy(alpha = 0.6f) else tokens.border,
            androidx.compose.animation.core.tween(200),
            label = "bordComposeur",
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 9.dp)
                .height(57.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tokens.surfaceRaised)
                .border(1.dp, bordComposeur, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // a bare plus, like the reference. the attach icon was circled, which made it a button
            // apart
            val fenetre = LocalFenetre.current
            // the plus opens a choice, like the phone's attachment sheet: a file, or a poll. it
            // only sent a file, and creating a poll existed nowhere on the desktop although the
            // sheet and the card are shared
            var menuJointes by remember { mutableStateOf(false) }
            Box {
                Icone(
                    Icones.ajouter,
                    tint = tokens.textSecondary,
                    taille = 22.dp,
                    description = t.joindreUnFichier,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).curseurMain().clickable {
                        menuJointes = true
                    },
                )
                if (menuJointes) {
                    androidx.compose.ui.window.Popup(
                        alignment = Alignment.BottomStart,
                        offset = androidx.compose.ui.unit.IntOffset(0, -40),
                        onDismissRequest = { menuJointes = false },
                        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier.width(240.dp).clip(RoundedCornerShape(8.dp))
                                .background(FOND_MENU)
                                .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                        ) {
                            EntreeMenu(t.joindreUnFichier, icone = Icones.ajouter) {
                                menuJointes = false
                                portee.launch {
                                    val fichier = choisirFichier(fenetre?.awt) ?: return@launch
                                    runCatching { moteur.sendMedia(salonId, fichier.absolutePath) }
                                }
                            }
                            EntreeMenu(t.joindreSondage, icone = Icones.chronique) {
                                menuJointes = false
                                feuilleSondage = true
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            BasicTextField(
                value = brouillon,
                onValueChange = { nouveau ->
                    // signalled on the transition from empty to non empty and back, not on every
                    // keystroke: the server wants a start and an end, not a stream
                    if (nouveau.text.isNotBlank() != brouillon.text.isNotBlank()) {
                        portee.launch {
                            runCatching {
                                moteur.signalerFrappe(salonId, nouveau.text.isNotBlank())
                            }
                        }
                    }
                    brouillon = nouveau
                },
                textStyle = TextStyle(color = tokens.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(tokens.accent),
                // `onPreviewKeyEvent`, because there is no soft keyboard
                //
                // reported from outside: enter did not send a message. the field asked for
                // `ImeAction.Send` and waited on `KeyboardActions(onSend)`, which is a virtual
                // keyboard's contract, the one that shows a send key in place of the newline. on a
                // computer nobody fills that in, so the action never fired and enter did nothing at
                // all
                //
                // in preview and not in reaction: the field would consume enter to insert a newline
                // before we could read it
                modifier = Modifier.weight(1f).focusRequester(focusComposeur)
                    .onFocusChanged { composeurFocalise = it.isFocused }
                    // clicking in the composer closes the gif / emoji / sticker picker
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Press) selecteur = null
                            }
                        }
                    }
                    .onPreviewKeyEvent { touche ->
                    if (touche.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val liste = visibles
                    when {
                        // the arrows walk the list while it is open, and without it they belong to
                        // the text
                        liste != null && touche.key == Key.DirectionDown -> {
                            choix = (choix + 1).mod(liste.size); true
                        }

                        liste != null && touche.key == Key.DirectionUp -> {
                            choix = (choix - 1).mod(liste.size); true
                        }

                        // up arrow on an empty field edits your last message. the reference's
                        // gesture, and it was missing. only on an empty field: in the middle of a
                        // text the arrow belongs to the cursor
                        touche.key == Key.DirectionUp && brouillon.text.isEmpty() -> {
                            val dernier = messages.lastOrNull {
                                it.sender == monIdentifiant &&
                                    it.body is li.velvetchat.core.model.MessageBody.Text
                            }
                            val texte = (dernier?.body as? li.velvetchat.core.model.MessageBody.Text)?.text
                            if (dernier != null && texte != null) {
                                enEdition = dernier
                                reponseA = null
                                brouillon = TextFieldValue(texte, TextRange(texte.length))
                                true
                            } else false
                        }

                        liste != null && touche.key == Key.Escape -> { ferme = true; true }

                        liste != null && (touche.key == Key.Enter || touche.key == Key.Tab) -> {
                            completerAvec(liste[choix.coerceIn(0, liste.lastIndex)]); true
                        }

                        // shift enter breaks the line: that is the reference's gesture, and letting
                        // it through to the field is enough to get it
                        touche.key == Key.Enter && !touche.isShiftPressed -> { envoyer(); true }

                        else -> false
                    }
                },
                decorationBox = { champ ->
                    if (brouillon.text.isEmpty()) {
                        Text(
                            // the reference's long form: "in" a channel, "to" a person
                            if (libelle.startsWith("@")) t.envoyerAPersonne(libelle)
                            else t.envoyerDansSalon(libelle),
                            color = tokens.textMuted,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    champ()
                },
            )
            Spacer(Modifier.width(14.dp))
            Icone(
                Icones.micro,
                tint = when {
                    finalisation -> tokens.textMuted
                    dictee != null -> tokens.accent
                    else -> tokens.textSecondary
                },
                taille = 22.dp,
                description = if (dictee != null) t.dicteeArreter else t.dicterCourt,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).curseurMain().clickable { basculerDictee() },
            )
            Spacer(Modifier.width(14.dp))
            // red while recording, like every record light ever. idle it sits with the other
            // composer icons so it doesn't look like something's wrong
            Icone(
                Icones.appeler,
                tint = if (enregistreVocal) tokens.danger else tokens.textSecondary,
                taille = 22.dp,
                description = if (enregistreVocal) t.vocalEnvoyer else t.vocalEnregistrer,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).curseurMain().clickable { basculerVocal() },
            )
            listOf(
                Triple(Icones.gif, "GIF", 1),
                Triple(Icones.autocollant, t.autocollant, 2),
                Triple(Icones.emoji, t.emoji, 0),
                // apps removed: activities come later
            ).forEach { (icone, quoi, onglet) ->
                Spacer(Modifier.width(14.dp))
                // the null check on the tab left with apps: the list only holds integers now, and
                // the compiler flagged both branches as dead
                val actif = selecteur == onglet
                Icone(
                    icone,
                    tint = if (actif) tokens.textPrimary else tokens.textSecondary,
                    taille = 22.dp,
                    description = quoi,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).curseurMain()
                        .clickable { selecteur = if (actif) null else onglet },
                )
            }
        }
    }
}

/**
 * one conversation row
 *
 * no grouping by author for now: that is a display decision the phone makes in `Shell.kt`, from a
 * time gap and the previous author. taking it up here by rewriting it would guarantee two divergent
 * rules, and it will come down into `ui-commun` with the rest of the timeline
 */
/** a message and what its neighbourhood does to it: continues a group, starts a day */
private data class LigneConversation(
    val message: Message,
    val groupe: Boolean,
    val jour: String?,
)

/**
 * the day change marker, the reference's: a thin rule 16 from the left and 14 from the right, the
 * date in 12 semibold in the middle
 */
@Composable
private fun SeparateurJour(libelle: String) {
    val tokens = LocalTokens.current
    // two half rules rather than a pill over one rule: the pill takes the conversation's background
    // to hide the rule, and over a wallpaper that background is see through
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(tokens.border))
        Text(
            libelle,
            color = tokens.textMuted,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(tokens.border))
    }
}

@Composable
private fun LigneMessage(
    ligne: LigneConversation,
    horodateur: Horodateur,
    onRepondre: () -> Unit = {},
    onCopierLien: () -> Unit = {},
    onSupprimer: () -> Unit = {},
    onReagir: (String) -> Unit = {},
    /** a poll choice: empty means remove your vote */
    onVoter: (List<String>) -> Unit = {},
    onModifier: () -> Unit = {},
    onCopierTexte: () -> Unit = {},
    onCopierId: () -> Unit = {},
    onAuteur: (androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp) -> Unit = { _, _ -> },
    /** is the message already pinned? decides the menu entry's label */
    estEpingle: Boolean = false,
    onEpingler: () -> Unit = {},
    /** reads down to this message, and no further. null hides the entry */
    onMarquerLu: (() -> Unit)? = null,
    /** puts this message and everything after it back to unread. null hides the entry */
    onMarquerNonLu: (() -> Unit)? = null,
    /** "create a thread": null where it is not possible, inside a thread */
    onCreerFil: (() -> Unit)? = null,
    onTransferer: () -> Unit = {},
    /** the message reached from a search: the same gold background as a mention */
    surligne: Boolean = false,
) {
    val tokens = LocalTokens.current
    val m = ligne.message
    // somebody arriving, leaving, renaming the room: the reference's compact line, not a message
    (m.body as? MessageBody.System)?.let { systeme ->
        LigneSysteme(systeme, horodateur.heureSeule(m.timestamp))
        return
    }
    // "this message is talking to me": read from `m.mentions`, never guessed from the text, the
    // same rule as the phone. a muted gold background and a 2 point rule on the left, like the
    // reference
    val moi = LocalMonIdentifiant.current
    val minterpelle = surligne || (!m.isOwn && (m.mentionneToutLeSalon || (moi != null && moi in m.mentions)))

    // the action bar appears on hover, top right of the row, and overhangs above it, which is what
    // the reference does and what makes it readable on a one line message, where it would not have
    // the room to fit inside
    //
    // `hoverable` and not a hand written `pointerInput`: the mouse is a device compose knows about,
    // and the interaction source is readable by other components too
    val survol = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val survole by survol.collectIsHoveredAsState()

    // the overflow menu and the reaction picker keep the bar visible while they are open
    var menuOuvert by remember { mutableStateOf(false) }
    var selecteurOuvert by remember { mutableStateOf(false) }
    val barreVisible = survole || menuOuvert || selecteurOuvert
    // where the row sits in the window: the popups anchor to its top right corner. a `Popup` inside
    // a `LazyColumn` would otherwise anchor to the whole list (see `AuCurseur`)
    val cadre = remember { IntArray(3) }
    val densite = androidx.compose.ui.platform.LocalDensity.current
    val reglagesEmojis = LocalReglagesEmojis.current
    val rapides = if (barreVisible) emojisRapides(reglagesEmojis.frequents()) else emptyList()

    fun ancre(largeur: androidx.compose.ui.unit.Dp) = with(densite) {
        androidx.compose.ui.unit.IntOffset(
            cadre[0] + cadre[2] - (16.dp + largeur).roundToPx(),
            cadre[1] + 24.dp.roundToPx(),
        )
    }
    // the author's card opens to the right of the avatar, at the row's height
    fun ouvrirAuteur() = with(densite) { onAuteur(cadre[0].toDp() + 75.dp, cadre[1].toDp()) }
    if (menuOuvert) {
        androidx.compose.ui.window.Popup(
            popupPositionProvider = AuCurseur(ancre(280.dp)),
            onDismissRequest = { menuOuvert = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        ) {
            MenuMessage(
                m, emojisRapides(reglagesEmojis.frequents()),
                onReagir = onReagir,
                onAjouterReaction = { selecteurOuvert = true },
                onModifier = onModifier,
                onRepondre = onRepondre,
                onCopierTexte = onCopierTexte,
                onCopierLien = onCopierLien,
                onSupprimer = onSupprimer,
                onCopierId = onCopierId,
                estEpingle = estEpingle,
                onEpingler = onEpingler,
                onMarquerLu = onMarquerLu,
                onMarquerNonLu = onMarquerNonLu,
                onCreerFil = onCreerFil,
                onTransferer = onTransferer,
                onFermer = { menuOuvert = false },
            )
        }
    }
    if (selecteurOuvert) {
        androidx.compose.ui.window.Popup(
            popupPositionProvider = AuCurseur(ancre(380.dp)),
            onDismissRequest = { selecteurOuvert = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        ) {
            Box(
                Modifier.width(380.dp).clip(RoundedCornerShape(8.dp)).background(FOND_MENU)
                    .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp)),
            ) {
                PanneauEmojis(
                    packs = LocalPacks.current,
                    hauteur = 360.dp,
                    poignee = false,
                    onChoisir = { choix ->
                        // a custom reaction has the image itself as its key
                        onReagir(
                            when (choix) {
                                is ChoixEmoji.Unicode -> choix.caractere
                                is ChoixEmoji.Personnalise -> choix.emote.url
                            },
                        )
                        selecteurOuvert = false
                    },
                )
            }
        }
    }

    // a grouped message has no avatar and no header. that is what tells a conversation from a log:
    // redrawing the name and the time on every turn makes the timeline cold, which is the exact
    // complaint that produced this rule on the phone side
    Box(
        Modifier.fillMaxWidth()
            .then(
                if (minterpelle) Modifier.background(tokens.mentionHighlight)
                    .drawBehind {
                        drawRect(tokens.mentionBar, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
                    }
                else Modifier,
            )
            // hovered: white at 9%, radius 8, inset by 8 on each side
            .drawBehind {
                if (barreVisible) {
                    val marge = 8.dp.toPx()
                    drawRoundRect(
                        Color.White.copy(alpha = 0.09f),
                        topLeft = androidx.compose.ui.geometry.Offset(marge, 0f),
                        size = androidx.compose.ui.geometry.Size(size.width - 2 * marge, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    )
                }
            }
            .hoverable(survol)
            .onGloballyPositioned {
                val o = it.positionInWindow()
                cadre[0] = o.x.toInt(); cadre[1] = o.y.toInt(); cadre[2] = it.size.width
            },
    ) {
    if (ligne.groupe) {
        // the reference's follow up: 2 above and below, the text at 72, and in the 56 to its left
        // the time, shown on hover only
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)) {
            Box(Modifier.width(56.dp).height(22.dp), contentAlignment = Alignment.CenterEnd) {
                if (barreVisible) {
                    Text(
                        horodateur.heureSeule(m.timestamp),
                        color = tokens.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }
            Column {
                CorpsDeLigne(m, onVoter)
                LigneReactions(m.reactions, onReagir, onAjouter = { selecteurOuvert = true })
                EncartFil(m, onCreerFil)
            }
        }
        if (barreVisible) {
            BarreActions(
                m, rapides, onReagir,
                onAjouterReaction = { selecteurOuvert = true },
                onModifier = onModifier,
                onRepondre = onRepondre,
                onTransferer = onTransferer,
                onMenu = { menuOuvert = true },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        return@Box
    }

    // the reference's code, not a measure of a themed client: the avatar 16 from the edge, the text
    // at 72 (16 + 40 + 16), 17 between groups (`--custom-group-spacing-start`) and 2 of padding
    // above and below the message
    Column(Modifier.fillMaxWidth().padding(top = 17.dp)) {
    // the reply thread: above the avatar, the elbow line comes down to it
    m.citation?.let { CitationBureau(it, Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp)) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)) {
        Avatar(
            userId = m.sender,
            displayName = m.senderDisplayName,
            avatarUrl = m.senderAvatarUrl,
            taille = 40.dp,
            modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).curseurMain().clickable { ouvrirAuteur() },
            // the avatar decoration: a ring that overhangs without taking space. it comes from the
            // person's profile, so it renders everywhere an avatar is drawn
            decoration = LocalProfils.current[m.sender]?.decoration,
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // today's name, nickname included: a message carries the one its author had at
                    // the time, which is not what anybody wants to read
                    nomAffiche(m.sender, m.senderDisplayName),
                    // the gradient the person chose, failing that their role's colour
                    color = LocalRoleColors.current[m.sender] ?: tokens.textPrimary,
                    style = brosseDuPseudo(m.sender)?.let {
                        androidx.compose.ui.text.TextStyle(brush = it)
                    } ?: androidx.compose.ui.text.TextStyle.Default,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.width(4.dp))
                // the server tag being worn. read from the profile and never resolved from the
                // server: the reference shows it even to somebody who is not a member of the one
                // issuing it
                BadgeTagDe(m.sender)
                Spacer(Modifier.width(4.dp))
                Text(horodateur.heureSeule(m.timestamp), color = tokens.textMuted, fontSize = 12.sp)
            }
            CorpsDeLigne(m, onVoter)
            LigneReactions(m.reactions, onReagir, onAjouter = { selecteurOuvert = true })
            EncartFil(m, onCreerFil)
        }
    }
    }
    if (barreVisible) {
        BarreActions(
                m, rapides, onReagir,
                onAjouterReaction = { selecteurOuvert = true },
                onModifier = onModifier,
                onRepondre = onRepondre,
                onTransferer = onTransferer,
                onMenu = { menuOuvert = true },
                modifier = Modifier.align(Alignment.TopEnd),
            )
    }
    }
}

/**
 * a state change in the conversation, the reference's way: one line, its icon in the 72 point
 * gutter (a green arrow for an arrival, red for a departure), the sentence muted and the time after
 * it, with no avatar and no header
 */
@Composable
private fun LigneSysteme(systeme: MessageBody.System, heure: String) {
    val tokens = LocalTokens.current
    val (icone, teinte) = when (systeme.kind) {
        li.velvetchat.core.model.SystemKind.JOINED,
        li.velvetchat.core.model.SystemKind.INVITE_ACCEPTED -> Icones.arrivee to Color(0xFF23A55A)
        li.velvetchat.core.model.SystemKind.LEFT,
        li.velvetchat.core.model.SystemKind.KICKED,
        li.velvetchat.core.model.SystemKind.BANNED -> Icones.depart to Color(0xFFF23F43)
        else -> Icones.modifier to tokens.textMuted
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 2.dp).heightIn(min = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
            Icone(icone, tint = teinte, taille = 16.dp, description = null)
        }
        Text(
            systeme.phrase() + "  ",
            color = tokens.textMuted,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(heure, color = tokens.textMuted, fontSize = 12.sp)
    }
}

/**
 * a thread's card under its originating message: the thread's name and the message count,
 * clickable, which is how you reopen an existing thread from the conversation
 */
@Composable
private fun EncartFil(m: Message, onOuvrir: (() -> Unit)?) {
    val t = textes()
    val combien = m.reponsesEnFil ?: return
    if (onOuvrir == null) return
    val tokens = LocalTokens.current
    Row(
        Modifier.padding(top = 6.dp).widthIn(max = 480.dp).clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .curseurMain().clickable(onClick = onOuvrir).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(Icones.fils, tint = tokens.textSecondary, taille = 16.dp, description = null)
        Spacer(Modifier.width(8.dp))
        Text(
            li.velvetchat.core.model.Fil(m, combien, null).titre,
            color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        Text(t.messagesNombre(combien) + " ›", color = DORE, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * a message's hover bar, remeasured on the reference: 34 tall, radius 8, dark fill, the three most
 * used emoji, a rule, then add a reaction, edit (your own messages), forward, overflow. beige
 * icons, 29 apart
 *
 * reply, copy link and delete, which used to be there, moved into the overflow menu, which is where
 * the reference files them
 */
@Composable
private fun BarreActions(
    m: Message,
    rapides: List<String>,
    onReagir: (String) -> Unit,
    onAjouterReaction: () -> Unit,
    onModifier: () -> Unit,
    onRepondre: () -> Unit,
    onTransferer: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = textes()
    val forme = RoundedCornerShape(8.dp)
    Row(
        modifier
            // zero height for layout. the bar (34) overhung a one line message and grew the row on
            // hover, so the whole list jumped. it is measured normally, reported with no height,
            // and drawn 16 above the top of the row
            .layout { mesurable, contraintes ->
                val p = mesurable.measure(contraintes.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                layout(p.width, 0) { p.place(0, -16.dp.roundToPx()) }
            }
            .padding(end = 16.dp)
            .clip(forme)
            .background(Color(0xFF191C21))
            .border(1.dp, Color(0xFF1F2329), forme)
            // the reference's bar: 2 inside, 32 buttons, a 1 by 24 rule
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rapides.take(3).forEach { emoji ->
            BoutonBarre(onClick = { onReagir(emoji) }) { echelle ->
                Text(
                    emoji,
                    fontSize = 18.sp,
                    fontFamily = LocalPoliceEmoji.current,
                    modifier = Modifier.graphicsLayer { scaleX = echelle; scaleY = echelle },
                )
            }
        }
        Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(Color(0xFF242832)))
        ActionMessage(Icones.emoji, t.ajouterUneReaction, onAjouterReaction)
        // your own message is edited, somebody else's is answered: the reference's bar
        if (m.isOwn && m.body is li.velvetchat.core.model.MessageBody.Text) {
            ActionMessage(Icones.modifier, t.modifier, onModifier)
        } else if (!m.isOwn) {
            ActionMessage(Icones.repondre, t.repondre, onRepondre)
        }
        ActionMessage(Icones.transferer, t.transferer, onTransferer)
        ActionMessage(Icones.troisPoints, t.plus, onMenu)
    }
}

@Composable
private fun ActionMessage(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    quoi: String,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    BoutonBarre(onClick = onClick) { echelle ->
        Icone(
            icone,
            tint = tokens.textSecondary,
            taille = 20.dp,
            description = quoi,
            modifier = Modifier.graphicsLayer { scaleX = echelle; scaleY = echelle },
        )
    }
}

/**
 * one button of the hover bar, the reference's: 32 square, radius 6, a fill on hover, and its icon
 * growing to 1.1 over 0.4 s
 */
@Composable
private fun BoutonBarre(onClick: () -> Unit, contenu: @Composable (Float) -> Unit) {
    val survol = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val survole by survol.collectIsHoveredAsState()
    val echelle by androidx.compose.animation.core.animateFloatAsState(
        if (survole) 1.1f else 1f,
        androidx.compose.animation.core.tween(400),
        label = "echelleBouton",
    )
    Box(
        Modifier.size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (survole) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(survol)
            .curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { contenu(echelle) }
}

/** the quick emoji: the most used unicode ones, topped up with a starter set */
private fun emojisRapides(frequents: List<String>): List<String> =
    (frequents.filter { it.startsWith("u:") }.map { it.removePrefix("u:") } + listOf("❤️", "👍", "😂", "😮"))
        .distinct()

/**
 * a message's overflow menu, in the reference's order: four quick reactions in rounded squares,
 * then the entries. 246 wide, rows 36 apart, beige icons, rules between the groups. apps and
 * dictate removed by decision
 *
 * wired up: the reactions, add a reaction, edit, reply, copy text, copy link, delete, copy id.
 * drawn with no effect, the engine cannot do them yet: forward, create a thread, pin, mark unread
 */
@Composable
private fun MenuMessage(
    m: Message,
    rapides: List<String>,
    onReagir: (String) -> Unit,
    onAjouterReaction: () -> Unit,
    onModifier: () -> Unit,
    onRepondre: () -> Unit,
    onCopierTexte: () -> Unit,
    onCopierLien: () -> Unit,
    onSupprimer: () -> Unit,
    onCopierId: () -> Unit,
    estEpingle: Boolean,
    onEpingler: () -> Unit,
    onMarquerLu: (() -> Unit)?,
    onMarquerNonLu: (() -> Unit)?,
    onCreerFil: (() -> Unit)?,
    onTransferer: () -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    fun puis(action: () -> Unit): () -> Unit = { action(); onFermer() }
    val texte = m.body is li.velvetchat.core.model.MessageBody.Text
    Column(
        // 280 and not 246: our font is wider than the reference's, and the longest entry stayed
        // truncated at 246 and then at 260, id chip included
        Modifier.width(280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FOND_MENU)
            .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rapides.take(4).forEach { emoji ->
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF24272F)).curseurMain().clickable(onClick = puis { onReagir(emoji) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 22.sp, fontFamily = LocalPoliceEmoji.current)
                }
            }
        }
        EntreeMenu(t.ajouterUneReaction, icone = Icones.emoji, sousMenu = true, taille = 13.sp, onClick = puis(onAjouterReaction))
        FiletMenu()
        if (m.isOwn && texte) EntreeMenu(t.modifierLeMessage, icone = Icones.modifier, taille = 13.sp, onClick = puis(onModifier))
        EntreeMenu(t.repondre, icone = Icones.repondre, taille = 13.sp, onClick = puis(onRepondre))
        EntreeMenu(t.transferer, icone = Icones.transferer, taille = 13.sp, onClick = puis(onTransferer))
        if (onCreerFil != null) EntreeMenu(t.creerUnFil, icone = Icones.fils, taille = 13.sp, onClick = puis(onCreerFil))
        FiletMenu()
        EntreeMenu(t.copierLeTexte, icone = Icones.copier, actif = texte, taille = 13.sp, onClick = puis(onCopierTexte))
        EntreeMenu(
            if (estEpingle) t.desepinglerLeMessage else t.epinglerLeMessage,
            icone = if (estEpingle) Icones.epingleActif else Icones.epingle,
            taille = 13.sp,
            onClick = puis(onEpingler),
        )
        onMarquerLu?.let {
            EntreeMenu(t.marquerCommeLu, icone = Icones.valide, taille = 13.sp, onClick = puis(it))
        }
        onMarquerNonLu?.let {
            EntreeMenu(t.marquerCommeNonLu, icone = Icones.messages, taille = 13.sp, onClick = puis(it))
        }
        EntreeMenu(t.copierLienMessage, icone = Icones.copierLien, taille = 13.sp, onClick = puis(onCopierLien))
        if (m.isOwn) {
            FiletMenu()
            EntreeMenu(t.supprimerLeMessage, icone = Icones.supprimer, couleur = ROSE_MENU, taille = 13.sp, onClick = puis(onSupprimer))
        }
        FiletMenu()
        EntreeMenu(t.copierIdMessage, pastilleId = true, taille = 13.sp, onClick = puis(onCopierId))
    }
}

@Composable
internal fun CorpsDeLigne(m: Message, onVoter: ((List<String>) -> Unit)? = null) {
    val t = textes()
    val tokens = LocalTokens.current
    Column {
            when (val corps = m.body) {
                // the same rendering as the phone, by construction. `CorpsFormate` borrows nothing
                // from android any more: bold, code, quotes, mentions and spoilers come from the
                // same parser on both sides. that is exactly what the html seam was meant to avoid,
                // two divergent renderings of one message
                //
                // a message that is only a gif link shows itself instead of being read. that is the
                // display consequence of sending gifs as links: the message is text, so it would
                // draw as a blue link. `LienGif` decides on its own whether the address is
                // loadable, and its rule exists so the sender cannot name the host
                is MessageBody.Text -> {
                    val enLigne = LocalGifsEnLigne.current
                    val lien = remember(corps.text, enLigne) {
                        if (enLigne) li.velvetchat.core.gif.LienGif.dans(corps.text) else null
                    }
                    // a failure falls back on the text: a vanished host at least leaves a readable
                    // address. we degrade, we never hide
                    var echec by remember(lien) { mutableStateOf(false) }
                    if (lien != null && !echec) {
                        GifDuMessage(lien) { echec = true }
                    } else {
                        CorpsFormate(corps.text, corps.html)
                        // under the text, never instead of it: the preview completes the message,
                        // it does not replace it, unlike the gif above, whose link is only the
                        // image
                        ApercuDuLien(corps.text)
                    }
                }

                is MessageBody.Media -> ImageDeMessage(corps)

                is MessageBody.Redacted -> Text(
                    t.messageSupprime,
                    color = tokens.textMuted,
                    fontSize = 14.sp,
                )

                // worded in plain language like on the phone, they used to draw as their class
                // name. and the polls, which showed nowhere on the desktop: the message fell into
                // the `else` and rendered its class name. the card is the phone's, shared from the
                // start
                is MessageBody.Poll -> CartePoll(
                    poll = corps,
                    // a poll still in the send queue has no event id, so the vote would be refused.
                    // we disable it rather than offer it and have it do nothing
                    confirme = m.sendState == li.velvetchat.core.model.SendState.Sent,
                    onVoter = onVoter ?: {},
                )

                is MessageBody.System -> Text(corps.phrase(), color = tokens.textMuted, fontSize = 13.sp)

                is MessageBody.Undecryptable -> Text(
                    when (corps.reason) {
                        li.velvetchat.core.model.UndecryptableReason.AVANT_NOTRE_ARRIVEE -> t.messageAvantArrivee
                        li.velvetchat.core.model.UndecryptableReason.CLE_REFUSEE -> t.cleNonPartagee
                        li.velvetchat.core.model.UndecryptableReason.CLE_MANQUANTE -> t.cleIntrouvable
                        li.velvetchat.core.model.UndecryptableReason.OLM -> t.chiffrementIllisible
                        li.velvetchat.core.model.UndecryptableReason.UNKNOWN -> t.dechiffrementImpossible
                    },
                    color = tokens.textMuted,
                    fontSize = 14.sp,
                )

                else -> Text(
                    corps::class.simpleName.orEmpty(),
                    color = tokens.textMuted,
                    fontSize = 14.sp,
                )
            }
            // "(edited)" under the text, like the reference. found in testing: nothing signalled an
            // edit
            if (m.isEdited && m.body is MessageBody.Text) {
                Text(t.modifie, color = tokens.textMuted, fontSize = 11.sp)
            }
    }
}

/**
 * a reply's quote, taken from the phone (`CitationDeReponse`): the elbow line leaves the middle of
 * the row, runs to the centre of the avatar gutter, then drops to it. a 16 point mini avatar, the
 * name in the role's colour, an excerpt on one line
 */
@Composable
private fun CitationBureau(citation: li.velvetchat.core.model.Citation, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    val gouttiere = 40.dp
    Row(
        modifier.fillMaxWidth()
            .drawBehind {
                val cx = gouttiere.toPx() / 2f
                val cy = size.height / 2f
                val rayon = 4.dp.toPx()
                val chemin = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, size.height)
                    lineTo(cx, cy + rayon)
                    quadraticTo(cx, cy, cx + rayon, cy)
                    lineTo(gouttiere.toPx() + 10.dp.toPx(), cy)
                }
                drawPath(
                    chemin,
                    color = tokens.traitCitation,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            }
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(gouttiere + 16.dp))
        Avatar(citation.auteurId, citation.auteur, citation.auteurAvatarUrl, 16.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            "@" + citation.auteur,
            color = LocalRoleColors.current[citation.auteurId] ?: tokens.textSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        BadgeTagDe(citation.auteurId)
        Spacer(Modifier.width(4.dp))
        Text(
            citation.apercu,
            color = tokens.textSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * a thread, as a panel to the right of the conversation, like the reference, and not as a full
 * screen like on the phone: you keep the room in view
 *
 * opened on a message ([racine]), the panel creates nothing until you write: in matrix a thread is
 * the first reply quoting its root (`m.thread`). with no root, from the thread list's create, it is
 * a new thread: a name, then the first message, and the name is posted as the originating message
 * ([MatrixEngine.creerFil])
 */
@Composable
private fun PanneauFil(moteur: MatrixEngine, salonId: String, racine: Message?, modifier: Modifier = Modifier, onFermer: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    val portee = rememberCoroutineScope()
    var racineId by remember(racine?.id) { mutableStateOf(racine?.id) }
    var liste by remember(racineId) { mutableStateOf<List<Message>>(emptyList()) }
    LaunchedEffect(racineId) {
        val id = racineId ?: return@LaunchedEffect
        essayer { moteur.filFlow(salonId, id).collectLatest { liste = it } }
            .onFailure { grogner("fil illisible", it) }
    }
    val origine = racine ?: liste.firstOrNull { it.id == racineId }
    val reponses = liste.filter { it.id != racineId }
    val nouveau = racineId == null
    var nom by remember(racine?.id) { mutableStateOf("") }
    var brouillon by remember(racine?.id) { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }
    val horodateur = remember { Horodateur() }
    val defilement = rememberLazyListState()
    LaunchedEffect(reponses.size) { if (reponses.isNotEmpty()) defilement.animateScrollToItem(reponses.size) }
    fun envoyer() {
        val texte = brouillon.trim()
        if (texte.isEmpty()) return
        brouillon = ""
        val id = racineId
        portee.launch {
            if (id != null) {
                runCatching { moteur.repondreDansFil(salonId, id, texte) }
            } else {
                runCatching { moteur.creerFil(salonId, nom.trim().ifEmpty { t.nouveauFil }, texte) }
                    .onSuccess { racineId = it; erreur = null }
                    .onFailure { brouillon = texte; erreur = t.filNonCree }
            }
        }
    }
    PanneauLateral(modifier) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(Icones.fils, tint = tokens.textSecondary, taille = 20.dp, description = null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    nouveau -> t.nouveauFil
                    origine != null -> li.velvetchat.core.model.Fil(origine, reponses.size, reponses.lastOrNull()).titre
                    else -> t.fil
                },
                color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Icone(
                Icones.fermer, tint = tokens.textSecondary, taille = 20.dp, description = t.fermerLeFil,
                modifier = Modifier.curseurMain().clickable(onClick = onFermer),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.border))
        if (nouveau) {
            // the reference: the thread icon in a circle, the thread's name, and the composer at
            // the bottom
            Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) { Icone(Icones.fils, tint = tokens.textPrimary, taille = 32.dp, description = null) }
                Spacer(Modifier.height(16.dp))
                Text(t.nomDuFil, color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (nom.isEmpty()) Text(t.nouveauFil, color = tokens.textMuted, fontSize = 14.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = nom,
                        onValueChange = { nom = it.take(100) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = tokens.textPrimary, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.textPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                erreur?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = tokens.danger, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = defilement) {
                item(key = "racine") {
                    origine?.let { LigneMessage(LigneConversation(it, groupe = false, jour = null), horodateur) }
                    Text(
                        if (reponses.isEmpty()) t.premiereReponseOuvreLeFil
                        else t.reponsesNombre(reponses.size),
                        color = tokens.textMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                    )
                    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(tokens.border))
                }
                items(reponses.size, key = { reponses[it].id }) { i ->
                    val m = reponses[i]
                    val precedent = reponses.getOrNull(i - 1)
                    LigneMessage(
                        LigneConversation(m, groupe = precedent != null && continueLeGroupe(precedent, m, tokens.density.messageGroupWindowMs), jour = null),
                        horodateur,
                    )
                }
            }
        }
        Box(
            Modifier.padding(12.dp).fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (brouillon.isEmpty()) {
                Text(
                    if (nouveau || reponses.isEmpty()) t.commencerLaConversation
                    else t.envoyerDansLeFil,
                    color = tokens.textMuted, fontSize = 14.sp,
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = brouillon,
                onValueChange = { brouillon = it },
                textStyle = androidx.compose.ui.text.TextStyle(color = tokens.textPrimary, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.textPrimary),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                    if (it.key == androidx.compose.ui.input.key.Key.Enter && !it.isShiftPressed &&
                        it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown
                    ) { envoyer(); true } else false
                },
            )
        }
    }
}

/**
 * search results, as a right hand panel like the reference: the room's messages containing the
 * text, case and accents ignored, over the last thousand events (see
 * [MatrixEngine.chercherDansSalon]). a click scrolls the conversation to the message
 */
@Composable
private fun PanneauRecherche(
    moteur: MatrixEngine,
    salonId: String,
    requete: String,
    modifier: Modifier = Modifier,
    onOuvrir: (Message) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val horodateur = remember { Horodateur() }
    var resultats by remember(salonId, requete) { mutableStateOf<List<Message>?>(null) }
    LaunchedEffect(salonId, requete) {
        resultats = runCatching { moteur.chercherDansSalon(salonId, requete) }.getOrDefault(emptyList())
    }
    PanneauLateral(modifier) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (val r = resultats) {
                    null -> t.rechercheEnCours
                    else -> t.resultatsNombre(r.size)
                },
                color = tokens.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            Icone(
                Icones.fermer, tint = tokens.textSecondary, taille = 20.dp, description = t.fermerLaRecherche,
                modifier = Modifier.curseurMain().clickable(onClick = onFermer),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.border))
        val liste = resultats.orEmpty()
        if (resultats != null && liste.isEmpty()) {
            Text(
                t.aucunMessageContenantCourt(requete),
                color = tokens.textMuted, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(liste.size, key = { liste[it].id }) { i ->
                Box(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.04f))
                        .curseurMain().clickable { onOuvrir(liste[i]) },
                ) {
                    LigneMessage(LigneConversation(liste[i], groupe = false, jour = null), horodateur)
                }
            }
        }
    }
}

/**
 * a right hand panel (thread, search), resizable by its left edge, with the width remembered
 */
@Composable
private fun PanneauLateral(modifier: Modifier, contenu: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(modifier.width(Largeurs.panneau).fillMaxHeight()) {
        Column(Modifier.fillMaxSize(), content = contenu)
        PoigneeLargeur(
            Modifier.align(Alignment.CenterStart),
            versLaGauche = true,
            lire = { Largeurs.panneau },
            ecrire = { Largeurs.panneau = it.coerceIn(Largeurs.PANNEAU_MIN, Largeurs.PANNEAU_MAX) },
        )
    }
}

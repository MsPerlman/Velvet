package li.velvetchat.app.ui

import li.velvetchat.core.tr

import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import li.velvetchat.core.ConnectionState
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.essayer
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.avec
import li.velvetchat.core.model.Decorations
import li.velvetchat.core.model.MediaKind
import li.velvetchat.core.model.MediaRef
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.RACCOURCI
import li.velvetchat.core.model.composerMessage
import li.velvetchat.core.model.estRaccourciConnu
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.SendState
import li.velvetchat.core.model.SystemKind
import li.velvetchat.core.model.Server
import li.velvetchat.core.model.UndecryptableReason

/**
 * the vertical slice: server rail, channels, timeline, composer
 *
 * everything on screen comes from `MatrixEngine` and nothing else. no sdk import here, which is the
 * property this slice exists to prove, and it checks by reading the imports rather than by looking
 * at the screen
 */
@Composable
fun Shell(
    engine: MatrixEngine,
    servers: List<Server>,
    connection: ConnectionState,
) {
    val tokens = LocalTokens.current
    val t = textes()
    val contexteApp = androidx.compose.ui.platform.LocalContext.current

    // the call screen floats above the whole app, being a dialog. placed here, before any early
    // return, it survives navigating between rooms, since the call's state lives in vocalmanager
    // and not in a screen. it draws nothing while no call is active
    //
    // it needs the engine, and that is new: the tiles used to draw initials on a gradient, which
    // asks nobody for anything. now that they carry the real profile photo, `Avatar` needs
    // `LocalEngine` to download it, and that provider sits much further down, after the early
    // returns. so the call crashed the second the screen appeared, with `IllegalStateException:
    // LocalEngine was not provided`
    CompositionLocalProvider(LocalEngine provides engine) {
        EcranAppelOverlay()
        // its collapsed counterpart. the two exclude each other, one returning null while the other
        // shows, so mounting them side by side here is safe, and the bar goes through a `Popup`,
        // which puts it at the bottom of the screen without depending on the layout that follows
        BarreAppelReplie()
    }

    var serverId by remember { mutableStateOf<String?>(null) }
    var channelId by remember { mutableStateOf<String?>(null) }

    // what a background job says when it fails. without it, a creation that failed after the sheet
    // closed would be silent, the silent failure again
    var messageFugace by remember { mutableStateOf<String?>(null) }

    // channel moves, shown before they are written: channel id to target category name (null for
    // the root)
    //
    // measured against the homeserver: a move takes 20 s, in four state writes of about 5 s each,
    // which is synapse's rate limit making the sdk wait without saying anything once the burst is
    // spent. waiting for the sync to see the channel change place meant a gesture that looks
    // ignored for twenty seconds
    val deplacementsEnCours = remember(serverId) { mutableStateMapOf<String, String?>() }

    // initial selection and recovery: on the first load, when the server changes, and when the
    // chosen channel disappears (left from another device), we fall back on something valid rather
    // than show an empty screen with no explanation
    //
    // `channelId` is a key, and that is what makes the repair universal
    //
    // the effect only listened to `servers`, so resetting `channelId` triggered nothing and left
    // the home screen showing indefinitely, reported from outside as it coming back forever
    //
    // adding `serverId` was not enough, and measuring showed it: tapping the already selected
    // server in the rail does not change `serverId`, so the key stayed identical and the effect did
    // not restart. and that is the commonest gesture of all. listening to `channelId` itself means
    // any path that invalidates it, whatever it is, including the ones not written yet, triggers
    // its own repair
    //
    // no loop: the effect only writes when the value is invalid, and what it writes is valid by
    // construction
    LaunchedEffect(servers, serverId, channelId) {
        val server = servers.firstOrNull { it.id == serverId } ?: servers.firstOrNull()
        serverId = server?.id
        if (server != null && server.channels.none { it.id == channelId }) {
            channelId = server.channels.firstOrNull()?.id
        }
    }

    val server = servers.firstOrNull { it.id == serverId }?.let { ouvert ->
        if (deplacementsEnCours.isEmpty()) ouvert else ouvert.copy(
            channels = ouvert.channels.map { salon ->
                if (salon.id in deplacementsEnCours) salon.copy(categorie = deplacementsEnCours[salon.id])
                else salon
            },
        )
    }
    LaunchedEffect(servers) {
        // the flow caught up with the screen: every confirmed move stops being simulated
        val reel = servers.firstOrNull { it.id == serverId }?.channels.orEmpty()
        deplacementsEnCours.keys.toList().forEach { id ->
            if (reel.any { it.id == id && it.categorie == deplacementsEnCours[id] }) {
                deplacementsEnCours.remove(id)
            }
        }
    }
    val channel = server?.channels?.firstOrNull { it.id == channelId }

    // on a phone there are two screen levels, and the rail belongs to the first
    //
    // the rail and the channel list form one block that disappears entirely when you enter a
    // conversation, which then takes the full width. the previous version kept the rail visible
    // during the conversation, leaving only 72 dp to read and write in. the rail and the channels
    // are not two components one of which is fixed, they are one level
    //
    // what we lose: the other servers' badges while reading. real time is already covered by the
    // notifications, and the rest is one back gesture away
    var vueSalons by remember { mutableStateOf(true) }

    // direct messages are a third level and not another server. they keep the rail, since you are
    // not going deeper into a place, you are looking at another list, and they replace the channel
    // panel, which has nothing to say here
    var vueMessages by remember { mutableStateOf(false) }

    /**
     * the last direct conversation opened, to reopen it when you come back to messages
     *
     * reported from outside, and it is the reference's behaviour: reopening direct messages puts
     * the last conversation back behind the list, and pushing the list aside drops you into it.
     * here you came back to an empty panel and had to find the conversation in the list every time
     *
     * `rememberSaveable` and not `remember`: the whole point of the marker is surviving rotation
     * and coming back from the background, which are exactly the moments you return to where you
     * were
     */
    var dernierDirect by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<String?>(null)
    }

    // the direct conversations are reread every time you enter the screen rather than held as a
    // flow: `m.direct` changes a few times a month, and one more flow would cost a permanent
    // listener for a list people rarely look at
    var directs by remember {
        mutableStateOf<List<li.velvetchat.core.model.DirectMessage>>(emptyList())
    }
    // is the first load done? without this flag the messages screen cannot tell "still loading"
    // from "you have no conversations", and showed the second, a "no direct conversations" that
    // flashed for a second or two before the list arrived. reported as stressful, it looks like
    // everything is gone
    var dmCharges by remember { mutableStateOf(false) }
    // reread when the messages screen opens and after every relation change: the friend list is a
    // filter over the conversations, never a second list stored alongside
    var mesAmis by remember { mutableStateOf<Set<String>>(emptySet()) }

    // a counter rather than a boolean: two additions in a row both have to restart the read, which
    // a flag returning to the same value would not do
    var relationsChangees by remember { mutableStateOf(0) }

    // the invites received, see `InvitationRecue` for what their absence cost
    //
    // reread on `servers` and not when a screen opens: `serversFlow` emits on every change of the
    // room list, and an incoming invite is such a change. so it is the signal we already have, and
    // it makes the invite visible without opening anything
    var invitations by remember {
        mutableStateOf<List<li.velvetchat.core.model.InvitationRecue>>(emptyList())
    }
    var invitationVisee by remember {
        mutableStateOf<li.velvetchat.core.model.InvitationRecue?>(null)
    }
    // on the dedicated flow and not on `servers`: see `invitationsFlow`, the first version woke on
    // the server list and therefore missed exactly the case that matters, an invite received when
    // you have no server yet
    LaunchedEffect(connection) {
        // the flow demands a started sync and throws otherwise, so we wait for it, and the
        // `connection` key restarts the collection after a reconnect
        if (connection == ConnectionState.STOPPED) return@LaunchedEffect
        essayer { engine.invitationsFlow().collect { invitations = it } }
            .onFailure { android.util.Log.w("Velvet", "Invitations illisibles", it) }
    }

    // loaded ahead of time, as soon as the sync runs, rather than only when the screen opens. that
    // is what kills the empty screen: while you look at a server the dm list is already there, so
    // opening it is instant. `vueMessages` stays as a key to refresh on entry, for a dm opened on
    // another device, and it no longer flickers, since `directs` is never emptied during the reread
    // and the old list stays on screen until the new one arrives
    //
    // as a flow, and not reread on three keys that say nothing about rooms. it used to be
    // `LaunchedEffect(connection, relationsChangees, vueMessages)`, and none of the three moves
    // when a conversation arrives through the sync: a conversation created a moment ago, or created
    // on another device, stayed invisible until you left the messages view and came back. the
    // symptom reads as "the sync is behind" when the server already had everything
    LaunchedEffect(connection) {
        if (connection == ConnectionState.STOPPED) return@LaunchedEffect
        essayer {
            engine.directsFlow().collect {
                directs = it
                dmCharges = true
            }
        }
            .onFailure { android.util.Log.w("Velvet", "Liste des messages prives illisible", it) }
    }

    // the friends keep their own key: they do not live in the room list but in account data, and
    // nothing in a room diff concerns them
    LaunchedEffect(connection, relationsChangees) {
        if (connection == ConnectionState.STOPPED) return@LaunchedEffect
        mesAmis = runCatching { engine.amis().toSet() }.getOrDefault(emptySet())
    }

    // the incoming call banner, at the root and for every room
    //
    // it used to live inside the conversation, so it only showed to somebody already looking at the
    // right place. checked with two devices: the call goes out, the other phone shows nothing and
    // does not ring. here it floats above any screen
    //
    // the map is rebuilt when the room list changes, not on every frame
    val salonsSurveilles = remember(servers, directs) {
        buildMap {
            // a voice room does not ring. you go in when you want, like a room with an open door,
            // and a ring is for a private call, which is addressed to somebody. making the whole
            // server ring because one person walked into a voice room is the opposite of what it is
            servers.forEach { serveur ->
                serveur.channels
                    .filterNot {
                        it.kind == li.velvetchat.core.model.ChannelKind.VOICE
                    }
                    .forEach { put(it.id, it.name) }
            }
            directs.forEach { put(it.roomId, it.name) }
        }
    }
    CompositionLocalProvider(LocalEngine provides engine) {
        BanniereAppelEntrant(salonsSurveilles)
    }

    // the correspondents' presence, read after the list and separately
    //
    // separately because `directs()` has no way to know it: it does not live in the rooms' state
    // but in a separate endpoint, one request per person. making the engine carry it would force
    // `directs()` to wait out ten round trips before returning a list we could show straight away
    var presences by remember { mutableStateOf<Map<String, Presence>>(emptyMap()) }
    LaunchedEffect(directs) {
        if (directs.isEmpty()) return@LaunchedEffect
        // a batched read: `associate { presence(...) }` chained N round trips in series, so a list
        // of ten conversations waited out ten latencies before appearing. see
        // `MatrixEngine.presences`
        val statuts = runCatching { engine.presences(directs.map { it.userId }) }
            .getOrDefault(emptyMap())
        presences = directs.associate { d ->
            d.userId to (statuts[d.userId]?.presence ?: Presence.INCONNU)
        }
    }

    // an open dm is not a server channel: it has no parent space, so the rail has nothing to
    // select. we keep it apart rather than pass it off as a channel, which would mean inventing a
    // server that does not exist
    var directOuvert by remember {
        mutableStateOf<li.velvetchat.core.model.DirectMessage?>(null)
    }
    // the marker is set here, at the single place where the open conversation changes, and not in
    // the seven callers that set it. the first omission would have been enough to make people doubt
    // the memory works
    LaunchedEffect(directOuvert?.roomId) {
        directOuvert?.roomId?.let { dernierDirect = it }
    }

    var nouvelleConversation by remember { mutableStateOf(false) }

    // a new message elsewhere gets the banner (see `BanniereMessage`). we compare the notifying
    // unread counts room by room, and the ones going up outside the open conversation trigger
    // reading the alerts, which carry the author, the photo and the preview. muted rooms do not
    // notify
    val nonLusParSalon = remember(servers, directs) {
        buildMap {
            servers.forEach { sv -> sv.channels.forEach { put(it.id, it.unread.notifications) } }
            directs.forEach { put(it.roomId, it.unread.notifications) }
        }
    }
    var nonLusAvant by remember { mutableStateOf<Map<String, Int>?>(null) }
    var banniere by remember { mutableStateOf<li.velvetchat.core.model.AlerteSalon?>(null) }
    LaunchedEffect(nonLusParSalon) {
        val avant = nonLusAvant
        nonLusAvant = nonLusParSalon
        if (avant == null) return@LaunchedEffect
        val ouvert = if (vueMessages) directOuvert?.roomId else channelId
        val montes = nonLusParSalon.filter { (id, n) -> n > (avant[id] ?: 0) && id != ouvert }.keys
        if (montes.isEmpty()) return@LaunchedEffect
        runCatching { engine.alertes() }.getOrNull()
            ?.filter { it.salonId in montes && !it.aMoi }
            ?.maxByOrNull { it.horodatage }
            ?.let { banniere = it }
    }
    LaunchedEffect(banniere) {
        if (banniere != null) { kotlinx.coroutines.delay(4_500); banniere = null }
    }
    banniere?.let { a ->
        CompositionLocalProvider(LocalEngine provides engine) {
            BanniereMessage(a) {
                banniere = null
                if (a.estDirect) {
                    vueMessages = true
                    directOuvert = directs.firstOrNull { it.roomId == a.salonId }
                } else {
                    servers.firstOrNull { sv -> sv.channels.any { it.id == a.salonId } }?.let { sv ->
                        vueMessages = false
                        directOuvert = null
                        serverId = sv.id
                        channelId = a.salonId
                    }
                }
            }
        }
    }

    // the system back gesture returns to the first level rather than leaving the app. on a phone in
    // gesture navigation, dragging from the left edge is that gesture, and wiring it here gives the
    // expected behaviour without fighting the system for the edge zone, which no app wins
    androidx.activity.compose.BackHandler(enabled = !vueSalons) { vueSalons = true }

    // the full screen viewer lives here and not in `Conversation`
    //
    // it was there first, with the comment "the first ancestor that fills the screen", which was
    // false: `Conversation` sits after the server rail inside a `Row`. on screen the rail stayed
    // visible on the left and the image overflowed to the right. the only ancestor that really
    // covers the window is this one
    var mediaOuvert by remember { mutableStateOf<DemandeVisionneuse?>(null) }

    // the home sheets live at `Shell` level: creating a server changes the rail, inviting concerns
    // the current server, and the profile depends on no room. none of them
    var feuilleServeur by remember { mutableStateOf(false) }
    var feuilleReglages by remember { mutableStateOf(false) }
    var feuilleInvitation by remember { mutableStateOf(false) }
    var feuilleEmojis by remember { mutableStateOf(false) }
    var feuilleSalon by remember { mutableStateOf(false) }
    // the server's members, loaded on demand from its first channel: the list is the same for every
    // channel of a space, so asking one is enough
    var membresServeur by remember(serverId) {
        mutableStateOf<List<li.velvetchat.core.model.Member>>(emptyList())
    }
    // the server's decorative roles and who has them, see `RoleDecoratif`. reread on
    // `decorationsChangees`: handing out a role from the moderation sheet has to show at once in
    // the member list and in the timeline, and a counter is what triggers a reread even when the
    // value comes back identical
    var decorations by remember(serverId) { mutableStateOf(Decorations()) }
    var decorationsChangees by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var feuilleProfil by remember { mutableStateOf(false) }

    // the control centre

    /**
     * null while we do not know, then the server's real answer. asked once per session rather than
     * every time the profile opens: about ten accounts on this server, the call is trivial, and
     * redoing it would buy nothing but one more round trip
     */
    var estAdministrateur by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        // under MAS an admin logs in without the rights and asks for them from the control centre
        estAdministrateur = runCatching { engine.estAdministrateurServeur() || engine.peutDemanderAdmin() }
            .getOrDefault(false)
    }
    var centreDeControleOuvert by remember { mutableStateOf(false) }

    // joining a voice room, permission included. `BoutonAppel` already walks this path for a
    // conversation's call, and it lives in a composable whose launcher cannot be borrowed from an
    // `onClick`, hence this duplicate and not another
    val contexteVocal = androidx.compose.ui.platform.LocalContext.current

    /**
     * the joining itself. separate from the gesture because it has two triggers: the click when the
     * permission is already there, and the system's answer when it was not
     */
    suspend fun entrerEnVocal(salonId: String) {
        if (li.velvetchat.app.VocalManager.etat.value.salonId == salonId) return
        val nom = servers.flatMap { it.channels }.firstOrNull { it.id == salonId }?.name
        val info = runCatching { engine.jetonVocal(salonId) }.getOrNull() ?: return
        li.velvetchat.app.VocalManager.rejoindre(contexteVocal, engine, salonId, info, nom)
    }

    // the room we are waiting to open while somebody answers the microphone prompt. a
    // `LaunchedEffect` on this value was not enough: refused then granted, the key has not changed
    // and nothing restarted, which is what left the voice room open as a plain conversation after
    // saying yes
    var vocalEnAttente by remember { mutableStateOf<String?>(null) }
    val demanderMicroVocal = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { accorde ->
        val salonId = vocalEnAttente
        vocalEnAttente = null
        if (accorde && salonId != null) scope.launch { entrerEnVocal(salonId) }
    }

    // the voice room being previewed before joining, see [ApercuVocal]
    var apercuVocal by remember { mutableStateOf<String?>(null) }

    fun rejoindreVocal(salonId: String) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                contexteVocal, android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            scope.launch { entrerEnVocal(salonId) }
        } else {
            vocalEnAttente = salonId
            demanderMicroVocal.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // the blocked people (`m.ignored_user_list`), read once then reread after every change. it is
    // account data, valid for every room at once, so it lives here and not per conversation
    var personnesBloquees by remember { mutableStateOf<Set<String>>(emptySet()) }

    // the muted channels, dimmed in the list like the reference does. reread when a channel's mode
    // changes, otherwise muting one would only show after a restart
    var salonsMuets by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        personnesBloquees = runCatching { engine.personnesBloquees().toSet() }
            .getOrDefault(emptySet())
    }
    var profil by remember { mutableStateOf<li.velvetchat.core.model.Profile?>(null) }

    // your own status, and the mute's state. read at startup: the user bar shows them permanently,
    // and it used to write "online" in code, which is to say an assertion nothing checked
    var monStatut by remember { mutableStateOf(li.velvetchat.core.model.Statut.INCONNU) }
    var silence by remember { mutableStateOf(false) }
    var feuilleStatut by remember { mutableStateOf(false) }
    var feuilleGif by remember { mutableStateOf(false) }

    // read once here rather than in every message body: the timeline recomposes dozens of them per
    // scroll, and each would reopen the preferences file. the settings sheet updates this state,
    // without which the change would only take effect on restart
    val gifsEnLigne = remember(PrefsGif.revision) { PrefsGif.gifsEnLigne(contexteApp) }

    // a mention waiting to be dropped into the composer, from a member's card. set back to null by
    // the composer once placed, otherwise it would drop again on every recomposition
    var mentionADeposer by remember { mutableStateOf<String?>(null) }

    // the photo picker lives at shell level and not in the profile screen: an activity launcher has
    // to be registered before the composition using it appears, and the profile screen comes and
    // goes
    //
    // what was just picked, waiting to be cropped, and where to send it next. the picker returns a
    // whole photo and an avatar is a square, so we used to show its centre without ever asking: a
    // landscape photo gave an avatar cropped at random, and the only recourse was cropping in the
    // gallery before coming back
    var recadrage by remember { mutableStateOf<DemandeRecadrage?>(null) }

    val choisirAvatar = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            recadrage = DemandeRecadrage(uri, rond = true) { fichier ->
                engine.setAvatar(fichier.absolutePath)
                profil = engine.profile()
            }
        }
    }
    // same reason as the avatar picker just above: a launcher registers at the level that does not
    // disappear. the server settings screen comes and goes
    val choisirIconeServeur = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val cible = serverId
        if (uri != null && cible != null) {
            // a rounded square and not a circle: that is the shape of a server icon in the rail,
            // and cropping to a shape nothing will display is cropping for nothing
            recadrage = DemandeRecadrage(uri, rond = false) { fichier ->
                engine.setAvatarServeur(cible, fichier.absolutePath)
                // nothing to reread by hand: the engine pushes the structure signal, and the rail
                // as well as this screen receive the new `Server` through `serversFlow`
            }
        }
    }

    // taking the photo instead of picking one
    //
    // the three profile images, avatar, banner, server icon, all went through the gallery. a sheet
    // now asks where the image comes from, and the camera writes into `cache/envois` a file the
    // rest of the path treats like any other
    var sourceImage by remember { mutableStateOf<SourceImage?>(null) }
    var sourceEnCours by remember { mutableStateOf<SourceImage?>(null) }
    var photoPrise by remember { mutableStateOf<java.io.File?>(null) }
    val prendrePhotoProfil = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { reussie ->
        val fichier = photoPrise
        val source = sourceEnCours
        photoPrise = null
        sourceEnCours = null
        if (reussie && fichier != null && fichier.length() > 0) source?.appareil?.invoke(fichier)
        else runCatching { fichier?.delete() }
    }
    val demanderCameraProfil = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { accorde ->
        val fichier = photoPrise
        if (accorde && fichier != null) prendrePhotoProfil.launch(uriDEnvoi(contexteApp, fichier))
        else { photoPrise = null; sourceEnCours = null }
    }
    fun photographierPour(source: SourceImage) {
        val fichier = java.io.File(
            contexteApp.cacheDir.resolve("envois").apply { mkdirs() },
            "photo-${System.currentTimeMillis()}.jpg",
        )
        photoPrise = fichier
        sourceEnCours = source
        sourceImage = null
        val accordee = androidx.core.content.ContextCompat.checkSelfPermission(
            contexteApp, android.Manifest.permission.CAMERA,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (accordee) prendrePhotoProfil.launch(uriDEnvoi(contexteApp, fichier))
        else demanderCameraProfil.launch(android.Manifest.permission.CAMERA)
    }

    LaunchedEffect(profil?.userId) {
        val moi = profil?.userId ?: return@LaunchedEffect
        // we declare ourselves before reading ourselves back
        //
        // matrix does not infer presence from the connection: a client that syncs without saying
        // anything stays whatever the server remembered, and the sdk publishes nothing on its own.
        // without this line you chose online, closed the app, and reopened it invisible, the server
        // simply telling the truth from its point of view. see `PrefsStatut` for what gets
        // remembered, and why it is on the device
        val (voulu, message) = PrefsStatut.choisi(contexteApp)

        // set before publishing, and the order is the fix
        //
        // `setPresence` makes two network calls. until they answered, the screen stayed on "unknown
        // status" and the picker sheet showed nothing ticked, at startup, which is the only moment
        // anybody looks at it. reading a local preference depends on no server, so nothing
        // justifies waiting
        //
        // your own status comes from your own choice, not from the server. we used to reread
        // `engine.presence(moi)`, reasoning that the server is authoritative. it is authoritative
        // for other people, and for yourself it does not return what you wrote to it. measured
        // against synapse: `PUT presence=unavailable` answers 200, keeps the `status_msg`, and
        // reads back `online` while a sync is running. with the app stopped, the same PUT holds. so
        // it is our own sync putting us back online, and nothing in the app can stop it on that
        // path
        //
        // what that looked like before the fix: the choice was correctly kept on the device, the
        // bottom bar showed the server's reread and the profile card showed the choice, two
        // different values for the same thing on the same screen. "changing your status does not
        // work at all", and that was exact
        monStatut = li.velvetchat.core.model.Statut(voulu, message, null)
        runCatching { engine.setPresence(voulu, message) }
        silence = runCatching { engine.silence() }.getOrDefault(false)
    }

    // the emoji pack follows the server and not the channel: that is the discord model, and it is
    // also where the state event lives. reloaded when the server changes rather than held as a
    // flow, since a pack changes a few times a year and not a few times a minute
    var pack by remember { mutableStateOf<List<li.velvetchat.core.model.CustomEmote>>(emptyList()) }
    var packs by remember {
        mutableStateOf<List<li.velvetchat.core.model.EmotePack>>(emptyList())
    }
    // the open server's pack, distinct from the global one
    //
    // the distinction is not cosmetic: the global pack is for writing, autocomplete and rendering,
    // and this one is for administering. showing the global one in the settings would suggest you
    // can remove another server's emoji from there, and the removal would fail, or worse, touch the
    // wrong server
    var packDuServeur by remember {
        mutableStateOf<List<li.velvetchat.core.model.CustomEmote>>(emptyList())
    }

    var paliers by remember {
        mutableStateOf<List<li.velvetchat.core.model.RoleTier>>(emptyList())
    }
    /** the open server's avatar decoration catalogue, read here and edited in the settings */
    /** every server's decoration catalogue, the one the profile picker uses */
    /**
     * the open server's members' extended profiles, for the tags beside their nicknames
     *
     * reread when the server changes rather than held as a flow: a profile moves a few times a
     * year, and the engine keeps them cached for a minute, see `profilsEtendus`
     */
    var profilsMembres by remember {
        mutableStateOf<Map<String, li.velvetchat.core.model.ProfilEtendu>>(emptyMap())
    }

    /** the open server's tag, for its settings */
    var tagDuServeurOuvert by remember(serverId) {
        mutableStateOf<li.velvetchat.core.model.TagServeur?>(null)
    }
    LaunchedEffect(serverId) {
        tagDuServeurOuvert = serverId?.let {
            runCatching { engine.tagServeur(it) }.getOrNull()
        }
    }

    var decorationsToutes by remember {
        mutableStateOf<List<li.velvetchat.core.model.DecorationAvatar>>(emptyList())
    }

    /** every server's effects and plates: the same catalogues, different families */
    var effetsTous by remember {
        mutableStateOf<List<li.velvetchat.core.model.DecorationAvatar>>(emptyList())
    }
    var plaquesToutes by remember {
        mutableStateOf<List<li.velvetchat.core.model.DecorationAvatar>>(emptyList())
    }

    /**
     * the frames, a fourth family, which can stay empty
     *
     * the reference's shop publishes none, hence the image import, which stays. but velvet's
     * catalogue is a room state like the others: publish some and the picker shows a grid,
     * otherwise the screen keeps its import panel alone
     */
    var cadresTous by remember {
        mutableStateOf<List<li.velvetchat.core.model.DecorationAvatar>>(emptyList())
    }

    /**
     * the tag badges, the pixel art a tag chip can carry
     *
     * can stay empty like the frames, and for the same reason: nothing is published until somebody
     * runs the script. the tag editor then offers its characters and the server's emoji, like
     * before
     */
    var badgesTags by remember {
        mutableStateOf<List<li.velvetchat.core.model.DecorationAvatar>>(emptyList())
    }

    var decorationsServeur by remember {
        mutableStateOf<List<li.velvetchat.core.model.DecorationAvatar>>(emptyList())
    }
    /** the soundboard's sound catalogue: same life as the decorations, same editing */
    var sonsServeur by remember {
        mutableStateOf<List<li.velvetchat.core.model.SonSoundboard>>(emptyList())
    }
    var membreVise by remember {
        mutableStateOf<li.velvetchat.core.model.Member?>(null)
    }

    // the card comes before moderation, and that is the point of this change
    //
    // tapping a member opened the moderation sheet. an admin shortcut sitting where everybody
    // expects a card: it offered to kick somebody you wanted to write to, and showed nothing about
    // the person. `membreVise` therefore opens the card, and moderation is an action inside it,
    // under `moderationSur`
    var moderationSur by remember {
        mutableStateOf<li.velvetchat.core.model.Member?>(null)
    }

    // what the card needs to know about the person and that does not live in `Member`: their
    // extended profile, their status, the note you keep on them. three reads made when the card
    // opens and not for the whole member list, since a card opens once and a list composes on every
    // keystroke
    var ficheEtendu by remember(membreVise) {
        mutableStateOf(li.velvetchat.core.model.ProfilEtendu())
    }
    var ficheStatut by remember(membreVise) {
        mutableStateOf(li.velvetchat.core.model.Statut.INCONNU)
    }
    var ficheNote by remember(membreVise) { mutableStateOf<String?>(null) }

    /** the nickname we put on this person, null when there is none */
    var ficheSurnom by remember(membreVise) { mutableStateOf<String?>(null) }

    // `false` and not `null` by default: the card hides the row while it is null, which would make
    // the action flicker on every open. read just afterwards, it corrects itself
    var ficheAmi by remember(membreVise) { mutableStateOf(false) }

    LaunchedEffect(membreVise) {
        val qui = membreVise?.userId ?: return@LaunchedEffect
        ficheEtendu = runCatching { engine.profilEtendu(qui) }
            .getOrDefault(li.velvetchat.core.model.ProfilEtendu())
        ficheStatut = runCatching { engine.presence(qui) }
            .getOrDefault(li.velvetchat.core.model.Statut.INCONNU)
        ficheNote = runCatching { engine.note(qui) }.getOrNull()
        ficheAmi = runCatching { engine.estAmi(qui) }.getOrDefault(false)
        ficheSurnom = runCatching { engine.surnoms()[qui] }.getOrNull()
    }

    // reloaded when the server list changes and not when the server changes: the pack is global, it
    // no longer depends on which one is open
    LaunchedEffect(servers.size) {
        // every server, not only the open one: otherwise the emoji would disappear in a direct
        // conversation, which belongs to no server, and you would have to switch server to place an
        // emoji from elsewhere
        //
        // we never clear a pack over a failed read. `emotePacks()` throws as soon as the server
        // list is not readable yet, which happens at startup, exactly when this effect fires. the
        // old `getOrDefault(emptyList())` then set an empty list, permanent until the number of
        // servers next changed: the custom emoji vanished for no visible reason. we retry, and only
        // replace with a non empty result
        repeat(6) { essai ->
            val lus = runCatching { engine.emotePacks() }.getOrNull()
            if (!lus.isNullOrEmpty()) {
                packs = lus
                // flattened here rather than through a second network round trip: `allEmotes()`
                // would redo the full tour of the servers for a result already in hand
                pack = lus.flatMap { it.emotes }.distinctBy { it.shortcode }
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(2_000L * (essai + 1))
        }
    }

    // the tiers stay tied to the open server: a role only means something in the server defining
    // it. so the two loads have different keys, and leaving them together would have frozen the
    // first displayed server's tiers
    LaunchedEffect(serverId, decorationsChangees) {
        paliers = serverId?.let { runCatching { engine.roleTiers(it) }.getOrDefault(emptyList()) }
            ?: emptyList()
        // loaded with the tiers and for the same reason: they are two tables of the open server,
        // which have to change with it rather than stay on the previous one's
        decorationsServeur = serverId?.let {
            runCatching { engine.inventaireDecorations(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        sonsServeur = serverId?.let {
            runCatching { engine.sonsSoundboard(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        // the call screen is mounted above the whole shell and cannot read a variable from here. so
        // the catalogue is pushed to it, like everything else that has to survive navigation
        li.velvetchat.app.VocalManager.setSons(sonsServeur)
        decorations = serverId?.let {
            runCatching { engine.decorations(it) }.getOrDefault(Decorations())
        } ?: Decorations()
        packDuServeur = serverId?.let {
            runCatching { engine.emotes(it) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    // the profile is reread every time it opens rather than kept: you have just changed something
    // in it, and showing the old name right after editing it would be the worst moment for a stale
    // value. it is read at startup too and not only when its sheet opens, since the bottom bar
    // shows it permanently
    LaunchedEffect(Unit) { runCatching { engine.profile() }.onSuccess { profil = it } }

    // the extended profile: pronouns, banner, chosen gradient. read separately from the base
    // profile because it takes another path, the hand signed client api, the sdk not knowing those
    // fields
    var etendu by remember {
        mutableStateOf(li.velvetchat.core.model.ProfilEtendu())
    }
    LaunchedEffect(profil?.userId, feuilleProfil) {
        val moi = profil?.userId ?: return@LaunchedEffect
        etendu = runCatching { engine.profilEtendu(moi) }
            .getOrDefault(li.velvetchat.core.model.ProfilEtendu())
    }

    // the decoration you imported yourself. uploaded in the clear, like the avatar: it is published
    // in the extended profile, which everybody you share a room with reads
    val choisirDecoration = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val fichier = copierPourEnvoi(contexteApp, uri) ?: error(tr("Image illisible", "Unreadable image"))
                val mxc = engine.uploadMedia(fichier.absolutePath)
                engine.setProfilEtendu(
                    li.velvetchat.core.model.ProfilEtendu.CLE_DECORATION,
                    mxc,
                )
                // reread from the server, like everywhere else on this screen: it is authoritative,
                // and a refused write must not leave a choice that did not take
                etendu = engine.profilEtendu(engine.currentSession().userId)
            }.onFailure {
                android.util.Log.w("Velvet", "Décoration non importée", it)
            }
        }
    }

    var feuilleApparence by remember { mutableStateOf(false) }

    /** creating a category, from the server menu */
    var feuilleCategorie by remember { mutableStateOf(false) }

    /** the notification mode applied to every channel of the server, from its menu */
    var feuilleNotifsServeur by remember { mutableStateOf(false) }

    /**
     * how many members of the open server are online
     *
     * null while we do not know: the reference shows "N online · N members", and writing "0 online"
     * before asking would be a false statement. exactly the fault where the count was right and
     * everybody thought it was broken
     */
    var enLigneServeur by remember(serverId) { mutableStateOf<Int?>(null) }
    LaunchedEffect(membresServeur) {
        if (membresServeur.isEmpty()) return@LaunchedEffect
        enLigneServeur = runCatching {
            engine.presences(membresServeur.map { it.userId })
                .count { (_, statut) -> statut.presence == Presence.EN_LIGNE }
        }.getOrNull()
    }

    /** the edit profile screen, modelled on the reference's */
    var ecranProfilEdition by remember { mutableStateOf(false) }
    /** the settings, opened from the profile's floating bar. see `EcranParametres` */
    var ecranParametres by remember { mutableStateOf(false) }
    var ecranImport by remember { mutableStateOf(false) }

    /** the tags the joined servers offer, for the choice in your profile */
    var tagsDisponibles by remember {
        mutableStateOf<List<Triple<String, String, li.velvetchat.core.model.TagServeur>>>(
            emptyList()
        )
    }
    LaunchedEffect(servers.size, ecranProfilEdition) {
        if (!ecranProfilEdition) return@LaunchedEffect
        tagsDisponibles = runCatching { engine.tagsDisponibles() }.getOrDefault(emptyList())
    }

    /**
     * one image picker for the three ornaments, with the destination key beside it
     *
     * three launchers would mean three registrations for an identical gesture: pick an image,
     * upload it, write its `mxc` into a profile field. only the field changes, so it, and it alone,
     * is held as state
     */
    var ornementVise by remember { mutableStateOf<String?>(null) }
    val choisirOrnement = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val cle = ornementVise
        ornementVise = null
        if (uri != null && cle != null) scope.launch {
            runCatching {
                val fichier = copierPourEnvoi(contexteApp, uri) ?: error(tr("Image illisible", "Unreadable image"))
                engine.setProfilEtendu(cle, engine.uploadMedia(fichier.absolutePath))
                etendu = engine.profilEtendu(engine.currentSession().userId)
            }.onFailure { android.util.Log.w("Velvet", "Ornement non posé", it) }
        }
    }

    /** writes one extended profile field, then rereads. the server is authoritative */
    fun ecrireProfil(cle: String, valeur: String?) {
        scope.launch {
            runCatching {
                engine.setProfilEtendu(cle, valeur)
                etendu = engine.profilEtendu(engine.currentSession().userId)
            }.onFailure { android.util.Log.w("Velvet", "Profil non écrit ($cle)", it) }
        }
    }

    /** the channel that got long pressed, and whose sheet is open */
    var salonVise by remember { mutableStateOf<Channel?>(null) }

    /** the direct conversation that got long pressed */
    var conversationVisee by remember {
        mutableStateOf<li.velvetchat.core.model.DirectMessage?>(null)
    }

    /** starting a call from the menu goes through the same path as the header button */
    val lancerAppel = rememberLanceurAppel(engine)

    /**
     * the conversation menu's profile, note and nickname entries, which all open the card
     *
     * the member is asked of the room rather than built from the [DirectMessage]: the card wants
     * a role, an avatar and the real name behind a nickname, and a `DirectMessage` carries a name
     * chosen for a list row. a fallback is kept for the case where the member list cannot be
     * read, since an incomplete card beats a tap that does nothing
     */
    val ouvrirFicheDuDirect: (li.velvetchat.core.model.DirectMessage) -> Unit = { conv ->
        conversationVisee = null
        scope.launch {
            membreVise = runCatching { engine.members(conv.roomId) }.getOrNull()
                ?.firstOrNull { it.userId == conv.userId }
                ?: li.velvetchat.core.model.Member(
                    userId = conv.userId,
                    displayName = conv.name,
                    avatarUrl = conv.avatarUrl,
                    role = null,
                    nomReel = conv.nomReel,
                )
        }
    }

    /** the same channel, once you have gone down into the notification choice */
    var notificationsSur by remember { mutableStateOf<Channel?>(null) }

    /** the target channel's current mode. null while it has not been read */
    var modeDuSalon by remember(salonVise, notificationsSur) {
        mutableStateOf<li.velvetchat.core.model.ModeNotification?>(null)
    }
    LaunchedEffect(salonVise, notificationsSur, conversationVisee) {
        // an id is enough: the notification mode depends on nothing else. the three surfaces
        // showing it, channel, notifications, direct conversation, are mutually exclusive, so only
        // one is open at a time
        val salon = salonVise?.id ?: notificationsSur?.id ?: conversationVisee?.roomId
            ?: return@LaunchedEffect
        modeDuSalon = try {
            engine.modeNotification(salon)
        } catch (annulation: kotlinx.coroutines.CancellationException) {
            throw annulation
        } catch (t: Throwable) {
            android.util.Log.w("Velvet", "Mode de notification illisible", t)
            null
        }
    }

    // reread when a channel's mode changes, otherwise muting one would only dim at the next
    // startup. placed after `modeDuSalon`, which it depends on, since kotlin reads top to bottom
    LaunchedEffect(modeDuSalon) {
        salonsMuets = runCatching { engine.salonsMuets() }.getOrDefault(emptySet())
    }

    // the banner picker, registered at shell level like the avatar's: an activity launcher has to
    // exist before the composition using it. the gesture is shared by both paths, gallery and
    // camera: upload, then write the address into the extended profile
    suspend fun poserBanniere(fichier: java.io.File) {
        runCatching {
            val mxc = engine.uploadMedia(fichier.absolutePath)
            engine.setProfilEtendu(li.velvetchat.core.model.ProfilEtendu.CLE_BANNIERE, mxc)
            profil?.userId?.let { moi -> etendu = engine.profilEtendu(moi) }
        }
    }
    val choisirBanniere = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val fichier = copierPourEnvoi(contexteApp, uri) ?: error(tr("Image illisible", "Unreadable image"))
                // the banner is uploaded as an ordinary media, then its `mxc://` is written into
                // the extended profile. two steps, because the extended profiles msc carries text
                // values and not files
                val mxc = engine.uploadMedia(fichier.absolutePath)
                engine.setProfilEtendu(
                    li.velvetchat.core.model.ProfilEtendu.CLE_BANNIERE,
                    mxc,
                )
                profil?.userId?.let { moi -> etendu = engine.profilEtendu(moi) }
            }
        }
    }

    // the cache sizes, reread when the profile screen opens. on disk, so off the main thread, since
    // walking a folder has no business inside a composition
    var poidsEnvois by remember { mutableStateOf(0L) }
    var poidsMoteur by remember { mutableStateOf(0L) }
    LaunchedEffect(feuilleProfil) {
        if (!feuilleProfil) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            poidsEnvois = runCatching { CacheMedia.taille(contexteApp) }.getOrDefault(0L)
            poidsMoteur = runCatching { CacheMedia.tailleMoteur(contexteApp) }.getOrDefault(0L)
        }
    }

    // the cache purge happens at startup, once, in the background
    //
    // not on every send: a purge costs a folder walk, and triggering it at the exact moment
    // somebody just wrote a file would charge that walk while they wait to see their photo leave.
    // once per launch is plenty to hold a budget only uploads grow
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { CacheMedia.purger(contexteApp) } }
    }
    LaunchedEffect(feuilleProfil) {
        if (feuilleProfil) runCatching { engine.profile() }.onSuccess { profil = it }
    }

    // the member count feeds the header's subtitle. one read per open server and not one per
    // channel: the list is the same for a whole space
    LaunchedEffect(serverId) {
        val premier = servers.firstOrNull { it.id == serverId }?.channels?.firstOrNull()?.id
        if (premier != null) {
            runCatching { engine.members(premier) }.onSuccess { membresServeur = it }
        }
    }

    // the same people's extended profiles, for their tags. separate from reading the members rather
    // than merged: that one is local and instant, this one goes over the network, and tying them
    // would make the member list wait on an ornament
    //
    // the direct conversations are added in: the tag shows in a one to one too, where the person
    // opposite is a member of no server you are looking at
    LaunchedEffect(membresServeur, directs) {
        val qui = (membresServeur.map { it.userId } + directs.map { it.userId }).distinct()
        if (qui.isEmpty()) return@LaunchedEffect
        runCatching { engine.profilsEtendus(qui) }
            .onSuccess { profilsMembres = profilsMembres + it }
    }

    // one id to colour map, derived from the members and the tiers. the timeline only has sender
    // ids, and without it colouring a nickname would mean asking the server on every row. the rule
    // lives in `Roles.kt` now that the desktop needs it too: the decorative role beats the tier,
    // and writing it twice would give two colour hierarchies for one server, visible side by side
    val couleursDeRole = remember(membresServeur, paliers, decorations) {
        couleursDeRole(membresServeur, paliers, decorations)
    }

    // the full tier, for the badges. see `LocalRoleTiers`: kept apart from the colour map because
    // that one is read on every timeline row and has to stay dumb
    //
    // the badges arrive late, and it shows. measured: opening a room straight from the list, the
    // nicknames draw first with no tier, and the badge appears once the members and the tiers come
    // back. both load on `LaunchedEffect(serverId)`, so in parallel with the timeline's first
    // render
    //
    // that cost a false alarm: the badge was missing on two screenshots in a row, which looked like
    // a regression from the icon rework. an older version reinstalled from the nextcloud behaved
    // the same, and walking through more slowly brought it back. it is a load order, not a breakage
    //
    // ponytail: left as is, a badge arriving a second after the nickname is not a fault anybody
    // notices. to revisit if somebody reports it, by persisting the open server's tiers rather than
    // rereading them on every open
    val paliersParMembre = remember(membresServeur, paliers) {
        paliersParMembre(membresServeur, paliers)
    }

    val contexteAppli = androidx.compose.ui.platform.LocalContext.current
    val reglagesEmojis = remember(contexteAppli) { ReglagesEmojisAndroid(contexteAppli) }

    // updates

    /** the published version, when it is newer and installable here and not refused */
    var releaseDispo by remember { mutableStateOf<li.velvetchat.core.model.Release?>(null) }
    var etatMaj by remember { mutableStateOf<EtatMiseAJour>(EtatMiseAJour.Repos) }

    // every server, not the one you are looking at
    //
    // the search was tied to the open server, so it only found the announcement if you happened to
    // be standing in the server it was published in. on a phone that lives in direct conversations
    // or in another server, no update ever arrived, and the cause was neither the download nor the
    // install but looking in the wrong place. the desktop already asked every server, and the phone
    // finally does
    LaunchedEffect(servers.map { it.id }) {
        // the installation's hidden room first: that is where announcements live now
        val ids = listOfNotNull(runCatching { engine.salonInstallation() }.getOrNull()) + servers.map { it.id }
        if (ids.isEmpty()) return@LaunchedEffect
        val publiee = ids.mapNotNull { id ->
            try {
                engine.releasePubliee(id)
            } catch (annulation: kotlinx.coroutines.CancellationException) {
                throw annulation
            } catch (t: Throwable) {
                // quiet on screen, but not in the logs. a version check that fails calls for no
                // action from anybody, so interrupting them to say so would be noise. silencing it
                // completely would be the silent failure again, nobody would ever know the channel
                // is broken
                android.util.Log.i("Velvet", "Version publiée illisible", t)
                null
            }
        }.maxByOrNull { it.versionCode } ?: return@LaunchedEffect

        val abis = android.os.Build.SUPPORTED_ABIS.toList()
        releaseDispo = publiee.takeIf {
            it.plusRecenteQue(li.velvetchat.app.BuildConfig.VERSION_CODE.toLong()) &&
                it.versionCode > PrefsMaj.refusee(contexteApp) &&
                // a version with no variant for this machine does not get offered: announcing an
                // update that cannot be installed is worse than saying nothing
                it.pourAbis(abis) != null
        }
    }

    // the automatic away status

    /**
     * away when the app goes to the background, online when it comes back
     *
     * discord goes idle after a while of inactivity, matrix has `unavailable`, and it is a local
     * timer with no negotiation with the server. we take the clearest signal there is, going to the
     * background, rather than an inactivity timer that would have to watch gestures
     *
     * a chosen status is never overwritten. somebody who set themselves invisible stays invisible
     * when they close the app, and without that test the automation would make them visible and
     * away, which is the exact opposite of what they asked for. same for an away set by hand: it
     * has no business becoming online because a screen reopened
     */
    val cycleDeVie = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(cycleDeVie) {
        val observateur = androidx.lifecycle.LifecycleEventObserver { _, evenement ->
            val (voulu, message) = PrefsStatut.choisi(contexteApp)
            if (voulu != li.velvetchat.core.model.Presence.EN_LIGNE) {
                return@LifecycleEventObserver
            }
            val vers = when (evenement) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP ->
                    li.velvetchat.core.model.Presence.ABSENT
                androidx.lifecycle.Lifecycle.Event.ON_START ->
                    li.velvetchat.core.model.Presence.EN_LIGNE
                else -> return@LifecycleEventObserver
            }
            scope.launch { runCatching { engine.setPresence(vers, message) } }
        }
        cycleDeVie.lifecycle.addObserver(observateur)
        onDispose { cycleDeVie.lifecycle.removeObserver(observateur) }
    }

    CompositionLocalProvider(
        LocalEngine provides engine,
        LocalEmotes provides pack,
        LocalPacks provides packs,
        // the server's members, for the whole app: the reactions' stacked avatars read them inside
        // the conversation. found in testing on the s21, they only had them in the composer's block
        // and fell back on initials. the composer keeps its own room list
        LocalMembres provides membresServeur,
        LocalSalons provides (servers.firstOrNull { it.id == serverId }?.channels ?: emptyList()),
        LocalMonIdentifiant provides profil?.userId,
        LocalRoleColors provides couleursDeRole,
        // without this line the picker would forget everything. it lives in `ui-commun` and reads
        // its contract through this local, and the contract's default is a memory that does not
        // survive closing. the favourites and the skin tone would be lost on every launch, with not
        // one error to say so
        LocalReglagesEmojis provides reglagesEmojis,
        LocalProfils provides profilsMembres,
        LocalRoleTiers provides paliersParMembre,
        LocalGifsEnLigne provides gifsEnLigne,
        LocalOuvrirMedia provides { ref -> mediaOuvert = ref },
        // a mention opens the person's card. `Shell` is the only place holding that state and the
        // only one high enough to draw the card over the conversation
        //
        // a `Member` reduced to its id is enough: `LaunchedEffect(membreVise)` fetches the extended
        // profile, the status, the note, the nickname and the tier as soon as it opens. filling it
        // in here with what we think we know would only add a chance to diverge
        LocalOuvrirMembre provides { id ->
            membreVise = Member(userId = id, displayName = null, avatarUrl = null, role = null)
        },
    ) {
    // one frame loop for every animation on screen: one per emoji would wake the main thread as
    // many times as there are visible ones
    PiloteAnimations()

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(tokens.bg)) {
        ConnectionBar(connection)

        BandeauMiseAJour(
            release = releaseDispo,
            etat = etatMaj,
            onInstaller = onInstaller@{
                val version = releaseDispo ?: return@onInstaller
                val variante = version.pourAbis(android.os.Build.SUPPORTED_ABIS.toList())
                    ?: return@onInstaller
                // ask for the permission before, not after. without it `commit` does not fail
                // visibly, the system's confirmation simply never appears, and it looks like the
                // app does nothing
                if (!MiseAJour.autorisee(contexteApp)) {
                    contexteApp.startActivity(
                        MiseAJour.intentionAutorisation(contexteApp)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    return@onInstaller
                }
                scope.launch {
                    etatMaj = EtatMiseAJour.Telechargement(0f)
                    val session = runCatching { engine.currentSession() }.getOrNull()
                    val erreur = MiseAJour.installer(
                        contexteApp,
                        variante,
                        homeserverUrl = session?.homeserverUrl.orEmpty(),
                        jeton = session?.accessToken.orEmpty(),
                        onRemise = { etatMaj = EtatMiseAJour.Installation },
                    ) { progres ->
                        etatMaj = EtatMiseAJour.Telechargement(progres)
                    }
                    etatMaj = if (erreur == null) EtatMiseAJour.Repos
                    else EtatMiseAJour.Echec(erreur)
                }
            },
            onPlusTard = {
                releaseDispo?.let { PrefsMaj.refuser(contexteApp, it.versionCode) }
                releaseDispo = null
            },
        )

        // a drawer that slides over, and not a screen swap
        //
        // the previous version exchanged the two levels through an `AnimatedContent`: the gesture
        // triggered a transition, then watched it play out. that is not what discord does, and the
        // difference is immediate. there the conversation follows your finger, you see the list
        // appear underneath as you go, and you can stop halfway or come back. a triggered
        // transition is a state flip, a drawer is an object you move
        //
        // so both levels are rendered at the same time, one over the other, and only the top one's
        // horizontal offset changes
        val densite = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val largeurTiroir = maxWidth - tokens.density.conversationPeekWidth.dp
            val course = with(densite) { largeurTiroir.toPx() }
            val ouvert = remember { Animatable(course) }

            // with no room to show, the drawer stays open: there is nothing to tuck behind, and
            // closing it would show an empty screen
            LaunchedEffect(channel?.id) {
                if (channel == null) ouvert.animateTo(course, tokens.glissementPage())
            }
            // the travel depends on the window's width, which changes on rotation or in split
            // screen. without this reframing the drawer would stay open by a distance that no
            // longer corresponds to anything
            LaunchedEffect(course) {
                if (ouvert.value > 0f) ouvert.snapTo(course)
                ouvert.updateBounds(0f, course)
            }

            val scope = rememberCoroutineScope()
            fun basculer(vers: Float) =
                scope.launch { ouvert.animateTo(vers, tokens.glissementPage()) }

            /**
             * opening a server channel closes the direct conversation. it is one function because
             * forgetting it once was enough to break navigation
             *
             * the fault as it looked: from a direct conversation you pick a server then a channel,
             * and the screen stays on the direct conversation. you can type a message there, which
             * goes to that person while you believe you are writing in the channel. the cause is
             * two lines below, `salonAffiche` puts `directOuvert` before the chosen channel. until
             * it is reset, the selected channel is computed, highlighted in the list, and never
             * shown
             *
             * three paths lead here, the rail, the list and the drag from the list, and each had to
             * remember that reset. one did
             */
            fun ouvrirSalon(id: String?) {
                // a channel still being created has only a placeholder id, see the desktop
                if (id?.startsWith("brouillon:") == true) return
                // a tap on a voice room previews it over the list, it does not join: the reference
                // opens a sheet with who is inside and a join button, so nobody lands in a call by
                // brushing the list. the drawer stays open under it. already in that very room,
                // there is nothing to preview
                val vocalTouche = id != null && server?.channels
                    ?.firstOrNull { it.id == id }?.kind == li.velvetchat.core.model.ChannelKind.VOICE
                if (vocalTouche) {
                    val appel = li.velvetchat.app.VocalManager.etat.value
                    val dejaDedans = appel.phase != li.velvetchat.app.VocalManager.Phase.INACTIF &&
                        appel.salonId == id
                    if (!dejaDedans) {
                        apercuVocal = id
                        return
                    }
                }
                directOuvert = null
                if (id != null) channelId = id
                basculer(0f)
            }

            // the gesture's two halves, named here because three surfaces use them: the
            // conversation, the drawer itself, and the strip that sticks out when it is open. they
            // only lived on the conversation, and that is what broke closing, see `glisserTiroir`
            val glisser: (Float) -> Unit = { delta ->
                scope.launch { ouvert.snapTo((ouvert.value + delta).coerceIn(0f, course)) }
            }
            val relacher: (Float) -> Unit = { vitessePxParSeconde ->
                // three cases, in this order: a clean fling decides on its own wherever the finger
                // stopped, and otherwise the distance decides
                val lancer = with(densite) { tokens.density.flingVelocityDpS.dp.toPx() }
                basculer(
                    when {
                        vitessePxParSeconde > lancer -> course
                        vitessePxParSeconde < -lancer -> 0f
                        ouvert.value > course * tokens.density.drawerOpenFraction -> course
                        else -> 0f
                    }
                )
            }

            // the back gesture closes the drawer when it is open and opens it otherwise. it stays
            // the guaranteed path to the list, for anybody who would not guess the drag
            //
            // active even with no room shown, and that is a fix: the condition was `channel !=
            // null`, so from the home screen the back was not intercepted at all and it left the
            // app. that screen became a dead end, neither a gesture nor a button nor the back key
            // got out of it. a help screen you cannot leave is no longer help
            //
            // the drawer stays closed in the foreground when there is nothing behind it, and in
            // that case the back opens it, which is the only gesture that makes sense here
            androidx.activity.compose.BackHandler {
                basculer(if (ouvert.value > course / 2f) 0f else course)
            }

            // level 1, underneath: rail plus channels, exactly as wide as the travel
            //
            // one column, with the user bar at the bottom of the whole drawer
            //
            // it used to live inside the channel panel, so to the right of the rail. a direct
            // complaint: on discord the avatar sits on the server rail. checked against the
            // screenshot, and it is true, the avatar is at x = 28 px on a 1008 wide image, which is
            // inside the server column, while the card carrying the nickname starts after it. that
            // overlap is what makes it look laid onto the interface rather than filed in a box
            //
            // from inside the panel it was structurally impossible: a child does not overhang the
            // row containing it. hence the move, the bar is now a sibling of the rail plus panel
            // row, not a child of the panel
            Column(Modifier.width(largeurTiroir).fillMaxHeight().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().weight(1f)
                    // the drawer closes with your finger, from the drawer. it did not: the gesture
                    // only lived on the conversation, which is pushed off screen precisely when the
                    // drawer is open. you opened it with a drag and could only close it by tapping
                    // the hundred and twenty pixels that stick out, checked on a device and
                    // reported as the drawer not working. it half worked, which is worse, since the
                    // natural gesture did nothing
                    //
                    // the lists it contains scroll vertically, and `detectHorizontal…` takes
                    // nothing from them
                    .glisserTiroir(Unit, glisser, relacher)
            ) {
                ServerRail(
                    servers = servers,
                    selected = if (vueMessages) null else serverId,
                    messagesActifs = vueMessages,
                    // the badge on the messages tile: that is where invites show, and without it
                    // they would stay invisible to anybody who never leaves their server
                    invitations = invitations.size,
                    onMessages = {
                        vueMessages = true
                        // the last one opened rather than nothing. it may have gone since, a
                        // conversation left, a list reread, and we then fall back on nothing, which
                        // is the old behaviour and is still correct
                        if (directOuvert == null) {
                            directOuvert = directs.firstOrNull { it.roomId == dernierDirect }
                        }
                    },
                    onAjouter = { feuilleServeur = true },
                    onProfil = { feuilleProfil = true },
                    onReordonner = { ids ->
                        scope.launch { runCatching { engine.setOrdreServeurs(ids) } }
                    },
                    directsNonLus = directs.filter { it.unread.notifications > 0 && !(vueMessages && directOuvert?.roomId == it.roomId) },
                    onDirect = { d -> vueMessages = true; directOuvert = d },
                ) {
                    vueMessages = false
                    serverId = it
                    // the `channelId` reset has been removed. it existed to open the list without
                    // jumping into a room, but it is `basculer(course)` two lines below that opens
                    // the list. all it added was emptiness behind it: the home screen shown
                    // wrongly, with no way out
                    //
                    // changing server invalidates the old room anyway, and the recovery effect at
                    // the top of the shell replaces it with the new server's first one. the direct
                    // conversation is closed here too: choosing a server means you have already
                    // left the direct messages, even if you have not named the channel yet. without
                    // this line the drawer opens on the server's channel list while the direct
                    // conversation stays displayed behind it
                    directOuvert = null
                    basculer(course)
                }
                // "N in voice", per channel of the open server
                //
                // declared before the branch: a `remember` placed inside only one of the two cases
                // would be forgotten on every switch between the messages and a server
                //
                // the rate is not here, it belongs to the engine, like presence. the screen only
                // says which rooms it cares about
                val idsSalonsVocal = remember(server) { server?.channels?.map { it.id }.orEmpty() }
                var enVocal by remember(server?.id) { mutableStateOf<Map<String, Int>>(emptyMap()) }
                LaunchedEffect(idsSalonsVocal) {
                    if (idsSalonsVocal.isEmpty()) {
                        enVocal = emptyMap()
                        return@LaunchedEffect
                    }
                    engine.vocauxFlow(idsSalonsVocal).collect { parSalon ->
                        enVocal = parSalon.mapValues { (_, qui) -> qui.size }
                    }
                }
                if (vueMessages) {
                    EcranMessages(
                        conversations = directs.map { d ->
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
                                // it was missing, and the group picker is what made it show: the
                                // sheet reads `groupe` off the engine model and never needed it
                                // here, so a group came through this projection looking like a one
                                // to one. the picker has to leave groups out, a group being nobody
                                // to invite
                                groupe = d.groupe,
                                presence = presences[d.userId] ?: Presence.INCONNU,
                            )
                        },
                        profil = profil,
                        // the three the channel panel's bar already received: it is the same bar
                        // now, so the same entries. the banner only showed inside a server because
                        // the messages screen had a copy of the bar that did not know about it
                        plaque = etendu.plaque,
                        decoration = etendu.decoration,
                        statut = monStatut,
                        onStatut = { feuilleStatut = true },
                        invitations = invitations,
                        onInvitation = { invitationVisee = it },
                        amis = mesAmis,
                        // a group is always created, never looked up: `m.direct` files rooms by
                        // person and not by set, so there is nothing to find
                        onNouveauGroupe = { ids ->
                            scope.launch {
                                runCatching { engine.creerGroupeDirect(ids) }
                                    .onSuccess { salle ->
                                        runCatching { engine.directs() }.onSuccess { directs = it }
                                        directOuvert = directs.firstOrNull { it.roomId == salle }
                                        basculer(0f)
                                    }
                                    .onFailure {
                                        messageFugace = it.message ?: t.creationGroupeImpossible
                                    }
                            }
                        },
                        // while the first load is not done, the screen shows a skeleton instead of
                        // "no direct conversations"
                        charge = dmCharges,
                        modifier = Modifier.weight(1f),
                        onOuvrir = { conv ->
                            directOuvert = directs.firstOrNull { it.roomId == conv.roomId }
                            basculer(0f)
                        },
                        onEpingler = { conv ->
                            // the long press now opens a sheet instead of pinning silently, see
                            // `FeuilleConversation`
                            conversationVisee = directs.firstOrNull { it.roomId == conv.roomId }
                        },
                        onNouvelle = { nouvelleConversation = true },
                        onProfil = { feuilleProfil = true },
                    )
                } else ChannelList(
                    server = server,
                    muets = salonsMuets,
                    plaque = etendu.plaque,
                    decoration = etendu.decoration,
                    statut = monStatut,
                    onStatut = { feuilleStatut = true },
                    selected = channelId,
                    nombreMembres = membresServeur.size.takeIf { it > 0 },
                    enVocal = enVocal,
                    profil = profil,
                    onProfil = { feuilleProfil = true },
                    modifier = Modifier.weight(1f),
                    onEntrer = { if (channelId != null) ouvrirSalon(null) },
                    onInviter = { feuilleReglages = true },
                    onSalonLong = { salonVise = it },
                    onMarquerLuSalon = { canal ->
                        scope.launch { marquerLu(contexteApp, engine, canal.id) }
                    },
                    onBasculerMuetSalon = { canal, etaitMuet ->
                        scope.launch {
                            runCatching {
                                engine.setModeNotification(
                                    canal.id,
                                    if (etaitMuet) li.velvetchat.core.model.ModeNotification.DEFAUT
                                    else li.velvetchat.core.model.ModeNotification.MUET,
                                )
                            }
                            // reread: the muted room list is what decides the dimming, and it comes
                            // from the server
                            salonsMuets = runCatching { engine.salonsMuets() }
                                .getOrDefault(salonsMuets)
                        }
                    },
                    onSelect = { ouvrirSalon(it) },
                )
            }

            // the user bar, at the bottom of the whole drawer
            //
            // rail included: that is what lets its avatar sit on the server column, like the
            // reference. from inside the panel it was structurally impossible, a child does not
            // overhang the row containing it
            //
            // one instance for both panels too: there used to be two, one per screen, and that is
            // what made a banner show on one side only
            BarreUtilisateur(
                profil = profil,
                statut = monStatut,
                plaque = etendu.plaque,
                decoration = etendu.decoration,
                silence = silence,
                onClick = { feuilleProfil = true },
                onSilence = { actif ->
                    silence = actif
                    scope.launch { runCatching { engine.setSilence(actif) } }
                },
            )
            }

            // level 2, on top: the conversation, offset by the drawer's position
            //
            // `offset { }` and not `offset()`: the lambda form defers reading `ouvert.value` until
            // layout, so the slide recomposes nothing. the direct form would have recomposed the
            // whole conversation on every frame of the gesture, exactly what we just spent time
            // avoiding on scrolling
            //
            // the direct conversation comes before the channel, and that priority is what makes
            // `ouvrirSalon` mandatory: while `directOuvert` holds something, the channel named
            // above has no chance of appearing. see the function for the fault that produced
            val salonAffiche = directOuvert?.let { direct ->
                // a dm is presented to the conversation as an ordinary room: the same timeline, the
                // same composer, the same gestures. only the header differs, and it reads the name
                // given here
                Channel(
                    id = direct.roomId,
                    name = direct.name,
                    topic = null,
                    kind = li.velvetchat.core.model.ChannelKind.TEXT,
                    unread = direct.unread,
                )
            } ?: channel

            // `derivedStateOf` brings sixty reads a second down to two flips: this boolean only
            // changes at the ends of the travel
            val tiroirOuvert by remember(course) {
                derivedStateOf { ouvert.value > course * 0.98f }
            }

            // the keyboard leaves as soon as the drawer moves
            //
            // it used to stay open: you dragged to go and find another conversation and half the
            // screen was still eaten by the keyboard of the message you had just left. the
            // threshold is deliberately low, at 98% of the travel the keyboard would only have gone
            // once the drawer was wide open, so too late to see the list while dragging
            val tiroirBouge by remember(course) { derivedStateOf { ouvert.value > 2f } }
            val gestionFocus = androidx.compose.ui.platform.LocalFocusManager.current
            val clavier = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            LaunchedEffect(tiroirBouge) {
                if (tiroirBouge) {
                    gestionFocus.clearFocus(force = true)
                    clavier?.hide()
                }
            }

            // nothing to show? the drawer opens by itself
            //
            // this is the deep fix for this screen, and it came from a direct complaint that the
            // screen was awful. it was. a full screen wall came down over the channel list, which
            // is to say over the only useful thing in that situation, and you had to guess an edge
            // drag to push it aside. worse, it did not live in the sliding container: "see the
            // channels" opened the drawer behind it, so it did nothing visible
            //
            // the reference shows no wall: when nothing is selected, you see the list. that is what
            // we do now, the welcome text stays but behind, as context you find on closing the
            // list, not as an obstacle
            LaunchedEffect(salonAffiche == null) {
                if (salonAffiche == null) basculer(course)
            }

            // the room you are looking at does not notify. the watch service reads this value, and
            // without it reading a conversation would make the phone ring on every message
            // received, including the one in front of you. set back to null on leaving the screen,
            // otherwise the last room opened would stay muted forever
            androidx.compose.runtime.DisposableEffect(salonAffiche?.id, tiroirOuvert) {
                li.velvetchat.app.Veille.salonOuvert =
                    salonAffiche?.id?.takeIf { !tiroirOuvert }
                onDispose { li.velvetchat.app.Veille.salonOuvert = null }
            }

            Box(Modifier.offset { IntOffset(ouvert.value.roundToInt(), 0) }.fillMaxSize()) {
                if (salonAffiche != null) {
                    // role tiers only exist inside a server: a tier dresses a power in a place, and
                    // a two person conversation belongs to none. without this clearing, the
                    // person's nickname showed flanked by a moderator badge inherited from the
                    // server you came from. seen during the beta test. this is its place, wrapping
                    // the call rather than inside the function it wraps
                    CompositionLocalProvider(
                        LocalRoleTiers provides
                            if (directOuvert != null) emptyMap() else LocalRoleTiers.current,
                        LocalRoleColors provides
                            if (directOuvert != null) emptyMap() else LocalRoleColors.current,
                    ) {
                    Conversation(
                        engine = engine,
                        channel = salonAffiche,
                        estPrive = directOuvert != null,
                        interlocuteur = directOuvert,
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                        // the drawer does not open all at once, it follows your finger. letting go
                        // picks the nearest edge, with no stable intermediate position, since a
                        // half open drawer is a useful state for nobody
                        onGlisser = glisser,
                        onRelacher = relacher,
                        onOuvrirTiroir = { basculer(course) },
                        onReglagesGif = { feuilleGif = true },
                        mentionADeposer = mentionADeposer,
                        onMentionDeposee = { mentionADeposer = null },
                        onMembre = { membreVise = it },
                        onLectureChangee = {
                            scope.launch {
                                runCatching { engine.directs() }.onSuccess { directs = it }
                            }
                        },
                    )
                    }

                } else {
                    // this panel was empty, literally, nothing was drawn here
                    //
                    // found while thinking about a new member registering by token: a freshly
                    // created account has neither room nor dm, `salonAffiche` stays null, and
                    // without this branch the screen behind the rail was a void with no explanation
                    // and no possible gesture
                    //
                    // it now lives inside the sliding container, in the exact place a conversation
                    // would occupy. that is what makes the drawer pass in front of it instead of
                    // opening underneath, and therefore what makes "see the channels" finally
                    // produce something visible
                    EcranBienvenue(
                        vide = servers.isEmpty() && directs.isEmpty(),
                        serveurSansSalon = server != null && server.channels.isEmpty(),
                        onCreerSalon = { feuilleSalon = true }.takeIf { server != null },
                        onEcrire = { nouvelleConversation = true },
                        onVoirSalons = { basculer(course) },
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    )
                }

                // with the drawer open, the strip that sticks out only serves to close it: letting
                // taps reach the messages there would act on something you only see a third of.
                // this detector only exists in that state
                if (tiroirOuvert) {
                    // the drag before the tap: `clickable` holds the press while it decides whether
                    // it is a tap, and that wait was enough to deprive the conversation underneath
                    // of the horizontal gesture. the two now share the same surface, a tap closes
                    // and a drag follows your finger
                    Box(
                        Modifier.matchParentSize()
                            .glisserTiroir(Unit, glisser, relacher)
                            .clickable { basculer(0f) }
                    )
                }
            }
        }
    }

    // over everything, drawer included: it opens from the channel list and must not be clipped by it
    apercuVocal?.let { id ->
        ApercuVocal(
            salonId = id,
            nom = server?.channels?.firstOrNull { it.id == id }?.name ?: "",
            onRejoindre = {
                apercuVocal = null
                rejoindreVocal(id)
            },
            onFermer = { apercuVocal = null },
        )
    }

    if (feuilleServeur) {
        FeuilleServeur(
            onCreer = { nom ->
                // the shell's scope, not the sheet's: the sheet closes in the same frame, and a
                // coroutine launched from it would be cancelled with it. the tile shows at once,
                // the engine draws it before synapse answers
                scope.launch {
                    runCatching { engine.createServer(nom) }
                        .onFailure { messageFugace = it.message ?: t.creationServeurImpossible }
                }
            },
            onRejoindre = { adresse -> engine.joinServer(adresse) },
            onFermer = { feuilleServeur = false },
        )
    }

    if (feuilleReglages && server != null) {
        MenuServeur(
            nomDuServeur = server.name,
            avatarUrl = server.avatarUrl,
            serverId = server.id,
            nombreMembres = membresServeur.size.takeIf { it > 0 },
            nombreEnLigne = enLigneServeur,
            tagDuServeur = tagDuServeurOuvert,
            onInviter = { feuilleReglages = false; feuilleInvitation = true },
            onSalon = { feuilleReglages = false; feuilleSalon = true },
            onCategorie = { feuilleReglages = false; feuilleCategorie = true },
            onNotifications = { feuilleReglages = false; feuilleNotifsServeur = true },
            // the channel search bar is right there at the top of the panel, so closing the menu
            // puts it under your eyes. a separate search screen would duplicate a field that
            // already filters live
            onChercher = { feuilleReglages = false },
            onTag = { feuilleReglages = false; feuilleEmojis = true },
            onParametres = { feuilleReglages = false; feuilleEmojis = true },
            onMarquerLu = {
                feuilleReglages = false
                scope.launch {
                    server.channels.forEach { marquerLu(contexteApp, engine, it.id) }
                }
            },
            onQuitter = {
                feuilleReglages = false
                // away at once: the engine hides the server before synapse confirms, one write per
                // channel can take minutes. reset, both keys repair themselves on whatever is left
                // (see the effect above)
                val parti = server.id
                serverId = null
                channelId = null
                scope.launch {
                    runCatching { engine.leaveServer(parti) }
                        .onFailure { messageFugace = it.message ?: t.quitterServeurImpossible }
                }
            },
            onSupprimer = server.id.takeIf {
                (membresServeur.firstOrNull { m -> m.userId == profil?.userId }?.role?.powerLevel ?: 0L) >= 100L
            }?.let { id ->
                {
                    feuilleReglages = false
                    serverId = null
                    channelId = null
                    scope.launch {
                        runCatching { engine.supprimerServeur(id) }
                            .onFailure { messageFugace = it.message ?: t.suppressionServeurImpossible }
                    }
                }
            },
            onFermer = { feuilleReglages = false },
        )
    }

    // the settings are a screen, laid over everything else: you navigate between several panels in
    // them, which a bottom sheet cannot do without becoming a screen that will not admit its name
    //
    // it comes in from the right and leaves the same way: the same vocabulary as the sections it
    // contains and as the channel drawer. one grammar of movement for the whole app rather than one
    // per screen
    androidx.compose.animation.AnimatedVisibility(
        visible = feuilleEmojis && server != null,
        enter = androidx.compose.animation.slideInHorizontally(
            tween(tokens.dureeNormale, easing = tokens.easingEntree)
        ) { it },
        exit = androidx.compose.animation.slideOutHorizontally(
            tween(tokens.dureeNormale, easing = tokens.easingSortie)
        ) { it },
    ) {
    if (server != null) {
        PanneauGlissant(onFermer = { feuilleEmojis = false }, gereLeRetour = false) {
        EcranParametresServeur(
            nomDuServeur = server.name,
            emotes = packDuServeur,
            membres = membresServeur,
            paliers = paliers,
            // 50 is matrix's `state_default`: the power needed to write a state event, so to
            // publish the tier table
            peutEditerRoles = (membresServeur.firstOrNull {
                it.userId == engine.currentSession().userId
            }?.role?.powerLevel ?: 0L) >= 50L,
            rolesDecoratifs = decorations.roles,
            attributions = decorations.parMembre,
            avatarDuServeur = server.avatarUrl,
            tagDuServeur = tagDuServeurOuvert,
            badgesTags = badgesTags,
            // the type is spelled out, and it has to be: `.takeIf {}` on a lambda literal loses its
            // `suspend`, which inference does not recover
            //
            // the same threshold as the rest of the server's state: 50, matrix's `state_default`
            //
            // the tag you wear is a copy in your profile, so changing it server side does not
            // update those who already took it. that is the correct behaviour, they chose that
            // badge, and it is the same call as the avatar decoration
            onEnregistrerTag = if (
                (membresServeur.firstOrNull {
                    it.userId == engine.currentSession().userId
                }?.role?.powerLevel ?: 0L) >= 50L
            ) {
                { nouveau ->
                    engine.setTagServeur(server.id, nouveau)
                    tagDuServeurOuvert = nouveau
                }
            } else null,
            // the same threshold as the tiers and for the same reason: `m.room.avatar` is a state
            // event and `state_default` is 50. offering the pencil to somebody the server will
            // refuse is the fault we remove elsewhere
            onChangerIcone = {
                val cible = serverId
                sourceImage = SourceImage(
                    t.iconeServeur,
                    galerie = {
                        choisirIconeServeur.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts
                                    .PickVisualMedia.ImageOnly,
                            )
                        )
                    },
                    appareil = { fichier ->
                        if (cible != null) {
                            recadrage = DemandeRecadrage(android.net.Uri.fromFile(fichier), rond = false) { recadree ->
                                engine.setAvatarServeur(cible, recadree.absolutePath)
                            }
                        }
                    },
                )
            }.takeIf {
                (membresServeur.firstOrNull {
                    it.userId == engine.currentSession().userId
                }?.role?.powerLevel ?: 0L) >= 50L
            },
            onEnregistrerDecoratifs = { nouvelles ->
                engine.setDecorations(server.id, nouvelles)
                decorationsChangees++
            },
            decorationsAvatar = decorationsServeur,
            monIdentifiant = profil?.userId.orEmpty(),
            onAjouterDecoration = { chemin, nomDecoration ->
                val mxc = engine.uploadMedia(chemin)
                // an id taken from the timestamp: it only names the row for removal, and two
                // decorations can legitimately share a name
                val entree = li.velvetchat.core.model.DecorationAvatar(
                    id = "deco-${'$'}{System.currentTimeMillis()}",
                    nom = nomDecoration,
                    mxc = mxc,
                    anime = chemin.endsWith(".gif", ignoreCase = true),
                )
                engine.setInventaireDecorations(server.id, decorationsServeur + entree)
                // reread from the server rather than completed locally: the state event is
                // authoritative, and two devices adding at the same time must not overwrite each
                // other silently
                decorationsServeur = engine.inventaireDecorations(server.id)
            },
            onRetirerDecoration = { id ->
                engine.setInventaireDecorations(
                    server.id,
                    decorationsServeur.filterNot { it.id == id },
                )
                decorationsServeur = engine.inventaireDecorations(server.id)
            },
            sonsSoundboard = sonsServeur,
            onAjouterSon = { chemin, nomSon, emojiSon ->
                val mxc = engine.uploadMedia(chemin)
                val entree = li.velvetchat.core.model.SonSoundboard(
                    id = "son-${'$'}{System.currentTimeMillis()}",
                    nom = nomSon,
                    mxc = mxc,
                    emoji = emojiSon,
                )
                engine.setSonsSoundboard(server.id, sonsServeur + entree)
                sonsServeur = engine.sonsSoundboard(server.id)
                li.velvetchat.app.VocalManager.setSons(sonsServeur)
            },
            // renaming writes the whole catalogue back, like adding: `setSonsSoundboard`
            // replaces the state event, there is no partial update. the `mxc` is carried over
            // untouched, so nothing is re uploaded and the sound keeps its identity in the
            // picker's favourites
            onRenommerSon = { id, nomSon, emojiSon ->
                engine.setSonsSoundboard(
                    server.id,
                    sonsServeur.map {
                        if (it.id == id) it.copy(nom = nomSon, emoji = emojiSon) else it
                    },
                )
                sonsServeur = engine.sonsSoundboard(server.id)
                li.velvetchat.app.VocalManager.setSons(sonsServeur)
            },
            onRetirerSon = { id ->
                engine.setSonsSoundboard(server.id, sonsServeur.filterNot { it.id == id })
                sonsServeur = engine.sonsSoundboard(server.id)
                li.velvetchat.app.VocalManager.setSons(sonsServeur)
            },
            onEnregistrerRoles = { nouveaux ->
                engine.setRoleTiers(server.id, nouveaux)
                // reread from the server rather than assumed: the state event is authoritative,
                // exactly like for the emoji pack just below
                paliers = engine.roleTiers(server.id)
            },
            onMembre = { membreVise = it },
            onChargerMembres = {
                scope.launch {
                    val premier = server.channels.firstOrNull()?.id ?: return@launch
                    runCatching { engine.members(premier) }
                        .onSuccess { membresServeur = it }
                }
            },
            onAjouterEmoji = { chemin, nom, autocollant ->
                engine.addEmote(server.id, nom, chemin, autocollant)
                // the pack is reread rather than completed locally: the state event is
                // authoritative, and two devices adding at the same time must not drift from what
                // the server actually kept
                packDuServeur = engine.emotes(server.id)
                packs = engine.emotePacks()
                pack = packs.flatMap { it.emotes }.distinctBy { it.shortcode }
            },
            onRetirerEmoji = { nom ->
                engine.removeEmote(server.id, nom)
                packDuServeur = engine.emotes(server.id)
                packs = engine.emotePacks()
                pack = packs.flatMap { it.emotes }.distinctBy { it.shortcode }
            },
            onInviter = { feuilleEmojis = false; feuilleInvitation = true },
            onFermer = { feuilleEmojis = false },
        )
        }
    }
    }

    if (feuilleCategorie && server != null) {
        FeuilleNouvelleCategorie(
            nomDuServeur = server.name,
            onCreer = { nom ->
                scope.launch {
                    runCatching { engine.createCategory(server.id, nom) }
                        .onFailure { messageFugace = it.message ?: t.creationCategorieImpossible }
                }
            },
            onFermer = { feuilleCategorie = false },
        )
    }

    // the mode applied to every channel of the server
    //
    // matrix has no per space push rule, the rules cover rooms. so a server setting is a loop, and
    // that is the protocol's truth rather than an approximation. a channel added afterwards keeps
    // the default mode, which the sheet does not claim to prevent
    if (feuilleNotifsServeur && server != null) {
        FeuilleNotifications(
            channel = li.velvetchat.core.model.Channel(
                id = server.id,
                name = server.name,
                topic = null,
                kind = li.velvetchat.core.model.ChannelKind.TEXT,
                unread = li.velvetchat.core.model.Unread.NONE,
                encryption = li.velvetchat.core.model.EncryptionState.UNKNOWN,
            ),
            mode = li.velvetchat.core.model.ModeNotification.DEFAUT,
            onChoisir = { mode ->
                feuilleNotifsServeur = false
                scope.launch {
                    server.channels.forEach {
                        runCatching { engine.setModeNotification(it.id, mode) }
                    }
                    salonsMuets = runCatching { engine.salonsMuets() }.getOrDefault(salonsMuets)
                }
            },
            onFermer = { feuilleNotifsServeur = false },
        )
    }

    if (feuilleSalon && server != null) {
        FeuilleNouveauSalon(
            nomDuServeur = server.name,
            // only the real ones: filing a channel into a category synapse has not created yet
            // would have no id to write
            categories = server.categories.filterNot { it.id.startsWith("brouillon:") },
            onCreer = { nom, categorie, vocal ->
                scope.launch {
                    runCatching {
                        engine.createChannel(server.id, nom, null, categorie?.id, vocal)
                    }.onFailure { messageFugace = it.message ?: t.creationSalonImpossible }
                }
            },
            onFermer = { feuilleSalon = false },
        )
    }

    if (feuilleInvitation && server != null) {
        FeuilleInvitation(
            nomDuServeur = server.name,
            onInviter = { qui -> engine.invite(server.id, qui) },
            onFermer = { feuilleInvitation = false },
            onLien = { engine.lienInvitation(server.id) },
        )
    }

    if (feuilleGif) {
        // nothing left to reread on close: `PrefsGif.revision` turns writing a preference into a
        // compose invalidation, so `gifsEnLigne` above recomputes itself
        FeuilleReglagesGif(onFermer = { feuilleGif = false })
    }

    // the profile comes up from the bottom and not in from the right, reported and correct: the
    // reference brings every person's card up from the bottom. the right is the gesture for screens
    // beside (settings, a room's details), and a person presents themselves by rising
    androidx.compose.animation.AnimatedVisibility(
        visible = feuilleProfil,
        enter = androidx.compose.animation.slideInVertically(
            tween(tokens.dureeNormale, easing = tokens.easingEntree)
        ) { it },
        exit = androidx.compose.animation.slideOutHorizontally(
            tween(tokens.dureeNormale, easing = tokens.easingSortie)
        ) { it },
    ) {
        val p = profil
        val moi = p?.userId ?: engine.currentSession().userId
        PanneauGlissant(onFermer = { feuilleProfil = false }, gereLeRetour = false) {
        EcranProfil(
            userId = moi,
            nom = p?.displayName,
            avatarUrl = p?.avatarUrl,
            etendu = etendu,
            statut = monStatut,
            // one badge per server where you hold a tier. `distinctBy` on the name: two servers can
            // both define "administrator", and showing it twice would say nothing more
            badges = paliersParMembre[moi]?.let { listOf(it) }.orEmpty().distinctBy { it.name },
            stockageEnvois = poidsEnvois.enPoidsLisible(),
            stockageMoteur = poidsMoteur.enPoidsLisible(),
            onChangerAvatar = { choisirAvatar.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts
                        .PickVisualMedia.ImageOnly,
                )
            ) },
            onNom = { nom ->
                scope.launch {
                    runCatching { engine.setDisplayName(nom) }
                        .onSuccess { profil = runCatching { engine.profile() }.getOrNull() }
                }
            },
            onPronoms = { pronoms ->
                scope.launch {
                    runCatching {
                        engine.setProfilEtendu(
                            li.velvetchat.core.model.ProfilEtendu.CLE_PRONOMS,
                            pronoms.ifBlank { null },
                        )
                    }
                    // reread from the server: it is authoritative, and a local value would lie if
                    // the write had been refused for want of the extended profiles msc
                    etendu = runCatching { engine.profilEtendu(moi) }.getOrDefault(etendu)
                }
            },
            onStatut = { feuilleStatut = true },
            // "edit profile" replaces "appearance". the appearance sheet carried three of the
            // reference's ten settings, and it stays reachable from the edit screen, which holds
            // them all
            // the profile stays open underneath, and that is the whole fix
            //
            // reported: opening settings, the control centre or the editor from the profile, then
            // pressing back, left you on the conversation — the profile had been closed on the
            // way in, so there was nothing to come back to. all three are drawn above it (the
            // first two later in this composition, the editor in a `Dialog`), so closing the
            // child reveals the profile again, which is what going back means
            onApparence = { ecranProfilEdition = true },
            onViderCache = {
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { CacheMedia.vider(contexteApp) } }
                    poidsEnvois = 0L
                }
            },
            onFermer = { feuilleProfil = false },
            estAdministrateur = estAdministrateur,
            onCentreDeControle = { centreDeControleOuvert = true },
            onParametres = { ecranParametres = true },
        )
        }
    }

    // placed after the profile screen, the same lesson about order as the appearance and status
    // sheets just above: it is opened from it
    androidx.compose.animation.AnimatedVisibility(
        visible = centreDeControleOuvert,
        enter = androidx.compose.animation.slideInHorizontally { it },
        exit = androidx.compose.animation.slideOutHorizontally { it },
    ) {
        PanneauGlissant(onFermer = { centreDeControleOuvert = false }) {
            EcranAdministration(
                engine = engine,
                serveurs = servers,
                onFermer = { centreDeControleOuvert = false },
                onActiverAdministration = (contexteApp as? android.app.Activity)?.let { a ->
                    { li.velvetchat.app.Engine.activerAdministration(a, engine) }
                },
            )
        }
    }

    // these two sheets are placed after the profile screen, and the order is the point
    //
    // both open from it. in a `Box` the last one drawn is on top, so placed before, they did open
    // and the profile screen covered them entirely. seen on screen, and invisible otherwise, since
    // nothing in the code says one composable hides another

    // the cosmetics only load when the profile opens
    //
    // they used to be read at startup, with the emoji. but an emoji serves from the first message,
    // while a catalogue of thirteen hundred objects only serves whoever opens edit profile, which
    // is now and then. on a mobile link that was several hundred kilobytes and a room to join
    // before anybody could read their first conversation
    //
    // the three families are read one after another on purpose: the engine keeps the room state for
    // a few seconds, so they share one request instead of three
    LaunchedEffect(ecranProfilEdition, servers.size) {
        if (!ecranProfilEdition) return@LaunchedEffect
        // the guard covers all three, not the first. it only looked at the decorations, so a read
        // interrupted or failed along the way left the effects and the plates empty permanently,
        // since the first one was enough to close the door. the nameplates were reported as not
        // visible in the shop, and there were 270 of them in the catalogue
        if (decorationsToutes.isNotEmpty() && effetsTous.isNotEmpty() &&
            plaquesToutes.isNotEmpty()
        ) {
            return@LaunchedEffect
        }
        // together and not in a queue. all three read the same room state, which the engine keeps
        // for ten seconds and serialises: the first fetches it and the other two find it cached. in
        // a queue the first, which joins the room as well as reading it, could exceed the timeout,
        // and the others would each go and fetch several hundred kilobytes over a mobile link
        kotlinx.coroutines.coroutineScope {
            val familles = listOf(
                li.velvetchat.core.model.FAMILLE_DECORATIONS,
                li.velvetchat.core.model.FAMILLE_EFFETS,
                li.velvetchat.core.model.FAMILLE_PLAQUES,
                li.velvetchat.core.model.FAMILLE_CADRES,
                li.velvetchat.core.model.FAMILLE_TAGS,
            ).map { famille ->
                async { runCatching { engine.toutesLesDecorations(famille) }.getOrDefault(emptyList()) }
            }
            decorationsToutes = familles[0].await()
            effetsTous = familles[1].await()
            plaquesToutes = familles[2].await()
            // the frames are not in the guard above: the catalogue is legitimately empty, and
            // putting it there would restart all four loads on every pass
            cadresTous = familles[3].await()
            badgesTags = familles[4].await()
        }
    }

    // the edit profile screen, over everything else
    //
    // a full screen and not a sheet: it carries ten sections and two sub screens, which a bottom
    // sheet cannot hold without becoming a screen that will not admit its name
    sourceImage?.let { source ->
        FeuilleSourceImage(
            titre = source.titre,
            onGalerie = { val g = source.galerie; sourceImage = null; g() },
            onAppareil = { photographierPour(source) },
            onFermer = { sourceImage = null },
        )
    }

    if (ecranImport) {
        PleinEcran {
            EcranImportDiscord(
                engine,
                onLance = { tache, jeton ->
                    li.velvetchat.app.ServiceImport.suivre(contexteApp, tache, jeton)
                },
            ) { ecranImport = false }
        }
    }

    if (ecranParametres) {
        PleinEcran {
            EcranParametres(
                stockageEnvois = poidsEnvois.enPoidsLisible(),
                stockageMoteur = poidsMoteur.enPoidsLisible(),
                onViderCache = {
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { CacheMedia.vider(contexteApp) } }
                        poidsEnvois = 0L
                    }
                },
                onFermer = { ecranParametres = false },
                identifiant = profil?.userId,
                onImportDiscord = { ecranParametres = false; ecranImport = true },
                version = "${li.velvetchat.app.BuildConfig.VERSION_NAME} " +
                    "(${li.velvetchat.app.BuildConfig.VERSION_CODE})",
                onChercherMaj = {
                    val id = runCatching { engine.salonInstallation() }.getOrNull() ?: serverId
                    val publiee = id?.let { runCatching { engine.releasePubliee(it) }.getOrNull() }
                    val abis = android.os.Build.SUPPORTED_ABIS.toList()
                    when {
                        id == null -> t.aucunServeurPourAnnonces
                        publiee == null -> t.aucuneVersionAnnoncee
                        !publiee.plusRecenteQue(
                            li.velvetchat.app.BuildConfig.VERSION_CODE.toLong(),
                        ) -> t.velvetAJour
                        publiee.pourAbis(abis) == null ->
                            t.versionPasPourCetAppareil(publiee.versionName)
                        else -> {
                            // a version dismissed with "later" becomes offerable again: that is
                            // what was just explicitly asked for
                            PrefsMaj.refuser(contexteApp, 0)
                            releaseDispo = publiee
                            t.versionDisponibleBandeau(publiee.versionName)
                        }
                    }
                },
                onDeconnexion = {
                    val activite = contexteApp as? android.app.Activity
                    if (activite != null) scope.launch { li.velvetchat.app.Engine.deconnecter(activite, engine) }
                },
            )
        }
    }

    if (ecranProfilEdition) {
        PleinEcran {
            val moi = profil?.userId ?: engine.currentSession().userId
            EcranModifierProfil(
                userId = moi,
                nom = profil?.displayName,
                avatarUrl = profil?.avatarUrl,
                etendu = etendu,
                decorations = decorationsToutes,
                effets = effetsTous,
                plaques = plaquesToutes,
                cadres = cadresTous,
                tags = tagsDisponibles,
                onNom = { nouveau ->
                    scope.launch {
                        runCatching { engine.setDisplayName(nouveau) }
                            .onSuccess { profil = engine.profile() }
                            .onFailure { android.util.Log.w("Velvet", "Nom non changé", it) }
                    }
                },
                // **one write path for the whole draft**
                //
                // there were eight typed callbacks here, one per field, each firing on its own
                // gesture. the editor keeps a draft now and hands over what changed, so this
                // walks the keys instead of naming them: a field added to `ProfilEtendu` is
                // saveable without a line here
                //
                // sequential and not parallel: these are writes to the same account data, and
                // the server is the one that serialises them. firing thirteen at once got them
                // rate limited (see the `rc_` note), so a profile saved in one go lost some of
                // its fields with nothing said
                onEnregistrer = { brouillon, cles ->
                    scope.launch {
                        var echecs = 0
                        for (cle in cles) {
                            runCatching { engine.setProfilEtendu(cle, brouillon.valeur(cle)) }
                                .onFailure {
                                    echecs++
                                    android.util.Log.w("Velvet", "Profil non écrit ($cle)", it)
                                }
                        }
                        // reread once at the end, not once per key. the server is authoritative,
                        // and this is what puts back a value it refused
                        etendu = runCatching { engine.profilEtendu(moi) }.getOrDefault(etendu)
                        if (echecs > 0) messageFugace = t.nonEnregistreQuoi("$echecs")
                    }
                },
                onChangerAvatar = {
                    sourceImage = SourceImage(
                        t.photoDeProfil,
                        galerie = {
                            choisirAvatar.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts
                                        .PickVisualMedia.ImageOnly,
                                )
                            )
                        },
                        appareil = { fichier ->
                            recadrage = DemandeRecadrage(android.net.Uri.fromFile(fichier), rond = true) { recadree ->
                                engine.setAvatar(recadree.absolutePath)
                                profil = engine.profile()
                            }
                        },
                    )
                },
                onBanniere = { sourceImage = SourceImage(
                    t.banniere,
                    galerie = {
                        choisirBanniere.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts
                                    .PickVisualMedia.ImageOnly,
                            )
                        )
                    },
                    appareil = { fichier -> scope.launch { poserBanniere(fichier) } },
                ) },
                onImporterDecoration = {
                    ornementVise = li.velvetchat.core.model.ProfilEtendu.CLE_DECORATION
                    choisirOrnement.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts
                                .PickVisualMedia.ImageOnly,
                        )
                    )
                },
                onEffet = {
                    ornementVise = li.velvetchat.core.model.ProfilEtendu.CLE_EFFET
                    choisirOrnement.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts
                                .PickVisualMedia.ImageOnly,
                        )
                    )
                },
                onCadre = {
                    ornementVise = li.velvetchat.core.model.ProfilEtendu.CLE_CADRE
                    choisirOrnement.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts
                                .PickVisualMedia.ImageOnly,
                        )
                    )
                },
                onPlaque = {
                    ornementVise = li.velvetchat.core.model.ProfilEtendu.CLE_PLAQUE
                    choisirOrnement.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts
                                .PickVisualMedia.ImageOnly,
                        )
                    )
                },
                onFermer = { ecranProfilEdition = false },
            )
        }
    }

    // cropping, full screen over everything
    //
    // a `Dialog` with no imposed width rather than a box placed in the composition: at this point
    // in the shell there is no stacking container, and a `Box` would be queued with the rest
    // instead of covering it
    recadrage?.let { demande ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { recadrage = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        ) {
            EcranRecadrage(
                uri = demande.uri,
                rond = demande.rond,
                onAnnuler = { recadrage = null },
            ) { fichier ->
                recadrage = null
                EnvoiImageEnCours.etat = EnvoiImage.EnCours
                scope.launch {
                    runCatching { demande.apres(fichier) }
                        .onSuccess { EnvoiImageEnCours.etat = EnvoiImage.Reussi }
                        .onFailure {
                            // said, never swallowed: an avatar that does not change with no word
                            // sends people hunting the fault on the network's side, or the photo's
                            android.util.Log.w("Velvet", "Image de profil non envoyée", it)
                            EnvoiImageEnCours.etat = EnvoiImage.Echec(it.message)
                        }
                    runCatching { fichier.delete() }
                }
            }
        }
    }

    BandeauEnvoiImage()

    // what a background job says when it fails
    //
    // optimism closes the sheet before the server answers, so without this banner a failed creation
    // would take its tile out of the rail without a word, and the gesture would look like it never
    // happened. that is the silent failure family, and optimism is exactly its best terrain
    messageFugace?.let { texte ->
        LaunchedEffect(texte) {
            kotlinx.coroutines.delay(6_000)
            messageFugace = null
        }
        Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                texte,
                color = tokens.textPrimary,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier.padding(horizontal = tokens.espXl)
                    .background(tokens.danger, RoundedCornerShape(tokens.radiusMedium))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
    // the dubious links dialog, rendered once and silent while it has nothing to say
    val navigateurLiens = androidx.compose.ui.platform.LocalUriHandler.current
    val pressePapier = copieur()
    DialogueLien(
        onOuvrir = { url -> runCatching { navigateurLiens.openUri(url) } },
        onCopier = { url ->
            pressePapier(url)
        },
    )

    if (feuilleStatut) {
        FeuilleStatut(
            statutActuel = monStatut,
            silenceActuel = silence,
            onChoisir = { presence, message ->
                // remembered first: if the write fails, we still want the next startup to try
                // publishing what the person asked for
                PrefsStatut.retenir(contexteApp, presence, message)
                // set immediately and not after the round trip: it is the person's choice and needs
                // no confirmation to show. rereading the server returned online and erased the
                // choice under the eyes of whoever had just made it
                monStatut = li.velvetchat.core.model.Statut(presence, message, null)
                scope.launch { runCatching { engine.setPresence(presence, message) } }
            },
            onSilence = { actif ->
                scope.launch {
                    runCatching { engine.setSilence(actif) }
                        .onSuccess { silence = actif }
                }
            },
            onFermer = { feuilleStatut = false },
        )
    }

    // the profile screen, and no longer a sheet
    //
    // it was a bottom sheet with two buttons. it now carries a banner, an identity, badges, a
    // status, two editable fields and a storage section. that is a screen, and passing it off as a
    // sheet would have forced it into the bottom third of the window
    //
    // it comes in from the right like the server settings: one grammar of movement for everything
    // you navigate
    conversationVisee?.let { conv ->
        FeuilleConversation(
            nom = conv.name,
            epingle = conv.pinned,
            aDesNonLus = conv.unread.hasUnread,
            estGroupe = conv.groupe,
            estAmi = conv.userId in mesAmis,
            estMuet = conv.roomId in salonsMuets,
            mode = modeDuSalon,
            serveurs = servers.map { it.id to it.name },
            actions = ActionsConversation(
                onEpingler = {
                    scope.launch {
                        runCatching { engine.setPinned(conv.roomId, !conv.pinned) }
                        // the list is reread rather than edited in place: the server holds the
                        // pin, and a list corrected locally would lie if the write had failed
                        runCatching { engine.directs() }.onSuccess { directs = it }
                    }
                    conversationVisee = null
                },
                onMarquerLu = {
                    scope.launch {
                        if (conv.unread.hasUnread) marquerLu(contexteApp, engine, conv.roomId)
                        else runCatching { engine.setNonLu(conv.roomId, true) }
                        runCatching { engine.directs() }.onSuccess { directs = it }
                    }
                    conversationVisee = null
                },
                onNotifications = {
                    notificationsSur = Channel(
                        conv.roomId, conv.name, null,
                        li.velvetchat.core.model.ChannelKind.TEXT,
                        li.velvetchat.core.model.Unread(notifications = 0, highlights = 0),
                    )
                    conversationVisee = null
                },
                // the profile, the note and the nickname all open the same card. discord shows
                // three entries there and they all land on the popout, because the note and the
                // nickname are fields of the profile, not screens of their own
                onProfil = { ouvrirFicheDuDirect(conv) },
                onNote = { ouvrirFicheDuDirect(conv) },
                onSurnom = { ouvrirFicheDuDirect(conv) },
                onAppeler = {
                    conversationVisee = null
                    lancerAppel(conv.roomId, conv.name)
                },
                onInviterServeur = { serveurId ->
                    scope.launch { runCatching { engine.invite(serveurId, conv.userId) } }
                    conversationVisee = null
                },
                onMuet = { duree ->
                    scope.launch {
                        runCatching {
                            if (duree == null) {
                                engine.setModeNotification(
                                    conv.roomId, li.velvetchat.core.model.ModeNotification.DEFAUT
                                )
                            } else {
                                engine.setSourdine(
                                    conv.roomId,
                                    duree.minutes?.let {
                                        System.currentTimeMillis() + it * 60_000L
                                    },
                                )
                            }
                        }
                        salonsMuets = runCatching { engine.salonsMuets() }
                            .getOrDefault(salonsMuets)
                    }
                    conversationVisee = null
                },
                onFermerMp = {
                    scope.launch {
                        // leaving and forgetting. a direct room you closed and that stays in
                        // `m.direct` comes straight back into the list on the next sync
                        runCatching { engine.leaveChannel(conv.roomId, forget = true) }
                        if (directOuvert?.roomId == conv.roomId) directOuvert = null
                        runCatching { engine.directs() }.onSuccess { directs = it }
                    }
                    conversationVisee = null
                },
                onRetirerAmi = {
                    scope.launch {
                        runCatching { engine.setAmi(conv.userId, false) }
                        mesAmis = runCatching { engine.amis().toSet() }.getOrDefault(mesAmis)
                        relationsChangees++
                    }
                    conversationVisee = null
                },
                onBloquer = {
                    scope.launch {
                        runCatching {
                            engine.bloquer(conv.userId)
                            personnesBloquees = engine.personnesBloquees().toSet()
                        }
                    }
                    conversationVisee = null
                },
            ),
            onFermer = { conversationVisee = null },
        )
    }

    salonVise?.let { cible ->
        FeuilleSalon(
            channel = cible,
            mode = modeDuSalon,
            // inviting into a channel of a space happens at the space's level: the server settings
            // sheet is already there for that, and two doors to the same invite would eventually
            // drift
            peutInviter = serverId != null,
            onMarquerLu = {
                scope.launch { marquerLu(contexteApp, engine, cible.id) }
                salonVise = null
            },
            onNotifications = { notificationsSur = cible; salonVise = null },
            onInviter = { feuilleReglages = true; salonVise = null },
            onFermer = { salonVise = null },
            // only the real categories: an optimistic draft has no id to write into yet
            categories = server?.categories.orEmpty().filterNot { it.id.startsWith("brouillon:") },
            // removing a channel takes the power to write the server's state, the same right as
            // creating one. with no server open, so inside the direct conversations, the row makes
            // no sense: you do not remove a conversation from a space
            onSupprimer = server?.let { ouvert ->
                {
                    val parent = ouvert.categories.firstOrNull { it.nom == cible.categorie }?.id
                    salonVise = null
                    scope.launch {
                        runCatching { engine.supprimerSalon(ouvert.id, cible.id, parent) }
                            .onFailure { messageFugace = it.message ?: t.suppressionImpossible }
                    }
                }
            },
            onDeplacer = { vers ->
                val ouvert = server
                salonVise = null
                if (ouvert != null) {
                    val depuis = ouvert.categories.firstOrNull { it.nom == cible.categorie }?.id
                    deplacementsEnCours[cible.id] = vers?.nom
                    scope.launch {
                        runCatching { engine.deplacerSalon(ouvert.id, cible.id, depuis, vers?.id) }
                            .onFailure {
                                // the channel goes back where it really is, and we say why
                                deplacementsEnCours.remove(cible.id)
                                messageFugace = it.message ?: t.deplacementImpossible
                            }
                    }
                }
            },
        )
    }

    notificationsSur?.let { cible ->
        FeuilleNotifications(
            channel = cible,
            mode = modeDuSalon ?: li.velvetchat.core.model.ModeNotification.DEFAUT,
            onChoisir = { choix ->
                // optimistic on screen and reread afterwards: the server is authoritative, and a
                // refused write must not leave the tick in the wrong place
                modeDuSalon = choix
                scope.launch {
                    runCatching { engine.setModeNotification(cible.id, choix) }
                    modeDuSalon = runCatching { engine.modeNotification(cible.id) }
                        .getOrDefault(choix)
                }
            },
            onFermer = { notificationsSur = null },
        )
    }

    if (feuilleApparence) {
        val moi = profil?.userId ?: engine.currentSession().userId
        FeuilleApparence(
            userId = moi,
            etendu = etendu,
            // the same catalogue as the server settings' and not a second copy: two states for one
            // list would drift at the first addition
            inventaire = decorationsToutes,
            onImporterDecoration = {
                choisirDecoration.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts
                            .PickVisualMedia.ImageOnly,
                    )
                )
            },
            onDecoration = { mxc ->
                scope.launch {
                    runCatching {
                        engine.setProfilEtendu(
                            li.velvetchat.core.model.ProfilEtendu.CLE_DECORATION,
                            mxc,
                        )
                    }
                    // reread from the server, like the gradient: it is authoritative, and a refused
                    // write must not leave the screen showing a choice that did not take
                    etendu = runCatching { engine.profilEtendu(moi) }.getOrDefault(etendu)
                }
            },
            onCouleurs = { haute, basse ->
                scope.launch {
                    runCatching {
                        engine.setProfilEtendu(
                            li.velvetchat.core.model.ProfilEtendu.CLE_COULEUR_HAUTE,
                            haute,
                        )
                        engine.setProfilEtendu(
                            li.velvetchat.core.model.ProfilEtendu.CLE_COULEUR_BASSE,
                            basse,
                        )
                    }
                    // reread from the server: it is authoritative. two writes the second of which
                    // failed would otherwise leave a half changed gradient
                    etendu = runCatching { engine.profilEtendu(moi) }.getOrDefault(etendu)
                }
            },
            onBanniere = { sourceImage = SourceImage(
                    t.banniere,
                    galerie = {
                        choisirBanniere.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts
                                    .PickVisualMedia.ImageOnly,
                            )
                        )
                    },
                    appareil = { fichier -> scope.launch { poserBanniere(fichier) } },
                ) },
            onRetirerBanniere = {
                scope.launch {
                    runCatching {
                        engine.setProfilEtendu(
                            li.velvetchat.core.model.ProfilEtendu.CLE_BANNIERE,
                            null,
                        )
                    }
                    etendu = runCatching { engine.profilEtendu(moi) }.getOrDefault(etendu)
                }
            },
            onFermer = { feuilleApparence = false },
        )
    }

    membreVise?.let { cible ->
        val moi = membresServeur.firstOrNull { it.userId == profil?.userId }
        val monPouvoir = moi?.role?.powerLevel ?: 0L
        val sonPouvoir = cible.role?.powerLevel ?: 0L
        FicheMembre(
            membre = cible,
            etendu = ficheEtendu,
            presence = ficheStatut.presence,
            messageDeStatut = ficheStatut.message,
            palier = palierDe(cible.role?.powerLevel, paliers),
            decoratifs = decorations.de(cible.userId),
            noteInitiale = ficheNote,
            surnomInitial = ficheSurnom,
            onSurnom = { surnom ->
                scope.launch {
                    runCatching { engine.setSurnom(cible.userId, surnom) }
                        .onSuccess {
                            ficheSurnom = surnom
                            // the conversation list carries the name: without this reread the
                            // nickname would only show there at the next message received
                            directs = runCatching { engine.directs() }.getOrDefault(directs)
                            directOuvert = directOuvert?.let { ouvert ->
                                directs.firstOrNull { it.roomId == ouvert.roomId } ?: ouvert
                            }
                        }
                }
            },
            // moderating takes more power than the target has, and not being that target. the
            // second test is not politeness: without it an admin would be offered the chance to
            // kick themselves
            peutModerer = server != null &&
                cible.userId != profil?.userId &&
                monPouvoir > sonPouvoir,
            // null on yourself: you do not block yourself, and the row disappears
            bloque = if (cible.userId == profil?.userId) null
            else cible.userId in personnesBloquees,
            ami = if (cible.userId == profil?.userId) null else ficheAmi,
            onAmi = { devenirAmi ->
                scope.launch {
                    runCatching { engine.setAmi(cible.userId, devenirAmi) }
                        .onSuccess { ficheAmi = devenirAmi; relationsChangees++ }
                        .onFailure {
                            // the case of a dm created elsewhere, with no power to write state: the
                            // engine says so and the screen must not lose it
                            android.util.Log.w("Velvet", "Relation non écrite", it)
                        }
                }
            },
            onBloquer = { bloquer ->
                scope.launch {
                    runCatching {
                        if (bloquer) engine.bloquer(cible.userId)
                        else engine.debloquer(cible.userId)
                        // reread rather than assumed: the list is account data shared between
                        // devices, and it is authoritative
                        personnesBloquees = engine.personnesBloquees().toSet()
                    }.onFailure {
                        android.util.Log.w("Velvet", "Blocage impossible", it)
                    }
                    membreVise = null
                }
            },
            onNote = { note ->
                scope.launch { runCatching { engine.setNote(cible.userId, note) } }
            },
            onMessagePrive = {
                scope.launch {
                    runCatching {
                        val salon = engine.openDirect(cible.userId)
                        directs = engine.directs()
                        directOuvert = directs.firstOrNull { it.roomId == salon }
                        vueMessages = true
                    }
                }
                membreVise = null
            },
            onMentionner = {
                // the mention is dropped into the composer, not sent: you want to write something
                // to this person, not send them their own name
                mentionADeposer = cible.displayName ?: cible.userId
                membreVise = null
            },
            onModerer = { moderationSur = cible; membreVise = null },
            onFermer = { membreVise = null },
        )
    }

    invitationVisee?.let { invitation ->
        FeuilleInvitationRecue(
            invitation = invitation,
            onAccepter = {
                engine.accepterInvitation(invitation.roomId, invitation.estEspace)
                // three lists change at once: the rail (one more server), the conversations, and
                // the invites themselves. reread rather than patched by hand, since the server is
                // authoritative and assuming here what it did is exactly the kind of gap you would
                // only see on the next reload
                invitations = runCatching { engine.invitationsRecues() }.getOrDefault(invitations)
                runCatching { engine.directs() }.onSuccess { directs = it }
                relationsChangees++
            },
            onRefuser = {
                engine.refuserInvitation(invitation.roomId)
                invitations = runCatching { engine.invitationsRecues() }.getOrDefault(invitations)
            },
            onFermer = { invitationVisee = null },
        )
    }

    moderationSur?.let { cible ->
        if (server != null) {
            val moi = membresServeur.firstOrNull { it.userId == profil?.userId }
            FeuilleModeration(
                membre = cible,
                tiers = paliers,
                monPouvoir = moi?.role?.powerLevel ?: 0L,
                decoratifs = decorations.roles,
                sesDecoratifs = decorations.parMembre[cible.userId].orEmpty(),
                // null, so the section is absent, without the power to write state: 50 is matrix's
                // `state_default`, the same threshold as the tier table. offering a switch the
                // server would refuse is the fault we remove elsewhere
                onBasculerDecoratif = if ((moi?.role?.powerLevel ?: 0L) >= 50L) {
                    { roleId, porter ->
                        // reread before writing: the whole table is one state event, and starting
                        // from the displayed copy would overwrite whatever another device assigned
                        // meanwhile
                        val actuel = engine.decorations(server.id)
                        val siens = actuel.parMembre[cible.userId].orEmpty()
                        val nouveaux =
                            if (porter) (siens + roleId).distinct() else siens - roleId
                        engine.setDecorations(
                            server.id,
                            actuel.copy(
                                parMembre = actuel.parMembre + (cible.userId to nouveaux),
                            ),
                        )
                        decorationsChangees++
                    }
                } else null,
                onPromouvoir = { niveau -> engine.setPowerLevel(server.id, cible.userId, niveau) },
                onExclure = { engine.kick(server.id, cible.userId) },
                onBannir = { engine.ban(server.id, cible.userId) },
                onFermer = {
                    moderationSur = null
                    // the list is reread afterwards: a changed power or a kick change what it
                    // should show, and keeping the old one would suggest nothing happened
                    scope.launch {
                        val premier = server.channels.firstOrNull()?.id ?: return@launch
                        runCatching { engine.members(premier) }.onSuccess { membresServeur = it }
                    }
                },
            )
        }
    }

    if (nouvelleConversation) {
        FeuilleEcrireA(
            // the current account's domain: on a small server, you write to your own people first
            domaineParDefaut = engine.currentSession().userId.substringAfter(':'),
            onEcrire = { qui ->
                val salon = engine.openDirect(qui)
                directs = engine.directs()
                directOuvert = directs.firstOrNull { it.roomId == salon }
                // the drawer is not closed here: this sheet lives outside the composable that
                // drives it, and handing it the command would mean crossing three levels for a
                // minor convenience. the conversation opens, the drawer stays where it is, and one
                // drag closes it
            },
            onFermer = { nouvelleConversation = false },
        )
    }

    mediaOuvert?.let { demande ->
        Lightbox(demande = demande, onFermer = { mediaOuvert = null })
    }
    }
    }
}

/**
 * the connection state strip
 *
 * it only appears when there is something to say. a permanent "connected" band is noise, and the
 * missing band when the sync had dropped was the real fault, a conversation that stops updating
 * without saying anything
 */
@Composable
private fun ConnectionBar(state: ConnectionState) {
    val tokens = LocalTokens.current
    val t = textes()
    val (texte, couleur) = when (state) {
        ConnectionState.CONNECTED -> return
        ConnectionState.CONNECTING -> t.connexionEnCours to tokens.textSecondary
        ConnectionState.OFFLINE -> t.horsLigneReessai to tokens.danger
        ConnectionState.STOPPED -> t.synchroArretee to tokens.textMuted
    }
    Box(
        Modifier.fillMaxWidth().background(tokens.surfaceRaised).statusBarsPadding()
            .padding(horizontal = tokens.espM, vertical = 6.dp)
    ) {
        Text(texte, color = couleur, fontSize = 12.sp)
    }
}

/**
 * the server rail
 *
 * a flat black background, which makes the icons read as tokens laid on it. three things in order:
 * the direct messages tile, a separator, then the servers. the separator says the first is not a
 * server but a place apart
 *
 * the selection indicator is to the left of the rail and not on the icon. it is a rounded white bar
 * against the edge: long when the server is open, short when it only has unreads, absent otherwise.
 * putting it on the icon would mean altering the drawing, and placed beside it, it announces the
 * state without touching what it points at
 */
@Composable
private fun ServerRail(
    servers: List<Server>,
    selected: String?,
    messagesActifs: Boolean = false,
    /** how many invites are waiting for an answer. a badge on the messages tile */
    invitations: Int = 0,
    onMessages: () -> Unit = {},
    onAjouter: () -> Unit = {},
    onProfil: () -> Unit = {},
    /** the rail was just reordered: the ids, in the new order */
    onReordonner: (List<String>) -> Unit = {},
    /** the unread direct conversations, under the messages tile, like the reference */
    directsNonLus: List<li.velvetchat.core.model.DirectMessage> = emptyList(),
    onDirect: (li.velvetchat.core.model.DirectMessage) -> Unit = {},
    onSelect: (String) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxHeight().width(tokens.density.serverRailWidth.dp).background(tokens.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(8.dp))
        // the direct messages tile carries the same selection indicator as the servers: it is a
        // place just as they are, even though it is not one
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(4.dp).height(if (messagesActifs) 40.dp else 0.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(
                        if (messagesActifs) tokens.textPrimary
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                // the badge aligns on the tile and not on the column. it aligned on the
                // `weight(1f)`, 64 dp wide, while the tile is 48, and then an `offset(x = 4.dp)`
                // pushed it further still: it left the rail and got clipped by it. this `Box` is
                // the tile's size, so the alignment lands on its corner, exactly like the servers
                // below
                Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier.size(tokens.tailleIconeServeur)
                        .clip(RoundedCornerShape(if (messagesActifs) 16.dp else 24.dp))
                        .background(if (messagesActifs) tokens.accent else tokens.surfaceRaised)
                        .clickable(onClick = onMessages),
                    contentAlignment = Alignment.Center,
                ) {
                    // outline at rest, filled once open: the pair the reference uses everywhere to
                    // say "you are here", and it costs nothing since the icon set provides both
                    // drawings
                    Icone(
                        if (messagesActifs) Icones.messagesActif else Icones.messages,
                        tint = if (messagesActifs) tokens.bg else tokens.textSecondary,
                        taille = 24.dp,
                        description = t.messagesPrives,
                    )
                }
                // `Badge` and not a copy: the one that lived here redrew the same pill with its own
                // sizes, without the "99+" cap or the minimum size. two red badges that do not
                // quite match is the kind of gap you only see with both on screen
                if (invitations > 0) Badge(invitations)
                }
            }
            Box(Modifier.width(4.dp))
        }

        // unread dms, one avatar each under the messages tile, with a red count badge: that is how
        // you see a new message without opening the dms
        directsNonLus.forEach { d ->
            Box(Modifier.padding(vertical = 2.dp), contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier.size(tokens.tailleIconeServeur).clip(CircleShape).clickable { onDirect(d) },
                ) { Avatar(d.userId, d.name, d.avatarUrl, tokens.tailleIconeServeur) }
                Badge(d.unread.notifications)
            }
        }

        Box(
            Modifier.padding(vertical = tokens.espS).width(32.dp).height(2.dp)
                .clip(RoundedCornerShape(1.dp)).background(tokens.border)
        )

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // reordering the rail, by long press then by finger
            //
            // the order lives in account data (see `setOrdreServeurs`), so it follows from one
            // device to another. it had been postponed for want of a second server: writing drag
            // and drop for a one item list is writing code no gesture can reach
            //
            // `detectDragGesturesAfterLongPress` and not a plain drag: the rail scrolls vertically,
            // and the two gestures are the same movement. the long press is what separates them,
            // which is also what the reference does
            //
            // the movement is visual only during the gesture (`graphicsLayer`), and the list is
            // only reordered on release: reordering on every frame would make the tile jump under
            // the finger at every crossing
            val hauteurPastille = tokens.tailleIconeServeur + 4.dp
            val densite = LocalDensity.current
            var glisse by remember { mutableStateOf<String?>(null) }
            var decalage by remember { mutableStateOf(0f) }

            servers.forEachIndexed { index, server ->
                val actif = server.id == selected
                // two distinct signals, and that is the whole unread model
                //
                // "something was said" and "somebody named me" do not call for the same answer: the
                // first waits for you to pass by, the second waits for a reply. conflating them
                // under one number, which is what the previous version did, made the counter
                // useless on a small server where every message counts as a notification: the rail
                // showed 47 permanently and nobody looked at it any more
                val quelqueChose = server.channels.any { it.unread.hasUnread }
                val mentions = server.channels.sumOf { it.unread.highlights }
                // how many slots the finger has crossed, and in which direction
                val crans = if (glisse == server.id) {
                    (decalage / with(densite) { hauteurPastille.toPx() }).roundToInt()
                } else 0

                Box(
                    Modifier
                        .zIndex(if (glisse == server.id) 1f else 0f)
                        .graphicsLayer {
                            if (glisse == server.id) {
                                translationY = decalage
                                // a touch larger: it lifts off the column without anybody having to
                                // draw a shadow
                                scaleX = 1.08f
                                scaleY = 1.08f
                            }
                        }
                        .pointerInput(server.id, servers.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { glisse = server.id; decalage = 0f },
                                onDrag = { changement, delta ->
                                    changement.consume()
                                    decalage += delta.y
                                },
                                onDragCancel = { glisse = null; decalage = 0f },
                                onDragEnd = {
                                    val vers = (index + crans).coerceIn(0, servers.lastIndex)
                                    if (vers != index) {
                                        val ids = servers.map { it.id }.toMutableList()
                                        ids.add(vers, ids.removeAt(index))
                                        onReordonner(ids)
                                    }
                                    glisse = null
                                    decalage = 0f
                                },
                            )
                        }
                ) {
                    IconeServeur(server, actif, quelqueChose, mentions) { onSelect(server.id) }
                }
            }
            Box(Modifier.height(4.dp))
            PastilleRail(Icones.ajouter, tokens.colors.success.toColor(), onAjouter)
            Box(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun IconeServeur(
    server: Server,
    actif: Boolean,
    nonLu: Boolean,
    mentions: Int,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = tokens.espXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the left bar: 40 dp open, 8 dp unread, nothing otherwise
        val hauteur = when {
            actif -> 40.dp
            nonLu -> 8.dp
            else -> 0.dp
        }
        Box(
            Modifier.width(4.dp).height(hauteur)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(if (hauteur > 0.dp) tokens.textPrimary else androidx.compose.ui.graphics.Color.Transparent)
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // bottom right and not top. measured off a reference screenshot: the badge sits in the
            // tile's bottom right corner, entirely inside its footprint, its right edge flush with
            // the tile's and its bottom edge too. at the top it capped the icon instead of filing
            // itself into it
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier.size(tokens.tailleIconeServeur)
                        // the radius changes on selection: round at rest, rounded square once open.
                        // that is the rail's signature gesture
                        .clip(
                            RoundedCornerShape(
                                if (actif) tokens.radii.serverIconSelected.dp
                                else tokens.radii.serverIcon.dp
                            )
                        )
                        .background(if (actif) tokens.accent else tokens.surfaceRaised)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (server.avatarUrl != null) {
                        // the same shape as the tile containing it, otherwise the icon is a circle
                        // sitting in a rounded square, with four corners of background showing
                        Avatar(
                            server.id,
                            server.name,
                            server.avatarUrl,
                            tokens.tailleIconeServeur,
                            forme = RoundedCornerShape(
                                if (actif) tokens.radii.serverIconSelected.dp
                                else tokens.radii.serverIcon.dp
                            ),
                        )
                    } else {
                        Text(
                            server.name.take(2).uppercase(),
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
                // the number is reserved for mentions. the white bar on the left already says there
                // is something new, and a number over it would say nothing more, and would say it
                // wrongly, since it would count messages and not requests for attention
                if (mentions > 0) Badge(mentions)
            }
        }
        Box(Modifier.width(4.dp))
    }
}

/** a rail tile carrying an icon rather than a server */
@Composable
private fun PastilleRail(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    couleur: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Box(
        Modifier.size(tokens.tailleIconeServeur).clip(RoundedCornerShape(tokens.radii.serverIcon.dp))
            .background(tokens.surfaceRaised).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icone(icone, tint = couleur, taille = 22.dp, description = t.ajouterServeur)
    }
}

/**
 * a mention badge: red, numbered, edged in the background's colour
 *
 * the edge is not decorative, it detaches the badge from the icon it overlaps, without which the
 * number merges into whatever it sits on
 */
@Composable
internal fun Badge(count: Int) {
    val tokens = LocalTokens.current
    Box(
        Modifier.padding(top = 2.dp, end = 2.dp)
            .clip(CircleShape).background(tokens.bg).padding(2.dp),
    ) {
        Box(
            Modifier.defaultMinSize(minWidth = tokens.tailleBadge, minHeight = tokens.tailleBadge)
                .clip(CircleShape).background(tokens.unreadBadge)
                .padding(horizontal = tokens.espXs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (count > 99) "99+" else "$count",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = tokens.tailles.badgeTexte.sp,
                fontWeight = FontWeight.Bold,
                // without android's font padding, the number sat low in its badge: that padding is
                // computed on the font's descenders, which a digit does not use. it offset
                // something that is centred
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        }
    }
}

/**
 * the dictation player, placed above the composer while you speak
 *
 * what it shows: that it is running (the dot beats), for how long, or that the nas is listening
 * back over the whole thing, and, while paused, that nothing is being transcribed any more. what it
 * carries: pause and resume, "finish" (the final text arrives), and a cross that cancels
 *
 * on failure it stays, with the reason, until somebody closes it: the toast it replaced disappeared
 * before anybody could read it
 */
@Composable
private fun LecteurDictee(
    enCours: Boolean,
    enPause: Boolean,
    finalisation: Boolean,
    erreur: String?,
    debut: Long,
    onPause: () -> Unit,
    onTerminer: () -> Unit,
    onAnnuler: () -> Unit,
    onFermerErreur: () -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()
    if (!enCours && !finalisation && erreur == null) return

    var secondes by remember(debut) { mutableStateOf(0L) }
    LaunchedEffect(debut, enCours, finalisation) {
        while (enCours || finalisation) {
            secondes = (System.currentTimeMillis() - debut) / 1000
            kotlinx.coroutines.delay(500)
        }
    }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dictee")
    val battement by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(700),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "point",
        )

    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espM, vertical = 6.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(if (erreur != null) tokens.danger.copy(alpha = 0.16f) else tokens.surfaceRaised)
            .padding(horizontal = tokens.espM, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(
                when {
                    erreur != null -> tokens.danger
                    finalisation -> tokens.accent
                    enPause -> tokens.textMuted
                    else -> tokens.danger.copy(alpha = battement)
                },
            ),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    erreur != null -> t.dicteeInterrompue
                    finalisation -> t.dicteeTranscription
                    enPause -> t.dicteeEnPause
                    else -> t.dicteeEnCours
                },
                color = tokens.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                erreur ?: "%d:%02d".format(secondes / 60, secondes % 60),
                color = tokens.textMuted,
                fontSize = 12.sp,
                maxLines = 2,
            )
        }
        if (erreur != null) {
            Icone(
                Icones.fermer, tint = tokens.textSecondary, taille = 18.dp, description = t.fermer,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onFermerErreur).padding(6.dp),
            )
        } else if (!finalisation) {
            Icone(
                if (enPause) Icones.micro else Icones.pause,
                tint = tokens.textSecondary,
                taille = 20.dp,
                description = if (enPause) t.dicteeReprendre else t.dicteePause,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onPause).padding(6.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                t.dicteeTerminer,
                color = tokens.bg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(tokens.accent)
                    .clickable(onClick = onTerminer).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(4.dp))
            Icone(
                Icones.fermer, tint = tokens.textSecondary, taille = 18.dp,
                description = t.dicteeAnnuler,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAnnuler).padding(6.dp),
            )
        }
    }
}

/**
 * where the image comes from, the gallery or the camera, and what to do with the photo taken. the
 * file is already on disk when [appareil] is called
 */
private class SourceImage(
    val titre: String,
    val galerie: () -> Unit,
    val appareil: (java.io.File) -> Unit,
)

/** the sheet asking where the image comes from. two rows, no third path */
@Composable
private fun FeuilleSourceImage(
    titre: String,
    onGalerie: () -> Unit,
    onAppareil: () -> Unit,
    onFermer: () -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()
    FeuilleBasse(onFermer = onFermer) {
        Text(
            titre,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espS),
        )
        LigneSourceImage(Icones.photo, t.choisirGalerie, onGalerie)
        LigneSourceImage(Icones.camera, t.prendrePhoto, onAppareil)
        Spacer(Modifier.height(tokens.espM))
    }
}

@Composable
private fun LigneSourceImage(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    libelle: String,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = tokens.espXl, vertical = tokens.espM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tokens.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) { Icone(icone, tint = tokens.textSecondary, taille = 20.dp) }
        Spacer(Modifier.width(14.dp))
        Text(libelle, color = tokens.textPrimary, fontSize = tokens.typography.bodySize.sp)
    }
}

/**
 * marking read also clears the notification
 *
 * the two were separate: you read the room and its notification stayed in the shade until you
 * swiped it away by hand. going through here saves having to think about it at each of the places
 * that mark a room read
 */
private suspend fun marquerLu(contexte: android.content.Context, engine: MatrixEngine, salonId: String) {
    runCatching { engine.markRead(salonId) }
    runCatching { li.velvetchat.app.Annonceur.effacer(contexte, salonId) }
}

/**
 * the channel panel
 *
 * the top left corner rounded, and only that one: the panel lifts off the rail like a sheet laid on
 * it, without lifting off the screen's edges, which it touches
 *
 * four stacked bands: the server header, the search bar and its two actions, the list, then the
 * user bar. the first two are fixed and only the list scrolls, which keeps the search reachable
 * whatever the number of channels
 */
@Composable
private fun ChannelList(
    server: Server?,
    selected: String?,
    muets: Set<String> = emptySet(),
    onSalonLong: (Channel) -> Unit = {},
    onMarquerLuSalon: (Channel) -> Unit = {},
    onBasculerMuetSalon: (Channel, Boolean) -> Unit = { _, _ -> },
    nombreMembres: Int?,
    /** how many people are in voice, per channel. absent means nobody, see `ChannelRow` */
    enVocal: Map<String, Int> = emptyMap(),
    profil: li.velvetchat.core.model.Profile?,
    /** the nameplate being worn, see `BarreUtilisateur` */
    plaque: String? = null,
    /** the avatar decoration being worn, passed down to the bottom bar */
    decoration: String? = null,
    statut: li.velvetchat.core.model.Statut =
        li.velvetchat.core.model.Statut.INCONNU,
    onStatut: () -> Unit = {},
    modifier: Modifier = Modifier,
    onEntrer: () -> Unit = {},
    onInviter: () -> Unit = {},
    onProfil: () -> Unit = {},
    onSelect: (String) -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()
    val seuil = with(LocalDensity.current) { tokens.density.swipeReplyThresholdDp.dp.toPx() }
    // reset when the server changes: a filter that survives would make a server look empty without
    // saying why
    var filtre by remember(server?.id) { mutableStateOf("") }

    Column(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(topStart = tokens.radiusLarge))
            .background(tokens.surface)
            .pointerInput(Unit) {
                var parcouru = 0f
                detectHorizontalDragGestures(
                    onDragEnd = { parcouru = 0f },
                    onDragCancel = { parcouru = 0f },
                ) { _, delta ->
                    parcouru += delta
                    if (parcouru < -seuil) { parcouru = 0f; onEntrer() }
                }
            }
    ) {
        // the header: the server's name, a chevron, and a context subtitle
        Column(
            Modifier.fillMaxWidth().clickable(enabled = server != null, onClick = onInviter)
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    server?.name ?: "—",
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (server != null) {
                    Icone(
                        Icones.suite,
                        tint = tokens.textMuted,
                        taille = 18.dp,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
            if (server != null) {
                Text(
                    listOfNotNull(
                        nombreMembres?.let { t.membresCompte(it) },
                        // nothing rather than "0 channels" while loading. the rail draws before the
                        // channels are paginated (see `espacesSeuls`), and a server saying "0
                        // channels" for a second is lying
                        server.channels.size.takeIf { it > 0 }?.let { t.salonsNombre(it) },
                    ).joinToString("  •  "),
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // the search filters the list, and it really does
        //
        // it used to be clickable scenery: a grey pill with the word "search" on it that reacted to
        // nothing. that is worse than an absence, an absence is visible and understood, and a dead
        // button teaches people to distrust all the others
        //
        // what it does is deliberately modest: it filters this server's channels by name. it is not
        // the reference's full text search, which asks the server about the whole history and needs
        // its own results screen, it is what a bar placed there can honestly do, and it earns its
        // keep as soon as a server has a dozen channels
        //
        // the events button was removed. it opened nothing, and there is nothing to open, matrix
        // has no scheduled events. keeping it to look like the reference would have meant keeping a
        // promise we cannot hold
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espM, vertical = tokens.espXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(44.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .padding(horizontal = tokens.espM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icone(Icones.recherche, tint = tokens.textMuted, taille = 16.dp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (filtre.isEmpty()) {
                        Text(
                            t.rechercher,
                            color = tokens.textMuted,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = filtre,
                        onValueChange = { filtre = it },
                        singleLine = true,
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (filtre.isNotEmpty()) {
                    Icone(
                        Icones.fermer,
                        tint = tokens.textMuted,
                        taille = 16.dp,
                        description = t.effacerRecherche,
                        modifier = Modifier.clickable { filtre = "" }.padding(start = 6.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            BoutonCarre(Icones.inviter, t.inviter) { onInviter() }
        }

        val salons = remember(server?.channels, filtre) {
            val tous = server?.channels.orEmpty()
            if (filtre.isBlank()) tous
            else tous.filter { it.name.contains(filtre.trim(), ignoreCase = true) }
        }

        // the categories, grouped keeping the server's order, and the empty ones with them, which
        // `groupBy` alone could not do: a category with no channel appeared in no grouping, so
        // nowhere. the grouping is shared with the desktop
        val groupes = remember(salons, server?.categories) {
            grouperSalons(salons, server?.categories.orEmpty())
        }

        // the collapsed categories, by name and not by position: collapsing one then creating
        // another before it must not expand the first and collapse the new one
        val repliees = remember(server?.id) { mutableStateListOf<String>() }

        LazyColumn(Modifier.weight(1f).padding(top = tokens.espXs)) {
            groupes.forEach { (categorie, canaux) ->
                val replie = categorie != null && categorie in repliees
                if (categorie != null) {
                    item(key = "cat:$categorie") {
                        EnteteCategorie(
                            nom = categorie,
                            replie = replie,
                            // a collapsed category keeps its unreads visible, otherwise collapsing
                            // would amount to hiding what you have not read
                            nonLus = canaux.count { it.unread.hasUnread },
                            mentions = canaux.sumOf { it.unread.highlights },
                        ) {
                            if (replie) repliees.remove(categorie) else repliees.add(categorie)
                        }
                    }
                }
                // a selected channel stays visible even inside a collapsed category, and that is
                // what the reference does: collapsing it would hide where you are, which is the
                // list's only landmark
                val visibles = if (replie) canaux.filter { it.id == selected } else canaux
                items(visibles, key = { it.id }) { canal ->
                    ChannelRow(
                        canal,
                        canal.id == selected,
                        muet = canal.id in muets,
                        onMarquerLu = { onMarquerLuSalon(canal) },
                        onBasculerMuet = { onBasculerMuetSalon(canal, canal.id in muets) },
                        onLong = { onSalonLong(canal) },
                        enVocal = enVocal[canal.id] ?: 0,
                    ) { onSelect(canal.id) }
                }
            }
            // a filter with no results has to say so. a list that empties without a word makes
            // people think the server lost its channels
            if (salons.isEmpty() && filtre.isNotBlank()) {
                item {
                    Text(
                        t.aucunSalonNomme(filtre.trim()),
                        color = tokens.textMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espL),
                    )
                }
            }
        }

        // the user bar is not here any more. it lives at the level of the whole drawer, so its
        // avatar can overhang the server rail, see the caller
    }
}

/**
 * a category header: chevron, the name in capitals, and its unreads when collapsed
 *
 * the capitals and the small size are not decorative, they tell a group title from a channel name
 * without having to draw a separator. that is the reference's vocabulary, and it fits a narrow
 * panel where a horizontal rule would eat a line
 *
 * the chevron rotates rather than changing glyph: you then see it is the same button in two states,
 * and not two different buttons
 */
@Composable
private fun EnteteCategorie(
    nom: String,
    replie: Boolean,
    nonLus: Int,
    mentions: Int,
    onBasculer: () -> Unit,
) {
    val tokens = LocalTokens.current
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (replie) -90f else 0f,
        animationSpec = tween(tokens.dureeRapide),
        label = "chevron",
    )
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onBasculer)
            .padding(start = tokens.espM, end = tokens.espL, top = tokens.espM, bottom = tokens.espXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(
            Icones.replier,
            tint = tokens.textMuted,
            taille = 14.dp,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
        Spacer(Modifier.width(4.dp))
        Text(
            nom.uppercase(),
            color = if (nonLus > 0) tokens.textSecondary else tokens.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        // collapsed, a category carries the signals of what it hides. without that, collapsing
        // would amount to hiding what you have not read, and nobody would ever collapse anything
        // again
        if (replie && mentions > 0) Badge(mentions)
        else if (replie && nonLus > 0) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tokens.unreadDot))
        }
    }
}

@Composable
private fun BoutonCarre(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icone(icone, tint = tokens.textSecondary, taille = 20.dp, description = description)
    }
}

/**
 * the user bar, at the bottom of the panel
 *
 * a rounded card and not a full width band: it belongs to the panel, it is not its plinth. the name
 * is in the accent colour, since it is you, and this is the only place in the interface where you
 * name yourself
 */
@Composable
internal fun BarreUtilisateur(
    profil: li.velvetchat.core.model.Profile?,
    statut: li.velvetchat.core.model.Statut,
    /**
     * the nameplate, laid as the card's background, which is what the reference does
     *
     * it used to be the banner, and it covered everything, nameplate included. the two do not play
     * the same role: a banner is the header image of a profile you open, and a nameplate is made to
     * sit behind a name in a list, which is exactly this bar
     */
    plaque: String? = null,
    /**
     * the avatar decoration being worn
     *
     * it showed everywhere except here, on your own bar, which is to say the one place where you
     * check the effect of the one you just picked. and it is also what overhangs the card, see the
     * measurements below
     */
    decoration: String? = null,
    /** is the global mute on? the bell says so, and toggles it */
    silence: Boolean = false,
    onClick: () -> Unit,
    /**
     * toggles the global mute
     *
     * the bell used to open the status sheet, because that is where the setting lived. that was the
     * last path making this bar a second user menu, the one just merged into the profile. a bell
     * that mutes in one gesture also says what it does better than a bell that opens a list
     */
    onSilence: (Boolean) -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current

    // the avatar is outside, and that is the whole of what makes it float
    //
    // first version: a full width card with the avatar tidily inside it. direct complaint, the
    // floating profile was not what was expected. measured to the pixel off the reference
    // screenshot (1008 by 2244, rescaled to 1344 by 2992 at 480 dpi, so 1 measured px is 0.444 dp)
    //
    // | what is measured        | measured | dp   | |-------------------------|----------|------| |
    // card height             | 125 px   | 55.5 | | profile photo           | 119 px   | 53   | |
    // decoration ring         | 145 px   | 64   | | card's right margin     |  36 px   | 16   | |
    // bell button             |  70 px   | 31   |
    //
    // two things come out of that, and they are exactly the two that were missing. the photo takes
    // nearly the card's whole height, 53 out of 55.5, instead of the 40 in 56 we had, which left it
    // swimming in empty space. and the ring is taller than the card, 64 against 55.5, so it
    // overhangs by four points top and bottom. that overhang is what makes the avatar look laid
    // over the card rather than filed inside it, and that is where the floating comes from
    //
    // so the card is offset to the right and the avatar drawn after it, straddling its left edge.
    // `Box` and not `Row` for that precise reason: in a row the avatar would push the card instead
    // of overlapping it
    Box(
        Modifier.fillMaxWidth()
            // the system inset first, before any painting: painted after, it pushed the card under
            // the gesture bar, with no bottom edge and no visible corners
            .navigationBarsPadding()
            .padding(horizontal = tokens.espS, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // the card, offset by half the avatar. so it passes under it
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 26.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(tokens.radiusLarge))
                .background(tokens.surfaceRaised)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // the banner as a background, dimmed: the reference has one, and it is what gives an
            // otherwise flat card its depth. darkened, because what matters here is the nickname
            // and the status, and an image at full strength would make them unreadable
            //
            // inside a `Row` it cannot be `matchParentSize`, so it is laid over from a sibling
            // `Box`, below
            Spacer(Modifier.width(38.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profil?.displayName
                            ?: profil?.userId?.removePrefix("@")?.substringBefore(':')
                            ?: "…",
                        color = tokens.accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // the chevron says this name opens. the same sign as the server header and a
                    // room's title: three places, one convention
                    Icone(
                        Icones.replier,
                        tint = tokens.textMuted,
                        taille = 13.dp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                // the real status, and not "online" written in code, which is what the bar used to
                // show, an assertion nothing checked. the status message beats the label when there
                // is one, since it is what the person chose to say
                //
                // the status no longer leads to its own menu. this bar opened a status menu of its
                // own while the card opened the profile: two competing user menus, for the same
                // person, a centimetre apart. reported bluntly as making no sense
                //
                // the reference has one: the bar opens the profile, and it is inside the profile
                // that the photo leads to the presence picker. so everything here inherits the
                // card's click, with no modifier of its own
                Text(
                    statut.message ?: libelle(statut.presence),
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // the bell is back, and this time it opens something
            //
            // it had been removed because it led nowhere, and a dead button costs more than the
            // hole it fills. since then the global mute exists: "mute notifications on all your
            // devices" lives in the status sheet
            //
            // a filled rounded square and not a bare icon: 31 dp measured off the reference, on a
            // fill darker than the card. without that fill it got lost in the banner as soon as a
            // light image passed underneath
            Box(
                Modifier.padding(end = 8.dp)
                    .size(31.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.bg.copy(alpha = 0.55f))
                    .clickable { onSilence(!silence) },
                contentAlignment = Alignment.Center,
            ) {
                Icone(
                    if (silence) Icones.silence else Icones.notifications,
                    tint = if (silence) tokens.danger else tokens.textPrimary,
                    taille = 17.dp,
                    description = t.notifications,
                )
            }
        }

        // the nameplate, laid over the card and under the text. it lives in its own `Box` because
        // it has to match the card, offset included, without taking part in the row that lays out
        // the text and the bell
        plaque?.let {
            Box(
                Modifier.padding(start = 26.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(tokens.radiusLarge))
            ) {
                // `Crop`, and it is a deliberate reversal. this comment used to say a nameplate "is
                // drawn to fit a strip, not to be cropped", which is false as soon as the strip
                // does not have its shape. a nameplate is 448 by 84, a ratio of 5.33, and this
                // strip is 6.4, and the gap reads by eye, the plates looked slightly squashed.
                // cropped, it keeps its proportions and only loses a band at the sides
                EmojiEnLigne(it, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                // a light veil: a nameplate is already made to carry a name, and darkening it as
                // much as a photo would amount to putting it out
                Box(Modifier.matchParentSize().background(tokens.bg.copy(alpha = 0.25f)))
            }
        }

        // the avatar last, so over everything else, and outside the card
        //
        // the presence dot opens the status and not the profile: two targets in the same bar,
        // because they are two different intentions, changing your photo has nothing to do with
        // declaring yourself away
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(
                userId = profil?.userId ?: "?",
                displayName = profil?.displayName,
                avatarUrl = profil?.avatarUrl,
                taille = 52.dp,
                decoration = decoration,
            )
            PastillePresence(
                statut.presence,
                taille = 15.dp,
                couleurDuFond = tokens.surface,
            )
        }
    }
}

/**
 * a channel row
 *
 * three states, and they are said through weight and colour, never through an ornament: read
 * (grey), unread (white and bold, plus a dot on the left), open (raised background). a mentioned
 * channel also carries the red badge, on the right
 */
// `internal` and not `private`: the interface tests mount this row on its own, with states a whole
// screen cannot produce on demand, a muted channel that still carries a mention for instance.
// making it reachable is the price of being able to check it other than by eye on a screenshot
@Composable
internal fun ChannelRow(
    channel: Channel,
    selected: Boolean,
    /** muted: dimmed, like in the reference */
    muet: Boolean = false,
    /**
     * swiping a channel row
     *
     * the same mechanics as swipe to reply on a message: it follows your finger, releasing past a
     * threshold acts, and short of it the row comes back. what changes is what the gesture carries,
     * here two actions depending on direction rather than one
     *
     * both already existed in the long press sheet, and the swipe only makes them reachable without
     * opening anything. exactly the gesture to value ratio the list was missing
     */
    onMarquerLu: () -> Unit = {},
    onBasculerMuet: () -> Unit = {},
    onLong: () -> Unit = {},
    /**
     * how many people are in voice here. 0 means nobody, and the badge does not show
     *
     * a muted channel keeps its own: muting says "do not alert me", not "hide what happens there",
     * the same distinction as for mentions
     */
    enVocal: Int = 0,
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current

    // a muted channel has no unread to show. that is what the reference does and it is consistent:
    // you muted it precisely so it would stop asking. keeping the white dot and the bold would let
    // it call out in another voice
    val nonLu = channel.unread.hasUnread && !muet

    val scope = rememberCoroutineScope()
    val densite = LocalDensity.current
    val glisse = remember { androidx.compose.animation.core.Animatable(0f) }
    val seuil = with(densite) { tokens.density.swipeReplyThresholdDp.dp.toPx() }

    // two actions depending on direction, and the hint reads before you act. the revealed
    // background announces what releasing will do, and without it a swipe would be a bet. to the
    // right marks read, the "i have seen it" gesture, and to the left mutes or unmutes
    val versLaDroite = glisse.value > 0f
    val actionPossible = if (versLaDroite) channel.unread.hasUnread else true
    val franchi = kotlin.math.abs(glisse.value) > seuil && actionPossible

    Box(Modifier.fillMaxWidth().padding(horizontal = tokens.espS, vertical = 1.dp)) {
        if (glisse.value != 0f) {
            Row(
                Modifier.matchParentSize()
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(
                        if (franchi) tokens.accent.copy(alpha = 0.20f)
                        else tokens.surfaceRaised.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (versLaDroite) Arrangement.Start else Arrangement.End,
            ) {
                Text(
                    when {
                        versLaDroite && !channel.unread.hasUnread -> t.dejaLu
                        versLaDroite -> t.marquerCommeLu
                        muet -> t.retablirLeSon
                        else -> t.rendreMuet
                    },
                    color = if (franchi) tokens.accent else tokens.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

    Row(
        Modifier.fillMaxWidth()
            .offset { IntOffset(glisse.value.roundToInt(), 0) }
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(if (selected) tokens.surfaceRaised else tokens.surface)
            // long press: what you want to do about the channel, as opposed to inside it. it was
            // the only place in the list where that gesture did nothing
            .combinedClickable(
                onLongClick = onLong,
                onClick = onClick,
            )
            .pointerInput(seuil, muet, channel.unread.hasUnread) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val d = glisse.value
                        scope.launch { glisse.animateTo(0f, tokens.ressort()) }
                        if (kotlin.math.abs(d) > seuil) {
                            if (d > 0f) { if (channel.unread.hasUnread) onMarquerLu() }
                            else onBasculerMuet()
                        }
                    },
                    onDragCancel = { scope.launch { glisse.animateTo(0f, tokens.ressort()) } },
                ) { _, delta ->
                    // capped at one and a half times the threshold: past that the gesture says
                    // nothing more and the row would leave its column
                    scope.launch {
                        glisse.snapTo((glisse.value + delta).coerceIn(-seuil * 1.5f, seuil * 1.5f))
                    }
                }
            }
            // measured on a real device: the row was 46 dp against the reference's 36.7, a quarter
            // too much, so on the same screen it shows four channels where the reference shows five
            //
            // the 34 dp below plus the 2 dp of margin give the 36 dp aimed at
            .height(34.dp)
            .padding(horizontal = tokens.espS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the unread dot sits to the left of the list's rail and not inside the row: it marks the
        // row without shifting its content, so the names stay aligned
        Box(
            Modifier.width(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (nonLu && !selected) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(tokens.unreadDot))
            }
        }
        Icone(
            if (channel.kind == li.velvetchat.core.model.ChannelKind.VOICE) {
                Icones.salonVocal
            } else {
                Icones.salon
            },
            tint = if (selected || nonLu) tokens.textSecondary else tokens.textMuted,
            // an 18 dp box for about 14.7 dp of ink, the measurement taken off the reference. at 20
            // dp the hash was 10% too big, a real gap, though far smaller than a first badly
            // calibrated measurement had suggested
            taille = 18.dp,
            modifier = Modifier.padding(start = tokens.espXs),
        )
        // 8 dp: measured on a real device after a first try at 7, which gave 28 px of space against
        // the 31 measured on the reference. one more point makes them match
        Spacer(Modifier.width(8.dp))
        Text(
            channel.name,
            color = when {
                selected -> tokens.textPrimary
                nonLu -> tokens.textPrimary
                // one notch under `textMuted`: the channel stays readable and reachable and stops
                // weighing in the list. that is exactly the reference's rendering
                muet -> tokens.textMuted.copy(alpha = 0.45f)
                else -> tokens.textMuted
            },
            fontWeight = if (nonLu) FontWeight.SemiBold else FontWeight.Normal,
            // 16 sp, unchanged, and that is a correction to my measurement rather than to the code
            //
            // a first comparison, calibrated against the status bar's clock, concluded the text was
            // a quarter too large. it was wrong: the emulator is an ldplayer whose status bar is a
            // house overlay and not android's, so i was comparing two different fonts. both devices
            // are at 480 dpi and font scale 1.0, and the name's ink measures 12.3 dp here against
            // 12.0 on the reference. there was nothing to fix
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // same in the list: the dot and the bold carry the unread, and the red badge carries the
        // mentions. a very active channel you follow without being named in stays readable at a
        // glance as "to read" and not as "you are wanted"
        //
        // except when muted: a mention in a muted channel keeps its badge. muting says "do not tell
        // me everything", not "do not tell me i was named". the same distinction as between
        // `hasUnread` and `highlights`, pushed one notch
        if (channel.unread.hasMention) Badge(channel.unread.highlights)

        // "N in voice", a state and not a request for attention
        //
        // deliberately not the mentions' red badge: that one says "you are wanted" and this one
        // only says "something is happening here". a discreet count next to a microphone icon is
        // enough, and it is what the reference does
        if (enVocal > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(start = tokens.espS),
            ) {
                Icone(Icones.salonVocal, tint = tokens.textMuted, taille = 14.dp)
                Text(
                    enVocal.toString(),
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    }
}

@Composable
internal fun Conversation(
    engine: MatrixEngine,
    channel: Channel?,
    estPrive: Boolean = false,
    /**
     * the person opposite, when there is one
     *
     * `Channel` carries neither avatar nor presence: it is a place. a two person conversation is
     * not a place, it is somebody, and the header used to draw an initials tile while the photo was
     * already there, one field away
     */
    interlocuteur: li.velvetchat.core.model.DirectMessage? = null,
    modifier: Modifier = Modifier,
    onGlisser: (Float) -> Unit = {},
    onRelacher: (Float) -> Unit = {},
    onOuvrirTiroir: () -> Unit = {},
    onReglagesGif: () -> Unit = {},
    mentionADeposer: String? = null,
    onMentionDeposee: () -> Unit = {},
    onMembre: (Member) -> Unit = {},
    /** says the read state changed here, so the conversation list rereads itself */
    onLectureChangee: () -> Unit = {},
) {
    val t = textes()
    // the server's roles do not come into a direct conversation
    //
    // `LocalRoleTiers` and `LocalRoleColors` are provided once for the whole tree, from the server
    // selected in the rail. and the rail keeps its selection when a direct message opens, so the
    // server's tiers leaked into the one to one and the person's nickname showed flanked by a
    // moderator badge. seen during the beta test
    //
    // it is wrong in the literal sense: a tier dresses a power inside a server, and a two person
    // conversation belongs to none. so we clear them here, at the exact place where we know we are
    // no longer in a server
    //
    // the clearing happens in the caller and not here. it used to be done by recursion: the direct
    // conversation called itself, under the cleared `CompositionLocal`s, with `estPrive = false`.
    // the clearing worked, and the flag was lost on the way. so everything depending on it ran in
    // the server channel branch: the hash instead of the avatar, "message in #channel" instead of
    // "@somebody", and, since it is no longer everywhere, the call button absent from precisely the
    // place that is its own
    //
    // the fault had been visible from the start, the hash at the top of a two person conversation,
    // and it took a button disappearing for anybody to go and look. a function that calls itself
    // while changing one of its own arguments is a trap: it looks like it is only wrapping

    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    val contexte = androidx.compose.ui.platform.LocalContext.current
    val emotes = LocalEmotes.current
    val salonsPourMentions = LocalSalons.current

    if (channel == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(t.aucunSalon, color = tokens.textMuted)
        }
        return
    }

    // the message flow is recollected when the room changes, and the old subscription released,
    // which cuts the sdk side listener. `collectAsStateWithLifecycle` would not do that on its own
    // if the key were not the room's id
    var messages by remember(channel.id) { mutableStateOf<List<Message>>(emptyList()) }

    /**
     * the message we jumped to, or null while reading the present
     *
     * when it is set, the conversation no longer shows the live flow but a timeline centred on that
     * target, context included, even when it is far older than what was loaded. the sdk builds it,
     * and the version i had announced as hard, paginating by hand to the target, turned out to be
     * unnecessary
     */
    var cibleDuSaut by remember(channel.id) { mutableStateOf<String?>(null) }

    /**
     * a listed room is not necessarily a joined room, and ignoring that took the whole app down
     *
     * the channel column comes from the space's hierarchy, and the sdk only knows the ones actually
     * joined. when `joinServer` refuses one, an invite only room, a failure in the middle of the
     * cascade, it stays displayed, and opening it threw "unknown room" from a `LaunchedEffect`, so
     * on the main thread, so as a hard crash. seen on an account that had just arrived in a space,
     * which is exactly the path invite registration opens
     */
    var erreurSalon by remember(channel.id) { mutableStateOf<String?>(null) }
    // closed when the room changes: a search covers the room it was started in
    var rechercheOuverte by remember(channel.id) { mutableStateOf(false) }

    LaunchedEffect(channel.id, cibleDuSaut) {
        // changing source empties the screen rather than leaving the other one's messages in it:
        // two timelines share neither bounds nor ids, and mixing them for one frame would produce a
        // conversation that contradicts itself
        messages = emptyList()
        erreurSalon = null
        val cible = cibleDuSaut
        try {
            if (cible == null) engine.messagesFlow(channel.id).collect { messages = it }
            else engine.contexteFlow(channel.id, cible).collect { messages = it }
        } catch (annulation: kotlinx.coroutines.CancellationException) {
            // changing room cancels this collection: that is normal operation and not a fault.
            // swallowing it here would break the coroutine's cancellation
            throw annulation
        } catch (t: Throwable) {
            erreurSalon = t.message ?: horsUi.conversationIndisponiblePoint
        }
    }

    erreurSalon?.let { message ->
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    t.conversationIndisponible,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    t.salonNonRejoint,
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    message,
                    color = tokens.textMuted,
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
        return
    }

    // marked read on open: without it the badge never comes down and turns into noise
    //
    // triggered on the room's id only and not on the list's size: on `messages.size` every incoming
    // message caused a network round trip while somebody reads. a pause lets the initial pagination
    // finish first
    val contexteLecture = androidx.compose.ui.platform.LocalContext.current
    // you said unread, so the room stays unread. the acknowledgement above waits a second and a
    // half, which is exactly long enough for somebody to open a room and mark a message unread
    // before it fires and puts it back
    var nonLuVoulu by remember(channel.id) { mutableStateOf(false) }
    LaunchedEffect(channel.id) {
        // the notification goes at once: the room is right there in front of you
        runCatching { li.velvetchat.app.Annonceur.effacer(contexteLecture, channel.id) }
        kotlinx.coroutines.delay(1_500)
        if (!nonLuVoulu) runCatching { engine.markRead(channel.id) }
    }

    // the attachment sheets live here and not in the composer, see `onPlus`
    var feuilleJointes by remember(channel.id) { mutableStateOf(false) }
    var feuilleSondage by remember(channel.id) { mutableStateOf(false) }

    val choisirPhotos = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                // sequential sending, and that is what keeps the chosen order
                //
                // launching the uploads in parallel would make them arrive in whatever order the
                // server accepts them, which is to say by size, and the small photo would overtake
                // the big one. the selection order is an intention
                uris.forEach { uri ->
                    val fichier = copierPourEnvoi(contexte, uri) ?: return@forEach
                    runCatching { engine.sendMedia(channel.id, fichier.absolutePath) }
                }
            }
        }
    }

    // the camera: you take a photo and send it, without going through the gallery. the camera
    // writes into a cache file we hand it, hence the `FileProvider` (the `envois/` folder), a
    // `content://` being the only thing another app is allowed to write into ours
    var photoEnCours by remember { mutableStateOf<java.io.File?>(null) }
    val prendreUnePhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { reussie ->
        val fichier = photoEnCours
        photoEnCours = null
        if (reussie && fichier != null && fichier.length() > 0) {
            scope.launch { runCatching { engine.sendMedia(channel.id, fichier.absolutePath) } }
        } else {
            runCatching { fichier?.delete() }
        }
    }
    val demanderCamera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { accorde -> if (accorde) photoEnCours?.let { prendreUnePhoto.launch(uriDEnvoi(contexte, it)) } }

    fun photographier() {
        val fichier = java.io.File(
            contexte.cacheDir.resolve("envois").apply { mkdirs() },
            "photo-${System.currentTimeMillis()}.jpg",
        )
        photoEnCours = fichier
        // the permission is only requested when the manifest declares it and it is missing: plenty
        // of devices already grant it for video calls
        val accordee = androidx.core.content.ContextCompat.checkSelfPermission(
            contexte, android.Manifest.permission.CAMERA,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (accordee) prendreUnePhoto.launch(uriDEnvoi(contexte, fichier))
        else demanderCamera.launch(android.Manifest.permission.CAMERA)
    }

    // `OpenDocument` and not `GetContent`. the first returns a durable uri you can ask for a stream
    // again, and the second returns a token that can expire before a slightly long upload finishes.
    // for a two megabyte photo the difference does not show, and for a two hundred megabyte video
    // it decides whether the send completes
    val choisirFichier = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val fichier = copierPourEnvoi(contexte, uri) ?: return@launch
                runCatching { engine.sendMedia(channel.id, fichier.absolutePath) }
            }
        }
    }

    // the room details screen. opened from the header's title, like on the reference: the room's
    // name is what you touch to find out what it holds
    var detailsOuverts by remember(channel.id) { mutableStateOf(false) }

    // read here and not in the details screen: `LocalOuvrirMedia` is provided by the shell, and the
    // clipboard is a platform dependency a display component has no business fetching itself
    val ouvrirMediaDepuisEntete = LocalOuvrirMedia.current
    val presseEntete = copieur()

    // the pinned messages with their bodies, through a focused timeline the engine builds
    //
    // collected only while the details screen is open: this flow opens a timeline sdk side, and
    // keeping it alive for a closed screen would be a subscription paid for nothing. closing the
    // screen cancels the effect, which closes the timeline
    /**
     * the room's threads, collected while the details screen is open
     *
     * the same policy as the pins: this flow holds a service sdk side, and keeping it alive for a
     * closed screen would be a subscription paid for nothing
     */
    var filsDuSalon by remember(channel.id) {
        mutableStateOf<List<li.velvetchat.core.model.Fil>>(emptyList())
    }
    LaunchedEffect(channel.id, detailsOuverts) {
        if (!detailsOuverts) return@LaunchedEffect
        try {
            engine.filsFlow(channel.id).collect { filsDuSalon = it }
        } catch (annulation: kotlinx.coroutines.CancellationException) {
            throw annulation
        } catch (t: Throwable) {
            android.util.Log.w("Velvet", "Fils illisibles pour ${channel.id}", t)
        }
    }

    var filOuvert by remember(channel.id) {
        mutableStateOf<li.velvetchat.core.model.Fil?>(null)
    }
    var messagesDuFil by remember(filOuvert) { mutableStateOf<List<Message>>(emptyList()) }
    LaunchedEffect(filOuvert) {
        val fil = filOuvert ?: return@LaunchedEffect
        try {
            engine.filFlow(channel.id, fil.racine.id).collect { messagesDuFil = it }
        } catch (annulation: kotlinx.coroutines.CancellationException) {
            throw annulation
        } catch (t: Throwable) {
            android.util.Log.w("Velvet", "Fil illisible dans ${channel.id}", t)
        }
    }

    var epinglesMessages by remember(channel.id) { mutableStateOf<List<Message>?>(null) }

    /**
     * a failed pin read, said and not swallowed
     *
     * the first version wrapped the collection in a `runCatching` with no failure branch. that is
     * the silent failure family, recorded three times in this repo: a swallowed error does not
     * produce a visible fault, it produces an empty list indistinguishable from a room with no
     * pins. people then hunt the fault in the wrong place, which is exactly what happened
     */
    var epinglesEnErreur by remember(channel.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(channel.id, detailsOuverts) {
        if (!detailsOuverts) return@LaunchedEffect
        epinglesEnErreur = null
        try {
            engine.pinnedFlow(channel.id).collect { epinglesMessages = it }
        } catch (annulation: kotlinx.coroutines.CancellationException) {
            // closing the screen cancels this effect: that is not a failure, and restarting it
            // would be a mistake. a `CancellationException` must always come back out
            throw annulation
        } catch (t: Throwable) {
            epinglesEnErreur = t.message ?: horsUi.lectureImpossibleMinuscule
            android.util.Log.w("Velvet", "Épingles illisibles pour ${channel.id}", t)
        }
    }

    // the room's pins, reread on open
    //
    // null, and not an empty list, when the read failed: the room does not let us read that state,
    // or the server did not answer. the distinction drives whether the pin action shows, which
    // disappears rather than being offered only to be refused
    var epingles by remember(channel.id) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(channel.id) {
        epingles = runCatching { engine.pinnedMessages(channel.id) }.getOrNull()
    }

    // one reply state: the sub bar, the target's highlight and the send button all depend on it
    var replyTo by remember(channel.id) { mutableStateOf<Message?>(null) }
    var mentionner by remember(channel.id) { mutableStateOf(true) }

    // replying and editing are mutually exclusive: both prepare the same text field, and letting
    // them coexist would mean deciding what sending does
    var enEdition by remember(channel.id) { mutableStateOf<Message?>(null) }

    // the long press target, and the y to anchor it at. the two are only worth anything together: a
    // target with no position would put the reaction bar anywhere
    var actionsSur by remember(channel.id) { mutableStateOf<Message?>(null) }
    var actionsY by remember(channel.id) { mutableStateOf(0) }
    // the conversation's origin in the window: the messages report their absolute position and the
    // overlay is placed in local coordinates. without this subtraction the bar would be offset by
    // the status bar's and the header's height
    var origineY by remember(channel.id) { mutableStateOf(0) }

    // the members are only loaded when the drawer opens
    //
    // `members()` asks the server, and doing it when every room opens would pay that cost for
    // something nobody looks at most of the time. the list is then reloaded on each open rather
    // than cached: somebody may have joined or left between two looks, and a stale list is worse
    // than a second's wait
    var tiroirMembres by remember(channel.id) { mutableStateOf(false) }
    var membres by remember(channel.id) { mutableStateOf<List<Member>>(emptyList()) }

    // typing indicators

    /** who is typing in this room, excluding yourself. the names, not the ids */
    var quiEcrit by remember(channel.id) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(channel.id, membres) {
        // guarded like the threads and the pins just above, and for the same reason brought here by
        // a real crash: on a room that is listed but not joined, this flow throws "unknown room",
        // from a `LaunchedEffect`, so on the main thread, so killing the app. a decoration must
        // never be able to do that
        try {
            engine.frappeFlow(channel.id).collect { identifiants ->
                quiEcrit = identifiants.map { id ->
                    membres.firstOrNull { it.userId == id }?.displayName
                        ?: id.removePrefix("@").substringBefore(':')
                }
            }
        } catch (annulation: kotlinx.coroutines.CancellationException) {
            throw annulation
        } catch (t: Throwable) {
            android.util.Log.w("Velvet", "Frappe illisible pour ${channel.id}", t)
        }
    }

    // the header's "N online" count
    //
    // it costs one request per member, because the sdk exposes no presence read, see
    // `MatrixEngine.presence`. that is sustainable here and nowhere else: a small server has about
    // ten people, and the engine keeps each answer for a minute. on a room with three hundred
    // members it would want another path, and this comment exists so nobody discovers that at the
    // time
    //
    // null means we do not know, and the subtitle then says nothing. zero and unknown do not get
    // conflated: the first says nobody is there and the second says the server does not say, and a
    // server with presence disabled would otherwise show "0 online" permanently
    var enLigne by remember(channel.id) { mutableStateOf<Int?>(null) }
    // the individual statuses are kept, not just their count
    //
    // the first version counted then threw them away. the member drawer then had to redo the same
    // tour, one request per person, to show the same dots. keeping the map costs nothing and
    // removes the second tour
    var presences by remember(channel.id) { mutableStateOf<Map<String, Presence>>(emptyMap()) }
    LaunchedEffect(channel.id) {
        val liste = runCatching { engine.members(channel.id) }.getOrNull() ?: return@LaunchedEffect
        // the list is kept here, and that was a real gap
        //
        // this effect already asked for the members to compute the presences, then threw the list
        // away, and a second effect asked for it again when the drawer opened. two requests for the
        // same answer, and above all every screen other than the drawer saw an empty list. that is
        // what showed "0 members" on the details screen, found on screen
        membres = liste
    }

    // presence keeps itself up to date
    //
    // it was read once when the room opened and never again: staying ten minutes in a conversation
    // showed an increasingly false "N online" with nothing saying so. the rate is deliberately not
    // here, it belongs to the engine, the only thing that knows how long it caches its presences
    // (see `presencesFlow`). the screen only says who it wants to be kept informed about
    //
    // resubscribed when the membership changes and not on every recomposition: the key is the list
    // of ids, compared by value
    val idsMembres = remember(membres) { membres.map { it.userId } }
    LaunchedEffect(channel.id, idsMembres) {
        if (idsMembres.isEmpty()) return@LaunchedEffect
        engine.presencesFlow(idsMembres).collect { lus ->
            val statuts = idsMembres.associateWith { id -> lus[id]?.presence ?: Presence.INCONNU }
            presences = statuts
            // if no status at all is known, presence is off server side, so we say nothing
            enLigne = if (statuts.values.all { it == Presence.INCONNU }) null
            else statuts.values.count { it == Presence.EN_LIGNE }
        }
    }
    // reread when one of the two surfaces showing members opens: somebody may have arrived or left
    // since. it is no longer the only load, see above, so both screens start from an already filled
    // list and merely refresh it
    LaunchedEffect(channel.id, tiroirMembres, detailsOuverts) {
        if (!tiroirMembres && !detailsOuverts) return@LaunchedEffect
        runCatching { engine.members(channel.id) }.onSuccess { membres = it }
    }

    // recomputed only when the list changes, never during scrolling
    val lignes = remember(messages) {
        calculerLignes(messages, tokens.density.messageGroupWindowMs)
    }

    val liste = rememberLazyListState()

    // load older history when approaching the top
    //
    // `MatrixEngine.loadMore` had existed from the start. no screen called it. the flow paginates
    // once on subscription and that was all: what you saw of a room was its first page, permanently
    //
    // on a private conversation of two hundred messages the initial page covers everything and the
    // fault is invisible. on the four hundred thousand events of the discord import, you see one
    // evening, the last, and scrolling up gives nothing. reported as the messages not being there:
    // they were, three days higher, out of reach
    //
    // the list is `reverseLayout`, so the past is towards the large indices. we ask for a page when
    // fewer than ten are left to show ahead
    //
    // `enCours` stops the burst: without it every scrolled frame would fire a request, and the sdk
    // would receive thirty for one wanted page. `fini` stops asking once the start of the
    // conversation is reached, without which we would query the void forever
    //
    // one loop, and not an effect restarted by its own state. the previous version had
    // `chargeEnCours` as its key: setting it true restarted the effect, which cancelled the load it
    // had just started, `runCatching` swallowed the cancellation, put it back to false, and it all
    // started again. the page never completed. the cancellation is now rethrown, never swallowed
    LaunchedEffect(channel.id) {
        while (true) {
            androidx.compose.runtime.snapshotFlow {
                liste.layoutInfo.totalItemsCount > 0 && run {
                    val dernier = liste.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    dernier >= liste.layoutInfo.totalItemsCount - 10
                }
            }.first { it }
            val reste = try {
                engine.loadMore(channel.id, 100)
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

    // two distinct situations, and conflating them was a real fault
    //
    // on opening we always go down: a room opens on its most recent message, not in the middle of
    // its history. the first version already applied the "only if you were already at the bottom"
    // condition here, a condition that means nothing when you are nowhere yet. the room then opened
    // at an arbitrary point, depending on how many events arrived in the flow's very first emission
    //
    // afterwards we only go down when already at the bottom, and without animation: tearing the
    // view away from somebody scrolling back through history is the opposite fault, and the
    // animation would compete with their gesture
    //
    // the list is anchored at the bottom (`reverseLayout`), and that is what settles the keyboard.
    // the fault: when the keyboard opened, the conversation was clipped at the bottom and the last
    // message went under it, and you had to scroll back by hand to find what you had just been
    // reading. the cause was not the keyboard but the anchoring, a list anchored at the top keeps
    // its first item in place when its height shrinks, so it eats from the bottom. a conversation
    // is read from the bottom, and that is the side it must hold on
    //
    // the consequence for indices: the render order is reversed and `0` is the most recent message.
    // "being at the bottom" therefore reads on the first visible indices, and going down means
    // going to index 0
    val ordreAffichage = remember(lignes) { lignes.asReversed() }
    // bring the target on screen. the focused timeline carries the target and its context, and
    // nothing guarantees it is visible: in a reversed list the opening happens at the bottom, so on
    // the most recent message of the context, not on the one you wanted to see. without this
    // scroll, jumping felt like doing nothing
    LaunchedEffect(cibleDuSaut, ordreAffichage) {
        val cible = cibleDuSaut ?: return@LaunchedEffect
        val rang = ordreAffichage.indexOfFirst { it.message.id == cible }
        if (rang >= 0) liste.animateScrollToItem(rang)
    }

    // scroll down when a message arrives, but only when already at the bottom: tearing the view
    // away from somebody scrolling back is the opposite fault. no initial position to force, the
    // bottom anchoring handles it, which the previous version had to make up for with a special
    // case
    LaunchedEffect(ordreAffichage.size) {
        if (ordreAffichage.isEmpty()) return@LaunchedEffect
        if (liste.firstVisibleItemIndex <= 2) liste.scrollToItem(0)
    }

    Box(
        modifier
            .onGloballyPositioned { origineY = it.positionInRoot().y.toInt() }
            // the drawer's gesture lives here, over the whole conversation, and not on each row
            //
            // the rows only claim leftward drags, to reply, and let the others through: a child
            // that does not consume an event lets it go up to its parent. that is what lets the two
            // gestures coexist with no arbiter, where a single detector would have had to guess the
            // intent
            .glisserTiroir(channel.id, onGlisser, onRelacher)
    ) {
    Column(
        // the theme's image, or the flat colour when there is none: see `fondConversation`
        Modifier.fillMaxSize().fondConversation(tokens.bg)
            // `imePadding` on the whole column and not on the input field alone
            //
            // placed on the field, the keyboard lifted it and left the list occupying the previous
            // height, so the last messages ended up under the keyboard. placed here, the whole
            // column shrinks, so the list does too, and since it is anchored at the bottom the last
            // message stays glued to the input
            .imePadding()
    ) {
        // 48 dp, and not whatever it happens to measure
        //
        // it was 66 dp: 12 dp of padding above and below a two line column. the reference fits in
        // 48, measured off its own screenshots, 98 px on a 448 dp wide screen. a third more, at the
        // top of the app's most visited screen, shows, and it got reported as the dm bar being far
        // too wide
        //
        // a fixed height rather than one fitted to the content: the "N online" subtitle does not
        // exist in a two person conversation, and a bar that changes size depending on the room
        // open makes everything below it jump on every navigation
        //
        // the person's nameplate as the bar's background, in a two person conversation. it is the
        // only ornament worn anywhere other than on a card, and a two person conversation is
        // exactly where you look at somebody rather than at a place. the member list already laid
        // it behind a row, and a dm's bar showed it nowhere
        //
        // `Crop` like everywhere else: a nameplate has a ratio (448 by 84), and stretching it
        // across a bar that does not share it deforms it
        val plaqueDuDm = interlocuteur?.userId
            ?.takeIf { estPrive }
            ?.let { LocalProfils.current[it]?.plaque }
        Box(Modifier.fillMaxWidth().height(48.dp).background(tokens.surface)) {
            plaqueDuDm?.let {
                Box(Modifier.matchParentSize()) {
                    EmojiEnLigne(
                        it,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
            }
            Row(
                Modifier.fillMaxSize().padding(horizontal = tokens.espM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // the chevron opens the drawer: the guaranteed path, for anybody who would not
                // guess the drag. a gesture must never be the only way to reach something. an arrow
                // and not a chevron: compared side by side with the reference, it is the bar's most
                // visible stroke, and a thin chevron reads as "expand" where an arrow reads as
                // "back"
                Icone(
                    Icones.retourFleche,
                    tint = tokens.textSecondary,
                    taille = 24.dp,
                    description = t.salons,
                    modifier = Modifier.clickable(onClick = onOuvrirTiroir).padding(end = 10.dp),
                )
                // a direct conversation carries the person's avatar and not a hash: you are writing
                // to somebody, not into a place
                if (estPrive) {
                    // the real photo, and the presence dot. the id being passed was the room's, so
                    // a fallback colour derived from the room and not from the person, and the
                    // avatar url was null although `DirectMessage` carries it. two fields right
                    // there, never wired
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Avatar(
                            interlocuteur?.userId ?: channel.id,
                            interlocuteur?.name ?: channel.name,
                            interlocuteur?.avatarUrl,
                            26.dp,
                        )
                        // the presence comes from `presences`, the map this screen already fills
                        // for the member list, and not from `DirectMessage.online`, which is
                        // declared and nothing fills: the dot would never have appeared
                        interlocuteur?.userId?.let { qui ->
                            Box(
                                Modifier.size(11.dp).clip(CircleShape).background(tokens.surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(7.dp).clip(CircleShape)
                                        .background(couleurDe(presences[qui] ?: Presence.INCONNU))
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                } else {
                    // the hash belongs to the name, it does not precede it
                    //
                    // it was dimmed grey and set at 22 dp with a margin, so it read as one more
                    // header icon beside the title. on the reference, the hash and the name form
                    // one block, same colour, same eye height, touching. that detail is what set
                    // the two bars apart
                    Icone(Icones.salon, tint = tokens.textPrimary, taille = 19.dp)
                    Spacer(Modifier.width(2.dp))
                }
                Column(
                    Modifier.weight(1f)
                        .clickable { detailsOuverts = true }
                        .padding(vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            channel.name,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = tokens.typography.headingSize.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // the chevron says this title opens. without it the details screen would
                        // only exist for whoever found it by accident, and it is already the sign
                        // used on the server header, so nothing new to learn
                        Icone(
                            Icones.suite,
                            tint = tokens.textMuted,
                            taille = 15.dp,
                            modifier = Modifier.padding(start = tokens.espXs),
                        )
                    }
                    // the reference's subtitle: a green dot and "N online". it only appears when
                    // the server answers, see `enLigne`. no "N online" in a one to one: there are
                    // two of you, and the dot on the avatar already says whether the other is
                    // there. the reference does not show it either, it is a room count
                    enLigne?.takeIf { !estPrive }?.let { combien ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(7.dp).clip(CircleShape)
                                    .background(couleurDe(Presence.EN_LIGNE))
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                t.enLigneNombre(combien),
                                color = tokens.textMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
                // calling, in a direct conversation only
                //
                // it used to be everywhere, including at the top of a server channel, and that
                // makes no sense: on the reference, voice is a room type and not a button placed
                // inside a text room. you join a voice channel by touching it in the list, and a
                // text channel has no call button. reported from the device, and rightly
                //
                // the direct conversation is the exception, and the reference does it too: a dm has
                // no voice room to go to, so a call has no door other than the header. that is
                // exactly what `estPrive` tells apart
                if (estPrive) BoutonAppel(channel.id, channel.name)
                // the magnifier, finally. it had been set aside during the cloning pass because
                // `/search` cannot see encrypted rooms, a button that would lead nowhere. the
                // search is local now, so it does lead somewhere: see `EcranRechercheSalon`, which
                // states its own scope
                Icone(
                    Icones.recherche,
                    tint = tokens.textSecondary,
                    taille = 22.dp,
                    description = t.chercherDansSalon,
                    modifier = Modifier
                        .clickable { rechercheOuverte = true }
                        .padding(start = 10.dp),
                )
                // the visible counterpart of the drag from the right. same reason as the chevron on
                // the left: a gesture must never be the only way to reach something
                Icone(
                    Icones.membres,
                    tint = tokens.textSecondary,
                    taille = 24.dp,
                    description = t.membres,
                    modifier = Modifier
                        .clickable { tiroirMembres = true }
                        .padding(start = 10.dp),
                )
            }
        }

        // the search covers the screen: it is a task in its own right and not a panel. you leave it
        // through the back gesture, possibly carrying a jump to a message with you
        if (rechercheOuverte) {
            PleinEcran {
            EcranRechercheSalon(
                nomDuSalon = channel.name,
                onChercher = { terme -> engine.chercherDansSalon(channel.id, terme) },
                onOuvrir = { id ->
                    // we reuse the jump to a message already written for the quotes: a search
                    // result and a quote ask exactly the same question, show me this message in its
                    // context
                    cibleDuSaut = id
                    rechercheOuverte = false
                },
                onFermer = { rechercheOuverte = false },
            )
            }
        }

        // say that you are not reading the present. without this strip, a timeline centred on an
        // old message is indistinguishable from a conversation that stopped there, and you would
        // wait for the next messages, which would never come
        androidx.compose.animation.AnimatedVisibility(visible = cibleDuSaut != null) {
            Row(
                Modifier.fillMaxWidth().background(tokens.accent.copy(alpha = 0.16f))
                    .clickable { cibleDuSaut = null }
                    .padding(horizontal = tokens.espL, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icone(Icones.chronique, tint = tokens.accent, taille = 16.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    t.vousRegardezPlusAncien,
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    t.revenirAuPresent,
                    color = tokens.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }

        // a skeleton while the conversation arrives
        //
        // there was none anywhere in the app, and it is most of what felt like slowness: an empty
        // screen is indistinguishable from a freeze. on a test account the timeline arrives in a
        // fraction of a second and the emptiness never shows, and on a loaded account it lasts,
        // with nothing saying it is working
        //
        // shapes and not a spinner: they announce what is coming, so the eye already settles where
        // the content will appear. and no continuous animation, the dimming is enough to say "not
        // here yet"
        if (messages.isEmpty() && erreurSalon == null) {
            SqueletteConversation(Modifier.weight(1f).fillMaxWidth())
        } else LazyColumn(
            state = liste,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // more air between the last message and the composer, like discord. the list being
            // `reverseLayout`, the visual bottom is this padding's `bottom`
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 18.dp),
            reverseLayout = true,
        ) {
            // nothing is computed here. everything below was decided once and for all in `lignes`
            // above, see `calculerLignes`'s comment: this body is re executed on every scrolled
            // frame
            items(ordreAffichage, key = { it.message.id }) { ligne ->
                // one single `Column`, and it is not about formatting
                //
                // the fault as it looked on screen: the day separator went under the first message
                // of that day, so between two messages of the same day. reading it, the
                // conversation looked out of order, and with no timestamps nothing let you check it
                // was only a misplaced label
                //
                // the cause is in `LazyColumn(reverseLayout = true)`: when an `item`'s body emits
                // several sibling composables, the list places those in reverse too, not only the
                // items between themselves. so the separator written first ended up drawn last
                //
                // wrapping them in a `Column` does one thing: the item has only one child to place,
                // and the internal order goes back to the code's
                Column {
                if (ligne.separateurJour) DaySeparator(ligne.libelleJour)
                // the "new messages" separator draws before the message carrying it: it is a
                // position between two events, not an event
                if (ligne.message.isFirstUnread) NewMessagesSeparator()

                // a system event is not a message and must not take a message's room. it went
                // through `MessageRow`, so with a forty point avatar, a bold coloured nickname and
                // a timestamp: "so and so joined" took three times the height of the sentence it
                // carries, and read as if somebody had just spoken. the reference puts it on a grey
                // line with an icon, and that is the right measure, since you want to be able to
                // skip it with your eyes
                //
                // it loses the long press and the swipe to reply at the same time. that is
                // deliberate: you do not reply to an arrival, and you do not react to a rename
                val corpsSysteme = ligne.message.body as? MessageBody.System
                if (ligne.chronique.isNotEmpty()) {
                    ChroniqueRepliee(ligne.chronique)
                } else if (corpsSysteme != null) {
                    LigneSysteme(corpsSysteme)
                } else MessageRow(
                    onAuteur = { id ->
                        // the card wants a `Member`, so we take the one from the already loaded
                        // list when it is there and build one from the message otherwise: an author
                        // who left the room is no longer in it, and looking at their card stays
                        // legitimate
                        onMembre(
                            membres.firstOrNull { it.userId == id } ?: Member(
                                userId = id,
                                displayName = messages.firstOrNull { m -> m.sender == id }
                                    ?.senderDisplayName,
                                avatarUrl = messages.firstOrNull { m -> m.sender == id }
                                    ?.senderAvatarUrl,
                                role = null,
                            )
                        )
                    },
                    onSauterVers = { cibleDuSaut = it },
                    onOuvrirFil = {
                        filOuvert = li.velvetchat.core.model.Fil(
                            racine = ligne.message,
                            nombreReponses = ligne.message.reponsesEnFil ?: 0,
                            derniere = null,
                        )
                    },
                    message = ligne.message,
                    galerie = ligne.galerie,
                    groupe = ligne.groupe,
                    horodatage = ligne.horodatage,
                    // highlighted like a reply target: arriving somewhere without knowing which of
                    // the visible messages you came for would make the jump useless in a dense
                    // conversation
                    cibleDeReponse = ligne.message.id == replyTo?.id ||
                        ligne.message.id == cibleDuSaut,
                    onVoter = { choix ->
                        scope.launch {
                            runCatching { engine.votePoll(channel.id, ligne.message.id, choix) }
                        }
                    },
                    onRepondre = { replyTo = ligne.message; enEdition = null },
                    // the same condition as the sheet's edit action: your own message, and a body
                    // the editor knows how to reopen. everywhere else the second threshold does not
                    // exist, rather than existing and doing nothing
                    onEditer = { enEdition = ligne.message; replyTo = null }
                        .takeIf {
                            ligne.message.isOwn && ligne.message.body is MessageBody.Text
                        },
                    onActions = { y -> actionsSur = ligne.message; actionsY = y },
                    onRenvoyer = {
                        scope.launch {
                            runCatching { engine.renvoyer(channel.id, ligne.message.id) }
                        }
                    },
                    onToggleReaction = { emoji ->
                        scope.launch {
                            runCatching { engine.toggleReaction(channel.id, ligne.message.id, emoji) }
                        }
                    },
                )
                }
            }

            // why the history from before you arrived is unreadable
            //
            // placed last in a reversed list, so at the top of the screen: it is the first thing
            // you see scrolling back through a conversation you just joined
            //
            // without that sentence you find a run of grey padlocks and conclude the app is broken.
            // it is not: those messages' encryption keys were never shared with this device, and no
            // client will ever be able to read them. saying so beats letting people hunt
            if (ordreAffichage.lastOrNull()?.message?.body is MessageBody.Undecryptable) {
                item(key = "note-historique") { NoteHistorique() }
            }
        }

        // the sub bar unfolds, it does not appear
        //
        // it used to pop in at the end of the drag: the row springs back into place, and during
        // that movement a bar materialises abruptly above the composer. two gestures at the same
        // instant, one animated and the other not, and the second is the one you feel
        //
        // `derniereCible` keeps the target for the length of the exit: without it `replyTo` goes
        // null and the bar would empty of its content before it finished folding away
        val derniereCible = remember { mutableStateOf(replyTo) }
        if (replyTo != null) derniereCible.value = replyTo
        androidx.compose.animation.AnimatedVisibility(
            visible = replyTo != null,
            enter = androidx.compose.animation.expandVertically(
                animationSpec = tween(tokens.dureeRapide, easing = tokens.easingEntree),
            ) + androidx.compose.animation.fadeIn(
                animationSpec = tween(tokens.dureeRapide, easing = tokens.easingEntree),
            ),
            exit = androidx.compose.animation.shrinkVertically(
                animationSpec = tween(tokens.dureeRapide, easing = tokens.easingSortie),
            ) + androidx.compose.animation.fadeOut(
                animationSpec = tween(tokens.dureeRapide, easing = tokens.easingSortie),
            ),
        ) {
            derniereCible.value?.let { cible ->
                ReplyBar(
                    cible = cible,
                    mentionner = mentionner,
                    onMentionner = { mentionner = it },
                    onAnnuler = { replyTo = null },
                )
            }
        }

        // the same treatment for editing: it is the same bar in the same place, and only one of the
        // two being animated would be more noticeable than neither
        androidx.compose.animation.AnimatedVisibility(
            visible = enEdition != null,
            enter = androidx.compose.animation.expandVertically(
                animationSpec = tween(tokens.dureeRapide, easing = tokens.easingEntree),
            ) + androidx.compose.animation.fadeIn(
                animationSpec = tween(tokens.dureeRapide, easing = tokens.easingEntree),
            ),
            exit = androidx.compose.animation.shrinkVertically(
                animationSpec = tween(tokens.dureeRapide, easing = tokens.easingSortie),
            ) + androidx.compose.animation.fadeOut(
                animationSpec = tween(tokens.dureeRapide, easing = tokens.easingSortie),
            ),
        ) {
            EditBar(onAnnuler = { enEdition = null })
        }

        // this room's members, not the server's
        //
        // the distinction shows in a direct conversation, which belongs to no server: offering the
        // server's list there would offer to address people who do not read that conversation, and
        // whose mention would therefore notify nobody
        var membresDuSalon by remember(channel.id) {
            mutableStateOf<List<li.velvetchat.core.model.Member>>(emptyList())
        }
        LaunchedEffect(channel.id) {
            membresDuSalon = runCatching { engine.members(channel.id) }.getOrDefault(emptyList())
        }

        CompositionLocalProvider(LocalMembres provides membresDuSalon) {
        Composer(
            // the gif tab's "open the settings" button led nowhere: the parameter existed and the
            // conversation screen did not pass it
            onReglagesGif = onReglagesGif,
            enReponse = replyTo != null,
            onVocal = { enregistrement ->
                scope.launch {
                    runCatching {
                        engine.sendVoice(
                            channel.id,
                            enregistrement.fichier.absolutePath,
                            enregistrement.dureeMs,
                            enregistrement.waveform,
                        )
                    }
                }
            },
            // named, and not only in private: the reference writes "send a message in #channel"
            // everywhere. a composer that says where it is sending earns its keep mostly when two
            // conversations look alike on screen
            destinataire = if (estPrive) "@${channel.name}" else "#${channel.name}",
            quiEcrit = quiEcrit,
            onFrappe = { enCours ->
                // nothing is published unless the person accepted it. the test is here, as close to
                // the send as possible, rather than at display time: the send is what carries the
                // cost, not the display
                if (PrefsStatut.frappePubliee(contexte)) {
                    scope.launch { runCatching { engine.signalerFrappe(channel.id, enCours) } }
                }
            },
            mentionADeposer = mentionADeposer,
            onMentionDeposee = onMentionDeposee,
            onPlus = { feuilleJointes = true },
            // a chosen gif is downloaded and re sent from our own server, never sent as an address.
            // see `telechargerGif`: that is the layer of the trade that lasts, and the one tenor's
            // death made indisputable
            //
            // a gif goes out as a media, not as an address. it used to go out as a text message
            // containing a url. three faults at once, all reported: an arbitrary size, since
            // nothing carries the dimensions, a still image, since nothing says it is a gif, and a
            // link to a third party left in an encrypted room, which every reader will fetch from
            // the gif's host
            //
            // uploaded to our side and sent as an `m.image`: the mime type says `image/gif`, the
            // dimensions come from the file, and nobody calls a third party to read the
            // conversation any more
            //
            // the panel closes at once: it is the only immediate feedback, and a panel that stays
            // open invites a second tap
            onGif = { gif ->
                scope.launch {
                    runCatching {
                        val fichier = telechargerGif(contexte, gif.complet)
                            ?: error(tr("GIF illisible", "Unreadable GIF"))
                        engine.sendMedia(channel.id, fichier.absolutePath)
                        fichier.delete()
                    }.onFailure {
                        android.util.Log.w("Velvet", "GIF non envoyé", it)
                    }
                }
            },
            onAutocollant = { autocollant ->
                scope.launch {
                    runCatching { engine.envoyerAutocollant(channel.id, autocollant) }
                        // said, never swallowed: a sticker refused by the server leaves no trace in
                        // the conversation, so nothing would contradict the silence
                        .onFailure {
                            android.util.Log.w("Velvet", "Autocollant non envoyé", it)
                        }
                }
            },
            // the field is prefilled on entering edit mode and emptied on leaving. so the key of
            // the composer's internal `remember` follows the edited id
            texteInitial = (enEdition?.body as? MessageBody.Text)?.text ?: "",
            cle = enEdition?.id ?: "nouveau",
            onSend = { texte ->
                val cible = replyTo
                val edite = enEdition
                val salonsDuServeur = salonsPourMentions
                scope.launch {
                    runCatching {
                        // custom emoji and mentions both force us off the markdown path: their
                        // rendering is a tag no converter will produce. we only go there when there
                        // really are some in the text, since forcing html everywhere would lose
                        // ordinary markdown, bold and lists included
                        val sortant =
                            composerMessage(texte, emotes, membresDuSalon, salonsDuServeur)
                        // a local `val` rather than `sortant.html` repeated: the property comes
                        // from another module, so kotlin refuses the smart cast, since it could in
                        // theory change between the test and the read
                        val html = sortant.html
                        // `sortant.corps` and not `texte`, on all four branches. `composerMessage`
                        // substitutes the unicode shortcodes into `corps`, and only raises
                        // `modifie`, so only produces html, for emotes and mentions. a message
                        // containing nothing but a shortcode therefore fell into the fallback
                        // branch, which returned the original text: the message went out spelled as
                        // typed. seen on a device, invisible in tests because the tests call
                        // `composerMessage` and read `corps`, not what the caller does with it
                        when {
                            edite != null -> engine.edit(channel.id, edite.id, sortant.corps)
                            cible != null -> engine.reply(channel.id, cible.id, sortant.corps)
                            html != null -> engine.sendHtml(
                                channel.id,
                                sortant.corps,
                                html,
                                sortant.mentions,
                                sortant.mentionneToutLeSalon,
                            )
                            // writing brings you back to the present. the message does go into the
                            // room, and it would not appear in a timeline centred somewhere else,
                            // so you would think the send was lost. discord does the same
                            else -> {
                                cibleDuSaut = null
                                engine.send(channel.id, sortant.corps)
                            }
                        }
                    }
                }
                replyTo = null
                enEdition = null
            },
        )
        }
    }

    if (feuilleJointes) {
        FeuillePiecesJointes(
            onPhotos = {
                choisirPhotos.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts
                            .PickVisualMedia.ImageAndVideo,
                    )
                )
            },
            // `*/*` and not a list of types: restricting here means deciding on somebody's behalf
            // what they are allowed to send at all. the engine recognises the real type from
            // the file's header, not from its extension
            onAppareilPhoto = { photographier() },
            onFichier = { choisirFichier.launch(arrayOf("*/*")) },
            onSondage = { feuilleSondage = true },
            onFermer = { feuilleJointes = false },
        )
    }

    if (feuilleSondage) {
        FeuilleSondage(
            onCreer = { question, options, secret ->
                scope.launch {
                    runCatching { engine.createPoll(channel.id, question, options, secret) }
                }
            },
            onFermer = { feuilleSondage = false },
        )
    }

    actionsSur?.let { cible ->
        val presse = copieur()
        MessageActionsOverlay(
            message = cible,
            ancreYpx = (actionsY - origineY).coerceAtLeast(0),
            onFermer = { actionsSur = null },
            onReagir = { emoji ->
                scope.launch { runCatching { engine.toggleReaction(channel.id, cible.id, emoji) } }
            },
            onRepondre = { replyTo = cible; enEdition = null },
            onRepondreEnFil = {
                // the thread may not exist yet: we open it on this message, and the first send is
                // what creates it server side. nothing to prepare, the `m.thread` relation is
                // carried by the focused timeline and not composed by hand
                filOuvert = li.velvetchat.core.model.Fil(
                    racine = cible,
                    nombreReponses = 0,
                    derniere = null,
                )
            },
            onModifier = { enEdition = cible; replyTo = null },
            onSupprimer = {
                scope.launch { runCatching { engine.delete(channel.id, cible.id) } }
            },
            // null when reading the pins failed: the room does not let us write that state, or does
            // not answer. the action then disappears, see the sheet. neither pin nor link until the
            // server confirms: both name an event, and a message in the send queue does not have
            // one yet. see `exigerEventId` on the engine side, the pin would have written an
            // unresolvable value into shared state
            estEpingle = epingles?.contains(cible.id)
                ?.takeIf { cible.sendState == SendState.Sent },
            onEpingler = {
                val etaitEpingle = epingles?.contains(cible.id) == true
                scope.launch {
                    runCatching { engine.setMessagePinned(channel.id, cible.id, !etaitEpingle) }
                        // reread from the server rather than patched in place: the state event is
                        // authoritative, and a list corrected locally would lie if the write had
                        // been refused for want of power
                        .onSuccess {
                            epingles = runCatching { engine.pinnedMessages(channel.id) }.getOrNull()
                        }
                }
            },
            // the message just before the one aimed at, which is where the markers go back to.
            // read here because the screen has the list, where the engine would have to walk a
            // timeline to find a neighbour we already know
            onMarquerNonLu = {
                val avant = messages.indexOfFirst { it.id == cible.id } - 1
                val precedent = messages.getOrNull(avant)?.id
                nonLuVoulu = true
                scope.launch {
                    runCatching { engine.marquerNonLuDepuis(channel.id, precedent) }
                    onLectureChangee()
                }
            },
            onMarquerLu = {
                nonLuVoulu = false
                scope.launch {
                    runCatching { engine.marquerLuJusqua(channel.id, cible.id) }
                    onLectureChangee()
                }
            },
            onCopierLien = {
                scope.launch {
                    runCatching { engine.messageLink(channel.id, cible.id) }
                        .onSuccess { presse(it) }
                }
            },
        )
    }

    // the drawer sits above all the rest of the conversation, the action sheet included: it is a
    // layer, and a layer that went under something would no longer be a layer
    TiroirMembres(
        membres = membres,
        presences = presences,
        ouvertureDemandee = tiroirMembres,
        // the rail stays open behind the card, and that is what the reference does: you look at
        // several people in a row, and closing the list each time meant reopening it between each
        // one
        onMembre = { onMembre(it) },
        onFermer = { tiroirMembres = false },
    )

    // placed after the drawer, and the order is the point, the lesson of the sheets that opened
    // under the profile screen. this one is a full screen: nothing must pass over it, so it draws
    // last. placed after the details screen: it is opened from the details, so it has to draw
    // above. the same lesson about order
    androidx.compose.animation.AnimatedVisibility(
        visible = filOuvert != null,
        enter = androidx.compose.animation.slideInHorizontally { it } +
            androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally { it } +
            androidx.compose.animation.fadeOut(),
    ) {
        val fil = filOuvert
        if (fil != null) {
            PanneauGlissant(onFermer = { filOuvert = null }) {
            EcranFil(
                racine = fil.racine,
                messages = messagesDuFil,
                horodateur = remember { Horodateur() },
                onEnvoyer = { texte ->
                    scope.launch {
                        runCatching { engine.repondreDansFil(channel.id, fil.racine.id, texte) }
                    }
                },
                onFermer = { filOuvert = null },
            )
            }
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = detailsOuverts,
        enter = androidx.compose.animation.slideInHorizontally { it / 3 } +
            androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally { it / 3 } +
            androidx.compose.animation.fadeOut(),
    ) {
        PanneauGlissant(onFermer = { detailsOuverts = false }) {
        EcranDetailsSalon(
            channel = channel,
            messages = messages,
            epingles = epinglesMessages,
            epinglesAttendues = epingles?.size,
            epinglesEnErreur = epinglesEnErreur,
            fils = filsDuSalon,
            onOuvrirFil = { filOuvert = it; detailsOuverts = false },
            membres = membres,
            presences = presences,
            onMembre = { detailsOuverts = false; onMembre(it) },
            onOuvrirMedia = { message ->
                (message.body as? MessageBody.Media)?.let { corps ->
                    ouvrirMediaDepuisEntete(demandeDe(message, listOf(corps.media), 0))
                }
            },
            onCopierLien = { url ->
                presseEntete(url)
            },
            // the same pagination as the message list, called by the inventory rather than by
            // scrolling. one page at a time: the screen decides when to stop, not the engine
            onRemonter = { engine.loadMore(channel.id, 200) },
            onFermer = { detailsOuverts = false },
        )
        }
    }

    }
}

/**
 * the edit sub bar, twin of [ReplyBar]
 *
 * it exists because without it a prefilled field is indistinguishable from a forgotten draft.
 * cancelling is the only action offered: confirming goes through the send button, which is already
 * the learned gesture
 */
@Composable
private fun EditBar(onAnnuler: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().background(tokens.surfaceRaised)
            .padding(horizontal = tokens.espM, vertical = tokens.espS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            t.modificationDuMessage,
            color = tokens.textSecondary,
            fontSize = tokens.typography.captionSize.sp,
            modifier = Modifier.weight(1f),
        )
        Icone(
            Icones.fermer,
            tint = tokens.textMuted,
            taille = 18.dp,
            description = t.annuler,
            modifier = Modifier.clickable(onClick = onAnnuler).padding(start = 6.dp),
        )
    }
}

/** what a timeline row needs to know, decided once */
private data class LigneRendue(
    val message: Message,
    val groupe: Boolean,
    val separateurJour: Boolean,
    val libelleJour: String,

    /**
     * the time as it shows next to the nickname, "today at 13:29"
     *
     * formatted here and not at render time: `SimpleDateFormat` is not reentrant and costs money,
     * and the line next door already explains why nothing gets computed inside the `LazyColumn`'s
     * body. empty for a grouped message, which shows no header
     */
    val horodatage: String = "",

    /**
     * the images to render together, when this message opens a run of image sends
     *
     * empty in the ordinary case. the messages absorbed this way produce no row of their own, which
     * is what tells a gallery from a simple stack of images
     */
    val galerie: List<Message> = emptyList(),

    /**
     * the chronicle rows folded under this one, when several come in a row
     *
     * empty in the ordinary case, an isolated arrival stays an isolated row. the folding only
     * appears past [SEUIL_REPLI_CHRONIQUE], where the wall begins
     */
    val chronique: List<Message> = emptyList(),
)

/**
 * prepares the timeline once per list change, because scrolling stuttered: the first draft put two
 * computations inside the `LazyColumn` body. a `filterNot` over the whole list, in a block that
 * reads `messages` and so re-walks it on every change including mid scroll — and two
 * `Calendar.getInstance()` per message in the item lambda, which only runs for composed items,
 * precisely what a fast fling does continuously, so every arriving row allocated the platform's
 * most expensive date object twice
 *
 * neither depends on scrolling: with a constant list the result is constant, so it moves up into a
 * `remember` outside the `LazyColumn`. computing the local day avoids `Calendar` entirely, an
 * offset and a division being enough, the offset read per timestamp to stay right across a
 * daylight saving change
 *
 * measured? no, and that has to be said: `adb shell input` sends gestures to the focused app, and
 * this one sits in another user profile on the test device, so the swipes meant for measuring
 * landed in the recents and closed the task. these four fixes rest on reading the code
 */
private fun calculerLignes(messages: List<Message>, fenetreMs: Long): List<LigneRendue> {
    val format = java.text.SimpleDateFormat("d MMMM yyyy", localeCourante())

    // built once per computation, and that is the point. this instance carries the
    // `SimpleDateFormat`s and the current day, and allocating them per message cost real money
    // while scrolling. see `Horodateur`, where this logic was pulled out so the room details screen
    // dates its pins with the same format, rewriting it would have been the two paths problem
    val horodateur = Horodateur()
    fun jourLocal(ts: Long): Long = horodateur.jourLocal(ts)
    fun horodater(ts: Long): String = horodateur.horodater(ts)

    // the filtering precedes the render: hiding a body does not hide the row containing it, and we
    // got rows carrying a name and nothing under it. a deleted message disappears, like on the
    // reference, with no "message deleted" row left behind
    val visibles = messages.filterNot {
        it.body is MessageBody.Redacted || (it.body as? MessageBody.System)?.kind?.isNoise == true
    }

    val lignes = mutableListOf<LigneRendue>()
    var index = 0
    while (index < visibles.size) {
        val message = visibles[index]
        val precedent = visibles.getOrNull(index - 1)
        val changementDeJour = precedent == null ||
            jourLocal(precedent.timestamp) != jourLocal(message.timestamp)

        // a run of images from the same author, in the same window, becomes one gallery
        //
        // matrix has no multi image message: sending three photos produces three events. stacking
        // them as they come would give three large blocks to scroll past where the intent was "here
        // are my photos". so the regrouping happens here, in the same place and by the same rules
        // as the message grouping
        val suite = mutableListOf(message)
        if (estImage(message)) {
            var suivant = index + 1
            while (suivant < visibles.size &&
                estImage(visibles[suivant]) &&
                visibles[suivant].sender == message.sender &&
                visibles[suivant].timestamp - visibles[suivant - 1].timestamp <= fenetreMs &&
                jourLocal(visibles[suivant].timestamp) == jourLocal(message.timestamp)
            ) {
                suite += visibles[suivant]
                suivant++
            }
        }

        // the wall of arrivals and departures folds up
        //
        // seen on screen: twenty "so and so joined" lines followed by as many "left", between two
        // real conversations. each one is legitimate, it is the reference's vocabulary, and twenty
        // in a row do not get read, they get skipped, and skipping them skips whatever came after
        //
        // folded into one expandable row, the way element and slack do. the threshold is low,
        // three, because below that there is no wall, and a fold that hides two lines costs more
        // than it gives
        val suiteSysteme = mutableListOf<Message>()
        if (message.body is MessageBody.System) {
            var k = index
            while (k < visibles.size &&
                visibles[k].body is MessageBody.System &&
                jourLocal(visibles[k].timestamp) == jourLocal(message.timestamp)
            ) {
                suiteSysteme += visibles[k]
                k++
            }
        }
        if (suiteSysteme.size >= SEUIL_REPLI_CHRONIQUE) {
            lignes += LigneRendue(
                message = message,
                groupe = false,
                separateurJour = changementDeJour,
                libelleJour = if (changementDeJour) {
                    format.format(java.util.Date(message.timestamp))
                } else "",
                chronique = suiteSysteme.toList(),
            )
            index += suiteSysteme.size
            continue
        }

        val groupe = !changementDeJour && continueLeGroupe(precedent, message, fenetreMs)
        lignes += LigneRendue(
            message = message,
            groupe = groupe,
            separateurJour = changementDeJour,
            libelleJour = if (changementDeJour) format.format(java.util.Date(message.timestamp)) else "",
            horodatage = if (groupe) "" else horodater(message.timestamp),
            galerie = if (suite.size > 1) suite else emptyList(),
        )
        index += suite.size
    }
    return lignes
}

/** an image and nothing else: a video or a file does not go into a grid */
/**
 * how many consecutive chronicle rows it takes before folding
 *
 * three: below that there is no wall to hide, and a fold that hides two rows loses more information
 * than it saves
 */
private const val SEUIL_REPLI_CHRONIQUE = 3

internal fun estImage(message: Message): Boolean {
    val media = (message.body as? MessageBody.Media) ?: return false
    if (media.kind != MediaKind.IMAGE) return false
    // a gif is not a photo, so it does not get regrouped into a sheet
    //
    // reported: sending several in a row condensed them into one message. that was exact and it was
    // this regrouping, three gifs sent in a row met the gallery's conditions (same author, same
    // window, `m.image`) and became a grid of thumbnails. and a gallery thumbnail does not animate:
    // you sent three animations and got a sheet of three still images
    //
    // the gallery answers "here are my photos", and sending three gifs is three successive
    // reactions and not an album. so each keeps its row, and its movement
    return media.media.mimeType !in MIMES_ANIMES
}

/**
 * the image types that move, and that would lose the point of themselves as gallery thumbnails
 */
private val MIMES_ANIMES = setOf("image/gif", "image/webp", "image/apng", "image/avif")

/**
 * a day separator: centred text between two thin rules
 *
 * neutral by construction: neither accent nor danger. it is neither an action nor an error, only a
 * landmark
 */
@Composable
private fun DaySeparator(libelle: String) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espM, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(tokens.border))
        Text("  $libelle  ", color = tokens.textMuted, fontSize = tokens.typography.captionSize.sp)
        Box(Modifier.weight(1f).height(1.dp).background(tokens.border))
    }
}

/**
 * the reply sub bar, above the composer
 *
 * the "mention" toggle is independent of the reply relation: `m.in_reply_to` carries the structured
 * quote, and the mention is only a purely notifying `m.mentions`. turning one off must not break
 * the other
 */
@Composable
private fun ReplyBar(
    cible: Message,
    mentionner: Boolean,
    onMentionner: (Boolean) -> Unit,
    onAnnuler: () -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()
    Row(
        Modifier.fillMaxWidth().background(tokens.surfaceRaised)
            .padding(horizontal = tokens.espM, vertical = tokens.espS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            t.repondreA(
                cible.senderDisplayName
                    ?: cible.sender.substringAfter('@').substringBefore(':'),
            ),
            color = tokens.textSecondary,
            fontSize = tokens.typography.captionSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (mentionner) t.mentionsActivees else t.mentionsCoupees,
            color = if (mentionner) tokens.accent else tokens.textMuted,
            fontSize = tokens.typography.captionSize.sp,
            modifier = Modifier
                .clickable { onMentionner(!mentionner) }
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
        Icone(
            Icones.fermer,
            tint = tokens.textMuted,
            taille = 18.dp,
            description = t.annulerReponse,
            modifier = Modifier.clickable(onClick = onAnnuler).padding(start = 6.dp),
        )
    }
}

@Composable
private fun NewMessagesSeparator() {
    // the reference's divider, shared with the desktop
    SeparateurNouveau(Modifier.padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp))
}

/**
 * one timeline row, with its gestures
 *
 * long press gives the two action surfaces. swiping left replies. swiping right goes back to the
 * channel list
 *
 * why replying goes left: on a phone in gesture navigation, dragging right is already the back
 * gesture, and it is also what opens the channel drawer. one movement cannot mean two things
 *
 * what this row does with rightward drags: nothing, and that is deliberate. it does not consume
 * them, which lets them go up to the conversation, where they drive the drawer. that is why the
 * detector is hand written rather than using `detectHorizontalDragGestures`: that one claims the
 * gesture as soon as the threshold is crossed, in either direction, and the parent then never saw a
 * drag that began on a message, which is to say nearly everywhere in a conversation
 *
 * the reply drag is bounded. without a bound you could pull the row to the edge of the screen,
 * which would suggest something else happens beyond
 *
 * on your own messages something else does happen beyond: at twice the threshold the chevron
 * becomes a pencil and releasing opens the edit instead of the reply. that is the reference's
 * gesture, and it is exactly where people look for it, since replying to your own message is rare
 * and correcting it much less so. so the bound is further out on those rows and identical
 * elsewhere: on somebody else's message, pulling further must still promise nothing
 */
@Composable
private fun MessageRow(
    message: Message,
    galerie: List<Message> = emptyList(),
    groupe: Boolean = false,
    horodatage: String = "",
    cibleDeReponse: Boolean = false,
    onVoter: (List<String>) -> Unit = {},
    onRepondre: () -> Unit = {},
    onActions: (Int) -> Unit = {},
    onToggleReaction: (String) -> Unit = {},
    /** go and see the message this one quotes. no effect when it quotes none */
    onSauterVers: (String) -> Unit = {},
    onOuvrirFil: () -> Unit = {},
    /**
     * touching the avatar opens its author's card
     *
     * it did nothing, and it is the most expected gesture in a timeline: the avatar is the only
     * thing on screen that names a person rather than a message. without it, getting to know
     * somebody met in a conversation meant opening the member list and finding them in it
     */
    onAuteur: (String) -> Unit = {},
    /**
     * puts this message into edit mode. null when it is not editable, somebody else's message, or a
     * body the editor cannot reopen. the drag's second threshold then disappears entirely, bound
     * included
     */
    onEditer: (() -> Unit)? = null,
    /**
     * retry a send the queue gave up on. null everywhere else, since a message still on its way has
     * nothing to offer
     */
    onRenvoyer: (() -> Unit)? = null,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val densite = LocalDensity.current
    val haptique = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val ouvrirMedia = LocalOuvrirMedia.current

    // "this message is talking to me". read from `m.mentions`, never guessed from the text: looking
    // for your own name in the body would confuse "papa replied" with "@papa", and above all would
    // highlight where the server sent no notification. the strip has to say the same thing as
    // whatever made the phone ring
    val moi = LocalMonIdentifiant.current
    val minterpelle = remember(message.id, moi) {
        !message.isOwn &&
            (message.mentionneToutLeSalon || (moi != null && moi in message.mentions))
    }

    val seuil = with(densite) { tokens.density.swipeReplyThresholdDp.dp.toPx() }
    // twice the reply threshold: far enough that you do not reach it aiming for the other one,
    // close enough that a thumb gets there without letting go of the phone
    val seuilEdition = seuil * 2f
    val borne = if (onEditer != null) seuilEdition * 1.2f else seuil * 1.5f
    val decalage = remember(message.id) { Animatable(0f) }
    var franchi by remember(message.id) { mutableStateOf(false) }
    var franchiEdition by remember(message.id) { mutableStateOf(false) }
    // the message's position in the window: it is what anchors the quick reactions bar on the
    // targeted row, and it is only known after placement
    var yRacine by remember(message.id) { mutableStateOf(0) }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { yRacine = it.positionInRoot().y.toInt() }) {
        // the chevron revealed behind the row, on the side it comes from: its opacity follows the
        // distance travelled, so the gesture explains itself within the first few millimetres. the
        // pictogram says what releasing will do, and it changes at the moment the action changes.
        // keeping the chevron past the second threshold would make editing a surprise, which a
        // gesture must never be
        val versEdition = onEditer != null && -decalage.value >= seuilEdition
        Icone(
            if (versEdition) Icones.modifier else Icones.repondre,
            tint = tokens.accent,
            taille = 22.dp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = tokens.espL)
                .alpha((-decalage.value / seuil).coerceIn(0f, 1f)),
        )

        Column(
            Modifier.fillMaxWidth()
                .offset { IntOffset(decalage.value.roundToInt(), 0) }
                // the message targeted by a reply in progress takes a temporary background.
                // `surfaceRaised` already says "this element is brought forward" elsewhere in the
                // app, and giving it the same meaning here avoids a second way of saying the same
                // thing
                //
                // the group spacing as a margin and not as padding. placed here, before the
                // background, it is not painted: it is black between two groups. it used to live in
                // the padding below, so inside the background, and a row where you are mentioned
                // had its amber strip stretch twelve points upwards. measured against a reference
                // screenshot: 131 px of strip here against 106 there, for the same row. the colour
                // had nothing to do with it
                .padding(
                    top = if (groupe) 0.dp
                    else (tokens.density.messageGroupSpacing -
                        tokens.density.messageVerticalPadding).dp
                )
                // `then` rather than a transparent background: a transparent `background` still
                // allocates a draw node for every row of the timeline
                .then(
                    when {
                        cibleDeReponse -> Modifier.background(tokens.surfaceRaised)
                        // gold, and not blue. reported in use, and the reference is unambiguous:
                        // its mention glow is amber. blue was wrong twice over, it is the accent
                        // hue, so the one used by links, buttons and the selected channel row. a
                        // "you are being spoken to" row therefore had no colour of its own
                        //
                        // `mentionHighlight` is the muted background already defined in the theme,
                        // and the edge reuses `warning`, the same family in a brighter shade. the
                        // edge is what makes the row spottable while scrolling without having to
                        // read it
                        minterpelle -> Modifier
                            .background(tokens.mentionHighlight)
                            .drawBehind {
                                drawRect(
                                    color = tokens.mentionBar,
                                    size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height),
                                )
                            }
                        // nothing to paint when an image passes underneath: otherwise every
                        // row lays down its own flat colour and the image only shows between
                        // them, in stripes. seen on screen
                        else -> if (fondEcranPose()) Modifier else Modifier.background(tokens.bg)
                    }
                )
                .pointerInput(message.id) {
                    detectTapGestures(onLongPress = { onActions(yRacine) })
                }
                .pointerInput(message.id) {
                    val seuilDeprise = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val doigt = awaitFirstDown(requireUnconsumed = false)
                        var cumul = 0f
                        // the vertical accumulation, which was missing, and that was the whole
                        // scrolling fault
                        //
                        // this detector only watched the horizontal. and a thumb does not scroll
                        // straight, it pivots around its joint, so an upward swipe drifts several
                        // millimetres to the left on the way. that drift was enough to cross the
                        // threshold, and from there every event was consumed here, the `LazyColumn`
                        // saw nothing come through and stopped dead. the scrolling felt braked
                        // because it was: it was being stolen, mid travel, by the reply
                        //
                        // the remedy is the direction locking every container that competes for a
                        // gesture does: the first axis to cross the threshold takes the gesture,
                        // and the other does not get it back
                        var cumulVertical = 0f
                        var aNous = false

                        while (true) {
                            val evenement = awaitPointerEvent()
                            val suivi = evenement.changes.firstOrNull { it.id == doigt.id } ?: break
                            if (!suivi.pressed) break
                            // somebody else took the gesture, usually the vertically scrolling
                            // list. we withdraw
                            if (suivi.isConsumed && !aNous) break

                            val dx = suivi.positionChange().x
                            if (!aNous) {
                                cumul += dx
                                cumulVertical += suivi.positionChange().y
                                // the vertical first: it crossed the threshold, so the gesture is a
                                // scroll. we withdraw without consuming anything, and for good,
                                // since coming back mid travel would make the grab unpredictable
                                if (kotlin.math.abs(cumulVertical) >= seuilDeprise) break
                                // to the right: we abandon this gesture for good without consuming
                                // anything, so the drawer can take it
                                if (cumul >= seuilDeprise) break
                                if (cumul > -seuilDeprise) continue
                                // the horizontal threshold is crossed, and the horizontal still has
                                // to dominate: a firmly diagonal gesture is a badly aimed scroll,
                                // not a reply
                                if (kotlin.math.abs(cumul) < kotlin.math.abs(cumulVertical)) break
                                aNous = true
                            }

                            suivi.consume()
                            scope.launch {
                                decalage.snapTo((decalage.value + dx).coerceIn(-borne, 0f))
                            }
                            // the haptic fires on crossing, so before release: you can still back
                            // out by coming back
                            if (!franchi && decalage.value <= -seuil) {
                                franchi = true
                                haptique.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            // a second one when it turns into an edit: that is a change of action,
                            // not progress, and the finger has to feel it
                            if (onEditer != null && !franchiEdition &&
                                decalage.value <= -seuilEdition
                            ) {
                                franchiEdition = true
                                haptique.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (franchiEdition && decalage.value > -seuilEdition) {
                                franchiEdition = false
                            }
                        }

                        if (aNous) {
                            // the furthest wins: under the second threshold you reply, past it you
                            // edit. never both, they are two different bars
                            val editer = onEditer
                            if (editer != null && decalage.value <= -seuilEdition) editer()
                            else if (decalage.value <= -seuil) onRepondre()
                            franchi = false
                            franchiEdition = false
                            // an elastic return rather than a snap: that is what tells "released
                            // before the threshold" from "nothing happened"
                            scope.launch { decalage.animateTo(0f, tokens.ressort()) }
                        }
                    }
                }
                .padding(
                    start = tokens.espM,
                    end = tokens.espM,
                    // what stays inside the background: the ordinary padding, the same whether a
                    // message heads a group or not. the group spacing sits higher up the chain, so
                    // above the background
                    top = tokens.density.messageVerticalPadding.dp,
                    bottom = tokens.density.messageVerticalPadding.dp,
                )
        ) {
        Column(Modifier.fillMaxWidth()) {
        // the quote is above the avatar, not beside it
        //
        // it lived in the text column, so at the avatar's height, and the elbow line came down to a
        // twenty point stub in front of it. on the reference, measured, the avatar is aligned with
        // the replying message, the quote sits over it, and the line bridges the two: it descends
        // from the middle of the quote to the top of the avatar
        //
        // that descent is what says "this comes from above and comes back down here". without it
        // the elbow connects nothing, it decorates
        message.citation?.let { citation ->
            CitationDeReponse(
                citation = citation,
                gouttiere = tokens.density.messageAvatarSize.dp,
                onSauter = { onSauterVers(citation.eventId) },
            )
        }
        Row(Modifier.fillMaxWidth()) {
            // the avatar only appears at the head of a group, and its place is always reserved: the
            // following messages keep the gutter, so the text column does not move from one row to
            // the next. without that reservation, a group would read as a run of misaligned
            // paragraphs
            val gouttiere = tokens.density.messageAvatarSize.dp
            if (!groupe) {
                Avatar(
                    userId = message.sender,
                    displayName = message.senderDisplayName,
                    avatarUrl = message.senderAvatarUrl,
                    taille = gouttiere,
                    modifier = Modifier.clickable { onAuteur(message.sender) },
                )
            } else {
                Spacer(Modifier.width(gouttiere))
            }
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
        // the quote, above the reply
        //
        // what was missing: the message did carry `replyTo`, which is to say its target's id, and
        // an event id does not display. on screen a reply was therefore an ordinary message. in a
        // two person conversation that passes, and with three you see people replying to nothing in
        // particular, and the thread becomes unreadable
        //
        // it is rendered before the header, which is the reference's order: you read what is being
        // quoted first, then who is replying. the other way round would make you backtrack. inside
        // a group only the first message carries the nickname, and the following ones show only
        // their body, which is the whole point of grouping
        if (!groupe) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                message.senderDisplayName ?: message.sender,
                // the role's colour beats the accent: that is what lets you spot who is in charge
                // without opening the member list. failing a coloured role we fall back on the
                // accent, which at least tells a nickname from a body of text. the person's
                // gradient when they have one, their role's colour otherwise. name styles promise
                // "everywhere it shows", and this is where that is decided, the timeline being
                // where a nickname gets written a thousand times a day
                color = LocalRoleColors.current[message.sender] ?: tokens.accent,
                style = brosseDuPseudo(message.sender)?.let {
                    androidx.compose.ui.text.TextStyle(brush = it)
                } ?: androidx.compose.ui.text.TextStyle.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            // the role badge, between the nickname and the time, the place it occupies on the
            // reference. it does not replace the nickname's colour: that reads at a glance while
            // scrolling, and the badge reads when you stop. the two say the same thing at two
            // reading distances
            //
            // the server tag first, the tier after. they are two different things and the reference
            // only shows one here: the tag, which is chosen. the tier, "can moderate in this room",
            // is computed, and it keeps its place where power matters, a member's card and the
            // member list
            tagDe(LocalProfils.current[message.sender])?.let { tag ->
                Spacer(Modifier.width(6.dp))
                BadgeTag(tag)
            }
            LocalRoleTiers.current[message.sender]?.let { palier ->
                Spacer(Modifier.width(6.dp))
                BadgeRole(palier)
            }
            // the time, next to the nickname. it was simply missing
            //
            // its absence does not show row by row, you read what just arrived and you know when it
            // was. it shows when you try to reconstruct an order: two messages sent two minutes
            // apart in two rooms then had no way at all of being compared, and a doubt about the
            // display order became impossible to settle from the screen
            if (horodatage.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(horodatage, color = tokens.textMuted, fontSize = 11.sp)
            }
            // the send state is shown, never hidden: a message that seems to disappear is the fault
            // we want to avoid
            when (val etat = message.sendState) {
                SendState.Sending -> {
                    Spacer(Modifier.width(6.dp))
                    Text(tr("envoi…", "sending…"), color = tokens.textMuted, fontSize = 10.sp)
                }
                is SendState.Failed -> {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (etat.recoverable) t.echecNouvelleTentative else t.echecAvecRaison(etat.reason),
                        color = tokens.danger,
                        fontSize = 10.sp,
                    )
                    // "resend" only appears on a definitive failure, and the nuance is not
                    // cosmetic: while the queue is still retrying, a button would suggest nothing
                    // happens without it. it only shows where the person really is the only one who
                    // can unblock things
                    //
                    // no extra feedback to design: pressing it puts the message back in the queue,
                    // so into `Sending`, so "sending…" replaces the failure line. the timeline's
                    // state is the confirmation
                    if (!etat.recoverable && onRenvoyer != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t.renvoyer,
                            color = tokens.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onRenvoyer() },
                        )
                    }
                }
                SendState.Sent -> Unit
            }
        }
        // a gallery replaces the message's body: the images composing it are rendered together, and
        // their captions make no sense separately. the useful caption is the first one's, which the
        // lightbox shows
        if (galerie.size > 1) {
            val refs = galerie.mapNotNull { (it.body as? MessageBody.Media)?.media }
            GalerieImages(refs) { position -> ouvrirMedia(demandeDe(message, refs, position)) }
        } else {
            MessageBodyView(
                body = message.body,
                onOuvrirImage = {
                    (message.body as? MessageBody.Media)?.media?.let { ref ->
                        ouvrirMedia(demandeDe(message, listOf(ref), 0))
                    }
                },
                confirme = message.sendState == SendState.Sent,
                onVoter = onVoter,
            )
        }
        // "(edited)" under the body, like the reference and the desktop. it used to be in the
        // header, and found in testing on the s21, a nickname plus tag plus tier plus time pushed
        // it off the screen, and a grouped message, which has no header, did not show it at all
        if (message.isEdited && message.body is MessageBody.Text) {
            Text(t.modifie, color = tokens.textMuted, fontSize = 10.sp)
        }

        // under the body, and the place is the point. it refers to that message, and placed above
        // it came before the author and the quote, so before you even knew which message was being
        // discussed. seen on screen
        //
        // without it a thread only exists in the tab that lists them: the conversation shows
        // nothing, and nobody knows a discussion opened under a message
        message.reponsesEnFil?.let { combien ->
            Row(
                Modifier.padding(top = 5.dp)
                    .clip(RoundedCornerShape(tokens.radiusSmall))
                    .background(tokens.surfaceRaised)
                    .clickable(onClick = onOuvrirFil)
                    .padding(horizontal = tokens.espS, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icone(Icones.messages, tint = tokens.accent, taille = 13.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    t.reponsesNombre(combien),
                    color = tokens.accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
        }
        // the pills live in `ui-commun`: the desktop shows them too, and two renderings of one
        // piece of data would have given two ways of counting and two colours for "i reacted"
        LigneReactions(message.reactions, onToggleReaction)
            }
        }
        }
        }
    }
}

/**
 * a reply's quote: the elbow line, the avatar, the nickname, the excerpt
 *
 * the elbow line is not an ornament. without it the quote looks like one more message, smaller,
 * which is what happens when you settle for putting the excerpt in grey. the elbow says "this comes
 * from above and comes back down here", so it says the relationship, which is the only thing this
 * row exists to say
 *
 * one line, truncated, and never two. an excerpt that wraps takes the replying message's room, and
 * inverts the hierarchy it just established
 *
 * the text is plain, with no html, see `Citation`: at that size you do not read, you recognise
 */
@Composable
private fun CitationDeReponse(
    citation: li.velvetchat.core.model.Citation,
    /** the width reserved for the replying message's avatar: the line centres on it */
    gouttiere: Dp,
    onSauter: () -> Unit = {},
) {
    val tokens = LocalTokens.current
    Row(
        // the chip becomes clickable, and that is the whole gesture: seeing what a reply replies to
        // used to be reserved to those whose target happened to still be on screen
        Modifier.fillMaxWidth()
            .clickable(onClick = onSauter)
            // the line, drawn behind the whole row, and not placed in front of it
            //
            // it was 20 dp wide and lived inside the row, to the left of the content: a quarter
            // circle followed by a dash, joining nothing. the reference's line leaves the middle of
            // this row, runs to the centre of the gutter, then goes down to the bottom, which is to
            // say to the top of the replying message's avatar, sitting just below. that descent is
            // what connects
            //
            // measured to the pixel: 2 dp thick, a dark grey, a 4 dp rounded elbow, the vertical
            // aligned on the avatar's centre
            .drawBehind {
                val trait = 2.dp.toPx()
                val rayon = 4.dp.toPx()
                val cx = gouttiere.toPx() / 2f
                val cy = size.height / 2f
                val chemin = androidx.compose.ui.graphics.Path().apply {
                    // from the bottom (the avatar's top) upwards, then the elbow to the right
                    moveTo(cx, size.height)
                    lineTo(cx, cy + rayon)
                    quadraticTo(cx, cy, cx + rayon, cy)
                    lineTo(gouttiere.toPx() + 10.dp.toPx(), cy)
                }
                drawPath(
                    chemin,
                    color = tokens.traitCitation,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = trait,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            }
            .padding(bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the line's space: it is painted underneath, so it only asks for room
        Spacer(Modifier.width(gouttiere + 10.dp))
        Avatar(citation.auteurId, citation.auteur, citation.auteurAvatarUrl, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            // the at sign, like on the reference. it is not decorative: without it the quoted
            // nickname and the excerpt form a continuous sentence you read as one line of text.
            // with it you see at once that the first half names somebody and the second reports
            // their words
            "@" + citation.auteur,
            color = LocalRoleColors.current[citation.auteurId] ?: tokens.textSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            citation.apercu,
            color = tokens.textMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * turns a message into a lightbox context
 *
 * one place does this translation: doing it at each call site would let what the viewer shows drift
 * depending on whether you came from a single image or a gallery, when it is the same information
 *
 * the caption falls back to empty when it is only the filename. the sdk fills it that way when
 * there is no text, and showing a camera filename under a photo teaches nothing
 */
private fun demandeDe(message: Message, refs: List<MediaRef>, position: Int): DemandeVisionneuse {
    val media = message.body as? MessageBody.Media
    val legende = media?.caption.orEmpty()
    return DemandeVisionneuse(
        medias = refs,
        index = position,
        userId = message.sender,
        auteur = message.senderDisplayName
            ?: message.sender.removePrefix("@").substringBefore(':'),
        avatarUrl = message.senderAvatarUrl,
        horodatage = message.timestamp,
        legende = if (legende == media?.media?.filename) "" else legende,
    )
}

@Composable
private fun MessageBodyView(
    body: MessageBody,
    onOuvrirImage: () -> Unit = {},
    /** confirmed by the server, see [CartePoll] */
    confirme: Boolean = true,
    onVoter: (List<String>) -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current
    when (body) {
        // the protocol's `formatted_body` was brought up by the engine and thrown away here: bold,
        // code, quotes and links arrived as flat text
        is MessageBody.Text -> {
            // a message that is only a gif link shows itself instead of being read
            //
            // that is the display consequence of sending gifs as links: the message is text, so it
            // drew as a blue link. `LienGif` decides on its own whether the address is loadable,
            // see its rule, which exists so the sender cannot name the host
            val enLigne = LocalGifsEnLigne.current
            val lien = remember(body.text, enLigne) {
                if (enLigne) li.velvetchat.core.gif.LienGif.dans(body.text) else null
            }
            // a failure falls back on the text: a vanished host at least leaves a readable address.
            // we degrade, we never hide
            var echec by remember(lien) { mutableStateOf(false) }
            if (lien != null && !echec) GifDuMessage(lien) { echec = true }
            else {
                CorpsFormate(texteBrut = body.text, html = body.html)
                // under the text, never instead of it: the preview completes the message, it does
                // not replace it, unlike the gif above whose link is only the image. a missing
                // preview leaves nothing visibly absent
                ApercuDuLien(body.text)
            }
        }

        // everything that can be shown gets shown. the previous comment said video and audio would
        // join the first category once they had a player, and they do: `LecteurVideo` and
        // `LecteurPdf` live in the viewer, which is the only place in the app that really covers
        // the window. see `Apercus.kt`
        is MessageBody.Media -> when (body.kind) {
            MediaKind.IMAGE -> Column {
                ImageDuMessage(body.media) { onOuvrirImage() }
                if (body.caption.isNotBlank() && body.caption != body.media.filename) {
                    Text(
                        body.caption,
                        color = tokens.textSecondary,
                        fontSize = tokens.typography.captionSize.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            MediaKind.VIDEO -> VideoDuMessage(body.media, body.caption) { onOuvrirImage() }
            MediaKind.AUDIO -> BulleVocale(body.media)
            // a pdf has a preview, its first page, and other files do not, and the card is then the
            // right display: it says what it is and what it weighs
            MediaKind.FILE ->
                if (body.media.estPdf()) PdfDuMessage(body.media, body.caption) { onOuvrirImage() }
                else FichierDuMessage("📎", body.media, body.caption)
        }

        MessageBody.Redacted ->
            Text(
                t.messageSupprimeMinuscule,
                color = tokens.textMuted,
                fontSize = tokens.typography.captionSize.sp,
            )

        // the case we refuse to hide: a silent gap would make people think something was lost
        is MessageBody.Undecryptable -> Text(
            when (body.reason) {
                UndecryptableReason.AVANT_NOTRE_ARRIVEE -> t.messageAvantArrivee
                UndecryptableReason.CLE_REFUSEE -> t.cleNonPartagee
                UndecryptableReason.CLE_MANQUANTE -> t.cleIntrouvable
                UndecryptableReason.OLM -> t.chiffrementIllisible
                UndecryptableReason.UNKNOWN -> t.dechiffrementImpossible
            },
            color = tokens.textMuted,
            fontSize = tokens.typography.captionSize.sp,
        )

        // state events, small and grey. the filtering already happened above, and here we only word
        // them in plain language rather than as an enum name
        is MessageBody.System -> Text(
            body.phrase(),
            color = tokens.textMuted,
            fontSize = tokens.typography.captionSize.sp,
        )

        is MessageBody.Poll -> CartePoll(body, confirme = confirme, onVoter = onVoter)

        is MessageBody.Unsupported ->
            Text("(${body.description})", color = tokens.textMuted, fontSize = 12.sp)
    }
}

/**
 * a role badge: glyph and name, in the tier's colour
 *
 * where it comes from: `RoleTier` already carried `name`, `colorArgb` and `icon`, and all we did
 * with it was tint a nickname. the chip on the reference screenshots is exactly the tier's name
 * placed next to the name, the data was there and the display was missing
 *
 * the background is the role's colour, heavily diluted, and the text that same colour at full
 * strength. a solid pill would pull the eye more than the nickname it qualifies, and plain coloured
 * text would merge into it. the diluted tint makes a distinct object without making a loud one, the
 * same technique as the mention highlight
 *
 * the glyph alone when there is one and room is short: on a timeline row, the tier's name competes
 * with the nickname and the time. the crown says the same thing in one character, and it is what
 * the member drawer already does
 */
@Composable
private fun BadgeRole(
    palier: li.velvetchat.core.model.RoleTier,
    avecNom: Boolean = false,
) {
    val tokens = LocalTokens.current
    val couleur = palier.colorArgb?.let { androidx.compose.ui.graphics.Color(it) }
        ?: tokens.textMuted
    Row(
        Modifier.clip(RoundedCornerShape(tokens.radiusSmall))
            .background(couleur.copy(alpha = 0.18f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        palier.icon?.let { glyphe ->
            Text(glyphe, fontSize = 9.sp, color = couleur)
            if (avecNom) Spacer(Modifier.width(3.dp))
        }
        // with no glyph, the name is all that is left, so we show it whatever happens, otherwise
        // the badge would be an empty pill
        if (avecNom || palier.icon == null) {
            Text(
                palier.name.uppercase(),
                color = couleur,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * a system event, on one row
 *
 * icon, sentence, time, all in `textMuted`, with no avatar and no nickname. it is a chronicle, not
 * somebody speaking: what you want is to be able to skip it with your eyes and find it again if you
 * look for it
 *
 * the icon changes with the event's nature rather than being a generic dot. an arrival, a kick and
 * encryption being switched on do not call for the same attention, and it is the only signal
 * telling them apart once the text is reduced to grey
 */
/**
 * several chronicle rows in a row, folded into one
 *
 * the label counts what it hides and spells it out, "14 arrivals and departures", because a plain
 * ellipsis does not say whether it is worth opening
 *
 * opened on tap and closable again: it is not information thrown away, only filed. the chronicle
 * stays the only trace of who came in and when, and that is sometimes exactly what people come
 * looking for
 */
@Composable
private fun ChroniqueRepliee(lignes: List<Message>) {
    val tokens = LocalTokens.current
    var ouverte by remember(lignes.firstOrNull()?.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { ouverte = !ouverte }
                .padding(horizontal = tokens.espL, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(tokens.density.messageAvatarSize.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icone(
                    if (ouverte) Icones.replier else Icones.suite,
                    tint = tokens.textMuted,
                    taille = 14.dp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                libelleChronique(lignes),
                color = tokens.textMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (ouverte) {
            lignes.forEach { m ->
                (m.body as? MessageBody.System)?.let { LigneSysteme(it) }
            }
        }
    }
}

/**
 * "14 arrivals and departures", "4 arrivals", "6 events"
 *
 * the label names what is inside rather than giving a bare count: you decide whether to open on
 * that sentence, and "14 events" does not let you decide
 */
private fun libelleChronique(lignes: List<Message>): String {
    val kinds = lignes.mapNotNull { (it.body as? MessageBody.System)?.kind }
    val arrivees = kinds.count { it == SystemKind.JOINED }
    val departs = kinds.count { it == SystemKind.LEFT }
    val n = lignes.size
    return when {
        arrivees > 0 && departs > 0 -> horsUi.arriveesEtDeparts(n)
        arrivees == n -> horsUi.arriveesNombre(n)
        departs == n -> horsUi.departsNombre(n)
        else -> horsUi.evenementsNombre(n)
    }
}

@Composable
private fun LigneSysteme(corps: MessageBody.System) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // reserved to the width of the avatar gutter, so the text column stays aligned with the
        // messages'. without it the chronicle shifts and the conversation looks like it is swaying
        Box(
            Modifier.width(tokens.density.messageAvatarSize.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icone(iconeSysteme(corps.kind), tint = tokens.textMuted, taille = 15.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            corps.phrase(),
            color = tokens.textMuted,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // no time on a chronicle row, and that is a trade rather than an oversight
        //
        // it was there first, pushed to the right. the measured result on screen: a room rename
        // sentence wrapped to make room for it, then got cut off mid word. we lost the room's name
        // to gain a minute nobody needs, since the day separator already gives the date and the
        // exact time of an arrival has never served anybody
    }
}

private fun iconeSysteme(kind: SystemKind): androidx.compose.ui.graphics.vector.ImageVector = when (kind) {
    SystemKind.JOINED, SystemKind.INVITE_ACCEPTED -> Icones.arrivee
    SystemKind.LEFT, SystemKind.INVITE_REJECTED -> Icones.depart
    SystemKind.INVITED, SystemKind.KNOCKED -> Icones.inviter
    SystemKind.KICKED, SystemKind.BANNED, SystemKind.INVITE_REVOKED -> Icones.exclusion
    SystemKind.UNBANNED -> Icones.valide
    SystemKind.ROOM_NAME_CHANGED, SystemKind.ROOM_TOPIC_CHANGED -> Icones.modifier
    SystemKind.ENCRYPTION_ENABLED -> Icones.chiffrement
    SystemKind.CALL -> Icones.micro
    else -> Icones.chronique
}

/**
 * the horizontal gesture that drives the drawer, placed everywhere it has to answer
 *
 * it only existed on the conversation, and that held as long as nobody thought past opening it: you
 * start from the conversation, you drag, the drawer comes. except the conversation is pushed off
 * screen by that same gesture, so when it came to closing, the only surface listening was no longer
 * there. the drawer opened by finger and only closed by tapping the strip that sticks out
 *
 * `cle` is `pointerInput`'s key: changing room has to restart the detector, otherwise it keeps the
 * previous room's closing
 *
 * velocity is measured on the distance travelled, never on the finger's position in the local
 * coordinate space. found on screen, and counterintuitive: a slow drag opened the drawer and a
 * flick did nothing. the cause is that the surface moves itself with the finger, so in its own
 * coordinate space the finger barely moves, and the faster the gesture the less it moves, since the
 * surface tracks it better. the measured velocity collapsed in exactly the case where it should
 * have been highest. accumulating the deltas depends on no coordinate space
 */
private fun Modifier.glisserTiroir(
    cle: Any?,
    onGlisser: (Float) -> Unit,
    onRelacher: (Float) -> Unit,
): Modifier = pointerInput(cle) {
    val vitesse = VelocityTracker()
    var parcouru = 0f
    detectHorizontalDragGestures(
        onDragStart = { parcouru = 0f; vitesse.resetTracking() },
        onDragEnd = { onRelacher(vitesse.calculateVelocity().x) },
        // a cancelled gesture has no velocity to pass on: it was not released, it was interrupted.
        // zero lets the distance decide alone
        onDragCancel = { onRelacher(0f) },
    ) { changement, delta ->
        parcouru += delta
        vitesse.addPosition(
            changement.uptimeMillis,
            androidx.compose.ui.geometry.Offset(parcouru, 0f),
        )
        onGlisser(delta)
    }
}

@Composable
private fun Composer(
    enReponse: Boolean = false,
    destinataire: String? = null,
    texteInitial: String = "",
    cle: String = "nouveau",
    onVocal: (Enregistrement) -> Unit = {},
    /**
     * the plus opens the attachments sheet, which does not live here
     *
     * it did live here for one version, and the screen broke: `FeuilleBasse` takes all the
     * available space, and placed in the composer's column it took its height, so the composer went
     * to the top of the screen and the conversation disappeared. a sheet is a layer: it has to sit
     * at the level that really covers the window, like the actions overlay and the viewer
     */
    onPlus: () -> Unit = {},
    /** who is typing in this room, excluding yourself. empty when nobody */
    quiEcrit: List<String> = emptyList(),
    /** signals, or stops signalling, that you are typing. see `PrefsStatut.frappePubliee` */
    onFrappe: (Boolean) -> Unit = {},
    /** a mention to insert at the cursor, from a member's card */
    mentionADeposer: String? = null,
    onMentionDeposee: () -> Unit = {},
    onGif: ((li.velvetchat.core.gif.Gif) -> Unit)? = null,
    /** a sticker was chosen, see `MatrixEngine.envoyerAutocollant` */
    onAutocollant: ((li.velvetchat.core.model.CustomEmote) -> Unit)? = null,
    onReglagesGif: () -> Unit = {},
    onSend: (String) -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()

    // android's photo picker, and not access to the gallery
    //
    // `PickMultipleVisualMedia` opens with no permission at all: the system shows the picker, the
    // person chooses, and the app only receives what they pointed at. the classic route, asking
    // permission to read all storage, would get far more than is needed to send three photos, and
    // would raise a prompt nobody wants to grant a personal messenger

    // the document picker, distinct from the photo one
    //
    // `OpenDocument` and not `GetContent`: the first returns a durable uri you can ask for a stream
    // again, and the second returns a token that can expire before a slightly long upload finishes.
    // for a two megabyte photo the difference does not show, and for a two hundred megabyte video
    // it decides whether the send completes

    // `cle` and not `texteInitial` as the `remember` key: on the text, any keystroke bringing the
    // value back to the original content would reset the field. the key has to say "we are editing
    // something else", not "the text looks like something else"
    //
    // `TextFieldValue` and not `String`, and that is not a typing detail
    //
    // measured on screen: after picking a suggestion, the next character landed at the start of the
    // field. a `BasicTextField` driven by a plain string is only told about text, and when that
    // text changes without anybody typing, the cursor stays where it was. picking a mention from
    // the list therefore left the cursor stuck after the original at sign, and the next keystroke
    // inserted there
    //
    // it is also this type that allows inserting at the cursor rather than at the end, which is
    // what anybody expects of an input field as soon as they come back to fix a word in the middle
    var champ by remember(cle) {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                texteInitial,
                androidx.compose.ui.text.TextRange(texteInitial.length),
            )
        )
    }
    // the mention arriving from a member's card, dropped at the cursor and not at the end: you may
    // well be in the middle of a sentence and want to slip somebody into it
    LaunchedEffect(mentionADeposer) {
        val qui = mentionADeposer ?: return@LaunchedEffect
        champ = remplacerAvantCurseur(champ, champ.text.take(champ.selection.start) + "@$qui ")
        onMentionDeposee()
    }

    // signalling that you are typing, and above all stopping signalling it
    //
    // the second part is the one people forget: with no expiry, "so and so is typing…" stays stuck
    // on everybody else's screen when you give up on your message. so we republish while the field
    // moves, and withdraw after a few seconds of silence. the sdk expires it on its side too, and
    // waiting for that would leave the notice up several seconds too long
    LaunchedEffect(champ.text) {
        if (champ.text.isBlank()) {
            onFrappe(false)
            return@LaunchedEffect
        }
        onFrappe(true)
        kotlinx.coroutines.delay(SILENCE_AVANT_ARRET_MS)
        onFrappe(false)
    }
    // leaving the room or closing the screen must also withdraw it: otherwise you stay "typing" for
    // everybody after you have gone
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onFrappe(false) } }

    val emotesDispo = LocalEmotes.current
    val membres = LocalMembres.current
    val salons = LocalSalons.current
    var pickerOuvert by remember { mutableStateOf(false) }
    var enregistre by remember { mutableStateOf(false) }
    val clavier = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusChamp = remember { androidx.compose.ui.focus.FocusRequester() }
    /** the composer's field has focus, see the panel closing effect below */
    var composeurFocalise by remember { mutableStateOf(false) }

    // the panel takes the keyboard's place, and letting them coexist would stack two input surfaces
    // and push the field off screen
    androidx.compose.runtime.LaunchedEffect(pickerOuvert) {
        if (pickerOuvert) clavier?.hide()
    }

    // the keyboard coming back closes the panel, and it is the only reliable signal
    //
    // reported from outside: opening the emoji menu then tapping the composer stacked the menu and
    // the keyboard. there was a `clickable` on the field's box for that, and `BasicTextField`
    // consumes the touch to place the cursor, so the outside click never fired. and the field keeps
    // focus while the panel is open, since we only hide the keyboard, so `onFocusChanged` says
    // nothing either
    //
    // what we want to detect is not the tap, it is the keyboard coming up, whatever the cause.
    // `WindowInsets.ime` says so unambiguously, and covers the physical keyboard and the paths we
    // did not foresee into the bargain
    //
    // `ime.getBottom(density) > 0` and not `isImeVisible`: that one is experimental and changes
    // package depending on the compose version, while the height is stable
    val densiteClavier = androidx.compose.ui.platform.LocalDensity.current
    val clavierVisible = WindowInsets.ime.getBottom(densiteClavier) > 0
    // but only when it is the composer calling it back
    //
    // the rule "the keyboard came up so we close" also closed the panel when the keyboard was
    // raised by a field inside that panel: touching the gif search opened the keyboard, the effect
    // closed the panel, and the search bar went with it. reported as the search bar being unusable
    // because the keyboard closed instantly
    //
    // focus discriminates here, where it said nothing for the original case: the composer's field
    // keeps focus while nothing else is touched, and the gif search takes it away. keyboard up plus
    // composer still focused means the composer is what got touched
    androidx.compose.runtime.LaunchedEffect(clavierVisible, composeurFocalise) {
        if (clavierVisible && pickerOuvert && composeurFocalise) pickerOuvert = false
    }
    // back closes the panel before leaving the room: otherwise you lose the conversation to close a
    // panel, and have to come back to it
    androidx.activity.compose.BackHandler(enabled = pickerOuvert) { pickerOuvert = false }

    // the permission is asked for at the moment of the gesture and not at startup: a prompt at
    // launch does not say what it is for, and gets refused all the more often
    val contexteMicro = androidx.compose.ui.platform.LocalContext.current
    val demanderMicro = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { accorde -> enregistre = accorde }

    // dictation: the text fills in live while you speak
    //
    // long press the mic to start it, and the short press keeps its original gesture, the voice
    // message. while dictating, the same button stops it. text already typed is kept: the dictation
    // writes after it, and the result stays editable before sending, nothing leaves on its own
    var dictee by remember { mutableStateOf<li.velvetchat.app.Dictee?>(null) }
    var avantDictee by remember { mutableStateOf("") }
    var finalisationDictee by remember { mutableStateOf(false) }
    // the dictation player: how long you have been speaking, whether it is paused, and what failed
    var debutDictee by remember { mutableStateOf(0L) }
    var pauseDictee by remember { mutableStateOf(false) }
    var erreurDictee by remember { mutableStateOf<String?>(null) }
    val moteurDictee = LocalEngine.current
    fun ecrireDictee(suite: String) {
        val texte = avantDictee + suite
        champ = androidx.compose.ui.text.input.TextFieldValue(
            texte,
            androidx.compose.ui.text.TextRange(texte.length),
        )
    }
    fun lancerDictee() {
        val jeton = runCatching { moteurDictee.currentSession().accessToken }.getOrNull() ?: return
        avantDictee = champ.text.let { if (it.isBlank() || it.endsWith(" ")) it else "$it " }
        debutDictee = System.currentTimeMillis()
        pauseDictee = false
        erreurDictee = null
        dictee = li.velvetchat.app.Dictee(
            url = li.velvetchat.core.Installation.ou({ this.dictee }, li.velvetchat.app.BuildConfig.DICTEE_URL),
            jeton = jeton,
            onTexte = { valide, provisoire ->
                ecrireDictee(listOf(valide, provisoire).filter { it.isNotBlank() }.joinToString(" "))
            },
            onFinalisation = { finalisationDictee = true },
            onFin = { texte -> ecrireDictee(texte); dictee = null; finalisationDictee = false },
            onErreur = { message ->
                // shown in the player, which stays: a toast goes away before anybody has read the
                // cause
                erreurDictee = message
                dictee = null
                finalisationDictee = false
            },
        ).also { it.demarrer() }
    }
    val demanderMicroDictee = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { accorde -> if (accorde) lancerDictee() }
    // leaving the room stops the dictation: the mic must never stay open behind the screen
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { dictee?.annuler() } }

    // the composer becomes a column: the suggestions bar sits above the field. `imePadding` stays
    // here, at the bottom, rather than on the column, since it is the field that has to stay above
    // the keyboard, and the bar follows it since it is glued to it
    if (enregistre) {
        // the bar replaces the composer rather than adding itself above: you are recording, there
        // is nothing to type. keeping the field visible would invite doing both at once, which the
        // send would not know how to resolve
        BarreEnregistrement(
            onAnnuler = { enregistre = false },
            onEnvoyer = { enregistrement -> enregistre = false; onVocal(enregistrement) },
        )
        return
    }

    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {

    // the dictation player, since there was not enough to say it was running. it says what is
    // happening, for how long, and carries the two gestures that were missing, pausing, and
    // stopping without hunting for the mic button
    LecteurDictee(
        enCours = dictee != null,
        enPause = pauseDictee,
        finalisation = finalisationDictee,
        erreur = erreurDictee,
        debut = debutDictee,
        onPause = { dictee?.let { it.enPause = !it.enPause; pauseDictee = it.enPause } },
        onTerminer = { dictee?.terminer() },
        onAnnuler = {
            dictee?.annuler()
            dictee = null
            finalisationDictee = false
            pauseDictee = false
            // we cancel: the dictated text goes with it
            champ = androidx.compose.ui.text.input.TextFieldValue(
                avantDictee,
                androidx.compose.ui.text.TextRange(avantDictee.length),
            )
        },
        onFermerErreur = { erreurDictee = null },
    )

    // the bar only appears when there is something to offer, and disappears afterwards: permanent,
    // it would eat a conversation line's height to say nothing most of the time. the autocomplete
    // only looks at what precedes the cursor: coming back to fix a word in the middle of a
    // sentence, it is that word you are completing and not the end of the message
    val avantCurseur = champ.text.take(champ.selection.start)
    val propositions = remember(avantCurseur, emotesDispo, membres, salons) {
        suggestions(avantCurseur, emotesDispo, membres, salons)
    }
    // the tone chosen in the picker holds here too: offering one emoji then inserting a different
    // tone from the one just seen would be the worst of both worlds
    val teintePeau = PrefsEmojis.teinte(LocalContext.current)
    // a vertical titled list, not a scrolling strip
    //
    // the horizontal strip was compared with the reference, screenshot against screenshot, and
    // discord's behaviour won. it only showed one suggestion, the others being off the right of the
    // screen, and nothing said there were others. you thought you had one result and did not look
    // further. the panel is shared with the desktop: two shapes for the same list meant two ways of
    // getting it wrong
    propositions?.let { (fragment, trouves) ->
        PanneauAutocompletion(
            fragment = fragment,
            declencheur = avantCurseur.getOrNull(avantCurseur.length - fragment.length - 1) ?: ':',
            propositions = trouves,
            teinte = teintePeau,
            onChoisir = { proposition ->
                champ = remplacerAvantCurseur(
                    champ, completer(avantCurseur, proposition, teintePeau),
                )
            },
        )
    }

    // above the input bar, and only while somebody is typing
    //
    // it does not take space permanently: the conversation is the content, and stealing a line from
    // it continuously for a state that is empty most of the time is a bad trade. so it pushes the
    // composer a few points when it appears, which is barely noticeable, unlike a jump in the
    // middle of the text you are reading
    if (quiEcrit.isNotEmpty()) LigneDeFrappe(quiEcrit)

    // one single block, and it is the measured fix from the gaps register
    //
    // the reference has no field flanked by buttons: it has one 342 by 48 dp pill holding the plus,
    // the text and the right hand icons. we had three separate objects on the screen's background,
    // a different silhouette on the app's most looked at element — so the pill is the row itself
    // and the children carry no background of their own
    //
    // it also floats rather than sitting on a bar. reported from outside, the reference's composer
    // being a floating bubble where ours was a nested box, and exact: the cause was one line, the
    // row's `background(tokens.surface)`, drawing a full width bar in one grey with a pill of
    // another grey cut out of it. two stacked surfaces where the reference has one
    //
    // so the row keeps only its margins, which are what make the pill float, and the colour moves
    // entirely to the pill. the three numbers come from a pixel measurement on a reference
    // screenshot (1008x2244 rescaled to 1344x2992 at 480 dpi): a pill 416 dp wide, so 16 dp of
    // margin each side, and 58 dp tall. ours was 427 by 44, wider and flatter, which is a bar
    // where the reference has a bubble
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = tokens.espL, vertical = tokens.espS)
            .clip(RoundedCornerShape(tokens.radiusLarge))
            .background(tokens.surfaceRaised)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the plus to the left of the field, like everywhere else: that is the learned position,
        // and putting it on the right would make it compete with the send button
        //
        // with no circle: the reference sets it bare. the circle we had made it a separate button
        // when it is part of the same block as the rest
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) {
            Icone(
                // `ajouter` and not `joindre`: the second is a circled plus, and the circle made it
                // a separate button when it belongs to the same block as the rest. the reference
                // sets a bare plus
                Icones.ajouter,
                tint = tokens.textSecondary,
                taille = 24.dp,
                description = t.joindreUnePhoto,
            )
        }
        Spacer(Modifier.width(2.dp))
        Box(
            // no background and no corners: the pill is the row around it. giving it one back would
            // draw a box inside a box
            Modifier.weight(1f).padding(horizontal = tokens.espXs, vertical = 10.dp)
                // the `clickable` that used to be here was removed, it never fired
                //
                // it was meant to close the panel when the field was tapped. but `BasicTextField`
                // consumes the touch to place the cursor, so the parent's click was never emitted,
                // and it stole part of the placement taps on the way. closing now happens on the
                // keyboard coming up, see `clavierVisible` above, which is the real signal
        ) {
            if (champ.text.isEmpty()) {
                Text(
                    when {
                        enReponse -> t.repondrePoints
                        // "message in #x" and not "send a message in #x"
                        //
                        // the reference writes the long form, and its composer takes the full
                        // width, while ours shares its width with two buttons. measured on screen:
                        // the long form got cut off right before the part that tells one
                        // conversation from another. a label that truncates the very thing it
                        // exists for is worse than the generic one it replaces
                        //
                        // "to" for a person and "in" for a place: that distinction comes from the
                        // reference, and it is right
                        destinataire?.startsWith("@") == true -> horsUi.messageA(destinataire)
                        destinataire != null -> horsUi.messageDans(destinataire)
                        else -> t.composerInvite
                    },
                    color = tokens.textMuted,
                    fontSize = tokens.typography.bodySize.sp,
                    // one line: the long form wrapped and doubled the composer's height before
                    // anybody had typed a character
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // a recognised shortcode gets coloured and the others do not
            //
            // you type a shortcode without knowing whether the catalogue has it, and the answer
            // arrives after sending, too late, as a message showing it spelled out. colouring while
            // typing moves the answer ahead of the mistake
            //
            // `OffsetMapping.Identity`: we change no character, only their colour. a transformation
            // that shifted positions would break the cursor and the selection, and that is
            // `VisualTransformation`'s classic trap
            //
            // both families are accepted, in the order `composerMessage` arbitrates: a server emoji
            // named `fire` beats the unicode one, so both colour the same. the colour says "this
            // will become an image", not which one
            val surlignage = remember(emotesDispo, tokens.accent) {
                val duServeur = emotesDispo.mapTo(HashSet()) { it.shortcode }
                VisualTransformation { texte ->
                    TransformedText(
                        buildAnnotatedString {
                            append(texte)
                            for (m in RACCOURCI.findAll(texte.text)) {
                                val nom = m.groupValues[1]
                                if (nom in duServeur || estRaccourciConnu(nom)) {
                                    addStyle(
                                        SpanStyle(color = tokens.accent),
                                        m.range.first,
                                        m.range.last + 1,
                                    )
                                }
                            }
                        },
                        OffsetMapping.Identity,
                    )
                }
            }
            BasicTextField(
                value = champ,
                onValueChange = { champ = it },
                visualTransformation = surlignage,
                // the field writes like the conversation. it did not, and the two gaps compounded:
                // `fontSize = 14.sp` where a message is at `bodySize` (16), and above all
                // `fontFamily = FontFamily.Default`, which overrode the app's font to put the
                // system's back. so you typed in a font and a size you did not find again once
                // sent, reported from outside with a discord screenshot to prove it
                //
                // `LocalTextStyle.current` already carries the right family (see `VelvetTheme`,
                // which sets it once for the whole app). starting from it rather than from a bare
                // `TextStyle()` is what stops the gap coming back: a field that inherits cannot
                // drift
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = tokens.textPrimary,
                    fontSize = tokens.typography.bodySize.sp,
                    lineHeight = tokens.typography.bodyLineHeight.sp,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focusChamp)
                    .onFocusChanged { composeurFocalise = it.isFocused },
            )
        }
        Spacer(Modifier.width(6.dp))
        // the emoji button, to the right of the field and not to the left: the plus on the left
        // attaches a file, and this one composes text. two gestures of different natures do not get
        // filed in the same place
        //
        // it changes glyph: with the panel open it brings the keyboard back. the same place for the
        // same toggle, and that is what the reference does, since two buttons side by side would
        // mean choosing which closes the other
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .clickable {
                    pickerOuvert = !pickerOuvert
                    if (!pickerOuvert) focusChamp.requestFocus()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icone(
                if (pickerOuvert) Icones.clavier else Icones.emoji,
                tint = tokens.textSecondary,
                taille = 22.dp,
                description = if (pickerOuvert) t.revenirAuClavier else t.emojis,
            )
        }
        // dictation, as a button of its own, since nobody could see how to do speech to text. it
        // only existed as a long press on the mic, so for nobody. gold while listening, and
        // touching it again stops it, with the nas listening back over the whole thing before the
        // final text
        Box(
            // a toggle and nothing else: you tap to speak and tap again to stop
            //
            // there already was a no hold mode, a press under half a second left the listening
            // running, but that threshold made it unreachable: a slightly deliberate press, which
            // is to say the normal press of somebody aiming at a 36 point button, went past 500 ms
            // and stopped everything on release. in practice you had to hold, which is exactly what
            // we no longer wanted
            //
            // push to talk is removed rather than fixed with a longer threshold: two gestures on
            // the same button, separated by a duration nobody measures, always end up confused.
            // better a gesture that shows, the mic turning gold, than a mode you discover by
            // getting it wrong
            Modifier.size(36.dp).clip(CircleShape)
                .clickable {
                    val enCours = dictee
                    when {
                        finalisationDictee -> Unit
                        enCours != null -> enCours.terminer()
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            contexteMicro, android.Manifest.permission.RECORD_AUDIO,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED ->
                            demanderMicroDictee.launch(android.Manifest.permission.RECORD_AUDIO)
                        else -> lancerDictee()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icone(
                Icones.micro,
                tint = if (dictee != null) tokens.accent else tokens.textSecondary,
                taille = 20.dp,
                description = if (dictee != null) t.dicteeArreter else t.dicter,
            )
            Text(
                "Aa",
                color = if (dictee != null) tokens.accent else tokens.textSecondary,
                fontSize = 8.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 3.dp),
            )
        }
        // one button, and three tabs behind it
        //
        // there were three icons here for one version, emoji, gif, stickers, because the gif button
        // was missing and we added it. the reference's screenshot shows one: the composer puts a
        // plus, a gift, an emoji and a mic, and the emoji is what opens the three tab picker. two
        // icons fewer for the same function, and the space goes back to the text
        //
        // so the opening tab stays emoji, and gifs and stickers are one gesture away, on the
        // panel's bar
        Spacer(Modifier.width(6.dp))
        Box(
            // bare at rest, filled once there is something to send. the reference sets its mic with
            // no pill, and the accent fill stays, since it is what tells "record a voice message"
            // from "send this text", and that piece of information does not exist on the reference
            Modifier.size(tokens.tailleBoutonEnvoi).clip(CircleShape)
                .background(
                    if (champ.text.isBlank() && dictee == null) androidx.compose.ui.graphics.Color.Transparent
                    else tokens.accent
                )
                .combinedClickable(
                    // dictation has its own button on the left: no more hidden long press here
                ) {
                    // while dictating, the button does one thing: stop it
                    val enCours = dictee
                    if (enCours != null) {
                        enCours.terminer()
                        return@combinedClickable
                    }
                    if (champ.text.isBlank()) {
                        // empty field: the button is a mic. the permission first, and when it is
                        // already granted the system answers immediately
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                contexteMicro,
                                android.Manifest.permission.RECORD_AUDIO,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            enregistre = true
                        } else {
                            demanderMicro.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        // we empty the field immediately: the local echo makes the message appear
                        // at once, so leaving it in the field would show it twice
                        onSend(champ.text)
                        champ = androidx.compose.ui.text.input.TextFieldValue("")
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // one place, two intentions: an empty field means ready to record a voice message, and
            // a filled one means ready to send. showing both would impose a pointless choice. on
            // release, the nas listens back over the whole dictation, so the text is still going to
            // improve and we wait
            if (finalisationDictee) {
                androidx.compose.material3.CircularProgressIndicator(
                    Modifier.size(18.dp), color = tokens.bg, strokeWidth = 2.dp,
                )
            } else Icone(
                if (champ.text.isBlank()) Icones.micro else Icones.envoyer,
                tint = if (champ.text.isBlank()) tokens.textMuted else tokens.bg,
                taille = 20.dp,
                description = if (champ.text.isBlank()) t.messageVocal else t.envoyer,
            )
        }
    }

    // under the composer, never over it: unlike the reactions, here you watch the text fill in
    // while you pick
    //
    // custom emojis are accepted, since we are writing a message, so html, where an image belongs.
    // that is the exact difference with the reactions picker
    if (pickerOuvert) {
        PanneauEmojis(
            packs = LocalPacks.current,
            // the panel closes itself before delegating. this is where the state lives, and it is
            // the only immediate answer to the tap, the send taking a moment to produce its echo. a
            // panel left open would invite a second tap, so a second send
            //
            // the gif panel is supplied here: it still lives in the android app, while the picker
            // is shared. see `panneauGif`
            panneauGif = onGif?.let { suite ->
                { onChercher ->
                    PanneauGif(
                        onChoisir = { gif -> pickerOuvert = false; suite(gif) },
                        onReglages = onReglagesGif,
                        onChercher = onChercher,
                    )
                }
            },
            // same rule as the gifs: close first, send after. a sticker is a whole message, and a
            // panel left open would invite sending two
            onAutocollant = onAutocollant?.let { suite ->
                { autocollant -> pickerOuvert = false; suite(autocollant) }
            },
            onChoisir = { choix ->
                champ = remplacerAvantCurseur(
                    champ,
                    avantCurseur + when (choix) {
                        is ChoixEmoji.Unicode -> choix.caractere
                        is ChoixEmoji.Personnalise -> ":${choix.emote.shortcode}: "
                    },
                )
            },
            // deletes a character, or a whole shortcode: `:PandaLove: ` is twelve keystrokes, and
            // removing them one by one to fix an emoji would be absurd
            onEffacer = {
                val avant = avantCurseur
                champ = remplacerAvantCurseur(
                    champ,
                    when {
                        avant.isEmpty() -> avant
                        avant.trimEnd().endsWith(":") &&
                            avant.trimEnd().dropLast(1).contains(":") ->
                            avant.trimEnd().dropLast(1).substringBeforeLast(":")
                        else -> avant.dropLast(1)
                    },
                )
            },
        )
    }
    }
}

/**
 * copies a chosen medium into a file the engine can read
 *
 * android's picker hands back a `content://`, which is a temporary access token and not a path.
 * `sendMedia` wants a file, because the sdk uploads as a stream and has to be able to reopen the
 * source. passing it the uri would have meant loading everything into memory first, which is
 * exactly what batch 6 set out to avoid
 *
 * the copy goes into the cache: it is an intermediate, not a saved file, and the system may reclaim
 * it whenever it wants room
 */
/** the `content://` uri of a file in `cache/envois`, which is what the camera can write to */
private fun uriDEnvoi(contexte: android.content.Context, fichier: java.io.File): android.net.Uri =
    androidx.core.content.FileProvider.getUriForFile(
        contexte, contexte.packageName + ".fichiers", fichier,
    )

private fun copierPourEnvoi(contexte: android.content.Context, uri: android.net.Uri): java.io.File? =
    runCatching {
        val nom = uri.lastPathSegment?.substringAfterLast('/')?.take(64) ?: "image"
        val extension = contexte.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
        val cible = java.io.File(
            contexte.cacheDir.resolve("envois").apply { mkdirs() },
            "$nom-${System.currentTimeMillis()}.$extension",
        )
        contexte.contentResolver.openInputStream(uri)!!.use { entree ->
            cible.outputStream().use { sortie -> entree.copyTo(sortie) }
        }
        cible
    }.getOrNull()

/**
 * replaces what precedes the cursor and puts the cursor back right behind it
 *
 * what naively substituting the whole string cannot do: keep the end of the message intact when you
 * come back to complete a word in the middle, and above all not leave the cursor where it was
 * before the text changed under it
 */
private fun remplacerAvantCurseur(
    champ: androidx.compose.ui.text.input.TextFieldValue,
    nouveauDebut: String,
): androidx.compose.ui.text.input.TextFieldValue {
    val apres = champ.text.drop(champ.selection.end)
    return androidx.compose.ui.text.input.TextFieldValue(
        text = nouveauDebut + apres,
        selection = androidx.compose.ui.text.TextRange(nouveauDebut.length),
    )
}

/**
 * how much silence before withdrawing "typing…"
 *
 * four seconds: past a pause that long you are no longer typing, you are thinking, and leaving the
 * notice up would keep the others waiting for nothing
 */
private const val SILENCE_AVANT_ARRET_MS = 4_000L

/**
 * the skeleton of a conversation being loaded
 *
 * five rows shaped like a grouped message: an avatar dot, a short name, one to three text bars of
 * uneven widths. the unevenness is not cosmetic, bars all the same length read as an empty template
 * rather than as text on its way
 *
 * no animation. continuous costly loops are out, and a shimmer over five rows would be one. the
 * dimming already says "not here yet", which is all we ask of this screen
 */
@Composable
private fun SqueletteConversation(modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    // fixed widths rather than drawn at random: a skeleton that changes shape on every
    // recomposition flickers, which is worse than the emptiness it fills
    val gabarits = remember {
        listOf(listOf(0.72f, 0.44f), listOf(0.55f), listOf(0.86f, 0.62f, 0.31f), listOf(0.48f), listOf(0.68f, 0.39f))
    }
    Column(modifier.padding(horizontal = tokens.espL, vertical = tokens.espM)) {
        gabarits.forEach { barres ->
            Row(Modifier.padding(vertical = 10.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(tokens.surfaceRaised.copy(alpha = 0.55f))
                )
                Column(Modifier.padding(start = tokens.espM).weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth(0.28f).height(12.dp)
                            .clip(RoundedCornerShape(tokens.radiusSmall))
                            .background(tokens.surfaceRaised.copy(alpha = 0.55f))
                    )
                    barres.forEach { largeur ->
                        Box(
                            Modifier.padding(top = tokens.espS).fillMaxWidth(largeur).height(11.dp)
                                .clip(RoundedCornerShape(tokens.radiusSmall))
                                .background(tokens.surfaceRaised.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        }
    }
}

/**
 * nothing to show, and a real screen to say so
 *
 * the worst possible first contact with velvet: an account just signed up, before the space
 * promised by the invite link has finished joining, or because it could not, a malformed token, a
 * space that became unreachable meanwhile. built on the already proven model of the empty direct
 * messages screen: an icon, a sentence that reassures rather than worries, and a gesture that
 * always works, even at zero rooms
 *
 * @param vide `true` when there is neither a server nor a private conversation, the real first day.
 *   `false` covers the rarer case where rooms exist but none is selected: the message changes, the
 *   gesture stays the same
 */
@Composable
private fun EcranBienvenue(
    vide: Boolean,
    onEcrire: () -> Unit,
    /** open the room list, the visible way out, see below */
    onVoirSalons: () -> Unit = {},
    /**
     * the open server has no room at all
     *
     * a distinct case from "no room selected", and confusing the two said something false. seen on
     * a real device: a server created before `createServer` laid down its `#general` had zero, and
     * the screen still invited you to "choose a room in the left column", where there was none. we
     * were sending people looking for what does not exist
     */
    serveurSansSalon: Boolean = false,
    onCreerSalon: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTokens.current
    val t = textes()
    Column(
        modifier.background(tokens.bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icone(Icones.messages, tint = tokens.textMuted, taille = 48.dp)
        Text(
            when {
                vide -> t.bienvenue
                serveurSansSalon -> t.serveurSansSalon
                else -> t.aucunSalonSelectionne
            },
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(top = tokens.espL),
        )
        Text(
            when {
                // this is not a failure, and it has to be worded that way: a freshly signed up
                // account may legitimately have nothing yet, while a space joins. phrasing it as a
                // wait rather than as a void reassures without lying, we do not know how long it
                // will take, but we do know it is not broken
                vide -> t.accueilVide

                // no version number in the text. the first wording said "since version 0.5", true
                // for a week and false afterwards, and nobody thinks to reread it. a screen must
                // not date itself
                serveurSansSalon -> t.serveurSansSalonAide

                else -> t.choisirSalonOuPersonne
            },
            color = tokens.textMuted,
            fontSize = tokens.typography.captionSize.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = tokens.espS, bottom = 22.dp),
        )

        // on an empty server, creating a room is the expected action, and writing to somebody does
        // not answer it. so the right one goes first and the other second
        if (serveurSansSalon && onCreerSalon != null) {
            BoutonPlein(libelle = t.creerSalon, actif = true, onClick = onCreerSalon)
            Text(
                t.ecrireAQuelquun,
                color = tokens.textSecondary,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier.padding(top = 14.dp).clickable(onClick = onEcrire).padding(tokens.espS),
            )
        } else {
            BoutonPlein(libelle = t.ecrireAQuelquun, actif = true, onClick = onEcrire)
        }

        // the way out, written in plain sight. this screen was a dead end: the only gesture it
        // offered opened a conversation, and back left the app. all that was left was the drag from
        // the edge, which nothing announces
        //
        // help you cannot get out of stops being help, and that is what was reported, rightly. the
        // link only appears when there are rooms to see: on a genuinely empty account it would lead
        // nowhere
        if (!vide) {
            Text(
                t.voirLesSalons,
                color = tokens.textSecondary,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .clickable(onClick = onVoirSalons)
                    .padding(tokens.espS),
            )
        }
    }
}

/**
 * the voice room's preview, before joining: its name, who is inside, and the join button
 *
 * the reference does this on the phone (`PRE_JOIN_SHEET`): a tap on a voice room shows it, it does
 * not drop you into the call. joining stays a deliberate second gesture
 */
@Composable
private fun ApercuVocal(
    salonId: String,
    nom: String,
    onRejoindre: () -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val membres = LocalMembres.current
    val engine = LocalEngine.current
    // who is inside, read for this room alone while the sheet is up
    var presents by remember(salonId) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(salonId) {
        runCatching { engine.vocauxFlow(listOf(salonId)).collect { presents = it[salonId].orEmpty() } }
    }
    FeuilleBasse(onFermer = onFermer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = tokens.espL).padding(bottom = tokens.espL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icone(Icones.salonVocal, tint = tokens.textSecondary, taille = 24.dp)
                Spacer(Modifier.width(tokens.espS))
                Text(
                    nom,
                    color = tokens.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(tokens.espL))
            if (presents.isEmpty()) {
                Text(t.personneDansLeVocal, color = tokens.textMuted, fontSize = 14.sp)
            } else {
                presents.forEach { qui ->
                    val membre = membres.firstOrNull { it.userId == qui }
                    Row(
                        Modifier.fillMaxWidth().height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(qui, membre?.displayName, membre?.avatarUrl, 32.dp)
                        Spacer(Modifier.width(tokens.espM))
                        Text(
                            membre?.displayName ?: qui.removePrefix("@").substringBefore(':'),
                            color = tokens.textPrimary,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(tokens.espL))
            Box(
                Modifier.fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFF248046))
                    .clickable(onClick = onRejoindre),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.rejoindreLeVocal, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

package li.velvetchat.desk

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import li.velvetchat.app.ui.*
import li.velvetchat.core.model.Categorie
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.ChannelKind
import li.velvetchat.core.model.ModeNotification
import li.velvetchat.core.model.Profile
import li.velvetchat.core.model.Server
import li.velvetchat.core.model.Statut

/**
 * the desktop shell, measured to the pixel off the screenshots
 *
 * the discord photographed is themed, custom background and widened channel column, and it is that
 * rendering that was asked for rather than a fresh install's. so the dimensions come from the
 * screenshots and not the house style. measured on a 1920 by 1080 window:
 *
 *     server rail          x    0 …   78     79
 *     channel column       x   79 …  428    350
 *     conversation area    x  429 … 1671   1243
 *     member panel         x 1672 … 1919    248
 *     header               y    0 …   52     53
 *     composer box         y  967 … 1023     56
 *
 * so the channel column is 350 and not 240, and the member panel 248. the gap is the theme and not
 * a measuring error, and it is what was wanted
 *
 * not reproduced for want of being able to: the background image, which belongs to whoever chose
 * it. the theme already carries `chatBackground` to receive one
 */

/**
 * the server rail's width, gap included, remeasured: rail x 0 to 64 with the icons centred on x 32,
 * then about 7 px where the wallpaper shows before the channel column
 */
internal val RAIL = 71.dp

/** the gap between the rail and the channel column */
private val ECART_RAIL = 7.dp

/** the rail's selection bar, measured: x 4 to 7, 38 tall, pale yellow */
private val BARRE_SELECTION = androidx.compose.ui.graphics.Color(0xFFFBFFBD)

/** the fill of tiles with no image (plus, compass, initials), measured dark grey */
private val FOND_CASE_RAIL = androidx.compose.ui.graphics.Color(0xFF181A1E)

/** the channel column, measured x 79 to 428 */
internal val COLONNE_SALONS = 350.dp

/** the member panel, measured x 1672 to 1919 */
internal val COLONNE_MEMBRES = 248.dp

/** the header's height, the same on both sides of the window, measured y 0 to 52 */
internal val ENTETE = 53.dp

/**
 * the server rail
 *
 * the selection pill is on the left and overhangs the rail: it is what tells "this server is open"
 * from "my mouse is on it", and without it two neighbouring icons look alike. four points wide,
 * forty tall when the server is open
 *
 * rounded squares of 40, radius 12, selected or not. the reference does not round them further at
 * rest (the phone keeps its circle to square mechanic)
 */
@Composable
fun Rail(
    serveurs: List<Server>,
    ouvert: String?,
    surAccueil: Boolean,
    onAccueil: () -> Unit,
    onChoisir: (String) -> Unit,
    onToutLire: () -> Unit = {},
    /** the rail's plus: opens create your server */
    onAjouter: () -> Unit = {},
    /** unread direct conversations: one tile each under direct messages, with a red badge */
    directsNonLus: List<ConversationPrivee> = emptyList(),
    onDirect: (ConversationPrivee) -> Unit = {},
    /** the rail's wanted order. `setOrdreServeurs` existed and had the phone as its only client */
    onReordonner: (List<String>) -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current
    // the rail drags like the channel column: a 48 tile with its 8 gap
    var glisse by remember { mutableStateOf<String?>(null) }
    var glisseY by remember { mutableStateOf(0f) }
    val pasDeCase = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    // the space opens in front of the tile being dragged. without it you drag blind, since nothing
    // says where you will land until you let go. the neighbours step back one slot, which opens the
    // hole the tile will fall into
    val depuisGlisse = serveurs.indexOfFirst { it.id == glisse }
    val versGlisse = if (depuisGlisse < 0) -1 else {
        (depuisGlisse + (glisseY / pasDeCase).roundToInt()).coerceIn(0, serveurs.lastIndex)
    }
    fun ecartDe(index: Int): Float = when {
        depuisGlisse < 0 || versGlisse < 0 || index == depuisGlisse -> 0f
        depuisGlisse < versGlisse && index in (depuisGlisse + 1)..versGlisse -> -pasDeCase
        versGlisse < depuisGlisse && index in versGlisse..(depuisGlisse - 1) -> pasDeCase
        else -> 0f
    }
    Column(
        Modifier.width(RAIL).fillMaxHeight().padding(end = ECART_RAIL)
            .background(fondColonne(tokens.bg)).padding(vertical = 12.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // direct messages, at the top and apart: they belong to no server, and it is the only
        // button in the rail that is not a space
        CaseRail(actif = surAccueil, onClick = onAccueil, infobulle = t.messagesPrives) {
            Icone(
                if (surAccueil) Icones.messagesActif else Icones.messages,
                tint = tokens.textPrimary,
                taille = 26.dp,
                description = t.messagesPrives,
            )
        }
        // read all: mark everything read, under the direct messages button
        Text(
            t.toutMarquerLu,
            color = tokens.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).curseurMain().clickable(onClick = onToutLire)
                .padding(horizontal = 2.dp),
        )

        // unread dms, under the button: the avatar, and the count in a red badge
        directsNonLus.forEach { d ->
            CaseRail(actif = false, onClick = { onDirect(d) }, infobulle = d.nom, badge = d.nonLus) {
                Avatar(userId = d.userId, displayName = d.nom, avatarUrl = d.avatarUrl, taille = 40.dp)
            }
        }

        // the rule separating the dms from the servers. short and centred: it marks a boundary, it
        // does not draw a box
        Box(
            Modifier.width(32.dp).height(2.dp).clip(RoundedCornerShape(1.dp))
                .background(tokens.border),
        )

        serveurs.forEachIndexed { index, s ->
            CaseRail(
                actif = s.id == ouvert,
                onClick = { onChoisir(s.id) },
                infobulle = s.name,
                nonLu = s.channels.any { it.unread.hasUnread },
                badge = s.channels.sumOf { it.unread.highlights },
                glisse = if (glisse == s.id) glisseY else null,
                ecart = ecartDe(index),
                onGlisser = { dy -> glisse = s.id; glisseY += dy },
                onGlisserFin = {
                    val depuis = index
                    val vers = (depuis + (glisseY / pasDeCase).roundToInt())
                        .coerceIn(0, serveurs.lastIndex)
                    glisse = null
                    glisseY = 0f
                    if (vers != depuis) {
                        val ordre = serveurs.map { it.id }.toMutableList()
                        ordre.removeAt(depuis)
                        ordre.add(vers, s.id)
                        onReordonner(ordre)
                    }
                },
            ) {
                if (s.avatarUrl == null) {
                    // with no image: light initials on a dark square, and not the coloured tile
                    // used for people, a server having no identity to colour
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(FOND_CASE_RAIL),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initialesDe(s.name),
                            color = androidx.compose.ui.graphics.Color(0xFFD9D9D9),
                            fontSize = 15.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                } else {
                    Avatar(
                        userId = s.id,
                        displayName = s.name,
                        avatarUrl = s.avatarUrl,
                        taille = 40.dp,
                        forme = RoundedCornerShape(12.dp),
                    )
                }
            }
        }

        // add a server, after the list. discover removed: a public directory has no purpose on a
        // server this size
        CaseRail(actif = false, onClick = onAjouter, infobulle = t.ajouterServeur) { CaseIconeRail(Icones.ajouter, t.ajouterServeur) }
    }
}

@Composable
private fun CaseIconeRail(icone: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(FOND_CASE_RAIL),
        contentAlignment = Alignment.Center,
    ) {
        Icone(icone, tint = androidx.compose.ui.graphics.Color(0xFFD9D9D9), taille = 20.dp, description = description)
    }
}

/** one rail tile: the selection pill on the left, the icon in the centre */
@Composable
private fun CaseRail(
    actif: Boolean,
    onClick: () -> Unit,
    infobulle: String? = null,
    /** unread: a small white pill on the left, when the tile is not open */
    nonLu: Boolean = false,
    /** mentions on a server, or messages on a dm: a red badge bottom right, 0 means none */
    badge: Int = 0,
    /** non null when this tile is the one being dragged: how far it has moved */
    glisse: Float? = null,
    /** how far this tile steps aside to let the dragged one past */
    ecart: Float = 0f,
    onGlisser: (Float) -> Unit = {},
    onGlisserFin: () -> Unit = {},
    contenu: @Composable () -> Unit,
) {
    val tokens = LocalTokens.current
    val survolCase = remember { MutableInteractionSource() }
    val caseSurvolee by survolCase.collectIsHoveredAsState()
    Box(
        Modifier.fillMaxWidth().height(40.dp).hoverable(survolCase)
            .then(
                if (glisse != null) {
                    Modifier.zIndex(1f).offset { IntOffset(0, glisse.roundToInt()) }.alpha(0.85f)
                } else {
                    // animated, unlike the dragged tile which has to stick to the cursor: it is the
                    // movement that reads as "the space is opening", not the position
                    val ecarte by androidx.compose.animation.core.animateFloatAsState(
                        ecart,
                        label = "ecart",
                    )
                    Modifier.offset { IntOffset(0, ecarte.roundToInt()) }
                },
            )
            .pointerInput(infobulle) {
                detectDragGestures(onDragEnd = onGlisserFin, onDragCancel = onGlisserFin) { c, d ->
                    c.consume()
                    onGlisser(d.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // the reference's tooltip: to the right of the rail, arrow on the left, name in bold
        if (caseSurvolee && infobulle != null) {
            val decalage = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.roundToPx() }
            Popup(alignment = Alignment.CenterStart, offset = IntOffset(decalage, 0)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.offset(x = 4.dp).size(10.dp).rotate(45f).background(Color(0xFF1B1D22)))
                    Box(
                        Modifier.widthIn(max = 200.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B1D22))
                            .border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Text(
                            infobulle,
                            color = Color(0xFFF2F3F5),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (actif) {
            Box(
                Modifier.align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .width(4.dp).height(38.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BARRE_SELECTION),
            )
        }
        if (!actif && nonLu) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                    .width(4.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(BARRE_SELECTION),
            )
        }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).curseurMain().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            contenu()
        }
        if (badge > 0) {
            // outlined in the rail's colour, like the reference: it bites into the icon without
            // dirtying it
            Box(
                Modifier.align(Alignment.Center).offset(x = 14.dp, y = 14.dp)
                    .clip(RoundedCornerShape(9.dp)).background(fondColonne(tokens.bg)).padding(3.dp)
                    .clip(RoundedCornerShape(7.dp)).background(Color(0xFFF23F43))
                    .widthIn(min = 16.dp).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (badge > 99) "99+" else badge.toString(),
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
            }
        }
    }
}

/**
 * the channel column: the server header, the categories, and the user bar
 *
 * categories collapse and keep their signals while collapsed: without that, collapsing would mean
 * hiding what you have not read, and nobody would ever collapse anything again. the rule was
 * already written for the phone
 */
@Composable
fun ColonneSalons(
    serveur: Server?,
    ouvert: String?,
    profil: Profile?,
    statut: Statut,
    decoration: String?,
    onChoisir: (String) -> Unit,
    onProfil: () -> Unit,
    onMembres: () -> Unit = {},
    onMarquerLu: (String) -> Unit = {},
    /** who is in each voice room, by matrix id. absent means nobody */
    enVocal: Map<String, List<String>> = emptyMap(),
    actions: ActionsServeur = ActionsServeur(),
    /** the channels whose mode is "no messages" */
    muets: Set<String> = emptySet(),
) {
    val t = textes()
    val tokens = LocalTokens.current
    var repliees by remember(serveur?.id) { mutableStateOf(setOf<String>()) }
    var masquerMuets by remember { mutableStateOf(false) }

    Column(Modifier.width(Largeurs.salons).fillMaxHeight().background(fondColonne(tokens.surface))) {
        // the server header, remeasured: about 50 tall then a rule, the server icon (20), the name
        // in bold, a chevron, and invite on the far right. the chevron opens the server menu, and
        // invite is drawn, not wired
        Row(
            // dragging the server header also moves the window: the whole top band is the title
            // bar, not only its right hand part
            Modifier.fillMaxWidth().height(50.dp).deplaceFenetre().padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (serveur != null) {
                Avatar(
                    userId = serveur.id,
                    displayName = serveur.name,
                    avatarUrl = serveur.avatarUrl,
                    taille = 20.dp,
                    forme = RoundedCornerShape(6.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            // the name and its chevron open the server menu: one target, like on the reference, and
            // not a fourteen point chevron to aim at
            Box {
                var menuServeur by remember(serveur?.id) { mutableStateOf(false) }
                Row(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .curseurMain().clickable { menuServeur = !menuServeur }
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        serveur?.name ?: t.connexionTitre,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icone(
                        Icones.replier,
                        tint = tokens.textPrimary,
                        taille = 14.dp,
                        description = null,
                        // the chevron flips when the menu is open, like a category
                        modifier = if (menuServeur) Modifier.rotate(180f) else Modifier,
                    )
                }
                if (menuServeur) {
                    androidx.compose.ui.window.Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(0, 40),
                        onDismissRequest = { menuServeur = false },
                        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                    ) {
                        MenuServeurBureau(serveur, actions, masquerMuets, { masquerMuets = !masquerMuets }) { menuServeur = false }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Icone(Icones.inviter, tint = tokens.textSecondary, taille = 20.dp, description = t.inviterDesGens,
                modifier = Modifier.curseurMain().clickable(onClick = actions.onInviter))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.border))

        val groupes = remember(serveur) {
            grouperSalons(serveur?.channels.orEmpty(), serveur?.categories.orEmpty())
        }
        // right click in the column's empty space, which did nothing. that is where people look for
        // it, and creating a channel had one door, the server menu at the top
        //
        // dragging a channel. only its rank inside its group is set that way, and changing category
        // goes through the menu's move to, which aims better than an approximate drop on a heading
        var glisse by remember { mutableStateOf<String?>(null) }
        var glisseY by remember { mutableStateOf(0f) }
        val pasDeLigne = with(androidx.compose.ui.platform.LocalDensity.current) { 34.dp.toPx() }
        var menuVide by remember { mutableStateOf<IntOffset?>(null) }
        val origineColonne = remember { floatArrayOf(0f, 0f) }
        menuVide?.let { ou ->
            Popup(
                popupPositionProvider = AuCurseur(ou),
                onDismissRequest = { menuVide = null },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier.width(230.dp).clip(RoundedCornerShape(8.dp)).background(FOND_MENU)
                        .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp)).padding(8.dp),
                ) {
                    EntreeMenu(t.creerSalonTextuel, icone = Icones.salon, onClick = {
                        menuVide = null; actions.onCreerSalon()
                    })
                    EntreeMenu(t.creerSalonVocalTitre, icone = Icones.salonVocal, onClick = {
                        menuVide = null; actions.onCreerSalonVocal()
                    })
                    FiletMenu()
                    EntreeMenu(t.creerUneCategorie, icone = Icones.plusGras, onClick = {
                        menuVide = null; actions.onCreerCategorie()
                    })
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp)
                .onGloballyPositioned {
                    val o = it.positionInWindow()
                    origineColonne[0] = o.x
                    origineColonne[1] = o.y
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                                val p = e.changes.first().position
                                menuVide = IntOffset(
                                    (origineColonne[0] + p.x).roundToInt(),
                                    (origineColonne[1] + p.y).roundToInt(),
                                )
                            }
                        }
                    }
                },
        ) {
            // the reference's shortcuts, then a rule. server boosts removed by decision. members
            // opens the member panel, and the other two are drawn, not wired
            item(key = "raccourcis") {
                Column(Modifier.padding(top = 8.dp)) {
                    LigneRaccourci(Icones.evenements, t.evenements) {}
                    LigneRaccourci(Icones.chercherSalons, t.chercherDesSalons) {}
                    LigneRaccourci(Icones.membres, t.membres, onMembres)
                    Box(
                        Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                            .fillMaxWidth().height(1.dp).background(tokens.border),
                    )
                }
            }
            groupes.forEach { (categorie, salons) ->
                val replie = categorie != null && categorie in repliees
                if (categorie != null) {
                    item(key = "cat-$categorie") {
                        EnteteCategorie(categorie, replie) {
                            repliees = if (replie) repliees - categorie else repliees + categorie
                        }
                    }
                }
                // an unread channel stays visible under a collapsed category: collapsing must not
                // amount to making it disappear
                val visibles = (if (replie) salons.filter { it.unread.hasUnread } else salons)
                    .filter { !masquerMuets || it.id !in muets || it.id == ouvert }
                // the space opens in front of the channel being dragged, like in the rail
                val depuisGlisse = visibles.indexOfFirst { it.id == glisse }
                val versGlisse = if (depuisGlisse < 0) -1 else {
                    (depuisGlisse + (glisseY / pasDeLigne).roundToInt())
                        .coerceIn(0, visibles.lastIndex)
                }
                itemsIndexed(visibles, key = { _, c -> c.id }) { index, c ->
                    val ecart = when {
                        depuisGlisse < 0 || versGlisse < 0 || index == depuisGlisse -> 0f
                        depuisGlisse < versGlisse && index in (depuisGlisse + 1)..versGlisse ->
                            -pasDeLigne
                        versGlisse < depuisGlisse && index in versGlisse..(depuisGlisse - 1) ->
                            pasDeLigne
                        else -> 0f
                    }
                    LigneSalon(
                        c, actif = c.id == ouvert, muet = c.id in muets, onMarquerLu = { onMarquerLu(c.id) },
                        onInviter = actions.onInviter, onModifier = { actions.onModifierSalon(c) },
                        onSupprimer = { actions.onSupprimerSalon(c) },
                        categories = serveur?.categories.orEmpty(),
                        onDeplacer = { cible -> actions.onDeplacerSalon(c, cible) },
                        onMode = { mode -> actions.onModeSalon(c, mode) },
                        enVocal = enVocal[c.id].orEmpty(),
                        glisse = if (glisse == c.id) glisseY else null,
                        ecart = ecart,
                        onGlisser = { dy -> glisse = c.id; glisseY += dy },
                        onGlisserFin = {
                            // the rank is computed on what you see, then written for the whole
                            // group: reordering only the visible list would leave the hidden
                            // channels, muted or collapsed, with their old ranks, so wedged
                            // anywhere on the next display
                            val depuis = visibles.indexOfFirst { it.id == c.id }
                            val vers = (depuis + (glisseY / pasDeLigne).roundToInt())
                                .coerceIn(0, visibles.lastIndex)
                            glisse = null
                            glisseY = 0f
                            val parent = categorie
                                ?.let { nom -> serveur?.categories?.firstOrNull { it.nom == nom }?.id }
                                ?: serveur?.id
                            if (parent != null && vers != depuis && depuis >= 0) {
                                val voisin = visibles[vers].id
                                val ordre = salons.map { it.id }.toMutableList()
                                ordre.remove(c.id)
                                val place = ordre.indexOf(voisin).let {
                                    if (vers > depuis) it + 1 else it
                                }.coerceIn(0, ordre.size)
                                ordre.add(place, c.id)
                                actions.onReordonnerSalons(parent, ordre)
                            }
                        },
                    ) { onChoisir(c.id) }
                    // who is in the voice room, under its channel. that is how the reference shows
                    // it, and it is what was missing: a counter says there are people, it does not
                    // say who, so it gives no reason to go in
                    enVocal[c.id].orEmpty().forEach { qui ->
                        LigneDansLeVocal(qui, c.id, actions.onMembreVocal)
                    }
                    if (enVocal[c.id].orEmpty().isNotEmpty()) {
                        LigneInviterAuVocal { actions.onInviter() }
                    }
                }
            }
            // the mini bar floats over the bottom of the column, so there is room for it not to
            // hide the last channels
            item(key = "pied") { Spacer(Modifier.height(MINI_BARRE + 8.dp)) }
        }
    }
}

/**
 * a shortcut at the top of the column: the same metrics as a channel, 34 tall, a 20 icon, 16 text
 */
@Composable
private fun LigneRaccourci(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    libelle: String,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(icone, tint = tokens.textSecondary, taille = 20.dp, description = null)
        Spacer(Modifier.width(8.dp))
        Text(libelle, color = tokens.textSecondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * a category header, like the reference: the name as written and not uppercased, the chevron after
 * the name, and a plus on the far right (create a channel here, drawn, not wired)
 */
@Composable
private fun EnteteCategorie(nom: String, replie: Boolean, onBasculer: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth()
            .padding(top = 16.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .curseurMain().clickable(onClick = onBasculer)
            .padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            nom,
            color = tokens.textMuted,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(3.dp))
        Icone(
            Icones.replier,
            tint = tokens.textMuted,
            taille = 12.dp,
            description = null,
            modifier = Modifier.rotate(if (replie) -90f else 0f),
        )
        Spacer(Modifier.weight(1f))
        Icone(Icones.ajouter, tint = tokens.textMuted, taille = 16.dp, description = t.creerSalon)
    }
}

@Composable
private fun LigneSalon(
    salon: Channel,
    actif: Boolean,
    muet: Boolean = false,
    onMarquerLu: () -> Unit,
    onInviter: () -> Unit = {},
    onModifier: () -> Unit = {},
    onSupprimer: () -> Unit = {},
    categories: List<Categorie> = emptyList(),
    onDeplacer: (Categorie?) -> Unit = {},
    /** the notification mode chosen for this channel, from its right click menu */
    onMode: (ModeNotification) -> Unit = {},
    /** who is already in this voice room. empty means nothing is shown */
    enVocal: List<String> = emptyList(),
    /** non null when this row is the one being dragged: how far it has moved */
    glisse: Float? = null,
    /** how far this row steps aside to let the dragged one past */
    ecart: Float = 0f,
    onGlisser: (Float) -> Unit = {},
    onGlisserFin: () -> Unit = {},
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val nonLu = salon.unread.hasUnread
    // right click opens the channel menu at the cursor and not under the row: that is the reference
    var menu by remember { mutableStateOf<IntOffset?>(null) }
    // where the row sits in the window, read on click, without recomposing on every scroll
    val origine = remember { floatArrayOf(0f, 0f) }
    menu?.let { ou ->
        Popup(
            popupPositionProvider = AuCurseur(ou),
            onDismissRequest = { menu = null },
            properties = PopupProperties(focusable = true),
        ) {
            MenuSalon(
                salon,
                onFermer = { menu = null },
                onMarquerLu = onMarquerLu,
                onSupprimer = onSupprimer,
                categories = categories,
                onDeplacer = onDeplacer,
                onMode = onMode,
            )
        }
    }
    // remeasured: rows 34 apart, a 20 icon 10 from the edge, text at 16 and 8 from the icon. the
    // open channel has a translucent light fill at radius 6, letting the wallpaper through, its
    // name in bold, and invite plus settings on the right (drawn, not wired). hovered: white at
    // 3.5%
    val survolSalon = remember { MutableInteractionSource() }
    val salonSurvole by survolSalon.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth()
            .height(34.dp)
            // the dragged row follows the mouse and draws above the others, otherwise it would
            // disappear under the next one as soon as it moved
            .then(
                if (glisse != null) {
                    Modifier.zIndex(1f)
                        .offset { IntOffset(0, glisse.roundToInt()) }
                } else {
                    val ecarte by androidx.compose.animation.core.animateFloatAsState(
                        ecart,
                        label = "ecart",
                    )
                    Modifier.offset { IntOffset(0, ecarte.roundToInt()) }
                },
            )
            // a muted channel is greyed, like the reference: you see it, it asks for nothing
            .alpha(if (glisse != null) 0.85f else if (muet && !actif) 0.45f else 1f)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    actif -> Color.White.copy(alpha = 0.10f)
                    salonSurvole -> Color.White.copy(alpha = 0.035f)
                    else -> Color.Transparent
                },
            )
            .hoverable(survolSalon)
            .onGloballyPositioned { val o = it.positionInWindow(); origine[0] = o.x; origine[1] = o.y }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                            val p = e.changes.first().position
                            menu = IntOffset((origine[0] + p.x).roundToInt(), (origine[1] + p.y).roundToInt())
                        }
                    }
                }
            }
            .curseurMain().clickable(onClick = onClick)
            .pointerInput(salon.id) {
                detectDragGestures(
                    onDragEnd = onGlisserFin,
                    onDragCancel = onGlisserFin,
                ) { changement, delta ->
                    changement.consume()
                    onGlisser(delta.y)
                }
            }
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(
            if (salon.kind == ChannelKind.VOICE) Icones.salonVocal else Icones.salon,
            tint = if (actif) tokens.textPrimary else tokens.textSecondary,
            taille = 20.dp,
            description = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            salon.name,
            // three levels and not two: open, unread, ordinary. an unread channel has to stand out
            // without being confused with the one you are looking at
            color = when {
                actif -> tokens.textPrimary
                nonLu -> tokens.textPrimary
                else -> tokens.textSecondary
            },
            fontWeight = if (nonLu || actif) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // who is already in the voice room, like on the phone: without that count an occupied voice
        // room and an empty one look exactly alike, and nobody joins
        if (enVocal.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icone(Icones.membres, tint = tokens.textMuted, taille = 13.dp, description = null)
                Spacer(Modifier.width(3.dp))
                Text(enVocal.size.toString(), color = tokens.textMuted, fontSize = 12.sp)
            }
        }
        if (actif) {
            Icone(
                Icones.inviter, tint = tokens.textSecondary, taille = 16.dp, description = t.inviterSurLeServeur,
                modifier = Modifier.curseurMain().clickable(onClick = onInviter),
            )
            Spacer(Modifier.width(8.dp))
            Icone(
                Icones.reglages, tint = tokens.textSecondary, taille = 16.dp, description = t.modifierLeSalon,
                modifier = Modifier.curseurMain().clickable(onClick = onModifier),
            )
        }
        if (salon.unread.notifications > 0) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(tokens.unreadBadge)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    salon.unread.notifications.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * the user bar, at the bottom of the channel column
 *
 * nothing like the phone's, whose measurements were taken to the pixel off a screenshot: over there
 * it is a floating card the avatar overhangs. here the reference puts a compact band, avatar,
 * nickname, state on two lines, then the bar's three buttons, mic, headset, settings
 */
@Composable
internal fun BarreUtilisateurBureau(
    profil: Profile?,
    etendu: li.velvetchat.core.model.ProfilEtendu?,
    statut: Statut,
    onProfil: () -> Unit,
    modifier: Modifier = Modifier,
    /** do not disturb: it showed as online, the matrix presence staying `online` */
    silence: Boolean = false,
    onParametres: () -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current
    val decoration = etendu?.decoration
    val survol = remember { MutableInteractionSource() }
    val survolee by survol.collectIsHoveredAsState()
    // measured on the reference: x 8 to 429, y 976 to 1031, so 56 tall, stuck to the bottom of the
    // window, top corners rounded, a translucent light fill letting the wallpaper through
    Row(
        modifier.height(MINI_BARRE)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .hoverable(survol)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f)
                .clip(RoundedCornerShape(tokens.radiusSmall))
                .curseurMain().clickable(onClick = onProfil)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                userId = profil?.userId ?: "",
                displayName = profil?.displayName,
                avatarUrl = profil?.avatarUrl,
                taille = 32.dp,
                decoration = decoration,
                presence = statut.presence,
                silence = silence,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                // the nickname in its own style: the gradient chosen in the profile, like on the
                // reference
                val degrade = degradePseudo(etendu)
                Text(
                    profil?.displayName ?: profil?.userId?.removePrefix("@")?.substringBefore(':')
                        ?: "…",
                    color = tokens.textPrimary,
                    style = androidx.compose.ui.text.TextStyle(brush = degrade),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // underneath: the status, replaced by the id when the bar is hovered, like the
                // reference
                Text(
                    if (survolee) profil?.userId?.removePrefix("@")?.substringBefore(':').orEmpty()
                    else statut.message?.takeIf { it.isNotBlank() } ?: if (silence) t.statutNePasDeranger else libellePresence(statut),
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // on the right: mic with a chevron, headset with a chevron, settings. the gamepad for
        // activities is removed while activities do not exist. mic and headset mute (barred in red,
        // remembered in the settings), and the chevron picks the device
        BoutonBarre(
            if (Voix.microCoupe || Voix.sonCoupe) Icones.microCoupe else Icones.micro,
            if (Voix.microCoupe) t.reactiverLeMicro else t.couperLeMicro,
            coupe = Voix.microCoupe || Voix.sonCoupe,
            peripheriques = true,
            onClick = { Voix.basculerMicro() },
        )
        BoutonBarre(
            Icones.casque,
            if (Voix.sonCoupe) t.reactiverLeSon else t.mettreEnSourdine,
            coupe = Voix.sonCoupe,
            peripheriques = false,
            onClick = { Voix.basculerSon() },
        )
        BoutonBarre(Icones.reglages, t.parametresUtilisateur, onClick = onParametres)
    }
}

/** the mini bar's height, measured y 976 to 1031 */
internal val MINI_BARRE = 56.dp

@Composable
private fun BoutonBarre(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    coupe: Boolean = false,
    /** chevron to the device list: true for input, false for output, null for no chevron */
    peripheriques: Boolean? = null,
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val rouge = Color(0xFFF23F43)
    var liste by remember { mutableStateOf(false) }
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(tokens.radiusSmall))
            .background(if (coupe) rouge.copy(alpha = 0.15f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight().curseurMain().clickable(onClick = onClick).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icone(icone, tint = if (coupe) rouge else Color(0xFFF4F4F4), taille = 20.dp, description = description)
            // the headset has no barred variant in the icon set, so the bar is drawn
            //
            // top left to bottom right, the way `ic_microphone_slash` draws its own. it used to
            // go the other way, so the two buttons sat side by side with their bars crossing each
            // other. reported, and right: two icons that mean the same thing have to look like it
            if (coupe && icone == Icones.casque) {
                Box(
                    Modifier.size(20.dp).drawBehind {
                        drawLine(
                            rouge,
                            Offset(2f, 2f),
                            Offset(size.width - 2f, size.height - 2f),
                            strokeWidth = 2.dp.toPx(),
                        )
                    },
                )
            }
        }
        if (peripheriques != null) {
            Box {
                Box(
                    Modifier.fillMaxHeight().curseurMain().clickable { liste = !liste }.padding(end = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Icone(Icones.replier, tint = tokens.textSecondary, taille = 12.dp, description = t.peripheriques) }
                if (liste) {
                    androidx.compose.ui.window.Popup(
                        alignment = Alignment.BottomCenter,
                        offset = IntOffset(0, -40),
                        onDismissRequest = { liste = false },
                        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier.width(300.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B1D22))
                                .border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(8.dp)).padding(8.dp),
                        ) {
                            Text(
                                if (peripheriques) t.peripheriqueDEntree else t.peripheriqueDeSortie,
                                color = tokens.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                            )
                            val appareils = remember { Voix.peripheriques(peripheriques) }
                            ListePeripheriques(appareils, if (peripheriques) Voix.entree else Voix.sortie) {
                                if (peripheriques) Voix.choisirEntree(it) else Voix.choisirSortie(it)
                                liste = false
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun libellePresence(statut: Statut): String =
    when (statut.presence) {
        li.velvetchat.core.model.Presence.EN_LIGNE -> horsUi.statutEnLigne
        li.velvetchat.core.model.Presence.ABSENT -> horsUi.statutInactif
        li.velvetchat.core.model.Presence.NE_PAS_DERANGER -> horsUi.statutNePasDeranger
        li.velvetchat.core.model.Presence.INVISIBLE -> horsUi.statutInvisible
        li.velvetchat.core.model.Presence.INCONNU -> ""
    }

/**
 * a room's header: its name, its topic, and the actions on the right
 *
 * the topic is separated from the name by a vertical rule and not by a dash: that is what the
 * reference does, and it tells the name, short and bold, from the topic, which is a sentence, at a
 * glance
 */
@Composable
fun EnteteSalon(
    salon: Channel,
    membresOuverts: Boolean,
    /** for the reference's "search <server>" field */
    nomServeur: String? = null,
    /** the pinned messages, for the pin panel. null means not read yet */
    epingles: List<li.velvetchat.core.model.Message>? = null,
    onDesepingler: (li.velvetchat.core.model.Message) -> Unit = {},
    /** the threads open in this room, for the thread panel. null means not read yet */
    fils: List<li.velvetchat.core.model.Fil>? = null,
    /** what this room rings for, and what changes it */
    mode: ModeNotification? = null,
    onMode: (ModeNotification) -> Unit = {},
    /** a thread picked from the list: open it as a side panel */
    onOuvrirFil: (li.velvetchat.core.model.Fil) -> Unit = {},
    /** create, from the thread list: a new thread panel */
    onCreerFil: () -> Unit = {},
    /** enter in the search field */
    onRechercher: (String) -> Unit = {},
    alertes: suspend () -> List<li.velvetchat.core.model.AlerteSalon> = { emptyList() },
    onAlerte: (li.velvetchat.core.model.AlerteSalon) -> Unit = {},
    onMembres: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().height(ENTETE).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(
            if (salon.kind == ChannelKind.VOICE) Icones.salonVocal else Icones.salon,
            tint = tokens.textMuted,
            taille = 22.dp,
            description = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            salon.name,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        salon.topic?.takeIf { it.isNotBlank() }?.let { sujet ->
            // no rule between the name and the topic: the reference puts them side by side
            Spacer(Modifier.width(16.dp))
            Text(
                sujet,
                color = tokens.textMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.weight(1f))
        // in the reference's order. what is not wired up yet does nothing rather than lie: threads,
        // notifications, pins, inbox
        Box {
            var filsOuverts by remember { mutableStateOf(false) }
            ActionEntete(Icones.fils, t.fils, actif = filsOuverts) { filsOuverts = !filsOuverts }
            if (filsOuverts) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, 42),
                    onDismissRequest = { filsOuverts = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                ) {
                    PopoverFils(fils, onCreer = { filsOuverts = false; onCreerFil() }) { filsOuverts = false; onOuvrirFil(it) }
                }
            }
        }
        Box {
            var notifsOuvertes by remember { mutableStateOf(false) }
            ActionEntete(
                // the bell is barred when the room is muted: the state reads without opening the
                // menu
                if (mode == ModeNotification.MUET) {
                    Icones.silence
                } else {
                    Icones.notificationsActif
                },
                t.parametresDeNotification,
                actif = notifsOuvertes,
            ) { notifsOuvertes = !notifsOuvertes }
            if (notifsOuvertes) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, 42),
                    onDismissRequest = { notifsOuvertes = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                ) {
                    MenuNotifications(mode, onMode, salon.id) { notifsOuvertes = false }
                }
            }
        }
        Box {
            var epinglesOuvertes by remember { mutableStateOf(false) }
            ActionEntete(
                Icones.epingleActif,
                t.messagesEpingles,
                actif = epinglesOuvertes,
            ) { epinglesOuvertes = !epinglesOuvertes }
            if (epinglesOuvertes) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopEnd,
                    // under the button, aligned on its right edge: the panel is 520 wide and
                    // therefore extends leftwards, like on the reference
                    offset = IntOffset(0, 42),
                    onDismissRequest = { epinglesOuvertes = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                ) {
                    PopoverEpingles(epingles, onDesepingler)
                }
            }
        }
        ActionEntete(
            Icones.membres,
            if (membresOuverts) t.masquerLesMembres else t.afficherLesMembres,
            actif = membresOuverts,
            onClick = onMembres,
        )
        Spacer(Modifier.width(8.dp))
        ChampRechercheEntete(
        if (nomServeur != null) t.rechercherDansServeur(nomServeur) else t.rechercher,
        onRechercher,
    )
        Spacer(Modifier.width(8.dp))
        BoutonBoiteReception(alertes, onAlerte)
    }
}

/**
 * the header's search field: about 240 by 32, rounded, magnifier on the right
 *
 * enter searches the open room, with a results panel on the right. the label reuses the server's
 * name, truncated like on the reference
 */
@Composable
internal fun ChampRechercheEntete(libelle: String, onRechercher: (String) -> Unit = {}) {
    val tokens = LocalTokens.current
    var texte by remember { mutableStateOf("") }
    Row(
        Modifier.width(240.dp).height(32.dp)
            .clip(RoundedCornerShape(tokens.radiusSmall))
            .background(tokens.surfaceRaised)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (texte.isEmpty()) {
                Text(libelle, color = tokens.textMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = texte,
                onValueChange = { texte = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = tokens.textPrimary, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.textPrimary),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                    if (it.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (it.key) {
                        androidx.compose.ui.input.key.Key.Enter -> { if (texte.isNotBlank()) onRechercher(texte.trim()); true }
                        androidx.compose.ui.input.key.Key.Escape -> { texte = ""; true }
                        else -> false
                    }
                },
            )
        }
        Icone(Icones.recherche, tint = tokens.textSecondary, taille = 16.dp, description = null)
    }
}

@Composable
internal fun ActionEntete(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    actif: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(tokens.radiusSmall))
            .curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icone(
            icone,
            tint = if (actif) tokens.textPrimary else tokens.textSecondary,
            taille = 20.dp,
            description = description,
        )
    }
}

/**
 * the server menu, under the name and its chevron
 *
 * what is not in it, and why
 *
 * the reference puts five entries there with no purpose here: server boost and analytics, a
 * marketplace and a commercial dashboard, the app directory, third party bots, and the security
 * actions and raid reporting, a public platform's anti raid tooling. velvet is a small server of
 * ten people, and those entries could only be greyed out in perpetuity. the same decision as server
 * boosts in the column
 *
 * the rest is drawn with no effect, and that is the house rule rather than an oversight: the
 * desktop has no server settings screen at all (checked, not one mention on the `app-desktop`
 * side), so server settings has nowhere to lead. only copy id acts, because it can
 *
 * the two checkboxes are shown off and inactive instead of toggling for show: a box that lights up
 * without changing anything lies more than a greyed one
 */
@Composable
private fun MenuServeurBureau(
    serveur: Server?,
    actions: ActionsServeur,
    masquerMuets: Boolean,
    onMasquerMuets: () -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val pressePapier = copieur()
    var sousNotifs by remember { mutableStateOf(false) }
    fun puis(action: () -> Unit): () -> Unit = { onFermer(); action() }
    Column(
        // 275 rather than the reference's 220: our font is wider
        Modifier.width(275.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FOND_MENU)
            .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        EntreeMenu(t.tagDuServeur, icone = Icones.bouclier, onClick = puis(actions.onTag))
        FiletMenu()
        EntreeMenu(t.inviterSurLeServeur, icone = Icones.inviter, onClick = puis(actions.onInviter))
        EntreeMenu(t.parametresDuServeur, icone = Icones.reglages, onClick = puis(actions.onParametres))
        FiletMenu()
        EntreeMenu(t.creerSalon, icone = Icones.ajouter, onClick = puis(actions.onCreerSalon))
        EntreeMenu(t.creerUneCategorie, icone = Icones.plusGras, onClick = puis(actions.onCreerCategorie))
        EntreeMenu(t.creerUnEvenement, icone = Icones.evenements, actif = false, onClick = onFermer)
        FiletMenu()
        EntreeMenu(t.montrerTousLesSalons, icone = Icones.salon, case = false, actif = false, onClick = {})
        // the server's notification setting, which was drawn and inert
        //
        // matrix has no per space push rule, the rules cover rooms. a server's setting is therefore
        // a loop over its rooms, the same one the phone already does, and "use category default"
        // removes their own rules, which returns each room to the account's general setting
        if (sousNotifs) {
            EntreeMenu(t.retour, icone = Icones.retour, onClick = { sousNotifs = false })
            FiletMenu()
            listOf(
                ModeNotification.DEFAUT to t.utiliserCategorieParDefaut,
                ModeNotification.TOUT to t.tousLesMessages,
                ModeNotification.MENTIONS to t.mentionsSeulement,
                ModeNotification.MUET to t.aucunMessage,
            ).forEach { (valeur, libelle) ->
                EntreeMenu(libelle, onClick = puis { actions.onNotifications(valeur) })
            }
            return@Column
        }
        EntreeMenu(
            t.parametresDeNotifications,
            icone = Icones.notifications,
            sousMenu = true,
            actif = serveur != null,
            onClick = { sousNotifs = true },
        )
        EntreeMenu(t.parametresDeConfidentialite, icone = Icones.bouclier, sousMenu = true, actif = false, onClick = onFermer)
        FiletMenu()
        EntreeMenu(t.modifierProfilParServeur, icone = Icones.modifier, actif = false, onClick = onFermer)
        EntreeMenu(t.masquerSalonsMuets, icone = Icones.silence, case = masquerMuets, onClick = onMasquerMuets)
        FiletMenu()
        FiletMenu()
        // leaving is all matrix allows: a space has no delete for everybody. you leave all of its
        // rooms and forget it, so it disappears from here. two clicks, since coming back costs a
        // new invite
        var confirmeDepart by remember { mutableStateOf(false) }
        EntreeMenu(
            if (confirmeDepart) t.confirmerLeDepart else t.quitterLeServeur,
            icone = Icones.supprimer,
            couleur = LocalTokens.current.danger,
            actif = serveur != null,
            onClick = { if (confirmeDepart) { onFermer(); actions.onQuitter() } else confirmeDepart = true },
        )
        // for everybody, so only offered to whoever is allowed to do it
        actions.onSupprimer?.let { supprimer ->
            var confirmeSuppression by remember { mutableStateOf(false) }
            EntreeMenu(
                if (confirmeSuppression) t.confirmerLaSuppression else t.supprimerLeServeur,
                icone = Icones.supprimer,
                couleur = LocalTokens.current.danger,
                onClick = { if (confirmeSuppression) { onFermer(); supprimer() } else confirmeSuppression = true },
            )
        }
        FiletMenu()
        EntreeMenu(
            t.copierIdServeur,
            pastilleId = true,
            actif = serveur != null,
            onClick = puis { serveur?.let { pressePapier(it.id) } },
        )
    }
}

/** what the server menu and the header's invite icon trigger, supplied by the desktop */
class ActionsServeur(
    /** someone in a voice room was clicked: their card, at the row's place in the window */
    val onMembreVocal: (userId: String, x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp) -> Unit = { _, _, _ -> },
    val onTag: () -> Unit = {},
    val onInviter: () -> Unit = {},
    val onParametres: () -> Unit = {},
    val onCreerSalon: () -> Unit = {},
    val onCreerCategorie: () -> Unit = {},
    val onModifierSalon: (Channel) -> Unit = {},
    /** removes the channel from the server and leaves it. confirmed twice in the menu */
    val onSupprimerSalon: (Channel) -> Unit = {},
    /** create a voice channel: the same modal, with the type already chosen */
    val onCreerSalonVocal: () -> Unit = {},
    /** files the channel into a category, null for the server's root */
    val onDeplacerSalon: (Channel, Categorie?) -> Unit = { _, _ -> },
    /** the wanted order for a parent's channels, the server, or a category's sub space */
    val onReordonnerSalons: (parentId: String, salons: List<String>) -> Unit = { _, _ -> },
    /** the mode chosen for every channel on the server */
    val onNotifications: (ModeNotification) -> Unit = {},
    /**
     * the mode chosen for one channel, from its right click menu
     *
     * separate from [onNotifications], which loops over the whole server for want of a per space
     * push rule. this one writes a single room's rule, which is what the protocol actually has
     */
    val onModeSalon: (Channel, ModeNotification) -> Unit = { _, _ -> },
    val onQuitter: () -> Unit = {},
    /** deletes the server for everybody. null when we are not an admin of it, which hides it */
    val onSupprimer: (() -> Unit)? = null,
)

/**
 * a room's notification menu, under the header's bell
 *
 * four choices and not three: "use category default" is not a synonym for "all messages". the first
 * says this room has no opinion, no push rule is its own, so it follows the general setting
 * including when that changes, and the second pins the room to everything. the protocol carries the
 * distinction natively, an absent rule is the default. that is why the first one's subtitle recalls
 * what the default is worth today, as the reference does
 *
 * mute the room opens the reference's durations, fifteen minutes to forever, kept as a deadline in
 * the account data (`setSourdine`), the same as on a dm
 */
@Composable
private fun MenuNotifications(
    mode: ModeNotification?,
    onMode: (ModeNotification) -> Unit,
    salonId: String? = null,
    onFermer: () -> Unit,
) {
    val t = textes()
    var choisitDuree by remember { mutableStateOf(false) }
    Column(
        // 275 rather than the reference's 245: seen on screen, the longest entry truncated at that
        // width, the choice dot taking its space on the right. the same lesson as the message menu,
        // which went from 246 to 280. our font is wider than the reference's, and a measurement
        // reused as is cuts the text it was supposed to contain
        Modifier.width(275.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FOND_MENU)
            .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        if (choisitDuree && salonId != null) {
            SousMenuSourdine(salonId, onRetour = { choisitDuree = false }, onFermer = onFermer)
            return@Column
        }
        EntreeMenu(t.rendreLeSalonMuet, sousMenu = true, onClick = { if (salonId != null) choisitDuree = true else onFermer() })
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
                // nothing ticked until it has been read: better no dot than a wrong one
                radio = mode?.let { it == valeur } ?: false,
                onClick = { onMode(valeur); onFermer() },
            )
        }
    }
}

/**
 * the threads panel, under the header's thread icon
 *
 * the engine already knows how to read them (`filsFlow`, the thread msc being stable), and the
 * header showed nothing. the search covers the thread's title, which is to say the message that
 * opened it, since a matrix thread has no name of its own: what the reference calls a thread name
 * does not exist in the protocol, and inventing a house field would make it invisible elsewhere
 *
 * create opens the new thread panel: its name becomes the thread's originating message
 */
@Composable
private fun PopoverFils(
    fils: List<li.velvetchat.core.model.Fil>?,
    onCreer: () -> Unit = {},
    onOuvrir: (li.velvetchat.core.model.Fil) -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current
    val horodateur = remember { Horodateur() }
    var recherche by remember { mutableStateOf("") }
    val affiches = remember(fils, recherche) {
        val cle = recherche.trim().sansAccents()
        fils.orEmpty().filter { cle.isEmpty() || it.titre.sansAccents().contains(cle) }
    }
    Column(
        Modifier.width(600.dp).heightIn(max = 450.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1B1D22))
            .border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(8.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(Icones.fils, tint = tokens.textPrimary, taille = 18.dp, description = null)
            Spacer(Modifier.width(8.dp))
            Text(t.fils, color = tokens.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp))
                    .border(1.dp, DORE, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (recherche.isEmpty()) {
                        Text(t.rechercherNomDeFil, color = tokens.textMuted, fontSize = 13.sp)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = recherche,
                        onValueChange = { recherche = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = tokens.textPrimary, fontSize = 13.sp,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(DORE),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Icone(Icones.recherche, tint = DORE, taille = 14.dp, description = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                t.creer,
                color = Color(0xFF1B1D22),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(DORE)
                    .curseurMain().clickable(onClick = onCreer)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        when {
            fils == null -> Text(
                t.lectureDesFils,
                color = tokens.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp),
            )

            affiches.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icone(Icones.fils, tint = tokens.textMuted, taille = 44.dp, description = null)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (fils.isEmpty()) t.aucunFil else t.aucunFilDeCeNom,
                    color = BARRE_SELECTION,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    t.filExplication,
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                )
            }

            else -> LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(affiches, key = { it.racine.id }) { fil ->
                    LigneFil(fil, horodateur) { onOuvrir(fil) }
                }
            }
        }
    }
}

/** a thread in the list: what opened it, how many replies, and when the last one was */
@Composable
private fun LigneFil(fil: li.velvetchat.core.model.Fil, horodateur: Horodateur, onClick: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF232529))
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            userId = fil.racine.sender,
            displayName = fil.racine.senderDisplayName,
            avatarUrl = fil.racine.senderAvatarUrl,
            taille = 32.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                fil.titre,
                color = tokens.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // the count excludes the root: that is how the sdk counts it and how people read
                // it, "3 replies" under a message and not "4 messages"
                t.reponsesNombre(fil.nombreReponses),
                color = tokens.textMuted,
                fontSize = 12.sp,
            )
        }
        fil.derniere?.let {
            Spacer(Modifier.width(8.dp))
            Text(horodateur.dateEtHeure(it.timestamp), color = tokens.textMuted, fontSize = 11.sp)
        }
    }
}

/**
 * the pinned messages panel, under the header's pin
 *
 * three states and not two, like on the phone: "we do not know yet", "there are none" and "the read
 * failed" all look alike on screen unless you separate them, and conflating them sends people
 * hunting a bug somewhere other than where it is. here a failure falls back on null, which the flow
 * never leaves, so it reads as not yet read, which is true either way
 *
 * a message's body is the conversation's (`CorpsDeLigne`) and not a remade excerpt: a pin shows the
 * message, with its images, its code and its quotes, otherwise it lies about what got pinned
 */
@Composable
internal fun PopoverEpingles(
    epingles: List<li.velvetchat.core.model.Message>?,
    onDesepingler: (li.velvetchat.core.model.Message) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val horodateur = remember { Horodateur() }
    Column(
        Modifier.width(520.dp).heightIn(max = 860.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1B1D22))
            .border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(8.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📌", fontSize = 16.sp, fontFamily = LocalPoliceEmoji.current)
            Spacer(Modifier.width(8.dp))
            Text(
                t.messagesEpingles,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
        when {
            epingles == null -> Text(
                t.lectureDesEpingles,
                color = tokens.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            )

            epingles.isEmpty() -> Text(
                t.aucunMessageEpingleAide,
                color = tokens.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            )

            else -> LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // most recently pinned to oldest: `pinnedFlow` returns the room's state in write
                // order, and the last pin placed is the one somebody just went looking for
                items(epingles.asReversed(), key = { it.id }) { message ->
                    CarteEpingle(message, horodateur) { onDesepingler(message) }
                }
            }
        }
    }
}

/** one pin: the author, when, the whole message, and a way to remove it */
@Composable
private fun CarteEpingle(
    message: li.velvetchat.core.model.Message,
    horodateur: Horodateur,
    onDesepingler: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF232529))
            .border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Avatar(
            userId = message.sender,
            displayName = message.senderDisplayName,
            avatarUrl = message.senderAvatarUrl,
            taille = 40.dp,
            // the decoration follows the person and not the screen: everywhere the desktop puts a
            // face, it puts what that person wears
            decoration = LocalProfils.current[message.sender]?.decoration,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.senderDisplayName
                        ?: message.sender.removePrefix("@").substringBefore(':'),
                    color = LocalRoleColors.current[message.sender] ?: tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                BadgeTagDe(message.sender)
                Spacer(Modifier.width(8.dp))
                // the full date and not "today at": a pin list takes messages out of their
                // conversation, so out of the day separator that carried the date
                Text(
                    horodateur.dateEtHeure(message.timestamp),
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            CorpsDeLigne(message)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(tokens.radiusSmall))
                .curseurMain().clickable(onClick = onDesepingler),
            contentAlignment = Alignment.Center,
        ) {
            Icone(
                Icones.epingleActif,
                tint = tokens.textSecondary,
                taille = 16.dp,
                description = t.desepingler,
            )
        }
    }
}

// `grouperSalons` now lives in `ui-commun`: the phone had its own version, inline and without the
// empty categories, and two groupings for one list means two possible orders for the same server

/**
 * places the menu at the clicked point, in window coordinates, and keeps it inside the window if it
 * would overflow at the bottom or the right
 *
 * the anchor compose provides is no use: inside a `LazyColumn` the `Popup`'s parent is the whole
 * list and not the row, so the anchor was the top of the list and the menu opened 160 px too high.
 * so the row records its own position in the window
 */
internal class AuCurseur(private val point: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        point.x.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)),
        point.y.coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height)),
    )
}

/**
 * measured off a right click on a channel: the fill, the outline, the rules, and the pink of delete
 */
internal val FOND_MENU = Color(0xFF1B1E23)
internal val BORD_MENU = Color(0xFF262A33)
private val FILET_MENU = Color(0xFF282D37)
internal val ROSE_MENU = Color(0xFFDE86A2)

/**
 * the right click menu on a channel, in the reference's order, minus view permissions and duplicate
 * channel, removed by decision
 *
 * wired: mark as read (greyed when there is nothing to read), copy the `matrix.to` link, copy the
 * id. the rest is drawn and closes the menu without doing anything. muting does not exist in the
 * model yet, and deleting a channel deserves its own confirmation
 */
@Composable
private fun MenuSalon(
    salon: Channel,
    onFermer: () -> Unit,
    onMarquerLu: () -> Unit,
    onSupprimer: () -> Unit = {},
    categories: List<Categorie> = emptyList(),
    onDeplacer: (Categorie?) -> Unit = {},
    onMode: (ModeNotification) -> Unit = {},
) {
    val t = textes()
    val pressePapier = copieur()
    // two clicks, like leaving a server: a removed channel does not come back on its own
    var confirme by remember { mutableStateOf(false) }
    var sousDeplacer by remember { mutableStateOf(false) }
    var sousNotifs by remember { mutableStateOf(false) }
    var sousSourdine by remember { mutableStateOf(false) }
    // the channel's own mode, read when the menu opens. it serves only to tick the right dot and
    // to fill the subtitle, which used to be the literal string "all messages" whatever the rule
    // actually was
    val moteur = li.velvetchat.app.ui.LocalEngine.current
    var mode by remember(salon.id) { mutableStateOf<ModeNotification?>(null) }
    LaunchedEffect(salon.id) {
        mode = runCatching { moteur.modeNotification(salon.id) }.getOrNull()
    }
    fun puis(action: () -> Unit): () -> Unit = { action(); onFermer() }
    Column(
        Modifier.width(254.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FOND_MENU)
            .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        EntreeMenu(t.marquerCommeLu, actif = salon.unread.hasUnread, onClick = puis(onMarquerLu))
        FiletMenu()
        EntreeMenu(t.inviterDansLeSalon, onClick = onFermer)
        EntreeMenu(t.epinglerLeSalon, onClick = onFermer)
        EntreeMenu(t.copierLeLien, onClick = puis {
            pressePapier("https://matrix.to/#/${salon.id}")
        })
        FiletMenu()
        if (sousSourdine) {
            SousMenuSourdine(salon.id, onRetour = { sousSourdine = false }, onFermer = onFermer)
            return@Column
        }
        // the durations, like on a dm: the deadline lives in the account data (`setSourdine`)
        EntreeMenu(t.rendreLeSalonMuet, sousMenu = true, onClick = { sousSourdine = true })
        // the notification mode, which was drawn with a subtitle hard coded to "all messages"
        //
        // a subtitle that states a value it never read is worse than none: it answers the question
        // you opened the menu to ask, and it answers it wrong. it now says what the rule is, and
        // nothing at all until the read comes back
        if (sousNotifs) {
            EntreeMenu(t.retour, icone = Icones.retour, onClick = { sousNotifs = false })
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
                    radio = mode?.let { it == valeur } ?: false,
                    onClick = puis { onMode(valeur) },
                )
            }
            return@Column
        }
        EntreeMenu(
            t.parametresDeNotification,
            sousTitre = mode?.libelle,
            sousMenu = true,
            onClick = { sousNotifs = true },
        )
        FiletMenu()
        // filing the channel, which only existed on the phone: `deplacerSalon` had been in the
        // contract from the start and no door on the desktop led to it
        if (categories.isNotEmpty()) {
            if (sousDeplacer) {
                EntreeMenu(t.retour, onClick = { sousDeplacer = false })
                FiletMenu()
                (listOf(null) + categories).forEach { cible ->
                    val ici = (cible?.nom ?: "") == (salon.categorie ?: "")
                    EntreeMenu(
                        cible?.nom ?: t.aucuneCategorie,
                        radio = ici,
                        actif = !ici,
                        onClick = { onFermer(); onDeplacer(cible) },
                    )
                }
                return@Column
            }
            EntreeMenu(t.deplacerVers, sousMenu = true, onClick = { sousDeplacer = true })
        }
        EntreeMenu(t.modifierLeSalon, onClick = onFermer)
        EntreeMenu(t.creerSalonTextuel, onClick = onFermer)
        EntreeMenu(
            if (confirme) t.confirmerLaSuppression else t.supprimerLeSalon,
            couleur = ROSE_MENU,
            onClick = { if (confirme) { onFermer(); onSupprimer() } else confirme = true },
        )
        FiletMenu()
        EntreeMenu(t.copierIdSalon, pastilleId = true, onClick = puis {
            pressePapier(salon.id)
        })
    }
}

@Composable
internal fun FiletMenu() {
    Box(Modifier.padding(horizontal = 4.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(FILET_MENU))
}

/** one entry: 35 tall, 14 point text 10 from the inner edge, a barely lightened hover */
@Composable
internal fun EntreeMenu(
    libelle: String,
    /** the icon on the left, beige like on a message's menu, and none on a channel's */
    icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actif: Boolean = true,
    couleur: Color? = null,
    sousTitre: String? = null,
    sousMenu: Boolean = false,
    /**
     * the dot of an exclusive choice: null for an ordinary entry, otherwise ticked or not
     *
     * gold like on the reference, and the colour is not an ornament: it tells a setting, which has
     * a state and gets read back, from an action, which has neither, at a glance. both live in the
     * same menu
     */
    radio: Boolean? = null,
    /**
     * the box of a setting that turns on or off. null for an ordinary entry
     *
     * square where the dot is exclusive: the shape already says whether you are choosing among
     * several or toggling one thing, before anybody has read the label
     */
    case: Boolean? = null,
    pastilleId: Boolean = false,
    /** 14 on a channel's menu, 13 on a message's, which is narrower */
    taille: androidx.compose.ui.unit.TextUnit = 14.sp,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    val survol by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 35.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (survol && actif) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .hoverable(interaction)
            .then(if (actif) Modifier.curseurMain().clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icone?.let {
            Icone(it, tint = couleur ?: tokens.textSecondary, taille = 18.dp, description = null)
            Spacer(Modifier.width(12.dp))
        }
        if (pastilleId) {
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF33363B))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                Text("ID", color = tokens.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                libelle,
                color = when {
                    !actif -> tokens.textMuted
                    couleur != null -> couleur
                    else -> tokens.textPrimary
                },
                fontSize = taille,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sousTitre?.let { Text(it, color = tokens.textMuted, fontSize = 12.sp, maxLines = 1) }
        }
        case?.let { coche ->
            Box(
                Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (coche) DORE else Color.Transparent)
                    .border(
                        2.dp,
                        if (coche) DORE else tokens.textMuted,
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
        radio?.let { coche ->
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .border(2.dp, if (coche) DORE else tokens.textMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (coche) Box(Modifier.size(8.dp).clip(CircleShape).background(DORE))
            }
        }
        if (sousMenu) {
            Icone(
                Icones.replier,
                tint = tokens.textSecondary,
                taille = 14.dp,
                description = null,
                modifier = Modifier.rotate(-90f),
            )
        }
    }
}

/**
 * the header's inbox: what has not been read, mentions first (the reference's mentions tab). a
 * click opens the conversation
 */
@Composable
internal fun BoutonBoiteReception(
    alertes: suspend () -> List<li.velvetchat.core.model.AlerteSalon>,
    onOuvrir: (li.velvetchat.core.model.AlerteSalon) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var ouverte by remember { mutableStateOf(false) }
    Box {
        ActionEntete(Icones.boiteReception, t.boiteDeReception, actif = ouverte) { ouverte = !ouverte }
        if (!ouverte) return@Box
        var liste by remember { mutableStateOf<List<li.velvetchat.core.model.AlerteSalon>?>(null) }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            liste = runCatching { alertes() }.getOrDefault(emptyList())
                .sortedWith(compareByDescending<li.velvetchat.core.model.AlerteSalon> { it.mentions > 0 }.thenByDescending { it.horodatage })
        }
        androidx.compose.ui.window.Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(0, 42),
            onDismissRequest = { ouverte = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        ) {
            Column(
                Modifier.width(480.dp).heightIn(max = 520.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B1D22)).border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(8.dp)),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icone(Icones.boiteReception, tint = tokens.textPrimary, taille = 18.dp, description = null)
                    Spacer(Modifier.width(8.dp))
                    Text(t.boiteDeReception, color = tokens.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                val l = liste
                when {
                    l == null -> Text(t.lectureEnCours, color = tokens.textMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    l.isEmpty() -> Text(t.toutEstLu, color = tokens.textMuted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                    else -> Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        l.forEach { a ->
                            Row(
                                Modifier.fillMaxWidth().curseurMain().clickable { ouverte = false; onOuvrir(a) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (if (a.estDirect) "@" else "#") + a.nom,
                                        color = tokens.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        listOfNotNull(a.auteur, a.apercu).joinToString(" : "),
                                        color = tokens.textSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (a.mentions > 0) "@${a.mentions}" else a.nonLus.toString(),
                                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (a.mentions > 0) tokens.unreadBadge else Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** a person sitting in a voice room: avatar, nickname, and their tag if they wear one */
@Composable
private fun LigneDansLeVocal(
    userId: String,
    salonId: String,
    onOuvrir: (String, androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp) -> Unit,
) {
    val tokens = LocalTokens.current
    val membre = LocalMembres.current.firstOrNull { it.userId == userId }
    // what the call knows about this person, only when i am in this very room: livekit tells who
    // speaks and whose microphone is muted to the people connected, and to nobody else
    val appel = AppelBureau.etat
    val ici = appel.phase == AppelBureau.Phase.EN_APPEL && appel.salonId == salonId
    val moi = userId == LocalMonIdentifiant.current
    val personne = if (ici && !moi) appel.personnes.firstOrNull { it.userId == userId } else null
    val parle = ici && (if (moi) appel.moiParle else personne?.parle == true)
    val micCoupe = if (moi) Voix.microCoupe || Voix.sonCoupe else personne?.micCoupe == true
    val sourd = moi && Voix.sonCoupe
    val muetLocal = !moi && userId in AppelBureau.muetsLocaux
    val survol = remember { MutableInteractionSource() }
    val survole by survol.collectIsHoveredAsState()
    val densite = androidx.compose.ui.platform.LocalDensity.current
    val position = remember { floatArrayOf(0f, 0f, 0f) }
    var menu by remember { mutableStateOf<IntOffset?>(null) }
    // the reference's right click on somebody in the call. only "mute" for now, the one entry
    // that acts on what i hear; the rest of its menu is the member card's
    menu?.let { ou ->
        Popup(
            popupPositionProvider = AuCurseur(ou),
            onDismissRequest = { menu = null },
            properties = PopupProperties(focusable = true),
        ) {
            Column(
                Modifier.width(220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FOND_MENU)
                    .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                EntreeMenu(textes().rendreMuet, case = muetLocal, onClick = {
                    AppelBureau.basculerMuetLocal(userId)
                    menu = null
                })
            }
        }
    }
    // the reference's voice row: 32 tall, a 24 avatar ringed green while speaking, 8 to the name,
    // the state icons at 16 on the right, a hover fill, and a click opening the person's card
    Row(
        Modifier.fillMaxWidth().padding(start = 30.dp, end = 8.dp).height(32.dp)
            .onGloballyPositioned {
                val o = it.positionInWindow()
                position[0] = o.x; position[1] = o.y; position[2] = it.size.width.toFloat()
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (survole) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .hoverable(survol)
            .pointerInput(ici, moi) {
                if (!ici || moi) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                            val p = e.changes.first().position
                            menu = IntOffset((position[0] + p.x).roundToInt(), (position[1] + p.y).roundToInt())
                        }
                    }
                }
            }
            .curseurMain()
            .clickable {
                with(densite) { onOuvrir(userId, (position[0] + position[2]).toDp() + 8.dp, position[1].toDp()) }
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp)
                .then(if (parle) Modifier.border(2.dp, VERT_VOIX, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                userId = userId,
                displayName = membre?.displayName,
                avatarUrl = membre?.avatarUrl,
                taille = if (parle) 18.dp else 24.dp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            membre?.displayName ?: userId.removePrefix("@").substringBefore(':'),
            color = if (parle || survole) tokens.textPrimary else tokens.textSecondary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(5.dp))
        BadgeTagDe(userId)
        Spacer(Modifier.weight(1f))
        if (micCoupe) {
            Icone(Icones.microCoupe, tint = tokens.textMuted, taille = 16.dp, description = null)
        }
        // muted by me: red, so it does not read as their own mute, like the reference
        if (muetLocal) {
            Spacer(Modifier.width(4.dp))
            Icone(Icones.microCoupe, tint = tokens.danger, taille = 16.dp, description = textes().rendreMuet)
        }
        if (sourd) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                Icone(Icones.casque, tint = tokens.textMuted, taille = 16.dp, description = null)
                Box(
                    Modifier.size(16.dp).drawBehind {
                        drawLine(tokens.textMuted, Offset(1f, 1f), Offset(size.width - 1f, size.height - 1f), strokeWidth = 1.5.dp.toPx())
                    },
                )
            }
        }
    }
}

/** "invite to the voice channel", under the people present, like the reference */
@Composable
private fun LigneInviterAuVocal(onInviter: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .curseurMain().clickable(onClick = onInviter)
            .padding(start = 30.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(Icones.inviter, tint = tokens.textMuted, taille = 16.dp, description = null)
        Spacer(Modifier.width(8.dp))
        Text(
            t.inviterDansLeSalonVocal,
            color = tokens.textMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icone(Icones.suite, tint = tokens.textMuted, taille = 13.dp, description = null)
    }
}

/**
 * the mute durations, shared by the channel's right click menu and the header's bell
 *
 * the same engine call as a dm's (`setSourdine`): the deadline lives in the account data, and
 * `salonsMuets` sweeps expired ones when it reads them
 */
@Composable
private fun SousMenuSourdine(salonId: String, onRetour: () -> Unit, onFermer: () -> Unit) {
    val t = textes()
    val moteur = li.velvetchat.app.ui.LocalEngine.current
    val portee = androidx.compose.runtime.rememberCoroutineScope()
    EntreeMenu(t.retour, icone = Icones.retour, onClick = onRetour)
    FiletMenu()
    li.velvetchat.app.ui.DureeSourdine.entries.forEach { duree ->
        EntreeMenu(
            when (duree) {
                li.velvetchat.app.ui.DureeSourdine.QUINZE -> t.sourdine15Min
                li.velvetchat.app.ui.DureeSourdine.UNE_HEURE -> t.sourdine1Heure
                li.velvetchat.app.ui.DureeSourdine.HUIT_HEURES -> t.sourdine8Heures
                li.velvetchat.app.ui.DureeSourdine.UN_JOUR -> t.sourdine24Heures
                li.velvetchat.app.ui.DureeSourdine.TOUJOURS -> t.sourdineToujours
            },
            onClick = {
                portee.launch {
                    runCatching {
                        moteur.setSourdine(salonId, duree.minutes?.let { System.currentTimeMillis() + it * 60_000L })
                    }
                }
                onFermer()
            },
        )
    }
}

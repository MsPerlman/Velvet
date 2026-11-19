package li.velvetchat.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.EncryptionState
import li.velvetchat.core.model.Extraits
import li.velvetchat.core.model.LienCite
import li.velvetchat.core.model.MediaKind
import li.velvetchat.core.model.MediaRef
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.Presence

/**
 * a room's details screen
 *
 * what discord puts behind the room's name, and which existed nowhere here: where to find what has
 * been shared without scrolling the whole conversation by hand
 *
 * ## four tabs, and not five
 *
 * the parity survey asked for five, members, media, pinned, threads, links. threads do not exist
 * yet (MSC3440), and a tab opening nothing would cost more than its absence: that is the rule that
 * got the bell removed from the user bar as long as no screen sat behind it. the tab will appear
 * the day it has something to show
 *
 * ## three tabs out of four cost no request
 *
 * media, links and pins are derived from the already loaded timeline, see `Extraits`, where that
 * logic lives in `commonMain` and tests without a screen
 *
 * media and links walk back through the history on their own, a page at a time, to the start of the
 * conversation or to the memory ceiling (`PLAFOND_INDEX`). so they are real inventories in the
 * common case, and the note about only seeing the loaded window now only shows when it is true,
 * which is when the ceiling has been reached
 *
 * what that does not replace: a server side index. discord queries its own and sees everything
 * without loading anything, and we could do the same the day `/search` is wired, and this screen
 * would have no ceiling at all
 *
 * ## the count in the tab
 *
 * each tab carries its number. that is not decoration: it saves opening a tab to find out it is
 * empty, which is the gesture this screen exists to spare
 */
@Composable
fun EcranDetailsSalon(
    channel: Channel,
    messages: List<Message>,
    /**
     * the pinned messages, bodies included, as the engine gathers them through a focused timeline.
     * `null` while they have not arrived
     */
    epingles: List<Message>?,
    /**
     * how many pins the room declares, read from its state (`pinnedMessages`)
     *
     * two sources for the same thing, and it is not a redundancy: this one says how many there are,
     * the other what they say. the bodies arrive through a timeline fetching them one by one, and
     * while they are not all there, a shorter list is not a complete list. without this count,
     * "loading" and "none" look exactly alike, which is what sent me looking for a fault where
     * there was none
     */
    epinglesAttendues: Int?,
    /** a read failure message, where applicable. shown rather than confused with "none" */
    epinglesEnErreur: String?,
    /** the threads open in this room. empty while nothing has been opened */
    fils: List<li.velvetchat.core.model.Fil>,
    onOuvrirFil: (li.velvetchat.core.model.Fil) -> Unit,
    membres: List<Member>,
    presences: Map<String, Presence>,
    onMembre: (Member) -> Unit,
    onOuvrirMedia: (Message) -> Unit,
    onCopierLien: (String) -> Unit,
    /**
     * goes back one page through the history. returns `true` when something is left before it
     *
     * `null` when the screen cannot paginate, the tabs then making do with the loaded window, which
     * was the whole screen's behaviour until now
     */
    onRemonter: (suspend () -> Boolean)? = null,
    onFermer: () -> Unit,
) {
    val tokens = LocalTokens.current

    // the three extractions are memoised on the message list: the timeline recomposes on every new
    // message received, and redoing three full walks each time would bog down a screen that only
    // reads
    val medias = remember(messages) { Extraits.medias(messages) }
    val liens = remember(messages) { Extraits.liens(messages) }
    val ancrees = remember(epingles) { epingles?.let { Extraits.epingles(it) } }

    var onglet by remember { mutableStateOf(OngletDetails.EPINGLES) }

    // media and links walk back through the history while any is left
    //
    // that was this screen's stated limit: you only saw the loaded window. over a two hundred
    // message conversation it covers everything, and over the 405,065 events of the discord import
    // it covers the last evening, and "the files, links and media are not indexed" is exactly what
    // that gives
    //
    // only on those two tabs: they are the only ones that inventory. the pins come from a focused
    // timeline, and the members and threads do not depend on the past
    //
    // the ceiling is not caution, it is a memory constraint: each page stays in the sdk's timeline,
    // and walking back 405,000 events on a phone would leave them all in it. `PLAFOND_INDEX` bounds
    // the walk, and the displayed counter says where we are rather than suggesting a complete
    // inventory when it is not
    var complet by remember(channel.id) { mutableStateOf(onRemonter == null) }
    var enRemontee by remember(channel.id) { mutableStateOf(false) }
    val inventorie = onglet == OngletDetails.MEDIAS || onglet == OngletDetails.LIENS

    LaunchedEffect(inventorie, complet, messages.size, channel.id) {
        if (!inventorie || complet || onRemonter == null) return@LaunchedEffect
        if (messages.size >= PLAFOND_INDEX) { complet = true; return@LaunchedEffect }
        enRemontee = true
        // one page per pass, and not a loop: `messages` is a key of this effect, so each page
        // received restarts it. the screen redraws in between, the counter advancing instead of
        // jumping, and leaving the tab stops the walk
        runCatching { onRemonter() }
            .onSuccess { reste -> if (!reste) complet = true }
            .onFailure { complet = true }
        enRemontee = false
    }

    // the "you only see the loaded window" note now only holds in two cases: when the screen cannot
    // paginate, and when the walk stopped at the ceiling. leaving it under a genuinely complete
    // inventory would cast doubt on an exact result
    val tronque = onRemonter == null || messages.size >= PLAFOND_INDEX

    // one instance for the whole screen: it carries its date formatters, and building one per row
    // would be the cost `calculerLignes` documents at length
    val horodateur = remember { Horodateur() }

    Column(Modifier.fillMaxSize().background(tokens.bg).statusBarsPadding()) {
        EnteteDetails(channel, membres.size, onFermer)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espM, vertical = tokens.espXs),
            horizontalArrangement = Arrangement.spacedBy(tokens.espXs),
        ) {
            OngletDetails.entries.forEach { valeur ->
                val compte = when (valeur) {
                    OngletDetails.EPINGLES -> epinglesAttendues ?: ancrees?.size ?: 0
                    OngletDetails.MEDIAS -> medias.size
                    OngletDetails.FILS -> fils.size
                    OngletDetails.LIENS -> liens.size
                    OngletDetails.MEMBRES -> membres.size
                }
                PuceOnglet(
                    libelle = valeur.libelle,
                    compte = compte,
                    actif = onglet == valeur,
                    modifier = Modifier.weight(1f),
                ) { onglet = valeur }
            }
        }

        when (onglet) {
            OngletDetails.EPINGLES -> ListeEpingles(
                ancrees, epinglesAttendues, epinglesEnErreur, horodateur,
            )
            OngletDetails.MEDIAS -> Column {
                BandeauRemontee(enRemontee, complet, messages.size)
                GrilleMedias(medias, tronque, onOuvrirMedia)
            }
            OngletDetails.FILS -> ListeFils(fils, horodateur, onOuvrirFil)
            OngletDetails.LIENS -> Column {
                BandeauRemontee(enRemontee, complet, messages.size)
                ListeLiens(liens, horodateur, tronque, onCopierLien)
            }
            OngletDetails.MEMBRES -> ListeMembres(membres, presences, onMembre)
        }
    }
}

/**
 * how many messages we agree to keep in memory to build the inventory
 *
 * each page walked back stays in the sdk's timeline: the bound is there for the phone's memory, not
 * out of principle. ten thousand covers several years of a private conversation, and stays well
 * short of a full discord import, hence the banner, which says which of the two cases you are
 * looking at
 */
private const val PLAFOND_INDEX = 10_000

/**
 * a status row above the inventory: how far back we have gone, and whether it is finished
 *
 * it replaces the general mention that you only see the loaded window, which was true but did not
 * say which one. here the number is real and it moves
 */
@Composable
private fun BandeauRemontee(enCours: Boolean, complet: Boolean, combien: Int) {
    val t = textes()
    val tokens = LocalTokens.current
    if (complet && !enCours) return
    Text(
        t.remonteeHistorique(combien),
        color = tokens.textMuted,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = tokens.espM, vertical = 6.dp),
    )
}

private enum class OngletDetails {
    EPINGLES,
    MEDIAS,
    /**
     * the fifth tab
     *
     * it was missing from the first pass, for a reason that held: threads did not exist, and a tab
     * that opens nothing costs more than its absence. it appears now that it has something to show,
     * which is exactly what that rule promises
     */
    FILS,
    LIENS,
    MEMBRES,
    ;

    val libelle: String get() = when (this) {
        EPINGLES -> horsUi.epingles
        MEDIAS -> horsUi.medias
        FILS -> horsUi.fils
        LIENS -> horsUi.liens
        MEMBRES -> horsUi.membres
    }
}

@Composable
private fun EnteteDetails(channel: Channel, combienDeMembres: Int, onFermer: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    Column(Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icone(
                Icones.fermer,
                tint = tokens.textSecondary,
                taille = 22.dp,
                description = t.fermer,
                modifier = Modifier.clickable(onClick = onFermer).padding(end = tokens.espM),
            )
            Icone(Icones.salon, tint = tokens.textMuted, taille = 20.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                channel.name,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.headingSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // the room's topic, which was displayed nowhere although the engine has been bringing it up
        // since the first batch. it is often the only sentence that says what a room is for
        channel.topic?.takeIf { it.isNotBlank() }?.let { sujet ->
            Text(
                sujet,
                color = tokens.textSecondary,
                fontSize = tokens.typography.bodySize.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // encryption gets said here, and it is the right place: on the conversation, a
            // permanent padlock becomes scenery nobody reads any more
            if (channel.encryption == EncryptionState.ENCRYPTED) {
                Icone(Icones.chiffrement, tint = tokens.success, taille = 14.dp)
                Spacer(Modifier.width(5.dp))
                Text(t.chiffreDeBoutEnBout, color = tokens.textMuted, fontSize = 12.sp)
                Text(" · ", color = tokens.textMuted, fontSize = 12.sp)
            }
            Text(
                t.membresCompte(combienDeMembres),
                color = tokens.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PuceOnglet(
    libelle: String,
    compte: Int,
    actif: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Column(
        modifier
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(if (actif) tokens.surfaceRaised else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = tokens.espS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            libelle,
            color = if (actif) tokens.textPrimary else tokens.textMuted,
            fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
            maxLines = 1,
        )
        // the count under the label and not beside it: at four tabs on a phone's width, "pinned 12"
        // on one line truncates before being readable
        Text(
            compte.toString(),
            color = if (actif) tokens.accent else tokens.textMuted,
            fontSize = 11.sp,
        )
    }
}

/** the note saying a list is not an inventory. placed at the bottom, not across */
@Composable
private fun NoteDeFenetre(supplement: String? = null) {
    val tokens = LocalTokens.current
    Text(
        listOfNotNull(supplement, Extraits.FENETRE_SEULEMENT).joinToString(" "),
        color = tokens.textMuted,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = tokens.espM),
    )
}

@Composable
private fun ListeEpingles(
    ancrees: List<Message>?,
    attendues: Int?,
    erreur: String?,
    horodateur: Horodateur,
) {
    val t = textes()
    // three states and not two. "we do not know yet", "there are none" and "the read failed" all
    // look alike on screen if you do not separate them, and confusing them sends people looking for
    // a fault somewhere other than where it is
    if (erreur != null) {
        Vide(t.epinglesIllisibles, erreur)
        return
    }
    val chargees = ancrees.orEmpty()
    if (chargees.size < (attendues ?: 0)) {
        Vide(
            if (attendues == 1) t.chargementEpingle
            else t.chargementEpinglesNombre(attendues ?: 0),
            t.epinglesUneParUne,
        )
        return
    }
    if (chargees.isEmpty()) {
        Vide(t.aucunMessageEpingle, t.appuiLongPourEpingler)
        return
    }
    val ancrees = chargees
    // no window note here, and that is the change: this list is complete. the engine goes and
    // fetches each pin wherever it is, unlike the media and the links, which stay dependent on what
    // is loaded
    LazyColumn(Modifier.fillMaxSize()) {
        items(ancrees, key = { it.id }) { message ->
            LigneMessageExtrait(message, horodateur)
        }
    }
}

/** a message in a list of extracts: who, when, and what it is */
@Composable
private fun LigneMessageExtrait(message: Message, horodateur: Horodateur) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = 10.dp),
    ) {
        Avatar(message.sender, message.senderDisplayName, message.senderAvatarUrl, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.senderDisplayName
                        ?: message.sender.removePrefix("@").substringBefore(':'),
                    color = couleurDeRole(message.sender) ?: tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    horodateur.horodater(message.timestamp),
                    color = tokens.textMuted,
                    fontSize = 11.sp,
                )
            }
            Text(
                apercuDe(message),
                color = tokens.textSecondary,
                fontSize = tokens.typography.bodySize.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun GrilleMedias(medias: List<Message>, tronque: Boolean, onOuvrir: (Message) -> Unit) {
    val t = textes()
    if (medias.isEmpty()) {
        Vide(t.aucunePhotoNiVideo, Extraits.FENETRE_SEULEMENT)
        return
    }
    val tokens = LocalTokens.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(tokens.espXs),
        verticalArrangement = Arrangement.spacedBy(tokens.espXs),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(medias.size, key = { medias[it].id }) { rang ->
            val message = medias[rang]
            val corps = message.body as? MessageBody.Media ?: return@items
            VignetteMedia(corps.media, corps.kind) { onOuvrir(message) }
        }
        if (tronque) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                NoteDeFenetre()
            }
        }
    }
}

/**
 * a square gallery thumbnail
 *
 * decoded at the thumbnail's width and not at the timeline's. a three column grid shows boxes of
 * some 150 points, and loading full width bitmaps into them would fill memory with pixels nobody
 * sees, and a media grid is precisely where there are many of them at once
 */
@Composable
private fun VignetteMedia(ref: MediaRef, kind: MediaKind, onOuvrir: () -> Unit) {
    val tokens = LocalTokens.current
    val engine = LocalEngine.current

    val cle = "vignette:${ref.source}"
    var image by remember(ref.source) { mutableStateOf<ImageBitmap?>(cacheMedia(cle)) }

    LaunchedEffect(ref.source) {
        if (image != null) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                decoderMedia(engine.mediaBytes(ref), LARGEUR_VIGNETTE_PX)
            }
        }.getOrNull()?.let { mettreEnCacheMedia(cle, it); image = it }
    }

    Box(
        Modifier.aspectRatio(1f)
            .clip(RoundedCornerShape(tokens.radiusSmall))
            .background(tokens.surfaceRaised)
            .clickable(onClick = onOuvrir),
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = ref.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // a video is told apart from a still image: with no mark, you tap expecting a photo and get
        // something else
        if (kind == MediaKind.VIDEO) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icone(Icones.lecture, tint = Color.White, taille = 13.dp)
            }
        }
    }
}

/** 3 columns on a phone screen: about 150 points, so about 450 pixels at 3x */
private const val LARGEUR_VIGNETTE_PX = 450

@Composable
private fun ListeLiens(
    liens: List<LienCite>,
    horodateur: Horodateur,
    tronque: Boolean,
    onCopier: (String) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    if (liens.isEmpty()) {
        Vide(t.aucunLienPartage, Extraits.FENETRE_SEULEMENT)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(liens, key = { it.url }) { lien ->
            Row(
                Modifier.fillMaxWidth()
                    // tapping copies, and does not open. opening would mean deciding what opens and
                    // under what confirmation, which is a security question and not a style one,
                    // and it is already the position held when rendering a message. copying is
                    // useful immediately and commits to nothing
                    .clickable { onCopier(lien.url) }
                    .padding(horizontal = tokens.espL, vertical = tokens.espM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(tokens.radiusSmall))
                        .background(tokens.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icone(Icones.lien, tint = tokens.textSecondary, taille = 17.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // the host first, because that is what you recognise a link you are looking for
                    // by, well before the path, which is often unreadable
                    Text(
                        lien.hote,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        lien.url,
                        color = tokens.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${lien.message.senderDisplayName
                            ?: lien.message.sender.removePrefix("@").substringBefore(':')}" +
                            " · ${horodateur.horodater(lien.message.timestamp)}",
                        color = tokens.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icone(Icones.copier, tint = tokens.textMuted, taille = 16.dp)
            }
        }
        if (tronque) item { NoteDeFenetre() }
    }
}

@Composable
private fun ListeMembres(
    membres: List<Member>,
    presences: Map<String, Presence>,
    onMembre: (Member) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    if (membres.isEmpty()) {
        Vide(t.aucunMembreAAfficher)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(membres, key = { it.userId }) { membre ->
            val profil = LocalProfils.current[membre.userId]
            Box(Modifier.fillMaxWidth()) {
                // the nameplate, as the row's background. it is the only one of the profile
                // ornaments that shows anywhere other than on a card, and that is its object: you
                // wear it to be recognised in a member list
                //
                // `Crop`: a nameplate has a ratio (448 by 84) and stretching it deforms it as soon
                // as the row does not have the same, see the user bar, same fix. and it stays under
                // the text, never over it
                profil?.plaque?.let {
                    Box(Modifier.matchParentSize()) {
                        EmojiEnLigne(
                            it,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onMembre(membre) }
                        .padding(horizontal = tokens.espL, vertical = tokens.espS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Avatar(
                            membre.userId,
                            membre.displayName,
                            membre.avatarUrl,
                            34.dp,
                            decoration = profil?.decoration,
                        )
                        PastillePresence(
                            presences[membre.userId] ?: Presence.INCONNU,
                            taille = 10.dp,
                            couleurDuFond = tokens.bg,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        membre.displayName
                            ?: membre.userId.removePrefix("@").substringBefore(':'),
                        color = couleurDeRole(membre.userId) ?: tokens.textPrimary,
                        fontSize = tokens.typography.bodySize.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    tagDe(profil)?.let {
                        Spacer(Modifier.width(6.dp))
                        BadgeTag(it)
                    }
                    Spacer(Modifier.weight(1f))
                    LocalRoleTiers.current[membre.userId]?.let { palier ->
                        Spacer(Modifier.width(8.dp))
                        palier.icon?.let { Text(it, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

/** a member's role colour, or `null` when they have none */
@Composable
private fun couleurDeRole(userId: String): Color? = LocalRoleColors.current[userId]

/**
 * what a message is, in one line, for a list of extracts
 *
 * a filename says nothing, "IMG_20260825.jpg" is indistinguishable from its neighbour. what you
 * want to read in a list of pins is what it is about: the caption when there is one, the medium's
 * nature otherwise. same reasoning as a quote's preview
 */
private fun apercuDe(message: Message): String = when (val corps = message.body) {
    is MessageBody.Text -> corps.text
    is MessageBody.Media -> corps.caption.takeIf {
        it.isNotBlank() && it != corps.media.filename
    } ?: when (corps.kind) {
        MediaKind.IMAGE -> horsUi.photo
        MediaKind.VIDEO -> horsUi.video
        MediaKind.AUDIO -> horsUi.messageVocal
        MediaKind.FILE -> corps.media.filename
    }

    is MessageBody.Poll -> corps.question
    MessageBody.Redacted -> horsUi.messageSupprimeMinuscule
    is MessageBody.Undecryptable -> horsUi.messageChiffreIllisible
    is MessageBody.System -> corps.phrase()
    is MessageBody.Unsupported -> corps.description
}

/**
 * the list of threads open in the room
 *
 * the title is the preview of the message that opened the thread, not a name: a matrix thread has
 * none, and inventing one would mean asking for it when creating one, which discord does not do
 * either. you recognise a thread by what set it off
 */
@Composable
private fun ListeFils(
    fils: List<li.velvetchat.core.model.Fil>,
    horodateur: Horodateur,
    onOuvrir: (li.velvetchat.core.model.Fil) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    if (fils.isEmpty()) {
        Vide(
            t.aucunFilDeDiscussion,
            t.appuiLongPourFil,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(fils, key = { it.racine.id }) { fil ->
            Row(
                Modifier.fillMaxWidth().clickable { onOuvrir(fil) }
                    .padding(horizontal = tokens.espL, vertical = tokens.espM),
            ) {
                Avatar(
                    fil.racine.sender,
                    fil.racine.senderDisplayName,
                    fil.racine.senderAvatarUrl,
                    32.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        fil.titre,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // the number of replies and the last activity: enough to decide whether the
                    // thread is worth opening, which is the only question you ask in front of this
                    // list
                    Text(
                        buildString {
                            append(
                                t.reponsesNombre(fil.nombreReponses)
                            )
                            fil.derniere?.let {
                                append(" · ")
                                append(horodateur.horodater(it.timestamp))
                            }
                        },
                        color = tokens.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    fil.derniere?.let { dernier ->
                        Text(
                            "${dernier.senderDisplayName
                                ?: dernier.sender.removePrefix("@").substringBefore(':')} : " +
                                apercuDe(dernier),
                            color = tokens.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icone(Icones.suite, tint = tokens.textMuted, taille = 15.dp)
            }
        }
    }
}

package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * a direct conversation, as the screen needs to know it
 *
 * defined here and not in the engine, on purpose: the interface gets built first, and it is the
 * interface that dictates what the contract will have to provide. the other way round, deriving the
 * screen from whatever the protocol makes convenient, ends in lists that show what was available
 * rather than what is useful
 *
 * every field matches something visible on the reference
 *
 * @param apercu the last line exchanged, or null. discord shows it for some conversations only, so
 *   it is optional data and not a box to fill by force
 * @param horodatage when the last activity happened, rendered as "18 m", "19 h", "2 j"
 * @param presence the coloured dot, and the most fragile piece of data in the list. matrix does
 *   have a notion of presence, but the sdk exposes no read for it and synapse happily turns it off
 *   for its cost. `INCONNU` draws nothing, which beats a grey dot suggesting an absence somebody
 *   observed
 */
data class ConversationPrivee(
    val roomId: String,
    val userId: String,
    val nom: String,
    val avatarUrl: String?,
    val apercu: String?,
    val horodatage: Long?,
    val nonLus: Int,
    /** put back to unread by hand, with nothing new to count */
    val marqueNonLu: Boolean = false,
    val epingle: Boolean,
    /** what the context menu needs and a list row does not draw */
    val groupe: Boolean = false,
    val ami: Boolean = false,
    val muet: Boolean = false,
    val presence: li.velvetchat.core.model.Presence =
        li.velvetchat.core.model.Presence.INCONNU,
)

/**
 * the direct messages screen
 *
 * the app's third level, next to the rail plus channels and the conversation. it keeps the rail,
 * unlike a conversation which hides it: you have not gone into anywhere, you are looking at another
 * list
 */
@Composable
fun EcranMessages(
    conversations: List<ConversationPrivee>,
    profil: li.velvetchat.core.model.Profile?,
    /**
     * the people the relation says are friends
     *
     * a filter and not a list. the friend list is built by filtering the conversations, never by
     * storing a second list alongside that could drift from the first
     */
    amis: Set<String> = emptySet(),
    /**
     * pending invites, put at the top of the list
     *
     * here and nowhere else because this is the front screen: an invite is what you should see
     * before choosing where to go. servers appear alongside conversations, since separating them
     * would make two places to watch for one question, who is waiting for me
     */
    invitations: List<li.velvetchat.core.model.InvitationRecue> = emptyList(),
    onInvitation: (li.velvetchat.core.model.InvitationRecue) -> Unit = {},
    /**
     * has the first load of conversations finished?
     *
     * false means we are still waiting for the first list, so we show a skeleton and never the "no
     * direct conversations" state. that one is a lie until something has been read, and it is
     * exactly what used to flash on open, enough to make people think something had been lost
     */
    charge: Boolean = true,
    /** the nameplate being worn, for the bottom bar. see `BarreUtilisateur` */
    plaque: String? = null,
    /** the avatar decoration being worn, passed down to the bottom bar */
    decoration: String? = null,
    statut: li.velvetchat.core.model.Statut =
        li.velvetchat.core.model.Statut.INCONNU,
    onStatut: () -> Unit = {},
    modifier: Modifier = Modifier,
    onOuvrir: (ConversationPrivee) -> Unit,
    onEpingler: (ConversationPrivee) -> Unit,
    onNouvelle: () -> Unit,
    /**
     * create a group conversation with these people
     *
     * the accent plus used to call [onNouvelle], the same thing as the "write to" row right beside
     * it: two controls, one action. so the plus was free, and a group is what the reference puts
     * behind it
     */
    onNouveauGroupe: (List<String>) -> Unit = {},
    onProfil: () -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()
    var seulementAmis by remember { mutableStateOf(false) }
    var nouveauGroupe by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(topStart = tokens.radiusLarge))
            .background(tokens.surface)
    ) {
        Text(
            t.mpTitre,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = tokens.espL, bottom = 10.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "add friends" takes all the remaining width. it is the action that fills an empty
            // screen, so it is the one that has to jump out when the screen is empty
            Row(
                Modifier.weight(1f).height(42.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .curseurMain().clickable(onClick = onNouvelle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icone(Icones.inviter, tint = tokens.textSecondary, taille = 18.dp)
                Spacer(Modifier.width(8.dp))
                // a short label, and that is a real space constraint: the messages panel is
                // narrower than discord's, since the conversation sticks out to its right. the long
                // version arrived truncated, and a cut label is worse than a brief one, because it
                // makes you wonder what it is hiding
                Text(
                    t.ecrireA,
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            // the accent square, the only coloured thing in the strip. this is creation, and it
            // stands apart from the two actions that only filter or browse
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.accent).curseurMain().clickable { nouveauGroupe = true },
                contentAlignment = Alignment.Center,
            ) {
                Icone(Icones.ajouter, tint = Color.White, taille = 22.dp, description = t.nouveauGroupe)
            }
        }

        if (nouveauGroupe) {
            FeuilleNouveauGroupe(
                candidats = conversations,
                onCreer = { ids -> nouveauGroupe = false; onNouveauGroupe(ids) },
                onFermer = { nouveauGroupe = false },
            )
        }

        // invites before everything else. they are the only thing on this screen waiting for an
        // answer rather than a visit
        if (invitations.isNotEmpty()) {
            Column(Modifier.padding(top = 10.dp)) {
                Text(
                    if (invitations.size == 1) t.uneInvitation else t.invitationsNombre(invitations.size),
                    color = tokens.textMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 18.dp, bottom = tokens.espXs),
                )
                invitations.forEach { invitation ->
                    LigneInvitation(invitation) { onInvitation(invitation) }
                }
            }
        }

        // the filter only appears when it has something to filter. on an account with no declared
        // friends, two tabs one of which is always empty teach nothing, they just send people
        // looking for a feature with no content yet
        if (amis.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espM, vertical = tokens.espS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(false to t.tous, true to t.amis).forEach { (cible, libelle) ->
                    val actif = seulementAmis == cible
                    Text(
                        libelle,
                        color = if (actif) tokens.bg else tokens.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(end = tokens.espS)
                            .clip(RoundedCornerShape(tokens.radiusLarge))
                            .background(if (actif) tokens.accent else tokens.surfaceRaised)
                            .curseurMain().clickable { seulementAmis = cible }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }

        val conversations = remember(conversations, amis, seulementAmis) {
            if (seulementAmis) conversations.filter { it.userId in amis } else conversations
        }

        if (conversations.isEmpty() && !charge) {
            // first load: a skeleton, not the empty state. showing "no conversations" here would
            // assert there are none when we do not know yet. the skeleton says "it is coming"
            // without lying
            Column(Modifier.weight(1f).fillMaxWidth().padding(top = tokens.espS)) {
                repeat(6) { LigneMessageSquelette() }
            }
        } else if (conversations.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icone(Icones.messages, tint = tokens.textMuted, taille = 44.dp)
                Text(
                    t.mpAucune,
                    color = tokens.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    t.mpExplication,
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).padding(top = tokens.espS)) {
                // pinned first, then by descending activity. the sort happens here and not in the
                // caller: it is a display rule, and duplicating it into every data source would
                // eventually make it drift
                val ordonnees = conversations.sortedWith(
                    compareByDescending<ConversationPrivee> { it.epingle }
                        .thenByDescending { it.horodatage ?: 0L }
                )
                items(ordonnees, key = { it.roomId }) { conversation ->
                    LigneMessage(
                        conversation = conversation,
                        onEpingler = { onEpingler(conversation) },
                        onClick = { onOuvrir(conversation) },
                    )
                }
            }
        }

        // the user bar is not here any more. it lives at the level of the whole drawer, so its
        // avatar can overhang the server rail. see `Shell`
    }
}

/**
 * a pending invite: who, where, and what it is
 *
 * no buttons on the row, contrary to the first urge to put "accept" and "decline" there. two
 * reasons: accepting a server means joining all of its channels, which is something you want
 * written down before you do it, and two 40 point targets on a list row get hit for each other. so
 * the row opens a sheet, where the decision is made while reading it
 */
@Composable
private fun LigneInvitation(
    invitation: li.velvetchat.core.model.InvitationRecue,
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espS, vertical = 2.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised)
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = tokens.espS, vertical = tokens.espS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            userId = invitation.roomId,
            displayName = invitation.nom,
            avatarUrl = invitation.avatarUrl,
            taille = 40.dp,
        )
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                invitation.nom,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(
                        when {
                            invitation.estEspace -> t.invitationServeur
                            invitation.estDirect -> t.invitationConversation
                            else -> t.invitationGroupe
                        }
                    )
                    val par = invitation.nomDeInvitant ?: invitation.invitePar
                    if (par != null) append(t.invitationDePar(par))
                },
                color = tokens.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icone(Icones.suite, tint = tokens.textMuted, taille = 16.dp)
    }
}

/**
 * a ghost row, until the real list arrives
 *
 * no animation, fixed widths. a pulsing skeleton draws the eye and tires it, while this one just
 * says a conversation is about to land there. same metrics as `LigneMessage` so the swap does not
 * make the layout jump
 */
@Composable
private fun LigneMessageSquelette() {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(tokens.surfaceRaised))
        Column(Modifier.padding(start = tokens.espM)) {
            Box(
                Modifier.height(13.dp).width(140.dp)
                    .clip(RoundedCornerShape(tokens.radiusSmall)).background(tokens.surfaceRaised)
            )
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier.height(11.dp).width(220.dp)
                    .clip(RoundedCornerShape(tokens.radiusSmall)).background(tokens.surface)
            )
        }
    }
}

/**
 * one direct conversation row
 *
 * the preview only appears when there is one. on the reference some rows carry it and others do
 * not, which is not an oversight, the row contracts when it has nothing to say. reserving the space
 * would produce a list twice as tall as it needs to be
 *
 * the timestamp is relative and on the right, with the pin just before it: the two say "when" and
 * "why it is here", so they read together
 */
@Composable
private fun LigneMessage(
    conversation: ConversationPrivee,
    onEpingler: () -> Unit,
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val nonLu = conversation.nonLus > 0 || conversation.marqueNonLu

    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espS, vertical = 2.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            // long press pins, the same way it opens a message's actions: that gesture already
            // means "give me what i can do with this" elsewhere in the app. one action here, so no
            // sheet, since opening one for a single choice would add a gesture and offer nothing
            .pointerInput(conversation.roomId) {
                detectTapGestures(
                    // opens a sheet instead of pinning silently. the callback keeps the name
                    // `onEpingler` for call compatibility, and what it does is decided further up
                    onLongPress = { onEpingler() },
                    onTap = { onClick() },
                )
            }
            .padding(horizontal = tokens.espS, vertical = tokens.espS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(
                userId = conversation.userId,
                displayName = conversation.nom,
                avatarUrl = conversation.avatarUrl,
                taille = 40.dp,
            )
            // the dot lives in `Statut.kt` now, with the colour and the sheet that changes it. the
            // three would drift if they were spread out. an unknown state draws nothing, see
            // `PastillePresence`
            PastillePresence(conversation.presence, taille = 10.dp)
        }

        Column(Modifier.padding(start = tokens.espM, end = tokens.espS).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.nom,
                    color = if (nonLu) tokens.textPrimary else tokens.textSecondary,
                    fontWeight = if (nonLu) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // the tag follows the person, here included. this is arguably where it helps most,
                // a conversation list being made of names that look alike
                tagDe(LocalProfils.current[conversation.userId])?.let {
                    Spacer(Modifier.width(6.dp))
                    BadgeTag(it)
                }
            }
            conversation.apercu?.takeIf { it.isNotBlank() }?.let {
                // `:shortcodes:` are rendered as the emoji they name. the list used to show
                // `:blahaj:` in the clear where the conversation showed the image, and a shortcode
                // is not text somebody meant to write, it is the shape an emoji travels in. leaving
                // it as is means showing the plumbing
                ApercuAvecEmotes(
                    texte = it,
                    couleur = tokens.textMuted,
                    taille = 13.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }

        // the pin and the timestamp align on the first line, not on the centre of the block
        //
        // centred, they landed between the name and the preview, so next to nothing, and jammed
        // against the truncated name. they qualify the conversation, whose name is the title, so
        // they read at its height
        if (conversation.epingle) {
            Icone(
                Icones.epingleActif,
                tint = tokens.textMuted,
                taille = 13.dp,
                description = t.epinglee,
                modifier = Modifier.align(Alignment.Top).padding(top = 3.dp, end = 6.dp),
            )
        }
        conversation.horodatage?.let {
            Text(
                depuis(it),
                color = tokens.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Top).padding(top = 2.dp),
            )
        }
        if (nonLu) {
            Spacer(Modifier.width(6.dp))
            // a hand set flag counts nothing, so it gets a plain dot. writing 1 in the badge
            // would claim a message that does not exist, and you would open the conversation
            // looking for it
            if (conversation.nonLus == 0) {
                Box(
                    Modifier.padding(horizontal = 5.dp).size(8.dp)
                        .clip(CircleShape).background(tokens.unreadBadge),
                )
            } else {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(tokens.unreadBadge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (conversation.nonLus > 9) "9+" else "${conversation.nonLus}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * "how long ago", in one or two letters
 *
 * relative and not absolute: in a conversation list what matters is freshness, not the date. "18 m"
 * compares instantly against "2 j", where a full date and time needs a calculation on every row
 *
 * the units stop at days, like on the reference. past a month we keep counting days rather than
 * moving to weeks, since nobody reads "5 w" faster than "35 d", and it saves one more unit to
 * interpret
 */
private fun depuis(horodatage: Long): String {
    val ecart = (System.currentTimeMillis() - horodatage).coerceAtLeast(0)
    val minutes = ecart / 60_000
    return when {
        minutes < 1 -> horsUi.aLInstant
        minutes < 60 -> "$minutes m"
        minutes < 1_440 -> "${minutes / 60} h"
        else -> "${minutes / 1_440} j"
    }
}


/**
 * writing to somebody
 *
 * by matrix id and not by directory search. matrix does have an identity service, but it is
 * optional, often missing from self hosted servers, and it would publish the server's addresses at
 * a third party. the id gets passed along out loud anyway
 *
 * the domain is offered and not imposed. typing a bare localpart is enough for somebody on the same
 * server, which is the common case, and the full address stays accepted for writing elsewhere,
 * matrix being federated
 */
@Composable
fun FeuilleEcrireA(
    domaineParDefaut: String,
    onEcrire: suspend (String) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    var saisie by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    // what the id will actually become, shown under the field. nobody should find out afterwards
    // that a bare name grew a domain
    val complet = remember(saisie, domaineParDefaut) {
        val propre = saisie.trim().removePrefix("@")
        when {
            propre.isBlank() -> null
            propre.contains(':') -> "@$propre"
            else -> "@$propre:$domaineParDefaut"
        }
    }

    FeuilleBasse(onFermer) {
        Column(Modifier.padding(horizontal = tokens.espXl)) {
            Text(
                t.ecrireAQuelquun,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = tokens.espXs),
            )
            Text(
                t.identifiantMatrixAide,
                color = tokens.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            Box(
                Modifier.fillMaxWidth()
                    .background(tokens.surfaceRaised, RoundedCornerShape(tokens.radiusMedium))
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                if (saisie.isEmpty()) {
                    Text("marie", color = tokens.textMuted, fontSize = 15.sp)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = saisie,
                    onValueChange = { saisie = it.trim() },
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        color = tokens.textPrimary,
                        fontSize = 15.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                complet ?: " ",
                color = tokens.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )

            BoutonPlein(
                if (enCours) t.ouverture else t.ecrire,
                actif = complet != null && !enCours,
            ) {
                enCours = true
                erreur = null
                scope.launch {
                    runCatching { onEcrire(complet!!) }
                        .onSuccess { onFermer() }
                        .onFailure {
                            erreur = it.message ?: t.conversationNonOuverte
                            enCours = false
                        }
                }
            }

            erreur?.let {
                Text(
                    it,
                    color = tokens.danger,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = tokens.espM),
                )
            }
            Box(Modifier.padding(bottom = tokens.espL))
        }
    }
}

/**
 * answering an invite
 *
 * the text says what accepting entails, and for a server that is not nothing: you join all of its
 * channels too. the gesture is irreversible from the point of view of whoever made it, leaving
 * afterwards being possible but a different gesture with its own consequences. writing it before
 * beats discovering it after
 *
 * declining does not forget the room: the engine leaves without a `forget`, so being reinvited
 * stays possible. a "no thanks" must not turn into a closed door
 */
@Composable
fun FeuilleInvitationRecue(
    invitation: li.velvetchat.core.model.InvitationRecue,
    onAccepter: suspend () -> Unit,
    onRefuser: suspend () -> Unit,
    onFermer: () -> Unit,
    /**
     * a card sized to its contents instead of a sheet across the bottom
     *
     * a bottom sheet is right on a phone and wrong on a desktop, where it covered the window
     * for two buttons. reported, and reported correctly. on the desktop the card goes in the
     * empty space where a conversation would be, which is exactly the space that was free
     */
    compacte: Boolean = false,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    fun lancer(action: suspend () -> Unit) {
        if (enCours) return
        enCours = true
        erreur = null
        scope.launch {
            runCatching { action() }
                .onSuccess { onFermer() }
                .onFailure {
                    // said, never swallowed. an invite can have been revoked between being
                    // displayed and being answered, and "nothing happens" is the worst possible
                    // feedback
                    erreur = it.message ?: t.echec
                    enCours = false
                }
        }
    }

    if (compacte) {
        CorpsInvitation(invitation, enCours, erreur, ::lancer, onAccepter, onRefuser)
        return
    }
    FeuilleBasse(onFermer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(invitation.roomId, invitation.nom, invitation.avatarUrl, 48.dp)
            Column(Modifier.padding(start = tokens.espM).weight(1f)) {
                Text(
                    invitation.nom,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val par = invitation.nomDeInvitant ?: invitation.invitePar
                Text(
                    if (par != null) t.invitationDe(par) else t.invitation,
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                )
            }
        }

        Text(
            if (invitation.estEspace) {
                t.accepterServeurExplication
            } else {
                t.accepterConversationExplication
            },
            color = tokens.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )

        Text(
            if (enCours) "…" else textes().invitationAccepter,
            color = tokens.colors.success.toColor(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
                .curseurMain().clickable(enabled = !enCours) { lancer(onAccepter) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        )
        Text(
            textes().invitationRefuser,
            color = tokens.danger,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth()
                .curseurMain().clickable(enabled = !enCours) { lancer(onRefuser) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        )

        erreur?.let {
            Text(
                it,
                color = tokens.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = tokens.espS),
            )
        }
    }
}

/**
 * the same invitation, as a card that takes only the room it needs
 *
 * the desktop puts it where a conversation would be, so nothing is covered and the list stays
 * usable behind it. same words, same two actions, same error line as the sheet: only the frame
 * changes, because a phone and a window do not have the same idea of "at the bottom"
 */
@Composable
private fun CorpsInvitation(
    invitation: li.velvetchat.core.model.InvitationRecue,
    enCours: Boolean,
    erreur: String?,
    lancer: (suspend () -> Unit) -> Unit,
    onAccepter: suspend () -> Unit,
    onRefuser: suspend () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(380.dp)
                .clip(RoundedCornerShape(tokens.radiusLarge))
                .background(tokens.surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(invitation.roomId, invitation.nom, invitation.avatarUrl, 56.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                invitation.nom,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val par = invitation.nomDeInvitant ?: invitation.invitePar
            Text(
                if (par != null) t.invitationDe(par) else t.invitation,
                color = tokens.textMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (invitation.estEspace) {
                    t.accepterServeurExplication
                } else {
                    t.accepterConversationExplication
                },
                color = tokens.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (enCours) "…" else t.invitationAccepter,
                    color = tokens.colors.success.toColor(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(tokens.radiusMedium))
                        .curseurMain().clickable(enabled = !enCours) { lancer(onAccepter) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    t.invitationRefuser,
                    color = tokens.danger,
                    fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(tokens.radiusMedium))
                        .curseurMain().clickable(enabled = !enCours) { lancer(onRefuser) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            erreur?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = tokens.danger, fontSize = 12.sp)
            }
        }
    }
}

/**
 * the group conversation sheet: pick people, create
 *
 * no name asked, like the desktop's modal and like the reference: matrix has no group direct
 * message, it has a private unnamed room marked `is_direct`, and a name would freeze a list that
 * changes as people come and go
 *
 * two people is the floor and the button says so instead of failing on a tap. with one, the right
 * call is `openDirect`, which finds the conversation that already exists
 */
@Composable
private fun FeuilleNouveauGroupe(
    candidats: List<ConversationPrivee>,
    onCreer: (List<String>) -> Unit,
    onFermer: () -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()
    var choisis by remember { mutableStateOf(setOf<String>()) }
    FeuilleBasse(onFermer = onFermer) {
        Column(Modifier.padding(horizontal = tokens.espL, vertical = tokens.espM)) {
            Text(
                t.nouveauGroupe,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(tokens.espXs))
            Text(
                if (choisis.size >= 2) t.groupeChoisis(choisis.size) else t.groupeChoisirPersonnes,
                color = tokens.textMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(tokens.espM))
            ChoixPersonnes(
                candidats = candidats,
                choisis = choisis,
                onBasculer = { id -> choisis = if (id in choisis) choisis - id else choisis + id },
                // taller than the desktop's: a sheet has the screen's height to play with, and
                // scrolling a list inside a sheet that also drags is the fight we avoid elsewhere
                hauteurMax = 380.dp,
            )
            Spacer(Modifier.height(tokens.espM))
            val pret = choisis.size >= 2
            Text(
                t.creerLeGroupe,
                color = if (pret) Color.White else tokens.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(if (pret) tokens.accent else tokens.surfaceRaised)
                    .then(
                        if (pret) {
                            Modifier.curseurMain().clickable { onCreer(choisis.toList()) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 12.dp),
            )
        }
    }
}

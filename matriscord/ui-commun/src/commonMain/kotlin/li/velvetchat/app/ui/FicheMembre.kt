package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.ProfilEtendu
import li.velvetchat.core.model.RoleTier

/**
 * a member's card
 *
 * where this screen comes from: tapping a member used to open the moderation sheet directly. that
 * was an admin shortcut sitting where everybody expects a card, and it had two separate faults. it
 * offered to kick somebody you only wanted to write to, and it showed nothing about the person, no
 * status, no pronouns, no roles
 *
 * so moderation becomes an action inside the card rather than somebody's front door. it only shows
 * up when you have the power to use it, since offering something in order to refuse it afterwards
 * is a fault we removed elsewhere
 *
 * the header's gradient is the person's own, like on their profile screen, chosen if they chose one
 * and derived from their id otherwise. that is what makes a card recognisable before it is read
 */
@Composable
fun FicheMembre(
    membre: Member,
    etendu: ProfilEtendu,
    presence: Presence,
    messageDeStatut: String?,
    palier: RoleTier?,
    /**
     * the decorative roles this person wears, in order, see [RoleDecoratif]
     *
     * kept apart from the [palier] and drawn under the name rather than beside it: there can be a
     * dozen of them while the tier is single. mixing them onto the same line would push the name
     * off the edge
     */
    decoratifs: List<li.velvetchat.core.model.RoleDecoratif> = emptyList(),
    /** the private note, already read. null when there is none */
    noteInitiale: String?,
    /**
     * the nickname we put on this person. null when there is none
     *
     * separate from the note right next to it, and the distinction is the reference's: the note is
     * a reminder you read here, the nickname replaces the name everywhere, conversation list,
     * header, notification. that is why it sits at the top of the card and not in the text box at
     * the bottom
     */
    surnomInitial: String? = null,
    /** false when you have no power to moderate, or when it is yourself */
    peutModerer: Boolean,
    /** null for yourself, since you do not block yourself */
    bloque: Boolean? = null,
    /** null for yourself, or while the relation has not been read yet */
    ami: Boolean? = null,
    onNote: (String?) -> Unit,
    /** null or empty clears the nickname and gives the person their real name back */
    onSurnom: (String?) -> Unit = {},
    onMessagePrive: () -> Unit,
    onMentionner: () -> Unit,
    onModerer: () -> Unit,
    onBloquer: (Boolean) -> Unit = {},
    onAmi: (Boolean) -> Unit = {},
    onFermer: () -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()
    // the member's `nomReel` when a nickname is hiding the real one. the engine already puts the
    // nickname into `displayName`, and the card is exactly where both should be visible
    val nomReel = membre.nomReel ?: membre.displayName
        ?: membre.userId.removePrefix("@").substringBefore(':')
    // the nickname beats the name the person chose, which is its whole purpose. the real name does
    // not vanish for all that, it takes its place under the id below
    val nom = surnomInitial?.takeIf { it.isNotBlank() } ?: nomReel

    // what gets edited is no longer shown permanently
    //
    // the card carried the "nickname" field and the "note" field spelled out, on anybody's card,
    // including when you had only come to look. two empty input boxes in the middle of a profile:
    // they take the place of what you came to read, and they give the card the air of a form
    //
    // like the reference: you touch the photo, and a menu offers what can be changed. a short press
    // does the same as a long press, because a long press is a gesture nobody discovers, and a
    // card's avatar has nothing else to do
    var menuOuvert by remember(membre.userId) { mutableStateOf(false) }
    var editeur by remember(membre.userId) { mutableStateOf<ChampMembre?>(null) }

    // pullable to full height: the card is taller than a sheet sized by its contents, so its foot
    // — the roles, the note — was out of reach with no way to get at it
    FeuilleBasse(onFermer = onFermer, extensible = true, rogner = false) {
        // the frame goes around the card, not around the banner
        //
        // it used to be drawn inside the banner's box, so its layers were pinned to a 76 point
        // strip and the bottom half of the frame landed in the middle of the card instead of edging
        // its foot
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        // the card takes the sheet's whole width and its frame overhangs it: the sides past the
        // screen's edges, the top above the sheet, as on the reference. it used to pull itself in
        // by the overhang, which left empty strips on both sides and a crown boxed inside the sheet
        Box(Modifier.fillMaxWidth()) {
        etendu.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize(), derriere = true) }
        // the card's own corners, which were square
        //
        // the sheet underneath is rounded and always was, so the roundness was there — thirty
        // points higher, behind the drag handle. the card starts below it, and its header paints
        // a gradient across the full width, so what you actually see at the top of the card is a
        // sharp corner. the reference rounds the card itself
        //
        // on the column and not on the box around it: the frame is a sibling of this, it overhangs
        // on purpose, and rounding it would clip the paws off
        // opaque, so the frame's back layers only show where they stick out, as on the reference
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.radiusLarge)).background(tokens.surface)) {
            // the coloured header, across the whole width of the sheet, and the person's banner
            // rather than just their gradient
            //
            // the profile screen showed the published image and a member's card never did, so the
            // same person had two different headers depending on which door you came in through.
            // nothing justified it, the banner is public and it is already read with the rest of
            // the extended profile
            //
            // drawn over the gradient and never instead of it, like on the profile screen. by the
            // time it arrives the background is already the person's
            Box(
                Modifier.fillMaxWidth().height(76.dp)
                    .background(degradeDe(membre.userId, etendu))
            ) {
                // the nameplate does not show here. it dresses a member's row in the list and that
                // is all it is, so the header shows the banner, which is what a banner is for
                etendu.banniere?.let {
                    Box(Modifier.fillMaxSize()) {
                        EmojiEnLigne(it, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                }
                // the profile effect, over everything, avatar and nickname included
                //
                // it used to be drawn inside the banner's strip, cropped. an effect measures 450 by
                // 880, so cropped into a wide strip only a thin ribbon out of the middle survived,
                // and the middle of a profile effect is often transparent. that was one of the two
                // reasons the effect was invisible, the other being the apng android would not
                // decode
                //
                // the box is taller than its parent, which does not crop, so the effect comes down
                // over the identity like on the reference. `requiredHeight` underneath, since
                // `height` would have been pulled back to the banner's 76 points by the parent's
                // constraints, see [EffetDeProfil]
                //
                // over the top, and not behind, which is the opposite of what we did and of what
                // sounded reasonable. the reference puts it in front, its effects are cut away in
                // the middle, which is exactly where they land, and seeing them over the identity
                // is the thing you are buying. the reasoning was fine, the premise was wrong
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espXl).offset(y = (-28).dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        // a ring painted round, not a clip: clipping cut the decoration, which is
                        // drawn at 1.2 times the avatar precisely to stick out of it
                        Modifier.size(72.dp).background(tokens.surface, CircleShape)
                            .pointerInput(membre.userId) {
                                detectTapGestures(
                                    onTap = { menuOuvert = true },
                                    onLongPress = { menuOuvert = true },
                                )
                            }
                            .padding(tokens.espXs)
                    ) {
                        Avatar(
                            membre.userId,
                            membre.displayName,
                            membre.avatarUrl,
                            64.dp,
                            decoration = etendu.decoration,
                        )
                    }
                    PastillePresence(presence, taille = 14.dp, couleurDuFond = tokens.surface)
                }
            }

            Column(Modifier.padding(horizontal = tokens.espXl).offset(y = (-16).dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        nom,
                        // the first coloured decorative role beats the tier. that is the
                        // reference's rule, the highest role gives the colour, and the list's order
                        // is that hierarchy. without it, being given a pink role would have no
                        // visible effect at all, which is the only thing a decorative role is
                        // supposed to do
                        //
                        // the personal gradient still comes first: that is the person's own choice,
                        // where a role colour is a property of the server
                        color = decoratifs.firstNotNullOfOrNull { it.colorArgb }?.let { Color(it) }
                            ?: palier?.colorArgb?.let { Color(it) }
                            ?: tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // the tag before the tier. it is the only one of the two the reference shows
                    // here, and the only one the person picked
                    tagDe(etendu)?.let {
                        Spacer(Modifier.width(8.dp))
                        BadgeTag(it, hauteur = 20f)
                    }
                    palier?.let {
                        Spacer(Modifier.width(8.dp))
                        BadgeMembre(it)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // under a nickname, the name the person chose for themselves. without it you
                    // eventually stop knowing who "papa" is in a list of twenty conversations, and
                    // it is exactly what the reference shows in this spot
                    if (nom != nomReel) {
                        Text(
                            nomReel,
                            color = tokens.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(" · ", color = tokens.textMuted, fontSize = 12.sp)
                    }
                    Text(membre.userId, color = tokens.textMuted, fontSize = 12.sp)
                    etendu.pronoms?.let {
                        Text(" · ", color = tokens.textMuted, fontSize = 12.sp)
                        Text(it, color = tokens.textMuted, fontSize = 12.sp)
                    }
                }

                // the decorative roles, as chips that wrap. `FlowRow` and not a `Row`: the
                // reference shows a dozen per person, and a single row would cut them off at the
                // edge of the screen, which is to say hide half of them without saying so
                if (decoratifs.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        decoratifs.forEach { PuceRole(it) }
                    }
                }

                // the status message, if there is one. the other person's presence label and not
                // your own: `offline` received from somebody else means offline, never invisible.
                // see `libelleDAutrui`, writing the other would be wrong half the time and would
                // betray what invisibility protects
                Text(
                    messageDeStatut ?: libelleDAutrui(presence),
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = tokens.espS),
                )

                // what they are playing, if anything, and if we still believe it
                //
                // between the status and the bio on purpose: it is the most perishable thing on
                // this card, and the eye should hit it before settling into a paragraph somebody
                // wrote about themselves eight months ago
                //
                // the expiry is checked **here** and not at the source, because the source is a
                // profile on a server that has no idea what an activity is and will happily keep
                // one from last tuesday. see `Activite.perimee`
                BlocActiviteDepuisProfil(
                    userId = membre.userId,
                    brut = etendu.activite,
                    couleurTitre = tokens.textPrimary,
                    couleurTexte = tokens.textMuted,
                    couleurIcone = tokens.accent,
                    modifier = Modifier.padding(top = tokens.espS),
                )

                // the bio, before the private note and clearly distinct from it: this one is what
                // the person says about themselves, that one is what i wrote down about them.
                // putting them side by side without naming them would make them interchangeable
                etendu.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    Text(
                        t.ficheAPropos,
                        color = tokens.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = tokens.espL, bottom = 6.dp),
                    )
                    // the shortcodes are drawn as the emoji they name, like on the desktop. a bio
                    // reading `:blahaj:` in plain text is the plumbing showing through, and it is
                    // the same fix as the direct conversation list's previews
                    //
                    // and it unfolds. six lines with an ellipsis and no way past it hides the end
                    // of a bio without saying so, where the desktop has had its "show full bio"
                    // from the start
                    var bioComplete by remember(bio) { mutableStateOf(true) }
                    ApercuAvecEmotes(
                        texte = bio,
                        couleur = tokens.textSecondary,
                        taille = 14.sp,
                        maxLines = if (bioComplete) 40 else 6,
                        lineHeight = 19.sp,
                    )
                    if (!bioComplete && (bio.lines().size > 6 || bio.length > 220)) {
                        Text(
                            t.afficherLaBioComplete,
                            color = tokens.accent,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { bioComplete = true },
                        )
                    }
                }

                // an existing note is read, and no longer typed here. a note is personal and rare,
                // and its empty field taught nobody anything
                noteInitiale?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        t.ficheNote,
                        color = tokens.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = tokens.espL, bottom = 6.dp),
                    )
                    Text(
                        note,
                        color = tokens.textSecondary,
                        fontSize = 14.sp,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(14.dp))
                ActionMembre(Icones.messages, t.ficheMessagePrive, onMessagePrive)
                ActionMembre(Icones.mention, t.ficheMentionner, onMentionner)

                // "add friend" opens the conversation, because it is the same gesture, and that is
                // the whole point of the lightweight version: a friend request to somebody you have
                // never written to is the creation of the dm. nothing to invent for the "request
                // sent" and "request received" states, the protocol already carries them
                ami?.let { estAmi ->
                    ActionMembre(
                        Icones.inviter,
                        if (estAmi) t.ficheRetirerAmi else t.ficheAjouterAmi,
                        onClick = { onAmi(!estAmi) },
                    )
                }
                // absent and not greyed out when you cannot moderate. a grey row still invites a
                // tap, and the refusal would only arrive after the gesture
                if (peutModerer) {
                    ActionMembre(Icones.exclusion, t.ficheModerer, onModerer, danger = true)
                }

                // blocking is not moderation, and it sits apart for that reason. moderation acts on
                // what somebody can do in the room and takes power. blocking only acts on what i
                // receive, and is refused to nobody. mixing them would suggest you have to be an
                // admin to protect yourself
                bloque?.let { estBloque ->
                    var confirme by remember(membre.userId) { mutableStateOf(false) }
                    ActionMembre(
                        Icones.exclusion,
                        when {
                            estBloque -> t.ficheDebloquer
                            confirme -> t.ficheConfirmerBlocage
                            else -> t.ficheBloquer
                        },
                        {
                            if (estBloque) onBloquer(false)
                            else if (confirme) onBloquer(true)
                            else confirme = true
                        },
                        danger = !estBloque,
                    )
                    if (confirme && !estBloque) {
                        Text(
                            t.blocageExplication,
                            color = tokens.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = tokens.espXs, vertical = tokens.espXs),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        // the effect stays inside the card. it used to live in the banner's 76 point strip and drew
        // twice as tall as it was wide, so it spilled over the conversation and the room's bar,
        // where it has no business being. laid over the whole card and cropped to its edges, it
        // falls where the reference puts it, on the card, in front of the avatar and the nickname
        etendu.effet?.let {
            Box(Modifier.matchParentSize().clipToBounds().zIndex(2f)) { EffetDeProfil(it) }
        }
        etendu.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize()) }
        }
        }

        if (menuOuvert) {
            MenuDeFiche(
                nom = nom,
                aUnSurnom = !surnomInitial.isNullOrBlank(),
                aUneNote = !noteInitiale.isNullOrBlank(),
                onChoisir = { champ -> menuOuvert = false; editeur = champ },
                onFermer = { menuOuvert = false },
            )
        }
        editeur?.let { champ ->
            EditeurDeChamp(
                titre = if (champ == ChampMembre.SURNOM) t.ficheSurnom else t.ficheNote,
                valeurInitiale = when (champ) {
                    ChampMembre.SURNOM -> surnomInitial.orEmpty()
                    ChampMembre.NOTE -> noteInitiale.orEmpty()
                },
                indication = if (champ == ChampMembre.SURNOM) nomReel else t.ficheNoteInvite,
                lignes = if (champ == ChampMembre.SURNOM) 1 else 4,
                maximum = if (champ == ChampMembre.SURNOM) 60 else 400,
                onValider = { valeur ->
                    when (champ) {
                        ChampMembre.SURNOM -> onSurnom(valeur)
                        ChampMembre.NOTE -> onNote(valeur)
                    }
                    editeur = null
                },
                onFermer = { editeur = null },
            )
        }
    }
}

/** what can be noted about somebody from their card */
private enum class ChampMembre { SURNOM, NOTE }

/**
 * the photo's menu: what can be changed about this person
 *
 * two entries only, and they say their state. "nickname" rather than "add a nickname" when there
 * already is one: the label does not have to guess what you came to do, it names what it opens
 */
@Composable
private fun MenuDeFiche(
    nom: String,
    aUnSurnom: Boolean,
    aUneNote: Boolean,
    onChoisir: (ChampMembre) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Dialog(onDismissRequest = onFermer) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surface).padding(vertical = 8.dp),
        ) {
            Text(
                nom,
                color = tokens.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            EntreeDeMenu(t.ficheSurnom, aUnSurnom) { onChoisir(ChampMembre.SURNOM) }
            EntreeDeMenu(t.ficheNote, aUneNote) { onChoisir(ChampMembre.NOTE) }
        }
    }
}

/** a row of the menu. the dot says there is already something behind it */
@Composable
private fun EntreeDeMenu(titre: String, rempli: Boolean, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titre, color = tokens.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (rempli) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(tokens.accent))
        }
    }
}

/**
 * the little editor for a card field
 *
 * it commits on a button and not on losing focus: you opened it for that, and on a phone the normal
 * way out of a dialog is back, which makes nobody lose focus. it is the same trap as the bio, fixed
 * in the same place
 *
 * emptying the field clears the value, which is the only way of removing a nickname without a
 * second button to say so
 */
@Composable
private fun EditeurDeChamp(
    titre: String,
    valeurInitiale: String,
    indication: String,
    lignes: Int,
    maximum: Int,
    onValider: (String?) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var texte by remember(valeurInitiale) { mutableStateOf(valeurInitiale) }
    Dialog(onDismissRequest = onFermer) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surface).padding(18.dp),
        ) {
            Text(
                titre,
                color = tokens.textPrimary,
                fontSize = tokens.typography.headingSize.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .padding(horizontal = tokens.espM, vertical = 10.dp),
            ) {
                if (texte.isEmpty()) {
                    Text(indication, color = tokens.textMuted, fontSize = 14.sp, maxLines = lignes)
                }
                BasicTextField(
                    value = texte,
                    onValueChange = { saisi ->
                        texte = if (saisi.length <= texte.length) saisi else saisi.take(maximum)
                    },
                    singleLine = lignes == 1,
                    maxLines = lignes,
                    minLines = lignes,
                    textStyle = androidx.compose.material3.LocalTextStyle.current
                        .copy(color = tokens.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    t.annuler,
                    color = tokens.textSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onFermer).padding(10.dp),
                )
                Text(
                    t.enregistrer,
                    color = tokens.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onValider(texte.ifBlank { null }) }
                        .padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionMembre(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    libelle: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tokens = LocalTokens.current
    val couleur = if (danger) tokens.danger else tokens.textPrimary
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = tokens.espXs, vertical = tokens.espM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(icone, tint = couleur, taille = 19.dp)
        Spacer(Modifier.width(14.dp))
        Text(libelle, color = couleur, fontSize = tokens.typography.bodySize.sp)
    }
}

/**
 * a decorative role chip: colour dot, name, outline
 *
 * the shape comes from the reference, and each detail earns its place. the solid dot states the
 * role's colour even when the background is nearly transparent, the outline separates two
 * neighbouring chips of similar hue, and the very diluted fill keeps them readable on a dark theme
 * without turning twelve labels into twelve blobs of colour
 */
@Composable
fun PuceRole(role: li.velvetchat.core.model.RoleDecoratif) {
    val tokens = LocalTokens.current
    val couleur = role.colorArgb?.let { Color(it) } ?: tokens.textSecondary
    Row(
        Modifier.clip(RoundedCornerShape(tokens.radiusLarge))
            .background(couleur.copy(alpha = 0.12f))
            .border(1.dp, couleur.copy(alpha = 0.35f), RoundedCornerShape(tokens.radiusLarge))
            .padding(start = 7.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(couleur))
        Spacer(Modifier.width(6.dp))
        Text(
            role.nom,
            color = tokens.textPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** glyph and name. there is no shortage of room on a card */
@Composable
private fun BadgeMembre(palier: RoleTier) {
    val tokens = LocalTokens.current
    val couleur = palier.colorArgb?.let { Color(it) } ?: tokens.textMuted
    Row(
        Modifier.clip(RoundedCornerShape(tokens.radiusSmall))
            .background(couleur.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        palier.icon?.let {
            Text(it, fontSize = 10.sp, color = couleur)
            Spacer(Modifier.width(4.dp))
        }
        Text(palier.name, color = couleur, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

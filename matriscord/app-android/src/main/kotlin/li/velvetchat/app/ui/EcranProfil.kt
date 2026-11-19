package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.ProfilEtendu
import li.velvetchat.core.model.RoleTier
import li.velvetchat.core.model.Statut

/**
 * the profile screen
 *
 * full screen, entering from the right like the settings: you navigate in it, you do not glance at
 * it
 *
 * what is not in it, and why. half of the reference's screen is monetisation, balance, shop,
 * quests, wishlist, and the other half assumes objects matrix does not have, friends, connections
 * to third party accounts. an empty section is worth less than its absence, and an invented one
 * less still
 *
 * "member since" is not there either, and that is a protocol limit rather than a choice: matrix
 * does not expose an account's creation date. only the date of arrival in a room can be had, and
 * that belongs on a member's card, not here
 */
@Composable
fun EcranProfil(
    userId: String,
    nom: String?,
    avatarUrl: String?,
    etendu: ProfilEtendu,
    statut: Statut,
    /** the person's role tiers, one per server where they have one */
    badges: List<RoleTier>,
    stockageEnvois: String,
    stockageMoteur: String,
    onChangerAvatar: () -> Unit,
    onNom: (String) -> Unit,
    onPronoms: (String) -> Unit,
    onStatut: () -> Unit,
    onApparence: () -> Unit,
    onViderCache: () -> Unit,
    onFermer: () -> Unit,
    /**
     * `null` while we do not know yet, `true`/`false` once the question has been put to the server.
     * see `MatrixEngine.estAdministrateurServeur`, which is what really closes the door, this entry
     * being only a visibility and never the check
     */
    estAdministrateur: Boolean? = null,
    onCentreDeControle: () -> Unit = {},
    /**
     * the settings, behind the floating bar at the bottom
     *
     * what they contain used to live right here, stacked under the bio. see `EcranParametres`
     */
    onParametres: () -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current
    androidx.activity.compose.BackHandler(onBack = onFermer)

    // the account's gradient belongs to the card, not to the page: around it you see the app's
    // background, as on the reference, and that is what gives the card edges
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(tokens.bg),
    ) {
        val hauteurEcran = maxHeight
        // the frame overhangs the full width card: its sides past the screen's edges, as on the
        // reference, and its top and bottom into room kept for them in the scroll
        val debords = li.velvetchat.app.ui.debordsDuCadre(etendu.cadre, maxWidth)
        val coiffe = debords.calculateTopPadding()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (coiffe > 0.dp) Spacer(Modifier.height(coiffe))
            Box(Modifier.fillMaxWidth()) {
            // the frame's background layers, before the card. they were never drawn here, only the
            // catalogue grid asked for them
            etendu.cadre?.let {
                CadreDeProfil(it, Modifier.matchParentSize(), derriere = true)
            }
            Column(
                Modifier.fillMaxWidth()
                    // the card holds at least the screen: without that minimum, a profile with no
                    // bio stops in the middle and leaves the app's background below
                    .heightIn(min = hauteurEcran - coiffe)
                    // the top corners only: the bottom of the card is never visible, the page
                    // continuing past the screen
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(degradeDe(userId, etendu)),
            ) {
            // the banner
            //
            // 150 dp: the reference gives it a little under a sixth of the screen's height, system
            // bar included. it was at 180, which pushed the identity far enough down that the "edit
            // profile" button fell outside the first screen
            Box(
                Modifier.fillMaxWidth().height(150.dp)
                    // a dark veil over the strip, even with no image. without it, an account with
                    // no banner gives one single flat colour from the top of the screen to the
                    // bottom: the banner no longer exists, and the avatar no longer overlaps
                    // anything. the reference always keeps two distinct areas
                    .background(Color.Black.copy(alpha = 0.18f)),
            ) {
                // the banner image lays itself over the gradient, never in its place: by the time
                // it arrives the background is already the person's, and there is no flash of grey
                etendu.banniere?.let { BanniereImage(it) }

                // an effect deliberately overhangs the banner, and it was not doing so
                //
                // reported: the profile effects were hidden inside the banner and could not be
                // seen. the cause is in the modifier's name. `Modifier.height(340.dp)` respects the
                // parent's constraints: the banner imposes 150 at most, so the box was brought back
                // to 150 with nothing flagging it. `requiredHeight` was wanted, the only one of the
                // two that overrides
                //
                // the overhang was not decorative: an effect measures 450 by 880, and `Crop` in a
                // wide strip only showed a ribbon taken from the middle, where a profile effect is
                // often transparent. hence not seeing them. over the whole preview, like on the
                // desktop and as the reference shows: the effect goes in front of the avatar and
                // the nickname

                // a rounded square, not a circle, laid under the system bar: that is what the
                // reference does, and the square tells itself apart better from a round avatar a
                // couple of centimetres away
                Box(
                    Modifier.align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = tokens.espM, top = tokens.espS)
                        .size(34.dp)
                        .clip(RoundedCornerShape(tokens.radiusMedium))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = onFermer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icone(Icones.fermer, tint = Color.White, taille = 18.dp, description = t.fermer)
                }
            }

            // avatar and status
            //
            // the avatar overlaps the bottom of the banner and the status bubble sits to its right,
            // the reference's exact arrangement. a negative `offset` rather than a stacked `Box`:
            // the column goes on measuring its height normally, and everything after it moves up by
            // as much
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espM).offset(y = (-44).dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    // tapping the avatar opens the status. that is the reference's gesture, checked
                    // on a screenshot: from the profile, the photo leads to the online / idle / do
                    // not disturb / invisible choice
                    //
                    // and it is now the only place that leads there. the bottom bar opened a status
                    // menu on its side and the profile on the other, which is two different user
                    // menus, and makes no sense. everything goes through here
                    Box(
                        // a ring painted round, not a clip, which cut the decoration off
                        Modifier.size(96.dp).background(tokens.bg, CircleShape)
                            .clickable(onClick = onStatut)
                            .padding(4.dp),
                    ) {
                        Avatar(userId, nom, avatarUrl, 88.dp, decoration = etendu.decoration)
                    }
                    PastillePresence(statut.presence, taille = 20.dp, couleurDuFond = tokens.bg)
                }

                // the custom status bubble, to the right of the avatar and aligned on its bottom, a
                // thought bubble with its little detached ball at the top left. it only appears
                // when there is something to say
                val emoji = emojiDuStatut(statut.message)
                val texte = texteDuStatut(statut.message)
                if (emoji != null || !texte.isNullOrBlank()) {
                    Column(
                        Modifier.padding(start = 6.dp, top = 30.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Box(
                            Modifier.padding(start = 4.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(tokens.surfaceRaised),
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .background(tokens.surfaceRaised)
                                .clickable(onClick = onStatut)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            emoji?.let {
                                Text(it, fontSize = 18.sp)
                                if (!texte.isNullOrBlank()) Spacer(Modifier.width(6.dp))
                            }
                            texte?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    color = tokens.textPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // the identity
            Column(Modifier.padding(horizontal = tokens.espM).offset(y = (-32).dp)) {
                // the name, large, with its chevron. the chevron is not decorative: it is the
                // second path to the status on the reference, and it says the name opens something
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onStatut),
                ) {
                    Text(
                        nom ?: userId.removePrefix("@").substringBefore(':'),
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icone(Icones.replier, tint = tokens.textPrimary, taille = 20.dp)
                }

                // one single line: id, pronouns, tag, tiers. the reference puts them all side by
                // side under the name, and that is what gives the top of the page its density. they
                // were spread over three blocks separated by margins
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        userId.removePrefix("@").substringBefore(':'),
                        color = tokens.textSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    etendu.pronoms?.takeIf { it.isNotBlank() }?.let {
                        Text(" • ", color = tokens.textSecondary, fontSize = 14.sp)
                        Text(it, color = tokens.textSecondary, fontSize = 14.sp)
                    }
                    tagDe(etendu)?.let {
                        Spacer(Modifier.width(8.dp))
                        BadgeTag(it, hauteur = 22f)
                    }
                    // no container over them. each tier already carries its own dot, and wrapping
                    // them in a second gave a frame inside a frame, twice as heavy as the
                    // reference's row of badges
                    badges.forEach { palier ->
                        Spacer(Modifier.width(8.dp))
                        BadgeRolePlein(palier)
                    }
                }

                // "edit profile" full width. it is the reference's main button, and here it looked
                // like one list row among others
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp)
                        .clip(RoundedCornerShape(tokens.radiusMedium))
                        .background(tokens.colors.accentMuted.toColor())
                        .clickable(onClick = onApparence)
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icone(Icones.modifier, tint = Color.White, taille = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        t.modifierLeProfil,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = tokens.typography.bodySize.sp,
                    )
                }

                // ------------------------------------------------------ the sections
                etendu.bio?.takeIf { it.isNotBlank() }?.let {
                    TitreSection(t.bio)
                    // `:shortcode:` becomes the image, like everywhere else it is shown
                    //
                    // the model has said so from the start, "which is half the point of the
                    // field", and the desktop, the member card and the member list all render
                    // them. this one screen printed the raw text, so your own bio was the only
                    // place you saw the plumbing instead of the emoji
                    ApercuAvecEmotes(
                        texte = it,
                        couleur = tokens.textPrimary,
                        taille = 15.sp,
                        // a bio is the one multiline field, so it is not cut to one line here
                        maxLines = 10,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // absent as long as we do not know it is a yes. neither greyed out while the server
                // answers, nor shown so as to refuse it afterwards
                if (estAdministrateur == true) {
                    TitreSection(t.champServeur)
                    LigneProfil(Icones.reglages, t.centreDeControle, onCentreDeControle)
                }

                // the floating bar covers the bottom of the page: without this reserve, the last
                // row reads half way
                Spacer(Modifier.height(96.dp))
            }
            }

            // the effect stays inside the card, and the frame goes in front of it. both were laid
            // on the whole page: the effect bled over the full height and the frame bordered the
            // screen
            etendu.effet?.let {
                Box(Modifier.matchParentSize().clipToBounds().zIndex(2f)) { EffetDeProfil(it) }
            }
            etendu.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize()) }
            }
            Spacer(Modifier.height(debords.calculateBottomPadding()))
        }

        // the floating bar
        //
        // where the reference files what is not the profile. quests, shop and settings float there
        // in a pill, above the content. we have neither quests nor currency, which leaves settings,
        // and that is exactly what was missing: the storage, the problem report and the legal
        // notices were lying at the bottom of the profile page, between a bio and a badge
        Row(
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
                // a pill, its radius half its height, and not a rounded square: that is what makes
                // it float rather than rest
                .clip(RoundedCornerShape(28.dp))
                .background(tokens.surface.copy(alpha = 0.94f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onParametres)
                    .padding(horizontal = 26.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icone(Icones.reglages, tint = tokens.textPrimary, taille = 20.dp)
                Spacer(Modifier.height(2.dp))
                Text(
                    t.parametres,
                    color = tokens.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

    }
}

/** a profile section's title: small, indented, above its content */
@Composable
private fun TitreSection(texte: String) {
    val tokens = LocalTokens.current
    Text(
        texte,
        color = tokens.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
    )
}

/** a clickable row: icon, label, chevron */
@Composable
private fun LigneProfil(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    libelle: String,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surface.copy(alpha = 0.65f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(icone, tint = tokens.textPrimary, taille = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            libelle,
            color = tokens.textPrimary,
            fontSize = tokens.typography.bodySize.sp,
            modifier = Modifier.weight(1f),
        )
        Icone(Icones.suite, tint = tokens.textSecondary, taille = 16.dp)
    }
}

/**
 * the emoji of a custom status, when it starts with one
 *
 * matrix only carries a `status_msg`, a string and nothing else: it has no field for the emoji the
 * reference lets you put next to the text. so we encode it at the head of the string, which has
 * three properties no invented field would have had, other matrix clients show it as is rather than
 * losing the information, a status written from element arrives here with no surprise, and there is
 * nothing to migrate
 *
 * recognised by its unicode category rather than by a list: an emoji is a code point in the symbols
 * area, and enumerating the ones we accept would mean refusing new ones every year
 */
internal fun emojiDuStatut(message: String?): String? {
    val m = message?.trimStart() ?: return null
    if (m.isEmpty()) return null
    val premier = m.codePointAt(0)
    if (!estEmoji(premier)) return null
    var fin = Character.charCount(premier)
    // the modifiers that follow are part of the same emoji: skin tone, presentation selector, a
    // family's zwj joins. cutting them would give back "👋" where the author had put "👋🏾"
    while (fin < m.length) {
        val suivant = m.codePointAt(fin)
        val attache = suivant == 0x200D || suivant == 0xFE0F || suivant == 0xFE0E ||
            (suivant in 0x1F3FB..0x1F3FF) || (suivant in 0xE0020..0xE007F)
        if (!attache && !(fin > 0 && m.codePointBefore(fin) == 0x200D)) break
        fin += Character.charCount(suivant)
    }
    return m.substring(0, fin)
}

/** a status's text, with the leading emoji removed */
internal fun texteDuStatut(message: String?): String? {
    val m = message?.trimStart() ?: return null
    val emoji = emojiDuStatut(m) ?: return m.takeIf { it.isNotBlank() }
    return m.removePrefix(emoji).trim().takeIf { it.isNotBlank() }
}

private fun estEmoji(point: Int): Boolean =
    point in 0x1F300..0x1FAFF || point in 0x2600..0x27BF ||
        point in 0x1F000..0x1F2FF || point == 0x2764 || point in 0x2190..0x21FF


/** the banner, loaded from the server. see `EmojiEnLigne` for the `mxc://` path */
@Composable
private fun BanniereImage(mxcUrl: String) {
    // `Crop`: a banner is a background, it fills even at the cost of being cropped. in `Fit` it
    // showed at its natural size leaving emptiness on either side
    Box(Modifier.fillMaxSize()) {
        EmojiEnLigne(mxcUrl, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

/** the full badge, glyph and name. on the profile screen there is no shortage of room */
@Composable
private fun BadgeRolePlein(palier: RoleTier) {
    val tokens = LocalTokens.current
    val couleur = palier.colorArgb?.let { Color(it) } ?: tokens.textMuted
    Row(
        Modifier.clip(RoundedCornerShape(tokens.radiusSmall))
            // a dark background rather than the role's colour diluted. at 18 %, a red laid on a
            // purple profile became unreadable, the tier's colour saying the role and making the
            // word invisible. the neutral background leaves the hue to the text, which is the only
            // place it carries meaning, and it is what the tag dot right next to it does, which did
            // not have the problem
            .background(tokens.bg.copy(alpha = 0.55f))
            .padding(horizontal = tokens.espS, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        palier.icon?.let {
            Text(it, fontSize = 11.sp, color = couleur)
            Spacer(Modifier.width(4.dp))
        }
        Text(palier.name, color = couleur, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * an editable field of the profile
 *
 * it saves on leaving the field, not on a button: a save button per field would make three buttons
 * on this screen, and forgetting it would let you think you had changed something. the field keeps
 * its local value while you type and only publishes it once
 */
@Composable
private fun ChampProfil(
    libelle: String,
    valeurInitiale: String,
    indication: String,
    onValider: (String) -> Unit,
    note: String? = null,
) {
    val tokens = LocalTokens.current
    var valeur by remember(valeurInitiale) { mutableStateOf(valeurInitiale) }

    Text(
        libelle,
        color = tokens.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surface)
            .padding(horizontal = tokens.espM, vertical = tokens.espM)
    ) {
        if (valeur.isEmpty()) {
            Text(indication, color = tokens.textMuted, fontSize = 15.sp, maxLines = 1)
        }
        BasicTextField(
            value = valeur,
            onValueChange = { valeur = it.take(120) },
            singleLine = true,
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { etat ->
                    if (!etat.isFocused && valeur != valeurInitiale) onValider(valeur)
                },
        )
    }
    note?.let {
        Text(it, color = tokens.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = tokens.espXs))
    }
}

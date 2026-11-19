package li.velvetchat.app.ui

import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.model.CustomEmote
import li.velvetchat.core.model.EmotePack
import li.velvetchat.core.model.apercu
import li.velvetchat.core.model.avec

/**
 * what the picker hands back when you pick
 *
 * two shapes that do not reduce to each other: a unicode emoji is its own character, a custom one
 * is only a name pointing at an image. hiding both behind a single string would force every caller
 * to guess which one it is holding, and a matrix reaction only accepts the first
 */
sealed interface ChoixEmoji {
    data class Unicode(val caractere: String) : ChoixEmoji
    data class Personnalise(val emote: CustomEmote) : ChoixEmoji
}

/** the three tabs of the discord picker, in their order */
private enum class Onglet {
    EMOJI, GIF, AUTOCOLLANTS,
    ;

    val titre: String get() = when (this) {
        EMOJI -> horsUi.emoji
        GIF -> "GIF"
        AUTOCOLLANTS -> horsUi.autocollants
    }
}

/** one cell of the grid: a section title, or an emoji from one of the two families */
private sealed interface Case {
    data class Entete(val titre: String) : Case

    /**
     * the catalogue entry, not the already tinted character
     *
     * the skin tone is applied at render time. it can change while the panel is open, and keeping
     * the finished character in the cell would mean rebuilding the whole grid, so making it jump
     * under the finger, which is what this file spends its time avoiding. it is also what lets a
     * favourite survive a tone change, since the preference names the emoji and not its skin colour
     */
    data class Uni(val emoji: li.velvetchat.core.model.EmojiUnicode) : Case
    data class Perso(val emote: CustomEmote) : Case
}

/** a marker in the bottom bar, and the cell it jumps to */
private class Ancre(
    val index: Int,
    val icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val serveur: EmotePack? = null,
)

/**
 * the emoji picker, cloned from discord's
 *
 * what sets this apart from the flat grid it replaced is not decoration: a picker that only shows
 * one long run of emoji makes you walk the whole catalogue to find the one you use ten times a day.
 * the three mechanisms that avoid it, favourites, frequents, per server sections, are exactly the
 * ones discord put there, and the bottom bar is what makes them reachable without scrolling
 *
 * the panel is anchored under the composer, in the keyboard's place, rather than dropped on the
 * screen as a modal sheet. that is the difference the reference shows most clearly: you keep seeing
 * the field you are filling in, and the emoji you tap lands in it under your eyes. a modal sheet
 * hides exactly what you are composing
 *
 * the panel does not close when you pick. people rarely place just one, and reopening the picker
 * between two emoji would cost a gesture every time
 *
 * @param packs the emoji grouped by server. empty in a direct conversation when nobody publishes
 *   any, and the panel is still useful, it still has the unicode ones
 * @param autoriserPersonnalises some contexts do not accept them, see the caller's comment on the
 *   reactions side. the picker has no business guessing
 * @param onEffacer wired to the backspace key, bottom right. null where there is no text to erase,
 *   and the key then disappears rather than sitting there doing nothing
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PanneauEmojis(
    packs: List<EmotePack>,
    autoriserPersonnalises: Boolean = true,
    hauteur: androidx.compose.ui.unit.Dp = 360.dp,
    poignee: Boolean = true,
    onChoisir: (ChoixEmoji) -> Unit,
    onEffacer: (() -> Unit)? = null,
    /**
     * a gif was picked
     *
     * null, the default, hides the gif tab rather than showing it inert. that is the reactions
     * case, where a gif makes no sense. offering what cannot go anywhere costs more than not
     * offering it
     */
    /**
     * the gif panel, supplied by the caller. null hides the tab
     *
     * a slot and not a callback: the panel queries a provider and still lives in the android app.
     * leaving it here would have dragged another four hundred odd lines into this move, for a tab
     * the desktop does not have yet
     *
     * the block's parameter means "expand yourself": touching the gif search grows the panel,
     * exactly like the emoji one
     */
    panneauGif: (@Composable (onChercher: () -> Unit) -> Unit)? = null,
    /**
     * which tab to open on
     *
     * the composer has three buttons that all lead here, emoji, gif, sticker, and each has to land
     * on its own. without this parameter the sticker one opened the panel on the emoji tab and left
     * you looking for it
     */
    ongletInitial: Int = 0,
    /**
     * a sticker was picked
     *
     * null hides the tab, like for gifs. a sticker is a message, so it makes no more sense as a
     * reaction than a gif does
     */
    onAutocollant: ((li.velvetchat.core.model.CustomEmote) -> Unit)? = null,
    /**
     * the desktop layout, taken off the reference: gif / stickers / emoji tabs on the left, a gold
     * edged field with the skin tone next to it, a category column down the left, collapsible
     * sections, a grid of 8 by 48 with hover, and a footer naming the hovered emoji. the phone
     * keeps its own, a bottom bar and ten columns
     */
    bureau: Boolean = false,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val reglages = LocalReglagesEmojis.current
    val doreBureau = Color(0xFFC9AB56)
    /** collapsed sections on desktop, by title */
    var repliees by remember { mutableStateOf(setOf<String>()) }
    /** the hovered cell on desktop, named in the footer */
    var survolee by remember { mutableStateOf<Case?>(null) }
    val scope = rememberCoroutineScope()
    val etatGrille = rememberLazyGridState()

    var onglet by remember(ongletInitial) {
        mutableStateOf(Onglet.entries.getOrElse(ongletInitial) { Onglet.EMOJI })
    }
    var recherche by remember { mutableStateOf("") }

    // the tone is read on every recomposition. it is shared observable state and not a disk read
    // (see `ReglagesEmojis`), so changing tone redraws the grid immediately without rebuilding it
    // or scrolling it
    val teinte = reglages.teinte()
    var choixTeinte by remember { mutableStateOf(false) }

    // the sections are frozen when the panel opens, and that is the least obvious thing in this
    // file
    //
    // placing one emoji brings the "frequently used" section into existence at the top, which
    // pushes everything else down two rows, under the finger, between two taps. the second emoji
    // you were about to place is no longer where you were aiming. a picker that reorganises itself
    // while you pick from it is unusable the moment you want two
    //
    // the preferences themselves are written straight away. it is only their effect on the layout
    // that waits for the next open. so neither `remember` nor the `remember` keys below may depend
    // on those two lists
    val favorisFiges = remember { reglages.favoris() }
    val frequentsFiges = remember { reglages.frequents() }

    // the visual feedback of favouriting, on the other hand, has to be immediate, otherwise the
    // long press proves nothing. it goes through a star in the corner of the cell, a mark that
    // moves nothing, rather than a section that shoves the grid around
    var favorisVivants by remember { mutableStateOf(favorisFiges.toSet()) }

    // stickers are filtered out of the emoji grid. the same pack carries both, and without this a
    // sticker would have been inserted into a text message between colons, which is everything it
    // is not. they have their own tab
    val paquets = if (autoriserPersonnalises) {
        packs.map { pack -> pack.copy(emotes = pack.emotes.filterNot { it.estAutocollant }) }
            .filter { it.emotes.isNotEmpty() }
    } else emptyList()

    // a preference key only names an emoji while that emoji still exists. a favourite pointing at a
    // shortcode the server removed has to disappear quietly rather than leave a hole in the grid
    val parRaccourci = remember(paquets) {
        paquets.flatMap { it.emotes }.associateBy { it.shortcode }
    }
    fun resoudre(cle: String): Case? = when {
        // `emojiUnicode` can return null: a preference written by a version that offered an emoji
        // since dropped from the catalogue. it then vanishes without a sound, exactly like a
        // favourite pointing at a deleted server emoji
        cle.startsWith("u:") ->
            li.velvetchat.core.model.emojiUnicode(cle.removePrefix("u:"))
                ?.let { Case.Uni(it) }

        cle.startsWith("p:") -> parRaccourci[cle.removePrefix("p:")]?.let { Case.Perso(it) }
        else -> null
    }

    // the grid and the bottom bar are built together, in one pass. the anchors are indices into the
    // cell list, and two separate passes would misalign them at the first section somebody forgot
    // to add on both sides
    val (cases, ancres) = remember(recherche, paquets, favorisFiges, frequentsFiges) {
        val cases = mutableListOf<Case>()
        val ancres = mutableListOf<Ancre>()

        if (recherche.isNotBlank()) {
            // no sections while searching. what you are looking for is somewhere other than where
            // you would have filed it, otherwise you would not be looking
            cases += Case.Entete(t.resultats)
            cases += paquets.flatMap { it.emotes }
                .filter { it.shortcode.contains(recherche, ignoreCase = true) }
                .map { Case.Perso(it) }
            // the shortcode and the keywords both. people search "laugh" and type `:joy:`, so the
            // two have to lead to the same emoji
            cases += li.velvetchat.core.model.CATALOGUE_UNICODE
                .filter { e ->
                    e.raccourci.contains(recherche, ignoreCase = true) ||
                        e.motsCles.any { it.contains(recherche, ignoreCase = true) }
                }
                .map { Case.Uni(it) }
            return@remember cases to ancres
        }

        favorisFiges.mapNotNull(::resoudre).takeIf { it.isNotEmpty() }?.let { trouves ->
            ancres += Ancre(cases.size, icone = Icones.favorisActif)
            cases += Case.Entete(t.favoris)
            cases += trouves
        }
        frequentsFiges.mapNotNull(::resoudre).takeIf { it.isNotEmpty() }?.let { trouves ->
            ancres += Ancre(cases.size, icone = Icones.recents)
            cases += Case.Entete(t.frequemmentUtilises)
            cases += trouves
        }
        paquets.forEach { pack ->
            ancres += Ancre(cases.size, serveur = pack)
            cases += Case.Entete(pack.serverName)
            cases += pack.emotes.map { Case.Perso(it) }
        }
        // one section per category, with its anchor, in the reference's order and icons
        val iconesCategories = listOf(
            Icones.emoji, Icones.nature, Icones.nourriture, Icones.manette,
            Icones.voyages, Icones.objets, Icones.symboles, Icones.drapeaux,
        )
        li.velvetchat.core.model.CATALOGUE_UNICODE.groupBy { it.categorie }.entries.sortedBy { it.key }
            .forEach { (categorie, emojis) ->
                ancres += Ancre(cases.size, icone = iconesCategories.getOrElse(categorie) { Icones.emoji })
                cases += Case.Entete(li.velvetchat.core.model.CATEGORIES_EMOJI.getOrElse(categorie) { t.emojisTitre })
                cases += emojis.map { Case.Uni(it) }
            }

        cases to ancres
    }

    // on desktop a collapsed section keeps its header and loses its cells. the markers are
    // recomputed against the displayed list, so they point at their section's header
    val affichees = remember(cases, repliees, bureau) {
        if (!bureau || repliees.isEmpty()) cases
        else {
            val sortie = mutableListOf<Case>()
            var cachee = false
            cases.forEach { c ->
                if (c is Case.Entete) { cachee = c.titre in repliees; sortie += c }
                else if (!cachee) sortie += c
            }
            sortie
        }
    }
    val ancresAffichees = remember(ancres, affichees) {
        ancres.map { a ->
            val titre = (cases.getOrNull(a.index) as? Case.Entete)?.titre
            Ancre(affichees.indexOfFirst { it is Case.Entete && it.titre == titre }.coerceAtLeast(0), a.icone, a.serveur)
        }
    }

    // the marker for the section you are currently looking at. `derivedStateOf` and not a direct
    // computation, since without it every scrolled pixel would recompose the whole bar
    val ancreActive by androidx.compose.runtime.remember(ancres) {
        androidx.compose.runtime.derivedStateOf {
            ancres.indexOfLast { it.index <= etatGrille.firstVisibleItemIndex }
        }
    }

    // the handle actually pulls now
    //
    // it used to be there because the reference has one, and its own comment said it did nothing.
    // dragging up to enlarge the window is exactly what a handle promises
    //
    // two detents and not a free height: in between you would not know where to let go, and a panel
    // sitting at some arbitrary size is a setting nobody chose. compact is the original height,
    // expanded is three quarters of the screen, enough for five more rows without swallowing the
    // conversation you are reading
    val densite = androidx.compose.ui.platform.LocalDensity.current
    // `LocalWindowInfo` and not `LocalConfiguration`, since the second is an android api and the
    // size we need is the window's, which is more correct anyway. on a desktop the screen and the
    // window are not the same thing
    val fenetre = androidx.compose.ui.platform.LocalWindowInfo.current
    val hauteurEcran = with(densite) { fenetre.containerSize.height.toDp() }
    val hauteurDepliee = remember(hauteurEcran) { (hauteurEcran * 0.75f).coerceAtLeast(hauteur) }
    var deplie by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val hauteurVisee = if (deplie) hauteurDepliee else hauteur
    // the drag follows the finger, and letting go snaps to the nearest detent. without the
    // following, the panel would jump between detents and you would not know you had hold of it
    var tire by remember { mutableStateOf(0.dp) }
    val hauteurRendue by androidx.compose.animation.core.animateDpAsState(
        (hauteurVisee + tire).coerceIn(hauteur, hauteurDepliee),
        label = "hauteurPicker",
    )

    Column(Modifier.fillMaxWidth().height(hauteurRendue).background(if (bureau) Color.Transparent else tokens.surface)) {

        // the handle, and the area around it, which is the gesture's target. wide, because a four
        // point tall line is not something a thumb catches
        //
        // removed in the modal sheet, where `FeuilleBasse` already draws its own. two stacked
        // handles were visible on screen and suggested two surfaces
        //
        // the grab area is the handle and the tab bar. the gesture worked before, but on a 4 dp
        // tall line. on the emoji tab you hit it by accident coming up from the grid, and on the
        // gif tab the grid scrolls under the finger and eats the drag before it reaches the line.
        // same code, two completely different feelings
        //
        // a fifty point band gets missed far less. the tabs stay clickable: a drag only becomes a
        // drag past the system's touch slop, and below that the touch goes to the tab as it should
        val prise = if (!poignee) Modifier else Modifier.pointerInput(hauteurDepliee) {
            detectVerticalDragGestures(
                onDragEnd = {
                    // the nearest detent, and not the gesture's direction. letting go halfway has
                    // to give a predictable result
                    deplie = (hauteurVisee + tire) > (hauteur + hauteurDepliee) / 2
                    tire = 0.dp
                },
                onDragCancel = { tire = 0.dp },
            ) { _, delta ->
                // upwards means a negative delta means bigger
                tire -= with(densite) { delta.toDp() }
            }
        }

        if (poignee) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).then(prise),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.width(40.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(tokens.border)
                )
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }

        if (bureau) {
            // the reference's tabs: text on the left, the active one in a pill, in the order gif,
            // stickers, emoji
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(Onglet.GIF, Onglet.AUTOCOLLANTS, Onglet.EMOJI).filter {
                    (it != Onglet.GIF || panneauGif != null) && (it != Onglet.AUTOCOLLANTS || onAutocollant != null)
                }.forEach { cible ->
                    val actif = onglet == cible
                    Text(
                        cible.titre,
                        color = if (actif) tokens.textPrimary else tokens.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(if (actif) tokens.surfaceRaised else Color.Transparent)
                            .curseurMain().clickable { onglet = cible }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        } else
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espM).then(prise),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // the gif tab disappears when nothing can receive a gif, the reaction picker for
            // instance. a tab that leads nowhere is the kind of thing we have been removing
            // elsewhere, so we do not put one back here
            Onglet.entries.filter {
                (it != Onglet.GIF || panneauGif != null) &&
                    (it != Onglet.AUTOCOLLANTS || onAutocollant != null)
            }.forEach { cible ->
                val actif = onglet == cible
                Box(
                    Modifier.weight(1f).height(42.dp)
                        .clip(RoundedCornerShape(tokens.radiusMedium))
                        .background(if (actif) tokens.surfaceRaised else Color.Transparent)
                        .curseurMain().clickable { onglet = cible },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cible.titre,
                        color = if (actif) tokens.textPrimary else tokens.textMuted,
                        fontSize = 15.sp,
                        fontWeight = if (actif) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        // the gif tab is no longer a placeholder, it has its panel, which decides for itself what
        // to show based on what the settings say. see `PanneauGif`
        if (onglet == Onglet.GIF && panneauGif != null) {
            // touching the search expands the panel. that is exactly the moment you want to see
            // more results, and making you drag for it as well is asking twice. the reference does
            // the same
            panneauGif?.invoke { deplie = true }
            return@Column
        }
        if (onglet == Onglet.AUTOCOLLANTS && onAutocollant != null) {
            PanneauAutocollants(packs, onAutocollant)
            return@Column
        }
        if (onglet != Onglet.EMOJI) {
            EnAttente(onglet)
            return@Column
        }

        if (bureau) {
            // the reference's field: gold edge, radius 8, with the skin tone to its right
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).height(40.dp)
                        .border(1.dp, doreBureau, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icone(Icones.recherche, tint = tokens.textSecondary, taille = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (recherche.isEmpty()) Text(t.trouveEmojiParfait, color = tokens.textMuted, fontSize = 14.sp)
                        BasicTextField(
                            value = recherche,
                            onValueChange = { recherche = it },
                            singleLine = true,
                            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(doreBureau),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).curseurMain().clickable { choixTeinte = !choixTeinte },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(teinte.apercu(), fontSize = 22.sp, fontFamily = LocalPoliceEmoji.current)
                }
            }
        } else
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espM, vertical = tokens.espS)
                .background(tokens.surfaceRaised, RoundedCornerShape(tokens.radiusMedium))
                .padding(horizontal = tokens.espM, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(Icones.recherche, tint = tokens.textMuted, taille = 17.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (recherche.isEmpty()) {
                    Text(t.trouveEmojiParfait, color = tokens.textMuted, fontSize = 15.sp)
                }
                BasicTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(Modifier.weight(1f)) {
        if (bureau) {
            // the reference's category column on the left. it jumps, it does not filter
            Column(
                Modifier.width(52.dp).fillMaxHeight().background(tokens.surfaceRaised.copy(alpha = 0.5f))
                    .verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ancresAffichees.forEach { ancre ->
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                            .curseurMain().clickable { scope.launch { etatGrille.animateScrollToItem(ancre.index) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        val pack = ancre.serveur
                        if (pack != null) Avatar(pack.serverId, pack.serverName, pack.serverAvatarUrl, 30.dp)
                        else ancre.icone?.let { Icone(it, tint = tokens.textSecondary, taille = 22.dp) }
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (cases.none { it !is Case.Entete }) {
                Text(
                    t.aucunEmojiPour(recherche),
                    color = tokens.textMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(tokens.espXl),
                )
            } else {
                // ten columns, like the reference. that is a 36 dp cell, under the 48 dp
                // recommended for a touch target, and discord makes exactly that choice. it holds
                // up here: missing your target places a neighbouring emoji, which one backspace
                // fixes, while a six column grid would mean three times the scrolling to find the
                // right one
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (bureau) 8 else 10),
                    state = etatGrille,
                    modifier = Modifier.fillMaxSize().padding(horizontal = tokens.espS),
                ) {
                    itemsIndexed(
                        if (bureau) affichees else cases,
                        span = { _, case ->
                            if (case is Case.Entete) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                        },
                        key = { i, case ->
                            when (case) {
                                is Case.Entete -> "t$i-${case.titre}"
                                is Case.Uni -> "u$i-${case.emoji.caractere}"
                                is Case.Perso -> "p$i-${case.emote.shortcode}"
                            }
                        },
                    ) { _, case ->
                        when (case) {
                            is Case.Entete -> if (bureau) {
                                // the reference's collapsible header: icon, title, chevron
                                val replie = case.titre in repliees
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .curseurMain().clickable { repliees = if (replie) repliees - case.titre else repliees + case.titre }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    when (case.titre) {
                                        t.favoris -> Icones.favorisActif
                                        t.frequemmentUtilises -> Icones.recents
                                        else -> null
                                    }?.let {
                                        Icone(it, tint = tokens.textSecondary, taille = 14.dp)
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(case.titre, color = tokens.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(4.dp))
                                    Icone(
                                        Icones.replier,
                                        tint = tokens.textSecondary,
                                        taille = 12.dp,
                                        modifier = Modifier.rotate(if (replie) -90f else 0f),
                                    )
                                }
                            } else Text(
                                case.titre,
                                color = tokens.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 6.dp, top = tokens.espM, bottom = 6.dp),
                            )

                            else -> {
                                val choix = when (case) {
                                    is Case.Uni ->
                                        ChoixEmoji.Unicode(case.emoji.avec(teinte))

                                    is Case.Perso -> ChoixEmoji.Personnalise(case.emote)
                                    else -> return@itemsIndexed
                                }
                                // the key is the bare emoji's, not the tinted character's. without
                                // that, changing tone would make your favourites disappear and
                                // reset the frequency counter to zero
                                val cle = when (case) {
                                    is Case.Uni -> "u:${case.emoji.caractere}"
                                    else -> cleDeChoix(choix)
                                }
                                val survolCase = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val caseSurvolee by survolCase.collectIsHoveredAsState()
                                if (bureau) LaunchedEffect(caseSurvolee) { if (caseSurvolee) survolee = case }
                                Box(
                                    Modifier.size(if (bureau) 48.dp else 36.dp)
                                        .then(
                                            if (bureau) Modifier.clip(RoundedCornerShape(6.dp))
                                                .background(if (caseSurvolee) tokens.surfaceRaised else Color.Transparent)
                                                .hoverable(survolCase)
                                            else Modifier,
                                        )
                                        // long press favourites, which is discord's gesture, and it
                                        // competes with nothing here since an emoji has no other
                                        // long action
                                        .curseurMain().combinedClickable(
                                            onClick = {
                                                reglages.noterUsage(cle)
                                                onChoisir(choix)
                                            },
                                            onLongClick = {
                                                favorisVivants =
                                                    reglages.basculerFavori(cle)
                                                        .toSet()
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when (case) {
                                        is Case.Uni ->
                                            // twemoji, set explicitly. this is the emoji grid, the
                                            // one place a different glyph would show most. see
                                            // `LocalPoliceEmoji`
                                            Text(
                                                case.emoji.avec(teinte),
                                                fontSize = if (bureau) 30.sp else 22.sp,
                                                fontFamily = LocalPoliceEmoji.current,
                                            )

                                        is Case.Perso -> Box(Modifier.size(if (bureau) 36.dp else 26.dp)) {
                                            EmojiEnLigne(case.emote.url)
                                        }
                                        else -> Unit
                                    }
                                    if (cle in favorisVivants) {
                                        Icone(
                                            Icones.favorisActif,
                                            tint = tokens.warning,
                                            taille = 9.dp,
                                            modifier = Modifier.align(Alignment.TopEnd),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        }

        if (bureau) {
            // the reference's footer: the hovered emoji large, a star if it is a favourite, its
            // shortcode
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(tokens.surfaceRaised.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (val c = survolee) {
                    is Case.Uni -> {
                        Text(c.emoji.avec(teinte), fontSize = 26.sp, fontFamily = LocalPoliceEmoji.current)
                        Spacer(Modifier.width(10.dp))
                        if ("u:${c.emoji.caractere}" in favorisVivants) Text("★ ", color = tokens.textSecondary, fontSize = 14.sp)
                        Text(":${c.emoji.raccourci}:", color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    is Case.Perso -> {
                        Box(Modifier.size(28.dp)) { EmojiEnLigne(c.emote.url) }
                        Spacer(Modifier.width(10.dp))
                        Text(":${c.emote.shortcode}:", color = tokens.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    else -> Unit
                }
            }
            if (choixTeinte) {
                androidx.compose.ui.window.Popup(alignment = Alignment.TopEnd, offset = androidx.compose.ui.unit.IntOffset(-16, 100), onDismissRequest = { choixTeinte = false }) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(tokens.surfaceRaised).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        li.velvetchat.core.model.TeintePeau.entries.forEach { t ->
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                                    .background(if (t == teinte) doreBureau.copy(alpha = 0.3f) else Color.Transparent)
                                    .curseurMain().clickable { reglages.setTeinte(t); choixTeinte = false },
                                contentAlignment = Alignment.Center,
                            ) { Text(t.apercu(), fontSize = 20.sp) }
                        }
                    }
                }
            }
            return@Column
        }

        // the bottom bar. it does not filter, it jumps: each marker scrolls the grid to its
        // section. the difference matters, since filtering would hide the fact that the other
        // sections exist, which is precisely what a picker is for
        Row(
            Modifier.fillMaxWidth().background(tokens.surfaceRaised)
                .padding(horizontal = tokens.espS, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ancres.forEachIndexed { rang, ancre ->
                    Box(
                        Modifier.size(38.dp).clip(CircleShape)
                            .background(
                                if (rang == ancreActive) tokens.surface else Color.Transparent
                            )
                            .curseurMain().clickable {
                                scope.launch { etatGrille.animateScrollToItem(ancre.index) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val pack = ancre.serveur
                        if (pack != null) {
                            Avatar(pack.serverId, pack.serverName, pack.serverAvatarUrl, 28.dp)
                        } else if (ancre.icone != null) {
                            Icone(
                                ancre.icone,
                                tint = if (rang == ancreActive) tokens.textPrimary
                                else tokens.textMuted,
                                taille = 20.dp,
                            )
                        }
                    }
                }
            }
            // the skin tone picker
            //
            // a global setting rather than a long press per emoji, unlike the reference. two
            // reasons, and the first is decisive: the long press is already taken here by
            // favouriting, and two long gestures on the same cell cannot coexist. the second is
            // that nobody changes tone per emoji, it is theirs, once and for all
            Box {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(if (choixTeinte) tokens.surface else Color.Transparent)
                        .curseurMain().clickable { choixTeinte = !choixTeinte },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(teinte.apercu(), fontSize = 19.sp)
                }
                if (choixTeinte) {
                    // above and not inline. the bar is already full, and six swatches pushing the
                    // section markers around would move whatever you were aiming at
                    androidx.compose.ui.window.Popup(
                        alignment = Alignment.BottomEnd,
                        offset = androidx.compose.ui.unit.IntOffset(0, -120),
                        onDismissRequest = { choixTeinte = false },
                    ) {
                        Row(
                            Modifier.clip(RoundedCornerShape(tokens.radiusLarge))
                                .background(tokens.surfaceRaised)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            li.velvetchat.core.model.TeintePeau.entries.forEach { t ->
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(
                                            if (t == teinte) tokens.accent.copy(alpha = 0.25f)
                                            else Color.Transparent
                                        )
                                        .curseurMain().clickable {
                                            reglages.setTeinte(t)
                                            choixTeinte = false
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(t.apercu(), fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            }

            onEffacer?.let { effacer ->
                Box(
                    Modifier.size(38.dp).clip(CircleShape).curseurMain().clickable(onClick = effacer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icone(Icones.effacer, tint = tokens.textSecondary, taille = 20.dp,
                        description = t.effacer)
                }
            }
        }
    }
}

/**
 * the two tabs with no source yet
 *
 * present and empty rather than removed, which needs justifying since this file otherwise complains
 * about dead buttons existing. the difference: an absent tab suggests velvet will never do gifs or
 * stickers, when the obstacle is named and of another kind entirely. for gifs it is not code that
 * is missing, it is a decision about what we accept sending to a third party
 */
@Composable
private fun EnAttente(onglet: Onglet) {
    val t = textes()
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icone(
            if (onglet == Onglet.GIF) Icones.gif else Icones.autocollant,
            tint = tokens.textMuted,
            taille = 34.dp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when (onglet) {
                else -> t.autocollantsPacksSepares
            },
            color = tokens.textMuted,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp,
        )
    }
}

/**
 * the same panel, as a modal sheet
 *
 * reserved for reactions, where there is no composer to anchor under and the gesture ends at the
 * first emoji picked
 */
@Composable
fun PickerEmojis(
    autoriserPersonnalises: Boolean = true,
    onChoisir: (ChoixEmoji) -> Unit,
    onFermer: () -> Unit,
) {
    FeuilleBasse(onFermer) {
        Box(Modifier.heightIn(max = 360.dp)) {
            PanneauEmojis(
                packs = LocalPacks.current,
                autoriserPersonnalises = autoriserPersonnalises,
                hauteur = 360.dp,
                poignee = false,
                onChoisir = onChoisir,
            )
        }
    }
}

/**
 * the stickers tab
 *
 * it used to show a placeholder saying matrix stickers come in separate packs that velvet does not
 * read yet. that was true of reading the `usage` field, not of the transport: the same state event
 * already carries both kinds, and all it took was telling them apart
 *
 * a grid of three and not of eight. a sticker gets looked at before it is sent, being an image
 * rather than a glyph, and eight per row would make them indistinguishable. the reference fits
 * three or four depending on width, and three fits our panels
 *
 * one gesture sends, with no confirmation: the sticker is the message, there is nothing to compose
 * around it. that is the difference from an emoji, which only gets inserted
 */
@Composable
private fun PanneauAutocollants(
    packs: List<EmotePack>,
    onChoisir: (li.velvetchat.core.model.CustomEmote) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val avecAutocollants = remember(packs) {
        packs.map { pack -> pack.copy(emotes = pack.emotes.filter { it.estAutocollant }) }
            .filter { it.emotes.isNotEmpty() }
    }

    if (avecAutocollants.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icone(Icones.autocollant, tint = tokens.textMuted, taille = 34.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                t.aucunAutocollant,
                color = tokens.textMuted,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        avecAutocollants.forEach { pack ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    pack.serverName,
                    color = tokens.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = tokens.espXs, top = tokens.espS, bottom = 6.dp),
                )
            }
            items(pack.emotes, key = { it.shortcode + pack.serverId }) { autocollant ->
                Box(
                    Modifier.padding(tokens.espXs).aspectRatio(1f)
                        .clip(RoundedCornerShape(tokens.radiusMedium))
                        .curseurMain().clickable { onChoisir(autocollant) }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmojiEnLigne(autocollant.url)
                }
            }
        }
    }
}

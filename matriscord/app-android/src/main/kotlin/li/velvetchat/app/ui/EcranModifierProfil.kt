package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.DecorationAvatar
import li.velvetchat.core.model.ProfilEtendu
import li.velvetchat.core.model.TagServeur

/**
 * which sub screen of the profile editor is open
 *
 * an enum and not the displayed label. the `when` used to compare `rubrique` against `t.xxx`, so
 * navigation depended on two translated strings being different. they were not: the english
 * `cadreDeProfil` read "Avatar decoration", exactly like `decorationDAvatar`, so opening the frames
 * fell into the decorations branch. a key that does not change with the language cannot do that
 */
private enum class Rubrique { DECORATION, EFFET, CADRE, PLAQUE, TAG }

/** the sub screen's title, in the current language */
@Composable
private fun Rubrique.titre(): String {
    val t = textes()
    return when (this) {
        Rubrique.DECORATION -> t.decorationDAvatar
        Rubrique.EFFET -> t.effetDeProfil
        Rubrique.CADRE -> t.cadreDeProfil
        Rubrique.PLAQUE -> t.plaqueNominative
        Rubrique.TAG -> t.tagDuServeur
    }
}

/**
 * "edit profile", the whole screen, modelled on the reference's
 *
 * there was none: two fields sat on the profile screen, name and pronouns, with a separate sheet
 * for the colours and the decoration. asked for from outside with a screenshot in hand, and
 * strictly the same thing was wanted, nitro features included
 *
 * the sections follow the reference's order — display name, name styles, pronouns, bio, profile
 * theme, avatar decoration, profile effect, profile frame, nameplate, server tag. the shop and the
 * connections were dropped by the owner, the first having no object here and the second wanting an
 * oauth per service to show one line, and the old username badge is a leftover of discord's
 * discriminators, which have no matrix equivalent since a matrix id does not change
 *
 * everything is written to the account's extended profile (MSC4133) and nothing to a server, so it
 * follows the person everywhere and reaches people who share no server with them. that is the
 * reference's behaviour, and for the server tag it is the whole point
 *
 * text fields are written on losing focus and never on keystroke, or every character would go out
 * as a request and a half typed name would propagate to the other devices
 */
@Composable
fun EcranModifierProfil(
    userId: String,
    nom: String?,
    avatarUrl: String?,
    etendu: ProfilEtendu,
    /** the decoration catalogue, all servers together */
    decorations: List<DecorationAvatar>,
    /**
     * the two other families, same origin and same mechanics
     *
     * this is the point that makes packaging viable: the apk only ships a default set, and the
     * whole shop lives in the server's catalogue, published once and browsed by everybody. without
     * it, choosing would be limited to what we compiled in
     */
    effets: List<DecorationAvatar> = emptyList(),
    plaques: List<DecorationAvatar> = emptyList(),
    /**
     * profile frames
     *
     * they had no catalogue, only importing an image. the reference's shop does not publish any
     * either (checked against its response: three families, none of frames), but velvet's catalogue
     * can carry them: it is the same room state as the other families
     */
    cadres: List<DecorationAvatar> = emptyList(),
    /** the tags offered by the servers we joined: `(serveurId, nom, tag)` */
    tags: List<Triple<String, String, TagServeur>>,
    /**
     * the display name, which is not a profile field
     *
     * it goes through `setDisplayName`, a different api from the extended profile, so it is saved
     * separately from [onEnregistrer] rather than folded into it
     */
    onNom: (String) -> Unit,
    /** opens the system picker and uploads. immediate, see the note on the draft */
    onChangerAvatar: () -> Unit,
    onBanniere: () -> Unit,
    onImporterDecoration: () -> Unit,
    /** the three ornaments that upload from the gallery. immediate too */
    onEffet: () -> Unit,
    onCadre: () -> Unit,
    onPlaque: () -> Unit,
    /**
     * writes the draft: the whole profile, and which keys of it changed
     *
     * one callback where there were eight typed ones. the editor used to fire a write per gesture,
     * so it needed a setter per field; now it keeps a draft and hands it over once, and the parent
     * walks [ProfilEtendu.CLES] rather than wiring each name. a field added to the model is then
     * saveable without touching this signature
     */
    onEnregistrer: (brouillon: ProfilEtendu, cles: Set<String>) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var rubrique by remember { mutableStateOf<Rubrique?>(null) }

    /** which of the theme's two colours is being picked, or `null` */
    var couleurEnCours by remember { mutableStateOf<String?>(null) }

    // ---------------------------------------------------------------- the draft
    //
    // **nothing is written until you ask for it**, and that is a change of model
    //
    // every gesture used to go straight out: pick a decoration, a request leaves. text fields
    // wrote on losing focus. so there was nothing to save and nothing to cancel, and going back
    // "applied the changes" because they had already been applied, one at a time, including the
    // ones you were only trying out. reported as the editor having no save button, which is the
    // same thing seen from the front
    //
    // so the screen keeps a draft and the keys you touched, and writes only those on save. the
    // keys are [ProfilEtendu.CLES], so a field added to the model saves itself
    //
    // **the exception, and it is not laziness**: the avatar, the banner and an imported ornament
    // upload at once. the file has to leave the phone to become an `mxc://`, and there is nothing
    // to draft about an upload that has happened. they come back through [etendu], and the merge
    // below is what keeps them from being clobbered by a draft opened before
    var brouillon by remember { mutableStateOf(etendu) }
    var touches by remember { mutableStateOf(emptySet<String>()) }
    var nomBrouillon by remember { mutableStateOf(nom.orEmpty()) }
    var nomTouche by remember { mutableStateOf(false) }
    var demandeSortie by remember { mutableStateOf(false) }

    val modifie = touches.isNotEmpty() || (nomTouche && nomBrouillon.trim() != nom.orEmpty())

    // an upload landed: take the new values for everything the draft has not touched
    LaunchedEffect(etendu) { brouillon = etendu.fusionner(brouillon, touches) }
    LaunchedEffect(nom) { if (!nomTouche) nomBrouillon = nom.orEmpty() }

    /** notes a change in the draft. the key is what decides it gets written on save */
    fun poser(cle: String, valeur: String?) {
        brouillon = brouillon.avec(cle, valeur)
        touches = touches + cle
    }

    fun enregistrer() {
        if (nomTouche && nomBrouillon.trim() != nom.orEmpty()) onNom(nomBrouillon.trim())
        if (touches.isNotEmpty()) onEnregistrer(brouillon, touches)
        touches = emptySet()
        nomTouche = false
    }

    // back goes up one level, it does not close everything, and it does not throw the draft away
    //
    // reported twice. first, every back press closed the profile entirely: the handler called
    // `onFermer` whatever the depth, so opening "avatar decoration" then going back left the
    // whole screen instead of giving the list back. then, once there was a draft, back silently
    // dropped it — so it asks, and the question has three answers, since "cancel" is the one you
    // want when you hit back by accident
    androidx.activity.compose.BackHandler {
        when {
            rubrique != null -> rubrique = null
            modifie -> demandeSortie = true
            else -> onFermer()
        }
    }

    Column(
        Modifier.fillMaxSize().background(tokens.bg).statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = tokens.espM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(
                Icones.retour,
                tint = tokens.textSecondary,
                taille = 22.dp,
                description = t.retour,
                modifier = Modifier.clickable {
                    when {
                        rubrique != null -> rubrique = null
                        modifie -> demandeSortie = true
                        else -> onFermer()
                    }
                },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                rubrique?.titre() ?: t.modifierLeProfil,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.headingSize.sp,
                modifier = Modifier.weight(1f),
            )
            // save, and only when there is something to save
            //
            // not greyed out and always there: a button that is permanently dim says the screen
            // is broken, while its absence says plainly that nothing is pending. it appears the
            // moment you change anything, which is also how you learn the editor has a draft
            if (modifie) {
                Text(
                    t.enregistrer,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(tokens.radiusMedium))
                        .background(tokens.accent)
                        .curseurMain()
                        .clickable { enregistrer() }
                        .padding(horizontal = tokens.espM, vertical = 7.dp),
                )
            }
        }

        when (rubrique) {
            Rubrique.DECORATION -> ChoixOrnement(
                userId = userId,
                nom = nomBrouillon.ifBlank { nom.orEmpty() },
                avatarUrl = avatarUrl,
                porte = brouillon.decoration,
                catalogue = decorations,
                onChoisir = { poser(ProfilEtendu.CLE_DECORATION, it) },
                onImporter = onImporterDecoration,
            )

            Rubrique.EFFET -> ChoixImage(
                catalogue = effets,
                porte = brouillon.effet,
                onChoisir = { poser(ProfilEtendu.CLE_EFFET, it) },
                onImporter = onEffet,
                proportion = 0.51f,
                // three and not two: at two, a 450 by 880 card took half the screen's height and
                // four of them fitted, so for three hundred and fifty objects that is ninety swipes
                colonnes = 3,
                // the effect is a transparent overlay: on its own it is a grey box with three
                // sparkles in the middle. what you judge is the effect on your profile
                dessous = { PastilleProfil(userId, nom, avatarUrl, empile = true) },
            )

            Rubrique.PLAQUE -> ChoixImage(
                catalogue = plaques,
                porte = brouillon.plaque,
                onChoisir = { poser(ProfilEtendu.CLE_PLAQUE, it) },
                onImporter = onPlaque,
                proportion = 5.3f,
                colonnes = 1,
                remplit = true,
                dessus = { PastilleProfil(userId, nom, avatarUrl, empile = false) },
            )

            Rubrique.CADRE -> ChoixCadre(
                userId = userId,
                nom = nom,
                avatarUrl = avatarUrl,
                porte = brouillon.cadre,
                catalogue = cadres,
                onChoisir = { poser(ProfilEtendu.CLE_CADRE, it) },
                onImporter = onCadre,
            )

            Rubrique.TAG -> ChoixTag(tags, brouillon) { tag ->
                // both fields leave together: an emoji with no text, or the other way round, is
                // not a tag. `tagDe` wants both, and half a tag written leaves a dead key
                poser(ProfilEtendu.CLE_TAG_EMOJI, tag?.emoji)
                poser(ProfilEtendu.CLE_TAG_TEXTE, tag?.texte)
            }

            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                // the preview shows the draft, which is the point of a preview: you judge what
                // you are about to save, not what the server still holds
                Apercu(
                    userId,
                    nomBrouillon.ifBlank { nom.orEmpty() },
                    avatarUrl,
                    brouillon,
                    onChangerAvatar,
                    onBanniere,
                )

                ChampEdition(
                    t.nomDAffichage,
                    nomBrouillon,
                    t.commentOnVousVoit,
                    { nomBrouillon = it; nomTouche = true },
                )

                // the display name style is removed, on request, to be seen later. both keys stay
                // in `ProfilEtendu`: they cost nothing, and removing them would mean reinventing
                // them the day the section comes back. nothing writes or reads them until it is
                // restored

                ChampEdition(
                    t.pronoms,
                    brouillon.pronoms.orEmpty(),
                    t.pronomsExemple,
                    { poser(ProfilEtendu.CLE_PRONOMS, it.ifBlank { null }) },
                )

                ChampEdition(
                    t.bio,
                    brouillon.bio.orEmpty(),
                    t.quelquesLignesSurVous,
                    { poser(ProfilEtendu.CLE_BIO, it.ifBlank { null }) },
                    lignes = 4,
                    maximum = ProfilEtendu.BIO_MAX,
                    // the shortcodes you type, rendered under the field
                    //
                    // the model has always said `:emoji:` renders in a bio, "which is half the
                    // point of the field", and the desktop has rendered them since. the phone
                    // showed the raw text, here and on the profile page, so you wrote
                    // `:blahaj:` and had no way of knowing it would become an image
                    apercuEmotes = true,
                )

                Intertitre(t.themeDeProfil)
                Text(
                    t.themeDeProfilAide,
                    color = tokens.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(
                        start = tokens.espXl, end = tokens.espXl, bottom = tokens.espS,
                    ),
                )
                // two pencil squares, and not a row of prepared gradients
                //
                // the reference separates the two colours and lets each be chosen freely. said
                // outright: the colour pickers are too restricted and not practical. twelve
                // gradients had a case for a role colour, which you read on an imposed background,
                // and none for your own theme, which is exactly where you want your own colour
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = tokens.espXl),
                    horizontalArrangement = Arrangement.spacedBy(tokens.espM),
                ) {
                    CarreCouleur(
                        t.primaire,
                        brouillon.couleurHaute,
                        Modifier.weight(1f),
                    ) { couleurEnCours = "haute" }
                    CarreCouleur(
                        t.secondaire,
                        brouillon.couleurBasse,
                        Modifier.weight(1f),
                    ) { couleurEnCours = "basse" }
                }

                Intertitre(t.ornements)
                LigneOrnement(
                    t.decorationDAvatar,
                    valeur = if (brouillon.decoration != null) t.portee else t.aucune,
                    apercu = brouillon.decoration,
                    onRetirer = { poser(ProfilEtendu.CLE_DECORATION, null) }
                        .takeIf { brouillon.decoration != null },
                ) { rubrique = Rubrique.DECORATION }
                LigneOrnement(
                    t.effetDeProfil,
                    valeur = if (brouillon.effet != null) t.actif else t.aucun,
                    apercu = brouillon.effet,
                    onRetirer = { poser(ProfilEtendu.CLE_EFFET, null) }
                        .takeIf { brouillon.effet != null },
                ) { rubrique = Rubrique.EFFET }
                LigneOrnement(
                    t.cadreDeProfil,
                    valeur = if (brouillon.cadre != null) t.actif else t.aucun,
                    apercu = brouillon.cadre,
                    onRetirer = { poser(ProfilEtendu.CLE_CADRE, null) }
                        .takeIf { brouillon.cadre != null },
                ) { rubrique = Rubrique.CADRE }
                LigneOrnement(
                    t.plaqueNominative,
                    valeur = if (brouillon.plaque != null) t.active else t.aucune,
                    apercu = brouillon.plaque,
                    onRetirer = { poser(ProfilEtendu.CLE_PLAQUE, null) }
                        .takeIf { brouillon.plaque != null },
                ) { rubrique = Rubrique.PLAQUE }

                Intertitre(t.identite)
                LigneOrnement(
                    t.tagDuServeur,
                    valeur = tagDe(brouillon)?.let { "${it.emoji} ${it.texte}" } ?: t.aucun,
                ) { rubrique = Rubrique.TAG }

                if (brouillon.banniere != null) {
                    LigneOrnement(t.retirerLaBanniere, valeur = "", danger = true) {
                        poser(ProfilEtendu.CLE_BANNIERE, null)
                    }
                }
                Spacer(Modifier.height(tokens.espEcran))
            }
        }
    }

    // **the colour sheet after the screen and not before: that is the whole fix**
    //
    // it used to be emitted first, right after the states. but `PanneauGlissant` stacks its
    // children in a `Box`, where the first is drawn at the bottom: the sheet really did open,
    // underneath the screen's opaque column, which covered it completely and took the touches
    // in its place. from the outside, touching a colour square did nothing — and the fault was
    // not in the picker, which had already been fixed once for another reason
    //
    // emitted last, it is on top. same rule as the image sending banner and the roles dialog:
    // a layer goes after what it covers, never before
    couleurEnCours?.let { laquelle ->
        // both colours in the same panel: the preview shows the gradient while you compose it, and
        // the touched dot says which one you are changing. which one you come in by depends on the
        // square you touched
        val haute = laquelle == "haute"
        FeuilleCouleur(
            titre = t.themeDeProfil,
            couleurInitiale = if (haute) brouillon.couleurHaute else brouillon.couleurBasse,
            secondeInitiale = if (haute) brouillon.couleurBasse else brouillon.couleurHaute,
            onChoisirDeux = { premiere, seconde ->
                poser(ProfilEtendu.CLE_COULEUR_HAUTE, if (haute) premiere else seconde)
                poser(ProfilEtendu.CLE_COULEUR_BASSE, if (haute) seconde else premiere)
            },
            onFermer = { couleurEnCours = null },
        )
    }

    // leaving with a draft open: the three answers, and "cancel" is there because back gets hit
    // by accident more often than it gets hit on purpose
    if (demandeSortie) {
        DialogueSortie(
            onEnregistrer = { demandeSortie = false; enregistrer(); onFermer() },
            onAbandonner = {
                demandeSortie = false
                brouillon = etendu
                touches = emptySet()
                nomBrouillon = nom.orEmpty()
                nomTouche = false
                onFermer()
            },
            onAnnuler = { demandeSortie = false },
        )
    }
}

/** the unsaved changes question, in the app's own dialog shape */
@Composable
private fun DialogueSortie(
    onEnregistrer: () -> Unit,
    onAbandonner: () -> Unit,
    onAnnuler: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onAnnuler) {
        Column(
            Modifier.clip(RoundedCornerShape(tokens.radiusLarge))
                .background(tokens.surfaceRaised)
                .padding(tokens.espXl),
        ) {
            Text(
                t.modificationsNonEnregistrees,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.height(tokens.espS))
            Text(t.quitterSansEnregistrer, color = tokens.textMuted, fontSize = 13.sp)
            Spacer(Modifier.height(tokens.espL))
            Text(
                t.enregistrer,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.accent)
                    .curseurMain().clickable(onClick = onEnregistrer)
                    .padding(vertical = 11.dp),
            )
            Spacer(Modifier.height(tokens.espS))
            Text(
                t.abandonnerLesModifications,
                color = tokens.danger,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .curseurMain().clickable(onClick = onAbandonner)
                    .padding(vertical = 11.dp),
            )
            Text(
                t.annuler,
                color = tokens.textSecondary,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .curseurMain().clickable(onClick = onAnnuler)
                    .padding(vertical = 11.dp),
            )
        }
    }
}

/**
 * the preview at the top, exactly like the reference: banner, decorated avatar, nickname in its
 * gradient, pronouns and tag. it is the only place you see the effect of what you are setting
 * below, and without it each choice would be made blind then checked elsewhere
 */
@Composable
private fun Apercu(
    userId: String,
    nom: String?,
    avatarUrl: String?,
    etendu: ProfilEtendu,
    onChangerAvatar: () -> Unit,
    onBanniere: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Box(Modifier.fillMaxWidth().height(120.dp).background(degradeDe(userId, etendu))) {
        etendu.banniere?.let {
            Box(Modifier.fillMaxSize()) {
                EmojiEnLigne(it, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
        }
        // the banner's pencil, top right like on the screenshot
        Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp)
                .size(30.dp).clip(CircleShape).background(tokens.bg.copy(alpha = 0.55f))
                .clickable(onClick = onBanniere),
            contentAlignment = Alignment.Center,
        ) {
            Icone(Icones.modifier, tint = Color.White, taille = 15.dp, description = t.banniere)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espXl).offset(y = (-30).dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(78.dp).clip(CircleShape).background(tokens.bg)
                    .padding(4.dp)
                    .clickable(onClick = onChangerAvatar)
            ) {
                Avatar(userId, nom, avatarUrl, 70.dp, decoration = etendu.decoration)
            }
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(tokens.surfaceRaised)
                    .clickable(onClick = onChangerAvatar),
                contentAlignment = Alignment.Center,
            ) {
                Icone(Icones.modifier, tint = tokens.textSecondary, taille = 13.dp)
            }
        }
    }
    Column(Modifier.padding(horizontal = tokens.espXl).offset(y = (-18).dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // the nickname's gradient is here and nowhere else on this screen: this is the preview,
            // so the only place that has to show the result
            val degradeNom = etendu.nomCouleurA?.let { a ->
                Brush.horizontalGradient(
                    listOf(
                        Color(android.graphics.Color.parseColor(a)),
                        Color(android.graphics.Color.parseColor(etendu.nomCouleurB ?: a)),
                    )
                )
            }
            // two separate `copy` calls and not one: `TextStyle.copy` has two overloads, one taking
            // `color` and the other `brush`. passing them together does not compile, and that is
            // consistent, only one of the two can paint the text
            val styleNom = if (degradeNom != null) {
                LocalTextStyle.current.copy(brush = degradeNom)
            } else {
                LocalTextStyle.current.copy(color = tokens.textPrimary)
            }
            Text(
                nom ?: userId.removePrefix("@").substringBefore(':'),
                style = styleNom,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            tagDe(etendu)?.let {
                Spacer(Modifier.width(8.dp))
                BadgeTag(it, hauteur = 20f)
            }
        }
        Row {
            Text(
                userId.removePrefix("@").substringBefore(':'),
                color = tokens.textMuted,
                fontSize = 12.sp,
            )
            etendu.pronoms?.let {
                Text(" · ", color = tokens.textMuted, fontSize = 12.sp)
                Text(it, color = tokens.textMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Intertitre(texte: String) {
    val tokens = LocalTokens.current
    Text(
        texte,
        color = tokens.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(
            start = tokens.espXl, end = tokens.espXl, top = 20.dp, bottom = tokens.espS,
        ),
    )
}

/**
 * one editable field: title, box, hint, and a counter when there is a cap
 *
 * **it writes into the draft on every keystroke, and that is a change**
 *
 * it used to commit on losing focus, and also on leaving the screen, because on a phone the
 * gesture after writing a bio is back, focus is never lost, and the text went with the screen.
 * both existed because a commit was a network request and one per character was out of the
 * question
 *
 * there is no request any more: the editor keeps a draft and writes it when you ask. so the field
 * reports every keystroke, which is what makes the save button appear while you are still typing
 * rather than after you happen to touch somewhere else. the two commit paths are gone, and with
 * them the one that fired on navigating into a sub screen
 */
@Composable
private fun ChampEdition(
    titre: String,
    valeur: String,
    indication: String,
    onChange: (String) -> Unit,
    lignes: Int = 1,
    maximum: Int = 80,
    /**
     * render the `:shortcode:` emoji under the field
     *
     * the model has always said a bio renders them, "which is half the point of the field", and
     * the desktop has done it for a while. the phone showed the raw text, so you typed
     * `:blahaj:` with no way of knowing it would become an image. the preview only appears when
     * the text holds a shortcode a loaded pack actually knows, so it never takes space to say
     * nothing
     */
    apercuEmotes: Boolean = false,
) {
    val tokens = LocalTokens.current
    val emotes = LocalEmotes.current

    Intertitre(titre)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espXl)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (valeur.isEmpty()) {
            // as many lines as the field: a truncated hint on a four line field leaves three empty
            // lines under a cut off sentence
            Text(indication, color = tokens.textMuted, fontSize = 14.sp, maxLines = lignes)
        }
        BasicTextField(
            value = valeur,
            // the cap only applies to what you type. `take` was applied on every keystroke, so also
            // to text coming from the server: a bio written on the desktop, longer than the phone's
            // limit, got shortened without anybody asking. a value that is too long stays whole and
            // the counter turns red, so you choose yourself what to remove
            onValueChange = { saisi ->
                onChange(
                    if (saisi.length <= valeur.length) {
                        saisi
                    } else {
                        saisi.take(maxOf(maximum, valeur.length))
                    },
                )
            },
            singleLine = lignes == 1,
            maxLines = lignes,
            // a bio field one line tall does not invite writing four lines, and you have to have
            // typed to find out that it grows
            minLines = lignes,
            textStyle = LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (apercuEmotes) {
        // only when a loaded pack knows one of them: an unknown shortcode stays text, and
        // repeating the line you are already looking at would teach nothing
        val connus = remember(valeur, emotes) {
            MOTIF_RACCOURCI_BIO.findAll(valeur).any { trouve ->
                emotes.any { it.shortcode == trouve.groupValues[1] }
            }
        }
        if (connus) {
            Box(
                Modifier.fillMaxWidth().padding(
                    start = tokens.espXl, end = tokens.espXl, top = 6.dp,
                ),
            ) {
                ApercuAvecEmotes(
                    texte = valeur,
                    couleur = tokens.textSecondary,
                    taille = 14.sp,
                    maxLines = lignes,
                    lineHeight = 18.sp,
                )
            }
        }
    }
    if (maximum > 80) {
        Text(
            "${valeur.length} / $maximum",
            color = if (valeur.length > maximum) tokens.danger else tokens.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = tokens.espXl, vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** `:name:`, the same characters a shortcode accepts when typed */
private val MOTIF_RACCOURCI_BIO = Regex(""":([A-Za-z0-9_+-]+):""")

/** a settings row: title, current value, chevron. the reference's pattern */
@Composable
private fun LigneOrnement(
    titre: String,
    valeur: String,
    apercu: String? = null,
    danger: Boolean = false,
    onRetirer: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 4.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apercu?.let {
            Box(Modifier.size(26.dp)) { EmojiEnLigne(it) }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            titre,
            color = if (danger) tokens.danger else tokens.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (valeur.isNotBlank()) {
            Text(valeur, color = tokens.textMuted, fontSize = 13.sp, maxLines = 1)
        }
        onRetirer?.let {
            Spacer(Modifier.width(8.dp))
            Icone(
                Icones.fermer,
                tint = tokens.textMuted,
                taille = 15.dp,
                description = t.retirer,
                modifier = Modifier.clickable(onClick = it),
            )
        }
        Spacer(Modifier.width(6.dp))
        Icone(Icones.suite, tint = tokens.textMuted, taille = 14.dp)
    }
}

/**
 * one of the theme's two squares: the colour, its name below, a pencil in the corner
 *
 * the pencil is not decorative, without it a coloured square looks like a displayed state rather
 * than a setting that opens. it is the sign the reference uses in the same place
 *
 * "auto" when nothing is chosen: the colour is then derived from the id, and you have to be able to
 * go back to it. a customisation you cannot take back is not one
 */
@Composable
private fun CarreCouleur(
    libelle: String,
    valeur: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(66.dp)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(valeur?.let { couleurOuNull(it) } ?: tokens.surfaceRaised)
                .clickable(onClick = onClick),
        ) {
            if (valeur == null) {
                Text(
                    "Auto",
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Icone(
                Icones.modifier,
                tint = Color.White,
                taille = 16.dp,
                description = t.changer,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
        Text(
            libelle,
            color = tokens.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * choosing a decoration: your own avatar wearing it, and not the ring on its own
 *
 * a ring shown empty does not say what it will give, the relation between the circle and the face
 * is precisely what you are choosing. so we show the result
 */
@Composable
private fun ChoixOrnement(
    userId: String,
    nom: String?,
    avatarUrl: String?,
    porte: String?,
    catalogue: List<DecorationAvatar>,
    onChoisir: (String?) -> Unit,
    onImporter: () -> Unit,
    /** frames: they are previewed around a card, not around a face */
    cadres: Boolean = false,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val tout = remember(catalogue, porte) {
        val connus = catalogue.map { it.mxc }.toSet()
        // what you are wearing always appears, even if no server offers it any more: otherwise no
        // box would look ticked while the avatar shows it
        catalogue + listOfNotNull(
            porte?.takeIf { it !in connus }?.let { DecorationAvatar(it, t.laVotre, it) },
        )
    }
    // you try before you set. touching a box did not change your mind, it changed the profile,
    // immediately and for everybody. browsing six hundred decorations therefore meant publishing
    // six hundred
    var choisi by remember(porte) { mutableStateOf(porte) }

    CadreChoix(
        modifie = choisi != porte,
        onValider = { onChoisir(choisi) },
        entete = {
            EnteteApercu(tout.firstOrNull { it.mxc == choisi }?.nom) {
                // the original file, not the preview: this is the only place on the screen where
                // the ornament animates. the grid shows still images because it displays six
                // hundred of them, here there is only one, and it is what you are going to wear
                if (cadres) {
                    choisi?.let { ApercuCadre(it, Modifier.width(220.dp)) }
                        ?: Avatar(userId, nom, avatarUrl, 88.dp)
                } else {
                    Avatar(userId, nom, avatarUrl, 88.dp, decoration = choisi)
                }
            }
        },
    ) {
    // search and sort, like on the desktop: six hundred decorations are not browsed by eye, and
    // that is even truer on a phone screen. the filtering rule is shared between the two
    // (`filtrerCatalogue`) so they do not diverge
    var recherche by remember { mutableStateOf("") }
    var tri by remember { mutableStateOf(TriCatalogue.NOM) }
    val affiches = remember(tout, recherche, tri) { filtrerCatalogue(tout, recherche, tri) }
    Column(Modifier.fillMaxSize()) {
    BarreCatalogue(
        recherche = recherche,
        onRecherche = { recherche = it },
        tri = tri,
        onTri = { tri = tri.suivant() },
        affiches = affiches.size,
        total = tout.size,
    )
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        // adaptive, and not four fixed columns. at four, a decoration was 72 dp on a phone screen:
        // you could tell neither the feathers, nor the fangs, nor the colour apart. a minimum size
        // lets the screen decide the column count, instead of the other way round
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(TAILLE_CASE),
        modifier = Modifier.weight(1f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.espXl),
        horizontalArrangement = Arrangement.spacedBy(tokens.espM),
        verticalArrangement = Arrangement.spacedBy(tokens.espM),
    ) {
        item {
            CaseOrnement(userId, nom, avatarUrl, null, choisi == null, cadres) { choisi = null }
        }
        val groupes = if (tri == TriCatalogue.COLLECTION) parCollection(affiches) else emptyList()
        if (groupes.isNotEmpty()) {
            groupes.forEach { (collection, objets) ->
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    TitreCollection(collection, objets.size)
                }
                // keyed on the image and not on the rank. without it, filtering the grid recomposes
                // each box instead of moving it: the hundred and eighty ornaments decode again, and
                // the selection jumps box
                items(objets.size, key = { objets[it].mxc }) { rang ->
                    val objet = objets[rang]
                    CaseOrnement(userId, nom, avatarUrl, objet, choisi == objet.mxc, cadres) {
                        choisi = objet.mxc
                    }
                }
            }
        } else {
            items(affiches.size, key = { affiches[it].mxc }) { rang ->
                val objet = affiches[rang]
                CaseOrnement(userId, nom, avatarUrl, objet, choisi == objet.mxc, cadres) {
                    choisi = objet.mxc
                }
            }
        }
        item {
            Box(
                Modifier.size(TAILLE_CASE).clip(CircleShape).background(tokens.surfaceRaised)
                    .clickable(onClick = onImporter),
                contentAlignment = Alignment.Center,
            ) {
                Icone(
                    Icones.ajouter,
                    tint = tokens.textSecondary,
                    taille = 22.dp,
                    description = t.importer,
                )
            }
        }
    }
    }
    }
}

@Composable
private fun TitreCollection(collection: String, combien: Int) {
    val tokens = LocalTokens.current
    Text(
        "$collection — $combien",
        color = tokens.textMuted,
        fontSize = 11.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        modifier = Modifier.padding(top = tokens.espS),
    )
}

/**
 * a grid of images to choose from, effects and nameplates
 *
 * distinct from [ChoixOrnement], which shows the avatar wearing the decoration: a ring is only
 * judged placed around a face. an effect and a nameplate are judged on their own, and by their
 * shape, hence the ratio as a parameter, 450 by 880 for an effect and 448 by 84 for a nameplate.
 * showing them square would make them unrecognisable
 *
 * one column for a nameplate, two for an effect: a 5.3 to 1 strip does not read next to another
 */
@Composable
private fun ChoixImage(
    catalogue: List<DecorationAvatar>,
    porte: String?,
    onChoisir: (String?) -> Unit,
    onImporter: () -> Unit,
    proportion: Float,
    colonnes: Int,
    /** `Crop` instead of `Fit`: true for a nameplate, which is a background */
    remplit: Boolean = false,
    /** the profile drawn under the image, an effect being a transparent overlay */
    dessous: @Composable () -> Unit = {},
    /** the profile drawn over it, a nameplate being a member row's background */
    dessus: @Composable () -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current
    val tout = remember(catalogue, porte) {
        val connus = catalogue.map { it.mxc }.toSet()
        // what you are wearing always appears, even if the catalogue no longer offers it
        catalogue + listOfNotNull(
            porte?.takeIf { it !in connus }?.let { DecorationAvatar(it, t.leVotre, it) },
        )
    }
    var choisi by remember(porte) { mutableStateOf(porte) }

    CadreChoix(
        modifie = choisi != porte,
        onValider = { onChoisir(choisi) },
        entete = {
            EnteteApercu(tout.firstOrNull { it.mxc == choisi }?.nom) {
                Box(
                    // two fifths wide for a portrait, the full width for a strip: an effect shown
                    // full page would crush the grid you came to see
                    Modifier.fillMaxWidth(if (proportion < 1f) 0.42f else 1f)
                        .aspectRatio(proportion)
                        .clip(RoundedCornerShape(tokens.radiusMedium))
                        .background(tokens.surfaceRaised),
                ) {
                    dessous()
                    choisi?.let {
                        // no `taillePx`: here we want the original, so the animation
                        EmojiEnLigne(
                            it,
                            contentScale = if (remplit) {
                                androidx.compose.ui.layout.ContentScale.Crop
                            } else {
                                androidx.compose.ui.layout.ContentScale.Fit
                            },
                        )
                    }
                    dessus()
                }
            }
        },
    ) {
    var recherche by remember { mutableStateOf("") }
    var tri by remember { mutableStateOf(TriCatalogue.NOM) }
    val affiches = remember(tout, recherche, tri) { filtrerCatalogue(tout, recherche, tri) }
    Column(Modifier.fillMaxSize()) {
    BarreCatalogue(
        recherche = recherche,
        onRecherche = { recherche = it },
        tri = tri,
        onTri = { tri = tri.suivant() },
        affiches = affiches.size,
        total = tout.size,
    )
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(colonnes),
        modifier = Modifier.weight(1f),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.espXl),
        horizontalArrangement = Arrangement.spacedBy(tokens.espM),
        verticalArrangement = Arrangement.spacedBy(tokens.espM),
    ) {
        // an empty catalogue has to be told apart from a catalogue that did not load
        //
        // without this line the two give the same thing on screen: the "none" box, the "import"
        // box, and nothing in between. that is what got "the nameplates are not visible" reported
        // as a display fault, when the grid was faithfully showing an empty list it should not have
        // received
        if (catalogue.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(colonnes) }) {
                Text(
                    t.catalogueVide,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = tokens.espM),
                )
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().aspectRatio(proportion)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .then(
                        if (choisi == null) {
                            Modifier.border(
                                2.dp, tokens.accent, RoundedCornerShape(tokens.radiusMedium),
                            )
                        } else Modifier
                    )
                    .clickable { choisi = null },
                contentAlignment = Alignment.Center,
            ) {
                Text(t.aucun, color = tokens.textMuted, fontSize = 13.sp)
            }
        }
        val groupes = if (tri == TriCatalogue.COLLECTION) parCollection(affiches) else emptyList()
        if (groupes.isNotEmpty()) {
            groupes.forEach { (collection, objets) ->
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(colonnes) }) {
                    TitreCollection(collection, objets.size)
                }
                // keyed on the image and not on the rank. without it, filtering the grid recomposes
                // each box instead of moving it: the hundred and eighty ornaments decode again, and
                // the selection jumps box
                items(objets.size, key = { objets[it].mxc }) { rang ->
                    val objet = objets[rang]
                    CaseImage(objet, objet.mxc == choisi, proportion, remplit, dessous, dessus) {
                        choisi = objet.mxc
                    }
                }
            }
        } else {
            items(affiches.size, key = { affiches[it].mxc }) { rang ->
                val objet = affiches[rang]
                CaseImage(objet, objet.mxc == choisi, proportion, remplit, dessous, dessus) {
                    choisi = objet.mxc
                }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().aspectRatio(proportion)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .clickable(onClick = onImporter),
                contentAlignment = Alignment.Center,
            ) {
                Icone(
                    Icones.ajouter,
                    tint = tokens.textSecondary,
                    taille = 22.dp,
                    description = t.importer,
                )
            }
        }
    }
    }
    }
}

/**
 * a box of the image grid: the object in its ratio, its name below
 *
 * pulled out so the grid can call it from two places, the plain list and the sections by
 * collection, without copying forty lines
 */
@Composable
private fun CaseImage(
    objet: DecorationAvatar,
    choisie: Boolean,
    proportion: Float,
    remplit: Boolean,
    dessous: @Composable () -> Unit,
    dessus: @Composable () -> Unit,
    onChoisir: () -> Unit,
) {
    val tokens = LocalTokens.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(proportion)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised)
                .then(
                    if (choisie) {
                        Modifier.border(2.dp, tokens.accent, RoundedCornerShape(tokens.radiusMedium))
                    } else Modifier
                )
                .clickable(onClick = onChoisir),
        ) {
            dessous()
            // `Fit` and not `Crop` for an effect: a preview has to show the whole object, and
            // cropped, a 450 by 880 came down to a close up on three pixels of the middle. a
            // nameplate, being a background, covers its box
            EmojiEnLigne(
                objet.apercu ?: objet.mxc,
                contentScale = if (remplit) {
                    androidx.compose.ui.layout.ContentScale.Crop
                } else {
                    androidx.compose.ui.layout.ContentScale.Fit
                },
                // the preview is already a small still image: having the server shrink it again
                // would add nothing. we only thumbnail the raw file
                taillePx = VIGNETTE_PX.takeIf { objet.apercu == null },
            )
            dessus()
        }
        // the name under the box. three hundred and fifty objects look alike from a distance, and
        // alphabetical order is no use if you cannot read where you are. it is also what the
        // reference shows
        Text(
            objet.nom,
            color = tokens.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CaseOrnement(
    userId: String,
    nom: String?,
    avatarUrl: String?,
    decoration: DecorationAvatar?,
    choisie: Boolean,
    cadres: Boolean = false,
    onClick: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Box(
        Modifier.size(TAILLE_CASE).clip(if (cadres) RoundedCornerShape(12.dp) else CircleShape)
            .background(if (choisie) tokens.accent.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // your own photo, and not initials. the avatar was called without its address: every box
        // fell back on the monogram, and you chose a ring around a coloured circle that was not
        // you. and the relation between the ring and the face is very exactly what you are judging
        //
        // the preview rather than the original: a decoration weighs a megabyte and its preview
        // fifty kilobytes, and in a grid of six hundred you only judge the shape
        if (cadres) {
            // the frame borders a card: the box shows one, in miniature
            decoration?.let { ApercuCadre(it.mxc, Modifier.width(TAILLE_CASE - 12.dp)) }
                ?: Text(t.aucun, color = tokens.textMuted, fontSize = 13.sp)
        } else {
            Avatar(
                userId,
                nom,
                avatarUrl,
                TAILLE_AVATAR,
                decoration = decoration?.let { it.apercu ?: it.mxc },
            )
        }
    }
}

/**
 * the side asked of the server for a grid thumbnail
 *
 * 256 and not 160: an effect is a nearly empty portrait, and under 200 px there are no longer
 * enough pixels to recognise which one it is
 */
private const val VIGNETTE_PX = 256

/**
 * the skeleton of a choice screen: the preview on top, the grid in the middle, the confirmation at
 * the bottom
 *
 * the preview is outside the grid, and that is the whole point: placed inside, it scrolled with it
 * and disappeared at the first swipe, and browsing is precisely when you want to see it. here the
 * grid scrolls under a header that does not move
 *
 * the confirm bar only appears when something has changed: a button always present but inert gets
 * touched for nothing, and an absent button says there is nothing to confirm
 */
@Composable
private fun CadreChoix(
    modifie: Boolean,
    onValider: () -> Unit,
    entete: @Composable () -> Unit,
    grille: @Composable () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Column(Modifier.fillMaxSize()) {
        entete()
        Box(Modifier.weight(1f)) { grille() }
        if (modifie) {
            Box(
                Modifier.fillMaxWidth()
                    .padding(horizontal = tokens.espXl, vertical = tokens.espM)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.accent)
                    .clickable(onClick = onValider)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.valider,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * the worn object, large and animated, above the grid
 *
 * the grid's thumbnails are still, which is what makes six hundred objects browsable. so there had
 * to be a place where the animation shows before confirming, otherwise you choose an animated
 * decoration on a frozen image and discover the movement afterwards
 *
 * the name under the preview also says what is worn when the ticked box is off screen
 */
@Composable
private fun EnteteApercu(nomPorte: String?, contenu: @Composable () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxWidth().padding(top = tokens.espM, bottom = tokens.espXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        contenu()
        Text(
            nomPorte ?: t.aucun,
            color = if (nomPorte == null) tokens.textMuted else tokens.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (nomPorte == null) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * the profile frame, an import flow rather than a catalogue
 *
 * the three other families are chosen from a grid of over a thousand objects published once for the
 * everyone. the frame has no catalogue, and this time that is measured rather than asserted:
 * the import script tries each of the shop's hashes against the three known cdn addresses,
 * including `collectibles/` where the nameplates come from. of the 2447 files brought back and
 * unclassified, none is square, only portrait effects (450 by 880), collection banners (1024 by
 * 512) and illustrations. an avatar frame would be square, and none comes back
 *
 * presenting it like the others would give a grid with two boxes, which promises a collection that
 * does not exist
 *
 * there is no history of imported frames either: an image from the gallery is uploaded as is, and
 * nothing files it on the app's side. adding it would mean keeping a list for a group you rarely
 * change
 */
@Composable
private fun ChoixCadre(
    userId: String,
    nom: String?,
    avatarUrl: String?,
    porte: String?,
    catalogue: List<DecorationAvatar> = emptyList(),
    onChoisir: (String?) -> Unit,
    onImporter: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    // a frame is worn around a face, like a decoration: if a catalogue publishes any, the
    // decorations grid is exactly what is wanted, with its "none", its "import" and its animated
    // preview. the panel below only serves the empty catalogue, which stays the normal case as long
    // as the reference's shop offers none
    if (catalogue.isNotEmpty()) {
        ChoixOrnement(userId, nom, avatarUrl, porte, catalogue, onChoisir, onImporter, cadres = true)
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(tokens.espXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EnteteApercu(if (porte != null) t.votreCadre else null) {
            Avatar(userId, nom, avatarUrl, 88.dp, decoration = porte)
        }
        Text(
            t.cadreSansCatalogue,
            color = tokens.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = tokens.espXl),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.espM),
        ) {
            Box(
                Modifier.weight(1f).height(64.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .then(
                        if (porte == null) {
                            Modifier.border(
                                2.dp, tokens.accent, RoundedCornerShape(tokens.radiusMedium),
                            )
                        } else Modifier
                    )
                    .clickable { onChoisir(null) },
                contentAlignment = Alignment.Center,
            ) {
                Text(t.aucun, color = tokens.textMuted, fontSize = 13.sp)
            }
            Box(
                Modifier.weight(1f).height(64.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .clickable(onClick = onImporter),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icone(
                        Icones.ajouter,
                        tint = tokens.textSecondary,
                        taille = 18.dp,
                        description = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(t.importer, color = tokens.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

/** the size of a decoration choice box, and that of the avatar inside it */
private val TAILLE_CASE = 104.dp
private val TAILLE_AVATAR = 60.dp

/**
 * the profile in miniature, laid under an effect or over a nameplate
 *
 * `empile` tells the two shapes apart: an effect is a portrait, so the avatar and the nickname go
 * one above the other, and a nameplate is a strip, so they go side by side, which is the member
 * list's layout, where a nameplate really shows
 */
@Composable
private fun PastilleProfil(
    userId: String,
    nom: String?,
    avatarUrl: String?,
    empile: Boolean,
) {
    val tokens = LocalTokens.current
    val etiquette = nom?.takeIf { it.isNotBlank() }
        ?: userId.substringAfter('@').substringBefore(':')
    if (empile) {
        Column(
            Modifier.fillMaxSize().padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Avatar(userId, nom, avatarUrl, 44.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                etiquette,
                color = tokens.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    } else {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(userId, nom, avatarUrl, 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                etiquette,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/**
 * choosing the server tag
 *
 * one at a time, like the reference: wearing three badges would make them all unreadable, and the
 * point of the tag is precisely to say where you come from at a glance
 *
 * the list is empty as long as no server has set one, and saying so beats a blank page: it is a
 * server setting, not something the app is missing
 */
@Composable
private fun ChoixTag(
    tags: List<Triple<String, String, TagServeur>>,
    etendu: ProfilEtendu,
    onTag: (TagServeur?) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val porte = tagDe(etendu)
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            t.tagAide,
            color = tokens.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(tokens.espXl),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 4.dp)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised)
                .clickable { onTag(null) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t.aucun, color = tokens.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (porte == null) Coche(true, taille = 18.dp)
        }
        tags.forEach { (_, nomServeur, tag) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 4.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .clickable { onTag(tag) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BadgeTag(tag, hauteur = 22f)
                Spacer(Modifier.width(12.dp))
                Text(
                    nomServeur,
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (porte?.emoji == tag.emoji && porte.texte == tag.texte) {
                    Coche(true, taille = 18.dp)
                }
            }
        }
        if (tags.isEmpty()) {
            Text(
                t.aucunTagDisponible,
                color = tokens.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(tokens.espXl),
            )
        }
    }
}

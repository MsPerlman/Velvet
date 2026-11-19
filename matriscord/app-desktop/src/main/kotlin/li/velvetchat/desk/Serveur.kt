package li.velvetchat.desk

import li.velvetchat.core.tr

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import li.velvetchat.app.ui.*
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.Particularite
import li.velvetchat.core.model.ProfilServeur
import li.velvetchat.core.model.Server
import li.velvetchat.core.model.TagServeur
import java.io.File

/** the headings' cream, the same as edit profile's */
internal val CREME_SERVEUR = Color(0xFFFBFFBD)
internal val TEXTE_CLAIR = Color(0xFFDBDEE1)
internal val TEXTE_GRIS = Color(0xFFA9ACB3)
internal val FOND_CHAMP = Color(0xFF1E2126)
internal val BORD_CHAMP = Color(0xFF2B2F37)

/**
 * the ten server profile banners, taken off the screenshot: a gradient from a deeper top to a
 * lighter bottom. in the grid's order, 5 by 2
 */
internal val BANNIERES_SERVEUR = listOf(
    Color(0xFF3346C8) to Color(0xFF8391F0),
    Color(0xFFE8138F) to Color(0xFFFF73C4),
    Color(0xFFD3121A) to Color(0xFFF65A55),
    Color(0xFFE0600C) to Color(0xFFFBA650),
    Color(0xFFDDB42A) to Color(0xFFF7E27A),
    Color(0xFF6E2A93) to Color(0xFFB36AD2),
    Color(0xFF0784E0) to Color(0xFF5AD4FF),
    Color(0xFF2EC2B0) to Color(0xFFA6F6EC),
    Color(0xFF356310) to Color(0xFF74A82E),
    Color(0xFF18191C) to Color(0xFF55565B),
)

internal fun degradeBanniere(index: Int): Brush {
    val (haut, bas) = BANNIERES_SERVEUR[index.coerceIn(0, BANNIERES_SERVEUR.lastIndex)]
    return Brush.verticalGradient(listOf(haut, bas))
}

/**
 * the scrim and centred card of the server's small modals. escape and a click beside close them
 */
@Composable
internal fun CarteModale(largeur: Int = 440, onFermer: () -> Unit, contenu: @Composable () -> Unit) {
    val t = textes()
    val sansEffet = remember { MutableInteractionSource() }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onFermer,
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { if (it.key == Key.Escape) { onFermer(); true } else false },
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(indication = null, interactionSource = sansEffet, onClick = onFermer),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.width(largeur.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FOND_MENU)
                    .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp))
                    .clickable(indication = null, interactionSource = sansEffet) {}
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Icone(
                        Icones.fermer, tint = TEXTE_GRIS, taille = 18.dp, description = t.fermer,
                        modifier = Modifier.curseurMain().clickable(onClick = onFermer),
                    )
                }
                contenu()
            }
        }
    }
}

/** a one line field with a gold border, like the one in create your server */
@Composable
internal fun ChampDore(valeur: String, indication: String, onValeur: (String) -> Unit, onEntree: () -> Unit = {}) {
    Box(
        Modifier.fillMaxWidth().height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, DORE, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (valeur.isEmpty()) Text(indication, color = TEXTE_GRIS, fontSize = 14.sp, maxLines = 1)
        BasicTextField(
            value = valeur,
            onValueChange = onValeur,
            singleLine = true,
            textStyle = TextStyle(color = TEXTE_CLAIR, fontSize = 14.sp),
            cursorBrush = SolidColor(DORE),
            modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                if (it.key == Key.Enter) { onEntree(); true } else false
            },
        )
    }
}

@Composable
internal fun BoutonDore(libelle: String, actif: Boolean, onClick: () -> Unit) {
    Text(
        libelle,
        color = if (actif) Color(0xFF1B1D22) else TEXTE_GRIS,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (actif) DORE else Color(0xFF2A2D33))
            .then(if (actif) Modifier.curseurMain().clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
    )
}

/**
 * create your server, velvet style: a photo, a name, and that is all. the server is born with its
 * general channel, like on the phone. the footer leads to join a server, which takes an invite link
 * (see `LienInvitation`)
 */
@Composable
internal fun ModaleCreerServeur(
    onCreer: (nom: String, image: File?) -> Unit,
    onRejoindre: suspend (lien: String) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val fenetre = LocalFenetre.current
    val portee = rememberCoroutineScope()
    var rejoindre by remember { mutableStateOf(false) }
    var nom by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<File?>(null) }
    var lien by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    CarteModale(onFermer = onFermer) {
        if (!rejoindre) {
            Text(t.creeTonServeur, color = CREME_SERVEUR, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                t.serveurPhotoEtNom,
                color = TEXTE_GRIS, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            val apercu = remember(image) {
                image?.let { f -> runCatching { f.inputStream().use { loadImageBitmap(it) } }.getOrNull() }
            }
            Box(Modifier.size(84.dp)) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).curseurMain().clickable {
                    portee.launch { choisirImage(fenetre?.awt, t.photoDuServeur)?.let { image = it } }
                }.drawBehind {
                    if (apercu == null) {
                        drawRoundRect(
                            color = TEXTE_GRIS,
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                            cornerRadius = CornerRadius(size.width / 2),
                        )
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (apercu != null) {
                    Image(apercu, contentDescription = t.photoDuServeur, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icone(Icones.photo, tint = TEXTE_CLAIR, taille = 24.dp, description = null)
                        Text("PHOTO", color = TEXTE_CLAIR, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
                // the gold plus, top right of the circle and outside its clip
                Box(
                    Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(DORE),
                    contentAlignment = Alignment.Center,
                ) { Icone(Icones.plusGras, tint = Color(0xFF1B1D22), taille = 14.dp, description = null) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                t.nomDuServeurMaj, color = TEXTE_GRIS, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            ChampDore(nom, tr("Le serveur de …", "My server…"), { nom = it }, onEntree = { if (nom.isNotBlank()) onCreer(nom.trim(), image) })
            Spacer(Modifier.height(16.dp))
            BoutonDore(t.creer, actif = nom.isNotBlank()) { onCreer(nom.trim(), image) }
            Spacer(Modifier.height(20.dp))
            Text(t.dejaUneInvitation, color = TEXTE_GRIS, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                t.rejoindreUnServeur,
                color = TEXTE_CLAIR, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2D33))
                    .curseurMain().clickable { rejoindre = true }.padding(vertical = 12.dp),
            )
        } else {
            Text(t.rejoindreUnServeur, color = CREME_SERVEUR, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(t.collerLienInvitation, color = TEXTE_GRIS, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Text(
                t.lienInvitationMaj, color = TEXTE_GRIS, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            fun lancer() {
                if (lien.isBlank() || enCours) return
                enCours = true
                erreur = null
                portee.launch {
                    runCatching { onRejoindre(lien.trim()) }
                        .onSuccess { onFermer() }
                        .onFailure { erreur = t.lienSansServeur; enCours = false }
                }
            }
            // the example is built from the current installation rather than naming one: somebody's
            // domain has no business being written into the code, and an example showing an address
            // other than your own helps nobody
            val exemple = li.velvetchat.core.model.LienInvitation.encoder("!abc:exemple.org")
            ChampDore(lien, exemple.substringBeforeLast('!') + "!…", { lien = it }, onEntree = ::lancer)
            Spacer(Modifier.height(6.dp))
            Text(
                "Exemples : $exemple, #general:exemple.org",
                color = TEXTE_GRIS, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
            )
            erreur?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = ROSE_MENU, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            BoutonDore(if (enCours) t.connexionEnCours else t.rejoindre, actif = lien.isNotBlank() && !enCours, onClick = ::lancer)
            Spacer(Modifier.height(12.dp))
            Text(
                t.retour, color = TEXTE_GRIS, fontSize = 13.sp,
                modifier = Modifier.curseurMain().clickable { rejoindre = false; erreur = null },
            )
        }
    }
}

/** invite to the server: the link, and a way to copy it */
@Composable
internal fun ModaleInviter(moteur: MatrixEngine, serveur: Server, onFermer: () -> Unit) {
    val t = textes()
    val pressePapier = copieur()
    var lien by remember { mutableStateOf<String?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var copie by remember { mutableStateOf(false) }
    LaunchedEffect(serveur.id) {
        runCatching { moteur.lienInvitation(serveur.id) }
            .onSuccess { lien = it }
            .onFailure { erreur = t.ouvertureInvitationsAdmin }
    }
    CarteModale(onFermer = onFermer) {
        Text(
            t.inviteDesAmisSur(serveur.name), color = CREME_SERVEUR, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            t.envoieCeLienMaj, color = TEXTE_GRIS, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(FOND_CHAMP)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                lien ?: erreur?.let { "—" } ?: t.preparationDuLien,
                color = TEXTE_CLAIR, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (copie) t.copie else t.copier,
                color = Color(0xFF1B1D22), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (copie) Color(0xFF3BA55C) else DORE)
                    .then(
                        if (lien != null) Modifier.curseurMain().clickable {
                            pressePapier(lien!!)
                            copie = true
                        } else Modifier
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            erreur ?: t.lienSansExpiration,
            color = if (erreur != null) ROSE_MENU else TEXTE_GRIS, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * a one field modal: create a channel, create a category
 *
 * [avecType] adds the text or voice choice above the field, and [onValider]'s second parameter
 * carries it. the renaming modals have none: a channel's type is fixed at creation and never after
 */
@Composable
internal fun ModaleNom(
    titre: String,
    etiquette: String,
    bouton: String,
    avecType: Boolean = false,
    /** the preselected type: the right click menu already knows which one you want */
    vocalParDefaut: Boolean = false,
    onValider: (String, Boolean) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    var nom by remember { mutableStateOf("") }
    var vocal by remember { mutableStateOf(vocalParDefaut) }
    CarteModale(onFermer = onFermer) {
        Text(titre, color = CREME_SERVEUR, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(20.dp))
        if (avecType) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                listOf(false to t.salonTexte, true to t.salonVocal).forEach { (estVocal, libelle) ->
                    val retenu = estVocal == vocal
                    Box(
                        Modifier.weight(1f).padding(end = 8.dp)
                            .background(
                                if (retenu) DORE.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(8.dp),
                            )
                            .curseurMain()
                            .clickable { vocal = estVocal }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            libelle,
                            color = if (retenu) DORE else TEXTE_GRIS,
                            fontSize = 13.sp,
                            fontWeight = if (retenu) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Text(
            etiquette, color = TEXTE_GRIS, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        ChampDore(nom, "", { nom = it }, onEntree = { if (nom.isNotBlank()) onValider(nom.trim(), vocal) })
        Spacer(Modifier.height(16.dp))
        BoutonDore(bouton, actif = nom.isNotBlank()) { onValider(nom.trim(), vocal) }
    }
}

/**
 * the server settings, measured off the reference at 1920 by 1080: navigation on the left (x 440 to
 * 680), the form in the middle (x 732 to 1292), a preview card on the right (x 1332 to 1632), a
 * cross with "ESC" top right. over the whole app, with the wallpaper visible underneath
 *
 * only the sections velvet can carry appear in the navigation: server profile, server tag and
 * invites. the rest of the reference, boosts, apps, automod, analytics, has no equivalent on a
 * server this size
 *
 * changes save through a button, like the reference's "careful, you have unsaved changes" bar
 */
@Composable
internal fun ParametresServeur(
    moteur: MatrixEngine,
    serveur: Server,
    enLigne: Int,
    sectionInitiale: Int = 0,
    onFermer: () -> Unit,
) {
    val t = textes()
    val portee = rememberCoroutineScope()
    val fenetre = LocalFenetre.current
    var section by remember { mutableStateOf(sectionInitiale) }
    var initial by remember { mutableStateOf<ProfilServeur?>(null) }
    var brouillon by remember { mutableStateOf<ProfilServeur?>(null) }
    var avatarUrl by remember { mutableStateOf(serveur.avatarUrl) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enregistrement by remember { mutableStateOf(false) }
    LaunchedEffect(serveur.id) {
        runCatching { moteur.profilServeur(serveur.id) }
            .onSuccess { initial = it; brouillon = it; avatarUrl = it.avatarUrl ?: serveur.avatarUrl }
            .onFailure { erreur = t.profilServeurIllisible(it.message.orEmpty()) }
    }

    val sansEffet = remember { MutableInteractionSource() }
    val tokens = LocalTokens.current
    // opaque: the user's wallpaper, or the theme's colour, under a scrim, never the app
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(tokens.bg)
            .clickable(indication = null, interactionSource = sansEffet) {}
            .onPreviewKeyEvent { if (it.key == Key.Escape) { onFermer(); true } else false },
    ) {
        val large = maxWidth > 1300.dp
        CoucheFond()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Box(Modifier.fillMaxWidth().height(BARRE_HAUTE).deplaceFenetre())
        Row(Modifier.fillMaxSize()) {
            // ------------------------------------------------------------------ navigation
            Box(
                Modifier.weight(if (large) 0.36f else 0.26f).fillMaxHeight().background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.TopEnd,
            ) {
                Column(Modifier.width(if (large) 260.dp else 200.dp).padding(top = 60.dp, end = 10.dp)) {
                    TitreNav(serveur.name.uppercase())
                    EntreeNav(t.profilDuServeur, section == 0) { section = 0 }
                    EntreeNav(t.tagDuServeur, section == 1) { section = 1 }
                    FiletNav()
                    TitreNav("PERSONNES")
                    EntreeNav(t.invitations, section == 2) { section = 2 }
                    // the roles, which only existed on the phone although the panel itself has
                    // lived in `ui-commun` from the start: the desktop read the tiers to colour
                    // nicknames without ever showing or writing them
                    EntreeNav(t.roles, section == 3) { section = 3 }
                    EntreeNav(t.membres, section == 6) { section = 6 }
                    FiletNav()
                    TitreNav(t.decorations.uppercase())
                    EntreeNav(t.emoji, section == 4) { section = 4 }
                    EntreeNav(t.decorations, section == 5) { section = 5 }
                    EntreeNav("Soundboard", section == 7) { section = 7 }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.06f)))

            // content: side by side on a large screen, the card under the form when the window is
            // narrow
            val zone = Modifier.weight(if (large) 0.64f else 0.74f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(start = 40.dp, top = 60.dp, end = if (large) 40.dp else 90.dp, bottom = 40.dp)
            val corps: @Composable () -> Unit = {
                Column(Modifier.width(560.dp)) {
                    when (section) {
                        0 -> brouillon?.let { p ->
                            SectionProfil(
                                profil = p,
                                onProfil = { brouillon = it },
                                onChangerIcone = {
                                    portee.launch {
                                        val f = choisirImage(fenetre?.awt, t.iconeServeur) ?: return@launch
                                        runCatching { moteur.setAvatarServeur(serveur.id, f.absolutePath) }
                                            .onSuccess { avatarUrl = runCatching { moteur.profilServeur(serveur.id).avatarUrl }.getOrNull() }
                                            .onFailure { erreur = t.iconeNonChangee(it.message.orEmpty()) }
                                    }
                                },
                                onSupprimerIcone = {
                                    portee.launch {
                                        runCatching { moteur.setAvatarServeur(serveur.id, null) }
                                            .onSuccess { avatarUrl = null }
                                            .onFailure { erreur = t.iconeNonSupprimee(it.message.orEmpty()) }
                                    }
                                },
                            )
                        } ?: Text(erreur ?: t.lectureDuProfil, color = TEXTE_GRIS, fontSize = 14.sp)
                        1 -> SectionTag(moteur, serveur)
                        3 -> SectionRoles(moteur, serveur)
                        4 -> SectionEmojis(moteur, serveur, fenetre?.awt)
                        5 -> SectionDecorations(moteur, serveur, fenetre?.awt)
                        6 -> SectionMembres(moteur, serveur)
                        7 -> SectionSons(moteur, serveur, fenetre?.awt)
                        else -> SectionInvitations(moteur, serveur)
                    }
                    erreur?.takeIf { brouillon != null }?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = ROSE_MENU, fontSize = 13.sp)
                    }
                }
                if (section == 0) {
                    Spacer(Modifier.size(40.dp))
                    brouillon?.let { CarteApercuServeur(serveur, it, avatarUrl, enLigne) }
                }
            }
            if (large) Row(zone) { corps() } else Column(zone) { corps() }
        }
        // floating at the bottom, like the reference's unsaved changes bar
            if (section == 0 && brouillon != null && brouillon != initial) {
                        Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).width(700.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111214))
                        .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.modificationsNonEnregistrees, color = TEXTE_CLAIR, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        t.reinitialiser, color = TEXTE_CLAIR, fontSize = 14.sp,
                        modifier = Modifier.curseurMain().clickable { brouillon = initial }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Text(
                        if (enregistrement) t.enregistrementEnCours else t.enregistrer,
                        color = Color(0xFF1B1D22), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(DORE).curseurMain().clickable {
                            val p = brouillon ?: return@clickable
                            if (enregistrement) return@clickable
                            enregistrement = true
                            portee.launch {
                                runCatching { moteur.setProfilServeur(serveur.id, p) }
                                    .onSuccess { initial = p; erreur = null }
                                    .onFailure { erreur = t.nonEnregistreQuoi(it.message.orEmpty()) }
                                enregistrement = false
                            }
                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

        // the ringed cross with "ESC" underneath
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 62.dp, end = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).border(2.dp, TEXTE_GRIS, CircleShape)
                    .curseurMain().clickable(onClick = onFermer),
                contentAlignment = Alignment.Center,
            ) { Icone(Icones.fermer, tint = TEXTE_GRIS, taille = 18.dp, description = t.fermer) }
            Spacer(Modifier.height(6.dp))
            Text("ESC", color = TEXTE_GRIS, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.align(Alignment.TopEnd).height(BARRE_HAUTE)) { BoutonsFenetre() }
    }
}

@Composable
internal fun TitreNav(texte: String) {
    Text(
        texte, color = TEXTE_GRIS, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
    )
}

@Composable
internal fun EntreeNav(libelle: String, actif: Boolean, onClick: () -> Unit) {
    Text(
        libelle,
        color = if (actif) TEXTE_CLAIR else TEXTE_GRIS,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(6.dp))
            .background(if (actif) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
internal fun FiletNav() {
    Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
internal fun TitreChamp(titre: String, sousTitre: String? = null) {
    Text(titre, color = CREME_SERVEUR, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    sousTitre?.let {
        Spacer(Modifier.height(4.dp))
        Text(it, color = TEXTE_GRIS, fontSize = 13.sp)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
internal fun FiletChamp() {
    Box(Modifier.padding(vertical = 28.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
}

@Composable
private fun ChampSombre(
    valeur: String,
    onValeur: (String) -> Unit,
    modifier: Modifier = Modifier,
    indication: String = "",
    uneLigne: Boolean = true,
) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(FOND_CHAMP)
            .border(1.dp, BORD_CHAMP, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (valeur.isEmpty()) Text(indication, color = TEXTE_GRIS, fontSize = 15.sp)
        BasicTextField(
            value = valeur,
            onValueChange = onValeur,
            singleLine = uneLigne,
            textStyle = TextStyle(color = TEXTE_CLAIR, fontSize = 15.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(DORE),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionProfil(
    profil: ProfilServeur,
    onProfil: (ProfilServeur) -> Unit,
    onChangerIcone: () -> Unit,
    onSupprimerIcone: () -> Unit,
) {
    val t = textes()
    Text(t.profilDuServeur, color = CREME_SERVEUR, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(
        t.profilServeurAide,
        color = TEXTE_CLAIR, fontSize = 13.sp,
    )
    Spacer(Modifier.height(28.dp))

    TitreChamp(t.nom)
    ChampSombre(profil.nom, { onProfil(profil.copy(nom = it.take(100))) }, Modifier.fillMaxWidth())
    FiletChamp()

    TitreChamp(t.icone, t.tailleImageRecommandee)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            t.changerIconeServeur, color = Color(0xFF1B1D22), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(DORE).curseurMain().clickable(onClick = onChangerIcone)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Text(
            t.supprimerLIcone, color = ROSE_MENU, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF24272D)).curseurMain().clickable(onClick = onSupprimerIcone)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
    FiletChamp()

    TitreChamp(t.banniere)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (ligne in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (colonne in 0 until 5) {
                    val index = ligne * 5 + colonne
                    val choisi = profil.banniere == index
                    Box(
                        Modifier.width(105.dp).height(63.dp).clip(RoundedCornerShape(6.dp))
                            .background(degradeBanniere(index))
                            .then(if (choisi) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
                            .curseurMain().clickable { onProfil(profil.copy(banniere = index)) },
                    )
                }
            }
        }
    }
    FiletChamp()

    TitreChamp(t.particularites, t.particularitesAide)
    val cases = (0 until ProfilServeur.PARTICULARITES_MAX).map { profil.particularites.getOrNull(it) ?: Particularite("", "") }
    fun ecrire(i: Int, p: Particularite) {
        val liste = cases.toMutableList().also { it[i] = p }
        onProfil(profil.copy(particularites = liste.filter { it.emoji.isNotEmpty() || it.texte.isNotBlank() }))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0..2, 3..4).forEach { rang ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rang.forEach { i -> CaseParticularite(cases[i]) { ecrire(i, it) } }
            }
        }
    }
    FiletChamp()

    TitreChamp(t.description, t.descriptionServeurAide)
    ChampSombre(
        profil.description.orEmpty(),
        { onProfil(profil.copy(description = it.take(300).ifEmpty { null })) },
        Modifier.fillMaxWidth().height(110.dp),
        uneLigne = false,
    )
}

/** an emoji plus text field, where the emoji opens in velvet's emoji panel */
@Composable
private fun CaseParticularite(valeur: Particularite, onValeur: (Particularite) -> Unit) {
    val t = textes()
    var panneau by remember { mutableStateOf(false) }
    Row(
        Modifier.width(181.dp).height(40.dp).clip(RoundedCornerShape(8.dp)).background(FOND_CHAMP)
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).curseurMain().clickable { panneau = true }, contentAlignment = Alignment.Center) {
            if (valeur.emoji.isEmpty()) Icone(Icones.emoji, tint = TEXTE_GRIS, taille = 20.dp, description = t.choisirUnEmoji)
            else Text(valeur.emoji, fontSize = 17.sp)
            if (panneau) {
                Popup(alignment = Alignment.BottomStart, onDismissRequest = { panneau = false }, properties = PopupProperties(focusable = true)) {
                    Box(Modifier.width(420.dp).clip(RoundedCornerShape(12.dp)).background(FOND_MENU).border(1.dp, BORD_MENU, RoundedCornerShape(12.dp))) {
                        PanneauEmojis(
                            packs = emptyList(),
                            autoriserPersonnalises = false,
                            hauteur = 380.dp,
                            poignee = false,
                            bureau = true,
                            onChoisir = { choix ->
                                (choix as? ChoixEmoji.Unicode)?.let { onValeur(valeur.copy(emoji = it.caractere)) }
                                panneau = false
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = valeur.texte,
            onValueChange = { onValeur(valeur.copy(texte = it.take(24))) },
            singleLine = true,
            textStyle = TextStyle(color = TEXTE_CLAIR, fontSize = 14.sp),
            cursorBrush = SolidColor(DORE),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * the preview card, about 300 wide: banner, icon overlapping it, name, counters, description
 */
@Composable
private fun CarteApercuServeur(serveur: Server, profil: ProfilServeur, avatarUrl: String?, enLigne: Int) {
    val t = textes()
    Column(
        Modifier.width(300.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1D22))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(degradeBanniere(profil.banniere)))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Box(
                Modifier.offset(y = (-34).dp).size(70.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1A1D22)).padding(3.dp),
            ) {
                Avatar(userId = serveur.id, displayName = profil.nom, avatarUrl = avatarUrl, taille = 64.dp, forme = RoundedCornerShape(16.dp))
            }
            Column(Modifier.offset(y = (-26).dp)) {
                Text(profil.nom, color = CREME_SERVEUR, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3BA55C)))
                    Text(" " + t.enLigneNombre(enLigne), color = TEXTE_GRIS, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(TEXTE_GRIS))
                    Text(" " + t.membresCompte(profil.membres), color = TEXTE_GRIS, fontSize = 13.sp)
                }
                profil.creeLe?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(t.depuisDate(moisAnnee(it)), color = TEXTE_GRIS, fontSize = 13.sp)
                }
                profil.description?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = TEXTE_CLAIR, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                if (profil.particularites.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        profil.particularites.forEach { p -> Text(p.emoji.ifEmpty { p.texte }, color = TEXTE_CLAIR, fontSize = if (p.emoji.isEmpty()) 13.sp else 20.sp) }
                    }
                }
            }
        }
    }
}

private fun moisAnnee(ms: Long): String {
    val date = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", localeCourante()).format(date)
}

/** the tag: an emoji and four letters, shown next to the nickname of whoever wears it */
@Composable
private fun SectionTag(moteur: MatrixEngine, serveur: Server) {
    val t = textes()
    val portee = rememberCoroutineScope()
    var tag by remember { mutableStateOf<TagServeur?>(null) }
    var emoji by remember { mutableStateOf("") }
    var texte by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(serveur.id) {
        tag = runCatching { moteur.tagServeur(serveur.id) }.getOrNull()
        emoji = tag?.emoji.orEmpty()
        texte = tag?.texte.orEmpty()
    }
    Text(t.tagDuServeur, color = CREME_SERVEUR, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(t.tagAfficheParLesMembres, color = TEXTE_CLAIR, fontSize = 13.sp)
    Spacer(Modifier.height(28.dp))
    TitreChamp(t.apercu)
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF2A2D33)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji.ifEmpty { "🏷️" }, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Text(texte.ifEmpty { "TAG" }, color = TEXTE_CLAIR, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    FiletChamp()
    TitreChamp(t.emojiEtNom, t.jusquACaracteres(TagServeur.LONGUEUR_MAX))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CaseParticularite(Particularite(emoji, texte)) { emoji = it.emoji; texte = it.texte.take(TagServeur.LONGUEUR_MAX) }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            t.enregistrer, color = Color(0xFF1B1D22), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(DORE).curseurMain().clickable {
                portee.launch {
                    val nouveau = if (emoji.isNotEmpty() && texte.isNotBlank()) TagServeur(emoji, texte.trim()) else null
                    runCatching { moteur.setTagServeur(serveur.id, nouveau) }
                        .onSuccess { tag = nouveau; message = if (nouveau == null) t.tagRetire else t.tagEnregistre }
                        .onFailure { message = t.nonEnregistreQuoi(it.message.orEmpty()) }
                }
            }.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        message?.let { Text(it, color = TEXTE_GRIS, fontSize = 13.sp) }
    }
}

@Composable
private fun SectionInvitations(moteur: MatrixEngine, serveur: Server) {
    val t = textes()
    val pressePapier = copieur()
    var lien by remember { mutableStateOf<String?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    Text(t.invitations, color = CREME_SERVEUR, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(
        t.lienInvitationExplication,
        color = TEXTE_CLAIR, fontSize = 13.sp,
    )
    Spacer(Modifier.height(28.dp))
    TitreChamp(t.lienDInvitation)
    LaunchedEffect(serveur.id) {
        runCatching { moteur.lienInvitation(serveur.id) }
            .onSuccess { lien = it }
            .onFailure { erreur = t.reserveAdmin }
    }
    if (lien == null) {
        Text(if (erreur == null) t.preparationDuLien else "—", color = TEXTE_GRIS, fontSize = 14.sp)
    } else {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(FOND_CHAMP).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(lien!!, color = TEXTE_CLAIR, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                t.copier, color = Color(0xFF1B1D22), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(DORE).curseurMain()
                    .clickable { pressePapier(lien!!) }.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    erreur?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = ROSE_MENU, fontSize = 13.sp)
    }
}

/**
 * the server's role tiers, on the desktop
 *
 * the panel itself is the phone's, it has lived in `ui-commun` from the start and nobody called it
 * here: the desktop read the tiers to colour nicknames without ever showing them or letting anybody
 * write them
 *
 * editable from 50 up, like on the phone: that is the power matrix wants to write a room state, so
 * the only honest answer to who may touch the roles
 */
@Composable
private fun SectionRoles(moteur: MatrixEngine, serveur: Server) {
    val t = textes()
    var paliers by remember { mutableStateOf<List<li.velvetchat.core.model.RoleTier>>(emptyList()) }
    var monPouvoir by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(serveur.id) {
        paliers = runCatching { moteur.roleTiers(serveur.id) }.getOrDefault(emptyList())
        monPouvoir = runCatching {
            val moi = moteur.currentSession().userId
            moteur.members(serveur.id).firstOrNull { it.userId == moi }?.role?.powerLevel ?: 0L
        }.getOrDefault(0L)
    }
    Text(t.roles, color = CREME_SERVEUR, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))
    PanneauRoles(
        tiers = paliers,
        modifiable = monPouvoir >= 50L,
        onEnregistrer = { nouveaux ->
            runCatching { moteur.setRoleTiers(serveur.id, nouveaux) }
            paliers = runCatching { moteur.roleTiers(serveur.id) }.getOrDefault(nouveaux)
        },
    )
}

/**
 * the desktop's file picker, in the shape the shared panels expect
 *
 * filekit returns a `File` from a suspending function, and the panels want a callback, which is the
 * shape android wants, its picker being a launcher. the conversion fits in one scope
 */
@Composable
private fun choixDeFichierBureau(parent: java.awt.Window?): ChoisirFichier {
    val portee = rememberCoroutineScope()
    return { type, suite ->
        portee.launch {
            val f = if (type == TypeFichier.SON) choisirFichier(parent) else choisirImage(parent)
            f?.let { suite(it.absolutePath) }
        }
    }
}

/** the server's emoji and stickers: the phone's panel, wired up here */
@Composable
private fun SectionEmojis(moteur: MatrixEngine, serveur: Server, parent: java.awt.Window?) {
    var emotes by remember { mutableStateOf<List<li.velvetchat.core.model.CustomEmote>>(emptyList()) }
    LaunchedEffect(serveur.id) {
        emotes = runCatching { moteur.emotes(serveur.id) }.getOrDefault(emptyList())
    }
    GestionEmojisDuServeur(
        emotes = emotes,
        onAjouter = { chemin, nom, autocollant ->
            moteur.addEmote(serveur.id, nom, chemin, autocollant)
            emotes = moteur.emotes(serveur.id)
        },
        onRetirer = { nom ->
            moteur.removeEmote(serveur.id, nom)
            emotes = moteur.emotes(serveur.id)
        },
        choisirFichier = choixDeFichierBureau(parent),
    )
}

/** the server's avatar decorations */
@Composable
private fun SectionDecorations(moteur: MatrixEngine, serveur: Server, parent: java.awt.Window?) {
    var inventaire by remember {
        mutableStateOf<List<li.velvetchat.core.model.DecorationAvatar>>(emptyList())
    }
    var moi by remember { mutableStateOf("") }
    LaunchedEffect(serveur.id) {
        inventaire = runCatching { moteur.inventaireDecorations(serveur.id) }.getOrDefault(emptyList())
        moi = runCatching { moteur.currentSession().userId }.getOrDefault("")
    }
    PanneauDecorations(
        decorations = inventaire,
        monIdentifiant = moi,
        onAjouter = { chemin, nom ->
            val mxc = moteur.uploadMedia(chemin)
            val entree = li.velvetchat.core.model.DecorationAvatar(
                // the timestamp is enough: the id only names the row for removal, and two
                // decorations can legitimately share a name
                id = "deco-${System.currentTimeMillis()}",
                nom = nom,
                mxc = mxc,
                anime = chemin.endsWith(".gif", ignoreCase = true),
            )
            moteur.setInventaireDecorations(serveur.id, inventaire + entree)
            inventaire = moteur.inventaireDecorations(serveur.id)
        },
        onRetirer = { id ->
            moteur.setInventaireDecorations(serveur.id, inventaire.filterNot { it.id == id })
            inventaire = moteur.inventaireDecorations(serveur.id)
        },
        choisirFichier = choixDeFichierBureau(parent),
    )
}

/** the server's soundboard */
@Composable
private fun SectionSons(moteur: MatrixEngine, serveur: Server, parent: java.awt.Window?) {
    var sons by remember { mutableStateOf<List<li.velvetchat.core.model.SonSoundboard>>(emptyList()) }
    LaunchedEffect(serveur.id) {
        sons = runCatching { moteur.sonsSoundboard(serveur.id) }.getOrDefault(emptyList())
    }
    PanneauSons(
        sons = sons,
        onAjouter = { chemin, nom, emoji ->
            val mxc = moteur.uploadMedia(chemin)
            val entree = li.velvetchat.core.model.SonSoundboard(
                id = "son-${System.currentTimeMillis()}",
                nom = nom,
                mxc = mxc,
                emoji = emoji,
            )
            moteur.setSonsSoundboard(serveur.id, sons + entree)
            sons = moteur.sonsSoundboard(serveur.id)
        },
        onRenommer = { id, nouveauNom, nouvelEmoji ->
            // the whole catalogue, with one entry changed. the `mxc` is carried over, so the
            // audio is neither re uploaded nor orphaned on the server
            moteur.setSonsSoundboard(
                serveur.id,
                sons.map {
                    if (it.id == id) it.copy(nom = nouveauNom, emoji = nouvelEmoji) else it
                },
            )
            sons = moteur.sonsSoundboard(serveur.id)
        },
        onRetirer = { id ->
            moteur.setSonsSoundboard(serveur.id, sons.filterNot { it.id == id })
            sons = moteur.sonsSoundboard(serveur.id)
        },
        choisirFichier = choixDeFichierBureau(parent),
    )
}

/** the server's members, with their tier */
@Composable
private fun SectionMembres(moteur: MatrixEngine, serveur: Server) {
    var membres by remember { mutableStateOf<List<li.velvetchat.core.model.Member>>(emptyList()) }
    var paliers by remember { mutableStateOf<List<li.velvetchat.core.model.RoleTier>>(emptyList()) }
    var vise by remember { mutableStateOf<li.velvetchat.core.model.Member?>(null) }
    LaunchedEffect(serveur.id) {
        paliers = runCatching { moteur.roleTiers(serveur.id) }.getOrDefault(emptyList())
    }
    PanneauMembres(
        membres = membres,
        paliers = paliers,
        onCharger = {},
        onMembre = { vise = it },
    )
    // promote, kick, ban: the sheet is the phone's, also shared from the start. the desktop read
    // the tiers and could touch nobody
    vise?.let { cible ->
        var monPouvoir by remember { mutableStateOf(0L) }
        LaunchedEffect(cible.userId) {
            monPouvoir = runCatching {
                val moi = moteur.currentSession().userId
                membres.firstOrNull { it.userId == moi }?.role?.powerLevel ?: 0L
            }.getOrDefault(0L)
        }
        FeuilleModeration(
            membre = cible,
            tiers = paliers,
            monPouvoir = monPouvoir,
            onPromouvoir = { niveau -> moteur.setPowerLevel(serveur.id, cible.userId, niveau) },
            onExclure = { moteur.kick(serveur.id, cible.userId) },
            onBannir = { moteur.ban(serveur.id, cible.userId) },
            onFermer = {
                vise = null
                // reread afterwards: a changed power or a kick change what it should show, and
                // keeping the old one would suggest nothing had moved
                membres = emptyList()
            },
        )
    }
    LaunchedEffect(membres.isEmpty()) {
        if (membres.isEmpty()) {
            membres = runCatching { moteur.members(serveur.id) }.getOrDefault(emptyList())
        }
    }
}

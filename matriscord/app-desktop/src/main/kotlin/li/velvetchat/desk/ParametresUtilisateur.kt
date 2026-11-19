package li.velvetchat.desk

import li.velvetchat.core.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.app.ui.*
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.essayer
import li.velvetchat.core.model.ModeNotification
import li.velvetchat.core.model.Profile

/**
 * the user bar's mic and headset, and the chosen devices
 *
 * kept in the settings, like the reference which remembers "mic muted" from one launch to the next.
 * the desktop's calls read them, and until then they state the wanted state
 */
object Voix {
    var microCoupe by mutableStateOf(Reglages.lire("voix.micro_coupe") == "1")
        private set
    var sonCoupe by mutableStateOf(Reglages.lire("voix.son_coupe") == "1")
        private set
    /**
     * the device's guid, not its name
     *
     * the name alone was kept, and it had to be found again in a different enumeration from the one
     * that produced it, see [AppelBureau.peripheriquesAudio]. the name stays, to write in the
     * settings, and the guid is what addresses
     */
    var entree by mutableStateOf(Reglages.lire("voix.entree"))
        private set
    var entreeNom by mutableStateOf(Reglages.lire("voix.entree_nom"))
        private set
    var sortie by mutableStateOf(Reglages.lire("voix.sortie"))
        private set
    var sortieNom by mutableStateOf(Reglages.lire("voix.sortie_nom"))
        private set

    /**
     * the mic button means one thing: be heard, or do not
     *
     * it used to flip its own flag only, and undeafen when both were set. from deafened that made
     * three clicks to get back on air, and the first changed nothing anybody could see: the icon
     * draws crossed as soon as either flag is set, so muting under a deafen was invisible. counted
     * on screen, three
     *
     * so coming back clears both, which is what the icon has been promising all along and what
     * [AppelBureau.synchroniser] already reads: it mutes the outgoing track on either flag
     */
    fun basculerMicro() {
        val coupe = microCoupe || sonCoupe
        microCoupe = !coupe
        sonCoupe = false
        Reglages.ecrire("voix.micro_coupe", if (microCoupe) "1" else "0")
        Reglages.ecrire("voix.son_coupe", "0")
        if (microCoupe) Sons.microCoupe() else Sons.microOuvert()
        AppelBureau.synchroniser()
    }

    fun basculerSon() {
        sonCoupe = !sonCoupe
        Reglages.ecrire("voix.son_coupe", if (sonCoupe) "1" else "0")
        if (sonCoupe) Sons.sonCoupe() else Sons.sonOuvert()
        AppelBureau.synchroniser()
    }

    // the choice is remembered and applied: without the second, it only took effect on the next
    // call
    fun choisirEntree(p: Peripherique) {
        entree = p.guid
        entreeNom = p.nom
        Reglages.ecrire("voix.entree", p.guid)
        Reglages.ecrire("voix.entree_nom", p.nom)
        AppelBureau.appliquerPeripheriques()
    }

    fun choisirSortie(p: Peripherique) {
        sortie = p.guid
        sortieNom = p.nom
        Reglages.ecrire("voix.sortie", p.guid)
        Reglages.ecrire("voix.sortie_nom", p.nom)
        AppelBureau.appliquerPeripheriques()
    }

    /**
     * the devices, asked of the audio engine that will open them and not of java
     *
     * the old version read `AudioSystem.getMixerInfo()`, a correct list, and not the one
     * `SetRecordingDevice` accepts. offering names we will not know how to address afterwards is
     * promising a choice we cannot keep
     */
    fun peripheriques(entree: Boolean): List<Peripherique> = AppelBureau.peripheriquesAudio(entree)
}

/**
 * user settings, the gear in the bar, in the reference's layout: navigation on the left, content on
 * the right, a cross with ESC. only what exists in velvet: account, appearance, voice, updates
 */
@Composable
internal fun ParametresUtilisateur(
    moteur: MatrixEngine,
    profil: Profile?,
    onModifierProfil: () -> Unit,
    onFermer: () -> Unit,
    onDeconnexion: () -> Unit = {},
) {
    val t = textes()
    var section by remember { mutableStateOf(0) }
    val tokens = LocalTokens.current
    val sansEffet = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(tokens.bg)
            .clickable(indication = null, interactionSource = sansEffet) {}
            .onPreviewKeyEvent { if (it.key == Key.Escape) { onFermer(); true } else false },
    ) {
        CoucheFond()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Box(Modifier.fillMaxWidth().height(BARRE_HAUTE).deplaceFenetre())
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(0.3f).fillMaxHeight().background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.TopEnd,
            ) {
                Column(Modifier.width(220.dp).padding(top = 60.dp, end = 10.dp)) {
                    TitreNav(t.parametresUtilisateurMaj)
                    EntreeNav(t.monCompte, section == 0) { section = 0 }
                    EntreeNav(t.profils, section == 1) { onModifierProfil() }
                    FiletNav()
                    TitreNav(t.parametresApplicationMaj)
                    EntreeNav(t.apparence, section == 2) { section = 2 }
                    EntreeNav(t.voixEtVideo, section == 3) { section = 3 }
                    EntreeNav(t.notifications, section == 6) { section = 6 }
                    EntreeNav(t.verifierCetAppareil, section == 7) { section = 7 }
                    EntreeNav(t.miseAJour, section == 4) { section = 4 }
                    EntreeNav(t.importDiscordTitre, section == 5) { section = 5 }
                    // the version, under the navigation: that is where people look for it, and
                    // nothing used to say which version the app was. a click goes to the section
                    Text(
                        t.velvetVersionOu(System.getProperty("jpackage.app-version") ?: t.versionDeDeveloppement),
                        color = TEXTE_GRIS,
                        fontSize = 11.sp,
                        modifier = Modifier.curseurMain().clickable { section = 4 }
                            .padding(start = 10.dp, top = 14.dp),
                    )
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.06f)))
            Column(
                Modifier.weight(0.7f).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(start = 40.dp, top = 60.dp, end = 100.dp, bottom = 40.dp),
            ) {
                Column(Modifier.width(660.dp)) {
                    when (section) {
                        0 -> SectionCompte(profil, onModifierProfil, onDeconnexion)
                        2 -> SectionApparence()
                        3 -> SectionVoix()
                        6 -> SectionNotifications(moteur)
                        7 -> SectionVerification(moteur)
                        5 -> li.velvetchat.app.ui.EcranImportDiscord(moteur) { section = 0 }
                        else -> SectionMiseAJour(moteur)
                    }
                }
            }
        }
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
private fun TitreSection(texte: String) {
    Text(texte, color = TEXTE_CLAIR, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(24.dp))
}

/** a "label … value [button]" row, like the reference's username line */
@Composable
private fun LigneReglage(libelle: String, valeur: String? = null, bouton: String? = null, onBouton: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(libelle, color = TEXTE_CLAIR, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        valeur?.let { Text(it, color = TEXTE_GRIS, fontSize = 15.sp) }
        bouton?.let {
            Spacer(Modifier.width(16.dp))
            Text(
                it, color = TEXTE_CLAIR, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))
                    .curseurMain().clickable(onClick = onBouton).padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun SectionCompte(profil: Profile?, onModifierProfil: () -> Unit, onDeconnexion: () -> Unit) {
    val t = textes()
    TitreSection(t.infosDuCompte)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(userId = profil?.userId.orEmpty(), displayName = profil?.displayName, avatarUrl = profil?.avatarUrl, taille = 72.dp)
        Spacer(Modifier.width(16.dp))
        Text(profil?.displayName ?: "…", color = TEXTE_CLAIR, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(16.dp))
    LigneReglage(t.nomDUtilisateur, profil?.userId?.removePrefix("@")?.substringBefore(':'), t.modifierLeProfil, onModifierProfil)
    LigneReglage(t.champIdentifiant, profil?.userId)
    LigneReglage(t.champServeur, profil?.userId?.substringAfter(':'))
    FiletChamp()
    TitreChamp(
        t.deconnexion,
        t.deconnexionBureauExplication,
    )
    // two clicks: this button makes the history unreadable until the next login
    var confirme by remember { mutableStateOf(false) }
    Text(
        if (confirme) t.confirmerDeconnexion else t.seDeconnecter,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFDA373C))
            .curseurMain().clickable { if (confirme) onDeconnexion() else confirme = true }
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

@Composable
private fun SectionApparence() {
    val fenetre = LocalFenetre.current
    val t = textes()
    val choisirLangue = LocalChoisirLangue.current
    TitreSection(t.apparence)
    // the language, here rather than on a screen of its own: it is an appearance setting, and this
    // is where people go looking for how to change it
    TitreChamp(t.langue, t.langueAide)
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Langue.entries.forEach { choix ->
            val actif = choix == LocalLangue.current
            Text(
                when (choix) {
                    Langue.FRANCAIS -> t.langueFrancais
                    Langue.ANGLAIS -> t.langueAnglais
                },
                color = if (actif) Color.White else TEXTE_GRIS,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (actif) DORE.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                    .curseurMain().clickable { choisirLangue(choix) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    FiletChamp()
    TitreChamp(t.theme, t.fichierDeTheme)
    // a gallery, not a file dialog: "Deep Arctic" says nothing, three dots taken from the
    // theme itself say everything. the file picker stays, for your own themes
    GalerieThemes()
    LigneReglage(t.importerUnTheme, bouton = t.choisirPoints) { ThemeVelvet.choisir(fenetre?.awt) }
    if (ThemeVelvet.actif) LigneReglage(t.revenirAuThemeParDefaut, bouton = t.retablir) { ThemeVelvet.retirer() }
    FiletChamp()
    TitreChamp(t.fondDEcran, t.fondDEcranAide)
    LigneReglage(t.fondDEcran, if (FondEcran.actif) t.personnalise else t.aucun, t.choisirPoints) { FondEcran.choisir(fenetre?.awt) }
    // the last twenty, to take one back without hunting through your folders
    GalerieFonds()
    if (FondEcran.actif) LigneReglage(t.retirerLeFondDEcran, bouton = t.retirer) { FondEcran.retirer() }
}

@Composable
private fun SectionVoix() {
    val t = textes()
    TitreSection(t.voixEtVideo)
    val entrees = remember { Voix.peripheriques(entree = true) }
    val sorties = remember { Voix.peripheriques(entree = false) }
    TitreChamp(t.peripheriqueDEntree)
    ListePeripheriques(entrees, Voix.entree) { Voix.choisirEntree(it) }
    FiletChamp()
    TitreChamp(t.peripheriqueDeSortie)
    ListePeripheriques(sorties, Voix.sortie) { Voix.choisirSortie(it) }
    FiletChamp()
    LigneReglage(t.micro, if (Voix.microCoupe) t.coupe else t.actif, if (Voix.microCoupe) t.retablir else t.couperCourt) { Voix.basculerMicro() }
    LigneReglage(t.son, if (Voix.sonCoupe) t.coupe else t.actif, if (Voix.sonCoupe) t.retablir else t.couperCourt) { Voix.basculerSon() }
}

@Composable
internal fun ListePeripheriques(
    appareils: List<Peripherique>,
    choisi: String?,
    onChoisir: (Peripherique) -> Unit,
) {
    val t = textes()
    if (appareils.isEmpty()) {
        Text(t.aucunPeripherique, color = TEXTE_GRIS, fontSize = 14.sp)
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(FOND_CHAMP)) {
        appareils.forEach { appareil ->
            val nom = appareil.nom
            val actif = appareil.guid == choisi || (choisi == null && appareil == appareils.first())
            Row(
                Modifier.fillMaxWidth().curseurMain().clickable { onChoisir(appareil) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape).border(2.dp, if (actif) DORE else TEXTE_GRIS, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (actif) Box(Modifier.size(8.dp).clip(CircleShape).background(DORE)) }
                Spacer(Modifier.width(10.dp))
                Text(nom, color = TEXTE_CLAIR, fontSize = 14.sp)
            }
        }
    }
}

/**
 * this session's verification state, and a way to verify at any time
 *
 * the verification screen only appeared at launch, and "later" made it disappear until the next
 * one, after which there was no door at all. whether the screen was missed or dismissed, the result
 * was the same, an unreadable history with nothing to go back to
 *
 * the state is read continuously (`accessFlow`, the same source as the launch), so this page says
 * what is true now and not what was true when it opened
 */
@Composable
private fun SectionVerification(moteur: MatrixEngine) {
    val t = textes()
    var acces by remember {
        mutableStateOf<li.velvetchat.core.AccessState>(
            li.velvetchat.core.AccessState.SignedOut,
        )
    }
    var verifier by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(moteur) {
        essayer { moteur.accessFlow().collect { acces = it } }
            .onFailure { grogner("etat de verification illisible", it) }
    }
    val besoin = acces as? li.velvetchat.core.AccessState.VerificationRequired
    if (verifier && besoin != null) {
        VerificationScreen(
            engine = moteur,
            canUseRecoveryKey = besoin.canUseRecoveryKey,
            aUnAppareilTemoin = besoin.aUnAppareilTemoin,
            onPlusTard = { verifier = false },
            onVerifie = { verifier = false },
        )
        return
    }
    TitreSection(t.verifierCetAppareil)
    TitreChamp(
        t.verifierCetAppareil,
        if (besoin == null) t.sessionVerifiee else t.historiqueChiffreExplication,
    )
    LigneReglage(
        t.cetAppareil,
        if (besoin == null) t.sessionVerifiee else t.sessionNonVerifiee,
        if (besoin == null) null else t.verifierMaintenant,
    ) { verifier = true }
}

/**
 * the general notification setting, the same as the phone's
 *
 * it existed nowhere: you could be silent everywhere, or ring for everything. "mentions only" was
 * set room by room, which nobody does
 */
@Composable
private fun SectionNotifications(moteur: MatrixEngine) {
    val t = textes()
    val portee = rememberCoroutineScope()
    var mode by remember { mutableStateOf<ModeNotification?>(null) }
    androidx.compose.runtime.LaunchedEffect(moteur) {
        mode = runCatching { moteur.modeNotificationGlobal() }.getOrNull()
    }
    TitreSection(t.notifications)
    TitreChamp(t.notifications, t.notificationsAide)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(FOND_CHAMP)) {
        listOf(
            ModeNotification.TOUT to (t.notifTout to t.notifToutDetail),
            ModeNotification.MENTIONS to (t.notifMentions to t.notifMentionsDetail),
            ModeNotification.MUET to (t.notifMuet to t.notifMuetDetail),
        ).forEach { (choix, libelles) ->
            val actif = choix == mode
            Row(
                Modifier.fillMaxWidth().curseurMain().clickable {
                    mode = choix
                    portee.launch {
                        runCatching { moteur.setModeNotificationGlobal(choix) }
                        mode = runCatching { moteur.modeNotificationGlobal() }.getOrDefault(choix)
                    }
                }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .border(2.dp, if (actif) DORE else TEXTE_GRIS, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (actif) Box(Modifier.size(8.dp).clip(CircleShape).background(DORE)) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(libelles.first, color = TEXTE_CLAIR, fontSize = 14.sp)
                    Text(libelles.second, color = TEXTE_GRIS, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionMiseAJour(moteur: MatrixEngine) {
    val t = textes()
    val portee = rememberCoroutineScope()
    var recherche by remember { mutableStateOf(false) }
    var faite by remember { mutableStateOf(false) }
    TitreSection(t.miseAJour)
    LigneReglage(t.versionInstallee, System.getProperty("jpackage.app-version") ?: tr("développement", "development"))
    val prete = MiseAJourBureau.prete
    LigneReglage(
        t.derniereVersion,
        when {
            prete != null -> t.versionPreteAInstaller(prete.versionName)
            recherche -> t.rechercheEnCours
            faite -> t.aucuneMiseAJour
            else -> null
        },
        if (recherche) null else t.rechercher,
    ) {
        recherche = true
        portee.launch {
            runCatching { MiseAJourBureau.verifier(moteur) }
            recherche = false
            faite = true
        }
    }
    if (prete != null) Text(t.bandeauMiseAJour, color = TEXTE_GRIS, fontSize = 13.sp)
}

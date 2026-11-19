package li.velvetchat.desk

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.app.ui.Avatar
import li.velvetchat.app.ui.Icone
import li.velvetchat.app.ui.Icones
import li.velvetchat.app.ui.*

internal val VERT_VOIX = Color(0xFF43A25A)
private val ROUGE_RACCROCHER = Color(0xFFDA373C)

/**
 * "voice connected", placed on the user mini bar like the reference: the call's state, the room,
 * who is there with a green ring when they speak, and the button to hang up
 */
@Composable
internal fun PanneauVoixConnectee(modifier: Modifier = Modifier) {
    val t = textes()
    val etat = AppelBureau.etat
    if (etat.phase == AppelBureau.Phase.INACTIF) return
    val tokens = LocalTokens.current
    // the reference's "voice connected" block: a 32 line with the state, the room and the ping,
    // then a row of equal buttons, 32 tall, radius 8, green while they are on. the people present
    // are not repeated here, they are listed under the room in the channel column
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF232428)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            if (etat.phase == AppelBureau.Phase.EN_APPEL) {
                IndicateurConnexion(etat.qualite)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                val (titre, couleur) = when (etat.phase) {
                    AppelBureau.Phase.CONNEXION -> t.connexionEnCours to Color(0xFFF0B232)
                    AppelBureau.Phase.ECHEC -> t.appelImpossibleCourt to ROUGE_RACCROCHER
                    else -> t.voixConnectee to VERT_VOIX
                }
                Text(titre, color = couleur, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 16.sp)
                Text(
                    etat.erreur ?: etat.salonNom ?: t.appel,
                    color = tokens.textSecondary, fontSize = 12.sp, lineHeight = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).curseurMain()
                    .clickable { AppelBureau.quitter() },
                contentAlignment = Alignment.Center,
            ) {
                Icone(Icones.raccrocher, tint = tokens.textPrimary, taille = 20.dp, description = t.seDeconnecter)
            }
        }
        if (etat.phase == AppelBureau.Phase.EN_APPEL) {
            // one video goes out at a time, so the two buttons share the sending: the one matching
            // what is being sent lights up, and clicking it stops
            val source = PartageEcranBureau.sourceCourante
            val camera = PartageEcranBureau.actif && source is SourceVideo.Entree
            val ecran = PartageEcranBureau.actif && !camera
            var selecteur by remember { mutableStateOf<Int?>(null) }
    // a video call: open the camera picker as soon as the call's controls exist
    androidx.compose.runtime.LaunchedEffect(PartageEcranBureau.demanderCamera) {
        if (PartageEcranBureau.demanderCamera) {
            PartageEcranBureau.demanderCamera = false
            selecteur = 2
        }
    }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonPanneauVoix(Icones.camera, if (camera) t.eteindreLaCamera else t.allumerLaCamera, camera, Modifier.weight(1f)) {
                    if (camera) PartageEcranBureau.arreter() else selecteur = 2
                }
                BoutonPanneauVoix(Icones.partageEcran, t.partagerLEcran, ecran, Modifier.weight(1f)) {
                    if (ecran) PartageEcranBureau.arreter() else selecteur = 0
                }
            }
            selecteur?.let { onglet ->
                Box { SelecteurDePartage(onFermer = { selecteur = null }, ongletInitial = onglet) }
            }
        }
    }
}

/** a button of the voice panel's row: full share of the width, 32 tall, green while on */
@Composable
private fun BoutonPanneauVoix(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    actif: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val survol = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val survole by survol.collectIsHoveredAsState()
    Box(
        modifier.height(32.dp).clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    actif && survole -> VERT_VOIX.copy(alpha = 0.24f)
                    actif -> VERT_VOIX.copy(alpha = 0.12f)
                    survole -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.05f)
                },
            )
            .then(if (actif) Modifier.border(1.dp, VERT_VOIX.copy(alpha = 0.12f), RoundedCornerShape(8.dp)) else Modifier)
            .hoverable(survol)
            .curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icone(icone, tint = if (actif) VERT_VOIX else Color(0xFFDBDEE1), taille = 20.dp, description = description)
    }
}

/**
 * the reference's ping: three rising bars, green when the connection is good, yellow when it is
 * weak, red when it is lost. livekit grades it, see `AppelBureau.Qualite`
 */
@Composable
private fun IndicateurConnexion(qualite: AppelBureau.Qualite) {
    val couleur = when (qualite) {
        AppelBureau.Qualite.EXCELLENTE, AppelBureau.Qualite.BONNE -> VERT_VOIX
        AppelBureau.Qualite.FAIBLE -> Color(0xFFF0B232)
        AppelBureau.Qualite.PERDUE -> ROUGE_RACCROCHER
    }
    val pleines = when (qualite) {
        AppelBureau.Qualite.EXCELLENTE -> 3
        AppelBureau.Qualite.BONNE -> 2
        AppelBureau.Qualite.FAIBLE -> 1
        AppelBureau.Qualite.PERDUE -> 0
    }
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val largeur = size.width / 5f
        for (i in 0 until 3) {
            val hauteur = size.height * (0.4f + 0.3f * i)
            drawRoundRect(
                color = if (i < pleines) couleur else couleur.copy(alpha = 0.3f),
                topLeft = androidx.compose.ui.geometry.Offset(i * largeur * 2f, size.height - hauteur),
                size = androidx.compose.ui.geometry.Size(largeur, hauteur),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(largeur / 2f),
            )
        }
    }
}

@Composable
private fun AvatarVoix(userId: String?, parle: Boolean) {
    val anneau = if (parle) Modifier.border(2.dp, VERT_VOIX, CircleShape) else Modifier
    Box(anneau.padding(2.dp)) {
        if (userId == null) {
            // yourself: no need for the profile, the initial is enough in that small circle
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF5865F2)), contentAlignment = Alignment.Center) {
                Icone(Icones.micro, tint = Color.White, taille = 14.dp)
            }
        } else {
            val membre = membreDe(userId)
            Avatar(
                userId = userId,
                displayName = membre?.displayName,
                avatarUrl = membre?.avatarUrl,
                taille = 24.dp,
            )
        }
    }
}

/** an incoming call: who is calling, where, and join or decline */
@Composable
internal fun BanniereAppelEntrant(
    appelant: String,
    lieu: String,
    onRejoindre: () -> Unit,
    onRefuser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        modifier.width(360.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF111214)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val quiAppelle = membreDe(appelant)
        Avatar(
            userId = appelant,
            displayName = quiAppelle?.displayName,
            avatarUrl = quiAppelle?.avatarUrl,
            taille = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.appelEntrant, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                "${appelant.removePrefix("@").substringBefore(':')} · $lieu",
                color = tokens.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        RondAppel(ROUGE_RACCROCHER, Icones.raccrocher, t.invitationRefuser, onRefuser)
        Spacer(Modifier.width(8.dp))
        RondAppel(VERT_VOIX, Icones.appeler, t.rejoindre, onRejoindre)
    }
}

@Composable
private fun RondAppel(fond: Color, icone: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(fond).curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icone(icone, tint = Color.White, taille = 20.dp, description = description)
    }
}

/**
 * a call's video stage: what other people are sharing, camera or screen
 *
 * it did not exist, the desktop released video tracks at subscription time, so a screen share
 * started from the phone arrived nowhere. laid over the conversation and only when there is
 * something to show: a permanent black frame would steal space from the one thing that serves when
 * nobody is sharing, the messages
 *
 * one tile per track, the widest first. a screen share comes before a camera (see
 * [AppelBureau.Video]), and at two or three videos we put them side by side, which is enough at this
 * scale and avoids the reference's tabbed layout
 */
@Composable
internal fun SceneVideo(modifier: Modifier = Modifier) {
    val etat = AppelBureau.etat
    if (etat.videos.isEmpty()) return
    val tokens = LocalTokens.current
    // reread on every frame: the image is not observable state, this counter is
    val horloge = VideoBureau.horloge
    Row(
        modifier.fillMaxWidth().background(Color(0xFF0B0B0D)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        etat.videos.forEach { video ->
            val image: androidx.compose.ui.graphics.ImageBitmap? =
                remember(video.sid, horloge) { VideoBureau.image(video.sid) }
            Box(
                Modifier.weight(1f).aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    androidx.compose.foundation.Image(
                        bitmap = image,
                        contentDescription = null,
                        // `Fit` and not `Crop`: a shared screen is read whole or it serves nothing,
                        // and cropping a spreadsheet cuts off the very column being shown
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // the track is there and the first frame is not: saying so beats a black
                    // rectangle people take for a fault
                    Text(
                        textes().chargement,
                        color = tokens.textMuted,
                        fontSize = 12.sp,
                    )
                }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icone(
                        if (video.ecran) Icones.partageEcran else Icones.camera,
                        tint = Color.White,
                        taille = 13.dp,
                        description = null,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        video.userId.removePrefix("@").substringBefore(':'),
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * the desktop's call screen, which did not exist
 *
 * there was only the "voice connected" strip on the mini bar: you knew you were in a call, you
 * could not see who. the phone and the reference fill the main area, which is where people look and
 * where video has room
 *
 * one tile per person, and the video replaces the avatar when there is one. somebody sharing their
 * screen and their camera therefore takes two tiles, like on the reference: they are two things to
 * look at, not two states of one person
 *
 * the bottom bar does not redraw the settings: the mic and the headset are the mini bar's (`Voix`),
 * and having them in two places that say the same thing beats choosing which of the two lies
 */
@Composable
internal fun EcranAppelBureau(
    modifier: Modifier = Modifier,
    /**
     * the call's state and the received frames, passed in rather than read from globals: that is
     * what lets this screen render offscreen, with a fabricated call, to be looked at without
     * holding a real one (`rendreEcranAppel`)
     */
    etat: AppelBureau.Etat = AppelBureau.etat,
    horloge: Long = VideoBureau.horloge,
    image: (String) -> androidx.compose.ui.graphics.ImageBitmap? = { VideoBureau.image(it) },
    /**
     * your own profile, because you are not always in the member list
     *
     * a fresh voice room has not loaded its list yet, and you saw yourself in initials while the
     * conversation right below showed the right photo. the session knows who you are
     */
    monProfil: li.velvetchat.core.model.Profile? = null,
    interactif: Boolean = true,
) {
    val t = textes()
    val tokens = LocalTokens.current

    // one video, large
    //
    // a shared screen in a tile the size of a postcard is a screen nobody reads. the grid is
    // right when four people are talking, and wrong the moment one of them shows a spreadsheet
    val agrandie = AppelBureau.videoAgrandie
        ?.let { sid -> AppelBureau.videosAvecLaSienne().firstOrNull { it.sid == sid } }
    // the last video watched, kept until a replacement arrives
    //
    // a video goes away for two very different reasons: the share was stopped, or it is
    // restarting because the shared window changed size. in the second case it comes back a
    // second later, and dropping straight back to the grid would throw the watcher out every
    // time the sharer drags the edge of their window
    var derniere by remember { mutableStateOf<AppelBureau.Video?>(null) }
    if (agrandie != null) derniere = agrandie

    if (AppelBureau.vue != AppelBureau.VueAppel.GRILLE) {
        val montree = agrandie ?: derniere
        if (montree != null) {
            if (agrandie == null) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2500)
                    val revenue = AppelBureau.videosAvecLaSienne().firstOrNull()
                    if (revenue != null) {
                        AppelBureau.videoAgrandie = revenue.sid
                    } else {
                        // nothing left to watch: without this you stay in fullscreen with no
                        // title bar in front of a grid, and nothing on screen says how to leave
                        AppelBureau.vue = AppelBureau.VueAppel.GRILLE
                        AppelBureau.videoAgrandie = null
                    }
                }
            }
            SceneFocus(modifier, montree, etat, horloge, image, interactif, monProfil)
            return
        }
        androidx.compose.runtime.SideEffect { AppelBureau.vue = AppelBureau.VueAppel.GRILLE }
    }

    // the reference lays its controls over the call, on a gradient, and lets them fade once the
    // mouse has been still for a while. a separate bar under the tiles took their height for good
    var dernierMouvement by remember { mutableStateOf(System.currentTimeMillis()) }
    var visibles by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(dernierMouvement, interactif) {
        visibles = true
        if (!interactif) return@LaunchedEffect
        kotlinx.coroutines.delay(DELAI_CONTROLES_MS)
        visibles = false
    }
    val opacite by androidx.compose.animation.core.animateFloatAsState(
        if (visibles) 1f else 0f,
        androidx.compose.animation.core.tween(200),
        label = "controles",
    )
    Box(
        modifier.fillMaxWidth().background(Color(0xFF0B0B0D))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        dernierMouvement = System.currentTimeMillis()
                    }
                }
            },
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 76.dp),
            contentAlignment = Alignment.Center,
        ) {
            // yours included: livekit does not send a track back to whoever publishes it
            val videos = AppelBureau.videosAvecLaSienne()
            // the tiles with no video: yourself, then the others no video already shows
            val moiIci = LocalMonIdentifiant.current
            // your own share adds a tile, it does not replace one. filtering your tile out because
            // you are publishing video made your avatar disappear at the moment you shared: you
            // stopped seeing yourself in your own call. what you share and who you are are two
            // things on screen, as they are for everybody else
            val sansVideo = (listOf<String?>(null) + etat.personnes.map { it.userId })
                .filterNot { id ->
                    id != null && videos.any { it.userId == id }
                }
            // the tiles fill the stage, like the reference: at a fixed size, three people left two
            // thirds of the height black. we look for the column count that gives the biggest 16/9
            // tile fitting the space
            val total = videos.size + sansVideo.size
            val ecart = 10.dp
            val colonnes = (1..total).minByOrNull { c ->
                val lignes = (total + c - 1) / c
                val large = (maxWidth - ecart * (c - 1)) / c
                val haute = (maxHeight - ecart * (lignes - 1)) / lignes
                // we want the biggest tile, so the smallest "how small is it"
                -minOf(large.value / 16f, haute.value / 9f)
            } ?: 1
            val lignes = (total + colonnes - 1) / colonnes
            val largeurTuile = (maxWidth - ecart * (colonnes - 1)) / colonnes
            val hauteurTuile = (maxHeight - ecart * (lignes - 1)) / lignes
            val cote = minOf(largeurTuile.value / 16f, hauteurTuile.value / 9f)
            val tuile = Modifier.width((cote * 16f).dp).height((cote * 9f).dp)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ecart, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(ecart, Alignment.CenterVertically),
                maxItemsInEachRow = colonnes,
            ) {
                videos.forEach { video ->
                    val vue: androidx.compose.ui.graphics.ImageBitmap? =
                        remember(video.sid, horloge) { image(video.sid) }
                    TuileAppel(
                        nom = membreDe(video.userId)?.displayName
                            ?: video.userId.removePrefix("@").substringBefore(':'),
                        icone = if (video.ecran) Icones.partageEcran else Icones.camera,
                        // the ring holds for a video tile too: knowing who is speaking does not
                        // depend on whether they show themselves
                        parle = etat.personnes.firstOrNull { it.userId == video.userId }?.parle == true,
                        // clicking the picture enlarges it, with no button to find first. that is
                        // the gesture everywhere else for "show me this properly"
                        modifier = if (interactif) {
                            tuile.curseurMain().clickable {
                                AppelBureau.videoAgrandie = video.sid
                                AppelBureau.vue = AppelBureau.VueAppel.FOCUS
                            }
                        } else {
                            tuile
                        },
                    ) {
                        if (vue != null) {
                            androidx.compose.foundation.Image(
                                bitmap = vue,
                                contentDescription = null,
                                // a shared screen is read whole or it serves nothing: cropping a
                                // spreadsheet cuts off the very column being shown
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(t.chargement, color = tokens.textMuted, fontSize = 12.sp)
                        }
                    }
                }
                sansVideo.forEach { userId ->
                    val parle = if (userId == null) {
                        etat.moiParle
                    } else {
                        etat.personnes.firstOrNull { it.userId == userId }?.parle == true
                    }
                    val moi = LocalMonIdentifiant.current.orEmpty()
                    val membre = membreDe(userId ?: moi)
                    // for yourself the session is authoritative, for everybody else the member list
                    val photo = if (userId == null) monProfil?.avatarUrl ?: membre?.avatarUrl
                    else membre?.avatarUrl
                    val pseudo = if (userId == null) monProfil?.displayName ?: membre?.displayName
                    else membre?.displayName
                    // the muted microphone next to the name, like the reference's tile
                    val coupe = if (userId == null) Voix.microCoupe || Voix.sonCoupe
                    else etat.personnes.firstOrNull { it.userId == userId }?.micCoupe == true
                    TuileAppel(
                        nom = pseudo
                            ?: (userId ?: moi).removePrefix("@").substringBefore(':'),
                        icone = if (coupe) Icones.microCoupe else null,
                        parle = parle,
                        modifier = tuile,
                    ) {
                        Avatar(
                            userId = userId ?: moi,
                            displayName = pseudo,
                            avatarUrl = photo,
                            // proportional to the tile: a fixed size avatar looks lost as soon as
                            // the stage grows, which it does at two people
                            taille = (cote * 3.2f).dp.coerceIn(48.dp, 160.dp),
                        )
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .graphicsLayer { alpha = opacite }
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                )
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PartageEcranBureau.erreur?.let {
                Text(it, color = ROUGE_RACCROCHER, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            ControlesAppel(interactif)
        }
    }
}

/** how long the mouse has to stay still before the call's controls fade, like the reference */
private const val DELAI_CONTROLES_MS = 3_000L

/**
 * micro, casque, partage, raccrocher
 *
 * the same four buttons under the grid and over the fullscreen. they used to live inside the
 * grid, which left fullscreen with no way to mute: you had to leave it first, which is exactly
 * the moment you do not want to leave
 */
@Composable
private fun ControlesAppel(interactif: Boolean) {
    val t = textes()
    // one video goes out at a time: the camera and the screen share the sending, and the button
    // matching what is being sent is the lit one
    val camera = PartageEcranBureau.actif && PartageEcranBureau.sourceCourante is SourceVideo.Entree
    val ecran = PartageEcranBureau.actif && !camera
    var selecteur by remember { mutableStateOf<Int?>(null) }
    // a video call: open the camera picker as soon as the call's controls exist
    androidx.compose.runtime.LaunchedEffect(PartageEcranBureau.demanderCamera) {
        if (PartageEcranBureau.demanderCamera) {
            PartageEcranBureau.demanderCamera = false
            selecteur = 2
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoutonAppel(
            if (Voix.microCoupe || Voix.sonCoupe) Icones.microCoupe else Icones.micro,
            etat = if (Voix.microCoupe || Voix.sonCoupe) EtatBouton.COUPE else EtatBouton.NORMAL,
            description = if (Voix.microCoupe) t.reactiverLeMicro else t.couperLeMicro,
        ) { Voix.basculerMicro() }
        BoutonAppel(
            Icones.casque,
            etat = if (Voix.sonCoupe) EtatBouton.COUPE else EtatBouton.NORMAL,
            description = if (Voix.sonCoupe) t.reactiverLeSon else t.mettreEnSourdine,
            barre = Voix.sonCoupe,
        ) { Voix.basculerSon() }
        Box {
            BoutonAppel(
                Icones.camera,
                etat = if (camera) EtatBouton.ALLUME else EtatBouton.NORMAL,
                description = if (camera) t.eteindreLaCamera else t.allumerLaCamera,
            ) { if (camera) PartageEcranBureau.arreter() else selecteur = 2 }
            if (selecteur == 2 && interactif) SelecteurDePartage(onFermer = { selecteur = null }, ongletInitial = 2)
        }
        Box {
            BoutonAppel(
                Icones.partageEcran,
                etat = if (ecran) EtatBouton.ALLUME else EtatBouton.NORMAL,
                description = t.partagerLEcran,
            ) { if (ecran) PartageEcranBureau.arreter() else selecteur = 0 }
            if (selecteur == 0 && interactif) SelecteurDePartage(onFermer = { selecteur = null }, ongletInitial = 0)
        }
        Spacer(Modifier.width(8.dp))
        BoutonAppel(
            Icones.raccrocher,
            etat = EtatBouton.RACCROCHER,
            description = t.seDeconnecter,
        ) { AppelBureau.quitter() }
    }
}

/** a call tile: dark fill, rounded corner, the name and its icon bottom left */
@Composable
private fun TuileAppel(
    nom: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector?,
    parle: Boolean = false,
    modifier: Modifier = Modifier,
    contenu: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1B1D21))
            .then(
                if (parle) {
                    Modifier.border(2.dp, VERT_VOIX, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        contenu()
        Row(
            Modifier.align(Alignment.BottomStart).padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icone?.let {
                Icone(it, tint = Color.White, taille = 13.dp, description = null)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                nom,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** a call button's look: the reference's colour families for these controls */
private enum class EtatBouton { NORMAL, COUPE, ALLUME, RACCROCHER }

/**
 * a call control: a 44 square with radius 8, a 24 icon. muted is red on a red glow, a source being
 * sent is white with a dark icon, hanging up is solid red
 */
@Composable
private fun BoutonAppel(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    etat: EtatBouton,
    description: String,
    /** a strike across the icon, for the deafened headset which has no icon of its own */
    barre: Boolean = false,
    onClick: () -> Unit,
) {
    val survol = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val survole by survol.collectIsHoveredAsState()
    val rougeClair = Color(0xFFF57976)
    val (fond, encre) = when (etat) {
        EtatBouton.NORMAL -> (if (survole) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f)) to Color.White
        EtatBouton.COUPE -> rougeClair.copy(alpha = if (survole) 0.16f else 0.12f) to rougeClair
        EtatBouton.ALLUME -> (if (survole) Color(0xFFD7D9DC) else Color.White) to Color(0xFF1E1F22)
        EtatBouton.RACCROCHER -> (if (survole) Color(0xFFC72E31) else ROUGE_RACCROCHER) to Color.White
    }
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(fond)
            .hoverable(survol).curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icone(icone, tint = encre, taille = 24.dp, description = description)
        if (barre) {
            Box(
                Modifier.size(24.dp).drawBehind {
                    drawLine(
                        encre,
                        androidx.compose.ui.geometry.Offset(2f, 2f),
                        androidx.compose.ui.geometry.Offset(size.width - 2f, size.height - 2f),
                        strokeWidth = 2.dp.toPx(),
                    )
                },
            )
        }
    }
}

/**
 * who this person is, among the members the column already carries
 *
 * `Avatar` does not go and fetch the photo on its own: it takes an `avatarUrl` and falls back on
 * initials when it is null. the whole desktop call passed it null, the mini bar from the start and
 * the call screen from birth, so no photo ever appeared in it. reported on a render, and the render
 * had nothing to do with it, the fault was in the app
 *
 * `LocalMembres` carries the open room's list with the photos, and outside a room it is empty and
 * we fall back on initials, like before
 */
@Composable
private fun membreDe(userId: String?): li.velvetchat.core.model.Member? =
    userId?.let { id -> LocalMembres.current.firstOrNull { it.userId == id } }

/**
 * one video in the middle, and three degrees of stripping away around it
 *
 * the four states of `AppelBureau.VueAppel` are one screen taking its furniture out piece by
 * piece, not four different screens. that is what lets you move between them without the image
 * moving: it stays where it is, and the edges are what leave
 *
 *   FOCUS        the app stays around it, the video takes the call area
 *   FAUX_PLEIN   the window takes the screen, the participant strip stays at the bottom
 *   VRAI_PLEIN   the strip goes, and the ui fades after ten seconds
 *
 * why ten seconds of stillness rather than a hot edge: a shared screen is watched without
 * touching the mouse for minutes, and that is precisely when nothing more should be drawn. a
 * trigger tied to position would bring the bar back every time the pointer happened to pass
 * near the top of the screen
 *
 * escape steps back one degree, never all the way to the grid at once
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SceneFocus(
    modifier: Modifier,
    video: AppelBureau.Video,
    etat: AppelBureau.Etat,
    horloge: Long,
    image: (String) -> androidx.compose.ui.graphics.ImageBitmap?,
    interactif: Boolean,
    monProfil: li.velvetchat.core.model.Profile?,
) {
    val t = textes()
    val vue = AppelBureau.vue
    val vraiPlein = vue == AppelBureau.VueAppel.VRAI_PLEIN
    val plein = AppelBureau.ecranEntier

    // what brings the ui back: the slightest movement. the counter restarts from zero, and the
    // timer below hides it again if nothing moves
    var bouge by remember { mutableStateOf(0L) }
    var montrer by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(bouge, vraiPlein) {
        if (!vraiPlein) {
            montrer = true
            return@LaunchedEffect
        }
        montrer = true
        kotlinx.coroutines.delay(10_000)
        montrer = false
    }

    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val vueImage = remember(video.sid, horloge) { image(video.sid) }
    val nom = membreDe(video.userId)?.displayName
        ?: video.userId.removePrefix("@").substringBefore(':')

    Box(
        modifier.fillMaxSize().background(Color.Black)
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move) { _ ->
                bouge = System.nanoTime()
            }
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val bas = e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown
                if (bas && e.key == androidx.compose.ui.input.key.Key.Escape) {
                    AppelBureau.reculer()
                    true
                } else {
                    false
                }
            },
    ) {
        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        // the image: edge to edge in true fullscreen, set in a rounded frame otherwise. it is
        // the difference you see straight away between the two fullscreens
        Box(
            Modifier.fillMaxSize()
                .padding(
                    // in true fullscreen the bar floats over the image instead of pushing it:
                    // otherwise the image jumped by 44 points every time the ui faded and came
                    // back, every ten seconds
                    top = if (vue == AppelBureau.VueAppel.FAUX_PLEIN) 44.dp else 0.dp,
                    bottom = when {
                        vraiPlein -> 0.dp
                        plein -> 132.dp
                        else -> 96.dp
                    },
                )
                .then(if (vraiPlein) Modifier else Modifier.padding(horizontal = 10.dp))
                .then(if (vraiPlein) Modifier else Modifier.clip(RoundedCornerShape(10.dp))),
            contentAlignment = Alignment.Center,
        ) {
            if (vueImage != null) {
                androidx.compose.foundation.Image(
                    bitmap = vueImage,
                    contentDescription = null,
                    // whole or it is useless, the same rule as the tile
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(t.chargement, color = Color(0xFF9A9A9A), fontSize = 13.sp)
            }
        }

        if (interactif && montrer) {
            if (plein) BarreHauteAppel(etat, nom, video.ecran, Modifier.align(Alignment.TopStart))
            Column(Modifier.align(Alignment.BottomCenter)) {
                if (vue == AppelBureau.VueAppel.FAUX_PLEIN) {
                    BandeParticipants(etat, horloge, image, monProfil, video.sid)
                }
                BarreOutilsFocus(vue, interactif)
            }
        }
    }
}

@Composable
private fun BarreHauteAppel(
    etat: AppelBureau.Etat,
    nom: String,
    ecran: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = textes()
    Row(
        modifier.fillMaxWidth().height(44.dp)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(Icones.salonVocal, tint = Color.White, taille = 16.dp, description = null)
        Spacer(Modifier.width(8.dp))
        Text(
            etat.salonNom.orEmpty(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("  -  ", color = Color(0xFF8A8A8A), fontSize = 14.sp)
        Icone(
            if (ecran) Icones.partageEcran else Icones.camera,
            tint = Color(0xFFBFBFBF),
            taille = 15.dp,
            description = null,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (ecran) t.partageSonEcran(nom) else t.saCamera(nom),
            color = Color(0xFFBFBFBF),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * the strip of everyone else, at the bottom
 *
 * this is what separates the two fullscreens, and it is not decoration: a shared screen is
 * watched together, and seeing the faces of the people watching it with you is half the point.
 * a click on a thumbnail changes what is in the middle
 */
@Composable
private fun BandeParticipants(
    etat: AppelBureau.Etat,
    horloge: Long,
    image: (String) -> androidx.compose.ui.graphics.ImageBitmap?,
    monProfil: li.velvetchat.core.model.Profile?,
    sidAuCentre: String,
) {
    val videos = AppelBureau.videosAvecLaSienne()
    val moi = LocalMonIdentifiant.current
    val sansVideo = etat.personnes.map { it.userId }
        .filterNot { id -> videos.any { it.userId == id } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        videos.forEach { v ->
            val vu = remember(v.sid, horloge) { image(v.sid) }
            Vignette(
                nom = membreDe(v.userId)?.displayName
                    ?: v.userId.removePrefix("@").substringBefore(':'),
                aucentre = v.sid == sidAuCentre,
                onClick = { AppelBureau.videoAgrandie = v.sid },
            ) {
                if (vu != null) {
                    androidx.compose.foundation.Image(
                        bitmap = vu,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        sansVideo.forEach { id ->
            Vignette(
                nom = membreDe(id)?.displayName ?: id.removePrefix("@").substringBefore(':'),
                aucentre = false,
                onClick = {},
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Avatar(
                        userId = id,
                        displayName = membreDe(id)?.displayName
                            ?: monProfil?.displayName.takeIf { id == moi },
                        avatarUrl = membreDe(id)?.avatarUrl
                            ?: monProfil?.avatarUrl.takeIf { id == moi },
                        taille = 40.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Vignette(
    nom: String,
    aucentre: Boolean,
    onClick: () -> Unit,
    contenu: @Composable () -> Unit,
) {
    Box(
        Modifier.width(128.dp).height(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1B1D21))
            .then(
                if (aucentre) Modifier.border(2.dp, VERT_VOIX, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .curseurMain().clickable(onClick = onClick),
    ) {
        contenu()
        Text(
            nom,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

/**
 * the two buttons that step from one degree to the next, on the right
 *
 * on the right and not in the middle: the middle belongs to the mic, the headset and hanging
 * up, which are the gestures made in a hurry. enlarging is never urgent
 */
@Composable
private fun BarreOutilsFocus(vue: AppelBureau.VueAppel, interactif: Boolean) {
    val t = textes()
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Box(Modifier.align(Alignment.Center)) { ControlesAppel(interactif) }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val suivant = when (vue) {
                AppelBureau.VueAppel.FOCUS -> t.pleinEcran
                AppelBureau.VueAppel.FAUX_PLEIN -> t.masquerLesMembres
                else -> t.afficherLesMembres
            }
            Text(
                suivant,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .curseurMain().clickable {
                        AppelBureau.vue = when (vue) {
                            AppelBureau.VueAppel.FOCUS -> AppelBureau.VueAppel.FAUX_PLEIN
                            AppelBureau.VueAppel.FAUX_PLEIN -> AppelBureau.VueAppel.VRAI_PLEIN
                            else -> AppelBureau.VueAppel.FAUX_PLEIN
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                    .curseurMain().clickable { AppelBureau.reculer() },
                contentAlignment = Alignment.Center,
            ) {
                Icone(
                    Icones.applications,
                    tint = Color.White,
                    taille = 18.dp,
                    // this button does not mean the same thing at every degree
                    description = if (vue == AppelBureau.VueAppel.FOCUS) {
                        t.retour
                    } else {
                        t.quitterPleinEcran
                    },
                )
            }
        }
    }
}

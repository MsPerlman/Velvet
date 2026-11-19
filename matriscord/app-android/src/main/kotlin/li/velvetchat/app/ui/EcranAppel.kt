package li.velvetchat.app.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectDragGestures
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.core.content.ContextCompat
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import li.velvetchat.app.Sonnerie
import li.velvetchat.app.Soundboard
import li.velvetchat.app.VocalManager
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.AppelEnCours
import li.velvetchat.core.model.MediaRef
import li.velvetchat.core.model.ProfilEtendu
import li.velvetchat.core.model.SonSoundboard

/**
 * the button that starts, or joins, a call in a room
 *
 * what it does in order: makes sure of the mic permission (RECORD_AUDIO, asked at runtime, livekit
 * refusing the mic without it), asks the engine for a token (`jetonVocal`), then hands the media
 * connection to [VocalManager]. the call screen ([EcranAppelOverlay]) then appears on its own,
 * driven by the manager's state
 *
 * ringing: starting a call sets a marker ([MatrixEngine.sonner]) that the other sees through
 * [BanniereAppelEntrant] as "call in progress, join" while the app is open. ringing with the screen
 * off (a full screen notification) is still to be wired in `ServiceVeille`
 */
/**
 * start or join a call in a room, from wherever the interface offers it
 *
 * extracted out of [BoutonAppel] the day the conversation menu got a call entry too. it is not a
 * cosmetic split: the mic permission, the token and handing over to [VocalManager] are three steps
 * in a fixed order, and a second copy would have been the one that forgets the permission
 *
 * the returned lambda takes the room and the name to show, and answers nothing. a call opens a
 * screen, so its result is visible without anybody being told
 *
 * [engine] is a parameter and not just `LocalEngine`, because `Shell` is what provides that local
 * and therefore cannot read it. the default keeps every other caller as short as it was
 */
@Composable
fun rememberLanceurAppel(
    engine: MatrixEngine = LocalEngine.current,
    onFini: () -> Unit = {},
): (String, String?) -> Unit {
    val contexte = LocalContext.current
    val scope = rememberCoroutineScope()
    // remembered rather than passed as an argument: the permission callback fires later, long
    // after the click, and needs to know what it was about to call
    var enAttente by remember { mutableStateOf<Pair<String, String?>?>(null) }

    fun lancer(salonId: String, nomSalon: String?) {
        scope.launch {
            val info = runCatching { engine.jetonVocal(salonId) }.getOrNull()
            if (info != null) VocalManager.rejoindre(contexte, engine, salonId, info, nomSalon)
            // a token failure leaves the manager's state at INACTIF: nothing opens, and that beats
            // a stuck call screen. visible feedback will come with the signalling
            onFini()
        }
    }

    val demanderMicro = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { accorde ->
        val cible = enAttente
        enAttente = null
        // `onFini` on refusal too. without it the caller's spinner spins for ever over a call
        // that was never going to happen
        if (accorde && cible != null) lancer(cible.first, cible.second) else onFini()
    }

    return { salonId, nomSalon ->
        val ok = ContextCompat.checkSelfPermission(
            contexte, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) {
            lancer(salonId, nomSalon)
        } else {
            enAttente = salonId to nomSalon
            demanderMicro.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
fun BoutonAppel(salonId: String, nomSalon: String? = null, modifier: Modifier = Modifier) {
    val t = textes()
    val tokens = LocalTokens.current
    var demarrage by remember { mutableStateOf(false) }
    val lancerAppel = rememberLanceurAppel { demarrage = false }

    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable {
                if (!demarrage) {
                    demarrage = true
                    lancerAppel(salonId, nomSalon)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (demarrage) {
            Text("…", fontSize = 18.sp, color = tokens.textSecondary)
        } else {
            Icone(
                Icones.appeler,
                tint = tokens.textSecondary,
                taille = 22.dp,
                description = t.appeler,
            )
        }
    }
}

/**
 * the call screen, full screen, driven by [VocalManager]
 *
 * placed once at the root of the app, so the call survives navigating between rooms (the state
 * living in the manager, not in a screen). renders nothing while no call is active
 */
@Composable
fun EcranAppelOverlay() {
    val t = textes()
    val tokens = LocalTokens.current
    val contexte = LocalContext.current
    val etat by VocalManager.etat.collectAsState()

    if (etat.phase == VocalManager.Phase.INACTIF) return
    // folded: the `Dialog` disappears entirely, so the app becomes usable again. the call goes on,
    // since it lives in `VocalManager` and not in this screen. [BarreAppelReplie] takes over,
    // placed at the shell's root
    if (etat.replie) return

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // a black background and not `surface`: that is what the reference does, and it is not a
        // whim, the tiles carrying saturated gradients, and a grey background would make them float
        // in a frame instead of laying them on emptiness
        // with a video on screen the controls step aside after three seconds untouched, and a tap
        // brings them back: the reference's `CONTROLS_HIDE_TIMEOUT`. a voice only call keeps them,
        // there is nothing behind them to see
        val video = etat.videoLocale != null || etat.participants.any { it.video != null || it.ecran != null }
        var dernierTouche by remember { mutableStateOf(System.currentTimeMillis()) }
        var controlesVisibles by remember { mutableStateOf(true) }
        LaunchedEffect(dernierTouche, video) {
            controlesVisibles = true
            if (!video) return@LaunchedEffect
            kotlinx.coroutines.delay(3_000)
            controlesVisibles = false
        }
        Column(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            dernierTouche = System.currentTimeMillis()
                        }
                    }
                },
        ) {

            EnTeteAppel(nom = etat.salonNom ?: t.appel)

            // ---------------------------------------------------------------- the tiles
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when {
                    etat.phase == VocalManager.Phase.ECHEC -> Text(
                        etat.erreur ?: t.appelImpossible,
                        color = tokens.textMuted,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = tokens.espEcran),
                    )

                    etat.phase == VocalManager.Phase.CONNEXION -> Text(
                        t.connexionEnCours,
                        color = tokens.textMuted,
                        fontSize = 15.sp,
                    )

                    else -> Column(
                        // the reference's grid: 12 to the edges and between cards
                        // (`EDGE_GUTTER`, `CALL_TILE_GUTTER`)
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // a shared screen gets the whole width, at 16:9, and comes first: it is
                        // what people are looking at
                        etat.participants.forEach { p ->
                            p.ecran?.let { ecran ->
                                TuileParticipant(
                                    identite = p.identite,
                                    nom = t.ecranDe(p.nom),
                                    video = ecran,
                                    avatar = p.avatar,
                                    parle = p.parle,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                                )
                            }
                        }
                        // then everybody, me included and last, as square cards two by two. the
                        // reference's are 165 on a common phone, which is half the width less the
                        // gutters. alone, you get one card on the whole width: that is what says
                        // you are in the call
                        val seul = etat.participants.isEmpty()
                        val cartes = etat.participants.map { p ->
                            @Composable { m: Modifier ->
                                TuileParticipant(
                                    identite = p.identite,
                                    nom = p.nom,
                                    video = p.video,
                                    avatar = p.avatar,
                                    parle = p.parle,
                                    modifier = m,
                                )
                            }
                        } + listOfNotNull(
                            etat.moi?.let { moi ->
                                @Composable { m: Modifier ->
                                    TuileParticipant(
                                        identite = moi.identite,
                                        nom = moi.nom,
                                        etiquette = if (seul) t.cestVous(moi.nom) else moi.nom,
                                        video = etat.videoLocale,
                                        avatar = moi.avatar,
                                        parle = moi.parle,
                                        modifier = m,
                                    )
                                }
                            },
                        )
                        if (cartes.size == 1) {
                            cartes[0](Modifier.fillMaxWidth().aspectRatio(1f))
                        } else {
                            cartes.chunked(2).forEach { rang ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    rang.forEach { carte -> carte(Modifier.weight(1f).aspectRatio(1f)) }
                                    if (rang.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        if (seul) {
                            Text(
                                t.personneDautreEncore,
                                color = tokens.textMuted,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = controlesVisibles,
                enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
            ) {
                BarreControlesAppel(etat = etat, contexte = contexte)
            }
        }
    }
}

/**
 * the call screen's header: fold, the room's name
 *
 * the chevron folds the call instead of hanging up, and the distinction is the whole point: you
 * give the app back to its normal use without leaving the voice conversation
 */
@Composable
private fun EnTeteAppel(nom: String) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = tokens.espM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.espM),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .clickable { VocalManager.basculerRepli() },
            contentAlignment = Alignment.Center,
        ) {
            Icone(
                Icones.replier,
                tint = tokens.textPrimary,
                taille = 22.dp,
                description = t.replierLAppel,
            )
        }
        Text(
            nom,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * the folded call bar: the call goes on, the app becomes usable again
 *
 * a `Popup` and not a `Dialog`, and that is the whole subject. a `Dialog` is modal: it would float
 * over an app you still could not touch, which is to say it would solve nothing. a non focusable
 * `Popup` floats without capturing interactions anywhere but on itself, which is exactly what we
 * want, going on reading and writing while talking
 *
 * it also avoids depending on the `Shell`'s layout: the bar places itself at the bottom of the
 * screen, with no need to find the right root `Box` to slot into
 *
 * renders nothing outside a call, nor when the screen is unfolded. the two states exclude each
 * other, which guarantees you never see both at once
 */
@Composable
fun BarreAppelReplie() {
    val t = textes()
    val tokens = LocalTokens.current
    val etat by VocalManager.etat.collectAsState()
    if (etat.phase == VocalManager.Phase.INACTIF || !etat.replie) return

    // the reference's folded call is a floating tile you can drag, not a bar: it shows who is
    // talking, a tap reopens the call, and released it settles against the nearest side, 16 from
    // the edge and clear of the tab bar (`PIP_WINDOW_OFFSET`, `PIP_OFFSET_BOTTOM`)
    val densite = androidx.compose.ui.platform.LocalDensity.current
    val fenetre = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val largeur = with(densite) { 104.dp.roundToPx() }
    val hauteur = with(densite) { 140.dp.roundToPx() }
    val marge = with(densite) { 16.dp.roundToPx() }
    val basMin = with(densite) { (44.dp + 56.dp).roundToPx() }
    val maxX = (fenetre.width - largeur - marge).coerceAtLeast(marge)
    val maxY = (fenetre.height - hauteur - basMin).coerceAtLeast(marge)
    var x by remember { mutableStateOf(maxX.toFloat()) }
    var y by remember { mutableStateOf(maxY.toFloat()) }
    val xAnime by androidx.compose.animation.core.animateFloatAsState(x, label = "pipX")

    val vedette = etat.participants.firstOrNull { it.parle } ?: etat.participants.firstOrNull() ?: etat.moi
    val parle = vedette?.parle == true

    Popup(
        alignment = Alignment.TopStart,
        offset = androidx.compose.ui.unit.IntOffset(xAnime.toInt(), y.toInt()),
    ) {
        Column(
            Modifier.size(104.dp, 140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1F22))
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            // to the nearest side, like the reference
                            x = if (x + largeur / 2f < fenetre.width / 2f) marge.toFloat() else maxX.toFloat()
                        },
                    ) { changement: androidx.compose.ui.input.pointer.PointerInputChange, delta: androidx.compose.ui.geometry.Offset ->
                        changement.consume()
                        x = (x + delta.x).coerceIn(marge.toFloat(), maxX.toFloat())
                        y = (y + delta.y).coerceIn(marge.toFloat(), maxY.toFloat())
                    }
                }
                .clickable { VocalManager.basculerRepli() }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                vedette?.let { v ->
                    Box(
                        Modifier.size(64.dp)
                            .then(if (parle) Modifier.border(3.dp, Color(0xFF23A55A), CircleShape) else Modifier)
                            .padding(if (parle) 4.dp else 0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Avatar(v.identite, v.nom, v.avatar, if (parle) 56.dp else 64.dp)
                    }
                }
            }
            Text(
                vedette?.nom ?: (etat.salonNom ?: t.appel),
                color = tokens.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            // mic and hang up stay reachable while folded: the two gestures you want to make fast
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(if (etat.microActif) Color.White.copy(alpha = 0.08f) else tokens.danger)
                        .clickable { VocalManager.basculerMicro() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icone(
                        if (etat.microActif) Icones.micro else Icones.microCoupe,
                        tint = Color.White,
                        taille = 18.dp,
                        description = if (etat.microActif) t.couperLeMicro else t.retablirLeMicro,
                    )
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(tokens.danger)
                        .clickable { VocalManager.quitter() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icone(Icones.raccrocher, tint = Color.White, taille = 18.dp, description = t.raccrocher)
                }
            }
        }
    }
}

/**
 * a participant tile, the screen's visual core
 *
 * video if the person publishes one, otherwise their avatar laid on their gradient. the gradient is
 * not decorative: it is the same as their profile's (`degradeDe`), so the tile already carries the
 * visual identity the person chose, with no extra storage or setting
 */
@Composable
private fun TuileParticipant(
    identite: String,
    nom: String,
    video: VideoTrack?,
    modifier: Modifier = Modifier,
    /** their profile picture. `null` falls back on the initials, like everywhere else */
    avatar: String? = null,
    /**
     * what the name dot shows, when something has to be added to it
     *
     * separate from [nom] because `Avatar` derives the initials from the name you give it: passing
     * it "somebody (you)" produced an "S(", the first letter of each word, bracket included. the
     * name serves the avatar, and the label the dot
     */
    etiquette: String = nom,
    parle: Boolean = false,
) {
    val tokens = LocalTokens.current
    // the ring breathes. a fixed outline gets confused with a tile border, and it is the movement
    // that says "this one is speaking right now", which is what the reference does
    val battement = androidx.compose.animation.core.rememberInfiniteTransition(label = "parle")
    val epaisseur by battement.animateFloat(
        initialValue = 2f,
        targetValue = 4f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(700),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "epaisseur",
    )
    Box(
        modifier.clip(RoundedCornerShape(tokens.radiusLarge))
            .then(
                if (parle) {
                    Modifier.border(
                        epaisseur.dp,
                        Color(0xFF43A25A),
                        RoundedCornerShape(tokens.radiusLarge),
                    )
                } else {
                    Modifier
                },
            )
            .background(
                if (video != null) SolidColor(Color.Black)
                else degradeDe(identite, ProfilEtendu())
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (video != null) {
            VueVideo(video, Modifier.fillMaxSize())
        } else {
            Avatar(userId = identite, displayName = nom, avatarUrl = avatar, taille = 88.dp)
        }
        // the name, as a dark dot laid on the tile: readable whatever the image or the gradient's
        // shade behind, which plain text does not guarantee
        //
        // one line, and never wider than the tile. "velvet-essai (you)" wrapped and came to sit on
        // the face, the dot being bounded by nothing
        Box(
            Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = tokens.espM, vertical = tokens.espM)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = tokens.espM, vertical = 6.dp),
        ) {
            Text(
                etiquette,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * the controls bar, in its rounded container with a handle
 *
 * five controls, two of which do not act yet, screen sharing and the soundboard. showing them
 * anyway is an explicit decision by the owner: the app will not be put in anybody's hands before
 * they work, so they lie to nobody, and laying the final bar now freezes its geometry once and for
 * all
 */
@Composable
private fun BarreControlesAppel(etat: VocalManager.EtatAppel, contexte: android.content.Context) {
    val t = textes()
    val tokens = LocalTokens.current
    val demanderCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { accorde -> if (accorde) VocalManager.basculerCamera() }
    var panneauSons by remember { mutableStateOf(false) }

    // the screen capture consent. a `StartActivityForResult` and not a permission: android does not
    // expose capture as a right granted once, but as an agreement given every session, whose result
    // is the token. it is that token, the returned `Intent`, that we pass to the sdk, not a boolean
    val demanderEcran = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultat ->
        val donnees = resultat.data
        if (resultat.resultCode == android.app.Activity.RESULT_OK && donnees != null) {
            VocalManager.basculerPartage(contexte, donnees)
        }
        // refused: nothing happens, and that is the right behaviour. insisting with a message would
        // be commenting on a decision just made knowingly. a capture that fails for another reason
        // does get said, see `basculerPartage`
    }

    Column(
        Modifier.fillMaxWidth().padding(tokens.espM)
            .clip(RoundedCornerShape(tokens.radiusLarge))
            .background(tokens.surfaceRaised)
            .padding(vertical = tokens.espM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // the reference's handle: it announces that the bar can be grabbed, even if the gesture is
        // not wired yet
        Box(
            Modifier.width(36.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tokens.textMuted.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.height(tokens.espM))

        // what failed during the call, right above the buttons: a screen share refused by android
        // does not change the call's phase, so the failure message higher up would never show it
        if (etat.phase == VocalManager.Phase.EN_APPEL) {
            etat.erreur?.let {
                Text(
                    it,
                    color = tokens.danger,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = tokens.espL, vertical = 2.dp),
                )
                Spacer(Modifier.height(tokens.espS))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espM),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // camera
            ControleAppel(
                icone = if (etat.cameraActive) Icones.camera else Icones.cameraCoupee,
                actif = etat.cameraActive,
                description = if (etat.cameraActive) t.eteindreLaCamera else t.allumerLaCamera,
            ) {
                // turning it off asks nothing, and turning it on requires the permission, claimed
                // at that precise moment and not on entering the call (see the manifest)
                if (etat.cameraActive) {
                    VocalManager.basculerCamera()
                } else {
                    val ok = ContextCompat.checkSelfPermission(
                        contexte, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (ok) VocalManager.basculerCamera()
                    else demanderCamera.launch(Manifest.permission.CAMERA)
                }
            }

            // flip the camera, and only when there is a camera to flip: a button that can do
            // nothing has no place in a bar of five
            //
            // the icon is a borrowing, `clock_counter_clockwise` for its two arrows, pending
            // `camera-rotate`, which is not in the generated set
            if (etat.cameraActive) {
                ControleAppel(
                    icone = Icones.recents,
                    actif = false,
                    description = t.retournerLaCamera,
                    onClick = { VocalManager.retournerCamera() },
                )
            }

            // Micro
            ControleAppel(
                icone = if (etat.microActif) Icones.micro else Icones.microCoupe,
                actif = !etat.microActif,
                description = if (etat.microActif) t.couperLeMicro else t.retablirLeMicro,
                onClick = { VocalManager.basculerMicro() },
            )

            // screen sharing. stopping asks nothing, and starting goes through the system's consent
            // screen, which nothing lets you bypass or remember, android asking again every
            // session, and quite rightly
            ControleAppel(
                icone = Icones.partageEcran,
                actif = etat.partageActif,
                description = if (etat.partageActif) t.arreterLePartage else t.partagerLEcran,
            ) {
                if (etat.partageActif) {
                    VocalManager.basculerPartage(contexte, null)
                } else {
                    val gestionnaire = contexte.getSystemService(
                        android.content.Context.MEDIA_PROJECTION_SERVICE
                    ) as? android.media.projection.MediaProjectionManager
                    gestionnaire?.let { demanderEcran.launch(it.createScreenCaptureIntent()) }
                }
            }

            // soundboard. the button disappears when the server has published no sound: we do not
            // offer what cannot come to anything. it takes its place back as soon as a sound is
            // added to the server's settings
            val sons by VocalManager.sons.collectAsState()
            if (sons.isNotEmpty()) {
                Box {
                    ControleAppel(
                        icone = Icones.soundboard,
                        actif = panneauSons,
                        description = "Soundboard",
                    ) { panneauSons = !panneauSons }
                    if (panneauSons) {
                        PanneauSoundboard(sons) { panneauSons = false }
                    }
                }
            }

            // hang up: a rounded square and not a circle, like the reference. that is what sets it
            // apart from the other four other than by its colour
            Box(
                Modifier.width(64.dp).height(48.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.danger)
                    .clickable { VocalManager.quitter() },
                contentAlignment = Alignment.Center,
            ) {
                Icone(Icones.raccrocher, tint = Color.White, taille = 24.dp, description = t.raccrocher)
            }
        }
    }
}

/**
 * the sound grid, laid over the controls bar
 *
 * a `Popup` and not a modal sheet: you fire a sound while talking, watching the others. a sheet
 * hiding the call to lay down a one second effect would invert the relation between the accessory
 * and the main thing
 *
 * the panel stays open after a sound. you fire several in a row, which is the whole point, and
 * reopening the grid between two would cost a gesture each time. same reasoning as the emoji picker
 */
@Composable
private fun PanneauSoundboard(sons: List<SonSoundboard>, onFermer: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    val engine = LocalEngine.current
    val contexte = LocalContext.current
    val scope = rememberCoroutineScope()

    // the sound being downloaded, so the box says something is happening. without it, a first tap
    // on a never played sound looks ineffective for as long as the network takes
    var enChargement by remember { mutableStateOf<String?>(null) }

    Popup(
        alignment = Alignment.BottomCenter,
        offset = androidx.compose.ui.unit.IntOffset(0, -220),
        onDismissRequest = onFermer,
    ) {
        Column(
            Modifier.padding(horizontal = tokens.espM)
                .clip(RoundedCornerShape(tokens.radiusLarge))
                .background(tokens.surfaceRaised)
                .padding(tokens.espM),
        ) {
            Text(
                if (Soundboard.auCasque(contexte)) "Soundboard"
                // said at the moment of the gesture, not in a help page. on speaker we play nothing
                // locally: the mic would pick the sound up on top of the copy sent, and the others
                // would hear it twice, offset
                else t.soundboardAuCasque,
                color = tokens.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = tokens.espS),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.width(300.dp).height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(sons, key = { it.id }) { son ->
                    val occupe = enChargement == son.id
                    Column(
                        Modifier.height(66.dp)
                            .clip(RoundedCornerShape(tokens.radiusMedium))
                            .background(tokens.surface)
                            .clickable(enabled = !occupe) {
                                enChargement = son.id
                                scope.launch {
                                    runCatching {
                                        val chemin = kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.IO
                                        ) {
                                            engine.mediaFile(
                                                MediaRef(
                                                    source = son.mxc,
                                                    filename = son.nom,
                                                    mimeType = "audio/*",
                                                    sizeBytes = null,
                                                )
                                            )
                                        }
                                        VocalManager.jouerSon(
                                            contexte, son.id, java.io.File(chemin)
                                        )
                                    }
                                    enChargement = null
                                }
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(son.emoji ?: "🔈", fontSize = 20.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            son.nom,
                            color = if (occupe) tokens.textMuted else tokens.textPrimary,
                            fontSize = 10.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** a round control of the call bar. `actif` is the engaged state (filled background) */
@Composable
private fun ControleAppel(
    icone: androidx.compose.ui.graphics.vector.ImageVector? = null,
    glyphe: String? = null,
    actif: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .background(if (actif) tokens.accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icone != null -> Icone(
                icone,
                tint = if (actif) Color.White else tokens.textPrimary,
                taille = 24.dp,
                description = description,
            )
            // the emoji fallback, kept for a control that would not have its drawing yet. none is
            // left: the six missing ones (phone, handset down, camera, camera crossed out, screen
            // share, headset) went into the phosphor set. reported from the device, and rightly, an
            // emoji changes drawing from one phone to the next, does not take the accent colour,
            // and is recognised as an emoji: it was not a button
            glyphe != null -> Text(glyphe, fontSize = 22.sp)
        }
    }
}

/**
 * a video tile: somebody's track, with their name overlaid
 *
 * rendered by a livekit `TextureViewRenderer` inside an `AndroidView`, compose having no video
 * surface, and that being the view the sdk knows how to feed
 *
 * `key(piste)` is not decorative: `factory` only runs once per slot, so without it, somebody
 * turning their camera off then on again (a new track, the same position in the list) would keep
 * the view attached to the old track, a frozen image, with no error
 */
@Composable
private fun VueVideo(piste: VideoTrack, modifier: Modifier = Modifier) {
    key(piste) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextureViewRenderer(ctx).also { vue ->
                    // the order matters: initialise (the room's egl context) before attaching the
                    // track, otherwise the view stays black without saying a thing
                    VocalManager.initialiserRendu(vue)
                    piste.addRenderer(vue)
                }
            },
            onRelease = { vue ->
                // detach then release: a view left subscribed holds the track and goes on consuming
                // frames for a screen that no longer exists
                piste.removeRenderer(vue)
                vue.release()
            },
        )
    }
}

/**
 * the "call in progress, join" banner, at the top of the conversation
 *
 * this is the ring, on the called side: when somebody starts a call in this room, a state marker is
 * set ([MatrixEngine.sonner]) and this banner appears, offering to join the same media room
 *
 * honest scope: this rings in the open app. ringing with the screen off (a full screen
 * notification) is still to be wired in `ServiceVeille`. and for want of a state event stream on
 * the sdk's side, we poll the marker every 3 s, which is crude but enough at this scale, a real
 * stream coming with matrixrtc membership
 */
@Composable
fun BanniereAppelEntrant(salons: Map<String, String>) {
    val t = textes()
    val engine = LocalEngine.current
    val tokens = LocalTokens.current
    val contexte = LocalContext.current
    val scope = rememberCoroutineScope()
    val etat by VocalManager.etat.collectAsState()

    var appels by remember { mutableStateOf<Map<String, AppelEnCours>>(emptyMap()) }
    var moi by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { moi = runCatching { engine.currentSession().userId }.getOrNull() }

    // the key is the set of ids, not the map: a room's name can change with no reason at all to
    // restart the watch
    val ids = remember(salons) { salons.keys.sorted() }
    LaunchedEffect(ids) {
        if (ids.isEmpty()) {
            appels = emptyMap()
            return@LaunchedEffect
        }
        engine.appelsEnCoursFlow(ids).collect { appels = it }
    }

    // the first call that is not ours and that we have not already joined. one at a time: two
    // stacked banners would only force a choice of which to close
    //
    // nothing until we know who we are. `moi` is read asynchronously, and on the first emission it
    // is still `null` while `a.appelant != null` is true, so we announced our own call to ourselves
    // for as long as the session took to answer. seen on the emulator
    val entrant = if (moi == null) null else appels.entries.firstOrNull { (id, a) ->
        a.appelant != moi &&
            !(etat.phase == VocalManager.Phase.EN_APPEL && etat.salonId == id)
    }
    val salonId = entrant?.key
    val nomSalon = salonId?.let { salons[it] }

    fun lancer() {
        if (salonId == null) return
        scope.launch {
            val info = runCatching { engine.jetonVocal(salonId) }.getOrNull()
            if (info != null) VocalManager.rejoindre(contexte, engine, salonId, info, nomSalon)
        }
    }

    // created unconditionally (compose's rule: no hook behind a `return`), and only the banner's
    // display is conditional, below
    val demanderMicro = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { accorde -> if (accorde) lancer() }

    // the sorting happens higher up: `entrant` is already "not mine, not already joined"
    val sonne = entrant != null

    // the ringtone follows the banner exactly, and stopping it goes through `onDispose`
    //
    // that is what guarantees it falls silent in every exit case, including those we do not
    // enumerate: you join, the caller hangs up, you change room, you leave the screen. a version
    // calling `arreter()` in the places where we know it is needed would forget one, and a ringtone
    // you cannot silence is the worst possible fault for a messaging app
    DisposableEffect(sonne) {
        if (sonne) Sonnerie.demarrer(contexte) else Sonnerie.arreter()
        onDispose { Sonnerie.arreter() }
    }

    if (!sonne) return

    // a `Popup`, like the folded call bar. the banner lived in the conversation, so you only saw it
    // by already being in the right room, which is to say almost never at the moment somebody
    // calls. placed here, it floats over whatever screen is up: the room list, another
    // conversation, the settings
    Popup(alignment = Alignment.TopCenter) {
    Row(
        Modifier.fillMaxWidth().background(tokens.accent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(Icones.appeler, tint = Color.White, taille = 18.dp, description = null)
        Spacer(Modifier.width(10.dp))
        Text(
            nomSalon?.let { t.appelAvec(it) } ?: t.appelEnCours,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White)
                .clickable {
                    val ok = ContextCompat.checkSelfPermission(
                        contexte, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (ok) lancer() else demanderMicro.launch(Manifest.permission.RECORD_AUDIO)
                }
                .padding(horizontal = tokens.espL, vertical = 7.dp),
        ) {
            Text(t.rejoindre, color = tokens.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
    }
}

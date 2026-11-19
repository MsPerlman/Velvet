package li.velvetchat.desk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.sun.jna.Memory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import livekit.proto.AudioFrame
import livekit.proto.Ffi
import livekit.proto.Room
import livekit.proto.Track
import livekit.proto.VideoFrame
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage

/**
 * the desktop's screen sharing
 *
 * the native core has one video source type, `VIDEO_SOURCE_NATIVE`: it opens neither camera nor
 * screen itself, it waits to be pushed frames. for a screen that is good news, `java.awt.Robot`
 * captures it from the jdk with no extra dependency
 *
 * what is capped, and why
 *
 * fifteen frames a second, 1600 points wide at most. a 4k screen at thirty frames is a gigabyte a
 * second before encoding, which the loop would spend its time copying, and a shared desktop is not
 * a film, it shows text and windows that barely move. those two caps fit the house link and keep
 * the text readable, which is the only criterion here
 *
 * `BGRA` and not `RGBA`: `BufferedImage` stores its pixels as `0xAARRGGBB`, so an int writes to
 * memory little endian as B, G, R, A. that is exactly `BGRA` for the native core, and declaring it
 * that way saves walking every pixel to swap two bytes. over two million pixels fifteen times a
 * second, that detour would show in the picture
 */
internal object PartageEcranBureau {

    // the quality chosen in the picker, reread every time a share starts
    private val IMAGES_PAR_SECONDE get() = ReglagesPartage.qualite.images
    private val LARGEUR_MAX get() = ReglagesPartage.qualite.largeur
    private const val TAUX_SON = 48_000
    private const val CANAUX_SON = 2

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var boucle: Job? = null
    private var source = 0L
    private var piste = 0L

    /**
     * what was published, so it can be unpublished
     *
     * freeing the handles is not unpublishing. the first pass did only that, and the track stayed
     * in the room from the server's point of view, frozen on its last frame, while the next share
     * arrived beside it. changing source therefore left the other side looking at the previous
     * window for ever, and every re-share during a call added one more dead track
     */
    private var sidPiste = ""
    private var sidPisteSon = ""
    private var participant = 0L

    var actif by androidx.compose.runtime.mutableStateOf(false)
        private set

    /** a video call was asked for: the call's controls open the camera picker once joined */
    var demanderCamera by androidx.compose.runtime.mutableStateOf(false)

    /**
     * we restart the capture ourselves, this is not somebody sharing again
     *
     * without this flag, dragging the edge of a shared window rings the start chime every
     * single time
     */
    private var enRelance = false

    var erreur by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    /** what is being sent right now, so the menu can say it */
    var sourceCourante by androidx.compose.runtime.mutableStateOf<SourceVideo?>(null)
        private set

    /** stops if it is already this source, starts otherwise */
    fun basculer(salle: Long, participantLocal: Long, voulue: SourceVideo) {
        val memeSource = actif && sourceCourante == voulue
        if (actif) arreter()
        if (!memeSource) demarrer(salle, participantLocal, voulue)
    }

    private fun demarrer(salle: Long, participantLocal: Long, voulue: SourceVideo) {
        if (salle == 0L || participantLocal == 0L) return
        erreur = null
        sourceCourante = voulue
        boucle = portee.launch {
            if (envoyerParProcessus(salle, participantLocal, voulue)) return@launch
            // nothing on disk can capture this source: no gstreamer, no ffmpeg. the jdk still
            // takes a screen, which is better than refusing
            val robot = runCatching { Robot() }.getOrElse {
                erreur = li.velvetchat.app.ui.horsUi.partageImpossible
                return@launch
            }
            val ecran = runCatching {
                val appareils = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
                val choisi = (voulue as? SourceVideo.Ecran)?.index ?: 0
                appareils[choisi.coerceIn(appareils.indices)].defaultConfiguration.bounds
            }.getOrElse {
                erreur = li.velvetchat.app.ui.horsUi.partageImpossible
                return@launch
            }
            // the size announced at creation does not change afterwards: we frame once
            val echelle = minOf(1.0, LARGEUR_MAX.toDouble() / ecran.width)
            val largeur = (ecran.width * echelle).toInt() and 1.inv()
            val hauteur = (ecran.height * echelle).toInt() and 1.inv()

            val ouverture = runCatching { ouvrir(salle, participantLocal, largeur, hauteur) }
            if (ouverture.isFailure) {
                erreur = li.velvetchat.app.ui.horsUi.partageImpossible
                fermer()
                return@launch
            }
            actif = true

            // one native buffer for the whole session: allocating one per frame would be fifteen
            // multi megabyte allocations a second, which the garbage collector would charge back as
            // stutter in the very picture being shared
            val tampon = Memory(largeur.toLong() * hauteur * 4)
            val cadre = Rectangle(ecran)
            var reduite: BufferedImage? = null
            val periode = 1000L / IMAGES_PAR_SECONDE
            try {
                while (isActive) {
                    val debut = System.currentTimeMillis()
                    val prise = runCatching { robot.createScreenCapture(cadre) }.getOrNull()
                    if (prise != null) {
                        val image = if (echelle == 1.0) prise else {
                            val cible = reduite ?: BufferedImage(
                                largeur,
                                hauteur,
                                BufferedImage.TYPE_INT_RGB,
                            ).also { reduite = it }
                            cible.createGraphics().apply {
                                setRenderingHint(
                                    java.awt.RenderingHints.KEY_INTERPOLATION,
                                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                                )
                                drawImage(prise, 0, 0, largeur, hauteur, null)
                                dispose()
                            }
                            cible
                        }
                        pousser(image, tampon, largeur, hauteur)
                    }
                    delay((periode - (System.currentTimeMillis() - debut)).coerceAtLeast(1))
                }
            } finally {
                tampon.close()
            }
        }
    }

    fun arreter() {
        VideoBureau.retirerApercuLocal()
        boucle?.cancel()
        boucle = null
        boucleSon?.cancel()
        boucleSon = null
        processus?.destroy()
        processus = null
        fermerSon()
        fermer()
        actif = false
        sourceCourante = null
    }

    private var processus: Process? = null
    private var processusSon: Process? = null
    private var sourceSon = 0L
    private var pisteSon = 0L
    private var boucleSon: Job? = null

    /**
     * a source read by an outside process, pushed frame by frame
     *
     * the process is gstreamer for a window or a screen, and ffmpeg for a camera or a film: see
     * `SourcesVideo.commandeCapture`, which is the only place that chooses. either way it writes
     * raw `BGRA`, which is already what [pousser] sends, so there is nothing to convert on either
     * side
     *
     * the size is imposed rather than guessed, because the source decides otherwise, a webcam at
     * 1280x720, a film at 1920x800, while the livekit track is created once and for all
     *
     * each frame is exactly `width * height * 4` bytes: we read that block, push it, and start
     * again. a short read means the process stopped, end of the film, camera unplugged, window
     * closed, and the loop stops with it
     *
     * returns false when nothing on disk can capture this source, and the caller then tries the
     * jdk
     */
    private suspend fun envoyerParProcessus(
        salle: Long,
        participantLocal: Long,
        voulue: SourceVideo,
    ): Boolean {
        // cancelling the loop has to stop the reading, so we keep its context, which `cancel()`
        // will mark inactive. reading it inside a lambda would want a suspension in the middle of a
        // blocking stream, where it would not happen anyway
        val contexte = kotlinx.coroutines.currentCoroutineContext()
        val vivant = { contexte.isActive }

        // the track takes the source's own shape, capped at the preset's width
        //
        // it used to be forced to 16/9. a window is almost never 16/9, so it arrived pillarboxed
        // inside the track: a 900 point tall window sent in a 1600×900 frame kept maybe half the
        // pixels, and people read that as "the quality settings do nothing". a screen that is 16/9
        // lands on exactly the same numbers as before
        val cadre = SourcesVideo.cadre(voulue)
        val echelle = if (cadre == null) 1.0 else minOf(1.0, LARGEUR_MAX.toDouble() / cadre.width)
        val largeur = if (cadre == null) LARGEUR_MAX else (cadre.width * echelle).toInt() and 1.inv()
        val hauteur = if (cadre == null) LARGEUR_MAX * 9 / 16 else (cadre.height * echelle).toInt() and 1.inv()

        val commande = SourcesVideo.commandeCapture(voulue, largeur, hauteur, IMAGES_PAR_SECONDE)
            ?: run {
                // only a screen has a last resort, and the caller owns it
                if (voulue is SourceVideo.Ecran) return false
                erreur = li.velvetchat.app.ui.horsUi.ffmpegAbsent
                return true
            }
        if (runCatching { ouvrir(salle, participantLocal, largeur, hauteur) }.isFailure) {
            erreur = li.velvetchat.app.ui.horsUi.partageImpossible
            fermer()
            return true
        }
        val p = runCatching {
            CaptureWgc.processus(commande).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        }.getOrElse {
            erreur = li.velvetchat.app.ui.horsUi.partageImpossible
            fermer()
            return true
        }
        processus = p
        actif = true
        if (enRelance) enRelance = false else Sons.partageLance()
        // the sound goes out in parallel
        //
        // a film carries its own and ffmpeg reads it a second time for that. everything else is a
        // windows loopback: the shared window's process when there is one, and otherwise the
        // machine minus velvet. "the window's sound" on a whole screen resolves to the machine,
        // because a screen does not have a sound of its own and refusing would only puzzle
        val commandeSon = when (val src = voulue) {
            is SourceVideo.Fichier -> SourcesVideo.ffmpeg?.let {
                listOf(
                    it.absolutePath, "-hide_banner", "-loglevel", "error",
                    "-re", "-i", src.chemin,
                    "-vn", "-f", "s16le",
                    "-ar", TAUX_SON.toString(), "-ac", CANAUX_SON.toString(), "-",
                )
            }
            else -> when (ReglagesPartage.son) {
                SonDuPartage.AUCUN -> null
                SonDuPartage.FENETRE -> (src as? SourceVideo.Fenetre)?.processus
                    ?.let { CaptureWgc.commandeSon(SonDuPartage.FENETRE, it, TAUX_SON, CANAUX_SON) }
                    ?: CaptureWgc.commandeSon(
                        SonDuPartage.SYSTEME, ProcessHandle.current().pid(), TAUX_SON, CANAUX_SON,
                    )
                SonDuPartage.SYSTEME -> CaptureWgc.commandeSon(
                    SonDuPartage.SYSTEME, ProcessHandle.current().pid(), TAUX_SON, CANAUX_SON,
                )
            }
        }
        if (commandeSon != null) {
            boucleSon = portee.launch { envoyerSon(commandeSon, participantLocal) }
        }
        val octets = largeur * hauteur * 4
        val tampon = Memory(octets.toLong())
        val bloc = ByteArray(octets)

        /**
         * the last frame, sent again when nothing has moved
         *
         * windows graphics capture hands over a frame when the window redraws and not on a clock,
         * which is the right way round: a still screen should cost no bandwidth. but somebody
         * joining the call while it is still would receive nothing at all and sit in front of a
         * grey rectangle until you moved a mouse. twice a second is enough to spare them that,
         * and identical frames cost an encoder almost nothing
         *
         * the lock is not ceremony: without it this thread could push while the reader is halfway
         * through writing the next frame into the same buffer, which shows as a torn picture
         */
        /**
         * the shared window changes size, and the image freezes
         *
         * everything is sized once at the start: gstreamer's caps, the read buffer, and the
         * livekit track's resolution. none of the three follows. measured here: a notepad goes
         * from 1440x753 to 800x620 during a share, and not one frame comes out afterwards —
         * the other side sits in front of the last image at the old size, with nothing saying
         * so. also tried scaling on the gpu with fixed caps before the download: same result,
         * wgc does not renegotiate
         *
         * so we restart, which redoes all three at once. we wait for the size to settle first,
         * otherwise we restart twenty times while the mouse drags the edge
         */
        val veilleTaille = if (voulue is SourceVideo.Fenetre && cadre != null) {
            portee.launch {
                var connue = cadre.width to cadre.height
                while (vivant()) {
                    delay(400)
                    val vu = SourcesVideo.cadre(voulue) ?: continue
                    val taille = vu.width to vu.height
                    if (taille == connue) continue
                    connue = taille
                    // long enough for the drag to be over
                    delay(700)
                    val apres = SourcesVideo.cadre(voulue) ?: continue
                    if ((apres.width to apres.height) != taille) continue
                    // detached, because `arreter` cancels the read loop whose `finally`
                    // cancels this watcher: we would cut ourselves off before having
                    // restarted
                    portee.launch {
                        arreter()
                        enRelance = true
                        demarrer(salle, participantLocal, voulue)
                    }
                    return@launch
                }
            }
        } else {
            null
        }

        val verrou = java.util.concurrent.locks.ReentrantLock()
        var uneTrameRecue = false
        val relance = portee.launch {
            while (vivant()) {
                delay(500)
                if (!uneTrameRecue) continue
                verrou.lock()
                try {
                    remettreAuCoeur(tampon, largeur, hauteur)
                } finally {
                    verrou.unlock()
                }
            }
        }

        try {
            val flux = p.inputStream
            while (vivant()) {
                var lu = 0
                while (lu < octets) {
                    val n = flux.read(bloc, lu, octets - lu)
                    if (n < 0) return true
                    lu += n
                }
                verrou.lock()
                try {
                    tampon.write(0, bloc, 0, octets)
                    uneTrameRecue = true
                    envoyerTrame(tampon, largeur, hauteur)
                } finally {
                    verrou.unlock()
                }
            }
        } finally {
            relance.cancel()
            veilleTaille?.cancel()
            tampon.close()
            runCatching { p.destroy() }
            // the capture stopped without us asking: the shared window closed, the driver
            // hiccuped, the film reached its end. reproduced by killing the capture process, and
            // the app went on claiming it was sharing — green button, track still published on a
            // frozen frame, sound still going out. so we take it down, and the button going out
            // is how you learn
            //
            // on another coroutine because `arreter` cancels this one, and cancelling yourself
            // from your own `finally` is a knot for no reason
            if (vivant()) portee.launch { arreter() }
        }
        return true
    }

    private suspend fun ouvrir(salle: Long, participantLocal: Long, largeur: Int, hauteur: Int) {
        source = LiveKitFfi.requete(
            Ffi.FfiRequest.newBuilder().setNewVideoSource(
                VideoFrame.NewVideoSourceRequest.newBuilder()
                    .setType(VideoFrame.VideoSourceType.VIDEO_SOURCE_NATIVE)
                    // `isScreencast` asks webrtc to favour sharpness over frame rate, which on text
                    // is exactly the right trade
                    .setIsScreencast(true)
                    .setResolution(
                        VideoFrame.VideoSourceResolution.newBuilder()
                            .setWidth(largeur)
                            .setHeight(hauteur),
                    ),
            ).build(),
        ).newVideoSource.source.handle.id
        piste = LiveKitFfi.requete(
            Ffi.FfiRequest.newBuilder().setCreateVideoTrack(
                Track.CreateVideoTrackRequest.newBuilder()
                    .setName("screen")
                    .setSourceHandle(source),
            ).build(),
        ).createVideoTrack.track.handle.id
        val id = LiveKitFfi.nouvelId()
        val qualite = ReglagesPartage.qualite
        val cb = LiveKitFfi.attendre(id) {
            Ffi.FfiRequest.newBuilder().setPublishTrack(
                Room.PublishTrackRequest.newBuilder()
                    .setLocalParticipantHandle(participantLocal)
                    .setTrackHandle(piste)
                    .setRequestAsyncId(id)
                    .setOptions(
                        Room.TrackPublishOptions.newBuilder()
                            .setSource(Track.TrackSource.SOURCE_SCREENSHARE)
                            // the four presets finally reach the encoder. without this the track
                            // went out on webrtc's own default rate whatever you had picked, so
                            // the buttons changed the capture size and nothing else. that is the
                            // whole of "the qualities do not work"
                            .setVideoEncoding(
                                Room.VideoEncoding.newBuilder()
                                    .setMaxBitrate(qualite.debit)
                                    .setMaxFramerate(qualite.images.toDouble()),
                            )
                            .setDegradationPreference(qualite.degradation)
                            // one layer and the whole budget for it. simulcast splits the bitrate
                            // across three sizes, which is worth it when six people watch on six
                            // different links, and a waste in a private call where it just means
                            // the good layer gets a third of the rate
                            .setSimulcast(false),
                    ),
            ).build()
        }.publishTrack
        if (cb.error.isNotBlank()) error("partage : " + cb.error)
        participant = participantLocal
        sidPiste = cb.publication.info.sid
    }

    private fun fermer() {
        depublier(sidPiste)
        sidPiste = ""
        listOf(piste, source).forEach { LiveKitFfi.liberer(it) }
        piste = 0
        source = 0
    }

    /**
     * takes the track out of the room, and says nothing when it fails
     *
     * a failure here means the call is already gone, which is the one case where there is
     * nothing left to unpublish anyway
     */
    private fun depublier(sid: String) {
        if (sid.isBlank() || participant == 0L) return
        runCatching {
            LiveKitFfi.requete(
                Ffi.FfiRequest.newBuilder().setUnpublishTrack(
                    Room.UnpublishTrackRequest.newBuilder()
                        .setLocalParticipantHandle(participant)
                        .setTrackSid(sid)
                        .setStopOnUnpublish(true)
                        .setRequestAsyncId(LiveKitFfi.nouvelId()),
                ).build(),
            )
        }
    }

    /** an awt image pushed as it is, see the file header for the `BGRA` choice */
    private fun pousser(image: BufferedImage, tampon: Memory, largeur: Int, hauteur: Int) {
        val pixels = image.getRGB(0, 0, largeur, hauteur, null, 0, largeur)
        tampon.write(0, pixels, 0, pixels.size)
        envoyerTrame(tampon, largeur, hauteur)
    }

    /**
     * what you are sending, seen from your own side
     *
     * livekit does not send a track back to whoever publishes it, so you never receive your own
     * share and the call screen stayed empty on the sending side. it worked, it was the return feed
     * that was missing, and without it there is no way to know what other people see
     *
     * every frame, and not one in ten as in the first pass: at one frame every two thirds of a
     * second what you saw looked like a run of photos rather than your screen. the cost is the one
     * received video already pays, frame for frame, one copy and one bitmap. if it ever weighs, it
     * is the preview's size that should come down and not its rate. a choppy picture shows, a
     * slightly softer one does not
     */
    private val porteeApercu = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apercuEnCours = java.util.concurrent.atomic.AtomicBoolean(false)
    private var apercuOctets: ByteArray? = null

    private fun envoyerTrame(tampon: Memory, largeur: Int, hauteur: Int) {
        // the core first, the preview after. it used to be the other way round, and the preview is
        // not free: a skia bitmap built from a fresh multi megabyte array, on the capture thread,
        // thirty times a second. every millisecond spent there was a millisecond the frame waited
        // before leaving the machine
        remettreAuCoeur(tampon, largeur, hauteur)

        // skipped rather than queued when the previous one is still going. a preview one frame
        // behind is invisible, a queue is delay, and it would be delay on the loop that matters
        if (!apercuEnCours.compareAndSet(false, true)) return
        val octets = largeur * hauteur * 4
        val bloc = apercuOctets?.takeIf { it.size == octets }
            ?: ByteArray(octets).also { apercuOctets = it }
        runCatching { tampon.read(0, bloc, 0, octets) }
        porteeApercu.launch {
            runCatching { VideoBureau.poserApercuLocal(bloc, largeur, hauteur) }
            apercuEnCours.set(false)
        }
    }

    /** the native buffer handed back to the core, whichever source filled it */
    private fun remettreAuCoeur(tampon: Memory, largeur: Int, hauteur: Int) {
        runCatching {
            LiveKitFfi.requete(
                Ffi.FfiRequest.newBuilder().setCaptureVideoFrame(
                    VideoFrame.CaptureVideoFrameRequest.newBuilder()
                        .setSourceHandle(source)
                        .setTimestampUs(System.nanoTime() / 1_000)
                        .setRotation(VideoFrame.VideoRotation.VIDEO_ROTATION_0)
                        .setBuffer(
                            VideoFrame.VideoBufferInfo.newBuilder()
                                .setType(VideoFrame.VideoBufferType.BGRA)
                                .setWidth(largeur)
                                .setHeight(hauteur)
                                .setStride(largeur * 4)
                                .setDataPtr(com.sun.jna.Pointer.nativeValue(tampon)),
                        ),
                ).build(),
            )
        }
    }

/**
 * the sound of what you share
 *
 * a film went out silent: livekit does have `SOURCE_SCREENSHARE_AUDIO` and nothing fed it. ffmpeg
 * already decodes the file for the picture, so it decodes it a second time for the sound, in a
 * separate process
 *
 * two processes rather than one. a single ffmpeg with two outputs would want a second pipe, which
 * java cannot open cleanly on windows without a named pipe or a socket. two processes are
 * independent: the sound failing leaves the picture, and the picture stopping cuts the sound. both
 * are paced by `-re` on the same wall clock and the same source, so they do not drift from each
 * other noticeably over a film
 *
 * for a file only. a webcam's sound comes from another directshow device, which would have to be
 * picked separately, and a screen's takes a system loopback capture windows does not expose
 * everywhere. a film carries its sound with it, which is the case that was asked for and the only
 * one where the answer is obvious
 *
 * `s16le` 48 kHz stereo in 10 ms slices: that is what webrtc wants, and the pipe's pace sets the
 * tempo, there is no point sleeping between two slices, `-re` already does
 */
private suspend fun envoyerSon(commande: List<String>, participantLocal: Long) {
    val contexte = kotlinx.coroutines.currentCoroutineContext()
    sourceSon = LiveKitFfi.requete(
        Ffi.FfiRequest.newBuilder().setNewAudioSource(
            AudioFrame.NewAudioSourceRequest.newBuilder()
                .setType(AudioFrame.AudioSourceType.AUDIO_SOURCE_NATIVE)
                .setSampleRate(TAUX_SON)
                .setNumChannels(CANAUX_SON)
                // no voice processing on a soundtrack: echo cancellation and noise suppression are
                // made for a microphone and would destroy music
                .setOptions(
                    AudioFrame.AudioSourceOptions.newBuilder()
                        .setEchoCancellation(false)
                        .setNoiseSuppression(false)
                        .setAutoGainControl(false),
                ),
        ).build(),
    ).newAudioSource.source.handle.id
    pisteSon = LiveKitFfi.requete(
        Ffi.FfiRequest.newBuilder().setCreateAudioTrack(
            Track.CreateAudioTrackRequest.newBuilder()
                .setName("film")
                .setSourceHandle(sourceSon),
        ).build(),
    ).createAudioTrack.track.handle.id
    val id = LiveKitFfi.nouvelId()
    val cb = LiveKitFfi.attendre(id) {
        Ffi.FfiRequest.newBuilder().setPublishTrack(
            Room.PublishTrackRequest.newBuilder()
                .setLocalParticipantHandle(participantLocal)
                .setTrackHandle(pisteSon)
                .setRequestAsyncId(id)
                .setOptions(
                    Room.TrackPublishOptions.newBuilder()
                        .setSource(Track.TrackSource.SOURCE_SCREENSHARE_AUDIO)
                        .setDtx(false)
                        .setRed(true),
                ),
        ).build()
    }.publishTrack
    if (cb.error.isNotBlank()) {
        fermerSon()
        return
    }
    participant = participantLocal
    sidPisteSon = cb.publication.info.sid

    val p = runCatching {
        CaptureWgc.processus(commande)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
    }.getOrElse {
        fermerSon()
        return
    }
    processusSon = p
    // 10 ms: 480 samples per channel, two bytes each
    val parCanal = TAUX_SON / 100
    val octets = parCanal * CANAUX_SON * 2
    val tampon = Memory(octets.toLong())
    val bloc = ByteArray(octets)
    try {
        val flux = p.inputStream
        while (contexte.isActive) {
            var lu = 0
            while (lu < octets) {
                val n = flux.read(bloc, lu, octets - lu)
                if (n < 0) return
                lu += n
            }
            tampon.write(0, bloc, 0, octets)
            runCatching {
                LiveKitFfi.requete(
                    Ffi.FfiRequest.newBuilder().setCaptureAudioFrame(
                        AudioFrame.CaptureAudioFrameRequest.newBuilder()
                            .setSourceHandle(sourceSon)
                            .setBuffer(
                                AudioFrame.AudioFrameBufferInfo.newBuilder()
                                    .setDataPtr(com.sun.jna.Pointer.nativeValue(tampon))
                                    .setNumChannels(CANAUX_SON)
                                    .setSampleRate(TAUX_SON)
                                    .setSamplesPerChannel(parCanal),
                            ),
                    ).build(),
                )
            }
        }
    } finally {
        tampon.close()
        runCatching { p.destroy() }
    }
}

private fun fermerSon() {
    processusSon?.destroy()
    processusSon = null
    depublier(sidPisteSon)
    sidPisteSon = ""
    listOf(pisteSon, sourceSon).forEach { LiveKitFfi.liberer(it) }
    pisteSon = 0
    sourceSon = 0
}
}

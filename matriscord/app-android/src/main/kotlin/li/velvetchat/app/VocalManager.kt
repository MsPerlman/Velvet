package li.velvetchat.app

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.InfoVocal
import li.velvetchat.app.ui.horsUi

/**
 * a call's media stack, app side — what the engine does not do, which is talk to livekit. media
 * does not live in the matrix sdk (see `PLAN-VOCAL.md`): the engine issues the token, this joins the
 * media room, publishes the mic and follows who is connected
 *
 * a singleton on purpose, because a call has to survive navigation — state inside a screen would
 * mean leaving the conversation cuts the call. one call at a time, and a second join while one runs
 * is ignored
 *
 * left to do: ring the other device, and hold the call in the background through a foreground
 * service. for now it lasts as long as the process and the call screen
 */
object VocalManager {

    /** the manager's own scope: a call must not die with a screen's scope */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var room: Room? = null

    /**
     * the room's profile pictures, by matrix id
     *
     * filled once on entering the call and emptied on leaving. a table rather than a call per
     * participant: members come and go during a call, and one request per arrival would turn a busy
     * room into a burst of requests for data that does not change
     */
    private var membresDuSalon: Map<String, li.velvetchat.core.model.Member> = emptyMap()

    /**
     * the matrix id behind a livekit identity
     *
     * the token sets `@person:server:DEVICE`, with a device suffix so two sessions of the same
     * person are two participants. the consequence showed on screen: the name looked up in the
     * member list matched nobody, so neither nickname nor picture, and the tile displayed the raw
     * identity, which was reported in fairly unambiguous terms. the desktop already stripped that
     * suffix and the phone never did
     */
    private fun userIdDe(identite: String): String {
        val morceaux = identite.split(':')
        return if (morceaux.size >= 3) morceaux.dropLast(1).joinToString(":") else identite
    }
    private var jobEvenements: Job? = null

    /**
     * the heartbeat that keeps the call visible
     *
     * the ring marker and the membership carry a timestamp and expire after two minutes, so that an
     * app killed abruptly does not leave a ghost call behind. the consequence, unnoticed until now:
     * with no rewrite, a call lasting more than two minutes disappeared, and nobody could join it
     * any more although it was still running
     */
    private var jobBattement: Job? = null

    /** the media keys the others send, fed to the key provider while the call lasts */
    private var jobCles: Job? = null

    /** my media key for this call, sent again whenever somebody joins */
    private var maCle: ByteArray? = null

    /** held so the foreground service can be stopped from anywhere */
    private var appContext: Context? = null

    /** held so the call marker (the ring) can be removed on hanging up */
    private var engine: MatrixEngine? = null
    private var salonActuel: String? = null

    private val _etat = MutableStateFlow(EtatAppel())
    val etat: StateFlow<EtatAppel> = _etat.asStateFlow()

    private val _sons =
        MutableStateFlow<List<li.velvetchat.core.model.SonSoundboard>>(emptyList())

    /**
     * the open server's sound catalogue, for the soundboard
     *
     * here and not in [EtatAppel]: the catalogue belongs to the server, not to the call. putting it
     * in the call's state would wipe it on every new `EtatAppel(...)`, so on every pick up, and it
     * would have to be loaded again for nothing
     *
     * here and not in a `CompositionLocal` either: the call screen is mounted right at the top of
     * the shell, before the open server is known, so no provider can wrap it
     */
    val sons: StateFlow<List<li.velvetchat.core.model.SonSoundboard>> = _sons.asStateFlow()

    fun setSons(catalogue: List<li.velvetchat.core.model.SonSoundboard>) {
        _sons.value = catalogue
    }

    /**
     * joins a room's media room
     *
     * the caller must have obtained [info] through `MatrixEngine.jetonVocal(salonId)` and the mic
     * permission (RECORD_AUDIO) beforehand: livekit refuses the mic without it
     *
     * idempotent while a call is already running: we do not join twice
     */
    suspend fun rejoindre(
        context: Context,
        engine: MatrixEngine,
        salonId: String,
        info: InfoVocal,
        /** the room's readable name, for the call screen's header */
        nomSalon: String? = null,
    ) {
        if (_etat.value.phase == Phase.EN_APPEL || _etat.value.phase == Phase.CONNEXION) return
        _etat.value = EtatAppel(salonId = salonId, salonNom = nomSalon, phase = Phase.CONNEXION)
        appContext = context.applicationContext
        SonsAppel.preparer(context.applicationContext)
        this.engine = engine
        salonActuel = salonId

        val r = LiveKit.create(context.applicationContext)
        room = r
        // end to end media encryption, element call's way: each sender has their own key (no
        // shared key), passed raw and derived by hkdf with livekit's salt, a ten step ratchet
        // window and a 256 slot keyring. the sfu only ever relays encrypted frames
        val cles = io.livekit.android.e2ee.BaseKeyProvider(
            "LKFrameEncryptionKey",
            "LK-ROCKS",
            10,
            false,
            -1,
            256,
            false,
            livekit.org.webrtc.FrameCryptorKeyDerivationAlgorithm.HKDF,
        )
        r.e2eeOptions = io.livekit.android.e2ee.E2EEOptions(keyProvider = cles)
        val cle = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        maCle = cle
        // keys sent a while before we arrived count: whoever was already there sent theirs then
        jobCles = scope.launch {
            runCatching {
                val repondus = mutableSetOf<String>()
                engine.clesAppelFlow(salonId, System.currentTimeMillis() - CLES_RECENTES_MS).collect { c ->
                    val pose = cles.rtcKeyProvider.setKey(c.identite, c.index, c.cle)
                    android.util.Log.i("VocalManager", "clé reçue de ${c.identite} [${c.index}] posée=$pose")
                    // the first key from somebody gets mine back: if they missed my first send,
                    // their channel opening late, this is what lets them hear me
                    if (repondus.add(c.identite)) envoyerMaCle(listOf(c.identite))
                }
            }.onFailure { android.util.Log.w("VocalManager", "clés d'appel", it) }
        }
        // the room's events drive the participant list and the end of the call
        jobEvenements = scope.launch {
            r.events.collect { ev -> surEvenement(ev) }
        }

        try {
            // audio publishing settings, set before the mic. they decide the soundboard's quality
            // more surely than any processing:
            //
            //     - `dtx = false`: activity detection cuts transmission during silences, which clips a sound
            //       effect's attack, the start of a sound looking exactly like a silence ending
            //     - `red = true`: redundancy, so a lost packet does not leave a hole. over a turn relay on 4g, that
            //       is where it holds up or does not
            //     - 48 kbit/s: opus in voice mode is set far lower, which crushes everything that is not a voice
            //
            // the price is a slightly higher upstream bitrate, continuously. taken on: a call here is
            // counted in minutes, not in hours of streaming
            r.audioTrackPublishDefaults = r.audioTrackPublishDefaults.copy(
                dtx = false,
                red = true,
                audioBitrate = 48_000,
            )
            r.connect(info.url, info.jwt)
            // my key, set before the mic publishes, then sent: raw bytes through the native
            // provider, since the sdk's string api would re-encode them as utf-8
            r.localParticipant.identity?.value?.let { cles.rtcKeyProvider.setKey(it, 0, cle) }
            envoyerMaCle(r.remoteParticipants.values.mapNotNull { it.identity?.value })
            r.localParticipant.setMicrophoneEnabled(true)
            // the soundboard adds itself to the mic's stream: see [Soundboard] for why it is not a
            // separate track. wired after the mic, for want of a track before it
            brancherSoundboard(r)
            // connected: we go to the foreground so the call survives a return to the home screen
            // (android otherwise cuts a background app's mic)
            appContext?.let { ServiceAppel.demarrer(it) }
            // the ring: we set the call marker so the others can join, and its membership, which is
            // what the "N in voice" badge counts. rewritten periodically, see [jobBattement]
            jobBattement = scope.launch {
                while (true) {
                    runCatching { engine.sonner(salonId) }
                    runCatching { engine.rejoindreVocal(salonId) }
                    kotlinx.coroutines.delay(BATTEMENT_APPEL_MS)
                }
            }
            // the faces, in one round trip. before switching to EN_APPEL so the call screen's first
            // frame is already the right one: filling them afterwards would show the initials then
            // the pictures, which shows
            membresDuSalon = runCatching {
                engine.members(salonId).associateBy { it.userId }
            }.getOrDefault(emptyMap())
            val moiId = runCatching { engine.profile() }.getOrNull()
            SonsAppel.vocalEntree()
            _etat.update {
                it.copy(
                    phase = Phase.EN_APPEL,
                    participants = participantsDe(r),
                    moi = moiId?.let { p ->
                        ParticipantAppel(
                            identite = p.userId,
                            nom = p.displayName ?: p.userId,
                            avatar = p.avatarUrl,
                        )
                    },
                    microActif = true,
                )
            }
        } catch (e: Exception) {
            nettoyer()
            _etat.value = EtatAppel(salonId = salonId, phase = Phase.ECHEC, erreur = e.message)
        }
    }

    /**
     * hooks the soundboard's mixer onto the mic track
     *
     * silent on failure, and deliberately: the soundboard is a nicety and the call is the service.
     * an sdk version that changed this path must not stop people talking. the button will have no
     * effect, which is something you can observe, where a call failing to establish diagnoses badly
     */
    private fun brancherSoundboard(r: Room) {
        if (soundboardBranche) return
        runCatching {
            val piste = r.localParticipant
                .getTrackPublication(io.livekit.android.room.track.Track.Source.MICROPHONE)
                ?.track as? io.livekit.android.room.track.LocalAudioTrack
            piste?.setAudioBufferCallback(Soundboard.melangeur)
            soundboardBranche = piste != null
        }
    }

    /**
     * has the mixer actually found a track to hook onto? the flag exists because the first attempt
     * usually fails, and failed **silently**: hooking ran right after `setMicrophoneEnabled(true)`,
     * which only *asks* for publication, so a signalling round trip later
     * `getTrackPublication(MICROPHONE)` was still null, `piste?.set…` did nothing, and the
     * `runCatching` had nothing to catch because nothing threw
     *
     * in use that meant the soundboard played in your own headphones — local monitoring goes
     * through our own [AudioTrack] — and reached nobody else. a button that works for you alone is
     * the hardest kind of broken to report
     *
     * so we retry on each track event until one sticks, and this says when to stop. the initial
     * attempt stays: with the publication already there it costs a map lookup and saves an event
     */
    private var soundboardBranche = false

    /**
     * plays a soundboard sound: for everybody in the call, and locally in the headset
     *
     * the file is already downloaded by the caller, the decoding and caching living in [Soundboard]
     * because they have nothing to do with running a call
     */
    fun jouerSon(contexte: Context, cle: String, fichier: java.io.File) {
        if (_etat.value.phase != Phase.EN_APPEL) return
        Soundboard.jouer(contexte, cle, fichier)
    }

    /**
     * folds or unfolds the call screen
     *
     * this is not a comfort. as long as the call screen is a full screen `Dialog`, being in a call
     * means being unable to read anything, so you are locked inside the call. folding gives the app
     * back to its normal use while keeping the voice conversation open, which is what the reference
     * does. the state lives here and not in the screen: it has to survive navigation, like the call
     * itself
     */
    fun basculerRepli() {
        _etat.update { it.copy(replie = !it.replie) }
    }

    /** mutes or unmutes your mic */
    fun basculerMicro() {
        val r = room ?: return
        val actif = !_etat.value.microActif
        scope.launch { runCatching { r.localParticipant.setMicrophoneEnabled(actif) } }
        if (actif) SonsAppel.microOuvert() else SonsAppel.microCoupe()
        _etat.update { it.copy(microActif = actif) }
    }

    /**
     * turns your camera on or off
     *
     * the state is only set after livekit's answer, unlike the mic: publishing a video track can
     * fail, the camera permission being refused or the camera taken by another app, and showing
     * "camera on" on a failure would let somebody think they are being seen. the mic is already
     * published when you mute it, so it cannot fail the same way
     */
    fun basculerCamera() {
        val r = room ?: return
        val actif = !_etat.value.cameraActive
        scope.launch {
            val ok = runCatching { r.localParticipant.setCameraEnabled(actif) }.isSuccess
            if (ok) _etat.update { it.copy(cameraActive = actif, videoLocale = videoLocaleDe(r)) }
        }
    }

    /**
     * switches from the front camera to the back one, and back
     *
     * the sdk can do it on the already published track: the others see the image change without the
     * track being republished, so without a reconnection's black hole. no effect when the camera is
     * off, there being nothing to flip then, and the button does not show
     */
    fun retournerCamera() {
        val r = room ?: return
        val piste = r.localParticipant.videoTrackPublications
            .firstOrNull { (pub, _) -> pub.source == Track.Source.CAMERA }
            ?.second as? io.livekit.android.room.track.LocalVideoTrack ?: return
        runCatching { piste.switchCamera() }
    }

    /**
     * starts or stops screen sharing, in the order android imposes and not ours: since android 14
     * it is consent, then a `mediaProjection` foreground service, then and only then the virtual
     * display. reversing it throws. the sdk keeps that order, we supply the consent and the
     * notification
     *
     * consent is never remembered — android asks every session and nothing lets you keep it — hence
     * [consentement], handed over by the caller who just launched the system screen
     *
     * `onStop` is not a politeness: the system offers its own way to stop a share, the red bubble
     * and the quick settings. without the callback you stop it there and our button goes on saying
     * "sharing", so the interface lies about the app's most sensitive state, the one where other
     * people see your screen
     *
     * @param consentement the result of `createScreenCaptureIntent()`, ignored when stopping
     */
    fun basculerPartage(contexte: Context, consentement: android.content.Intent?) {
        val r = room ?: return
        val actif = !_etat.value.partageActif
        scope.launch {
            // the failure gets said. it was swallowed by a `runCatching{}.isSuccess`: when android
            // refused the capture, foreground service rules, expired consent, a device that forbids
            // it, the button simply did nothing and there was nothing to read anywhere. a screen
            // share that fails silently is the worst of both worlds, you either think you are
            // showing your screen or think you have stopped
            val echec = if (!actif) {
                runCatching { r.localParticipant.setScreenShareEnabled(false) }.exceptionOrNull()
            } else {
                val donnees = consentement
                if (donnees == null) {
                    _etat.update { it.copy(erreur = horsUi.partageDEcranRefuse) }
                    return@launch
                }
                runCatching {
                    r.localParticipant.setScreenShareEnabled(
                        true,
                        io.livekit.android.room.track.screencapture.ScreenCaptureParams(
                            mediaProjectionPermissionResultData = donnees,
                            notificationId = ServiceAppel.ID_PARTAGE,
                            notification = ServiceAppel.notificationPartage(contexte),
                            onStop = {
                                _etat.update { it.copy(partageActif = false) }
                            },
                        ),
                    )
                }.exceptionOrNull()
            }
            if (echec == null) {
                if (actif) SonsAppel.partage()
                _etat.update { it.copy(partageActif = actif, erreur = null) }
            } else {
                android.util.Log.w("Velvet/Vocal", "Partage d'écran", echec)
                _etat.update {
                    it.copy(
                        partageActif = false,
                        erreur = horsUi.partageImpossible +
                            (echec.message?.take(120) ?: echec::class.simpleName.orEmpty()),
                    )
                }
            }
        }
    }

    /** leaves the call and releases the media stack */
    fun quitter() {
        SonsAppel.vocalSortie()
        room?.disconnect()
        nettoyer()
        _etat.value = EtatAppel()
    }

    /** my media key to these livekit identities, encrypted to-device, see the engine */
    private fun envoyerMaCle(pour: List<String>) {
        val e = engine ?: return
        val s = salonActuel ?: return
        val c = maCle ?: return
        if (pour.isEmpty()) return
        scope.launch {
            runCatching { e.envoyerCleAppel(s, 0, c, pour) }
                .onSuccess { android.util.Log.i("VocalManager", "clé envoyée à $pour") }
                .onFailure { android.util.Log.w("VocalManager", "clé non envoyée à $pour", it) }
        }
    }

    private fun nettoyer() {
        jobEvenements?.cancel()
        jobEvenements = null
        jobCles?.cancel()
        jobCles = null
        maCle = null
        // first of all, stop maintaining the ring, otherwise the heartbeat would rewrite it right
        // after it was removed
        jobBattement?.cancel()
        jobBattement = null
        // sounds in flight do not survive the call: the mixer would write into the next call's
        // stream, where nobody asked for anything
        Soundboard.nettoyer()
        // the next call publishes a new track: the hook has to be redone, and saying so
        // here is what stops the flag surviving into a call where it is false
        soundboardBranche = false
        room = null
        membresDuSalon = emptyMap()
        // stop the foreground service. no effect if it never started (a failure before connecting),
        // stopService on an inactive service being a no op
        appContext?.let { ServiceAppel.arreter(it) }
        appContext = null

        val e = engine
        val s = salonActuel
        if (e != null && s != null) {
            scope.launch {
                // your own membership always goes: you are no longer in the call
                runCatching { e.quitterVocal(s) }
                // the ring, though, only goes when nobody is left. in a one to one the two gestures
                // looked the same, and in a group, removing the marker on leaving would hide the
                // call from the others, who are still in it. so we look before cutting, and in case
                // of doubt (an unreadable state) we do not cut: one marker too many expires on its
                // own in two minutes, a missing marker makes the call unfindable immediately
                val moi = runCatching { e.currentSession().userId }.getOrNull()
                val restants = runCatching { e.participantsVocal(s) }.getOrNull()
                    ?.filter { it != moi }
                if (restants != null && restants.isEmpty()) {
                    runCatching { e.raccrocherSonnerie(s) }
                }
            }
        }
        engine = null
        salonActuel = null
    }

    private fun surEvenement(ev: RoomEvent) {
        val r = room ?: return
        // somebody else arriving or leaving is heard, like on the reference (`user_join`,
        // `user_leave`). these events only fire after we connected, so the people already there
        // stay silent
        when (ev) {
            // a track's encryption state, the one way to know from a log that frames decrypt
            is RoomEvent.TrackE2EEStateEvent ->
                android.util.Log.i("VocalManager", "e2ee ${ev.participant.identity?.value} : ${ev.state}")
            is RoomEvent.ParticipantConnected -> {
                SonsAppel.vocalEntree()
                // the newcomer cannot decrypt me without my key
                ev.participant.identity?.value?.let { envoyerMaCle(listOf(it)) }
            }
            is RoomEvent.ParticipantDisconnected -> SonsAppel.vocalSortie()
            else -> Unit
        }
        when (ev) {
            // arrivals and departures, but also everything touching a track: without the track
            // events, a camera turned on mid call never appeared, the list only being recomputed
            // when somebody arrived
            is RoomEvent.ParticipantConnected,
            is RoomEvent.ParticipantDisconnected,
            is RoomEvent.TrackSubscribed,
            is RoomEvent.TrackUnsubscribed,
            is RoomEvent.TrackPublished,
            is RoomEvent.TrackUnpublished,
            is RoomEvent.TrackMuted,
            is RoomEvent.TrackUnmuted,
            is RoomEvent.LocalTrackSubscribed -> {
                // the mic's publication arrives here and not at connection time: this is where
                // the soundboard finally has a track to hook onto. no effect once it is hooked
                brancherSoundboard(r)
                _etat.update {
                    it.copy(participants = participantsDe(r), videoLocale = videoLocaleDe(r))
                }
            }
            // who is speaking. livekit says so on every change, and it is the only source:
            // measuring the level ourselves would cost an analyser per track for information the
            // server already computes
            is RoomEvent.ActiveSpeakersChanged -> {
                val parlent = ev.speakers.mapNotNull { it.identity?.value }.toSet()
                _etat.update { etat ->
                    etat.copy(
                        participants = etat.participants.map { it.copy(parle = it.identite in parlent) },
                        moi = etat.moi?.copy(
                            parle = r.localParticipant.identity?.value in parlent,
                        ),
                    )
                }
            }
            is RoomEvent.Disconnected -> {
                nettoyer()
                _etat.value = EtatAppel()
            }
            else -> {}
        }
    }

    /**
     * the other participants, with their video track when they publish one
     *
     * recomputed in full on every event rather than maintained by additions and removals: a track
     * can appear, be muted, come back, and unsubscribe, and following those transitions one by one
     * multiplies the cases where the map diverges from reality. here the source of truth stays the
     * room, and the state is only a snapshot of it
     */
    private fun participantsDe(r: Room): List<ParticipantAppel> =
        r.remoteParticipants.values.map { p ->
            val identite = p.identity?.value ?: "?"
            val userId = userIdDe(identite)
            val membre = membresDuSalon[userId]
            ParticipantAppel(
                identite = identite,
                // the room nickname first, the one livekit carries next, and as a last resort the
                // localpart of the id, never the raw identity
                nom = membre?.displayName
                    ?: p.name?.takeIf { it.isNotBlank() }
                    ?: userId.removePrefix("@").substringBefore(':'),
                video = p.pisteVideo(Track.Source.CAMERA),
                ecran = p.pisteVideo(Track.Source.SCREEN_SHARE),
                avatar = membre?.avatarUrl,
            )
        }

    /**
     * the video track of a given source, or `null`
     *
     * ## why the source became a question
     *
     * up to phase 3 a participant had only one video track and "the first unmuted one" was enough.
     * screen sharing adds a second: somebody sharing while keeping their camera publishes two.
     * taking the first would show the screen or the face depending on the order the publications
     * arrive in, so at random, and differently from one device to the next
     *
     * a muted or unsubscribed publication has nothing to show: we return `null` rather than a black
     * tile, and the screen falls back on the name
     */
    private fun Participant.pisteVideo(source: Track.Source): VideoTrack? =
        videoTrackPublications
            .firstOrNull { (pub, _) -> pub.source == source && pub.subscribed && !pub.muted }
            ?.second as? VideoTrack

    /** your own camera, for the preview. `null` until it is turned on */
    private fun videoLocaleDe(r: Room): VideoTrack? =
        r.localParticipant.videoTrackPublications
            .firstOrNull { (pub, _) -> pub.source == Track.Source.CAMERA && !pub.muted }
            ?.second as? VideoTrack

    /**
     * prepares a render view for the current call (the room's egl context)
     *
     * to be called before attaching a track to it, otherwise the view stays black with no error. no
     * effect outside a call, which lets the screen compose unconditionally
     */
    fun initialiserRendu(vue: TextureViewRenderer) {
        room?.initVideoRenderer(vue)
    }

    /**
     * how often the ring and the membership are rewritten
     *
     * well under the two minute expiry, not just under it: a heartbeat set right at the deadline
     * would make the call flicker out of the list at the slightest network delay. forty five
     * seconds leave two chances to catch up before anybody sees the call disappear
     */
    private const val BATTEMENT_APPEL_MS = 45_000L

    /** a call's phase, from the screen's point of view */
    enum class Phase { INACTIF, CONNEXION, EN_APPEL, ECHEC }

    /**
     * a person in the call, and their video if they publish one
     *
     * `identite` is what livekit knows of them, the matrix id, put in the token by `lk-jwt-
     * service`. it is that and not the display name that derives their avatar and their gradient:
     * two people can carry the same name, never the same id
     */
    data class ParticipantAppel(
        val identite: String,
        val nom: String,
        /** their camera */
        val video: VideoTrack? = null,
        /**
         * their shared screen
         *
         * separate from [video] and not "the video track": the two coexist for somebody sharing
         * without turning their camera off, and you have to know which is which to put them in the
         * right place, the screen large and the face as a thumbnail
         */
        val ecran: VideoTrack? = null,
        /**
         * their profile picture (`mxc://`), or `null`
         *
         * livekit does not know it: a token only carries an identity and a name. so it comes from
         * matrix, through `members(salonId)`, one round trip on entering that gives the whole table
         * rather than a request per arrival. without it the tile fell back on the initials, and a
         * call where nobody turns their camera on showed nothing but gradients
         */
        val avatar: String? = null,
        /**
         * are they speaking?
         *
         * nothing said so: the phone did not listen to `ActiveSpeakersChanged`, so no ring lit up
         * and a two person call never showed who had the floor. the desktop had been listening from
         * the start
         */
        val parle: Boolean = false,
    )

    /** the observable state of a call in progress */
    data class EtatAppel(
        val salonId: String? = null,
        /** the room's readable name. `null` when the caller did not supply it */
        val salonNom: String? = null,
        val phase: Phase = Phase.INACTIF,
        val participants: List<ParticipantAppel> = emptyList(),
        val microActif: Boolean = true,
        /** camera published. false by default: a call starts in audio, like the reference */
        val cameraActive: Boolean = false,
        /**
         * your own screen is published
         *
         * set back to false also when the system stops the share on its side (the red bubble, the
         * quick settings): see `onStop` in [basculerPartage]
         */
        val partageActif: Boolean = false,
        /** the call screen is folded: the call goes on, the app becomes usable again */
        val replie: Boolean = false,
        /** your own track, for the preview. `null` while the camera is off */
        val videoLocale: VideoTrack? = null,
        /**
         * yourself, so the call screen shows your own tile
         *
         * it did not: alone you saw "nobody else is here yet" and nothing else, while the reference
         * always shows at least your own dot. a call where you cannot see yourself does not say
         * that you are in it
         */
        val moi: ParticipantAppel? = null,
        val erreur: String? = null,
    )
}

/** how far back a media key sent before we joined still counts */
private const val CLES_RECENTES_MS = 10 * 60 * 1000L

package li.velvetchat.desk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import livekit.proto.AudioFrame
import livekit.proto.Ffi
import livekit.proto.Room
import livekit.proto.Track
import li.velvetchat.core.MatrixEngine
import li.velvetchat.app.ui.horsUi

/**
 * the desktop's calls, on livekit's native core ([LiveKitFfi])
 *
 * the same sequence as the phone's `VocalManager`, so the two meet in the same room: a token from
 * `jetonVocal`, then the ring and the membership kept alive every 45 s, and on the way out the
 * membership removed, and the ring too when nobody is left
 *
 * the audio goes through webrtc's device module, so windows captures the mic and plays the others
 * directly, with webrtc's echo cancellation, noise suppression and automatic gain. no frame passes
 * through the jvm
 */
/**
 * an audio device as webrtc names it: the [guid] to address it, the [nom] to put on screen. both
 * come from the same enumeration, which is the entire point
 */
data class Peripherique(val guid: String, val nom: String)

object AppelBureau {
    enum class Phase { INACTIF, CONNEXION, EN_APPEL, ECHEC }

    data class Personne(
        val identite: String,
        val userId: String,
        val parle: Boolean = false,
        /** their microphone track is muted, shown next to their name like on the reference */
        val micCoupe: Boolean = false,
    )

    /** my connection to the call, as livekit grades it: the reference's ping icon colour */
    enum class Qualite { EXCELLENTE, BONNE, FAIBLE, PERDUE }

    /**
     * an incoming video
     *
     * a screen share comes before a camera when both arrive: somebody sharing their screen is doing
     * it so people look at it, where their camera is a side dish
     */
    data class Video(val sid: String, val ecran: Boolean, val userId: String)

    data class Etat(
        val salonId: String? = null,
        val salonNom: String? = null,
        val phase: Phase = Phase.INACTIF,
        val personnes: List<Personne> = emptyList(),
        val moiParle: Boolean = false,
        val erreur: String? = null,
        val videos: List<Video> = emptyList(),
        val qualite: Qualite = Qualite.EXCELLENTE,
    )

    var etat by mutableStateOf(Etat())
        private set

    /**
     * the people i muted for myself, by user id: the reference's "mute" in a voice member's menu
     *
     * only received audio is cut, nobody else is told. by user and not by livekit identity, so it
     * holds across their devices and across reconnections
     *
     * ponytail: lost when the app closes, and no volume slider. livekit's ffi turns a received
     * track on or off and nothing finer; a slider means mixing the streams ourselves
     */
    var muetsLocaux by mutableStateOf(emptySet<String>())
        private set

    fun basculerMuetLocal(userId: String) {
        muetsLocaux = if (userId in muetsLocaux) muetsLocaux - userId else muetsLocaux + userId
        pistesRecues.forEach { (sid, handle) -> activerRecue(sid, handle) }
    }

    /**
     * the video somebody asked to see big, by its `sid`, or null for the grid
     *
     * a shared screen in a tile the size of a postcard is a shared screen nobody can read. the
     * grid is right when four people are talking and wrong the moment one of them shows a
     * spreadsheet
     */
    var videoAgrandie by mutableStateOf<String?>(null)

    /**
     * how much room the call is taking, in four steps
     *
     * four and not two, and each step earns itself. [GRILLE] is everybody at once, which is
     * right until one person shows something worth reading. [FOCUS] gives that one the stage
     * and keeps the app around it, so the conversation and the channel list stay reachable.
     * [FAUX_PLEIN] takes the screen but keeps the other faces on a strip along the bottom,
     * because a shared screen is usually watched together. [VRAI_PLEIN] drops even them, and
     * then the interface itself gets out of the way after a while
     */
    enum class VueAppel { GRILLE, FOCUS, FAUX_PLEIN, VRAI_PLEIN }

    var vue by mutableStateOf(VueAppel.GRILLE)

    /** the window is the screen for the last two, and an ordinary window for the first two */
    val ecranEntier: Boolean
        get() = vue == VueAppel.FAUX_PLEIN || vue == VueAppel.VRAI_PLEIN

    /**
     * back one step, which is what escape does
     *
     * one step at a time rather than straight back to the grid: somebody in true full screen
     * who presses escape wants the faces back, not the whole application
     */
    fun reculer() {
        vue = when (vue) {
            VueAppel.VRAI_PLEIN -> VueAppel.FAUX_PLEIN
            VueAppel.FAUX_PLEIN -> VueAppel.FOCUS
            VueAppel.FOCUS -> { videoAgrandie = null; VueAppel.GRILLE }
            VueAppel.GRILLE -> VueAppel.GRILLE
        }
    }

    /** leaving a call cannot leave the window full screen on an empty room */
    private fun rendreLEcran() {
        videoAgrandie = null
        vue = VueAppel.GRILLE
    }

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var moteur: MatrixEngine? = null

    /** my media key for this call, sent again whenever somebody joins, see [poserCle] */
    private var maCle: ByteArray? = null
    private var jobCles: kotlinx.coroutines.Job? = null
    private var battement: Job? = null
    private var surveillance: Job? = null
    private var arreterEcoute: (() -> Unit)? = null
    private var arreterVideo: (() -> Unit)? = null
    private var moi: String? = null

    private var salle = 0L
    private var audio = 0L
    private var source = 0L
    private var piste = 0L
    /** incoming audio tracks: sid to handle, for muting the output */
    private val pistesRecues = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** incoming video tracks: sid to "this is a screen share", otherwise a camera */
    private val ecrans = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** who sends each video track: sid to livekit identity */
    private val auteurs = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** audio track sid to its owner's identity, so a mute event can find whose microphone it was */
    private val micros = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun videosCourantes(): List<Video> = ecrans.map { (sid, ecran) ->
        Video(sid = sid, ecran = ecran, userId = personne(auteurs[sid].orEmpty()).userId)
    }.sortedByDescending { it.ecran }

    /**
     * the videos to show: other people's, and your own
     *
     * livekit does not send a track back to whoever publishes it, and without that tile the person
     * sharing their screen sees nothing of what they are sending
     */
    internal fun videosAvecLaSienne(): List<Video> {
        val siennes = if (PartageEcranBureau.actif) {
            listOf(
                Video(
                    sid = VideoBureau.APERCU_LOCAL,
                    ecran = PartageEcranBureau.sourceCourante !is SourceVideo.Entree,
                    userId = moi.orEmpty(),
                ),
            )
        } else {
            emptyList()
        }
        return siennes + etat.videos
    }

    fun rejoindre(moteur: MatrixEngine, salonId: String, nom: String?) {
        if (etat.phase == Phase.CONNEXION || etat.phase == Phase.EN_APPEL) {
            if (etat.salonId == salonId) return
            quitter()
        }
        etat = Etat(salonId = salonId, salonNom = nom, phase = Phase.CONNEXION)
        this.moteur = moteur
        portee.launch {
            try {
                moi = runCatching { moteur.currentSession().userId }.getOrNull()
                val info = moteur.jetonVocal(salonId)
                connecter(info.url, info.jwt)
                // my key before the mic publishes, then sent; the others' as they come. keys sent
                // a while before we arrived count, whoever was already there sent theirs then
                val cle = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                maCle = cle
                poserCle(identiteLocale, 0, cle)
                jobCles = portee.launch {
                    runCatching {
                        moteur.clesAppelFlow(salonId, System.currentTimeMillis() - 10 * 60 * 1000L)
                            .collect { c ->
                                poserCle(c.identite, c.index, c.cle)
                                System.err.println("[Appel] clé reçue de ${c.identite} [${c.index}]")
                                // the first key from somebody gets mine back, in case they
                                // missed my first send
                                if (repondus.add(c.identite)) envoyerMaCle(listOf(c.identite))
                            }
                    }.onFailure { System.err.println("[Appel] clés : $it") }
                }
                envoyerMaCle(etat.personnes.map { it.identite })
                publierMicro()
                synchroniser()
                battement = portee.launch {
                    while (true) {
                        runCatching { moteur.sonner(salonId) }
                        runCatching { moteur.rejoindreVocal(salonId) }
                        delay(45_000)
                    }
                }
                // the mic gets watched more often than the presence gets renewed: a bluetooth
                // headset connects in a second and takes windows' default with it. see
                // `surveillerPeripheriques`
                surveillance = portee.launch {
                    while (true) {
                        delay(2_000)
                        runCatching { surveillerPeripheriques() }
                    }
                }
                etat = etat.copy(phase = Phase.EN_APPEL)
                Sons.vocalEntree()
            } catch (e: Exception) {
                System.err.println("[Appel] échec : $e")
                nettoyer(salonId)
                etat = Etat(salonId = salonId, salonNom = nom, phase = Phase.ECHEC, erreur = e.message ?: horsUi.appelImpossibleCourt)
            }
        }
    }

    /**
     * send a video source, or stop
     *
     * here rather than in [PartageEcranBureau] because the room and the local participant are what
     * the call owns: the sharing does not know them, and has no business holding them
     */
    internal fun basculerPartage(source: SourceVideo) {
        if (etat.phase != Phase.EN_APPEL) return
        PartageEcranBureau.basculer(salle, localParticipant, source)
    }

    fun quitter() {
        if (etat.phase == Phase.INACTIF) return
        val s = salle
        // read now, not later
        //
        // `etat = Etat()` below runs at once and the coroutine reaches `nettoyer` afterwards, so
        // it read an id that had already been wiped: leaving never retracted the membership or
        // the ring. you stayed listed in the voice room, and the room went on ringing for
        // everybody else. seen on screen, still in `essai-vocal` after leaving it
        val quitte = etat.salonId
        Sons.vocalSortie()
        portee.launch {
            if (s != 0L) runCatching {
                val id = LiveKitFfi.nouvelId()
                LiveKitFfi.attendre(id, 5_000) {
                    Ffi.FfiRequest.newBuilder().setDisconnect(
                        Room.DisconnectRequest.newBuilder().setRoomHandle(s).setRequestAsyncId(id),
                    ).build()
                }
            }
            nettoyer(quitte)
        }
        rendreLEcran()
        etat = Etat()
    }

    /**
     * mic muted or output muted ([Voix]): applied to the published track and to the received ones
     */
    fun synchroniser() {
        // the same pass takes the mic back when the list has moved
        if (audio != 0L) surveillerPeripheriques()
        if (piste != 0L) runCatching {
            LiveKitFfi.requete(
                Ffi.FfiRequest.newBuilder().setLocalTrackMute(
                    Track.LocalTrackMuteRequest.newBuilder().setTrackHandle(piste).setMute(Voix.microCoupe || Voix.sonCoupe),
                ).build(),
            )
        }
        pistesRecues.forEach { (sid, handle) -> activerRecue(sid, handle) }
    }

    private fun activerRecue(sid: String, handle: Long) {
        val muet = micros[sid]?.let { personne(it).userId in muetsLocaux } == true
        runCatching {
            LiveKitFfi.requete(
                Ffi.FfiRequest.newBuilder().setEnableRemoteTrack(
                    Track.EnableRemoteTrackRequest.newBuilder().setTrackHandle(handle).setEnabled(!Voix.sonCoupe && !muet),
                ).build(),
            )
        }
    }

    private suspend fun connecter(url: String, jwt: String) {
        // the audio module first: it has to exist before the source that uses it
        val reponseAudio = LiveKitFfi.requete(
            Ffi.FfiRequest.newBuilder().setNewPlatformAudio(AudioFrame.NewPlatformAudioRequest.getDefaultInstance()).build(),
        ).newPlatformAudio
        if (reponseAudio.hasError() && reponseAudio.error.isNotBlank()) error("audio : ${reponseAudio.error}")
        audio = reponseAudio.platformAudio.handle.id
        choisirPeripheriques()

        arreterEcoute = LiveKitFfi.ecouter(::surEvenement)
        arreterVideo = LiveKitFfi.ecouter(VideoBureau::surEvenement)
        val id = LiveKitFfi.nouvelId()
        val cb = LiveKitFfi.attendre(id) {
            Ffi.FfiRequest.newBuilder().setConnect(
                Room.ConnectRequest.newBuilder().setUrl(url).setToken(jwt).setRequestAsyncId(id)
                    .setOptions(
                        Room.RoomOptions.newBuilder().setAutoSubscribe(true).setDynacast(true)
                            .setEncryption(chiffrement()).setE2Ee(chiffrement()),
                    ),
            ).build()
        }.connect
        if (cb.error.isNotBlank()) error(cb.error)
        salle = cb.result.room.handle.id
        localParticipant = cb.result.localParticipant.handle.id
        identiteLocale = cb.result.localParticipant.info.identity
        etat = etat.copy(
            personnes = cb.result.participantsList.map { p ->
                val identite = p.participant.info.identity
                val audio = p.publicationsList.map { it.info }.filter { it.kind == Track.TrackKind.KIND_AUDIO }
                audio.forEach { micros[it.sid] = identite }
                personne(identite).copy(micCoupe = audio.isNotEmpty() && audio.all { it.muted })
            },
        )
        LiveKitFfi.requete(
            Ffi.FfiRequest.newBuilder().setReadyForRoomEvent(Room.ReadyForRoomEventRequest.newBuilder().setRoomHandle(salle)).build(),
        )
    }

    private var localParticipant = 0L
    private var identiteLocale = ""

    /**
     * end to end media encryption, element call's way: a key per sender (no shared key), raw and
     * derived by hkdf with livekit's salt, a ten step ratchet window, a 256 slot keyring. the sfu
     * only ever relays encrypted frames
     */
    private fun chiffrement() = livekit.proto.E2Ee.E2eeOptions.newBuilder()
        .setEncryptionType(livekit.proto.E2Ee.EncryptionType.GCM)
        .setKeyProviderOptions(
            livekit.proto.E2Ee.KeyProviderOptions.newBuilder()
                .setRatchetSalt(com.google.protobuf.ByteString.copyFromUtf8("LKFrameEncryptionKey"))
                .setRatchetWindowSize(10)
                .setFailureTolerance(-1)
                .setKeyRingSize(256)
                .setKeyDerivationFunction(livekit.proto.E2Ee.KeyDerivationFunction.HKDF),
        )
        .build()

    /** who already got my key back after sending theirs, see the key collection */
    private val repondus = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** my media key to these livekit identities, encrypted to-device, see the engine */
    private fun envoyerMaCle(pour: List<String>) {
        val m = moteur ?: return
        val s = etat.salonId ?: return
        val c = maCle ?: return
        if (pour.isEmpty()) return
        portee.launch {
            runCatching { m.envoyerCleAppel(s, 0, c, pour) }
                .onFailure { System.err.println("[Appel] clé non envoyée à $pour : $it") }
        }
    }

    /** hands a participant's media key to the frame cryptors */
    private fun poserCle(identite: String, index: Int, cle: ByteArray) {
        if (salle == 0L || identite.isEmpty()) return
        runCatching {
            LiveKitFfi.requete(
                Ffi.FfiRequest.newBuilder().setE2Ee(
                    livekit.proto.E2Ee.E2eeRequest.newBuilder().setRoomHandle(salle).setSetKey(
                        livekit.proto.E2Ee.SetKeyRequest.newBuilder()
                            .setParticipantIdentity(identite)
                            .setKeyIndex(index)
                            .setKey(com.google.protobuf.ByteString.copyFrom(cle)),
                    ),
                ).build(),
            )
        }.onFailure { System.err.println("[Appel] clé de $identite : $it") }
    }

    private suspend fun publierMicro() {
        source = LiveKitFfi.requete(
            Ffi.FfiRequest.newBuilder().setNewAudioSource(
                AudioFrame.NewAudioSourceRequest.newBuilder()
                    .setType(AudioFrame.AudioSourceType.AUDIO_SOURCE_PLATFORM)
                    .setPlatformAudioHandle(audio)
                    .setSampleRate(48_000).setNumChannels(1)
                    .setOptions(
                        AudioFrame.AudioSourceOptions.newBuilder()
                            .setEchoCancellation(true).setNoiseSuppression(true).setAutoGainControl(true),
                    ),
            ).build(),
        ).newAudioSource.source.handle.id
        piste = LiveKitFfi.requete(
            Ffi.FfiRequest.newBuilder().setCreateAudioTrack(
                Track.CreateAudioTrackRequest.newBuilder().setName("microphone").setSourceHandle(source),
            ).build(),
        ).createAudioTrack.track.handle.id
        val id = LiveKitFfi.nouvelId()
        val cb = LiveKitFfi.attendre(id) {
            Ffi.FfiRequest.newBuilder().setPublishTrack(
                Room.PublishTrackRequest.newBuilder()
                    .setLocalParticipantHandle(localParticipant).setTrackHandle(piste).setRequestAsyncId(id)
                    .setOptions(Room.TrackPublishOptions.newBuilder().setSource(Track.TrackSource.SOURCE_MICROPHONE).setDtx(false).setRed(true)),
            ).build()
        }.publishTrack
        if (cb.error.isNotBlank()) error("micro : ${cb.error}")
        // said again after opening the mic. the audio module opens a device when the source is
        // created, and setting the choice again afterwards closes the one it would have taken
        // meanwhile, which is what makes windows give a headset its stereo profile back instead of
        // hands free
        choisirPeripheriques()
        portee.launch {
            // and a second and a half later too: opening is asynchronous driver side, and a headset
            // connecting while the call is being set up arrives after we do
            delay(1_500)
            runCatching { choisirPeripheriques() }
        }
    }

    /**
     * the chosen device applies immediately
     *
     * it was only read when connecting, so changing mic or headset in the settings wrote the
     * preference and did not touch the call in progress. which held right up until you hung up
     */
    fun appliquerPeripheriques() {
        if (audio == 0L) return
        choisirPeripheriques()
    }

    /** the fingerprint of the device list at the last pass, see [surveillerPeripheriques] */
    private var empreintePeripheriques: String? = null

    /**
     * taking back the mic windows just stole
     *
     * plugging in a bluetooth headset switches windows' default communications device to its hands
     * free profile, an 8 kHz mic in the headset instead of the one on the desk
     *
     * that is not what was stealing the mic: the cause was that the choice never reached the adm,
     * for want of speaking its language (see [peripheriquesAudio]). once the guid is set, webrtc
     * holds its device. this watch stays for the case that remains: a device appearing or
     * disappearing renumbers the list, and the adm can fall back on the default. so we watch the
     * list, which changes at exactly that moment, and set the choice again. reimposing it in a loop
     * would be simpler and would chop the sound
     */
    private fun surveillerPeripheriques() {
        val liste = runCatching {
            LiveKitFfi.requete(
                Ffi.FfiRequest.newBuilder().setGetAudioDevices(
                    AudioFrame.GetAudioDevicesRequest.newBuilder().setPlatformAudioHandle(audio),
                ).build(),
            ).getAudioDevices
        }.getOrNull() ?: return
        val empreinte = (liste.recordingDevicesList.map { it.guid } +
            liste.playoutDevicesList.map { it.guid }).joinToString("|")
        if (empreinte == empreintePeripheriques) return
        empreintePeripheriques = empreinte
        choisirPeripheriques()
    }

    /**
     * the device list as webrtc sees it, the only one that counts
     *
     * this was the real cause of the headset stealing the mic, and it had nothing to do with
     * windows being windows: the settings offered `javax.sound.sampled`'s list, and the call set
     * its choice through `SetRecordingDevice`, which only knows webrtc's. two different
     * enumerations, with different names, java truncating its own to thirty one characters, so a
     * match by name that failed, and a failure that said nothing: with no `SetRecordingDevice`, the
     * adm stays on windows' default communications device, which is the bluetooth headset's hands
     * free as soon as it connects. the right mic was ticked in the settings, nobody had ever asked
     * the audio engine for it
     *
     * a handle created for the occasion when no call is running: enumerating opens no capture
     * stream, so the headset does not flip to hands free for it
     */
    fun peripheriquesAudio(entree: Boolean): List<Peripherique> {
        val propre = audio == 0L
        val h = if (propre) {
            runCatching {
                LiveKitFfi.requete(
                    Ffi.FfiRequest.newBuilder()
                        .setNewPlatformAudio(AudioFrame.NewPlatformAudioRequest.getDefaultInstance()).build(),
                ).newPlatformAudio.platformAudio.handle.id
            }.getOrNull() ?: return emptyList()
        } else {
            audio
        }
        try {
            val liste = runCatching {
                LiveKitFfi.requete(
                    Ffi.FfiRequest.newBuilder().setGetAudioDevices(
                        AudioFrame.GetAudioDevicesRequest.newBuilder().setPlatformAudioHandle(h),
                    ).build(),
                ).getAudioDevices
            }.getOrNull() ?: return emptyList()
            val brut = if (entree) liste.recordingDevicesList else liste.playoutDevicesList
            // the windows adm's first two entries are aliases, "default device" and "default
            // communications device", and choosing an alias is precisely letting windows decide. we
            // only offer real devices
            return brut.filter { it.guid.isNotBlank() && !it.name.startsWith("Default ", true) }
                .map { Peripherique(it.guid, it.name) }
                .distinctBy { it.guid }
        } finally {
            if (propre) LiveKitFfi.liberer(h)
        }
    }

    /** the mic and output picked in the settings, found again among webrtc's */
    private fun choisirPeripheriques() {
        val liste = runCatching {
            LiveKitFfi.requete(
                Ffi.FfiRequest.newBuilder().setGetAudioDevices(
                    AudioFrame.GetAudioDevicesRequest.newBuilder().setPlatformAudioHandle(audio),
                ).build(),
            ).getAudioDevices
        }.getOrNull() ?: return
        // by guid first. it is the identifier the adm gave and that we kept when it was clicked: it
        // does not get truncated, translated, or changed when windows renames a device. the name is
        // now only a rescue for a setting written by an earlier version, which kept nothing else
        fun trouve(guid: String?, nom: String?, parmi: List<AudioFrame.AudioDeviceInfo>) =
            guid?.takeIf { it.isNotBlank() }?.let { g -> parmi.firstOrNull { it.guid == g } }
                ?: nom?.takeIf { it.isNotBlank() }?.let { v ->
                    parmi.firstOrNull { it.name.equals(v, true) }
                        ?: parmi.firstOrNull { it.name.startsWith(v, true) || v.startsWith(it.name, true) }
                }
        trouve(Voix.entree, Voix.entreeNom, liste.recordingDevicesList)?.let { d ->
            runCatching {
                LiveKitFfi.requete(
                    Ffi.FfiRequest.newBuilder().setSetRecordingDevice(
                        AudioFrame.SetRecordingDeviceRequest.newBuilder().setPlatformAudioHandle(audio).setDeviceId(d.guid),
                    ).build(),
                )
            }
        }
        trouve(Voix.sortie, Voix.sortieNom, liste.playoutDevicesList)?.let { d ->
            runCatching {
                LiveKitFfi.requete(
                    Ffi.FfiRequest.newBuilder().setSetPlayoutDevice(
                        AudioFrame.SetPlayoutDeviceRequest.newBuilder().setPlatformAudioHandle(audio).setDeviceId(d.guid),
                    ).build(),
                )
            }
        }
    }

    private fun surEvenement(e: Ffi.FfiEvent) {
        if (e.messageCase != Ffi.FfiEvent.MessageCase.ROOM_EVENT || e.roomEvent.roomHandle != salle) return
        val r = e.roomEvent
        when (r.messageCase) {
            Room.RoomEvent.MessageCase.TRACK_PUBLISHED -> {
                val p = r.trackPublished.publication.info
                if (p.kind == Track.TrackKind.KIND_VIDEO) {
                    ecrans[p.sid] = p.source == Track.TrackSource.SOURCE_SCREENSHARE
                } else if (p.kind == Track.TrackKind.KIND_AUDIO) {
                    micros[p.sid] = r.trackPublished.participantIdentity
                    micro(r.trackPublished.participantIdentity, p.muted)
                }
            }
            Room.RoomEvent.MessageCase.PARTICIPANT_CONNECTED -> {
                val p = personne(r.participantConnected.info.info.identity)
                etat = etat.copy(personnes = (etat.personnes.filter { it.identite != p.identite } + p))
                // somebody else arriving or leaving is heard, like on the reference. only events
                // after we connected come through here, so the people already present stay silent
                Sons.vocalEntree()
                // the newcomer cannot decrypt me without my key
                envoyerMaCle(listOf(p.identite))
            }
            Room.RoomEvent.MessageCase.PARTICIPANT_DISCONNECTED -> {
                etat = etat.copy(personnes = etat.personnes.filter { it.identite != r.participantDisconnected.participantIdentity })
                Sons.vocalSortie()
            }
            // a sender's encryption state, the one way to know from a log that frames decrypt
            Room.RoomEvent.MessageCase.E2EE_STATE_CHANGED ->
                System.err.println("[Appel] e2ee ${r.e2EeStateChanged.participantIdentity} : ${r.e2EeStateChanged.state}")
            Room.RoomEvent.MessageCase.TRACK_MUTED ->
                micros[r.trackMuted.trackSid]?.let { micro(it, true) }
            Room.RoomEvent.MessageCase.TRACK_UNMUTED ->
                micros[r.trackUnmuted.trackSid]?.let { micro(it, false) }
            Room.RoomEvent.MessageCase.CONNECTION_QUALITY_CHANGED -> {
                val q = r.connectionQualityChanged
                if (q.participantIdentity.startsWith("${moi ?: " "}:") || q.participantIdentity == moi) {
                    etat = etat.copy(
                        qualite = when (q.quality) {
                            Room.ConnectionQuality.QUALITY_EXCELLENT -> Qualite.EXCELLENTE
                            Room.ConnectionQuality.QUALITY_GOOD -> Qualite.BONNE
                            Room.ConnectionQuality.QUALITY_POOR -> Qualite.FAIBLE
                            else -> Qualite.PERDUE
                        },
                    )
                }
            }
            Room.RoomEvent.MessageCase.TRACK_SUBSCRIBED -> {
                val t = r.trackSubscribed.track
                if (t.info.kind == Track.TrackKind.KIND_AUDIO) {
                    pistesRecues[t.info.sid] = t.handle.id
                    // the same identity may come back with a new track: a local mute follows it
                    micros.putIfAbsent(t.info.sid, r.trackSubscribed.participantIdentity)
                    activerRecue(t.info.sid, t.handle.id)
                } else {
                    // the video used to be released on the spot, so a camera or a screen share
                    // coming from the phone arrived nowhere on the desktop. the handle is now kept
                    // by `VideoBureau`, which opens a stream on it
                    //
                    // the source is on the publication and not on the track: `TrackInfo` carries
                    // neither camera nor screen. it is recorded at publication time, which always
                    // precedes the subscription
                    auteurs[t.info.sid] = r.trackSubscribed.participantIdentity
                    // failing that, a camera. somebody already in the room when we arrived
                    // published before we were listening, and their track would otherwise have no
                    // entry, so would appear nowhere although we are receiving it
                    ecrans.putIfAbsent(t.info.sid, false)
                    VideoBureau.suivre(t.info.sid, t.handle.id)
                    etat = etat.copy(videos = videosCourantes())
                }
            }
            Room.RoomEvent.MessageCase.TRACK_UNSUBSCRIBED -> {
                val sid = r.trackUnsubscribed.trackSid
                pistesRecues.remove(sid)?.let { LiveKitFfi.liberer(it) }
                if (ecrans.remove(sid) != null) {
                    auteurs.remove(sid)
                    VideoBureau.oublier(sid)
                    etat = etat.copy(videos = videosCourantes())
                }
            }
            Room.RoomEvent.MessageCase.ACTIVE_SPEAKERS_CHANGED -> {
                val parlent = r.activeSpeakersChanged.participantIdentitiesList.toSet()
                etat = etat.copy(
                    personnes = etat.personnes.map { it.copy(parle = it.identite in parlent) },
                    moiParle = parlent.any { it.startsWith("${moi ?: " "}:") || it == moi },
                )
            }
            Room.RoomEvent.MessageCase.DISCONNECTED -> {
                nettoyer(etat.salonId)
                rendreLEcran()
                etat = Etat()
            }
            else -> Unit
        }
    }

    private fun micro(identite: String, coupe: Boolean) {
        etat = etat.copy(personnes = etat.personnes.map { if (it.identite == identite) it.copy(micCoupe = coupe) else it })
    }

    /** a livekit identity is `@person:server:DEVICE`, see `lk-jwt-service`'s logs */
    private fun personne(identite: String): Personne {
        val morceaux = identite.split(':')
        val userId = if (morceaux.size >= 3) morceaux.dropLast(1).joinToString(":") else identite
        return Personne(identite, userId)
    }

    /**
     * [salonQuitte] is passed and not read from [etat], because the caller may already have
     * cleared it. that is exactly the fault this parameter exists for
     */
    private fun nettoyer(salonQuitte: String?) {
        battement?.cancel()
        battement = null
        surveillance?.cancel()
        surveillance = null
        empreintePeripheriques = null
        arreterEcoute?.invoke()
        arreterEcoute = null
        arreterVideo?.invoke()
        arreterVideo = null
        // before releasing the room: the sharing holds a track published on it
        PartageEcranBureau.arreter()
        VideoBureau.toutOublier()
        ecrans.clear()
        micros.clear()
        jobCles?.cancel()
        jobCles = null
        maCle = null
        repondus.clear()
        identiteLocale = ""
        auteurs.clear()
        pistesRecues.values.forEach { LiveKitFfi.liberer(it) }
        pistesRecues.clear()
        listOf(piste, source, salle, audio).forEach { LiveKitFfi.liberer(it) }
        piste = 0; source = 0; salle = 0; audio = 0; localParticipant = 0
        val m = moteur
        val s = salonQuitte
        if (m != null && s != null) portee.launch {
            runCatching { m.quitterVocal(s) }
            val restants = runCatching { m.participantsVocal(s) }.getOrNull()?.filter { it != moi }
            if (restants != null && restants.isEmpty()) runCatching { m.raccrocherSonnerie(s) }
        }
        moteur = null
    }
}

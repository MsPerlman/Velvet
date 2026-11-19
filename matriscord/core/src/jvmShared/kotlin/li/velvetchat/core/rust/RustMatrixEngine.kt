package li.velvetchat.core.rust

import li.velvetchat.core.Installation

import li.velvetchat.core.tr

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.booleanOrNull
import li.velvetchat.core.essayer
import org.matrix.rustcomponents.sdk.AllowRule
import org.matrix.rustcomponents.sdk.AudioInfo
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.ClientSessionDelegate
import org.matrix.rustcomponents.sdk.OAuthConfiguration
import org.matrix.rustcomponents.sdk.OAuthPrompt
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.EditedContent
import org.matrix.rustcomponents.sdk.EmbeddedEventDetails
import org.matrix.rustcomponents.sdk.EncryptedMessage
import org.matrix.rustcomponents.sdk.EventOrTransactionId
import org.matrix.rustcomponents.sdk.EventSendState
import org.matrix.rustcomponents.sdk.SendHandle
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.FileInfo
import org.matrix.rustcomponents.sdk.ImageInfo
import org.matrix.rustcomponents.sdk.InReplyToDetails
import org.matrix.rustcomponents.sdk.JoinRule
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.Mentions
import org.matrix.rustcomponents.sdk.messageEventContentFromHtml
import org.matrix.rustcomponents.sdk.MembershipChange
import org.matrix.rustcomponents.sdk.MembershipState
import org.matrix.rustcomponents.sdk.MessageContent
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.OtherState
import org.matrix.rustcomponents.sdk.PollKind
import org.matrix.rustcomponents.sdk.PowerLevel
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.QueueWedgeError
import org.matrix.rustcomponents.sdk.RecoveryStateListener
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind
import org.matrix.rustcomponents.sdk.RoomListEntriesListener
import org.matrix.rustcomponents.sdk.RoomListEntriesUpdate
import org.matrix.rustcomponents.sdk.RoomListServiceState
import org.matrix.rustcomponents.sdk.RoomListServiceStateListener
import org.matrix.rustcomponents.sdk.PowerLevels
import org.matrix.rustcomponents.sdk.RoomHistoryVisibility
import org.matrix.rustcomponents.sdk.RoomPreset
import org.matrix.rustcomponents.sdk.RoomType
import org.matrix.rustcomponents.sdk.RoomVisibility
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import org.matrix.rustcomponents.sdk.SpaceRoom
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.SyncServiceState
import org.matrix.rustcomponents.sdk.SyncServiceStateObserver
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineFocus
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.UploadParameters
import org.matrix.rustcomponents.sdk.UploadSource
import org.matrix.rustcomponents.sdk.VideoInfo
import org.matrix.rustcomponents.sdk.VerificationStateListener
import org.matrix.rustcomponents.sdk.VirtualTimelineItem
import org.matrix.rustcomponents.sdk.initPlatform
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown
import li.velvetchat.core.AccessState
import li.velvetchat.core.ConnectionState
import li.velvetchat.core.EngineException
import li.velvetchat.core.EngineLogLevel
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.NotificationEngine
import li.velvetchat.core.ReadVisibility
import li.velvetchat.core.RecoveryState
import li.velvetchat.core.RecoveryEngine
import li.velvetchat.core.VerificationEngine
import li.velvetchat.core.VerificationState
import li.velvetchat.core.media.MediaProbe
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.Citation
import li.velvetchat.core.model.avecCitations
import li.velvetchat.core.model.enApercu
import li.velvetchat.core.model.texteSansBalises
import li.velvetchat.core.model.ChannelKind
import li.velvetchat.core.model.EncryptionState
import li.velvetchat.core.model.MediaKind
import li.velvetchat.core.model.MediaRef
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import li.velvetchat.core.model.InfoVocal
import li.velvetchat.core.model.CustomEmote
import li.velvetchat.core.model.EmotePack
import li.velvetchat.core.model.Fil
import li.velvetchat.core.model.DirectMessage
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.ModeNotification
import li.velvetchat.core.model.CompteServeur
import li.velvetchat.core.model.JetonInvitation
import li.velvetchat.core.model.ModerationResult
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.PollOption
import li.velvetchat.core.model.ProfilEtendu
import li.velvetchat.core.model.Profile
import li.velvetchat.core.model.Statut
import li.velvetchat.core.model.presenceDepuisProtocole
import li.velvetchat.core.model.versProtocole
import li.velvetchat.core.model.Release
import li.velvetchat.core.model.RoleTier
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.Reaction
import li.velvetchat.core.model.Role
import li.velvetchat.core.model.ApercuLien
import li.velvetchat.core.model.SendState
import li.velvetchat.core.model.Server
import li.velvetchat.core.model.ServerJoin
import li.velvetchat.core.model.SystemKind
import li.velvetchat.core.model.UndecryptableReason
import uniffi.matrix_sdk_crypto.UtdCause
import li.velvetchat.core.model.Unread
import li.velvetchat.core.model.Session as CoreSession
import org.matrix.rustcomponents.sdk.Session as SdkSession
import org.matrix.rustcomponents.sdk.TimelineItem as SdkTimelineItem
import uniffi.matrix_sdk_base.EncryptionState as SdkEncryptionState
import org.matrix.rustcomponents.sdk.VerificationState as SdkVerificationState
import org.matrix.rustcomponents.sdk.RecoveryState as SdkRecoveryState

/**
 * the engine, implemented on top of `matrix-rust-sdk`
 *
 * this file compiles for android and for the desktop from one source. that works because the
 * bindings on both targets expose an identical api, which is only true as long as the native
 * lib is built with the same set of cargo features on both sides. that condition is a project
 * invariant and not a build detail: break it and `core` stops compiling on one of the two
 *
 * seven thousand lines of it, which is what happens when one interface hides an entire sdk
 *
 * @param dataPath session directory, supplied by the platform
 * @param cachePath cache directory, supplied by the platform
 */
@OptIn(FlowPreview::class)
class RustMatrixEngine(
    private val dataPath: String,
    private val cachePath: String,
    private val userAgent: String = "Velvet/1.0",
    /**
     * how loud we ask the sdk to be
     *
     * a limit worth knowing: the sdk also applies its own hardcoded per target filter
     * (`matrix_sdk::http_client=debug`, `matrix_sdk::client=trace`). lowering this level does
     * not make it quiet, it keeps writing a line per http request. for readable output, use
     * [logToStdout]
     *
     * those logs carry room, event and device ids. never an access token, but enough to treat
     * as sensitive
     */
    private val logLevel: EngineLogLevel = EngineLogLevel.WARN,
    /**
     * on android this is what feeds logcat, so leave it true. on desktop turn it off for any
     * tool whose output has to stay readable, see the limit above
     */
    private val logToStdout: Boolean = true,
    /**
     * turns tls verification off. never in production, never by default
     *
     * it exists for one measured reason: on some android environments the sdk's verifier
     * (`rustls-platform-verifier`) rejects with `InvalidCertificate(Revoked)` a certificate the
     * android trust store itself accepts. checked by comparing the two in the same process,
     * with `HttpsURLConnection` returning http 200 on the same url. so it is neither the device
     * nor the server, it is the verifier's revocation step
     *
     * this flag unblocks development on those environments. it fixes nothing, and the behaviour
     * has to be retested on a physical device before drawing any general conclusion
     */
    private val insecureTlsForDevelopment: Boolean = false,
    /**
     * trust anchors to use instead of the platform verifier
     *
     * why this exists: on android the sdk's verifier (`rustls-platform-verifier`) delegates to
     * `CertPathValidator` with revocation checking, and translates every
     * `CertPathValidatorException` into "revoked", including "certificate does not specify ocsp
     * responder". let's encrypt stopped publishing an ocsp responder, so its certificates get
     * rejected while android itself accepts them, because android's default validation does not
     * check revocation
     *
     * supplying anchors makes `reqwest` switch to rustls's webpki validator
     * (`tls_certs_only`). the validation stays complete, chain and hostname and expiry, minus
     * the broken revocation step
     *
     * passing the system store's anchors here keeps exactly the platform's trust decisions. it
     * is not a weakening of the trust model, only a way round a check that does not work
     *
     * the accepted cost: no revocation checking at all. in practice it was already largely
     * vestigial, since android does not do it by default and the major authorities stopped
     * publishing ocsp
     */
    private val trustAnchors: List<ByteArray> = emptyList(),
    /**
     * the homeserver's lan url, to be used when it answers
     *
     * empty, the default, means no switching at all and we stay on the public url. otherwise, on
     * the process's first `buildClient`, we probe `<localHomeserverUrl>/_matrix/client/versions`
     * with a short timeout. if it answers, every connection goes over the lan, which is faster and
     * keeps working with cloudflare or the internet down. if it does not, we keep the public
     * domain
     * decided once per launch. the rust sdk `Client` freezes its url at construction, so there
     * is no hot switching, and changing network mid session means reopening it
     *
     * the lan url is never persisted into the session, see [canonicalHomeserverUrl]. a session
     * saved with the local ip would fail to restore anywhere but the house
     */
    private val localHomeserverUrl: String = "",
    /**
     * the alias of the global cosmetics catalogue room, or empty
     *
     * an alias and not an id, because an alias reads, sticks in your head and can be recreated
     * if the room is lost. a `!abcd:server` is unreadable and does not survive a rebuild
     *
     * empty by default, meaning no global catalogue and the app makes do with the servers' own.
     * that is the intended degradation: nothing breaks, there is just less to pick from
     */
    private val aliasCatalogue: String? = null,
    /**
     * the one account that walks into the global catalogue by itself
     *
     * reading a matrix room's state means being a member of it, since peeking is not deployed,
     * so opening the shop used to walk every account into the catalogue room on its first
     * launch. nobody asked for that, and the room only concerns whoever stocks it. three accounts
     * ended up in there, none of them on purpose
     *
     * empty by default, meaning nobody walks in and the picker makes do with each server's
     * catalogue. that is what a public copy of this code looks like and it stays whole: less to
     * pick from, nothing broken. the value comes from `prive.properties`, outside the repo
     *
     * the real lock is server side, the room is invite only. this field avoids a doomed join
     * attempt every time the shop opens, it does not pretend to protect anything, since a
     * client recompiles
     */
    private val intendantCatalogue: String? = null,

    /**
     * the address of the voice token service (`lk-jwt-service`), or empty when there is none
     *
     * it used to be a `const` in this file, pointing at one particular household's sfu. two
     * things wrong with that, and the second is the one that matters. an app built by somebody
     * else asked **our** server for its voice tokens, so voice could not work for them and our
     * token service answered strangers. and a hostname is a name: hardcoding it published whose
     * installation this is, in every copy of the source
     *
     * empty is a supported state, not a failure: the rest of the app works and voice reports
     * that it is not configured, the same way firebase being absent leaves the foreground
     * service in place. the server's own answer wins when it gives one, see [Installation]: a
     * homeserver that runs matrixrtc advertises its sfu in `.well-known/matrix/client`, under
     * `org.matrix.msc4143.rtc_foci`, which is how a build without any private config (the one on
     * the site) still finds its server's voice
     */
    private val urlJetonVocal: String = "",
) : MatrixEngine {

    private var client: Client? = null
    private var syncService: SyncService? = null

    /**
     * the session's public url, the one to persist, as distinct from the url actually used to
     * connect, which may be the lan. set by `login`, `register` and `restore`, read back by
     * [toCore] so the local ip never leaks into a saved session
     */
    @Volatile
    private var canonicalHomeserverUrl: String? = null

    /** who saves a refreshed session, see [surSessionRenouvelee] */
    @Volatile
    private var rappelSession: ((CoreSession) -> Unit)? = null

    /** the last session the sdk handed over, which is what it asks back for */
    @Volatile
    private var derniereSessionSdk: SdkSession? = null

    /**
     * the sdk refreshes oauth tokens on its own and reports here. the session goes on to the
     * platform, which writes it to disk: a refresh token is single use, and keeping the old one
     * would mean a dead session on the next launch
     */
    private val delegueSession = object : ClientSessionDelegate {
        override fun retrieveSessionFromKeychain(userId: String): SdkSession =
            derniereSessionSdk ?: client?.session()
                ?: throw IllegalStateException("aucune session pour $userId")

        override fun saveSessionInKeychain(session: SdkSession) {
            derniereSessionSdk = session
            runCatching { rappelSession?.invoke(session.toCore()) }
                .onFailure { erreurNonBloquante("Session renouvelée non enregistrée", it) }
        }
    }

    /** the authentication service's address (`issuer`), read once per session */
    @Volatile
    private var emetteurMas: String? = null

    /** does the lan homeserver answer. decided once per launch */
    @Volatile
    private var lanReachable: Boolean? = null

    private val connection = MutableStateFlow(ConnectionState.STOPPED)
    private var stateHandle: TaskHandle? = null
    private var restartJob: Job? = null
    private var restartAttempt = 0

    /**
     * tells a requested stop from an interruption we suffered, without which the supervisor
     * would conscientiously restart whatever the caller just stopped
     */
    @Volatile
    private var stopRequested = false

    /**
     * supervision only starts once `startSync` has returned
     *
     * without this guard, the `IDLE` state the service carries before it starts would trigger a
     * restart competing with the one already under way
     */
    @Volatile
    private var supervising = false

    /**
     * holds the restarts. `SupervisorJob` so a failed attempt does not take the next ones with
     * it, which is precisely what you want from a retry mechanism
     */
    private val supervisor = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * the current account's id, remembered when the session opens
     *
     * used to recognise your own reactions in the timeline. remembered rather than re read,
     * because `toMessage()` runs once per message and on every diff, and `userId()` crosses the
     * ffi boundary
     */
    private var ownUserId: String? = null

    override val recovery: RecoveryEngine = RustRecoveryEngine { requireClient() }

    override val verification: VerificationEngine = RustVerificationEngine { requireClient() }

    override val notifications: NotificationEngine =
        RustNotificationEngine({ requireClient() }, { syncService })

    override fun initPlatform() {
        if (platformReady) return
        synchronized(LOCK) {
            if (platformReady) return
            try {
                initPlatformOnce()
            } catch (t: Throwable) {
                // "a global default trace dispatcher has already been set" means somebody else
                // initialised the sdk in this process
                //
                // our flag cannot know that, it only covers calls going through the engine. so
                // an app using the sdk elsewhere, or a third party library doing it, would make
                // the engine unusable over a message that does not say there was nothing to do
                //
                // the tokio runtime and the tls verification are in place either way, only the
                // log configuration is the other caller's, which changes nothing functionally
                if (t.message?.contains("already been set") != true) throw t
            }
            platformReady = true
        }
    }

    private fun initPlatformOnce() {
        initPlatform(
                config = TracingConfiguration(
                    logLevel = when (logLevel) {
                        EngineLogLevel.ERROR -> LogLevel.ERROR
                        EngineLogLevel.WARN -> LogLevel.WARN
                        EngineLogLevel.INFO -> LogLevel.INFO
                        EngineLogLevel.DEBUG -> LogLevel.DEBUG
                        EngineLogLevel.TRACE -> LogLevel.TRACE
                    },
                    traceLogPacks = emptyList(),
                    extraTargets = emptyList(),
                    writeToStdoutOrSystem = logToStdout,
                    writeToFiles = null,
                    sentryConfig = null,
                ),
                useLightweightTokioRuntime = false,
            )
    }

    override suspend fun login(
        homeserverUrl: String,
        username: String,
        password: String,
    ): CoreSession {
        canonicalHomeserverUrl = homeserverUrl
        val c = buildClient(homeserverUrl)
        try {
            c.login(username, password, userAgent, null)
        } catch (t: Throwable) {
            throw EngineException.AuthFailed(raisonDuRefus(t), t)
        }
        client = c
        ownUserId = c.userId()
        Installation.decouvrir(dataPath, c.userId(), attendre = true)
        lireConfigPrivee()
        return c.session().toCore()
    }

    override fun surSessionRenouvelee(rappel: (CoreSession) -> Unit) {
        rappelSession = rappel
    }

    override suspend fun connexionParNavigateur(homeserverUrl: String): Boolean =
        metadonneesAuth(connectBase(homeserverUrl)) != null

    /**
     * `auth_metadata` (msc2965), or null when the server has no authentication service. read by
     * hand rather than by building a client: a client opens the store on disk, and the login
     * screen asks this while the address is still being typed
     */
    private suspend fun metadonneesAuth(base: String): kotlinx.serialization.json.JsonObject? =
        runCatching {
            val r = requeteNonAuthentifiee(base.trimEnd('/'), "GET", "/_matrix/client/v1/auth_metadata", null)
            if (r.code != 200) return@runCatching null
            (kotlinx.serialization.json.Json.parseToJsonElement(r.corps) as? kotlinx.serialization.json.JsonObject)
                ?.takeIf { it["issuer"] != null }
        }.getOrNull()

    private fun configurationOauth(retour: String) = OAuthConfiguration(
        clientName = "Velvet",
        redirectUri = retour,
        // MAS only accepts a custom scheme that is this host reversed (`li.velvet-chat:/…`), so
        // the two move together
        clientUri = CLIENT_URI,
        logoUri = null,
        tosUri = null,
        policyUri = null,
        staticRegistrations = emptyMap(),
    )

    /**
     * one browser round trip on [c]. the sdk keeps the pkce state between the url and the
     * callback; a failed or abandoned trip is aborted, otherwise the next one is refused
     */
    private suspend fun allerRetourNavigateur(
        c: Client,
        retour: String,
        prompt: OAuthPrompt?,
        deviceId: String?,
        portees: List<String>,
        ouvrir: suspend (String) -> String,
    ) {
        val donnees = c.urlForOauth(configurationOauth(retour), prompt, null, deviceId, portees)
        try {
            c.loginWithOauthCallback(ouvrir(donnees.loginUrl()))
        } catch (t: Throwable) {
            runCatching { c.abortOauthAuth(donnees) }
            if (t is kotlinx.coroutines.CancellationException) throw t
            throw EngineException.AuthFailed(raisonDuRefus(t), t)
        }
    }

    override suspend fun loginNavigateur(
        homeserverUrl: String,
        retour: String,
        creation: Boolean,
        ouvrir: suspend (String) -> String,
    ): CoreSession {
        canonicalHomeserverUrl = homeserverUrl
        val c = buildClient(homeserverUrl)
        allerRetourNavigateur(
            c, retour, if (creation) OAuthPrompt.Create else null, null,
            listOf(PORTEE_GRAPHQL), ouvrir,
        )
        client = c
        ownUserId = c.userId()
        Installation.decouvrir(dataPath, c.userId(), attendre = true)
        lireConfigPrivee()
        return c.session().toCore()
    }

    override suspend fun peutDemanderAdmin(): Boolean = runCatching {
        if (currentSession().oauthData == null) return@runCatching false
        val r = requeteSignee(
            "POST", "graphql",
            """{"query":"{ viewer { ... on User { canRequestAdmin } } }"}""",
            base = emetteur(),
        )
        r.code == 200 && "\"canRequestAdmin\":true" in r.corps.replace(" ", "")
    }.getOrDefault(false)

    /**
     * the same client, the same device: the sdk accepts a second login for the user it already
     * holds and only swaps the tokens. a new device would mean new keys and a verification all
     * over again, for a gesture that is only about rights
     */
    override suspend fun activerAdministration(
        retour: String,
        ouvrir: suspend (String) -> String,
    ): CoreSession {
        val c = requireClient()
        allerRetourNavigateur(
            c, retour, OAuthPrompt.Consent, c.session().deviceId,
            listOf(PORTEE_GRAPHQL, PORTEE_ADMIN_SYNAPSE, PORTEE_ADMIN_MAS), ouvrir,
        )
        return c.session().toCore()
    }

    /** the service's base url, ending with a slash */
    private suspend fun emetteur(): String = emetteurMas ?: run {
        val meta = metadonneesAuth(connectBase(currentSession().homeserverUrl))
            ?: throw EngineException.Unexpected(tr("Ce serveur n'a pas de service d'authentification", "This server has no authentication service"))
        ((meta["issuer"] as kotlinx.serialization.json.JsonPrimitive).content.trimEnd('/') + "/")
            .also { emetteurMas = it }
    }

    /** under MAS the accounts and the invite codes are its business, not synapse's */
    private fun sousMas(): Boolean = runCatching { currentSession().oauthData != null }.getOrDefault(false)

    override suspend fun register(
        homeserverUrl: String,
        username: String,
        password: String,
        registrationToken: String,
    ): CoreSession {
        canonicalHomeserverUrl = homeserverUrl
        // the sdk exposes no `register()`. matrix-rust-sdk assumes an account already exists,
        // so we do the interactive auth dance by hand and then hand the result to the sdk
        // through `restoreSession`, which is exactly the path `restore()` already takes for a
        // session read off disk
        val base = homeserverUrl.trimEnd('/')

        fun corpsInscription(auth: kotlinx.serialization.json.JsonObject?) =
            kotlinx.serialization.json.buildJsonObject {
                put("username", username)
                put("password", password)
                put("initial_device_display_name", userAgent)
                auth?.let { put("auth", it) }
            }.toString()

        // first call, with no `auth`. this is not a doomed attempt, it is how interactive
        // registration always starts: the server answers 401 and opens a uia session listing
        // what it wants. we read it rather than guess it
        var reponseFinale = requeteNonAuthentifiee(
            base, "POST", "/_matrix/client/v3/register", corpsInscription(null),
        )

        // a loop and not a single token post, because the server decides how many stages
        // there are
        //
        // the first version posted the token and expected success. against the real server the
        // announced flow is `["m.login.registration_token", "m.login.dummy"]`: after the token
        // synapse answers 401 *again*, with `completed: ["m.login.registration_token"]`, and
        // waits for the dummy stage. that second 401 read as a refusal, so registration could
        // never complete. now we walk whatever stages the server announces, while there are
        // some left and we know how to supply them
        var idSession: String? = null
        var derniereTentee: String? = null
        var faitesPrecedentes: Set<String> = emptySet()

        while (reponseFinale.code == 401) {
            val racine = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(reponseFinale.corps)
                    as? kotlinx.serialization.json.JsonObject
            }.getOrNull() ?: break

            idSession = (racine["session"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?: idSession ?: break

            val faites = (racine["completed"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                ?.toSet().orEmpty()

            // no progress since the last attempt, so stop. that is what happens when the
            // token is refused, the server asks for the same stage forever. without this guard
            // we would send it back in a loop instead of saying what is wrong
            if (derniereTentee != null && faites == faitesPrecedentes) break

            val flux = (racine["flows"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { element ->
                    ((element as? kotlinx.serialization.json.JsonObject)
                        ?.get("stages") as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                }.orEmpty()

            // we only commit to a flow whose stages we all know. starting one we cannot
            // finish would leave someone at a halfway failure with nothing to fix
            val parcours = flux.firstOrNull { etapes -> etapes.all { it in ETAPES_INSCRIPTION } }
                ?: throw EngineException.AuthFailed(
                    tr("Ce serveur demande une étape d'inscription que Velvet ne sait pas fournir ", "This server asks for a sign-up step Velvet cannot provide ") +
                        tr("(par exemple un captcha ou une adresse e-mail). Créez le compte depuis ", "(a captcha or an email address, for instance). Create the account from ") +
                        tr("un autre client Matrix, puis connectez-vous ici.", "another Matrix client, then sign in here."),
                    null,
                )

            val suivante = parcours.firstOrNull { it !in faites } ?: break
            derniereTentee = suivante
            faitesPrecedentes = faites

            val auth = kotlinx.serialization.json.buildJsonObject {
                put("type", suivante)
                if (suivante == ETAPE_JETON) put("token", registrationToken)
                put("session", idSession)
            }
            reponseFinale = requeteNonAuthentifiee(
                base, "POST", "/_matrix/client/v3/register", corpsInscription(auth),
            )
        }

        if (reponseFinale.code !in 200..299) {
            throw EngineException.AuthFailed(interpreterEchecInscription(reponseFinale), null)
        }

        val corpsReponse = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(reponseFinale.corps)
                as? kotlinx.serialization.json.JsonObject
        }.getOrNull() ?: throw EngineException.Unexpected(tr("Réponse d'inscription illisible", "Unreadable sign-up response"))

        fun champ(cle: String) =
            (corpsReponse[cle] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

        val session = CoreSession(
            userId = champ("user_id") ?: throw EngineException.Unexpected("user_id absent"),
            deviceId = champ("device_id") ?: throw EngineException.Unexpected("device_id absent"),
            homeserverUrl = homeserverUrl,
            accessToken = champ("access_token") ?: throw EngineException.Unexpected("access_token absent"),
            refreshToken = null,
            slidingSyncNative = true,
        )

        // same move as `restore()`: build the client, hand it the session we just got. the
        // account already exists server side at this point, this only attaches the sdk to the
        // session
        val c = buildClient(homeserverUrl)
        try {
            c.restoreSession(session.toSdk())
        } catch (t: Throwable) {
            throw EngineException.AuthFailed(tr("Session invalide après inscription", "Invalid session after sign-up"), t)
        }
        client = c
        ownUserId = c.userId()
        Installation.decouvrir(dataPath, c.userId(), attendre = true)
        lireConfigPrivee()
        return session
    }

    /**
     * reads what synapse says about a failed registration rather than showing a bare http code
     *
     * three causes cover nearly every real case, and telling them apart matters: confusing
     * "username taken" with "invalid invite code" sends someone to fix the wrong line of the
     * form
     */
    private fun interpreterEchecInscription(reponse: Reponse): String {
        val racine = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                as? kotlinx.serialization.json.JsonObject
        }.getOrNull()
        val code = (racine?.get("errcode") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        return when (code) {
            "M_USER_IN_USE" -> tr("Cet identifiant est déjà pris.", "That username is already taken.")
            "M_INVALID_USERNAME" -> tr("Identifiant invalide : lettres minuscules, chiffres, « _ », « - » ou « . ».", "Invalid username: lowercase letters, digits, \"_\", \"-\" or \".\".")
            "M_WEAK_PASSWORD" -> tr("Mot de passe trop simple.", "Password too simple.")
            "M_FORBIDDEN", "M_UNAUTHORIZED" ->
                tr("Code d'invitation invalide, expiré, ou déjà entièrement utilisé.", "Invite code invalid, expired, or already used up.")
            else -> {
                val message = (racine?.get("error") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                tr("Inscription refusée (${reponse.code})", "Sign-up refused (${reponse.code})") + (message?.let { " : $it" } ?: "")
            }
        }
    }

    /**
     * an http call with no access token, the one case `requeteSignee` cannot serve: it needs a
     * session already open, and registration happens precisely before there is one
     */
    private suspend fun requeteNonAuthentifiee(
        base: String,
        methode: String,
        chemin: String,
        corps: String?,
    ): Reponse = withContext(Dispatchers.IO) {
        val cnx = (java.net.URI.create(base + chemin).toURL().openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = methode
            if (corps != null) {
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            corps?.let { c -> cnx.outputStream.use { it.write(c.toByteArray()) } }
            val code = cnx.responseCode
            val texte = (if (code in 200..299) cnx.inputStream else cnx.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            Reponse(code, texte)
        } finally {
            cnx.disconnect()
        }
    }

    override suspend fun restore(session: CoreSession) {
        // before the client: the lan address may come from the server's config
        Installation.decouvrir(dataPath, session.userId, attendre = false)
        canonicalHomeserverUrl = session.homeserverUrl
        val c = buildClient(session.homeserverUrl)
        try {
            c.restoreSession(session.toSdk())
        } catch (t: Throwable) {
            throw EngineException.AuthFailed(tr("Session invalide", "Invalid session"), t)
        }
        client = c
        ownUserId = session.userId
        supervisor.launch { lireConfigPrivee() }
    }

    /**
     * the members-only half of the installation's config, see [Installation.completer]
     *
     * joined on every start, which costs nothing when already in, and is how an account made
     * before the room existed ends up in it. the room is not a space and not a dm, so it shows
     * nowhere in the app
     */
    @Volatile private var idSalonInstallation: String? = null

    override suspend fun salonInstallation(): String? = idSalonInstallation ?: run {
        lireConfigPrivee()
        idSalonInstallation
    }

    private suspend fun lireConfigPrivee() {
        // after a restore the public half may still be on its way
        val alias = kotlinx.coroutines.withTimeoutOrNull(15_000) {
            Installation.config.first { it.salon.isNotBlank() }
        }?.salon ?: return
        runCatching {
            val code = java.net.URLEncoder.encode(alias, "UTF-8")
            val entree = requeteSignee("POST", "/_matrix/client/v3/join/$code", "{}")
            check(entree.code == 200) { "join ${entree.code} ${entree.corps.take(200)}" }
            val id = kotlinx.serialization.json.Json.parseToJsonElement(entree.corps)
                .jsonObject["room_id"]?.jsonPrimitive?.contentOrNull ?: error("join sans room_id")
            idSalonInstallation = id
            val salon = java.net.URLEncoder.encode(id, "UTF-8")
            val etat = requeteSignee("GET", "/_matrix/client/v3/rooms/$salon/state/${Installation.TYPE_ETAT}/")
            if (etat.code == 200) Installation.completer(dataPath, etat.corps)
        }.onFailure { erreurNonBloquante("Configuration privée de l'installation non lue", it) }
    }

    override fun currentSession(): CoreSession =
        requireClient().session().toCore()

    override fun connectionFlow(): Flow<ConnectionState> = connection.asStateFlow()

    /**
     * access state, as a flow
     *
     * it actually listens to verification. the first version derived from the connection flow,
     * so it only re evaluated when the connection changed. someone would type their recovery
     * key, verification would succeed, and the screen would not move, for want of an event to
     * trigger it. a state that only re evaluates on the occasion of something else is not an
     * observable state
     *
     * three sources emit here, because three things can change access: the session opening, the
     * device being verified, and the recovery state, which decides whether the key is an option
     * worth offering
     */
    override fun accessFlow(): Flow<AccessState> = callbackFlow {
        trySend(Unit)

        // listening needs a client. before the session opens there is nothing to listen to,
        // and the state is `SignedOut` anyway
        val handles = runCatching {
            val c = requireClient()
            listOf(
                c.encryption().verificationStateListener(object : VerificationStateListener {
                    override fun onUpdate(status: SdkVerificationState) { trySend(Unit) }
                }),
                c.encryption().recoveryStateListener(object : RecoveryStateListener {
                    override fun onUpdate(status: SdkRecoveryState) { trySend(Unit) }
                }),
            )
        }.getOrDefault(emptyList())

        awaitClose { handles.forEach { runCatching { it.cancel() } } }
    }
        // the connection changes access too: a restored session goes from signed out to
        // something else without any crypto listener firing
        .combine(connection) { _, _ -> Unit }
        // the third wakeup, and the one that was missing
        //
        // neither crypto listener fires when the sdk bootstraps cross signing for a fresh
        // account by itself. the device was signed server side and the verification screen
        // stayed up regardless. `startSync` pushes here once
        // `waitForE2eeInitializationTasks` returns, which causes the re read nothing was
        // causing
        .combine(reveilAcces) { _, _ -> evaluerAcces() }
        .distinctUntilChanged()

    /** counter of forced `accessFlow` wakeups, see `startSync` */
    private val reveilAcces = MutableStateFlow(0)

    private suspend fun evaluerAcces(): AccessState {
        if (client == null) return AccessState.SignedOut
        return when (verification.verificationState()) {
            VerificationState.VERIFIED -> AccessState.Ready
            // `UNKNOWN` counts as unverified on purpose. between "i do not know" and "it is
            // fine", the safe choice is the one that asks. the worst case is a verification
            // screen shown when it need not be, not an unreadable history with no explanation
            VerificationState.UNVERIFIED, VerificationState.UNKNOWN ->
                AccessState.VerificationRequired(
                    canUseRecoveryKey = recovery.recoveryState() == RecoveryState.ENABLED,
                    // read here rather than by the screen. `evaluerAcces` is already where we
                    // decide what the session allows, and this is part of that
                    aUnAppareilTemoin = verification.aUnAppareilTemoin(),
                )
        }
    }

    override suspend fun startSync() {
        if (syncService != null) return
        val c = requireClient()

        stopRequested = false
        connection.value = ConnectionState.CONNECTING

        // `withOfflineMode` makes the sdk report a lost network as an offline state instead of
        // stopping on an error. that is what makes recovery possible without rebuilding the
        // service, so without losing the timelines or the listeners
        val service = c.syncService().withOfflineMode().finish()
        syncService = service

        // supervisor attached before the start. a failure during setup counts as much as one
        // later, and is in fact the likelier of the two
        stateHandle = service.state(object : SyncServiceStateObserver {
            override fun onUpdate(state: SyncServiceState) = onSyncState(state)
        })

        service.start()

        // wait for sync to be working rather than return immediately. without that the first
        // `servers()` call comes back empty and the ui shows "no servers" instead of a loading
        // state
        val ready = CompletableDeferred<Unit>()
        val handle = service.roomListService().state(object : RoomListServiceStateListener {
            override fun onUpdate(state: RoomListServiceState) {
                if (state == RoomListServiceState.RUNNING) ready.complete(Unit)
            }
        })
        // being offline is not a startup failure. the engine is in place and will retry on its
        // own, and throwing here would make the caller handle a case the engine already handles.
        // the ui has what it needs to say offline rather than error
        val offline = supervisor.launch {
            connection.first { it == ConnectionState.OFFLINE }
            ready.complete(Unit)
        }
        try {
            withTimeout(SYNC_TIMEOUT_MS) { ready.await() }
        } catch (_: TimeoutCancellationException) {
            throw EngineException.Unreachable(
                tr("Synchronisation non établie après ${SYNC_TIMEOUT_MS / 1000} s", "Sync not established after ${SYNC_TIMEOUT_MS / 1000} s")
            )
        } finally {
            handle.cancel()
            offline.cancel()
        }

        // wait for encryption to bootstrap, then re evaluate access
        //
        // `autoEnableCrossSigning` does its work in the background on a fresh account's first
        // start: keys published, device signed, all verifiable server side. but neither
        // `verificationStateListener` nor a re read of `verificationState()` reports it, and the
        // verification screen stayed up forever in front of a perfectly good account.
        // `waitForE2eeInitializationTasks` is the wait point the sdk provides for this, and once
        // it returns we force a re emission rather than hope for a
        // event that never comes
        //
        // launched alongside rather than awaited. on an older account there is nothing to
        // bootstrap and it returns at once, but nothing guarantees that stays true, and making
        // the app's opening depend on a crypto task would be one more block on the busiest path
        //
        // friend nicknames, read once at startup: the timeline applies them to names without
        // being able to go and fetch them itself, see `surnomsMemoire`
        supervisor.launch { runCatching { surnoms() } }

        supervisor.launch {
            runCatching { c.encryption().waitForE2eeInitializationTasks() }
                .onFailure { erreurNonBloquante("Amorçage du chiffrement", it) }
            reveilAcces.value = reveilAcces.value + 1
        }

        supervising = true
    }

    /**
     * translates the sdk's state and restarts by itself when interrupted
     *
     * the whole point: a sync that stops does not announce itself. the conversation simply
     * stops updating, which looks exactly like a quiet conversation. nobody restarts an app
     * that appears to be working
     */
    private fun onSyncState(state: SyncServiceState) {
        connection.value = when (state) {
            SyncServiceState.RUNNING -> ConnectionState.CONNECTED
            SyncServiceState.OFFLINE, SyncServiceState.ERROR -> ConnectionState.OFFLINE
            SyncServiceState.IDLE, SyncServiceState.TERMINATED ->
                if (stopRequested) ConnectionState.STOPPED else ConnectionState.CONNECTING
        }

        // a successful recovery resets the counter, otherwise an hour long outage would leave
        // the delay at its ceiling for the rest of the session
        if (state == SyncServiceState.RUNNING) {
            restartAttempt = 0
            // the rail order is re read on every recovery and not once at startup. it lives in
            // account data, so it changes when you tidy your rail on another device. reading it
            // once would leave this one frozen until the next restart, which is exactly the gap
            // that makes you doubt the tidying saved
            supervisor.launch { runCatching { chargerOrdreServeurs() } }
            return
        }

        // anything other than running calls for a restart, idle included
        //
        // the first version only recovered on error and terminated. testing caught it, because
        // an interrupted service falls back to `IDLE` and not to `TERMINATED`. the displayed
        // state was right, the recovery simply never happened: exactly the silent failure this
        // is supposed to remove
        if (!supervising || stopRequested) return
        scheduleRestart()
    }

    /**
     * schedules another start, spacing the attempts out
     *
     * the delay doubles on each failure up to a ceiling, and there is no last attempt. the
     * network comes back at a moment nobody picks. giving up would leave an app that looks like
     * it works and is permanently mute until someone restarts it by hand
     */
    private fun scheduleRestart() {
        if (restartJob?.isActive == true) return
        restartJob = supervisor.launch {
            val wait = minOf(RESTART_BASE_MS shl restartAttempt.coerceAtMost(6), RESTART_MAX_MS)
            restartAttempt++
            delay(wait)
            if (stopRequested) return@launch
            // failing here is normal. if it persists the sdk goes back to error and
            // reschedules. that is the loop, not an exception to surface
            runCatching { syncService?.start() }
        }
    }

    override suspend fun stopSync() {
        // set the flag first. without it, the stop we are asking for wakes the supervisor,
        // which immediately restarts what we are trying to stop
        stopRequested = true
        supervising = false
        restartJob?.cancel()
        restartJob = null
        stateHandle?.cancel()
        stateHandle = null
        runCatching { syncService?.stop() }
        syncService = null
        // the shared flows capture the sync service on first collection. keeping them after a
        // stop would hand back data in the name of a dead service
        fluxServeurs = null
        fluxInvitations = null
        // the subscriptions lived in the service we just stopped. keeping them would make the
        // next start believe the rooms are already subscribed when nothing is, server side
        abonnes.clear()
        restartAttempt = 0
        connection.value = ConnectionState.STOPPED
    }

    override suspend fun deconnecter() {
        // revoke first, stop second. `logout` goes through the client, which `stopSync` leaves
        // in place while cutting its service. the other order would work too, this one saves
        // having to check
        runCatching { client?.logout() }
        stopSync()
        runCatching { client?.close() }
        client = null
        ownUserId = null
        canonicalHomeserverUrl = null
        emetteurMas = null
        derniereSessionSdk = null
        Installation.oublier()
        oublierStructure()
    }

    /**
     * the stable part of a room inside a space hierarchy
     *
     * data of our own rather than the sdk's `SpaceRoom`, which is an ffi handle we do not keep
     * past the call that produced it. only the fields that move on creation, rename or refiling
     * are kept
     */
    private class StructureSalon(
        val roomId: String,
        val nom: String,
        val topic: String?,
        val type: RoomType,
    )

    /** `spaceId -> (when it was read, what was in it)`, see `channelsOf` */
    private val structureDesEspaces = mutableMapOf<String, Pair<Long, List<StructureSalon>>>()

    /**
     * empties the hierarchy cache
     *
     * called after any gesture that changes the structure: creating a channel or a server,
     * leaving one. without it, a channel you just created would take up to `STRUCTURE_TTL_MS`
     * to appear and the gesture would look like it did nothing. that delay is fine for a change
     * made elsewhere, never for one you just made yourself
     */
    private fun oublierStructure() {
        structureDesEspaces.clear()
        // emptying is not enough, it has to be announced. an empty cache only gets re read if
        // somebody asks for the hierarchy again, and the only trigger was the sdk's room list
        // diff. `m.space.child`, the event that files a channel into a server, is server state
        // and not a list entry, so it produces none
        //
        // the symptom was "creating a channel loads into nothing", then "if i go back, the
        // channel is there". the channel existed all along, the screen had simply never been
        // told to look again
        signalManuel.tryEmit(Unit)
    }

    /**
     * the second trigger for [signalSalons], the one we pull ourselves
     *
     * `DROP_OLDEST` rather than a suspending emit. these pokes are idempotent, all that matters
     * is that one happened after the last computation, and making a channel creation wait
     * because a buffer is full would add the very bug we are fixing
     */
    private val signalManuel = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override suspend fun servers(): List<Server> {
        val c = requireClient()
        val spaces = c.spaceService()
        val roomList = syncService?.roomListService()

        return try {
            // sorted here and not by the caller, because this is the one place every
            // consumer goes through and therefore the one place it cannot be forgotten. the
            // rail, the default selection and companion mode all see the same order
            trierServeurs(
                spaces.topLevelJoinedSpaces().mapNotNull { space ->
                    // categories are collected on the way down. they appear nowhere else,
                    // since an empty sub space produces no channels
                    val categories = mutableListOf<li.velvetchat.core.model.Categorie>()
                    // one server at a time. a single unreadable hierarchy (a server we were just
                    // kicked from answers 403 until the sync catches up) used to throw away the
                    // whole list, every server at once. a forbidden one is dropped, it is no
                    // longer ours; anything else keeps the server, without its channels this round
                    val channels = try {
                        channelsOf(spaces, space.roomId, roomList, categories = categories)
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        erreurNonBloquante("Hiérarchie de ${space.roomId} illisible", t)
                        if ("M_FORBIDDEN" in (t.message ?: "")) return@mapNotNull null
                        emptyList()
                    }
                    Server(
                        id = space.roomId,
                        name = space.displayName,
                        avatarUrl = space.avatarUrl,
                        channels = channels,
                        categories = categories,
                    )
                }
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Lecture des serveurs impossible", "Could not read the servers"), t)
        }
    }

    /**
     * a server's channels, categories included
     *
     * where the recursion comes from, and it is not an optimisation. the previous version
     * dropped sub spaces from the list with a comment saying they are not channels and the ui
     * will dress them separately, and never walked into them. so channels filed into a category
     * appeared nowhere. not badly sorted, absent. the comment described an intention, the code
     * performed an omission, and nothing on screen told "this server only has three channels"
     * apart from "this server has twelve and you can see three"
     *
     * one level of categories, deliberately. discord has one, and a three level collapsible
     * tree in a panel three hundred points wide would be miserable to navigate. so a
     * sub sub space has its channels surface into the category above rather than disappear.
     * degrade towards visible, never towards absent
     *
     * `vus` guards against a cyclic hierarchy. nothing stops two spaces declaring each other as
     * children, and the protocol does not prevent it
     */
    private suspend fun channelsOf(
        spaces: org.matrix.rustcomponents.sdk.SpaceService,
        spaceId: String,
        roomList: org.matrix.rustcomponents.sdk.RoomListService?,
        categorie: String? = null,
        vus: MutableSet<String> = mutableSetOf(),
        /** filled on the way down: the first level sub spaces, empty ones included */
        categories: MutableList<li.velvetchat.core.model.Categorie> = mutableListOf(),
    ): List<Channel> {
        if (!vus.add(spaceId)) return emptyList()

        // the hierarchy is cached, the unread counts are not
        //
        // `paginate()` is a round trip, and it was being done on every emission of the flow,
        // which means on every message received, for every space, plus once per sub space. the
        // `debounce` downstream grouped the bursts without removing anything: it hid the cost
        // rather than getting rid of it. prime suspect for the stutters people noticed
        //
        // what rarely changes, which channels exist and what they are called and which category
        // they are in, is kept for a few seconds. what changes constantly, the unread counts and
        // the encryption state, is re read every time, just below. so a badge can never be
        // stale while a channel created a second ago can be, by a few seconds. that is the right
        // way round for the trade, not the other
        val maintenant = System.currentTimeMillis()
        val enCache = structureDesEspaces[spaceId]
            ?.takeIf { maintenant - it.first < STRUCTURE_TTL_MS }
            ?.second

        val contenu = enCache ?: run {
            val list = spaces.spaceRoomList(spaceId)
            list.paginate()
            list.rooms()
                .map { StructureSalon(it.roomId, it.displayName, it.topic, it.roomType) }
                .also { structureDesEspaces[spaceId] = maintenant to it }
        }

        // sub spaces are the categories. we walk into them carrying their name
        val desCategories = contenu
            .filter { it.type is RoomType.Space }
            .flatMap { sous ->
                // only the first level names a category. below that the channels surface into
                // the one above, so there is no category to announce
                if (categorie == null) {
                    categories += li.velvetchat.core.model.Categorie(sous.roomId, sous.nom)
                }
                channelsOf(spaces, sous.roomId, roomList, categorie ?: sous.nom, vus, categories)
            }

        // the intended order, if anyone wrote one. `m.space.child.order` is the field the
        // protocol reserves for this: a string of printable ascii sorted lexicographically,
        // with children that have none coming after. without it, a server's order was whatever
        // the sdk handed back, which is no order at all from the point of view of somebody
        // tidying their channels
        val ordres = ordreDesEnfants(spaceId)
        return contenu
            .filter { it.type !is RoomType.Space }
            .sortedWith(
                compareBy(
                    { ordres[it.roomId] == null },
                    { ordres[it.roomId].orEmpty() },
                    { it.roomId },
                ),
            )
            .map { room ->
                val info = runCatching { roomList?.room(room.roomId)?.roomInfo() }.getOrNull()
                Channel(
                    id = room.roomId,
                    name = room.nom,
                    topic = room.topic,
                    kind = room.type.toChannelKind(info),
                    unread = info?.let {
                        Unread(
                            notifications = it.numUnreadNotifications.toInt(),
                            highlights = it.numUnreadMentions.toInt(),
                            marqueNonLu = it.isMarkedUnread,
                        )
                    } ?: Unread.NONE,
                    encryption = when (info?.encryptionState) {
                        SdkEncryptionState.ENCRYPTED -> EncryptionState.ENCRYPTED
                        SdkEncryptionState.NOT_ENCRYPTED -> EncryptionState.NOT_ENCRYPTED
                        else -> EncryptionState.UNKNOWN
                    },
                    categorie = categorie,
                )
            }
            // root channels before the ones in categories, like the reference client. what is
            // filed nowhere is what gets read the most
            .plus(desCategories)
    }

    /**
     * text channel or voice channel
     *
     * two signals, because neither is enough alone. the room type, since element marks voice
     * rooms with a custom type (`m.call` or `video-room` depending on the version), which is the
     * explicit marker when it is there. and a live call (`hasRoomCall`), because a room with a
     * call running behaves like a voice channel from where the user is standing, custom type or
     * not
     *
     * what stays true and is worth knowing: matrix has no standard voice channel in the discord
     * sense. so this is a heuristic over client conventions rather than a reading of the
     * protocol, and it is allowed to guess wrong towards text, which is the readable degradation
     */
    private fun RoomType.toChannelKind(
        info: org.matrix.rustcomponents.sdk.RoomInfo?,
    ): ChannelKind {
        val typeVocal = (this as? RoomType.Custom)?.value?.lowercase()?.let { v ->
            v.contains("call") || v.contains("video") || v.contains("voice")
        } == true
        return if (typeVocal || info?.hasRoomCall == true) ChannelKind.VOICE else ChannelKind.TEXT
    }

    // unread counts and encryption state both come from `RoomInfo`, so from the sync stream
    // and not the space hierarchy, which means reading them room by room. acceptable for a
    // snapshot

    override suspend fun createServer(name: String, topic: String?): Server {
        // the id names no matrix room: it only keys the tile until the real one takes its place
        val brouillon = Server("brouillon:${java.util.UUID.randomUUID()}", name, null, emptyList())
        return optimiste({ it.copy(serveurs = it.serveurs + brouillon) }, { it.copy(serveurs = it.serveurs - brouillon) }) {
            creerServeur(name, topic)
        }.also { cree -> optimisme.update { it.copy(serveurs = it.serveurs - brouillon + cree) } }
    }

    private suspend fun creerServeur(name: String, topic: String?): Server {
        val c = requireClient()
        val id = try {
            c.createRoom(
                CreateRoomParameters(
                    name = name,
                    topic = topic,
                    // a space carries no messages, so there is nothing to encrypt, and
                    // turning it on would cost a key negotiation for nothing
                    isEncrypted = false,
                    isDirect = false,
                    visibility = RoomVisibility.Private,
                    preset = RoomPreset.PRIVATE_CHAT,
                    isSpace = true,
                )
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Création du serveur « $name » impossible", "Could not create the server \"$name\""), t)
        }
        oublierStructure()

        // a server with no channel is unusable, and shipping one is shipping a room with no
        // door: you get there, there is nothing, and nothing says what to do
        //
        // the failure is deliberately not propagated. by this point the space exists, and
        // failing would leave a server created server side but announced as failed, the worst of
        // both. so we hand back a server with no channel, which the screen can draw and which
        // you can add a channel to by hand
        //
        // and when sync is behind, we do not stand around waiting for it. the general channel
        // needs the client to know the space, because `m.space.child` is written inside the
        // space. if it does not know it yet, the creation goes to the background and the server
        // comes back immediately: it appears empty, then its channel arrives. thirty seconds of
        // spinner followed by an error about a server that existed was the worst of the three
        // outcomes
        val general = if (c.attendreConnu(id)) {
            runCatching { createChannel(id, "general") }
                .onFailure { erreurNonBloquante("Salon « general » non créé dans « $name »", it) }
                .getOrNull()
        } else {
            supervisor.launch {
                if (requireClient().attendreConnu(id, ECHO_PATIENT_MS)) {
                    runCatching { createChannel(id, "general") }
                        .onFailure { erreurNonBloquante("Salon « general » retardé", it) }
                    // the panel only re reads when told to, and the channel lands after the
                    // fact
                    oublierStructure()
                }
            }
            null
        }

        return Server(
            id = id,
            name = name,
            avatarUrl = null,
            channels = listOfNotNull(general),
        )
    }

    override suspend fun leaveChannel(channelId: String, forget: Boolean) = optimiste(
        { it.copy(partis = it.partis + channelId) },
        { it.copy(partis = it.partis - channelId) },
    ) { quitterSalon(channelId, forget) }

    private suspend fun quitterSalon(channelId: String, forget: Boolean) {
        oublierStructure()
        val c = requireClient()
        val salon = c.getRoom(channelId)
            ?: throw EngineException.Unexpected(tr("Salon inconnu : $channelId", "Unknown channel: $channelId"))
        try {
            salon.leave()
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Impossible de quitter le salon", "Could not leave the channel"), t)
        }
        // failure tolerated here. we did leave, which is what matters. same call as
        // `leaveServer`
        if (forget) runCatching { c.getRoom(channelId)?.forget() }
        // the cached timeline points at a room we just left
        verrouTimelines.withLock { timelines.remove(channelId) }
    }

    override suspend fun createCategory(serverId: String, name: String): String {
        val brouillon = li.velvetchat.core.model.Categorie("brouillon:${java.util.UUID.randomUUID()}", name)
        val sans: (Optimisme) -> Optimisme = { it.avecCategorie(serverId, brouillon, retirer = true) }
        return optimiste({ it.avecCategorie(serverId, brouillon) }, sans) {
            creerCategorie(serverId, name)
        }.also { id ->
            optimisme.update { sans(it).avecCategorie(serverId, li.velvetchat.core.model.Categorie(id, name)) }
        }
    }

    private suspend fun creerCategorie(serverId: String, name: String): String {
        val c = requireClient()
        verifierRattachable(serverId, tr("Catégorie « $name »", "Category \"$name\""))
        val id = try {
            c.createRoom(
                CreateRoomParameters(
                    name = name,
                    topic = null,
                    // a category is not encrypted, and that is fine: it carries no messages.
                    // encrypting an empty space would protect nothing and make its hierarchy
                    // harder for other clients to read
                    isEncrypted = false,
                    isDirect = false,
                    visibility = RoomVisibility.Private,
                    preset = RoomPreset.PRIVATE_CHAT,
                    joinRuleOverride = JoinRule.Restricted(
                        rules = listOf(AllowRule.RoomMembership(serverId))
                    ),
                    historyVisibilityOverride = RoomHistoryVisibility.Shared,
                    isSpace = true,
                )
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Création de la catégorie « $name » impossible", "Could not create the category \"$name\""), t)
        }

        // no waiting. `attachToSpace` writes `m.space.child` into the server, which has been
        // known all along. only the optional `m.space.parent`, already wrapped, reads the new
        // category. same reasoning as for channels, and it was the same bug, left here after
        // being fixed there
        attachToSpace(childId = id, spaceId = serverId, what = tr("Catégorie « $name »", "Category \"$name\""))
        // without this the category only turns up at the next hierarchy sweep, and creating one
        // looks like it does nothing at all
        oublierStructure()
        return id
    }

    override suspend fun deplacerSalon(
        serverId: String,
        channelId: String,
        depuis: String?,
        vers: String?,
    ) {
        val ancien = depuis ?: serverId
        val nouveau = vers ?: serverId
        if (ancien == nouveau) return

        // new parent first, old parent second. the other way round, a failure between the two
        // writes would leave a channel attached to nothing, invisible and therefore lost for
        // everyone. this way round, the worst case is a channel shown twice until somebody
        // tries again, which you can see and fix
        attachToSpace(childId = channelId, spaceId = nouveau, what = tr("Salon déplacé", "Moved channel"))

        val c = requireClient()
        val espace = c.getRoom(ancien)
            ?: throw EngineException.Unexpected(tr("Ancien parent inconnu : $ancien", "Unknown former parent: $ancien"))
        try {
            // matrix does not delete a state, it writes an empty one. `{}` with no `via`
            // removes the child from the hierarchy, that is the protocol's own definition
            espace.sendStateEventRaw(eventType = "m.space.child", stateKey = channelId, content = "{}")
        } catch (t: Throwable) {
            oublierStructure()
            throw EngineException.Unexpected(tr("Salon rattaché à sa nouvelle place, mais pas retiré de l'ancienne", "Channel attached to its new place, but not removed from the old one"), t)
        }
        // the reverse link, optional like at creation. the old one gets cleared, the new one
        // was already written by `attachToSpace`
        runCatching {
            c.getRoom(channelId)?.sendStateEventRaw(
                eventType = "m.space.parent",
                stateKey = ancien,
                content = "{}",
            )
        }
        oublierStructure()
    }

    override suspend fun supprimerSalon(serverId: String, channelId: String, parent: String?) = optimiste(
        { it.copy(partis = it.partis + channelId) },
        { it.copy(partis = it.partis - channelId) },
    ) { retirerSalon(serverId, channelId, parent) }

    private suspend fun retirerSalon(serverId: String, channelId: String, parent: String?) {
        val c = requireClient()
        val espace = c.getRoom(parent ?: serverId)
            ?: throw EngineException.Unexpected(tr("Serveur inconnu : ${parent ?: serverId}", "Unknown server: ${parent ?: serverId}"))
        // `{}` with no `via` is how the protocol removes a child from a hierarchy
        espace.sendStateEventRaw(eventType = "m.space.child", stateKey = channelId, content = "{}")
        runCatching {
            c.getRoom(channelId)?.sendStateEventRaw(
                eventType = "m.space.parent",
                stateKey = parent ?: serverId,
                content = "{}",
            )
        }
        oublierStructure()
        quitterSalon(channelId, forget = true)
    }

    override suspend fun createChannel(
        serverId: String,
        name: String,
        topic: String?,
        categorieId: String?,
        vocal: Boolean,
    ): Channel {
        // `categorie` on a channel is the category's name, the id is what we were handed
        val nomCategorie = categorieId?.let { id ->
            (derniereListe + optimisme.value.serveurs).firstOrNull { it.id == serverId }
                ?.categories?.firstOrNull { it.id == id }?.nom
                ?: optimisme.value.categories[serverId]?.firstOrNull { it.id == id }?.nom
        }
        val brouillon = Channel(
            id = "brouillon:${java.util.UUID.randomUUID()}",
            name = name,
            topic = topic,
            kind = if (vocal) ChannelKind.VOICE else ChannelKind.TEXT,
            unread = Unread.NONE,
            categorie = nomCategorie,
        )
        val sans: (Optimisme) -> Optimisme = { it.avecSalon(serverId, brouillon, retirer = true) }
        return optimiste({ it.avecSalon(serverId, brouillon) }, sans) {
            creerSalon(serverId, name, topic, categorieId, vocal)
        }.let { cree ->
            val place = cree.copy(categorie = nomCategorie)
            optimisme.update { sans(it).avecSalon(serverId, place) }
            place
        }
    }

    private suspend fun creerSalon(
        serverId: String,
        name: String,
        topic: String?,
        categorieId: String?,
        vocal: Boolean,
    ): Channel {
        val c = requireClient()
        verifierRattachable(categorieId ?: serverId, tr("Salon « $name »", "Channel \"$name\""))
        val id = if (vocal) {
            creerSalonVocal(serverId, name, topic)
        } else try {
            c.createRoom(
                CreateRoomParameters(
                    name = name,
                    topic = topic,
                    isEncrypted = true,
                    isDirect = false,
                    visibility = RoomVisibility.Private,
                    preset = RoomPreset.PRIVATE_CHAT,
                    // the two lines the entire server model rests on
                    //
                    // `restricted` means being in the space is enough to get in. without it
                    // the preset leaves the room on named invites, and joining a server hands
                    // you a list of locked doors
                    joinRuleOverride = JoinRule.Restricted(
                        rules = listOf(AllowRule.RoomMembership(serverId))
                    ),
                    // `shared` keeps the history readable for whoever joins later. server
                    // side only, see the contract's warning about megolm keys not following
                    historyVisibilityOverride = RoomHistoryVisibility.Shared,
                    // without this, only an admin can start or join a call: the voice markers
                    // are state events and `state_default` is 50. see [POUVOIRS_APPEL]. we
                    // touch those two types and nothing else
                    powerLevelContentOverride = PowerLevels(
                        usersDefault = null,
                        eventsDefault = null,
                        stateDefault = null,
                        ban = null,
                        kick = null,
                        redact = null,
                        invite = null,
                        notifications = null,
                        users = emptyMap(),
                        events = POUVOIRS_APPEL,
                    ),
                    isSpace = false,
                )
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Création du salon « $name » impossible", "Could not create the channel \"$name\""), t)
        }

        // waiting for the new channel's echo is not necessary here, and it was the thing that
        // hung
        //
        // the symptom was "the create channel button spins into nothing". the cause is this
        // wait, up to thirty seconds spent watching for the channel to land in the local cache
        // when nothing after it needs that: `attachToSpace` writes `m.space.child` on the
        // server, which is already known. only the optional `m.space.parent`, already wrapped,
        // reads the new channel
        //
        // on a loaded account that sync lags, on an empty one it arrives instantly, which is a
        // bug invisible in testing and thoroughly real in use. so we attempt the wait without
        // depending on it: what matters is written afterwards, and the channel shows up as soon
        // as the server's hierarchy is re read anyway
        c.attendreConnu(id)

        // attached to the category when there is one, but the join rule written above still
        // names the server. see the contract: a channel whose right of entry pointed at the
        // category would open for nobody
        attachToSpace(
            childId = id,
            spaceId = categorieId ?: serverId,
            what = tr("Salon « $name »", "Channel \"$name\""),
        )
        oublierStructure()

        return Channel(
            id = id,
            name = name,
            topic = topic,
            kind = if (vocal) ChannelKind.VOICE else ChannelKind.TEXT,
            unread = Unread.NONE,
            encryption = EncryptionState.ENCRYPTED,
        )
    }

    /**
     * a voice channel gets created by hand, because the sdk cannot state its type
     *
     * `CreateRoomParameters` has no field for `creation_content`, and a room's type is written
     * exactly once, in `m.room.create`. so we go to the api directly, sending the same request
     * the sdk would have sent plus that one key, and everything else (encryption, join rule
     * pointing at the server, history visibility, lowered call powers) is repeated here as is.
     * those lines are what make a channel usable, not the path they are written down
     */
    private suspend fun creerSalonVocal(serverId: String, name: String, topic: String?): String {
        val corps = kotlinx.serialization.json.buildJsonObject {
            put("name", kotlinx.serialization.json.JsonPrimitive(name))
            topic?.let { put("topic", kotlinx.serialization.json.JsonPrimitive(it)) }
            put("preset", kotlinx.serialization.json.JsonPrimitive("private_chat"))
            put("visibility", kotlinx.serialization.json.JsonPrimitive("private"))
            put("is_direct", kotlinx.serialization.json.JsonPrimitive(false))
            // the one key this whole detour exists for
            put(
                "creation_content",
                kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive(TYPE_SALON_VOCAL))
                },
            )
            put(
                "power_level_content_override",
                kotlinx.serialization.json.buildJsonObject {
                    put(
                        "events",
                        kotlinx.serialization.json.buildJsonObject {
                            POUVOIRS_APPEL.forEach { (type, niveau) ->
                                put(type, kotlinx.serialization.json.JsonPrimitive(niveau))
                            }
                        },
                    )
                },
            )
            put(
                "initial_state",
                kotlinx.serialization.json.buildJsonArray {
                    add(
                        kotlinx.serialization.json.buildJsonObject {
                            put("type", kotlinx.serialization.json.JsonPrimitive("m.room.encryption"))
                            put("state_key", kotlinx.serialization.json.JsonPrimitive(""))
                            put(
                                "content",
                                kotlinx.serialization.json.buildJsonObject {
                                    put(
                                        "algorithm",
                                        kotlinx.serialization.json.JsonPrimitive("m.megolm.v1.aes-sha2"),
                                    )
                                },
                            )
                        },
                    )
                    add(
                        kotlinx.serialization.json.buildJsonObject {
                            put("type", kotlinx.serialization.json.JsonPrimitive("m.room.join_rules"))
                            put("state_key", kotlinx.serialization.json.JsonPrimitive(""))
                            put(
                                "content",
                                kotlinx.serialization.json.buildJsonObject {
                                    put("join_rule", kotlinx.serialization.json.JsonPrimitive("restricted"))
                                    put(
                                        "allow",
                                        kotlinx.serialization.json.buildJsonArray {
                                            add(
                                                kotlinx.serialization.json.buildJsonObject {
                                                    put(
                                                        "type",
                                                        kotlinx.serialization.json.JsonPrimitive("m.room_membership"),
                                                    )
                                                    put(
                                                        "room_id",
                                                        kotlinx.serialization.json.JsonPrimitive(serverId),
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                    add(
                        kotlinx.serialization.json.buildJsonObject {
                            put(
                                "type",
                                kotlinx.serialization.json.JsonPrimitive("m.room.history_visibility"),
                            )
                            put("state_key", kotlinx.serialization.json.JsonPrimitive(""))
                            put(
                                "content",
                                kotlinx.serialization.json.buildJsonObject {
                                    put(
                                        "history_visibility",
                                        kotlinx.serialization.json.JsonPrimitive("shared"),
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }.toString()

        val reponse = requeteSignee("POST", "/_matrix/client/v3/createRoom", corps)
        if (reponse.code != 200) {
            throw EngineException.Unexpected(
                tr("Création du salon vocal « $name » impossible (${reponse.code} ${reponse.corps.take(200)})", "Could not create the voice channel \"$name\" (${reponse.code} ${reponse.corps.take(200)})"),
            )
        }
        return (kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
            as? kotlinx.serialization.json.JsonObject)
            ?.get("room_id")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
            ?: throw EngineException.Unexpected(tr("Le serveur n'a pas rendu l'identifiant du salon", "The server did not return the channel id"))
    }

    /**
     * the `order` values the `m.space.child` events of a space declare, per channel
     *
     * read from the api rather than the sdk. `SpaceService` gives you a space's rooms and not
     * the contents of the events attaching them, and `order` lives in those contents. one round
     * trip per space, cached as long as the structure itself, since an order only changes when
     * somebody asks for it
     */
    private suspend fun ordreDesEnfants(spaceId: String): Map<String, String> {
        val maintenant = System.currentTimeMillis()
        ordresDesEspaces[spaceId]?.takeIf { maintenant - it.first < STRUCTURE_TTL_MS }
            ?.let { return it.second }
        val lu = runCatching {
            val reponse = requeteSignee("GET", "/_matrix/client/v3/rooms/${enc(spaceId)}/state")
            if (reponse.code != 200) return@runCatching emptyMap<String, String>()
            (kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                as? kotlinx.serialization.json.JsonArray)
                .orEmpty()
                .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                .filter { (it["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "m.space.child" }
                .mapNotNull { evenement ->
                    val salon = (evenement["state_key"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val contenu = evenement["content"] as? kotlinx.serialization.json.JsonObject
                    val ordre = (contenu?.get("order") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    if (salon == null || ordre.isNullOrEmpty()) null else salon to ordre
                }
                .toMap()
        }.getOrDefault(emptyMap())
        ordresDesEspaces[spaceId] = maintenant to lu
        return lu
    }

    private val ordresDesEspaces = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Map<String, String>>>()

    override suspend fun reordonnerSalons(parentId: String, salonsDansLOrdre: List<String>) {
        val c = requireClient()
        val espace = c.getRoom(parentId)
            ?: throw EngineException.Unexpected(tr("Serveur inconnu : $parentId", "Unknown server: $parentId"))
        val via = parentId.substringAfter(':', "")
        val viaJson = if (via.isEmpty()) "[]" else "[\"$via\"]"
        // ranks spaced ten apart, and rewritten wholesale. inserting between two neighbours
        // would want an intermediate key, which is doable, and the day two ranks touch you have
        // to rewrite everything anyway. on a small server rewriting everything costs a handful
        // of state writes and cannot get itself stuck
        salonsDansLOrdre.forEachIndexed { rang, salon ->
            val ordre = ((rang + 1) * 10).toString().padStart(6, '0')
            runCatching {
                espace.sendStateEventRaw(
                    eventType = "m.space.child",
                    stateKey = salon,
                    content = """{"via":$viaJson,"order":"$ordre"}""",
                )
            }
        }
        ordresDesEspaces.remove(parentId)
        oublierStructure()
    }

    /**
     * refuses before anything is created when the result could not be attached
     *
     * writing `m.space.child` takes the space's `state_default` (50) unless lowered. a member
     * without that right used to get the channel made, then the attach refused, and an orphan
     * room nobody sees. asking first costs a cache read. an unknown answer lets the attempt go
     * on: [attachToSpace] still reports a real refusal
     */
    private suspend fun verifierRattachable(spaceId: String, what: String) {
        val peut = runCatching {
            requireClient().getRoom(spaceId)?.getPowerLevels()
                ?.canOwnUserSendState(uniffi.ruma_events.StateEventType.SpaceChild)
        }.getOrNull()
        if (peut == false) {
            throw EngineException.Unexpected(tr("$what : vous n'avez pas le droit d'ajouter un salon ici", "$what: you are not allowed to add a channel here"))
        }
    }

    /**
     * attaches a channel to a server by writing the state events directly
     *
     * `SpaceService.addChildToSpace` would be the natural path, and it fails on a channel you
     * just created with "local cache doesn't contain all necessary data to perform the action".
     * waiting for the remote echo of both rooms changes nothing, so it is not a timing question
     * but data the sdk derives from sync and has not built yet for a new room
     *
     * writing `m.space.child` ourselves sidesteps the question, and it is the protocol's own
     * mechanism rather than a detour. we know exactly what goes out, and the `via`, the server
     * through which to reach the room, is the one we pick
     *
     * `m.space.parent` goes out too. it lets a client that finds the channel on its own walk
     * back up to its server. its failure is not blocking, `m.space.child` is what carries the
     * hierarchy
     */
    private suspend fun attachToSpace(childId: String, spaceId: String, what: String) {
        val c = requireClient()
        val via = spaceId.substringAfter(':', "")
        val viaJson = if (via.isEmpty()) "[]" else "[\"$via\"]"

        val space = c.getRoom(spaceId)
            ?: throw EngineException.Unexpected(tr("$what créé ($childId) : serveur $spaceId inconnu", "$what created ($childId): unknown server $spaceId"))

        try {
            space.sendStateEventRaw(
                eventType = "m.space.child",
                stateKey = childId,
                content = """{"via":$viaJson}""",
            )
        } catch (t: Throwable) {
            // the channel exists and will show up in no server: invisible and unusable.
            // saying so beats leaving an orphan behind
            val raison = t.message?.lineSequence()?.firstOrNull()?.take(200)
            throw EngineException.Unexpected(
                tr("$what créé ($childId) mais non rattaché au serveur", "$what created ($childId) but not attached to the server") + (raison?.let { " : $it" } ?: ""),
                t,
            )
        }

        runCatching {
            c.getRoom(childId)?.sendStateEventRaw(
                eventType = "m.space.parent",
                stateKey = spaceId,
                content = """{"via":$viaJson,"canonical":true}""",
            )
        }
    }

    /**
     * waits for a freshly created room to be known to the client, not only to the server
     *
     * `createRoom` returns an id as soon as the server made the room. the client only learns
     * about it on the next sync, and `getRoom()` returns null until then, which is what made
     * attaching fail while announcing an "unknown" server
     *
     * it no longer throws, and it no longer takes thirty seconds. the symptom was "creating a
     * server spins into nothing for 30 s then shows the error, except when you quit and reopen
     * the app it worked". both halves of that sentence say the same thing: the server existed,
     * only the announcement was wrong. a wait that fails over successful work is worse than no
     * wait at all
     *
     * measured: the echo arrives in one to twenty odd milliseconds when sync is keeping up. so
     * it is a second, not thirty, that separates "sync is keeping up" from "sync is behind". we
     * return false then, and the caller decides. see [createServer], which finishes in the
     * background rather than announce a failure
     *
     * `getRoom` rather than `awaitRoomRemoteEcho`, because that is exactly what attaching needs,
     * and it is a cache read rather than a loop somebody has to interrupt from outside
     */
    private suspend fun Client.attendreConnu(
        roomId: String,
        plafondMs: Long = ECHO_COURT_MS,
    ): Boolean {
        val fin = System.currentTimeMillis() + plafondMs
        while (System.currentTimeMillis() < fin) {
            if (runCatching { getRoom(roomId) }.getOrNull() != null) return true
            delay(50)
        }
        return runCatching { getRoom(roomId) }.getOrNull() != null
    }

    override suspend fun invite(serverId: String, userId: String) = guard(
        tr("Invitation impossible", "Could not invite")
    ) {
        val room = requireClient().getRoom(serverId)
            ?: throw EngineException.Unexpected(tr("Serveur inconnu : $serverId", "Unknown server: $serverId"))
        room.inviteUserById(userId)
    }

    override suspend fun joinServer(serverIdOrAlias: String): ServerJoin {
        val c = requireClient()
        // an invite link pasted as is joins as whatever id it carries
        val cible = li.velvetchat.core.model.LienInvitation.cible(serverIdOrAlias)
        val space = try {
            c.joinRoomByIdOrAlias(cible, emptyList())
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Impossible de rejoindre $serverIdOrAlias", "Could not join $serverIdOrAlias"), t)
        }
        val spaceId = space.id()

        val joined = mutableListOf<String>()
        val refused = mutableMapOf<String, String>()
        joinChildren(spaceId, joined, refused, depth = 0, seen = mutableSetOf(spaceId))

        // re read the servers rather than build a `Server` by hand. the channels were just
        // joined, and the real hierarchy is what should count
        val server = servers().firstOrNull { it.id == spaceId }
            ?: Server(spaceId, space.displayName() ?: spaceId, null, emptyList())

        return ServerJoin(server = server, joined = joined, refused = refused)
    }

    override suspend fun invitationsRecues(): List<li.velvetchat.core.model.InvitationRecue> =
        guardListe {
            // `client.rooms()` and not the sliding sync list. that one is filtered `NonLeft`
            // by the server flow, and more importantly it is paginated and may not carry the
            // unread invite yet. the client's list holds everything the store knows, invites
            // included
            requireClient().rooms().mapNotNull { room ->
                if (room.membership() != org.matrix.rustcomponents.sdk.Membership.INVITED) {
                    return@mapNotNull null
                }
                // `inviter()` may be a round trip, so we tolerate its failure rather than lose
                // the whole invite over a missing name
                val invitant = runCatching { room.inviter() }.getOrNull()
                li.velvetchat.core.model.InvitationRecue(
                    roomId = room.id(),
                    nom = room.displayName() ?: room.id(),
                    avatarUrl = room.avatarUrl(),
                    estEspace = room.isSpace(),
                    estDirect = runCatching { room.isDirect() }.getOrDefault(false),
                    invitePar = invitant?.userId,
                    nomDeInvitant = invitant?.displayName,
                )
            }
        }

    override suspend fun alertes(): List<li.velvetchat.core.model.AlerteSalon> =
        guardListe {
            val moi = ownUserId
            // the state comes from the sync list and not from the client
            //
            // both expose `roomInfo()` and `latestEvent()`, and they are not the same caches.
            // the client's updates after the list, which showed up in use as a notification
            // that always displayed the previous message and never the one that had just
            // arrived, so a conversation's last message never announced itself at all.
            // `directs()` and `channelsOf()` already read the list and their badges were right,
            // which was the clue
            //
            // the client is only used to enumerate now, since the set of rooms does not move
            // from one message to the next
            val liste = syncService?.roomListService()
            requireClient().rooms().mapNotNull { room ->
                if (room.membership() != org.matrix.rustcomponents.sdk.Membership.JOINED) {
                    return@mapNotNull null
                }
                val vivant = runCatching { liste?.room(room.id()) }.getOrNull() ?: room
                val info = runCatching { vivant.roomInfo() }.getOrNull() ?: return@mapNotNull null
                // a space has no conversation. its unread counts are its channels', and
                // counting them here would double every message
                if (info.isSpace) return@mapNotNull null
                if (info.numUnreadNotifications == 0uL && info.numUnreadMentions == 0uL) {
                    return@mapNotNull null
                }
                val dernier = runCatching { vivant.latestEvent() }.getOrNull()
                    as? org.matrix.rustcomponents.sdk.LatestEventValue.Remote
                li.velvetchat.core.model.AlerteSalon(
                    salonId = room.id(),
                    nom = info.displayName ?: room.id(),
                    auteur = (dernier?.profile as? ProfileDetails.Ready)?.displayName
                        ?: dernier?.sender,
                    auteurId = dernier?.sender,
                    apercu = dernier?.let { apercuDe(it.content) },
                    nonLus = info.numUnreadNotifications.toInt(),
                    mentions = info.numUnreadMentions.toInt(),
                    estDirect = info.isDirect,
                    // the timestamp is the deduplication key for want of an id, since
                    // `LatestEventValue.Remote` does not carry the `event_id`. two distinct
                    // messages in the same millisecond in the same room would
                    // collide, and that is the one case we accept missing
                    dernierEvenement = dernier?.timestamp?.toLong()?.toString(),
                    horodatage = dernier?.timestamp?.toLong() ?: 0L,
                    aMoi = dernier?.isOwn == true || (moi != null && dernier?.sender == moi),
                    avatarSalon = info.avatarUrl,
                    // the last event's profile is almost never resolved, which is why
                    // notifications had no face on them. `LatestEventValue.Remote.profile` is
                    // `Unavailable` or `Pending` until the room has been opened, and a
                    // notification arrives precisely for a room you have not opened. the icon
                    // was always the fallback and the bubble showed the app logo instead of the
                    // face of whoever was writing
                    //
                    // so we ask the room for the avatar when the profile does not give one. a
                    // possible round trip, but only for rooms that actually have something to
                    // announce
                    avatarAuteur = (dernier?.profile as? ProfileDetails.Ready)?.avatarUrl
                        ?: dernier?.sender?.let { qui ->
                            runCatching { vivant.memberAvatarUrl(qui) }.getOrNull()
                        },
                )
            }
        }

    override suspend fun accepterInvitation(roomId: String, estEspace: Boolean) {
        if (estEspace) {
            // the space and its channels, otherwise you inherit an empty server. see
            // `joinServer`
            joinServer(roomId)
            return
        }
        val room = requireClient().getRoom(roomId)
            ?: throw EngineException.Unexpected(tr("Invitation introuvable : $roomId", "Invite not found: $roomId"))
        try {
            room.join()
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Impossible de rejoindre $roomId", "Could not join $roomId"), t)
        }
        // the hierarchy can change: a joined room may belong to a space we already draw
        oublierStructure()
    }

    override suspend fun refuserInvitation(roomId: String) = guard(tr("Refus impossible", "Could not decline")) {
        // `leave` and not `forget`. forgetting would stop the server reinviting us to the same
        // room, which turns "no thanks" into a self imposed ban
        requireClient().getRoom(roomId)?.leave()
        oublierStructure()
    }

    // control centre
    //
    // everything in this block goes through `requeteSignee`, so through synapse's admin api,
    // which is an entirely separate world from rooms and their power levels. a 403 here never
    // means broken, it means not an admin, and that is the normal case for nearly every account

    override suspend fun estAdministrateurServeur(): Boolean = try {
        // the same call is both the test and the first useful read. no separate "am i an
        // admin" request, which would cost a round trip for a question this one already answers
        // under MAS synapse no longer serves its registration tokens (404), the user list stays
        if (sousMas()) requeteSignee("GET", "/_synapse/admin/v2/users?limit=1&guests=false").code == 200
        else requeteSignee("GET", "/_synapse/admin/v1/registration_tokens").code == 200
    } catch (t: Throwable) {
        false
    }

    // under MAS. registration happens on its page, so its tokens are the ones that count: the
    // ones synapse still stores would be accepted by nobody

    private suspend fun requeteMas(methode: String, chemin: String, corps: String? = null) =
        requeteSignee(methode, chemin, corps, base = emetteur())

    private fun objetJson(texte: String) = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(texte) as? kotlinx.serialization.json.JsonObject
    }.getOrNull()

    private fun kotlinx.serialization.json.JsonObject.texte(cle: String) =
        (this[cle] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    /** `data[]` of a MAS list, as `(id, attributes)` */
    private fun ressourcesMas(corps: String) =
        (objetJson(corps)?.get("data") as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull {
            val o = it as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val attributs = o["attributes"] as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            (o.texte("id") ?: return@mapNotNull null) to attributs
        }

    private suspend fun jetonsMas(): List<Pair<String, kotlinx.serialization.json.JsonObject>> {
        val r = requeteMas("GET", "api/admin/v1/user-registration-tokens?filter%5Bvalid%5D=true&page%5Bfirst%5D=100")
        if (r.code != 200) throw EngineException.Unexpected("Invitations illisibles (${r.code})")
        return ressourcesMas(r.corps)
    }

    override suspend fun jetonsInvitation(): List<JetonInvitation> = guardListe {
        if (sousMas()) {
            val espaces = espacesDesJetons()
            return@guardListe jetonsMas().mapNotNull { (_, a) ->
                val jeton = a.texte("token") ?: return@mapNotNull null
                val limite = (a["usage_limit"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
                val faits = (a["times_used"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 0
                JetonInvitation(
                    jeton = jeton,
                    usagesRestants = limite?.let { (it - faits).coerceAtLeast(0) },
                    expiration = a.texte("expires_at")
                        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() },
                    idEspace = espaces[jeton],
                )
            }
        }
        val reponse = requeteSignee("GET", "/_synapse/admin/v1/registration_tokens")
        if (reponse.code != 200) return@guardListe emptyList()
        val racine = kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
            as? kotlinx.serialization.json.JsonObject ?: return@guardListe emptyList()
        val jetons = racine["registration_tokens"] as? kotlinx.serialization.json.JsonArray
            ?: return@guardListe emptyList()

        val espaces = espacesDesJetons()

        val liste = jetons.mapNotNull { element ->
            val objet = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val jeton = (objet["token"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?: return@mapNotNull null
            val autorises = (objet["uses_allowed"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
            val complets = (objet["completed"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 0
            val enCours = (objet["pending"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull ?: 0
            val expiration = (objet["expiry_time"] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull

            JetonInvitation(
                jeton = jeton,
                // null means unlimited on synapse's side. otherwise what is left, never
                // negative: a token whose count drifted between two reads shows 0 rather than a
                // number that would mean nothing
                usagesRestants = autorises?.let { (it - complets - enCours).coerceAtLeast(0) },
                expiration = expiration,
                idEspace = espaces[jeton],
            )
        }

        // the note to self is trimmed to what the server says. a token revoked from another
        // device would otherwise leave its entry forever. the token list is authoritative, and
        // what is no longer in it has nothing left to remember
        val vivants = liste.map { it.jeton }.toSet()
        if (espaces.keys.any { it !in vivants }) {
            runCatching { ecrireEspacesDesJetons(espaces.filterKeys { it in vivants }) }
        }

        liste
    }

    /** the `token to space` note to self, in account data. empty when there is none */
    private suspend fun espacesDesJetons(): Map<String, String> = runCatching {
        val brut = requireClient().donneeDeCompte(TYPE_INVITATIONS) ?: return emptyMap()
        val carte = kotlinx.serialization.json.Json.parseToJsonElement(brut)
            as? kotlinx.serialization.json.JsonObject ?: return emptyMap()
        carte.mapNotNull { (jeton, valeur) ->
            (valeur as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?.let { jeton to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private suspend fun ecrireEspacesDesJetons(carte: Map<String, String>) {
        requireClient().setAccountData(
            TYPE_INVITATIONS,
            kotlinx.serialization.json.JsonObject(
                carte.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }
            ).toString(),
        )
    }

    override suspend fun creerInvitation(
        idEspace: String,
        usages: Int?,
        expirationJours: Int?,
    ): String = guardValeur(tr("Création de l'invitation impossible", "Could not create the invite")) {
        val corps = kotlinx.serialization.json.buildJsonObject {
            // `if` and not `?:`, and that is a fix rather than a preference
            //
            // `put` returns the key's *previous* value, so null on a new key. written as
            // `usages?.let { put(...) } ?: put("uses_allowed", null)`, the elvis therefore fired
            // every single time, including when a use count had been chosen: every code went out
            // unlimited. on a server facing the internet, a code meant for one person stayed
            // usable by anyone who came across it. the explicit form cannot fall into that
            if (usages != null) put("uses_allowed", usages) else put("uses_allowed", null as Number?)
            if (expirationJours != null) {
                put("expiry_time", System.currentTimeMillis() + expirationJours * 86_400_000L)
            }
        }.toString()

        val jeton = if (sousMas()) {
            val corpsMas = kotlinx.serialization.json.buildJsonObject {
                if (usages != null) put("usage_limit", usages)
                if (expirationJours != null) {
                    put("expires_at", java.time.Instant.now().plusSeconds(expirationJours * 86_400L).toString())
                }
            }.toString()
            val r = requeteMas("POST", "api/admin/v1/user-registration-tokens", corpsMas)
            if (r.code !in 200..299) throw EngineException.Unexpected(tr("Création refusée (${r.code}) : ${r.corps}", "Creation refused (${r.code}): ${r.corps}"))
            ((objetJson(r.corps)?.get("data") as? kotlinx.serialization.json.JsonObject)
                ?.get("attributes") as? kotlinx.serialization.json.JsonObject)?.texte("token")
                ?: throw EngineException.Unexpected(tr("Jeton absent de la réponse", "No token in the response"))
        } else {
            val reponse = requeteSignee("POST", "/_synapse/admin/v1/registration_tokens/new", corps)
            if (reponse.code !in 200..299) {
                throw EngineException.Unexpected(tr("Création refusée (${reponse.code}) : ${reponse.corps}", "Creation refused (${reponse.code}): ${reponse.corps}"))
            }
            val racine = kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                as? kotlinx.serialization.json.JsonObject
            (racine?.get("token") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?: throw EngineException.Unexpected(tr("Jeton absent de la réponse", "No token in the response"))
        }

        // the note to self is written here, never swallowed silently if it fails, and it does
        // not fail the creation either: the code returned just below is complete and usable,
        // only the convenience of finding it again later would be lost. `runCatching` with a
        // trace, not a bare one
        runCatching { ecrireEspacesDesJetons(espacesDesJetons() + (jeton to idEspace)) }
            .onFailure { erreurNonBloquante("Aide-mémoire d'invitation non enregistré", it) }

        li.velvetchat.core.model.CodeInvitation.encoder(jeton, idEspace)
    }

    override suspend fun revoquerJeton(jeton: String) = guard(
        tr("Révocation impossible", "Could not revoke")
    ) {
        val jetonEncode = java.net.URLEncoder.encode(jeton, "UTF-8")
        // under MAS a token is revoked by its id. one already gone answers like synapse's 404
        val reponse = if (sousMas()) {
            jetonsMas().firstOrNull { it.second.texte("token") == jeton }
                ?.let { requeteMas("POST", "api/admin/v1/user-registration-tokens/${it.first}/revoke") }
                ?: Reponse(404, "")
        } else {
            requeteSignee("DELETE", "/_synapse/admin/v1/registration_tokens/$jetonEncode")
        }
        // 404 means already gone, so the wanted result is already reached. same call as for
        // unpinning and extended profiles
        if (reponse.code !in 200..299 && reponse.code != 404) {
            throw EngineException.Unexpected(tr("Révocation refusée (${reponse.code})", "Revocation refused (${reponse.code})"))
        }
        runCatching { ecrireEspacesDesJetons(espacesDesJetons() - jeton) }
            .onFailure { erreurNonBloquante("Aide-mémoire d'invitation non purgé", it) }
    }

    override suspend fun comptesServeur(): List<CompteServeur> = guardListe {
        val reponse = requeteSignee("GET", "/_synapse/admin/v2/users?guests=false&deactivated=true&locked=true")
        if (reponse.code != 200) return@guardListe emptyList()
        val racine = kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
            as? kotlinx.serialization.json.JsonObject ?: return@guardListe emptyList()
        val comptes = racine["users"] as? kotlinx.serialization.json.JsonArray
            ?: return@guardListe emptyList()

        // under MAS an account is shut by locking it there, which synapse does not see
        val fermesMas: Set<String> = if (!sousMas()) emptySet() else {
            val r = requeteMas("GET", "api/admin/v1/users?page%5Bfirst%5D=1000")
            ressourcesMas(r.corps).filter { (_, a) ->
                a["locked_at"] !is kotlinx.serialization.json.JsonNull && a["locked_at"] != null ||
                    a["deactivated_at"] !is kotlinx.serialization.json.JsonNull && a["deactivated_at"] != null
            }.mapNotNull { it.second.texte("username") }.toSet()
        }

        comptes.mapNotNull { element ->
            val objet = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val userId = (objet["name"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                ?: return@mapNotNull null
            CompteServeur(
                userId = userId,
                displayName = (objet["displayname"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull,
                administrateur = (objet["admin"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull == "1"
                    || (objet["admin"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull == true,
                desactive = (objet["deactivated"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull == "1"
                    || (objet["deactivated"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull == true
                    || (objet["locked"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull == true
                        && !sousMas()
                    || userId.removePrefix("@").substringBefore(':') in fermesMas,
            )
        }
    }

    override suspend fun setCompteDesactive(
        userId: String,
        desactive: Boolean,
    ) = guard(tr("Changement d'état du compte impossible", "Could not change the account's state")) {
        if (sousMas()) {
            // locked rather than deactivated: under MAS it is reversible, password included
            val nom = java.net.URLEncoder.encode(userId.removePrefix("@").substringBefore(':'), "UTF-8")
            val r = requeteMas("GET", "api/admin/v1/users/by-username/$nom")
            val id = (objetJson(r.corps)?.get("data") as? kotlinx.serialization.json.JsonObject)?.texte("id")
                ?: throw EngineException.Unexpected(tr("Compte introuvable (${r.code})", "Account not found (${r.code})"))
            val action = requeteMas("POST", "api/admin/v1/users/$id/${if (desactive) "lock" else "unlock"}")
            if (action.code !in 200..299) throw EngineException.Unexpected(tr("Refusé (${action.code}) : ${action.corps}", "Refused (${action.code}): ${action.corps}"))
            return@guard
        }
        val userIdEncode = java.net.URLEncoder.encode(userId, "UTF-8")
        val corps = kotlinx.serialization.json.buildJsonObject {
            put("deactivated", desactive)
        }.toString()
        val reponse = requeteSignee("PUT", "/_synapse/admin/v2/users/$userIdEncode", corps)
        if (reponse.code !in 200..299) {
            throw EngineException.Unexpected(tr("Refusé (${reponse.code}) : ${reponse.corps}", "Refused (${reponse.code}): ${reponse.corps}"))
        }
    }

    /**
     * a failure that must not fail the call, and must not vanish either. a swallowed error gets
     * hunted for in the wrong place later
     *
     * `System.err` and not `android.util.Log`, because this module is shared with the desktop
     * target, which has no android log. smallest thing that works on both sides
     */
    /**
     * one read of a flow shared on [supervisor], or null when it fails
     *
     * an exception there has no collector to land in: it goes up to the supervisor and takes the
     * process with it. seen on the phone: one server answering 403 during a sync, app closed.
     * a failed read now skips its turn and the screen keeps the last list, until the next signal
     */
    private suspend inline fun <T> ouRien(quoi: String, bloc: () -> T): T? = try {
        bloc()
    } catch (annulation: kotlinx.coroutines.CancellationException) {
        throw annulation
    } catch (t: Throwable) {
        erreurNonBloquante(quoi, t)
        null
    }

    private fun erreurNonBloquante(quoi: String, t: Throwable) {
        System.err.println("[Velvet] $quoi : ${t.message ?: t::class.simpleName}")
    }

    private inline fun <T> guardListe(block: () -> List<T>): List<T> = try {
        block()
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Lecture du centre de contrôle impossible", "Could not read the control centre"), t)
    }

    /** like `guard`, but for a call returning a value rather than `Unit` */
    private inline fun <T> guardValeur(message: String, block: () -> T): T = try {
        block()
    } catch (t: EngineException) {
        throw t
    } catch (t: Throwable) {
        throw EngineException.Unexpected(message, t)
    }

    override suspend fun directs(): List<DirectMessage> = try {
        val client = requireClient()
        val roomList = syncService?.roomListService()

        // `m.direct` maps a user id to a LIST of rooms, and every one of them is a real
        // conversation. we used to keep the most recent and drop the others, on the theory that
        // nobody deliberately makes two dms with the same person
        //
        // somebody does: the server. one account here had two with another, the import with its
        // four hundred thousand messages, and an empty encrypted room created a couple of days
        // later. the import's last message is from september, the empty room's most recent event
        // is a membership change from this morning, so on recency the empty one won and four
        // hundred thousand messages were simply not in the app. element lists both rooms and
        // showed them fine, which is what made it look like a velvet bug. it was one
        //
        // no replacement heuristic, because that is what broke: any rule for "which single room
        // wins" is wrong the day the data disagrees with it. we keep them all and let
        // `distinctBy` below handle the one case that really is a duplicate, the same room filed
        // under two people
        val brut = client.accountData("m.direct") ?: return emptyList()
        val carte = kotlinx.serialization.json.Json.parseToJsonElement(brut)
            as? kotlinx.serialization.json.JsonObject ?: return emptyList()

        // one read for the whole list. a nickname is chosen per person but read per account,
        // and re reading it inside the loop would be n reads of something that does not change
        // from one conversation to the next
        val etiquettes = runCatching { surnoms() }.getOrDefault(emptyMap())

        carte.flatMap { (userId, valeur) ->
            val salons = (valeur as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                .orEmpty()

            salons.mapNotNull { roomId ->
                val item = runCatching { roomList?.room(roomId) }.getOrNull()
                    ?: return@mapNotNull null
                val info = runCatching { item.roomInfo() }.getOrNull()
                    ?: return@mapNotNull null
                // the last event is asked of the room LIST and not of `RoomInfo`, which
                // describes the room's state rather than its contents. the distinction is not
                // free: the first is cached, the second can cost a round trip
                val dernier = runCatching { item.latestEvent() }.getOrNull()
                    as? org.matrix.rustcomponents.sdk.LatestEventValue.Remote
                // a left room stays in `m.direct`, the protocol does not tidy that list.
                // without this filter you would see conversations you can no longer open
                if (info.membership != org.matrix.rustcomponents.sdk.Membership.JOINED) {
                    return@mapNotNull null
                }
                // the room name rather than the profile. matrix already names a direct room
                // after the other person, and reading it elsewhere risks showing two different
                // names for the same conversation
                val nomReel = info.displayName
                    ?: userId.removePrefix("@").substringBefore(':')
                DirectMessage(
                    roomId = roomId,
                    userId = userId,
                    name = etiquettes[userId]?.takeIf { it.isNotBlank() } ?: nomReel,
                    nomReel = nomReel,
                    // a direct conversation has no room avatar, it has the person's.
                    // `info.avatarUrl` is often empty, which showed up on the phone as no
                    // avatar in the rail and none in the header
                    avatarUrl = info.avatarUrl
                        ?: info.heroes.firstOrNull { it.userId == userId }?.avatarUrl
                        ?: runCatching { item.memberAvatarUrl(userId) }.getOrNull(),
                    preview = dernier?.let { apercuDe(it.content) },
                    timestamp = dernier?.timestamp?.toLong(),
                    unread = Unread(
                        notifications = info.numUnreadNotifications.toInt(),
                        highlights = info.numUnreadMentions.toInt(),
                        marqueNonLu = info.isMarkedUnread,
                    ),
                    pinned = roomId in epingles(),
                    groupe = info.joinedMembersCount > 2uL,
                )
            }
        }
            // one room, one conversation. `m.direct` maps rooms under every id, so the same
            // room turns up under two people as soon as a conversation was recorded from both
            // sides. flattened as is, it came out twice, and a compose list that sees the same
            // key twice does not complain, it kills the app
            //
            // deduplicated here and not in each screen, because this is the one place the
            // phone's list, the desktop column and the notifications all go through
            .distinctBy { it.roomId }
            .sortedWith(
                // pinned first, then by activity. sorted here because it is the order every
                // view will show, and leaving it to the caller would make each screen rediscover
                // the rule, then drift at the first one that forgets
                compareByDescending<DirectMessage> { it.pinned }
                    .thenByDescending { it.timestamp ?: 0L },
            )
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Lecture des conversations privées impossible", "Could not read the direct messages"), t)
    }

    /**
     * the nicknames, kept in memory
     *
     * the timeline builds its messages in non suspending code, so it cannot go and read account
     * data at the moment it puts a name down. this copy refreshes on every read and every write
     * of a nickname, so at app startup and on every change
     */
    @Volatile
    private var surnomsMemoire: Map<String, String> = emptyMap()

    private suspend fun surnomsEnCache(): Map<String, String> =
        runCatching { surnoms() }.getOrDefault(surnomsMemoire)

    override suspend fun surnoms(): Map<String, String> = runCatching {
        val brut = requireClient().donneeDeCompte(TYPE_SURNOMS) ?: return emptyMap()
        val racine = kotlinx.serialization.json.Json.parseToJsonElement(brut)
            as? kotlinx.serialization.json.JsonObject ?: return emptyMap()
        (racine["surnoms"] as? kotlinx.serialization.json.JsonObject)
            ?.mapNotNull { (id, v) ->
                val texte = (v as? kotlinx.serialization.json.JsonPrimitive)
                    ?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to texte
            }?.toMap().orEmpty().also { surnomsMemoire = it }
        // quiet when unreadable. a missing nickname shows the real name, which is the
        // degradation we want. failing the conversation list over a label would lose the
        // important thing for the decorative one
    }.getOrDefault(emptyMap())

    override suspend fun setSurnom(userId: String, surnom: String?) {
        val propre = surnom?.trim()?.takeIf { it.isNotBlank() }
        // the whole table gets rewritten. account data replaces, it does not merge. same call
        // as the rail order right next door
        val table = surnoms().toMutableMap()
        if (propre == null) table.remove(userId) else table[userId] = propre
        requireClient().setAccountData(
            TYPE_SURNOMS,
            kotlinx.serialization.json.JsonObject(
                mapOf("surnoms" to kotlinx.serialization.json.JsonObject(
                    table.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }
                ))
            ).toString(),
        )
        surnomsMemoire = table
        // the conversation list carries the name. without this poke, the nickname would only
        // show up there on the next message received
        oublierStructure()
    }

    /**
     * a client api response, cut down to what the callers use
     *
     * the body is read either way, success or error, because a matrix error body carries the
     * `errcode` and the reason, and throwing those away means reporting "it failed" without
     * saying why
     */
    private data class Reponse(val code: Int, val corps: String)

    /**
     * the one road to the hand signed client api
     *
     * six copy pasted `HttpURLConnection` blocks used to live in this file, each with its own
     * timeouts, authorization header and error handling. they differed only by method and path,
     * and none of them handled 429. synapse rate limits presence changes (`rc_presence`, two a
     * minute by default), and a single "go away, come back" round trip is enough to trip it
     *
     * the consequence in the app was worse than a failing runner. `setPresence` is wrapped in a
     * `runCatching`, so the 429 was swallowed there in silence and choosing your status simply
     * did nothing, without a word. an error turned into a normal case by a catch
     *
     * a 429 is not an error, it is an instruction. the server says how long to wait, in
     * `retry_after_ms`. we wait, then try again, up to [tentatives] times and no further: a loop
     * that insists forever turns a delay into a hang, and on mobile into a flat battery
     *
     * what does not get retried: everything else. a 403 does not improve with insistence and a
     * 404 even less. only the code that explicitly says "later" is tried again
     */
    private suspend fun requeteSignee(
        methode: String,
        chemin: String,
        corps: String? = null,
        tentatives: Int = 3,
        /**
         * how long the server is allowed to hold the line
         *
         * fifteen seconds suits a request that answers straight away. a blocking `/sync` is built
         * to answer only when it has something to say, so with the ordinary timeout it would be
         * cut off before it had anything to report, and the real time it brings would be lost.
         * hence a knob rather than one value for everybody
         */
        lectureMs: Int = 15_000,
        /** another server than the homeserver, MAS for instance. [chemin] is then relative to it */
        base: String? = null,
    ): Reponse = withContext(Dispatchers.IO) {
        var session = currentSession()
        var attenteTotale = 0L
        var derniere: Reponse? = null
        var renouvele = false

        for (essai in 0 until tentatives) {
            val url = java.net.URI.create(
                if (base != null) base + chemin else session.homeserverUrl.trimEnd('/') + chemin
            ).toURL()
            val cnx = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = methode
                setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                if (corps != null) {
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                connectTimeout = 15_000
                readTimeout = lectureMs
            }
            val reponse = try {
                corps?.let { charge -> cnx.outputStream.use { it.write(charge.toByteArray()) } }
                val code = cnx.responseCode
                // `use` and not a bare `readText`, and StrictMode had to say it out loud:
                // reading a stream to the end does not close it, and `disconnect()` tears the
                // connection down without closing the reader that was opened on it. every
                // signed request therefore left a descriptor for the collector to notice,
                // which it eventually does, quietly and much later. this path serves the
                // extended profiles, so it runs once per member of every room opened
                val texte = (if (code in 200..299) cnx.inputStream else cnx.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                Reponse(code, texte)
            } finally {
                cnx.disconnect()
            }
            derniere = reponse

            // an oauth token lives minutes. the sdk refreshes it on its own requests only, so an
            // expired one is handed to it once: any authenticated call makes it refresh, and the
            // session read again carries the new token
            if (reponse.code == 401 && !renouvele && session.refreshToken != null) {
                renouvele = true
                runCatching { requireClient().getProfile(session.userId) }
                session = currentSession()
                continue
            }

            if (reponse.code != 429 || essai == tentatives - 1) break

            // the delay comes from the server. failing that, a second: long enough to let a
            // narrow limit pass, short enough not to block a gesture
            val delai = delaiDeReprise(reponse.corps) ?: 1_000L
            if (attenteTotale + delai > ATTENTE_MAX_MS) break
            attenteTotale += delai
            delay(delai)
        }
        derniere ?: error(tr("requête abandonnée sans réponse", "request given up without an answer"))
    }

    private fun delaiDeReprise(corps: String): Long? = runCatching {
        (kotlinx.serialization.json.Json.parseToJsonElement(corps)
            as? kotlinx.serialization.json.JsonObject)
            ?.get("retry_after_ms")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() }
    }.getOrNull()

    override suspend fun setPinned(roomId: String, pinned: Boolean) {
        // the sdk does not expose room tags. it can read them from an account data flow but not
        // write them, so we go back through the hand signed client api, like the state event
        // reads do (see `lireEtat`)
        val session = currentSession()
        val salon = java.net.URLEncoder.encode(roomId, "UTF-8")
        val moi = java.net.URLEncoder.encode(session.userId, "UTF-8")

        val reponse = requeteSignee(
            methode = if (pinned) "PUT" else "DELETE",
            chemin = "/_matrix/client/v3/user/$moi/rooms/$salon/tags/$TAG_FAVORI",
            // `order` ranks favourites against each other. we do not use it, but an empty
            // body makes some implementations fail
            corps = if (pinned) "{}" else null,
        )
        // a 404 on a delete means it was not pinned. the wanted result is already reached, and
        // throwing here would fail a successful unpin
        if (reponse.code !in 200..299 && !(reponse.code == 404 && !pinned)) {
            throw EngineException.Unexpected(
                tr("Épinglage refusé (${reponse.code}) : ${reponse.corps}", "Pin refused (${reponse.code}): ${reponse.corps}")
            )
        }
        // the cache follows the gesture rather than wait for the re read. without this line,
        // pinning a conversation would leave it unpinned on screen until the next tag sweep,
        // which is precisely while you are watching to see whether it worked
        verrouEpingles.withLock {
            val avant = epinglesEnMemoire ?: lireCacheEpingles()
            val apres = if (pinned) avant + roomId else avant - roomId
            epinglesEnMemoire = apres
            ecrireCacheEpingles(apres)
        }
    }

    /**
     * pinned conversations, kept on the device
     *
     * there is no global endpoint for tags, `/user/{id}/rooms/tags` answers 404, so reading them
     * costs one request per conversation, and `directs()` re read all of them on every pass of
     * the room flow. on twenty conversations refreshed on every event that is the daftest
     * expense in the app: a set of ids that changes twice a month, asked for twenty times a
     * minute
     *
     * the disk answers immediately and the sweep goes out behind it, one at a time, rewriting
     * the file if it finds something else. so another client pinning on its side shows up on the
     * next pass, which is the right trade for a display preference
     */
    private val fichierCacheEpingles by lazy { java.io.File(dataPath, "epingles.json") }
    private val verrouEpingles = Mutex()

    /**
     * an immutable set, replaced, never modified in place
     *
     * the first version kept a `MutableSet` and edited it under a lock. the lock was not enough:
     * `epingles()` handed back that same instance to `directs()`, which reads it outside the
     * lock while the background sweep or a pin edits it. two threads on a `HashSet` with one of
     * them writing is undefined, and it is the kind of bug that shows up once in a thousand
     * times, so never while you are looking for it
     *
     * replacing the instance makes the read safe with no lock: whoever got it holds a photo that
     * will not move
     */
    @Volatile
    private var epinglesEnMemoire: Set<String>? = null
    private val relectureEpingles = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun lireCacheEpingles(): Set<String> = runCatching {
        val brut = fichierCacheEpingles.takeIf { it.isFile }?.readText() ?: return emptySet()
        kotlinx.serialization.json.Json.decodeFromString<Set<String>>(brut)
    }.getOrDefault(emptySet())

    private fun ecrireCacheEpingles(quoi: Set<String>) {
        runCatching {
            fichierCacheEpingles.writeText(kotlinx.serialization.json.Json.encodeToString(quoi))
        }
    }

    /** the rooms `m.direct` names and nothing else, which is as far as the tag sweep goes */
    private suspend fun salonsDirects(): List<String> {
        val brut = requireClient().accountData("m.direct") ?: return emptyList()
        val carte = kotlinx.serialization.json.Json.parseToJsonElement(brut)
            as? kotlinx.serialization.json.JsonObject ?: return emptyList()
        return carte.values.flatMap { valeur ->
            (valeur as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                .orEmpty()
        }.distinct()
    }

    private suspend fun epingles(): Set<String> {
        val connues = verrouEpingles.withLock {
            epinglesEnMemoire ?: lireCacheEpingles().also { epinglesEnMemoire = it }
        }
        if (relectureEpingles.compareAndSet(false, true)) {
            supervisor.launch {
                try {
                    // direct rooms only. nobody here needs the others' tags, and the sweep has
                    // to stay proportionate to what we draw
                    val salons = runCatching { salonsDirects() }.getOrDefault(emptyList())
                    if (salons.isEmpty()) return@launch
                    val jauge = Semaphore(8)
                    val lus = coroutineScope {
                        salons.map { salon ->
                            async { salon.takeIf { jauge.withPermit { estEpingle(salon) } } }
                        }.awaitAll()
                    }.filterNotNull().toSet()
                    verrouEpingles.withLock {
                        if (lus != epinglesEnMemoire) {
                            epinglesEnMemoire = lus
                            ecrireCacheEpingles(lus)
                        }
                    }
                } finally {
                    relectureEpingles.set(false)
                }
            }
        }
        return connues
    }

    /**
     * does this room carry the favourite tag
     *
     * one request per room and not a global one, because there is no global endpoint:
     * `/user/{id}/rooms/tags` answers 404 and tags read room by room. the first version assumed
     * otherwise, and the failure was swallowed by a silent catch, so a successful pin (put,
     * 200) still came back as `pinned = false`
     */
    private suspend fun estEpingle(roomId: String): Boolean {
        val session = currentSession()
        val moi = java.net.URLEncoder.encode(session.userId, "UTF-8")
        val salon = java.net.URLEncoder.encode(roomId, "UTF-8")

        val reponse = runCatching {
            requeteSignee("GET", "/_matrix/client/v3/user/$moi/rooms/$salon/tags")
        }.getOrNull() ?: return false
        if (reponse.code != 200) return false

        // an unreadable tag list must not stop the conversation being shown. we lose the pin,
        // not the conversation. different from the case above: here we know the request happened,
        // since the response code was read
        return runCatching {
            val racine = kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                as? kotlinx.serialization.json.JsonObject
            (racine?.get("tags") as? kotlinx.serialization.json.JsonObject)
                ?.containsKey(TAG_FAVORI) == true
        }.getOrDefault(false)
    }

    override suspend fun openDirect(userId: String): String {
        // look for an existing conversation first. without that, writing to the same person
        // twice would create two rooms and split the history
        //
        // `!groupe` is not a refinement, it is what stops the lookup landing on a group. a group
        // is filed in `m.direct` under every member, so once one exists with this person, the
        // first match for their id can be the group, and asking for a one to one would have
        // opened the three person room instead
        directs().firstOrNull { it.userId == userId && !it.groupe }?.let { return it.roomId }

        // then adopt before creating. see [adopterSalonExistant]: a two person room that is
        // in no space and in no `m.direct` is invisible in the app, and making a second one on
        // top would split the history and hide it
        adopterSalonExistant(userId)?.let { return it }

        return try {
            requireClient().createRoom(
                org.matrix.rustcomponents.sdk.CreateRoomParameters(
                    name = null,
                    // `isDirect` does much more than label. it is what makes the server add
                    // the room to `m.direct` on both sides, so it shows up in the other
                    // person's list too
                    isDirect = true,
                    // encrypted like everything else, and a two person conversation is
                    // precisely where people expect it most
                    isEncrypted = true,
                    visibility = org.matrix.rustcomponents.sdk.RoomVisibility.Private,
                    preset = org.matrix.rustcomponents.sdk.RoomPreset.TRUSTED_PRIVATE_CHAT,
                    invite = listOf(userId),
                )
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Ouverture de la conversation impossible", "Could not open the conversation"), t)
        }
    }

    override suspend fun creerGroupeDirect(userIds: List<String>): String {
        // yourself out, duplicates out, see `invitesDeGroupe`: the rule lives in common code so
        // it can be tested without a homeserver, which is where its three traps are written down
        val invites = li.velvetchat.core.invitesDeGroupe(userIds, currentSession().userId)
        require(li.velvetchat.core.groupeAssezGrand(invites)) {
            tr("un groupe demande au moins deux autres personnes", "a group needs at least two other people")
        }

        return try {
            requireClient().createRoom(
                org.matrix.rustcomponents.sdk.CreateRoomParameters(
                    // no name, like the reference: a group is named after its members, and a room
                    // name would freeze a list that changes as people come and go
                    name = null,
                    // `isDirect` on a room with several invitees is what matrix has in place of a
                    // group direct message. it is what puts the room in everyone's `m.direct`, so
                    // it lands in their conversation list and not in their server rail
                    isDirect = true,
                    isEncrypted = true,
                    visibility = org.matrix.rustcomponents.sdk.RoomVisibility.Private,
                    preset = org.matrix.rustcomponents.sdk.RoomPreset.TRUSTED_PRIVATE_CHAT,
                    invite = invites,
                )
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Creation du groupe impossible", "Could not create the group"), t)
        }
    }

    /**
     * a preview line built from the last event
     *
     * null rather than a fallback string when the event is not text. a generic "[image]" teaches
     * nothing the absence of a line does not, and the row contracts when it has nothing to say
     */
    private fun apercuDe(contenu: TimelineItemContent): String? {
        val msgLike = (contenu as? TimelineItemContent.MsgLike)?.content ?: return null
        val kind = msgLike.kind as? MsgLikeKind.Message ?: return null
        // `body` already carries readable text whatever the type, but we only want it for
        // text. an image's `body` is its filename, and "IMG_4821.jpg" as a preview teaches
        // nothing an empty line does not
        val message = kind.content
        val texte = message.msgType as? MessageType.Text ?: return null

        // stripped html beats `body`, and that is not a preference
        //
        // when a client sends markdown the protocol puts the source in `body` and the rendering
        // in `formatted_body`. a preview taken from `body` therefore prints `**bold** and
        // _italic_`, literally. html with its tags removed gives "bold and italic", which is
        // what the person actually wrote
        val source = texte.content.formatted?.body?.let(::texteSansBalises)?.ifBlank { null }
            ?: message.body
        return source.replace('\n', ' ').take(80).ifBlank { null }
    }

    override suspend fun roleTiers(serverId: String): List<RoleTier> {
        val racine = lireEtat(serverId, TYPE_ROLES) ?: return emptyList()
        val paliers = (racine["tiers"] as? kotlinx.serialization.json.JsonArray) ?: return emptyList()
        return paliers.mapNotNull { element ->
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            fun texte(cle: String) =
                (obj[cle] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { !it.isString || it.content.isNotBlank() }?.content
            val power = texte("power")?.toLongOrNull() ?: return@mapNotNull null
            RoleTier(
                power = power,
                name = texte("name") ?: return@mapNotNull null,
                // the colour is written as `#RRGGBB` so the state event stays readable. a
                // signed integer in there would be unreadable to anyone inspecting it by hand
                colorArgb = texte("color")?.let { couleurVersArgb(it) },
                icon = texte("icon"),
            )
        }.sortedByDescending { it.power }
    }

    override suspend fun setRoleTiers(serverId: String, tiers: List<RoleTier>) {
        val contenu = kotlinx.serialization.json.buildJsonObject {
            putJsonArray("tiers") {
                tiers.sortedByDescending { it.power }.forEach { tier ->
                    addJsonObject {
                        put("power", kotlinx.serialization.json.JsonPrimitive(tier.power))
                        put("name", kotlinx.serialization.json.JsonPrimitive(tier.name))
                        tier.colorArgb?.let {
                            put("color", kotlinx.serialization.json.JsonPrimitive(argbVersCouleur(it)))
                        }
                        tier.icon?.let { put("icon", kotlinx.serialization.json.JsonPrimitive(it)) }
                    }
                }
            }
        }.toString()
        ecrireEtat(serverId, TYPE_ROLES, contenu)
    }

    override suspend fun inventaireDecorations(
        serverId: String,
        famille: String,
    ): List<li.velvetchat.core.model.DecorationAvatar> {
        return entreesCatalogue(lireEtat(serverId, typeCosmetique(famille)))
    }

    /** decodes one catalogue page. shared by the per server read and the global one */
    private fun entreesCatalogue(
        racine: kotlinx.serialization.json.JsonObject?,
    ): List<li.velvetchat.core.model.DecorationAvatar> {
        val entrees = (racine?.get("decorations") as? kotlinx.serialization.json.JsonArray)
            ?: return emptyList()
        return entrees.mapNotNull { element ->
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            fun texte(cle: String) = (obj[cle] as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull?.takeIf { it.isNotBlank() }
            li.velvetchat.core.model.DecorationAvatar(
                id = texte("id") ?: return@mapNotNull null,
                nom = texte("nom") ?: return@mapNotNull null,
                // with no image the entry can draw nothing, so we drop it rather than leave a
                // clickable hole in the catalogue
                mxc = texte("mxc") ?: return@mapNotNull null,
                anime = (obj["anime"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.booleanOrNull ?: false,
                apercu = texte("apercu"),
                pack = texte("pack"),
                theme = texte("theme"),
                // a frame's bottom half, when the entry carries one. absent everywhere else,
                // and absent from frames published before we knew they come in two pieces, in
                // which case the display falls back on the single image
                bas = texte("bas"),
                calques = (obj["calques"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { c ->
                    val o = c as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    fun champ(cle: String) = (o[cle] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    li.velvetchat.core.model.CalqueCadre(
                        mxc = champ("mxc") ?: return@mapNotNull null,
                        derriere = champ("ordre") == "back",
                        ancre = champ("ancre") ?: return@mapNotNull null,
                        largeur = champ("l")?.toIntOrNull() ?: return@mapNotNull null,
                        hauteur = champ("h")?.toIntOrNull() ?: return@mapNotNull null,
                        type = champ("type"),
                    )
                },
                debordHaut = texte("debord_haut")?.toIntOrNull() ?: 126,
                debordBas = texte("debord_bas")?.toIntOrNull() ?: 116,
                debordCote = texte("debord_cote")?.toIntOrNull() ?: 56,
            )
        }
    }

    /**
     * the cosmetics, kept on the device
     *
     * one family costs one state request per server, and on an account holding
     * several, opening the frame grid or drawing one profile frame cost that whole sweep every
     * time, for a list that changes a few times a month
     *
     * the disk answers immediately and the re read goes out behind it: the grid opens full, and
     * anything published recently lands a second later with nothing having waited. an empty
     * catalogue is not kept, see [ecrireCacheDecors]
     */
    override suspend fun toutesLesDecorations(
        famille: String,
    ): List<li.velvetchat.core.model.DecorationAvatar> {
        cacheDecors(famille)?.let { garde ->
            // background re read, one at a time per family. four frames on screen must not
            // trigger four sweeps of every server
            if (relectures.add(famille)) {
                supervisor.launch {
                    try {
                        ecrireCacheDecors(famille, lireDecorationsDuReseau(famille))
                    } finally {
                        relectures.remove(famille)
                    }
                }
            }
            return garde
        }
        return lireDecorationsDuReseau(famille).also { ecrireCacheDecors(famille, it) }
    }

    private suspend fun lireDecorationsDuReseau(
        famille: String,
    ): List<li.velvetchat.core.model.DecorationAvatar> {
        val vues = mutableSetOf<String>()

        // the global catalogue first. it is the same for everyone, so it is what should open
        // the grid, and whatever a server adds comes after as a supplement. the other order
        // would make the display depend on which server happens to be open
        val global = essayer { catalogueGlobal(famille) }.getOrDefault(emptyList())

        // **`servers()` was the one unguarded call on this path**
        //
        // the per server read below has always been wrapped, with the comment saying that one
        // unreadable catalogue must not empty everyone else's. the listing itself was not. so a
        // `servers()` that failed — a slow sync, one room that will not load — threw out of this
        // function, the caller's `runCatching` turned it into an empty list, and **the global
        // catalogue already in hand was thrown away with it**. an account with one server barely
        // meets it; an account with many meets it exactly where it hurts
        //
        // the global one is now kept whatever the servers do, which is what the sentence above
        // was already promising
        val desServeurs = essayer {
            servers().flatMap { serveur ->
                essayer { inventaireDecorations(serveur.id, famille) }.getOrDefault(emptyList())
            }
        }.getOrElse {
            erreurNonBloquante("Liste des serveurs illisible pour le catalogue « $famille »", it)
            emptyList()
        }

        // deduplicated on the image and not the id. two servers publishing the same ring give it
        // two different ids, and offering it twice would suggest two different decorations
        return (global + desServeurs).filter { vues.add(it.mxc) }
            // said out loud, because an empty grid and a catalogue nobody published look the
            // same from the outside, and that is exactly the pair that cost a day
            .also {
                if (it.isEmpty()) {
                    erreurNonBloquante(
                        "Catalogue « $famille » vide : global=${global.size}, " +
                            "serveurs=${desServeurs.size}",
                        IllegalStateException("rien a afficher"),
                    )
                }
            }
    }

    /** the families with a background re read already in flight */
    private val relectures = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** same place and same lifetime as the other caches, see `lireCacheServeurs` */
    private val fichierCacheDecors by lazy { java.io.File(dataPath, "decorations.json") }
    private val verrouDecors = Mutex()

    /** family to list, read off disk once then held in memory */
    private var decorsEnMemoire: MutableMap<String, List<li.velvetchat.core.model.DecorationAvatar>>? = null

    private suspend fun cacheDecors(
        famille: String,
    ): List<li.velvetchat.core.model.DecorationAvatar>? = verrouDecors.withLock {
        val carte = decorsEnMemoire ?: runCatching {
            val brut = fichierCacheDecors.takeIf { it.isFile }?.readText()
            if (brut == null) {
                mutableMapOf()
            } else {
                kotlinx.serialization.json.Json.decodeFromString<Map<String, List<DecorEnCache>>>(brut)
                    .mapValues { (_, v) -> v.map { it.versModele() } }
                    .toMutableMap()
            }
        }.getOrDefault(mutableMapOf()).also { decorsEnMemoire = it }
        // frames cached before their layers carried a type: a rail would be drawn as a staple,
        // so the network answers this once instead
        carte[famille]?.takeUnless { l -> l.any { d -> d.calques.any { it.type == null } } }
    }

    /**
     * an empty result is not kept. [lireDecorationsDuReseau] catches its errors server by server
     * and returns an empty list rather than a failure, so "empty" means both "nothing to show"
     * and "everything failed". writing that empty would wipe a cache over a dropped connection
     */
    private suspend fun ecrireCacheDecors(
        famille: String,
        liste: List<li.velvetchat.core.model.DecorationAvatar>,
    ) {
        if (liste.isEmpty()) return
        verrouDecors.withLock {
            val carte = decorsEnMemoire ?: mutableMapOf<String, List<li.velvetchat.core.model.DecorationAvatar>>()
                .also { decorsEnMemoire = it }
            if (carte[famille] == liste) return
            carte[famille] = liste
            runCatching {
                fichierCacheDecors.writeText(
                    kotlinx.serialization.json.Json.encodeToString(
                        carte.mapValues { (_, v) -> v.map { DecorEnCache.de(it) } },
                    ),
                )
            }
        }
    }

    override suspend fun lienInvitation(serverId: String): String {
        val regle = (lireEtat(serverId, "m.room.join_rules")?.get("join_rule")
            as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        if (regle != "public") {
            ecrireEtat(serverId, "m.room.join_rules", """{"join_rule":"public"}""")
        }
        return li.velvetchat.core.model.LienInvitation.encoder(serverId)
    }

    override suspend fun profilServeur(serverId: String): li.velvetchat.core.model.ProfilServeur {
        val evenements = etatDuSalon(serverId)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            ?: throw EngineException.Unexpected(tr("État du serveur illisible : $serverId", "Unreadable server state: $serverId"))
        fun kotlinx.serialization.json.JsonObject.texte(cle: String) =
            (this[cle] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        fun contenu(type: String) = evenements.firstOrNull { it.texte("type") == type && it.texte("state_key") == "" }
            ?.get("content") as? kotlinx.serialization.json.JsonObject
        val profil = contenu(TYPE_PROFIL_SERVEUR)
        return li.velvetchat.core.model.ProfilServeur(
            nom = contenu("m.room.name")?.texte("name") ?: serverId,
            description = contenu("m.room.topic")?.texte("topic")?.takeIf { it.isNotBlank() },
            avatarUrl = contenu("m.room.avatar")?.texte("url"),
            banniere = profil?.texte("banniere")?.toIntOrNull() ?: 0,
            particularites = (profil?.get("particularites") as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                ?.map { li.velvetchat.core.model.Particularite(it.texte("emoji") ?: "", it.texte("texte") ?: "") }
                ?: emptyList(),
            membres = evenements.count {
                it.texte("type") == "m.room.member" &&
                    (it["content"] as? kotlinx.serialization.json.JsonObject)?.texte("membership") == "join"
            },
            creeLe = evenements.firstOrNull { it.texte("type") == "m.room.create" }?.texte("origin_server_ts")?.toLongOrNull(),
        )
    }

    override suspend fun setProfilServeur(
        serverId: String,
        profil: li.velvetchat.core.model.ProfilServeur,
    ) {
        val avant = profilServeur(serverId)
        fun json(vararg paires: Pair<String, Any?>) = kotlinx.serialization.json.buildJsonObject {
            paires.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(v?.toString() ?: "")) }
        }.toString()
        if (profil.nom != avant.nom) ecrireEtat(serverId, "m.room.name", json("name" to profil.nom))
        if (profil.description != avant.description) {
            ecrireEtat(serverId, "m.room.topic", json("topic" to profil.description))
        }
        if (profil.banniere != avant.banniere || profil.particularites != avant.particularites) {
            val contenu = kotlinx.serialization.json.buildJsonObject {
                put("banniere", kotlinx.serialization.json.JsonPrimitive(profil.banniere.toString()))
                put("particularites", kotlinx.serialization.json.JsonArray(
                    profil.particularites.take(li.velvetchat.core.model.ProfilServeur.PARTICULARITES_MAX).map {
                        kotlinx.serialization.json.buildJsonObject {
                            put("emoji", kotlinx.serialization.json.JsonPrimitive(it.emoji))
                            put("texte", kotlinx.serialization.json.JsonPrimitive(it.texte))
                        }
                    }
                ))
            }.toString()
            ecrireEtat(serverId, TYPE_PROFIL_SERVEUR, contenu)
        }
        // the state cache would serve the old profile for ten seconds, and the name is in the rail
        verrouEtat.withLock { etatEnCache = null }
        oublierStructure()
    }

    override suspend fun tagServeur(
        serverId: String,
    ): li.velvetchat.core.model.TagServeur? {
        val racine = lireEtat(serverId, TYPE_TAG) ?: return null
        fun texte(cle: String) = (racine[cle] as? kotlinx.serialization.json.JsonPrimitive)
            ?.contentOrNull?.takeIf { it.isNotBlank() }
        val emoji = texte("emoji") ?: return null
        val libelle = texte("texte") ?: return null
        return li.velvetchat.core.model.TagServeur(emoji, libelle)
    }

    override suspend fun setTagServeur(
        serverId: String,
        tag: li.velvetchat.core.model.TagServeur?,
    ) {
        // an empty object rather than a deleted event. matrix cannot remove a state event,
        // only write another one. the read above returns null on empty content, which comes to
        // the same thing for everyone
        val contenu = kotlinx.serialization.json.buildJsonObject {
            tag?.let {
                put("emoji", kotlinx.serialization.json.JsonPrimitive(it.emoji))
                put(
                    "texte",
                    kotlinx.serialization.json.JsonPrimitive(
                        it.texte.take(li.velvetchat.core.model.TagServeur.LONGUEUR_MAX)
                    ),
                )
            }
        }.toString()
        ecrireEtat(serverId, TYPE_TAG, contenu)
    }

    override suspend fun tagsDisponibles():
        List<Triple<String, String, li.velvetchat.core.model.TagServeur>> =
        servers().mapNotNull { serveur ->
            // per server, like everywhere else. one unreadable state must not empty the
            // others' list
            runCatching { tagServeur(serveur.id) }.getOrNull()
                ?.let { Triple(serveur.id, serveur.name, it) }
        }

    /**
     * the catalogue room's id, resolved once then kept
     *
     * `""` marks "already looked, not there". without that distinction a missing alias would
     * cost a failed resolution every time the picker opens
     */
    @Volatile
    private var salonCatalogue: String? = null

    override suspend fun catalogueGlobal(
        famille: String,
    ): List<li.velvetchat.core.model.DecorationAvatar> {
        val alias = Installation.ou({ this.catalogue }, aliasCatalogue.orEmpty()).takeIf { it.isNotBlank() } ?: return emptyList()
        val moi = runCatching { currentSession().userId }.getOrNull() ?: return emptyList()
        val intendant = Installation.ou({ this.intendant }, intendantCatalogue.orEmpty()).takeIf { it.isNotBlank() }

        // **who gets to see the frames is a room membership, not a hard coded name**
        //
        // this used to be `if (moi != intendant) return emptyList()`, which meant the global
        // catalogue existed for exactly one account and nobody else ever saw a single frame.
        // that isn't access control, that's an off switch
        //
        // the catalogue is a closed matrix room, so membership already answers the question:
        // whoever has been invited and joined can read it, everyone else gets a 403 and an
        // empty list. inviting is the owner's call, which is the whole point
        //
        // only the steward joins by itself. for anyone else, walking in uninvited would mean
        // the room has to be public, and then it isn't the owner's call any more
        val connu = salonCatalogue
        val salon = if (connu != null) connu else if (moi != intendant) {
            // already a member? then the alias resolves and the state reads. not a member?
            // nothing happens, and nothing is said — not being in the catalogue is normal
            runCatching { requireClient().resolveRoomAlias(alias)?.roomId }.getOrNull()
                ?.also { salonCatalogue = it }
                ?: return emptyList()
        } else {
            // we join rather than peek. matrix only lets members read a room's state, since
            // peeking is not deployed, so joining is the only way to read. and it has no visible
            // consequence, the room appears nowhere, see the contract
            //
            // `try/catch` and not `runCatching`, and that is a measured bug
            //
            // `runCatching` catches everything, `CancellationException` included. the caller is
            // a `LaunchedEffect` whose key changes at startup as the server list goes from empty
            // to full, so compose cancels the first run in the middle of resolving the alias.
            // the cancellation then read as a failure, and the negative result was memoised
            // permanently: the catalogue stayed empty for the life of the process, without a
            // single log line
            //
            // the trace showed a `GET /directory/room/…` going out, no response, and never a
            // second attempt. so the cache only keeps successes, and a real failure retries on
            // the next pass, which costs one request and avoids a permanent outage
            try {
                requireClient().joinRoomByIdOrAlias(alias, emptyList()).id()
                    .also { salonCatalogue = it }
            } catch (annulation: kotlinx.coroutines.CancellationException) {
                throw annulation
            } catch (t: Throwable) {
                erreurNonBloquante("Catalogue global « $alias » injoignable", t)
                return emptyList()
            }
        }

        return runCatching { cataloguePagine(salon, famille) }.getOrDefault(emptyList())
    }

    /** the last `/state` read: the room id, the moment, the response */
    @Volatile
    private var etatEnCache: Triple<String, Long, kotlinx.serialization.json.JsonArray>? = null

    /** serialises the state reads, see [etatDuSalon] */
    private val verrouEtat = Mutex()

    /**
     * a room's full state, kept for a few seconds
     *
     * opening the ornament picker reads three families. without this cache that is the same
     * entire state three times, several hundred kilobytes for a full catalogue, for three
     * different filters over it
     *
     * the lock, and why the cache alone was not enough. the symptom was "the nameplates are not
     * visible in the shop", while the catalogue held 270 of them, previews included. so it was
     * neither the publishing nor the display
     *
     * the three families read one after another, and the first does more than the others: it
     * joins the catalogue room, then reads its whole state. past the cache's ten seconds, which
     * happens on a mobile link with a state that size, the other two each went back for it. one
     * of those round trips fails and its family stays empty without a word, because null comes
     * back as an empty list, which is indistinguishable from a catalogue with no nameplates
     *
     * the lock lets all three go together: the first reads, the other two wait and find the
     * cache full. one request for three families, however long it takes
     */
    private suspend fun etatDuSalon(
        roomId: String,
    ): kotlinx.serialization.json.JsonArray? = verrouEtat.withLock {
        val garde = etatEnCache
        if (garde != null && garde.first == roomId &&
            System.currentTimeMillis() - garde.second < ETAT_TTL_MS
        ) {
            return@withLock garde.third
        }
        val salon = java.net.URLEncoder.encode(roomId, "UTF-8")
        // **this one response is big, and the ordinary timeout was sized for small ones**
        //
        // the cosmetics catalogue is fourteen state events of about twenty three kilobytes, so
        // the room's whole state is the better part of a megabyte. fifteen seconds is generous
        // on a lan and tight on a phone going through the tunnel, and a cut off read is
        // indistinguishable from an empty catalogue — the failure this path keeps producing
        //
        // forty five seconds, because the alternative to waiting is showing nothing
        val reponse = requeteSignee(
            "GET",
            "/_matrix/client/v3/rooms/$salon/state",
            lectureMs = 45_000,
        )
        if (reponse.code != 200) {
            // say why. a failed read returned null, which the caller turns into an empty
            // list, meaning "this catalogue offers nothing", which is false. the log tells the
            // two apart, and it is the only trace we will get of a catalogue that is missing
            // while being published
            erreurNonBloquante(
                tr("État du salon $roomId illisible (HTTP ${reponse.code})", "Unreadable state for channel $roomId (HTTP ${reponse.code})"),
                IllegalStateException(reponse.corps.take(200)),
            )
            return@withLock null
        }
        val evenements = kotlinx.serialization.json.Json
            .parseToJsonElement(reponse.corps) as? kotlinx.serialization.json.JsonArray
            ?: return@withLock null
        etatEnCache = Triple(roomId, System.currentTimeMillis(), evenements)
        evenements
    }

    private suspend fun cataloguePagine(
        roomId: String,
        famille: String,
    ): List<li.velvetchat.core.model.DecorationAvatar> {
        val type = typeCosmetique(famille)
        val evenements = etatDuSalon(roomId) ?: return emptyList()
        val objets = evenements.mapNotNull { it as? kotlinx.serialization.json.JsonObject }

        // **a catalogue published under a name we no longer read looks exactly like no catalogue**
        //
        // this cost the whole thing once, on 2026-10-06. `Identifiants.ANCIENS` was emptied on
        // 2026-09-30 on the grounds that what sat under the old prefix was "test bios and test
        // decorations nobody minds losing". it was also 1344 ornaments: the global catalogue's
        // fourteen state events were all named `<old prefix>avatar_decorations` and
        // `<old prefix>cosmetics.*`, so every picker in the app went empty while the data sat
        // untouched on the homeserver. no log line, no error, just an empty grid
        //
        // so when we find nothing, we say whether the room really is empty or whether it holds
        // the same thing under another name. the suffix is matched, never a domain: the point is
        // to notice a prefix nobody thought about, which hard coding one more prefix cannot do
        if (objets.none { (it["type"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull == type }) {
            val suffixe = type.removePrefix(li.velvetchat.core.model.Identifiants.PREFIXE)
            val sousUnAutreNom = objets.mapNotNull {
                (it["type"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            }.filter { it != type && it.endsWith(".$suffixe") }.distinct()
            if (sousUnAutreNom.isNotEmpty()) {
                erreurNonBloquante(
                    "Catalogue « $famille » publie sous ${sousUnAutreNom.joinToString()} " +
                        "et non sous $type : rien ne sera affiche. Voir Identifiants.ANCIENS",
                    IllegalStateException("prefixe non lu"),
                )
            }
        }

        return objets
            .filter {
                (it["type"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull == type
            }
            // sorted by state key. `/state` guarantees no order, and a grid whose contents
            // reshuffle every time you open it is annoying for no reason
            .sortedBy {
                (it["state_key"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    ?: ""
            }
            .flatMap { evenement ->
                val contenu = evenement["content"] as? kotlinx.serialization.json.JsonObject
                entreesCatalogue(contenu)
            }
    }

    override suspend fun setInventaireDecorations(
        serverId: String,
        decorations: List<li.velvetchat.core.model.DecorationAvatar>,
        famille: String,
    ) {
        // the whole table in one state event, like the role tiers. matrix state replaces
        // content rather than merging it, so writing entry by entry would send that many full
        // writes, and two quick edits would catch each other up
        val contenu = kotlinx.serialization.json.buildJsonObject {
            putJsonArray("decorations") {
                decorations.forEach { d ->
                    addJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive(d.id))
                        put("nom", kotlinx.serialization.json.JsonPrimitive(d.nom))
                        put("mxc", kotlinx.serialization.json.JsonPrimitive(d.mxc))
                        put("anime", kotlinx.serialization.json.JsonPrimitive(d.anime))
                    }
                }
            }
        }.toString()
        ecrireEtat(serverId, typeCosmetique(famille), contenu)
    }

    override suspend fun sonsSoundboard(
        serverId: String,
    ): List<li.velvetchat.core.model.SonSoundboard> {
        val racine = lireEtat(serverId, TYPE_SONS_SOUNDBOARD) ?: return emptyList()
        val entrees = (racine["sons"] as? kotlinx.serialization.json.JsonArray)
            ?: return emptyList()
        return entrees.mapNotNull { element ->
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            fun texte(cle: String) = (obj[cle] as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull?.takeIf { it.isNotBlank() }
            li.velvetchat.core.model.SonSoundboard(
                id = texte("id") ?: return@mapNotNull null,
                nom = texte("nom") ?: return@mapNotNull null,
                // with no file the tile can play nothing, so we drop it rather than leave a
                // silent button in a grid people tap blind during a call
                mxc = texte("mxc") ?: return@mapNotNull null,
                emoji = texte("emoji"),
            )
        }
    }

    override suspend fun setSonsSoundboard(
        serverId: String,
        sons: List<li.velvetchat.core.model.SonSoundboard>,
    ) {
        val contenu = kotlinx.serialization.json.buildJsonObject {
            putJsonArray("sons") {
                sons.forEach { s ->
                    addJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive(s.id))
                        put("nom", kotlinx.serialization.json.JsonPrimitive(s.nom))
                        put("mxc", kotlinx.serialization.json.JsonPrimitive(s.mxc))
                        s.emoji?.let { put("emoji", kotlinx.serialization.json.JsonPrimitive(it)) }
                    }
                }
            }
        }.toString()
        ecrireEtat(serverId, TYPE_SONS_SOUNDBOARD, contenu)
    }

    override suspend fun decorations(
        serverId: String,
    ): li.velvetchat.core.model.Decorations {
        val racine = lireEtat(serverId, TYPE_DECORATIONS)
            ?: return li.velvetchat.core.model.Decorations()

        val roles = (racine["roles"] as? kotlinx.serialization.json.JsonArray)
            .orEmpty()
            .mapNotNull { element ->
                val obj = element as? kotlinx.serialization.json.JsonObject
                    ?: return@mapNotNull null
                fun texte(cle: String) =
                    (obj[cle] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.content?.takeIf { it.isNotBlank() }
                li.velvetchat.core.model.RoleDecoratif(
                    id = texte("id") ?: return@mapNotNull null,
                    nom = texte("nom") ?: return@mapNotNull null,
                    colorArgb = texte("color")?.let { couleurVersArgb(it) },
                )
            }

        val parMembre = (racine["membres"] as? kotlinx.serialization.json.JsonObject)
            .orEmpty()
            .mapValues { (_, valeur) ->
                (valeur as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
            }
            // an empty entry clutters the state event without saying anything, so we drop it
            // on read rather than make every caller watch out for it
            .filterValues { it.isNotEmpty() }

        return li.velvetchat.core.model.Decorations(roles, parMembre)
    }

    override suspend fun setDecorations(
        serverId: String,
        decorations: li.velvetchat.core.model.Decorations,
    ) {
        // the assignments are filtered against the roles that still exist. deleting a role has
        // to take it off everyone, and leaving dead ids lying around would grow the state
        // forever
        val connus = decorations.roles.map { it.id }.toSet()
        val contenu = kotlinx.serialization.json.buildJsonObject {
            putJsonArray("roles") {
                decorations.roles.forEach { role ->
                    addJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive(role.id))
                        put("nom", kotlinx.serialization.json.JsonPrimitive(role.nom))
                        role.colorArgb?.let {
                            put(
                                "color",
                                kotlinx.serialization.json.JsonPrimitive(argbVersCouleur(it)),
                            )
                        }
                    }
                }
            }
            putJsonObject("membres") {
                decorations.parMembre.forEach { (userId, ids) ->
                    val gardes = ids.filter { it in connus }
                    if (gardes.isNotEmpty()) {
                        putJsonArray(userId) {
                            gardes.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        }
                    }
                }
            }
        }.toString()
        ecrireEtat(serverId, TYPE_DECORATIONS, contenu)
    }

    override suspend fun kick(serverId: String, userId: String, reason: String?) =
        surToutLeServeur(serverId) { it.kickUser(userId, reason) }

    override suspend fun ban(serverId: String, userId: String, reason: String?) =
        surToutLeServeur(serverId) { it.banUser(userId, reason) }

    override suspend fun unban(serverId: String, userId: String, reason: String?) =
        surToutLeServeur(serverId) { it.unbanUser(userId, reason) }

    override suspend fun setPowerLevel(serverId: String, userId: String, level: Long) =
        surToutLeServeur(serverId) {
            it.updatePowerLevelsForUsers(
                listOf(org.matrix.rustcomponents.sdk.UserPowerLevelUpdate(userId, level))
            )
        }

    /**
     * applies an action to the space and to each of its rooms
     *
     * what the protocol imposes, and it is not intuitive: membership and power are properties of
     * the room, not of the account and not of the space. a discord style server role therefore
     * does not exist in matrix, it has to be built by repeating the operation. a power level
     * written on the space alone gave moderator in the space and member everywhere else
     *
     * no single failure fails the lot. a room can refuse without the others being wrong to
     * succeed, and throwing on the first refusal would lose what was just done. so every refusal
     * is reported, with its reason
     *
     * the space goes first, because it carries the decision and the rooms only follow it. if we
     * cannot change it, the rest is pointless
     */
    private suspend fun surToutLeServeur(
        serverId: String,
        action: suspend (org.matrix.rustcomponents.sdk.Room) -> Unit,
    ): ModerationResult {
        val client = requireClient()
        val space = client.getRoom(serverId)
            ?: throw EngineException.Unexpected(tr("Serveur inconnu : $serverId", "Unknown server: $serverId"))

        val faits = mutableListOf<String>()
        val refuses = mutableMapOf<String, String>()

        runCatching { action(space) }
            .onSuccess { faits += serverId }
            .onFailure { refuses[serverId] = it.message ?: tr("refus sans motif", "refused without a reason") }

        val salons = servers().firstOrNull { it.id == serverId }?.channels.orEmpty()
        salons.forEach { canal ->
            val room = client.getRoom(canal.id)
            if (room == null) {
                refuses[canal.id] = tr("salon introuvable", "channel not found")
                return@forEach
            }
            runCatching { action(room) }
                .onSuccess { faits += canal.id }
                .onFailure { refuses[canal.id] = it.message ?: tr("refus sans motif", "refused without a reason") }
        }

        return ModerationResult(faits, refuses)
    }

    /** `#RRGGBB` to opaque argb. unreadable one way, unavoidable the other */
    private fun couleurVersArgb(hex: String): Int? = runCatching {
        (0xFF000000L or hex.removePrefix("#").toLong(16)).toInt()
    }.getOrNull()

    private fun argbVersCouleur(argb: Int): String =
        "#%06X".format(argb and 0x00FFFFFF)

    override suspend fun emotePacks(): List<EmotePack> = try {
        servers().mapNotNull { serveur ->
            // `runCatching` per server. one unreadable pack, refused rights or missing state,
            // must not make every other server's emoji disappear. a single 401 once emptied the
            // whole picker
            val emotes = runCatching { emotes(serveur.id) }.getOrDefault(emptyList())
            if (emotes.isEmpty()) null
            else EmotePack(serveur.id, serveur.name, serveur.avatarUrl, emotes)
        }
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Lecture des emojis impossible", "Could not read the emoji"), t)
    }

    override suspend fun allEmotes(): List<CustomEmote> {
        val vus = mutableSetOf<String>()
        return emotePacks().flatMap { it.emotes }.filter { vus.add(it.shortcode) }
    }

    override suspend fun envoyerAutocollant(channelId: String, autocollant: CustomEmote) = guard(
        tr("Envoi de l'autocollant impossible", "Could not send the sticker")
    ) {
        // `sendRaw` and not `send`. `m.sticker` is an event type in its own right and not a
        // flavour of `m.room.message`. sending it as a message makes it arrive as text in other
        // clients
        //
        // the body carries the name, which is what clients that cannot render the image show,
        // and what screen readers read
        val contenu = kotlinx.serialization.json.buildJsonObject {
            put("body", kotlinx.serialization.json.JsonPrimitive(autocollant.shortcode))
            put("url", kotlinx.serialization.json.JsonPrimitive(autocollant.url))
            // `info` is required by the spec. we know neither the size nor the dimensions,
            // the pack does not carry them, so we put the type and nothing else rather than
            // invent numbers that would reserve the wrong space on screen
            putJsonObject("info") {
                put("mimetype", kotlinx.serialization.json.JsonPrimitive("image/png"))
            }
        }.toString()
        val room = requireClient().getRoom(channelId)
            ?: throw EngineException.Unexpected(tr("Salon inconnu : $channelId", "Unknown channel: $channelId"))
        room.sendRaw("m.sticker", contenu)
    }

    override suspend fun transferer(depuis: String, eventId: String, vers: String) = guard(
        tr("Transfert impossible", "Could not forward")
    ) {
        // the event's own json, decrypted, rather than our model of it: a model rebuilt into a
        // message would lose whatever we do not read (dimensions, thumbnails, formatting)
        val brut = timelineFor(depuis).getEventTimelineItemByEventId(eventId).lazyProvider.latestJson()
            ?: throw EngineException.Unexpected(tr("Message pas encore envoyé", "Message not sent yet"))
        val evenement = kotlinx.serialization.json.Json.parseToJsonElement(brut).jsonObject
        val type = evenement["type"]?.jsonPrimitive?.contentOrNull
        if (type != "m.room.message" && type != "m.sticker") {
            throw EngineException.Unexpected(tr("Ce message ne se transfère pas ($type)", "This message cannot be forwarded ($type)"))
        }
        val contenu = evenement["content"]?.jsonObject
            ?: throw EngineException.Unexpected(tr("Message sans contenu", "Message without content"))
        // an edit carries the current text in `m.new_content`
        val actuel = contenu["m.new_content"]?.jsonObject ?: contenu
        val propre = kotlinx.serialization.json.JsonObject(actuel - "m.relates_to" - "m.new_content" - "m.mentions")
        val salon = requireClient().getRoom(vers)
            ?: throw EngineException.Unexpected(tr("Salon inconnu : $vers", "Unknown channel: $vers"))
        salon.sendRaw(type, propre.toString())
    }

    override suspend fun emotes(serverId: String): List<CustomEmote> =
        lirePack(serverId).map { (nom, entree) -> CustomEmote(nom, entree.first, entree.second) }

    override suspend fun emoteBytes(mxcUrl: String): ByteArray? = try {
        requireClient().getMediaContent(MediaSource.fromUrl(mxcUrl))
    } catch (_: Throwable) {
        // quiet, like avatars. a missing emoji falls back to its shortcode in plain text,
        // which is still readable. throwing would turn every message containing one broken
        // emoji into an error
        null
    }

    override suspend fun sendHtml(
        channelId: String,
        body: String,
        html: String,
        mentions: List<String>,
        mentionneToutLeSalon: Boolean,
    ) {
        try {
            timelineFor(channelId).send(
                messageEventContentFromHtml(body, html)
                    .withMentions(Mentions(mentions, mentionneToutLeSalon)),
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Envoi impossible", "Could not send"), t)
        }
    }

    override suspend fun addEmote(
        serverId: String,
        shortcode: String,
        filePath: String,
        autocollant: Boolean,
    ): CustomEmote {
        val nom = shortcode.trim().trim(':')
        if (nom.isEmpty()) throw EngineException.Unexpected(tr("Le nom de l'emoji est vide", "The emoji name is empty"))
        val file = java.io.File(filePath)
        if (!file.isFile) throw EngineException.Unexpected(tr("Fichier introuvable : $filePath", "File not found: $filePath"))

        return try {
            val octets = file.readBytes()
            val entete = octets.copyOf(minOf(MediaProbe.HEADER_BYTES, octets.size))
            val mime = MediaProbe.mimeType(entete, file.name)
            val mxc = requireClient().uploadMedia(mime, octets, null)

            // the whole pack is rewritten on every add, because a state event has no partial
            // update: you replace the content or nothing. hence reading it first, since writing
            // without reading would wipe every emoji already there
            val pack = lirePack(serverId).toMutableMap()
            pack[nom] = mxc to autocollant
            ecrirePack(serverId, pack)
            CustomEmote(nom, mxc, autocollant)
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Ajout de l'emoji impossible", "Could not add the emoji"), t)
        }
    }

    override suspend fun removeEmote(serverId: String, shortcode: String) {
        try {
            val pack = lirePack(serverId).toMutableMap()
            pack.remove(shortcode.trim().trim(':'))
            ecrirePack(serverId, pack)
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Retrait de l'emoji impossible", "Could not remove the emoji"), t)
        }
    }

    override suspend fun importerEmotesUtilises(salonId: String, serverId: String): Int {
        // first, harvest the emoji that appeared in the history. same skeleton as
        // `chercherDansSalon`: a throwaway timeline paginated backwards so the screen's live
        // window does not grow, then closed
        val trouves = mutableMapOf<String, String>()
        val timeline = timelineConfiguree(salonId, TimelineFocus.Live(hideThreadedEvents = false))
        try {
            var vus = 0
            var debutAtteint = false
            while (vus < MAX_SCAN_EMOTES && !debutAtteint) {
                debutAtteint = runCatching {
                    timeline.paginateBackwards(PAGE_RECHERCHE)
                }.getOrDefault(true)
                vus += PAGE_RECHERCHE.toInt()
            }

            val items = mutableListOf<SdkTimelineItem>()
            val premier = CompletableDeferred<Unit>()
            val handle = timeline.addListener(object : TimelineListener {
                override fun onUpdate(diff: List<TimelineDiff>) {
                    synchronized(items) { diff.forEach { apply(items, it) } }
                    premier.complete(Unit)
                }
            })
            try {
                withTimeoutOrNull(RECHERCHE_TIMEOUT_MS) { premier.await() }
                val instantane = synchronized(items) { items.toList() }
                instantane.toMessages().forEach { message ->
                    (message.body as? MessageBody.Text)?.html?.let { emojisDeHtml(it, trouves) }
                }
            } finally {
                handle.cancel()
            }
        } finally {
            runCatching { timeline.close() }
        }
        if (trouves.isEmpty()) return 0

        // then merge into the server's pack. we do not replace a shortcode already defined,
        // the local emoji wins over an imported namesake. full rewrite of the pack, since a
        // state event has no partial update, so read it first
        val pack = lirePack(serverId).toMutableMap()
        var ajoutes = 0
        trouves.forEach { (nom, mxc) ->
            if (!pack.containsKey(nom)) {
                pack[nom] = mxc to false
                ajoutes++
            }
        }
        if (ajoutes > 0) ecrirePack(serverId, pack)
        return ajoutes
    }

    /**
     * compiled once. they used to live in [emojisDeHtml]'s body, the last two inside its loop,
     * which is three compilations per `<img>` of every message received, for constant patterns.
     * same fix as on the ui side in `Emotes.kt`
     */
    private val MOTIF_IMG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val MOTIF_SRC = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val MOTIF_ALT = Regex("""alt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

    /**
     * picks the custom emoji out of a `formatted_body`. every `<img>` with an `mxc://` and an
     * `alt` gives a shortcode to mxc pair. same logic as `extraireEmojis` on the ui side, except
     * we keep the name (the `alt`, so `:panda:` becomes `panda`) to use as a pack key. a
     * non `mxc` image, meaning an arbitrary link from a message, is ignored: we only harvest
     * what the server already hosts
     */
    private fun emojisDeHtml(html: String, out: MutableMap<String, String>) {
        MOTIF_IMG.findAll(html).forEach { m ->
            val balise = m.value
            val src = MOTIF_SRC.find(balise)?.groupValues?.get(1)
            if (src == null || !src.startsWith("mxc://")) return@forEach
            val nom = MOTIF_ALT.find(balise)?.groupValues?.get(1)
                ?.trim()?.trim(':')?.takeIf { it.isNotEmpty() } ?: return@forEach
            out.putIfAbsent(nom, src)
        }
    }

    /**
     * thread timelines, cached like the room's and for the same reason: a timeline per send would
     * work and would repaginate the whole thread for a three word message. the key carries both
     * ids, two threads in one room being exactly the confusion the `timelines` lock exists to stop
     */
    private val timelinesDeFil = mutableMapOf<String, org.matrix.rustcomponents.sdk.Timeline>()

    private suspend fun timelineDeFil(
        channelId: String,
        racineId: String,
    ): org.matrix.rustcomponents.sdk.Timeline = verrouTimelines.withLock {
        val cle = "$channelId#$racineId"
        timelinesDeFil[cle]?.let { return@withLock it }
        timelineConfiguree(channelId, TimelineFocus.Thread(rootEventId = racineId))
            .also { timelinesDeFil[cle] = it }
    }

    override fun filsFlow(channelId: String): Flow<List<Fil>> = callbackFlow {
        val salon = requireClient().getRoom(channelId)
            ?: throw EngineException.Unexpected(tr("Salon inconnu : $channelId", "Unknown channel: $channelId"))
        val service = salon.threadListService()

        fun publier() {
            trySend(service.items().mapNotNull { it.versFil() })
        }

        val handle = service.subscribeToItemsUpdates(
            object : org.matrix.rustcomponents.sdk.ThreadListEntriesListener {
                override fun onUpdate(diff: List<org.matrix.rustcomponents.sdk.ThreadListUpdate>) {
                    // we republish the whole snapshot rather than apply the diffs. the
                    // service already keeps the list current, and replaying its operations
                    // ourselves would be a second source of truth for nothing
                    publier()
                }
            }
        )
        publier()

        // `paginate()` is not optional. subscribing loads nothing: the service starts empty and
        // waits for the first page to be asked for. without this call the threads tab shows zero
        // on a room that has some, with no error and no request. a capability taken for granted
        // because it had been wired up
        launch { runCatching { service.paginate() } }

        awaitClose { handle.cancel() }
    }.distinctUntilChanged()

    override fun filFlow(channelId: String, racineId: String): Flow<List<Message>> =
        // `ferme = false`, since this timeline is shared with the sending and lives in the cache
        fluxDe(ferme = false) { timelineDeFil(channelId, racineId) }

    override suspend fun repondreDansFil(
        channelId: String,
        racineId: String,
        markdown: String,
    ) = guard(tr("Réponse en fil impossible", "Could not reply in the thread")) {
        // sending on the thread's own timeline is enough, it is what carries the `m.thread`
        // relation. nothing to compose by hand, nothing to forget
        timelineDeFil(channelId, racineId).send(messageEventContentFromMarkdown(markdown))
        Unit
    }

    override suspend fun creerFil(channelId: String, nom: String, premierMessage: String): String {
        // through the sdk queue, which encrypts if the room is encrypted. a hand rolled put
        // went out in the clear. the queue does not return the id, so we wait for the root to
        // come back from the server carrying its own
        val depuis = System.currentTimeMillis() - 5_000
        send(channelId, nom)
        val racineId = kotlinx.coroutines.withTimeoutOrNull(30_000) {
            messagesFlow(channelId).map { liste ->
                liste.lastOrNull { m ->
                    m.isOwn && m.id.startsWith("$") && m.timestamp >= depuis &&
                        (m.body as? li.velvetchat.core.model.MessageBody.Text)?.text == nom
                }?.id
            }.first { it != null }
        } ?: throw EngineException.Unexpected(tr("Création du fil : la racine n'est pas revenue du serveur", "Thread creation: the root did not come back from the server"))
        repondreDansFil(channelId, racineId, premierMessage)
        return racineId
    }

    /**
     * translates an entry from the thread service
     *
     * null when the root has nothing usable in it. a thread you cannot show anything of has no
     * place in a list you scan to choose from
     */
    private fun org.matrix.rustcomponents.sdk.ThreadListItem.versFil(): Fil? {
        val racine = rootEvent.versMessage() ?: return null
        return Fil(
            racine = racine,
            nombreReponses = numReplies.toInt(),
            derniere = latestEvent?.versMessage(),
        )
    }

    private fun org.matrix.rustcomponents.sdk.ThreadListItemEvent.versMessage(): Message? {
        // only message like content interests us. a thread opened on an avatar change does not
        // exist, and content we cannot summarise would give an empty row in a list you scan
        // precisely in order to choose
        val msgLike = (content as? TimelineItemContent.MsgLike)?.content ?: return null
        val corps = (msgLike.kind as? MsgLikeKind.Message)?.content?.toBody() ?: return null
        val profil = senderProfile as? ProfileDetails.Ready
        return Message(
            id = eventId,
            sender = sender,
            senderDisplayName = profil?.displayName,
            senderAvatarUrl = profil?.avatarUrl,
            timestamp = timestamp.toLong(),
            isOwn = isOwn,
            isEdited = false,
            body = corps,
        )
    }

    override suspend fun modeNotification(channelId: String): ModeNotification {
        val salon = requireClient().getRoom(channelId) ?: return ModeNotification.DEFAUT
        val reglages = requireClient().getNotificationSettings()
        val etat = reglages.getRoomNotificationSettings(
            roomId = channelId,
            isEncrypted = salon.isEncrypted(),
            // a two person room does not have the same defaults as a group room in the spec.
            // getting this wrong gives you a "default" the server will not actually apply
            isOneToOne = salon.isDirect(),
        )
        // `isDefault` tells "this room has no opinion" from "this room wants exactly what the
        // default does". confusing them would freeze the room the first time the account setting
        // changed
        if (etat.isDefault) return ModeNotification.DEFAUT
        return when (etat.mode) {
            org.matrix.rustcomponents.sdk.RoomNotificationMode.ALL_MESSAGES ->
                ModeNotification.TOUT
            org.matrix.rustcomponents.sdk.RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY ->
                ModeNotification.MENTIONS
            org.matrix.rustcomponents.sdk.RoomNotificationMode.MUTE -> ModeNotification.MUET
        }
    }

    override suspend fun sourdines(): Map<String, Long> = runCatching {
        val brut = requireClient().donneeDeCompte(TYPE_SOURDINES) ?: return emptyMap()
        val racine = kotlinx.serialization.json.Json.parseToJsonElement(brut)
            as? kotlinx.serialization.json.JsonObject ?: return emptyMap()
        (racine["fins"] as? kotlinx.serialization.json.JsonObject)
            ?.mapNotNull { (id, v) ->
                val fin = (v as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
                    ?: return@mapNotNull null
                id to fin
            }?.toMap().orEmpty()
        // quiet when unreadable, like the nicknames. no deadline read means no deadline
        // expires, so the room stays muted, which is the harmless way round. the other way
        // would unmute rooms on a parse error
    }.getOrDefault(emptyMap())

    override suspend fun setSourdine(roomId: String, finMillis: Long?) =
        guard(tr("Mise en sourdine impossible", "Could not mute")) {
            setModeNotification(roomId, ModeNotification.MUET)
            ecrireSourdines(sourdines() - roomId + listOfNotNull(finMillis?.let { roomId to it }))
        }

    private suspend fun ecrireSourdines(table: Map<String, Long>) {
        requireClient().setAccountData(
            TYPE_SOURDINES,
            kotlinx.serialization.json.JsonObject(
                mapOf("fins" to kotlinx.serialization.json.JsonObject(
                    table.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }
                ))
            ).toString(),
        )
    }

    /**
     * puts back the sound in the rooms whose deadline has passed
     *
     * done on reading rather than on a timer. there is no process of ours running at the
     * minute the delay expires, and a scheduled job that has to survive a reboot, a killed app
     * and four devices would be a lot of machinery for something a comparison answers
     */
    private suspend fun balayerSourdines() {
        val maintenant = System.currentTimeMillis()
        val echues = sourdines().filterValues { it <= maintenant }
        if (echues.isEmpty()) return
        echues.keys.forEach {
            runCatching { setModeNotification(it, ModeNotification.DEFAUT) }
        }
        runCatching { ecrireSourdines(sourdines() - echues.keys) }
    }

    override suspend fun salonsMuets(): Set<String> = try {
        runCatching { balayerSourdines() }
        val reglages = requireClient().getNotificationSettings()
        // one read gives the rooms with a rule of their own, and we keep the ones whose rule is
        // mute. the second pass costs one read per room concerned, not per room outright, and
        // there is only a handful by construction since a room with no opinion is not in there
        reglages.getRoomsWithUserDefinedRules(enabled = true)
            .filter { id ->
                runCatching { modeNotification(id) == ModeNotification.MUET }.getOrDefault(false)
            }
            .toSet()
    } catch (t: Throwable) {
        // a failed read must not empty the room list. with no known mode they
        // all draw normally, which is the acceptable worst case
        erreurNonBloquante("Salons muets illisibles", t)
        emptySet()
    }

    override suspend fun setModeNotification(
        channelId: String,
        mode: ModeNotification,
    ) = guard(tr("Réglage des notifications impossible", "Could not change the notifications")) {
        // leaving the mute drops its deadline. keeping it would silence the room again at the
        // sweep, minutes after somebody deliberately turned the sound back on
        if (mode != ModeNotification.MUET) {
            runCatching { sourdines() }.getOrNull()?.takeIf { it.containsKey(channelId) }
                ?.let { runCatching { ecrireSourdines(it - channelId) } }
        }
        val reglages = requireClient().getNotificationSettings()
        if (mode == ModeNotification.DEFAUT) {
            // remove the rule rather than write an equivalent one. a rule that says "same as
            // the default" stops being that the moment the default changes, without anyone
            // asking
            reglages.restoreDefaultRoomNotificationMode(channelId)
            return@guard
        }
        reglages.setRoomNotificationMode(
            channelId,
            when (mode) {
                ModeNotification.TOUT ->
                    org.matrix.rustcomponents.sdk.RoomNotificationMode.ALL_MESSAGES
                ModeNotification.MENTIONS ->
                    org.matrix.rustcomponents.sdk.RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY
                ModeNotification.MUET ->
                    org.matrix.rustcomponents.sdk.RoomNotificationMode.MUTE
                ModeNotification.DEFAUT -> return@guard
            },
        )
    }

    override fun frappeFlow(channelId: String): Flow<List<String>> = callbackFlow {
        val salon = requireClient().getRoom(channelId)
            ?: throw EngineException.Unexpected(tr("Salon inconnu : $channelId", "Unknown channel: $channelId"))
        val moi = currentSession().userId
        val handle = salon.subscribeToTypingNotifications(
            object : org.matrix.rustcomponents.sdk.TypingNotificationsListener {
                override fun call(typingUserIds: List<String>) {
                    // filter yourself out. the server echoes your own typing back, and "you
                    // are typing" teaches nobody anything
                    trySend(typingUserIds.filterNot { it == moi })
                }
            }
        )
        awaitClose { handle.cancel() }
    }.distinctUntilChanged()

    override suspend fun signalerFrappe(
        channelId: String,
        enCours: Boolean,
    ) = guard(tr("Signalement de frappe impossible", "Could not send the typing notice")) {
        requireClient().getRoom(channelId)?.typingNotice(enCours)
        Unit
    }

    override suspend fun releasePubliee(serverId: String, type: String): Release? =
        // no `guard` here, because that one returns `Unit` and the value is the entire point.
        // `lireEtat` already returns null on a 404, and a server where nobody published is not
        // a fault
        li.velvetchat.core.model.Release.depuisJson(
            lireEtat(serverId, type)
        )

    override suspend fun publierRelease(
        serverId: String,
        release: Release,
        type: String,
    ) = guard(tr("Publication de la version impossible", "Could not publish the version")) {
        val contenu = kotlinx.serialization.json.buildJsonObject {
            put("versionCode", kotlinx.serialization.json.JsonPrimitive(release.versionCode))
            put("versionName", kotlinx.serialization.json.JsonPrimitive(release.versionName))
            release.notes?.let { put("notes", kotlinx.serialization.json.JsonPrimitive(it)) }
            put(
                "variantes",
                kotlinx.serialization.json.buildJsonObject {
                    release.variantes.forEach { (abi, variante) ->
                        put(
                            abi,
                            kotlinx.serialization.json.buildJsonObject {
                                put("url", kotlinx.serialization.json.JsonPrimitive(variante.url))
                                put(
                                    "sha256",
                                    kotlinx.serialization.json.JsonPrimitive(variante.sha256),
                                )
                                put(
                                    "taille",
                                    kotlinx.serialization.json.JsonPrimitive(variante.taille),
                                )
                            },
                        )
                    }
                },
            )
        }.toString()
        ecrireEtat(serverId, type, contenu)
    }

    /**
     * account data, read under the key's current name and then under every name it has had
     *
     * same reason as [lireEtat], different storage: notes, nicknames, mutes, the server order
     * and the pending invitations are account data, and they were written under the earlier
     * prefix. `accountData` answers null for "never written" and for "written under the old
     * name" alike, so the null is checked against the earlier names before it is trusted
     */
    private suspend fun Client.donneeDeCompte(type: String): String? {
        for (nom in li.velvetchat.core.model.Identifiants.toutesLesFormes(type)) {
            accountData(nom)?.let { return it }
        }
        return null
    }

    /**
     * room state, read under the type's current name and then under every name it has had
     *
     * the prefix of our own types changed on 2026-09-30. what a room already carries — roles,
     * server tag, soundboard, decorations, call state — is filed under the old one, and asking
     * only for the new name gets a clean 404 that looks exactly like "never written". so a miss
     * falls through to the earlier names before it is believed
     *
     * only a miss costs the extra round trip, and only until the room is written again: a write
     * always uses the current name, so the fallback stops firing on its own
     */
    private suspend fun lireEtat(
        roomId: String,
        type: String,
    ): kotlinx.serialization.json.JsonObject? {
        for (nom in li.velvetchat.core.model.Identifiants.toutesLesFormes(type)) {
            lireEtatExact(roomId, nom)?.let { return it }
        }
        return null
    }

    /**
     * one state event, by its exact type, through a hand signed http call
     *
     * the sdk writes any state (`sendStateEventRaw`) and reads back only the types it knows. its
     * `getUrl` is the trap: it adds no access token, so the server answers `401 M_MISSING_TOKEN`,
     * being built for public addresses rather than the client api. so we redo the request with the
     * session's token and reimplement nothing else of the transport, neither discovery nor refresh
     *
     * `roomId` and not `serverId` because the pins use it too: the endpoint is the same for every
     * room, and a name that is too narrow eventually makes somebody write a second identical
     * function
     */
    private suspend fun lireEtatExact(
        roomId: String,
        type: String,
    ): kotlinx.serialization.json.JsonObject? {
        val salon = java.net.URLEncoder.encode(roomId, "UTF-8")
        val reponse = requeteSignee("GET", "/_matrix/client/v3/rooms/$salon/state/$type/")
        // 404 means the state was never written. normal, not a fault: a room with no pins and
        // no emoji pack has nothing to answer
        if (reponse.code == 404) return null
        if (reponse.code != 200) {
            throw EngineException.Unexpected(
                tr("Lecture de $type refusée (${reponse.code}) : ${reponse.corps}", "Reading $type refused (${reponse.code}): ${reponse.corps}")
            )
        }
        return kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
            as? kotlinx.serialization.json.JsonObject
    }

    /**
     * @param cle the `state_key`. empty, the default, means one event of this type per room. a
     *   non empty key gives one event per key, which is how you write one membership per person
     *   without two simultaneous writes overwriting each other, see [TYPE_APPEL_MEMBRE]
     */
    override suspend fun renommerSalon(channelId: String, nom: String) = guard(tr("Renommage impossible", "Could not rename")) {
        val contenu = kotlinx.serialization.json.buildJsonObject {
            put("name", kotlinx.serialization.json.JsonPrimitive(nom))
        }.toString()
        ecrireEtat(channelId, "m.room.name", contenu)
        oublierStructure()
    }

    private suspend fun ecrireEtat(
        serverId: String,
        type: String,
        contenu: String,
        cle: String = "",
    ) {
        val space = requireClient().getRoom(serverId)
            ?: throw EngineException.Unexpected(tr("Serveur inconnu : $serverId", "Unknown server: $serverId"))
        space.sendStateEventRaw(type, cle, contenu)
    }

    /**
     * a server's pack: `name -> (url, is it a sticker)`
     *
     * `usage` gets read, which it did not before. the spec puts both kinds in the same state
     * event and tells them apart only by that array, and ignoring it made stickers from an
     * element made pack turn up in the `:name:` autocomplete and nowhere else. a pack that
     * declares nothing is an emoji pack, which is the spec's default and not a guess
     */
    private suspend fun lirePack(serverId: String): Map<String, Pair<String, Boolean>> {
        val images = lireEtat(serverId, TYPE_EMOTES)
            ?.get("images") as? kotlinx.serialization.json.JsonObject
            ?: return emptyMap()
        return images.mapNotNull { (nom, valeur) ->
            val obj = valeur as? kotlinx.serialization.json.JsonObject
            val url = (obj?.get("url") as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: return@mapNotNull null
            val usages = (obj["usage"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                .orEmpty()
            // sticker means declared `sticker` and not `emoticon`. a pack can mark both, which
            // means usable either way, and we file it as an emoji, the quieter of the two uses
            nom to (url to (usages.contains("sticker") && !usages.contains("emoticon")))
        }.toMap()
    }

    private suspend fun ecrirePack(serverId: String, pack: Map<String, Pair<String, Boolean>>) {
        // json built by the serialiser and not by concatenation. an emoji name comes from a
        // filename, so from a person, and one apostrophe or quote would be enough to produce an
        // invalid state event
        val contenu = kotlinx.serialization.json.buildJsonObject {
            putJsonObject("images") {
                pack.forEach { (nom, entree) ->
                    val (url, autocollant) = entree
                    putJsonObject(nom) {
                        put("url", kotlinx.serialization.json.JsonPrimitive(url))
                        // `usage` gets written in both cases and not only for stickers. a pack
                        // that declares nothing is ambiguous to other clients, which then file
                        // it however they feel
                        putJsonArray("usage") {
                            add(
                                kotlinx.serialization.json.JsonPrimitive(
                                    if (autocollant) "sticker" else "emoticon"
                                )
                            )
                        }
                    }
                }
            }
        }.toString()
        ecrireEtat(serverId, TYPE_EMOTES, contenu)
    }

    override suspend fun profile(): Profile = try {
        val client = requireClient()
        Profile(
            userId = client.userId(),
            displayName = client.displayName(),
            avatarUrl = client.avatarUrl(),
        )
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Lecture du profil impossible", "Could not read the profile"), t)
    }

    override suspend fun setDisplayName(name: String) {
        // an empty name would clear the nickname without saying so. the protocol accepts it,
        // and the person would end up named by their raw id in every room they are in
        val propre = name.trim()
        if (propre.isEmpty()) {
            throw EngineException.Unexpected(tr("Le nom affiché ne peut pas être vide", "The display name cannot be empty"))
        }
        try {
            requireClient().setDisplayName(propre)
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Changement de nom impossible", "Could not change the name"), t)
        }
    }

    override suspend fun setAvatar(filePath: String) {
        val file = java.io.File(filePath)
        if (!file.isFile) throw EngineException.Unexpected(tr("Fichier introuvable : $filePath", "File not found: $filePath"))
        try {
            // read whole, unlike `sendMedia` which streams. the sdk's avatar api takes a byte
            // array, so the difference in handling follows the difference in api rather than
            // carelessness, and an avatar stays small
            val octets = file.readBytes()
            val entete = octets.copyOf(minOf(MediaProbe.HEADER_BYTES, octets.size))
            requireClient().uploadAvatar(MediaProbe.mimeType(entete, file.name), octets)
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Envoi de l'avatar impossible", "Could not upload the avatar"), t)
        }
    }

    override suspend fun removeAvatar() {
        try {
            requireClient().removeAvatar()
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Retrait de l'avatar impossible", "Could not remove the avatar"), t)
        }
    }

    override suspend fun setAvatarServeur(serverId: String, filePath: String?) {
        val salon = requireClient().getRoom(serverId)
            ?: throw EngineException.Unexpected(tr("Serveur inconnu : $serverId", "Unknown server: $serverId"))
        try {
            if (filePath == null) {
                salon.removeAvatar()
            } else {
                val file = java.io.File(filePath)
                if (!file.isFile) {
                    throw EngineException.Unexpected(tr("Fichier introuvable : $filePath", "File not found: $filePath"))
                }
                // read whole like `setAvatar`. the sdk api takes a byte array, and a server
                // icon is small by nature
                val octets = file.readBytes()
                val entete = octets.copyOf(minOf(MediaProbe.HEADER_BYTES, octets.size))
                // `mediaInfo` null. the dimensions only serve to pre size a thumbnail, and
                // the server already resizes on demand, which is what `avatarBytes` uses.
                // building one would mean decoding the image here, so a graphics dependency in
                // a module that has none
                salon.uploadAvatar(MediaProbe.mimeType(entete, file.name), octets, null)
            }
        } catch (t: EngineException) {
            throw t
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Icône de serveur impossible à changer", "Could not change the server icon"), t)
        }
        // `m.room.avatar` is space state, and no room list diff announces it, so the rail would
        // keep its two letters until the next message arrived
        oublierStructure()
    }

    override suspend fun members(channelId: String): List<Member> {
        val roomList = syncService?.roomListService()
            ?: throw EngineException.Unexpected(tr("Synchronisation non démarrée", "Sync not started"))
        val room = try {
            roomList.room(channelId)
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Salon inconnu : $channelId", "Unknown channel: $channelId"), t)
        }

        return try {
            val iterator = room.members()
            // one read for the whole list, like the direct conversations. a nickname is chosen
            // per person but read per account
            val etiquettes = surnomsEnCache()
            val out = mutableListOf<Member>()
            // the sdk returns a chunked iterator rather than a list. a room can hold thousands
            // of members, and materialising all of them at once is what we want to avoid on a
            // phone
            while (true) {
                val chunk = iterator.nextChunk(MEMBER_CHUNK) ?: break
                if (chunk.isEmpty()) break
                chunk.forEach { m ->
                    // only members actually present. invited and departed belong in the
                    // timeline as system events, not in the list of people you are talking to
                    if (m.membership !is MembershipState.Join) return@forEach
                    if (m.isServiceMember) return@forEach
                    val surnom = etiquettes[m.userId]
                    out += Member(
                        userId = m.userId,
                        displayName = surnom ?: m.displayName,
                        avatarUrl = m.avatarUrl,
                        role = m.toRole(),
                        nomReel = m.displayName.takeIf { surnom != null },
                    )
                }
            }
            // sorted by power descending then by name, which is a discord style member panel's
            // order, and doing it here saves every screen reinventing it
            out.sortedWith(
                compareByDescending<Member> { it.role?.powerLevel ?: 0L }
                    .thenBy { (it.displayName ?: it.userId).lowercase() }
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Lecture des membres impossible", "Could not read the members"), t)
        }
    }

    /**
     * the displayed role, derived from the power level, which is the only source of truth for
     * permissions
     *
     * the sdk already suggests the tier (`suggestedRoleForPowerLevel`), so we take that rather
     * than recode thresholds that would drift from the ones it actually applies
     *
     * `PowerLevel.Infinite` exists for room creators in recent protocol versions: they cannot be
     * demoted. representing it as [Long.MAX_VALUE] keeps the sort right without introducing a
     * special case into the product model
     */
    private fun org.matrix.rustcomponents.sdk.RoomMember.toRole(): Role = Role(
        name = when (suggestedRoleForPowerLevel) {
            uniffi.matrix_sdk.RoomMemberRole.CREATOR -> tr("Créateur", "Creator")
            uniffi.matrix_sdk.RoomMemberRole.ADMINISTRATOR -> tr("Administrateur", "Administrator")
            uniffi.matrix_sdk.RoomMemberRole.MODERATOR -> tr("Modérateur", "Moderator")
            uniffi.matrix_sdk.RoomMemberRole.USER -> tr("Membre", "Member")
        },
        // default glyph until the state event that makes it configurable exists. only the two
        // tiers that are in charge carry one, because putting a symbol next to everybody teaches
        // nothing, which is exactly what a badge must avoid
        icon = when (suggestedRoleForPowerLevel) {
            uniffi.matrix_sdk.RoomMemberRole.CREATOR,
            uniffi.matrix_sdk.RoomMemberRole.ADMINISTRATOR -> "👑"
            uniffi.matrix_sdk.RoomMemberRole.MODERATOR -> "🛡"
            uniffi.matrix_sdk.RoomMemberRole.USER -> null
        },
        // custom colours come from the space state event. null means the ui picks, not that
        // there is no role
        colorArgb = null,
        powerLevel = when (val p = powerLevel) {
            is PowerLevel.Value -> p.value
            PowerLevel.Infinite -> Long.MAX_VALUE
        },
    )

    override suspend fun leaveServer(serverId: String, forget: Boolean): List<String> = optimiste(
        { it.copy(partis = it.partis + serverId) },
        { it.copy(partis = it.partis - serverId) },
    ) {
        val c = requireClient()

        // the sdk returns the rooms in reverse bfs order, leaves first and the space last. that
        // order is not cosmetic: leaving the space first would drop the membership that grants
        // access to the `restricted` rooms
        //
        // only the joined ones, and one by one rather than `handle.leave`, which stops at the
        // first error. a single channel refusing (never joined, already left from elsewhere) kept
        // the whole server, space included, and since nothing reported it the button simply did
        // nothing. a channel that refuses now stays behind, the server still goes
        val enfants = try {
            c.spaceService().leaveSpace(serverId).rooms()
                .filter { it.spaceRoom.state == org.matrix.rustcomponents.sdk.Membership.JOINED }
                .map { it.spaceRoom.roomId }
                .filter { it != serverId }
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Impossible de quitter le serveur $serverId", "Could not leave the server $serverId"), t)
        }
        val quittes = enfants.filter { id ->
            runCatching { c.getRoom(id)?.leave() }
                .onFailure { erreurNonBloquante("Salon $id non quitté", it) }
                .isSuccess
        }
        try {
            (c.getRoom(serverId) ?: throw EngineException.Unexpected(tr("Serveur inconnu : $serverId", "Unknown server: $serverId"))).leave()
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Impossible de quitter le serveur $serverId", "Could not leave the server $serverId"), t)
        }
        val ids = quittes + serverId

        if (forget) {
            // without forgetting, a left room stays listed as gone. failure tolerated: we did
            // leave, which is what matters
            ids.forEach { id -> runCatching { c.getRoom(id)?.forget() } }
        }
        // the cached timelines point at rooms we just left. under the same lock as their
        // creation, since it is the same map
        verrouTimelines.withLock { timelines.keys.removeAll(ids.toSet()) }
        oublierStructure()
        ids
    }

    override suspend fun supprimerServeur(serverId: String) = optimiste(
        { it.copy(partis = it.partis + serverId) },
        { it.copy(partis = it.partis - serverId) },
    ) {
        val c = requireClient()
        val moi = c.userId()
        val enfants = try {
            c.spaceService().leaveSpace(serverId).rooms()
                .filter { it.spaceRoom.state == org.matrix.rustcomponents.sdk.Membership.JOINED }
                .map { it.spaceRoom.roomId }
                .filter { it != serverId }
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Suppression du serveur impossible", "Could not delete the server"), t)
        }
        // the space first and strictly: if we cannot shut it, we are not allowed to delete the
        // server, and touching the channels would leave it half destroyed
        try {
            fermerEtVider(c.getRoom(serverId) ?: throw EngineException.Unexpected(tr("Serveur inconnu : $serverId", "Unknown server: $serverId")), moi)
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Suppression du serveur impossible", "Could not delete the server"), t)
        }
        // a channel that refuses stays behind, but the space it hung from is shut and empty, so
        // nobody can find it anymore
        enfants.forEach { id ->
            c.getRoom(id)?.let { salon ->
                runCatching { fermerEtVider(salon, moi) }
                    .onFailure { erreurNonBloquante("Salon $id non vidé", it) }
            }
        }
        leaveServer(serverId, forget = true)
        Unit
    }

    /** shuts a room's door, then kicks everybody but us, invites included */
    private suspend fun fermerEtVider(salon: org.matrix.rustcomponents.sdk.Room, moi: String) {
        salon.sendStateEventRaw(eventType = "m.room.join_rules", stateKey = "", content = "{\"join_rule\":\"invite\"}")
        val membres = salon.members()
        while (true) {
            val lot = membres.nextChunk(MEMBER_CHUNK) ?: break
            if (lot.isEmpty()) break
            lot.filter {
                it.userId != moi &&
                    (it.membership is MembershipState.Join || it.membership is MembershipState.Invite)
            }.forEach { m ->
                runCatching { salon.kickUser(m.userId, tr("Serveur supprimé", "Server deleted")) }
                    .onFailure { erreurNonBloquante("${m.userId} non expulsé", it) }
            }
        }
    }

    /**
     * walks the space hierarchy and enters every room
     *
     * recursive because sub spaces are the categories. stopping at the first level would leave
     * every channel filed into a category unjoined, which is the normal case as soon as a server
     * is even slightly organised
     *
     * `seen` guards against a cyclic hierarchy. nothing stops two spaces declaring each other as
     * children, and the protocol does not prevent it
     */
    private suspend fun joinChildren(
        spaceId: String,
        joined: MutableList<String>,
        refused: MutableMap<String, String>,
        depth: Int,
        seen: MutableSet<String>,
    ) {
        if (depth > MAX_SPACE_DEPTH) return
        val c = requireClient()

        val children = try {
            c.spaceService().spaceRoomList(spaceId).let { list ->
                list.paginate()
                list.rooms()
            }
        } catch (t: Throwable) {
            refused[spaceId] = tr("hiérarchie illisible : ${t.message}", "unreadable hierarchy: ${t.message}")
            return
        }

        for (child in children) {
            if (!seen.add(child.roomId)) continue

            if (child.roomType is RoomType.Space) {
                // a category gets joined too, otherwise its channels stay outside the visible
                // hierarchy even once joined
                runCatching { c.joinRoomById(child.roomId) }
                    .onSuccess { joined += child.roomId }
                    .onFailure { refused[child.roomId] = it.describeJoinFailure() }
                joinChildren(child.roomId, joined, refused, depth + 1, seen)
            } else {
                runCatching { c.joinRoomById(child.roomId) }
                    .onSuccess { joined += child.roomId }
                    .onFailure { refused[child.roomId] = it.describeJoinFailure() }
            }
        }
    }

    /** the reason matters. "already a member" and "access denied" call for different follow ups */
    private fun Throwable.describeJoinFailure(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("M_FORBIDDEN") || raw.contains("not invited") ->
                tr("accès refusé — la règle du salon est plus stricte que celle du serveur", "access refused — the channel's rule is stricter than the server's")
            raw.contains("already") -> tr("déjà membre", "already a member")
            raw.isBlank() -> this::class.simpleName ?: tr("cause inconnue", "unknown cause")
            else -> raw
        }
    }

    override suspend fun send(channelId: String, markdown: String) {
        if (markdown.isBlank()) return
        try {
            // `send` drops into the sdk queue and returns. the message survives a dropped
            // connection and a restart, so we do not wait for the server
            timelineFor(channelId).send(messageEventContentFromMarkdown(markdown))
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Envoi impossible dans $channelId", "Could not send in $channelId"), t)
        }
    }

    override suspend fun sendMedia(channelId: String, filePath: String, caption: String?) {
        val file = java.io.File(filePath)
        if (!file.isFile) throw EngineException.Unexpected(tr("Fichier introuvable : $filePath", "File not found: $filePath"))

        // we read the header and not the file. recognising a 500 mb video must not cost 500 mb
        // of heap. the upload itself is streamed by the sdk
        val header = ByteArray(minOf(MediaProbe.HEADER_BYTES.toLong(), file.length()).toInt())
        java.io.FileInputStream(file).use { it.read(header) }

        val mime = MediaProbe.mimeType(header, file.name)
        val kind = MediaProbe.kindOf(mime)
        val size = file.length().toULong()
        val dim = if (kind == MediaKind.IMAGE || kind == MediaKind.VIDEO) {
            MediaProbe.dimensions(header)
        } else {
            null
        }

        val params = UploadParameters(
            source = UploadSource.File(filePath),
            caption = caption?.takeIf { it.isNotBlank() },
            formattedCaption = null,
            mentions = null,
            inReplyTo = null,
        )

        val timeline = timelineFor(channelId)
        val handle = try {
            when (kind) {
                MediaKind.IMAGE -> timeline.sendImage(
                    params, null,
                    ImageInfo(
                        height = dim?.height?.toULong(),
                        width = dim?.width?.toULong(),
                        mimetype = mime,
                        size = size,
                        thumbnailInfo = null,
                        thumbnailSource = null,
                        blurhash = null,
                        isAnimated = null,
                    ),
                )
                MediaKind.VIDEO -> timeline.sendVideo(
                    params, null,
                    VideoInfo(
                        duration = null,
                        height = dim?.height?.toULong(),
                        width = dim?.width?.toULong(),
                        mimetype = mime,
                        size = size,
                        thumbnailInfo = null,
                        thumbnailSource = null,
                        blurhash = null,
                    ),
                )
                MediaKind.AUDIO -> timeline.sendAudio(
                    params,
                    AudioInfo(duration = null, size = size, mimetype = mime),
                )
                MediaKind.FILE -> timeline.sendFile(
                    params,
                    FileInfo(
                        mimetype = mime,
                        size = size,
                        thumbnailInfo = null,
                        thumbnailSource = null,
                    ),
                )
            }
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Envoi du média impossible", "Could not send the media"), t)
        }

        // `join()` does not wait for the upload to finish, whatever its name suggests. it waits
        // for the attachment task to have dropped the media into the send queue. about 100 ms for
        // an 11 mb file, on a network where a round trip costs 90. we call it anyway, because it
        // is where preparation errors surface. delivery is followed through `sendState`
        try {
            handle.join()
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Média refusé par la file d'envoi", "Media refused by the send queue"), t)
        }
    }

    override suspend fun avatarBytes(mxcUrl: String, sizePx: Int): ByteArray? = try {
        // `getMediaThumbnail` and not `getMediaContent`. unlike media in an encrypted room,
        // where the server only sees a blob and can resize nothing (see `mediaThumbnail`),
        // profile avatars are in the clear. so this is one of the rare places server side
        // resizing actually works, and it saves pulling a full size image to draw it in a 40 dp
        // circle
        requireClient().getMediaThumbnail(
            MediaSource.fromUrl(mxcUrl),
            sizePx.toULong(),
            sizePx.toULong(),
        )
    } catch (_: Throwable) {
        // quiet, and that is a choice. a missing avatar falls back to initials, and throwing
        // would make every caller handle a failure that is not one, for something purely
        // decorative
        null
    }

    override suspend fun sendVoice(
        channelId: String,
        filePath: String,
        durationMs: Long,
        waveform: List<Float>,
    ) {
        val file = java.io.File(filePath)
        if (!file.isFile) throw EngineException.Unexpected(tr("Enregistrement introuvable", "Recording not found"))

        try {
            val entete = ByteArray(minOf(MediaProbe.HEADER_BYTES.toLong(), file.length()).toInt())
            java.io.FileInputStream(file).use { it.read(entete) }

            timelineFor(channelId).sendVoiceMessage(
                UploadParameters(
                    source = UploadSource.File(filePath),
                    caption = null,
                    formattedCaption = null,
                    mentions = null,
                    inReplyTo = null,
                ),
                AudioInfo(
                    // the duration is the one measured while recording, not read back from
                    // the file. a container may not carry it, and a missing duration shows
                    // "0:00" on a bubble that runs for thirty seconds
                    duration = java.time.Duration.ofMillis(durationMs),
                    size = file.length().toULong(),
                    mimetype = MediaProbe.mimeType(entete, file.name),
                ),
                // clamped to 0..1. the protocol wants it normalised, and a value outside that
                // makes the recipient draw nonsense
                waveform.map { it.coerceIn(0f, 1f) },
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Envoi du message vocal impossible", "Could not send the voice message"), t)
        }
    }

    override suspend fun mediaBytes(ref: MediaRef): ByteArray = try {
        requireClient().getMediaContent(MediaSource.fromJson(ref.source))
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Téléchargement de ${ref.filename} impossible", "Could not download ${ref.filename}"), t)
    }

    override suspend fun mediaFile(ref: MediaRef): String = try {
        requireClient().getMediaFile(
            mediaSource = MediaSource.fromJson(ref.source),
            filename = ref.filename,
            mimeType = ref.mimeType ?: "application/octet-stream",
            useCache = true,
            tempDir = null,
        ).path()
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Écriture de ${ref.filename} impossible", "Could not write ${ref.filename}"), t)
    }

    override suspend fun mediaThumbnail(ref: MediaRef, width: Int, height: Int): ByteArray? {
        val source = ref.thumbnail ?: ref.source
        return try {
            requireClient().getMediaThumbnail(
                MediaSource.fromJson(source),
                width.toULong(),
                height.toULong(),
            )
        } catch (_: Throwable) {
            // deliberately quiet. in an encrypted room the server only sees a blob and can
            // resize nothing, which is the normal case here rather than a failure. throwing
            // would make expected behaviour look like an error. the caller falls back on the
            // blurhash, then on the full media
            null
        }
    }

    /**
     * timelines kept per room
     *
     * not only an optimisation: the local echo of a sent message only appears on a live
     * timeline. rebuilding one on every read would make messages in flight vanish between two
     * calls
     */
    private val timelines = mutableMapOf<String, org.matrix.rustcomponents.sdk.Timeline>()

    /**
     * the timeline cache is shared, and it used to be read and written with no lock
     *
     * who calls it: the screen subscribing to a room's flow, the send, the read receipt, the
     * pagination, all from different coroutines and quite happily at the same time. switching
     * room starts a read while the previous send is holding another
     *
     * two kinds of damage, mild to severe. two timelines created for the same room, where the
     * sdk only really keeps one, the second gets its own diffs, and a message's local echo can
     * appear only on the one the screen is not subscribed to. and a `HashMap` written by two
     * threads with no synchronisation guarantees nothing at all: it can lose an entry, hand back
     * a different one, or spin forever during a resize. returning the timeline of a room other
     * than the one asked for is a state nothing further up can detect, and the send would simply
     * go somewhere else
     *
     * the lock covers the read and the write, which is the only correct split. test then create
     * outside the lock leaves exactly the window of the first case
     */
    private val verrouTimelines = Mutex()

    /**
     * the rooms whose full state we asked the server for
     *
     * see [abonner]: sliding sync only delivers a summary until a room is subscribed. we
     * remember which ones were, so as not to ask again on every open
     */
    private val abonnes =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * asks the server for a room's full state
     *
     * this was the missing piece, and it explains two failures that looked entirely unrelated
     *
     * for most rooms, sliding sync only carries a summary: the name, the avatar, the last event,
     * the counters. no history, no power levels, no member list. that is the protocol working as
     * designed, since a client showing a hundred rooms does not need the state of a hundred. it
     * has to say which ones it is opening, and subscribing is exactly that word
     *
     * without it, a room's history stayed empty, since you saw the last message and nothing
     * before it, back pagination having no anchor client side. and you could not write in it
     * just after joining: with no `m.room.power_levels` and no member list, the sdk knows
     * neither whether sending is allowed nor who to share the megolm key with. the workaround
     * people found, going into a dm and coming back, was nothing but one extra sync round that
     * eventually brought the state in by another route
     *
     * one cause, two symptoms with no apparent connection, hence the fix here on the single path
     * everything opens a room through rather than at the two places it showed
     *
     * failing is not fatal: we log it and return, and the room behaves like it used to rather
     * than not opening at all
     */
    private suspend fun abonner(roomId: String) {
        if (!abonnes.add(roomId)) return
        val service = syncService?.roomListService() ?: run { abonnes.remove(roomId); return }
        // `setRoomSubscriptions` and not `resetAndAddRoomSubscriptions`. the sdk split
        // `subscribeToRooms` into three: `set…` adds to the current set, `resetAndAdd…` empties
        // it first, `remove…` takes away. here we open one more room without touching the
        // previous ones, which is exactly the old behaviour and what `abonnes` assumes by never
        // resubscribing. the resetting variant would unsubscribe every already open room on
        // every new open
        runCatching { service.setRoomSubscriptions(listOf(roomId)) }.onFailure {
            // take it back out of the set, otherwise one transient failure would condemn the
            // room for the whole session with nothing saying so
            abonnes.remove(roomId)
            erreurNonBloquante("Abonnement du salon $roomId", it)
        }
    }

    private suspend fun timelineFor(channelId: String): org.matrix.rustcomponents.sdk.Timeline {
        // before the lock and before the timeline. asking for the room's full state is what
        // makes the history paginable and the sending possible. see `abonner`
        abonner(channelId)
        return verrouTimelines.withLock {
            timelines[channelId]?.let { return@withLock it }
            val roomList = syncService?.roomListService()
                ?: throw EngineException.Unexpected(tr("Synchronisation non démarrée", "Sync not started"))
            val room = try {
                roomList.room(channelId)
            } catch (t: Throwable) {
                throw EngineException.Unexpected(tr("Salon inconnu : $channelId", "Unknown channel: $channelId"), t)
            }
            // thread replies stay out of the main conversation
            //
            // without `hideThreadedEvents` they show up there too, and then a thread has no
            // point at all, since it exists precisely to take a discussion out of the common
            // flow
            //
            // read receipts stay tracked (`ALL_EVENTS`), unlike on the throwaway timelines. that
            // is where the "new messages" divider comes from, and turning it off here would make
            // it vanish with no explanation
            room.timelineWithConfiguration(
                org.matrix.rustcomponents.sdk.TimelineConfiguration(
                    focus = TimelineFocus.Live(hideThreadedEvents = true),
                    filter = org.matrix.rustcomponents.sdk.TimelineFilter.All,
                    internalIdPrefix = null,
                    dateDividerMode = org.matrix.rustcomponents.sdk.DateDividerMode.DAILY,
                    trackReadReceipts =
                        uniffi.matrix_sdk_ui.TimelineReadReceiptTracking.ALL_EVENTS,
                    // false, and not a choice but a constraint. the sdk refuses to build the
                    // timeline otherwise: "configuration is set to report UTDs but no hook
                    // manager is set". reporting undecryptable messages wants a hook manager the
                    // app does not install. `room.timeline()` did not enable it either, and
                    // setting it true while moving to explicit configuration crashed startup.
                    // found at runtime, not at compile time
                    reportUtds = false,
                )
            ).also {
                timelines[channelId] = it
                elaguerTimelines(garder = channelId)
            }
        }
    }

    /**
     * caps the timeline cache
     *
     * it only emptied when you left a room, so a long session accumulated one timeline per room
     * visited, each holding its message window, its decrypted media and its sdk side listener.
     * on a small server with a dozen rooms that was survivable, and it stops being so as soon
     * as anyone browses. exactly the kind of cost that shows up as stutter rather than as a
     * clean failure
     *
     * least recently used goes first, and never the one just opened, hence `garder`. the evicted
     * timeline is not closed by hand: the sdk frees it when nothing references it, and closing
     * it out from under a flow that is still collecting would be worse than keeping it
     *
     * call it under `verrouTimelines`, like everything else that touches this map
     */
    private fun elaguerTimelines(garder: String) {
        if (timelines.size <= TIMELINES_MAX) return
        // `LinkedHashMap` by default, so insertion order stands in for age, which is enough
        // here. we are not after a real lru, we are after not growing
        timelines.keys
            .filterNot { it == garder }
            .take(timelines.size - TIMELINES_MAX)
            .forEach { timelines.remove(it) }
    }

    override suspend fun loadMore(channelId: String, count: Int): Boolean = try {
        // the sdk answers true once it has reached the start of the conversation. we return the
        // opposite, "is there anything left to load", which is the question the caller is asking
        !timelineFor(channelId).paginateBackwards(count.toUShort())
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Chargement de l'historique impossible", "Could not load the history"), t)
    }

    override suspend fun chercherDansSalon(
        channelId: String,
        requete: String,
        maxEvenements: Int,
    ): List<Message> {
        val cherche = normaliser(requete)
        if (cherche.isBlank()) return emptyList()

        return guardListe {
            // a throwaway timeline, not the room's. two reasons: paginating a thousand events
            // on the live one would grow the window the screen is showing, so the conversation
            // would jump around while you search, and closing this one afterwards frees the lot
            // in one go
            val timeline = timelineConfiguree(
                channelId,
                TimelineFocus.Live(hideThreadedEvents = false),
            )
            try {
                var vus = 0
                var debutAtteint = false
                // we paginate in whole pages rather than all at once. `paginateBackwards`
                // returns true once it has hit the start, and that is the only way to know there
                // is nothing left to ask for
                while (vus < maxEvenements && !debutAtteint) {
                    debutAtteint = runCatching {
                        timeline.paginateBackwards(PAGE_RECHERCHE)
                    }.getOrDefault(true)
                    vus += PAGE_RECHERCHE.toInt()
                }

                val items = mutableListOf<SdkTimelineItem>()
                val premier = CompletableDeferred<Unit>()
                val handle = timeline.addListener(object : TimelineListener {
                    override fun onUpdate(diff: List<TimelineDiff>) {
                        synchronized(items) { diff.forEach { apply(items, it) } }
                        premier.complete(Unit)
                    }
                })
                try {
                    withTimeoutOrNull(RECHERCHE_TIMEOUT_MS) { premier.await() }
                    val instantane = synchronized(items) { items.toList() }
                    instantane.toMessages()
                        .filter { message ->
                            // text only. searching image filenames would surface "IMG_4821"
                            // for the query "img", which nobody was looking for
                            val corps = message.body
                            corps is MessageBody.Text && normaliser(corps.text).contains(cherche)
                        }
                        // newest first. you are nearly always looking for something recent, and
                        // the timeline comes back the other way round
                        .sortedByDescending { it.timestamp }
                } finally {
                    handle.cancel()
                }
            } finally {
                runCatching { timeline.close() }
            }
        }
    }

    /**
     * lowercase and stripped of accents, on both sides of the comparison
     *
     * without it, searching "repas" would miss "Repas" and "répas", and nobody types accents into
     * a search box. `NFD` splits the letter from its accent, and the `Mn` class, non spacing
     * marks, removes the accents once they stand alone
     */
    private fun normaliser(texte: String): String =
        java.text.Normalizer.normalize(texte, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .trim()

    override suspend fun marquerNonLuDepuis(
        channelId: String,
        eventIdPrecedent: String?,
    ) = guard(tr("Marquage en non lu impossible", "Could not mark as unread")) {
        if (eventIdPrecedent != null) {
            val timeline = timelineFor(channelId)
            // both markers, like `markRead`. the receipt is what brings the counter down and the
            // fully read marker is what places the new messages divider, and moving only one
            // leaves the divider and the badge disagreeing about where you stopped
            timeline.sendReadReceipt(ReceiptType.FULLY_READ, eventIdPrecedent)
            timeline.sendReadReceipt(ReceiptType.READ_PRIVATE, eventIdPrecedent)
        }
        requireClient().getRoom(channelId)?.setUnreadFlag(true) ?: error(tr("Salon inconnu", "Unknown channel"))
    }

    override suspend fun marquerLuJusqua(channelId: String, eventId: String) = guard(
        tr("Marquage en lu impossible", "Could not mark as read")
    ) {
        runCatching { requireClient().getRoom(channelId)?.setUnreadFlag(false) }
        val timeline = timelineFor(channelId)
        timeline.sendReadReceipt(ReceiptType.FULLY_READ, eventId)
        timeline.sendReadReceipt(ReceiptType.READ_PRIVATE, eventId)
    }

    override suspend fun setNonLu(channelId: String, nonLu: Boolean) = guard(
        tr("Marquage en non lu impossible", "Could not mark as unread")
    ) {
        requireClient().getRoom(channelId)?.setUnreadFlag(nonLu) ?: error(tr("Salon inconnu", "Unknown channel"))
    }

    override suspend fun markRead(channelId: String, visibility: ReadVisibility) = guard(
        tr("Marquage en lu impossible", "Could not mark as read")
    ) {
        val timeline = timelineFor(channelId)

        // two distinct markers for two distinct jobs, and both are needed
        //
        // the read receipt (`m.read` / `m.read.private`) is what brings the notification counters
        // back down. the fully read marker (`m.fully_read`) is what places the "new messages"
        // divider
        //
        // sending only the first would clear the badges while leaving the divider stuck on a
        // message from three weeks ago. consistent for the protocol, absurd on screen
        // the hand set flag goes first. clearing it after the receipts would leave a window
        // where the counter is back to zero and the room still says unread
        runCatching { requireClient().getRoom(channelId)?.setUnreadFlag(false) }
        timeline.markAsRead(ReceiptType.FULLY_READ)
        timeline.markAsRead(
            when (visibility) {
                ReadVisibility.PRIVATE -> ReceiptType.READ_PRIVATE
                ReadVisibility.PUBLIC -> ReceiptType.READ
            }
        )
    }

    override suspend fun edit(channelId: String, messageId: String, markdown: String) = guard(
        tr("Édition impossible", "Could not edit")
    ) {
        timelineFor(channelId).edit(
            eventOrTransactionId = messageId.toEventRef(),
            newContent = EditedContent.RoomMessage(messageEventContentFromMarkdown(markdown)),
        )
    }

    override suspend fun reply(channelId: String, messageId: String, markdown: String) = guard(
        tr("Réponse impossible", "Could not reply")
    ) {
        timelineFor(channelId).sendReply(messageEventContentFromMarkdown(markdown), messageId)
    }

    override suspend fun toggleReaction(
        channelId: String,
        messageId: String,
        emoji: String,
    ): Boolean = try {
        timelineFor(channelId).toggleReaction(messageId.toEventRef(), emoji)
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Réaction impossible", "Could not react"), t)
    }

    override suspend fun delete(channelId: String, messageId: String, reason: String?) = guard(
        tr("Suppression impossible", "Could not delete")
    ) {
        timelineFor(channelId).redactEvent(messageId.toEventRef(), reason)
    }

    /**
     * presences already read, with the instant they were read at
     *
     * a cache is required here, not a nicety. the "N online" subtitle asks for the presence of
     * every member of the room, so with no cache, opening a ten person room means ten requests,
     * redone on every recomposition. with a short validity window we ask once a minute at most,
     * which is the right granularity for something that changes by the minute anyway
     */
    private val presences = mutableMapOf<String, Pair<Long, Statut>>()
    /** the presence `/sync` `next_batch` token, which makes the following calls incremental */
    private var jetonPresence: String? = null

    private val verrouPresences = Mutex()

    override suspend fun presence(userId: String): Statut {
        val maintenant = System.currentTimeMillis()
        verrouPresences.withLock {
            presences[userId]?.let { (lu, statut) ->
                if (maintenant - lu < PRESENCE_VALIDITE_MS) return statut
            }
        }

        val statut = lirePresence(userId)
        verrouPresences.withLock { presences[userId] = maintenant to statut }
        return statut
    }

    override suspend fun presences(userIds: Collection<String>): Map<String, Statut> {
        val voulus = userIds.distinct()
        if (voulus.isEmpty()) return emptyMap()

        val maintenant = System.currentTimeMillis()
        val connus = mutableMapOf<String, Statut>()
        val aLire = mutableListOf<String>()
        verrouPresences.withLock {
            voulus.forEach { id ->
                val cache = presences[id]
                if (cache != null && maintenant - cache.first < PRESENCE_VALIDITE_MS) {
                    connus[id] = cache.second
                } else {
                    aLire += id
                }
            }
        }
        if (aLire.isEmpty()) return connus

        // one request for everybody, before making one per person
        //
        // the protocol has no batch read of presence, which is true of `/presence/{id}/status`
        // and is what made one request per member look acceptable. but `/sync` carries a
        // `presence` block, and that one holds every person you share a room with, in a single
        // round trip
        //
        // the filter empties the rooms (`rooms: []`, no state types, no messages), so what is
        // left is presence and nothing else. the first call gives the full picture, the following
        // ones, with the token kept, report only the changes, so almost nothing
        //
        // someone missing from the block is not an unknown: the server answered, and a person
        // absent from it is offline. that is what keeps us off N requests per round most of
        // whom are asleep
        if (runCatching { presencesParSync() }.getOrNull() != null) {
            verrouPresences.withLock {
                val instant = System.currentTimeMillis()
                val trouves = mutableMapOf<String, Statut>()
                aLire.forEach { id ->
                    val cache = presences[id]
                    trouves[id] = if (cache != null && instant - cache.first < PRESENCE_VALIDITE_MS) {
                        cache.second
                    } else {
                        // `presenceDepuisProtocole("offline")` rather than a hardcoded value.
                        // same translation as for a server answer, so the two paths cannot
                        // drift apart
                        Statut(presence = presenceDepuisProtocole("offline"))
                            .also { presences[id] = instant to it }
                    }
                }
                return connus + trouves
            }
        }

        // capped, and the cap is not decorative. firing a few hundred simultaneous requests at
        // synapse would trip its rate limiting, and `requeteSignee` would dutifully start
        // waiting, so we would have swapped one queue for another, and a ruder one for the
        // server
        val frein = Semaphore(PRESENCES_SIMULTANEES)
        val lus = kotlinx.coroutines.coroutineScope {
            aLire.map { id ->
                async {
                    // each read already tolerates failure, see `lirePresence` which returns
                    // INCONNU on anything but a 200. one unreachable person must not take the
                    // whole list down
                    id to frein.withPermit { runCatching { lirePresence(id) }.getOrDefault(Statut.INCONNU) }
                }
            }.awaitAll()
        }

        val instant = System.currentTimeMillis()
        verrouPresences.withLock { lus.forEach { (id, s) -> presences[id] = instant to s } }
        return connus + lus.toMap()
    }

    /**
     * presence in real time, rather than every ninety seconds or so
     *
     * this used to be polling: one full round, then a minute and a half of waiting. someone
     * coming online therefore stayed "offline" on screen for that long, and the round cost N
     * requests even when nothing had moved. the worst of both worlds, slow and chatty
     *
     * `/sync` knows how to wait. with a thirty second timeout the server holds the connection and
     * only answers when a presence changes, which is how matrix is designed, and one open
     * connection costs less than waking up every ninety seconds. the first round gives the full
     * picture, the next ones only the changes
     *
     * we only emit when it concerns us. the `presence` block carries everybody you share a room
     * with, and a change touching none of the displayed [userIds] must not recompose the screen
     *
     * the fallback stays for a server with no presence, or one that turned it off. `/sync` then
     * returns null and we drop back to the old rhythm rather than spin on a server that will
     * never answer
     */
    override fun presencesFlow(userIds: Collection<String>): Flow<Map<String, Statut>> = flow {
        // full picture first, since the screen has to be right before the first long wait
        runCatching { presences(userIds) }.getOrNull()?.let { emit(it) }
        val interesse = userIds.toSet()
        while (true) {
            // `ensureActive` at the top of the round, and not as a matter of style
            //
            // `runCatching` catches everything, `CancellationException` included. a long wait
            // cancelled mid flight would therefore look like a network failure, and the loop
            // would go round again on a dead coroutine. the explicit check gets out at once
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val touches = runCatching { presencesParSync(PRESENCE_ATTENTE_MS) }.getOrNull()
            if (touches == null) {
                // no presence on this server, so the old rhythm, which knows how to say unknown
                kotlinx.coroutines.delay(PRESENCE_RAFRAICHISSEMENT_MS)
                runCatching { presences(userIds) }.getOrNull()?.let { emit(it) }
                continue
            }
            if (touches.none { it in interesse }) continue
            // the cache was just filled by the `/sync`, so rereading it costs no request
            runCatching { presences(userIds) }.getOrNull()?.let { emit(it) }
        }
    }

    /**
     * the `presence` block of a filtered `/sync`, poured into the cache. null if unavailable
     *
     * the token is kept from one call to the next: the first `/sync` brings the full picture, the
     * following ones only the changes. on a server with presence disabled the block is empty and
     * we return null, and the caller drops back to the individual reads, which know how to say
     * unknown cleanly
     */
    private suspend fun presencesParSync(attenteMs: Int = 0): Set<String>? {
        val filtre = """{"presence":{"limit":200},"room":{"rooms":[],"timeline":{"limit":0},""" +
            """"state":{"types":[]},"ephemeral":{"types":[]}},"account_data":{"types":[]}}"""
        val chemin = buildString {
            append("/_matrix/client/v3/sync?timeout=").append(attenteMs).append("&filter=")
            append(java.net.URLEncoder.encode(filtre, "UTF-8"))
            jetonPresence?.let { append("&since=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
        }
        // give the server a bit more than its own timeout before hanging up, otherwise we cut
        // the line exactly when it was about to answer
        val reponse = runCatching {
            requeteSignee("GET", chemin, lectureMs = attenteMs + 15_000)
        }.getOrNull() ?: return null
        if (reponse.code != 200) return null

        val racine = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                as? kotlinx.serialization.json.JsonObject
        }.getOrNull() ?: return null

        jetonPresence = (racine["next_batch"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.contentOrNull ?: jetonPresence

        val evenements = ((racine["presence"] as? kotlinx.serialization.json.JsonObject)
            ?.get("events") as? kotlinx.serialization.json.JsonArray) ?: return null

        val touches = mutableSetOf<String>()
        val instant = System.currentTimeMillis()
        verrouPresences.withLock {
            evenements.forEach { element ->
                val e = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val qui = (e["sender"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    ?: return@forEach
                touches += qui
                val contenu = e["content"] as? kotlinx.serialization.json.JsonObject
                fun texte(cle: String) =
                    (contenu?.get(cle) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                presences[qui] = instant to Statut(
                    presence = presenceDepuisProtocole(texte("presence")),
                    message = texte("status_msg")?.takeIf { it.isNotBlank() },
                    depuisMs = texte("last_active_ago")?.toLongOrNull(),
                )
            }
        }
        return touches
    }

    private suspend fun lirePresence(userId: String): Statut {
        val qui = java.net.URLEncoder.encode(userId, "UTF-8")
        // anything but a 200 gives unknown, without throwing. the cases are too many and too
        // ordinary to deserve an exception: 404 when the server has never seen this person, 403
        // when you share no room, 400 when presence is off in synapse's config. none of them is
        // a fault
        val reponse = runCatching {
            requeteSignee("GET", "/_matrix/client/v3/presence/$qui/status")
        }.getOrNull() ?: return Statut.INCONNU
        if (reponse.code != 200) return Statut.INCONNU

        return runCatching {
            val corps = kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                as? kotlinx.serialization.json.JsonObject ?: return Statut.INCONNU

            fun texte(cle: String) =
                (corps[cle] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

            Statut(
                presence = presenceDepuisProtocole(texte("presence")),
                message = texte("status_msg")?.takeIf { it.isNotBlank() },
                depuisMs = texte("last_active_ago")?.toLongOrNull(),
            )
        }.getOrDefault(Statut.INCONNU)
    }

    override suspend fun annoncerPresence(presence: Presence) {
        // quiet. this is only a local setting, and a background wakeup must not fail because
        // the presence state could not be written
        runCatching { requireClient().setPresence(versSdk(presence) ?: return, immediate = true) }
    }

    /** null for [Presence.INCONNU], which is not a state you announce but a silence */
    private fun versSdk(
        presence: Presence,
    ): org.matrix.rustcomponents.sdk.PresenceState? =
        when (presence) {
            Presence.EN_LIGNE -> org.matrix.rustcomponents.sdk.PresenceState.ONLINE
            Presence.ABSENT -> org.matrix.rustcomponents.sdk.PresenceState.UNAVAILABLE
            // invisible is `offline` on purpose. the protocol does not tell the two apart,
            // which is precisely what makes it useful
            Presence.INVISIBLE -> org.matrix.rustcomponents.sdk.PresenceState.OFFLINE
            // the sdk has no "busy": its enum stops at `ONLINE`, `OFFLINE`, `UNAVAILABLE`. so
            // we give it nothing, and the state goes out through [setPresence]'s PUT under the
            // protocol's own name (`org.matrix.msc3026.busy`). sending it `ONLINE` would be worse
            // than nothing, since every `/sync` carries `set_presence` and would wipe do not
            // disturb the moment it was written
            Presence.NE_PAS_DERANGER -> null
            Presence.INCONNU -> null
        }

    override suspend fun setPresence(presence: Presence, message: String?) {
        val session = currentSession()

        // without this line, picking "away" changes nothing, and the server is the one in the
        // right
        //
        // checked against the api: the PUT below is accepted, the `status_msg` is kept, and
        // reading it straight back returns `online`. the message went through, the state did not
        //
        // the cause is not in the write but in what follows it. every `/sync` carries a
        // `set_presence` parameter and the server honours it, so the next sync, a few hundred
        // milliseconds later, put the person back online. we were writing into a box that sync
        // rewrote immediately
        //
        // the sdk docs say it word for word, "updates the presence state used by future generated
        // sync requests". so this, and not the PUT, is where the state's persistence is decided
        //
        // both calls stay necessary: this one takes no `status_msg`, the PUT does not survive the
        // sync. the PUT goes second so the final state carries both, since synapse replaces
        // `status_msg` with whatever the request holds and the other order would erase the
        // message
        annoncerPresence(presence)

        // the write goes through the client api and not the sdk's `Client.setPresence`, which
        // only takes the state and not the `status_msg`. two calls, one for the state and one for
        // the text, would have produced two presence events where the protocol expects one
        val corps = buildString {
            append("""{"presence":"${presence.versProtocole()}"""")
            message?.takeIf { it.isNotBlank() }?.let {
                append(""","status_msg":"${echapperJson(it)}"""")
            }
            append("}")
        }
        requetePut("/_matrix/client/v3/presence/${enc(session.userId)}/status", corps)

        // invalidate the cache for yourself. without it the user bar would show the old status
        // for up to a minute after you changed it, which is the worst possible moment to display
        // a stale value
        verrouPresences.withLock { presences.remove(session.userId) }
    }

    override suspend fun setSilence(silence: Boolean) {
        requetePut(
            "/_matrix/client/v3/pushrules/global/override/.m.rule.master/enabled",
            """{"enabled":$silence}""",
        )
    }

    override suspend fun silence(): Boolean {
        val reponse = runCatching {
            requeteSignee("GET", "/_matrix/client/v3/pushrules/global/override/.m.rule.master")
        }.getOrNull() ?: return false
        // 404 means the rule was never touched, so it sits at its default, disabled, so
        // notifications go through. normal on a fresh account
        if (reponse.code != 200) return false
        return runCatching {
            val corps = kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                as? kotlinx.serialization.json.JsonObject
            (corps?.get("enabled") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
        }.getOrDefault(false)
    }

    /**
     * the two rules that make every message ring, one in the clear and one encrypted
     *
     * turning both off is "mentions only", since the mention and keyword rules sit elsewhere in
     * the list and take priority. turning one on without the other gives an account that rings
     * for unencrypted rooms and stays quiet for the rest, which is the exact opposite of what you
     * want on an end to end encrypted server
     */
    private val REGLES_TOUT_MESSAGE = listOf(".m.rule.message", ".m.rule.encrypted")

    override suspend fun modeNotificationGlobal(): ModeNotification {
        if (silence()) return ModeNotification.MUET
        // a missing rule sits at its default, and the default for these is enabled
        val toutSonne = REGLES_TOUT_MESSAGE.all { regle ->
            val reponse = runCatching {
                requeteSignee("GET", "/_matrix/client/v3/pushrules/global/underride/$regle")
            }.getOrNull() ?: return@all true
            if (reponse.code != 200) return@all true
            runCatching {
                val corps = kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                    as? kotlinx.serialization.json.JsonObject
                (corps?.get("enabled") as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content != "false"
            }.getOrDefault(true)
        }
        return if (toutSonne) ModeNotification.TOUT else ModeNotification.MENTIONS
    }

    override suspend fun setModeNotificationGlobal(mode: ModeNotification) {
        // lift the silence first. leaving the master rule on would mute an account just set to
        // "everything", with nothing saying so
        setSilence(mode == ModeNotification.MUET)
        if (mode == ModeNotification.MUET) return
        val actif = mode != ModeNotification.MENTIONS
        REGLES_TOUT_MESSAGE.forEach { regle ->
            runCatching {
                requetePut(
                    "/_matrix/client/v3/pushrules/global/underride/$regle/enabled",
                    """{"enabled":$actif}""",
                )
            }
        }
    }

    private fun enc(valeur: String) = java.net.URLEncoder.encode(valeur, "UTF-8")

    /** the handful of characters json will not let you leave as they are inside a string */
    private fun echapperJson(texte: String): String = texte
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private suspend fun requetePut(chemin: String, corps: String) {
        val reponse = requeteSignee("PUT", chemin, corps)
        if (reponse.code !in 200..299) {
            throw EngineException.Unexpected(
                tr("Écriture refusée (${reponse.code}) : ${reponse.corps}", "Write refused (${reponse.code}): ${reponse.corps}")
            )
        }
    }

    /**
     * demands a confirmed event id, and refuses a local echo
     *
     * where it comes from: voting on a poll you just created made the sdk answer `Failed to parse
     * EventId: leading sigil is incorrect or missing`. the cause is in `toMessage`. a message
     * still in the send queue has no event id, it carries its transaction id. the model exposes
     * both under the same `id` field, which is the right call for display and the wrong one for
     * operations that name an event to the server
     *
     * three of those were exposed: voting, closing a poll, pinning. the third is the worst, since
     * the message would have been written as is into `m.room.pinned_events`, shared state, with a
     * value no client could resolve. the poll failed loudly, the pin would have failed quietly
     * and permanently
     *
     * the message says what is happening and what to do, because this is transient. a second
     * later the same gesture works
     */
    private fun exigerEventId(id: String, quoi: String): String {
        if (!id.startsWith("$")) {
            throw EngineException.Unexpected(
                tr("$quoi : ce message n'est pas encore confirmé par le serveur. Réessayez dans un instant.", "$quoi: the server has not confirmed this message yet. Try again in a moment.")
            )
        }
        return id
    }

    override suspend fun createPoll(
        channelId: String,
        question: String,
        options: List<String>,
        secret: Boolean,
        maxChoix: Int,
    ) = guard(tr("Création du sondage impossible", "Could not create the poll")) {
        timelineFor(channelId).createPoll(
            question = question.trim(),
            answers = options.map { it.trim() }.filter { it.isNotEmpty() },
            maxSelections = maxChoix.coerceAtLeast(1).toUByte(),
            pollKind = if (secret) PollKind.UNDISCLOSED else PollKind.DISCLOSED,
        )
    }

    override suspend fun votePoll(channelId: String, pollId: String, optionIds: List<String>) =
        guard(tr("Vote impossible", "Could not vote")) {
            timelineFor(channelId).sendPollResponse(
                exigerEventId(pollId, tr("Vote impossible", "Could not vote")),
                optionIds,
            )
        }

    override suspend fun endPoll(channelId: String, pollId: String) =
        guard(tr("Clôture impossible", "Could not close the poll")) {
            // the text is the fallback shown by clients that cannot read polls. the protocol
            // asks for one, and leaving it empty gives them a blank line
            timelineFor(channelId).endPoll(
                exigerEventId(pollId, tr("Clôture impossible", "Could not close the poll")),
                tr("Le sondage est clos.", "The poll is closed."),
            )
        }

    /**
     * counts an sdk poll
     *
     * the counting happens here and not on screen. `votes` is an answer to voters map, and
     * getting a total out of it means knowing the same voter can appear under several answers
     * when the poll allows it. so the total is the number of distinct people who voted and not
     * the sum of the columns. on a multiple choice poll the sum would exceed the number of
     * participants and the percentages would stop meaning anything
     */
    private fun MsgLikeKind.Poll.toBody(): MessageBody.Poll {
        val ferme = endTime != null
        return MessageBody.Poll(
            question = question,
            options = answers.map { reponse ->
                val votants = votes[reponse.id].orEmpty()
                PollOption(
                    id = reponse.id,
                    texte = reponse.text,
                    votes = votants.size,
                    mien = ownUserId in votants,
                )
            },
            totalVotes = votes.values.flatten().distinct().size,
            ferme = ferme,
            // a secret ballot only shows its numbers once closed. showing them earlier would
            // cancel the one property that makes this kind of poll different
            resultatsVisibles = kind == PollKind.DISCLOSED || ferme,
            maxChoix = maxSelections.toInt(),
        )
    }

    /**
     * uploads a local file and returns its `mxc://`
     *
     * `withContext(IO)` and not merely `suspend`, and it took StrictMode on a device to see it:
     * `suspend` says the call can wait, it says nothing about which thread it waits on. every
     * caller here was a compose click handler, so the `isFile` and the `readBytes` ran on the
     * main thread. measured at 14 ms for a 576 ko sound, which nobody notices, and the same
     * code path carries the two hundred megabyte video that would sit there as an ANR
     */
    override suspend fun uploadMedia(filePath: String): String =
        withContext(Dispatchers.IO) {
            val fichier = java.io.File(filePath)
            if (!fichier.isFile) throw EngineException.Unexpected(tr("Fichier introuvable : $filePath", "File not found: $filePath"))
            // `guard` returns nothing, since it wraps calls we only want the effect of. here we
            // want the value, so the `try` is written by hand rather than bending the guard out
            // of shape
            try {
                val octets = fichier.readBytes()
                // the type comes from the header and not the extension. a jpeg renamed `.png`
                // would otherwise give a media nobody could display
                val entete = octets.copyOf(minOf(MediaProbe.HEADER_BYTES, octets.size))
                requireClient().uploadMedia(MediaProbe.mimeType(entete, fichier.name), octets, null)
            } catch (t: Throwable) {
                throw EngineException.Unexpected(tr("Téléversement impossible", "Could not upload"), t)
            }
        }

    /**
     * `userId -> (instant read at, profile)`
     *
     * came in with the server tag, which shows next to every nickname in the timeline, so for
     * every member of a room. with no cache, opening a ten person room means ten profile
     * requests, redone on every recomposition
     *
     * a profile changes a few times a year, so a minute of staleness is already generous
     */
    private val profilsEnCache = mutableMapOf<String, Pair<Long, ProfilEtendu>>()
    private val verrouProfils = Mutex()

    override suspend fun profilsEtendus(userIds: List<String>): Map<String, ProfilEtendu> {
        // eight at a time. the homeserver is ours, but ten simultaneous requests over a mobile
        // link get in each other's way more than they help
        val jauge = Semaphore(8)
        return coroutineScope {
            userIds.distinct().map { qui ->
                async { qui to runCatching { jauge.withPermit { profilEtendu(qui) } }.getOrNull() }
            }.awaitAll()
        }.mapNotNull { (qui, profil) -> profil?.let { qui to it } }.toMap()
    }

    /**
     * extended profiles survive a restart
     *
     * the cache was memory only with a minute of staleness, so opening the app asked
     * `/profile/{id}` again for every member shown, on every launch. something like twenty
     * requests before the first screen, for banners and colours that change a few times a year
     *
     * the disk answers first and the network comes behind. same trade as the cosmetics: better to
     * show yesterday's colour at once than make the screen wait for today's
     */
    private val fichierCacheProfils by lazy { java.io.File(dataPath, "profils.json") }
    private var profilsDuDisque: MutableMap<String, ProfilEtenduEnCache>? = null

    private fun disqueProfils(): MutableMap<String, ProfilEtenduEnCache> =
        profilsDuDisque ?: runCatching {
            val brut = fichierCacheProfils.takeIf { it.isFile }?.readText()
                ?: return@runCatching mutableMapOf()
            kotlinx.serialization.json.Json
                .decodeFromString<Map<String, ProfilEtenduEnCache>>(brut).toMutableMap()
        }.getOrDefault(mutableMapOf<String, ProfilEtenduEnCache>()).also { profilsDuDisque = it }

    private fun noterProfilSurDisque(userId: String, profil: ProfilEtendu) {
        val carte = disqueProfils()
        val neuf = ProfilEtenduEnCache.de(profil)
        if (carte[userId] == neuf) return
        carte[userId] = neuf
        runCatching { fichierCacheProfils.writeText(kotlinx.serialization.json.Json.encodeToString(carte)) }
    }

    override suspend fun profilEtendu(userId: String): ProfilEtendu {
        val maintenant = System.currentTimeMillis()
        verrouProfils.withLock {
            profilsEnCache[userId]?.takeIf { maintenant - it.first < PROFIL_TTL_MS }
        }?.let { return it.second }

        // the disk if it has one: we return its value and refetch behind. the `runCatching`
        // around the refetch is deliberate, it must never fail the call already served
        verrouProfils.withLock { disqueProfils()[userId] }?.let { garde ->
            supervisor.launch { runCatching { relireProfil(userId) } }
            return garde.versModele()
        }
        return relireProfil(userId)
    }

    override suspend fun activiteDe(userId: String): String? {
        val qui = java.net.URLEncoder.encode(userId, "UTF-8")
        val cle = java.net.URLEncoder.encode(ProfilEtendu.CLE_ACTIVITE, "UTF-8")
        val reponse = runCatching {
            requeteSignee("GET", "/_matrix/client/v3/profile/$qui/$cle")
        }.getOrNull() ?: return null
        // 404 is the normal answer for "this person is not doing anything", and it is by far the
        // most common one. not an error, not worth a log line
        if (reponse.code != 200) return null
        return runCatching {
            val corps = kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                as? kotlinx.serialization.json.JsonObject ?: return null
            (corps[ProfilEtendu.CLE_ACTIVITE] as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private suspend fun relireProfil(userId: String): ProfilEtendu {
        val maintenant = System.currentTimeMillis()
        val qui = java.net.URLEncoder.encode(userId, "UTF-8")
        // the whole profile in one request rather than a field at a time. `/profile/{id}`
        // returns every field, extended ones included, and asking one by one would mean four
        // round trips to draw one screen
        val reponse = runCatching {
            requeteSignee("GET", "/_matrix/client/v3/profile/$qui")
        }.getOrNull() ?: return ProfilEtendu()
        // 404 means this server has no extended profiles, or does not know this person. both are
        // normal, and the display copes without
        if (reponse.code != 200) return ProfilEtendu()

        // what was only found under the old key, and therefore has to be written back
        val aRecoller = mutableMapOf<String, String>()
        return runCatching {
            val corps = kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                as? kotlinx.serialization.json.JsonObject ?: return ProfilEtendu()
            fun brut(cle: String) =
                (corps[cle] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
            // the earlier key, read once and then repaired
            //
            // the package rename rewrote the profile keys along with the code. they name data
            // already written on the server, though, and a renamed key is a bio, a tag and a
            // frame gone missing. the constants went back to their original names, and this
            // reads back what was written in between, one version, one evening, but it is
            // somebody's work
            //
            // reading it back was never going to be enough on its own. nothing rewrites a
            // profile you do not edit, so the compatibility path would have had to stay
            // forever, and forever is how a temporary branch becomes load bearing. so what
            // the fallback rescues on our own account is written back under the right key,
            // once, and the old one is deleted. see `recollerProfil` below
            //
            // somebody else's account we cannot repair: only their device can write their
            // profile. theirs heals the next time they edit it, and until then this reads it
            fun champ(cle: String): String? {
                brut(cle)?.let { return it }
                // every earlier name, newest first. the list lives in [Identifiants] so a
                // rename adds one entry there instead of one branch here
                for (ancienne in li.velvetchat.core.model.Identifiants.anciennes(cle)) {
                    brut(ancienne)?.let {
                        aRecoller[cle] = it
                        return it
                    }
                }
                return null
            }
            ProfilEtendu(
                pronoms = champ(ProfilEtendu.CLE_PRONOMS),
                banniere = champ(ProfilEtendu.CLE_BANNIERE),
                couleurHaute = champ(ProfilEtendu.CLE_COULEUR_HAUTE),
                couleurBasse = champ(ProfilEtendu.CLE_COULEUR_BASSE),
                decoration = champ(ProfilEtendu.CLE_DECORATION),
                bio = champ(ProfilEtendu.CLE_BIO),
                tagEmoji = champ(ProfilEtendu.CLE_TAG_EMOJI),
                tagTexte = champ(ProfilEtendu.CLE_TAG_TEXTE),
                nomCouleurA = champ(ProfilEtendu.CLE_NOM_COULEUR_A),
                nomCouleurB = champ(ProfilEtendu.CLE_NOM_COULEUR_B),
                effet = champ(ProfilEtendu.CLE_EFFET),
                cadre = champ(ProfilEtendu.CLE_CADRE),
                plaque = champ(ProfilEtendu.CLE_PLAQUE),
                activite = champ(ProfilEtendu.CLE_ACTIVITE),
            )
        }.getOrDefault(ProfilEtendu()).also { lu ->
            if (aRecoller.isNotEmpty()) recollerProfil(userId, aRecoller)
            verrouProfils.withLock {
                profilsEnCache[userId] = maintenant to lu
                // an empty profile does not get written to disk. `ProfilEtendu()` is also what
                // a 404 and a server without extended profiles return, and keeping it would wipe
                // a perfectly good banner at the first dropped connection
                val aGarder = lu.copy(activite = null)
                if (aGarder != ProfilEtendu()) noterProfilSurDisque(userId, aGarder)
            }
        }
    }

    /**
     * writes back, under the right key, what the 0.80 fallback had to go and find
     *
     * closes the debt instead of carrying it. the fallback on its own only ever read: nothing
     * rewrites a profile its owner does not edit, so the compatibility branch would have had
     * to live forever, and a branch that lives forever stops being a migration and becomes
     * part of the design
     *
     * only our own account, because a profile is written by the person it belongs to and by
     * nobody else. the server would refuse anything else, and rightly
     *
     * fire and forget, and failures are swallowed on purpose: this repairs, it does not
     * serve. somebody reading a profile must not wait on it, and must not see it fail either,
     * since the value they asked for has already been found and returned. an attempt that
     * does not go through is retried at the next read, the old key still being there
     */
    private fun recollerProfil(userId: String, valeurs: Map<String, String>) {
        val moi = runCatching { currentSession().userId }.getOrNull() ?: return
        if (userId != moi) return
        supervisor.launch {
            runCatching {
                val qui = java.net.URLEncoder.encode(userId, "UTF-8")
                for ((cle, valeur) in valeurs) {
                    val nouvelle = java.net.URLEncoder.encode(cle, "UTF-8")
                    val reponse = requeteSignee(
                        "PUT",
                        "/_matrix/client/v3/profile/$qui/$nouvelle",
                        """{"$cle":"${echapperJson(valeur)}"}""",
                    )
                    // the old key only goes once the new one is in. the other order, on a
                    // connection that drops in between, is how you lose the value you came to
                    // save
                    if (reponse.code in 200..299) {
                        val ancienne = java.net.URLEncoder.encode(
                            cle.replace(".matriscord.", ".velvet."), "UTF-8",
                        )
                        requeteSignee("DELETE", "/_matrix/client/v3/profile/$qui/$ancienne")
                    }
                }
                System.err.println(
                    "[Velvet] profil recollé : ${valeurs.size} clé(s) réécrites après la 0.80"
                )
            }
        }
    }

    override suspend fun setProfilEtendu(cle: String, valeur: String?) {
        val session = currentSession()
        // clear the cache for yourself. without it the screen would read the pre edit profile
        // for a minute, which is exactly when you are checking the change took
        verrouProfils.withLock {
            profilsEnCache.remove(session.userId)
            disqueProfils().remove(session.userId)
        }
        val qui = java.net.URLEncoder.encode(session.userId, "UTF-8")
        val chemin = "/_matrix/client/v3/profile/$qui/${java.net.URLEncoder.encode(cle, "UTF-8")}"

        val reponse = if (valeur.isNullOrBlank()) {
            // delete rather than write an empty string. an empty field and a missing field read
            // the same, and the first leaves a dead key in the profile
            requeteSignee("DELETE", chemin)
        } else {
            requeteSignee("PUT", chemin, """{"$cle":"${echapperJson(valeur)}"}""")
        }

        // a 404 on a delete means it was not there, so the wanted result is already in place.
        // same call as for unpinning
        if (reponse.code !in 200..299 && !(reponse.code == 404 && valeur.isNullOrBlank())) {
            throw EngineException.Unexpected(
                tr("Ce serveur n'accepte pas les profils étendus (${reponse.code}) : ${reponse.corps}", "This server does not accept extended profiles (${reponse.code}): ${reponse.corps}")
            )
        }
    }

    override suspend fun personnesBloquees(): List<String> = guardListe {
        requireClient().ignoredUsers()
    }

    override suspend fun bloquer(userId: String) = guard(tr("Blocage impossible", "Could not block")) {
        requireClient().ignoreUser(userId)
    }

    override suspend fun debloquer(userId: String) = guard(tr("Déblocage impossible", "Could not unblock")) {
        requireClient().unignoreUser(userId)
    }

    // ------------------------------------------------------------------------------ friends

    override suspend fun estAmi(userId: String): Boolean {
        val salon = directs().firstOrNull { it.userId == userId } ?: return false
        return estAmiDans(salon.roomId)
    }

    override suspend fun amis(): List<String> = guardListe {
        // one read per conversation. fine here and nowhere else: a dozen conversations on a
        // small server, and the state is tiny. at hundreds it would have to come from the sync
        // instead, same call as presence and the same limit written down in advance
        directs().filter { runCatching { estAmiDans(it.roomId) }.getOrDefault(false) }
            .map { it.userId }
    }

    override suspend fun setAmi(userId: String, ami: Boolean) {
        // writing assumes a conversation, and creating one if it does not exist is exactly what
        // sending a friend request means here. the room invite is the request
        val roomId = openDirect(userId)
        val contenu = kotlinx.serialization.json.buildJsonObject {
            put("state", if (ami) ETAT_AMIS else ETAT_AUCUN)
        }.toString()

        val salon = requireClient().getRoom(roomId)
            ?: throw EngineException.Unexpected(tr("Conversation introuvable : $roomId", "Conversation not found: $roomId"))
        try {
            salon.sendStateEventRaw(TYPE_AMI, "", contenu)
        } catch (t: Throwable) {
            // said out loud, never swallowed. our dms are created as `TRUSTED_PRIVATE_CHAT`,
            // which hands the invitee the same power, so the write goes through from both sides.
            // a dm opened from element or fluffychat is not necessarily, and the invitee ends up
            // without the right to write state. that is the trap: an "accept" that does nothing,
            // with a symptom very far from its cause
            throw EngineException.Unexpected(
                tr("Cette conversation ne permet pas d'y inscrire une relation — elle a sans ", "This conversation does not allow recording a relationship — it was probably ") +
                    tr("doute été créée depuis un autre client, qui n'accorde pas ce droit à ", "created from another client, which does not grant that right to ") +
                    tr("l'invité.", "the invitee."),
                t,
            )
        }
    }

    private suspend fun estAmiDans(roomId: String): Boolean {
        val etat = lireEtat(roomId, TYPE_AMI) ?: return false
        return (etat["state"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ==
            ETAT_AMIS
    }

    override suspend fun note(userId: String): String? = runCatching {
        val brut = requireClient().donneeDeCompte(TYPE_NOTES) ?: return null
        val carte = kotlinx.serialization.json.Json.parseToJsonElement(brut)
            as? kotlinx.serialization.json.JsonObject ?: return null
        (carte[userId] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    override suspend fun setNote(userId: String, note: String?) = guard(tr("Note impossible", "Could not save the note")) {
        val client = requireClient()
        // read before write. account data has no partial update, so writing without reading
        // would wipe the notes taken on everybody else. same trap as the emoji pack and the pins,
        // third time now
        val actuelles = runCatching {
            client.donneeDeCompte(TYPE_NOTES)?.let {
                kotlinx.serialization.json.Json.parseToJsonElement(it)
                    as? kotlinx.serialization.json.JsonObject
            }
        }.getOrNull()?.toMutableMap() ?: mutableMapOf()

        if (note.isNullOrBlank()) actuelles.remove(userId)
        else actuelles[userId] = kotlinx.serialization.json.JsonPrimitive(note)

        client.setAccountData(
            TYPE_NOTES,
            kotlinx.serialization.json.JsonObject(actuelles).toString(),
        )
    }

    override suspend fun pinnedMessages(channelId: String): List<String> =
        (lireEtat(channelId, TYPE_EPINGLES)?.get("pinned")
            as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            .orEmpty()
            // the state holds pins oldest to newest, and we read the other way round, because
            // what gets pinned is mostly what was just being talked about
            .asReversed()

    override suspend fun setMessagePinned(
        channelId: String,
        messageId: String,
        pinned: Boolean,
    ) = guard(tr("Épinglage impossible", "Could not pin")) {
        // a local echo has no event id, and writing one into shared state would leave a value no
        // client could resolve. see `exigerEventId`
        val evenement = exigerEventId(messageId, tr("Épinglage impossible", "Could not pin"))

        // read before write, or you wipe everybody else's pins. a state event has no partial
        // update. same trap as the emoji pack
        val actuelles = pinnedMessages(channelId).asReversed().toMutableList()
        actuelles.remove(evenement)
        if (pinned) actuelles += evenement

        val contenu = kotlinx.serialization.json.JsonObject(
            mapOf(
                "pinned" to kotlinx.serialization.json.JsonArray(
                    actuelles.map { kotlinx.serialization.json.JsonPrimitive(it) }
                )
            )
        )
        val salon = requireClient().getRoom(channelId)
            ?: throw EngineException.Unexpected(tr("Salon inconnu : $channelId", "Unknown channel: $channelId"))
        salon.sendStateEventRaw(TYPE_EPINGLES, "", contenu.toString())
    }

    override suspend fun messageLink(channelId: String, messageId: String): String {
        // the alias when there is one, the id otherwise
        //
        // the difference is not cosmetic. an internal room id is not routable for someone not
        // already in the room, since nothing in it says which server to go through to join. an
        // alias does
        val salon = runCatching { requireClient().getRoom(channelId) }.getOrNull()
        val cible = runCatching { salon?.canonicalAlias() }.getOrNull() ?: channelId
        return "https://matrix.to/#/" +
            java.net.URLEncoder.encode(cible, "UTF-8") + "/" +
            java.net.URLEncoder.encode(messageId, "UTF-8")
    }

    /**
     * an event id starts with `$`, everything else is a transaction id, so a message still in the
     * send queue. the distinction matters: you can delete or edit a message before it is
     * confirmed, and the sdk needs to know which of the two you are naming
     */
    private fun String.toEventRef(): EventOrTransactionId =
        if (startsWith("$")) EventOrTransactionId.EventId(this)
        else EventOrTransactionId.TransactionId(this)

    private inline fun guard(message: String, block: () -> Unit) = try {
        block()
    } catch (t: Throwable) {
        throw EngineException.Unexpected(message, t)
    }

    override fun messagesFlow(channelId: String): Flow<List<Message>> =
        fluxDe(ferme = false) { timelineFor(channelId) }

    /**
     * the pinned messages, with their bodies
     *
     * `pinnedMessages` returns ids, and the details screen could only show the ones whose message
     * happened to be inside its window, so it displayed a count of "missing". that limit was self
     * inflicted, and it is gone
     *
     * `pinnedMessages` stays: writing, pinning and unpinning, and the "is this message pinned"
     * test all work on ids and need no bodies at all
     */
    override fun pinnedFlow(channelId: String): Flow<List<Message>> = flow {
        val identifiants = pinnedMessages(channelId)
        if (identifiants.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        // emitted as they arrive. in a room with ten pins, waiting for the tenth before showing
        // the first would mean an empty screen for several seconds
        val trouves = mutableListOf<Message>()
        identifiants.forEach { id ->
            // one unreachable pin must not take the others down with it
            //
            // `messageUnique` returns null on a timeout, which the loop already handled. but
            // `timelineConfiguree` inside it THROWS when the sync is not running or the room list
            // does not know the room, and that throw left through the whole flow: one bad pin and
            // the panel showed nothing at all. the promise of resilience was already written three
            // lines below, the null path alone did not keep it
            essayer { messageUnique(channelId, id) }.getOrNull()?.let {
                trouves += it
                emit(trouves.toList())
            }
        }
        // a final emission even when nothing was found, otherwise the screen sits on "loading"
        // forever for pins that have become unrecoverable
        emit(trouves.toList())
    }

    /**
     * one specific message, fetched wherever it lives
     *
     * goes through a focused timeline with no context: the sdk fetches the event, decrypts it and
     * hands it back. this is what replaces `TimelineFocus.PinnedEvents`
     *
     * why the detour, and it is worth writing down. `PinnedEvents` builds its list from
     * `RoomInfo.pinnedEventIds`, which the sync feeds. that field comes back empty even when the
     * room does carry a pin, because sliding sync does not bring `m.room.pinned_events` and its
     * `required_state` is not settable from uniffi. so the timeline built without error and
     * loaded nothing: no request, no exception, an empty list indistinguishable from a room with
     * no pins. a feature that looks like it works
     *
     * `pinnedMessages` reads the room state through the client api directly, so it does not
     * depend on what the sync felt like carrying. it is the authority on which pins exist, and
     * this function only gives them bodies
     *
     * the timeout bounds the wait. a deleted or unreachable event must not hold up the whole list
     */
    private suspend fun messageUnique(channelId: String, eventId: String): Message? =
        withTimeoutOrNull(DELAI_EVENEMENT_MS) {
            fluxDe(ferme = true) {
                timelineConfiguree(
                    channelId,
                    TimelineFocus.Event(
                        eventId = eventId,
                        numContextEvents = 0u,
                        threadMode = uniffi.matrix_sdk_ui.TimelineEventFocusThreadMode
                            .Automatic(hideThreadedEvents = false),
                    ),
                )
            }
                .mapNotNull { liste -> liste.firstOrNull { it.id == eventId } }
                .first()
        }

    /**
     * how long to wait for a single event
     *
     * five seconds. past that it is not latency any more, it is an event we are not going to get,
     * and holding on longer blocks the pins that did arrive
     */
    private val DELAI_EVENEMENT_MS = 5_000L

    /**
     * the context around a message, including outside the loaded window
     *
     * the sdk describes `TimelineFocus.Event` as the initial event to centre on, usually a
     * permalink's target. that is exactly jumping to a message from a quote, and the hard part,
     * paginating towards a distant target without making the screen jump, is the sdk's problem
     * rather than ours: it hands back a centred timeline with context on both sides
     *
     * @param contexte how many events to load on each side of the target. deliberately modest,
     *   since this timeline is a visit and not somewhere you settle in. you go and see what was
     *   said, then you come back to the present
     */
    override fun contexteFlow(
        channelId: String,
        eventId: String,
        contexte: Int,
    ): Flow<List<Message>> = fluxDe(ferme = true) {
        timelineConfiguree(
            channelId,
            TimelineFocus.Event(
                eventId = eventId,
                numContextEvents = contexte.toUShort(),
                // `Automatic` means that if the target is in a thread, the timeline keeps to
                // that thread. which is what we want, since jumping to a thread reply and landing
                // in the middle of the main conversation would make no sense
                threadMode = uniffi.matrix_sdk_ui.TimelineEventFocusThreadMode.Automatic(
                    hideThreadedEvents = false,
                ),
            ),
        )
    }

    /**
     * a throwaway timeline, built for one particular focus
     *
     * deliberately outside the `timelines` cache, and that is the point: the cache maps a room to
     * its live timeline, the one you send on. filing a pinned timeline in there would send the
     * next message into the wrong object, exactly the kind of mistake the cache's lock exists to
     * prevent. this one belongs to its flow and closes with it
     */
    private suspend fun timelineConfiguree(
        channelId: String,
        focus: TimelineFocus,
    ): org.matrix.rustcomponents.sdk.Timeline {
        val roomList = syncService?.roomListService()
            ?: throw EngineException.Unexpected(tr("Synchronisation non démarrée", "Sync not started"))
        val room = try {
            roomList.room(channelId)
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Salon inconnu : $channelId", "Unknown channel: $channelId"), t)
        }
        return room.timelineWithConfiguration(
            org.matrix.rustcomponents.sdk.TimelineConfiguration(
                focus = focus,
                filter = org.matrix.rustcomponents.sdk.TimelineFilter.All,
                internalIdPrefix = null,
                dateDividerMode = org.matrix.rustcomponents.sdk.DateDividerMode.DAILY,
                // off, and the sdk warns it is not free: tracking read receipts has "a non
                // negligible cost". a pin list or a context preview shows none of them
                trackReadReceipts =
                    uniffi.matrix_sdk_ui.TimelineReadReceiptTracking.DISABLED,
                reportUtds = false,
            )
        )
    }

    /**
     * the flow of a timeline, whichever one
     *
     * pulled out of `messagesFlow` so focused timelines can use it too. writing a second diff
     * loop would have meant two paths to the same output with only one of them getting the
     * fixes, and this one is far from trivial: it replays diffs and republishes a whole snapshot,
     * precisely so the ui does not have to
     *
     * @param ferme does the timeline belong to this flow? true for a throwaway one, which has to
     *   be closed or it leaves a subscription open on the rust side. false for the room's live
     *   timeline, which is shared and kept in the cache
     */
    private fun fluxDe(
        ferme: Boolean,
        construire: suspend () -> org.matrix.rustcomponents.sdk.Timeline,
    ): Flow<List<Message>> = callbackFlow {
        val timeline = construire()
        val items = mutableListOf<SdkTimelineItem>()

        // the sdk emits diffs from its own threads. we replay each one onto a local list and
        // republish a whole snapshot, so the ui gets a consistent state rather than a sequence of
        // operations to apply itself
        val handle = timeline.addListener(object : TimelineListener {
            override fun onUpdate(diff: List<TimelineDiff>) {
                val snapshot = synchronized(items) {
                    diff.forEach { apply(items, it) }
                    items.toList()
                }
                trySend(snapshot.toMessages().avecCitations())
            }
        })

        // initial history, in the background, since the subscriber has to get the already cached
        // state without waiting for the network
        //
        // except on a focused timeline, which is already centred on its target and carries its
        // context. paginating on top would take it back past what we came to look at
        if (!ferme) launch { runCatching { timeline.paginateBackwards(INITIAL_PAGE) } }

        awaitClose {
            handle.cancel()
            if (ferme) runCatching { timeline.close() }
        }
    }
        // the sdk happily re emits diffs with no visible consequence, read receipts, resolved
        // profiles. without this filter the ui would redraw for nothing
        .distinctUntilChanged()

    /**
     * the "the room list moved" signal, saying nothing about what moved
     *
     * pulled out of `serversFlow`, which used to be its only consumer, because it now has two,
     * and the reason for the split is a real bug rather than a preference
     *
     * `serversFlow` ends with `.map { servers() }.distinctUntilChanged()`, and `servers()` only
     * returns joined spaces. on an account with no server, receiving an invite does produce a
     * list diff, but the server list stays empty, so identical, so dropped by
     * `distinctUntilChanged`, and the invite never reached the screen. the badge simply did not
     * appear until a restart
     *
     * the raw signal has no such filter. whatever depends on it decides for itself what changed
     */
    private fun signalSalons(): Flow<Unit> = merge(callbackFlow<Unit> {
        val roomList = syncService?.roomListService()
            ?: throw EngineException.Unexpected(tr("Synchronisation non démarrée", "Sync not started"))

        val all = roomList.allRooms()
        val entries = all.entriesWithDynamicAdapters(
            pageSize = ROOM_PAGE_SIZE,
            listener = object : RoomListEntriesListener {
                // we do not use the diff's contents: it carries rooms flat, while the product
                // model needs the space hierarchy. so the diff is just a "something changed"
                // signal
                override fun onUpdate(roomEntriesUpdate: List<RoomListEntriesUpdate>) {
                    trySend(Unit)
                }
            },
        )
        entries.controller().setFilter(RoomListEntriesDynamicFilterKind.NonLeft)

        // unread counters live in `RoomInfo` and not in the room list, so an incoming message
        // does not necessarily produce a list diff. without this second signal a badge might only
        // appear at the next structural change, which is to say never
        val stateHandle = roomList.state(object : RoomListServiceStateListener {
            override fun onUpdate(state: RoomListServiceState) {
                if (state == RoomListServiceState.RUNNING) trySend(Unit)
            }
        })

        trySend(Unit)
        awaitClose {
            // the entries stream subscription stays alive until cancelled, so every abandoned
            // collection would otherwise leave a listener behind
            runCatching { entries.entriesStream().cancel() }
            runCatching { stateHandle.cancel() }
        }
    }, signalManuel)
        // rederiving the hierarchy costs several calls, so we group the bursts of diffs that
        // come with a single incoming message. the manual poke goes through the same grouping,
        // since creating a server empties the cache twice, the space then its general channel,
        // and one reread covers both
        .debounce(SERVERS_DEBOUNCE_MS)

    /**
     * the flows derived from the room list, shared between all their subscribers
     *
     * measured on an account with fifteen rooms, before the fix: every emission produced two log
     * lines a few milliseconds apart, on two different threads. `servers()` was therefore
     * computed twice, once for the screen and once for the background service, because a
     * `callbackFlow` reruns everything upstream of it for each collector. around 30 to 50 ms per
     * derivation normally, 140 odd when the hierarchy cache had just expired. times two, every
     * 400 ms
     *
     * `shareIn` kills the duplication: one sdk listener, one computation, N readers. `replay = 1`
     * because a late subscriber, the service starts after the screen, has to get the current
     * state without waiting for the next message. `WhileSubscribed` with a grace period because a
     * screen rotation unsubscribes and resubscribes immediately, and without it we would restart
     * the sdk subscription for nothing
     *
     * what it does not fix: the derivation itself still costs its 30 ms, and it scales with the
     * number of rooms. fine at fifteen. at three hundred it would have to derive per room rather
     * than rebuild the whole hierarchy
     */
    @Volatile
    private var fluxServeurs: Flow<List<Server>>? = null

    /**
     * what the user just did, shown before the homeserver confirms it
     *
     * every structural write goes through synapse's rate limit, about 5 s per write once the
     * burst is spent, and leaving a server is one write per channel. waiting for the sync to
     * reflect the gesture made it look ignored for tens of seconds, sometimes minutes. so the
     * gesture lands here at once, the real list takes over when it catches up, and a failure
     * takes it back out (the caller gets the exception and says so). here rather than in each
     * app, so both get it and neither can forget it
     */
    internal data class Optimisme(
        /** servers and channels being left or deleted */
        val partis: Set<String> = emptySet(),
        val serveurs: List<Server> = emptyList(),
        /** server id to channels not yet in the real list */
        val salons: Map<String, List<Channel>> = emptyMap(),
        val categories: Map<String, List<li.velvetchat.core.model.Categorie>> = emptyMap(),
    ) {
        fun avecSalon(serveur: String, c: Channel, retirer: Boolean = false) = copy(
            salons = salons + (serveur to salons[serveur].orEmpty().let { if (retirer) it - c else it + c }),
        )

        fun avecCategorie(serveur: String, c: li.velvetchat.core.model.Categorie, retirer: Boolean = false) = copy(
            categories = categories + (serveur to categories[serveur].orEmpty().let { if (retirer) it - c else it + c }),
        )

        fun sur(base: List<Server>): List<Server> {
            if (this == Optimisme()) return base
            val connus = base.map { it.id }.toSet()
            return (base + serveurs.filter { it.id !in connus })
                .filter { it.id !in partis }
                .map { s ->
                    val salonsConnus = s.channels.map { it.id }.toSet()
                    val categoriesConnues = s.categories.map { it.id }.toSet()
                    s.copy(
                        channels = (s.channels + salons[s.id].orEmpty().filter { it.id !in salonsConnus })
                            .filter { it.id !in partis },
                        categories = s.categories +
                            categories[s.id].orEmpty().filter { it.id !in categoriesConnues },
                    )
                }
        }

        /** forgets what the real list now shows by itself, or no longer shows for a departure */
        fun elaguer(base: List<Server>): Optimisme {
            val serveursReels = base.map { it.id }.toSet()
            val salonsReels = base.flatMap { s -> s.channels.map { it.id } }.toSet()
            val categoriesReelles = base.flatMap { s -> s.categories.map { it.id } }.toSet()
            return copy(
                partis = partis.filter { it in serveursReels || it in salonsReels }.toSet(),
                serveurs = serveurs.filter { it.id !in serveursReels },
                salons = salons.mapValues { (_, l) -> l.filter { it.id !in salonsReels } }.filterValues { it.isNotEmpty() },
                categories = categories.mapValues { (_, l) -> l.filter { it.id !in categoriesReelles } }
                    .filterValues { it.isNotEmpty() },
            )
        }
    }

    private val optimisme = MutableStateFlow(Optimisme())

    /** the last real list, to name a category from its id when a channel is filed into it */
    @Volatile
    private var derniereListe: List<Server> = emptyList()

    /** shows [montrer] now, runs [bloc], and undoes it with [defaire] if [bloc] fails */
    private suspend fun <T> optimiste(
        montrer: (Optimisme) -> Optimisme,
        defaire: (Optimisme) -> Optimisme,
        bloc: suspend () -> T,
    ): T {
        optimisme.update(montrer)
        return try {
            bloc()
        } catch (t: Throwable) {
            optimisme.update(defaire)
            throw t
        }
    }

    @Volatile
    private var fluxDirects: Flow<List<DirectMessage>>? = null
    private var fluxInvitations: Flow<List<li.velvetchat.core.model.InvitationRecue>>? = null

    /** the chosen order, in memory. read back from the account at startup, rewritten on every
     *  reshuffle */
    @Volatile
    private var ordreServeurs: List<String> = emptyList()

    override suspend fun setOrdreServeurs(ids: List<String>) {
        ordreServeurs = ids
        // and say so. the rail reads `serversFlow`, which only re emits on a structural change,
        // and reordering servers is not one, so nothing fired and the dropped tile sprang back to
        // where it came from. the order was written correctly, the screen was simply never told
        // to read it again
        oublierStructure()
        runCatching {
            requireClient().setAccountData(
                TYPE_ORDRE_SERVEURS,
                kotlinx.serialization.json.JsonObject(
                    mapOf("ordre" to kotlinx.serialization.json.JsonArray(
                        ids.map { kotlinx.serialization.json.JsonPrimitive(it) }
                    ))
                ).toString(),
            )
        }
    }

    /** reads the order back from the account. quiet, since an unsorted rail is a default rail */
    private suspend fun chargerOrdreServeurs() {
        ordreServeurs = runCatching {
            val brut = requireClient().donneeDeCompte(TYPE_ORDRE_SERVEURS) ?: return@runCatching emptyList()
            val racine = kotlinx.serialization.json.Json.parseToJsonElement(brut)
                as? kotlinx.serialization.json.JsonObject ?: return@runCatching emptyList()
            (racine["ordre"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * applies the chosen order. a server the ranking does not know about goes to the end, in its
     * original order, so a server joined a second ago turns up at the bottom of the rail instead
     * of vanishing, which is what a sort that assumes a complete table would do
     */
    private fun trierServeurs(liste: List<Server>): List<Server> {
        val rang = ordreServeurs.withIndex().associate { (i, id) -> id to i }
        if (rang.isEmpty()) return liste
        return liste.withIndex().sortedWith(
            compareBy({ rang[it.value.id] ?: Int.MAX_VALUE }, { it.index })
        ).map { it.value }
    }

    /**
     * the spaces on their own, without walking down into their rooms
     *
     * `topLevelJoinedSpaces()` reads the local store and returns straight away. it is `channelsOf`
     * that costs, with its pagination, one network round trip per space. on ten servers the rail
     * therefore stayed empty for several seconds on every open, and the screen said "no servers"
     * when what it meant was "i do not know yet"
     *
     * emitted first, it puts the rail on screen immediately, and the full list follows a second
     * later and fills the channels in. a server with no channels for one breath reads as loading.
     * an empty rail reads as "i have nothing", which is a lie
     */
    private suspend fun espacesSeuls(): List<Server> = runCatching {
        trierServeurs(
            requireClient().spaceService().topLevelJoinedSpaces().map { space ->
                Server(
                    id = space.roomId,
                    name = space.displayName,
                    avatarUrl = space.avatarUrl,
                    channels = emptyList(),
                    categories = emptyList(),
                )
            },
        )
    }.getOrDefault(emptyList())

    /**
     * the last known list, on disk
     *
     * even emitting the bare spaces first (see [espacesSeuls]), the channel column stays empty for
     * as long as one pagination per server takes. and that list almost never changes, so keeping
     * it as is and redrawing it on the next launch puts the whole app on screen before the first
     * byte of network. what you see is what you saw last time, replaced a second later by the
     * real thing
     *
     * the unread counts are part of it, so they will be a second out of date. that is the right
     * trade: a stale badge beats an empty screen, and the real one lands before you have finished
     * reading
     *
     * written into `dataPath` next to the sdk store, so same lifetime and same wipe on logout
     */
    private val fichierCacheServeurs by lazy { java.io.File(dataPath, "serveurs.json") }

    private fun lireCacheServeurs(): List<Server>? = runCatching {
        val brut = fichierCacheServeurs.takeIf { it.isFile }?.readText() ?: return null
        kotlinx.serialization.json.Json.decodeFromString<List<ServeurEnCache>>(brut)
            .map { it.versModele() }
            .takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun ecrireCacheServeurs(liste: List<Server>) {
        runCatching {
            fichierCacheServeurs.writeText(
                kotlinx.serialization.json.Json.encodeToString(liste.map { ServeurEnCache.de(it) }),
            )
        }
    }

    override fun serversFlow(): Flow<List<Server>> =
        fluxServeurs ?: synchronized(this) {
            fluxServeurs ?: combine(
                signalSalons()
                    .mapNotNull { ouRien("Lecture des serveurs") { servers().also { ecrireCacheServeurs(it) } } }
                    .onStart {
                        // disk first, instant and complete. failing that the bare spaces, which
                        // at least fill the rail
                        val depart = lireCacheServeurs() ?: espacesSeuls().takeIf { it.isNotEmpty() }
                        depart?.let { emit(it) }
                    }
                    .onEach { base ->
                        derniereListe = base
                        optimisme.update { it.elaguer(base) }
                    },
                optimisme,
            ) { base, o -> o.sur(base) }
                .distinctUntilChanged()
                .catch { erreurNonBloquante("Flux des serveurs", it) }
                .shareIn(
                    supervisor,
                    kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(PARTAGE_GRACE_MS),
                    replay = 1,
                )
                .also { fluxServeurs = it }
        }

    /**
     * the two person room that already existed, and that nothing was showing
     *
     * matrix has no "dm type", `m.direct` is what says so. a joined room that is neither in a
     * space nor in `m.direct` therefore appears nowhere in the app, not in the rail and not in
     * the conversation list. it exists, it has its history, and you cannot open it
     *
     * not a laboratory case: the discord import poured four hundred thousand odd events into a
     * room it had not created, so never marked, and the thing was unreachable from velvet
     *
     * without this adoption, `openDirect` would create a second room with the same person, the
     * history would stay where it is, invisible, and the conversation would start again from
     * nothing. the worst possible outcome, because it looks like it works
     *
     * two guards. exactly two members, and not a space room: a two person room filed inside a
     * server is a server channel with two inhabitants, not a private conversation. the cost is
     * one request per joined room, once, at the moment you explicitly ask to write to someone
     */
    private suspend fun adopterSalonExistant(userId: String): String? {
        val moi = runCatching { currentSession().userId }.getOrNull() ?: return null
        val reponse = runCatching {
            requeteSignee("GET", "/_matrix/client/v3/joined_rooms")
        }.getOrNull() ?: return null
        if (reponse.code !in 200..299) return null

        val ids = runCatching {
            (kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                as kotlinx.serialization.json.JsonObject)["joined_rooms"]
                ?.let { it as kotlinx.serialization.json.JsonArray }
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.getOrNull().orEmpty()
        if (ids.isEmpty()) return null

        // rooms that belong to a space are ruled out up front
        val dEspace = runCatching { servers().flatMap { s -> s.channels.map { it.id } } }
            .getOrDefault(emptyList()).toSet()

        for (id in ids) {
            if (id in dEspace) continue
            val chemin = "/_matrix/client/v3/rooms/" +
                java.net.URLEncoder.encode(id, "UTF-8") + "/joined_members"
            val m = runCatching { requeteSignee("GET", chemin) }.getOrNull() ?: continue
            if (m.code !in 200..299) continue
            val membres = runCatching {
                ((kotlinx.serialization.json.Json.parseToJsonElement(m.corps)
                    as kotlinx.serialization.json.JsonObject)["joined"]
                    as? kotlinx.serialization.json.JsonObject)?.keys
            }.getOrNull().orEmpty()
            if (membres.size == 2 && userId in membres && moi in membres) {
                runCatching { marquerCommeDirect(id, userId) }
                return id
            }
        }
        return null
    }

    override suspend fun marquerCommeDirect(salonId: String, userId: String) {
        val client = requireClient()
        val brut = client.accountData("m.direct")
        val carte = brut
            ?.let {
                kotlinx.serialization.json.Json.parseToJsonElement(it)
                    as? kotlinx.serialization.json.JsonObject
            }
            ?.toMutableMap()
            ?: mutableMapOf()

        val existants = (carte[userId] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            .orEmpty()
        if (salonId in existants) return

        carte[userId] = kotlinx.serialization.json.JsonArray(
            (existants + salonId).map { kotlinx.serialization.json.JsonPrimitive(it) }
        )
        client.setAccountData(
            "m.direct",
            kotlinx.serialization.json.JsonObject(carte).toString(),
        )

        // drop the explicit name, otherwise it beats the name derived from the other person and
        // the room keeps calling itself "group with so and so"
        runCatching {
            ecrireEtat(salonId, "m.room.name", "{}")
        }
    }

    /** same cache as the servers one, for the same reason, see `lireCacheServeurs` */
    private val fichierCacheDirects by lazy { java.io.File(dataPath, "directs.json") }

    private fun lireCacheDirects(): List<DirectMessage>? = runCatching {
        val brut = fichierCacheDirects.takeIf { it.isFile }?.readText() ?: return null
        kotlinx.serialization.json.Json.decodeFromString<List<DirectEnCache>>(brut)
            .map { it.versModele() }
            // a file written before the duplicate fix is still sitting on disk, and reading it
            // back as is would crash exactly once, which is worse than a plain bug
            .distinctBy { it.roomId }
            .takeIf { it.isNotEmpty() }
    }.getOrNull()

    override fun directsFlow(): Flow<List<DirectMessage>> =
        fluxDirects ?: synchronized(this) {
            fluxDirects ?: signalSalons()
                .mapNotNull { liste ->
                    ouRien("Lecture des messages privés") { directs() }?.also { d ->
                        runCatching {
                            fichierCacheDirects.writeText(
                                kotlinx.serialization.json.Json.encodeToString(
                                    d.map { DirectEnCache.de(it) },
                                ),
                            )
                        }
                    }
                }
                .onStart { lireCacheDirects()?.let { emit(it) } }
                .distinctUntilChanged()
                .catch { erreurNonBloquante("Flux des messages privés", it) }
                .shareIn(
                    supervisor,
                    kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(PARTAGE_GRACE_MS),
                    replay = 1,
                )
                .also { fluxDirects = it }
        }

    override fun invitationsFlow(): Flow<List<li.velvetchat.core.model.InvitationRecue>> =
        fluxInvitations ?: synchronized(this) {
            fluxInvitations ?: signalSalons()
                .mapNotNull { ouRien("Lecture des invitations") { invitationsRecues() } }
                .distinctUntilChanged()
                .catch { erreurNonBloquante("Flux des invitations", it) }
                .shareIn(
                    supervisor,
                    kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(PARTAGE_GRACE_MS),
                    replay = 1,
                )
                .also { fluxInvitations = it }
        }

    override suspend fun messages(channelId: String, limit: Int): List<Message> {
        val timeline = timelineFor(channelId)
        val items = mutableListOf<SdkTimelineItem>()
        val firstUpdate = CompletableDeferred<Unit>()

        val handle = timeline.addListener(object : TimelineListener {
            override fun onUpdate(diff: List<TimelineDiff>) {
                synchronized(items) { diff.forEach { apply(items, it) } }
                firstUpdate.complete(Unit)
            }
        })

        try {
            // the first batch comes from the local cache with no network, and the pagination
            // fetches what is missing. waiting for one then the other avoids returning an empty
            // list for a room that has content
            runCatching { withTimeout(FIRST_UPDATE_TIMEOUT_MS) { firstUpdate.await() } }
            runCatching { timeline.paginateBackwards(limit.toUShort()) }
            delay(SETTLE_MS)
        } finally {
            handle.cancel()
        }

        val snapshot = synchronized(items) { items.toList() }
        return snapshot.mapNotNull { it.asEvent()?.toMessage() }.takeLast(limit)
            .avecCitations()
    }

    /**
     * turns the sdk's raw list into messages, working out along the way where the "new messages"
     * divider goes
     *
     * the sdk timeline mixes two kinds of item: events, and virtual ones, date dividers, start of
     * conversation, and the read marker. that last one is not an event but a position between two
     * events, which is exactly what the divider needs
     *
     * so we read it before throwing the virtual ones away. the first event after the marker is the
     * first unread. doing it afterwards would mean the position is already gone
     */
    private fun List<SdkTimelineItem>.toMessages(): List<Message> {
        val markerAt = indexOfFirst { it.asVirtual() == VirtualTimelineItem.ReadMarker }
        var firstUnreadSeen = false
        return mapIndexedNotNull { index, item ->
            val evenement = item.asEvent() ?: return@mapIndexedNotNull null
            // a call's plumbing (media keys) travels as room events and is nobody's message
            if (evenement.eventTypeRaw?.startsWith("io.element.call.") == true) return@mapIndexedNotNull null
            val message = evenement.toMessage() ?: return@mapIndexedNotNull null
            // `markerAt >= 0`, since with no marker everything is read and no message carries
            // the flag
            if (!firstUnreadSeen && markerAt >= 0 && index > markerAt) {
                firstUnreadSeen = true
                message.copy(isFirstUnread = true)
            } else {
                message
            }
        }
    }

    /** timeline diffs have exactly the same shape as room list ones */
    private fun apply(target: MutableList<SdkTimelineItem>, diff: TimelineDiff) {
        when (diff) {
            is TimelineDiff.Append -> target.addAll(diff.values)
            is TimelineDiff.PushBack -> target.add(diff.value)
            is TimelineDiff.PushFront -> target.add(0, diff.value)
            is TimelineDiff.Insert -> target.add(diff.index.toInt(), diff.value)
            is TimelineDiff.Set -> target[diff.index.toInt()] = diff.value
            is TimelineDiff.Remove -> target.removeAt(diff.index.toInt())
            is TimelineDiff.Truncate ->
                while (target.size > diff.length.toInt()) target.removeAt(target.size - 1)
            is TimelineDiff.Reset -> { target.clear(); target.addAll(diff.values) }
            TimelineDiff.PopBack -> target.removeLastOrNull()
            TimelineDiff.PopFront -> target.removeFirstOrNull()
            TimelineDiff.Clear -> target.clear()
        }
    }

    private fun EventTimelineItem.toMessage(): Message? {
        // both shapes count. an event confirmed by the server carries an `eventId`, a message
        // still in the send queue carries a `transactionId`. that second case is the local echo,
        // and ignoring it would make the message you just typed disappear from the conversation
        val id = when (val ref = eventOrTransactionId) {
            is EventOrTransactionId.EventId -> ref.eventId
            is EventOrTransactionId.TransactionId -> ref.transactionId
        }
        val state = localSendState.toCore()
        // the handle is grabbed here or nowhere. the sdk cannot find a local echo by its
        // transaction id, since `Timeline` only exposes lookup by `eventId` and a failed message
        // has none. the one chance to hold its `SendHandle` is this item going past, carrying it
        if (state is SendState.Failed) {
            (eventOrTransactionId as? EventOrTransactionId.TransactionId)?.let { ref ->
                lazyProvider.getSendHandle()?.let { poignees[ref.transactionId] = it }
            }
        }

        val msgLike = (content as? TimelineItemContent.MsgLike)?.content
            ?: return Message(
                id = id,
                sender = sender,
                senderDisplayName = displayName(),
                senderAvatarUrl = avatarUrl(),
                timestamp = timestamp.toLong(),
                isOwn = isOwn,
                isEdited = false,
                body = content.toSystemBody(sender, displayName(), eventTypeRaw),
                sendState = state,
            )

        var edited = false
        var mentions = emptyList<String>()
        var toutLeSalon = false
        val body = when (val kind = msgLike.kind) {
            is MsgLikeKind.Message -> {
                edited = kind.content.isEdited
                kind.content.mentions?.let { mentions = it.userIds; toutLeSalon = it.room }
                kind.content.toBody()
            }
            // the sdk did try and could not. we say so
            is MsgLikeKind.UnableToDecrypt -> MessageBody.Undecryptable(kind.msg.toReason())
            MsgLikeKind.Redacted -> MessageBody.Redacted
            // a sticker is an image, and rendering it as one costs a line. before that it drew
            // "unsupported content" for anyone receiving one from element or fluffychat, which
            // made an incomplete `when` look like a limitation of velvet
            //
            // what it does not do is send them. that wants a picker and a personal pack
            // (`im.ponies.user_emotes`), which is a whole other price. receiving without sending
            // is asymmetric but honest. a grey box was not
            is MsgLikeKind.Sticker -> media(
                kind = MediaKind.IMAGE,
                caption = kind.body,
                filename = kind.body.ifBlank { "autocollant" },
                source = kind.source,
                mimeType = kind.info.mimetype,
                size = kind.info.size,
                width = kind.info.width,
                height = kind.info.height,
                blurhash = kind.info.blurhash,
                thumbnail = kind.info.thumbnailSource,
            )
            is MsgLikeKind.Poll -> kind.toBody()
            is MsgLikeKind.LiveLocation -> MessageBody.Unsupported("localisation")
            is MsgLikeKind.Other -> MessageBody.Unsupported(tr("contenu non pris en charge", "unsupported content"))
        }

        return Message(
            id = id,
            sender = sender,
            senderDisplayName = displayName(),
            senderAvatarUrl = avatarUrl(),
            timestamp = timestamp.toLong(),
            isOwn = isOwn,
            isEdited = edited,
            body = if (msgLike.inReplyTo != null) sansRepli(body) else body,
            sendState = state,
            mentions = mentions,
            mentionneToutLeSalon = toutLeSalon,
            // the thread summary comes from the server. the homeserver attaches `numReplies` to
            // the root event, and counting it ourselves would only know the replies already
            // loaded, so almost always zero
            reponsesEnFil = msgLike.threadSummary?.numReplies()?.toInt()?.takeIf { it > 0 },
            reactions = msgLike.reactions.map { r ->
                Reaction(
                    emoji = r.key,
                    count = r.senders.size,
                    // knowing whether you reacted yourself is not decorative, it decides
                    // whether a click adds or removes
                    mine = r.senders.any { it.senderId == ownUserId },
                    par = r.senders.map { it.senderId },
                )
            },
            replyTo = msgLike.inReplyTo?.let { runCatching { it.eventId() }.getOrNull() },
            citation = msgLike.inReplyTo?.let { citationDe(it) },
        )
    }

    /**
     * strips the reply fallback out of a reply
     *
     * the protocol used to ask that a reply copy the quoted message at the top of its own body,
     * prefixed with `>` and followed by a blank line, the rich reply fallback. that is deprecated
     * now and we do not produce it, our replies go out with `m.in_reply_to` alone. element still
     * sends it though, and ignoring it gave this on screen:
     *
     *     > hello
     *
     *     hello yourself
     *
     * that is, the quoted message twice, once in the quote we draw above it and once in plain
     * text in the body
     *
     * we only strip what matches the fallback's exact shape: `>` lines at the very start, then a
     * blank line. a quote someone deliberately wrote in the middle of a message does not look
     * like that and survives, which is the difference between cleaning and censoring
     *
     * the html gets the same treatment. the fallback lives in an `<mx-reply>` there, a tag the
     * protocol reserves for it, so it cannot be content anyone meant to write
     */
    private fun sansRepli(body: MessageBody): MessageBody {
        if (body !is MessageBody.Text) return body

        val lignes = body.text.lines()
        var i = 0
        while (i < lignes.size && lignes[i].startsWith(">")) i++
        // nothing consumed, or nothing left after: either way this is not a fallback, it is the
        // message itself, and we leave it alone
        if (i == 0 || i >= lignes.size) return body
        // the blank separator line is part of the fallback
        if (lignes[i].isBlank()) i++
        // a hardcoded `\n` and not `System.lineSeparator()`. a matrix message body separates its
        // lines that way whatever the platform, and rejoining with the platform's separator would
        // put `\r` back on lines that never had one
        val reste = lignes.drop(i).joinToString(SAUT_DE_LIGNE)
        if (reste.isBlank()) return body

        val html = body.html?.let { texte ->
            Regex("(?is)<mx-reply>.*?</mx-reply>").replace(texte, "").trim().ifBlank { null }
        }
        return MessageBody.Text(reste, html ?: body.html?.takeIf { !it.contains("<mx-reply", true) })
    }

    /**
     * the quoted message's excerpt, when the sdk has it to hand
     *
     * nothing is asked of the network here. `EmbeddedEventDetails` is either `Ready`, the sdk
     * already had the event, or `Pending` / `Unavailable`. we return null in those two rather than
     * go and fetch, because this runs for every message of every timeline snapshot, and one round
     * trip per quote would bill the network for every keystroke somebody else makes in the room
     *
     * the consequence is accepted: a quote can show up empty and fill in on the next diff, once
     * the sdk has resolved the target. that is the right way to degrade, the relationship shows
     * immediately and the excerpt arrives when it arrives
     */
    private fun citationDe(details: InReplyToDetails): Citation? {
        val id = runCatching { details.eventId() }.getOrNull() ?: return null
        val cible = runCatching { details.event() }.getOrNull() as? EmbeddedEventDetails.Ready
            ?: return null
        val profil = cible.senderProfile as? ProfileDetails.Ready
        return Citation(
            eventId = id,
            auteurId = cible.sender,
            auteur = profil?.displayName
                ?: cible.sender.removePrefix("@").substringBefore(':'),
            auteurAvatarUrl = profil?.avatarUrl,
            // `apercuDe` only returns text for text, since an image's `body` is its filename.
            // for everything else the quote names what the thing is, which is what `enApercu`
            // does, and we go back through it rather than write a second media type table here
            // that would drift. the quote should say "he replied to your photo", not "he replied
            // to IMG_4821.jpg"
            apercu = apercuDe(cible.content)
                ?: ((cible.content as? TimelineItemContent.MsgLike)?.content?.kind
                    as? MsgLikeKind.Message)?.content?.toBody()?.enApercu()
                ?: "Message",
        )
    }

    private fun EventTimelineItem.displayName(): String? =
        surnomsMemoire[sender]
            ?: (senderProfile as? ProfileDetails.Ready)?.displayName

    private fun EventTimelineItem.avatarUrl(): String? =
        (senderProfile as? ProfileDetails.Ready)?.avatarUrl

    /**
     * turns a state event into something displayable
     *
     * all of this used to fall through to `Unsupported("m.room.member")`, which is a protocol type
     * name sitting in the middle of a conversation. dropping them would have been worse, since
     * "so and so joined" is part of what you expect from a discord shaped client
     *
     * the actor is the event's sender, not its subject. the difference matters on a kick: the
     * moderator emits it, the kicked person is the target. conflating them would display "julie
     * kicked julie"
     */
    private fun TimelineItemContent.toSystemBody(
        sender: String,
        senderName: String?,
        eventTypeRaw: String?,
    ): MessageBody {
        fun system(
            kind: SystemKind,
            target: String? = null,
            targetName: String? = null,
            detail: String? = null,
        ) = MessageBody.System(kind, sender, senderName, target, targetName, detail)

        return when (this) {
            is TimelineItemContent.RoomMembership -> system(
                kind = when (change) {
                    MembershipChange.JOINED -> SystemKind.JOINED
                    MembershipChange.LEFT -> SystemKind.LEFT
                    MembershipChange.INVITED -> SystemKind.INVITED
                    MembershipChange.INVITATION_ACCEPTED -> SystemKind.INVITE_ACCEPTED
                    MembershipChange.INVITATION_REJECTED -> SystemKind.INVITE_REJECTED
                    MembershipChange.INVITATION_REVOKED -> SystemKind.INVITE_REVOKED
                    MembershipChange.KICKED -> SystemKind.KICKED
                    MembershipChange.BANNED, MembershipChange.KICKED_AND_BANNED -> SystemKind.BANNED
                    MembershipChange.UNBANNED -> SystemKind.UNBANNED
                    MembershipChange.KNOCKED, MembershipChange.KNOCK_ACCEPTED,
                    MembershipChange.KNOCK_RETRACTED, MembershipChange.KNOCK_DENIED,
                    -> SystemKind.KNOCKED
                    else -> SystemKind.OTHER
                },
                target = userId,
                targetName = userDisplayName,
                detail = reason,
            )

            // a profile change is an `m.room.member` like any other, only with no membership
            // change. the sdk does that sorting, not us
            is TimelineItemContent.ProfileChange -> when {
                displayName != prevDisplayName ->
                    system(SystemKind.DISPLAY_NAME_CHANGED, detail = displayName)
                avatarUrl != prevAvatarUrl -> system(SystemKind.AVATAR_CHANGED)
                else -> system(SystemKind.OTHER)
            }

            is TimelineItemContent.State -> when (val s = content) {
                is OtherState.RoomCreate -> system(SystemKind.ROOM_CREATED)
                is OtherState.RoomName -> system(SystemKind.ROOM_NAME_CHANGED, detail = s.name)
                is OtherState.RoomTopic -> system(SystemKind.ROOM_TOPIC_CHANGED, detail = s.topic)
                is OtherState.RoomAvatar -> system(SystemKind.ROOM_AVATAR_CHANGED)
                OtherState.RoomEncryption -> system(SystemKind.ENCRYPTION_ENABLED)
                OtherState.RoomTombstone -> system(SystemKind.ROOM_UPGRADED)
                // join rules, history visibility, power levels, acls, spaces and the rest.
                // recognised as state events with no dedicated rendering: `isNoise` hides them by
                // default, and the raw type stays available for a "show everything" mode
                else -> system(SystemKind.OTHER, detail = eventTypeRaw)
            }

            TimelineItemContent.CallInvite -> system(SystemKind.CALL)
            is TimelineItemContent.RtcNotification -> system(SystemKind.CALL)

            // these are not states we chose not to render, they are events the sdk could not
            // read. passing them off as system events would hide a real problem
            is TimelineItemContent.FailedToParseMessageLike ->
                MessageBody.Unsupported(tr("événement illisible : $eventType", "unreadable event: $eventType"))
            is TimelineItemContent.FailedToParseState ->
                MessageBody.Unsupported(tr("état illisible : $eventType", "unreadable state: $eventType"))

            else -> system(SystemKind.OTHER, detail = eventTypeRaw)
        }
    }

    private fun MessageContent.toBody(): MessageBody = msgType.toBody(body)

    /**
     * the send handles of failed local echoes, by transaction id
     *
     * concurrent, and not out of principle: it is filled from the thread the sdk emits its diffs
     * on, and read from the one where somebody taps
     *
     * it does not grow forever. a failed message is rare and its entry leaves on the first retry.
     * a handle that has gone stale, the message went through in the end, or the app restarted,
     * does nothing dangerous, [SendHandle.tryResend] simply has no effect
     */
    private val poignees = java.util.concurrent.ConcurrentHashMap<String, SendHandle>()

    /**
     * previews already fetched, oldest to most recently read
     *
     * a cache is required here, not a comfort. the preview is asked for from a message's render,
     * so with no memory, scrolling a conversation holding ten links would fire ten requests every
     * time past, and the server would go and refetch the pages
     *
     * a `LinkedHashMap` in access order is an lru cache from the standard library, and there is
     * nothing to write to get one. failures are remembered too, as null, since a page with no
     * opengraph must not be asked for again on every scroll
     */
    private val apercus = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, ApercuLien?>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, ApercuLien?>) = size > 128
        }
    )

    override suspend fun apercuLien(url: String): ApercuLien? {
        if (apercus.containsKey(url)) return apercus[url]

        // no exception leaves this function. a missing preview is normal: the option is off on
        // the server (404), a private address refused by the blocklist (403), an unreachable site,
        // a page with no tags. in every case the link is still readable, and raising an error
        // would only force every caller to swallow it
        val trouve = runCatching {
            val reponse = requeteSignee(
                "GET",
                "/_matrix/client/v1/media/preview_url?url=" + url.encodeUrl(),
            )
            if (reponse.code != 200) return@runCatching null

            val objet = kotlinx.serialization.json.Json
                .parseToJsonElement(reponse.corps) as? kotlinx.serialization.json.JsonObject
                ?: return@runCatching null

            fun texte(cle: String) = (objet[cle] as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull?.takeIf { it.isNotBlank() }
            fun entier(cle: String) = (objet[cle] as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull?.toIntOrNull()

            ApercuLien(
                url = url,
                titre = texte("og:title"),
                // an opengraph description often comes from the page body and carries line
                // breaks. wikipedia manages about three before the first useful word
                description = texte("og:description")
                    ?.replace(Regex("""\s+"""), " ")?.trim()?.takeIf { it.isNotBlank() },
                site = texte("og:site_name"),
                image = texte("og:image")?.takeIf { it.startsWith("mxc://") },
                largeurImage = entier("og:image:width"),
                hauteurImage = entier("og:image:height"),
            ).takeIf { !it.vide }
        }.getOrNull()

        apercus[url] = trouve
        return trouve
    }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    override suspend fun renvoyer(channelId: String, messageId: String) = guard(
        tr("Renvoi impossible", "Could not resend")
    ) {
        val poignee = poignees.remove(messageId)
            ?: error(tr("ce message n'est plus dans la file d'envoi", "this message is no longer in the send queue"))
        poignee.tryResend()
    }

    /**
     * null means a remote event: it came from the server, so it got there. a non null state only
     * exists for our own messages, while they are still in the queue
     */
    private fun EventSendState?.toCore(): SendState = when (this) {
        null, is EventSendState.Sent -> SendState.Sent
        is EventSendState.NotSentYet -> SendState.Sending
        is EventSendState.SendingFailed -> SendState.Failed(
            reason = error.describe(),
            // a distinction that changes what the ui should offer: in one case an automatic
            // retry is coming, in the other no retry helps until somebody does something
            recoverable = isRecoverable,
        )
    }

    private fun QueueWedgeError.describe(): String = when (this) {
        is QueueWedgeError.InsecureDevices ->
            tr("des appareils non vérifiés sont présents dans le salon", "unverified devices are present in the channel")
        is QueueWedgeError.IdentityViolations ->
            tr("l'identité d'un participant a changé et doit être re-vérifiée", "a participant's identity changed and must be verified again")
        QueueWedgeError.CrossVerificationRequired ->
            tr("cet appareil doit d'abord être vérifié", "this device must be verified first")
        QueueWedgeError.MissingMediaContent -> tr("média introuvable", "media not found")
        is QueueWedgeError.InvalidMimeType -> tr("type de média refusé : $mimeType", "media type refused: $mimeType")
        is QueueWedgeError.GenericApiError -> msg
    }

    /**
     * why this one stayed unreadable, taken from the sdk rather than assumed
     *
     * the megolm branch used to return `MISSING_KEY` and drop `cause` on the floor, so every
     * failure was reported to the screen as "sent before you arrived". that sentence happens
     * to be the only one of the lot that means nothing is broken, and it was covering the
     * four cases where something is
     *
     * the sdk's enum has nine values and the screen has three sentences, so the fold is
     * deliberate: what matters to whoever is reading is whether they can do something about
     * it. the exact value still goes to the log, because that is where you go when the answer
     * is "tell somebody"
     */
    private fun EncryptedMessage.toReason(): UndecryptableReason = when (this) {
        is EncryptedMessage.MegolmV1AesSha2 -> when (cause) {
            // nothing to repair: the key went to the devices that were in the room, and
            // matrix does not hand it out again afterwards
            UtdCause.SENT_BEFORE_WE_JOINED,
            UtdCause.HISTORICAL_MESSAGE_AND_BACKUP_IS_DISABLED,
            UtdCause.HISTORICAL_MESSAGE_AND_DEVICE_IS_UNVERIFIED,
                -> UndecryptableReason.AVANT_NOTRE_ARRIVEE

            // somebody decided we should not have it. verifying the device fixes what comes
            // next, never what already went past
            UtdCause.VERIFICATION_VIOLATION,
            UtdCause.UNSIGNED_DEVICE,
            UtdCause.UNKNOWN_DEVICE,
            UtdCause.WITHHELD_FOR_UNVERIFIED_OR_INSECURE_DEVICE,
            UtdCause.WITHHELD_BY_SENDER,
                -> UndecryptableReason.CLE_REFUSEE

            // the sdk's own wording for this one is "probably a bug, or a network split".
            // that is worth knowing, so it gets its own sentence and a log line
            UtdCause.UNKNOWN -> {
                System.err.println(
                    "[Velvet] message indéchiffrable sans cause nommée : la clé devrait être " +
                        "là. À regarder du côté de la sauvegarde de clés ou de la fédération."
                )
                UndecryptableReason.CLE_MANQUANTE
            }
        }
        is EncryptedMessage.OlmV1Curve25519AesSha2 -> UndecryptableReason.OLM
        EncryptedMessage.Unknown -> UndecryptableReason.UNKNOWN
    }

    /**
     * which url to connect to: the lan homeserver if it answers, [canonical] otherwise. decided
     * once per launch and remembered in [lanReachable]
     */
    private suspend fun connectBase(canonical: String): String {
        val localHomeserverUrl = Installation.ou({ this.lan }, localHomeserverUrl)
        if (localHomeserverUrl.isBlank()) return canonical
        val vivant = lanReachable ?: sondeLan().also { lanReachable = it }
        return if (vivant) localHomeserverUrl.trimEnd('/') else canonical
    }

    /**
     * probes `<localHomeserverUrl>/_matrix/client/versions` with a short timeout. any http answer,
     * error included, proves the lan service is there. only a network failure or a timeout means
     * we are not at home, and then we keep the public domain
     */
    private suspend fun sondeLan(): Boolean = withContext(Dispatchers.IO) {
        val cnx = runCatching {
            (java.net.URI.create(localHomeserverUrl.trimEnd('/') + "/_matrix/client/versions")
                .toURL().openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = SONDE_LAN_MS
                readTimeout = SONDE_LAN_MS
            }
        }.getOrNull() ?: return@withContext false
        try {
            cnx.responseCode in 200..499
        } catch (_: Throwable) {
            false
        } finally {
            cnx.disconnect()
        }
    }

    private suspend fun buildClient(homeserverUrl: String): Client = try {
        ClientBuilder()
            .homeserverUrl(connectBase(homeserverUrl))
            .sessionPaths(dataPath, cachePath)
            .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
            // cross signing and key backup are two different mechanisms, and turning the second
            // on does not create the first. with no published cross signing identity, every device
            // verification fails on "Failed retrieving user identity". so both get bootstrapped
            //
            // not a comfort setting: encryption ships in v1, and an account with no cross signing
            // identity can neither verify its devices nor stop showing warnings in other clients
            .autoEnableCrossSigning(true)
            .autoEnableBackups(true)
            // threads get switched on here and nowhere else. the msc is stable and the server
            // declares it active, and without this flag the sdk builds neither the thread list
            // nor the timelines that go with it, so `threadListService()` would return an empty
            // list with nothing explaining why
            //
            // `threadSubscriptions` comes along: that is what will tell us which threads are
            // followed, so which ones should notify. raising it now saves rebuilding the session
            // the day we use it
            .threadsEnabled(enabled = true, threadSubscriptions = true)
            // sharing earlier megolm keys with a newcomer
            //
            // without it, `history_visibility: shared` gives nothing in an encrypted room: the
            // events are reachable server side but unreadable for want of keys. a newcomer
            // therefore finds an empty conversation, which is the opposite of the intended feel
            //
            // how it works: the inviter bundles the session keys and hands them to the invitee.
            // which implies a structural limit, there has to be an invite. joining through
            // `join_rule: restricted`, which is exactly our server model, produces none
            //
            // the sdk marks it experimental. enabled anyway, since it is the only existing path to
            // a readable history, and it failing degrades to precisely the behaviour we have
            // without it
            .enableShareHistoryOnInvite(true)
            // do not put `roomKeyRecipientStrategy(IDENTITY_BASED_STRATEGY)` here
            //
            // tempting, because it is what is missing for the history sharing to deliver
            // anything. its crypto layer refuses the default strategy for history sharing, with
            // the log to prove it:
            //
            //   WARN  Ignoring request to use unsafe sharing strategy AllDevices
            //         for room key history sharing
            //   INFO  Encrypting content of type m.room_key_bundle recipients={}
            //
            // that is, a key bundle assembled, uploaded, then encrypted for nobody
            //
            // but the strategy is global and not specific to history. identity based, session
            // keys only go to devices signed by their owner's identity. tried and measured:
            // `verifyFlows` then stops passing, and an unverified device receives no current
            // messages at all
            //
            // trading "the history is missing" for "nothing arrives any more" is not progress. so
            // the prerequisite stays device verification, not a setting
            .userAgent(userAgent)
            .setSessionDelegate(delegueSession)
            .let {
                if (trustAnchors.isEmpty()) it
                // order matters: disable the built in roots before adding ours, otherwise
                // `reqwest` merges instead of replacing and the platform verifier stays in the
                // path
                else it.disableBuiltInRootCertificates().addRootCertificates(trustAnchors)
            }
            .let { if (insecureTlsForDevelopment) it.disableSslVerification() else it }
            .build()
    } catch (t: Throwable) {
        throw EngineException.Unreachable("Homeserver injoignable : $homeserverUrl", t)
    }

    private fun requireClient(): Client =
        client ?: throw EngineException.Unexpected(tr("Aucune session ouverte", "No open session"))

    private fun SdkSession.toCore() = CoreSession(
        userId = userId,
        deviceId = deviceId,
        // never the connection url, which may be the lan one, always the public canonical one.
        // this is the value that gets persisted and read back on the next launch
        homeserverUrl = canonicalHomeserverUrl ?: homeserverUrl,
        accessToken = accessToken,
        refreshToken = refreshToken,
        slidingSyncNative = slidingSyncVersion == SlidingSyncVersion.NATIVE,
        oauthData = oauthData,
    )

    private fun CoreSession.toSdk() = SdkSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        userId = userId,
        deviceId = deviceId,
        homeserverUrl = homeserverUrl,
        oauthData = oauthData,
        slidingSyncVersion =
            if (slidingSyncNative) SlidingSyncVersion.NATIVE else SlidingSyncVersion.NONE,
    )

    /** the token service's `/sfu/get` address: the server's (see [Installation]), else the build's */
    private fun serviceJetonVocal(): String? =
        Installation.ou({ this.vocal }, urlJetonVocal).takeIf { it.isNotBlank() }

    override suspend fun jetonVocal(salonId: String): InfoVocal = withContext(Dispatchers.IO) {
        val client = requireClient()
        val session = currentSession()

        // 1. the account's openid token. the sdk asks the homeserver for it, and it is the only
        //    proof of identity lk-jwt-service accepts, which it rechecks on its side
        val openid = try {
            client.requestOpenidToken()
        } catch (e: Exception) {
            throw EngineException.Unexpected(tr("jeton OpenID indisponible : ${e.message}", "OpenID token unavailable: ${e.message}"), e)
        }

        // 2. the /sfu/get body. format checked against the service's rust source: we send room,
        //    openid_token and device_id only. the delayed event fields are optional, that is what
        //    element call posts, and including them empty risks a stray delayed departure
        val corps = buildJsonObject {
            put("room", salonId)
            putJsonObject("openid_token") {
                put("access_token", openid.accessToken)
                put("token_type", openid.tokenType)
                put("matrix_server_name", openid.matrixServerName)
                put("expires_in", openid.expiresInSeconds.toLong())
            }
            put("device_id", session.deviceId)
        }.toString()

        // 3. post to the token service. a different host from the homeserver and auth through
        //    the body rather than a bearer header, so `requeteSignee` is not the way. the
        //    address comes from the installation, since the homeserver cannot advertise it
        val service = serviceJetonVocal()
            ?: throw EngineException.Unexpected(
                tr("Aucun service de jetons vocaux n'est configuré pour cette installation", "No voice token service is configured for this install")
            )
        val reponse = try {
            val cnx = (java.net.URI.create(service).toURL().openConnection()
                as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            try {
                cnx.outputStream.use { it.write(corps.toByteArray()) }
                val code = cnx.responseCode
                val texte = (if (code in 200..299) cnx.inputStream else cnx.errorStream)
                    ?.bufferedReader()?.readText().orEmpty()
                Reponse(code, texte)
            } finally {
                cnx.disconnect()
            }
        } catch (e: Exception) {
            throw EngineException.Unreachable(tr("service de jetons vocal injoignable : ${e.message}", "voice token service unreachable: ${e.message}"), e)
        }

        if (reponse.code !in 200..299) {
            throw EngineException.Unexpected(tr("jeton vocal refusé (${reponse.code}) : ${reponse.corps}", "voice token refused (${reponse.code}): ${reponse.corps}"))
        }

        val obj = kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps).jsonObject
        val url = obj["url"]?.jsonPrimitive?.contentOrNull
        val jwt = obj["jwt"]?.jsonPrimitive?.contentOrNull
        if (url.isNullOrBlank() || jwt.isNullOrBlank()) {
            throw EngineException.Unexpected(tr("réponse du service de jetons incomplète : ${reponse.corps}", "incomplete token service response: ${reponse.corps}"))
        }
        InfoVocal(url = url, jwt = jwt)
    }

    override fun identiteVocale(): String {
        val session = currentSession()
        return "${session.userId}:${session.deviceId}"
    }

    /** a call's widget channel, open while [clesAppelFlow] is collected, see there */
    private class CanalAppel(
        val handle: org.matrix.rustcomponents.sdk.WidgetDriverHandle,
        val widgetId: String,
        val pret: kotlinx.coroutines.CompletableDeferred<Unit>,
        val attente: java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<kotlinx.serialization.json.JsonObject?>>,
    ) {
        suspend fun demander(action: String, data: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject? {
            val id = java.util.UUID.randomUUID().toString()
            val reponse = kotlinx.coroutines.CompletableDeferred<kotlinx.serialization.json.JsonObject?>()
            attente[id] = reponse
            handle.send(
                buildJsonObject {
                    put("api", "fromWidget")
                    put("widgetId", widgetId)
                    put("requestId", id)
                    put("action", action)
                    put("data", data)
                }.toString(),
            )
            return kotlinx.coroutines.withTimeoutOrNull(15_000) { reponse.await() }.also { attente.remove(id) }
        }
    }

    private val canauxAppel = java.util.concurrent.ConcurrentHashMap<String, CanalAppel>()

    override suspend fun envoyerCleAppel(salonId: String, index: Int, cle: ByteArray, pour: Collection<String>) = guard(
        tr("Envoi de la clé d'appel impossible", "Could not send the call key")
    ) {
        if (pour.isEmpty()) return@guard
        // the channel opens with the key collection, which starts in the same breath as the
        // call: the first send may come a moment before it exists
        val canal = kotlinx.coroutines.withTimeoutOrNull(10_000) {
            var c = canauxAppel[salonId]
            while (c == null) {
                kotlinx.coroutines.delay(50)
                c = canauxAppel[salonId]
            }
            c
        } ?: throw EngineException.Unexpected(tr("Aucun canal d'appel ouvert pour $salonId", "No call channel open for $salonId"))
        kotlinx.coroutines.withTimeoutOrNull(20_000) { canal.pret.await() }
            ?: throw EngineException.Unexpected(tr("Canal d'appel pas prêt", "Call channel not ready"))
        val session = currentSession()
        // element call's to-device shape: one key, who sends it, for which call
        val contenu = buildJsonObject {
            putJsonObject("keys") {
                put("index", index)
                put("key", java.util.Base64.getEncoder().encodeToString(cle))
            }
            putJsonObject("member") { put("claimed_device_id", session.deviceId) }
            put("room_id", salonId)
            putJsonObject("session") {
                put("application", "m.call")
                put("call_id", "")
                put("scope", "m.room")
            }
            put("sent_ts", System.currentTimeMillis())
        }
        // a livekit identity is `@person:server:DEVICE`
        val messages = buildJsonObject {
            pour.groupBy({ it.substringBeforeLast(':') }, { it.substringAfterLast(':') }).forEach { (qui, appareils) ->
                putJsonObject(qui) { appareils.forEach { put(it, contenu) } }
            }
        }
        val reponse = canal.demander(
            "send_to_device",
            buildJsonObject {
                put("type", TYPE_CLES_APPEL)
                put("encrypted", true)
                put("messages", messages)
            },
        )
        if (reponse == null || reponse.containsKey("error")) {
            throw EngineException.Unexpected(tr("Envoi to-device refusé : $reponse", "To-device send refused: $reponse"))
        }
    }

    /**
     * the media keys the others send, received through the sdk's widget driver
     *
     * keys travel as encrypted to-device messages, like current element call: room events would
     * sit in the conversation, and show there as undecryptable to whoever joined after. room
     * ones are still read, older element call sends those
     *
     * not through the timeline: it drops custom message events, the keys among them, measured.
     * the widget driver is the channel element x gives element call, and it hands over any event
     * type it is granted, decrypted. velvet plays the widget: it answers the driver's widget api
     * requests (capabilities, incoming events) and asks it for the recent history once, for the
     * keys sent before we arrived
     */
    override fun clesAppelFlow(salonId: String, depuis: Long): Flow<li.velvetchat.core.model.CleAppel> = callbackFlow {
        val salon = requireClient().getRoom(salonId)
            ?: throw EngineException.Unexpected(tr("Salon inconnu : $salonId", "Unknown channel: $salonId"))
        val session = currentSession()
        val widgetId = "velvet-appel-${java.util.UUID.randomUUID()}"
        val paire = org.matrix.rustcomponents.sdk.makeWidgetDriver(
            org.matrix.rustcomponents.sdk.WidgetSettings(widgetId, false, "https://velvet.invalid/appel"),
        )
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val attente = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<kotlinx.serialization.json.JsonObject?>>()
        val pret = kotlinx.coroutines.CompletableDeferred<Unit>()
        val canal = CanalAppel(paire.handle, widgetId, pret, attente)
        canauxAppel[salonId] = canal

        // element call's to-device key: one key object, the sender's device in `member`
        fun traiterAppareil(ev: kotlinx.serialization.json.JsonObject) {
            if (ev["type"]?.jsonPrimitive?.contentOrNull != TYPE_CLES_APPEL) return
            val qui = ev["sender"]?.jsonPrimitive?.contentOrNull ?: return
            val contenu = ev["content"] as? kotlinx.serialization.json.JsonObject ?: return
            if (contenu["room_id"]?.jsonPrimitive?.contentOrNull != salonId) return
            val appareil = (contenu["member"] as? kotlinx.serialization.json.JsonObject)
                ?.get("claimed_device_id")?.jsonPrimitive?.contentOrNull ?: return
            val k = contenu["keys"] as? kotlinx.serialization.json.JsonObject ?: return
            val index = k["index"]?.jsonPrimitive?.intOrNull ?: return
            val cle = k["key"]?.jsonPrimitive?.contentOrNull?.let { decoderBase64(it) } ?: return
            trySend(li.velvetchat.core.model.CleAppel("$qui:$appareil", index, cle))
        }

        fun traiter(ev: kotlinx.serialization.json.JsonObject) {
            if (ev["type"]?.jsonPrimitive?.contentOrNull != TYPE_CLES_APPEL) return
            val ts = ev["origin_server_ts"]?.jsonPrimitive?.longOrNull ?: return
            if (ts < depuis) return
            val qui = ev["sender"]?.jsonPrimitive?.contentOrNull ?: return
            val contenu = ev["content"] as? kotlinx.serialization.json.JsonObject ?: return
            val appareil = contenu["device_id"]?.jsonPrimitive?.contentOrNull ?: return
            if (qui == session.userId && appareil == session.deviceId) return
            (contenu["keys"] as? kotlinx.serialization.json.JsonArray)?.forEach { k ->
                val o = k as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val index = o["index"]?.jsonPrimitive?.intOrNull ?: return@forEach
                val cle = o["key"]?.jsonPrimitive?.contentOrNull?.let { decoderBase64(it) } ?: return@forEach
                trySend(li.velvetchat.core.model.CleAppel("$qui:$appareil", index, cle))
            }
        }

        launch {
            runCatching {
                paire.driver.run(
                    salon,
                    object : org.matrix.rustcomponents.sdk.WidgetCapabilitiesProvider {
                        override fun acquireCapabilities(
                            capabilities: org.matrix.rustcomponents.sdk.WidgetCapabilities,
                        ) = capabilities
                    },
                )
            }.onFailure { erreurNonBloquante("Canal d'appel de $salonId", it) }
        }
        launch {
            while (true) {
                val brut = paire.handle.recv() ?: break
                val message = runCatching { json.parseToJsonElement(brut).jsonObject }.getOrNull() ?: continue
                val requete = message["requestId"]?.jsonPrimitive?.contentOrNull
                if (message["api"]?.jsonPrimitive?.contentOrNull == "fromWidget") {
                    requete?.let { attente[it]?.complete(message["response"] as? kotlinx.serialization.json.JsonObject) }
                    continue
                }
                val data = message["data"] as? kotlinx.serialization.json.JsonObject
                val reponse = when (message["action"]?.jsonPrimitive?.contentOrNull) {
                    "capabilities" -> buildJsonObject {
                        putJsonArray("capabilities") {
                            add(kotlinx.serialization.json.JsonPrimitive("org.matrix.msc2762.receive.event:$TYPE_CLES_APPEL"))
                            add(kotlinx.serialization.json.JsonPrimitive("org.matrix.msc3819.receive.to_device:$TYPE_CLES_APPEL"))
                            add(kotlinx.serialization.json.JsonPrimitive("org.matrix.msc3819.send.to_device:$TYPE_CLES_APPEL"))
                        }
                    }
                    "supported_api_versions" -> buildJsonObject {
                        putJsonArray("supported_versions") {
                            listOf("0.0.1", "0.0.2", "org.matrix.msc2762", "org.matrix.msc2871", "org.matrix.msc2876", "org.matrix.msc3819")
                                .forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        }
                    }
                    "notify_capabilities" -> {
                        pret.complete(Unit)
                        buildJsonObject {}
                    }
                    "send_event" -> {
                        data?.let { traiter(it) }
                        buildJsonObject {}
                    }
                    "send_to_device" -> {
                        data?.let { traiterAppareil(it) }
                        buildJsonObject {}
                    }
                    else -> buildJsonObject {}
                }
                paire.handle.send(
                    kotlinx.serialization.json.JsonObject(message + ("response" to reponse)).toString(),
                )
            }
        }
        // the keys sent before we arrived: whoever was already in the call sent theirs then
        launch {
            kotlinx.coroutines.withTimeoutOrNull(20_000) { pret.await() } ?: return@launch
            val historique = canal.demander(
                "org.matrix.msc2876.read_events",
                buildJsonObject {
                    put("type", TYPE_CLES_APPEL)
                    put("limit", 100)
                },
            )
            (historique?.get("events") as? kotlinx.serialization.json.JsonArray)?.forEach { e ->
                (e as? kotlinx.serialization.json.JsonObject)?.let { traiter(it) }
            }
        }
        awaitClose {
            canauxAppel.remove(salonId, canal)
            runCatching { paire.handle.close() }
            runCatching { paire.driver.close() }
        }
    }

    /** standard or url safe, padded or not: senders differ on this */
    private fun decoderBase64(texte: String): ByteArray? {
        val propre = texte.trim().replace('-', '+').replace('_', '/')
        val complet = propre + "=".repeat((4 - propre.length % 4) % 4)
        return runCatching { java.util.Base64.getDecoder().decode(complet) }.getOrNull()
    }

    override suspend fun sonner(salonId: String) {
        val moi = currentSession().userId
        val contenu = buildJsonObject {
            put("actif", true)
            put("appelant", moi)
            put("ts", System.currentTimeMillis())
        }.toString()
        // a state marker with an empty state key, so one ongoing call per room at a time
        ecrireEtat(salonId, TYPE_APPEL, contenu)
    }

    override suspend fun rejoindreVocal(salonId: String) {
        val moi = currentSession().userId
        val contenu = buildJsonObject {
            put("actif", true)
            put("ts", System.currentTimeMillis())
        }.toString()
        // `state_key` is your own id, so everybody writes only their own line. that is what lets
        // two people join at the same time without one erasing the other
        ecrireEtat(salonId, TYPE_APPEL_MEMBRE, contenu, cle = moi)
        annoncerRtc(salonId)
        armerDeparts(salonId)
    }

    override suspend fun quitterVocal(salonId: String) {
        val moi = currentSession().userId
        // the server side leaves are no longer needed once we leave ourselves
        val differes = departs.remove(salonId)
        differes?.job?.cancel()
        // like the ring: a state event is not deleted, it is marked inactive
        ecrireEtat(salonId, TYPE_APPEL_MEMBRE, """{"actif":false}""", cle = moi)
        // the standard way to leave: the same key, an empty content
        if (rtcAnnonces.remove(salonId) != null) {
            runCatching { ecrireEtat(salonId, TYPE_RTC_MEMBRE, "{}", cle = cleRtc()) }
                .onFailure { erreurNonBloquante("Départ matrixrtc de $salonId", it) }
        }
        differes?.ids?.values?.forEach { id -> runCatching { agirSurDiffere(id, "cancel") } }
    }

    /** a room's pending server side leaves, by state key, and the job pushing them back */
    private class DepartsDifferes(val job: Job, val ids: MutableMap<String, String>)

    private val departs = java.util.concurrent.ConcurrentHashMap<String, DepartsDifferes>()

    /**
     * the leave written in advance, so that a dead app leaves the call within seconds, like the
     * reference, instead of after an expiry measured in minutes or hours
     *
     * msc4140: the server holds the "left" state and sends it by itself once [DEPART_DIFFERE_MS]
     * pass without news. a living app pushes it back every [RELANCE_DEPART_MS]; leaving normally
     * writes the leave at once and cancels these. both markers get one, ours and matrixrtc's
     *
     * a push back that fails means the server dropped the pending event, for instance because
     * the heartbeat rewrote the same state: a fresh one is scheduled on the spot
     */
    private fun armerDeparts(salonId: String) {
        if (departs.containsKey(salonId)) return
        val moi = currentSession().userId
        val departsVoulus = mapOf(
            "$TYPE_APPEL_MEMBRE|$moi" to """{"actif":false}""",
            "$TYPE_RTC_MEMBRE|${cleRtc()}" to "{}",
        )
        val ids = java.util.concurrent.ConcurrentHashMap<String, String>()
        val job = supervisor.launch {
            while (true) {
                departsVoulus.forEach { (cible, contenu) ->
                    val existant = ids[cible]
                    val tenu = existant != null && runCatching { agirSurDiffere(existant, "restart") }.getOrDefault(false)
                    if (!tenu) {
                        val (type, cle) = cible.split('|', limit = 2)
                        programmerDepart(salonId, type, cle, contenu)?.let { ids[cible] = it }
                            ?: ids.remove(cible)
                    }
                }
                kotlinx.coroutines.delay(RELANCE_DEPART_MS)
            }
        }
        departs[salonId] = DepartsDifferes(job, ids)
    }

    /** schedules a state write the server sends by itself later, returns its id or null */
    private suspend fun programmerDepart(salonId: String, type: String, cle: String, contenu: String): String? {
        fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")
        val reponse = runCatching {
            requeteSignee(
                "PUT",
                "/_matrix/client/v3/rooms/${enc(salonId)}/state/${enc(type)}/${enc(cle)}" +
                    "?org.matrix.msc4140.delay=$DEPART_DIFFERE_MS",
                contenu,
                tentatives = 1,
            )
        }.getOrNull() ?: return null
        if (reponse.code != 200) {
            erreurNonBloquante("Départ différé de $type dans $salonId", RuntimeException("${reponse.code} ${reponse.corps}"))
            return null
        }
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps)
                .jsonObject["delay_id"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    /**
     * `restart`, `cancel` or `send` on a scheduled event. the body form first, the one synapse
     * shipped; the path form of the later msc text when the server does not know the first
     */
    private suspend fun agirSurDiffere(id: String, action: String): Boolean {
        val base = "/_matrix/client/unstable/org.matrix.msc4140/delayed_events/" +
            java.net.URLEncoder.encode(id, "UTF-8")
        val r = requeteSignee("POST", base, """{"action":"$action"}""", tentatives = 1)
        if (r.code == 200) return true
        if ("M_UNRECOGNIZED" !in r.corps) return false
        return requeteSignee("POST", "$base/$action", "{}", tentatives = 1).code == 200
    }

    /** when each room last got my standard membership, see [annoncerRtc] */
    private val rtcAnnonces = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** `_@me:server_DEVICE_m.call`, element call's per device key (msc4143) */
    private fun cleRtc(): String {
        val session = currentSession()
        return "_${session.userId}_${session.deviceId}_m.call"
    }

    /**
     * my presence in the call the way matrixrtc writes it, so element call and the sdk see me
     *
     * written beside our own marker, not instead of it: velvet copies already installed only read
     * ours. once per call rather than on every heartbeat, since the event carries its own expiry,
     * and again before that runs out on a long call
     *
     * best effort. a room created before this type was opened to everybody refuses it below
     * moderator, and the call itself must not fail over that
     *
     * an app killed outright does not stay listed: the departure is also scheduled on the server
     * as a delayed event (msc4140, see [programmerDepart]), which goes out by itself when the app
     * stops renewing it
     */
    private suspend fun annoncerRtc(salonId: String) {
        val maintenant = System.currentTimeMillis()
        val derniere = rtcAnnonces[salonId]
        if (derniere != null && maintenant - derniere < RTC_REANNONCE_MS) return
        val session = currentSession()
        val contenu = buildJsonObject {
            put("application", "m.call")
            put("call_id", "")
            put("scope", "m.room")
            put("device_id", session.deviceId)
            put("expires", RTC_EXPIRATION_MS)
            putJsonObject("focus_active") {
                put("type", "livekit")
                put("focus_selection", "oldest_membership")
            }
            putJsonArray("foci_preferred") {
                addJsonObject {
                    put("type", "livekit")
                    put("livekit_service_url", serviceJetonVocal().orEmpty().removeSuffix("/sfu/get"))
                    put("livekit_alias", salonId)
                }
            }
        }.toString()
        val ecrire = suspend { ecrireEtat(salonId, TYPE_RTC_MEMBRE, contenu, cle = cleRtc()) }
        runCatching { ecrire() }
            .recoverCatching { refus ->
                // a room from before this type: whoever may edit its powers opens the call types
                // to everybody, once, then retries. anyone else stays refused, see above
                if ("M_FORBIDDEN" !in (refus.message ?: "") || !autoriserAppels(salonId)) throw refus
                ecrire()
            }
            .onSuccess { rtcAnnonces[salonId] = maintenant }
            .onFailure { erreurNonBloquante("Présence matrixrtc dans $salonId", it) }
    }

    override suspend fun participantsVocal(salonId: String): List<String> {
        val salon = java.net.URLEncoder.encode(salonId, "UTF-8")
        // **this one response is big, and the ordinary timeout was sized for small ones**
        //
        // the cosmetics catalogue is fourteen state events of about twenty three kilobytes, so
        // the room's whole state is the better part of a megabyte. fifteen seconds is generous
        // on a lan and tight on a phone going through the tunnel, and a cut off read is
        // indistinguishable from an empty catalogue — the failure this path keeps producing
        //
        // forty five seconds, because the alternative to waiting is showing nothing
        val reponse = requeteSignee(
            "GET",
            "/_matrix/client/v3/rooms/$salon/state",
            lectureMs = 45_000,
        )
        // 403 means we are not, or no longer, in the room. not a fault, we simply do not know,
        // and the caller draws no badge
        if (reponse.code != 200) return emptyList()

        val etats = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(reponse.corps).jsonArray
        }.getOrNull() ?: return emptyList()

        val maintenant = System.currentTimeMillis()
        return etats.mapNotNull { element ->
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            // the old name counts too: a client not yet updated writes its presence under it,
            // and refusing that would make it invisible in the call
            val typeLu = obj["type"]?.jsonPrimitive?.contentOrNull
            // a matrixrtc membership, element call's or ours: its sender is the person, and it is
            // live while not emptied and not past its own expiry
            if (typeLu == TYPE_RTC_MEMBRE) {
                val contenu = obj["content"] as? kotlinx.serialization.json.JsonObject
                if (contenu.isNullOrEmpty() || contenu["application"]?.jsonPrimitive?.contentOrNull != "m.call") {
                    return@mapNotNull null
                }
                val depuis = contenu["created_ts"]?.jsonPrimitive?.longOrNull
                    ?: obj["origin_server_ts"]?.jsonPrimitive?.longOrNull
                    ?: return@mapNotNull null
                val duree = contenu["expires"]?.jsonPrimitive?.longOrNull ?: RTC_EXPIRATION_MS
                if (maintenant > depuis + duree) return@mapNotNull null
                return@mapNotNull obj["sender"]?.jsonPrimitive?.contentOrNull
            }
            if (typeLu !in li.velvetchat.core.model.Identifiants.toutesLesFormes(TYPE_APPEL_MEMBRE)) {
                return@mapNotNull null
            }
            val qui = obj["state_key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val contenu = obj["content"] as? kotlinx.serialization.json.JsonObject
                ?: return@mapNotNull null
            if (contenu["actif"]?.jsonPrimitive?.booleanOrNull != true) return@mapNotNull null
            // same guard as the ring: an app killed outright leaves its membership at
            // `actif: true` forever. with no expiry the badge would show a ghost call until the
            // next time the app opens
            val ts = contenu["ts"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            if (maintenant - ts > APPEL_VALIDITE_MS) return@mapNotNull null
            qui
        }.distinct()
    }

    /**
     * who is in each voice room, reread when it changes rather than when the clock says so
     *
     * voice room membership is room state: joining or leaving writes an event, which the sync
     * brings us. this used to be a plain interval loop, thirty seconds, then three, and there is
     * no reason to wait out a tick for information that has already arrived
     *
     * `signalSalons()` is that wakeup, and it already tells the servers and the conversations
     * that state moved. the slow heartbeat stays on top, as a net only: a participant can leave
     * by their marker expiring, with no event written at all, and then nothing would wake anyone
     */
    override fun vocauxFlow(salonIds: Collection<String>): Flow<Map<String, List<String>>> {
        val battement = flow {
            while (true) {
                emit(Unit)
                kotlinx.coroutines.delay(VOCAL_RAFRAICHISSEMENT_MS)
            }
        }
        return kotlinx.coroutines.flow.merge(signalSalons(), battement)
            // two wakeups at the same instant, the event and the heartbeat, are worth one read.
            // without this a burst of sync would make a burst of requests
            .debounce(150)
            .map {
                val frein = Semaphore(PRESENCES_SIMULTANEES)
                val lot = coroutineScope {
                    salonIds.map { id ->
                        async {
                            id to runCatching { frein.withPermit { participantsVocal(id) } }
                                .getOrDefault(emptyList())
                        }
                    }.awaitAll()
                }
                // empty rooms are dropped rather than returned as zero. the screen draws a badge
                // when the key exists, and it has no business telling "nobody" from "not read"
                lot.toMap().filterValues { it.isNotEmpty() }
            }
            .distinctUntilChanged()
    }

    override fun appelsEnCoursFlow(
        salonIds: Collection<String>,
    ): Flow<Map<String, li.velvetchat.core.model.AppelEnCours>> = flow {
        while (true) {
            // same guard as in `presencesFlow`, since `runCatching` would swallow the
            // cancellation
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            // no semaphore here, unlike `vocauxFlow`. that one asks the server about every
            // person, this one reads already cached state, so the brake would guard against a
            // load that does not exist
            val lot = salonIds.mapNotNull { id ->
                runCatching { appelEnCours(id) }.getOrNull()?.let { id to it }
            }
            emit(lot.toMap())
            kotlinx.coroutines.delay(APPEL_RAFRAICHISSEMENT_MS)
        }
    }

    override suspend fun autoriserAppels(salonId: String): Boolean {
        val actuel = runCatching { lireEtat(salonId, "m.room.power_levels") }.getOrNull()
            ?: return false
        val evenements = (actuel["events"] as? kotlinx.serialization.json.JsonObject)
            ?: kotlinx.serialization.json.JsonObject(emptyMap())

        // already in place, so no rewrite. a state event rewritten identically is still an
        // event, and it would show up in every room's timeline on every pass
        if (POUVOIRS_APPEL.all { (type, niveau) ->
                evenements[type]?.jsonPrimitive?.intOrNull == niveau
            }
        ) return false

        // we start from the existing content and replace `events` only. matrix state is replaced
        // whole and not merged, so writing a partial content back would wipe the per member
        // levels, which means the room's admins
        val fusionne = kotlinx.serialization.json.buildJsonObject {
            actuel.forEach { (cle, valeur) -> if (cle != "events") put(cle, valeur) }
            putJsonObject("events") {
                evenements.forEach { (cle, valeur) -> put(cle, valeur) }
                POUVOIRS_APPEL.forEach { (type, niveau) -> put(type, niveau) }
            }
        }.toString()

        ecrireEtat(salonId, "m.room.power_levels", fusionne)
        return true
    }

    override suspend fun raccrocherSonnerie(salonId: String) {
        // you cannot delete a state event, so we mark it inactive
        ecrireEtat(salonId, TYPE_APPEL, """{"actif":false}""")
    }

    override suspend fun appelEnCours(salonId: String): li.velvetchat.core.model.AppelEnCours? {
        val etat = lireEtat(salonId, TYPE_APPEL) ?: return null
        if (etat["actif"]?.jsonPrimitive?.booleanOrNull != true) return null
        val ts = etat["ts"]?.jsonPrimitive?.longOrNull ?: return null
        // an expired marker means a call abandoned without hanging up properly, so we ignore it
        if (System.currentTimeMillis() - ts > APPEL_VALIDITE_MS) return null
        val appelant = etat["appelant"]?.jsonPrimitive?.contentOrNull ?: return null
        return li.velvetchat.core.model.AppelEnCours(
            salonId = salonId,
            appelant = appelant,
            horodatage = ts,
        )
    }

    private companion object {
        /** presented to MAS when the app registers itself. https, and its host reversed is the android scheme */
        const val CLIENT_URI = "https://velvet-chat.li/"

        /** lets the app read MAS's own api (graphql), granted to every account */
        const val PORTEE_GRAPHQL = "urn:mas:graphql:*"
        const val PORTEE_ADMIN_SYNAPSE = "urn:synapse:admin:*"
        const val PORTEE_ADMIN_MAS = "urn:mas:admin"

        /**
         * how often the call markers get reread. tighter than the voice one, because this is the
         * delay before a phone starts ringing, and two seconds late on a ring gets noticed
         */
        const val APPEL_RAFRAICHISSEMENT_MS = 2_000L

        /** lan probe, kept short so it does not hold up startup when we are not at home */
        const val SONDE_LAN_MS = 800
        const val SYNC_TIMEOUT_MS = 45_000L

        /** the first batch of events, which comes from the local cache, so no network */
        const val FIRST_UPDATE_TIMEOUT_MS = 10_000L

        /**
         * gives the pagination diffs time to arrive. crude and owned as such, it is the price of a
         * snapshot api, and it goes away when the last caller moves to the reactive flow
         */
        const val SETTLE_MS = 800L
        const val ROOM_PAGE_SIZE = 100u
        /** a room's initial page, enough to fill a screen without waiting for the network */
        const val INITIAL_PAGE: UShort = 30u
        const val SERVERS_DEBOUNCE_MS = 400L
        /** the first delay before a restart, doubling afterwards up to [RESTART_MAX_MS] */
        const val RESTART_BASE_MS = 1_000L
        /** the ceiling. past it, retrying less often would change nothing anyone can feel */
        const val RESTART_MAX_MS = 60_000L
        /**
         * how deep the hierarchy gets walked when joining a server
         *
         * three levels covers server, category, channel with room to spare. the limit is not there
         * for that though: it bounds the cost when an unknown server declares a deep or circular
         * tree
         */
        const val MAX_SPACE_DEPTH = 3
        /** batch size for reading members, plenty for a room this size */
        const val MEMBER_CHUNK = 100u
        /** how long the client gets to learn about a room it just created */
        const val REMOTE_ECHO_TIMEOUT_MS = 30_000L

        /**
         * what we grant the sync before carrying on without it
         *
         * measured at a few milliseconds when it keeps up (`sondeCreation`). a second is therefore
         * generous, and past that it is not a delay any more but a lateness, which belongs in a
         * background task rather than behind an hourglass
         */
        const val ECHO_COURT_MS = 1_000L

        /** what the background task is allowed to wait, since nobody is watching it */
        const val ECHO_PATIENT_MS = 120_000L

        /**
         * how long a presence reading stays good
         *
         * a minute, which is the scale the thing changes on, and what takes "open a ten person
         * room" from ten requests per recomposition down to ten per minute at worst
         */
        const val PRESENCE_VALIDITE_MS = 60_000L

        /**
         * [presencesFlow]'s cadence
         *
         * strictly greater than [PRESENCE_VALIDITE_MS], and that is the whole point of declaring
         * it here: polling at exactly 60 s would land on the still cached value every other time,
         * so twice the requests for the same information. the rule lives next to the cache it has
         * to outlast, rather than in the screen doing the collecting
         */
        const val PRESENCE_RAFRAICHISSEMENT_MS = 90_000L

        /**
         * how long `/sync` holds the line waiting for a presence change
         *
         * thirty seconds. long enough that an open connection really does replace the polling,
         * short enough that a mobile network dying quietly recovers on the next round instead of
         * being waited on forever
         */
        const val PRESENCE_ATTENTE_MS = 30_000

        /**
         * how many presence reads go out at once
         *
         * eight. enough at this scale sized list to leave in one go, few enough not to look like a
         * flood from synapse's side. there is nothing sacred about the number, only about there
         * being one. see `presences`
         */
        const val PRESENCES_SIMULTANEES = 8

        /**
         * how long a shared flow outlives its last subscriber
         *
         * five seconds, which is how long a screen rotation takes to unsubscribe and come back. at
         * zero we would restart the sdk listener on every configuration change
         */
        const val PARTAGE_GRACE_MS = 5_000L

        /** the pagination page size during a search, see `chercherDansSalon` */
        const val PAGE_RECHERCHE: UShort = 100u

        // the ceiling on `importerEmotesUtilises`'s sweep. past it we stop even if there is
        // history left. five thousand events covers a private dm several times over, and a real
        // need beyond that gets solved by a sweep resumed page by page
        const val MAX_SCAN_EMOTES = 5000

        /** past this we return what we have rather than keep anyone waiting forever */
        const val RECHERCHE_TIMEOUT_MS = 5_000L

        /**
         * how long in total a request will wait out a rate limit
         *
         * thirty seconds. past that it is not a delay any more, it is a hang. better to hand the
         * 429 back to the caller, who can put it on screen, than to leave a gesture suspended for
         * a minute
         */
        const val ATTENTE_MAX_MS = 30_000L

        /**
         * how long the hierarchy cache lives (`channelsOf`)
         *
         * eight seconds. long enough that a burst of messages does not start one pagination per
         * message, short enough that a room created from another device does not look lost.
         * changes made here empty the cache immediately anyway
         */
        const val STRUCTURE_TTL_MS = 8_000L

        /** a profile changes a few times a year, so a minute is already generous */
        const val PROFIL_TTL_MS = 60_000L

        /**
         * the state cache only lives as long as it takes to open a picker. enough to cover the
         * three families read in a row, too short to show a stale catalogue to somebody who just
         * published one
         */
        const val ETAT_TTL_MS = 10_000L

        /**
         * how many timelines we keep open, see `elaguerTimelines`
         *
         * six. enough to cover going back and forth between the rooms anyone actually follows
         * without ever rereading, while bounding what a long session can pile up
         */
        const val TIMELINES_MAX = 6

        /** the uia stage that consumes the invite token, the only one carrying a parameter */
        const val ETAPE_JETON = "m.login.registration_token"

        /**
         * the registration stages velvet can get through on its own
         *
         * `m.login.dummy` is not filler, it is the stage matrix ends a flow with when there is
         * nothing left to ask, and synapse wants it after the token. any other stage, captcha,
         * email, terms of service, needs a ui we do not have, and saying so beats failing halfway
         */
        val ETAPES_INSCRIPTION = setOf(ETAPE_JETON, "m.login.dummy")

        val LOCK = Any()

        /** the sdk's `initPlatform` can only be called once per process */
        @Volatile
        var platformReady = false
    }
}

/** the emoji extension matrix clients share */
private const val TYPE_EMOTES = "im.ponies.room_emotes"

/**
 * private notes, under a project specific type
 *
 * account data: private by construction, never published, synced between your own devices. no
 * shared convention describes what discord calls a note, so inventing one under our own prefix is
 * the honest option, unlike borrowing an `m.` that would promise interoperability nobody has
 */
private const val TYPE_NOTES = "li.velvetchat.notes"

/**
 * the `invite token -> space` crib sheet
 *
 * house prefix, deliberately: nothing here is meant to be read by another client, and borrowing
 * `m.` would suggest interoperability that does not exist. same call as `TYPE_ROLES` and
 * `TYPE_NOTES`
 *
 * in account data, so private to the admin and synced between their devices: a code generated on
 * the phone turns up on the tablet. losing it loses nothing essential, the token stays valid and
 * only the convenience of rebuilding the code goes away
 */
private const val TYPE_INVITATIONS = "li.velvetchat.invitations"

/** the order of servers in the rail. account data, so shared between devices */
private const val TYPE_ORDRE_SERVEURS = "li.velvetchat.ordre_serveurs"

/**
 * the friendship relation, as state on the conversation between the two people
 *
 * shared, so symmetric by construction. two separate pieces of account data would diverge for good
 * at the first lost notification, with nothing to reconcile them. and room state is not encrypted,
 * only messages are, so it stays readable inside an encrypted dm, which is exactly what makes this
 * workable
 *
 * house prefix: nothing here is meant to be read by another client, and borrowing `m.` would
 * suggest interoperability that does not exist
 */
private const val TYPE_AMI = "li.velvetchat.friend_state"
private const val ETAT_AMIS = "friends"
private const val ETAT_AUCUN = "none"

/**
 * pinned messages, a protocol type and not a house invention
 *
 * which is the point: a message pinned from velvet is pinned for element and fluffychat too, and
 * the other way round. same call as `m.favourite` for direct conversations, borrow what the
 * protocol already has rather than invent a marker that would only exist here
 */
private const val TYPE_EPINGLES = "m.room.pinned_events"

/** the line separator of a matrix message body, see `sansRepli` */
private const val SAUT_DE_LIGNE = "\n"

/**
 * the table of role tiers
 *
 * under a project specific type rather than an `im.*` one. no shared extension describes this
 * mapping, and borrowing the prefix of a spec we do not implement would suggest interoperability
 * that does not exist
 */
private const val TYPE_ROLES = "li.velvetchat.roles"

/**
 * the decorative roles and who has them
 *
 * a separate type from `TYPE_ROLES` rather than one more field inside it. the two tables share
 * neither their write rate nor their audience: tiers change once a year and decide permissions,
 * decorations change weekly and decide nothing. mixing them would mean handing someone a colour
 * rewrites the table that grants power, and that a concurrent write could lose a tier on the way
 */
private const val TYPE_DECORATIONS = "li.velvetchat.decorations"

/**
 * friend nicknames, in account data
 *
 * not in the room, and that is the point: a nickname is what i wrote down about someone. written
 * into the room it would be readable by that someone, which no client does, for reasons that need
 * no explaining
 */
private const val TYPE_SOURDINES = "li.velvetchat.sourdines"
private const val TYPE_SURNOMS = "li.velvetchat.surnoms"

/**
 * a server's tag, in the space's state
 *
 * what each person wears does not live here but in their profile, with the emoji and the text
 * copied across, see `ProfilEtendu.tagEmoji`. the duplication is deliberate: the profile shows the
 * tag even to someone who is not a member of the server, and an id to resolve would only render
 * for members
 */
private const val TYPE_TAG = "li.velvetchat.server_tag"
private const val TYPE_PROFIL_SERVEUR = "li.velvetchat.profil_serveur"

/**
 * the server's inventory of avatar decorations
 *
 * a separate type from [TYPE_DECORATIONS], close names notwithstanding. that one carries the
 * decorative roles, the coloured badge next to a nickname, and who wears them. this one is a
 * catalogue of images that nobody wears here, since each person's pick lives in their profile
 * (`ProfilEtendu.decoration`) and not in the catalogue. two tables sharing neither content nor
 * life cycle have no business in the same state event
 */
private const val TYPE_DECORATIONS_AVATAR = "li.velvetchat.avatar_decorations"

/**
 * the state type carrying one family's catalogue
 *
 * the decorations keep their old name. renaming would have made already published catalogues
 * invisible, which is the kind of silent migration nobody wants out of a cosmetic refactor
 */
private fun typeCosmetique(famille: String): String = when (famille) {
    li.velvetchat.core.model.FAMILLE_DECORATIONS -> TYPE_DECORATIONS_AVATAR
    else -> "li.velvetchat.cosmetics.$famille"
}
private const val TYPE_SONS_SOUNDBOARD = "li.velvetchat.soundboard"

/**
 * the protocol's own favourite tag
 *
 * borrowed rather than invented: a conversation pinned here is pinned in element and the rest too.
 * a project specific marker would have been easier to write and would have meant nothing anywhere
 * else
 */
private const val TAG_FAVORI = "m.favourite"

private const val TYPE_APPEL = "li.velvetchat.call"

/**
 * one presence in the call per person, the `state_key` being the id of whoever is talking
 *
 * a single shared event would have been easier to read and wrong: two people joining at the same
 * time would write the same state key and the second would overwrite the first, since matrix state
 * replaces the whole content rather than merging it. one key per person removes the race instead
 * of trying to win it, everybody writes only their own
 */
private const val TYPE_APPEL_MEMBRE = "li.velvetchat.call.member"

/**
 * what has to be declared so a call is not admin only
 *
 * the two markers above are state events, and matrix defaults `state_default` to 50. on an older
 * server that is exactly what it was, with no exceptions declared and everybody at 0 except the
 * owner, so nobody but him could start or join a call in a server channel. the bug hid because the
 * testing was done on the owner's account, and because a dm, created as `trusted_private_chat`
 * which puts both at 100, never had the problem
 *
 * setting these two types to 0 is the minimum. it opens nothing else, `state_default` stays at 50
 * for the rest of the room's state
 */
/**
 * the state types a call must be able to write, under all their names
 *
 * the old ones are there too: a room created now must stay usable by a client that does not
 * have the new name yet, otherwise it joins the call and the server refuses its presence
 */
private val POUVOIRS_APPEL: Map<String, Int> =
    (listOf(TYPE_APPEL, TYPE_APPEL_MEMBRE)
        .flatMap { li.velvetchat.core.model.Identifiants.toutesLesFormes(it) } + TYPE_RTC_MEMBRE)
        .associateWith { 0 }

/**
 * the standard call membership (msc3401 / msc4143), what element call and the sdk read
 *
 * a protocol identifier, not ours: it never follows a rename, see `Identifiants`
 */
private const val TYPE_RTC_MEMBRE = "org.matrix.msc3401.call.member"

/** element call's media keys, sent as encrypted room events (its "room" key transport) */
private const val TYPE_CLES_APPEL = "io.element.call.encryption_keys"

/** a dead app is out of the call after this, see `armerDeparts` */
private const val DEPART_DIFFERE_MS = 10_000L

/** how often a living app pushes its pending leave back */
private const val RELANCE_DEPART_MS = 3_000L

/** how long a matrixrtc membership claims to last, element call's four hours */
private const val RTC_EXPIRATION_MS = 4 * 60 * 60 * 1000L

/** a long call announces itself again an hour before its membership would lapse */
private const val RTC_REANNONCE_MS = 3 * 60 * 60 * 1000L

/**
 * a voice room's type, the protocol's own `m.call`
 *
 * and not one of ours. a voice room created by velvet has to stay a call room for element and the
 * rest, otherwise people end up with rooms only velvet can read. the reading heuristic
 * ([toChannelKind]) already recognised it
 */
private const val TYPE_SALON_VOCAL = "m.call"

/**
 * past this a call marker counts as stale. two minutes: enough to cover a ring nobody has picked
 * up yet, short enough not to offer joining a call an abruptly closed app left marked as ongoing.
 * refreshed by `sonner`
 */
private const val APPEL_VALIDITE_MS = 120_000L

/**
 * [RustMatrixEngine.vocauxFlow]'s heartbeat, every three seconds and not every thirty
 *
 * at thirty, somebody walking into a voice room only appeared half a minute later, which is the
 * kind of latency that makes people think the feature is broken. the cost is one state read per
 * voice room, so on a small server with two or three of them, about a request a second, which
 * synapse's rate limiting absorbs without blinking since it applies to writes
 *
 * well under [APPEL_VALIDITE_MS], so we never show a call the expiry has just taken away
 */
private const val VOCAL_RAFRAICHISSEMENT_MS = 3_000L

/**
 * why the login was refused, in one sentence you can read under the form
 *
 * every failure used to say "connection refused", wrong password and unreachable server alike. the
 * case that cost the most was neither: a local store left behind by a previous account, which the
 * sdk refuses to reopen. we were hunting a password while the problem was a file
 */
private fun raisonDuRefus(t: Throwable): String {
    val texte = generateSequence(t) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" | ")
        .ifBlank { t::class.simpleName.orEmpty() }
    val bas = texte.lowercase()
    return when {
        "m_forbidden" in bas || "invalid password" in bas || "invalid username" in bas ->
            tr("Identifiant ou mot de passe refusé", "Username or password refused")
        "m_user_deactivated" in bas -> tr("Ce compte est désactivé", "This account is deactivated")
        "m_limit_exceeded" in bas || "too many" in bas ->
            tr("Trop de tentatives d'affilée : réessayer dans une minute", "Too many attempts in a row: try again in a minute")
        "store" in bas || "sqlite" in bas || "database" in bas || "locked" in bas ->
            tr("Les données locales appartiennent à une autre session : ", "The local data belongs to another session: ") +
                tr("fermer Velvet, le rouvrir, puis réessayer", "close Velvet, open it again, then retry")
        "timed out" in bas || "timeout" in bas || "dns" in bas || "connect" in bas ||
            "unreachable" in bas || "certificate" in bas ->
            tr("Serveur injoignable : vérifier l'adresse et la connexion", "Server unreachable: check the address and the connection")
        else -> tr("Connexion refusée : $texte", "Sign-in refused: $texte")
    }
}

/**
 * a server's shape on disk, see `lireCacheServeurs`
 *
 * a mirror of the model rather than the model annotated. `Server` and `Channel` belong to the
 * public contract, and making them serialisable would turn their json shape into a promise nobody
 * meant to keep. here the format is an implementation detail, free to change, and an unreadable
 * file is simply ignored
 */
@kotlinx.serialization.Serializable
private data class ServeurEnCache(
    val id: String,
    val nom: String,
    val avatar: String? = null,
    val salons: List<SalonEnCache> = emptyList(),
    val categories: List<CategorieEnCache> = emptyList(),
) {
    fun versModele(): Server = Server(
        id = id,
        name = nom,
        avatarUrl = avatar,
        channels = salons.map { it.versModele() },
        categories = categories.map {
            li.velvetchat.core.model.Categorie(it.id, it.nom)
        },
    )

    companion object {
        fun de(s: Server) = ServeurEnCache(
            id = s.id,
            nom = s.name,
            avatar = s.avatarUrl,
            salons = s.channels.map { SalonEnCache.de(it) },
            categories = s.categories.map { CategorieEnCache(it.id, it.nom) },
        )
    }
}

@kotlinx.serialization.Serializable
private data class SalonEnCache(
    val id: String,
    val nom: String,
    val sujet: String? = null,
    val vocal: Boolean = false,
    val notifications: Int = 0,
    val mentions: Int = 0,
    val chiffre: Boolean = true,
    val categorie: String? = null,
) {
    fun versModele(): Channel = Channel(
        id = id,
        name = nom,
        topic = sujet,
        kind = if (vocal) ChannelKind.VOICE else ChannelKind.TEXT,
        unread = Unread(notifications = notifications, highlights = mentions),
        encryption = if (chiffre) EncryptionState.ENCRYPTED else EncryptionState.NOT_ENCRYPTED,
        categorie = categorie,
    )

    companion object {
        fun de(c: Channel) = SalonEnCache(
            id = c.id,
            nom = c.name,
            sujet = c.topic,
            vocal = c.kind == ChannelKind.VOICE,
            notifications = c.unread.notifications,
            mentions = c.unread.highlights,
            chiffre = c.encryption != EncryptionState.NOT_ENCRYPTED,
            categorie = c.categorie,
        )
    }
}

@kotlinx.serialization.Serializable
private data class CategorieEnCache(val id: String, val nom: String)

/** a direct conversation's shape on disk, see `lireCacheDirects` */
@kotlinx.serialization.Serializable
private data class DirectEnCache(
    val salon: String,
    val personne: String,
    val nom: String,
    val nomReel: String,
    val avatar: String? = null,
    val apercu: String? = null,
    val quand: Long? = null,
    val notifications: Int = 0,
    val mentions: Int = 0,
    val epingle: Boolean = false,
) {
    fun versModele() = DirectMessage(
        roomId = salon,
        userId = personne,
        name = nom,
        nomReel = nomReel,
        avatarUrl = avatar,
        preview = apercu,
        timestamp = quand,
        unread = Unread(notifications = notifications, highlights = mentions),
        pinned = epingle,
        // never "online" from memory. a day old presence is a lie you cannot catch at a glance.
        // null says i do not know, and the presence flow settles it within the second
        online = null,
    )

    companion object {
        fun de(d: DirectMessage) = DirectEnCache(
            salon = d.roomId,
            personne = d.userId,
            nom = d.name,
            nomReel = d.nomReel,
            avatar = d.avatarUrl,
            apercu = d.preview,
            quand = d.timestamp,
            notifications = d.unread.notifications,
            mentions = d.unread.highlights,
            epingle = d.pinned,
        )
    }
}

/**
 * a cosmetic's shape on disk, see `toutesLesDecorations`
 *
 * a mirror rather than an annotation on [li.velvetchat.core.model.DecorationAvatar]. the model
 * is shared by every platform and has no business carrying the format of a cache that only exists
 * on the ones with a disk. same call as `ServeurEnCache`
 */
@kotlinx.serialization.Serializable
private data class DecorEnCache(
    val id: String,
    val nom: String,
    val mxc: String,
    val anime: Boolean = false,
    val pack: String? = null,
    val theme: String? = null,
    val bas: String? = null,
    val apercu: String? = null,
    val calques: List<CalqueEnCache> = emptyList(),
    val debordHaut: Int = 126,
    val debordBas: Int = 116,
    val debordCote: Int = 56,
) {
    fun versModele() = li.velvetchat.core.model.DecorationAvatar(
        id = id,
        nom = nom,
        mxc = mxc,
        anime = anime,
        pack = pack,
        theme = theme,
        bas = bas,
        apercu = apercu,
        calques = calques.map { it.versModele() },
        debordHaut = debordHaut,
        debordBas = debordBas,
        debordCote = debordCote,
    )

    companion object {
        fun de(d: li.velvetchat.core.model.DecorationAvatar) = DecorEnCache(
            id = d.id,
            nom = d.nom,
            mxc = d.mxc,
            anime = d.anime,
            pack = d.pack,
            theme = d.theme,
            bas = d.bas,
            apercu = d.apercu,
            calques = d.calques.map { CalqueEnCache.de(it) },
            debordHaut = d.debordHaut,
            debordBas = d.debordBas,
            debordCote = d.debordCote,
        )
    }
}

@kotlinx.serialization.Serializable
private data class CalqueEnCache(
    val mxc: String,
    val derriere: Boolean,
    val ancre: String,
    val largeur: Int,
    val hauteur: Int,
    val type: String? = null,
) {
    fun versModele() = li.velvetchat.core.model.CalqueCadre(mxc, derriere, ancre, largeur, hauteur, type)

    companion object {
        fun de(c: li.velvetchat.core.model.CalqueCadre) =
            CalqueEnCache(c.mxc, c.derriere, c.ancre, c.largeur, c.hauteur, c.type)
    }
}

/**
 * an extended profile's shape on disk, see `profilEtendu`
 *
 * a mirror rather than an annotation on the shared model, like `ServeurEnCache` and
 * `DecorEnCache`. the model crosses every platform, a cache format only concerns the ones with a
 * disk
 */
@kotlinx.serialization.Serializable
private data class ProfilEtenduEnCache(
    val pronoms: String? = null,
    val banniere: String? = null,
    val couleurHaute: String? = null,
    val couleurBasse: String? = null,
    val decoration: String? = null,
    val bio: String? = null,
    val tagEmoji: String? = null,
    val tagTexte: String? = null,
    val nomCouleurA: String? = null,
    val nomCouleurB: String? = null,
    val effet: String? = null,
    val cadre: String? = null,
    val plaque: String? = null,
) {
    fun versModele() = li.velvetchat.core.model.ProfilEtendu(
        pronoms = pronoms,
        banniere = banniere,
        couleurHaute = couleurHaute,
        couleurBasse = couleurBasse,
        decoration = decoration,
        bio = bio,
        tagEmoji = tagEmoji,
        tagTexte = tagTexte,
        nomCouleurA = nomCouleurA,
        nomCouleurB = nomCouleurB,
        effet = effet,
        cadre = cadre,
        plaque = plaque,
    )

    companion object {
        fun de(p: li.velvetchat.core.model.ProfilEtendu) = ProfilEtenduEnCache(
            pronoms = p.pronoms,
            banniere = p.banniere,
            couleurHaute = p.couleurHaute,
            couleurBasse = p.couleurBasse,
            decoration = p.decoration,
            bio = p.bio,
            tagEmoji = p.tagEmoji,
            tagTexte = p.tagTexte,
            nomCouleurA = p.nomCouleurA,
            nomCouleurB = p.nomCouleurB,
            effet = p.effet,
            cadre = p.cadre,
            plaque = p.plaque,
        )
    }
}

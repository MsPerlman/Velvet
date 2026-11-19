package li.velvetchat.banc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind
import org.matrix.rustcomponents.sdk.RoomListEntriesListener
import org.matrix.rustcomponents.sdk.RoomListEntriesUpdate
import org.matrix.rustcomponents.sdk.RoomListServiceState
import org.matrix.rustcomponents.sdk.RoomListServiceStateListener
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import org.matrix.rustcomponents.sdk.sdkGitSha
import java.io.File

class S01Spike(private val context: Context) {

    private val store = SessionStore(context)

    suspend fun run(emit: (String) -> Unit) {
        val log: (String) -> Unit = { line ->
            Log.i(TAG, line)
            emit(line)
        }

        log("⓪ Chargement de la lib native…")
        log("   ✓ matrix-rust-sdk chargé — git sha ${sdkGitSha()}")
        log("   ABI de l'appareil : ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        log("")

        if (!platformInitialized) {
            log("⓪bis Initialisation de la plateforme (tokio + vérificateur TLS)…")
            try {
                initPlatform(
                    config = TracingConfiguration(
                        logLevel = LogLevel.INFO,
                        traceLogPacks = emptyList(),
                        extraTargets = emptyList(),
                        writeToStdoutOrSystem = true,
                        writeToFiles = null,
                        sentryConfig = null,
                    ),
                    useLightweightTokioRuntime = false,
                )
                log("   ✓ plateforme initialisée")
            } catch (t: Throwable) {
                if (t.message?.contains("already been set") != true) throw t
                log("   ✓ plateforme déjà initialisée par ailleurs (moteur ou push)")
            }
            platformInitialized = true
            log("")
        }

        if (BuildConfig.HOMESERVER.isBlank() || BuildConfig.USERNAME.isBlank()) {
            log("✗ local.properties incomplet — arrêt avant le login.")
            log("")
            log("  La lib native fonctionne (étape ⓪ ci-dessus), c'est déjà la moitié")
            log("  la plus risquée de S0.1. Pour la suite :")
            log("")
            log("  1. copier local.properties.example en local.properties")
            log("  2. renseigner velvet.homeserver / username / password")
            log("  3. ./gradlew :banc-android:installDebug")
            log("")
            log("  Les identifiants restent sur la machine (fichier gitignoré).")
            return
        }

        log("① Construction du client…")
        val dataDir = File(context.filesDir, "matriscord/session").apply { mkdirs() }
        val cacheDir = File(context.cacheDir, "matriscord/session").apply { mkdirs() }

        val client: Client = ClientBuilder()
            .homeserverUrl(BuildConfig.HOMESERVER)
            .sessionPaths(dataDir.absolutePath, cacheDir.absolutePath)
            .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
            .userAgent("Matriscord-S01/0.1")
            .let { b ->
                if (BuildConfig.INSECURE_TLS) {
                    log("   ⚠ TLS NON VÉRIFIÉ — contournement émulateur, jamais en production")
                    return@let b.disableSslVerification()
                }
                val ancres = li.velvetchat.core.tls.AndroidTrustAnchors.load()
                if (ancres.isEmpty()) {
                    log("   ⚠ aucune ancre système lisible — vérificateur de la plateforme")
                    b
                } else {
                    log("   ✓ ${ancres.size} ancres du magasin système")
                    b.disableBuiltInRootCertificates().addRootCertificates(ancres)
                }
            }
            .build()

        log("   ✓ client construit — lib native chargée")

        val saved = store.load()
        if (saved != null) {
            log("② Session existante trouvée → restauration")
            client.restoreSession(saved)
            log("   ✓ session restaurée SANS ressaisie d'identifiants")
        } else {
            log("② Aucune session enregistrée → login")
            client.login(
                username = BuildConfig.USERNAME,
                password = BuildConfig.PASSWORD,
                initialDeviceName = "Matriscord S0.1",
                deviceId = null,
            )
            store.save(client.session())
            log("   ✓ login réussi, session enregistrée")
            log("   → RELANCE l'app : elle doit passer par « restauration ».")
            log("     C'est la seconde moitié du critère de sortie de S0.1.")
        }

        val session = client.session()
        log("")
        log("   userId    : ${session.userId}")
        log("   deviceId  : ${session.deviceId}")
        log("   serveur   : ${session.homeserverUrl}")

        log("")
        when (session.slidingSyncVersion) {
            SlidingSyncVersion.NATIVE ->
                log("   ★ S0.3 VALIDÉ — sliding sync natif négocié avec le serveur")
            SlidingSyncVersion.NONE ->
                log("   ✗ S0.3 ÉCHOUÉ — le serveur n'annonce pas le sliding sync natif. " +
                    "Retour à la tâche -1.1 (version de Synapse) avant toute conclusion.")
        }

        log("")
        log("③ Démarrage du sync…")
        val syncService = client.syncService().finish()
        syncService.start()

        val roomListService = syncService.roomListService()
        val firstRunning = CompletableDeferred<Unit>()
        val stateHandle = roomListService.state(object : RoomListServiceStateListener {
            override fun onUpdate(state: RoomListServiceState) {
                Log.i(TAG, "   état room list : $state")
                if (state == RoomListServiceState.RUNNING) firstRunning.complete(Unit)
            }
        })

        val rooms = mutableListOf<Room>()
        val roomList = roomListService.allRooms()
        val entries = roomList.entriesWithDynamicAdapters(
            pageSize = PAGE_SIZE,
            listener = object : RoomListEntriesListener {
                override fun onUpdate(roomEntriesUpdate: List<RoomListEntriesUpdate>) {
                    synchronized(rooms) { roomEntriesUpdate.forEach(::apply) }
                }

                private fun apply(update: RoomListEntriesUpdate) = when (update) {
                    is RoomListEntriesUpdate.Append -> rooms.addAll(update.values)
                    is RoomListEntriesUpdate.PushBack -> rooms.add(update.value)
                    is RoomListEntriesUpdate.PushFront -> rooms.add(0, update.value)
                    is RoomListEntriesUpdate.Insert ->
                        rooms.add(update.index.toInt(), update.value)
                    is RoomListEntriesUpdate.Set ->
                        rooms[update.index.toInt()] = update.value
                    is RoomListEntriesUpdate.Remove ->
                        rooms.removeAt(update.index.toInt()).let { }
                    is RoomListEntriesUpdate.Reset -> {
                        rooms.clear(); rooms.addAll(update.values)
                    }
                    is RoomListEntriesUpdate.Truncate ->
                        while (rooms.size > update.length.toInt()) rooms.removeAt(rooms.size - 1)
                    RoomListEntriesUpdate.PopBack ->
                        rooms.removeLastOrNull().let { }
                    RoomListEntriesUpdate.PopFront ->
                        rooms.removeFirstOrNull().let { }
                    RoomListEntriesUpdate.Clear -> rooms.clear()
                }
            },
        )
        entries.controller().setFilter(RoomListEntriesDynamicFilterKind.NonLeft)

        try {
            withTimeout(SYNC_TIMEOUT_MS) { firstRunning.await() }
            log("   ✓ room list RUNNING")
        } catch (_: TimeoutCancellationException) {
            log("   ⚠ toujours pas RUNNING après ${SYNC_TIMEOUT_MS / 1000}s — " +
                "on affiche ce qui est arrivé jusqu'ici")
        }

        val snapshot = synchronized(rooms) { rooms.toList() }
        log("")
        log("④ ${snapshot.size} room(s) :")
        snapshot.forEachIndexed { i, room ->
            val kind = if (room.isSpace()) "space" else "salon"
            val name = room.displayName() ?: room.canonicalAlias() ?: "(sans nom)"
            log("   ${"%2d".format(i + 1)}. [$kind] $name — ${room.id()}")
        }

        log("")
        if (snapshot.isNotEmpty()) {
            log("✓ S0.1 : critère de sortie atteint côté liste de rooms.")
            log("  Reste à vérifier la persistance : relance l'app.")
        } else {
            log("⚠ Zéro room. Attendu si le compte de test n'a rejoint aucun salon —")
            log("  vérifie la tâche -1.3 (space de test) avant de conclure à un échec.")
        }

        stateHandle.cancel()
        syncService.stop()
    }

    private companion object {
        const val TAG = "MatriscordS01"
        const val PAGE_SIZE = 100u
        const val SYNC_TIMEOUT_MS = 45_000L

        @Volatile
        var platformInitialized = false
    }
}

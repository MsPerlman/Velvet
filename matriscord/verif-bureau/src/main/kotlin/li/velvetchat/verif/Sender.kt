package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.RoomListEntriesDynamicFilterKind
import org.matrix.rustcomponents.sdk.RoomListEntriesListener
import org.matrix.rustcomponents.sdk.RoomListEntriesUpdate
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown
import java.io.File
import java.util.Properties

fun main(args: Array<String>) = runBlocking {
    val creds = Sender::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val roomId = creds.getProperty("testRoomId")
        ?: error("velvet.testRoomId absent de local.properties")
    val text = args.firstOrNull()
        ?: "Message chiffré de test S0.5 — ${System.currentTimeMillis()}"

    println("Envoi d'un message CHIFFRÉ vers $roomId")

    initPlatform(
        config = TracingConfiguration(
            logLevel = LogLevel.WARN,
            traceLogPacks = emptyList(),
            extraTargets = emptyList(),
            writeToStdoutOrSystem = false,
            writeToFiles = null,
            sentryConfig = null,
        ),
        useLightweightTokioRuntime = false,
    )

    val client = ClientBuilder()
        .homeserverUrl(creds.getProperty("homeserver"))
        .sessionPaths(
            File("build/sender-session").apply { mkdirs() }.absolutePath,
            File("build/sender-cache").apply { mkdirs() }.absolutePath,
        )
        .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
        .userAgent("Matriscord-Sender/0.1")
        .build()

    val store = File("build/sender-session.properties")
    if (store.exists()) {
        val s = Properties().apply { store.inputStream().use { load(it) } }
        client.restoreSession(
            Session(
                accessToken = s.getProperty("accessToken"),
                refreshToken = s.getProperty("refreshToken").orEmpty().ifEmpty { null },
                userId = s.getProperty("userId"),
                deviceId = s.getProperty("deviceId"),
                homeserverUrl = s.getProperty("homeserverUrl"),
                oauthData = s.getProperty("oauthData").orEmpty().ifEmpty { null },
                slidingSyncVersion = SlidingSyncVersion.valueOf(s.getProperty("slidingSyncVersion")),
            )
        )
        println("  session restaurée : ${client.session().userId} / ${client.session().deviceId}")
    } else {
        client.login(
            username = creds.getProperty("username2"),
            password = creds.getProperty("password2"),
            initialDeviceName = "Matriscord Sender",
            deviceId = null,
        )
        val s = client.session()
        Properties().apply {
            setProperty("accessToken", s.accessToken)
            setProperty("refreshToken", s.refreshToken.orEmpty())
            setProperty("userId", s.userId)
            setProperty("deviceId", s.deviceId)
            setProperty("homeserverUrl", s.homeserverUrl)
            setProperty("oauthData", s.oauthData.orEmpty())
            setProperty("slidingSyncVersion", s.slidingSyncVersion.name)
        }.also { pr -> store.outputStream().use { pr.store(it, "session emetteur S0.5") } }
        println("  connecté : ${s.userId} / ${s.deviceId}")
    }

    val syncService = client.syncService().finish()
    syncService.start()
    val roomList = syncService.roomListService().allRooms()
    val entries = roomList.entriesWithDynamicAdapters(
        pageSize = 100u,
        listener = object : RoomListEntriesListener {
            override fun onUpdate(roomEntriesUpdate: List<RoomListEntriesUpdate>) = Unit
        },
    )
    entries.controller().setFilter(RoomListEntriesDynamicFilterKind.NonLeft)
    println("  synchronisation…")
    delay(15_000)

    val room = syncService.roomListService().room(roomId)
    println("  salon : ${room.displayName()}")

    room.timeline().send(messageEventContentFromMarkdown(text))
    println("  ✓ envoyé : \"$text\"")
    delay(5_000)

    syncService.stop()
    println("Terminé. Surveiller : adb logcat -s MatriscordS05")
}

private object Sender

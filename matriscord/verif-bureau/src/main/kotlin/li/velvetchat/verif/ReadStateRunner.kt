package li.velvetchat.verif

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.Server
import li.velvetchat.core.model.SystemKind
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val creds = ReadStateRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val roomId = creds.getProperty("testRoomId")

    println("=".repeat(72))
    println("  Lot 7 — non-lus et événements d'état")
    println("=".repeat(72))
    println()

    val aDir = File("build/engine-session")
    val a: MatrixEngine = engine("build/engine-session")
    a.initPlatform()
    a.restore(WindowsSessionStore(aDir).load() ?: error("lancer d'abord verifyEngine"))
    a.startSync()
    println("Lecteur  : ${a.currentSession().userId} / ${a.currentSession().deviceId}")

    val bDir = File("build/flow-sender")
    val b: MatrixEngine = engine("build/flow-sender")
    val bStored = WindowsSessionStore(bDir).load()
    if (bStored != null) b.restore(bStored) else {
        val s = b.login(
            homeserverUrl = creds.getProperty("homeserver"),
            username = creds.getProperty("username2"),
            password = creds.getProperty("password2"),
        )
        WindowsSessionStore(bDir).save(s)
    }
    b.startSync()
    println("Écrivain : ${b.currentSession().userId} / ${b.currentSession().deviceId}")
    println()

    val messages = MutableStateFlow<List<Message>>(emptyList())
    val servers = MutableStateFlow<List<Server>>(emptyList())
    val scope = CoroutineScope(Dispatchers.Default)
    val jobs = listOf(
        scope.launch { a.messagesFlow(roomId).collect { messages.value = it } },
        scope.launch { a.serversFlow().collect { servers.value = it } },
    )

    val checks = Checks()

    messages.awaitAny("l'historique initial", 30_000) { it.isNotEmpty() }
    a.markRead(roomId)
    val clean = servers.awaitChannel("remise à zéro initiale", roomId, 30_000) {
        it.unread.notifications == 0
    }
    if (clean == null) {
        println("✗ Impossible de partir d'un salon lu. Scénario interrompu.")
        jobs.forEach { it.cancel() }; a.stopSync(); b.stopSync(); exitProcess(1)
    }
    println("① Salon remis à zéro avant de commencer")

    val stamp = System.currentTimeMillis()
    val text = "Lot 7 — message non lu $stamp"
    b.send(roomId, text)
    println("② L'autre compte envoie : « $text »")

    val unread = servers.awaitChannel("apparition du non-lu", roomId, 60_000) {
        it.unread.notifications > 0
    }
    if (unread != null) {
        checks.pass(
            "compteur de non-lus",
            "${unread.unread.notifications} non-lu(s) remonté(s) par serversFlow, sans interrogation",
        )
    } else {
        checks.fail("compteur de non-lus", "aucun non-lu n'est apparu après l'envoi")
    }

    val withSeparator = messages.awaitAny("séparateur nouveaux messages", 60_000) { msgs ->
        msgs.any { it.isFirstUnread }
    }
    if (withSeparator != null) {
        val at = withSeparator.first { it.isFirstUnread }
        checks.pass(
            "séparateur « nouveaux messages »",
            "posé sur « ${at.preview()} » — dérivé du marqueur serveur, donc suivi d'un appareil à l'autre",
        )
    } else {
        checks.fail("séparateur « nouveaux messages »", "aucun message ne porte isFirstUnread")
    }

    a.markRead(roomId)
    println("③ markRead appelé")

    val back = servers.awaitChannel("retour à zéro", roomId, 60_000) {
        it.unread.notifications == 0
    }
    if (back != null) {
        checks.pass("retour à zéro", "la pastille retombe, et l'UI l'apprend par le flux")
    } else {
        val seen = servers.value.channelOf(roomId)?.unread?.notifications
        checks.fail("retour à zéro", "compteur toujours à $seen après markRead")
    }

    val separatorGone = messages.awaitAny("effacement du séparateur", 60_000) { msgs ->
        msgs.none { it.isFirstUnread }
    }
    if (separatorGone != null) {
        checks.pass(
            "séparateur effacé",
            "le marqueur « entièrement lu » a bien suivi — c'est pour ça que markRead envoie les DEUX marqueurs",
        )
    } else {
        checks.fail(
            "séparateur effacé",
            "un message porte encore isFirstUnread : le marqueur m.fully_read n'a pas avancé",
        )
    }

    println()
    println("④ Remontée jusqu'au début du salon pour atteindre sa création…")
    var guard = 0
    while (a.loadMore(roomId, 50) && guard++ < 20) { }

    val all = messages.awaitStable(quietMs = 2_000, timeoutMs = 30_000)
    println("   ${all.size} événements chargés")

    val system = all.mapNotNull { it.body as? MessageBody.System }
    val raw = all.mapNotNull { it.body as? MessageBody.Unsupported }

    if (system.isNotEmpty()) {
        val byKind = system.groupingBy { it.kind }.eachCount().entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key}×${it.value}" }
        checks.pass("événements d'état typés", byKind)
    } else {
        checks.inconclusive(
            "événements d'état typés",
            "aucun événement d'état dans l'historique chargé — rien à typer, donc rien de prouvé",
        )
    }

    val protocolLeaks = raw.filter { it.description.startsWith("m.") }
    when {
        protocolLeaks.isNotEmpty() -> checks.fail(
            "aucun type protocolaire brut en timeline",
            "encore visibles : ${protocolLeaks.map { it.description }.distinct()}",
        )
        system.isEmpty() -> checks.inconclusive(
            "aucun type protocolaire brut en timeline",
            "aucun événement d'état chargé : rien n'aurait pu fuiter, donc rien n'est démontré",
        )
        else -> checks.pass(
            "aucun type protocolaire brut en timeline",
            "${system.size} événement(s) d'état typé(s), ${raw.size} en Unsupported dont aucun `m.*`",
        )
    }

    val others = system.filter { it.kind == SystemKind.OTHER }
    if (others.isNotEmpty()) {
        checks.note(
            "retombés en OTHER",
            others.mapNotNull { it.detail }.distinct().joinToString(", ")
                .ifEmpty { "${others.size}, sans type brut associé" },
        )
    }

    val noise = system.count { it.kind.isNoise }
    checks.note(
        "politique d'affichage",
        "${system.size - noise} événement(s) à montrer, $noise classé(s) bruit par défaut — " +
            "l'UI décide, le moteur ne filtre pas",
    )

    println()
    jobs.forEach { it.cancel() }
    a.stopSync()
    b.stopSync()
    checks.report()
}

// -------------------------------------------------------------------------------------------

private fun engine(path: String): MatrixEngine = RustMatrixEngine(
    dataPath = File(path).apply { mkdirs() }.absolutePath,
    cachePath = File("$path-cache").apply { mkdirs() }.absolutePath,
    logToStdout = false,
)

private fun Message.preview(): String = when (val b = body) {
    is MessageBody.Text -> b.text.take(40)
    is MessageBody.Media -> "[${b.kind}] ${b.caption.take(30)}"
    is MessageBody.System -> "[${b.kind}]"
    else -> b::class.simpleName ?: "?"
}

private fun List<Server>.channelOf(channelId: String): Channel? =
    firstNotNullOfOrNull { s -> s.channels.firstOrNull { it.id == channelId } }

private suspend fun <T> MutableStateFlow<List<T>>.awaitAny(
    label: String,
    timeoutMs: Long,
    predicate: (List<T>) -> Boolean,
): List<T>? {
    val hit = withTimeoutOrNull(timeoutMs) { first(predicate) }
    if (hit == null) println("   … « $label » non observé dans les ${timeoutMs / 1000} s")
    return hit
}

private suspend fun <T> MutableStateFlow<List<T>>.awaitStable(
    quietMs: Long,
    timeoutMs: Long,
): List<T> {
    val deadline = System.currentTimeMillis() + timeoutMs
    var last = value
    var stableSince = System.currentTimeMillis()
    while (System.currentTimeMillis() < deadline) {
        val next = withTimeoutOrNull(quietMs) { first { it.size != last.size } }
        if (next == null) return last
        last = next
        stableSince = System.currentTimeMillis()
    }
    println("   … la liste bougeait encore au bout de ${timeoutMs / 1000} s")
    return last
}

private suspend fun MutableStateFlow<List<Server>>.awaitChannel(
    label: String,
    channelId: String,
    timeoutMs: Long,
    predicate: (Channel) -> Boolean,
): Channel? {
    val hit = withTimeoutOrNull(timeoutMs) {
        first { servers -> servers.channelOf(channelId)?.let(predicate) == true }
    }
    if (hit == null) println("   … « $label » non observé dans les ${timeoutMs / 1000} s")
    return hit?.channelOf(channelId)
}

private object ReadStateRunner

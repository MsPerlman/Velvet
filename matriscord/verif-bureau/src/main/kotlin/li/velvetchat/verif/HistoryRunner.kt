package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.VerificationState
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties

fun main(): Unit = runBlocking {
    val creds = HistoryRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    println("=".repeat(72))
    println("  Historique chiffré et nouvel arrivant (MSC4268)")
    println("=".repeat(72))
    println()

    val aDir = File("build/engine-session")
    val a: MatrixEngine = engine("build/engine-session")
    a.initPlatform()
    a.restore(WindowsSessionStore(aDir).load() ?: error("lancer d'abord verifyEngine"))
    a.startSync()

    val bDir = File("build/history-joiner-${System.currentTimeMillis()}")
    val b: MatrixEngine = engine(bDir.path)
    b.initPlatform()
    b.login(
        homeserverUrl = creds.getProperty("homeserver"),
        username = creds.getProperty("username2"),
        password = creds.getProperty("password2"),
    )
    b.startSync()

    val cleRecuperation = System.getProperty("velvet.recoveryKey")
        ?: obtenirCleDeRecuperation(creds)
    if (cleRecuperation.isNullOrBlank()) {
        println("⚠ Aucune clé de récupération : l'arrivant restera non vérifié.")
    } else {
        runCatching { b.recovery.recover(cleRecuperation) }
            .onFailure { println("⚠ Récupération refusée : ${it.message}") }
    }

    val verifie = withTimeoutOrNull(20_000) {
        while (b.verification.verificationState() != VerificationState.VERIFIED) delay(2_000)
        true
    } == true
    println("   identité de l'arrivant : ${b.verification.verificationState()}")

    val invite = b.currentSession().userId
    println("Fondateur : ${a.currentSession().userId} / ${a.currentSession().deviceId}")
    println("Arrivant  : $invite / ${b.currentSession().deviceId} (appareil neuf)")
    println()

    val checks = Checks()
    val stamp = System.currentTimeMillis()
    val created = mutableListOf<String>()

    try {
        val serveurA = a.createServer("Historique restricted — $stamp")
        val serveurB = a.createServer("Historique invitation — $stamp")
        created += serveurA.id
        created += serveurB.id
        val parRestricted = a.createChannel(serveurA.id, "par-restricted")
        val parInvitation = a.createChannel(serveurB.id, "par-invitation")
        created += parRestricted.id
        created += parInvitation.id
        println("① Deux serveurs cloisonnés, un salon chacun")

        val secret = "Message ANTÉRIEUR à l'arrivée — $stamp"
        a.send(parRestricted.id, secret)
        a.send(parInvitation.id, secret)

        listOf(parRestricted.id, parInvitation.id).forEach { id ->
            withTimeoutOrNull(60_000) {
                while (a.messages(id, 10).none { it.text() == secret && it.id.startsWith("$") }) delay(500)
                true
            } ?: error("message antérieur non confirmé dans $id")
        }
        println("   message antérieur posé dans les deux salons")
        println()

        println("② Chemin « serveur » : invitation au SPACE seulement, entrée par restricted")
        a.invite(serveurA.id, invite)
        delay(3_000)
        b.joinServer(serveurA.id)
        delay(10_000)

        val vuParRestricted = b.readBack(parRestricted.id, secret)
        if (vuParRestricted.lisible) {
            checks.pass(
                "historique après entrée par restricted",
                "l'arrivant LIT le message antérieur — le partage fonctionne aussi sans invitation nominative",
            )
        } else {
            checks.note(
                "historique après entrée par restricted",
                "illisible (${vuParRestricted.illisibles} événement(s) chiffrés). MSC4268 partage les clés " +
                    "**à l'invitation** ; une entrée par `restricted` n'en produit pas. C'est la limite " +
                    "structurelle du modèle « serveur ».",
            )
        }

        println()
        println("③ Chemin « invitation » : invitation nominative AU SALON, jamais visité avant")
        a.invite(parInvitation.id, invite)
        delay(10_000)
        b.joinChannel(parInvitation.id)
        delay(15_000)

        val vuParInvitation = b.readBack(parInvitation.id, secret)
        if (vuParInvitation.lisible) {
            checks.pass(
                "historique après invitation nominative",
                "l'arrivant LIT le message antérieur — MSC4268 fait bien son travail",
            )
        } else if (!verifie) {
            checks.note(
                "historique après invitation nominative",
                "illisible (${vuParInvitation.illisibles} chiffré(s)) — attendu : l'appareil de l'arrivant " +
                    "n'est pas vérifié, donc il ne reçoit aucune clé. Témoin négatif, pas un échec.",
            )
        } else {
            checks.fail(
                "historique après invitation nominative",
                "illisible (${vuParInvitation.illisibles} chiffré(s)) alors que l'arrivant EST vérifié : " +
                    "le partage de clés ne fonctionne sur aucun chemin",
            )
        }

        println()
        if (!verifie) {
            checks.note(
                "appareil de l'arrivant",
                "NON vérifié — les clés d'historique ne partent que vers des appareils signés " +
                    "par leur propriétaire (MSC4153). Relancer avec -PrecoveryKey=… pour le cas réel.",
            )
        }
        when {
            vuParRestricted.lisible ->
                checks.note("conclusion", "aucun compromis nécessaire : l'historique suit dans les deux cas")
            vuParInvitation.lisible ->
                checks.note(
                    "conclusion",
                    "contournement possible mais à un prix : il faut INVITER nominativement à chaque " +
                        "salon, au lieu de laisser `restricted` faire le travail. Décision produit, pas technique.",
                )
            else ->
                checks.note(
                    "conclusion",
                    "aucun chemin ne partage l'historique dans cette configuration — à traiter côté UI",
                )
        }
    } finally {
        println()
        println("④ Ménage")
        created.take(2).forEach { id ->
            runCatching { b.leaveServer(id) }
            runCatching { a.leaveServer(id) }
        }
        runCatching { b.leaveChannel(created.last()) }
        a.stopSync()
        b.stopSync()

        runCatching { deconnecter(b) }
        runCatching { bDir.deleteRecursively(); File("${bDir.path}-cache").deleteRecursively() }
    }

    println()
    checks.report()
}

// ---------------------------------------------------------------------------------------------

private suspend fun obtenirCleDeRecuperation(creds: Properties): String? {
    val dir = File("build/flow-sender")
    val e = engine(dir.path)
    return try {
        e.initPlatform()
        val stored = WindowsSessionStore(dir).load()
        if (stored != null) e.restore(stored) else {
            val s = e.login(
                homeserverUrl = creds.getProperty("homeserver"),
                username = creds.getProperty("username2"),
                password = creds.getProperty("password2"),
            )
            WindowsSessionStore(dir).save(s)
        }
        e.startSync()
        delay(4_000)
        e.recovery.resetRecoveryKey().also {
            println("Clé de récupération obtenue depuis ${e.currentSession().deviceId}")
            delay(5_000)
        }
    } catch (t: Throwable) {
        println("⚠ Impossible d'obtenir une clé de récupération : ${t.message}")
        null
    } finally {
        runCatching { e.stopSync() }
    }
}

private data class ReadBack(val lisible: Boolean, val illisibles: Int, val total: Int)

private suspend fun MatrixEngine.readBack(channelId: String, attendu: String): ReadBack {
    repeat(3) { runCatching { loadMore(channelId, 50) } }
    delay(4_000)
    val msgs = messages(channelId, 50)
    return ReadBack(
        lisible = msgs.any { it.text() == attendu },
        illisibles = msgs.count { it.body is MessageBody.Undecryptable },
        total = msgs.size,
    )
}

private fun li.velvetchat.core.model.Message.text(): String? =
    (body as? MessageBody.Text)?.text

private suspend fun MatrixEngine.leaveChannel(channelId: String) {
    val client = clientOf(this)
    client.getRoom(channelId)?.leave()
}

private suspend fun MatrixEngine.joinChannel(channelId: String) {
    clientOf(this).joinRoomById(channelId)
}

private fun clientOf(engine: MatrixEngine): org.matrix.rustcomponents.sdk.Client {
    val f = RustMatrixEngine::class.java.getDeclaredField("client")
    f.isAccessible = true
    return f.get(engine) as org.matrix.rustcomponents.sdk.Client
}

private suspend fun deconnecter(engine: MatrixEngine) {
    clientOf(engine).logout()
}

private fun engine(path: String): MatrixEngine = RustMatrixEngine(
    dataPath = File(path).apply { mkdirs() }.absolutePath,
    cachePath = File("$path-cache").apply { mkdirs() }.absolutePath,
    logLevel = if (System.getProperty("velvet.debug") != null)
        li.velvetchat.core.EngineLogLevel.DEBUG
    else
        li.velvetchat.core.EngineLogLevel.WARN,
    logToStdout = System.getProperty("velvet.debug") != null,
)

private object HistoryRunner

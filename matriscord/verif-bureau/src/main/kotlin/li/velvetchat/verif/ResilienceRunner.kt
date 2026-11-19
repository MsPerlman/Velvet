package li.velvetchat.verif

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.ConnectionState
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList

fun main(): Unit = runBlocking {
    val creds = ResilienceRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val roomId = creds.getProperty("testRoomId")

    println("=".repeat(72))
    println("  Lot 10 — robustesse de la synchronisation")
    println("=".repeat(72))
    println()

    val aDir = File("build/engine-session")
    val a: MatrixEngine = engine("build/engine-session")
    a.initPlatform()
    a.restore(WindowsSessionStore(aDir).load() ?: error("lancer d'abord verifyEngine"))

    val scope = CoroutineScope(Dispatchers.Default)
    val states = CopyOnWriteArrayList<ConnectionState>()
    val stateJob = scope.launch {
        a.connectionFlow().collect {
            if (states.lastOrNull() != it) {
                states += it
                println("     [état] $it")
            }
        }
    }

    val checks = Checks()

    delay(300)
    if (states.firstOrNull() == ConnectionState.STOPPED) {
        checks.pass("état initial", "STOPPED — le flux répond avant même le premier démarrage")
    } else {
        checks.fail("état initial", "premier état vu : ${states.firstOrNull()}")
    }

    println("① Démarrage de la synchronisation")
    a.startSync()
    val connecte = withTimeoutOrNull(60_000) {
        while (states.lastOrNull() != ConnectionState.CONNECTED) delay(100)
        true
    }
    if (connecte == true && states.contains(ConnectionState.CONNECTING)) {
        checks.pass(
            "cycle de connexion",
            "${states.joinToString(" → ")} — l'UI a de quoi afficher un état de chargement",
        )
    } else {
        checks.fail("cycle de connexion", "états vus : ${states.joinToString(" → ")}")
    }

    val messages = CopyOnWriteArrayList<Message>()
    val msgJob = scope.launch { a.messagesFlow(roomId).collect { messages.clear(); messages.addAll(it) } }
    delay(3_000)

    println()
    println("② Interruption de la synchronisation, sans que le moteur l'ait demandé")
    val avant = states.size
    stopSyncBehindTheEngine(a)

    val detecte = withTimeoutOrNull(60_000) {
        while (states.size == avant) delay(100)
        true
    }
    if (detecte == true) {
        checks.pass(
            "interruption détectée",
            "le moteur voit ${states.last()} — sans ça, la conversation se figerait en silence",
        )
    } else {
        checks.fail("interruption détectée", "aucun changement d'état après l'arrêt")
    }

    println("③ Attente d'une reprise automatique (aucun appel côté test)")
    val repris = withTimeoutOrNull(120_000) {
        while (states.lastOrNull() != ConnectionState.CONNECTED) delay(200)
        true
    }
    if (repris == true) {
        checks.pass(
            "reprise automatique",
            "retour à CONNECTED sans intervention — la relance est espacée, jamais abandonnée",
        )
    } else {
        checks.fail("reprise automatique", "état final : ${states.lastOrNull()}")
    }

    println("④ L'autre compte envoie un message après l'interruption")
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

    val texte = "Lot 10 — message d'après la coupure ${System.currentTimeMillis()}"
    b.send(roomId, texte)

    val recu = withTimeoutOrNull(90_000) {
        while (messages.none { (it.body as? MessageBody.Text)?.text == texte }) delay(200)
        true
    }
    if (recu == true) {
        checks.pass(
            "reprise fonctionnelle",
            "un message envoyé après l'interruption est bien arrivé — la reprise n'est pas qu'une étiquette",
        )
    } else {
        checks.fail(
            "reprise fonctionnelle",
            "l'état dit CONNECTED mais aucun message ne passe : la reprise est cosmétique",
        )
    }

    println()
    println("⑤ Arrêt volontaire")
    a.stopSync()
    delay(8_000)
    if (states.lastOrNull() == ConnectionState.STOPPED) {
        checks.pass(
            "arrêt volontaire respecté",
            "toujours STOPPED après 8 s — le superviseur ne relance pas ce qu'on a arrêté",
        )
    } else {
        checks.fail(
            "arrêt volontaire respecté",
            "l'état est reparti en ${states.lastOrNull()} : stopSync ne tient pas",
        )
    }

    checks.note(
        "portée du test",
        "l'interruption est un arrêt réel du service, pas une partition réseau — bloquer le " +
            "trafic demande des droits administrateur. Le chemin de reprise est exercé, la " +
            "coupure réseau elle-même reste à vérifier sur appareil.",
    )

    println()
    stateJob.cancel()
    msgJob.cancel()
    b.stopSync()
    checks.report()
}

// ---------------------------------------------------------------------------------------------

private fun stopSyncBehindTheEngine(engine: MatrixEngine) {
    val field = RustMatrixEngine::class.java.getDeclaredField("syncService")
    field.isAccessible = true
    val service = field.get(engine) as? org.matrix.rustcomponents.sdk.SyncService
        ?: error("aucun service de synchronisation à interrompre")
    runBlocking { service.stop() }
}

private fun engine(path: String): MatrixEngine = RustMatrixEngine(
    dataPath = File(path).apply { mkdirs() }.absolutePath,
    cachePath = File("$path-cache").apply { mkdirs() }.absolutePath,
    logToStdout = false,
)

private object ResilienceRunner

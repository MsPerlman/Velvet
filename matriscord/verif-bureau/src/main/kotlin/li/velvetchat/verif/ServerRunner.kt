package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.Server
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val creds = ServerRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    println("=".repeat(72))
    println("  Lot 8 — création de serveur, restricted, auto-join")
    println("=".repeat(72))
    println()

    val aDir = File("build/engine-session")
    val a: MatrixEngine = engineAt("build/engine-session")
    a.initPlatform()
    a.restore(WindowsSessionStore(aDir).load() ?: error("lancer d'abord verifyEngine"))
    a.startSync()

    val bDir = File("build/flow-sender")
    val b: MatrixEngine = engineAt("build/flow-sender")
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

    val fondateur = a.currentSession().userId
    val invite = b.currentSession().userId
    println("Fondateur : $fondateur")
    println("Invité    : $invite")
    println()

    val checks = Checks()
    val stamp = System.currentTimeMillis()
    val created = mutableListOf<String>()
    var scenarioComplet = false

    try {
        val server = a.createServer("Test Lot 8 — $stamp", topic = "Serveur de vérification")
        created += server.id
        println("① Serveur créé : ${server.id}")

        val general = a.createChannel(server.id, "general", "Salon principal")
        val annonces = a.createChannel(server.id, "annonces")
        created += general.id
        created += annonces.id
        println("   deux salons créés : ${general.id}, ${annonces.id}")

        if (general.encryption == li.velvetchat.core.model.EncryptionState.ENCRYPTED) {
            checks.pass("salon chiffré d'office", "§2.8ter — pas d'option à cocher, c'est le défaut")
        } else {
            checks.fail("salon chiffré d'office", "état ${general.encryption}")
        }

        val avant = "Message écrit AVANT l'arrivée de l'invité — $stamp"
        a.send(general.id, avant)

        val vuParA = withTimeoutOrNull(60_000) {
            var s: Server? = null
            while (s?.channels?.size != 2) {
                s = a.servers().firstOrNull { it.id == server.id }
                if (s?.channels?.size != 2) delay(1_000)
            }
            s
        }
        if (vuParA != null) {
            checks.pass(
                "hiérarchie côté fondateur",
                "${vuParA.channels.size} salons rattachés au serveur (m.space.child posé)",
            )
        } else {
            checks.fail("hiérarchie côté fondateur", "les salons ne sont pas remontés dans le serveur")
        }

        a.invite(server.id, invite)
        println("② $invite invité au serveur — et à AUCUN salon")

        delay(3_000)
        val join = b.joinServer(server.id)
        println("③ joinServer : ${join.joined.size} salon(s) rejoint(s), ${join.refused.size} refusé(s)")

        val expected = setOf(general.id, annonces.id)
        val reallyJoined = join.joined.toSet()
        if (expected.all { it in reallyJoined }) {
            checks.pass(
                "auto-join des salons",
                "les 2 salons rejoints sans invitation nominative — c'est `restricted` qui l'autorise",
            )
        } else {
            checks.fail(
                "auto-join des salons",
                "manquants : ${expected - reallyJoined} ; refus : ${join.refused}",
            )
        }

        if (join.isComplete) {
            checks.pass("entrée complète", "aucun salon refusé")
        } else {
            checks.note("entrée partielle", join.refused.entries.joinToString { "${it.key} : ${it.value}" })
        }

        val preuve = "Preuve d'appartenance de l'invité — $stamp"
        val ecritureOk = runCatching { b.send(general.id, preuve) }.isSuccess
        val vuParB = withTimeoutOrNull(60_000) {
            var found = false
            while (!found) {
                found = b.messages(general.id, 20)
                    .any { (it.body as? MessageBody.Text)?.text == preuve && it.id.startsWith("$") }
                if (!found) delay(1_000)
            }
            true
        }
        if (ecritureOk && vuParB == true) {
            checks.pass(
                "appartenance réelle",
                "l'invité écrit dans le salon et le serveur accepte — il en est membre, pas spectateur",
            )
        } else {
            checks.fail("appartenance réelle", "envoi accepté : $ecritureOk, confirmé : $vuParB")
        }

        val historique = b.messages(general.id, 50)
        val vuAvant = historique.firstOrNull {
            (it.body as? MessageBody.Text)?.text == avant
        }
        val illisible = historique.count { it.body is MessageBody.Undecryptable }
        when {
            vuAvant != null -> checks.pass(
                "historique antérieur",
                "le message d'avant l'arrivée est lisible — `shared` ET les clés ont suivi",
            )
            illisible > 0 -> checks.note(
                "historique antérieur",
                "présent mais NON DÉCHIFFRABLE ($illisible événement(s)). `history_visibility: shared` " +
                    "rend l'historique accessible côté serveur, il ne partage pas les clés Megolm " +
                    "antérieures. Comportement de Matrix, pas du moteur — aucun client ne fait autrement.",
            )
            else -> checks.note(
                "historique antérieur",
                "aucun événement antérieur remonté pour l'instant",
            )
        }

        val chezB = withTimeoutOrNull(60_000) {
            var s: Server? = null
            while (s == null) {
                s = b.servers().firstOrNull { it.id == server.id }
                if (s == null) delay(1_000)
            }
            s
        }
        if (chezB != null && chezB.channels.size == 2) {
            checks.pass(
                "serveur utilisable par l'invité",
                "« ${chezB.name} » avec ses ${chezB.channels.size} salons — rien à cliquer salon par salon",
            )
        } else {
            checks.fail(
                "serveur utilisable par l'invité",
                "vu : ${chezB?.channels?.size ?: "aucun serveur"}",
            )
        }
        scenarioComplet = true
    } finally {
        println()
        println("④ Ménage : les deux comptes quittent le serveur")
        if (created.isNotEmpty()) {
            val serverId = created.first()
            val partiB = runCatching { b.leaveServer(serverId) }.getOrElse { emptyList() }
            val partiA = runCatching { a.leaveServer(serverId) }.getOrElse { emptyList() }
            println("   invité : ${partiB.size} salon(s), fondateur : ${partiA.size} salon(s)")

            when {
                !scenarioComplet -> println(
                    "   (scénario interrompu : le ménage a tourné, mais ne compte pas comme vérification)"
                )
                partiA.size >= created.size -> checks.pass(
                    "sortie complète",
                    "${partiA.size} salon(s) quittés pour ${created.size} créés — quitter un serveur " +
                        "emporte ses salons, pas d'orphelin laissé en liste",
                )
                else -> checks.fail(
                    "sortie complète",
                    "${partiA.size} salon(s) quitté(s) sur ${created.size} créé(s) : des salons restent en liste",
                )
            }
        }

        a.stopSync()
        b.stopSync()
    }

    println()
    checks.report()
}

// ---------------------------------------------------------------------------------------------

private fun engineAt(path: String): MatrixEngine = RustMatrixEngine(
    dataPath = File(path).apply { mkdirs() }.absolutePath,
    cachePath = File("$path-cache").apply { mkdirs() }.absolutePath,
    logToStdout = false,
)

private object ServerRunner

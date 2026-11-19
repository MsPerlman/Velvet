package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File

fun main(): Unit = runBlocking {
    val a: MatrixEngine = engineAt("build/engine-session")
    a.initPlatform()
    a.restore(WindowsSessionStore(File("build/engine-session")).load() ?: error("lancer d'abord verifyEngine"))
    a.startSync()
    val b: MatrixEngine = engineAt("build/flow-sender")
    b.restore(WindowsSessionStore(File("build/flow-sender")).load() ?: error("lancer d'abord verifyServer"))
    b.startSync()
    b.servers().filter { it.name.startsWith("Sonde ") }.forEach { runCatching { b.leaveServer(it.id) } }

    val serveur = a.createServer("Sonde suppression ${System.currentTimeMillis()}")
    a.createChannel(serveur.id, "autre")
    a.invite(serveur.id, b.currentSession().userId)
    val entree = b.joinServer(serveur.id)
    println("B est entré : ${entree.joined.size} salon(s), refus ${entree.refused}")

    val debut = System.currentTimeMillis()
    a.supprimerServeur(serveur.id)
    println("supprimerServeur — ${System.currentTimeMillis() - debut} ms")

    delay(4_000)
    val chezA = a.servers().any { it.id == serveur.id }
    val chezB = b.servers().any { it.id == serveur.id }
    println(if (!chezA) "✔ A ne le voit plus" else "✘ A le voit encore")
    println(if (!chezB) "✔ B ne le voit plus" else "✘ B le voit encore")
    a.stopSync()
    b.stopSync()
    if (chezA || chezB) error("suppression incomplète")
}

private fun engineAt(path: String): MatrixEngine = RustMatrixEngine(
    dataPath = File(path).apply { mkdirs() }.absolutePath,
    cachePath = File("$path-cache").apply { mkdirs() }.absolutePath,
    logToStdout = false,
)

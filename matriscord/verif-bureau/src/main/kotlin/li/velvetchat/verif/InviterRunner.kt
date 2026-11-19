package li.velvetchat.verif

import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties

object InviterRunner {

@JvmStatic
fun main(args: Array<String>): Unit = runBlocking {
    val qui = args.getOrNull(0)?.takeIf { it.isNotBlank() } ?: error("identifiant manquant")
    val creds = InviterRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val dir = File("build/publication").apply { mkdirs() }
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dir.absolutePath,
        cachePath = File("build/publication-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()
    val store = WindowsSessionStore(dir)
    val stockee = store.load()
    if (stockee != null) {
        engine.restore(stockee)
    } else {
        store.save(
            engine.login(
                homeserverUrl = creds.getProperty("homeserver") ?: error("homeserver absent"),
                username = creds.getProperty("username") ?: error("username absent"),
                password = creds.getProperty("password") ?: error("password absent"),
            ),
        )
    }
    engine.startSync()
    engine.servers().forEach { serveur ->
        val issue = runCatching { engine.invite(serveur.id, qui) }
        println(if (issue.isSuccess) "  ✓ ${serveur.name}" else "  · ${serveur.name} : ${issue.exceptionOrNull()?.message}")
    }
    engine.stopSync()
}
}

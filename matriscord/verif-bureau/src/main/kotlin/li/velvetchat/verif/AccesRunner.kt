package li.velvetchat.verif

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.AccessState
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.rust.RustMatrixEngine
import java.io.File

object AccesRunner {

@JvmStatic
fun main(args: Array<String>): Unit = runBlocking {
    val identifiant = args.getOrNull(0)?.takeIf { it.isNotBlank() } ?: error("identifiant manquant")
    val motDePasse = args.getOrNull(1)?.takeIf { it.isNotBlank() } ?: error("mot de passe manquant")

    val dir = File("build/acces-${System.currentTimeMillis()}").apply { mkdirs() }
    val moteur: MatrixEngine = RustMatrixEngine(
        dataPath = dir.absolutePath,
        cachePath = File(dir, "cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    moteur.initPlatform()
    println("dossier neuf : ${dir.name}")
    moteur.login("https://exemple.org", identifiant, motDePasse)
    println("connecté")
    moteur.startSync()
    println("sync lancé")

    val vus = mutableListOf<AccessState>()
    withTimeoutOrNull(25_000) {
        moteur.accessFlow().collect { etat ->
            vus += etat
            println("  accessFlow → $etat")
        }
    }
    println()
    println("états vus : ${vus.size}")
    println("dernier : ${vus.lastOrNull()}")
    moteur.deconnecter()
    dir.deleteRecursively()
}
}

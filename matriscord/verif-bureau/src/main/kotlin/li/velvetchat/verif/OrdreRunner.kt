package li.velvetchat.verif

import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.rust.RustMatrixEngine
import java.io.File

object OrdreRunner {

@JvmStatic
fun main(args: Array<String>): Unit = runBlocking {
    val identifiant = args.getOrNull(0)?.takeIf { it.isNotBlank() } ?: error("identifiant manquant")
    val motDePasse = args.getOrNull(1)?.takeIf { it.isNotBlank() } ?: error("mot de passe manquant")

    val dir = File("build/ordre-${System.currentTimeMillis()}").apply { mkdirs() }
    val moteur: MatrixEngine = RustMatrixEngine(
        dataPath = dir.absolutePath,
        cachePath = File(dir, "cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    moteur.initPlatform()
    moteur.login("https://exemple.org", identifiant, motDePasse)
    moteur.startSync()

    val serveur = moteur.createServer("essai-ordre-${System.currentTimeMillis() % 100000}")
    println("serveur : ${serveur.id}")
    val noms = listOf("alpha", "beta", "gamma")
    val salons = noms.map { moteur.createChannel(serveur.id, it) }
    salons.forEach { println("  salon ${it.name} = ${it.id}") }

    fun lire(): List<String> = runBlocking {
        moteur.servers().firstOrNull { it.id == serveur.id }?.channels?.map { it.name }.orEmpty()
    }

    println("avant  : ${lire()}")
    val voulu = salons.reversed().map { it.id }
    moteur.reordonnerSalons(serveur.id, voulu)
    kotlinx.coroutines.delay(3_000)
    val apres = lire()
    println("après  : $apres")
    val attendu = noms.reversed() + "general"
    println(
        if (apres == attendu) "  ✓ l'ordre écrit est l'ordre relu, les sans-rang à la fin"
        else "  ✗ ÉCHEC — attendu $attendu",
    )

    salons.forEach { runCatching { moteur.leaveChannel(it.id, forget = true) } }
    runCatching { moteur.leaveServer(serveur.id) }
    println("ménage fait")
    moteur.deconnecter()
    dir.deleteRecursively()
}
}

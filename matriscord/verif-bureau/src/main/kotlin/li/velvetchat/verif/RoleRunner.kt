package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.RoleTier
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties

fun main(): Unit = runBlocking {
    println("=".repeat(72))
    println("  Paliers de rôle et niveaux de pouvoir")
    println("=".repeat(72))

    val creds = Roles::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents")
    val roomId = creds.getProperty("testRoomId") ?: error("testRoomId absent")
    val cible = creds.getProperty("username2")?.let { "@$it:exemple.org" }
        ?: error("username2 absent")

    val dir = File("build/engine-session")
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dir.apply { mkdirs() }.absolutePath,
        cachePath = File("build/engine-session-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()
    engine.restore(WindowsSessionStore(dir).load() ?: error("lancer d'abord verifyEngine"))
    engine.startSync()
    delay(10_000)

    val serveur = engine.servers().firstOrNull { s -> s.channels.any { it.id == roomId } }
        ?: error("space parent introuvable")
    println("Serveur : ${serveur.name}")
    println()

    val voulus = listOf(
        RoleTier(100, "Administrateur", 0xFFF23F43.toInt(), "👑"),
        RoleTier(50, "Modérateur", 0xFF5865F2.toInt(), "🛡"),
        RoleTier(0, "Membre", null, null),
    )
    engine.setRoleTiers(serveur.id, voulus)
    delay(3_000)
    val relus = engine.roleTiers(serveur.id)

    println("① Paliers relus : ${relus.size}")
    relus.forEach { println("   ${it.power} — ${it.name} ${it.icon ?: ""} ${it.colorArgb?.let { c -> "#%06X".format(c and 0xFFFFFF) } ?: ""}") }

    val identiques = relus.size == voulus.size && relus.zip(voulus.sortedByDescending { it.power })
        .all { (a, b) -> a.power == b.power && a.name == b.name && a.colorArgb == b.colorArgb && a.icon == b.icon }
    println(if (identiques) "   ✓ identiques à l'écriture" else "   ✗ divergence après relecture")

    println()
    val avant = engine.members(roomId).firstOrNull { it.userId == cible }
    println("② $cible avant : ${avant?.role?.name} (${avant?.role?.powerLevel})")

    val resultat = engine.setPowerLevel(serveur.id, cible, 50)
    println("   appliqué dans ${resultat.applied.size} salon(s), refusé dans ${resultat.refused.size}")
    resultat.refused.forEach { (salon, motif) -> println("     ✗ $salon : $motif") }
    delay(5_000)

    val dansLeSpace = engine.members(serveur.id).firstOrNull { it.userId == cible }
    println("   dans le SPACE  : ${dansLeSpace?.role?.name} (${dansLeSpace?.role?.powerLevel})")
    val apres = engine.members(roomId).firstOrNull { it.userId == cible }
    println("   dans le SALON  : ${apres?.role?.name} (${apres?.role?.powerLevel})")

    if (apres?.role?.powerLevel == 50L) println("   ✓ le serveur a bien appliqué le changement")
    else println("   ✗ pouvoir inchangé — permissions insuffisantes ?")

    engine.setPowerLevel(serveur.id, cible, avant?.role?.powerLevel ?: 0L)
    delay(3_000)
    val remis = engine.members(roomId).firstOrNull { it.userId == cible }
    println("   remis à : ${remis?.role?.powerLevel}")

    delay(2_000)
    engine.stopSync()
}

private object Roles

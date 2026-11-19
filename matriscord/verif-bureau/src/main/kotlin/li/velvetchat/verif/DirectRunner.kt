package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties

fun main(): Unit = runBlocking {
    println("=".repeat(72))
    println("  Conversations privées — m.direct")
    println("=".repeat(72))

    val creds = Directs::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents")
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

    println("Avant : ${engine.directs().size} conversation(s)")

    val salon = engine.openDirect(cible)
    println("① Ouverte avec $cible → $salon")
    delay(6_000)

    engine.send(salon, "Bonjour depuis le banc d'essai des conversations privées.")
    delay(5_000)

    val liste = engine.directs()
    println()
    println("② Relues : ${liste.size}")
    liste.forEach {
        println("   ${it.name}  (${it.userId})")
        println("     salon   : ${it.roomId}")
        println("     aperçu  : ${it.preview ?: "—"}")
        println("     activité: ${it.timestamp ?: "—"}")
    }

    val trouvee = liste.firstOrNull { it.roomId == salon }
    println()
    if (trouvee == null) {
        println("✗ le salon créé n'apparaît PAS dans m.direct")
    } else {
        println("✓ le salon créé apparaît bien dans m.direct")
        if (trouvee.preview != null) println("✓ l'aperçu remonte le dernier message")
        else println("✗ aucun aperçu — le dernier événement n'a pas été lu")
    }

    val second = engine.openDirect(cible)
    println()
    if (second == salon) println("✓ un second appel retrouve le même salon")
    else println("✗ un second appel a créé $second — l'historique se scinderait")

    println()
    engine.setPinned(salon, true)
    delay(3_000)
    val epingle = engine.directs().firstOrNull { it.roomId == salon }?.pinned
    println("③ après épinglage   : $epingle")

    engine.setPinned(salon, false)
    delay(3_000)
    val depingle = engine.directs().firstOrNull { it.roomId == salon }?.pinned
    println("   après dépinglage  : $depingle")

    val secondDepinglage = runCatching { engine.setPinned(salon, false) }
    println("   dépinglage répété : ${if (secondDepinglage.isSuccess) "toléré" else "ÉCHEC"}")

    println()
    if (epingle == true && depingle == false && secondDepinglage.isSuccess) {
        println("✓ l'épinglage fait l'aller-retour, et le dépinglage est idempotent")
    } else {
        println("✗ l'épinglage ne se comporte pas comme attendu")
    }

    delay(2_000)
    engine.stopSync()
}

private object Directs

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
    println("  Emojis personnalisés — aller-retour im.ponies.room_emotes")
    println("=".repeat(72))

    val creds = Emotes::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents")
    val roomId = creds.getProperty("testRoomId") ?: error("testRoomId absent")

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
    println("Serveur : ${serveur.name} (${serveur.id})")

    val source = File("../../../emoji")
    val fichiers = source.listFiles { f -> f.extension.lowercase() in setOf("png", "webp", "gif") }
        ?.sortedBy { it.name }
        ?: error("aucun emoji dans ${source.absolutePath}")
    println("À poser : ${fichiers.joinToString { it.name }}")
    println()

    val avant = engine.emotes(serveur.id)
    println("Pack avant : ${avant.size} emoji(s)")

    fichiers.forEach { f ->
        val emote = engine.addEmote(serveur.id, f.nameWithoutExtension, f.absolutePath)
        println("  + :${emote.shortcode}: → ${emote.url}")
        delay(1_500)
    }

    delay(3_000)
    val apres = engine.emotes(serveur.id)
    println()
    println("Pack après : ${apres.size} emoji(s)")
    apres.forEach { println("  :${it.shortcode}: ${it.url}") }

    val attendus = fichiers.map { it.nameWithoutExtension }.toSet()
    val trouves = apres.map { it.shortcode }.toSet()
    val manquants = attendus - trouves
    println()
    if (manquants.isEmpty()) {
        println("✓ les ${attendus.size} emojis écrits sont relus à l'identique")
        val vides = apres.filter { !it.url.startsWith("mxc://") }
        if (vides.isEmpty()) println("✓ toutes les adresses sont des mxc:// valides")
        else println("✗ adresses invalides : ${vides.map { it.shortcode }}")
    } else {
        println("✗ manquants après relecture : $manquants")
    }

    val corps = apres.joinToString(" ") { ":${it.shortcode}:" }
    val html = apres.joinToString(" ") {
        """<img data-mx-emoticon src="${it.url}" alt=":${it.shortcode}:" title=":${it.shortcode}:" height="32" />"""
    }
    engine.sendHtml(roomId, "Emojis : $corps", "Emojis : $html")
    println()
    println("✓ message d'essai envoyé : $corps")

    delay(5_000)
    engine.stopSync()
}

private object Emotes

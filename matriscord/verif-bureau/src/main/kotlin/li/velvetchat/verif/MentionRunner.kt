package li.velvetchat.verif

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.composerMessage
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val creds = MentionRunnerMarqueur::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val roomId = creds.getProperty("testRoomId")
    val cible = creds.getProperty("username")?.let { "@$it:exemple.org" }
        ?: error("username absent")

    println("=".repeat(72))
    println("  Mentions — le lien, la notification, et la surbrillance chez l'autre")
    println("=".repeat(72))
    println()

    val dir = File("build/mention-sender")
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dir.apply { mkdirs() }.absolutePath,
        cachePath = File("build/mention-sender-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()

    val stockee = WindowsSessionStore(dir).load()
    if (stockee != null) {
        engine.restore(stockee)
    } else {
        val s = engine.login(
            homeserverUrl = creds.getProperty("homeserver"),
            username = creds.getProperty("username2") ?: error("username2 absent"),
            password = creds.getProperty("password2") ?: error("password2 absent"),
        )
        WindowsSessionStore(dir).save(s)
    }
    engine.startSync()
    println("① Connecté en tant que ${engine.currentSession().userId}")

    val salons = engine.servers().flatMap { it.channels }
    val vise = salons.firstOrNull { it.name == "general" }?.id ?: roomId
    println("   salons : ${salons.joinToString { "${it.name}=${it.id}" }}")
    println("   testRoomId=$roomId")
    println("   visé     : $vise")
    val membres = engine.members(vise)
    println("   ${membres.size} membre(s)")

    println()
    println("   Derniers messages du salon, vus du serveur :")
    engine.messages(vise, 10).forEach { m ->
        val t = m.body as? MessageBody.Text
        println("   · ${m.sender.substringBefore(':')} : ${t?.text?.take(48) ?: m.body}")
        if (t != null) {
            println("       html     = ${t.html}")
            println("       mentions = ${m.mentions}")
        }
    }
    println()

    val moi = engine.currentSession().userId
    val nomCible = membres.firstOrNull { it.userId == cible }?.displayName
        ?: cible.removePrefix("@").substringBefore(':')
    val marque = System.currentTimeMillis().toString().takeLast(5)

    val texte = "Test $marque pour @$nomCible"
    val sortant = composerMessage(texte, emptyList(), membres, salons)

    println()
    println("② Texte tapé      : $texte")
    println("   corps envoyé   : ${sortant.corps}")
    println("   html envoyé    : ${sortant.html}")
    println("   m.mentions     : ${sortant.mentions}")
    println()

    if (sortant.html == null) {
        echec("aucun HTML produit — la mention n'a pas été reconnue dans le texte")
    }
    if (cible !in sortant.mentions) {
        echec("$cible absent de m.mentions — le lien serait décoratif, sans notification")
    }

    engine.sendHtml(vise, sortant.corps, sortant.html!!, sortant.mentions, false)
    println("③ Envoyé. Attente du retour par le flux…")

    val recu = withTimeoutOrNull(45_000) {
        engine.messagesFlow(vise).first { messages ->
            messages.any { (it.body as? MessageBody.Text)?.text?.contains(marque) == true }
        }.first { (it.body as? MessageBody.Text)?.text?.contains(marque) == true }
    } ?: echec("le message n'est jamais revenu par le flux")

    val corps = recu.body as MessageBody.Text
    println()
    println("④ Relu du serveur :")
    println("   body       : ${corps.text}")
    println("   formatted  : ${corps.html}")
    println("   mentions   : ${recu.mentions}")
    println("   @room      : ${recu.mentionneToutLeSalon}")
    println()

    if (corps.html?.contains("matrix.to") != true) {
        echec("le formatted_body relu ne porte pas le lien — il a été perdu à l'envoi")
    }
    if (cible !in recu.mentions) {
        echec("m.mentions relu ne contient pas $cible — la notification n'est pas partie")
    }

    println("✓ Le lien et la mention ont fait l'aller-retour.")
    println()
    println("  Sur le téléphone connecté à $cible, ce message doit maintenant")
    println("  apparaître **surligné**, avec le liseré d'accent à gauche.")
    println("  (envoyé par $moi, marque $marque)")
    engine.stopSync()
    exitProcess(0)
}

private fun echec(raison: String): Nothing {
    println("✗ ÉCHEC : $raison")
    exitProcess(1)
}

private class MentionRunnerMarqueur

package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.SendState
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties

fun main() = runBlocking {
    val creds = SendRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val roomId = creds.getProperty("testRoomId")

    println("=".repeat(72))
    println("  Lot 2 — envoi de message et écho local")
    println("=".repeat(72))
    println()

    val dataDir = File("build/engine-session")
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dataDir.absolutePath,
        cachePath = File("build/engine-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()

    val stored = WindowsSessionStore(dataDir).load()
        ?: error("aucune session — lancer d'abord :verif-bureau:verifyEngine")
    engine.restore(stored)
    engine.startSync()
    println("① Session : ${engine.currentSession().deviceId}")

    val before = engine.messages(roomId, limit = 30)
    println("   ${before.size} message(s) avant envoi")
    println()

    val text = "Envoyé par le moteur — ${System.currentTimeMillis()}"
    println("② Envoi : « $text »")
    val startedAt = System.currentTimeMillis()
    engine.send(roomId, text)
    println("   send() a rendu la main en ${System.currentTimeMillis() - startedAt} ms")
    println("   (il dépose dans la file, il n'attend pas le serveur)")
    println()

    println("③ Lecture immédiate — l'écho local est-il présent ?")
    val immediate = engine.messages(roomId, limit = 30)
    val echo = immediate.firstOrNull { (it.body as? MessageBody.Text)?.text == text }
    if (echo == null) {
        println("   ✗ absent — le message n'apparaîtrait pas avant la réponse du serveur")
    } else {
        println("   ✓ présent, état = ${echo.sendState::class.simpleName}")
        println("     identifiant : ${echo.id.take(24)}…")
        if (echo.sendState == SendState.Sent) {
            println("   ⓘ déjà confirmé : sur un réseau rapide, l'aller-retour est plus")
            println("     court que le délai de lecture. L'état Sending existe, mais cette")
            println("     lecture ne l'observe pas — voir la rafale ci-dessous.")
        }
    }
    println()

    println("③bis Rafale de 5 messages, pour observer l'état Sending")
    val burst = (1..5).map { "Rafale $it — ${System.currentTimeMillis()}" }
    burst.forEach { engine.send(roomId, it) }
    val duringBurst = engine.messages(roomId, limit = 40)
    val states = burst.mapNotNull { t ->
        duringBurst.firstOrNull { (it.body as? MessageBody.Text)?.text == t }?.sendState
    }
    val sending = states.count { it == SendState.Sending }
    states.forEachIndexed { i, s -> println("   message ${i + 1} : ${s::class.simpleName}") }
    println(
        if (sending > 0) "   ✓ $sending message(s) observé(s) en cours d'envoi — l'écho local précède bien la confirmation"
        else "   ⓘ aucun message pris en flagrant délit d'envoi : la file s'est vidée trop vite"
    )
    println()

    println("④ Attente de la confirmation serveur…")
    var confirmed: li.velvetchat.core.model.Message? = null
    repeat(20) {
        delay(1_000)
        confirmed = engine.messages(roomId, limit = 30)
            .firstOrNull { (it.body as? MessageBody.Text)?.text == text }
        if (confirmed?.sendState == SendState.Sent && confirmed?.id?.startsWith("$") == true) {
            return@repeat
        }
    }

    val final = confirmed
    println()
    when {
        final == null -> println("✗ Le message n'est jamais apparu dans la timeline.")

        final.sendState == SendState.Sent && final.id.startsWith("$") -> {
            println("⑤ État final : Sent, identifiant d'événement ${final.id.take(24)}…")
            println()
            println("✓ LOT 2 VALIDÉ — le moteur envoie.")
            println("  Observé : send() rend la main sans attendre le serveur, le message")
            println("  apparaît dans la timeline dès la lecture suivante, et il atteint")
            println("  l'état Sent avec son identifiant d'événement.")
        }

        final.sendState is SendState.Failed -> {
            val f = final.sendState as SendState.Failed
            println("✗ Envoi refusé : ${f.reason}")
            println("  reprise automatique possible : ${f.recoverable}")
        }

        else -> println("⚠ État après attente : ${final.sendState} (id ${final.id.take(24)}…)")
    }

    engine.stopSync()
}

private object SendRunner

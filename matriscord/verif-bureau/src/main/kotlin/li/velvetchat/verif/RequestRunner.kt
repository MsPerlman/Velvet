package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.VerificationStep
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File

fun main(): Unit = runBlocking {
    println("=".repeat(72))
    println("  Demandeur — éprouve l'écran récepteur du téléphone")
    println("=".repeat(72))

    val dir = File("build/engine-session")
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dir.apply { mkdirs() }.absolutePath,
        cachePath = File("build/engine-session-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()
    engine.restore(WindowsSessionStore(dir).load() ?: error("lancer d'abord verifyEngine"))
    engine.startSync()
    delay(8_000)

    println("Appareil demandeur : ${engine.currentSession().deviceId}")
    println("Envoi de la demande — le téléphone doit afficher « Demande de vérification ».")
    println()

    val decision = File("build/verification-decision")
    decision.delete()

    val fini = withTimeoutOrNull(600_000) {
        engine.verification.requestVerification().collect { pas ->
            when (pas) {
                is VerificationStep.RequestReceived ->
                    println("① Demande relayée vers ${pas.deviceDisplayName ?: pas.deviceId}")

                VerificationStep.RequestAccepted -> {
                    println("② ACCEPTÉE PAR LE TÉLÉPHONE — l'écran récepteur a fonctionné")
                    runCatching { engine.verification.startEmojiComparison() }
                }

                VerificationStep.ComparisonStarted -> println("   …")

                is VerificationStep.Comparison -> {
                    println()
                    println("─".repeat(72))
                    println("SUITE DE CET APPAREIL — à comparer avec l'écran du téléphone")
                    pas.items.forEachIndexed { i, item ->
                        println("  ${i + 1}. ${item.symbol}  ${item.description}")
                    }
                    println("─".repeat(72))
                    println("  approuver : echo ok > build/verification-decision")
                    println("  refuser   : echo no > build/verification-decision")
                    val reponse = withTimeoutOrNull(300_000) {
                        while (!decision.exists()) delay(1_000)
                        decision.readText().trim()
                    }
                    when {
                        reponse == null -> println("Aucune décision en 5 min, abandon.")
                        reponse.startsWith("ok") -> engine.verification.approve()
                        else -> engine.verification.decline()
                    }
                    decision.delete()
                }

                VerificationStep.Finished -> {
                    println("✓ VÉRIFICATION RÉUSSIE des deux côtés")
                    return@collect
                }
                VerificationStep.Cancelled -> {
                    println("✗ Annulée par le téléphone")
                    return@collect
                }
                VerificationStep.Failed -> {
                    println("✗ Échouée")
                    return@collect
                }
            }
        }
        true
    }

    if (fini == null) println("Rien reçu dans le délai.")
    delay(2_000)
    engine.stopSync()
}

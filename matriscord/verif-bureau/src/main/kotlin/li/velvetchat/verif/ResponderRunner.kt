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
    println("  Répondant de vérification — appareil déjà vérifié")
    println("=".repeat(72))
    println()

    val dir = File("build/engine-session")
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dir.apply { mkdirs() }.absolutePath,
        cachePath = File("build/engine-session-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()
    engine.restore(WindowsSessionStore(dir).load() ?: error("lancer d'abord verifyEngine"))
    engine.startSync()

    println("Appareil : ${engine.currentSession().deviceId}")
    println("État     : ${engine.verification.verificationState()}")
    println()
    println("En attente d'une demande de vérification…")
    println("(déclencher « Vérifier depuis un autre appareil » sur le téléphone)")
    println()

    val approuver = System.getProperty("velvet.approuver") != null
    val refuser = System.getProperty("velvet.refuser") != null

    val fini = withTimeoutOrNull(1_200_000) {
        engine.verification.observeVerification().collect { pas ->
            when (pas) {
                is VerificationStep.RequestReceived -> {
                    println("① Demande reçue de ${pas.deviceDisplayName ?: pas.deviceId}")
                    engine.verification.acceptIncoming()
                }

                VerificationStep.RequestAccepted -> {
                    println("② Demande acceptée, démarrage de la comparaison")
                    runCatching { engine.verification.startEmojiComparison() }
                }

                VerificationStep.ComparisonStarted -> println("   …")

                is VerificationStep.Comparison -> {
                    println()
                    println("─".repeat(72))
                    println("SUITE DE CET APPAREIL — à comparer avec l'écran du téléphone")
                    println()
                    pas.items.forEachIndexed { i, item ->
                        println("  ${i + 1}. ${item.symbol}  ${item.description}")
                    }
                    println("─".repeat(72))
                    println()
                    when {
                        refuser -> {
                            println("→ Refus demandé (-Prefuser)")
                            engine.verification.decline()
                        }
                        approuver -> {
                            println("→ Approbation demandée (-Papprouver)")
                            println("  ⚠️ N'approuver que si les deux écrans concordent VRAIMENT.")
                            engine.verification.approve()
                        }
                        else -> {
                            val decision = File("build/verification-decision")
                            decision.delete()
                            println("En attente de la décision humaine.")
                            println("  approuver : echo ok > build/verification-decision")
                            println("  refuser   : echo no > build/verification-decision")
                            val reponse = withTimeoutOrNull(300_000) {
                                while (!decision.exists()) delay(1_000)
                                decision.readText().trim()
                            }
                            when {
                                reponse == null -> println("Aucune décision en 5 min, abandon.")
                                reponse.startsWith("ok") -> {
                                    println("→ Approuvé")
                                    engine.verification.approve()
                                }
                                else -> {
                                    println("→ Refusé")
                                    engine.verification.decline()
                                }
                            }
                            decision.delete()
                        }
                    }
                }

                VerificationStep.Finished -> {
                    println("✓ VÉRIFICATION RÉUSSIE — l'autre appareil est maintenant vérifié")
                    return@collect
                }

                VerificationStep.Cancelled -> {
                    println("✗ Annulée par l'autre appareil")
                    return@collect
                }

                VerificationStep.Failed -> {
                    println("✗ Échouée — suites divergentes ou délai dépassé")
                    return@collect
                }
            }
        }
        true
    }

    if (fini == null) println("Aucune demande reçue en 5 minutes.")
    delay(2_000)
    engine.stopSync()
}

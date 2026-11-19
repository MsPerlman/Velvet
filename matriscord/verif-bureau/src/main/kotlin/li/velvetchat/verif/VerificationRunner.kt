package li.velvetchat.verif

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.ComparisonItem
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.VerificationState
import li.velvetchat.core.VerificationStep
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

fun main() = runBlocking {
    val creds = VerificationRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    println("=".repeat(72))
    println("  A-bis.2 — vérification d'appareil entre deux sessions")
    println("=".repeat(72))
    println()

    val aDir = File("build/engine-session")
    val aSession = WindowsSessionStore(aDir).load()
        ?: error("Aucune session — lancer d'abord :verif-bureau:verifyEngine")

    val a = engineAt("build/engine-session")
    a.initPlatform()
    a.restore(aSession)
    a.startSync()
    println("① Appareil A : ${a.currentSession().deviceId}  (${a.verification.verificationState()})")

    File("build/verify-b").deleteRecursively()
    val b = engineAt("build/verify-b")
    b.login(
        homeserverUrl = creds.getProperty("homeserver"),
        username = creds.getProperty("username"),
        password = creds.getProperty("password"),
    )
    b.startSync()
    println("② Appareil B : ${b.currentSession().deviceId}  (${b.verification.verificationState()})")
    println()

    val emojisA = AtomicReference<List<ComparisonItem>>(null)
    val emojisB = AtomicReference<List<ComparisonItem>>(null)
    val scope = CoroutineScope(Dispatchers.Default)

    scope.launch {
        a.verification.observeVerification().collect { step ->
            when (step) {
                is VerificationStep.RequestReceived -> {
                    println("   A ← demande de ${step.deviceId}")
                    a.verification.acceptIncoming()
                }
                is VerificationStep.Comparison -> {
                    emojisA.set(step.items)
                    println("   A : ${step.items.joinToString(" ") { it.symbol }}")
                }
                VerificationStep.Finished -> println("   A ✓ terminé")
                VerificationStep.Cancelled -> println("   A ✗ annulé")
                VerificationStep.Failed -> println("   A ✗ échec")
                else -> Unit
            }
        }
    }

    scope.launch {
        b.verification.requestVerification().collect { step ->
            when (step) {
                VerificationStep.RequestAccepted -> {
                    println("   B ← acceptée, démarrage de la comparaison")
                    b.verification.startEmojiComparison()
                }
                is VerificationStep.Comparison -> {
                    emojisB.set(step.items)
                    println("   B : ${step.items.joinToString(" ") { it.symbol }}")
                }
                VerificationStep.Finished -> println("   B ✓ terminé")
                VerificationStep.Cancelled -> println("   B ✗ annulé")
                VerificationStep.Failed -> println("   B ✗ échec")
                else -> Unit
            }
        }
    }

    println("③ Échange en cours…")

    val bothArrived = withTimeoutOrNull(60_000) {
        while (emojisA.get() == null || emojisB.get() == null) delay(250)
        true
    }

    if (bothArrived == null) {
        println()
        println("✗ Les suites d'emojis ne sont pas arrivées des deux côtés.")
        a.stopSync(); b.stopSync()
        return@runBlocking
    }

    val left = emojisA.get()
    val right = emojisB.get()
    println()
    println("④ Comparaison :")
    println("   A : ${left.joinToString(" ") { "${it.symbol} ${it.description}" }}")
    println("   B : ${right.joinToString(" ") { "${it.symbol} ${it.description}" }}")

    val identical = left.map { it.symbol } == right.map { it.symbol }
    if (!identical) {
        println()
        println("✗ LES SUITES DIFFÈRENT — refus. C'est exactement ce que la comparaison")
        println("  d'emojis sert à détecter : un tiers s'est intercalé.")
        b.verification.decline()
        a.stopSync(); b.stopSync()
        return@runBlocking
    }
    println("   ✓ suites identiques")
    println()

    println("⑤ Approbation des deux côtés…")
    a.verification.approve()
    b.verification.approve()
    delay(8_000)

    val stateA = a.verification.verificationState()
    val stateB = b.verification.verificationState()
    println("   A : $stateA")
    println("   B : $stateB")
    println()

    if (stateB == VerificationState.VERIFIED) {
        println("✓ A-bis.2 VALIDÉ — l'appareil neuf est vérifié par comparaison d'emojis.")
        println("  Plus d'avertissement de session non vérifiée, et le cross-signing")
        println("  couvre désormais les deux appareils.")
    } else {
        println("✗ L'appareil B n'est pas passé à VERIFIED (état : $stateB).")
    }

    a.stopSync()
    b.stopSync()
}

private fun engineAt(path: String): MatrixEngine = RustMatrixEngine(
    dataPath = File(path).apply { mkdirs() }.absolutePath,
    cachePath = File("$path-cache").apply { mkdirs() }.absolutePath,
    logToStdout = false,
)

private object VerificationRunner

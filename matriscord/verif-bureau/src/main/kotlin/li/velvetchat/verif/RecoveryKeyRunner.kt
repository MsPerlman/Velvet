package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.RecoveryProgress
import li.velvetchat.core.RecoveryState
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File

fun main(): Unit = runBlocking {
    println("=".repeat(72))
    println("  Clé de récupération — compte principal")
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

    delay(5_000)

    println("compte      : ${engine.currentSession().userId}")
    println("appareil    : ${engine.currentSession().deviceId}")
    println("vérification: ${engine.verification.verificationState()}")
    println("récupération: ${engine.recovery.recoveryState()}")
    println("sauvegarde  : ${engine.recovery.backupState()}")
    println()

    val reset = System.getProperty("velvet.resetRecovery") != null
    if (!reset) {
        when (engine.recovery.recoveryState()) {
            RecoveryState.ENABLED -> {
                println("Une récupération est déjà active, mais sa clé n'est récupérable nulle part —")
                println("une clé de récupération n'est affichée qu'à sa création, par conception.")
                println()
                println("Pour en obtenir une nouvelle (l'ancienne cessera de fonctionner) :")
                println("  ./gradlew :verif-bureau:recoveryKey -Preset")
            }
            else -> {
                println("Aucune récupération active. Pour en créer une :")
                println("  ./gradlew :verif-bureau:recoveryKey -Preset")
            }
        }
        engine.stopSync()
        return@runBlocking
    }

    println("Génération d'une clé…")
    val cle = try {
        if (engine.recovery.recoveryState() == RecoveryState.ENABLED) {
            engine.recovery.resetRecoveryKey()
        } else {
            engine.recovery.enableRecovery(waitForUpload = true) { p ->
                when (p) {
                    is RecoveryProgress.BackingUp -> println("   téléversement ${p.done}/${p.total}")
                    else -> println("   $p")
                }
            }
        }
    } catch (t: Throwable) {
        println()
        println("✗ Échec : ${t.cause?.message ?: t.message}")
        engine.stopSync()
        return@runBlocking
    }

    delay(6_000)

    println()
    println("état après : récupération=${engine.recovery.recoveryState()}, " +
        "sauvegarde=${engine.recovery.backupState()}")
    println()
    println("─".repeat(72))
    println("CLÉ DE RÉCUPÉRATION — à recopier sur l'appareil à vérifier")
    println()
    println("  $cle")
    println()
    println("Elle ne sera plus jamais affichée.")
    println("─".repeat(72))

    engine.stopSync()
}

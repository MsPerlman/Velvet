package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.RecoveryProgress
import li.velvetchat.core.RecoveryState
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties

fun main() = runBlocking {
    val creds = RecoveryRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val homeserver = creds.getProperty("homeserver")
    val user = creds.getProperty("username")
    val password = creds.getProperty("password")
    val roomId = creds.getProperty("testRoomId")

    println("=".repeat(72))
    println("  A-bis.1 — sauvegarde de clés et récupération après réinstallation")
    println("=".repeat(72))
    println()

    val originDir = File("build/engine-session")
    val originStore = WindowsSessionStore(originDir)
    val originSession = originStore.load()
        ?: error(
            "Aucune session dans build/engine-session — lancer d'abord " +
                "./gradlew :verif-bureau:verifyEngine"
        )

    val origin = engineAt("build/engine-session")
    origin.initPlatform()
    origin.restore(originSession)
    origin.startSync()
    println("① Appareil d'origine : ${origin.currentSession().deviceId}")
    println("   (celui de verifyEngine, qui détient déjà des clés de salon)")
    println("   état de récupération : ${origin.recovery.recoveryState()}")

    delay(4_000)
    println("   état après synchronisation : ${origin.recovery.recoveryState()}")

    println("   activation de la sauvegarde…")
    val key = try {
        origin.recovery.enableRecovery(waitForUpload = true) { p ->
            when (p) {
                is RecoveryProgress.BackingUp ->
                    println("      téléversement ${p.done}/${p.total}")
                RecoveryProgress.RoomKeyUploadError ->
                    println("      ⚠ une clé de salon n'a pas pu être téléversée")
                else -> println("      $p")
            }
        }
    } catch (t: Throwable) {
        println()
        println("✗ Activation impossible : ${t.cause?.message ?: t.message}")
        println()
        println("  Si la cause est BackupExistsOnServer, supprimer la sauvegarde côté")
        println("  serveur puis relancer :")
        println("    DELETE /_matrix/client/v3/room_keys/version/{version}")
        origin.stopSync()
        return@runBlocking
    }

    println("   ✓ sauvegarde : ${origin.recovery.backupState()}")
    println("   clé de récupération : ${key.take(14)}… (${key.length} caractères)")
    println()

    delay(5_000)
    origin.stopSync()

    val freshDir = File("build/recovery-fresh")
    freshDir.deleteRecursively()
    val fresh = engineAt("build/recovery-fresh")
    fresh.login(homeserver, user, password)
    fresh.startSync()
    println("② Appareil « fraîchement installé » : ${fresh.currentSession().deviceId}")
    println("   état de récupération : ${fresh.recovery.recoveryState()}")
    println()

    val before = fresh.messages(roomId, limit = 30)
    val readableBefore = before.count { it.body is MessageBody.Text }
    val lockedBefore = before.count { it.body is MessageBody.Undecryptable }
    println("③ AVANT récupération : $readableBefore lisible(s), $lockedBefore verrouillé(s)")
    println()

    println("④ Application de la clé de récupération…")
    fresh.recovery.recover(key)
    println("   état : ${fresh.recovery.recoveryState()} / sauvegarde ${fresh.recovery.backupState()}")
    delay(8_000)
    println()

    val after = fresh.messages(roomId, limit = 30)
    val readableAfter = after.count { it.body is MessageBody.Text }
    val lockedAfter = after.count { it.body is MessageBody.Undecryptable }
    println("⑤ APRÈS récupération : $readableAfter lisible(s), $lockedAfter verrouillé(s)")
    println()

    after.filter { it.body is MessageBody.Text }.takeLast(6).forEach {
        println("   ${it.senderDisplayName ?: it.sender} : ${(it.body as MessageBody.Text).text}")
    }
    println()

    if (readableAfter > readableBefore) {
        println("✓ A-bis.1 VALIDÉ — la clé de récupération a rendu lisible sur un appareil")
        println("  neuf un historique qui ne l'était pas ($readableBefore → $readableAfter).")
    } else {
        println("✗ Aucun gain — la sauvegarde n'a pas restitué de clés.")
        println("  Vérifier l'état de sauvegarde côté appareil d'origine avant de conclure.")
    }

    fresh.stopSync()
}

private fun engineAt(path: String): MatrixEngine = RustMatrixEngine(
    dataPath = File(path).apply { mkdirs() }.absolutePath,
    cachePath = File("$path-cache").apply { mkdirs() }.absolutePath,
    logToStdout = false,
)

private object RecoveryRunner


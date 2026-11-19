package li.velvetchat.core.rust

import li.velvetchat.core.tr

import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.EnableRecoveryProgress
import org.matrix.rustcomponents.sdk.EnableRecoveryProgressListener
import li.velvetchat.core.EngineException
import li.velvetchat.core.RecoveryEngine
import li.velvetchat.core.RecoveryProgress
import li.velvetchat.core.BackupState as CoreBackupState
import li.velvetchat.core.RecoveryState as CoreRecoveryState
import org.matrix.rustcomponents.sdk.BackupState as SdkBackupState
import org.matrix.rustcomponents.sdk.RecoveryState as SdkRecoveryState

/**
 * key backup on top of `matrix-rust-sdk`
 *
 * like the rest of the engine, this file compiles for both targets from one source
 *
 * @param clientProvider the current client, supplied lazily because recovery is configured after
 *   the session opens and not when the engine is built
 */
internal class RustRecoveryEngine(
    private val clientProvider: () -> Client,
) : RecoveryEngine {

    override fun recoveryState(): CoreRecoveryState =
        when (encryption().recoveryState()) {
            SdkRecoveryState.ENABLED -> CoreRecoveryState.ENABLED
            SdkRecoveryState.DISABLED -> CoreRecoveryState.DISABLED
            SdkRecoveryState.INCOMPLETE -> CoreRecoveryState.INCOMPLETE
            SdkRecoveryState.UNKNOWN -> CoreRecoveryState.UNKNOWN
        }

    override fun backupState(): CoreBackupState =
        when (encryption().backupState()) {
            SdkBackupState.CREATING -> CoreBackupState.CREATING
            SdkBackupState.ENABLING -> CoreBackupState.ENABLING
            SdkBackupState.RESUMING -> CoreBackupState.RESUMING
            SdkBackupState.ENABLED -> CoreBackupState.ENABLED
            SdkBackupState.DOWNLOADING -> CoreBackupState.DOWNLOADING
            SdkBackupState.DISABLING -> CoreBackupState.DISABLING
            SdkBackupState.UNKNOWN -> CoreBackupState.UNKNOWN
        }

    override suspend fun enableRecovery(
        waitForUpload: Boolean,
        onProgress: (RecoveryProgress) -> Unit,
    ): String = try {
        encryption().enableRecovery(
            waitForBackupsToUpload = waitForUpload,
            // no passphrase. it offers a second recovery path, but a phrase someone chose is
            // also a phrase someone can guess. the key alone is safer, at the price of a ui that
            // has to insist it gets kept somewhere reliable
            passphrase = null,
            progressListener = object : EnableRecoveryProgressListener {
                override fun onUpdate(status: EnableRecoveryProgress) {
                    onProgress(status.toCore())
                }
            },
        )
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Activation de la sauvegarde impossible", "Could not turn on the backup"), t)
    }

    override suspend fun recover(recoveryKey: String) {
        try {
            encryption().recover(recoveryKey.trim())
        } catch (t: Throwable) {
            // by far the most common cause is a mistyped key. say so rather than let it look
            // like a fault
            throw EngineException.AuthFailed(tr("Clé de récupération refusée", "Recovery key refused"), t)
        }
    }

    override suspend fun resetRecoveryKey(): String = try {
        encryption().resetRecoveryKey()
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Réinitialisation de la clé impossible", "Could not reset the key"), t)
    }

    override suspend fun disableRecovery() {
        try {
            encryption().disableRecovery()
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Désactivation de la sauvegarde impossible", "Could not turn off the backup"), t)
        }
    }

    private fun encryption() = clientProvider().encryption()

    private fun EnableRecoveryProgress.toCore(): RecoveryProgress = when (this) {
        EnableRecoveryProgress.Starting -> RecoveryProgress.Starting
        EnableRecoveryProgress.CreatingBackup -> RecoveryProgress.CreatingBackup
        EnableRecoveryProgress.CreatingRecoveryKey -> RecoveryProgress.CreatingRecoveryKey
        is EnableRecoveryProgress.BackingUp ->
            RecoveryProgress.BackingUp(backedUpCount.toInt(), totalCount.toInt())
        EnableRecoveryProgress.RoomKeyUploadError -> RecoveryProgress.RoomKeyUploadError
        is EnableRecoveryProgress.Done -> RecoveryProgress.Done
    }
}

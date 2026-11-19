package li.velvetchat.core

/**
 * key backup and recovery
 *
 * without it, encryption produces exactly the experience people hold against matrix:
 * reinstalling the app loses the entire history for good, and every new device arrives blind to
 * past conversations
 *
 * how it works: room keys are backed up to the homeserver encrypted, and the only thing that
 * opens them again is a recovery key the server never sees. so it has to be shown to the person
 * once, at the right moment, and they have to understand it cannot be replaced. one of the few
 * places in this product where the ux carries a real consequence, since losing it means losing
 * the history
 */
interface RecoveryEngine {

    fun recoveryState(): RecoveryState

    fun backupState(): BackupState

    /**
     * turns backup on and returns the recovery key, to be shown exactly once. it is not
     * retrievable afterwards, the server never had it
     *
     * @param waitForUpload wait for existing keys to finish uploading. slower, but it
     *   guarantees the history already on this device is backed up. preferable when someone
     *   turned this on by hand
     * @param onProgress progress reporting. the upload can take a while on an old account, and
     *   a silent ui during that looks like a freeze
     */
    suspend fun enableRecovery(
        waitForUpload: Boolean = true,
        onProgress: (RecoveryProgress) -> Unit = {},
    ): String

    /**
     * restores the keys from the recovery key. this is the gesture that makes the history
     * readable on a new device
     */
    suspend fun recover(recoveryKey: String)

    /**
     * generates a new key and invalidates the old one, for someone who lost theirs. history
     * already backed up stays reachable from this device, which still holds the keys
     */
    suspend fun resetRecoveryKey(): String

    /**
     * turns backup off and deletes the one on the server
     *
     * destructive. any history whose keys only live in that backup becomes permanently
     * unreadable. only offer it with an explicit warning, or to get out of an inconsistent
     * state, meaning a backup sitting on the server that no device can open, in which case
     * `enableRecovery` fails on `BackupExistsOnServer` and this is the only way out
     */
    suspend fun disableRecovery()
}

enum class RecoveryState {
    UNKNOWN,
    ENABLED,
    DISABLED,

    /**
     * a backup exists but this device cannot reach it, so the recovery key has to be typed in.
     * this is the state of a freshly installed device, and the ui should invite recovery rather
     * than leave someone staring at a silent history
     */
    INCOMPLETE,
}

enum class BackupState {
    UNKNOWN, CREATING, ENABLING, RESUMING, ENABLED, DOWNLOADING, DISABLING,
}

sealed interface RecoveryProgress {
    data object Starting : RecoveryProgress
    data object CreatingBackup : RecoveryProgress
    data object CreatingRecoveryKey : RecoveryProgress
    data class BackingUp(val done: Int, val total: Int) : RecoveryProgress

    /**
     * a room key failed to upload. not fatal, the backup carries on, but not something to keep
     * quiet either: that history will not be recoverable
     */
    data object RoomKeyUploadError : RecoveryProgress
    data object Done : RecoveryProgress
}

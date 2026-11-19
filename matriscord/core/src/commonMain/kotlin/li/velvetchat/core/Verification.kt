package li.velvetchat.core

import kotlinx.coroutines.flow.Flow

/**
 * device verification and cross signing
 *
 * without it every device is an isolated identity: other clients show permanent unverified
 * session warnings, and nothing tells a legitimate device apart from an intruder who got hold of
 * the password
 *
 * two paths lead to the same result and the ui has to offer both. the recovery key, where the
 * device pulls the cross signing secrets out of the backup and signs itself, which needs no
 * second device and is therefore the only path available when you only have one. or interactive
 * verification between two devices, comparing a run of emoji, which is what this interface
 * describes
 *
 * the emoji comparison is not decoration, it defends against someone in the middle. both devices
 * derive the same run from a shared secret and a third party cannot produce the same one. hence
 * the design rule: never prefill, never pre approve. the person has to compare for themselves,
 * on both screens
 */
interface VerificationEngine {

    fun verificationState(): VerificationState

    /**
     * asks the account's other devices to verify this one. the flow emits each step until
     * [VerificationStep.Finished] or a failure
     */
    suspend fun requestVerification(): Flow<VerificationStep>

    /**
     * watches the steps without sending a request, which is what the receiving device needs.
     * wire it up as soon as the session opens: a request can arrive from another device at any
     * moment, and with no observer it would go unnoticed
     */
    suspend fun observeVerification(): Flow<VerificationStep>

    /** accepts an incoming request, on the already verified device */
    suspend fun acceptIncoming()

    /** starts the emoji comparison, once both sides have accepted */
    suspend fun startEmojiComparison()

    /** only call this if the person confirms both screens match */
    suspend fun approve()

    /** the runs differ, so refuse. this is the case that actually protects anything */
    suspend fun decline()

    suspend fun cancel()

    /** is there another device that could vouch for this one */
    suspend fun aUnAppareilTemoin(): Boolean

    /**
     * makes this device the account's reference, the only path open to someone who just
     * registered
     *
     * both usual paths assume something already exists: another device to ask, or a backup to
     * unlock. a fresh account has neither, so the verification screen showed it two closed
     * doors, a dead end you only escaped by giving up on the app
     *
     * this is not harmless on an older account. it replaces the account's signing identity,
     * which forces contacts to verify again, and abandons whatever history only the old identity
     * could unlock. hence the display rule: only expose it when there genuinely is nothing else,
     * no witness device and no backup
     *
     * @param motDePasse the account password. the server reauthenticates before letting anyone
     *   replace signing keys, and thank goodness it does
     */
    suspend fun etablirIdentite(motDePasse: String)
}

enum class VerificationState { UNKNOWN, VERIFIED, UNVERIFIED }

/** the steps of an interactive verification, in the order they happen */
sealed interface VerificationStep {

    /** a request arrived from another device */
    data class RequestReceived(
        val deviceId: String,
        val deviceDisplayName: String?,
    ) : VerificationStep

    data object RequestAccepted : VerificationStep

    data object ComparisonStarted : VerificationStep

    /**
     * what to compare. seven emoji, or decimals depending on what the other device can draw
     */
    data class Comparison(val items: List<ComparisonItem>) : VerificationStep

    data object Finished : VerificationStep

    data object Cancelled : VerificationStep

    data object Failed : VerificationStep
}

/**
 * @param symbol the emoji itself, or the number in decimal mode
 * @param description its name, which is not optional: two emoji can look alike on screen, and
 *   the name is what resolves it when the two devices do not have the same font
 */
data class ComparisonItem(
    val symbol: String,
    val description: String,
)

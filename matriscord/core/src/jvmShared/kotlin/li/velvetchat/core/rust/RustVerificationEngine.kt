package li.velvetchat.core.rust

import li.velvetchat.core.tr

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.matrix.rustcomponents.sdk.AuthData
import org.matrix.rustcomponents.sdk.AuthDataPasswordDetails
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.CrossSigningResetAuthType
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationControllerDelegate
import org.matrix.rustcomponents.sdk.SessionVerificationData
import org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails
import li.velvetchat.core.ComparisonItem
import li.velvetchat.core.EngineException
import li.velvetchat.core.VerificationEngine
import li.velvetchat.core.VerificationStep
import li.velvetchat.core.VerificationState as CoreVerificationState
import org.matrix.rustcomponents.sdk.VerificationState as SdkVerificationState

/**
 * device verification on top of `matrix-rust-sdk`
 *
 * the sdk exposes a controller with a delegate, and we turn it into a flow, which suits a ui that
 * has to react to every step far better than hand rolled subscriptions
 */
internal class RustVerificationEngine(
    private val clientProvider: () -> Client,
) : VerificationEngine {

    private var controller: SessionVerificationController? = null

    /** the incoming request's ids, needed to acknowledge it */
    private val pending = java.util.concurrent.atomic.AtomicReference<Pair<String, String>?>(null)

    // `replay = 1` so an observer subscribing just after a step does not miss it. the steps come
    // in on an sdk thread, with nothing synchronising them with the ui
    private val steps = MutableSharedFlow<VerificationStep>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun verificationState(): CoreVerificationState =
        when (clientProvider().encryption().verificationState()) {
            SdkVerificationState.VERIFIED -> CoreVerificationState.VERIFIED
            SdkVerificationState.UNVERIFIED -> CoreVerificationState.UNVERIFIED
            SdkVerificationState.UNKNOWN -> CoreVerificationState.UNKNOWN
        }

    override suspend fun requestVerification(): Flow<VerificationStep> {
        ensureController().requestDeviceVerification()
        return steps.asSharedFlow()
    }

    override suspend fun observeVerification(): Flow<VerificationStep> {
        ensureController()
        return steps.asSharedFlow()
    }

    override suspend fun acceptIncoming() = guard {
        val c = ensureController()
        // mandatory step, and easy to miss. the request has to be acknowledged first, otherwise
        // `acceptVerificationRequest` does nothing visible and the exchange stops in silence, with
        // the asking device waiting forever
        pending.get()?.let { (senderId, flowId) ->
            c.acknowledgeVerificationRequest(senderId, flowId)
        }
        c.acceptVerificationRequest()
    }

    override suspend fun startEmojiComparison() = guard {
        ensureController().startSasVerification()
    }

    override suspend fun approve() = guard {
        ensureController().approveVerification()
    }

    override suspend fun decline() = guard {
        ensureController().declineVerification()
    }

    override suspend fun cancel() = guard {
        ensureController().cancelVerification()
    }

    /**
     * when in doubt, answer yes
     *
     * this boolean only decides whether to offer replacing the identity, which is a heavy move.
     * answering no wrongly would offer it while a normal path existed. answering yes wrongly only
     * hides it and leaves the screen as it was. the harmless mistake is the second one
     */
    override suspend fun aUnAppareilTemoin(): Boolean = try {
        clientProvider().encryption().hasDevicesToVerifyAgainst()
    } catch (t: Throwable) {
        true
    }

    override suspend fun etablirIdentite(motDePasse: String) {
        val client = clientProvider()
        val handle = try {
            client.encryption().resetIdentity()
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Impossible d'établir l'identité de ce compte", "Could not set up this account's identity"), t)
        } ?: throw EngineException.Unexpected(
            tr("Ce compte n'a pas d'identité à établir depuis cet appareil", "This account has no identity to set up from this device"),
        )

        try {
            // the server reauthenticates before letting anyone replace signing keys. the auth
            // type is asked of the sdk rather than assumed, since an oauth server will not settle
            // for a password, and saying so beats sending proof it will refuse
            val auth = when (val type = handle.authType()) {
                is CrossSigningResetAuthType.Uiaa -> AuthData.Password(
                    AuthDataPasswordDetails(
                        identifier = client.userId(),
                        password = motDePasse,
                    )
                )

                else -> throw EngineException.Unexpected(
                    tr("Ce serveur demande une authentification que Velvet ne sait pas fournir ", "This server asks for an authentication Velvet cannot provide ") +
                        "(${type::class.simpleName}).",
                )
            }
            handle.reset(auth)
        } catch (t: EngineException) {
            runCatching { handle.cancel() }
            throw t
        } catch (t: Throwable) {
            runCatching { handle.cancel() }
            throw EngineException.Unexpected(tr("Mot de passe refusé, ou identité non remplacée", "Password refused, or identity not replaced"), t)
        }
    }

    private suspend fun ensureController(): SessionVerificationController {
        controller?.let { return it }
        val c = try {
            clientProvider().getSessionVerificationController()
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Contrôleur de vérification indisponible", "Verification controller unavailable"), t)
        }
        c.setDelegate(Delegate())
        controller = c
        return c
    }

    private inline fun guard(block: () -> Unit) = try {
        block()
    } catch (t: Throwable) {
        throw EngineException.Unexpected(tr("Étape de vérification impossible", "Verification step failed"), t)
    }

    private inner class Delegate : SessionVerificationControllerDelegate {

        override fun didReceiveVerificationRequest(details: SessionVerificationRequestDetails) {
            pending.set(details.senderProfile.userId to details.flowId)
            steps.tryEmit(
                VerificationStep.RequestReceived(
                    deviceId = details.deviceId,
                    deviceDisplayName = details.deviceDisplayName,
                )
            )
        }

        override fun didAcceptVerificationRequest() {
            steps.tryEmit(VerificationStep.RequestAccepted)
        }

        override fun didStartSasVerification() {
            steps.tryEmit(VerificationStep.ComparisonStarted)
        }

        override fun didReceiveVerificationData(data: SessionVerificationData) {
            val items = when (data) {
                is SessionVerificationData.Emojis -> data.emojis.map {
                    ComparisonItem(symbol = it.symbol(), description = it.description())
                }
                // the fallback for a device that cannot show emojis. the name stands for itself
                // here, since the number is the thing being compared
                is SessionVerificationData.Decimals -> data.values.map {
                    ComparisonItem(symbol = it.toString(), description = it.toString())
                }
            }
            steps.tryEmit(VerificationStep.Comparison(items))
        }

        override fun didFail() {
            steps.tryEmit(VerificationStep.Failed)
        }

        override fun didCancel() {
            steps.tryEmit(VerificationStep.Cancelled)
        }

        override fun didFinish() {
            steps.tryEmit(VerificationStep.Finished)
        }
    }
}


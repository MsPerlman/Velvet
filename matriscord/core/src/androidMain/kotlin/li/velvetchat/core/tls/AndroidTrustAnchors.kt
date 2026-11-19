package li.velvetchat.core.tls

import android.util.Log
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * the android system store's trust anchors, in der
 *
 * handed to the engine so it validates tls with rustls' webpki validator rather than the
 * platform's verifier
 *
 * why that is necessary. `rustls-platform-verifier` delegates to `CertPathValidator` with
 * revocation checking, and its kotlin code reports every `CertPathValidatorException` as revoked:
 *
 * ```kotlin
 * catch (e: CertPathValidatorException) {
 *     return VerificationResult(StatusCode.Revoked, e.toString())
 * }
 * ```
 *
 * the real reason, seen on a physical device, is `Certificate does not specify OCSP responder`,
 * let's encrypt having stopped publishing ocsp responders. so the "revoked" label is wrong twice
 * over: the certificate is not revoked, and the failure is not even about revocation
 *
 * why this is not a weakening. we reuse exactly the platform's own anchors, user and enterprise
 * added authorities included. chain, hostname and expiry are all still checked. the only thing
 * that goes is the revocation step, which android does not do in its default tls validation
 * anyway, the one that accepts this same certificate without blinking
 */
object AndroidTrustAnchors {

    /**
     * @return the system root certificates in der, or an empty list when the store is unreadable,
     *   in which case the engine falls back on the platform verifier, which is the safest default
     */
    fun load(): List<ByteArray> = runCatching {
        val store = KeyStore.getInstance(STORE).apply { load(null) }
        store.aliases().toList().mapNotNull { alias ->
            (store.getCertificate(alias) as? X509Certificate)?.encoded
        }
    }.onFailure {
        Log.w(TAG, "Magasin de confiance système illisible, repli sur le vérificateur plateforme", it)
    }.getOrDefault(emptyList())

    private const val STORE = "AndroidCAStore"
    private const val TAG = "VelvetTls"
}

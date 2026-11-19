package li.velvetchat.banc

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.HttpPusherData
import org.matrix.rustcomponents.sdk.PushFormat
import org.matrix.rustcomponents.sdk.PusherIdentifiers
import org.matrix.rustcomponents.sdk.PusherKind
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.AndroidSessionStore
import java.io.File

class S05Spike(private val context: Context) {

    suspend fun configure(emit: (String) -> Unit) {
        val log: (String) -> Unit = { Log.i(TAG, it); emit(it) }

        log("S0.5 — configuration du push chiffré")
        log("")

        val distributors = UnifiedPush.distributors(context)
        if (distributors.isEmpty()) {
            log("✗ Aucun distributeur UnifiedPush installé.")
            log("  Installer l'app ntfy et la pointer sur la passerelle du foyer.")
            return
        }
        val distributor = distributors.first()
        log("① Distributeur : $distributor")

        UnifiedPush.unregister(context, distributor)
        S05State.clearEndpoint(context)
        delay(2_000)
        UnifiedPush.register(context, distributor)

        var endpoint: String? = null
        repeat(30) {
            endpoint = S05State.endpoint(context)
            if (endpoint != null) return@repeat
            delay(1_000)
        }
        val ep = endpoint ?: run {
            log("✗ Pas d'endpoint après 30 s — le distributeur n'a pas répondu.")
            log("  Vérifier que l'app ntfy est lancée au moins une fois et")
            log("  configurée sur la bonne URL de serveur.")
            return
        }
        log("② Endpoint : $ep")

        val engine: MatrixEngine = RustMatrixEngine(
            dataPath = File(context.filesDir, "engine").apply { mkdirs() }.absolutePath,
            cachePath = File(context.cacheDir, "engine").apply { mkdirs() }.absolutePath,
            insecureTlsForDevelopment = BuildConfig.INSECURE_TLS,
            trustAnchors = li.velvetchat.core.tls.AndroidTrustAnchors.load(),
        )
        engine.initPlatform()
        engine.restore(
            AndroidSessionStore(context).load() ?: run {
                log("✗ Aucune session dans le moteur — lance d'abord EngineActivity.")
                return
            }
        )

        try {
            engine.notifications.registerPush(
                endpoint = ep,
                gatewayUrl = BuildConfig.PUSH_GATEWAY,
                appId = APP_ID,
                deviceName = android.os.Build.MODEL ?: "Android",
                trustedEndpointHosts = setOf(BuildConfig.PUSH_ENDPOINT_HOST),
            )
        } catch (t: Throwable) {
            log("✗ Pusher refusé : ${t.message}")
            return
        }

        log("③ Pusher enregistré auprès de Synapse")
        log("   passerelle : ${BuildConfig.PUSH_GATEWAY} (interne, hors tunnel)")
        log("   endpoint autorisé : ${BuildConfig.PUSH_ENDPOINT_HOST}")
        log("   format     : EVENT_ID_ONLY")
        log("")
        log("✓ Chaîne câblée. Envoie maintenant un message chiffré depuis")
        log("  l'autre compte, application FERMÉE, et surveille :")
        log("      adb logcat -s $TAG")
    }

    private companion object {
        const val TAG = UnifiedPush.TAG
        const val APP_ID = "li.velvetchat.banc"
    }
}

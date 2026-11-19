package li.velvetchat.banc

import android.content.Context
import android.util.Log
import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.NotificationResult
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.AndroidSessionStore
import java.io.File
import org.matrix.rustcomponents.sdk.NotificationProcessSetup
import org.matrix.rustcomponents.sdk.NotificationStatus
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder

object NotificationDecryptWorker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueue(context: Context, rawPayload: String, pending: BroadcastReceiver.PendingResult?) {
        val app = context.applicationContext
        scope.launch {
            try {
                decrypt(app, rawPayload)
            } finally {
                pending?.finish()
            }
        }
    }

    private suspend fun decrypt(context: Context, rawPayload: String) {
        val log: (String) -> Unit = { Log.i(UnifiedPush.TAG, it) }

        val (roomId, eventId) = parse(rawPayload) ?: run {
            log("   ✗ charge utile illisible : $rawPayload")
            return
        }
        log("   room=$roomId event=$eventId")

        val engine: MatrixEngine = RustMatrixEngine(
            dataPath = File(context.filesDir, "engine").apply { mkdirs() }.absolutePath,
            cachePath = File(context.cacheDir, "engine").apply { mkdirs() }.absolutePath,
            insecureTlsForDevelopment = BuildConfig.INSECURE_TLS,
            trustAnchors = li.velvetchat.core.tls.AndroidTrustAnchors.load(),
        )
        val stored = AndroidSessionStore(context).load() ?: run {
            log("   ✗ aucune session dans le moteur — lancer EngineActivity d'abord")
            return
        }

        try {
            engine.initPlatform()

            var derniere: Throwable? = null
            var pret = false
            repeat(3) { essai ->
                if (!pret) {
                    try {
                        engine.restore(stored)
                        pret = true
                    } catch (t: Throwable) {
                        derniere = t
                        log("   … réseau pas prêt (essai ${essai + 1}/3)")
                        kotlinx.coroutines.delay(2_000L * (essai + 1))
                    }
                }
            }
            if (!pret) throw derniere ?: IllegalStateException("session non reprise")

            when (val r = engine.notifications.resolve(roomId, eventId)) {
                is NotificationResult.Show -> {
                    val n = r.notification
                    val texte = (n.body as? MessageBody.Text)?.text
                    log("")
                    log("   ✓✓✓ ÉVÉNEMENT DÉCHIFFRÉ ✓✓✓")
                    log("   salon      : ${n.channelName}")
                    log("   expéditeur : ${n.displayName}")
                    log("   contenu    : ${texte ?: "(${n.body::class.simpleName})"}")
                    log("")
                    log("   ★ Le contenu d'un message CHIFFRÉ a été reconstitué en")
                    log("     arrière-plan, à partir du seul identifiant d'événement,")
                    log("     par le MOTEUR partagé — pas par du code propre au spike.")
                }
                NotificationResult.NotFound -> log("   ✗ événement introuvable côté serveur")
                NotificationResult.Filtered -> log("   ⓘ filtré par les règles de push")
                NotificationResult.Redacted -> log("   ⓘ événement supprimé entre-temps")
            }
        } catch (t: Throwable) {
            log("   ✗ déchiffrement impossible : ${t.message}")
            log("     cause : ${t.cause?.message}")
            log("     ancres TLS : ${li.velvetchat.core.tls.AndroidTrustAnchors.load().size}")
            Log.e(UnifiedPush.TAG, "échec du worker", t)
        }
    }

    private fun parse(raw: String): Pair<String, String>? = runCatching {
        val n = JSONObject(raw).optJSONObject("notification") ?: JSONObject(raw)
        val room = n.getString("room_id")
        val event = n.getString("event_id")
        room to event
    }.getOrNull()

}

package li.velvetchat.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import li.velvetchat.app.ui.horsUi

/**
 * dictation: the mic goes live to the nas, and the text comes back as you speak
 *
 * the app never talks to the whisper server: it talks to the nas's dictation gateway
 * (`/mnt/ia/dictee/passerelle.py`), which checks the matrix token with synapse before accepting a
 * single byte, then glues whisper's passages together as you speak. see `TODO-DESK.md`, batch B,
 * for the measurements that settled this behaviour
 *
 * the protocol, in order: 1. `{"type":"debut","jeton":…}`, the gateway answers `pret` (or `erreur`
 * then closes) 2. the mic starts only at that moment: no audio recorded for a refused connection 3.
 * binary frames of 100 ms, pcm 16 khz mono 16 bit, the format whisper expects, with no conversion
 * on
 *     the server side
 * 4. `partiel` every second (validated text plus a still provisional tail), then after `fin`,
 *     `finalisation` (the whole audio goes back through in full context, 1.5 to 5 s) and `final`, which
 *     replaces all the live text
 *
 * `VOICE_RECOGNITION` rather than `MIC`: it is the source the system tunes for speech recognition
 * (no aggressive automatic gain), the one giving whisper the cleanest signal
 *
 * the callbacks arrive on the main thread: the caller changes the compose state directly
 */
class Dictee(
    private val url: String,
    private val jeton: String,
    /**
     * the text already validated, and the still provisional tail that can change on the next pass
     */
    private val onTexte: (valide: String, provisoire: String) -> Unit,
    /**
     * mic off, the nas listening back over the whole thing: the definitive text arrives with
     * `onFin`
     */
    private val onFinalisation: () -> Unit,
    private val onFin: (texte: String) -> Unit,
    private val onErreur: (message: String) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private val principal = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null

    @Volatile
    private var micro = false

    /**
     * paused: the mic is still running, and its frames no longer go out
     *
     * stopping the recorder would be cheaper and far more fragile, reopening it wants the
     * permission, the device may have been taken meanwhile, and the resume would fail at the worst
     * moment. what we want from a pause is that what is said meanwhile is not transcribed, and that
     * is exactly what this flag does
     */
    @Volatile
    var enPause = false

    /** opens the connection. the mic only starts after the gateway agrees */
    fun demarrer() {
        val requete = Request.Builder().url(url).build()
        ws = client.newWebSocket(requete, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "debut").put("jeton", jeton).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val reponse = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (reponse.optString("type")) {
                    "pret" -> lancerMicro(webSocket)
                    "partiel" -> principal.post {
                        onTexte(reponse.optString("valide"), reponse.optString("provisoire"))
                    }
                    "finalisation" -> principal.post { onFinalisation() }
                    "final" -> {
                        principal.post { onFin(reponse.optString("texte")) }
                        webSocket.close(1000, null)
                    }
                    "erreur" -> {
                        micro = false
                        principal.post { onErreur(reponse.optString("message", horsUi.dicteeRefusee)) }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                micro = false
                // say what failed, and not "it did not work"
                //
                // the gateway goes through the cloudflare tunnel (`dictee.exemple.org`), so it is
                // reachable everywhere the internet is, except that dictation not working outside
                // the local network was reported, and the message of the time did not let you tell
                // whether it was the dns, the certificate, the tunnel or the token. it says so now:
                // the dictation player shows this line as is
                val cause = listOfNotNull(
                    response?.code?.let { "HTTP $it" },
                    t::class.simpleName,
                    t.message?.take(120),
                ).joinToString(" · ")
                principal.post { onErreur(horsUi.dicteeInjoignableCause(cause)) }
            }
        })
    }

    /** stops the mic and asks for the final text. `onFin` will follow */
    fun terminer() {
        micro = false
        ws?.send(JSONObject().put("type", "fin").toString())
    }

    /** abandon (the screen was left): we cut without waiting for text */
    fun annuler() {
        micro = false
        ws?.cancel()
    }

    // the RECORD_AUDIO permission is checked by the caller before `demarrer()`
    @SuppressLint("MissingPermission")
    private fun lancerMicro(webSocket: WebSocket) {
        val taille = maxOf(
            AudioRecord.getMinBufferSize(TAUX, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            TRAME,
        ) * 2
        val enregistreur = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            TAUX,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            taille,
        )
        if (enregistreur.state != AudioRecord.STATE_INITIALIZED) {
            enregistreur.release()
            principal.post { onErreur(horsUi.microIndisponible) }
            webSocket.close(1000, null)
            return
        }
        micro = true
        thread(name = "dictee-micro") {
            val tampon = ByteArray(TRAME)
            try {
                enregistreur.startRecording()
                while (micro) {
                    val lus = enregistreur.read(tampon, 0, tampon.size)
                    if (lus > 0 && !enPause) webSocket.send(tampon.toByteString(0, lus))
                }
            } finally {
                runCatching { enregistreur.stop() }
                enregistreur.release()
            }
        }
    }

    companion object {
        private val clientVocal = OkHttpClient()

        /**
         * the text of a voice message already sent, to read it without listening. same gateway,
         * same token check: `transcrire`, then the whole file in one frame, and the nas runs it in
         * full context. an empty string means no speech detected
         */
        suspend fun transcrireVocal(url: String, jeton: String, fichier: java.io.File): String =
            suspendCancellableCoroutine { suite ->
                val ws = clientVocal.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(JSONObject().put("type", "transcrire").put("jeton", jeton).toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val reponse = runCatching { JSONObject(text) }.getOrNull() ?: return
                        when (reponse.optString("type")) {
                            "pret" -> webSocket.send(fichier.readBytes().toByteString())
                            "final" -> {
                                webSocket.close(1000, null)
                                if (suite.isActive) suite.resume(reponse.optString("texte"))
                            }
                            "erreur" -> if (suite.isActive) {
                                suite.resumeWithException(IllegalStateException(reponse.optString("message")))
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (suite.isActive) suite.resumeWithException(t)
                    }
                })
                suite.invokeOnCancellation { ws.cancel() }
            }

        private const val TAUX = 16_000
        /** 100 ms of 16 bit mono pcm at 16 khz */
        private const val TRAME = 3_200
    }
}

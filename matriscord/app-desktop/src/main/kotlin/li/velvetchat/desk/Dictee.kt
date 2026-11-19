package li.velvetchat.desk

import li.velvetchat.core.tr

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import li.velvetchat.app.ui.horsUi

/**
 * the nas dictation gateway, desktop side, the counterpart of the phone's `Dictee.kt`, same
 * protocol (see `nas/dictee/passerelle.py`): the first message is the type plus the matrix token,
 * checked against synapse before any byte
 *
 * a jdk websocket client (`java.net.http`) and a `javax.sound` microphone: nothing to add
 */
/** the service's address, specific to the installation, see `prive.properties` */
private val DICTEE_URL get() = li.velvetchat.core.Installation.ou({ this.dictee }, Prive.DICTEE)

private fun JsonObject.champ(nom: String) = this[nom]?.jsonPrimitive?.contentOrNull.orEmpty()

/**
 * opens the gateway. `recevoir` sees every text message (type, content), on the network thread
 */
private fun ouvrir(
    type: String,
    jeton: String,
    recevoir: (WebSocket, String, JsonObject) -> Unit,
    echec: (Throwable) -> Unit,
): CompletionStage<WebSocket> {
    val recu = StringBuilder()
    return HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI(DICTEE_URL), object : WebSocket.Listener {
        override fun onOpen(ws: WebSocket) {
            ws.sendText("""{"type":"$type","jeton":${JsonPrimitive(jeton)}}""", true)
            ws.request(1)
        }

        override fun onText(ws: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            recu.append(data)
            if (last) {
                val message = runCatching { Json.parseToJsonElement(recu.toString()).jsonObject }.getOrNull()
                recu.setLength(0)
                if (message != null) recevoir(ws, message.champ("type"), message)
            }
            ws.request(1)
            return null
        }

        override fun onError(ws: WebSocket, error: Throwable) = echec(error)

        override fun onClose(ws: WebSocket, code: Int, raison: String): CompletionStage<*>? {
            echec(IllegalStateException(tr("passerelle fermée ($code)", "gateway closed ($code)")))
            return null
        }
    }).also { it.exceptionally { e -> echec(e); null } }
}

/** the text of a voice message already sent: the whole file goes out in one frame */
suspend fun transcrireVocal(jeton: String, octets: ByteArray): String {
    val resultat = CompletableDeferred<String>()
    val ws = ouvrir("transcrire", jeton, { ws, type, m ->
        when (type) {
            "pret" -> ws.sendBinary(ByteBuffer.wrap(octets), true)
            "final" -> { resultat.complete(m.champ("texte")); ws.sendClose(WebSocket.NORMAL_CLOSURE, "") }
            "erreur" -> resultat.completeExceptionally(IllegalStateException(m.champ("message")))
        }
    }, { resultat.completeExceptionally(it) }).await()
    return try {
        resultat.await()
    } finally {
        ws.abort()
    }
}

/**
 * a live dictation. the mic only opens after `pret`, in 100 ms frames of pcm 16 kHz mono 16 bit,
 * whisper's format. the callbacks arrive on swing's thread, so on compose's
 */
class DicteeBureau(
    private val jeton: String,
    private val onTexte: (String) -> Unit,
    private val onFinalisation: () -> Unit,
    private val onFin: (String) -> Unit,
    private val onErreur: (String) -> Unit,
) {
    @Volatile private var micro = false
    @Volatile private var ws: WebSocket? = null
    @Volatile private var close = false

    fun demarrer() {
        ouvrir("debut", jeton, { socket, type, m ->
            ws = socket
            when (type) {
                "pret" -> lancerMicro(socket)
                "partiel" -> surFil {
                    onTexte(listOf(m.champ("valide"), m.champ("provisoire")).filter { it.isNotBlank() }.joinToString(" "))
                }
                "finalisation" -> surFil(onFinalisation)
                "final" -> { close = true; surFil { onFin(m.champ("texte")) }; socket.sendClose(WebSocket.NORMAL_CLOSURE, "") }
                "erreur" -> { micro = false; close = true; surFil { onErreur(m.champ("message").ifBlank { horsUi.dicteeRefusee }) } }
            }
        }, {
            micro = false
            // a close after `final` or `erreur` is expected, and the others mean the network or the
            // nas
            if (!close) { close = true; surFil { onErreur(horsUi.dicteeInjoignable) } }
        })
    }

    /** cuts the mic and asks for the final text. `onFin` will follow */
    fun terminer() {
        micro = false
        ws?.sendText("""{"type":"fin"}""", true)
    }

    fun annuler() {
        micro = false
        close = true
        ws?.abort()
    }

    private fun lancerMicro(socket: WebSocket) {
        val format = AudioFormat(16_000f, 16, 1, true, false)
        val ligne = runCatching { AudioSystem.getTargetDataLine(format).apply { open(format, 3_200 * 4) } }.getOrElse {
            close = true
            surFil { onErreur(horsUi.microIndisponible) }
            socket.abort()
            return
        }
        micro = true
        thread(name = "dictee-micro", isDaemon = true) {
            val tampon = ByteArray(3_200)
            try {
                ligne.start()
                while (micro) {
                    val lus = ligne.read(tampon, 0, tampon.size)
                    // `join()`: one frame at a time, as the jdk's websocket api requires
                    if (lus > 0) socket.sendBinary(ByteBuffer.wrap(tampon.copyOf(lus)), true).join()
                }
            } catch (_: Exception) {
                // connection dropped mid send: `echec` has already said so
            } finally {
                ligne.stop()
                ligne.close()
            }
        }
    }

    private fun surFil(action: () -> Unit) = SwingUtilities.invokeLater(action)
}

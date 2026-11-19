package li.velvetchat.banc

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.ConnectionState
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.NotificationResult
import li.velvetchat.core.model.MediaKind
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.AndroidSessionStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.random.Random

class CampaignActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var output: TextView
    private val results = mutableListOf<Triple<String, String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTextIsSelectable(true)
            setPadding(16, 16, 16, 16)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@CampaignActivity).apply {
                text = "Campagne moteur — Android"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(16, 16, 16, 0)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(
                ScrollView(this@CampaignActivity).apply { addView(output) },
                LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f),
            )
        })
        scope.launch { run() }
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        runOnUiThread { output.append(line + "\n") }
    }

    private fun pass(lot: String, detail: String) = record("OK ", lot, detail)
    private fun fail(lot: String, detail: String) = record("ECHEC", lot, detail)
    private fun note(lot: String, detail: String) = record("info", lot, detail)

    private fun record(mark: String, lot: String, detail: String) {
        results += Triple(mark, lot, detail)
        log("[$mark] $lot — $detail")
    }

    private suspend fun run() {
        val roomId = BuildConfig.TEST_ROOM_ID
        var msgJob: Job? = null
        val engine: MatrixEngine = RustMatrixEngine(
            dataPath = File(filesDir, "engine").apply { mkdirs() }.absolutePath,
            cachePath = File(cacheDir, "engine").apply { mkdirs() }.absolutePath,
            insecureTlsForDevelopment = BuildConfig.INSECURE_TLS,
            trustAnchors = li.velvetchat.core.tls.AndroidTrustAnchors.load(),
        )

        try {
            engine.initPlatform()
            val sessions = AndroidSessionStore(applicationContext)
            val stored = sessions.load()
            if (stored != null) engine.restore(stored) else {
                sessions.save(
                    engine.login(BuildConfig.HOMESERVER, BuildConfig.USERNAME, BuildConfig.PASSWORD)
                )
            }
            val session = engine.currentSession()
            log("${session.userId} / ${session.deviceId}")
            log("salon : $roomId")
            log("")

            val states = mutableListOf<ConnectionState>()
            scope.launch {
                engine.connectionFlow().collect { if (states.lastOrNull() != it) states += it }
            }
            delay(300)
            engine.startSync()
            val connecte = withTimeoutOrNull(90_000) {
                while (states.lastOrNull() != ConnectionState.CONNECTED) delay(150)
                true
            }
            if (connecte == true) {
                pass("lot 10 · connexion", states.joinToString(" → "))
            } else {
                fail("lot 10 · connexion", "états : ${states.joinToString(" → ")}")
                return
            }

            val timeline = MutableStateFlow<List<Message>>(emptyList())
            msgJob = scope.launch { engine.messagesFlow(roomId).collect { timeline.value = it } }
            val initial = timeline.awaitFor(60_000) { it.isNotEmpty() }
            if (initial != null) {
                pass("lot 3 · flux de timeline", "${initial.size} message(s) poussés sans interrogation")
            } else {
                fail("lot 3 · flux de timeline", "aucune émission")
                return
            }

            val avant = timeline.value.size
            val reste = engine.loadMore(roomId, 20)
            val agrandi = timeline.awaitFor(45_000) { it.size > avant }
            when {
                agrandi != null -> pass("lot 4 · pagination", "$avant → ${agrandi.size}, reste=$reste")
                !reste -> note("lot 4 · pagination", "début de conversation déjà atteint ($avant)")
                else -> fail("lot 4 · pagination", "le SDK annonce de l'historique, le flux ne grandit pas")
            }

            val stamp = System.currentTimeMillis()
            val texte = "Android — campagne $stamp"
            engine.send(roomId, texte)
            val confirme = timeline.awaitFor(90_000) { msgs ->
                msgs.any { it.text() == texte && it.id.startsWith("$") }
            }
            if (confirme == null) {
                fail("lot 2 · envoi", "jamais confirmé par le serveur")
                return
            }
            val eventId = confirme.first { it.text() == texte }.id
            pass("lot 2 · envoi", "confirmé, $eventId")

            val modifie = "$texte (MODIFIÉ)"
            engine.edit(roomId, eventId, modifie)
            if (timeline.awaitFor(60_000) { m -> m.any { it.id == eventId && it.text() == modifie && it.isEdited } } != null) {
                pass("lot 5 · édition", "contenu changé et drapeau « modifié » porté")
            } else fail("lot 5 · édition", "non observée")

            val reponse = "Android — réponse $stamp"
            engine.reply(roomId, eventId, reponse)
            if (timeline.awaitFor(60_000) { m -> m.any { it.text() == reponse && it.replyTo == eventId } } != null) {
                pass("lot 5 · réponse", "replyTo renseigné")
            } else fail("lot 5 · réponse", "non observée")

            val ajoutee = engine.toggleReaction(roomId, eventId, "👍")
            val vue = timeline.awaitFor(60_000) { m ->
                m.any { it.id == eventId && it.reactions.any { r -> r.emoji == "👍" && r.mine } }
            }
            if (ajoutee && vue != null) pass("lot 5 · réaction", "ajoutée, reconnue comme sienne")
            else fail("lot 5 · réaction", "retour=$ajoutee")

            val retiree = engine.toggleReaction(roomId, eventId, "👍")
            val partie = timeline.awaitFor(60_000) { m ->
                m.any { it.id == eventId && it.reactions.none { r -> r.emoji == "👍" && r.mine } }
            }
            if (!retiree && partie != null) pass("lot 5 · réaction retirée", "même geste, effet inverse")
            else fail("lot 5 · réaction retirée", "retour=$retiree")

            val png = File(cacheDir, "campagne-$stamp.png")
            png.writeBytes(noisyPng(320, 200, stamp))
            engine.sendMedia(roomId, png.absolutePath, "Android — média $stamp")
            val media = timeline.awaitFor(120_000) { m ->
                m.any { msg -> (msg.body as? MessageBody.Media)?.media?.filename == png.name && msg.id.startsWith("$") }
            }
            if (media == null) {
                fail("lot 6 · média", "jamais revenu par le flux")
            } else {
                val body = media.firstNotNullOf { msg ->
                    (msg.body as? MessageBody.Media)?.takeIf { it.media.filename == png.name }
                }
                val octets = engine.mediaBytes(body.media)
                val identique = octets.contentEquals(png.readBytes())
                val dims = body.media.width == 320 && body.media.height == 200
                if (identique && dims && body.kind == MediaKind.IMAGE) {
                    pass("lot 6 · média", "${octets.size} octets identiques, ${body.media.width}×${body.media.height}, ${body.media.mimeType}")
                } else {
                    fail("lot 6 · média", "identique=$identique dims=${body.media.width}×${body.media.height} kind=${body.kind}")
                }
                runCatching { engine.delete(roomId, media.first { (it.body as? MessageBody.Media)?.media?.filename == png.name }.id) }
            }

            runCatching { engine.markRead(roomId) }
                .onSuccess { pass("lot 7 · marquage lu", "accepté par le serveur") }
                .onFailure { fail("lot 7 · marquage lu", it.message ?: "échec") }

            var garde = 0
            while (engine.loadMore(roomId, 50) && garde++ < 10) { }
            delay(3_000)
            val systeme = timeline.value.mapNotNull { it.body as? MessageBody.System }
            val bruts = timeline.value.mapNotNull { it.body as? MessageBody.Unsupported }
                .filter { it.description.startsWith("m.") }
            if (systeme.isNotEmpty() && bruts.isEmpty()) {
                pass("lot 7 · événements d'état", systeme.groupingBy { it.kind }.eachCount().toString())
            } else if (systeme.isEmpty()) {
                note("lot 7 · événements d'état", "aucun événement d'état chargé")
            } else {
                fail("lot 7 · événements d'état", "types bruts visibles : ${bruts.map { it.description }}")
            }

            val autrui = timeline.value.lastOrNull { !it.isOwn && it.id.startsWith("$") }
            if (autrui == null) {
                note("lot 9 · notification", "aucun message d'un autre compte dans l'historique chargé")
            } else {
                when (val n = engine.notifications.resolve(roomId, autrui.id)) {
                    is NotificationResult.Show -> {
                        val lu = (n.notification.body as? MessageBody.Text)?.text
                        val attendu = (autrui.body as? MessageBody.Text)?.text
                        if (lu != null && lu == attendu) {
                            pass(
                                "lot 9 · notification",
                                "« ${lu.take(40)} » de ${n.notification.displayName}, reconstitué depuis 2 identifiants",
                            )
                        } else {
                            fail("lot 9 · notification", "corps « $lu » au lieu de « $attendu »")
                        }
                    }
                    else -> fail("lot 9 · notification", "statut $n sur un message d'autrui")
                }
            }

            runCatching { engine.delete(roomId, eventId, "campagne Android") }

            val avantEtats = states.size
            interrompre(engine)
            val detecte = withTimeoutOrNull(60_000) {
                while (states.size == avantEtats) delay(150)
                true
            }
            val repris = withTimeoutOrNull(150_000) {
                while (states.lastOrNull() != ConnectionState.CONNECTED) delay(250)
                true
            }
            if (detecte == true && repris == true) {
                pass("lot 10 · reprise", "interruption détectée puis rétablie seule : ${states.takeLast(4).joinToString(" → ")}")
            } else {
                fail("lot 10 · reprise", "détectée=$detecte reprise=$repris, état=${states.lastOrNull()}")
            }

            engine.stopSync()
        } catch (t: Throwable) {
            Log.e(TAG, "campagne interrompue", t)
            fail("campagne", "${t::class.simpleName} : ${t.message}")
        } finally {
            msgJob?.cancel()
            verdict()
        }
    }

    private fun verdict() {
        val echecs = results.count { it.first == "ECHEC" }
        log("")
        log("=".repeat(46))
        if (echecs == 0) {
            log("CAMPAGNE ANDROID VERTE — ${results.count { it.first == "OK " }} vérification(s)")
            log("Le moteur ne fait pas que compiler pour Android : il y tourne.")
        } else {
            log("CAMPAGNE ANDROID : $echecs ÉCHEC(S)")
            results.filter { it.first == "ECHEC" }.forEach { log("  • ${it.second} : ${it.third}") }
        }
        log("=".repeat(46))
    }

    private fun interrompre(engine: MatrixEngine) {
        val f = RustMatrixEngine::class.java.getDeclaredField("syncService")
        f.isAccessible = true
        val service = f.get(engine) as? org.matrix.rustcomponents.sdk.SyncService ?: return
        runBlocking { service.stop() }
    }

    private suspend fun MutableStateFlow<List<Message>>.awaitFor(
        timeoutMs: Long,
        predicate: (List<Message>) -> Boolean,
    ): List<Message>? = withTimeoutOrNull(timeoutMs) { first(predicate) }

    private fun Message.text(): String? = (body as? MessageBody.Text)?.text

    private fun noisyPng(width: Int, height: Int, seed: Long): ByteArray {
        val rng = Random(seed)
        val raw = ByteArray(height * (1 + width * 3))
        var i = 0
        repeat(height) {
            raw[i++] = 0
            repeat(width * 3) { raw[i++] = rng.nextInt(256).toByte() }
        }
        val d = Deflater(Deflater.BEST_SPEED)
        d.setInput(raw); d.finish()
        val comp = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (!d.finished()) comp.write(buf, 0, d.deflate(buf))
        d.end()

        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        out.chunk("IHDR", be32(width) + be32(height) + byteArrayOf(8, 2, 0, 0, 0))
        out.chunk("IDAT", comp.toByteArray())
        out.chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.chunk(type: String, data: ByteArray) {
        write(be32(data.size))
        val typed = type.toByteArray(Charsets.US_ASCII) + data
        write(typed)
        write(be32(CRC32().apply { update(typed) }.value.toInt()))
    }

    private fun be32(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private companion object {
        const val TAG = "MatriscordCampagne"
    }
}

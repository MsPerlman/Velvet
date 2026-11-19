package li.velvetchat.verif

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.NotificationResult
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList

fun main(): Unit = runBlocking {
    val creds = PushChainRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val roomId = creds.getProperty("testRoomId")
    val ntfyHost = creds.getProperty("pushEndpointHost")
    val gateway = creds.getProperty("pushGateway")

    println("=".repeat(72))
    println("  Chaîne push de bout en bout — Synapse → passerelle → endpoint")
    println("=".repeat(72))
    println()
    println("Passerelle (vue par Synapse) : $gateway")
    println("Endpoint   (vu par le client) : https://$ntfyHost/…")
    println()

    val checks = Checks()
    val stamp = System.currentTimeMillis()
    val topic = "matriscord-verif-$stamp"
    val endpoint = "https://$ntfyHost/$topic?up=1"
    val appId = "li.velvetchat.pushchain"

    val aDir = File("build/engine-session")
    val a: MatrixEngine = engine("build/engine-session")
    a.initPlatform()
    a.restore(WindowsSessionStore(aDir).load() ?: error("lancer d'abord verifyEngine"))
    a.startSync()

    val recus = CopyOnWriteArrayList<String>()
    val scope = CoroutineScope(Dispatchers.IO)
    val ecoute = scope.launch { ecouterNtfy("https://$ntfyHost/$topic/json", recus) }
    delay(3_000)
    println("① Abonné à l'endpoint, en attente")

    try {
        a.notifications.registerPush(
            endpoint = endpoint,
            gatewayUrl = gateway,
            appId = appId,
            deviceName = "runner chaîne push",
            trustedEndpointHosts = setOf(ntfyHost),
        )
        println("② Pusher enregistré")

        val bDir = File("build/flow-sender")
        val b: MatrixEngine = engine("build/flow-sender")
        val bStored = WindowsSessionStore(bDir).load()
        if (bStored != null) b.restore(bStored) else {
            val s = b.login(
                homeserverUrl = creds.getProperty("homeserver"),
                username = creds.getProperty("username2"),
                password = creds.getProperty("password2"),
            )
            WindowsSessionStore(bDir).save(s)
        }
        b.startSync()

        val texte = "Chaîne push — contenu qui ne doit PAS transiter $stamp"
        b.send(roomId, texte)
        println("③ Message chiffré envoyé par l'autre compte")

        val arrive = withTimeoutOrNull(90_000) {
            while (recus.isEmpty()) delay(250)
            recus.first()
        }

        if (arrive == null) {
            checks.fail(
                "notification acheminée",
                "rien reçu sur l'endpoint en 90 s — Synapse n'a pas appelé la passerelle, " +
                    "ou la passerelle n'a pas relayé",
            )
            b.stopSync()
            return@runBlocking
        }
        checks.pass(
            "notification acheminée",
            "Synapse → $gateway → endpoint, sans intervention",
        )

        val charge = arrive
        val fuite = charge.contains(texte) || charge.contains("body", ignoreCase = true) &&
            charge.contains(texte.take(20))
        if (!fuite) {
            checks.pass(
                "aucun contenu dans la charge utile",
                "le texte du message n'apparaît nulle part dans ce qui a transité par la passerelle",
            )
        } else {
            checks.fail(
                "aucun contenu dans la charge utile",
                "LE TEXTE DU MESSAGE A TRANSITÉ EN CLAIR par la passerelle",
            )
        }

        val roomIdVu = Regex("\"room_id\"\\s*:\\s*\"([^\"]+)\"").find(charge)?.groupValues?.get(1)
        val eventIdVu = Regex("\"event_id\"\\s*:\\s*\"([^\"]+)\"").find(charge)?.groupValues?.get(1)

        if (roomIdVu != null && eventIdVu != null) {
            checks.pass(
                "identifiants transmis",
                "salon $roomIdVu, événement $eventIdVu — et rien d'autre d'exploitable",
            )
        } else {
            checks.fail(
                "identifiants transmis",
                "charge utile inattendue : ${charge.take(300)}",
            )
        }

        if (roomIdVu != null && eventIdVu != null) {
            when (val r = a.notifications.resolve(roomIdVu, eventIdVu)) {
                is NotificationResult.Show -> {
                    val lu = (r.notification.body as? MessageBody.Text)?.text
                    if (lu == texte) {
                        checks.pass(
                            "chaîne complète",
                            "« ${lu.take(50)} » reconstitué depuis ce que la passerelle a relayé — " +
                                "elle-même n'en a jamais rien su",
                        )
                    } else {
                        checks.fail("chaîne complète", "corps obtenu : ${r.notification.body}")
                    }
                }
                else -> checks.fail("chaîne complète", "statut $r")
            }
        }

        b.stopSync()
    } finally {
        runCatching { a.notifications.unregisterPush(endpoint, appId) }
        ecoute.cancel()
        a.stopSync()
        println()
        println("④ Pusher retiré")
    }

    println()
    checks.report()
}

// ---------------------------------------------------------------------------------------------

private fun ecouterNtfy(url: String, out: MutableList<String>) {
    val conn = URI(url).toURL().openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 0
    conn.inputStream.bufferedReader().use { reader ->
        while (true) {
            val ligne = reader.readLine() ?: break
            if (!ligne.contains("\"event\":\"message\"")) continue

            val brut = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .find(ligne)?.groupValues?.get(1) ?: continue
            val decode = if (ligne.contains("\"encoding\":\"base64\"")) {
                runCatching { String(Base64.getDecoder().decode(brut)) }.getOrDefault(brut)
            } else {
                brut.replace("\\\"", "\"").replace("\\\\", "\\")
            }
            out += decode
        }
    }
}

private fun engine(path: String): MatrixEngine = RustMatrixEngine(
    dataPath = File(path).apply { mkdirs() }.absolutePath,
    cachePath = File("$path-cache").apply { mkdirs() }.absolutePath,
    logToStdout = false,
)

private object PushChainRunner

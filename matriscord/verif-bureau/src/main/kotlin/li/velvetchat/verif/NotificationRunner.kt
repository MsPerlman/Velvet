package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.EngineException
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.NotificationResult
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

fun main(): Unit = runBlocking {
    val creds = NotificationRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val roomId = creds.getProperty("testRoomId")
    val homeserver = creds.getProperty("homeserver").trimEnd('/')

    println("=".repeat(72))
    println("  Lot 9 — notifications remontées dans le core")
    println("=".repeat(72))
    println()

    val aDir = File("build/engine-session")
    val a: MatrixEngine = engine("build/engine-session")
    a.initPlatform()
    a.restore(WindowsSessionStore(aDir).load() ?: error("lancer d'abord verifyEngine"))
    a.startSync()

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

    println("Destinataire : ${a.currentSession().userId} / ${a.currentSession().deviceId}")
    println("Émetteur     : ${b.currentSession().userId}")
    println()

    val checks = Checks()
    val stamp = System.currentTimeMillis()

    val texte = "Lot 9 — contenu à reconstituer depuis un push $stamp"
    b.send(roomId, texte)

    val eventId = withTimeoutOrNull(60_000) {
        var id: String? = null
        while (id == null) {
            id = b.messages(roomId, 10)
                .firstOrNull { (it.body as? MessageBody.Text)?.text == texte && it.id.startsWith("$") }
                ?.id
            if (id == null) delay(500)
        }
        id
    }
    if (eventId == null) {
        println("✗ Le message n'a pas été confirmé. Scénario interrompu.")
        a.stopSync(); b.stopSync(); checks.fail("envoi", "pas de confirmation"); checks.report()
        return@runBlocking
    }
    println("① Message envoyé, chiffré. Identifiant : $eventId")
    println("   C'est TOUT ce qu'un push transporte : ce couple salon + événement.")

    val result = a.notifications.resolve(roomId, eventId)
    when (result) {
        is NotificationResult.Show -> {
            val n = result.notification
            val lu = (n.body as? MessageBody.Text)?.text
            if (lu == texte) {
                checks.pass(
                    "déchiffrement depuis un push",
                    "« $lu » — reconstitué à partir de deux identifiants, sans que la passerelle voie rien",
                )
            } else {
                checks.fail("déchiffrement depuis un push", "corps obtenu : ${n.body}")
            }
            checks.pass(
                "contexte de la notification",
                "salon « ${n.channelName} », expéditeur « ${n.displayName} », mention=${n.isMention}",
            )
        }
        NotificationResult.Filtered ->
            checks.fail("déchiffrement depuis un push", "filtré par les règles de push")
        NotificationResult.Redacted ->
            checks.fail("déchiffrement depuis un push", "événement supprimé")
        NotificationResult.NotFound ->
            checks.fail("déchiffrement depuis un push", "événement introuvable côté serveur")
    }

    val fuite = runCatching {
        a.notifications.registerPush(
            endpoint = "https://ntfy.sh/UPfaketoken",
            gatewayUrl = "https://ntfy.exemple.invalid/_matrix/push/v1/notify",
            appId = "li.velvetchat.test",
            deviceName = "runner",
            trustedEndpointHosts = setOf("ntfy.exemple.org"),
        )
    }
    if (fuite.isFailure && fuite.exceptionOrNull() is EngineException) {
        checks.pass(
            "endpoint hors passerelle refusé",
            "« ${fuite.exceptionOrNull()?.message?.take(70)}… » — le scénario de F-04 ne peut plus se rejouer",
        )
    } else {
        checks.fail(
            "endpoint hors passerelle refusé",
            "l'enregistrement a été ACCEPTÉ : les identifiants de salon partiraient vers un tiers",
        )
    }

    val host = URI(homeserver).host
    val endpoint = "https://$host/UP-verif-$stamp"
    val gateway = "https://$host/_matrix/push/v1/notify"
    val appId = "li.velvetchat.verif"

    a.notifications.registerPush(endpoint, gateway, appId, "runner de vérification", setOf(host))
    delay(1_000)

    val token = a.currentSession().accessToken
    val pushers = getPushers(homeserver, token)
    val notre = pushers.firstOrNull { it.contains(endpoint) }

    when {
        notre == null -> checks.fail("pusher enregistré", "absent de la liste rendue par le serveur")
        notre.contains("\"format\":\"event_id_only\"") -> checks.pass(
            "format event_id_only",
            "confirmé par le SERVEUR, pas par le SDK — la charge utile ne portera aucun contenu",
        )
        else -> checks.fail(
            "format event_id_only",
            "le serveur a enregistré autre chose : ${notre.take(200)}",
        )
    }

    a.notifications.unregisterPush(endpoint, appId)
    delay(1_000)
    val apres = getPushers(homeserver, token)
    if (apres.none { it.contains(endpoint) }) {
        checks.pass(
            "pusher retiré",
            "${pushers.size} → ${apres.size} — le nôtre a bien disparu",
        )
    } else {
        checks.fail("pusher retiré", "toujours présent après unregisterPush")
    }

    if (apres.isEmpty()) {
        checks.pass("aucun pusher orphelin", "le compte n'a plus rien d'enregistré")
    } else {
        apres.forEach { println("     · ${it.pushkeyOfEntry()} → ${it.urlOfEntry()}") }

        val hosts = apres.mapNotNull { it.hostOfUrl() }.distinct()
            .filterNot { it.equals(host, ignoreCase = true) }
        val publics = hosts.filterNot { it.isPrivateAddress() }
        val prives = hosts.filter { it.isPrivateAddress() }

        when {
            publics.isNotEmpty() -> checks.fail(
                "pusher vers un tiers",
                "$publics reçoit les identifiants de salon de ce compte — c'est exactement F-04",
            )
            prives.isNotEmpty() -> checks.note(
                "pusher orphelin",
                "$prives — adresse privée, donc pas une fuite vers l'extérieur, mais un reliquat " +
                    "de la campagne S0.5 : IP en dur (F-02) et HTTP en clair. À retirer.",
            )
            else -> checks.pass("aucun pusher orphelin", "${apres.size} pusher(s), tous sur $host")
        }
    }

    println()
    a.stopSync()
    b.stopSync()
    checks.report()
}

// ---------------------------------------------------------------------------------------------

private fun getPushers(homeserver: String, token: String): List<String> {
    val conn = URI("$homeserver/_matrix/client/v3/pushers").toURL()
        .openConnection() as HttpURLConnection
    conn.setRequestProperty("Authorization", "Bearer $token")
    conn.connectTimeout = 15_000
    conn.readTimeout = 15_000
    return try {
        val json = conn.inputStream.bufferedReader().use { it.readText() }
        json.split("{\"app_display_name\"", "{\"pushkey\"")
            .drop(1)
            .map { it.substringBefore("},{") }
    } finally {
        conn.disconnect()
    }
}

private fun String.urlOfEntry(): String? =
    substringAfter("\"url\":\"", "").substringBefore('"').takeIf { it.isNotBlank() }

private fun String.pushkeyOfEntry(): String =
    substringAfter("\"pushkey\":\"", "").substringBefore('"').ifBlank { "?" }

private fun String.hostOfUrl(): String? = urlOfEntry()
    ?.substringAfter("://", "")
    ?.substringBefore('/')
    ?.substringBefore(':')
    ?.takeIf { it.isNotBlank() }

private fun String.isPrivateAddress(): Boolean =
    this == "localhost" ||
        startsWith("127.") ||
        startsWith("10.") ||
        startsWith("192.168.") ||
        Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(this) ||
        endsWith(".local")

private fun engine(path: String): MatrixEngine = RustMatrixEngine(
    dataPath = File(path).apply { mkdirs() }.absolutePath,
    cachePath = File("$path-cache").apply { mkdirs() }.absolutePath,
    logToStdout = false,
)

private object NotificationRunner

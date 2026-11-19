package li.velvetchat.verif

import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.EncryptionState
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.UndecryptableReason
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties

fun main() = runBlocking {
    val creds = EngineRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val dataDir = File("build/engine-session").apply { mkdirs() }
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dataDir.absolutePath,
        cachePath = File("build/engine-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    val sessions = WindowsSessionStore(dataDir)

    println("=".repeat(72))
    println("  Vérification du moteur — A.2 (session chiffrée) + A.3 (déchiffrement)")
    println("=".repeat(72))
    println()

    engine.initPlatform()

    val stored = sessions.load()
    if (stored != null) {
        println("① Session restaurée depuis le stockage chiffré (DPAPI)")
        println("   AUCUN mot de passe utilisé — c'est le critère de sortie d'A.2")
        engine.restore(stored)
    } else {
        println("① Aucune session stockée → connexion par mot de passe")
        val session = engine.login(
            homeserverUrl = creds.getProperty("homeserver"),
            username = creds.getProperty("username"),
            password = creds.getProperty("password"),
        )
        sessions.save(session)
        println("   session enregistrée chiffrée — relancer pour vérifier la restauration")
    }

    val session = engine.currentSession()
    println("   ${session.userId} / ${session.deviceId}")
    println("   sliding sync natif : ${session.slidingSyncNative}")
    println()

    println("② Synchronisation…")
    engine.startSync()
    println("   ✓ opérationnelle")
    println()

    val servers = engine.servers()
    println("③ ${servers.size} serveur(s) :")
    servers.forEach { server ->
        println("   ▸ ${server.name}  (${server.channels.size} salon(s))")
        server.channels.forEach { channel ->
            val lock = when (channel.encryption) {
                EncryptionState.ENCRYPTED -> "🔒"
                EncryptionState.NOT_ENCRYPTED -> "  "
                EncryptionState.UNKNOWN -> "? "
            }
            val badge = if (channel.unread.hasUnread) " [${channel.unread.notifications}]" else ""
            println("      $lock #${channel.name}$badge")
        }
    }
    println()

    val encrypted = servers.flatMap { it.channels }
        .firstOrNull { it.encryption == EncryptionState.ENCRYPTED }

    if (encrypted == null) {
        println("④ Aucun salon chiffré trouvé — impossible de vérifier A.3.")
        engine.stopSync()
        return@runBlocking
    }

    println("④ Lecture de #${encrypted.name} — salon CHIFFRÉ")
    val messages = engine.messages(encrypted.id, limit = 20)
    println("   ${messages.size} message(s) :")
    println()

    val clock = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
    var readable = 0
    var undecryptable = 0

    messages.forEach { m ->
        val who = m.senderDisplayName ?: m.sender
        val at = clock.format(Instant.ofEpochMilli(m.timestamp))
        when (val body = m.body) {
            is MessageBody.Text -> {
                readable++
                println("   [$at] $who : ${body.text}")
            }
            is MessageBody.Media -> {
                readable++
                println("   [$at] $who : <${body.kind}> ${body.caption}")
            }
            is MessageBody.Poll -> {
                readable++
                val chiffres = body.options.joinToString(", ") { "${it.texte}=${it.votes}" }
                println("   [$at] $who : <sondage> ${body.question} ($chiffres)")
            }
            is MessageBody.Undecryptable -> {
                undecryptable++
                val why = when (body.reason) {
                    UndecryptableReason.AVANT_NOTRE_ARRIVEE -> "envoyé avant cet appareil"
                    UndecryptableReason.CLE_REFUSEE -> "clé non partagée avec cet appareil"
                    UndecryptableReason.CLE_MANQUANTE -> "clé absente sans cause nommée"
                    UndecryptableReason.OLM -> "chiffrement Olm illisible"
                    UndecryptableReason.UNKNOWN -> "cause inconnue"
                }
                println("   [$at] $who : 🔒 non déchiffrable ($why)")
            }
            MessageBody.Redacted -> println("   [$at] $who : (supprimé)")
            is MessageBody.System ->
                println("   [$at] · ${body.kind}${body.detail?.let { " : $it" } ?: ""}")
            is MessageBody.Unsupported -> println("   [$at] $who : (${body.description})")
        }
    }

    println()
    println("   $readable lisible(s), $undecryptable non déchiffrable(s)")
    println()
    if (readable > 0) {
        println("✓ A.3 VALIDÉ — le moteur déchiffre le contenu d'un salon chiffré.")
        if (undecryptable > 0) {
            println("  Les messages non déchiffrables sont ANTÉRIEURS à cette session :")
            println("  c'est le comportement attendu tant que la sauvegarde de clés")
            println("  (A-bis.1) n'est pas en place. Le critère d'A.3 porte sur les")
            println("  messages reçus après l'ouverture de session.")
        }
    } else {
        println("✗ Aucun message lisible — envoyer un message puis relancer :")
        println("    ./gradlew :verif-bureau:sendTestMessage")
    }

    engine.stopSync()
}

private object EngineRunner


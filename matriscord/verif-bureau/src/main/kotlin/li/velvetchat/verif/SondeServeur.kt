package li.velvetchat.verif

import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val creds = SondeMarqueur::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    println("=".repeat(72))
    println("  Sonde — ce que le homeserver déclare et ce qu'il accepte")
    println("=".repeat(72))
    println()

    val dir = File("build/sonde")
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dir.apply { mkdirs() }.absolutePath,
        cachePath = File("build/sonde-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()
    val store = WindowsSessionStore(dir)
    store.load()?.let { engine.restore(it) } ?: store.save(
        engine.login(
            homeserverUrl = creds.getProperty("homeserver"),
            username = creds.getProperty("username") ?: error("username absent"),
            password = creds.getProperty("password") ?: error("password absent"),
        )
    )

    val session = engine.currentSession()
    val base = session.homeserverUrl.trimEnd('/')
    val jeton = session.accessToken
    val lignes = mutableListOf<String>()

    fun rapporte(ligne: String) {
        println(ligne)
        lignes += ligne
    }

    println("① Versions et fonctionnalités instables déclarées")
    val versions = obtenir("$base/_matrix/client/versions", jeton)
    val stables = Regex("\"(r\\d+\\.\\d+\\.\\d+|v1\\.\\d+)\"").findAll(versions.orEmpty())
        .map { it.groupValues[1] }.toList()
    rapporte("| Versions du protocole | ${stables.joinToString(", ").ifBlank { "illisible" }} |")

    val instables = Regex("\"(org\\.matrix\\.[a-z0-9_.]+|uk\\.[a-z0-9_.]+|msc\\d+[a-z0-9_.]*)\"\\s*:\\s*true")
        .findAll(versions.orEmpty()).map { it.groupValues[1] }.toList()
    if (instables.isEmpty()) {
        rapporte("| MSC déclarés actifs | aucun |")
    } else {
        instables.forEach { rapporte("| MSC déclaré actif | `$it` |") }
    }
    println()

    println("② Profils étendus (MSC4133) — l'essai qui décide des pronoms")
    val qui = java.net.URLEncoder.encode(session.userId, "UTF-8")
    val cle = "li.velvetchat.sonde"
    var chemin = "/_matrix/client/unstable/uk.tcpip.msc4133/profile/$qui/$cle"
    var code = ecrire("$base$chemin", jeton, """{"$cle":"essai"}""")
    if (code == 404 || code == 400) {
        chemin = "/_matrix/client/v3/profile/$qui/$cle"
        code = ecrire("$base$chemin", jeton, """{"$cle":"essai"}""")
    }
    when (code) {
        in 200..299 -> {
            val relu = obtenir("$base$chemin", jeton)
            if (relu?.contains("essai") == true) {
                rapporte("| **Profils étendus (MSC4133)** | ✅ **actifs** — pronoms et bannière possibles |")
                supprimer("$base$chemin", jeton)
            } else {
                rapporte("| **Profils étendus (MSC4133)** | ⚠️ écriture acceptée, relecture vide |")
            }
        }
        404 -> rapporte("| **Profils étendus (MSC4133)** | ❌ **inactifs** (404) — pas de pronoms publiables |")
        403 -> rapporte("| **Profils étendus (MSC4133)** | ❌ refusés (403) — désactivés par configuration |")
        else -> rapporte("| **Profils étendus (MSC4133)** | ❌ code $code |")
    }
    println()

    println("③ Présence")
    val presence = obtenir("$base/_matrix/client/v3/presence/$qui/status", jeton)
    if (presence != null) {
        rapporte("| Présence | ✅ activée — `$presence` |")
    } else {
        rapporte("| Présence | ❌ indisponible — le sous-titre « N en ligne » se taira |")
    }
    val premier = ecrire("$base/_matrix/client/v3/presence/$qui/status", jeton, """{"presence":"online","status_msg":""}""")
    val second = ecrire("$base/_matrix/client/v3/presence/$qui/status", jeton, """{"presence":"unavailable"}""")
    if (second == 429) {
        rapporte("| `rc_presence` | ⚠️ **limite serrée** — deux écritures d'affilée sont refusées (429) |")
    } else {
        rapporte("| `rc_presence` | large — deux écritures d'affilée passent ($premier puis $second) |")
    }
    println()

    println("④ Capacités déclarées")
    val capacites = obtenir("$base/_matrix/client/v3/capabilities", jeton)
    rapporte("| Capacités | `${capacites?.take(300) ?: "illisible"}` |")
    println()

    val fichier = File("../../serveur/CAPACITES.md")
    fichier.parentFile.mkdirs()
    fichier.writeText(
        buildString {
            appendLine("# Capacités du homeserver — **mesurées**")
            appendLine()
            appendLine("> Généré par `./gradlew :verif-bureau:sondeServeur`.")
            appendLine("> **Ne pas modifier à la main** : ce fichier n'a de valeur que s'il dit ce que le")
            appendLine("> serveur fait vraiment. Le regénérer après toute modification de sa configuration.")
            appendLine(">")
            appendLine("> Dernière sonde : ${java.time.LocalDateTime.now()}")
            appendLine("> Serveur : `$base`")
            appendLine()
            appendLine("| Point | État |")
            appendLine("|---|---|")
            lignes.forEach { appendLine(it) }
            appendLine()
            appendLine("## Pourquoi ce fichier existe")
            appendLine()
            appendLine("La configuration de Synapse n'est pas versionnée dans ce dépôt, et elle décide")
            appendLine("pourtant du comportement du client : `rc_presence` a imposé une reprise sur 429")
            appendLine("(F-46), et MSC4133 décide si les pronoms et la bannière de profil sont possibles.")
            appendLine("Tant que la configuration elle-même n'est pas ici, la sonder est la seule façon")
            appendLine("honnête de savoir — et l'écrire dans le dépôt est la seule façon de s'en souvenir.")
        },
        Charsets.UTF_8,
    )
    println("Écrit : ${fichier.absolutePath}")

    engine.stopSync()
    exitProcess(0)
}

private fun obtenir(url: String, jeton: String): String? = runCatching {
    val cnx = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Authorization", "Bearer $jeton")
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    try {
        if (cnx.responseCode != 200) null
        else cnx.inputStream.bufferedReader().readText()
    } finally {
        cnx.disconnect()
    }
}.getOrNull()

private fun ecrire(url: String, jeton: String, corps: String): Int = runCatching {
    val cnx = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "PUT"
        setRequestProperty("Authorization", "Bearer $jeton")
        setRequestProperty("Content-Type", "application/json")
        doOutput = true
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    try {
        cnx.outputStream.use { it.write(corps.toByteArray()) }
        cnx.responseCode
    } finally {
        cnx.disconnect()
    }
}.getOrDefault(-1)

private fun supprimer(url: String, jeton: String) = runCatching {
    val cnx = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "DELETE"
        setRequestProperty("Authorization", "Bearer $jeton")
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    try {
        cnx.responseCode
    } finally {
        cnx.disconnect()
    }
}

private class SondeMarqueur

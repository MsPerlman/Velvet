package li.velvetchat.verif

import kotlinx.coroutines.runBlocking
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import org.matrix.rustcomponents.sdk.sdkGitSha
import java.io.File
import java.util.Properties

fun main() = runBlocking {
    println("=".repeat(70))
    println("  Matriscord — spike S0.2a (JVM Windows)")
    println("=".repeat(70))
    println()

    println("① Chargement de la lib native via JNA…")
    println("   jna.library.path = ${System.getProperty("jna.library.path")}")

    val sha = try {
        sdkGitSha()
    } catch (t: UnsatisfiedLinkError) {
        println()
        println("✗ ÉCHEC — la DLL n'a pas été trouvée ou n'a pas pu être chargée.")
        println("  ${t.message}")
        println()
        println("  Vérifier que native/matrix_sdk_ffi.dll existe (voir README.md).")
        return@runBlocking
    }

    println("   ✓ matrix_sdk_ffi.dll chargée — git sha $sha")
    println("   JVM : ${System.getProperty("java.version")} / ${System.getProperty("os.arch")}")
    println()
    println("   ★ La moitié fatale de S0.2a est franchie : le SDK Rust tourne")
    println("     sur JVM Windows, donc un `core` KMP partagé est possible.")
    println()

    println("①bis Initialisation de la plateforme (runtime tokio)…")
    initPlatform(
        config = TracingConfiguration(
            logLevel = LogLevel.INFO,
            traceLogPacks = emptyList(),
            extraTargets = emptyList(),
            writeToStdoutOrSystem = false,
            writeToFiles = null,
            sentryConfig = null,
        ),
        useLightweightTokioRuntime = false,
    )
    println("   ✓ plateforme initialisée")
    println()

    val creds = loadCredentials()
    if (creds == null || creds.getProperty("username").isNullOrBlank()) {
        println("② Identifiants absents — arrêt avant le login.")
        println()
        println("   Renseigner local.properties à la racine du projet (tâche -1.2 du plan :")
        println("   créer le compte de test sur le homeserver).")
        return@runBlocking
    }

    println("② Construction du client et ouverture de session…")
    val sessionDir = File("build/s02a-session").apply { mkdirs() }
    val cacheDir = File("build/s02a-cache").apply { mkdirs() }

    val client: Client = ClientBuilder()
        .homeserverUrl(creds.getProperty("homeserver"))
        .sessionPaths(sessionDir.absolutePath, cacheDir.absolutePath)
        .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
        .userAgent("Matriscord-S02a/0.1")
        .build()

    client.login(
        username = creds.getProperty("username"),
        password = creds.getProperty("password"),
        initialDeviceName = "Matriscord S0.2a (PC)",
        deviceId = null,
    )

    val session = client.session()
    println("   ✓ session ouverte")
    println("     userId   : ${session.userId}")
    println("     deviceId : ${session.deviceId}")
    println("     serveur  : ${session.homeserverUrl}")
    println(
        when (session.slidingSyncVersion) {
            SlidingSyncVersion.NATIVE -> "     sliding sync : NATIVE"
            SlidingSyncVersion.NONE -> "     sliding sync : NONE (le serveur ne l'annonce pas)"
        }
    )
    println()
    println("✓ S0.2a : critère de sortie atteint.")
    println("  Le `core` KMP partagé Android + PC est viable.")
}

private fun loadCredentials(): Properties? =
    Main::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }

private object Main

package li.velvetchat.verif

import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.Release
import li.velvetchat.core.model.VarianteRelease
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import kotlin.system.exitProcess

fun main(args: Array<String>): Unit = runBlocking {
    val bureau = args.getOrNull(4) == "true"
    val versionName = args.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: erreur("versionName manquant")
    val versionCode = (if (bureau) Release.codeDeVersion(versionName) else args.getOrNull(0)?.toLongOrNull())
        ?: erreur("versionCode manquant ou non numérique")
    val base = args.getOrNull(2)?.trimEnd('/')?.takeIf { it.isNotBlank() }
        ?: erreur("adresse de base manquante")
    val notes = args.getOrNull(3)?.takeIf { it.isNotBlank() }
    if (bureau) exitProcess(publierBureau(versionCode, versionName, base, notes))

    val dossier = File("../app-android/build/outputs/apk/release")
    val apks = dossier.listFiles { f -> f.name.endsWith(".apk") }?.sortedBy { it.name }
    if (apks.isNullOrEmpty()) erreur("aucun APK dans $dossier — lancer d'abord assembleRelease")

    println("=".repeat(72))
    println("  Publication de Velvet $versionName (code $versionCode)")
    println("=".repeat(72))

    val engine = moteurDePublication()
    val variantes = apks.associate { apk ->
        val abi = MOTIF_ABI.find(apk.name)?.groupValues?.get(1)
            ?: erreur("ABI illisible dans ${apk.name}")
        val empreinte = empreinteDe(apk)
        val adresse = if (base == MATRIX) televerser(engine, apk, nomPublie(versionName, abi))
        else "$base/${nomPublie(versionName, abi)}"
        println("  %-12s %7.1f Mo  %s".format(abi, apk.length() / 1048576.0, empreinte.take(16) + "…"))
        println("               $adresse")
        abi to VarianteRelease(url = adresse, sha256 = empreinte, taille = apk.length())
    }

    println()
    if (base != MATRIX) variantes.forEach { (abi, variante) ->
        val cnx = (java.net.URL(variante.url).openConnection() as java.net.HttpURLConnection)
            .apply { requestMethod = "HEAD"; connectTimeout = 10_000; readTimeout = 10_000 }
        val code = try { cnx.responseCode } finally { cnx.disconnect() }
        if (code != 200) erreur("$abi : ${variante.url} répond $code — copier l'APK d'abord")
        println("  ✓ $abi joignable")
    }

    val serveurs = tousLesServeurs(engine)
    val release = Release(versionCode, versionName, notes, variantes)
    println()
    serveurs.forEach { serveur ->
        engine.publierRelease(serveur.id, release)
        val relue = engine.releasePubliee(serveur.id)
        if (relue == null || relue.versionCode != versionCode) {
            erreur("relecture incohérente sur ${serveur.name} : $relue")
        }
        println("  ✓ ${serveur.name} : ${relue.versionName} (${relue.versionCode})")
    }
    println("    variantes : ${variantes.keys.joinToString()}")
    engine.stopSync()
    exitProcess(0)
}

private suspend fun moteurDePublication(): MatrixEngine {
    val creds = PublicationMarqueur::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: erreur("identifiants absents — renseigner local.properties")

    val dir = File("build/publication")
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dir.apply { mkdirs() }.absolutePath,
        cachePath = File("build/publication-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()
    val store = WindowsSessionStore(dir)
    val stockee = store.load()
    if (stockee != null) {
        engine.restore(stockee)
    } else {
        val session = engine.login(
            homeserverUrl = creds.getProperty("homeserver") ?: erreur("homeserver absent"),
            username = creds.getProperty("username") ?: erreur("username absent"),
            password = creds.getProperty("password") ?: erreur("password absent"),
        )
        store.save(session)
    }
    engine.startSync()
    return engine
}

private suspend fun publierBureau(versionCode: Long, versionName: String, base: String, notes: String?): Int {
    val nom = "VelvetDesk-$versionName.msi"
    val msi = File("../app-desktop/build/compose/binaries/main/msi/$nom")
    if (!msi.exists()) erreur("$msi absent — lancer d'abord :app-desktop:packageMsi")
    val engine = moteurDePublication()
    val url = if (base == MATRIX) televerser(engine, msi, nom) else "$base/$nom"
    val variante = VarianteRelease(url = url, sha256 = empreinteDe(msi), taille = msi.length())
    println("  VelvetDesk $versionName (code $versionCode)  ${variante.sha256.take(16)}…  ${variante.url}")
    if (base != MATRIX) {
        val cnx = (java.net.URL(variante.url).openConnection() as java.net.HttpURLConnection)
            .apply { requestMethod = "HEAD"; connectTimeout = 10_000; readTimeout = 10_000 }
        val code = try { cnx.responseCode } finally { cnx.disconnect() }
        if (code != 200) erreur("${variante.url} répond $code — publier le site d'abord (site/publier.py)")
    }
    val release = Release(versionCode, versionName, notes, mapOf("windows-x64" to variante))
    tousLesServeurs(engine).forEach { serveur ->
        engine.publierRelease(serveur.id, release, Release.TYPE_BUREAU)
        val relue = engine.releasePubliee(serveur.id, Release.TYPE_BUREAU)
        if (relue?.versionCode != versionCode) {
            erreur("relecture incohérente sur ${serveur.name} : $relue")
        }
        println("  ✓ ${serveur.name} : ${relue.versionName} (${relue.versionCode})")
    }
    engine.stopSync()
    return 0
}

private const val MATRIX = "matrix"

private fun televerser(engine: MatrixEngine, fichier: File, nom: String): String {
    val session = engine.currentSession()
    val local = System.getenv("VELVET_TELEVERSEMENT").orEmpty()
    println("  téléversement de $nom (${fichier.length() / 1048576} Mo)…")
    val cnx = (java.net.URL("$local/_matrix/media/v3/upload?filename=" + java.net.URLEncoder.encode(nom, "UTF-8"))
        .openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setFixedLengthStreamingMode(fichier.length())
        setRequestProperty("Authorization", "Bearer ${session.accessToken}")
        setRequestProperty("Content-Type", "application/octet-stream")
        connectTimeout = 15_000
        readTimeout = 600_000
    }
    fichier.inputStream().use { entree -> cnx.outputStream.use { entree.copyTo(it, 1 shl 16) } }
    val code = cnx.responseCode
    val corps = (if (code in 200..299) cnx.inputStream else cnx.errorStream)?.bufferedReader()?.readText().orEmpty()
    if (code !in 200..299) erreur("téléversement de $nom refusé ($code) : $corps")
    val mxc = Regex("\"content_uri\"\\s*:\\s*\"mxc://([^\"]+)\"").find(corps)?.groupValues?.get(1)
        ?: erreur("téléversement de $nom : réponse illisible $corps")
    return session.homeserverUrl.trimEnd('/') + "/_matrix/client/v1/media/download/" + mxc
}

private fun nomPublie(versionName: String, abi: String) = "velvet-$versionName-$abi.apk"

private fun empreinteDe(fichier: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fichier.inputStream().use { entree ->
        val tampon = ByteArray(1 shl 16)
        while (true) {
            val lus = entree.read(tampon)
            if (lus < 0) break
            digest.update(tampon, 0, lus)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private val MOTIF_ABI = Regex("""-(arm64-v8a|armeabi-v7a|x86_64|x86)-release""")

private fun erreur(message: String): Nothing {
    System.err.println("ARRÊT : $message")
    exitProcess(1)
}

private class PublicationMarqueur

/**
 * where the announcement goes: the installation's hidden room, which every app joins at login
 * (see `Installation`). a fake server keeps the call sites as they were
 */
private suspend fun tousLesServeurs(engine: MatrixEngine): List<li.velvetchat.core.model.Server> {
    val id = engine.salonInstallation()
        ?: erreur("ce serveur n'a pas de salon d'installation (li.velvetchat.salon dans son .well-known)")
    return listOf(li.velvetchat.core.model.Server(id = id, name = "installation", avatarUrl = null, channels = emptyList()))
}

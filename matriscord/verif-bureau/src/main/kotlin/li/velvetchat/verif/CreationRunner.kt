package li.velvetchat.verif

import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File

fun main(args: Array<String>): Unit = runBlocking {
    val menageSeul = "menage" in args
    val dir = File("build/engine-session")
    val a: MatrixEngine = RustMatrixEngine(
        dataPath = dir.apply { mkdirs() }.absolutePath,
        cachePath = File("build/engine-session-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
        aliasCatalogue = "#velvet-cosmetiques:exemple.org",
    )
    a.initPlatform()
    a.restore(WindowsSessionStore(dir).load() ?: error("lancer d'abord verifyEngine"))
    a.startSync()

    if (menageSeul) {
        menage(a)
        a.stopSync()
        return@runBlocking
    }

    if ("plaque" in args) {
        val plaques = a.toutesLesDecorations(li.velvetchat.core.model.FAMILLE_PLAQUES)
        println("${plaques.size} plaque(s) au catalogue")
        plaques.take(3).forEach { plaque ->
            val court = plaque.mxc.substringAfterLast('/').take(8)
            val vignette = runCatching { a.avatarBytes(plaque.mxc, 256) }.getOrNull()
            val entier = runCatching { a.emoteBytes(plaque.mxc) }.getOrNull()
            val apercu = plaque.apercu?.let { runCatching { a.emoteBytes(it) }.getOrNull() }
            println("• ${plaque.nom} — vignette ${vignette?.size ?: 0} o, original ${entier?.size ?: 0} o, apercu ${apercu?.size ?: 0} o")
            vignette?.let { File("build/plaque-$court-vignette.png").writeBytes(it) }
            entier?.let { File("build/plaque-$court-original.png").writeBytes(it) }
            apercu?.let { File("build/plaque-$court-apercu.png").writeBytes(it) }
        }
        a.stopSync()
        return@runBlocking
    }

    if ("cosmetiques" in args) {
        listOf(
            li.velvetchat.core.model.FAMILLE_DECORATIONS,
            li.velvetchat.core.model.FAMILLE_EFFETS,
            li.velvetchat.core.model.FAMILLE_PLAQUES,
        ).forEach { famille ->
            val trouves = chrono("toutesLesDecorations($famille)") { a.toutesLesDecorations(famille) }
            println("   ${trouves?.size ?: 0} entrée(s)")
            println("   dont ${trouves?.count { it.apercu != null } ?: 0} avec aperçu")
            trouves?.take(3)?.forEach {
                println("      • ${it.nom} — mxc=${it.mxc} apercu=${it.apercu} anime=${it.anime}")
            }
        }
        a.stopSync()
        return@runBlocking
    }

    val stamp = System.currentTimeMillis()
    println("=".repeat(72))
    println("  Sonde création — serveur, catégorie, salon")
    println("=".repeat(72))

    val serveur = chrono("createServer") { a.createServer("Sonde $stamp", topic = null) }
    println("   serveur = ${serveur?.id} — ${serveur?.channels?.size} salon(s)")
    if (serveur == null) {
        a.stopSync()
        return@runBlocking
    }

    val categorie = chrono("createCategory") { a.createCategory(serveur.id, "Sonde catégorie") }
    println("   catégorie = $categorie")

    val salon = chrono("createChannel (dans la catégorie)") {
        a.createChannel(serveur.id, "sonde-rangee", null, categorie)
    }
    println("   salon = ${salon?.id}")

    val vide = chrono("createCategory (laissée vide)") { a.createCategory(serveur.id, "Sonde vide") }
    println("   catégorie vide = $vide")

    val relu = chrono("servers()") { a.servers().firstOrNull { it.id == serveur.id } }
    relu?.channels?.forEach { println("   • ${it.name} — catégorie=${it.categorie}") }
    println("   catégories annoncées : ${relu?.categories?.map { it.nom }}")

    val general = relu?.channels?.firstOrNull { it.name == "general" }
    if (general != null && vide != null) {
        chrono("deplacerSalon(general → Sonde vide)") { a.deplacerSalon(serveur.id, general.id, null, vide) }
    }
    if (salon != null && categorie != null) {
        chrono("deplacerSalon(sonde-rangee → racine)") { a.deplacerSalon(serveur.id, salon.id, categorie, null) }
    }
    kotlinx.coroutines.delay(1_500)
    val apres = a.servers().firstOrNull { it.id == serveur.id }
    apres?.channels?.forEach { println("   après • ${it.name} — catégorie=${it.categorie}") }

    menage(a)
    a.stopSync()
}

private suspend fun menage(a: MatrixEngine) {
    a.servers().filter { it.name.startsWith("Sonde ") }.forEach { vieux ->
        runCatching { a.leaveServer(vieux.id) }
            .onSuccess { println("   quitté : ${vieux.name}") }
            .onFailure { println("   ménage raté sur ${vieux.name} : ${it.message}") }
    }
}

private suspend fun <T> chrono(quoi: String, bloc: suspend () -> T): T? {
    val debut = System.currentTimeMillis()
    return try {
        bloc().also { println("✔ $quoi — ${System.currentTimeMillis() - debut} ms") }
    } catch (t: Throwable) {
        println("✘ $quoi — ${System.currentTimeMillis() - debut} ms — ${t.message}")
        null
    }
}

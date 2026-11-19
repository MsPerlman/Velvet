package li.velvetchat.desk

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import li.velvetchat.design.Theme
import li.velvetchat.design.ThemeLoader
import li.velvetchat.design.ThemeStatus
import java.io.File

/**
 * velvetdesk's theme, chosen by the person
 *
 * same principle as [FondEcran], and not by accident: it is the same problem, pick a file, keep it,
 * and have the screen follow with no restart. the file is the setting:
 * `%APPDATA%\VelvetDesk\theme.json` exists, so a theme is applied, it does not exist, so it is the
 * default. nothing to keep in sync elsewhere, nothing to let drift
 *
 * the `design` module has known how to load a theme for months, with a safe fallback, unknown keys
 * ignored and a refusal's reason preserved, and nobody called it: both apps built a `Theme()` in
 * code, and the bundled themes served no purpose
 */
data class ThemeLivreBureau(val fichier: String, val theme: Theme)

object ThemeVelvet {

    private val fichier: File get() = File(Donnees.racine, "theme.json")

    /** where the bundled themes get dropped, so the file picker has something to offer */
    private val dossier: File get() = File(Donnees.racine, "themes")

    /** incremented on every change: reads in composition recompose themselves */
    val version = mutableIntStateOf(0)

    /** is a theme applied? read in composition, so it depends on [version] */
    val actif: Boolean get() = version.intValue.let { fichier.isFile }

    private val portee = kotlinx.coroutines.MainScope()

    /**
     * the theme as read, cached by version
     *
     * without this cache, `courant` would reread and reparse the file on every recomposition, which
     * is to say on every keystroke in the composer. the cache is invalidated by [version], so it
     * cannot serve a stale value: the same counter triggers the screen's reread and the file's
     */
    private var cache: Pair<Int, Theme>? = null

    /** the theme to apply: the one that is set, or the default */
    val courant: Theme
        get() {
            val v = version.intValue
            cache?.let { (vu, theme) -> if (vu == v) return theme }
            val lu = ThemeLoader.parse(fichier.takeIf { it.isFile }?.readText())
            // a refused theme says so: the loader keeps the reason "to show it in the settings",
            // and it served no purpose while nobody read it
            (lu.status as? ThemeStatus.Invalid)?.let {
                println("Thème : ${fichier.path} REFUSÉ (${it.reason}) — défaut appliqué")
            }
            cache = v to lu.theme
            return lu.theme
        }

    /** the applied theme's name, to show in the menu. null when it is the default */
    val nom: String? get() = if (actif) courant.name else null

    /**
     * drops the bundled themes into `%APPDATA%\VelvetDesk\themes\`, once
     *
     * they live in the repo's `design/themes/`, which the build puts on the classpath, and an
     * installed app has no access to the sources. extracting them gives the file picker something
     * to offer, and it leaves the files editable: a bundled theme becomes the starting point for
     * your own, which is what the module's readme promises
     *
     * it never overwrites an existing file: a palette tweaked by hand survives a restart, and an
     * update
     */
    fun preparerLivres() {
        runCatching {
            dossier.mkdirs()
            // the classpath cannot be enumerated from inside a jar, so the list is written here in
            // the clear. adding a theme to `design/themes/` therefore means adding it here too,
            // which is the price of an extraction with no index, paid once per theme
            for (nom in LIVRES) {
                val cible = File(dossier, nom)
                if (cible.isFile) continue
                val flux = javaClass.getResourceAsStream("/themes/$nom") ?: continue
                flux.use { entree -> cible.outputStream().use { entree.copyTo(it) } }
            }
        }
    }

    /**
     * opens the file picker on the themes folder and applies the one you choose
     *
     * the theme is validated before being applied: unreadable json is refused straight away, with
     * its reason, rather than copied only to fall back silently on the default at the next start,
     * which would give "i picked a theme and nothing changed", the worst possible report since it
     * does not say whether the file or the app has the problem
     */
    fun choisir(parent: java.awt.Window?) {
        portee.launch {
            preparerLivres()
            val source = choisirTheme(parent) ?: return@launch
            if (!source.isFile) return@launch
            val lu = ThemeLoader.parse(runCatching { source.readText() }.getOrNull())
            when (val etat = lu.status) {
                is ThemeStatus.Invalid -> {
                    println("Thème : ${source.name} refusé (${etat.reason}) — rien n'a été changé")
                    return@launch
                }
                ThemeStatus.Default -> return@launch
                ThemeStatus.Loaded -> Unit
            }
            runCatching { source.copyTo(fichier, overwrite = true) }
                .onSuccess { version.intValue++ }
        }
    }

    /**
     * the shipped themes, read from the classpath for the preview
     *
     * not reread from `preparerLivres()` on disk: there they are **editable**, and that is
     * wanted — a shipped theme is a starting point for your own. but the gallery has to show
     * what it will apply, and apply what it shows. so it reads the original, and a file edited
     * by hand is chosen through "import"
     *
     * an unreadable theme is skipped rather than bringing the list down
     */
    fun livres(): List<ThemeLivreBureau> =
        li.velvetchat.design.ThemesLivres.NOMS.mapNotNull { nom ->
            runCatching {
                val texte = javaClass.getResourceAsStream("/themes/$nom")
                    ?.bufferedReader()?.use { it.readText() } ?: return@runCatching null
                val lu = ThemeLoader.parse(texte)
                if (lu.status is ThemeStatus.Invalid) null else ThemeLivreBureau(nom, lu.theme)
            }.getOrNull()
        }

    fun appliquer(nomLivre: String) {
        val texte = runCatching {
            javaClass.getResourceAsStream("/themes/$nomLivre")?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return
        runCatching { fichier.writeText(texte) }.onSuccess { version.intValue++ }
    }

    /** back to the default theme */
    fun retirer() {
        fichier.delete()
        version.intValue++
    }

    /**
     * the themes the build bundles from `design/themes/`
     *
     * the list used to live here, in its own copy, so adding a theme meant remembering a file
     * nobody would think to open — and forgetting it meant the theme shipped and never showed
     * up. it is now [ThemesLivres.NOMS] in `design`, shared with android, and
     * `tools/verifier-themes.py` fails when the folder and the list disagree
     */
    private val LIVRES = li.velvetchat.design.ThemesLivres.NOMS
}

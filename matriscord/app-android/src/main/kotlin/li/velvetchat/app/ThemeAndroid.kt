package li.velvetchat.app

import li.velvetchat.core.tr

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import li.velvetchat.design.Theme
import li.velvetchat.design.ThemeLoader
import li.velvetchat.design.ThemeStatus
import li.velvetchat.design.ThemesLivres
import java.io.File

/**
 * a shipped theme, as the settings screen presents it
 *
 * the whole [Theme] is kept and not three colours: the preview shows three today, and it will
 * show others the day a theme can change an image. rereading it at that point would cost one
 * file read per thumbnail
 */
data class ThemeLivre(val fichier: String, val theme: Theme)

/**
 * the chosen theme, and what it takes to choose one
 *
 * ## what this replaces
 *
 * a twenty line function that read `filesDir/theme.json` **which nothing ever wrote**. the
 * `design` module has carried a full loader for months, its readme promises changing a colour
 * without recompiling, the five shipped themes existed — and on the phone there was no way to
 * apply one. the promise was false on that side, and the silence was total: nothing on screen
 * said a theme existed
 *
 * ## the file, and why that one
 *
 * `filesDir/theme.json`, in the app's private folder, the same one the engine uses. missing or
 * unreadable: the default theme applies — that is the loader's guarantee, and it is what makes
 * the operation safe. a theme cannot break the app
 *
 * ## why an observable version
 *
 * the old comment said a theme does not change while you are looking at the screen. that was
 * true as long as nobody could change one. with a picker it changes precisely while you are
 * looking, and it has to show immediately: [version] is read by the composition, exactly as
 * the desktop does
 */
object ThemeVelvetAndroid {

    private val version = mutableIntStateOf(0)

    private var cache: Pair<Int, Theme>? = null

    private fun fichier(contexte: Context) = File(contexte.filesDir, "theme.json")

    /**
     * the theme to apply right now
     *
     * to be read **from the composition**: it consults [version], so applying a theme shows
     * without restarting
     */
    fun courant(contexte: Context): Theme {
        val v = version.intValue
        cache?.let { (vu, theme) -> if (vu == v) return theme }
        val lu = ThemeLoader.parse(fichier(contexte).takeIf { it.isFile }?.readText()).theme
        cache = v to lu
        return lu
    }

    fun actif(contexte: Context): Boolean = fichier(contexte).isFile

    fun nom(contexte: Context): String? = if (actif(contexte)) courant(contexte).name else null

    /**
     * the shipped themes, read from the assets
     *
     * an unreadable theme is **skipped** rather than bringing the list down: one damaged file
     * in the apk must not cost all the others
     */
    fun livres(contexte: Context): List<ThemeLivre> =
        ThemesLivres.NOMS.mapNotNull { nom ->
            runCatching {
                val texte = contexte.assets.open("themes/$nom").bufferedReader().use { it.readText() }
                val lu = ThemeLoader.parse(texte)
                if (lu.status is ThemeStatus.Invalid) null else ThemeLivre(nom, lu.theme)
            }.getOrNull()
        }

    fun appliquer(contexte: Context, fichierLivre: String): Boolean = runCatching {
        val texte = contexte.assets.open("themes/$fichierLivre").bufferedReader().use { it.readText() }
        poser(contexte, texte)
    }.getOrDefault(false)

    /**
     * applies a theme chosen from the phone's files
     *
     * @return null when all is well, otherwise the reason for the refusal, to be shown
     */
    fun importer(contexte: Context, uri: Uri): String? {
        val texte = runCatching {
            contexte.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return tr("Fichier illisible", "Unreadable file")
        return when (val etat = ThemeLoader.parse(texte).status) {
            // **validated before being applied.** copying first and falling back to the
            // default at the next start would give "i picked a theme and nothing changed", the
            // worst kind of report: it does not say whether the file or the app is at fault
            is ThemeStatus.Invalid -> etat.reason
            ThemeStatus.Default -> tr("Fichier vide", "Empty file")
            ThemeStatus.Loaded -> if (poser(contexte, texte)) null else tr("Écriture impossible", "Could not write")
        }
    }

    fun retirer(contexte: Context) {
        fichier(contexte).delete()
        version.intValue++
    }

    private fun poser(contexte: Context, texte: String): Boolean = runCatching {
        fichier(contexte).writeText(texte)
        version.intValue++
        true
    }.getOrDefault(false)
}

/**
 * kept: `ActiviteBulle` reads it once at creation, and a bubble does not live long enough to
 * see a theme change
 */
fun themeDeLUtilisateur(contexte: Context): Theme = ThemeVelvetAndroid.courant(contexte)

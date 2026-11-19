package li.velvetchat.app

import li.velvetchat.core.tr

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import li.velvetchat.core.fonds.MagasinFonds
import java.io.File

/**
 * the wallpaper, on the phone
 *
 * ## what this fills in
 *
 * nothing either. the desktop has had a wallpaper for a long time; the phone had none, and
 * the theme could not carry one. the two gaps are closed together because they have the same
 * answer: an image filed under the hash of its contents, which a theme knows how to name
 *
 * ## who wins, the theme or the person
 *
 * the person. a theme **offers** a wallpaper, it does not impose one: otherwise changing theme
 * would erase the photograph somebody set, without saying so. "remove" gives the screen back
 * to the theme rather than to nothing, which is the only way back that does not lose the offer
 *
 * same object as on the desktop, same rules, same store: [MagasinFonds] lives in
 * `core/jvmShared`, compiled by both
 */
object FondEcranAndroid {

    private fun magasin(contexte: Context) = MagasinFonds(contexte.filesDir)

    private fun choix(contexte: Context) = File(contexte.filesDir, "fond-choisi.txt")

    val version = mutableIntStateOf(0)

    /**
     * what should be shown: the personal choice, otherwise what the theme offers
     */
    private fun empreinteCourante(contexte: Context): String? {
        val m = magasin(contexte)
        val perso = runCatching {
            choix(contexte).takeIf { it.isFile }?.readText()?.trim()
        }.getOrNull()
        if (!perso.isNullOrBlank() && m.existe(perso)) return perso
        return ThemeVelvetAndroid.courant(contexte).images.chatBackground.takeIf { m.existe(it) }
    }

    fun actif(contexte: Context): Boolean =
        version.intValue.let { empreinteCourante(contexte) != null }

    fun voile(contexte: Context): Float =
        version.intValue.let { ThemeVelvetAndroid.courant(contexte).images.chatBackgroundVeil }

    fun recents(contexte: Context): List<String> =
        version.intValue.let { magasin(contexte).recents().map { f -> f.empreinte } }

    fun octets(contexte: Context): ByteArray? =
        empreinteCourante(contexte)?.let { magasin(contexte).lire(it) }

    fun octetsDe(contexte: Context, empreinte: String): ByteArray? =
        magasin(contexte).lire(empreinte)

    /**
     * files away an image picked from the phone's files, and sets it
     *
     * @return null when all is well, otherwise the reason, to be shown rather than swallowed
     */
    fun importer(contexte: Context, uri: Uri): String? {
        val octets = runCatching {
            contexte.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return tr("Image illisible", "Unreadable image")
        // a plain bound rather than a phone that freezes: past this it is a raw 50 MB photo
        // that will never be shown whole anyway
        if (octets.size > 32 * 1024 * 1024) return tr("Image trop lourde (32 Mo au plus)", "Image too heavy (32 MB at most)")
        val empreinte = magasin(contexte).ranger(octets, protegees = protegees(contexte))
            ?: return tr("Écriture impossible", "Could not write")
        poser(contexte, empreinte)
        return null
    }

    fun poser(contexte: Context, empreinte: String) {
        val m = magasin(contexte)
        if (!m.existe(empreinte)) return
        m.noter(empreinte, protegees = protegees(contexte))
        runCatching { choix(contexte).writeText(empreinte) }
            .onSuccess { version.intValue++ }
    }

    fun oublier(contexte: Context, empreinte: String) {
        val courant = runCatching {
            choix(contexte).takeIf { it.isFile }?.readText()?.trim()
        }.getOrNull()
        magasin(contexte).oublier(empreinte, protegees = protegees(contexte) - empreinte)
        if (courant == empreinte) choix(contexte).delete()
        version.intValue++
    }

    fun retirer(contexte: Context) {
        choix(contexte).delete()
        version.intValue++
    }

    /**
     * what the sweep is not allowed to delete
     *
     * the image a theme names is not necessarily in the history — an imported theme arrives
     * with its own — and dropping it from the list must not make it vanish
     */
    private fun protegees(contexte: Context): Set<String> = setOfNotNull(
        ThemeVelvetAndroid.courant(contexte).images.chatBackground.takeIf { it.isNotBlank() },
    )
}

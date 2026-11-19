package li.velvetchat.core.fonds

import java.io.File
import java.security.MessageDigest

/**
 * one wallpaper and what is known about it
 *
 * [empreinte] is the file's name in the store, and the wallpaper's identity everywhere else.
 * [vuLe] orders the list and decides who leaves first when it overflows
 */
data class Fond(val empreinte: String, val vuLe: Long)

/**
 * where the wallpapers live, and the last twenty of them
 *
 * ## the filing, and why this one
 *
 * one file per image in the app's private folder, **named after the hash of its contents**. it is
 * the simplest filing that holds, and it is safe for three reasons no other gives all three of:
 *
 *   · **nothing depends on an outside path.** remembering `D:\Photos\beach.jpg` works until the
 *     day somebody tidies their photos, and that day the wallpaper disappears with nothing to
 *     explain it. here the image is copied, so it outlives its original
 *   · **a hash cannot name its way out of the folder.** a theme people pass around names the
 *     image it wants, and if that name were a path, a rigged theme could ask to read elsewhere
 *     on the machine. a hash is sixty four hex characters, [valide] checks it, and there is
 *     nothing to traverse
 *   · **the same image twice weighs once.** setting the same wallpaper from two different themes
 *     does not duplicate it: same hash, same file
 *
 * ## the last twenty
 *
 * the list is the order they were set in, most recent first. past twenty the oldest leaves — and
 * its file is deleted **only** if nothing names it any more. that is the one subtlety: an image
 * can be in the history and in the current theme at once, and dropping it from the history must
 * not make it vanish from the screen
 */
class MagasinFonds(private val racine: File) {

    private val dossier: File get() = File(racine, "fonds")
    private val index: File get() = File(racine, "fonds-recents.txt")

    /** one file per image, named after its hash */
    private fun fichierDe(empreinte: String) = File(dossier, "$empreinte.img")

    /**
     * files an image away and puts it at the head of the recents
     *
     * @param protegees the hashes that must not be deleted when they drop off the list — the one
     *   the current theme is showing, typically. without it, setting a twenty first wallpaper
     *   deleted the file of the wallpaper **still on screen**: the list overflowed, the oldest
     *   left, and nobody asked whether it was still in use
     * @return its hash, or null if the bytes could not be written
     */
    fun ranger(octets: ByteArray, protegees: Set<String> = emptySet()): String? {
        val empreinte = empreinteDe(octets)
        return runCatching {
            dossier.mkdirs()
            val cible = fichierDe(empreinte)
            // already there: same contents, same hash, nothing to rewrite
            if (!cible.isFile) cible.writeBytes(octets)
            noter(empreinte, protegees)
            empreinte
        }.getOrNull()
    }

    /** a wallpaper's bytes, or null if it is not there */
    fun lire(empreinte: String): ByteArray? {
        if (!valide(empreinte)) return null
        return fichierDe(empreinte).takeIf { it.isFile }?.let {
            runCatching { it.readBytes() }.getOrNull()
        }
    }

    fun existe(empreinte: String): Boolean = valide(empreinte) && fichierDe(empreinte).isFile

    /** the recents, last one set at the head */
    fun recents(): List<Fond> = lireIndex().filter { existe(it.empreinte) }

    /**
     * moves a wallpaper back to the head, and pushes the twenty first one out
     *
     * @param protegees the hashes that must not be deleted even once off the list — typically
     *   the one the current theme uses
     */
    fun noter(empreinte: String, protegees: Set<String> = emptySet()) {
        if (!valide(empreinte)) return
        val avant = lireIndex().filterNot { it.empreinte == empreinte }
        val apres = (listOf(Fond(empreinte, System.currentTimeMillis())) + avant).take(MAX)
        val sortis = avant.map { it.empreinte }.toSet() - apres.map { it.empreinte }.toSet()
        ecrireIndex(apres)
        // the file only leaves when nothing names it any more
        for (parti in sortis - protegees - setOf(empreinte)) {
            runCatching { fichierDe(parti).delete() }
        }
    }

    /** drops a wallpaper from the history, and deletes its file unless it is protected */
    fun oublier(empreinte: String, protegees: Set<String> = emptySet()) {
        ecrireIndex(lireIndex().filterNot { it.empreinte == empreinte })
        if (empreinte !in protegees) runCatching { fichierDe(empreinte).delete() }
    }

    /**
     * deletes what is neither in the history nor protected
     *
     * nothing calls this in a loop: it is the sweep of a store that accumulated files after a
     * stop at the wrong moment
     */
    fun menage(protegees: Set<String>): Int {
        val gardees = recents().map { it.empreinte }.toSet() + protegees
        val partis = dossier.listFiles()
            ?.filter { it.isFile && it.nameWithoutExtension !in gardees }
            ?: return 0
        partis.forEach { runCatching { it.delete() } }
        return partis.size
    }

    // ---------------------------------------------------------------- the index

    /**
     * one line per wallpaper, `hash space timestamp`
     *
     * text and not json: two fields, one hex and one a number. a damaged line is skipped rather
     * than brought down the whole list — a history is not data whose partial loss deserves an
     * error screen
     */
    private fun lireIndex(): List<Fond> {
        val texte = index.takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
            ?: return emptyList()
        return texte.lineSequence().mapNotNull { ligne ->
            val bouts = ligne.trim().split(' ')
            if (bouts.size != 2 || !valide(bouts[0])) return@mapNotNull null
            val quand = bouts[1].toLongOrNull() ?: return@mapNotNull null
            Fond(bouts[0], quand)
        }.take(MAX).toList()
    }

    private fun ecrireIndex(fonds: List<Fond>) {
        runCatching {
            racine.mkdirs()
            index.writeText(fonds.joinToString("\n") { "${it.empreinte} ${it.vuLe}" })
        }
    }

    companion object {
        /** what "the last twenty" means */
        const val MAX = 20

        /** sha-256, lowercase hex */
        fun empreinteDe(octets: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(octets)
                .joinToString("") { "%02x".format(it) }

        /**
         * a hash and nothing else
         *
         * this is **the** security control of the store: everything that names a wallpaper goes
         * through here, so no `..`, no separator and no absolute path can get out. a theme from
         * elsewhere names its images, and it must not be able to name anything else
         */
        fun valide(empreinte: String): Boolean =
            empreinte.length == 64 && empreinte.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

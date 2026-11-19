package li.velvetchat.desk

import java.io.File

/**
 * where velvetdesk keeps what it keeps
 *
 * `%APPDATA%\VelvetDesk`, and `-Dvelvet.donnees=<path>` to point somewhere else, which is how you
 * run two accounts side by side, or work on a throwaway folder
 *
 * there is no portable mode, and that is a decision
 *
 * the idea held: a self contained app image, a `donnees/` folder next to the executable, and velvet
 * fits on a usb stick. what killed it is not the difficulty, it is what the stick does not carry:
 * the session is encrypted by dpapi, with a key windows derives from the user account. on another
 * machine it does not decrypt, so you have to log in again, and a portable app that asks for the
 * password at every desk is portable in name only
 *
 * the alternative would have been encrypting the session with something other than the windows
 * account. that amounts to putting a full access token on removable media: whoever picks up the
 * stick reads everybody's conversations. not an acceptable trade for saving an install
 */
object Donnees {

    /** the root, created if needed */
    val racine: File by lazy {
        val choisi = System.getProperty("velvet.donnees")?.let(::File)
            ?: System.getenv("APPDATA")?.let { File(it, "VelvetDesk") }
            // linux: where the freedesktop spec puts an app's data
            ?: File(System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"), "velvetdesk")
        choisi.apply { mkdirs() }
    }

    /** the session, the history, the encryption keys. what makes you logged in */
    val session: File by lazy { File(racine, "session").apply { mkdirs() } }

    /** what rebuilds itself from the server: media, thumbnails */
    val cache: File by lazy { File(racine, "cache").apply { mkdirs() } }

    private val marqueurPurge: File get() = File(racine, "purger")

    /**
     * asks for the account to be erased at the next start
     *
     * windows keeps the sdk's database open as long as the process lives, so erasing on the spot
     * would leave locked pieces behind, therefore a mixture of two accounts. a marker, a relaunch,
     * and [purgerSiDemande] erases a folder nobody holds any more
     */
    fun demanderPurge() {
        runCatching { racine.mkdirs(); marqueurPurge.writeText("") }
    }

    /**
     * erases the session and the cache when a logout asked for it. call it before anything else
     *
     * move aside before erasing. the instance we just left can still hold a file of the database,
     * so the erase would half fail and the next login would open onto leftovers, which is worse
     * than anything. so the folder is renamed first, which windows accepts even on an open file as
     * long as it stays on the same volume, then erased as best it can, and whatever resists gets
     * swept at the next launch
     */
    /**
     * a store nobody can open any more has no reason to stay
     *
     * the session lives in `session.dpapi`, and the sdk's databases next to it hold the history and
     * the keys. erasing the first without the second leaves a folder belonging to an account that
     * can no longer be restored, and the sdk then refuses to connect to it with another one. that
     * is what produced "connection refused" on a correct password: the two `.sqlite3` files were a
     * day old and the `session.dpapi` had gone
     *
     * two paths led there and neither erased the databases: `WindowsSessionStore.clear()`, and
     * `load()` itself when the decryption fails, a different windows account, a restored profile, a
     * damaged file
     *
     * moved aside by renaming, like [purgerSiDemande], because a file can still be held
     */
    fun ecarterMagasinOrphelin() {
        if (File(session, "session.dpapi").exists()) return
        val bases = session.listFiles()?.filter { it.name.startsWith("matrix-sdk-") }
        if (bases.isNullOrEmpty()) return
        bases.forEach { base ->
            val ecarte = File(session, "orphelin-${base.name}-${System.currentTimeMillis()}")
            if (!runCatching { base.renameTo(ecarte) }.getOrDefault(false)) {
                runCatching { base.delete() }
            } else {
                runCatching { ecarte.delete() }
            }
        }
        session.listFiles()?.filter { it.name.startsWith("orphelin-") }
            ?.forEach { runCatching { it.delete() } }
    }

    fun purgerSiDemande() {
        if (!marqueurPurge.exists()) return
        listOf("session", "cache").forEach { nom ->
            val vivant = File(racine, nom)
            if (!vivant.exists()) return@forEach
            val ecarte = File(racine, "$nom-a-effacer-${System.currentTimeMillis()}")
            if (!runCatching { vivant.renameTo(ecarte) }.getOrDefault(false)) {
                runCatching { vivant.deleteRecursively() }
            }
        }
        // the leftovers of previous times, once the files are released
        racine.listFiles()?.filter { it.name.contains("-a-effacer-") }
            ?.forEach { runCatching { it.deleteRecursively() } }
        runCatching { marqueurPurge.delete() }
    }
}

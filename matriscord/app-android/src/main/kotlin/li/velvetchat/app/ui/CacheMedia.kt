package li.velvetchat.app.ui

import android.content.Context
import java.io.File

/**
 * the media cache policy
 *
 * two stores live in the app's cache, and they do not obey the same rule, which is the point this
 * file exists to say
 *
 * 1. what the app writes itself, in [DOSSIERS]: the copy of a photo made so it can be uploaded, the
 * recording of a voice message already sent. they are by products. once the send is over nothing
 * depends on them any more, and yet nothing was deleting them: a twelve megabyte photo sent last
 * month was still taking up room this morning. so they are under a budget, the oldest going first
 *
 * 2. what the sdk writes, in `cache/engine`: the downloaded media, but also the event cache's
 * database. going through it with a broom would mean deleting files the sdk holds open descriptors
 * on, and corrupting the local history. that store is only emptied through the sdk's api, on
 * explicit request, never automatically, and never file by file
 *
 * confusing the two would be the easy mistake here: both are "cache", both are in `cacheDir`, and
 * the second is the bigger. it is precisely the one not to touch yourself
 */
object CacheMedia {

    /**
     * the budget of the folders the app fills itself
     *
     * 128 mb: enough to keep several dozen recent sends, so enough to resend a photo you just
     * shared without taking it from the gallery again, without ever weighing on a phone whose
     * storage is filling. the real cache of received media is the sdk's, and it lives next door
     */
    const val BUDGET_OCTETS: Long = 128L * 1024 * 1024

    /**
     * a file younger than this is never deleted
     *
     * an upload in progress has its file open and freshly written: it would be exactly the
     * candidate eviction would choose, being the most recent at the moment the purge fires after
     * it. the delay puts it out of reach
     */
    private const val SURSIS_MS = 5 * 60 * 1000L

    /** the folders written by the app, and those alone */
    private val DOSSIERS = listOf("envois", "vocal")

    /** what the files under the budget weigh */
    fun taille(contexte: Context): Long =
        fichiers(contexte).sumOf { it.length() }

    /**
     * brings the app's folders back under the budget, oldest to newest
     *
     * the order is that of the last modification, not of the last access. android exposes no
     * reliable access date, most filesystems mounted `noatime` not even keeping it up to date. for
     * files written once and almost never reread, the write date is the right measure of "the
     * oldest" anyway
     *
     * @return the number of bytes freed
     */
    fun purger(contexte: Context, budget: Long = BUDGET_OCTETS): Long {
        val maintenant = System.currentTimeMillis()
        val tries = fichiers(contexte).sortedBy { it.lastModified() }
        var total = tries.sumOf { it.length() }
        var libere = 0L

        for (f in tries) {
            if (total <= budget) break
            if (maintenant - f.lastModified() < SURSIS_MS) continue
            val poids = f.length()
            if (f.delete()) {
                total -= poids
                libere += poids
            }
        }
        // the folder a deleted file lived in goes with it when nothing is left. `delete` on a
        // directory only succeeds when it is empty, so the call says "if this is now useless"
        // without having to ask first
        DOSSIERS.forEach { nom ->
            File(contexte.cacheDir, nom).listFiles()?.filter { it.isDirectory }?.forEach { it.delete() }
        }
        return libere
    }

    /**
     * empties the app's folders entirely. the sdk's cache is not touched
     *
     * @return the number of bytes freed
     */
    fun vider(contexte: Context): Long = purger(contexte, budget = 0)

    /**
     * what the sdk's cache weighs, for information
     *
     * measured but never deleted here, see the header. the figure belongs on screen because it is
     * the one that explains the app's size in android's settings, and an unexplained figure worries
     * more than a large explained one
     */
    fun tailleMoteur(contexte: Context): Long =
        File(contexte.cacheDir, "engine").poids()

    private fun fichiers(contexte: Context): List<File> =
        // `walkTopDown` and not `listFiles`, since the copies made for an upload live in a
        // folder named after their instant: a flat listing walked straight past them, so they
        // were never counted against the budget and never evicted. the kind of leak that only
        // shows up months later, as an application that weighs a gigabyte for no stated reason
        DOSSIERS.flatMap { nom ->
            File(contexte.cacheDir, nom).walkTopDown().filter { it.isFile }.toList()
        }

    private fun File.poids(): Long = when {
        !exists() -> 0L
        isFile -> length()
        else -> listFiles()?.sumOf { it.poids() } ?: 0L
    }
}

/** formats a byte weight for display: "12.4 MB" */
fun Long.enPoidsLisible(): String {
    // the unit and the decimal separator follow the chosen language. `%,.1f` takes the machine's
    // default locale: an english screen showed "6,2 Mo", with french's comma and french's unit. it
    // is the kind of detail that gives away a half done translation
    val t = horsUi
    val locale = localeCourante()
    return when {
        this < 1024 -> "$this ${t.uniteOctets}"
        this < 1024 * 1024 -> "%,.0f %s".format(locale, this / 1024.0, t.uniteKilooctets)
        this < 1024L * 1024 * 1024 ->
            "%,.1f %s".format(locale, this / (1024.0 * 1024), t.uniteMegaoctets)
        else -> "%,.1f %s".format(locale, this / (1024.0 * 1024 * 1024), t.uniteGigaoctets)
    }
}

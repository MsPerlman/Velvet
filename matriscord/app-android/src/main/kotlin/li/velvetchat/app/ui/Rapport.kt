package li.velvetchat.app.ui

import android.content.Context
import android.content.Intent
import java.io.File

/**
 * the problem report: what was missing for a reported bug to be fixable
 *
 * the fault it corrects is not in the app, it is in the loop: somebody says "it does
 * not work", and there is nothing to look at. you then have to plug the phone in over usb, turn
 * debugging on, and hope the fault happens again, for an app installed on devices that never come
 * back to the desk
 *
 * ## what it is, and what it is not
 *
 * it is not an automatic upload. the file is written, then offered for sharing: the person chooses
 * where it goes, and whether. a report that sends itself is a collection, not a report, and on a
 * relative's device that difference is not a nuance
 *
 * it is not a rageshake in element's sense either, listening to the accelerometer permanently. we
 * forbid exactly that kind of continuous watch, and a sensor running to catch a rare gesture is the
 * app's worst cost to use ratio. so the entry lives in the settings, where you look for it when you
 * need it
 *
 * ## what the file contains
 *
 * the system log of velvet's process alone. android gives no other since jelly bean, an app can no
 * longer read its neighbours' logs. in it are the app's warnings (twenty six places, none of which
 * logs a message's text) and the matrix sdk's, which names rooms and events by their id
 *
 * in other words: enough to know what failed, not enough to read a conversation. that is said on
 * screen rather than only here, because it is for the person sharing it to know
 */
internal object Rapport {

    /** the folder served by the `FileProvider`, and only it, see `chemins_partages.xml` */
    private const val DOSSIER = "journaux"

    /**
     * how many log lines we keep
     *
     * android's buffer holds a few thousand, and taking everything would produce a file no
     * messaging app accepts as an attachment. two thousand lines amply cover the current session,
     * which is the one where the fault just happened
     */
    private const val LIGNES_MAX = 2_000

    /**
     * writes the report and returns the file, or `null` when the log is unreadable
     *
     * to be called off the main thread: `logcat` is a process, and reading it takes as long as it
     * takes
     */
    fun capturer(contexte: Context): File? = runCatching {
        val dossier = File(contexte.cacheDir, DOSSIER).apply {
            mkdirs()
            // one report at a time: the previous ones are no longer any use, and letting them pile
            // up in the cache would be a slow leak nobody would see
            listFiles()?.forEach { it.delete() }
        }

        val processus = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-v", "time", "--pid=" + android.os.Process.myPid()),
        )
        val lignes = processus.inputStream.bufferedReader().use { it.readLines() }
        processus.destroy()

        val fichier = File(dossier, "velvet-" + horodatage() + ".txt")
        fichier.writeText(entete(contexte) + lignes.takeLast(LIGNES_MAX).joinToString("\n"))
        fichier
    }.getOrNull()

    /**
     * the share intent, to be launched from an `Activity`
     *
     * `FLAG_GRANT_READ_URI_PERMISSION` is what makes the file readable to the chosen app: without
     * it the provider would refuse, and the share would fail at the least useful moment, after the
     * person has chosen where to send it
     */
    fun partage(contexte: Context, fichier: File): Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            contexte,
            contexte.packageName + ".fichiers",
            fichier,
        )
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, horsUi.rapportSujet)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            horsUi.envoyerLeRapport,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * the header, and it is often worth the log itself
     *
     * the two fixes invisible from the emulator, a test library calling an api removed from android
     * 17, and a package id conflict, would have read at a glance from three lines of model and
     * version
     */
    private fun entete(contexte: Context): String {
        val version = runCatching {
            contexte.packageManager.getPackageInfo(contexte.packageName, 0).versionName
        }.getOrNull() ?: "?"
        return buildString {
            appendLine("Velvet $version")
            appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            appendLine(horodatage())
            appendLine("-".repeat(60))
        }
    }

    private fun horodatage(): String = java.text.SimpleDateFormat(
        "yyyy-MM-dd_HH-mm-ss",
        java.util.Locale.US,
    ).format(java.util.Date())
}

package li.velvetchat.desk

import java.io.File
import java.util.Properties

/**
 * the desktop's small settings, in a file: `%APPDATA%\VelvetDesk\reglages.properties`
 *
 * they used to go through `java.util.prefs`, the registry. found in testing: nothing was ever
 * written there. column widths, frequent emoji and the klipy opt in all restarted from zero on
 * every launch, with not one error line. the wallpaper, filed as a file in the same folder,
 * survived. so the same place, and the same guarantee: every write goes to disk immediately
 */
object Reglages {
    private val fichier: File get() = File(Donnees.racine, "reglages.properties")
    private val valeurs = Properties().apply {
        runCatching { fichier.takeIf { it.isFile }?.inputStream()?.use { load(it) } }
    }

    fun lire(cle: String): String? = synchronized(valeurs) { valeurs.getProperty(cle) }

    fun ecrire(cle: String, valeur: String) = synchronized(valeurs) {
        valeurs.setProperty(cle, valeur)
        // ponytail: the whole file rewritten on every change, and it is a few lines long. a buffer
        // with a deferred write if a setting ever starts changing every frame
        runCatching { fichier.outputStream().use { valeurs.store(it, "VelvetDesk") } }
    }
}

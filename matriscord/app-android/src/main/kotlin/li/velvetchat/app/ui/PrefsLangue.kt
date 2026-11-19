package li.velvetchat.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * the chosen language, or the phone's
 *
 * kept on the device and not in the account: it is a reading setting, not a property of the person,
 * the same person may want english on the work phone and french at home
 */
object PrefsLangue {

    private const val FICHIER = "langue"
    private const val CLE = "choisie"

    fun choisie(contexte: Context): Langue {
        val enregistree = contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .getString(CLE, null)
        // nothing chosen: the system's language, which is the right default answer
        val langue = Langue.depuisCode(enregistree ?: java.util.Locale.getDefault().language)
        // the services and the notifications do not compose: they read `horsUi`, which reads this
        langueCourante = langue
        return langue
    }

    fun choisir(contexte: Context, langue: Langue) {
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
            .edit().putString(CLE, langue.code).apply()
        revision++
    }

    /** compose does not watch the preferences: this counter serves as a reread key */
    var revision by mutableIntStateOf(0)
        private set
}

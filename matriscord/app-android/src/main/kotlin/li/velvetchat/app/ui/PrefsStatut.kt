package li.velvetchat.app.ui

import android.content.Context
import li.velvetchat.core.model.Presence

/**
 * the chosen status, kept on the device and republished at startup
 *
 * where it comes from: you pick "online", close the app, reopen it, and the status is "invisible".
 * measured on screen, and it was not a display bug
 *
 * the cause is in the protocol. matrix does not deduce presence from the connection: a client that
 * syncs without saying anything about its presence stays whatever the server had kept, and the sdk
 * publishes nothing on its own. so the server gave us back `offline`, which is the truth from its
 * point of view. the client is what has to declare itself, on every start
 *
 * why on the device and not in the account data: same reasoning as the emoji favourites. the
 * published status is already on the server, that being exactly what we republish. what we keep
 * here is not the status but the preference, "what i want to be when i connect". putting it in the
 * account data would have it follow you from one device to the next, which is the opposite of what
 * we want: you may want to be visible from your phone and invisible from the office computer
 *
 * the default is "online", and not "publish nothing". publishing nothing means letting the server
 * think we are away while we are reading, so showing the others false information in the direction
 * that discourages them from writing. somebody who wants to disappear says so explicitly, and that
 * choice is kept
 */
object PrefsStatut {

    private const val FICHIER = "statut"
    private const val CLE_PRESENCE = "presence"
    private const val CLE_MESSAGE = "message"

    private fun prefs(contexte: Context) =
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /** what the device has to publish at startup */
    fun choisi(contexte: Context): Pair<Presence, String?> {
        val p = prefs(contexte)
        val presence = p.getString(CLE_PRESENCE, null)
            ?.let { nom -> runCatching { Presence.valueOf(nom) }.getOrNull() }
            // `INCONNU` is never a choice: it is the admission that we do not know, and
            // republishing "i do not know" makes no sense. so it falls back on the default
            ?.takeIf { it != Presence.INCONNU }
            ?: Presence.EN_LIGNE
        return presence to p.getString(CLE_MESSAGE, null)?.takeIf { it.isNotBlank() }
    }

    /**
     * publishing your own typing, or not
     *
     * seeing "x is typing" on what you receive is useful. publishing it is something else: it is a
     * continuous signal to the server and to every member of the room, saying to the second when
     * you hesitate, when you delete, when you give up. it is not an interface effect, it is
     * information about yourself that you emit without thinking
     *
     * the default is "yes", because the indicator is only worth anything reciprocally, a room where
     * everybody sees the others typing without being seen would not work. but it can be turned off,
     * and the setting says what it turns off
     *
     * on the device, like the emoji favourites and the gif provider: what you choose not to emit
     * has no reason to be published in the account data
     */
    fun frappePubliee(contexte: Context): Boolean =
        prefs(contexte).getBoolean(CLE_FRAPPE, true)

    fun setFrappePubliee(contexte: Context, publier: Boolean) {
        prefs(contexte).edit().putBoolean(CLE_FRAPPE, publier).apply()
    }

    private const val CLE_FRAPPE = "frappe_publiee"

    fun retenir(contexte: Context, presence: Presence, message: String?) {
        prefs(contexte).edit()
            .putString(CLE_PRESENCE, presence.name)
            .putString(CLE_MESSAGE, message)
            .apply()
    }
}

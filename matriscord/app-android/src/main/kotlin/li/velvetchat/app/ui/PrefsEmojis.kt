package li.velvetchat.app.ui

import android.content.Context
import li.velvetchat.core.model.TeintePeau

/**
 * what the picker remembers from one time to the next: the favourites and the usage frequency
 *
 * stored on the device, never on the server, and it is a choice to own rather than a convenience:
 * matrix could sync it through the account data, and discord does. but the list of the emojis you
 * use most is a remarkably telling usage profile, it says who you write to and in what tone.
 * putting it on a small server would make it readable by the server's administrator, and keeping
 * it here costs losing it when you change phone, which is the lesser of the two prices
 *
 * `SharedPreferences` rather than a home made file: the payload is a few hundred bytes, `apply()`
 * writes off the main thread, and the file goes with the app
 *
 * the keys carry their family, `u:😂` for a unicode one and `p:PandaLove` for a custom one. without
 * that prefix, a custom emoji named `fire` and the character 🔥 would merge into the same entry, and
 * one's favourite would move the other's
 */
object PrefsEmojis {

    private const val FICHIER = "emojis"
    private const val CLE_FAVORIS = "favoris"
    private const val CLE_USAGES = "usages"
    private const val CLE_TEINTE = "teinte"

    /** past that, the old counts only serve to remember what you no longer do */
    private const val MAX_USAGES = 60

    fun cleDe(choix: ChoixEmoji): String = when (choix) {
        is ChoixEmoji.Unicode -> "u:${choix.caractere}"
        is ChoixEmoji.Personnalise -> "p:${choix.emote.shortcode}"
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /**
     * the chosen skin tone, in an observable state and not behind a plain preference read
     *
     * two surfaces read it, the picker and the autocomplete row, and one of the two changes it. a
     * direct `SharedPreferences` read would not recompose the second: you would change the tone in
     * the picker and the autocomplete would go on offering the old one until the screen was
     * reopened. shared state is the correct behaviour here, not a shortcut
     *
     * a device setting, like the favourites, and for the same reason: the tone you pick for
     * yourself does not have to be published on a small server
     */
    private val teinteVive = androidx.compose.runtime.mutableStateOf<TeintePeau?>(null)

    fun teinte(ctx: Context): TeintePeau {
        teinteVive.value?.let { return it }
        val lue = prefs(ctx).getString(CLE_TEINTE, null)
            ?.let { nom -> TeintePeau.entries.firstOrNull { it.name == nom } }
            ?: TeintePeau.DEFAUT
        teinteVive.value = lue
        return lue
    }

    fun setTeinte(ctx: Context, choisie: TeintePeau) {
        teinteVive.value = choisie
        prefs(ctx).edit().putString(CLE_TEINTE, choisie.name).apply()
    }

    fun favoris(ctx: Context): List<String> =
        prefs(ctx).getString(CLE_FAVORIS, "").orEmpty()
            .split('\n').filter { it.isNotBlank() }

    /**
     * toggles the favourite and returns the new list
     *
     * new favourites are added at the end: a favourite's place is a visual landmark, and seeing it
     * jump to the front on every addition would mean hunting again for the ones you had learned to
     * find
     */
    fun basculerFavori(ctx: Context, cle: String): List<String> {
        val actuel = favoris(ctx)
        val nouveau = if (cle in actuel) actuel - cle else actuel + cle
        prefs(ctx).edit().putString(CLE_FAVORIS, nouveau.joinToString("\n")).apply()
        return nouveau
    }

    /** the keys already used, from the most frequent to the least */
    fun frequents(ctx: Context): List<String> =
        lireUsages(ctx).entries.sortedByDescending { it.value }.map { it.key }

    fun noterUsage(ctx: Context, cle: String): List<String> {
        val usages = lireUsages(ctx).toMutableMap()
        usages[cle] = (usages[cle] ?: 0) + 1
        val gardes = usages.entries.sortedByDescending { it.value }.take(MAX_USAGES)
        prefs(ctx).edit()
            .putString(CLE_USAGES, gardes.joinToString("\n") { "${it.value}\t${it.key}" })
            .apply()
        return gardes.map { it.key }
    }

    private fun lireUsages(ctx: Context): Map<String, Int> =
        prefs(ctx).getString(CLE_USAGES, "").orEmpty()
            .split('\n').filter { it.isNotBlank() }
            .mapNotNull { ligne ->
                val sep = ligne.indexOf('\t')
                if (sep <= 0) return@mapNotNull null
                val n = ligne.take(sep).toIntOrNull() ?: return@mapNotNull null
                ligne.substring(sep + 1) to n
            }
            .toMap()
}

/**
 * the picker's settings, as the android app files them
 *
 * the panel lives in `ui-commun` and no longer knows a `Context`, it reads this contract.
 * `PrefsEmojis` keeps all the machinery, observable states included, and this class only presents
 * it in the expected shape
 */
class ReglagesEmojisAndroid(private val contexte: android.content.Context) : ReglagesEmojis {
    override fun teinte(): li.velvetchat.core.model.TeintePeau = PrefsEmojis.teinte(contexte)
    override fun setTeinte(teinte: li.velvetchat.core.model.TeintePeau) =
        PrefsEmojis.setTeinte(contexte, teinte)
    override fun favoris(): List<String> = PrefsEmojis.favoris(contexte)
    override fun basculerFavori(cle: String): List<String> =
        PrefsEmojis.basculerFavori(contexte, cle)
    override fun frequents(): List<String> = PrefsEmojis.frequents(contexte)
    override fun noterUsage(cle: String) {
        // the explicit body discards the return value: `PrefsEmojis.noterUsage` returns the usage
        // table, which the contract does not need
        PrefsEmojis.noterUsage(contexte, cle)
    }
}

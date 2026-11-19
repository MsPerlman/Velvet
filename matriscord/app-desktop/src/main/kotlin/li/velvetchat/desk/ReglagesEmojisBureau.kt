package li.velvetchat.desk

import androidx.compose.runtime.mutableStateOf
import li.velvetchat.app.ui.ReglagesEmojis
import li.velvetchat.core.model.TeintePeau

/**
 * the emoji picker's memory on the desktop: favourites and most used, kept from one launch to the
 * next
 *
 * the desktop took `ReglagesEmojis`' in memory default, so the most used emoji the hover bar shows
 * on a message restarted from zero on every launch. filed in the `Reglages` file, like the column
 * widths
 *
 * the skin tone stays in memory: nothing changes it on the desktop yet
 */
object ReglagesEmojisBureau : ReglagesEmojis {

    private val teinte = mutableStateOf(TeintePeau.DEFAUT)
    private val favoris = mutableStateOf(Reglages.lire("emojis.favoris").orEmpty().lines().filter { it.isNotBlank() })
    private val usages = mutableStateOf(
        Reglages.lire("emojis.usages").orEmpty().lines().mapNotNull { ligne ->
            val tab = ligne.lastIndexOf('\t')
            if (tab <= 0) null else ligne.substring(0, tab) to (ligne.substring(tab + 1).toIntOrNull() ?: 0)
        }.toMap(),
    )

    override fun teinte(): TeintePeau = teinte.value

    override fun setTeinte(teinte: TeintePeau) {
        this.teinte.value = teinte
    }

    override fun favoris(): List<String> = favoris.value

    override fun basculerFavori(cle: String): List<String> {
        favoris.value = if (cle in favoris.value) favoris.value - cle else favoris.value + cle
        Reglages.ecrire("emojis.favoris", favoris.value.joinToString("\n"))
        return favoris.value
    }

    override fun frequents(): List<String> =
        usages.value.entries.sortedByDescending { it.value }.map { it.key }

    override fun noterUsage(cle: String) {
        usages.value = usages.value + (cle to (usages.value[cle] ?: 0) + 1)
        // ponytail: sixty keys kept, the most used ones. the whole history adds nothing to the sort
        Reglages.ecrire(
            "emojis.usages",
            usages.value.entries.sortedByDescending { it.value }.take(60)
                .joinToString("\n") { "${it.key}\t${it.value}" },
        )
    }
}

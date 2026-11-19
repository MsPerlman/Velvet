package li.velvetchat.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import li.velvetchat.core.model.TeintePeau

/**
 * what the emoji picker keeps from one opening to the next
 *
 * the panel itself knows no platform: it draws a grid, it searches, it files things into sections.
 * the one thing anchoring it to android was its memory, the chosen skin tone, the favourites, the
 * most used. three lists and an integer, stored in `SharedPreferences`
 *
 * an interface rather than an `expect`/`actual`, and the reason is the direction of the dependency:
 * an `expect` would make persistence a detail of the panel, when it is the other way round. the app
 * knows where it keeps its settings and the panel uses them. supplying it through a
 * `CompositionLocal` also lets tests and previews do without it and configure nothing: the default
 * below lives in memory and forgets on close
 *
 * the tone has to be observable
 *
 * `teinte()` is read during composition, and changing tone has to redraw the grid there and then,
 * without rebuilding it or scrolling it. an implementation that read the disk on every call would
 * break both: it would be slow, and above all compose would have no reason to recompose. so it has
 * to read observable state, see [ReglagesEmojisMemoire], which shows the expected shape
 */
interface ReglagesEmojis {

    /** the skin tone applied to the emoji that accept one */
    fun teinte(): TeintePeau

    fun setTeinte(teinte: TeintePeau)

    /** the favourited keys, in the order they should be shown */
    fun favoris(): List<String>

    /** @return the favourites list after the toggle, which the caller displays immediately */
    fun basculerFavori(cle: String): List<String>

    /** the most used keys, most frequent first */
    fun frequents(): List<String>

    /** counts one use. called on every send, so often: keep it cheap */
    fun noterUsage(cle: String)
}

/**
 * the default: a memory that does not survive closing
 *
 * it is not a stopgap for production, both apps supply their own, but it is what lets the panel
 * open inside a test or a preview with no storage set up. and it shows the expected shape,
 * observable state rather than disk reads
 */
class ReglagesEmojisMemoire : ReglagesEmojis {
    private val teinte = mutableStateOf(TeintePeau.DEFAUT)
    private val favoris = mutableStateOf<List<String>>(emptyList())
    private val usages = mutableStateOf<Map<String, Int>>(emptyMap())

    override fun teinte(): TeintePeau = teinte.value

    override fun setTeinte(teinte: TeintePeau) {
        this.teinte.value = teinte
    }

    override fun favoris(): List<String> = favoris.value

    override fun basculerFavori(cle: String): List<String> {
        favoris.value = if (cle in favoris.value) favoris.value - cle else favoris.value + cle
        return favoris.value
    }

    override fun frequents(): List<String> =
        usages.value.entries.sortedByDescending { it.value }.map { it.key }

    override fun noterUsage(cle: String) {
        usages.value = usages.value + (cle to (usages.value[cle] ?: 0) + 1)
    }
}

val LocalReglagesEmojis = staticCompositionLocalOf<ReglagesEmojis> { ReglagesEmojisMemoire() }

/**
 * the key an emoji choice is remembered under
 *
 * prefixed by its family: a custom emoji named `blahaj` and a unicode character must not be
 * confused in the same favourites list, and the prefix is what guarantees they cannot be, even if
 * somebody one day names an emoji after a character
 */
fun cleDeChoix(choix: ChoixEmoji): String = when (choix) {
    is ChoixEmoji.Unicode -> "u:${choix.caractere}"
    is ChoixEmoji.Personnalise -> "p:${choix.emote.shortcode}"
}

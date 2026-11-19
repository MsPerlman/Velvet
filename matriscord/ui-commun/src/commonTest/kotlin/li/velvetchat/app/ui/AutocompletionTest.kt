package li.velvetchat.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import li.velvetchat.core.model.TeintePeau

/**
 * the autocomplete, judged side by side with the reference
 *
 * two screenshots were put next to each other and the verdict was clear, discord's behaviour was
 * better. the reference offered three emoji for `:joy`, including `:rofl:`, which does not contain
 * "joy". velvet offered one, because it only searched the name. the catalogue had carried the
 * keywords from the start and nobody was reading them
 */
class AutocompletionTest {

    private fun cherche(texte: String) =
        suggestions(texte, emptyList(), emptyList(), emptyList())

    private fun raccourcis(texte: String) = cherche(texte)?.second.orEmpty()
        .filterIsInstance<Suggestion.Unicode>()
        .map { it.emoji.raccourci }

    @Test
    fun `un mot-cle trouve un emoji dont le nom ne dit rien`() {
        // the french word for laughing appears in no shortcode: it is a keyword and nothing else
        val trouves = raccourcis(":rire")
        assertTrue("joy" in trouves, "attendu joy parmi $trouves")
        assertTrue("rofl" in trouves, "attendu rofl parmi $trouves")
    }

    @Test
    fun `le nom passe avant le sens`() {
        // somebody typing `:joy` wants `:joy:` at the top, and `:rofl:`, found by keyword, is
        // allowed to follow, which is exactly what the reference shows
        val trouves = raccourcis(":joy")
        assertEquals("joy", trouves.first())
        assertTrue("rofl" in trouves, "attendu rofl parmi $trouves")
    }

    @Test
    fun `deux-points seul ne propose rien`() {
        // "18:30" must open nothing, and the two character threshold is what stops it
        assertNull(cherche("rendez-vous à 18:"))
        assertNull(cherche("rendez-vous à 18:3"))
    }

    @Test
    fun `completer insere le caractere et non le raccourci`() {
        val choix = cherche(":joy")!!.second.first()
        assertEquals("😂 ", completer(":joy", choix, TeintePeau.DEFAUT))
    }

    @Test
    fun `un fragment sans correspondance ne rend rien`() {
        assertNull(cherche(":zzzzq"))
    }
}

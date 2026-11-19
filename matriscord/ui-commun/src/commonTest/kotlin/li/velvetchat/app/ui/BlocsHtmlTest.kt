package li.velvetchat.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** quotes and code blocks come out of the html in order, with the text around them */
class BlocsHtmlTest {
    @Test
    fun decoupeCitationCodeEtTexte() {
        val blocs = decouperBlocs(
            "avant<blockquote><p>cité</p></blockquote>\n<pre><code>a &lt; b</code></pre>après",
        )
        assertEquals(
            listOf(GenreBloc.TEXTE, GenreBloc.CITATION, GenreBloc.CODE, GenreBloc.TEXTE),
            blocs.map { it.genre },
        )
        assertEquals("a < b", analyserHtml(blocs[2].html).texte.trim('\n'))
    }

    @Test
    fun sansBlocRienNeChange() {
        assertEquals(listOf(GenreBloc.TEXTE), decouperBlocs("<b>gras</b>").map { it.genre })
    }
}

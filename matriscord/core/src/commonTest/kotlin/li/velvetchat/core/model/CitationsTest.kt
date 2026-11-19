package li.velvetchat.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * what these tests protect: the quote we rebuild ourselves when the sdk has not resolved the
 * target. it is computation over data, so it checks here in milliseconds, unlike the same kind of
 * logic locked inside a screen, which costs a build cycle per bug
 */
class CitationsTest {

    private fun texte(
        id: String,
        corps: String,
        html: String? = null,
        sender: String = "@jean:exemple.org",
        nom: String? = "Jean",
        replyTo: String? = null,
        citation: Citation? = null,
    ) = Message(
        id = id,
        sender = sender,
        senderDisplayName = nom,
        timestamp = 0,
        isOwn = false,
        isEdited = false,
        body = MessageBody.Text(corps, html),
        replyTo = replyTo,
        citation = citation,
    )

    @Test
    fun `la cible presente dans la liste devient une citation`() {
        val liste = listOf(
            texte("e1", "bonjour"),
            texte("e2", "salut", replyTo = "e1"),
        )
        val citation = liste.avecCitations()[1].citation
        assertEquals("e1", citation?.eventId)
        assertEquals("Jean", citation?.auteur)
        assertEquals("@jean:exemple.org", citation?.auteurId)
        assertEquals("bonjour", citation?.apercu)
    }

    @Test
    fun `une cible hors de la liste laisse la citation absente`() {
        val liste = listOf(texte("e2", "salut", replyTo = "e-vieux"))
        assertNull(liste.avecCitations()[0].citation)
    }

    @Test
    fun `la citation deja resolue par le SDK est prioritaire`() {
        // theirs is right even when the target is outside the loaded window. ours is not
        val duSdk = Citation("e1", "@x:exemple.org", "X", null, "venu du SDK")
        val liste = listOf(
            texte("e1", "autre chose"),
            texte("e2", "salut", replyTo = "e1", citation = duSdk),
        )
        assertEquals("venu du SDK", liste.avecCitations()[1].citation?.apercu)
    }

    @Test
    fun `une liste sans reponse est rendue telle quelle`() {
        // not merely equal, the same list. a room with no replies must not pay for a map and a
        // copy on every timeline snapshot
        val liste = listOf(texte("e1", "bonjour"), texte("e2", "salut"))
        assertSame(liste, liste.avecCitations())
    }

    @Test
    fun `l apercu prefere le HTML rendu a la source markdown`() {
        // the bug as seen on screen: the quote showed the markdown source, because `body` carries
        // it while `formatted_body` carries the rendering
        val corps = MessageBody.Text("**gras** et _italique_", "<b>gras</b> et <i>italique</i>")
        assertEquals("gras et italique", corps.enApercu())
    }

    @Test
    fun `l apercu retombe sur le texte brut quand le HTML est vide de texte`() {
        // a lone custom emoji: `<img>` carries no text at all, and returning an empty line would
        // be worse than the shortcode
        val corps = MessageBody.Text(":blahaj:", "<img src=\"mxc://x/y\" alt=\":blahaj:\">")
        assertEquals(":blahaj:", corps.enApercu())
    }

    @Test
    fun `un media est nomme par ce qu il est, jamais par son fichier`() {
        val ref = MediaRef("mxc://s/1", "IMG_4821.jpg", mimeType = null, sizeBytes = null)
        assertEquals(
            "Photo",
            MessageBody.Media("IMG_4821.jpg", MediaKind.IMAGE, ref).enApercu(),
        )
        assertEquals(
            "Message vocal",
            MessageBody.Media("audio.ogg", MediaKind.AUDIO, ref).enApercu(),
        )
    }

    @Test
    fun `l apercu tient sur une ligne et reste borne`() {
        val long = "mot ".repeat(60)
        val apercu = MessageBody.Text("ligne un\nligne deux").enApercu()
        assertEquals("ligne un ligne deux", apercu)
        assertTrue(MessageBody.Text(long).enApercu().length <= 80)
    }

    @Test
    fun `les entites sont detendues, et l esperluette en dernier`() {
        // `&amp;lt;` has to give `&lt;`. unescaping `&amp;` first would give `<`, which is to say
        // it would rebuild a tag out of text that was never one
        assertEquals("&lt;", texteSansBalises("&amp;lt;"))
        assertEquals("a < b & c", texteSansBalises("<p>a &lt; b &amp; c</p>"))
    }
}

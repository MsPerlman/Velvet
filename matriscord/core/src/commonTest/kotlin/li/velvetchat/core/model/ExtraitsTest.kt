package li.velvetchat.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * what these tests protect: three lists that, when they get it wrong, crash nothing
 *
 * a missed link, a duplicate, a pin quietly absent, none of it produces an error. you only notice
 * by looking for something you know you sent and not finding it. exactly the class of bug a test
 * catches and a screen does not
 */
class ExtraitsTest {

    private fun texte(id: String, corps: String, quand: Long = 0L) = Message(
        id = id,
        sender = "@a:exemple.org",
        senderDisplayName = "A",
        timestamp = quand,
        isOwn = false,
        isEdited = false,
        body = MessageBody.Text(corps),
    )

    private fun media(id: String, kind: MediaKind, quand: Long = 0L) = Message(
        id = id,
        sender = "@a:exemple.org",
        senderDisplayName = "A",
        timestamp = quand,
        isOwn = false,
        isEdited = false,
        body = MessageBody.Media(
            caption = "x",
            kind = kind,
            media = MediaRef(source = "s", filename = "f", mimeType = null, sizeBytes = null),
        ),
    )

    // ------------------------------------------------------------------ the links

    @Test
    fun `un lien est releve avec son hote`() {
        val liens = Extraits.liens(listOf(texte("1", "regarde https://exemple.org/page")))
        assertEquals(1, liens.size)
        assertEquals("https://exemple.org/page", liens[0].url)
        assertEquals("exemple.org", liens[0].hote)
    }

    @Test
    fun `le www est retire de l hote mais pas de l adresse`() {
        val liens = Extraits.liens(listOf(texte("1", "https://www.exemple.org/a")))
        assertEquals("exemple.org", liens[0].hote)
        assertEquals("https://www.exemple.org/a", liens[0].url)
    }

    @Test
    fun `la ponctuation finale d une phrase n appartient pas a l adresse`() {
        assertEquals("https://exemple.org/a", Extraits.nettoyer("https://exemple.org/a."))
        assertEquals("https://exemple.org/a", Extraits.nettoyer("https://exemple.org/a,"))
        assertEquals("https://exemple.org/a", Extraits.nettoyer("https://exemple.org/a)"))
        assertEquals("https://exemple.org/a", Extraits.nettoyer("https://exemple.org/a?!"))
    }

    @Test
    fun `une parenthese ouverte dans l adresse protege la fermante`() {
        // the wikipedia case: cutting here would give a dead link
        assertEquals(
            "https://fr.wikipedia.org/wiki/Velvet_(album)",
            Extraits.nettoyer("https://fr.wikipedia.org/wiki/Velvet_(album)"),
        )
    }

    @Test
    fun `une mention n est pas un lien`() {
        // the protocol expresses a mention as a matrix.to link. counting it here would fill the
        // tab with addresses nobody typed
        val liens = Extraits.liens(
            listOf(texte("1", "coucou https://matrix.to/#/@papa:exemple.org ça va ?"))
        )
        assertTrue(liens.isEmpty())
    }

    @Test
    fun `le meme lien partage trois fois ne fait qu une ligne, la plus recente`() {
        val liens = Extraits.liens(
            listOf(
                texte("1", "https://exemple.org/a", quand = 100),
                texte("2", "https://exemple.org/a", quand = 300),
                texte("3", "https://exemple.org/a", quand = 200),
            )
        )
        assertEquals(1, liens.size)
        assertEquals("2", liens[0].message.id)
    }

    @Test
    fun `plusieurs liens dans un meme message sont tous releves`() {
        val liens = Extraits.liens(
            listOf(texte("1", "https://a.org/x et aussi https://b.org/y"))
        )
        assertEquals(listOf("https://a.org/x", "https://b.org/y"), liens.map { it.url })
    }

    @Test
    fun `les liens sont rendus du plus recent au plus ancien`() {
        val liens = Extraits.liens(
            listOf(
                texte("vieux", "https://a.org", quand = 10),
                texte("neuf", "https://b.org", quand = 90),
            )
        )
        assertEquals(listOf("neuf", "vieux"), liens.map { it.message.id })
    }

    @Test
    fun `un message sans lien ne produit rien`() {
        assertTrue(Extraits.liens(listOf(texte("1", "bonjour tout le monde"))).isEmpty())
        assertTrue(Extraits.liens(emptyList()).isEmpty())
    }

    // ------------------------------------------------------------------- the media

    @Test
    fun `seules les images et videos entrent dans la galerie`() {
        val trouves = Extraits.medias(
            listOf(
                media("img", MediaKind.IMAGE, quand = 1),
                media("vid", MediaKind.VIDEO, quand = 2),
                media("son", MediaKind.AUDIO, quand = 3),
                media("doc", MediaKind.FILE, quand = 4),
                texte("txt", "bonjour", quand = 5),
            )
        )
        // a voice note and a pdf in a thumbnail grid would give grey squares
        assertEquals(listOf("vid", "img"), trouves.map { it.id })
    }

    // -------------------------------------------------------------------- the pins

    @Test
    fun `les epingles sortent de la plus recente a la plus ancienne`() {
        // the engine returns them in conversation order, and a pin list reads the other way
        // round. the three tests that used to sit next to this one covered resolving ids against
        // the loaded window, machinery that is gone now the sdk hands back the messages
        val ordonnees = Extraits.epingles(
            listOf(texte("a", "un"), texte("b", "deux"), texte("c", "trois"))
        )
        assertEquals(listOf("c", "b", "a"), ordonnees.map { it.id })
    }
}

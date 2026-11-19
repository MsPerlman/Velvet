package li.velvetchat.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * what these tests cover, and why they exist
 *
 * translating typed text into the protocol is the one step where a mistake is invisible: the
 * message goes out, it renders for everybody, and nothing says the person named in it was never
 * notified. that is exactly what happened, and it happened because the code lived in the android
 * module, where checking it meant building an apk and looking at it
 */
class ComposeMessageTest {

    private val papa = Member("@papa:exemple.org", "Papa", null, null)
    private val jean = Member("@jean:exemple.org", "Jean", null, null)
    private val jeanMichel = Member("@jm:exemple.org", "Jean-Michel", null, null)
    private val general = salon("!abc:exemple.org", "general")
    private val blahaj = CustomEmote("blahaj", "mxc://exemple.org/xyz")

    private fun salon(id: String, nom: String) =
        Channel(id, nom, null, ChannelKind.TEXT, Unread(0, 0))

    @Test
    fun `un texte ordinaire reste du markdown`() {
        val r = composerMessage("Bonjour tout le monde", emptyList(), listOf(papa), listOf(general))
        assertNull(r.html, "sans emoji ni mention, on ne doit pas forcer du HTML")
        assertTrue(r.mentions.isEmpty())
    }

    @Test
    fun `une mention devient un lien et notifie`() {
        val r = composerMessage("Coucou @Papa", emptyList(), listOf(papa), emptyList())
        assertEquals(listOf("@papa:exemple.org"), r.mentions)
        assertTrue(
            r.html!!.contains("""<a href="https://matrix.to/#/@papa:exemple.org">@Papa</a>"""),
            "html obtenu : ${r.html}",
        )
        assertEquals("Coucou @Papa", r.corps, "le corps garde le nom en clair")
    }

    @Test
    fun `le plus long nom gagne`() {
        val r = composerMessage(
            "salut @Jean-Michel", emptyList(), listOf(jean, jeanMichel), emptyList(),
        )
        assertEquals(listOf("@jm:exemple.org"), r.mentions)
        assertTrue(r.html!!.endsWith("@Jean-Michel</a>"), "html obtenu : ${r.html}")
    }

    @Test
    fun `un salon devient un lien avec son via`() {
        val r = composerMessage("rendez-vous dans #general", emptyList(), emptyList(), listOf(general))
        assertTrue(
            r.html!!.contains("""href="https://matrix.to/#/!abc:exemple.org?via=exemple.org""""),
            "html obtenu : ${r.html}",
        )
        assertTrue(r.mentions.isEmpty(), "un salon ne notifie personne")
    }

    @Test
    fun `emoji et mention cohabitent dans le meme message`() {
        val r = composerMessage("@Papa :blahaj:", listOf(blahaj), listOf(papa), emptyList())
        assertTrue(r.html!!.contains("data-mx-emoticon"), r.html)
        assertTrue(r.html.contains("matrix.to"), r.html)
        assertEquals(listOf("@papa:exemple.org"), r.mentions)
    }

    @Test
    fun `le nom ne doit pas etre reecrit a l interieur d un lien deja pose`() {
        val autre = salon("!x:exemple.org", "gen")
        val r = composerMessage("#general et #gen", emptyList(), emptyList(), listOf(general, autre))
        assertEquals(2, Regex("<a ").findAll(r.html!!).count(), "html obtenu : ${r.html}")
    }

    @Test
    fun `le cas exact vu a l ecran`() {
        val test2 = Member("@compte-essai2:exemple.org", "compte-essai2", null, null)
        val dev = salon("!dev:exemple.org", "dev")
        val r = composerMessage(
            "Coucou @compte-essai2 dans #dev", emptyList(), listOf(test2), listOf(dev),
        )
        assertEquals(listOf("@compte-essai2:exemple.org"), r.mentions, "html : ${r.html}")
    }

    @Test
    fun `arobase room interpelle tout le salon`() {
        val r = composerMessage("@room on mange", emptyList(), emptyList(), emptyList())
        assertTrue(r.mentionneToutLeSalon)
    }

    @Test
    fun `le html est echappe avant tout`() {
        val r = composerMessage("<script> @Papa", emptyList(), listOf(papa), emptyList())
        assertTrue(r.html!!.startsWith("&lt;script&gt;"), "html obtenu : ${r.html}")
    }
}

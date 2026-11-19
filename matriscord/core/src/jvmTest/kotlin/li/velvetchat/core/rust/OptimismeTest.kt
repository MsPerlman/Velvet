package li.velvetchat.core.rust

import kotlin.test.Test
import kotlin.test.assertEquals
import li.velvetchat.core.model.Categorie
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.ChannelKind
import li.velvetchat.core.model.Server
import li.velvetchat.core.model.Unread

/**
 * the overlay is what the user sees between a gesture and synapse's answer, so the two cases that
 * would break trust are tested: a gesture that does not show, and a draft that never goes away
 */
class OptimismeTest {
    private fun salon(id: String) = Channel(id, id, null, ChannelKind.TEXT, Unread.NONE)
    private val base = listOf(Server("!s", "s", null, listOf(salon("!a"), salon("!b"))))

    @Test
    fun `un geste se voit avant la confirmation`() {
        val o = RustMatrixEngine.Optimisme(partis = setOf("!b"))
            .avecSalon("!s", salon("brouillon:c"))
            .avecCategorie("!s", Categorie("brouillon:k", "k"))
            .copy(serveurs = listOf(Server("brouillon:x", "x", null, emptyList())))
        val vu = o.sur(base)
        assertEquals(listOf("!s", "brouillon:x"), vu.map { it.id })
        assertEquals(listOf("!a", "brouillon:c"), vu[0].channels.map { it.id })
        assertEquals(listOf("brouillon:k"), vu[0].categories.map { it.id })
    }

    @Test
    fun `la vraie liste reprend la main`() {
        val o = RustMatrixEngine.Optimisme(partis = setOf("!b", "!parti"))
            .avecSalon("!s", salon("!a"))
            .copy(serveurs = listOf(Server("!s", "s", null, emptyList())))
        // `!b` is still there, so its departure is still pending; the rest is now real
        assertEquals(RustMatrixEngine.Optimisme(partis = setOf("!b")), o.elaguer(base))
        assertEquals(base, RustMatrixEngine.Optimisme().sur(base))
    }
}

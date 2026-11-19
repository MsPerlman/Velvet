package li.velvetchat.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * the invite list of a new group conversation
 *
 * none of these three cases fails loudly at the call site. the server refuses the whole
 * `createRoom` when one invite is bad, so a duplicate or the creator's own id costs the group
 * rather than the bad entry
 */
class GroupeDirectTest {

    private val moi = "@moi:example.org"

    @Test
    fun `deux personnes passent telles quelles`() {
        assertEquals(
            listOf("@a:example.org", "@b:example.org"),
            invitesDeGroupe(listOf("@a:example.org", "@b:example.org"), moi),
        )
    }

    /**
     * the creator is already in the room, and a server asked to invite them refuses the call
     */
    @Test
    fun `on ne s invite pas soi-meme`() {
        val invites = invitesDeGroupe(listOf("@a:example.org", moi, "@b:example.org"), moi)
        assertEquals(listOf("@a:example.org", "@b:example.org"), invites)
        assertTrue(groupeAssezGrand(invites))
    }

    /** one failed invite takes the whole room creation with it, so the duplicate costs the group */
    @Test
    fun `la meme personne deux fois ne compte qu une`() {
        val invites = invitesDeGroupe(listOf("@a:example.org", "@a:example.org"), moi)
        assertEquals(listOf("@a:example.org"), invites)
        assertFalse(groupeAssezGrand(invites), "une seule personne n est pas un groupe")
    }

    /**
     * a trailing space makes a different string, so the deduplication would miss it and the
     * server would get an id it does not know
     */
    @Test
    fun `les blancs sont retires avant de dedupliquer`() {
        assertEquals(
            listOf("@a:example.org"),
            invitesDeGroupe(listOf(" @a:example.org", "@a:example.org ", "   ", ""), moi),
        )
    }

    /**
     * with one person the right call is `openDirect`, which finds the conversation that already
     * exists. with none there is nothing to create
     */
    @Test
    fun `le plancher est a deux`() {
        assertFalse(groupeAssezGrand(emptyList()))
        assertFalse(groupeAssezGrand(listOf("@a:example.org")))
        assertTrue(groupeAssezGrand(listOf("@a:example.org", "@b:example.org")))
    }

    /** a list made only of yourself leaves nothing, and must not come out as a group of one */
    @Test
    fun `une liste reduite a soi-meme ne fait pas de groupe`() {
        val invites = invitesDeGroupe(listOf(moi, moi), moi)
        assertTrue(invites.isEmpty())
        assertFalse(groupeAssezGrand(invites))
    }
}

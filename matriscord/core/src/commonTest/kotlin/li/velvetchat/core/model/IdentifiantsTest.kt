package li.velvetchat.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * the fallback is the only thing standing between a renamed key and data that looks lost
 *
 * these are cheap checks, and they are here because the failure they guard against is silent:
 * a wrong translation returns a key nobody ever wrote, the server answers 404, and the app
 * shows an empty bio instead of an error
 */
class IdentifiantsTest {

    /**
     * the list is empty today, so a key has no other name
     *
     * the test does not pin the emptiness: it pins the **rule**, by checking it against a
     * sample list. that way it keeps saying something the day a prefix comes back
     */
    @Test
    fun `sans ancien prefixe une cle n a pas d autre nom`() {
        assertEquals(emptyList(), Identifiants.anciennes("li.velvetchat.bio"))
        assertEquals(listOf("li.velvetchat.bio"), Identifiants.toutesLesFormes("li.velvetchat.bio"))
    }

    @Test
    fun `le suffixe compose serait conserve entier`() {
        val essai = listOf("ancien.exemple.", "plus.ancien.")
        assertEquals(
            listOf("ancien.exemple.cosmetics.plaques", "plus.ancien.cosmetics.plaques"),
            essai.map { it + "li.velvetchat.cosmetics.plaques".removePrefix(Identifiants.PREFIXE) },
        )
    }

    @Test
    fun `une cle qui n est pas la notre n a pas d ancien nom`() {
        assertEquals(emptyList(), Identifiants.anciennes("m.room.member"))
        assertEquals(listOf("m.room.member"), Identifiants.toutesLesFormes("m.room.member"))
    }

    @Test
    fun `l ordre de lecture commence par le nom courant`() {
        val formes = Identifiants.toutesLesFormes("li.velvetchat.notes")
        assertEquals("li.velvetchat.notes", formes.first())
        assertEquals(1 + Identifiants.ANCIENS.size, formes.size)
    }

    /**
     * a prefix that does not end in a dot would glue the domain to the field
     * (`li.velvetchatbio`), and the mistake would only show at runtime
     */
    @Test
    fun `tous les prefixes finissent par un point`() {
        assertTrue(Identifiants.PREFIXE.endsWith("."))
        Identifiants.ANCIENS.forEach { assertTrue(it.endsWith("."), it) }
    }

    @Test
    fun `aucun prefixe n est repete`() {
        val tous = listOf(Identifiants.PREFIXE) + Identifiants.ANCIENS
        assertEquals(tous.size, tous.toSet().size)
    }
}

package li.velvetchat.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * the activity, and the fact that it arrives from somebody else's profile
 *
 * this is untrusted input: a profile is written by its owner, and what we read is whatever they
 * — or whatever client they run — chose to put there. so the parser drops what it does not
 * understand instead of throwing, exactly like the theme loader
 */
class ActiviteTest {

    @Test
    fun `un aller-retour garde tous les champs`() {
        val avant = Activite(
            nom = "Celeste",
            details = "Chapitre 3",
            etat = "1 mort",
            depuis = 1_759_000_000_000,
            grandeImage = "mxc://example.org/abc",
            petiteImage = "mxc://example.org/def",
            applicationId = "123456789",
            publieeLe = 1_759_000_123_456,
        )
        assertEquals(avant, activiteDepuisJson(avant.versJson()))
    }

    @Test
    fun `le nom seul suffit`() {
        val a = Activite(nom = "Factorio")
        val relu = activiteDepuisJson(a.versJson())
        assertEquals(a, relu)
        assertFalse(a.versJson().contains("\"d\""), "un champ nul ne s ecrit pas")
    }

    @Test
    fun `sans nom, pas d activite`() {
        assertNull(activiteDepuisJson("""{"d":"Chapitre 3"}"""))
        assertNull(activiteDepuisJson("""{"n":""}"""))
        assertNull(activiteDepuisJson("""{"n":"   "}"""))
    }

    @Test
    fun `une entree vide ou absurde rend null et ne jette pas`() {
        assertNull(activiteDepuisJson(null))
        assertNull(activiteDepuisJson(""))
        assertNull(activiteDepuisJson("   "))
        assertNull(activiteDepuisJson("pas du json"))
        assertNull(activiteDepuisJson("[1,2,3]"), "un tableau n est pas un objet")
        assertNull(activiteDepuisJson("""{"n":{"imbrique":true}}"""), "un objet n est pas un nom")
    }

    @Test
    fun `un horodatage non positif est ignore`() {
        assertNull(activiteDepuisJson("""{"n":"Jeu","t":-5}""")?.depuis)
        assertNull(activiteDepuisJson("""{"n":"Jeu","t":0}""")?.depuis)
        assertNull(activiteDepuisJson("""{"n":"Jeu","t":"demain"}""")?.depuis)
        assertEquals(42L, activiteDepuisJson("""{"n":"Jeu","t":42}""")?.depuis)
    }

    @Test
    fun `les champs trop longs sont coupes`() {
        val long = "a".repeat(500)
        val relu = activiteDepuisJson(Activite(nom = long, details = long).versJson())
        assertEquals(128, relu?.nom?.length)
        assertEquals(128, relu?.details?.length)
        assertEquals(128, activiteDepuisJson("""{"n":"$long"}""")?.nom?.length)
    }

    @Test
    fun `une activite trop vieille est perimee`() {
        val t = 1_000_000_000L
        val a = Activite(nom = "Jeu", publieeLe = t)
        assertFalse(a.perimee(t), "fraiche")
        assertFalse(a.perimee(t + Activite.REFRESH_MS), "un rafraichissement de retard")
        assertFalse(a.perimee(t + Activite.PEREMPTION_MS), "le seuil lui-meme tient")
        assertTrue(a.perimee(t + Activite.PEREMPTION_MS + 1))
        assertTrue(Activite(nom = "Jeu").perimee(t), "sans date d ecriture on ne croit rien")
    }

    @Test
    fun `le rafraichissement laisse de la marge avant la peremption`() {
        assertTrue(
            Activite.PEREMPTION_MS >= 3 * Activite.REFRESH_MS,
            "au moins trois rafraichissements de marge",
        )
        assertTrue(
            Activite.MIN_ECART_MS < Activite.REFRESH_MS,
            "le plancher entre deux ecritures est plus court que le battement",
        )
    }

    @Test
    fun `la duree ecoulee se lit comme une horloge`() {
        assertEquals("00:00", dureeEcoulee(0))
        assertEquals("00:09", dureeEcoulee(9_999))
        assertEquals("01:00", dureeEcoulee(60_000))
        assertEquals("59:59", dureeEcoulee(3_599_000))
        assertEquals("1:00:00", dureeEcoulee(3_600_000))
        assertEquals("12:34:56", dureeEcoulee(((12 * 3600) + (34 * 60) + 56) * 1000L))
    }

    @Test
    fun `une horloge en retard ne fait pas reculer le compteur`() {
        assertEquals("00:00", dureeEcoulee(-120_000))
    }
}

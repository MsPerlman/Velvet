package li.velvetchat.desk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ActiviteDiscordTest {

    private fun charge(brut: String) = Json.parseToJsonElement(brut) as JsonObject

    @Test
    fun `un jeu prend le nom de son processus`() {
        val a = activiteDepuisDiscord(
            charge("""{"details":"Chapitre 3","state":"1 mort"}"""),
            processus = "Celeste",
        )
        assertEquals("Celeste", a?.nom)
        assertEquals("Chapitre 3", a?.details)
        assertEquals("1 mort", a?.etat)
    }

    @Test
    fun `un nom dans la charge passe devant le processus`() {
        val a = activiteDepuisDiscord(
            charge("""{"name":"Modrinth App","state":"Idling..."}"""),
            processus = "Modrinth App",
        )
        assertEquals("Modrinth App", a?.nom)
    }

    @Test
    fun `PreMiD lit le texte de la grande image plutot que son propre nom`() {
        val a = activiteDepuisDiscord(
            charge(
                """{"details":"Une video quelconque","state":"par quelqu un",
                   "assets":{"large_image":"yt","large_text":"YouTube"}}"""
            ),
            processus = "PreMiD",
        )
        assertEquals("YouTube", a?.nom)
    }

    @Test
    fun `PreMiD est reconnu quelle que soit la casse`() {
        val a = activiteDepuisDiscord(
            charge("""{"assets":{"large_text":"Spotify"}}"""),
            processus = "premid",
        )
        assertEquals("Spotify", a?.nom)
    }

    @Test
    fun `hors PreMiD le texte de la grande image ne vole pas le nom`() {
        val a = activiteDepuisDiscord(
            charge("""{"assets":{"large_text":"une illustration"}}"""),
            processus = "UnJeu",
        )
        assertEquals("UnJeu", a?.nom)
    }

    @Test
    fun `sans rien pour nommer, on ne montre rien`() {
        assertNull(activiteDepuisDiscord(charge("""{"details":"sans nom"}"""), processus = null))
    }

    @Test
    fun `un horodatage en secondes devient des millisecondes`() {
        val a = activiteDepuisDiscord(
            charge("""{"timestamps":{"start":1791315212}}"""),
            processus = "UnJeu",
        )
        assertEquals(1791315212000L, a?.depuis)
    }

    @Test
    fun `un horodatage deja en millisecondes est laisse tranquille`() {
        val a = activiteDepuisDiscord(
            charge("""{"timestamps":{"start":1791315212000}}"""),
            processus = "UnJeu",
        )
        assertEquals(1791315212000L, a?.depuis)
    }

    @Test
    fun `pas d horodatage, pas de compteur`() {
        val a = activiteDepuisDiscord(charge("""{"state":"Idling..."}"""), processus = "Modrinth App")
        assertNull(a?.depuis)
    }

    @Test
    fun `un horodatage absurde est ignore plutot que dessine a l envers`() {
        val a = activiteDepuisDiscord(
            charge("""{"timestamps":{"start":0}}"""),
            processus = "UnJeu",
        )
        assertNull(a?.depuis)
    }

    @Test
    fun `les images et l identifiant d application suivent`() {
        val a = activiteDepuisDiscord(
            charge(
                """{"application_id":"123","assets":{"large_image":"grande","small_image":"petite"}}"""
            ),
            processus = "UnJeu",
        )
        assertEquals("grande", a?.grandeImage)
        assertEquals("petite", a?.petiteImage)
        assertEquals("123", a?.applicationId)
    }

    @Test
    fun `une charge biscornue ne fait pas tomber la lecture`() {
        val a = activiteDepuisDiscord(
            charge("""{"name":123,"assets":"pas un objet","timestamps":[]}"""),
            processus = "UnJeu",
        )
        assertEquals("123", a?.nom)
        assertNull(a?.grandeImage)
        assertNull(a?.depuis)
    }

    @Test
    fun `venue du navigateur, l activite prend le texte de la grande image`() {
        val a = activiteDepuisDiscord(
            charge(
                """{"details":"Un documentaire","state":"chaine quelconque",
                   "assets":{"large_image":"yt","large_text":"YouTube"}}"""
            ),
            processus = null,
        )
        assertEquals("YouTube", a?.nom)
        assertEquals("Un documentaire", a?.details)
    }

    @Test
    fun `venue du navigateur, un nom explicite passe devant`() {
        val a = activiteDepuisDiscord(
            charge("""{"name":"Netflix","assets":{"large_text":"autre chose"}}"""),
            processus = null,
        )
        assertEquals("Netflix", a?.nom)
    }
}

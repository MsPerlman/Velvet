package li.velvetchat.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import li.velvetchat.core.model.Categorie
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.ChannelKind
import li.velvetchat.core.model.EncryptionState
import li.velvetchat.core.model.Unread

/**
 * grouping the channel panel
 *
 * the case that matters is the first: an empty category has to appear. that is the reported bug,
 * that creating a category did nothing, and it was visible nowhere in the code since the gesture
 * succeeded. a category only existed as a label carried by its channels, and a brand new one has
 * none
 */
class GrouperSalonsTest {

    private fun salon(nom: String, categorie: String? = null) = Channel(
        id = "!$nom:exemple.org",
        name = nom,
        topic = null,
        kind = ChannelKind.TEXT,
        unread = Unread.NONE,
        encryption = EncryptionState.ENCRYPTED,
        categorie = categorie,
    )

    @Test
    fun `une categorie sans salon garde son intertitre`() {
        val groupes = grouperSalons(
            salons = listOf(salon("general")),
            categories = listOf(Categorie("!vide:exemple.org", "À l'église")),
        )

        assertEquals(listOf(null, "À l'église"), groupes.map { it.first })
        assertEquals(emptyList(), groupes.last().second)
    }

    @Test
    fun `les salons sans categorie viennent en premier et sans intitule`() {
        val groupes = grouperSalons(
            salons = listOf(salon("blabla", "Vocal"), salon("general"), salon("musique", "Vocal")),
            categories = listOf(Categorie("!v:exemple.org", "Vocal")),
        )

        assertEquals(null, groupes.first().first)
        assertEquals(listOf("general"), groupes.first().second.map { it.name })
        assertEquals(listOf("blabla", "musique"), groupes[1].second.map { it.name })
    }

    @Test
    fun `l'ordre est celui du serveur, pas l'alphabet`() {
        val groupes = grouperSalons(
            salons = listOf(salon("un", "Zèbre"), salon("deux", "Abeille")),
            categories = listOf(Categorie("!z:exemple.org", "Zèbre"), Categorie("!a:exemple.org", "Abeille")),
        )

        assertEquals(listOf("Zèbre", "Abeille"), groupes.map { it.first })
    }

    @Test
    fun `une etiquette de salon sans sous-espace connu ne perd pas ses salons`() {
        // the hierarchy can lag behind the channels: better a category shown before the space was
        // reread than a channel that disappears for the length of a sync
        val groupes = grouperSalons(salons = listOf(salon("archives", "Vieilleries")))

        assertEquals(listOf("Vieilleries"), groupes.map { it.first })
        assertEquals(listOf("archives"), groupes.single().second.map { it.name })
    }
}

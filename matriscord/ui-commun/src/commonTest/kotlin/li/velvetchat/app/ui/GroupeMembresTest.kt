package li.velvetchat.app.ui

import li.velvetchat.core.model.Member
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * grouping the member list
 *
 * the rule that matters here is not the sort, it is the distinction between "the server says this
 * person is away" and "the server says nothing". synapse disables presence by default, and
 * conflating the two would empty the present list and file everyone under offline, for a
 * server setting rather than for reality
 */
class GroupeMembresTest {

    private fun membre(nom: String, pouvoir: Long? = null) = Member(
        userId = "@$nom:exemple.org",
        displayName = nom,
        avatarUrl = null,
        role = pouvoir?.let { Role(name = "Palier $it", powerLevel = it, colorArgb = null) },
    )

    @Test
    fun lesRolesSOrdonnentParPouvoirEtNonParNom() {
        val gens = listOf(membre("alice", 100), membre("bob"), membre("zoe", 50))
        val groupes = grouperMembres(gens, emptyMap())
        assertEquals(listOf("Palier 100", "Palier 50", "Membres"), groupes.map { it.first })
    }

    @Test
    fun lesAbsentsDeclaresDescendentEnBas() {
        val gens = listOf(membre("alice", 100), membre("bob"))
        val groupes = grouperMembres(
            gens,
            mapOf("@alice:exemple.org" to Presence.INVISIBLE, "@bob:exemple.org" to Presence.EN_LIGNE),
        )
        assertEquals(listOf("Membres", "Hors ligne"), groupes.map { it.first })
        assertEquals(listOf("alice"), groupes.last().second.map { it.displayName })
    }

    /**
     * the case that justifies the rule: presence off on the server, so everybody unknown. no
     * offline group may appear
     */
    @Test
    fun uneMPresenceInconnueNEstPasUneAbsence() {
        val gens = listOf(membre("alice", 100), membre("bob"))
        val groupes = grouperMembres(gens, emptyMap())
        assertTrue(groupes.none { it.first == "Hors ligne" })
        assertEquals(2, groupes.sumOf { it.second.size })

        val explicite = grouperMembres(
            gens,
            gens.associate { it.userId to Presence.INCONNU },
        )
        assertTrue(explicite.none { it.first == "Hors ligne" })
    }

    /** away means idle, not gone: you stay in your role group */
    @Test
    fun unInactifResteDansSonRole() {
        val groupes = grouperMembres(
            listOf(membre("alice", 100)),
            mapOf("@alice:exemple.org" to Presence.ABSENT),
        )
        assertEquals(listOf("Palier 100"), groupes.map { it.first })
    }

    @Test
    fun dansUnGroupeLeTriEstAlphabetiqueEtInsensibleALaCasse() {
        val gens = listOf(membre("Zoe"), membre("alice"), membre("Bob"))
        val groupes = grouperMembres(gens, emptyMap())
        assertEquals(listOf("alice", "Bob", "Zoe"), groupes.single().second.map { it.displayName })
    }
}

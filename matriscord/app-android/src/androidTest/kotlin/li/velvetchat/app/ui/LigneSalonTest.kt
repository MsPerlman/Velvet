package li.velvetchat.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.ChannelKind
import li.velvetchat.core.model.Unread

/**
 * the room row's interface tests
 *
 * why this one first: it is the whole screen's state densest surface (read, unread, mentioned,
 * muted, in voice), and those states combine. checking it by eye means building each combination on
 * a real server, which nobody does, and it is exactly the ground where a fault goes unnoticed for
 * days
 *
 * the states tested are those a rule was written for somewhere, not the pretty cases: a test
 * checking that a name shows defends nothing, and a test checking that a muted room keeps its
 * mention defends a decision
 */
class LigneSalonTest {

    @get:Rule
    val compose = createComposeRule()

    private fun salon(
        nom: String = "general",
        notifications: Int = 0,
        highlights: Int = 0,
        kind: ChannelKind = ChannelKind.TEXT,
    ) = Channel(
        id = "!$nom:exemple.org",
        name = nom,
        topic = null,
        kind = kind,
        unread = Unread(notifications = notifications, highlights = highlights),
    )

    private fun monter(
        channel: Channel,
        muet: Boolean = false,
        enVocal: Int = 0,
        selected: Boolean = false,
    ) = compose.setContent {
        VelvetTheme {
            ChannelRow(
                channel = channel,
                selected = selected,
                muet = muet,
                enVocal = enVocal,
                onClick = {},
            )
        }
    }

    @Test
    fun leNomDuSalonEstAffiche() {
        monter(salon("general"))
        compose.onNodeWithText("general").assertIsDisplayed()
    }

    /**
     * the rule: the red dot carries mentions, not unreads. a very active room you follow without
     * being named in has to stay "to read", not "you are wanted"
     */
    @Test
    fun unSalonNonLuSansMentionNaPasDePastille() {
        monter(salon(notifications = 12, highlights = 0))
        compose.onAllNodesWithText("12").assertCountEquals(0)
    }

    @Test
    fun unSalonMentionneAffricheLeNombreDeMentions() {
        monter(salon(notifications = 12, highlights = 3))
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    /**
     * the file's most counterintuitive rule, so the one that most deserves a test
     *
     * muting a room says "do not tell me about everything", not "do not tell me i was named". a
     * muted room therefore loses its bold and its unread dot, and keeps its mention badge. it is
     * the kind of nuance a rework breaks without noticing
     */
    @Test
    fun unSalonMuetGardeSaPastilleDeMention() {
        monter(salon(notifications = 5, highlights = 2), muet = true)
        compose.onNodeWithText("2").assertIsDisplayed()
    }

    /** nobody in voice: no counter, and above all not a "0" */
    @Test
    fun sansPersonneEnVocalAucunCompteurNestAffiche() {
        monter(salon(), enVocal = 0)
        compose.onAllNodesWithText("0").assertCountEquals(0)
    }

    @Test
    fun avecDesGensEnVocalLeCompteurEstAffiche() {
        monter(salon(), enVocal = 3)
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    /**
     * same reasoning as the mention: muting a room must not hide what is happening in it. the voice
     * counter is a state, not a solicitation, and it survives the silence
     */
    @Test
    fun unSalonMuetAfficheQuandMemeLesGensEnVocal() {
        monter(salon(), muet = true, enVocal = 2)
        compose.onNodeWithText("2").assertIsDisplayed()
    }

    /**
     * the voice counter and the mention badge coexist without eating each other: they are two
     * distinct pieces of information, and a room can perfectly well be both at once
     */
    @Test
    fun compteurVocalEtMentionCoexistent() {
        monter(salon(notifications = 4, highlights = 1), enVocal = 5)
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
    }
}

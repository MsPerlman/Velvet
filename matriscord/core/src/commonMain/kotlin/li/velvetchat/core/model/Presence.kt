package li.velvetchat.core.model

/**
 * what the protocol actually carries
 *
 * matrix has three presence states and only three. that is fewer than discord, and worth saying
 * out loud rather than pretending otherwise: inventing a fourth would give us something no other
 * client understands and that would not survive federation
 *
 * the mapping onto the words people expect is nearly complete anyway:
 *
 * | discord | here | what the server gets |
 * |---|---|---|
 * | online | [EN_LIGNE] | `online` |
 * | idle | [ABSENT] | `unavailable` |
 * | invisible | [INVISIBLE] | `offline`, because declaring yourself offline while connected is
 *   exactly what invisible means |
 * | do not disturb | not a state | see [Statut.message] and the push rules |
 *
 * [INCONNU] is not a protocol state, it is admitting we do not know: the server is not
 * answering, or presence is off there. it counts, because painting grey in that case would look
 * like an absence somebody observed
 */
enum class Presence {
    EN_LIGNE,
    ABSENT,

    /**
     * do not disturb, as the protocol manages to say it: `org.matrix.msc3026.busy`
     *
     * before this the state only existed locally, since the master push rule cut the
     * notifications, so everyone else saw "online". synapse can publish `busy` once
     * `msc3026_enabled` is set
     *
     * a server that refuses the state returns an error, and then the call falls back on
     * `online`. the silence part works everywhere regardless
     */
    NE_PAS_DERANGER,

    /**
     * connected, but declaring yourself offline
     *
     * the protocol's single word (`offline`) covers two situations nothing tells apart from
     * outside: "this person is not here" and "this person would rather you did not know they
     * are". that is a property of the protocol and not a bug, and it is precisely what makes
     * invisibility useful
     */
    INVISIBLE,

    /** the server says nothing: presence disabled, or out of reach */
    INCONNU,
}

/**
 * someone's state, as it gets shown
 *
 * @param message the protocol's `status_msg`: free text, visible to anyone sharing a room. that
 *   is where a custom status lives, without inventing anything, and it stays readable from
 *   element
 *
 *   it is also what makes a fourth state unnecessary. "do not disturb" does not need to be a
 *   state to be said, it needs to be read by other people and to silence notifications for you.
 *   the first goes through this field, the second through the push rules, which are account
 *   data and therefore follow every device
 *
 * @param depuisMs milliseconds since last activity, when the server bothers to say. tells
 *   "away for two minutes" from "away for three days"
 */
data class Statut(
    val presence: Presence,
    val message: String? = null,
    val depuisMs: Long? = null,
) {
    /**
     * does this status count as present for a headcount
     *
     * [INVISIBLE] and [INCONNU] both say no, for opposite reasons that land in the same place:
     * one asked not to be counted, the other cannot be
     */
    val estPresent: Boolean get() = presence == Presence.EN_LIGNE ||
        presence == Presence.ABSENT ||
        presence == Presence.NE_PAS_DERANGER

    companion object {
        val INCONNU = Statut(Presence.INCONNU)
    }
}

/** the protocol's word, both ways */
fun Presence.versProtocole(): String = when (this) {
    Presence.EN_LIGNE -> "online"
    Presence.ABSENT -> "unavailable"
    Presence.NE_PAS_DERANGER -> BUSY
    // `INCONNU` never gets written, we do not publish "i do not know". if it turned up here the
    // least dishonest fallback is to assert nothing, so offline
    Presence.INVISIBLE, Presence.INCONNU -> "offline"
}

/** msc3026's word for do not disturb. synapse only accepts it with `msc3026_enabled` */
const val BUSY = "org.matrix.msc3026.busy"

fun presenceDepuisProtocole(mot: String?): Presence = when (mot?.lowercase()) {
    "online" -> Presence.EN_LIGNE
    "unavailable" -> Presence.ABSENT
    BUSY, "busy" -> Presence.NE_PAS_DERANGER
    // received from someone else, `offline` means "not here". we have no way of telling an
    // absence from an invisibility, and that is the point. seen on yourself the screen draws it
    // as invisible, because there we know we are connected
    "offline" -> Presence.INVISIBLE
    else -> Presence.INCONNU
}

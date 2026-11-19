package li.velvetchat.core.model

/**
 * what the control centre shows
 *
 * two panels only, deliberately: invites and accounts. what is missing, server statistics, room
 * management, gdpr purging an account, is not forgotten but set aside. those are real control
 * centre ambitions and none of them was what the problem of the day needed, which was a new
 * member arriving
 */

/**
 * a synapse registration token, as the control centre shows it
 *
 * @param jeton the string to paste into a [CodeInvitation], or already pasted if velvet created
 *   this token itself rather than importing it
 * @param usagesRestants null means unlimited. 0 never appears in the list, since a spent token
 *   has nothing left to show and synapse stops accepting it anyway
 * @param expiration milliseconds since the epoch, null when the token never expires
 * @param idEspace the space this token leads to, when we know. see [CodeInvitation]
 *
 *   the server does not know and cannot know: a synapse token only carries itself. so the
 *   association lives in the admin's account data, private and synced between their devices.
 *   this is not a second source of truth that could contradict the first, the invite code stays
 *   self contained and this is only a note to self for rebuilding it without having to revoke
 *   and regenerate. null when the note is missing, meaning a token created elsewhere or account
 *   data that went away
 */
data class JetonInvitation(
    val jeton: String,
    val usagesRestants: Int?,
    val expiration: Long?,
    val idEspace: String? = null,
)

/**
 * an account on the homeserver, as the admin api returns it. every account, not only the ones
 * sharing a room with whoever is looking. that is the difference with `Member`: that one is a
 * view from inside a room, this one is a view from the server
 */
data class CompteServeur(
    val userId: String,
    val displayName: String?,
    val administrateur: Boolean,
    val desactive: Boolean,
)

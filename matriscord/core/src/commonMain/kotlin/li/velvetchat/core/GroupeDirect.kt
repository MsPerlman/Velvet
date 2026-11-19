package li.velvetchat.core

/**
 * who actually gets invited to a new group conversation
 *
 * pulled out of [MatrixEngine.creerGroupeDirect] so the rule can be tested without a homeserver.
 * three things go wrong here and none of them is visible at the call site:
 *
 *   · **yourself.** the creator is already in the room, and a server asked to invite them refuses
 *     the whole call. a picker built from your own conversations cannot normally offer you, but
 *     the method is public and nothing stops a caller passing a list that includes you
 *   · **the same person twice.** one failed invite takes the whole `createRoom` down with it, so
 *     a duplicate costs the group, not just the duplicate
 *   · **fewer than two.** with one person the right call is [MatrixEngine.openDirect], which
 *     finds the conversation that already exists instead of making a second one beside it. with
 *     none there is no group at all
 *
 * blanks are dropped on the way through: a trailing space on a matrix id is not the same string,
 * and the deduplication would miss it
 */
internal fun invitesDeGroupe(userIds: List<String>, moi: String): List<String> =
    userIds.map { it.trim() }.filter { it.isNotBlank() && it != moi.trim() }.distinct()

/** two other people is the smallest thing worth calling a group */
internal fun groupeAssezGrand(invites: List<String>): Boolean = invites.size >= 2

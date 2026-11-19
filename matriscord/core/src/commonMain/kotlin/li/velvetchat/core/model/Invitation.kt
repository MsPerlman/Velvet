package li.velvetchat.core.model

/**
 * the invite code: what an admin shares so a new member joins velvet and lands somewhere,
 * rather than in an empty account
 *
 * why a self contained code rather than a stored association. synapse has a real controlled
 * registration mechanism, registration tokens, but a token only carries itself. the protocol has
 * no notion of "this token leads to this space". there were three ways to tie the two together:
 *
 * 1. a table of our own somewhere on the server, which is state to maintain and one more source
 *    of truth to keep alive for a pair of strings
 * 2. a room state event, readable only by people who already joined, which is never the person
 *    who is not a member yet, who is exactly the person who needs to read it
 * 3. the code carries both halves itself. nothing to store, nothing that can drift away from
 *    the token it goes with
 *
 * the third is the only one that needs no new infrastructure
 *
 * the shape is `<token>!<spaceId>`, and the exclamation mark is not arbitrary: it is the first
 * character of every matrix room id, and it never belongs to the character set synapse allows
 * in a registration token (`[A-Za-z0-9._~-]`). so the separator can never be confused with
 * either half. no escaping to write, no ambiguity to resolve
 */
object CodeInvitation {

    fun encoder(jeton: String, idEspace: String): String = "$jeton!$idEspace"

    /**
     * decodes a code. null when the shape does not hold, which covers a code truncated while
     * being copied by hand, or a link that was never one
     */
    fun decoder(code: String): Invitation? {
        val texte = code.trim()
        val position = texte.indexOf('!')
        if (position <= 0 || position == texte.length - 1) return null
        val jeton = texte.substring(0, position)
        // `position + 1` and not `position`. the separator we just found is THIS `!`, which is
        // not the mandatory one opening the room id right after. including both would have
        // given a space id starting `!!`
        val idEspace = texte.substring(position + 1)
        // a matrix room id starts with `!` and carries a `:` between its local part and the
        // hostname. checking here avoids accepting an obviously broken code and only finding
        // out when the `joinServer` after registration fails
        if (!idEspace.startsWith("!") || !idEspace.contains(':')) return null
        return Invitation(jeton, idEspace)
    }
}

/**
 * @param jeton pass it as is to registration (`m.login.registration_token`)
 * @param idEspace the space to join right after. see `MatrixEngine.joinServer`, which already
 *   joins everything the space contains, recursively
 */
data class Invitation(
    val jeton: String,
    val idEspace: String,
)

/**
 * an invite you received, to a channel, a group or a server where somebody is waiting for you
 *
 * why this model had to be added afterwards: the app showed none of them, and not because a
 * screen was missing. both paths that build the lists explicitly dropped the "invited" state
 *
 * `servers()` starts from `topLevelJoinedSpaces()`, joined being the operative word, so a
 * server you are only invited to does not exist as far as the rail is concerned. and `directs()`
 * filtered out `membership != JOINED` with a comment saying a left room stays in `m.direct`,
 * which is true, and which threw away the pending invite along the way, since that is the same
 * state protocol side
 *
 * the consequence people noticed: inviting somebody "did not work". the invite went out fine,
 * element could see it, and velvet had nowhere at all to show it. a feature can be complete on
 * the wire and entirely absent for whoever uses it
 *
 * @param estEspace a server, so join it with `joinServer`, which also walks into the channels.
 *   the rest join on their own
 * @param estDirect a two person conversation rather than a group. only changes the wording
 * @param invitePar who invited, null when the server does not say
 */
data class InvitationRecue(
    val roomId: String,
    val nom: String,
    val avatarUrl: String?,
    val estEspace: Boolean,
    val estDirect: Boolean,
    val invitePar: String?,
    val nomDeInvitant: String?,
)

/**
 * a server's invite link, the thing you paste into "join a server"
 *
 * matrix has no secret invite link: a space is joinable by its id as soon as its join rule is
 * `public`. so the link carries the id, behind a `#` so the site's page never sends it to the
 * web server. the server is still not listed in any directory, you have to have been given the
 * link
 */
object LienInvitation {
    /**
     * the web page hosting the link, filled in at startup from the installation's config
     *
     * empty by default, and that is the right default: an installation with no website should
     * not have to invent one, and the domain of one that has a site does not belong in the
     * source
     */
    var base: String = ""

    /**
     * with no landing page the link goes out as `matrix.to`, the address every matrix client
     * knows how to open, which belongs to nobody and does exactly the same job
     */
    fun encoder(idEspace: String): String =
        if (base.isBlank()) "https://matrix.to/#/$idEspace" else base + idEspace

    /**
     * what to join based on whatever got pasted: a velvet link, a `matrix.to` link, or an id
     * (`!…`) or alias (`#…`) handed over directly
     */
    fun cible(texte: String): String {
        val t = texte.trim()
        if (!t.startsWith("http")) return t
        val brut = when {
            "matrix.to/#/" in t -> t.substringAfter("matrix.to/#/")
            else -> t.substringAfter('#', missingDelimiterValue = "")
        }.substringBefore('?')
        return brut.replace("%21", "!").replace("%23", "#").replace("%3A", ":").replace("%3a", ":")
    }
}

package li.velvetchat.core.model

/**
 * a message from the timeline, already decrypted where that was possible
 *
 * the engine never hands back raw ciphertext: either it could read it, or it says it couldn't
 * and why. see [MessageBody.Undecryptable]
 */
data class Message(
    val id: String,
    val sender: String,
    /** null until the profile resolves, show the id in the meantime */
    val senderDisplayName: String?,

    /**
     * sender avatar as `mxc://`, taken from the event's own profile
     *
     * deliberately the event and not the member list: a message keeps the avatar its author had
     * at the time, which also covers someone who has since left and isn't in the list any more.
     * looking up the current profile would wipe the avatar off every former member
     */
    val senderAvatarUrl: String? = null,
    /** milliseconds since the unix epoch */
    val timestamp: Long,
    val isOwn: Boolean,
    val isEdited: Boolean,
    val body: MessageBody,
    val sendState: SendState = SendState.Sent,
    /** grouped by emoji, like the row under a discord message */
    val reactions: List<Reaction> = emptyList(),
    /** the message this one replies to, if any */
    val replyTo: String? = null,

    /**
     * enough to draw the quote above a reply, when the sdk has resolved the target
     *
     * `replyTo` alone doesn't cut it: an event id doesn't render. without this the screen knew
     * a message was a reply but not to what, and a three way conversation becomes unreadable
     * the moment you see replies without knowing what they're answering
     *
     * null has two causes and we don't try to tell them apart: the target is outside the loaded
     * window, or the sdk hasn't resolved it yet. either way the display does the same thing,
     * it shows the relation without the excerpt
     */
    val citation: Citation? = null,
    /**
     * first unread message, which is where the "new messages" divider goes
     *
     * derived from the server's read marker rather than a local counter, so it follows the
     * account across devices. at most one message carries it, none when everything is read
     */
    val isFirstUnread: Boolean = false,

    /**
     * who this message pings, as carried by `m.mentions`
     *
     * read from the protocol rather than guessed from the text: looking for your own name in
     * the body would confuse "papa a répondu" with "@papa". it's also this field and only this
     * field that decides whether the server sends a notification, so the display had better
     * agree with whatever woke the phone up
     */
    val mentions: List<String> = emptyList(),

    /** `@room`, the message pings the whole channel */
    val mentionneToutLeSalon: Boolean = false,

    /**
     * how many thread replies hang off this message, null if none
     *
     * without it a thread only exists in the tab that lists them: the conversation shows
     * nothing and nobody knows a discussion opened under a message. this number is what makes
     * threads findable, the rest is plumbing
     *
     * read from the bundled summary the server attaches to the root event, never counted here.
     * a local count would only know about replies already loaded
     */
    val reponsesEnFil: Int? = null,
)

/**
 * reactions grouped under a message
 *
 * @param mine true if this account already reacted with that emoji. it's what lets the ui
 *   highlight the pill and know a click removes the reaction instead of adding a second one
 */
data class Reaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
    /**
     * who reacted, in server order, as matrix ids
     *
     * for the stacked avatars inside the pill. empty by default so a local echo, or a source
     * that only knows the count, still works
     */
    val par: List<String> = emptyList(),
)

/**
 * the excerpt of a quoted message, as drawn above a reply
 *
 * plain text on purpose: a quote is one truncated line next to a sixteen point avatar.
 * rendering html in it, bold and code and custom emoji, would need the whole message body
 * machinery for something you don't read, you recognise. discord makes the same call
 *
 * @param apercu the body cut down to one line. for media, what the media is ("photo", "voice
 *   message") rather than a filename
 */
data class Citation(
    val eventId: String,

    /**
     * the quoted author's matrix id, not just their display name
     *
     * two things need it and neither can work off the name: the fallback avatar colour, derived
     * from the id so one person keeps the same tint everywhere, and the role colour, whose map
     * is keyed by id. two members can share a display name, which on a small server is the normal case
     */
    val auteurId: String,
    val auteur: String,
    val auteurAvatarUrl: String?,
    val apercu: String,
)

/**
 * delivery state of a message we just sent
 *
 * the local echo is what makes a message show up immediately instead of waiting for the server.
 * without it, on a flaky mobile connection, the message looks like it vanished and people
 * retype it
 *
 * the sdk owns the send queue and the retries, the engine just translates its state into
 * something the ui can draw
 */
sealed interface SendState {

    /** confirmed by the server, which is every message you receive */
    data object Sent : SendState

    /** in the send queue. show it greyed out, definitely don't hide it */
    data object Sending : SendState

    /**
     * the send failed. the sdk's queue retries, but you need to know and be able to act
     *
     * @param recoverable false when no retry will help, say an encryption refusal because a
     *   device isn't verified. worth the distinction: in one case you wait, in the other you act
     */
    data class Failed(
        val reason: String,
        val recoverable: Boolean,
    ) : SendState
}

sealed interface MessageBody {

    data class Text(
        val text: String,
        /** html from `formatted_body` when there is one, that's where rendered markdown lives */
        val html: String? = null,
    ) : MessageBody

    /** `m.image`, `m.video`, `m.file`, `m.audio` */
    data class Media(
        /** what goes next to the media: caption if there is one, otherwise the filename */
        val caption: String,
        val kind: MediaKind,
        val media: MediaRef,
    ) : MessageBody

    /**
     * an `m.poll.start` poll with its votes already counted, by the engine and not the screen —
     * the protocol carries "who voted what", and the tally rules are subtler than they look: a
     * vote replaces that author's previous one, a vote after closing does not count. left to the
     * display, that guarantees two diverging tallies the day a second screen shows polls
     *
     * @param ferme closed: nobody votes any more and the results are final. not
     *   [resultatsVisibles] — an open poll can perfectly well show results as they come in
     * @param resultatsVisibles false for a secret ballot (`UNDISCLOSED`), counts appearing only
     *   once closed. chosen at creation, so a property of the poll and not a display setting
     */
    data class Poll(
        val question: String,
        val options: List<PollOption>,
        val totalVotes: Int,
        val ferme: Boolean,
        val resultatsVisibles: Boolean,
        /** how many choices at once. one, normally */
        val maxChoix: Int = 1,
    ) : MessageBody

    data object Redacted : MessageBody

    /**
     * a state event made readable: joined, left, kicked, renamed. these land in the timeline like
     * messages, and leaving them [Unsupported] printed "m.room.member" mid conversation, while
     * dropping them in the engine would take away the ui's right to show the ones it wants —
     * "untel a rejoint le salon" being part of the discord vocabulary
     *
     * so the engine types the event and the ui decides. see [SystemKind.isNoise] for the default
     *
     * @param actor who did it. for a kick, the moderator and not the person kicked
     * @param target who to, when that is not [actor]
     * @param detail new name, new topic, kick reason, depending on [kind]
     */
    data class System(
        val kind: SystemKind,
        val actor: String,
        val actorDisplayName: String?,
        val target: String? = null,
        val targetDisplayName: String? = null,
        val detail: String? = null,
    ) : MessageBody

    /**
     * an encrypted message this client can't read
     *
     * usually because it predates this session and the keys haven't come back yet. modelled
     * explicitly rather than hidden: showing "can't decrypt this" with a reason beats a silent
     * hole in the conversation, which reads as a lost message
     */
    data class Undecryptable(val reason: UndecryptableReason) : MessageBody

    /** types we haven't modelled yet */
    data class Unsupported(val description: String) : MessageBody
}

enum class SystemKind {
    JOINED,
    LEFT,
    INVITED,
    INVITE_ACCEPTED,
    INVITE_REJECTED,
    INVITE_REVOKED,
    KICKED,
    BANNED,
    UNBANNED,
    KNOCKED,

    DISPLAY_NAME_CHANGED,
    AVATAR_CHANGED,

    ROOM_CREATED,
    ROOM_NAME_CHANGED,
    ROOM_TOPIC_CHANGED,
    ROOM_AVATAR_CHANGED,
    ENCRYPTION_ENABLED,
    ROOM_UPGRADED,

    /** incoming call or rtc notification */
    CALL,

    /** recognised as a state event, no dedicated rendering. almost always noise */
    OTHER,
    ;

    /**
     * the default of what discord would show, not a rule of the engine
     *
     * discord prints "untel a rejoint" and prints nothing when someone changes their avatar.
     * that distinction isn't in the protocol, both are `m.room.member`. it's a product call,
     * so it has to be replaceable, hence a property the ui reads rather than a filter baked
     * into the flow
     *
     * in practice: the engine sends everything up, the ui hides this by default, and a "show
     * every event" setting stays possible without touching the engine
     */
    val isNoise: Boolean
        get() = when (this) {
            DISPLAY_NAME_CHANGED, AVATAR_CHANGED,
            ROOM_AVATAR_CHANGED, ROOM_CREATED, OTHER,
            -> true
            else -> false
        }
}

/**
 * one poll option, with its count and whether you picked it
 *
 * @param mien true if this account voted for it, which is how the screen knows a second tap
 *   removes the vote rather than adding one, same as reactions
 */
data class PollOption(
    val id: String,
    val texte: String,
    val votes: Int,
    val mien: Boolean,
)

enum class MediaKind { IMAGE, VIDEO, AUDIO, FILE }

/**
 * enough to display a media, and to fetch it later when we decide to. nothing is downloaded on
 * construction: a timeline can hold hundreds of attachments, so the engine hands back metadata and
 * the ui asks for the bytes of what it actually shows
 *
 * @param source opaque handle, not a url and deliberately not one — media in an encrypted room
 *   cannot be fetched over plain http, it needs the key and the iv, which this string carries.
 *   pass it as is to [li.velvetchat.core.MatrixEngine.mediaBytes]
 * @param width in pixels when the sender filled it in, to reserve the space before the download,
 *   without which the conversation jumps every time an image lands
 * @param blurhash a blurred preview in a few bytes, already in the event, so something plausible
 *   shows straight away with no network
 * @param thumbnail handle for a separately served thumbnail, when the sender attached one
 */
data class MediaRef(
    val source: String,
    val filename: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val blurhash: String? = null,
    val thumbnail: String? = null,
)

/**
 * why a message stayed unreadable. this was one value for the whole megolm family, and the screen
 * wrote "message from before you arrived" under all of them — reassuring, and the only one of the
 * five that says nothing is wrong, shown for the four where something is
 *
 * the sdk has carried `UtdCause` all along and we threw it away a line after receiving it. below is
 * that enum folded to what changes what somebody should do: nothing, verify a device, or tell
 * whoever maintains the server
 */
enum class UndecryptableReason {
    /**
     * sent before this device existed, or before we joined the room
     *
     * not a fault and not repairable: the key was handed out to the devices that were there,
     * and matrix does not hand it out again afterwards. this is the one case where the
     * reassuring wording is true
     */
    AVANT_NOTRE_ARRIVEE,

    /**
     * the key exists somewhere and did not come to us: the sender's device withheld it, or
     * ours is not trusted enough to be given it
     *
     * actionable, and it is the only one that is: verify the device, and the next messages
     * arrive. the ones already sent stay unreadable
     */
    CLE_REFUSEE,

    /**
     * the key should be here and is not, with no reason the sdk can name
     *
     * this is the one worth reporting: a gap in the backup, or two homeservers that stopped
     * talking to each other. folding it in with the others is what let it go unnoticed
     */
    CLE_MANQUANTE,

    /** the olm point to point layer couldn't be read */
    OLM,

    UNKNOWN,
}

/**
 * what a server can tell us about a pasted link, filled in by the homeserver and not the phone —
 * the whole point of `preview_url`: the device never touches the site, hands over neither ip nor
 * user agent, and a link cannot be used to find out who opened it. the price is that the server
 * fetches the url, hence the private address blocklist in `homeserver.yaml`, without which the
 * option is a machine for probing your own lan
 *
 * every field is optional, and a page with no title, description or image has nothing to show
 *
 * @param image an `mxc://` the server already uploaded, not the image's original url
 */
data class ApercuLien(
    val url: String,
    val titre: String? = null,
    val description: String? = null,
    val site: String? = null,
    val image: String? = null,
    val largeurImage: Int? = null,
    val hauteurImage: Int? = null,
) {
    /** nothing to show: no title, no description, no image */
    val vide: Boolean
        get() = titre.isNullOrBlank() && description.isNullOrBlank() && image.isNullOrBlank()
}

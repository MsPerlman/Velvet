package li.velvetchat.core

import kotlinx.coroutines.flow.Flow
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.CompteServeur
import li.velvetchat.core.model.CustomEmote
import li.velvetchat.core.model.EmotePack
import li.velvetchat.core.model.AppelEnCours
import li.velvetchat.core.model.Fil
import li.velvetchat.core.model.InfoVocal
import li.velvetchat.core.model.InvitationRecue
import li.velvetchat.core.model.JetonInvitation
import li.velvetchat.core.model.MediaRef
import li.velvetchat.core.model.DirectMessage
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.FAMILLE_DECORATIONS
import li.velvetchat.core.model.ModeNotification
import li.velvetchat.core.model.ModerationResult
import li.velvetchat.core.model.Profile
import li.velvetchat.core.model.Release
import li.velvetchat.core.model.RoleTier
import li.velvetchat.core.model.Server
import li.velvetchat.core.model.ServerJoin
import li.velvetchat.core.model.Session

/**
 * the only door between the ui and matrix
 *
 * product words (servers, channels), not protocol ones: when the sdk changes, one file changes.
 * a hundred and fifty methods is not a design goal, it is what copying discord costs
 */
interface MatrixEngine {

    /**
     * once per process, before anything else: starts the tokio runtime and, on android, sets up
     * tls verification. skip it and the first network call fails for no stated reason
     */
    fun initPlatform()

    /** an existing session, or null if none was saved */
    suspend fun restore(session: Session)

    suspend fun login(homeserverUrl: String, username: String, password: String): Session

    /**
     * registration, token only (`registration_requires_token`). the server is on the open
     * internet and open registration would be found and spammed within a week
     *
     * the session opens as after [login], but joining the space is the caller's next step via
     * [joinServer]: the two fail independently, and merging them would hide which one broke
     */
    suspend fun register(
        homeserverUrl: String,
        username: String,
        password: String,
        registrationToken: String,
    ): Session

    /**
     * does this server hand logins to an authentication service (MAS, oauth 2.0)
     *
     * then the password is typed in the browser, on the service's own page, and the app never
     * sees it. a server that answers no keeps the password form
     */
    suspend fun connexionParNavigateur(homeserverUrl: String): Boolean = false

    /**
     * login or account creation through the browser
     *
     * [ouvrir] shows the url to the person and suspends until the service sends them back to
     * [retour]; it returns that full callback url. the platform owns both halves: a custom
     * scheme on android, a loopback port on the desktop
     */
    suspend fun loginNavigateur(
        homeserverUrl: String,
        retour: String,
        creation: Boolean,
        ouvrir: suspend (String) -> String,
    ): Session = throw UnsupportedOperationException(tr("connexion par navigateur indisponible", "browser sign-in unavailable"))

    /**
     * called each time the sdk refreshes the tokens. the platform saves the session again, or the
     * next launch restores a refresh token that was already spent
     */
    fun surSessionRenouvelee(rappel: (Session) -> Unit) {}

    /**
     * the authentication service allows this account to ask for admin rights, which a normal
     * login does not carry (see [activerAdministration]). false for a password session
     */
    suspend fun peutDemanderAdmin(): Boolean = false

    /**
     * goes back through the browser asking for the admin scopes, on the same device, so the
     * keys stay. the returned session replaces the stored one
     */
    suspend fun activerAdministration(retour: String, ouvrir: suspend (String) -> String): Session =
        throw UnsupportedOperationException("administration indisponible")

    /** the current session, throws if there is not one */
    fun currentSession(): Session

    /**
     * what the app is allowed to do. velvet wants the device verified first, and the second
     * reason is the one that bites: historical key sharing only delivers to devices signed by
     * their owner, so an unverified one gets a bundle encrypted for nobody (`recipients={}`
     * in the sdk logs). the first is that every other client shows permanent warnings
     *
     * a state and not a `verificationState()` for the ui to interpret: a blocking rule
     * improvised per screen diverges, and the engine knows what not verifying costs
     */
    fun accessFlow(): Flow<AccessState>

    /** starts sync and waits until it is actually working. idempotent */
    suspend fun startSync()

    suspend fun stopSync()

    /**
     * token revoked, sync stopped, client forgotten. touches no disk: the store belongs to one
     * account and the caller deletes it, or the next login opens on somebody else's history
     *
     * never throws on a refused revocation. offline or expired, the local logout goes through
     * anyway — stuck inside a session you are leaving is the worse failure
     */
    suspend fun deconnecter()

    /**
     * connection state, as a flow
     *
     * what a chat ui has to be able to say at any moment: connected, retrying, stopped. without
     * it a dropped connection shows up as a conversation that quietly stops updating, which is
     * the worst possible symptom because it looks exactly like a quiet conversation
     *
     * emits the current state on subscribe, then on every change
     */
    fun connectionFlow(): Flow<ConnectionState>

    /** a snapshot of the servers and their channels. for the live version see [serversFlow] */
    suspend fun servers(): List<Server>

    /**
     * emits whenever the room list moves, which is what lets the rail badge itself without
     * polling. every emission rederives the whole space hierarchy, so changes are batched and
     * an identical result does not re emit
     */
    fun serversFlow(): Flow<List<Server>>

    /**
     * the rail's order, in account data so it follows you between devices — tidying on the
     * phone and finding the old order on the desktop would be tidying for nothing
     *
     * [servers] applies it, and a server missing from the list goes to the end rather than
     * vanishing. no caller has to know about this, and none can forget it
     */
    suspend fun setOrdreServeurs(ids: List<String>)

    /**
     * a channel's messages, decrypted where possible. a chat client cannot poll: every message,
     * edit, reaction and send state change has to come up on its own
     *
     * emits current state, then every change, and paginates back once on subscribe. holds an
     * sdk listener, released when collection is cancelled
     */
    fun messagesFlow(channelId: String): Flow<List<Message>>

    /**
     * an older page of history. the flow re emits by itself afterwards
     *
     * @return true while older messages remain, so the ui stops offering "load more" instead of
     *   asking forever about a conversation it has already loaded in full
     */
    suspend fun loadMore(channelId: String, count: Int = 30): Boolean

    /**
     * text search inside one channel, in memory. `/search` is useless here: the server indexes
     * what it can read and it cannot read an encrypted room — not a synapse limitation,
     * encryption working as advertised. element answers it with a local index on desktop and
     * nothing at all on mobile
     *
     * so: paginate back through a throwaway timeline and filter. bounded, no index, and it stops
     * after [maxEvenements] even with history left, so the caller has to say so on screen —
     * "nothing in the last thousand messages" is not "nothing". it finds only what decrypts, and
     * costs a round trip per page, so it is triggered and never background
     *
     * @param requete compared ignoring case and accents
     * @return the matching messages, newest first
     */
    suspend fun chercherDansSalon(
        channelId: String,
        requete: String,
        maxEvenements: Int = 1000,
    ): List<Message>

    /**
     * reads the channel to its last message, which is what drops the badges back to zero.
     * the effect comes back up through [serversFlow] on its own
     *
     * @param visibility private by default: the counters drop the same way without telling
     *   everyone the second you opened a channel. velvet has no "seen by" and neither does
     *   discord, so the public variant would publish what nobody reads
     */
    suspend fun markRead(channelId: String, visibility: ReadVisibility = ReadVisibility.PRIVATE)

    /**
     * `m.marked_unread`, room account data, so it follows the account rather than living in
     * this phone's head. [markRead] clears it: a room you read after flagging it is read
     */
    suspend fun setNonLu(channelId: String, nonLu: Boolean)

    /**
     * unread from one message down, by moving the markers back to [eventIdPrecedent] — the one
     * just before, which the caller knows from its list. null means nothing is read
     *
     * the flag goes with it because the counters are the server's: markers moved back without
     * push rules firing leaves a count of zero, and the room would look read
     */
    suspend fun marquerNonLuDepuis(channelId: String, eventIdPrecedent: String?)

    /** read up to and including [eventId] and no further, unlike [markRead] which reads it all */
    suspend fun marquerLuJusqua(channelId: String, eventId: String)

    /** edits a message already sent. the "edited" badge is the protocol's doing, not ours */
    suspend fun edit(channelId: String, messageId: String, markdown: String)

    /**
     * a link preview as the homeserver sees it. the client never touches the site: the server
     * fetches the page and copies the image into its own store, so pasting a booby trapped link
     * into a room reveals nobody's ip address. that is the whole point of the mechanism
     *
     * @return null for no opengraph, the option off, a blocked or unreachable address. none is
     *   worth surfacing, a missing preview leaves the link readable
     */
    suspend fun apercuLien(url: String): li.velvetchat.core.model.ApercuLien?

    /**
     * retries a message the queue gave up on — `Failed` with `recoverable = false` only. while
     * an automatic retry is possible the queue handles it, and a button would be lying
     *
     * usually encryption: a recipient had an unverified device or a changed identity, which is
     * a human decision and not a network problem. this picks the message back up afterwards
     *
     * @param messageId the local echo's transaction id. a failed message has no `eventId`
     */
    suspend fun renvoyer(channelId: String, messageId: String)

    /** replies to a message. replies are a native matrix relation, not something we invented */
    suspend fun reply(channelId: String, messageId: String, markdown: String)

    /**
     * adds or removes a reaction, which is the same action, same as discord: clicking your own
     * reaction a second time takes it off
     *
     * @return true when it was added, false when it was removed
     */
    suspend fun toggleReaction(channelId: String, messageId: String, emoji: String): Boolean

    /**
     * the content goes server side, the event stays, so the timeline renders it as `Redacted`.
     * the protocol's doing, not ours
     */
    suspend fun delete(channelId: String, messageId: String, reason: String? = null)

    /**
     * someone's presence, read through the client api by hand: the sdk can write presence and
     * exposes no way to read it, though the information does arrive in the sync stream
     *
     * returns unknown rather than throwing. synapse turns presence off for what it costs, the
     * server may refuse it for someone you share no room with, and a grey dot beats an error
     * screen. not knowing is a normal case here
     */
    suspend fun presence(userId: String): li.velvetchat.core.model.Statut

    /**
     * presence for several people. the protocol has no batched read, so it is one request each;
     * what changes is that they go out in parallel and capped, plus a short cache
     *
     * callers used to write `liste.associate { presence(it) }` — ten people at 40 ms of latency
     * cost half a second, growing linearly. three hundred members still make three hundred
     * requests, just faster: the cost moved from noticeable at ten to noticeable at several
     * hundred. the real fix is reading presence out of the sync stream, which the sdk hides
     */
    suspend fun presences(
        userIds: Collection<String>,
    ): Map<String, li.velvetchat.core.model.Statut>

    /**
     * presence for several people, kept fresh while you collect
     *
     * the screen read it once on open and never again, so ten minutes in the "n online" had
     * drifted with nothing to say so. a stale status that looks fresh is worse than none, which
     * is why [presences] tells "offline" apart from "we do not know"
     *
     * the first fix was a loop in the screen, which then carried "90 s and not 60, because the
     * engine caches for 60" — a detail leaking into its caller. the cadence lives here now, next
     * to the cache it outlasts, at one request per person per round: consistent, not free
     *
     * a failure skips a turn rather than ending the flow and freezing presence until you reopen
     */
    fun presencesFlow(
        userIds: Collection<String>,
    ): Flow<Map<String, li.velvetchat.core.model.Statut>>

    /**
     * what the next `/sync` calls will announce, without writing anything
     *
     * writing presence and announcing it are two things, and that is the whole bug: the put
     * sets a value and `set_presence` on every `/sync` overwrites it, so a client that syncs
     * without saying anything announces "online"
     *
     * for background wakeups — a notification, a bubble, a shade action — that open a session
     * to read one event. without it every message received put you back online after you had
     * declared yourself away, once per notification
     */
    suspend fun annoncerPresence(presence: li.velvetchat.core.model.Presence)

    /**
     * publishes your own presence and status message
     *
     * `message` is the protocol's `status_msg`, free text everyone sharing a room can see,
     * element included. the protocol has no "do not disturb" state; writing it here is what
     * other clients do
     *
     * @param message null clears it, as does an empty string
     */
    suspend fun setPresence(
        presence: li.velvetchat.core.model.Presence,
        message: String? = null,
    )

    /**
     * silences every notification on every device, through `.m.rule.master`. it is account
     * data, so it silences the tablet and the web too and survives a reinstall — a real do not
     * disturb rather than a setting that holds on one phone
     *
     * enabled, the master rule *suppresses*. its name says the opposite of what it does, which
     * is a lovely thing to discover at two in the morning. so `silence = true` enables it
     */
    suspend fun setSilence(silence: Boolean)

    /** the master rule as it stands. true means nothing gets through */
    suspend fun silence(): Boolean

    /**
     * the account wide setting a channel on `DEFAUT` follows. the master rule only did all or
     * nothing; what was missing is the case people want, mentions only, everywhere
     *
     * not invented: "notify for every message" is `.m.rule.message` and `.m.rule.encrypted`,
     * and turning them off still lets mentions through on their higher priority rules. every
     * matrix client offering "mentions only" does exactly this
     *
     * `DEFAUT` means nothing here, there is nothing above it, so it reads as TOUT
     */
    suspend fun modeNotificationGlobal(): ModeNotification

    /** writes the account wide setting. see [modeNotificationGlobal] */
    suspend fun setModeNotificationGlobal(mode: ModeNotification)

    /**
     * creates a poll
     *
     * @param secret secret ballot: the counts only show once the poll closes. a property of the
     *   poll itself, fixed at creation, not a display setting you can flip afterwards
     * @param maxChoix how many answers at once. one, normally
     */
    suspend fun createPoll(
        channelId: String,
        question: String,
        options: List<String>,
        secret: Boolean = false,
        maxChoix: Int = 1,
    )

    /**
     * a vote replaces the same author's previous one — the protocol talking, not a client
     * policy — so an empty list withdraws it
     */
    suspend fun votePoll(channelId: String, pollId: String, optionIds: List<String>)

    /** closes a poll. you have to be its author, or able to moderate */
    suspend fun endPoll(channelId: String, pollId: String)

    /**
     * uploads a file and returns its `mxc://`, without sending it anywhere — unlike `sendMedia`
     * which also posts a message. the banner needs these two steps because extended profiles
     * carry text values, not files
     *
     * the file goes up unencrypted, like an avatar: it belongs to no room and so to no key.
     * that is the protocol, and it is said on screen rather than buried down here
     */
    suspend fun uploadMedia(filePath: String): String

    /**
     * someone's extended profile, empty rather than throwing when the server does not follow.
     * a homeserver without them answers 404, which is a normal case for about half of matrix
     */
    suspend fun profilEtendu(userId: String): li.velvetchat.core.model.ProfilEtendu

    /**
     * extended profiles for several people, in parallel. matrix has no batched read, so yes, it
     * really is n requests; they go out together and get cached, which is what makes an
     * ornament next to every name in the timeline affordable
     *
     * a profile that fails is missing from the result rather than failing everyone else's
     */
    suspend fun profilsEtendus(
        userIds: List<String>,
    ): Map<String, li.velvetchat.core.model.ProfilEtendu>

    /**
     * writes one field of your own extended profile
     *
     * null deletes the field rather than writing an empty string. a cleared pronoun and an
     * empty pronoun would read identically, and the second leaves a dead key in the profile
     *
     * @throws EngineException when the server does not do extended profiles. here, unlike on
     *   read, the failure has to surface: somebody just typed something, and losing it quietly
     *   is the worst answer available
     */
    suspend fun setProfilEtendu(cle: String, valeur: String?)

    /**
     * somebody's activity, read fresh every time. [profilEtendu] caches for a minute and keeps
     * a copy on disk, which is right for a banner and wrong for this: an activity stops
     * existing when the game closes, and a cache would show one you closed before lunch
     *
     * one key, one request, no cache, only while somebody is looking at a card
     *
     * @return the raw json, or null for absent, unreadable, or a server without extended
     *   profiles
     */
    suspend fun activiteDe(userId: String): String?

    /**
     * blocking through `m.ignored_user_list`. the server filters, which is the whole difference
     * with a client side block: the events are never sent rather than arriving and being hidden
     *
     * two things that surprise people. it is per account, so blocking is everywhere. and
     * history already received stays visible, since the filter applies to what arrives
     */
    suspend fun personnesBloquees(): List<String>

    suspend fun bloquer(userId: String)

    suspend fun debloquer(userId: String)

    /**
     * friends, the cheap version. the plan wanted four states and three already exist natively:
     * a request to somebody you never wrote to *is* creating the dm, and pending in and out are
     * that invite from either side. only "we have talked" against "we are friends" needed
     * building, hence one boolean and no state machine — and in element a friend request shows
     * up as a conversation invite, which is what it is
     *
     * in room state, not account data: two pieces of account data desynchronise permanently on
     * the first lost notification, where room state is shared and symmetric by construction, and
     * is unencrypted, so it stays readable inside an encrypted dm
     */
    suspend fun estAmi(userId: String): Boolean

    /**
     * @throws EngineException when the server refuses the write, which happens in a dm created
     *   by another client where the invitee may have no power at all. swallowing it would give
     *   you a button that does nothing
     */
    suspend fun setAmi(userId: String, ami: Boolean)

    /** the ids of everyone the relation says is a friend */
    suspend fun amis(): List<String>

    /**
     * the "only visible to you" note. account data, which is exactly that: private, synced
     * between your own devices, invisible to everyone else. no caveat for once
     */
    suspend fun note(userId: String): String?

    suspend fun setNote(userId: String, note: String?)

    /**
     * a channel's pinned messages, most recently pinned first, as event ids
     *
     * `m.room.pinned_events`, the state event the protocol already has, rather than a marker of
     * our own: a message pinned from velvet is pinned for element too, and the other way round.
     * same reasoning as `m.favourite` for dms and `im.ponies` for emoji
     */
    suspend fun pinnedMessages(channelId: String): List<String>

    /**
     * pinned messages with their bodies. [pinnedMessages] gives event ids, which is enough to
     * pin and unpin and not enough to draw a list, because an id does not read
     *
     * the first details screen resolved them against the conversation already loaded and
     * announced how many it had missed. unnecessary: the sdk builds a timeline focused on pins
     * and fetches them wherever they are. the limit was mine, not the protocol's
     *
     * single pass: reads the state once, emits, completes. it cannot re emit, sliding sync does
     * not carry `m.room.pinned_events`. so a pin set elsewhere shows up next time you open it
     */
    fun pinnedFlow(channelId: String): Flow<List<Message>>

    /**
     * one message with [contexte] either side, however far back. this is what lets you jump to
     * a quote or a search result without scrolling page by page
     *
     * for looking, not writing. the screen has to say you are in the past and offer a way back,
     * or it looks like the conversation simply stopped there
     *
     * @param contexte how many messages on each side
     */
    fun contexteFlow(
        channelId: String,
        eventId: String,
        contexte: Int = 25,
    ): Flow<List<Message>>

    /**
     * writes the whole state, never part of it: a state event has no incremental update. so the
     * list is re read first, since writing without reading wipes every pin already there,
     * other people's included. same trap as the emoji pack, same way out
     *
     * needs power over `m.room.pinned_events`, usually moderators. a refusal comes back as is
     */
    suspend fun setMessagePinned(channelId: String, messageId: String, pinned: Boolean)

    /**
     * a message's `matrix.to` address. built here because it takes knowing that a room id is
     * not routable for anyone not already inside while a public alias is — so the engine picks
     * the alias when there is one
     */
    suspend fun messageLink(channelId: String, messageId: String): String

    /**
     * a snapshot of the last messages, decrypted where possible. older ones come back as
     * `Undecryptable` when the keys have not arrived, which is expected and said rather than
     * hidden. for the live version see [messagesFlow]
     *
     * @param limit how many messages to fetch
     */
    suspend fun messages(channelId: String, limit: Int = 50): List<Message>

    /**
     * markdown becomes an html `formatted_body`, which is the protocol's own mechanism
     *
     * returns immediately: the message goes into the sdk queue, which persists and retries
     * across a dropped connection or a restart. the call says "taken", not "sent" — delivery
     * reads from `Message.sendState` on the local echo
     *
     * waiting for the server before drawing it would produce the exact bug we avoid, a message
     * that looks like it vanished
     */
    suspend fun send(channelId: String, markdown: String)

    /**
     * sends a file. the type comes from the content, not the extension: an image renamed `.txt`
     * is still an image, and a `.jpg` that is not one must not be announced as one. dimensions
     * are read from the headers where the format allows, so receivers reserve the space instead
     * of making the conversation jump when the image lands
     *
     * returns on acceptance like [send] — measured around 100 ms for an 11 mb file where a bare
     * round trip costs 90. the upload carries on in the queue, which persists and resumes, and
     * progress reads from `sendState`. so an [EngineException] is only what fails before the
     * queue, a missing file or an unknown channel; an upload failure reads as `SendState.Failed`
     *
     * @param caption optional, shown next to the media
     */
    suspend fun sendMedia(channelId: String, filePath: String, caption: String? = null)

    /**
     * not [sendMedia] with an audio file: a voice message carries `org.matrix.msc3245.voice`,
     * which tells other clients to draw a bubble you play rather than a file you download
     *
     * @param waveform amplitude between 0 and 1, transmitted rather than computed on playback,
     *   so the other device draws it without downloading the audio first
     */
    suspend fun sendVoice(
        channelId: String,
        filePath: String,
        durationMs: Long,
        waveform: List<Float>,
    )

    /**
     * a media in memory, decrypted, for what gets drawn in the conversation. to save or open a
     * file elsewhere use [mediaFile] and keep several megabytes off the heap
     */
    suspend fun mediaBytes(ref: MediaRef): ByteArray

    /**
     * an avatar, resized by the server. separate from [mediaBytes] because an avatar is a plain
     * `mxc://` and is not encrypted, which is what makes server side resizing possible — it
     * cannot work for media in an encrypted room
     *
     * @param sizePx the side you want, in pixels
     * @return null when there is none, which is not a fault: initials replace it
     */
    suspend fun avatarBytes(mxcUrl: String, sizePx: Int): ByteArray?

    /**
     * a media on disk, in the sdk cache, so another app can open it and a second play costs
     * nothing
     */
    suspend fun mediaFile(ref: MediaRef): String

    /**
     * a thumbnail at the size you ask for — except that **in an encrypted room it shrinks
     * nothing**. the server sees a blob and cannot resize, so the sdk hands back the full
     * media. measured: identical bytes, about 690 kb either way
     *
     * every velvet room is encrypted, so a grid of previews built on this downloads every image
     * in full. draw [MediaRef.blurhash] instead, a few bytes already in the event and no
     * request, and fetch the media only for what somebody opens
     *
     * null when nothing could be fetched, which is not an error either
     */
    suspend fun mediaThumbnail(
        ref: MediaRef,
        width: Int,
        height: Int,
    ): ByteArray?

    /**
     * a matrix space wearing a discord costume. invite only: the space carries the membership
     * and the channels lean on it through [createChannel]. it is not encrypted and needs not be,
     * holding no messages, only the list of its channels
     *
     * a `#general` comes with it, which is not a convenience — a server with no channel is not
     * half finished, it is unusable. if that part fails the server still exists, empty, and a
     * channel can be added after: saying "not created" when it plainly was would be worse
     */
    suspend fun createServer(name: String, topic: String? = null): Server

    /**
     * an encrypted channel, with the two settings that make the model work
     *
     * `join_rule: restricted`, so being in the server is enough to walk in without a named
     * invite, which is discord exactly and what makes [joinServer] possible — without it,
     * joining a server hands you a list of locked doors
     *
     * `history_visibility: shared`, which buys less than it sounds in an encrypted room: events
     * become reachable, not decryptable. earlier megolm keys are not shared with a newcomer, so
     * old history reads as `Undecryptable`. matrix as designed, and nobody does better today
     */
    suspend fun createChannel(
        serverId: String,
        name: String,
        topic: String? = null,
        /**
         * which category to file it under, null for the server root
         *
         * the channel hangs off the category but its right of entry still names the **server**,
         * because that is what people join. pointing `restricted` at the category gives a
         * channel only its members can open, and nobody joins a category, it is a folder
         */
        categorieId: String? = null,
        /**
         * a voice channel. matrix has none in the discord sense; the nearest is the `m.call`
         * room type, which the ecosystem already treats as a place you call from
         *
         * set at creation and never after — `m.room.create` is the one state event the protocol
         * makes immutable. hence the separate path further down
         */
        vocal: Boolean = false,
    ): Channel

    /**
     * a category is a sub space: matrix has no "channel folder", it has spaces inside spaces.
     * no timeline, no members of its own, and the ui never opens it
     *
     * @return the sub space id, to pass as `categorieId` to [createChannel]
     */
    suspend fun createCategory(serverId: String, name: String): String

    /**
     * files a channel into a category, or back to the root. moving means detaching from one
     * parent and attaching to the other: `m.space.child` emptied on the old, written on the
     * new. the join rule does not move, it still names the server, see [createChannel]
     *
     * @param depuis the current category, null at the root
     * @param vers the target category, null for the root
     */
    suspend fun deplacerSalon(serverId: String, channelId: String, depuis: String?, vers: String?)

    /**
     * matrix deletes no room — nothing erases one for other people. what we can do, and what
     * discord visibly does, is detach it (`m.space.child` emptied, as in [deplacerSalon]) so it
     * leaves everyone's column, then leave and forget
     *
     * **detach first.** leaving first can cost the right to write the space state, if that
     * channel was our only claim to being in it, and leaves a ghost: visible and unreachable
     *
     * [parent] is the category holding it, null at the root
     */
    suspend fun supprimerSalon(serverId: String, channelId: String, parent: String? = null)

    /**
     * orders the channels of [parentId], a server or a category. the order used to be whatever
     * the sdk handed back; the protocol had the field all along, `order` inside
     * `m.space.child`, printable ascii sorted lexicographically with the unset coming last
     */
    suspend fun reordonnerSalons(parentId: String, salonsDansLOrdre: List<String>)

    /**
     * joins a server and all of its channels. the auto join is not a comfort: entering a space
     * puts you in none of its rooms, so without it a new member sees an empty hierarchy and
     * clicks channel by channel. sub spaces are walked recursively
     *
     * partly fallible by nature — a channel can refuse entry — so the result says what was
     * joined and what was refused rather than failing as a block or going quiet
     */
    suspend fun joinServer(serverIdOrAlias: String): ServerJoin

    /**
     * the server's invite link, see [li.velvetchat.core.model.LienInvitation]
     *
     * opens the server to whoever holds the link (rule `public`, no directory listing) if that
     * is not already the case, so it needs the right to change the server settings
     */
    suspend fun lienInvitation(serverId: String): String

    /** what the server profile screen shows */
    suspend fun profilServeur(serverId: String): li.velvetchat.core.model.ProfilServeur

    /** writes name, description, banner and the rest, only what actually changed */
    suspend fun setProfilServeur(serverId: String, profil: li.velvetchat.core.model.ProfilServeur)

    /**
     * pending invites, servers and conversations together: the caller sorts them with
     * `estEspace`, and two calls would walk the same room list twice
     */
    suspend fun invitationsRecues(): List<InvitationRecue>

    /**
     * direct messages, as a flow
     *
     * there was none, and the screen re read `directs()` on connection state, a relations
     * counter and entering the view — none of which is about rooms. a conversation arriving
     * through sync had no way of getting itself re read and stayed invisible until you left and
     * came back. the symptom reads as "sync is lagging"; it was the screen not looking again
     *
     * same signal as [serversFlow] and [invitationsFlow], so subscribing costs no round trip
     */
    fun directsFlow(): Flow<List<DirectMessage>>

    /**
     * reclassifies a room as a direct conversation with [userId]. matrix has no dm type: a two
     * person conversation is an ordinary room marked by `m.direct`, and one created without it
     * stays a group forever
     *
     * the discord import poured four hundred thousand events into a room that already existed.
     * having created nothing it never wrote `m.direct`, so twenty five hours of two person
     * history read as "group, 2 participants". fixing that by hand with an access token is what
     * you do once before deciding it needs a button, and there will be more imports
     *
     * the room name is dropped on the way: an explicit name would win over matrix naming a
     * direct after the other person
     */
    suspend fun marquerCommeDirect(salonId: String, userId: String)

    /**
     * invites, as a flow. its own and not a refresh hung off [serversFlow], which returns only
     * joined spaces and ends in a `distinctUntilChanged` — so an invite arriving at an account
     * with no server changed nothing and never reached the screen
     */
    fun invitationsFlow(): Flow<List<InvitationRecue>>

    /**
     * what has not been read, channel by channel, from the running sync rather than a push. see
     * `AlerteSalon` for the distinction and `ServiceVeille` for what uses it
     */
    suspend fun alertes(): List<li.velvetchat.core.model.AlerteSalon>

    /** renames a channel (`m.room.name`), needs the right to write its state */
    suspend fun renommerSalon(channelId: String, nom: String)

    /**
     * accepts an invite
     *
     * a space is not a room, which is the whole reason for the flag: accepting a server invite
     * without entering its channels would give you an empty hierarchy, exactly what [joinServer]
     * exists to avoid. so `estEspace = true` routes through it
     */
    suspend fun accepterInvitation(roomId: String, estEspace: Boolean)

    /**
     * declines an invite, which in protocol terms means leaving a room you never joined.
     * without forgetting it: declining is not erasing, and being invited again has to stay
     * possible
     */
    suspend fun refuserInvitation(roomId: String)

    // control centre

    /**
     * does this account administer the homeserver
     *
     * not to be confused with a room power level. this is an entirely separate synapse field
     * that had no consumer in the app until now. asks the admin api and reads the answer, where
     * a 403 means no rather than broken
     *
     * the result drives whether the control centre entry is visible, never whether it works.
     * the server is what actually closes the door on every call in this section, the app just
     * avoids showing it to people who could not open it
     */
    suspend fun estAdministrateurServeur(): Boolean

    /** live registration tokens. empty when the account is not an admin */
    suspend fun jetonsInvitation(): List<JetonInvitation>

    /**
     * creates a token and returns the complete invite code, ready to share, see
     * [CodeInvitation]. the space is never sent to the server: synapse has no notion of "this
     * token leads here", the code itself carries both halves
     */
    suspend fun creerInvitation(idEspace: String, usages: Int?, expirationJours: Int?): String

    suspend fun revoquerJeton(jeton: String)

    /** every account on the homeserver, not only the ones visible from a shared room */
    suspend fun comptesServeur(): List<CompteServeur>

    /**
     * deactivates or reactivates an account, and never deletes — that would be a separate and
     * deliberate gesture this section does not offer
     *
     * reactivating does not give the access back, which is checked rather than assumed: synapse
     * wipes the password when it deactivates, so setting `deactivated` back to false returns a
     * cheerful 200, the account exists again with its history, and the login then fails with
     * `M_FORBIDDEN` for want of a password to present
     *
     * so it is a one way door from the person's point of view, and the ui has to say so before
     * you pull it rather than let it look like a toggle
     */
    suspend fun setCompteDesactive(userId: String, desactive: Boolean)

    /**
     * direct conversations, read from `m.direct`
     *
     * a completely separate path from [servers], and not for convenience: a two person
     * conversation is a room with no parent space, so the space hierarchy never walks past it.
     * finding them by looking for rooms not reached by any space would be a heuristic, whereas
     * `m.direct` is the protocol saying so explicitly
     *
     * sorted by most recent activity, which is the order the list will show anyway, and doing
     * it here saves every caller rediscovering the same thing
     */
    suspend fun directs(): List<DirectMessage>

    /**
     * friend nicknames, `userId -> nickname`, empty until you set one
     *
     * what discord calls a friend nickname: the name *i* file someone under, which has nothing
     * to do with the name they chose. both exist at once and neither replaces the other, hence
     * [DirectMessage.nomReel] keeping the second
     *
     * stored in account data and not in the other person's profile, because it is my label.
     * putting it in the room would make it visible to them, which is precisely what a nickname
     * is not supposed to be. the corollary is that it follows the account: all my devices see
     * the same nickname, nobody else sees it at all
     */
    suspend fun surnoms(): Map<String, String>

    /** sets or clears a nickname. null or empty deletes the entry rather than keeping a blank */
    suspend fun setSurnom(userId: String, surnom: String?)

    /**
     * opens a conversation with someone, or finds the one that already exists
     *
     * the lookup first is not an optimisation. without it, writing to the same person twice
     * would create two rooms and split the history in half with nobody understanding why
     *
     * @return the room id, to open like any other
     */
    suspend fun openDirect(userId: String): String

    /**
     * a conversation with several people, always created rather than found: [openDirect] exists
     * to reuse what is there, but a group has no identity to look up — `m.direct` files rooms by
     * person, not by set, and three people may well want a second group with the same three
     *
     * matrix has no group dm. discord's is a private unnamed room marked `is_direct`, which is
     * what this makes, so it lands in `m.direct` under **each** invitee and shows up once per
     * person until [directs] deduplicates it
     *
     * and it only reads as a group once somebody accepts, `groupe` going on the joined count: a
     * fresh one looks like a two person conversation until then, and the count is not lying
     *
     * @param userIds who to invite, yourself excluded. with one, [openDirect] is the right call
     * @return the room id, to open like any other
     */
    suspend fun creerGroupeDirect(userIds: List<String>): String

    /**
     * pins or unpins a conversation
     *
     * goes through the protocol's `m.favourite` tag, so other clients see it. inventing a
     * marker of our own would have been easier to write and would have meant a conversation
     * pinned here was pinned nowhere else
     */
    suspend fun setPinned(roomId: String, pinned: Boolean)

    /**
     * the threads open in this channel, most recently active first
     *
     * the `m.thread` relation has been in the protocol since matrix v1.4 and the server says it
     * is on, so this is not an experiment
     *
     * a flow, because a thread is born out of an ordinary message: somebody replies in a thread
     * to something posted a minute ago, and the list has to show it without reopening the screen
     */
    fun filsFlow(channelId: String): Flow<List<Fil>>

    /**
     * the messages of one thread, root included
     *
     * a timeline in its own right and not an excerpt: a thread can run to hundreds of replies,
     * and cramming those into the object that summarises it would make no sense
     */
    fun filFlow(channelId: String, racineId: String): Flow<List<Message>>

    /**
     * replies inside a thread
     *
     * different from [reply], and the difference is not cosmetic: an ordinary reply stays in
     * the main conversation and scrolls it for everyone, whereas a thread reply does not appear
     * there at all. which is exactly the point of opening a thread, getting a discussion out of
     * the common flow without losing it
     */
    suspend fun repondreDansFil(channelId: String, racineId: String, markdown: String)

    /**
     * opens a thread with no message to hang off, the "create" button in the thread list. [nom]
     * is posted in the channel as the root, then [premierMessage] replies to it. returns the
     * root id
     */
    suspend fun creerFil(channelId: String, nom: String, premierMessage: String): String

    /**
     * what this channel is allowed to make noise about
     *
     * returns [ModeNotification.DEFAUT] when no rule is specific to it, which is true of the
     * overwhelming majority of channels and is an answer rather than the absence of one
     */
    suspend fun modeNotification(channelId: String): ModeNotification

    /**
     * `DEFAUT` removes the channel's own rule rather than writing one that imitates the account
     * setting. they look alike today and would diverge the first time that setting changed
     */
    suspend fun setModeNotification(channelId: String, mode: ModeNotification)

    /**
     * the muted channels at once, for the list that greys them out. asking [modeNotification]
     * per row would cost a read per draw when the rules live in one table
     */
    suspend fun salonsMuets(): Set<String>

    /**
     * the muted rooms with a deadline, and when it runs out. matrix has no "muted until" — a
     * push rule is on or off — so we keep it in account data for every device to agree. a room
     * muted with no end is absent here while still being in [salonsMuets]
     */
    suspend fun sourdines(): Map<String, Long>

    /**
     * mutes [roomId] until [finMillis], or with no end when null. unmuting goes through
     * [setModeNotification] with `DEFAUT`, which drops the deadline too: one way in, one way
     * out, rather than a boolean that has to agree with the push rule
     */
    suspend fun setSourdine(roomId: String, finMillis: Long?)

    /**
     * who is typing, you excepted. the sdk expires the notices itself, which avoids the
     * "someone is typing" stuck on screen because they closed the app mid word
     */
    fun frappeFlow(channelId: String): Flow<List<String>>

    /**
     * announces that you are typing. **only call it if the person agreed**: this is a continuous
     * signal about you, broadcast to the server and the room, not a ui effect
     */
    suspend fun signalerFrappe(channelId: String, enCours: Boolean)

    /**
     * the published version, or null. read from the server state: velvet goes through no store,
     * so this is the only way an installed app finds out it is behind
     *
     * null means nothing publishable — no state, or none whose variants survive
     * `Release.depuisJson`, which drops anything without a verifiable digest
     */
    suspend fun releasePubliee(serverId: String, type: String = Release.TYPE): Release?

    /**
     * publishes a version, reserved by power level to whoever can write the state. no client
     * side check replaces that: the server refusing is what makes this an update channel
     * rather than a suggestion box
     */
    suspend fun publierRelease(serverId: String, release: Release, type: String = Release.TYPE)

    /**
     * the installation's hidden room, once joined: where the version announcements live now,
     * next to the config. null when the server has none
     */
    suspend fun salonInstallation(): String? = null

    /**
     * the server's role tiers, highest power first, so "the first tier reached" is the right
     * one without sorting. empty on a fresh server, and the ui falls back on matrix's own
     */
    suspend fun roleTiers(serverId: String): List<RoleTier>

    /** replaces the tier table. like every state event, you write all of it or none of it */
    suspend fun setRoleTiers(serverId: String, tiers: List<RoleTier>)

    /** the decorative roles and who wears them. unlike a [RoleTier] they stack and grant nothing */
    suspend fun decorations(serverId: String): li.velvetchat.core.model.Decorations

    /**
     * the catalogue of rings and frames people pick from — not [decorations], which is
     * decorative roles
     *
     * shared rather than individual uploads: a decoration is something you choose more than
     * something you make. what somebody wears lives in their profile and stores the **image**,
     * not a reference, because an avatar has to draw where no catalogue is loaded
     *
     * empty when the server published none, which is common and not a fault
     */
    suspend fun inventaireDecorations(
        serverId: String,
        /**
         * which family: `decorations`, `effets`, `plaques`. one parameter and not three twin
         * apis — same shape, same state, one word apart. three methods would be three places
         * to fix the same bug
         */
        famille: String = FAMILLE_DECORATIONS,
    ): List<li.velvetchat.core.model.DecorationAvatar>

    /**
     * every server's catalogue, deduplicated, which is what the picker shows. what you wear was
     * already account wide; it was the *choosing* that was not — you had to open the right
     * server, and a direct conversation belongs to none, so it offered nothing
     *
     * same reasoning and mechanism as [emotePacks]: one unreadable server does not empty it
     */
    suspend fun toutesLesDecorations(
        famille: String = FAMILLE_DECORATIONS,
    ): List<li.velvetchat.core.model.DecorationAvatar>

    /**
     * the catalogue that belongs to no server. you wear a cosmetic everywhere, including in a
     * conversation that belongs to no space, so tying it to a server meant joining that server
     * to see a ring for your avatar
     *
     * a dedicated room and not account data: account data is private, so ten people would mean
     * publishing it ten times, where a room is shared by construction. the same pattern matrix
     * already uses for emoji packs
     *
     * it appears nowhere in the ui, and that is deliberate — velvet shows joined spaces and
     * `m.direct`, so a room that is neither is invisible. that was a bug the day the discord
     * import fell into one, and it is the property leaned on here
     *
     * empty when the alias does not resolve, we have no access, or nothing was published. all
     * three degrade the same way, with the picker showing only the servers' catalogues
     */
    suspend fun catalogueGlobal(
        famille: String = FAMILLE_DECORATIONS,
    ): List<li.velvetchat.core.model.DecorationAvatar>

    /**
     * discord's server tag, or null. written in the space state, so defined once by an admin
     * and seen identically by everyone, same mechanism as emoji and decorations
     */
    suspend fun tagServeur(serverId: String): li.velvetchat.core.model.TagServeur?

    /** sets or clears the server tag. needs the power to write its state */
    suspend fun setTagServeur(
        serverId: String,
        tag: li.velvetchat.core.model.TagServeur?,
    )

    /**
     * tags from every server you joined, for the picker in your profile
     *
     * returned with the server id and name, otherwise you could not tell which one you are
     * wearing, because two servers can offer the same emoji
     */
    suspend fun tagsDisponibles(): List<Triple<String, String, li.velvetchat.core.model.TagServeur>>

    /**
     * replaces the whole stock. matrix state replaces rather than merges, so entry by entry
     * would be that many full writes and two quick edits would catch each other up
     */
    suspend fun setInventaireDecorations(
        serverId: String,
        decorations: List<li.velvetchat.core.model.DecorationAvatar>,
        famille: String = FAMILLE_DECORATIONS,
    )

    /**
     * the soundboard: same pattern as the decoration stock, a catalogue set by an admin and
     * seen by everyone. what differs is when it is used, mid call, where nobody has the
     * patience to go and find a file
     *
     * the sound is an `mxc://` like an attachment, uploaded once and cached per device rather
     * than shipped in the app — otherwise adding a sound would mean publishing a release
     *
     * empty when the server set none, which is common
     */
    suspend fun sonsSoundboard(serverId: String): List<li.velvetchat.core.model.SonSoundboard>

    /** replaces the whole catalogue, like [setInventaireDecorations] */
    suspend fun setSonsSoundboard(
        serverId: String,
        sons: List<li.velvetchat.core.model.SonSoundboard>,
    )

    /**
     * replaces the whole decorative table, definitions and assignments. a state event has no
     * partial update, so two devices editing at once means last one wins — acceptable, since
     * handing out a role is rare and done by one or two people
     */
    suspend fun setDecorations(
        serverId: String,
        decorations: li.velvetchat.core.model.Decorations,
    )

    /**
     * kicks from the space **and** its channels. they can be invited back, unlike [ban] — not a
     * difference of degree but of kind: kicking interrupts, banning shuts the door
     *
     * all the channels because being in a space and being in its rooms are two different
     * things: kicking from the space alone leaves the person in every conversation. measured,
     * not assumed
     */
    suspend fun kick(serverId: String, userId: String, reason: String? = null): ModerationResult

    suspend fun ban(serverId: String, userId: String, reason: String? = null): ModerationResult

    suspend fun unban(serverId: String, userId: String, reason: String? = null): ModerationResult

    /**
     * appoints, promotes and mutes. applied to the space **and** every channel, because a power
     * level belongs to the room and not the account: on the space alone it gave "moderator"
     * there and "member" in every conversation, a title with no power where it mattered
     *
     * muting is not raising `events_default`, which is room wide and would silence everyone
     * below it. it is lowering the person past that threshold — promoting, in reverse
     */
    suspend fun setPowerLevel(serverId: String, userId: String, level: Long): ModerationResult

    /**
     * a server's custom emoji (`im.ponies.room_emotes`)
     *
     * carried by the space rather than by each channel, which is what reproduces the discord
     * model where emoji belong to the server and work in all of its channels
     */
    suspend fun emotes(serverId: String): List<CustomEmote>

    /**
     * every emoji across all servers, for the unified picker — including from a direct
     * conversation, which belongs to no server and would otherwise offer none
     *
     * a shortcode collision goes to the first server in list order. two servers can point
     * `:panda:` at two images, somebody has to win, and a stable winner beats a random one
     *
     * `im.ponies.emote_rooms` exists so people can choose their packs. until that setting does,
     * taking everything is the closest thing to the intent
     */
    suspend fun allEmotes(): List<CustomEmote>

    /**
     * an `m.sticker` event and not a message, which is what separates it from an emoji: an
     * emoji goes inside text, a sticker *is* the message, drawn full size with no bubble
     *
     * receiving already worked, sending was missing. see `CustomEmote.estAutocollant` for what
     * makes an image in a pack one
     */
    suspend fun envoyerAutocollant(
        channelId: String,
        autocollant: CustomEmote,
    )

    /**
     * a copy of a message sent into another room, the reference's "forward"
     *
     * the content goes out as it was read, text, image or sticker alike, without its reply or
     * thread: those point at events the other room does not have. an edited message goes out as
     * it reads now
     */
    suspend fun transferer(depuis: String, eventId: String, vers: String)

    /**
     * the same emoji grouped by server. empty servers are left out: the picker makes each one a
     * tab, and a tab opening onto nothing is more noticeable than its absence
     *
     * ordered like [servers], the rail's order — a picker arranged differently from the screen
     * next to it would make you find things twice
     */
    suspend fun emotePacks(): List<EmotePack>

    /**
     * an unencrypted media by `mxc://`, the emoji case. not [avatarBytes], which asks for a
     * resized thumbnail — fine for a photo, and it would flatten the animation out of a webp
     */
    suspend fun emoteBytes(mxcUrl: String): ByteArray?

    /**
     * sends a message whose formatting is given as html rather than inferred from markdown,
     * which covers custom emoji and mentions, neither of which a markdown converter will make
     *
     * @param mentions who gets pinged. not cosmetic: without this field, a link to someone in
     *   the message body triggers no notification at all on their side. the link is what you
     *   see, `m.mentions` is what wakes them up
     * @param mentionneToutLeSalon the `@room` equivalent. separate from the rest because it
     *   names nobody in particular and obeys different push rules
     */
    suspend fun sendHtml(
        channelId: String,
        body: String,
        html: String,
        mentions: List<String> = emptyList(),
        mentionneToutLeSalon: Boolean = false,
    )

    /**
     * adds an image to the server's pack. it goes up unencrypted, like everything in a state
     * event: a pack is a public property of the server, which is what lets other clients draw it
     */
    suspend fun addEmote(
        serverId: String,
        shortcode: String,
        filePath: String,
        /**
         * file the image under stickers rather than emoji
         *
         * asked at upload rather than guessed from the file size, because the two uses have
         * nothing to do with how heavy the image is. see
         * [li.velvetchat.core.model.CustomEmote.estAutocollant]
         */
        autocollant: Boolean = false,
    ): CustomEmote

    /** removes an emoji from the pack. the uploaded media stays on the server */
    suspend fun removeEmote(serverId: String, shortcode: String)

    /**
     * harvests the custom emoji used in a channel into a server's pack
     *
     * built for the discord migration: an imported dm is full of emoji already on the media
     * server as `<img>` inside the messages. this walks the history and writes them into the
     * pack, reusing the images as is rather than uploading anything twice
     *
     * never overwrites a shortcode the server already defines, so yours wins, and the walk is
     * bounded on a very long history
     *
     * @return how many were added, not counting those already there
     */
    suspend fun importerEmotesUtilises(salonId: String, serverId: String): Int

    /**
     * the signed in account's profile, which is what other people see of you
     *
     * separate from [currentSession], which describes the connection (token, device, server)
     * and not the person. the two change for different reasons: you swap phones without
     * changing your name, and change your name without swapping phones
     */
    suspend fun profile(): Profile

    /** changes the display name. it is public and visible from every shared room */
    suspend fun setDisplayName(name: String)

    /**
     * uploaded unencrypted: an avatar belongs to no room and so to no key. worth knowing
     * before you pick a photo rather than after
     */
    suspend fun setAvatar(filePath: String)

    /** removes the avatar. everyone falls back on initials */
    suspend fun removeAvatar()

    /**
     * the server icon, where the rail draws two letters. null removes it. needs the power to
     * write the space state, so an admin: this changes the rail for everyone on the server
     *
     * on the space, not its channels — the space *is* the server, and it is the only icon
     * other matrix clients show. unencrypted, like an avatar: a space carries no key, and an
     * icon is what you show people before they come in anyway
     */
    suspend fun setAvatarServeur(serverId: String, filePath: String?)

    /**
     * a channel's members, with the role derived from their power level
     *
     * a deliberate divergence from discord: a power level is one hierarchical integer, so one
     * role per person, where discord stacks several. keeping stackable decorative roles next to
     * the real power levels would build two systems destined to disagree
     *
     * custom names and colours come from a state event on the space. failing that,
     * [li.velvetchat.core.model.Role] carries matrix's standard tiers, the ones that
     * actually govern permissions
     */
    suspend fun members(channelId: String): List<Member>

    /**
     * on a server the channels follow by themselves thanks to `restricted`. on a single
     * channel the invite also shares earlier megolm keys, so the history comes out readable
     */
    suspend fun invite(serverId: String, userId: String)

    /**
     * the exact mirror of [joinServer]. doing it piecemeal leaves orphan channels: out of the
     * server, still joined, drawn outside any hierarchy
     *
     * @param forget also drops them locally, or they linger in a "gone" state, visible and inert
     * @return the ids actually left
     */
    suspend fun leaveServer(serverId: String, forget: Boolean = true): List<String>

    /**
     * deletes the server for everybody, as far as matrix allows
     *
     * a room cannot be destroyed from a client. so every room of the server is shut (join rule
     * `invite`, so `restricted` no longer lets anyone back in), everyone else is kicked, invites
     * included, then we leave. nothing reachable is left, which is what "deleted" means to the
     * people using it. needs the right to kick and to change the join rule, so an admin
     */
    suspend fun supprimerServeur(serverId: String)

    /**
     * steps out of one channel and stays in the server, which with the `restricted` rule means
     * you can walk back in without being invited again
     *
     * @param forget also drops the local history, or it stays listed as "gone"
     */
    suspend fun leaveChannel(channelId: String, forget: Boolean = true)

    /**
     * key backup and recovery. split out of the rest of the contract because the ui consumes it
     * in a completely different context, settings and onboarding rather than daily navigation
     */
    val recovery: RecoveryEngine

    /** device verification and cross signing */
    val verification: VerificationEngine

    /**
     * push notifications. split out because the caller is too: it is a background wakeup with
     * the app closed, which has nothing to do with navigation
     */
    val notifications: NotificationEngine

    /**
     * the pass that gets you into a call's media
     *
     * trades an openid token for a livekit one from `lk-jwt-service`, which checks on the way
     * that the person is really in the room. **that is where the access barrier to voice
     * lives**: not in the matrix room means no token means no media
     *
     * the engine hands out the token and stops there. mic, speaker, ice and turn belong to the
     * livekit sdk on the app side, which keeps this contract independent of the media stack
     * exactly as the rest of the app is independent of the rust sdk
     *
     * the sfu url comes from the token service, not from `.well-known`: ours answers
     * `M_NOT_FOUND`, so the sdk's `isLivekitRtcSupported()` would say no
     *
     * @param salonId doubles as the livekit room name, deterministic, so two devices land in
     *   the same room without arranging it
     * @throws EngineException when the service refuses or is unreachable. a call button that
     *   does nothing would be the worst answer available
     */
    suspend fun jetonVocal(salonId: String): InfoVocal

    /** my livekit identity in calls, `@me:server:DEVICE`: whose key [envoyerCleAppel] sends */
    fun identiteVocale(): String

    /**
     * sends my media key for this call to [pour], livekit identities, as encrypted to-device
     * messages: nothing lands in the room's conversation. needs [clesAppelFlow] collected for
     * this room, which opens the channel both go through
     */
    suspend fun envoyerCleAppel(salonId: String, index: Int, cle: ByteArray, pour: Collection<String>)

    /**
     * the media keys the others send me for this room, mine left out. to-device ones, and room
     * events too from [depuis] (ms) on, which older element call versions send
     */
    fun clesAppelFlow(salonId: String, depuis: Long): kotlinx.coroutines.flow.Flow<li.velvetchat.core.model.CleAppel>

    /**
     * the ringing side: a state marker (`li.velvetchat.call`) others read to offer joining. the
     * sdk reads matrixrtc membership and cannot write it, hence a marker of our own — fine for
     * an all velvet server, to be replaced the day interop matters
     *
     * idempotent, calling it again refreshes the timestamp
     */
    suspend fun sonner(salonId: String)

    /**
     * hangs up. does not delete the state event, the protocol forbids it, it marks it inactive
     * and [appelEnCours] then returns null
     */
    suspend fun raccrocherSonnerie(salonId: String)

    /**
     * the call in this room, or null — including for a stale marker. an app killed outright
     * leaves a call "in progress" that is not, and offering to join nothing is worse than
     * nothing
     */
    suspend fun appelEnCours(salonId: String): AppelEnCours?

    /**
     * every ringing call, across the watched rooms
     *
     * [appelEnCours] is one shot and per room, so the screen polled the one conversation you
     * happened to be in. checked with two devices: the call goes out, the other phone shows
     * nothing. a call you can only see by already being in the right place is not a call
     *
     * the marker reads from local state with no network, so thirty rooms cost thirty cache
     * reads rather than thirty requests — which is what makes this shape workable
     *
     * a room with no call is absent from the map
     */
    fun appelsEnCoursFlow(salonIds: Collection<String>): Flow<Map<String, AppelEnCours>>

    /**
     * one membership per person, under their own `state_key`. a single shared event would have
     * been easier to read and wrong: state replaces instead of merging, so two people joining
     * at once overwrite each other. a key per person removes the race rather than winning it
     *
     * call it again periodically: the membership carries a timestamp and expires, or an app
     * killed outright leaves somebody in the voice channel forever
     */
    suspend fun rejoindreVocal(salonId: String)

    /** drops your membership in the call. like the ring, it gets marked inactive */
    suspend fun quitterVocal(salonId: String)

    /**
     * who is in voice, expired memberships excluded, which is where the "n in voice" badge
     * comes from. not the sdk's `activeRoomCallParticipants()`: it reads matrixrtc memberships
     * that velvet cannot write, hence the homemade marker. the day interop matters, this
     * implementation changes and its callers do not
     */
    suspend fun participantsVocal(salonId: String): List<String>

    /**
     * lets everyone start and join a call here. the voice markers are state events and
     * `state_default` is 50, so in an older room only an admin could call — unnoticed for a
     * while because the testing ran from the owner's account, and a dm puts both parties at 100
     *
     * touches only the two voice types. needs the power over `m.room.power_levels`
     *
     * @return false when it was already in place: rewriting a state to the same value is still
     *   an event, and would show up in the timeline
     */
    suspend fun autoriserAppels(salonId: String): Boolean

    /**
     * who is in voice room by room, for the badge in the channel list. a room with nobody is
     * absent rather than at zero, and the cadence lives here for the same reason as
     * [presencesFlow]: it has to stay consistent with how markers expire
     *
     * the accepted cost is one state read per room per round, parallel and capped. fine at
     * fifteen channels, not at a hundred. the real fix is not this loop but what carries the
     * information: standard matrixrtc memberships would let the sdk index them from sync for
     * free. that is the same switch interop needs, so it gets done once
     */
    fun vocauxFlow(salonIds: Collection<String>): Flow<Map<String, List<String>>>
}

/**
 * how loud the sdk is. at `INFO` it writes a line per request and per sync step, handy when
 * diagnosing and unusable otherwise. those logs carry room, event and device ids — never a
 * token, but enough to treat as sensitive
 */
enum class EngineLogLevel { ERROR, WARN, INFO, DEBUG, TRACE }

/**
 * connection state, with no "permanent failure" on purpose: the engine never gives up. a phone
 * gets its network back at an unpredictable moment, and a client that had stopped retrying
 * looks broken until somebody restarts it by hand
 *
 * so attempts space out instead of stopping, doubling up to a ceiling
 */
enum class ConnectionState {

    /** no sync running. the initial state, and the one after [MatrixEngine.stopSync] */
    STOPPED,

    /** connecting, or reconnecting after a drop */
    CONNECTING,

    /** synced. what lands on the server comes up through the flows */
    CONNECTED,

    /**
     * unreachable. the engine retries by itself, indefinitely. the ui has nothing to trigger,
     * only something to show
     */
    OFFLINE,
}

/**
 * what the session lets you do. deliberately coarse: the ui does not need to know what cross
 * signing is to decide between a conversation and a verification screen
 */
sealed interface AccessState {

    /** no session open */
    data object SignedOut : AccessState

    /**
     * session open, device not verified. messaging would technically work, but the history
     * stays unreadable and other clients flag the device as suspicious — letting people in
     * here means shipping a degraded experience without saying so
     *
     * @param canUseRecoveryKey there is a backup, so the recovery key path is open
     * @param aUnAppareilTemoin another device can vouch for this one. false for someone who
     *   just registered, whose account has one session and both usual paths shut
     */
    data class VerificationRequired(
        val canUseRecoveryKey: Boolean,
        val aUnAppareilTemoin: Boolean,
    ) : AccessState

    /** session open and device verified. normal use */
    data object Ready : AccessState
}

/** `m.read` against `m.read.private`. both drop the counters; only one is visible to others */
enum class ReadVisibility {
    /** `m.read.private`, nobody else sees it. the default */
    PRIVATE,

    /** `m.read`, published in the room. needed the day a "seen by" exists */
    PUBLIC,
}

/** engine errors, translated from the sdk so its types never leak into the ui */
sealed class EngineException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    class Unreachable(message: String, cause: Throwable? = null) :
        EngineException(message, cause)

    class AuthFailed(message: String, cause: Throwable? = null) :
        EngineException(message, cause)

    class Unexpected(message: String, cause: Throwable? = null) :
        EngineException(message, cause)
}

package li.velvetchat.core.model

/**
 * the client's business models, written in product words (server, channel, role) rather than
 * protocol ones (space, room, power level)
 *
 * deliberate. the whole ui has to think in those terms, and translating from matrix is the
 * engine's job, in exactly one place. otherwise the mapping scatters through the ui and stops
 * being changeable
 */

/** a discord server, which is a matrix space */
data class Server(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val channels: List<Channel>,
    /**
     * the server's categories, including the ones with no channel in them yet
     *
     * without this list a category has no existence of its own, it is only a label its channels
     * carry ([Channel.categorie]), so an empty one has none at all. which is why creating a
     * category used to have precisely no visible effect: the gesture worked and nothing showed
     *
     * in space hierarchy order, like the channels. the server decides how it is arranged, not
     * the alphabet
     */
    val categories: List<Categorie> = emptyList(),
)

/**
 * a category, which is a sub space of the server whose only job is tidying
 *
 * the name alone is not enough, and that is what was missing: creating a channel inside one
 * needs the sub space id, because that is where `m.space.child` gets written. two categories
 * can also share a name, the protocol does not mind
 */
data class Categorie(val id: String, val nom: String)

/**
 * the result of walking into a server
 *
 * joining a server means joining n plus one rooms: the space, then each of its children. some
 * can refuse, with a stricter rule than `restricted`, or belonging to another server, or
 * moderation. a boolean could not say which, and an exception would throw away the work
 * already done
 *
 * so the ui opens the server with what worked and reports the rest
 *
 * @param refused channel id to reason for the refusal
 */
data class ServerJoin(
    val server: Server,
    val joined: List<String>,
    val refused: Map<String, String>,
) {
    val isComplete: Boolean get() = refused.isEmpty()
}

/** a channel, which is a room hanging off the space */
data class Channel(
    val id: String,
    val name: String,
    val topic: String?,
    val kind: ChannelKind,
    val unread: Unread,
    val encryption: EncryptionState = EncryptionState.UNKNOWN,

    /**
     * the category holding this channel, or null when it sits at the server root
     *
     * categories are sub spaces. matrix has no idea of a "channel folder", it has spaces that
     * contain spaces. this is the sub space's name and not its id, because the screen groups
     * and draws, it has nothing to open there
     *
     * what this field fixed, which was not cosmetic: the previous version filtered sub spaces
     * out of the hierarchy on the grounds that they are not channels, and never walked into
     * them. so channels filed into a category appeared nowhere at all. not badly sorted,
     * missing. on any server that is even slightly organised, half the channels were gone with
     * nothing saying so
     */
    val categorie: String? = null,
)

/**
 * the room's encryption state
 *
 * exposed all the way to the ui on purpose. an encrypted room can never go back to being
 * readable in the clear, so it is a property people should be able to see rather than an
 * implementation detail
 */
enum class EncryptionState {
    ENCRYPTED,
    NOT_ENCRYPTED,

    /** we do not know yet, because sync has not reached the room */
    UNKNOWN,
}

enum class ChannelKind {
    TEXT,

    /** drawn differently from the start, and it took a while before it actually did anything */
    VOICE,
}

/**
 * the unread model, which is one of the most discord things about discord: the badge, the bold
 * name, the divider. the counters come straight out of sync so they cost nothing, all that was
 * missing was somewhere to put them
 */
data class Unread(
    val notifications: Int,
    val highlights: Int,

    /**
     * put back to unread by hand, with nothing new in the room
     *
     * `m.marked_unread`, a real account data flag carried by the protocol and understood by
     * element. it is separate from the counter on purpose: a room you deliberately flagged has
     * nothing new in it, so no counter would ever hold the fact
     */
    val marqueNonLu: Boolean = false,
) {
    val hasUnread: Boolean get() = notifications > 0 || marqueNonLu
    val hasMention: Boolean get() = highlights > 0

    companion object {
        val NONE = Unread(notifications = 0, highlights = 0)
    }
}

/**
 * the displayed role, derived from the power level
 *
 * a deliberate divergence: a power level is one strictly hierarchical integer, so one role per
 * person, where discord stacks several. more honest than maintaining two parallel systems
 */
data class Role(
    val name: String,
    val colorArgb: Int?,
    val powerLevel: Long,

    /**
     * a short glyph next to the name, the owner's crown for instance
     *
     * belongs in the same state event as the name and the colour. until that event exists the
     * engine derives a default from the power tier, because that is what people will see
     * anyway, and returning null in the meantime would have left the member list silent about
     * who is in charge
     */
    val icon: String? = null,
)

/**
 * the signed in account, as other people see it
 *
 * different from [Member], which describes someone inside a room and therefore carries a role.
 * a profile has no role: it has as many as it has rooms, and none outside them
 */
data class Profile(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,

    /**
     * what the profile carries beyond the protocol's original two fields
     *
     * matrix had a display name and a photo for a long time and nothing else. extended profiles
     * add free form fields any client that knows about them can read. this is what holds
     * pronouns, the banner and the gradient
     *
     * empty when the server does not implement it, which is the common case and not an error
     */
    val etendu: ProfilEtendu = ProfilEtendu(),
)

/**
 * the extended fields of a profile
 *
 * two families of key, and mixing them would be a mistake. the shared ones take their name from
 * the emerging convention (`m.pronouns`), and exist to be read elsewhere: element and the rest
 * will show them the day they read them, which is the entire point. the ones that belong only
 * to us are prefixed `li.velvetchat.`, because borrowing `m.` for something homemade would
 * claim an interoperability that does not exist
 *
 * @param pronoms free text. "they/them", "il/lui", whatever the person writes
 * @param banniere an `mxc://`. the image at the top of the profile screen
 * @param couleurHaute, couleurBasse the two ends of the gradient, as `#RRGGBB`. chosen rather
 *   than derived. when they are missing the display falls back on a gradient computed from the
 *   id, which is a fallback and not the rule
 */
data class ProfilEtendu(
    val pronoms: String? = null,
    val banniere: String? = null,
    val couleurHaute: String? = null,
    val couleurBasse: String? = null,
    /**
     * the decoration drawn around the avatar, an `mxc://` or null
     *
     * we store the image and not a catalogue id, even though the catalogue exists
     * ([DecorationAvatar]). deliberate: an avatar is drawn everywhere, timeline, member list,
     * call tile, notification, and most of those places do not know which server the person
     * came from, so they cannot resolve an id. a self contained profile always renders, an id
     * that needs resolving only renders where the right catalogue happens to be loaded
     *
     * the trade off, accepted: changing the image behind a catalogue entry does not change it
     * for people already wearing it. which is the correct behaviour, they picked that image
     */
    val decoration: String? = null,

    /**
     * a few lines about yourself, what discord calls a bio
     *
     * multiline, unlike everything else in the extended profile. it is the only field where
     * someone writes sentences, and running them together on one line would make them
     * unreadable. `:emoji:` shortcodes render like they do in a message, which is half the
     * point of the field
     */
    val bio: String? = null,

    /**
     * the server tag being worn, emoji first then text
     *
     * a server defines one ([TagServeur]), and each member chooses to wear exactly one, which
     * then follows them everywhere
     *
     * kept in the profile rather than resolved from the server, for the same reason as
     * [decoration], and here it cuts even harder: discord shows it to people who are not
     * members of that server. an id needing resolution would only render for members, which is
     * to say for the people who already know, which is exactly where it teaches nothing
     */
    val tagEmoji: String? = null,
    val tagTexte: String? = null,

    /**
     * the two colours of the display name style, the gradient discord puts on a nickname. null
     * on both sides and the name keeps its role colour
     *
     * separate from [couleurHaute] and [couleurBasse], which dress the profile card. discord
     * really does have two settings, and merging them would make it impossible to have a
     * readable name on a dark theme
     */
    val nomCouleurA: String? = null,
    val nomCouleurB: String? = null,

    /**
     * the profile effect: an animated image played over the card, as an `mxc://`
     *
     * purely decorative and deliberately understated, discord drops them in front of the
     * profile. rendered down the same path as animated emoji, so it animates when it should
     */
    val effet: String? = null,

    /** the profile frame: a border image laid around the card, as an `mxc://` */
    val cadre: String? = null,

    /**
     * the nameplate: the image behind the name in lists
     *
     * the only one of these ornaments visible anywhere other than the profile card, which is
     * the whole point of it. you wear one to be recognised in a member list
     */
    val plaque: String? = null,

    /**
     * what they are playing, as the compact json of an [Activite]
     *
     * a string and not an `Activite`, because this model is the shape of the profile on the wire
     * and the profile is a flat map of strings. unpack it with `activiteDepuisJson`
     *
     * deliberately **not** in [CLES]: nobody types their rich presence by hand, a game does it,
     * and offering it in the editor would be offering a text field for something that gets
     * overwritten every ninety seconds
     */
    val activite: String? = null,
) {
    companion object {
        /** the shared convention, so other clients can read it */
        const val CLE_PRONOMS = "m.pronouns"

        /**
         * the keys that belong to us alone
         *
         * `m.banner` is not a convention. several proposals are arguing over the name and none
         * has won, so writing under our own prefix is the honest option: it works here, and it
         * claims nothing untrue
         */
        const val CLE_BANNIERE = "li.velvetchat.banner"
        const val CLE_COULEUR_HAUTE = "li.velvetchat.gradient_top"
        const val CLE_COULEUR_BASSE = "li.velvetchat.gradient_bottom"
        const val CLE_DECORATION = "li.velvetchat.avatar_decoration"

        /**
         * the keys for parity with discord's edit profile screen, paid features included, minus
         * the shop and the third party connections, which have no equivalent here
         */
        const val CLE_BIO = "li.velvetchat.bio"
        const val CLE_TAG_EMOJI = "li.velvetchat.tag_emoji"
        const val CLE_TAG_TEXTE = "li.velvetchat.tag_text"
        const val CLE_NOM_COULEUR_A = "li.velvetchat.name_gradient_a"
        const val CLE_NOM_COULEUR_B = "li.velvetchat.name_gradient_b"
        const val CLE_EFFET = "li.velvetchat.profile_effect"
        const val CLE_CADRE = "li.velvetchat.profile_frame"
        const val CLE_PLAQUE = "li.velvetchat.nameplate"

        /** see [activite]. the same constant as [Activite.CLE], named here for symmetry */
        const val CLE_ACTIVITE = Activite.CLE

        /**
         * how long a bio may be, in characters
         *
         * one constant and not one per screen: the phone capped at 190 and the desktop at nothing,
         * so a bio written on the desktop got silently cut the moment somebody opened the field on
         * the phone. the number is the reference client's
         */
        const val BIO_MAX = 190

        /**
         * every key this profile holds, in the order the editor shows them
         *
         * a list and not a comment, because two things need to walk it: the editor, which saves
         * only the keys you touched, and [CLES_COUVERTES], which checks that [valeur] and [avec]
         * know all of them. a key added without its branch would otherwise be silently
         * unsaveable
         */
        val CLES: List<String> = listOf(
            CLE_PRONOMS, CLE_BIO, CLE_BANNIERE,
            CLE_COULEUR_HAUTE, CLE_COULEUR_BASSE,
            CLE_NOM_COULEUR_A, CLE_NOM_COULEUR_B,
            CLE_DECORATION, CLE_EFFET, CLE_CADRE, CLE_PLAQUE,
            CLE_TAG_EMOJI, CLE_TAG_TEXTE,
        )
    }

    /**
     * the field a key names, or null when the key is not one of ours
     *
     * exists so the profile editor can hold a draft keyed by what changed rather than by which
     * of thirteen callbacks fired. without it, saving meant mapping each key back onto a typed
     * setter, which is thirteen places to forget one
     */
    fun valeur(cle: String): String? = when (cle) {
        CLE_PRONOMS -> pronoms
        CLE_BIO -> bio
        CLE_BANNIERE -> banniere
        CLE_COULEUR_HAUTE -> couleurHaute
        CLE_COULEUR_BASSE -> couleurBasse
        CLE_NOM_COULEUR_A -> nomCouleurA
        CLE_NOM_COULEUR_B -> nomCouleurB
        CLE_DECORATION -> decoration
        CLE_EFFET -> effet
        CLE_CADRE -> cadre
        CLE_PLAQUE -> plaque
        CLE_TAG_EMOJI -> tagEmoji
        CLE_TAG_TEXTE -> tagTexte
        CLE_ACTIVITE -> activite
        else -> null
    }

    /** the same profile with one field replaced. an unknown key changes nothing */
    fun avec(cle: String, valeur: String?): ProfilEtendu = when (cle) {
        CLE_PRONOMS -> copy(pronoms = valeur)
        CLE_BIO -> copy(bio = valeur)
        CLE_BANNIERE -> copy(banniere = valeur)
        CLE_COULEUR_HAUTE -> copy(couleurHaute = valeur)
        CLE_COULEUR_BASSE -> copy(couleurBasse = valeur)
        CLE_NOM_COULEUR_A -> copy(nomCouleurA = valeur)
        CLE_NOM_COULEUR_B -> copy(nomCouleurB = valeur)
        CLE_DECORATION -> copy(decoration = valeur)
        CLE_EFFET -> copy(effet = valeur)
        CLE_CADRE -> copy(cadre = valeur)
        CLE_PLAQUE -> copy(plaque = valeur)
        CLE_TAG_EMOJI -> copy(tagEmoji = valeur)
        CLE_TAG_TEXTE -> copy(tagTexte = valeur)
        CLE_ACTIVITE -> copy(activite = valeur)
        else -> this
    }

    /**
     * this profile, with [cles] taken from [brouillon] and everything else from here
     *
     * what it is for: an image upload writes at once and the profile comes back from the server
     * while a draft is open. the draft must take the new picture without putting back the old
     * value of a field somebody has since edited elsewhere
     */
    fun fusionner(brouillon: ProfilEtendu, cles: Set<String>): ProfilEtendu =
        cles.fold(this) { acc, cle -> acc.avec(cle, brouillon.valeur(cle)) }
}

/**
 * a server's custom emoji, on the `im.ponies.room_emotes` extension rather than something homemade,
 * because several matrix clients already read it — so an emoji posted here shows up elsewhere. the
 * protocol does not tell still from animated: a webp and a png go through the same field
 */
data class CustomEmote(
    /** the name between the colons, minus the colons: `PandaLove` for `:PandaLove:` */
    val shortcode: String,
    /** the media's `mxc://` */
    val url: String,

    /**
     * sticker rather than emoji, the `usage` field — the same state event carries both and only
     * this separates them. not cosmetic: an emoji goes inside a text message between colons, a
     * sticker *is* the message, an `m.sticker` event at full size with no text around it
     *
     * confusing the two had a visible effect. stickers from a pack made in element turned up in
     * the `:name:` autocomplete where they have no business being, and nowhere at all in the
     * tab named after them
     *
     * defaults to false, since a pack with no declared `usage` is an emoji pack, which is what
     * the spec says
     */
    val estAutocollant: Boolean = false,
)

/**
 * one server's emoji, with enough to name it and show it
 *
 * the protocol has no such notion. `im.ponies.room_emotes` is room state, so every pack is
 * already "that server's" without having to say so. it is the picker that needs telling, since
 * it groups by server with the avatar as the landmark, and a flat list of emoji lost that
 * information before it reached the screen
 *
 * different from [allEmotes][li.velvetchat.core.MatrixEngine.allEmotes], which stays the
 * right shape for autocomplete: typing `:pan` you are not thinking about which server it
 * came from
 */
data class EmotePack(
    val serverId: String,
    val serverName: String,
    val serverAvatarUrl: String?,
    val emotes: List<CustomEmote>,
)

/**
 * a power level dressed up with a name, a colour and a glyph. matrix's is a strictly hierarchical
 * integer, so one per person, where discord stacks freely — and two systems side by side, real
 * power levels plus stackable decoration, would be less honest and more fragile than one role
 *
 * @param power the threshold from which this tier applies
 */
/**
 * same shape as [ServerJoin] and for the same reason: a server is n plus one rooms, so a kick can
 * land in some and fail in others — a room owned by another server, or power here and none there.
 * a boolean cannot say which, and an exception would throw away the work already done
 *
 * @param refused channel id to reason for the refusal
 */
data class ModerationResult(
    val applied: List<String>,
    val refused: Map<String, String>,
) {
    val isComplete: Boolean get() = refused.isEmpty()
}

/**
 * not a room type, which governs the whole implementation: a two person conversation is an ordinary
 * room marked direct by `m.direct`. it belongs to no space, so [MatrixEngine.servers] never sees it,
 * hence a separate read path entirely
 *
 * @param userId the other person, because the list shows somebody rather than a room, and it is
 *   what gives the fallback avatar and the identity colour
 * @param preview the last line, or null — optional so the row contracts rather than reserving space
 * @param online null means we do not know, not that they are away. synapse often turns presence off
 *   for what it costs, and grey would look like an absence somebody actually observed
 */
data class DirectMessage(
    val roomId: String,
    val userId: String,

    /**
     * the name to show, which is the nickname when there is one and [nomReel] otherwise
     *
     * decided here at the source rather than by each caller. the conversation list, the header,
     * the notification and the android shortcut all draw a person's name, and the first one to
     * forget the nickname would have been enough to make you doubt it ever saved
     */
    val name: String,

    /**
     * the name the person chose for themselves, always there
     *
     * equals [name] until a nickname is set. used where both have to be visible, like the
     * profile card, which shows what you filed them under and who they actually are
     */
    val nomReel: String = name,

    val avatarUrl: String?,
    val preview: String?,
    val timestamp: Long?,
    val unread: Unread,

    /**
     * pinned to the top of the list
     *
     * carried by the `m.favourite` matrix tag. the protocol has one, and borrowing it instead
     * of inventing a marker means the pin shows up in element and the other clients too. same
     * reasoning as the emoji (`im.ponies`)
     */
    val pinned: Boolean = false,

    val online: Boolean? = null,

    /**
     * more than two people in the room, so discord's group dm
     *
     * `m.direct` files a group room under each participant, which makes it look like several
     * one to one conversations that happen to share an id. the member count is the only thing
     * that tells them apart, and the context menu needs to know: a group has no profile to
     * open, no nickname to set and nobody to unfriend
     */
    val groupe: Boolean = false,
)

/**
 * the tag a server offers its members, discord's server tag. what we had instead were role tiers
 * computed from matrix power in whichever room you were looking at, so they said "can moderate
 * here" — nothing like discord's, which is chosen, identical everywhere, and visible to people who
 * are not even members of the issuing server
 *
 * @param emoji one emoji character, or an `mxc://` of one of the server's own
 * @param texte four characters at most, like discord: past that a tag stops being a badge and
 *   becomes a second line of name
 */
/**
 * the families of cosmetics a server can catalogue. strings and not an enum, because they end up in
 * a matrix state event type, so in a protocol key: an enum would add a conversion at both ends for
 * the same value, plus a migration the day a fourth turned up — which, as the lines below show, it did
 */
const val FAMILLE_DECORATIONS = "decorations"
const val FAMILLE_EFFETS = "effets"
const val FAMILLE_PLAQUES = "plaques"
/** profile frames. catalogue `li.velvetchat.cosmetics.cadres`, published by `tools/decoration-discord.py` */
const val FAMILLE_CADRES = "cadres"

/**
 * tag badges, the little pixel art blobs a server tag carries
 *
 * what discord puts in its tags is not a character but a 16 px image
 * (`clan-badges/<server>/<hash>.png`), which is why no emoji font ever reproduced them. a
 * [TagServeur]'s `emoji` field already accepts an `mxc://`, this family just supplies something
 * to put in it
 */
const val FAMILLE_TAGS = "tags"

data class TagServeur(
    val emoji: String,
    val texte: String,
) {
    companion object {
        /** discord's limit, taken as is */
        const val LONGUEUR_MAX = 4
    }
}

data class RoleTier(
    val power: Long,
    val name: String,
    val colorArgb: Int?,
    val icon: String?,
)

/**
 * a title you wear with no power attached, which is why it could not be one more [RoleTier]: tiers
 * are indexed by power level, so a person has exactly one. right for who is in charge, since matrix
 * permissions are an integer and not a set — wrong here, where discord shows a dozen on the same
 * person, none granting a single right. they are labels, they stack, and routing them through power
 * would mean inventing twelve meaningless levels to tell them apart
 *
 * so the two read differently: the tier says what you can do, this says who you are. a moderator
 * can also be "Ratatoing", and it changes nothing about what they moderate
 *
 * @param id stable and never reused, since it is what the assignments store: renaming a role must
 *   not take it off the people wearing it
 */
data class RoleDecoratif(
    val id: String,
    val nom: String,
    val colorArgb: Int?,
)

/**
 * a server's decorative roles and who wears them, in one object
 *
 * both live in the same state event, so they are read and written together. splitting them
 * would let an assignment survive pointing at a deleted role, with nothing able to catch it
 *
 * @param parMembre `userId -> role ids`, in display order. the order matters: the first
 *   coloured role is the one that colours the name, like discord where the highest role wins
 */
data class Decorations(
    val roles: List<RoleDecoratif> = emptyList(),
    val parMembre: Map<String, List<String>> = emptyMap(),
) {
    /** the roles someone wears, resolved and in order. empty when they wear none */
    fun de(userId: String): List<RoleDecoratif> {
        val ids = parMembre[userId] ?: return emptyList()
        // `mapNotNull` and not `map`. an assignment can point at a role deleted from another
        // device, and showing a gap beats crashing
        return ids.mapNotNull { id -> roles.firstOrNull { it.id == id } }
    }
}

/**
 * an entry in the server's stock of avatar decorations — the ring or frame around the avatar, not
 * [RoleDecoratif] or [Decorations], which are the coloured badge beside a name
 *
 * a shared stock rather than individual uploads, because a decoration is something you pick more
 * than something you make, which is what makes it usable by somebody with no image to hand. the
 * catalogue lives in the server state like the role tiers: one administrator, the same view for all
 *
 * @param id stable, so an entry can be renamed without breaking the catalogue
 * @param mxc the image, already uploaded to the homeserver
 * @param anime true for a gif, and it exists so we do not animate everywhere: forty animated rings
 *   in a member list is forty render loops. animated on the profile screen, still elsewhere
 */
data class DecorationAvatar(
    val id: String,
    val nom: String,
    val mxc: String,
    val anime: Boolean = false,
    /**
     * the collection this came from ("Mermaid Melodies", "Dark Folklore"), or null
     *
     * discord's shop files its cosmetics by collection, and that is what makes six hundred
     * objects browsable: you go looking for the mermaid pack, not for a ring's name. the
     * scraper reads it out of the shop response and publishes it with the entry. an older
     * catalogue has none, hence the null
     */
    val pack: String? = null,
    /**
     * the shop's theme, one level above the pack, or null. the grid files by theme then by pack
     */
    val theme: String? = null,
    /**
     * a profile frame's second image, null for everything else
     *
     * a discord frame is not one image but two, a top half and a bottom half laid either side of
     * the card, each with its own mask. 1312 by 538 up top, 1312 by 328 underneath, for a
     * container 1200 wide that they overflow by 126, 116 and 56
     *
     * the field lives here and not in the profile: the key someone wears stays a single address,
     * the top half, and the catalogue is what knows there is a second. one more profile key
     * would have had to cross the model, the engine and both editors to say what the catalogue
     * already knows, and a frame imported by hand still only has one image anyway
     */
    val bas: String? = null,
    /**
     * a light still image for the picker grid, or null
     *
     * the server cannot make one. it resizes an apng by keeping its first frame, and a profile
     * effect starts empty and builds up, so the thumbnail came back transparent: a grid of grey
     * squares. the preview is therefore chosen at publish time, on the fullest frame of the
     * animation
     *
     * missing for anything imported from the gallery yourself, and then we fall back on [mxc],
     * which for a gallery image is still and light anyway
     */
    val apercu: String? = null,
    /**
     * the layers of a scraped frame, or empty for a one or two image frame, see [bas]
     *
     * a frame stacks up to five images, in front of or behind the card, anchored top, bottom or
     * full height, and the overflows vary from one frame to the next: 126 to 304 at the top, 0
     * to 212 at the bottom, 0 to 56 on the sides, for a container 1200 wide
     */
    val calques: List<CalqueCadre> = emptyList(),
    val debordHaut: Int = 126,
    val debordBas: Int = 116,
    val debordCote: Int = 56,
)

/** one image of a profile frame, in image pixels, for a container 1200 wide */
data class CalqueCadre(
    val mxc: String,
    /** drawn behind the card rather than in front */
    val derriere: Boolean,
    /** `top`, `bottom` or `center` */
    val ancre: String,
    val largeur: Int,
    val hauteur: Int,
    /**
     * how the reference lays the layer out, read off its client: `staple` overhangs the card at
     * its anchor, `rail` fills the card's height with one image fitted to the width and pinned at
     * its anchor, `border` fills it with the image repeated downwards
     *
     * null for a catalogue cached before the field existed: a centre layer was a border then, and
     * everything else a staple
     */
    val type: String? = null,
) {
    val genre: String get() = type ?: if (ancre == "center") "border" else "staple"
}

/**
 * a noise you fire into a live call, filed here and not beside the voice code because it is server
 * catalogue exactly like [DecorationAvatar]: one administrator, the same view for everyone
 *
 * @param id stable, so renaming does not break the catalogue
 * @param nom what shows under the tile
 * @param mxc the uploaded audio. any format the platform decoder reads, and it decides, not us
 * @param emoji the glyph on the tile, optional and not a defect when absent: a grid of noises reads
 *   by name first, and the emoji is one more landmark for aiming quickly mid call
 */
data class SonSoundboard(
    val id: String,
    val nom: String,
    val mxc: String,
    val emoji: String? = null,
)

data class Member(
    val userId: String,
    /**
     * the name to show, meaning the nickname you set when there is one, otherwise the one the
     * person chose
     *
     * decided at the source, like [DirectMessage.name]. a nickname every screen has to remember
     * to apply is a nickname you believe saved and see nowhere
     */
    val displayName: String?,
    val avatarUrl: String?,
    val role: Role?,
    /** the name the person chose, when a nickname hides it. null otherwise */
    val nomReel: String? = null,
)

/**
 * a persisted session
 *
 * `@Serializable` because the platform encrypts it and writes it to disk (see
 * `session/SessionStore.kt`), so the format has to match on both sides
 *
 * it holds an access token, which is everything needed to be you without knowing your password.
 * never log this
 */
@kotlinx.serialization.Serializable
data class Session(
    val userId: String,
    val deviceId: String,
    val homeserverUrl: String,
    val accessToken: String,
    val refreshToken: String?,
    val slidingSyncNative: Boolean,
    /**
     * the oauth client the session was issued to (json, owned by the sdk). null for a password
     * session. without it the sdk cannot refresh a token, and an oauth token lives five minutes
     */
    val oauthData: String? = null,
)

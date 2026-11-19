package li.velvetchat.design

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * theme tokens, read at runtime from json
 *
 * the plan asks to cover most of vencord's real use without the plugin engine building site:
 * changing a value in the file has to change the app, with no recompilation. hence a model that is
 * entirely serialisable and entirely failable, every field having a default value, so a partial
 * theme stays valid, and a malformed theme cannot make the app unusable
 *
 * this module renders nothing, it describes. translating into compose styles belongs to the ui
 */
@Serializable
data class Theme(
    val name: String = "Velvet Teal",

    /**
     * a dark background by default. light mode will come as a second token file, not as a branch in
     * the code
     */
    val dark: Boolean = true,

    val colors: Colors = Colors(),
    val radii: Radii = Radii(),
    val spacing: Spacing = Spacing(),
    val tailles: Tailles = Tailles(),
    val typography: Typography = Typography(),
    val density: Density = Density(),
    val motion: Motion = Motion(),
    val images: Images = Images(),
)

/**
 * the images a theme carries, named by the **hash of their contents** — the sixty four hex
 * characters `MagasinFonds` gives what it stores — and never by a path. a path points outside the
 * app, so it breaks the day somebody tidies their photos, and silently. worse, a path inside a
 * theme people pass around is a door: the file would say what to read on the machine of whoever
 * opens it, where a hash can only name what the store already holds, and the store takes nothing
 * but hashes
 *
 * a theme may also carry the image **inline** as `data:image/...;base64,...`, which is what makes
 * it one file to send. on reading it is filed into the store and replaced by its hash
 *
 * empty means "none", not "the default": a theme silent about images leaves whichever is set
 */
@Serializable
data class Images(
    val chatBackground: String = "",

    /**
     * how much of the wallpaper shows under the text
     *
     * this is not the image's transparency, it is the **dark veil** laid over it: without the
     * veil, light text on a light photograph is unreadable, and that is half of all
     * wallpapers. 0 leaves the photograph bare, 1 drowns it
     */
    val chatBackgroundVeil: Float = 0.35f,

    val dmIcon: String = "",
)

/**
 * the spacing scale, asked for since v1 and put off on every pass, the number of literal values
 * growing as the interface grew
 *
 * steps of 4 up to `m`, then wider ones: it is what the screens already used in practice (`4`, `8`,
 * `12`, `16`, `20`, `28` come up everywhere), and the scale only names what existed without a name
 *
 * what it is not: a retroactive obligation to convert everything at once. a literal value
 * describing a physical measure, a rule's thickness, a touch target's height, has no business in a
 * spacing scale and rightly stays literal
 */
@Serializable
/**
 * the named scale, and the grid it comes from — these five steps are not all the interface's
 * measurements, and saying so would be false. over the app's 506 `padding()`/`spacedBy()` calls,
 * 88 % of the values are even: the clone is built on a 2 dp grid, not on six steps, which follows
 * from aligning screenshot by screenshot on discord, who had no reason to land on ours
 *
 * so, decided rather than suffered: a standard step takes its name (`espXs` to `espXl`), a hand cut
 * value stays a number — `esp10` adds nothing to `10.dp` and would be eleven more names that are
 * themselves numbers — and the 43 odd values are the only real inconsistencies, left alone on
 * purpose, because putting them on the grid would move the rendering by a point and fidelity beats
 * regularity
 *
 * for new code: a named step when it fits, an even value otherwise. an odd one means something was
 * measured by eye rather than against the reference
 */
data class Spacing(
    val xs: Int = 4,
    val s: Int = 8,
    val m: Int = 12,
    val l: Int = 16,
    val xl: Int = 20,

    /** a full screen's margins, one step above `xl`, to breathe */
    val ecran: Int = 28,
)

/**
 * colours in `#RRGGBB` or `#AARRGGBB`
 *
 * decision reversed at the project owner's explicit request
 *
 * v1 laid down a teal/cyan palette so as to cross neither nest's and café leblanc's persona 5 red
 * and black nor star stream's navy blue, and the charter noted that no value was taken from
 * discord, which is the only genuinely sensitive point legally. the request is now a faithful
 * clone, and it was restated after that reservation was raised. it is a product decision, and it is
 * recorded as such
 *
 * what is not taken, for want of being able to: the font (gg sans is proprietary, we stay on the
 * system's) and the brand icons (redrawn as glyphs of the same shape). those are material limits,
 * not reservations
 */
@Serializable
data class Colors(
    /** the blue violet of actions and selection */
    val accent: String = "#5865F2",
    val accentMuted: String = "#4752C4",

    /** the rail: outright black, which makes the server icons stand out like tokens */
    @SerialName("background") val background: String = "#000000",

    /** the rooms panel, barely lighter than the rail */
    @SerialName("surface") val surface: String = "#0D0E11",

    /** a selected row, the search pill, the sheets' cards */
    @SerialName("surfaceRaised") val surfaceRaised: String = "#232428",

    val border: String = "#2B2D31",
    val textPrimary: String = "#F2F3F5",
    val textSecondary: String = "#B5BAC1",
    val textMuted: String = "#80848E",
    val danger: String = "#F23F43",
    val warning: String = "#F0B232",
    val success: String = "#23A55A",

    /**
     * the mention badge: red, against the rule that forbade it
     *
     * that rule said an unread is not an error and must therefore not be red, a reasoning that
     * holds but that described another app. the clone takes discord's convention: a discreet white
     * dot for "there is something new", a red badge for "you were named". both levels do exist,
     * they are simply said differently
     */
    val unreadBadge: String = "#F23F43",
    /**
     * the background of a row where you are named. muted amber, and the value is measured
     *
     * read at the pixel on a reference screenshot: `(38, 33, 23)`. a first version had been
     * derived, the reference's amber at 10 % over the conversation background, and gave `#2A2316`,
     * within three units. the measurement beats the calculation, because it makes no assumption
     * about the background actually painted underneath
     *
     * the old `#3B3A2E` had r and g nearly equal: it read khaki, not amber, and it was twice too
     * light
     */
    val mentionHighlight: String = "#262117",

    /**
     * the mentioned row's edge. measured, not derived
     *
     * read at the pixel on a reference screenshot: `#DEAA41` over 5 px of a 1008 wide capture for a
     * 1344 screen, so a little over 2 dp. `warning` (`#F0B232`) served until then, and it is
     * noticeably brighter: next to the `#262117` background, the difference shows
     */
    val mentionBar: String = "#DEAA41",

    /**
     * the elbow line linking a reply to the quoted message
     *
     * measured at the pixel on a reference screenshot: `(59, 60, 67)`. `border` (`#2B2D31`) served
     * until then, noticeably darker, to the point where the line disappears on a black background
     * although it is the only thing saying the relation
     */
    val traitCitation: String = "#3B3C43",

    /**
     * the opacity of the veil laid under a drawer or a sheet
     *
     * a number among `#RRGGBB` strings, and deliberately: the veil has no colour of its own, it is
     * `background` made partly transparent. giving it its own colour would create a second black to
     * keep in sync with the first
     */
    val scrimAlpha: Float = 0.7f,

    /** the conversation's background, distinct from the rooms panel */
    val chatBackground: String = "#131416",

    val unreadDot: String = "#F2F3F5",
)

/**
 * radii in dp. generous rounded corners, identical to discord in intent, a deliberate departure
 * from the rest of the ecosystem's persona 5 cut corners, fidelity of feel winning here over house
 * consistency
 */
@Serializable
data class Radii(
    val small: Int = 4,
    val medium: Int = 8,
    val large: Int = 16,

    /** server rail icons: round at rest, rounded on selection */
    val serverIcon: Int = 24,
    val serverIconSelected: Int = 16,
)

/**
 * the sizes of recurring elements, taken out of the screens where they were hard coded
 *
 * these are sizes, not spacings: mixing them into [Spacing] would make the scale unreadable, you do
 * not adjust a badge's size by changing a screen's margin
 */
@Serializable
data class Tailles(
    /** a server icon in the rail */
    val iconeServeur: Int = 48,

    /**
     * the unread badge, and the text size that goes in it
     *
     * 16 and 10, and not 18 and 9 as the charter announced: they are the values the code actually
     * uses since the badge was reworked. the token records what exists rather than imposing a
     * visual change on the way
     */
    val badge: Int = 16,
    val badgeTexte: Int = 10,

    val boutonEnvoi: Int = 40,
)

/**
 * two families, like discord: a very readable font for running text, and a font with tabular
 * figures for everything that has to line up vertically, timestamps, unread counts. without tabular
 * figures, a column of numbers jitters
 *
 * no discord font is taken: it is the typographic structure that is reproduced, not the protected
 * asset
 */
@Serializable
data class Typography(
    val bodyFamily: String = "Inter",
    val tabularFamily: String = "Inter",
    /**
     * 16, and not 15: it is the reference's size, measured on a screenshot
     *
     * one point of difference does not show on a word, it shows over a whole column of
     * conversation, where it gives the impression of a dense app where the reference is
     * comfortable. it is the kind of gap you only find comparing two screenshots side by side,
     * never looking at your own
     */
    val bodySize: Int = 16,
    val bodyLineHeight: Int = 20,
    val headingSize: Int = 17,
    val captionSize: Int = 12,
    val codeFamily: String = "JetBrains Mono",
    val codeSize: Int = 13,

    /**
     * headings inside a message (`#`, `##`, `###`), not [headingSize], which dresses a room header
     *
     * ratios and not absolute sizes: a heading has no size of its own, it is larger than the
     * running text. in dp the three would need retouching every time [bodySize] moved, and the
     * first forgotten pass would collapse the hierarchy with nothing flagging it
     *
     * the values are discord's, `1.5` / `1.25` / `1.0` of the body, all bold. a first version set
     * them by eye — 23 / 20 / 17 dp for a body of 15, so 1.53 / 1.33 / 1.13 — and the side by side
     * showed the gap, worst on the third level
     *
     * `###` matching the body exactly looks like a mistake until it is said: bold tells it apart,
     * and a third level barely larger is worse than no difference, since you believe you are
     * reading a hierarchy too faint to be one. three levels, not six — 4 to 6 fall back on the third
     */
    val heading1Scale: Float = 1.5f,
    val heading2Scale: Float = 1.25f,
    val heading3Scale: Float = 1.0f,

    /**
     * the small text of `-#`: the aside, the detail you add without interrupting
     *
     * `0.8` of the body, discord's value again. smaller and duller (`textMuted`): the size alone
     * would give small text still in the foreground, which is exactly the opposite of the intent
     */
    val subtextScale: Float = 0.8f,

    /**
     * discord's jumbo: a message containing only emojis enlarges them
     *
     * it is not a decoration, it is a reading. a lone `👍` is an answer, not a sentence, and at text
     * size it gets lost in the column although it is all the message says. enlarging it makes the
     * conversation more readable, not prettier
     *
     * `2.5` of the body, so 40 dp for a body of 16, aligned on the reference, which goes from 22 to
     * about 48 px at density 1
     */
    val jumboEmojiScale: Float = 2.5f,

    /**
     * past this number of emojis, the message stays at text size
     *
     * there has to be a bound, otherwise the rule turns against itself: thirty emojis at 40 dp take
     * the whole screen and stop being readable, which is the opposite of the aim. `27` is the
     * reference's threshold, high enough that nobody meets it by accident, low enough that a paste
     * of two hundred emojis does not make the page scroll
     */
    val jumboEmojiMax: Int = 27,
)

/**
 * density identical to discord: reproducing the amount of information on screen, and not airing it
 * out as a classic mobile app would
 */
@Serializable
data class Density(
    val serverRailWidth: Int = 72,
    val channelListWidth: Int = 240,
    val memberListWidth: Int = 240,
    val messageVerticalPadding: Int = 2,

    /**
     * extra spacing when the author changes: it is what creates the groups
     *
     * it is a margin, not padding, and that is where the gap with the reference was, not in its
     * value. it was applied inside the row's background: a row where you are named therefore had
     * its amber band stretch twelve points upwards, and measured 131 px where the reference has
     * 106. there, group spacing is an outer margin, which the highlight does not swallow
     *
     * the value itself goes back to 12: raising it to 16 had been decided on a baseline
     * measurement, which confused the line height with the padding
     */
    val messageGroupSpacing: Int = 12,
    val channelRowHeight: Int = 34,

    /**
     * the maximum gap between two messages from the same author to stay in the same group
     *
     * `messageGroupSpacing` only says what to do when the author changes, never when the same
     * author restarts a group. without this threshold, two messages sent three hours apart would
     * stay visually welded
     *
     * 7 minutes: discord's observed threshold, taken as a behaviour reference rather than
     * rediscovered by trial and error
     */
    val messageGroupWindowMs: Long = 420_000,

    /**
     * the avatar laid to the left of a message, and the gutter grouped messages keep
     *
     * one number for both: inside a group the following messages show no avatar but keep exactly
     * the indent it occupied. that is what holds the text column together, two independent values
     * would end up misaligning
     */
    val messageAvatarSize: Int = 40,

    /** the quick reactions bar opened by a long press on mobile */
    val quickReactionBarHeight: Int = 44,

    /**
     * the horizontal drag distance past which "reply" fires
     *
     * the threshold is what separates a drag from a badly aimed scroll: without it, any accidental
     * sideways movement would open a reply. it is also where the haptic fires, so the confirmation
     * comes before you release, which lets you back out by coming back
     */
    val swipeReplyThresholdDp: Int = 64,

    /**
     * the strip of conversation that stays visible when the rooms drawer is open
     *
     * it is not decorative: it is the grip you close the drawer by, and the reminder that you have
     * not left the conversation but slid something over it. a drawer covering everything would be a
     * disguised screen change
     *
     * it is also what sets the drawer's width: the drawer takes the screen minus this strip. the
     * room list therefore takes all the available space rather than `channelListWidth`'s 240 dp,
     * which describes a desktop column and makes no sense here
     */
    val conversationPeekWidth: Int = 56,

    /**
     * the speed past which a drag is a flick, in dp per second
     *
     * without it, a drawer followed at 1:1 under the finger means crossing the whole screen to open
     * it, which is exhausting to repeat. the flick solves that without cheating on the movement:
     * the drawer still follows the finger exactly, and a sharp stroke is enough to decide it.
     * shortening the travel would have made the tracking dishonest instead
     */
    val flingVelocityDpS: Int = 400,

    /**
     * the fraction of the travel past which a slow drag opens rather than closes
     *
     * under half, deliberately: opening has to be easier than closing, since closing always has a
     * second way out, the strip of conversation sticking out, and the back gesture
     */
    val drawerOpenFraction: Float = 0.3f,

    val memberDrawerWidth: Int = 280,
)

/**
 * discreet animations, and above all no costly continuous one. not cosmetic: the rule comes from the
 * real time liquid glass that bogged nest and café leblanc down in native, and it is the same cause
 * and remedy as capping the gifs
 *
 * it says nothing about how long a one off transition lasts. a continuous animation costs by never
 * stopping, where a screen switch costs only while it runs, and 260 ms weigh no more than 160 over
 * a session — the two questions had been confused, and the original durations tuned for a problem
 * they did not solve
 *
 * what feels dry is the curve, not the brevity: a linear movement starts and stops dead at any
 * duration, which reads as a state switch. a warm interface decelerates like a thing with mass
 */
@Serializable
data class Motion(
    val fastMs: Int = 140,
    val normalMs: Int = 260,
    val slowMs: Int = 380,

    /**
     * the entrance curve: starts briskly, settles slowly
     *
     * the asymmetry is deliberate and justified by attention: what arrives has to be seen arriving,
     * so it finishes gently, where the eye follows it. what leaves no longer needs watching, so it
     * goes fast
     */
    val enterCurve: Curve = Curve(0.05f, 0.7f, 0.1f, 1f),

    val exitCurve: Curve = Curve(0.3f, 0f, 0.8f, 0.15f),

    /**
     * the spring of finger driven movements (a drag's elastic return)
     *
     * a damping below 1 leaves a slight overshoot: that is what tells "the thing came back" from
     * "the thing was put back". too low and it would bounce like a toy, and 0.72 is felt without
     * being seen
     */
    val springDamping: Float = 0.72f,
    val springStiffness: Float = 260f,

    /**
     * the drawer's spring, distinct from the previous one, and it took noticing
     *
     * the same spring served both, and what suits a message row coming back six millimetres does
     * not suit a panel crossing three hundred and fifty points. at equal stiffness the settling
     * time is the same in both cases: what read as "elastic" on the row read as "soft" on the
     * drawer
     *
     * the numbers: at 260 stiffness and 0.72 damping, the drawer takes about 350 ms to settle,
     * overshoot included. at 800 and 0.92 it takes 150, and it practically no longer overshoots,
     * which is what we want here, a drawer bouncing against its edge looking dropped rather than
     * put away
     */
    val drawerSpringDamping: Float = 0.92f,
    val drawerSpringStiffness: Float = 800f,

    /**
     * a page sliding over another, measured and not chosen: a screen recording of the reference at
     * 43 frames per second, the incoming page's leading edge followed frame by frame over 841 px,
     * then a bézier fitted by least squares. it matches within a pixel the whole way, so it is not
     * an approximation, it is the curve
     *
     *     duration   250 ms
     *     curve      (0.05, 0.35, 0.35, 1.0)
     *     at 34 ms   41 % of the way
     *
     * what we had instead: a spring (0.92 / 800) settling in about 154 ms, and above all
     * [enterCurve], at 69 % after those same 34 ms. twice too fast at the start, which is what made
     * it snap where the reference glides — a fault you see in the beginning of the movement, not in
     * a duration
     */
    val pageSlideMs: Int = 250,
    val pageSlideCurve: Curve = Curve(0.05f, 0.35f, 0.35f, 1f),

    /** a global switch, to check a performance hypothesis without touching the code */
    val enabled: Boolean = true,
)

/**
 * a cubic bézier curve, by its two control points
 *
 * four numbers rather than an enum name: a name would force the tokens module to know a catalogue
 * of curves, and above all would stop one being tuned from the theme file, which is explicitly
 * asked for everything else
 */
@Serializable
data class Curve(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

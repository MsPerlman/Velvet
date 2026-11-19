package li.velvetchat.core.model



/**
 * a unicode emoji the app offers, with the name you can type to get it
 *
 * @param caractere the character itself, with no tone modifier
 * @param raccourci the name between the colons, without the colons. these are discord and slack
 *   names (`joy`, `thumbsup`, `tada`) rather than translations, because they get typed by
 *   fingers that already know them from elsewhere, and inventing our own would mean learning
 *   them twice
 * @param motsCles what you type into the picker's search, in french and in english. different
 *   from the shortcode: you search "rire" and you type `:joy:`
 * @param teintable does the emoji accept a skin tone modifier. see [TeintePeau]
 */
data class EmojiUnicode(
    val caractere: String,
    val raccourci: String,
    val motsCles: List<String>,
    val teintable: Boolean = false,
    /** the index into [CATEGORIES_EMOJI], meaning which section of the picker */
    val categorie: Int = 0,
)

/**
 * unicode's six skin tones
 *
 * `DEFAUT` is not a tone, it is the absence of a modifier: the conventional yellow, which
 * describes nobody. that is why it comes first and why its modifier is empty rather than equal
 * to one of the five
 *
 * the other five are the fitzpatrick scale as the standard defines it. we choose neither their
 * number nor their colours
 */
enum class TeintePeau(val modificateur: String) {
    DEFAUT(""),
    CLAIRE("🏻"),
    MOYENNE_CLAIRE("🏼"),
    MOYENNE("🏽"),
    MOYENNE_FONCEE("🏾"),
    FONCEE("🏿"),
}

/** what to draw: the emoji, followed by the modifier when it takes one */
fun EmojiUnicode.avec(teinte: TeintePeau): String =
    if (teintable && teinte != TeintePeau.DEFAUT) caractere + teinte.modificateur else caractere

/** how a skin tone previews in the picker: a hand, in that tone */
fun TeintePeau.apercu(): String = "✋" + modificateur

/**
 * does the shortcode [nom] name an emoji in the catalogue
 *
 * used by the composer, which colours `:tada:` as soon as it knows what to do with it. without
 * that feedback you type a shortcode and only find out it was wrong after sending, when the
 * message shows `:tadaa:` instead of the emoji. a predicate rather than the table, because
 * nothing else needs to read the catalogue by key and exposing it whole would invite walking it
 * by hand
 */
fun estRaccourciConnu(nom: String): Boolean = nom in PAR_RACCOURCI

/** the `:name:` syntax, shared by catalogue emoji and server emoji alike */
val RACCOURCI = Regex(""":([A-Za-z0-9_+-]{1,64}):""")

fun remplacerRaccourcisUnicode(
    texte: String,
    exclus: Set<String> = emptySet(),
    teinte: TeintePeau = TeintePeau.DEFAUT,
): String {
    if (':' !in texte) return texte
    return RACCOURCI.replace(texte) { m ->
        val nom = m.groupValues[1]
        if (nom in exclus) return@replace m.value
        PAR_RACCOURCI[nom]?.avec(teinte) ?: m.value
    }
}

/**
 * how many emoji, if the message is *only* emoji, otherwise 0
 *
 * this is discord's jumbo condition: a lone `👍` is an answer rather than a sentence, and at text
 * size it gets lost in the column when it is everything the message says
 *
 * the count comes back rather than a boolean because the caller needs both: whether to
 * enlarge, and whether the number stays under the ceiling past which enlarging hurts
 *
 * what it accepts without counting: spaces (a message of "👍 👍" is one), `FE0F` variation
 * selectors, `ZWJ` joiners, tone modifiers and the keycap combiner. a `ZWJ` stops the next
 * codepoint being counted, so `👨‍👩‍👧` is one family and not three people
 *
 * what it refuses: everything else, `©` and `®` and `™` included. they are technically emoji and
 * they turn up in ordinary text, and including them would render "© 2026" at forty points
 *
 * @param marque the character standing in for a custom emoji, counted as one emoji
 */
fun emojisSeuls(texte: String, marque: Char = '￼'): Int {
    var compte = 0
    var i = 0
    var apresLiant = false
    while (i < texte.length) {
        val c = texte[i]
        val cp: Int
        val pas: Int
        if (c.isHighSurrogate() && i + 1 < texte.length && texte[i + 1].isLowSurrogate()) {
            cp = 0x10000 + ((c.code - 0xD800) shl 10) + (texte[i + 1].code - 0xDC00)
            pas = 2
        } else {
            cp = c.code
            pas = 1
        }
        i += pas

        when {
            c == marque -> { compte++; apresLiant = false }
            c.isWhitespace() -> apresLiant = false
            cp == 0x200D -> apresLiant = true
            cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3 -> Unit
            cp in 0x1F3FB..0x1F3FF -> Unit
            estBaseEmoji(cp) -> {
                if (!apresLiant) compte++
                apresLiant = false
            }

            else -> return 0
        }
    }
    return compte
}

/**
 * the ranges we count as emoji
 *
 * deliberately coarse and deliberately closed at the bottom. they cover this file's whole
 * catalogue and the vast majority of what a keyboard produces, without reaching down into the
 * punctuation and symbol blocks where the arrows and `©` and `™` live. one range too many makes
 * a message that is not an emoji enormous, one range too few leaves it at normal size, which is
 * never a visible fault
 */
/**
 * the emoji spans inside [texte], as `String` indices
 *
 * used to *remove* a style rather than apply one. a message in italics was slanting its emoji
 * too, and twemoji has no italic face, so compose synthesises the slant by shearing the image. a
 * leaning 😭, which exists nowhere and is spotted instantly
 *
 * the joining characters are inside the span: variation selector (`FE0F`), zero width joiner
 * (`200D`), skin tones (`1F3FB` to `1F3FF`) and regional indicators. cutting those in half would
 * break composed sequences, a flag or a family, into pieces that then draw separately
 */
fun porteesEmojis(texte: String): List<IntRange> {
    val portees = mutableListOf<IntRange>()
    var i = 0
    var debut = -1
    while (i < texte.length) {
        val cp = texte.codePointAt(i)
        val taille = Character.charCount(cp)
        val dedans = estBaseEmoji(cp) || cp == 0xFE0F || cp == 0x200D ||
            cp in 0x1F3FB..0x1F3FF || cp in 0x1F1E6..0x1F1FF
        if (dedans) {
            if (debut < 0) debut = i
        } else if (debut >= 0) {
            portees += debut..(i - 1)
            debut = -1
        }
        i += taille
    }
    if (debut >= 0) portees += debut..(texte.length - 1)
    return portees
}

private fun estBaseEmoji(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF

/**
 * the shortcode index
 *
 * `by lazy` and not a direct initialisation. [CATALOGUE_UNICODE] is declared further down the
 * file, where it is because it runs to sixty lines and the rules read better before the data.
 * top level `val`s initialise in source order, so a direct initialisation would read a list that
 * is still empty. the compiler refuses it, which beats a silently empty index
 */
private val PAR_RACCOURCI: Map<String, EmojiUnicode> by lazy {
    CATALOGUE_UNICODE.associateBy { it.raccourci }
}

/** the emoji with this base character, or null. used to read back a saved preference */
fun emojiUnicode(caractere: String): EmojiUnicode? = PAR_CARACTERE[caractere]

private val PAR_CARACTERE: Map<String, EmojiUnicode> by lazy {
    CATALOGUE_UNICODE.associateBy { it.caractere }
}

/**
 * a hand picked selection, with familiar keywords, layered on top of the full catalogue
 *
 * only six are marked tone capable, and not the ones you would guess: faces (😀 😂 🤗) take no
 * modifier at all. they are yellow by convention and staying that way. only hands and bodies
 * accept one, and marking a face tone capable would draw the character followed by an orphan
 * coloured square
 */
private val EMOJIS_CHOISIS: List<EmojiUnicode> = listOf(
    EmojiUnicode("😀", "grinning", listOf("sourire", "content", "smile", "grin")),
    EmojiUnicode("😂", "joy", listOf("rire", "pleure", "mdr", "joy", "laugh")),
    // it was missing. the reference client offers `:rofl:` to anyone typing `:joy`
    EmojiUnicode("🤣", "rofl", listOf("rire", "mdr", "joy", "laugh", "rofl")),
    EmojiUnicode("🥲", "smiling_face_with_tear", listOf("sourire", "larme", "tear")),
    EmojiUnicode("😊", "blush", listOf("content", "timide", "blush")),
    EmojiUnicode("😍", "heart_eyes", listOf("amour", "coeur", "yeux", "love")),
    EmojiUnicode("😘", "kissing_heart", listOf("bisou", "kiss")),
    EmojiUnicode("🤔", "thinking", listOf("penser", "reflechir", "think")),
    EmojiUnicode("😐", "neutral_face", listOf("neutre", "blase", "neutral")),
    EmojiUnicode("🙃", "upside_down", listOf("envers", "ironie", "upside")),
    EmojiUnicode("😴", "sleeping", listOf("dormir", "fatigue", "sleep")),
    EmojiUnicode("😭", "sob", listOf("pleurer", "triste", "cry", "sob")),
    EmojiUnicode("😱", "scream", listOf("peur", "choc", "scream")),
    EmojiUnicode("😡", "rage", listOf("colere", "fache", "angry")),
    EmojiUnicode("🥳", "partying_face", listOf("fete", "anniversaire", "party")),
    EmojiUnicode("😎", "sunglasses", listOf("cool", "lunettes", "sunglasses")),
    EmojiUnicode("🤗", "hugging", listOf("calin", "hug")),
    EmojiUnicode("🤝", "handshake", listOf("accord", "main", "handshake")),
    EmojiUnicode("👍", "thumbsup", listOf("pouce", "oui", "ok", "thumbsup"), teintable = true),
    EmojiUnicode("👎", "thumbsdown", listOf("pouce", "non", "thumbsdown"), teintable = true),
    EmojiUnicode("👏", "clap", listOf("bravo", "applaudir", "clap"), teintable = true),
    EmojiUnicode("🙏", "pray", listOf("merci", "prier", "pray"), teintable = true),
    EmojiUnicode("💪", "muscle", listOf("force", "muscle"), teintable = true),
    EmojiUnicode("👀", "eyes", listOf("yeux", "regarder", "eyes")),
    EmojiUnicode("❤️", "heart", listOf("coeur", "amour", "heart")),
    EmojiUnicode("💔", "broken_heart", listOf("coeur", "brise", "broken")),
    EmojiUnicode("🔥", "fire", listOf("feu", "chaud", "fire")),
    EmojiUnicode("✨", "sparkles", listOf("etoiles", "brillant", "sparkles")),
    EmojiUnicode("🎉", "tada", listOf("fete", "bravo", "tada")),
    EmojiUnicode("🎂", "birthday", listOf("gateau", "anniversaire", "cake")),
    EmojiUnicode("🎁", "gift", listOf("cadeau", "gift")),
    EmojiUnicode("✅", "white_check_mark", listOf("valide", "fait", "check")),
    EmojiUnicode("❌", "x", listOf("non", "erreur", "cross")),
    EmojiUnicode("⚠️", "warning", listOf("attention", "warning")),
    EmojiUnicode("❓", "question", listOf("question", "quoi")),
    EmojiUnicode("💡", "bulb", listOf("idee", "ampoule", "idea")),
    EmojiUnicode("📌", "pushpin", listOf("epingle", "pin")),
    EmojiUnicode("🚀", "rocket", listOf("fusee", "vite", "rocket")),
    EmojiUnicode("⭐", "star", listOf("etoile", "favori", "star")),
    EmojiUnicode("💯", "100", listOf("cent", "parfait", "hundred")),
    EmojiUnicode("🧠", "brain", listOf("cerveau", "brain")),
    EmojiUnicode("🎮", "video_game", listOf("jeu", "manette", "game")),
    EmojiUnicode("🎵", "musical_note", listOf("musique", "note", "music")),
    EmojiUnicode("📷", "camera", listOf("photo", "appareil", "camera")),
    EmojiUnicode("🍕", "pizza", listOf("pizza", "manger")),
    EmojiUnicode("☕", "coffee", listOf("cafe", "coffee")),
    EmojiUnicode("🍺", "beer", listOf("biere", "beer")),
    EmojiUnicode("🐧", "penguin", listOf("pingouin", "penguin", "linux")),
    EmojiUnicode("🐱", "cat", listOf("chat", "cat")),
    EmojiUnicode("🐶", "dog", listOf("chien", "dog")),
    EmojiUnicode("🌙", "crescent_moon", listOf("lune", "nuit", "moon")),
    EmojiUnicode("☀️", "sunny", listOf("soleil", "sun")),
    EmojiUnicode("🌧️", "rain_cloud", listOf("pluie", "rain")),
    EmojiUnicode("❄️", "snowflake", listOf("neige", "froid", "snow")),
    EmojiUnicode("🌍", "earth_africa", listOf("terre", "monde", "earth")),
    EmojiUnicode("🚗", "car", listOf("voiture", "car")),
    EmojiUnicode("✈️", "airplane", listOf("avion", "voyage", "plane")),
    EmojiUnicode("🏠", "house", listOf("maison", "home")),
    EmojiUnicode("💻", "computer", listOf("ordinateur", "laptop")),
    EmojiUnicode("📱", "iphone", listOf("telephone", "phone")),
    EmojiUnicode("🔒", "lock", listOf("cadenas", "securite", "lock")),
)

/** the picker's sections, in discord's order */
val CATEGORIES_EMOJI: List<String>
    get() = if (li.velvetchat.core.LangueMoteur.anglais)
        listOf("People", "Nature", "Food", "Activities", "Travel", "Objects", "Symbols", "Flags")
    else listOf("Personnes", "Nature", "Nourriture", "Activités", "Voyages", "Objets", "Symboles", "Drapeaux")

/**
 * the full catalogue, everything the bundled twemoji font can draw, which is unicode up to 14
 *
 * the hand picked list above stopped being enough once the desktop picker was only showing
 * sixty two of them. the data comes from `DonneesEmojis.kt`, and the hand picked list layers its
 * familiar keywords on top ("mdr") and keeps its own shortcodes
 */
val CATALOGUE_UNICODE: List<EmojiUnicode> by lazy {
    // compared without the variation selector (u+fe0f). the hand picked list writes "❤️", the
    // data writes "❤". without this the two ended up side by side with the same shortcode, which
    // the duplicate test caught
    fun String.nu() = replace("️", "")
    val choisis = EMOJIS_CHOISIS.associateBy { it.caractere.nu() }
    val vus = HashSet<String>()
    val complet = DONNEES_EMOJIS.asSequence().flatMap { it.lineSequence() }.mapNotNull { ligne ->
        val p = ligne.split('|')
        if (p.size < 5) return@mapNotNull null
        val choisi = choisis[p[0].nu()]
        vus += p[0].nu()
        EmojiUnicode(
            caractere = p[0],
            raccourci = choisi?.raccourci ?: p[1],
            motsCles = ((choisi?.motsCles ?: emptyList()) + p[4].split(',').filter { it.isNotBlank() }).distinct(),
            teintable = choisi?.teintable ?: (p[3] == "1"),
            categorie = p[2].toIntOrNull() ?: 0,
        )
    }.toList()
    // whatever is left of the hand picked list, but only when neither the character nor the
    // shortcode is already in there
    val raccourcisPris = complet.mapTo(HashSet()) { it.raccourci }
    complet + EMOJIS_CHOISIS.filter { it.caractere.nu() !in vus && it.raccourci !in raccourcisPris }
}

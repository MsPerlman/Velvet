package li.velvetchat.app.ui

/**
 * the parser for matrix's `formatted_body`, written here rather than borrowed from the platform
 *
 * this was one of the seams in the desktop plan, and the only one of the seven we removed instead
 * of crossing. putting an `expect`/`actual` on "render some html" would give two different parsers
 * producing two different renderings of the same message, a gap you only see by comparing two
 * screens side by side, and never by looking at your own
 *
 * what made the trade possible
 *
 * the original file said the html matrix uses is restricted but not trivial, and that a hand
 * written parser would be a source of bugs on content from other clients. the argument was good,
 * and what made it expire is not a change of heart: half the work had already been done against the
 * platform rather than with it
 *
 * three workarounds, all in the old code. `<code>` and `<pre>`, the only two code tags matrix's
 * markdown produces, were ignored by `Html.fromHtml`, which only knows `<tt>`, so a `TagHandler`
 * was needed to make them exist. spoilers arrived as `<span data-mx-spoiler>`, and `Html.fromHtml`
 * never passes attributes to its `TagHandler`, so the tag had to be renamed upstream. and `<li>`
 * bullets were drawn by android's text engine, so absent from the string, so compose did not show
 * them, and they had to be injected into the html
 *
 * in other words, the platform rendered badly exactly the tags this protocol uses. what it
 * genuinely brought, entities, nesting, tolerance of malformed html, fits in the hundred lines
 * below and tests on the jvm instead of needing a device
 *
 * what the parser does not do
 *
 * it validates nothing and never throws. malformed html produces text, possibly without its
 * formatting: that is the rule for this whole path, we degrade, we do not hide. an unknown tag is
 * walked through, its content shows and its name does not
 *
 * tables are not rendered as tables, their cells become text separated by spaces. that was already
 * the case, and a table readable in columns needs a component, not a style
 */

/** what formatting a span of text carries */
enum class Balise {
    GRAS,
    ITALIQUE,
    BARRE,
    SOULIGNE,
    /** `<code>` like `<pre>`: the difference is layout, not style */
    CHASSE_FIXE,
    CITATION,
    SPOILER,
    TITRE1,
    TITRE2,
    TITRE3,
    LIEN,
    /** [Marque.valeur] carries the colour exactly as the message wrote it (`#rrggbb`) */
    COULEUR_TEXTE,
    COULEUR_FOND,
}

/**
 * one piece of formatting over a range of the rendered text
 *
 * the ranges overlap freely, bold starting in the middle of a link for instance. that is
 * deliberate: we apply them one at a time and let compose combine the stacked styles, rather than
 * cutting the text into segments of uniform style. manual splitting is the classic trap of this
 * conversion, it gives longer code that loses the overlaps
 */
data class Marque(
    val balise: Balise,
    val debut: Int,
    val fin: Int,
    val valeur: String? = null,
)

/** the rendered text, and what applies to it */
data class Analyse(val texte: String, val marques: List<Marque>)

/** the tags that separate blocks. they are worth a newline */
private val BLOCS = setOf(
    "p", "div", "blockquote", "pre", "ul", "ol", "li", "table", "tr", "caption",
    "h1", "h2", "h3", "h4", "h5", "h6", "details", "summary", "hr",
)

/**
 * parses a `formatted_body`
 *
 * @param html the protocol's restricted html, with emoji already pulled out, see `extraireEmojis`,
 *   which replaces them with [MARQUE_EMOJI] before they get here
 */
fun analyserHtml(html: String): Analyse {
    val texte = StringBuilder()
    val marques = mutableListOf<Marque>()
    // the stack of open tags, each remembering where it started in the rendered text. that is what
    // lets `<b>bold <i>and leaning</i></b>` produce two correct ranges without either needing to
    // know about the other
    val pile = ArrayDeque<Triple<String, Int, String?>>()
    var i = 0
    // inside a `<pre>` the spaces count. everywhere else html collapses them and so must we: the
    // protocol puts a newline between `</p>` and `<p>` for the readability of its own document, not
    // so it turns up in the message
    var dansPre = 0

    /** a newline, unless there is one already. an empty block must not punch a hole in the text */
    fun retourLigne() {
        if (texte.isNotEmpty() && texte.last() != '\n') texte.append('\n')
    }

    while (i < html.length) {
        val c = html[i]
        if (c != '<') {
            i = ajouterTexte(html, i, texte, dansPre > 0)
            continue
        }

        val fermeture = html.startsWith("</", i)
        val finBalise = html.indexOf('>', i)
        if (finBalise < 0) {
            // a `<` with no `>` is not a tag, it is an angle bracket somebody typed
            texte.append(html.substring(i))
            break
        }

        val interieur = html.substring(i + if (fermeture) 2 else 1, finBalise).trim()
        val nom = interieur.takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
        i = finBalise + 1

        if (nom == "br") {
            texte.append('\n')
            continue
        }
        // the emoji left before the parse, so what remains as an `<img>` is an external image we do
        // not load. see `extraireEmojis` for why
        if (nom == "img") continue

        if (fermeture) {
            // we unwind to the named tag and not just the top of the stack. badly closed html,
            // `<b><i>text</b>`, genuinely arrives, and giving up at the first disagreement would
            // lose the formatting of everything after it
            val rang = pile.indexOfLast { it.first == nom }
            if (rang >= 0) {
                while (pile.size > rang) {
                    val (ouverte, debut, valeur) = pile.removeLast()
                    if (ouverte == "pre") dansPre--
                    // the end is read before the block's newline, not after. the other way round
                    // overran a `<pre>` or an `<h1>` span by one character, the very one the final
                    // trim removes, hence an out of bounds range
                    val fin = texte.length
                    if (ouverte in BLOCS) retourLigne()
                    baliseDe(ouverte, valeur)?.let { b ->
                        if (fin > debut) marques += Marque(b, debut, fin, valeur)
                    }
                }
            }
            continue
        }

        // a self closing tag (`<hr/>`): nothing to push, it has no content
        if (interieur.endsWith("/")) {
            if (nom in BLOCS) retourLigne()
            continue
        }

        if (nom in BLOCS) retourLigne()
        if (nom == "pre") dansPre++
        // the bullet lives in the text. on android it was drawn by the text engine and did not
        // exist in the string, so compose did not show it, and a list became a run of lines with no
        // visible connection
        if (nom == "li") texte.append("• ")
        pile.addLast(Triple(nom, texte.length, attributUtile(nom, interieur)))
    }

    // whatever is still open closes at the end of the text. a truncated message keeps its
    // formatting as far as it went, instead of losing it entirely
    while (pile.isNotEmpty()) {
        val (ouverte, debut, valeur) = pile.removeLast()
        baliseDe(ouverte, valeur)?.let { b ->
            if (texte.length > debut) marques += Marque(b, debut, texte.length, valeur)
        }
    }

    return Analyse(texte.toString().trim('\n', ' '), marques)
}

/**
 * the attribute we keep from a tag, when there is one
 *
 * three only: a link's `href`, the colour, and the presence of `data-mx-spoiler`. the rest of
 * matrix's html is decorative or ignored, and keeping attributes we do not use would make the
 * parser promise something it does not deliver
 */
private fun attributUtile(nom: String, interieur: String): String? = when (nom) {
    "a" -> valeurAttribut(interieur, "href")
    "span", "font" -> when {
        interieur.contains("data-mx-spoiler", ignoreCase = true) -> SPOILER
        else -> valeurAttribut(interieur, "data-mx-color")
            ?: valeurAttribut(interieur, "color")
            ?: valeurAttribut(interieur, "data-mx-bg-color")?.let { "$FOND$it" }
    }
    else -> null
}

/**
 * internal marker meaning "this `<span>` is a spoiler", put where an attribute value would go
 */
private const val SPOILER = " spoiler"

/** internal prefix: the colour kept is a background, not a text colour */
private const val FOND = " fond"

private fun valeurAttribut(interieur: String, nom: String): String? {
    val motif = Regex("""\b$nom\s*=\s*["']?([^"'\s>]+)""", RegexOption.IGNORE_CASE)
    return motif.find(interieur)?.groupValues?.get(1)
}

/**
 * from the protocol's tag to what we make of it
 *
 * `h4` to `h6` fall back on the third level rather than inventing a fourth: the house style
 * describes three, and a message from another client using `<h5>` has to stay readable, since
 * smaller than an `<h3>` would be smaller than body text
 */
private fun baliseDe(nom: String, valeur: String?): Balise? = when (nom) {
    "b", "strong" -> Balise.GRAS
    "i", "em" -> Balise.ITALIQUE
    "s", "del", "strike" -> Balise.BARRE
    "u", "ins" -> Balise.SOULIGNE
    "code", "pre", "tt" -> Balise.CHASSE_FIXE
    "blockquote" -> Balise.CITATION
    "h1" -> Balise.TITRE1
    "h2" -> Balise.TITRE2
    "h3", "h4", "h5", "h6" -> Balise.TITRE3
    "a" -> if (valeur != null) Balise.LIEN else null
    "span", "font" -> when {
        valeur == SPOILER -> Balise.SPOILER
        valeur?.startsWith(FOND) == true -> Balise.COULEUR_FOND
        valeur != null -> Balise.COULEUR_TEXTE
        else -> null
    }
    else -> null
}

/** a mark's colour, with the internal background prefix stripped */
internal fun Marque.couleur(): String? = valeur?.removePrefix(FOND)

/**
 * appends the text up to the next `<`, entities decoded
 *
 * @return the position of the `<` found, or the end
 */
private fun ajouterTexte(
    html: String,
    depuis: Int,
    sortie: StringBuilder,
    preserverEspaces: Boolean,
): Int {
    var i = depuis
    while (i < html.length && html[i] != '<') {
        val c = html[i]
        if (!preserverEspaces && c.estEspace()) {
            // a run of spaces is worth one space, which is html's own rule, and it is what stops a
            // removed `<img>` leaving a two space hole, and a newline written between two `<p>`
            // doubling the block's line break
            while (i < html.length && html[i].estEspace()) i++
            if (sortie.isNotEmpty() && sortie.last() != '\n' && sortie.last() != ' ') {
                sortie.append(' ')
            }
            continue
        }
        if (c == '&') {
            val pointVirgule = html.indexOf(';', i)
            // an entity is ten characters at most. looking further would swallow a lone `&` and all
            // the text after it up to the next semicolon
            if (pointVirgule in (i + 1)..(i + 12)) {
                val entite = html.substring(i + 1, pointVirgule)
                val decode = decoderEntite(entite)
                if (decode != null) {
                    sortie.append(decode)
                    i = pointVirgule + 1
                    continue
                }
            }
        }
        sortie.append(c)
        i++
    }
    return i
}

private fun decoderEntite(entite: String): String? = when {
    entite.startsWith("#x", ignoreCase = true) ->
        entite.drop(2).toIntOrNull(16)?.let { pointDeCode(it) }

    entite.startsWith("#") -> entite.drop(1).toIntOrNull()?.let { pointDeCode(it) }

    else -> when (entite.lowercase()) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos", "#39" -> "'"
        // a non breaking space becomes an ordinary one. it exists for a browser's layout, and here
        // it is compose deciding where to break lines. keeping it prevents a line break nothing
        // justifies
        "nbsp" -> " "
        else -> null
    }
}

private fun pointDeCode(valeur: Int): String? =
    if (valeur in 1..0x10FFFF) {
        // `appendCodePoint` does not exist in common code, so the surrogate pair gets computed
        if (valeur <= 0xFFFF) valeur.toChar().toString()
        else {
            val v = valeur - 0x10000
            charArrayOf(
                (0xD800 + (v shr 10)).toChar(),
                (0xDC00 + (v and 0x3FF)).toChar(),
            ).concatToString()
        }
    } else null

/** what html counts as whitespace, and collapses outside a `<pre>` */
private fun Char.estEspace(): Boolean =
    this == ' ' || this == '\t' || this == '\n' || this == '\r'

/**
 * addresses written in the clear inside some text
 *
 * replaces `Linkify.addLinks`, and it did need replacing: matrix does not guarantee bare urls
 * arrive wrapped in an `<a>`, most clients recognise them at display time. without this pass,
 * typing an address without formatting it gives grey text indistinguishable from the rest
 *
 * trailing punctuation is given back to the text. "look at https://example.org." holds an address
 * and a full stop, and gluing them together would give a dead link. closing brackets are kept when
 * an opening one precedes them, because encyclopedia urls contain them
 */
fun liensNus(texte: String): List<IntRange> {
    if (texte.isEmpty()) return emptyList()
    return MOTIF_URL.findAll(texte).mapNotNull { trouve ->
        var fin = trouve.range.last
        while (fin > trouve.range.first) {
            val c = texte[fin]
            val ferme = c == ')' &&
                texte.substring(trouve.range.first, fin).count { it == '(' } >
                texte.substring(trouve.range.first, fin).count { it == ')' }
            if (c in ".,;:!?'\"»" || (c == ')' && !ferme)) fin-- else break
        }
        if (fin > trouve.range.first) trouve.range.first..fin else null
    }.toList()
}

/**
 * what counts as an address written in the clear
 *
 * `https?://` required, which is stricter than `Linkify`, which also recognised a bare domain. the
 * reasoning: with no scheme there is no boundary between an address and a sentence that happens to
 * end in a domain name, and a false positive turns text into something clickable, which is the
 * wrong direction for the mistake to go
 */
private val MOTIF_URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

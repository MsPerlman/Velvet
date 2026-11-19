package li.velvetchat.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.porteesEmojis
import li.velvetchat.design.Theme

/**
 * a message's body, with its formatting
 *
 * matrix carries two versions of every message: the plain text, and a `formatted_body` in
 * restricted html. the second is the one holding the bold, the code, the quotes and the mentions
 *
 * the parsing is ours now, see `AnalyseHtml.kt`, which says why the seam was removed rather than
 * crossed. this file only translates [Marque] into compose styles. it knows nothing of `Spanned`,
 * `TagHandler` or `Linkify`, so it renders exactly the same thing on the phone and on the desktop,
 * by construction
 *
 * what deliberately does not get translated: inline images and tables. they fall back to plain text
 * rather than to missing text, since a message stays readable even when its formatting is not
 * understood. that is the rule for this whole file, we degrade, we never hide
 *
 * two syntaxes come from somewhere other than html. the `#`/`##`/`###` headings do arrive as
 * `<h1>`…`<h3>` and take the normal path, but `-#`, the aside borrowed from discord, exists in no
 * html at all: matrix does not know it and lets it through in the clear. it gets spotted on the
 * final text, after conversion
 */
@Composable
fun CorpsFormate(
    texteBrut: String,
    html: String?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTokens.current

    // quotes and code blocks are blocks, not text styles: the reference draws a 4 point bar beside
    // a quote and a framed box around code, neither of which a span can carry. so the html is cut
    // at those two, each piece rendered on its own, the text ones through the path below
    val blocs = remember(html) { html?.let { decouperBlocs(it) } }
    if (blocs != null && blocs.any { it.genre != GenreBloc.TEXTE }) {
        androidx.compose.foundation.layout.Column(
            modifier,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            blocs.forEach { bloc ->
                when (bloc.genre) {
                    GenreBloc.TEXTE -> CorpsFormate("", bloc.html)
                    GenreBloc.CITATION -> BlocCitation { CorpsFormate("", bloc.html) }
                    GenreBloc.CODE -> BlocCode(analyserHtml(bloc.html).texte.trim('\n'))
                }
            }
        }
        return
    }

    // who i am, to tell "somebody is being talked about" from "somebody is talking to me". see the
    // mention colour rule in `versAnnotated`
    val moi = LocalMonIdentifiant.current

    // custom emoji are pulled out of the html before it gets parsed, and put back afterwards as
    // inline content. the image is at the end of a download, the parsing is synchronous. see
    // `extraireEmojis`
    val emojis = remember(html) { html?.let { extraireEmojis(it) } }

    // a spoiler stays hidden until somebody asks. one state for the whole message and not one per
    // tag: a message with two spoilers is rare, and the alternative would mean working out which
    // tag was touched from a position in the rendered text
    //
    // ponytail: reveal per message, to refine if anybody ever sends two separate spoilers
    val aSpoiler = remember(html) { html?.contains("data-mx-spoiler", ignoreCase = true) == true }
    var devoile by remember(html) { mutableStateOf(false) }

    // what happens when you tap a url
    //
    // three possible destinations, and only one of them leaves the app:
    //
    // - a mention (`matrix.to/#/@somebody`) opens that person's card, which it never used to do,
    //     although it is the one gesture a nickname invites
    // - an honest link goes to the browser - a link announcing one destination and aiming at
    // another goes through a confirmation showing
    //     the real address. see `Liens.kt`, which carries the rule and its limits
    //
    // the callback is built once (`remember` with no key) and reads its dependencies through
    // `rememberUpdatedState`. that is what makes it stable: it is the key to the `remember` of the
    // render just below, and a lambda recreated on every recomposition would rebuild the whole
    // formatted text every frame
    val ouvrirMembre = rememberUpdatedState(LocalOuvrirMembre.current)
    val navigateur = rememberUpdatedState(LocalUriHandler.current)
    val surLien: (String, String) -> Unit = remember {
        { brut, texteAffiche ->
            val url = completerScheme(brut.trim())
            when {
                url.startsWith(LIEN_MATRIX) -> {
                    val cible = url.removePrefix(LIEN_MATRIX)
                        .substringBefore('?').substringBefore('/')
                    // a person, and nothing else for now. a `matrix.to` pointing at a room or an
                    // event would mean resolving an alias then navigating, which is a different
                    // subject from making links tappable
                    if (cible.startsWith("@")) ouvrirMembre.value(cible)
                }

                !schemeSur(url) ->
                    LienEnAttente.demande = LienACliquer(url, texteAffiche, ouvrable = false)

                lienTrompeur(texteAffiche, url) ->
                    LienEnAttente.demande = LienACliquer(url, texteAffiche, ouvrable = true)

                // the browser may have nothing to answer with. we do not break the message over it,
                // the address stays readable and a long press copies it
                else -> runCatching { navigateur.value.openUri(url) }
            }
            Unit
        }
    }

    val police = LocalPoliceEmoji.current
    val rendu = remember(html, texteBrut, emojis, moi, devoile, surLien, police) {
        // a message with no `formatted_body` is not a message with no formatting
        //
        // found on screen: a message containing only `-# something` drew with its marker, while the
        // same aside in the middle of a message rendered fine. the cause is that the sdk only
        // produces a `formatted_body` when it recognised markdown, and `-#` is not markdown, so
        // there is nothing to format and the field stays empty. the case is anything but marginal,
        // an aside on its own is the commonest use of all
        if (html.isNullOrBlank()) versAnnotatedTexte(texteBrut, tokens, surLien, police)
        else versAnnotated(emojis?.first ?: html, tokens, moi, devoile, surLien, police)
    }

    // the jumbo rule: a message that is nothing but emoji draws them large
    //
    // measured on the rendered text and not on the raw one. that is the only place custom emoji
    // exist as a countable mark, and where the html tags are already gone. a `<p>` in the raw text
    // would make the detection fail on a message that contains one heart
    val echelle = remember(rendu) {
        val combien = li.velvetchat.core.model.emojisSeuls(rendu.text, MARQUE_EMOJI)
        // two conditions, and the second is not a theoretical precaution. pasting two hundred emoji
        // would fill the screen at 40 dp each
        if (combien in 1..tokens.typography.jumboEmojiMax) tokens.typography.jumboEmojiScale
        else 1f
    }

    // an emoji takes a line's height and a bit more: it has to stand out without prising the lines
    // apart, hence a height pinned to the body's line spacing
    //
    // but not always the body's. a unicode emoji is a glyph, so in a `#` heading or a `-#` aside it
    // follows the text size with nothing to do on our side. a custom one is an image sitting in a
    // fixed size `Placeholder`, so it would stay body sized in the middle of a heading twice as
    // large, and the reverse inside an aside. the reference scales them, and it is the only
    // consistent reading anyway, since the two families are typed the same way
    val cote = (tokens.typography.bodyLineHeight * echelle).sp
    val contenus = remember(emojis, rendu, echelle) {
        // where the marks sit in the final text. `appendInlineContent` left exactly one character
        // per emoji, in order
        val positions = rendu.text.indices.filter { rendu.text[it] == MARQUE_EMOJI }

        emojis?.second.orEmpty().mapIndexed { i, url ->
            val pos = positions.getOrNull(i)
            // the last style wins, like at render time: the ranges are applied in order and compose
            // resolves them the same way. taking the largest would grow an emoji inside an aside,
            // which is supposed to shrink
            val taillePolice = pos?.let { p ->
                rendu.spanStyles
                    .filter { p >= it.start && p < it.end && it.item.fontSize.isSpecified }
                    .lastOrNull()?.item?.fontSize
            }
            val cotePropre = taillePolice
                ?.let { (tokens.typography.bodyLineHeight * it.value / tokens.typography.bodySize).sp }
                ?: cote
            "emoji-$i" to InlineTextContent(
                Placeholder(cotePropre, cotePropre, PlaceholderVerticalAlign.TextCenter),
            ) { EmojiEnLigne(url) }
        }.toMap()
    }

    androidx.compose.material3.Text(
        text = rendu,
        color = tokens.textPrimary,
        // `echelle` is 1 everywhere except on an all emoji message. it is the text size going up,
        // so unicode emoji follow on their own, being glyphs. the custom ones follow through
        // `cote`, above
        fontSize = (tokens.typography.bodySize * echelle).sp,
        lineHeight = (tokens.typography.bodyLineHeight * echelle).sp,
        inlineContent = contenus,
        // the gesture is only attached to a message that really carries a spoiler. anywhere else,
        // one more touch consumer would get between the line and its own gestures, the long press
        // for actions and the swipe to reply
        modifier = if (aSpoiler && !devoile) {
            modifier.pointerInput(html) { detectTapGestures { devoile = true } }
        } else modifier,
    )
}

/**
 * turns an [Analyse] into an `AnnotatedString`
 *
 * the marks overlap freely, bold starting in the middle of a link. so we apply them one at a time
 * over their own range and let compose combine the stacked styles, rather than cutting the text
 * into segments of uniform style
 */
internal fun versAnnotated(
    html: String,
    tokens: Theme,
    moi: String?,
    spoilersDevoiles: Boolean = false,
    surLien: (String, String) -> Unit = { _, _ -> },
    /** twemoji, applied over the emoji spans. null leaves the theme's font */
    policeEmoji: androidx.compose.ui.text.font.FontFamily? = null,
): AnnotatedString {
    val analyse = analyserHtml(html)
    val texte = analyse.texte

    // `-#` is handled here, on the text, and not in the html
    //
    // it is a syntax borrowed from discord that matrix's markdown does not know. `-#` is not a
    // valid list start, that would need a space after the dash, so the line goes through the
    // conversion untouched and turns up here in the clear
    val apartes = repererApartes(texte)
    val marqueurs = apartes.map { it.marqueur }
    val texteFinal = retirer(texte, marqueurs)

    // addresses written in the clear, that the message did not wrap in an `<a>`. we skip the ones
    // already inside a link, otherwise an address whose `<a href>` differs from its label would get
    // two links, and the second, the one pointing at the displayed text, would win. that is exactly
    // the bug `Linkify` used to cause on android, avoided here in two lines rather than by lifting
    // and reposting spans
    val dejaLies = analyse.marques.filter { it.balise == Balise.LIEN }
    val nus = liensNus(texte)
        .filterNot { p -> dejaLies.any { p.first >= it.debut && p.first < it.fin } }

    return buildAnnotatedString {
        // the text is appended in chunks, inserting inline content at each emoji mark
        //
        // the thing that makes the trick work: `appendInlineContent` appends its own replacement
        // text, here a single character, the very one we are removing. so the total length is
        // rigorously unchanged, and every style range computed on the original text stays valid
        // with no recomputation
        var curseur = 0
        var rang = 0
        while (true) {
            val marque = texteFinal.indexOf(MARQUE_EMOJI, curseur)
            if (marque < 0) break
            append(texteFinal.substring(curseur, marque))
            appendInlineContent("emoji-$rang", MARQUE_EMOJI.toString())
            curseur = marque + 1
            rang++
        }
        append(texteFinal.substring(curseur))

        // the ranges are expressed against the text before the markers are removed, so each one has
        // to be projected. applying the styles first and cutting afterwards would have shifted all
        // formatting past the first `-#` by the marker's length
        apartes.forEach { aparte ->
            addStyle(
                SpanStyle(
                    fontSize = (tokens.typography.bodySize * tokens.typography.subtextScale).sp,
                    color = tokens.textMuted,
                ),
                projeter(aparte.portee.first, marqueurs),
                projeter(aparte.portee.last, marqueurs),
            )
        }

        analyse.marques.forEach { marque ->
            val debut = projeter(marque.debut.coerceIn(0, texte.length), marqueurs)
            val fin = projeter(marque.fin.coerceIn(0, texte.length), marqueurs)
            if (debut >= fin) return@forEach

            styleDe(marque, tokens, moi, spoilersDevoiles)?.let { addStyle(it, debut, fin) }

            // the link itself, and not just its colour
            //
            // `LinkAnnotation` rather than a `pointerInput` over the whole `Text`: compose does the
            // hit testing, so a tap next to an address does not open it, and the link becomes a
            // target for screen readers into the bargain. a hand written offset calculation would
            // have got the first and not the second
            if (marque.balise == Balise.LIEN && fin <= texteFinal.length) {
                val affiche = texteFinal.substring(debut, fin)
                val url = marque.valeur.orEmpty()
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "lien",
                        styles = STYLE_LIEN_NEUTRE,
                        linkInteractionListener = { surLien(url, affiche) },
                    ),
                    debut,
                    fin,
                )
            }
        }

        // bare addresses, coloured and made tappable like the rest. here the displayed text is the
        // address, there is no label that could lie, so `Liens.kt`'s confirmation will never fire
        // on this path
        nus.forEach { portee ->
            val debut = projeter(portee.first, marqueurs)
            val fin = projeter(portee.last + 1, marqueurs)
            if (debut >= fin || fin > texteFinal.length) return@forEach
            addStyle(
                SpanStyle(color = tokens.accent, textDecoration = TextDecoration.Underline),
                debut,
                fin,
            )
            val url = texteFinal.substring(debut, fin)
            addLink(
                LinkAnnotation.Clickable(
                    tag = "lien",
                    styles = STYLE_LIEN_NEUTRE,
                    linkInteractionListener = { surLien(url, url) },
                ),
                debut,
                fin,
            )
        }

        // an emoji does not lean. applied last, so it wins
        //
        // the italic used to apply to the whole span, emoji included. twemoji having no italic
        // face, compose synthesises the slant by shearing the image, which gives you a crooked 😭.
        // and this is also where twemoji gets applied: the sweep already existed to keep an emoji
        // upright, and it now carries the family too. three fallback attempts failed before this
        // one, see `LocalPoliceEmoji`, and the lesson fits in one sentence, glyph fallback is not
        // ours to control but the span is
        porteesEmojis(texteFinal).forEach { portee ->
            addStyle(
                SpanStyle(fontStyle = FontStyle.Normal, fontFamily = policeEmoji),
                portee.first,
                (portee.last + 1).coerceAtMost(texteFinal.length),
            )
        }
    }
}

/**
 * one mark's style
 *
 * pulled out of the loop for a reason that is not cosmetic: this is the mapping between the
 * protocol and the house style, and it reads better in one block than drowned in a range projection
 */
private fun styleDe(
    marque: Marque,
    tokens: Theme,
    moi: String?,
    spoilersDevoiles: Boolean,
): SpanStyle? = when (marque.balise) {
    Balise.GRAS -> SpanStyle(fontWeight = FontWeight.Bold)
    Balise.ITALIQUE -> SpanStyle(fontStyle = FontStyle.Italic)
    Balise.BARRE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    Balise.SOULIGNE -> SpanStyle(textDecoration = TextDecoration.Underline)

    // `<code>` and `<pre>` get the same treatment. the difference between inline code and a code
    // block is about layout and not about text style, and a block deserves its own component the
    // day we want a background and horizontal scrolling
    Balise.CHASSE_FIXE -> SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = tokens.typography.codeSize.sp,
        background = tokens.surfaceRaised,
    )

    // a quote's own look is its block, see [BlocCitation]: inside it the text stays as it is
    Balise.CITATION -> null

    // hidden means unreadable, not absent. the text keeps its place, so a spoiler's length stays
    // visible like on the reference, but it is painted transparent over a block. once revealed, the
    // block disappears entirely rather than staying as a highlight
    Balise.SPOILER -> if (spoilersDevoiles) null else SpanStyle(
        color = Color.Transparent,
        background = tokens.textMuted.copy(alpha = 0.32f),
    )

    // the three heading levels, and the house style decides the sizes, not a scale table inherited
    // from one platform that had to be sorted into thresholds
    Balise.TITRE1 -> titre(tokens, tokens.typography.heading1Scale)
    Balise.TITRE2 -> titre(tokens, tokens.typography.heading2Scale)
    Balise.TITRE3 -> titre(tokens, tokens.typography.heading3Scale)

    // a mention is not a link. they arrive in the same shape, since the protocol expresses mentions
    // as a `matrix.to` link, and rendering them alike would be a misreading: a link leads outside
    // and deserves a thought before you follow it, a mention names somebody in the conversation you
    // are already reading. hence the pill, with no underline, rather than the underlined blue of
    // links
    Balise.LIEN -> {
        val url = marque.valeur.orEmpty()
        if (url.startsWith(LIEN_MATRIX)) {
            // gold when it is me, blue when it is somebody else. the comparison is against the
            // link's target, since the id is in there and the display name, which can change, does
            // not come into it
            val pourMoi = moi != null &&
                url.removePrefix(LIEN_MATRIX).substringBefore('?').substringBefore('/') == moi
            if (pourMoi) {
                SpanStyle(
                    color = tokens.warning,
                    background = tokens.warning.copy(alpha = 0.16f),
                    fontWeight = FontWeight.Bold,
                )
            } else {
                SpanStyle(
                    color = tokens.accent,
                    background = tokens.accent.copy(alpha = 0.16f),
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            SpanStyle(color = tokens.accent, textDecoration = TextDecoration.Underline)
        }
    }

    // colours coming from the html are taken as they are, since the message's author asked for them
    // explicitly. an unreadable colour is ignored rather than replaced with a fallback tint. the
    // magenta in `toColor` is there to flag a badly written theme, not to repaint somebody's
    // message
    Balise.COULEUR_TEXTE -> couleurStricte(marque.couleur())?.let { SpanStyle(color = it) }
    Balise.COULEUR_FOND -> couleurStricte(marque.couleur())?.let { SpanStyle(background = it) }
}

private fun titre(tokens: Theme, echelle: Float) = SpanStyle(
    fontSize = (tokens.typography.bodySize * echelle).sp,
    fontWeight = FontWeight.Bold,
)

/** `#rrggbb` or `#rgb`, and nothing else. null when it does not read */
private fun couleurStricte(valeur: String?): Color? {
    val hex = valeur?.trim()?.removePrefix("#") ?: return null
    val complet = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("")
        6 -> hex
        else -> return null
    }
    val n = complet.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or n)
}

/** an aside line: where its `-# ` marker is, and how far its text runs */
private data class Aparte(val marqueur: IntRange, val portee: IntRange)

/**
 * finds the lines starting with `-# `
 *
 * per line and not per block. `-#` applies to one line and stops at the newline, exactly like on
 * the reference. making it cover a whole paragraph would shrink text the author never marked
 */
/** compiled once, since [repererApartes] runs over every message displayed */
private val MOTIF_APARTE = Regex("""^[ \t]*-#[ \t]+""")

private fun repererApartes(texte: String): List<Aparte> {
    val motif = MOTIF_APARTE
    val trouves = mutableListOf<Aparte>()
    var debut = 0
    while (debut <= texte.length) {
        val saut = texte.indexOf('\n', debut)
        val fin = if (saut < 0) texte.length else saut
        motif.find(texte.substring(debut, fin))?.let { m ->
            trouves += Aparte(
                marqueur = (debut + m.range.first)..(debut + m.range.last),
                portee = debut..fin,
            )
        }
        if (saut < 0) break
        debut = saut + 1
    }
    return trouves
}

/** removes ranges from a string, back to front so the others stay valid */
private fun retirer(texte: String, intervalles: List<IntRange>): String {
    var resultat = texte
    intervalles.sortedByDescending { it.first }.forEach { r ->
        resultat = resultat.removeRange(r.first, (r.last + 1).coerceAtMost(resultat.length))
    }
    return resultat
}

/**
 * projects a position in the original text onto the text with its markers removed
 *
 * the ranges arrive sorted and disjoint, being line initial markers, so they cannot overlap. a
 * position landing inside a marker falls back to its start, which is what you want for a style
 * beginning exactly on a removed marker
 */
private fun projeter(position: Int, retraits: List<IntRange>): Int {
    var recul = 0
    for (r in retraits) {
        when {
            r.last < position -> recul += r.last - r.first + 1
            r.first < position -> return r.first - recul
            else -> return position - recul
        }
    }
    return position - recul
}

/**
 * the path for messages with no `formatted_body`
 *
 * a message with no html is not a message with no formatting, and it is a bug that could only show
 * on screen: a message containing only `-# something` drew with its marker, while the same aside in
 * the middle of a longer message rendered fine. the sdk only produces a `formatted_body` when it
 * recognised markdown, and `-#` is not, so there is nothing to format and the field stays empty
 */
private fun versAnnotatedTexte(
    texte: String,
    tokens: Theme,
    surLien: (String, String) -> Unit = { _, _ -> },
    policeEmoji: androidx.compose.ui.text.font.FontFamily? = null,
): AnnotatedString {
    val apartes = repererApartes(texte)
    val marqueurs = apartes.map { it.marqueur }
    val final = if (apartes.isEmpty()) texte else retirer(texte, marqueurs)

    // links get looked for here too, and that is the second half of the same bug. this path is
    // exactly the one a message that is only an address takes: nothing to format, so no
    // `formatted_body`, so no html, so no coloured link
    val liens = liensNus(final)

    val emojis = porteesEmojis(final)
    if (apartes.isEmpty() && liens.isEmpty() && emojis.isEmpty()) return AnnotatedString(texte)

    return buildAnnotatedString {
        append(final)
        // a message with no `formatted_body` has emoji like any other. it is even the commonest
        // case, since a message that is only an emoji has nothing to format
        emojis.forEach { portee ->
            addStyle(
                SpanStyle(fontFamily = policeEmoji),
                portee.first,
                (portee.last + 1).coerceAtMost(final.length),
            )
        }
        apartes.forEach { aparte ->
            addStyle(
                SpanStyle(
                    fontSize = (tokens.typography.bodySize * tokens.typography.subtextScale).sp,
                    color = tokens.textMuted,
                ),
                projeter(aparte.portee.first, marqueurs),
                projeter(aparte.portee.last, marqueurs),
            )
        }
        // the ranges come from the text with its markers already removed, so there is no projection
        // to do, unlike the asides whose bounds are from before
        liens.forEach { portee ->
            val fin = (portee.last + 1).coerceAtMost(final.length)
            if (portee.first >= fin) return@forEach
            addStyle(
                SpanStyle(color = tokens.accent, textDecoration = TextDecoration.Underline),
                portee.first,
                fin,
            )
            val url = final.substring(portee.first, fin)
            addLink(
                LinkAnnotation.Clickable(
                    tag = "lien",
                    styles = STYLE_LIEN_NEUTRE,
                    linkInteractionListener = { surLien(url, url) },
                ),
                portee.first,
                fin,
            )
        }
    }
}

/**
 * the prefix the protocol uses for links naming a person or a room
 *
 * `matrix.to` is not a site you visit, it is an addressing convention. a client that recognises the
 * prefix resolves the target itself without ever contacting that domain, which is exactly why we
 * test for it here rather than letting the link go to the browser
 */
private const val LIEN_MATRIX = "https://matrix.to/#/"

/**
 * a link that brings no appearance of its own
 *
 * `LinkAnnotation` is used here to make the text clickable and not to decorate it: the colour, the
 * background and the underline come from the `addStyle` calls that tell a mention from an address.
 * left at null, compose applies its default decoration and the mention comes out underlined
 */
private val STYLE_LIEN_NEUTRE = TextLinkStyles(
    style = SpanStyle(),
    focusedStyle = SpanStyle(),
    hoveredStyle = SpanStyle(),
    pressedStyle = SpanStyle(),
)

internal enum class GenreBloc { TEXTE, CITATION, CODE }

internal data class BlocHtml(val genre: GenreBloc, val html: String)

private val BLOC_HTML = Regex(
    """<(blockquote|pre)\b[^>]*>(.*?)</\1\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

/**
 * the html cut at its top level quotes and code blocks, in order. blank text between two blocks is
 * dropped: it is only the newline that separated them
 *
 * ponytail: a quote inside a quote closes at the inner end tag, and its remainder reads as text.
 * matrix clients nest quotes for replies only, which we draw from the relation, not from the html
 */
internal fun decouperBlocs(html: String): List<BlocHtml> {
    val blocs = mutableListOf<BlocHtml>()
    var depuis = 0
    fun texte(jusqua: Int) {
        val morceau = html.substring(depuis, jusqua)
        if (morceau.replace(Regex("""<br\s*/?>|<p>\s*</p>""", RegexOption.IGNORE_CASE), "").isNotBlank()) {
            blocs += BlocHtml(GenreBloc.TEXTE, morceau)
        }
    }
    BLOC_HTML.findAll(html).forEach { m ->
        texte(m.range.first)
        val genre = if (m.groupValues[1].equals("pre", ignoreCase = true)) GenreBloc.CODE else GenreBloc.CITATION
        blocs += BlocHtml(genre, m.groupValues[2])
        depuis = m.range.last + 1
    }
    texte(html.length)
    return blocs
}

/** the reference's quote: a 4 point bar, radius 4, then the text 12 further (`.blockquoteContainer`) */
@Composable
private fun BlocCitation(contenu: @Composable () -> Unit) {
    val tokens = LocalTokens.current
    androidx.compose.foundation.layout.Row(
        Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.width(4.dp).fillMaxHeight()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .background(tokens.textMuted.copy(alpha = 0.5f)),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Box(Modifier.padding(end = 8.dp)) { contenu() }
    }
}

/**
 * the reference's code block: its own box, raised fill, a 1 point border, radius 4, 8 inside, and
 * long lines that scroll sideways rather than wrap
 *
 * ponytail: no syntax colouring and no copy button yet
 */
@Composable
private fun BlocCode(code: String) {
    val tokens = LocalTokens.current
    val forme = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth()
            .clip(forme)
            .background(tokens.surfaceRaised)
            .border(1.dp, tokens.border, forme)
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(8.dp),
    ) {
        androidx.compose.material3.Text(
            code,
            color = tokens.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = tokens.typography.codeSize.sp,
            lineHeight = (tokens.typography.codeSize * 1.4f).sp,
            softWrap = false,
        )
    }
}

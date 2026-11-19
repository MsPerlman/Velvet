package li.velvetchat.core.model

/** compiled once rather than on every send, same as in `Emotes.kt` */
private val MOTIF_RACCOURCI = Regex(""":([A-Za-z0-9_+-]{1,64}):""")
private val MOTIF_TOUT_LE_SALON = Regex("""(^|\s)@room(\s|$)""")

/**
 * the name we call someone by: their display name, or the local part of their id
 *
 * public because the ui and the translation to the protocol have to use the same one. that's
 * the name inserted in the field and the name we look for when sending, and two diverging
 * definitions would break linking without saying a word
 */
fun Member.nomAffiche(): String =
    displayName?.takeIf { it.isNotBlank() } ?: userId.removePrefix("@").substringBefore(':')

/**
 * a message ready to go: plain body, html version if one is needed, and who it pings
 *
 * the three go together and are computed in one pass, because they all derive from the same
 * text escaped exactly once. splitting them would escape the html twice, or worse, every other
 * time
 */
data class MessageSortant(
    val corps: String,
    /** null when the text needs no formatting, and markdown stays live */
    val html: String?,
    val mentions: List<String>,
    val mentionneToutLeSalon: Boolean,
)

/**
 * turns typed text into a matrix message
 *
 * three rewrites over the same string: emoji shortcodes become `<img>`, `@someone` becomes a
 * `matrix.to` link, `#channel` too
 *
 * mentions are re-derived from the final text rather than remembered from the picker. that
 * matters: someone typing `@papa` from memory, without going through the list, gets pinged
 * exactly as if they'd been picked, which is what you'd expect and what remembering wouldn't
 * give you
 *
 * the price: two people with the same display name can't be told apart, and the first in the
 * list gets the mention. on a small server that's rare and harmless, and the alternative is
 * giving up hand typed mentions, which costs more than the bug it avoids
 *
 * the link text keeps the `@`. the protocol lets you put anything there, and keeping it reads
 * like discord, where a mention is visible inside the sentence
 */
fun composerMessage(
    texte: String,
    pack: List<CustomEmote>,
    membres: List<Member>,
    salons: List<Channel>,
    /** the chosen tone, applied to shortcodes that accept one */
    teinte: TeintePeau = TeintePeau.DEFAUT,
): MessageSortant {
    val parNom = pack.associateBy { it.shortcode }

    // unicode shortcodes first, and on the plain text. on the text and not the html because
    // `corps` is what clients that ignore `formatted_body` read, and substituting only in the
    // html would leave them a message that says ":joy:". first of all so everything after
    // works on the final text
    val clair = remplacerRaccourcisUnicode(texte, exclus = parNom.keys, teinte = teinte)

    var html = echapperHtml(clair)
    var modifie = false
    val mentionnes = mutableListOf<String>()
    html = MOTIF_RACCOURCI.replace(html) { m ->
        val emote = parNom[m.groupValues[1]] ?: return@replace m.value
        modifie = true
        // `data-mx-emoticon` is what tells an emoji apart from an ordinary image, and what lets
        // a client draw it at line height instead of as an attachment. the height goes in an
        // attribute because the protocol has no styling
        """<img data-mx-emoticon src="${emote.url}" alt="${m.value}" title="${m.value}" height="32" />"""
    }

    // longest names first. without that sort, `@jean` would eat the start of `@jean-michel` and
    // leave "-michel" dangling behind a link to the wrong person
    membres.sortedByDescending { it.nomAffiche().length }.forEach { membre ->
        val aiguille = "@" + echapperHtml(membre.nomAffiche())
        if (!html.contains(aiguille)) return@forEach
        // don't touch what's already inside a tag: a name appearing in the url of a link laid
        // down on a previous pass would get rewritten in the middle of its own href
        html = html.remplacerHorsLiens(aiguille) {
            modifie = true
            if (membre.userId !in mentionnes) mentionnes += membre.userId
            """<a href="https://matrix.to/#/${membre.userId}">$aiguille</a>"""
        }
    }

    salons.sortedByDescending { it.name.length }.forEach { salon ->
        val aiguille = "#" + echapperHtml(salon.name)
        if (!html.contains(aiguille)) return@forEach
        // `via`: a room id starts with `!` and doesn't say on its own where to find it. the
        // origin server is readable from the id itself, so nobody has to be asked
        val via = salon.id.substringAfter(':', "")
        val lien = "https://matrix.to/#/${salon.id}" + if (via.isNotEmpty()) "?via=$via" else ""
        html = html.remplacerHorsLiens(aiguille) {
            modifie = true
            """<a href="$lien">$aiguille</a>"""
        }
    }

    val toutLeSalon = MOTIF_TOUT_LE_SALON.containsMatchIn(clair)

    return MessageSortant(
        corps = clair,
        html = if (modifie) html else null,
        mentions = mentionnes,
        mentionneToutLeSalon = toutLeSalon,
    )
}

/**
 * replaces every occurrence of a needle, skipping links already laid down
 *
 * needed because the rewrites run one after another over the same string, and skipping tags
 * alone isn't enough. `@jean-michel` became a link, then `@jean`'s turn found its own name
 * inside the link text and rewrote it: a link inside a link, and jean got a notification meant
 * for someone else. `#general` became a link, then `#gen` helped itself the same way
 *
 * so it isn't tag contents that need protecting, it's the whole `<a>` element: once a fragment
 * of text has been attributed to someone or somewhere, it doesn't get reattributed
 */
private fun String.remplacerHorsLiens(aiguille: String, parQuoi: () -> String): String {
    val sortie = StringBuilder(length)
    var i = 0
    while (i < length) {
        when {
            startsWith("<a", i) -> {
                // the whole element is copied through, closing tag included
                val fin = indexOf("</a>", i).let { if (it < 0) length else it + 4 }
                sortie.append(substring(i, fin))
                i = fin
            }

            this[i] == '<' -> {
                val fin = indexOf('>', i).let { if (it < 0) length else it + 1 }
                sortie.append(substring(i, fin))
                i = fin
            }

            startsWith(aiguille, i) -> { sortie.append(parQuoi()); i += aiguille.length }
            else -> { sortie.append(this[i]); i++ }
        }
    }
    return sortie.toString()
}

/** escapes the bare minimum needed to drop user text into html */
fun echapperHtml(texte: String): String = texte
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

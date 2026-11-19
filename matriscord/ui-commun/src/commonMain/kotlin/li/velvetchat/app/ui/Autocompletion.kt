package li.velvetchat.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.CustomEmote
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.avec
import li.velvetchat.core.model.nomAffiche

/** the open room's members, for the `@` autocomplete */
val LocalMembres = staticCompositionLocalOf<List<Member>> { emptyList() }

/** the open server's channels, for the `#` autocomplete */
val LocalSalons = staticCompositionLocalOf<List<Channel>> { emptyList() }

/**
 * our own matrix id, to know whether a message is talking to us
 *
 * null until the profile is read, in which case nothing gets highlighted, which is the right
 * answer: better to flag nothing than to wrongly announce that somebody was addressed
 */
val LocalMonIdentifiant = staticCompositionLocalOf<String?> { null }

/**
 * opening somebody's card from their id alone
 *
 * supplied by `Shell`, the only place holding the floating card's state and the only one high
 * enough to draw it over everything. a `CompositionLocal` rather than a parameter: a mention is
 * rendered deep inside a message's text, four levels under the conversation, and passing the
 * callback down would make `MessageBodyView` and `CorpsFormate` carry a dependency they do not use
 * themselves
 *
 * the id is enough: the card fetches the rest, extended profile, status, note, tier, as soon as it
 * opens
 */
val LocalOuvrirMembre = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * what the autocomplete can offer
 *
 * three families, because the three triggers do not name the same kind of thing and do not insert
 * the same way: an emoji becomes an image, a person becomes a link that notifies them, a channel
 * becomes a link that leads there
 */
sealed interface Suggestion {
    data class Emoji(val emote: CustomEmote) : Suggestion
    data class Personne(val membre: Member) : Suggestion
    data class Salon(val salon: Channel) : Suggestion

    /**
     * a unicode emoji, named by its `:shortcode:`
     *
     * distinct from [Emoji] because it is its character: it inserts as is, where a custom emoji
     * inserts only a name that the send will translate into an image
     */
    data class Unicode(val emoji: li.velvetchat.core.model.EmojiUnicode) : Suggestion

    /** `@room`, the whole room. no object behind it, since it is nobody in particular */
    data object ToutLeSalon : Suggestion
}

/** the protocol's keyword for addressing a whole room. not translatable */
const val MENTION_SALON_ENTIER = "@room"

/**
 * what to offer, based on what is being typed
 *
 * the trigger is the last `:`, `@` or `#` in the text, and it has to start a word, preceded by the
 * start of the text or a space. without that condition, the matrix id `@papa:exemple.org` would
 * trigger the autocomplete on its own `@`, and an email address would do the same every time
 * somebody typed one
 *
 * the thresholds differ per trigger, and that is not an inconsistency. `:` needs two characters,
 * since on its own it appears in "18:30" and in any ordinary colon, where offering a list would be
 * noise. `@` and `#` offer from the sign itself, because people type them precisely to name
 * somebody or somewhere, and they do not always know the exact spelling of what they are looking
 * for
 */
fun suggestions(
    texte: String,
    pack: List<CustomEmote>,
    membres: List<Member>,
    salons: List<Channel>,
): Pair<String, List<Suggestion>>? {
    val debut = texte.indexOfDernierDeclencheur() ?: return null
    val signe = texte[debut]
    val fragment = texte.substring(debut + 1)

    // a space closes the fragment: "@papa thanks" is not looking for anybody any more
    if (fragment.any { it.isWhitespace() }) return null

    val trouves: List<Suggestion> = when (signe) {
        ':' -> {
            if (fragment.length < 2) return null
            if (fragment.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) return null
            // the server's emoji first, the unicode ones after. a server pack holds a few dozen
            // entries chosen on purpose, the unicode catalogue holds thousands nobody decided on,
            // and interleaving them would push a house emoji below a `:cat:` nobody asked for. same
            // order as the picker's grid, where the server sections come before the unicode ones
            pack.filter { it.shortcode.contains(fragment, ignoreCase = true) }
                .triParDebut(fragment) { it.shortcode }
                .map { Suggestion.Emoji(it) } +
                li.velvetchat.core.model.CATALOGUE_UNICODE
                    // the keywords count as much as the shortcode, and that was the heart of the
                    // complaint. the reference, on `:joy`, also offers `:rofl:`, which does not
                    // contain "joy", because it searches what the emoji means and not only what it
                    // is called. the catalogue has carried those words from the start, nobody was
                    // reading them
                    .filter { emoji ->
                        emoji.raccourci.contains(fragment, ignoreCase = true) ||
                            emoji.motsCles.any { it.contains(fragment, ignoreCase = true) }
                    }
                    // the name first, the meaning after: somebody typing `:joy` wants `:joy:` at
                    // the top, even though `:rofl:` may follow. `sortedByDescending` is stable, so
                    // the catalogue's order survives inside each group
                    .sortedByDescending { it.raccourci.contains(fragment, ignoreCase = true) }
                    .triParDebut(fragment) { it.raccourci }
                    .map { Suggestion.Unicode(it) }
        }

        '@' -> {
            val personnes = membres
                .filter { membre ->
                    fragment.isEmpty() ||
                        membre.nomAffiche().contains(fragment, ignoreCase = true) ||
                        membre.userId.contains(fragment, ignoreCase = true)
                }
                .triParDebut(fragment) { it.nomAffiche() }
                .map { Suggestion.Personne(it) }
            // `@room` last, never first. it is the one that wakes everyone's phones, and
            // offering it at the top would get it picked by accident by anybody confirming quickly
            val salonEntier =
                if (fragment.isEmpty() || "room".startsWith(fragment, ignoreCase = true) ||
                    "salon".startsWith(fragment, ignoreCase = true)
                ) listOf(Suggestion.ToutLeSalon) else emptyList()
            personnes + salonEntier
        }

        '#' -> salons
            .filter { fragment.isEmpty() || it.name.contains(fragment, ignoreCase = true) }
            .triParDebut(fragment) { it.name }
            .map { Suggestion.Salon(it) }

        else -> return null
    }

    return if (trouves.isEmpty()) null else fragment to trouves.take(12)
}

/** replaces the fragment being typed with the chosen suggestion, followed by a space */
fun completer(
    texte: String,
    suggestion: Suggestion,
    /** the tone chosen in the picker, applied to the emoji that take one */
    teinte: li.velvetchat.core.model.TeintePeau =
        li.velvetchat.core.model.TeintePeau.DEFAUT,
): String {
    val debut = texte.indexOfDernierDeclencheur() ?: return texte
    // the trailing space saves typing one before the next word, and above all stops the next
    // suggestion sticking to the previous one, since `:a::b:` does not read
    val insere = when (suggestion) {
        is Suggestion.Emoji -> ":${suggestion.emote.shortcode}: "
        // the character, and not the `:shortcode:`. inserting the name would mean translating it
        // again on send, and above all would leave the field showing `:joy:` where somebody just
        // picked the emoji from a list that showed it. translating shortcodes typed straight
        // through still happens on send, for anybody not going through the list
        is Suggestion.Unicode -> suggestion.emoji.avec(teinte) + " "

        is Suggestion.Personne -> "@${suggestion.membre.nomAffiche()} "
        is Suggestion.Salon -> "#${suggestion.salon.name} "
        Suggestion.ToutLeSalon -> "$MENTION_SALON_ENTIER "
    }
    return texte.substring(0, debut) + insere
}

/**
 * the last trigger in word initial position, or null
 *
 * we search backwards: the trigger that counts is the one being typed, not an emoji already placed
 * earlier in the sentence. by the time you are writing what follows, you are not writing that one
 * any more
 */
private fun String.indexOfDernierDeclencheur(): Int? {
    for (i in indices.reversed()) {
        if (this[i] !in ":@#") continue
        val avant = getOrNull(i - 1)
        if (avant == null || avant.isWhitespace()) return i
        // a `:` can also close an already complete shortcode, so we do not stop at the first sign
        // we meet, we keep going back
    }
    return null
}

/**
 * the ones that start with the fragment first
 *
 * that is what you were looking for by typing it, and a match in the middle of a word is a
 * secondary find. `sortedByDescending` on a boolean is stable, so the original order, by power for
 * members and by space order for channels, survives inside each of the two groups
 */
private fun <T> List<T>.triParDebut(fragment: String, cle: (T) -> String): List<T> =
    sortedByDescending { cle(it).startsWith(fragment, ignoreCase = true) }


// `composerMessage` and its html escaping now live in `core`, see `core/model/ComposeMessage.kt`.
// they have no android dependency at all, being text to protocol translation rules, and keeping
// them here made them untestable other than by building an apk and looking at it. which is exactly
// what cost three build cycles before the code moved down a floor

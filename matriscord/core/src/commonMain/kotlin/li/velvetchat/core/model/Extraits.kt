package li.velvetchat.core.model

import li.velvetchat.core.tr

/**
 * what we pull out of a conversation for the channel details screen
 *
 * media, links and pins ask the server for nothing: they're already in the timeline we're
 * holding. that's what makes three of those tabs nearly free, and it's also what limits them.
 * see [FENETRE_SEULEMENT]
 *
 * the whole file is arithmetic on data we already loaded: no network, no platform, no compose.
 * so it tests in a second, which is the only defence there is against extraction bugs. a
 * missed link or a duplicate doesn't crash anything, it quietly produces a wrong list
 */
object Extraits {

    /**
     * the caveat to put on screen rather than hide
     *
     * these lists only see the loaded window of the conversation. something sent six months ago
     * isn't in them until somebody scrolls back that far. discord queries a server side index
     * and sees everything
     *
     * saying so beats implying a complete inventory: a gallery that looks exhaustive and isn't
     * makes people conclude a photo went missing
     */
    val FENETRE_SEULEMENT get() =
        tr("Ce que la conversation a chargé jusqu'ici. Remontez-la pour en voir davantage.", "What the conversation has loaded so far. Scroll it up to see more.")

    /**
     * messages carrying a media, newest first
     *
     * images and video only. an attached file or a voice message is an attachment, not something
     * you browse in a grid. putting them in a thumbnail gallery gives you grey squares you
     * can't look at
     */
    fun medias(messages: List<Message>): List<Message> =
        messages.asSequence()
            .filter { message ->
                val corps = message.body
                corps is MessageBody.Media &&
                    (corps.kind == MediaKind.IMAGE || corps.kind == MediaKind.VIDEO)
            }
            .sortedByDescending { it.timestamp }
            .toList()

    /**
     * links mentioned in the conversation, newest first
     *
     * deduplicated on the address, keeping the most recent one: a link three people reshare is
     * one resource, and three identical rows would ruin a list whose entire job is helping you
     * find something again
     *
     * mentions are not links. the protocol writes "@papa" as a `matrix.to` link, and letting
     * those through would fill the tab with addresses nobody typed that lead nowhere outside the
     * app. same reasoning as rendering a message body, where a mention gets a pill and not an
     * underline
     */
    fun liens(messages: List<Message>): List<LienCite> {
        val vus = mutableSetOf<String>()
        return messages.asSequence()
            .sortedByDescending { it.timestamp }
            .flatMap { message ->
                val corps = message.body as? MessageBody.Text ?: return@flatMap emptySequence()
                MOTIF_URL.findAll(corps.text)
                    .map { nettoyer(it.value) }
                    .filter { it.isNotEmpty() && !it.startsWith(LIEN_MATRIX) }
                    .map { url -> LienCite(url, hoteDe(url), message) }
            }
            .filter { vus.add(it.url) }
            .toList()
    }

    /**
     * pinned messages, newest first
     *
     * they arrive from the engine with their bodies, through a timeline focused on pins, so all
     * of them show up including ones outside the loaded window. only the ordering is left to do
     * here: the sdk returns them oldest first, and a pin list reads the other way round, like
     * anywhere you're looking for "what got marked last"
     */
    fun epingles(messages: List<Message>): List<Message> = messages.asReversed()

    /**
     * strips the punctuation a sentence glues onto an address
     *
     * "regarde https://exemple.org/page." does not contain an address ending in a full stop.
     * closing brackets only come off when nothing opens them inside the address, otherwise we'd
     * chop a wikipedia url in half
     */
    internal fun nettoyer(brut: String): String {
        var url = brut
        while (url.isNotEmpty()) {
            val dernier = url.last()
            val aRetirer = when (dernier) {
                '.', ',', ';', ':', '!', '?', '"', '\'', '»' -> true
                ')' -> !url.contains('(')
                ']' -> !url.contains('[')
                '}' -> !url.contains('{')
                else -> false
            }
            if (!aRetirer) break
            url = url.dropLast(1)
        }
        return url
    }

    /** the host, minus `www.`, which is what goes under the title */
    internal fun hoteDe(url: String): String =
        url.substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfter('@')
            .substringBefore(':')
            .removePrefix("www.")
            .lowercase()

    /**
     * `http://` and `https://` only
     *
     * no bare domain detection on purpose: it would read the end of a sentence as a hostname
     * ("c'est fini.Bonjour") and fill the tab with links that aren't. anything you actually want
     * to find again was almost certainly pasted with its scheme
     */
    private val MOTIF_URL = Regex("""https?://[^\s<>"'`]+""")

    private const val LIEN_MATRIX = "https://matrix.to/#/"
}

/**
 * a cited link, with the message it came from
 *
 * the whole message rather than just author and date: the tab also shows a snippet of the text
 * around the link, and jumping to the message is the obvious next thing to want
 */
data class LienCite(
    val url: String,
    /** hostname without `www.`, the most useful label for recognising a link */
    val hote: String,
    val message: Message,
)

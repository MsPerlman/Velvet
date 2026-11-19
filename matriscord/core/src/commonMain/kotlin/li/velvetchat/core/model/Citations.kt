package li.velvetchat.core.model

import li.velvetchat.core.tr

/**
 * rebuilds a quote from the timeline we already have
 *
 * the sdk only resolves the target of a reply once it's holding it, and when you open a channel
 * it isn't. so a reply sent a minute ago showed its quote, and the same reply after a restart
 * showed none. the quote went missing exactly when it's useful, which is when you come back
 * to a channel and read what was said
 *
 * except the information is right there. `replyTo` carries the target id, and the target is very
 * often in the list we're already drawing, because you reply to what you can see. looking it up
 * in that list costs nothing and waits for nobody
 *
 * what it doesn't cover: a reply to something older than the loaded window. only the sdk can
 * answer that, and it will once it has paginated. the two sources complement each other rather
 * than compete, and the sdk's wins because it's right even when the target is out of view
 *
 * here rather than in the screen because it's a data transformation, so it can be tested
 */
fun List<Message>.avecCitations(): List<Message> {
    // nothing to do in the common case, a channel with no replies doesn't pay for the map
    if (none { it.replyTo != null && it.citation == null }) return this

    val parId = associateBy { it.id }
    return map { message ->
        if (message.citation != null || message.replyTo == null) return@map message
        val cible = parId[message.replyTo] ?: return@map message
        message.copy(citation = cible.enCitation())
    }
}

/** this message cut down to what a one line quote shows of it */
fun Message.enCitation(): Citation = Citation(
    eventId = id,
    auteurId = sender,
    auteur = senderDisplayName ?: sender.removePrefix("@").substringBefore(':'),
    auteurAvatarUrl = senderAvatarUrl,
    apercu = body.enApercu(),
)

/**
 * a message body cut down to one line
 *
 * stripped html beats raw text here, and that isn't a preference: when a client sends markdown
 * the protocol puts the source in `body` and the rendering in `formatted_body`. a preview taken
 * from `body` therefore prints `**gras** et _italique_`, literally, inside the quote
 *
 * for anything that isn't text we name what the thing is. the `body` of an image is its
 * filename, and "replied to IMG_4821.jpg" tells you nothing that "replied to your photo"
 * doesn't, only less readably
 */
fun MessageBody.enApercu(): String = when (this) {
    is MessageBody.Text ->
        (html?.let(::texteSansBalises)?.ifBlank { null } ?: text)
            .replace('\n', ' ')
            .trim()
            .take(APERCU_MAX)

    is MessageBody.Media -> when (kind) {
        MediaKind.IMAGE -> "Photo"
        MediaKind.VIDEO -> tr("Vidéo", "Video")
        MediaKind.AUDIO -> tr("Message vocal", "Voice message")
        MediaKind.FILE -> tr("Fichier", "File")
    }

    is MessageBody.Poll -> "Sondage · $question"
    MessageBody.Redacted -> tr("Message supprimé", "Deleted message")
    is MessageBody.Undecryptable -> tr("Message chiffré", "Encrypted message")
    is MessageBody.System -> tr("Événement", "Event")
    is MessageBody.Unsupported -> "Message"
}

/** compiled once, [texteSansBalises] runs over every message of every timeline snapshot */
private val MOTIF_BALISE = Regex("<[^>]*>")

/**
 * the text inside a fragment of html
 *
 * not a parser, and it must not turn into one. it drops tags and unescapes the entities
 * matrix's html produces. for one line truncated at eighty characters, next to a sixteen point
 * avatar, that's plenty. wiring a real parser in would cost a tree per message per timeline
 * snapshot
 *
 * message bodies do go through the platform parser, see `RichText.kt`
 *
 * `&amp;` is unescaped last, otherwise it would re-expand the `&` of entities already handled:
 * `&amp;lt;` has to come out as `&lt;`, not `<`
 */
fun texteSansBalises(html: String): String = html
    .replace(MOTIF_BALISE, "")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .trim()

private const val APERCU_MAX = 80

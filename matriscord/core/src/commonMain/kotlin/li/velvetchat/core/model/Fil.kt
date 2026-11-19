package li.velvetchat.core.model

import li.velvetchat.core.tr

/**
 * a discussion thread
 *
 * useful with three people, indispensable with thirty. worth doing before the ui cloning pass
 * rather than after, because a thread changes the structure of a conversation and not its
 * appearance
 *
 * this is not an experiment: the `m.thread` relation has been in the protocol since matrix v1.4.
 * the `org.matrix.msc3440.stable` flag the server declares is the historical one kept for
 * compatibility
 *
 * what this type carries is a thread as seen from the list, enough to know whether it is worth
 * opening. the contents are a timeline in their own right and read from their own flow, since a
 * thread of three hundred replies has no business fitting inside the object that summarises it
 *
 * @param racine the event that opened the thread. its id is what opens the matching timeline
 * @param nombreReponses excluding the root, the way the sdk counts them. zero never happens,
 *   since an event with no replies is not a thread
 * @param derniere the most recent reply, when the server supplied it. null is not an error, the
 *   server's bundled summary may simply not have it yet
 */
data class Fil(
    val racine: Message,
    val nombreReponses: Int,
    val derniere: Message?,
) {
    /** what we show so a thread is recognisable: a preview of whatever opened it */
    val titre: String
        get() = when (val corps = racine.body) {
            is MessageBody.Text -> corps.text
            is MessageBody.Media -> corps.caption.takeIf { it.isNotBlank() } ?: tr("Média", "Media")
            is MessageBody.Poll -> corps.question
            else -> tr("Fil de discussion", "Thread")
        }
}

package li.velvetchat.app.ui

import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody

// moved out of `Shell.kt`: the desktop groups by the same rule as the phone. rewriting it there
// would have given two visibly different conversations for one room, and that is exactly the kind
// of gap you only see by opening them side by side

/**
 * does this message continue the previous one's group?
 *
 * four breaks, even when the author does not change, the day change being handled upstream by the
 * separator
 *
 * 1. a gap larger than the grouping window 2. this message is a reply: it carries its own quote
 * above it, and a header has to sit over that
 *      to say who is replying
 * 3. a system message is involved 4. the author changes
 *
 * what changed, and why it was wrong. rule 2 also broke the group when the previous message was a
 * reply. so writing a reply then a second message straight after showed the avatar, the nickname
 * and the time twice, in the same minute
 *
 * the justification did not hold: the previous message's quote is above the previous message, not
 * between the two. nothing breaks the continuity between a message and the one after it. the
 * reference groups them, and a side by side comparison shows it, a reply followed by a word gives
 * one block there
 *
 * this is the concrete lead behind "velvet feels colder": a timeline that redraws the header on
 * every turn looks like a log, not a conversation
 */
fun continueLeGroupe(precedent: Message?, message: Message, fenetreMs: Long): Boolean {
    if (precedent == null) return false
    if (precedent.sender != message.sender) return false
    if (message.replyTo != null) return false
    if (message.body is MessageBody.System || precedent.body is MessageBody.System) return false
    return message.timestamp - precedent.timestamp <= fenetreMs
}

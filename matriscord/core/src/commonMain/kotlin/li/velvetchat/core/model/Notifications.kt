package li.velvetchat.core.model

import li.velvetchat.core.tr

/**
 * what a channel is allowed to make noise about
 *
 * discord calls this notification settings and hides it behind a long press on the channel
 *
 * four values and not three, and the fourth is not a detail: [DEFAUT] means this channel has no
 * opinion and follows the account rule. telling it apart from [TOUT] is what lets you change the
 * global setting without every channel you already visited staying frozen on the old value. the
 * protocol carries it natively, since a missing push rule *is* the default
 */
enum class ModeNotification {
    /** every message rings */
    TOUT,

    /** mentions and keywords only */
    MENTIONS,

    /**
     * nothing rings
     *
     * the unread counter keeps climbing. going quiet is not forgetting. same distinction as
     * between the master rule, which stops a push being sent, and a frozen counter
     */
    MUET,

    /** no rule of its own, this channel follows the account setting */
    DEFAUT,
    ;

    /** what we show to describe it */
    val libelle: String
        get() = when (this) {
            TOUT -> tr("Tous les messages", "All messages")
            MENTIONS -> tr("Mentions uniquement", "Only mentions")
            MUET -> tr("Rien", "Nothing")
            DEFAUT -> tr("Par défaut", "Default")
        }

    /** what it means, said at the moment you choose rather than on a help page */
    val detail: String
        get() = when (this) {
            TOUT -> tr("Chaque message de ce salon fait sonner le téléphone.", "Every message in this channel rings the phone.")
            MENTIONS -> tr("Seulement quand quelqu'un vous nomme, ou nomme le salon entier.", "Only when someone mentions you, or the whole channel.")
            // rewritten because the sentence had become false on screen. it used to say the
            // unread counter keeps climbing, which is true server side, but since the list
            // started greying out muted channels their unread dot and their bold went away. a
            // description of the old interface is worse than no description, it makes you doubt
            // what you are looking at
            MUET -> tr("Aucune notification, et le salon s'estompe dans la liste. Une mention ", "No notifications, and the channel fades in the list. A mention ") +
                tr("reste signalée.", "still shows.")
            DEFAUT -> tr("Ce salon suit le réglage général du compte.", "This channel follows the account's general setting.")
        }
}

/**
 * what is new in a channel, cut down to what a system notification has to show
 *
 * different from [li.velvetchat.core.Notification], which rebuilds an event from a push and
 * therefore only serves the unifiedpush path. this one comes from the other end, the sync
 * already running. both exist because they answer two different questions: what does this push
 * say, and what did i miss
 *
 * @param dernierEvenement the deduplication key, which is what separates "a new message
 *   arrived" from "the list simply redrew". without it the slightest recompute would make the
 *   phone ring again
 * @param aMoi the last message is ours, sent from another device. never notified, nobody needs
 *   telling about what they just wrote
 * @param auteurId the sender's id, distinct from [auteur] which is their display name. android
 *   identifies a person by a stable key to group their messages, and a display name changes,
 *   which would show two correspondents where there is one
 * @param avatarSalon and [avatarAuteur] as `mxc://`. they feed the conversation shortcut icon
 *   and the person's icon in the notification. without them android's conversations section
 *   shows a grey blob for everybody
 */
data class AlerteSalon(
    val salonId: String,
    val nom: String,
    val auteur: String?,
    val auteurId: String?,
    val apercu: String?,
    val nonLus: Int,
    val mentions: Int,
    val estDirect: Boolean,
    val dernierEvenement: String?,
    val horodatage: Long,
    val aMoi: Boolean,
    val avatarSalon: String? = null,
    val avatarAuteur: String? = null,
)

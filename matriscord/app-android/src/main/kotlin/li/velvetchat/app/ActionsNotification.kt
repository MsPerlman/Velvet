package li.velvetchat.app

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import li.velvetchat.app.ui.horsUi

/**
 * the two gestures a notification has to allow without opening the app
 *
 * ## what was missing
 *
 * a velvet notification offered nothing: no "mark as read", no "reply". the reference has offered
 * both forever, and the side by side comparison leaves no doubt about what jumps out, two buttons
 * against none
 *
 * this is not garnish. a personal messenger mostly receives messages you answer in three words, or
 * acknowledge without answering. making people open the app, wait for the sync and find the room to
 * write "ok" is the kind of friction that stops them answering
 *
 * ## why a receiver rather than an activity
 *
 * replying from the panel must not open the screen, which is the whole point. a `BroadcastReceiver`
 * runs with no interface, receives the input through [RemoteInput], and returns. [goAsync] keeps
 * the process alive for the duration of the send, and without it android would destroy the receiver
 * when `onReceive` returns, which is to say before the request had left
 *
 * the receiver is not exported: only the `PendingIntent`s we build can trigger it. a notification
 * carries a room id, and letting any app post in any room would be an open door
 */
class ActionsNotification : BroadcastReceiver() {

    override fun onReceive(contexte: Context, intent: Intent) {
        val salon = intent.getStringExtra(EXTRA_SALON) ?: return
        val app = contexte.applicationContext
        val notifId = intent.getIntExtra(EXTRA_NOTIF, salon.hashCode())

        // the text only exists for the "reply" action, and is `null` for "mark as read"
        val texte = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(CLE_REPONSE)?.toString()?.trim()

        val fini = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val engine = Engine.get(app)
                // the session may not be in memory: the receiver sometimes starts a brand new
                // process, woken by the notification alone
                runCatching { Engine.restaurer(app, engine) }

                val envoye = when {
                    texte.isNullOrBlank() -> true
                    else -> runCatching { engine.send(salon, texte) }.isSuccess
                }
                // we only acknowledge what went out. marking as read a message whose reply failed
                // would erase the one trace saying to come back to it
                if (envoye) {
                    runCatching { engine.markRead(salon) }
                    // replying no longer closes the conversation, it feeds it
                    //
                    // the notification used to disappear: no trace of what you had just written,
                    // and nothing to tell "sent" from "the button did nothing". the reference adds
                    // the line to the thread and leaves it open, so you send two messages in a row
                    // without ever opening the app
                    //
                    // "mark as read" does close: that is what it is asked to do
                    val alimente = !texte.isNullOrBlank() &&
                        runCatching { Annonceur.ajouterMaReponse(app, salon, texte) }
                            .getOrDefault(false)
                    if (!alimente) {
                        androidx.core.app.NotificationManagerCompat.from(app).cancel(notifId)
                    }
                }
            } finally {
                fini.finish()
            }
        }
    }

    companion object {
        private const val ACTION_LU = "li.velvetchat.LU"
        private const val ACTION_REPONDRE = "li.velvetchat.REPONDRE"
        private const val EXTRA_SALON = "salon"
        private const val EXTRA_NOTIF = "notif"
        private const val CLE_REPONSE = "reponse"

        /**
         * a distinct request code per room and per action
         *
         * `PendingIntent` does not tell apart two intents differing only by their extras: with no
         * distinct code, the second notification would reuse the first, and replying to marie would
         * send into jean's room. shifting by one bit separates the two actions of the same room
         */
        private fun code(salon: String, reponse: Boolean) =
            (salon.hashCode() shl 1) or (if (reponse) 1 else 0)

        private fun intention(
            contexte: Context,
            action: String,
            salon: String,
            notifId: Int,
        ) = Intent(contexte, ActionsNotification::class.java)
            .setAction(action)
            .putExtra(EXTRA_SALON, salon)
            .putExtra(EXTRA_NOTIF, notifId)

        /** "mark as read": acknowledges without opening anything */
        fun actionLu(contexte: Context, salon: String, notifId: Int): Notification.Action =
            Notification.Action.Builder(
                // the toolkit's tick, not `ic_menu_view`, android 2.3's magnifier, which said
                // "view" where the action is "acknowledge"
                android.graphics.drawable.Icon.createWithResource(
                    contexte, li.velvetchat.app.R.drawable.ic_check,
                ),
                horsUi.marquerCommeLu,
                PendingIntent.getBroadcast(
                    contexte,
                    code(salon, reponse = false),
                    intention(contexte, ACTION_LU, salon, notifId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build()

        /**
         * "reply", with input in the panel
         *
         * `FLAG_MUTABLE` is compulsory here, unlike everywhere else in this file: the system is
         * what writes the typed reply into the intent before firing it. an immutable intent would
         * receive it empty, with no error at all
         */
        fun actionRepondre(contexte: Context, salon: String, notifId: Int): Notification.Action =
            Notification.Action.Builder(
                // the app's paper plane, the composer's: the same gesture carries the same sign on
                // both sides
                android.graphics.drawable.Icon.createWithResource(
                    contexte, li.velvetchat.app.R.drawable.ic_paper_plane_tilt,
                ),
                horsUi.repondre,
                PendingIntent.getBroadcast(
                    contexte,
                    code(salon, reponse = true),
                    intention(contexte, ACTION_REPONDRE, salon, notifId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            )
                .addRemoteInput(
                    RemoteInput.Builder(CLE_REPONSE).setLabel(horsUi.repondrePoints).build(),
                )
                // tells the system that this action is the reply: that is what puts it in the right
                // place on a watch and in grouped conversations
                .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                .setAllowGeneratedReplies(true)
                .build()
    }
}

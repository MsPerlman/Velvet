package li.velvetchat.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import li.velvetchat.core.NotificationResult
import li.velvetchat.core.model.AlerteSalon
import li.velvetchat.core.model.MediaKind
import li.velvetchat.core.model.MessageBody
import li.velvetchat.app.ui.horsUi

/**
 * the firebase wake up, which replaces the permanent watch when it is configured
 *
 * what arrives here amounts to two ids: a room and an event. that is all the `EVENT_ID_ONLY`
 * payload carries, and it is deliberate, neither the text, nor the sender, nor a preview crosses
 * google. the content is read here, by `NotificationEngine.resolve`, which briefly opens the
 * session, fetches the event and decrypts it with this device's keys
 *
 * ## `runBlocking`, and why it is correct here specifically
 *
 * `onMessageReceived` runs on a firebase worker thread, not on the main thread, and the system
 * considers the message handled as soon as the method returns. returning before having shown the
 * notification amounts to showing nothing: the process can be killed within the second. a coroutine
 * launched in the background would have exactly that fault
 *
 * there is a time limit, android granting roughly ten seconds to a high priority wake up, hence
 * [DELAI_MAX_MS]'s guard. beyond it, a poor notification beats none, see [secours]
 *
 * ## what it does not do
 *
 * it does not deduplicate against [ServiceVeille]: the two never run together,
 * `ServiceVeille.doitTourner` seeing to that. it does not filter the room open on screen either,
 * [Veille.salonOuvert] handling that below, exactly as in the watch
 */
class ServicePush : FirebaseMessagingService() {

    /**
     * the token changed: reinstall, restore, play services purge
     *
     * without this reregistration the server would push to a dead token and the notifications would
     * stop one day, with no error and with nobody knowing when
     */
    override fun onNewToken(token: String) {
        val app = applicationContext
        runCatching {
            runBlocking {
                val engine = Engine.get(app)
                // the process may be brand new, woken by firebase alone: the session is not
                // necessarily in memory
                runCatching { Engine.restaurer(app, engine) }
                Push.enregistrer(app, engine)
            }
        }.onFailure { android.util.Log.w(JOURNAL, "Jeton renouvelé mais non enregistré", it) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val donnees = message.data
        val salon = donnees["room_id"] ?: return
        val evenement = donnees["event_id"]
        // `unread` comes from the server's counters, not from us: it is what knows how many
        // messages are waiting, including those we never received
        val nonLus = donnees["unread"]?.toIntOrNull() ?: 1

        // the room in front of your eyes does not notify. same rule as the watch, and the same
        // fault avoided, being told about what you are reading
        if (Veille.auPremierPlan && salon == Veille.salonOuvert) return

        val app = applicationContext
        runCatching {
            runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(DELAI_MAX_MS) {
                    val engine = Engine.get(app)
                    if (!runCatching { Engine.restaurer(app, engine) }.getOrDefault(false)) {
                        // no session: pushing a notification for a logged out account would make no
                        // sense, and the pusher should have been removed
                        return@withTimeoutOrNull
                    }
                    if (evenement == null) {
                        // the `EVENT_ID_ONLY` format always carries one, and when it is missing the
                        // gateway is misconfigured. we show it anyway: "something is happening"
                        // beats a silence caused by a configuration fault
                        Annonceur.annoncer(app, secours(salon, nonLus))
                        return@withTimeoutOrNull
                    }
                    when (val quoi = engine.notifications.resolve(salon, evenement)) {
                        is NotificationResult.Show -> {
                            val n = quoi.notification
                            Annonceur.annoncer(
                                app,
                                AlerteSalon(
                                    salonId = n.channelId,
                                    nom = n.channelName,
                                    auteur = n.senderDisplayName,
                                    auteurId = n.sender,
                                    apercu = apercu(n.body),
                                    nonLus = nonLus,
                                    // the server has already decided: `isMention` is the result of
                                    // its push rules, not of a reread of the text. that is what
                                    // makes the banner say the same thing as whatever made the
                                    // phone ring
                                    mentions = if (n.isMention) 1 else 0,
                                    estDirect = n.isDirect,
                                    dernierEvenement = n.eventId,
                                    horodatage = n.timestamp,
                                    aMoi = false,
                                    avatarSalon = null,
                                    avatarAuteur = n.senderAvatarUrl,
                                )
                            )
                        }
                        // the server's push rules say to show nothing. that is normal operation, a
                        // muted room, a message from yourself, and not a failure: showing it anyway
                        // would be the fault
                        NotificationResult.Filtered -> Unit
                        NotificationResult.Redacted -> Unit
                        // an anomaly: the push announces an event that cannot be found. logged,
                        // because a push that lies is the kind of thing you want to know about
                        NotificationResult.NotFound ->
                            android.util.Log.w(JOURNAL, "Événement introuvable : $evenement")
                    }
                } ?: run {
                    // the deadline expired, a loaded account's first sync, a slow network. a poor
                    // notification rather than none: it opens the right room
                    android.util.Log.w(JOURNAL, "Résolution trop lente, annonce de secours")
                    Annonceur.annoncer(app, secours(salon, nonLus))
                }
            }
        }.onFailure { android.util.Log.w(JOURNAL, "Push non traité", it) }
    }

    /**
     * what we show when we could not read the event
     *
     * deliberately with no sender name and no preview: inventing either would be worse than saying
     * nothing. the room is known, coming from the push, so the notification stays actionable, which
     * is the main thing
     */
    private fun secours(salon: String, nonLus: Int) = AlerteSalon(
        salonId = salon,
        nom = horsUi.nouveauMessage,
        auteur = null,
        auteurId = null,
        apercu = null,
        nonLus = nonLus,
        mentions = 0,
        estDirect = false,
        dernierEvenement = null,
        horodatage = System.currentTimeMillis(),
        aMoi = false,
    )

    /**
     * the body reduced to one line
     *
     * a medium's filename is not shown: "IMG_20260825.jpg" teaches nothing more than an empty line,
     * and "photo" says exactly what there is to know in a status bar. same trade off as a quote's
     * preview
     */
    private fun apercu(corps: MessageBody): String = when (corps) {
        is MessageBody.Text -> corps.text
        is MessageBody.Media -> corps.caption.takeIf {
            it.isNotBlank() && it != corps.media.filename
        } ?: when (corps.kind) {
            MediaKind.IMAGE -> horsUi.photo
            MediaKind.VIDEO -> horsUi.video
            MediaKind.AUDIO -> horsUi.messageVocal
            MediaKind.FILE -> horsUi.joindreFichier
        }

        is MessageBody.Poll -> corps.question
        MessageBody.Redacted -> horsUi.messageSupprime
        // the case explicitly foreseen: the push arrived and the megolm key is missing. we say so,
        // "julie, #general" without the text stays actionable, and beats a "new message" that would
        // suggest something else
        is MessageBody.Undecryptable -> horsUi.messageChiffreNonLisible
        is MessageBody.System -> horsUi.nouveauMessage
        is MessageBody.Unsupported -> corps.description
    }

    private companion object {
        const val JOURNAL = "Velvet/Push"

        /**
         * a wake up's budget
         *
         * android grants about ten seconds to a high priority message before reclaiming the
         * process. we stop before that, to have time to show the fallback announcement rather than
         * being cut off mid resolution
         */
        const val DELAI_MAX_MS = 8_000L
    }
}

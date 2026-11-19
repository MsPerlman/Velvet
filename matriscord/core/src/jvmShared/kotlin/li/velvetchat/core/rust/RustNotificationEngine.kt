package li.velvetchat.core.rust

import li.velvetchat.core.tr

import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.HttpPusherData
import org.matrix.rustcomponents.sdk.MessageLikeEventContent
import org.matrix.rustcomponents.sdk.NotificationEvent
import org.matrix.rustcomponents.sdk.NotificationItem
import org.matrix.rustcomponents.sdk.NotificationProcessSetup
import org.matrix.rustcomponents.sdk.NotificationStatus
import org.matrix.rustcomponents.sdk.PushFormat
import org.matrix.rustcomponents.sdk.PusherIdentifiers
import org.matrix.rustcomponents.sdk.PusherKind
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.TimelineEventContent
import li.velvetchat.core.EngineException
import li.velvetchat.core.Notification
import li.velvetchat.core.NotificationEngine
import li.velvetchat.core.NotificationResult
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.UndecryptableReason

/**
 * notifications on top of `matrix-rust-sdk`
 *
 * decrypting an event from nothing but its id is engine work, and the desktop needs it
 * identically, which is why it lives here rather than in the android app
 *
 * @param clientProvider the current client, supplied lazily, since notifications get set up
 *   after the session opens
 * @param syncProvider the sync service if one is running. it decides how the crypto store gets
 *   accessed, see [notificationClient]
 */
internal class RustNotificationEngine(
    private val clientProvider: () -> Client,
    private val syncProvider: () -> SyncService?,
) : NotificationEngine {

    private var cached: org.matrix.rustcomponents.sdk.NotificationClient? = null
    private var cachedForSync: SyncService? = null

    /**
     * the notification client, with the right crypto store access mode
     *
     * not a cosmetic distinction. the crypto store is a sqlite database, and two processes
     * opening it at once tread on each other. the sdk offers two modes and getting it wrong
     * gives you either a lock or corruption
     *
     * sync running means same process, so we reuse its access rather than opening a second one
     * on the same database. no sync means separate process, which is the background wakeup with
     * the app closed, and precisely the case all of this exists for
     *
     * the choice is inferred from the actual state rather than asked of the caller, because the
     * caller is an android `BroadcastReceiver` with no way of knowing whether the app is running
     */
    private suspend fun notificationClient(): org.matrix.rustcomponents.sdk.NotificationClient {
        val sync = syncProvider()
        cached?.let { if (cachedForSync === sync) return it }

        val setup = if (sync != null) {
            NotificationProcessSetup.SingleProcess(sync)
        } else {
            NotificationProcessSetup.MultipleProcesses
        }
        return clientProvider().notificationClient(setup).also {
            cached = it
            cachedForSync = sync
        }
    }

    override suspend fun resolve(channelId: String, eventId: String): NotificationResult {
        val status = try {
            notificationClient().getNotification(channelId, eventId)
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Notification illisible pour $eventId", "Unreadable notification for $eventId"), t)
        }

        return when (status) {
            is NotificationStatus.Event -> NotificationResult.Show(status.item.toCore(channelId))
            NotificationStatus.EventFilteredOut -> NotificationResult.Filtered
            NotificationStatus.EventRedacted -> NotificationResult.Redacted
            NotificationStatus.EventNotFound -> NotificationResult.NotFound
        }
    }

    private fun NotificationItem.toCore(channelId: String): Notification {
        val ev = event
        val timeline = (ev as? NotificationEvent.Timeline)?.event

        val body = when {
            // an invite has no content, the event itself is the message. announce it rather
            // than hand back an empty body
            ev is NotificationEvent.Invite -> MessageBody.Unsupported("invitation")
            timeline == null -> MessageBody.Unsupported(tr("événement non pris en charge", "unsupported event"))
            else -> when (val content = timeline.content()) {
                is TimelineEventContent.MessageLike -> when (val m = content.content) {
                    is MessageLikeEventContent.RoomMessage ->
                        m.messageType.toBody(fallback = "message")
                    // the push arrived but the megolm key is missing. say so rather than show
                    // "new message". "julie, #general" with no text is still actionable
                    //
                    // `UNKNOWN` and not one of the named causes: this path never attempted a
                    // decryption, it reads a raw event the server pushed. it has no `UtdCause`
                    // to report, and guessing one here would put a sentence in a notification
                    // that nothing measured
                    MessageLikeEventContent.RoomEncrypted ->
                        MessageBody.Undecryptable(UndecryptableReason.UNKNOWN)
                    is MessageLikeEventContent.Poll -> MessageBody.Unsupported("sondage")
                    else -> MessageBody.Unsupported(tr("événement non affichable", "event that cannot be shown"))
                }
                is TimelineEventContent.State -> MessageBody.Unsupported(tr("événement d'état", "state event"))
            }
        }

        return Notification(
            channelId = channelId,
            channelName = roomInfo.displayName,
            eventId = timeline?.eventId() ?: "",
            sender = timeline?.senderId() ?: "",
            senderDisplayName = senderInfo.displayName,
            senderAvatarUrl = senderInfo.avatarUrl,
            body = body,
            timestamp = timeline?.timestamp()?.toLong() ?: 0L,
            isDirect = roomInfo.isDirect,
            isMention = hasMention == true,
            isNoisy = isNoisy == true,
        )
    }

    override suspend fun registerPush(
        endpoint: String,
        gatewayUrl: String,
        appId: String,
        deviceName: String,
        trustedEndpointHosts: Set<String>,
    ) {
        requireTrustedEndpoint(endpoint, trustedEndpointHosts)
        requireUsableGateway(gatewayUrl)

        try {
            clientProvider().setPusher(
                identifiers = PusherIdentifiers(pushkey = endpoint, appId = appId),
                kind = PusherKind.Http(
                    HttpPusherData(
                        url = gatewayUrl,
                        // the setting that carries the entire privacy promise: the payload
                        // holds nothing but the room id and the event id. the gateway relays
                        // without ever seeing the content
                        format = PushFormat.EVENT_ID_ONLY,
                        defaultPayload = null,
                    )
                ),
                appDisplayName = "Velvet",
                deviceDisplayName = deviceName,
                profileTag = null,
                lang = "fr",
                // false means replace the pusher with the same key rather than stack a
                // second one. otherwise every re registration leaves a duplicate behind and
                // the server pushes twice
                append = false,
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Enregistrement du pusher refusé", "Pusher registration refused"), t)
        }
    }

    override suspend fun registerPushFcm(
        jeton: String,
        gatewayUrl: String,
        appId: String,
        deviceName: String,
    ) {
        // no `requireTrustedEndpoint` here, and the contract says why: an fcm token is not an
        // address and has no host. the gateway is checked like everywhere else
        requireUsableGateway(gatewayUrl)
        if (jeton.isBlank()) {
            throw EngineException.Unexpected(tr("Jeton Firebase vide : rien à enregistrer", "Empty Firebase token: nothing to register"))
        }
        try {
            clientProvider().setPusher(
                identifiers = PusherIdentifiers(pushkey = jeton, appId = appId),
                kind = PusherKind.Http(
                    HttpPusherData(
                        url = gatewayUrl,
                        // same as the unifiedpush path, for the same reason: the gateway, so
                        // sygnal, so google, only relays two ids. the content is fetched and
                        // decrypted here
                        format = PushFormat.EVENT_ID_ONLY,
                        defaultPayload = null,
                    )
                ),
                appDisplayName = "Velvet",
                deviceDisplayName = deviceName,
                profileTag = null,
                lang = "fr",
                // `false`, like everywhere else. an fcm token changes on its own, a reinstall, a
                // restore, play services data cleared. stacking pushers would keep pushing to dead
                // tokens until the server pruned them
                append = false,
            )
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Enregistrement du pusher Firebase refusé", "Firebase pusher registration refused"), t)
        }
    }

    override suspend fun unregisterPush(endpoint: String, appId: String) {
        try {
            clientProvider().deletePusher(PusherIdentifiers(pushkey = endpoint, appId = appId))
        } catch (t: Throwable) {
            throw EngineException.Unexpected(tr("Suppression du pusher impossible", "Could not remove the pusher"), t)
        }
    }

    /**
     * the endpoint has to come from a declared host, over https
     *
     * this is the untrusted value of the whole operation: it comes back from the unifiedpush
     * distributor, and it is what will receive this account's room ids. https with no exceptions,
     * since that address crosses the internet on its way to the phone
     */
    private fun requireTrustedEndpoint(endpoint: String, trusted: Set<String>) {
        if (trusted.isEmpty()) {
            throw EngineException.Unexpected(
                tr("Aucun hôte de confiance déclaré : refus d'enregistrer un pusher à l'aveugle", "No trusted host declared: refusing to register a pusher blindly")
            )
        }
        if (!endpoint.startsWith("https://")) {
            throw EngineException.Unexpected(tr("L'endpoint de push doit être en HTTPS : « $endpoint »", "The push endpoint must be HTTPS: \"$endpoint\""))
        }
        val host = endpoint.hostOrNull()
            ?: throw EngineException.Unexpected(tr("Endpoint de push illisible : « $endpoint »", "Unreadable push endpoint: \"$endpoint\""))
        if (trusted.none { it.equals(host, ignoreCase = true) }) {
            throw EngineException.Unexpected(
                tr("Endpoint de push refusé : « $host » n'est pas un hôte de confiance ", "Push endpoint refused: \"$host\" is not a trusted host ") +
                    tr("($trusted). Un distributeur mal configuré enverrait les identifiants ", "($trusted). A misconfigured distributor would send the ids ") +
                    tr("de vos salons à un tiers.", "of your channels to a third party.")
            )
        }
    }

    /**
     * the gateway has to be reachable by the server, not by the phone
     *
     * hence a different rule from the endpoint's: https required, except for a name with no dot in
     * it. such a name cannot be a public domain, it is a container or a local network host, so
     * traffic that never leaves. that is what allows `http://ntfy/_matrix/push/v1/notify` and
     * saves a round trip through the public tunnel on every notification
     */
    private fun requireUsableGateway(gatewayUrl: String) {
        val host = gatewayUrl.hostOrNull()
            ?: throw EngineException.Unexpected(tr("Passerelle de push illisible : « $gatewayUrl »", "Unreadable push gateway: \"$gatewayUrl\""))
        val interne = !host.contains('.')
        if (!gatewayUrl.startsWith("https://") && !interne) {
            throw EngineException.Unexpected(
                tr("Passerelle de push en clair vers un hôte public : « $gatewayUrl ». ", "Plain-text push gateway to a public host: \"$gatewayUrl\". ") +
                    tr("HTTPS obligatoire hors réseau interne.", "HTTPS is required outside the internal network.")
            )
        }
    }

    /** host extraction kept deliberately minimal. we are not validating a url, we are comparing
     *  one */
    private fun String.hostOrNull(): String? =
        substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .substringBefore(':')
            .takeIf { it.isNotBlank() }
}

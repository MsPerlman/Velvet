package li.velvetchat.core

import li.velvetchat.core.model.MessageBody

/**
 * push notifications, engine side — rebuilding a message from a push is decryption, so it belongs
 * here, and the desktop will want the same thing
 *
 * a push carries two identifiers, the room and the event: no content, no sender, no preview. the
 * gateway and the relaying server learn nothing about what was said, and the client alone fetches
 * the event and decrypts it with its own keys. that is what makes rich notifications possible
 * without handing the conversations to a push service
 */
interface NotificationEngine {

    /**
     * rebuilds a showable notification from the two identifiers, called from a background wakeup
     * with the app closed: it opens just enough to read the event, decrypts, and returns
     *
     * @return what to do, not merely what was found. see [NotificationResult]
     */
    suspend fun resolve(channelId: String, eventId: String): NotificationResult

    /**
     * registers this device with the homeserver so it sends pushes here, and **refuses an endpoint
     * outside [trustedEndpointHosts]** — not a theoretical precaution: a misconfigured distributor
     * once handed out an endpoint on the public `ntfy.sh` and the pusher got registered against it,
     * twice. every message would have given a room id and an event id to a stranger
     *
     * the endpoint and not the gateway, where the first version of the check demanded both be on
     * one host: the endpoint comes back from a third party component and is what receives the room
     * ids, while the gateway is a config value we write ourselves — and comparing the two even
     * accepted the case where both pointed at `ntfy.sh`
     *
     * which also frees the gateway to be internal (`http://ntfy/_matrix/push/v1/notify`), since the
     * server calls it from the same network. one hop and one dependency fewer
     *
     * @param endpoint the address the unifiedpush distributor gave us, and the pusher's key
     * @param gatewayUrl the matrix gateway. ntfy implements it natively, avoiding sygnal and fcm
     * @param trustedEndpointHosts mandatory and with no default, deliberately: a permissive one
     *   would have spread everywhere without anybody rereading it
     */
    suspend fun registerPush(
        endpoint: String,
        gatewayUrl: String,
        appId: String,
        deviceName: String,
        trustedEndpointHosts: Set<String>,
    )

    /**
     * the same for a firebase token. a separate method and not one more parameter, because the
     * difference lands exactly on what [registerPush] checks: the kind of key the pusher holds
     *
     * unifiedpush's key is a url from a third party component, so it can be pointed at the wrong
     * host, which is the incident [registerPush] describes. an fcm token is opaque, issued by
     * google for this install, not an address at all — there is no host to validate, and applying
     * the same check would simply have failed, the token not starting `https://`
     *
     * the gateway stays checked, being a different matter: that is our own sygnal, and the server
     * will call it in the clear if you let it
     *
     * the payload format stays `EVENT_ID_ONLY`, so google relays two identifiers and never a body
     * or a sender. what was refused was handing conversations to a third party, not the wakeup
     *
     * @param jeton this install's fcm token (`FirebaseMessaging.getToken`)
     * @param gatewayUrl sygnal's address, as the homeserver sees it
     */
    suspend fun registerPushFcm(
        jeton: String,
        gatewayUrl: String,
        appId: String,
        deviceName: String,
    )

    /**
     * call it on logout: an orphaned pusher keeps notifying a device that no longer reads them,
     * indefinitely and without saying so
     */
    suspend fun unregisterPush(endpoint: String, appId: String)
}

/**
 * what to do with a push, deliberately not a `Notification?`: "nothing to show" covers three
 * situations wanting different follow ups. a filtered event must show nothing, a missing one is an
 * anomaly worth reporting, and a single null would make the bug look like normal operation
 */
sealed interface NotificationResult {

    data class Show(val notification: Notification) : NotificationResult

    /** the push rules say show nothing. normal behaviour, not a failure */
    data object Filtered : NotificationResult

    /** the message was deleted before we could show it */
    data object Redacted : NotificationResult

    /** not found server side. an anomaly: the push announced an event that does not exist */
    data object NotFound : NotificationResult
}

/**
 * a notification ready to show
 *
 * @param body already decrypted where possible, and
 *   [li.velvetchat.core.model.MessageBody.Undecryptable] otherwise. that is the intended
 *   fallback: "julie, #general" with no text is still actionable, "new message" is not
 * @param isMention a direct mention, which is what separates a badge from a vibration
 * @param isNoisy the push rules asked for a sound
 */
data class Notification(
    val channelId: String,
    val channelName: String,
    val eventId: String,
    val sender: String,
    val senderDisplayName: String?,
    val senderAvatarUrl: String?,
    val body: MessageBody,
    val timestamp: Long,
    val isDirect: Boolean,
    val isMention: Boolean,
    val isNoisy: Boolean,
) {
    /** what goes in the title: the readable name, the id only as a last resort */
    val displayName: String get() = senderDisplayName ?: sender
}

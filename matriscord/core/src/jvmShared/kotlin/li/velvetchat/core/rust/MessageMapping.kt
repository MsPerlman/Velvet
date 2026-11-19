package li.velvetchat.core.rust

import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.MessageType
import li.velvetchat.core.model.MediaKind
import li.velvetchat.core.model.MediaRef
import li.velvetchat.core.model.MessageBody

// translating the sdk's message contents into the product model
//
// it lives here, outside `RustMatrixEngine`, because two paths use it: the timeline and the
// notifications. notifications do not go through the timeline, the sdk hands back a bare
// `MessageType` there, and two separate mappings would drift, until a media ended up drawn one way
// in the conversation and another in a notification
//
// file level functions and not methods, since nothing here depends on the engine's state


/**
 * turns an sdk message type into a displayable body
 *
 * @param fallback the text to fall back on for unmodelled types. the timeline has one, a
 *   notification does not always
 */
internal fun MessageType.toBody(fallback: String): MessageBody = when (val type = this) {
    is MessageType.Text -> MessageBody.Text(
        text = type.content.body,
        html = type.content.formatted?.body,
    )
    is MessageType.Notice -> MessageBody.Text(text = type.content.body)
    is MessageType.Emote -> MessageBody.Text(text = type.content.body)
    is MessageType.Image -> with(type.content) {
        media(
            MediaKind.IMAGE, caption, filename, source,
            mimeType = info?.mimetype,
            size = info?.size,
            width = info?.width,
            height = info?.height,
            blurhash = info?.blurhash,
            thumbnail = info?.thumbnailSource,
        )
    }
    is MessageType.Video -> with(type.content) {
        media(
            MediaKind.VIDEO, caption, filename, source,
            mimeType = info?.mimetype,
            size = info?.size,
            width = info?.width,
            height = info?.height,
            durationMs = info?.duration?.toMillis(),
            blurhash = info?.blurhash,
            thumbnail = info?.thumbnailSource,
        )
    }
    is MessageType.Audio -> with(type.content) {
        media(
            MediaKind.AUDIO, caption, filename, source,
            mimeType = info?.mimetype,
            size = info?.size,
            durationMs = info?.duration?.toMillis(),
        )
    }
    is MessageType.File -> with(type.content) {
        media(
            MediaKind.FILE, caption, filename, source,
            mimeType = info?.mimetype,
            size = info?.size,
            thumbnail = info?.thumbnailSource,
        )
    }
    else -> MessageBody.Unsupported(fallback)
}

/**
 * assembles a displayable media
 *
 * the handle is serialised here, while the timeline item is still alive. `MediaSource` is a native
 * object owned by that item, and keeping it as is inside an immutable model that crosses threads
 * and outlives the item would be a dangling reference waiting to happen. its json form travels
 * safely and rebuilds on demand
 */
internal fun media(
    kind: MediaKind,
    caption: String?,
    filename: String,
    source: MediaSource,
    mimeType: String? = null,
    size: ULong? = null,
    width: ULong? = null,
    height: ULong? = null,
    durationMs: Long? = null,
    blurhash: String? = null,
    thumbnail: MediaSource? = null,
) = MessageBody.Media(
    // an empty caption is not one. without it the filename shows, never a blank
    caption = caption?.takeIf { it.isNotBlank() } ?: filename,
    kind = kind,
    media = MediaRef(
        source = source.toJson(),
        filename = filename,
        mimeType = mimeType,
        sizeBytes = size?.toLong(),
        width = width?.toInt(),
        height = height?.toInt(),
        durationMs = durationMs,
        blurhash = blurhash,
        thumbnail = thumbnail?.toJson(),
    ),
)

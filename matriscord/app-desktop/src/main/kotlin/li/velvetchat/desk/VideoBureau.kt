package li.velvetchat.desk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import livekit.proto.Ffi
import livekit.proto.VideoFrame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * incoming call video, desktop side
 *
 * velvetdesk had none: on subscribing to a track, anything that was not audio was released on the
 * spot (`AppelBureau.surEvenement`, the `TRACK_SUBSCRIBED` branch). a camera or a screen share from
 * the phone therefore arrived nowhere, and there was not even a place to show it
 *
 * why it is so short
 *
 * the native core already does everything. `NewVideoStreamRequest` carries a `format` field: ask
 * for rgba and the frames arrive converted, with not one line of yuv arithmetic. with
 * `normalizeStride` every row is exactly `width * 4` bytes, so the buffer copies into a skia bitmap
 * in one block. writing the conversion by hand would have been longer, slower, and wrong on
 * resolutions whose width is not even
 *
 * a frame is copied, not kept
 *
 * `data_ptr` points into memory the native core owns and reuses on the next frame. we copy into a
 * bitmap of our own before returning, then release the buffer. keeping the pointer would give an
 * image that changes under compose's eyes, then a crash
 */
internal object VideoBureau {

    /** the most recent frames, by track id (`sid`) */
    private val dernieres = ConcurrentHashMap<String, ImageBitmap>()

    /** open streams: stream handle to sid, to know who a frame belongs to */
    private val flux = ConcurrentHashMap<Long, String>()

    /** the video tracks being followed: sid to track handle, to release at the end */
    private val pistes = ConcurrentHashMap<String, Long>()

    /**
     * changes on every frame received
     *
     * an image is not observable state for compose, it is an object replaced in a map. this counter
     * is what the view observes, and it then rereads [image] and redraws. a `mutableStateMapOf`
     * would do the same work with one recomposition per track, which buys nothing while one video
     * shows at a time
     */
    var horloge by androidx.compose.runtime.mutableStateOf(0L)
        private set

    fun image(sid: String): ImageBitmap? = dernieres[sid]

    /** the video tracks we have received at least one frame from */
    fun pistesVisibles(): List<String> = dernieres.keys.toList()

    /**
     * opens a stream on a video track we just received
     *
     * the track's handle is kept: releasing it would cut the stream we just opened
     */
    fun suivre(sid: String, handlePiste: Long) {
        if (pistes.putIfAbsent(sid, handlePiste) != null) return
        runCatching {
            val reponse = LiveKitFfi.requete(
                Ffi.FfiRequest.newBuilder().setNewVideoStream(
                    VideoFrame.NewVideoStreamRequest.newBuilder()
                        .setTrackHandle(handlePiste)
                        .setType(VideoFrame.VideoStreamType.VIDEO_STREAM_NATIVE)
                        // converted by the native core, and every row at the exact width
                        .setFormat(VideoFrame.VideoBufferType.RGBA)
                        .setNormalizeStride(true),
                ).build(),
            ).newVideoStream
            flux[reponse.stream.handle.id] = sid
        }.onFailure { pistes.remove(sid) }
    }

    /**
     * the id of your own image, the one you are sending
     *
     * a received track is as good as another for display: giving it a `sid` puts it in the same map
     * and the same stage, with no second path to keep up to date
     */
    const val APERCU_LOCAL = "apercu-local"

    /** the sharing drops what it just sent here, see `PartageEcranBureau` */
    fun poserApercuLocal(octets: ByteArray, largeur: Int, hauteur: Int) {
        val bitmap = Bitmap()
        val decrit = ImageInfo(
            width = largeur,
            height = hauteur,
            colorType = ColorType.BGRA_8888,
            alphaType = ColorAlphaType.OPAQUE,
        )
        if (!bitmap.installPixels(decrit, octets, largeur * 4)) return
        bitmap.setImmutable()
        dernieres[APERCU_LOCAL] = org.jetbrains.skia.Image.makeFromBitmap(bitmap)
            .toComposeImageBitmap()
        horloge++
    }

    fun retirerApercuLocal() {
        dernieres.remove(APERCU_LOCAL)
        horloge++
    }

    /** closes a track's stream and forgets its image */
    fun oublier(sid: String) {
        flux.entries.firstOrNull { it.value == sid }?.let { (handle, _) ->
            flux.remove(handle)
            LiveKitFfi.liberer(handle)
        }
        pistes.remove(sid)?.let { LiveKitFfi.liberer(it) }
        dernieres.remove(sid)
        horloge++
    }

    fun toutOublier() {
        flux.keys.toList().forEach { LiveKitFfi.liberer(it) }
        pistes.values.forEach { LiveKitFfi.liberer(it) }
        flux.clear()
        pistes.clear()
        dernieres.clear()
        horloge++
    }

    /** to be wired onto the native core's events, and it ignores anything that is not a frame */
    fun surEvenement(e: Ffi.FfiEvent) {
        if (e.messageCase != Ffi.FfiEvent.MessageCase.VIDEO_STREAM_EVENT) return
        val evenement = e.videoStreamEvent
        val sid = flux[evenement.streamHandle] ?: return
        when (evenement.messageCase) {
            VideoFrame.VideoStreamEvent.MessageCase.FRAME_RECEIVED -> {
                val tampon = evenement.frameReceived.buffer
                runCatching { versImage(tampon.info) }.getOrNull()?.let {
                    dernieres[sid] = it
                    horloge++
                }
                // always, failures included: the buffer is ours now
                LiveKitFfi.liberer(tampon.handle.id)
            }
            VideoFrame.VideoStreamEvent.MessageCase.EOS -> oublier(sid)
            else -> Unit
        }
    }

    /**
     * the native buffer copied into a bitmap
     *
     * `installPixels` copies, which is what we want, the original memory gets rewritten on the next
     * frame
     */
    private fun versImage(info: VideoFrame.VideoBufferInfo): ImageBitmap? {
        if (info.width <= 0 || info.height <= 0 || info.dataPtr == 0L) return null
        val octets = com.sun.jna.Pointer(info.dataPtr)
            .getByteArray(0, info.width * info.height * 4)
        val bitmap = Bitmap()
        val decrit = ImageInfo(
            width = info.width,
            height = info.height,
            colorType = ColorType.RGBA_8888,
            alphaType = ColorAlphaType.OPAQUE,
        )
        if (!bitmap.installPixels(decrit, octets, info.width * 4)) return null
        bitmap.setImmutable()
        // a skia `Image`, immutable, which is what compose knows how to present
        return org.jetbrains.skia.Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    }

}

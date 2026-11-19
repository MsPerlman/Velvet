package li.velvetchat.desk

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import livekit.proto.Ffi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * livekit's native core, `livekit_ffi.dll`, the same one the python, node and unity sdks use
 *
 * there is no livekit sdk for the jvm, and the voice plan expected the rust sdk exposed through
 * uniffi. this core is that rust sdk, already exposed: four c functions, and everything else goes
 * over protobuf (a synchronous `FfiRequest` to `FfiResponse`, then asynchronous `FfiEvent`s through
 * the callback)
 *
 * asynchronous requests carry a `request_async_id` chosen here: the wait is registered before the
 * send, so a callback cannot arrive before anybody is waiting for it
 */
internal object LiveKitFfi {
    private interface Bibliotheque : Library {
        fun livekit_ffi_initialize(rappel: Rappel, captureLogs: Boolean, sdk: String, version: String)
        fun livekit_ffi_request(donnees: ByteArray, taille: Long, reponse: PointerByReference, tailleReponse: LongByReference): Long
        fun livekit_ffi_drop_handle(handle: Long): Boolean
        fun livekit_ffi_dispose()
    }

    fun interface Rappel : Callback {
        fun invoke(donnees: Pointer, taille: Long)
    }

    private val lib: Bibliotheque by lazy {
        Native.load("livekit_ffi", Bibliotheque::class.java).also {
            it.livekit_ffi_initialize(rappel, false, "velvet-desk", "1.0")
        }
    }

    // a strong reference: a jna callback collected by the gc crashes the process on the first event
    private val rappel = Rappel { donnees, taille ->
        val evenement = runCatching { Ffi.FfiEvent.parseFrom(donnees.getByteArray(0, taille.toInt())) }.getOrNull()
            ?: return@Rappel
        if (evenement.messageCase == Ffi.FfiEvent.MessageCase.PANIC) {
            System.err.println("[LiveKit] panique : ${evenement.panic.message}")
        }
        idAsync(evenement)?.let { id -> attentes.remove(id)?.complete(evenement); return@Rappel }
        ecouteurs.forEach { runCatching { it(evenement) } }
    }

    private val compteur = AtomicLong(1)
    private val attentes = ConcurrentHashMap<Long, CompletableDeferred<Ffi.FfiEvent>>()
    private val ecouteurs = java.util.concurrent.CopyOnWriteArrayList<(Ffi.FfiEvent) -> Unit>()

    fun ecouter(ecouteur: (Ffi.FfiEvent) -> Unit): () -> Unit {
        ecouteurs += ecouteur
        return { ecouteurs -= ecouteur }
    }

    fun nouvelId(): Long = compteur.getAndIncrement()

    fun requete(requete: Ffi.FfiRequest): Ffi.FfiResponse {
        val octets = requete.toByteArray()
        val pointeur = PointerByReference()
        val taille = LongByReference()
        val handle = lib.livekit_ffi_request(octets, octets.size.toLong(), pointeur, taille)
        try {
            return Ffi.FfiResponse.parseFrom(pointeur.value.getByteArray(0, taille.value.toInt()))
        } finally {
            lib.livekit_ffi_drop_handle(handle)
        }
    }

    /** sends an asynchronous request numbered [id] and waits for its callback */
    suspend fun attendre(id: Long, delaiMs: Long = 30_000, envoi: () -> Ffi.FfiRequest): Ffi.FfiEvent {
        val attente = CompletableDeferred<Ffi.FfiEvent>()
        attentes[id] = attente
        try {
            requete(envoi())
            return withTimeout(delaiMs) { attente.await() }
        } finally {
            attentes.remove(id)
        }
    }

    fun liberer(handle: Long) {
        if (handle != 0L) runCatching { lib.livekit_ffi_drop_handle(handle) }
    }

    private fun idAsync(e: Ffi.FfiEvent): Long? = when (e.messageCase) {
        Ffi.FfiEvent.MessageCase.CONNECT -> e.connect.asyncId
        Ffi.FfiEvent.MessageCase.DISCONNECT -> e.disconnect.asyncId
        Ffi.FfiEvent.MessageCase.PUBLISH_TRACK -> e.publishTrack.asyncId
        Ffi.FfiEvent.MessageCase.UNPUBLISH_TRACK -> e.unpublishTrack.asyncId
        else -> null
    }
}

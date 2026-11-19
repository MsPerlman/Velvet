package li.velvetchat.desk

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * a windows named pipe, served instead of merely visited
 *
 * ## why jna, or: the jvm can read the diary but not write in it
 *
 * java will happily **open** a named pipe — `\\.\pipe\whatever` is just a file if you squint.
 * creating one? no api, never has been, probably never will be. so it is `CreateNamedPipeW` or
 * it is nothing, and we are back in win32 land with the rest of the window code. at least we
 * already own a shovel
 *
 * ## a crowd, and somebody else's door
 *
 * clients count up: `discord-ipc-0`, then `-1`, all the way to `-9`, first one that answers.
 * holding zero is therefore the whole game. each instance of a pipe serves exactly one client
 * from start to finish, so serving a crowd means keeping several instances open at once — which
 * is what discord does and what we now do, after an evening spent discovering the alternative
 *
 * this object also knows how to be a **client**, which is not symmetry for its own sake: taking
 * zero means discord does not have it, and relaying to discord means knocking on its door like
 * everybody else
 */
internal object TuyauIpc {

    private const val PIPE_ACCESS_DUPLEX = 0x00000003

    /**
     * refuse to become the second instance of somebody else's pipe
     *
     * this cost an evening. `CreateNamedPipeW` on `discord-ipc-0` **succeeds** while discord is
     * running, because windows lets another process add an instance to an existing pipe name and
     * discord's pipe is permissive enough to allow it. so without this flag we did not take a
     * free index at all — we silently joined discord's queue, and incoming clients were handed
     * whichever instance happened to be free. measured by connecting six times: five went to
     * discord, one to us
     *
     * which is worse than it sounds. it is not that we saw one game in six, it is that we were
     * **stealing** one connection in six from discord, so a game launched at the wrong moment
     * would tell its story to us and discord would show nothing. breaking somebody's actual
     * discord presence to half-populate ours is not a trade anybody asked for
     *
     * with the flag, creation fails cleanly when the name is taken and the loop moves to the
     * next index, which is what it always looked like it was doing
     */
    private const val FILE_FLAG_FIRST_PIPE_INSTANCE = 0x00080000
    private const val PIPE_TYPE_BYTE = 0x00000000
    private const val PIPE_READMODE_BYTE = 0x00000000
    private const val PIPE_WAIT = 0x00000000
    private const val PIPE_UNLIMITED_INSTANCES = 255
    private const val ERROR_PIPE_CONNECTED = 535
    private const val ERROR_BROKEN_PIPE = 109
    private val INVALIDE = Pointer.createConstant(-1L)

    private const val GENERIC_READ = -0x80000000
    private const val GENERIC_WRITE = 0x40000000
    private const val OPEN_EXISTING = 3
    private const val FILE_ATTRIBUTE_NORMAL = 0x80

    private interface Kernel32 : StdCallLibrary {
        fun CreateNamedPipeW(
            nom: com.sun.jna.WString,
            ouverture: Int,
            tuyau: Int,
            instances: Int,
            tamponSortie: Int,
            tamponEntree: Int,
            delai: Int,
            securite: Pointer?,
        ): Pointer

        fun ConnectNamedPipe(tuyau: Pointer, chevauchement: Pointer?): Boolean
        fun DisconnectNamedPipe(tuyau: Pointer): Boolean
        fun ReadFile(
            fichier: Pointer,
            tampon: ByteArray,
            aLire: Int,
            lus: IntArray,
            chevauchement: Pointer?,
        ): Boolean

        fun WriteFile(
            fichier: Pointer,
            tampon: ByteArray,
            aEcrire: Int,
            ecrits: IntArray,
            chevauchement: Pointer?,
        ): Boolean

        fun CreateFileW(
            nom: com.sun.jna.WString,
            acces: Int,
            partage: Int,
            securite: Pointer?,
            creation: Int,
            drapeaux: Int,
            modele: Pointer?,
        ): Pointer

        fun FlushFileBuffers(fichier: Pointer): Boolean
        fun PeekNamedPipe(
            tuyau: Pointer,
            tampon: ByteArray?,
            taille: Int,
            lus: IntArray?,
            disponibles: IntArray?,
            restants: IntArray?,
        ): Boolean

        fun CloseHandle(objet: Pointer): Boolean
        fun GetLastError(): Int
    }

    private val k32: Kernel32? by lazy {
        runCatching { Native.load("kernel32", Kernel32::class.java) }.getOrNull()
    }

    val disponible: Boolean get() = k32 != null

    /**
     * a served pipe, open and waiting
     *
     * closing it is the only way out of a blocking read, which is how the server is stopped
     */
    class Canal(private val poignee: Pointer, private val serveur: Boolean = true) :
        AutoCloseable {
        private val lib = k32 ?: error("kernel32 absent")

        /**
         * closed already, so do not do it again
         *
         * this cost an evening, and it is the oldest bug in the book. the relay closes a channel
         * from **two** threads: the one pumping each way, whichever notices first. without this
         * the second `CloseHandle` runs on a handle windows has already taken back and quite
         * possibly **handed to somebody else** — in our case the listening instance created two
         * lines later, which then quietly died. the symptom was a server that answered the first
         * client and then accepted connections forever without ever replying, which is a
         * wonderful way to spend an hour looking at the protocol code instead
         */
        private val ferme = java.util.concurrent.atomic.AtomicBoolean(false)

        /**
         * waits for somebody to knock. false means the pipe was closed under us, which is our
         * only way of getting out of here — see [arreter], it is not elegant but it is the api
         */
        fun attendreClient(): Boolean {
            if (lib.ConnectNamedPipe(poignee, null)) return true
            // a client that got in during the half microsecond between `CreateNamedPipe` and
            // this line makes the call *fail*, with a code that means "actually it worked". win32
            // is like that sometimes. the connection is fine, take it
            return lib.GetLastError() == ERROR_PIPE_CONNECTED
        }

        fun liberer() {
            runCatching { lib.DisconnectNamedPipe(poignee) }
        }

        /**
         * reads exactly [combien] bytes or dies trying
         *
         * exact, not "up to". the whole protocol is headers announcing lengths, so one short
         * read and every frame after it is garbage — you end up parsing somebody's game title
         * as an opcode and wondering why nothing works
         */
        fun lireExact(combien: Int): ByteArray {
            val sortie = ByteArray(combien)
            var place = 0
            val morceau = ByteArray(combien)
            while (place < combien) {
                val lus = IntArray(1)
                if (!lib.ReadFile(poignee, morceau, combien - place, lus, null)) {
                    throw IOException("lecture du tuyau : erreur ${lib.GetLastError()}")
                }
                if (lus[0] <= 0) throw IOException("tuyau ferme")
                morceau.copyInto(sortie, place, 0, lus[0])
                place += lus[0]
            }
            return sortie
        }

        /**
         * how many bytes are waiting, without touching them
         *
         * this exists because of the single nastiest thing in this file. a pipe handle opened
         * **without** `FILE_FLAG_OVERLAPPED` is synchronous, and windows serialises every
         * operation on it: a thread parked in `ReadFile` holds the queue, and a `WriteFile`
         * issued from another thread waits behind it. forever, since the read is waiting for
         * something the write was supposed to cause
         *
         * which is exactly how the relay hung. the log said `discord -> client op=2 (43 o)` and
         * then nothing at all — not an error, not a close, nothing. the write was sitting in a
         * queue behind a read on the same handle
         *
         * the honest fix is overlapped i/o, with events and `GetOverlappedResult` and a good
         * afternoon. the cheap one is this: never block on a read, ask first. one thread does
         * both directions, nothing is ever concurrent on a handle, and the price is a poll
         *
         * @return the bytes available, or -1 when the handle is gone, which is how a hang up
         *   gets noticed without blocking on anything
         */
        fun enAttente(): Int {
            val disponibles = IntArray(1)
            if (!lib.PeekNamedPipe(poignee, null, 0, null, disponibles, null)) return -1
            return disponibles[0]
        }

        fun ecrire(octets: ByteArray) {
            val ecrits = IntArray(1)
            if (!lib.WriteFile(poignee, octets, octets.size, ecrits, null)) {
                val erreur = lib.GetLastError()
                if (erreur == ERROR_BROKEN_PIPE) throw IOException("tuyau ferme")
                throw IOException("ecriture du tuyau : erreur $erreur")
            }
        }

        override fun close() {
            if (!ferme.compareAndSet(false, true)) return
            if (serveur) {
                runCatching { lib.FlushFileBuffers(poignee) }
                runCatching { lib.DisconnectNamedPipe(poignee) }
            }
            runCatching { lib.CloseHandle(poignee) }
        }
    }

    /**
     * grabs `\\.\pipe\discord-ipc-<n>`, first come first served
     *
     * the order is the whole trick: clients count up from zero, so holding zero means games find
     * us before anyone else. if the real discord is running it already owns zero and we settle
     * for one, which works fine — whoever a game talks to is just whichever door it knocked on
     * first. yes, this means velvet and discord can both be showing you playing the same game.
     * we are not going to fight about it
     *
     * @return the channel and which number we ended up with, or null if all ten are taken, in
     *   which case something on this machine has a pipe hoarding problem
     */
    fun ouvrir(): Pair<Canal, Int>? {
        for (n in 0..9) creer(n, premiere = true)?.let { return it to n }
        return null
    }

    /**
     * another instance of a pipe we already own
     *
     * a pipe instance serves exactly one client, start to finish. one instance therefore means
     * one game at a time, and the second one knocks on a door that is already busy — it gets
     * `ERROR_PIPE_BUSY`, tries the next index, finds nothing, and gives up. which is how you end
     * up with "my game shows up unless modrinth is open", a bug report nobody would enjoy
     *
     * discord solves this by keeping several instances listening and so do we: hand the accepted
     * one to a thread, open the next one immediately, go back to waiting. without
     * [FILE_FLAG_FIRST_PIPE_INSTANCE] this time, obviously — the whole point is that the name
     * already exists and that it is **ours**
     */
    fun instanceDePlus(n: Int): Canal? = creer(n, premiere = false)

    /**
     * knocks on `discord-ipc-<n>` as an ordinary client
     *
     * @return null when nobody is home, or when somebody is home and already busy
     */
    fun joindre(n: Int): Canal? {
        val lib = k32 ?: return null
        val poignee = runCatching {
            lib.CreateFileW(
                com.sun.jna.WString("""\\.\pipe\discord-ipc-$n"""),
                GENERIC_READ or GENERIC_WRITE,
                0,
                null,
                OPEN_EXISTING,
                FILE_ATTRIBUTE_NORMAL,
                null,
            )
        }.getOrNull() ?: return null
        if (poignee == INVALIDE || poignee == Pointer.NULL) return null
        return Canal(poignee, serveur = false)
    }

    /**
     * finds the real discord, if it is around
     *
     * every index except our own, in order, first one that opens. we cannot tell discord from
     * anything else listening on that name, and we do not try: whatever is there is exactly what
     * the client would have reached if we had not taken the door in front of it
     *
     * called **per connection** rather than once at startup, because discord is perfectly
     * entitled to be launched after us, and usually is
     */
    fun trouverDiscord(sauf: Int): Pair<Canal, Int>? {
        for (n in 0..9) {
            if (n == sauf) continue
            joindre(n)?.let { return it to n }
        }
        return null
    }

    private fun creer(n: Int, premiere: Boolean): Canal? {
        val lib = k32 ?: return null
        val mode = if (premiere) PIPE_ACCESS_DUPLEX or FILE_FLAG_FIRST_PIPE_INSTANCE
        else PIPE_ACCESS_DUPLEX
        val poignee = runCatching {
            lib.CreateNamedPipeW(
                com.sun.jna.WString("""\\.\pipe\discord-ipc-$n"""),
                mode,
                PIPE_TYPE_BYTE or PIPE_READMODE_BYTE or PIPE_WAIT,
                PIPE_UNLIMITED_INSTANCES,
                64 * 1024,
                64 * 1024,
                0,
                null,
            )
        }.getOrNull() ?: return null
        if (poignee == INVALIDE || poignee == Pointer.NULL) return null
        return Canal(poignee)
    }

    /** a frame: the opcode, then the payload's length, then the payload. both little endian */
    data class Trame(val op: Int, val charge: String)

    fun lireTrame(canal: Canal): Trame {
        val entete = ByteBuffer.wrap(canal.lireExact(8)).order(ByteOrder.LITTLE_ENDIAN)
        val op = entete.int
        val taille = entete.int
        // never trust a length somebody else wrote. sixty four kilobytes is already absurdly
        // generous for "I am playing Celeste", and a client claiming two gigabytes would get
        // two gigabytes, which is one way to find out how much ram you have
        if (taille < 0 || taille > 64 * 1024) throw IOException("trame de $taille octets refusee")
        val charge = if (taille == 0) "" else String(canal.lireExact(taille), Charsets.UTF_8)
        return Trame(op, charge)
    }

    fun ecrireTrame(canal: Canal, op: Int, charge: String) {
        val utile = charge.toByteArray(Charsets.UTF_8)
        val tampon = ByteBuffer.allocate(8 + utile.size).order(ByteOrder.LITTLE_ENDIAN)
        tampon.putInt(op)
        tampon.putInt(utile.size)
        tampon.put(utile)
        canal.ecrire(tampon.array())
    }

    const val OP_HANDSHAKE = 0
    const val OP_TRAME = 1
    const val OP_FERMETURE = 2
    const val OP_PING = 3
    const val OP_PONG = 4
}

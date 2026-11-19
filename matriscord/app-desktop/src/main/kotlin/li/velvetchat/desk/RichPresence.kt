package li.velvetchat.desk

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import li.velvetchat.core.model.Activite

/**
 * the local rich presence server, in which we put on a discord costume and nobody notices
 *
 * ## the trick, and it is barely a trick
 *
 * matrix has no idea what an activity is, so there is nothing to subscribe to and the source has
 * to be local. meanwhile every game on earth already knows exactly one way to say what it is
 * doing: discord's local ipc. so we speak that. no integration to write, no game to convince,
 * no api key to beg for — the games were already shouting into a pipe, we just stood where the
 * pipe was
 *
 * everything that is **not** a game comes in by the other door. PreMiD, which is what puts
 * youtube and netflix on a profile, used to ship a desktop app that spoke this pipe — and an
 * earlier version of this comment said so with great confidence. it does not any more: since
 * 2026 it is a browser extension and nothing else, and a browser extension cannot open a named
 * pipe, has never been able to, and never will be
 *
 * so it reaches discord the only way a web page can, over a websocket on 6463-6472, which is
 * exactly what [ServeurWs] is. the open question on the list — "does PreMiD feed a third party
 * rpc server?" — still has the same answer, "it does not know what it feeds, which is why this
 * works". it just arrives through the other transport
 *
 * modelled on [arRPC](https://github.com/OpenAsar/arrpc), MIT, who got there first and for the
 * same reasons. nothing copied, a protocol is a protocol
 *
 * ## what we flatly refuse to do
 *
 * **anything that is not the activity.** discord's ipc also carries authorisation, voice
 * settings, channel subscriptions, and a long tail of things we have no business touching. a
 * game that asks gets a polite error and goes on with its life. answering them would mean
 * *pretending to be discord*, which is a different and much worse project
 *
 * **nothing leaves this machine from here.** all this does is shove a value into a flow. whether
 * it ever reaches the server is [PresencePubliee]'s problem, and so is the rate limiting
 */
internal object RichPresence {

    private val etat = MutableStateFlow<Activite?>(null)

    /** what the local clients are currently saying, null when nobody is saying anything */
    val activite: StateFlow<Activite?> = etat

    @Volatile
    private var fil: Thread? = null

    /** the instance currently parked in `ConnectNamedPipe`, waiting for somebody to knock */
    @Volatile
    private var ecoute: TuyauIpc.Canal? = null

    /** the connections being served, kept only so [arreter] can slam all of them at once */
    private val servis = ConcurrentHashMap.newKeySet<TuyauIpc.Canal>()

    /**
     * which connection set the activity on display, and a source of connection numbers
     *
     * with several clients at once somebody has to own the thing. last writer wins, and only the
     * owner may take it back — otherwise a game closing quietly in the background would wipe the
     * one you are actually looking at, which is a bug you would never manage to reproduce on
     * purpose
     */
    private val proprietaire = AtomicLong(0)
    private val compteur = AtomicLong(0)

    /**
     * which door a client came in by, which decides whether its `pid` means anything
     *
     * a game on the named pipe **is** the thing it is describing, so asking windows what that
     * process is called gives you the game's name. a websocket client is a web page, and the
     * only process behind it is the browser — so resolving its pid would put "chrome" on your
     * profile, which is true and completely useless
     */
    enum class Transport { TUYAU, SOCKET }

    /** a number for a new connection, whichever transport brought it in */
    internal fun nouvelleConnexion(): Long = compteur.incrementAndGet()

    /**
     * a connection is over
     *
     * only the owner may take the activity down with it, see [proprietaire]
     */
    internal fun fermerConnexion(id: Long) {
        if (proprietaire.compareAndSet(id, 0)) etat.value = null
    }

    /** the pipe index we took, for the log and for nothing else */
    @Volatile
    var indexTuyau: Int? = null
        private set

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * starts serving, if the platform allows it
     *
     * idempotent: a second call on a running server does nothing. called from the shell's start,
     * so an app that gets reopened does not pile up threads
     */
    fun demarrer() {
        if (fil != null || !TuyauIpc.disponible) return
        val t = Thread({ boucle() }, "velvet-rpc")
        // daemon, non negotiable. this thread spends its entire life blocked on a read, and a
        // blocked non daemon thread is how you get an app that closes its window and then sits
        // in the task manager forever like a ghost with unfinished business
        t.isDaemon = true
        fil = t
        t.start()
    }

    fun arreter() {
        fil = null
        // slamming the handle shut is the only way to wake a thread parked in
        // `ConnectNamedPipe`. `Thread.interrupt` does precisely nothing to a win32 call, which
        // you learn by trying it and watching absolutely nothing happen
        runCatching { ecoute?.close() }
        ecoute = null
        servis.forEach { runCatching { it.close() } }
        servis.clear()
        proprietaire.set(0)
        etat.value = null
    }

    /**
     * takes a pipe index, then spends its life opening doors
     *
     * the index is chosen **once**. the old version called [TuyauIpc.ouvrir] again after every
     * disconnect, which looked harmless and meant the server could wander to a different number
     * between two games
     *
     * each accepted connection goes to its own thread and the next instance opens immediately,
     * so a second client never finds the door busy. the listening instance is the one [arreter]
     * closes to wake this thread up
     */
    private fun boucle() {
        val depart = TuyauIpc.ouvrir() ?: run {
            // all ten gone. discord plus nine friends, or more likely something crashed and took
            // its pipe to the grave. complain once, give up, go home
            grogner("rich presence", IllegalStateException("les dix tuyaux discord-ipc sont pris"))
            return
        }
        val n = depart.second
        indexTuyau = n
        murmurer("rpc: on tient discord-ipc-$n")
        var canal: TuyauIpc.Canal? = depart.first

        while (fil != null) {
            val courant = canal ?: break
            ecoute = courant
            val accepte = runCatching { courant.attendreClient() }.getOrDefault(false)
            // `arreter` wakes us by closing the handle, so the first thing to check on the way
            // out of the wait is whether we are still wanted
            if (fil == null) {
                runCatching { courant.close() }
                break
            }
            if (accepte) {
                servis += courant
                val id = compteur.incrementAndGet()
                val t = Thread({ servirPuisRanger(courant, id) }, "velvet-rpc-$id")
                t.isDaemon = true
                t.start()
            } else {
                runCatching { courant.close() }
            }
            canal = TuyauIpc.instanceDePlus(n)
            if (canal == null) murmurer("rpc: plus moyen d ouvrir une instance de discord-ipc-$n")
        }
        murmurer("rpc: la boucle d acceptation s arrete")
        ecoute = null
    }

    /**
     * one connection, from its handshake to its last breath
     *
     * two possible lives. if the real discord is running somewhere behind us we become a wire
     * and forward everything, reading as it goes past. if it is not, we answer ourselves
     *
     * the lookup happens **here**, once per connection, and not at startup: discord is entirely
     * entitled to be launched after velvet, and in this house it usually is
     */
    private fun servirPuisRanger(canal: TuyauIpc.Canal, id: Long) {
        try {
            // the first frame is read **before** deciding, and that order is not cosmetic:
            // discord drops a connection that stays silent after opening, with
            // `{"code":1006,"message":"Handshake timeout"}`. connecting upstream and only then
            // waiting for a client to make up its mind spends that patience on nothing. seen
            // for real against a client that took a few hundred milliseconds to say hello
            val premiere = TuyauIpc.lireTrame(canal)
            val derriere = indexTuyau?.let { TuyauIpc.trouverDiscord(sauf = it) }
            if (derriere != null) {
                murmurer("rpc: client $id relaye vers discord-ipc-${derriere.second}")
                relayer(canal, derriere.first, id, premiere)
            } else {
                murmurer("rpc: client $id servi par nous, discord est absent")
                servir(canal, id, premiere)
            }
        } catch (_: IOException) {
            // a client vanishing mid sentence is just how games say goodbye. not an incident,
            // barely even news
        } catch (t: Throwable) {
            grogner("rich presence", t)
        } finally {
            // the activity dies with the connection that set it, and only with that one. a
            // closed game is a game you are not playing, and leaving the last frame up there
            // would be a small permanent lie
            fermerConnexion(id)
            servis -= canal
            runCatching { canal.close() }
        }
    }

    /**
     * becomes a wire between a client and the real discord, and listens to what goes past
     *
     * ## why this exists
     *
     * only one process can hold `discord-ipc-0`, and games talk to whoever has it. so velvet
     * taking it meant discord got nothing: you traded your actual discord presence for ours,
     * which is not a trade anybody would make on purpose. the fix is not to fight over the door,
     * it is to answer it and pass everyone through
     *
     * velvet is **transparent** here. it does not answer the handshake, does not invent a user,
     * does not refuse the commands it does not implement — discord's own answers go back, so the
     * client gets a real `READY`, real authorisation, real everything. whatever worked before
     * works now, and we read the postcards on the way
     *
     * ## one thread each way, because the pipes block
     *
     * both handles are in `PIPE_WAIT`, so a read parks until something arrives. one thread
     * cannot wait on two of those, hence the second one. either side going quiet closes the
     * other, which is what gets both threads out of their wait — slamming the handle is still
     * the only language a blocked win32 call understands
     *
     * idk how discord would feel about this. it is its own protocol, spoken correctly, to its
     * own socket, by a program on the same machine as the user who owns both
     */
    private fun relayer(
        client: TuyauIpc.Canal,
        discord: TuyauIpc.Canal,
        id: Long,
        premiere: TuyauIpc.Trame,
    ) {
        // upstream first, before the reader thread exists: discord is counting
        TuyauIpc.ecrireTrame(discord, premiere.op, premiere.charge)
        try {
            while (fil != null) {
                // ask before reading, in both directions, from this one thread. see
                // `TuyauIpc.Canal.enAttente` for why there is not a thread per direction, and
                // for the hour it cost to find out
                val cote = client.enAttente()
                if (cote < 0) return
                if (cote > 0) {
                    val trame = TuyauIpc.lireTrame(client)
                    // we read, we do not reply. the ack is discord's to send, and sending our
                    // own would give the client two answers to one nonce
                    if (trame.op == TuyauIpc.OP_TRAME) {
                        traiterCharge(trame.charge, id, Transport.TUYAU, null)
                    }
                    TuyauIpc.ecrireTrame(discord, trame.op, trame.charge)
                }

                val dela = discord.enAttente()
                if (dela < 0) return
                if (dela > 0) {
                    val trame = TuyauIpc.lireTrame(discord)
                    TuyauIpc.ecrireTrame(client, trame.op, trame.charge)
                }

                // a few bytes present means a whole frame present, in practice: a frame goes out
                // in one `WriteFile`. if one ever did arrive in pieces, `lireTrame` blocks for
                // the rest of it, which costs the other direction a few microseconds and not
                // its life
                if (cote == 0 && dela == 0) Thread.sleep(PAUSE_POMPE_MS)
            }
        } finally {
            runCatching { discord.close() }
        }
    }

    /**
     * how long the relay waits when neither side is saying anything
     *
     * ten milliseconds. nobody can see it, presence changes a few times a minute at most, and
     * the alternative was an afternoon of overlapped i/o to shave off a delay that does not
     * exist from where anybody is sitting
     */
    private const val PAUSE_POMPE_MS = 10L

    /** serves one client until it goes away, discord being nowhere to be found */
    private fun servir(canal: TuyauIpc.Canal, id: Long, premiere: TuyauIpc.Trame) {
        var presente = false
        var trame = premiere
        while (fil != null) {
            when (trame.op) {
                TuyauIpc.OP_HANDSHAKE -> {
                    presente = true
                    TuyauIpc.ecrireTrame(canal, TuyauIpc.OP_TRAME, chargePret())
                }

                TuyauIpc.OP_PING ->
                    TuyauIpc.ecrireTrame(canal, TuyauIpc.OP_PONG, trame.charge)

                TuyauIpc.OP_FERMETURE -> return

                TuyauIpc.OP_TRAME -> {
                    // talking before the handshake. we could guess what they meant, but
                    // guessing is how you end up supporting six undocumented variants forever
                    if (!presente) {
                        TuyauIpc.ecrireTrame(canal, TuyauIpc.OP_FERMETURE, erreur("pas de poignee de main"))
                        return
                    }
                    traiterCharge(trame.charge, id, Transport.TUYAU) {
                        TuyauIpc.ecrireTrame(canal, TuyauIpc.OP_TRAME, it)
                    }
                }
            }
            trame = TuyauIpc.lireTrame(canal)
        }
    }

    /**
     * one json payload from a client, whatever pipe or socket it arrived through
     *
     * this is the whole protocol brain, and it deliberately knows nothing about transports: the
     * named pipe wraps it in an eight byte header, the websocket wraps it in a masked frame, and
     * neither difference reaches this far down
     *
     * [repondre] is null while relaying. the activity still gets read, discord still sends the
     * reply, and the client never learns there was anybody in the middle
     */
    internal fun traiterCharge(
        charge: String,
        id: Long,
        transport: Transport,
        repondre: ((String) -> Unit)?,
    ) {
        val objet = runCatching { json.parseToJsonElement(charge) as? JsonObject }.getOrNull()
            ?: return
        val cmd = (objet["cmd"] as? JsonPrimitive)?.contentOrNull
        val nonce = (objet["nonce"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        when (cmd) {
            "SET_ACTIVITY" -> {
                val args = objet["args"] as? JsonObject
                val pid = (args?.get("pid") as? JsonPrimitive)?.longOrNull
                val brute = args?.get("activity") as? JsonObject
                // no activity in the payload is how a well brought up game says "that's me
                // finished". so null clears it. the badly brought up ones just die, which the
                // `finally` above covers
                val lue = brute?.let { lireActivite(it, pid, transport) }
                etat.value = lue
                // whoever speaks last owns the screen, and a client clearing its own
                // activity hands ownership back rather than keeping it hostage
                if (lue != null) proprietaire.set(id) else proprietaire.compareAndSet(id, 0)
                repondre?.invoke(accuse(cmd, nonce))
            }

            // everything else gets a clear no. silence would leave the client waiting on a
            // nonce that is never coming, and a hung game is a much worse bug than a missing
            // feature nobody asked for
            else -> repondre?.invoke(refus(cmd.orEmpty(), nonce))
        }
    }

    /**
     * the activity behind a `SET_ACTIVITY`, named by asking windows who sent it
     *
     * the rule itself lives in [activiteDepuisDiscord], away from the pipe: it is the fiddly
     * part, it rests on two undocumented conventions, and it deserved tests that do not need a
     * named pipe to run
     */
    private fun lireActivite(a: JsonObject, pid: Long?, transport: Transport): Activite? {
        // the pid is only worth asking about when the sender is the thing it describes. over a
        // websocket it is a browser at best, a zero at worst, and either way naming the activity
        // after it would be wrong in a way nobody would think to question
        val processus = if (transport == Transport.TUYAU) pid?.let { nomDuProcessus(it) } else null
        return activiteDepuisDiscord(a, processus)
    }

    /**
     * the READY handshake, without which no client will say another word
     *
     * the shape matters, the contents mostly do not: clients check `evt` is `READY` and that
     * there is a `data.user` with a pulse, and some poke at `data.config`. we hand back a user
     * that says "velvet" in every field, because inventing a fake discord account would be both
     * dishonest and completely pointless — nothing checks it
     *
     * idk exactly which of these fields each client actually reads. this set is what makes
     * everything tried so far stop complaining, and trimming it is a fine way to spend an
     * afternoon finding out which one mattered
     */
    internal fun chargePret(): String = buildJsonObject {
        put("cmd", JsonPrimitive("DISPATCH"))
        put("evt", JsonPrimitive("READY"))
        put("data", buildJsonObject {
            put("v", JsonPrimitive(1))
            put("config", buildJsonObject {
                put("cdn_host", JsonPrimitive(""))
                put("api_endpoint", JsonPrimitive(""))
                put("environment", JsonPrimitive("production"))
            })
            put("user", buildJsonObject {
                put("id", JsonPrimitive("0"))
                put("username", JsonPrimitive("velvet"))
                put("discriminator", JsonPrimitive("0"))
                put("global_name", JsonPrimitive("Velvet"))
                put("avatar", JsonPrimitive(""))
                put("bot", JsonPrimitive(false))
                put("flags", JsonPrimitive(0))
                put("premium_type", JsonPrimitive(0))
            })
        })
    }.toString()

    private fun accuse(cmd: String, nonce: String) = buildJsonObject {
        put("cmd", JsonPrimitive(cmd))
        put("data", JsonPrimitive(null as String?))
        put("evt", JsonPrimitive(null as String?))
        put("nonce", JsonPrimitive(nonce))
    }.toString()

    private fun refus(cmd: String, nonce: String) = buildJsonObject {
        put("cmd", JsonPrimitive(cmd))
        put("evt", JsonPrimitive("ERROR"))
        put("data", buildJsonObject {
            put("code", JsonPrimitive(4002))
            put("message", JsonPrimitive("velvet ne sert que SET_ACTIVITY"))
        })
        put("nonce", JsonPrimitive(nonce))
    }.toString()

    private fun erreur(quoi: String) = buildJsonObject {
        put("code", JsonPrimitive(4000))
        put("message", JsonPrimitive(quoi))
    }.toString()

    private const val PROCESS_QUERY_LIMITED_INFORMATION = 0x1000

    private interface Kernel32Proc : StdCallLibrary {
        fun OpenProcess(acces: Int, heriter: Boolean, pid: Int): Pointer?
        fun QueryFullProcessImageNameW(
            processus: Pointer,
            drapeaux: Int,
            tampon: CharArray,
            taille: IntArray,
        ): Boolean

        fun CloseHandle(objet: Pointer): Boolean
    }

    private val k32proc: Kernel32Proc? by lazy {
        runCatching { Native.load("kernel32", Kernel32Proc::class.java) }.getOrNull()
    }

    /**
     * the executable behind a pid, stripped of its path and its `.exe`
     *
     * `PROCESS_QUERY_LIMITED_INFORMATION` rather than the full `QUERY_INFORMATION`, because the
     * limited one is enough to ask a name and is handed out for same user processes without any
     * elevation song and dance. asking for more would mean asking for a uac prompt to find out
     * that you are playing minecraft
     */
    private fun nomDuProcessus(pid: Long): String? {
        val lib = k32proc ?: return null
        val poignee = lib.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid.toInt())
            ?: return null
        try {
            val tampon = CharArray(1024)
            val taille = intArrayOf(tampon.size)
            if (!lib.QueryFullProcessImageNameW(poignee, 0, tampon, taille)) return null
            val chemin = String(tampon, 0, taille[0])
            return chemin.substringAfterLast('\\').substringBeforeLast('.')
                .takeIf { it.isNotBlank() }
        } finally {
            runCatching { lib.CloseHandle(poignee) }
        }
    }
}

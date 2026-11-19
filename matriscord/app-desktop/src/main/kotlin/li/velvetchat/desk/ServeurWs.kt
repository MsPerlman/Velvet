package li.velvetchat.desk

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * the same rich presence, through the other door discord leaves open
 *
 * ## why there are two doors at all
 *
 * a game links a native library and talks down a named pipe. a **web page** cannot open a named
 * pipe, has never been allowed to, and never will be — so discord also listens on plain tcp,
 * ports 6463 to 6472, speaking websocket. that is how a browser extension tells discord what you
 * are watching, and it is the only transport a page can reach
 *
 * same json, same commands, same nonces. the only difference is the wrapper: the pipe puts an
 * eight byte header in front of every payload, this puts a websocket frame around it. so
 * [RichPresence.traiterCharge] does the thinking for both and neither transport knows the other
 * exists
 *
 * ## what this is **not** for, and it was going to be
 *
 * this was written to catch PreMiD, which is what puts youtube and netflix on a profile. it will
 * not: PreMiD stopped talking to anything local. its extension authenticates with discord by
 * oauth, asks for `activities.write`, and sets the activity through
 * `discord.com/api/v10/users/@me/headless-sessions` — discord's own servers, over the internet,
 * with no discord client involved at all. read out of the installed extension, not guessed: the
 * only local socket left in it is a `DevWebSocket` on port 3021 for people writing presences
 *
 * so there is nothing to intercept, and the only way to see what somebody watches would go
 * through their discord account, which is a line this project does not cross
 *
 * what remains is still worth having: every local rpc client that is not PreMiD, and the door a
 * velvet browser extension would knock on the day somebody writes one — which is now the honest
 * route to "everything that is not a game"
 *
 * ## and it relays too, for the same reason the pipe does
 *
 * whoever binds 6463 first gets the browsers, exactly like the pipe. taking the port and keeping
 * quiet about it would break the thing we are trying to add to
 *
 * the relay here is a small joke at our own expense: a page talks websocket to us, and we hand
 * the whole conversation to discord **down the named pipe**, because it is the same protocol
 * underneath and writing a websocket client to talk to a program we are already connected to
 * would be work for the sake of symmetry. the one thing missing is the handshake — a websocket
 * client never sends one, its `client_id` rides in the query string — so we write it ourselves
 * and discord is none the wiser
 */
internal object ServeurWs {

    /** discord's range, and therefore the only range anything looks in */
    private const val PORT_MIN = 6463
    private const val PORT_MAX = 6472

    /** rfc 6455's magic string, which exists so that a proxy cannot fake an upgrade by accident */
    private const val MAGIE = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    /** same ceiling as the pipe. nobody describes a game in sixty four kilobytes */
    private const val MAX_CHARGE = 64 * 1024

    private const val TEXTE = 0x1
    private const val FERMETURE = 0x8
    private const val PING = 0x9
    private const val PONG = 0xA

    @Volatile
    private var accueil: ServerSocket? = null

    @Volatile
    private var fil: Thread? = null

    private val prises = ConcurrentHashMap.newKeySet<Socket>()

    /** the port we got, for the log and for nothing else */
    @Volatile
    var port: Int? = null
        private set

    fun demarrer() {
        if (fil != null) return
        // loopback only, and that is not paranoia: this accepts commands that change what the
        // whole house sees you doing. binding it to every interface would put it on the wifi
        val boucleLocale = runCatching { InetAddress.getByName("127.0.0.1") }.getOrNull() ?: return
        val serveur = (PORT_MIN..PORT_MAX).firstNotNullOfOrNull { p ->
            runCatching { ServerSocket(p, 16, boucleLocale) }.getOrNull()
        } ?: return
        accueil = serveur
        port = serveur.localPort
        murmurer("rpc ws: on ecoute sur 127.0.0.1:${serveur.localPort}")
        val t = Thread({ boucle(serveur) }, "velvet-rpc-ws")
        t.isDaemon = true
        fil = t
        t.start()
    }

    fun arreter() {
        fil = null
        // closing the listening socket is what wakes the thread parked in `accept`
        runCatching { accueil?.close() }
        accueil = null
        port = null
        prises.forEach { runCatching { it.close() } }
        prises.clear()
    }

    private fun boucle(serveur: ServerSocket) {
        while (fil != null) {
            val prise = runCatching { serveur.accept() }.getOrNull() ?: break
            prises += prise
            val t = Thread({
                try {
                    servir(prise)
                } catch (_: IOException) {
                    // a page navigating away is not an incident
                } catch (e: Throwable) {
                    grogner("rich presence ws", e)
                } finally {
                    prises -= prise
                    runCatching { prise.close() }
                }
            }, "velvet-rpc-ws-client")
            t.isDaemon = true
            t.start()
        }
    }

    private fun servir(prise: Socket) {
        // nagle would hold a twenty byte ack waiting for company that is never coming
        runCatching { prise.tcpNoDelay = true }
        val entree = prise.getInputStream().buffered()
        val sortie = prise.getOutputStream()

        val requete = lireRequete(entree)
        if (requete == null) return
        val cle = requete.entetes["sec-websocket-key"]
        if (cle == null) {
            ecrireTexteBrut(sortie, "HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n")
            return
        }
        ecrireTexteBrut(
            sortie,
            "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: ${accepter(cle)}\r\n\r\n",
        )

        val id = RichPresence.nouvelleConnexion()
        try {
            val derriere = TuyauIpc.trouverDiscord(sauf = RichPresence.indexTuyau ?: -1)
            if (derriere != null) {
                murmurer("rpc ws: client $id ponte vers discord-ipc-${derriere.second}")
                ponter(entree, sortie, derriere.first, requete.clientId, id)
            } else {
                murmurer("rpc ws: client $id servi par nous, discord est absent")
                seul(entree, sortie, id)
            }
        } finally {
            RichPresence.fermerConnexion(id)
        }
    }

    /** we are the only one here, so we answer */
    private fun seul(entree: InputStream, sortie: OutputStream, id: Long) {
        // a websocket client never sends a handshake frame — its `client_id` was in the url and
        // the server is expected to speak first. so READY goes out before anything comes in
        ecrireTrame(sortie, TEXTE, RichPresence.chargePret().toByteArray(Charsets.UTF_8))
        while (fil != null) {
            val trame = lireMessage(entree) ?: return
            when (trame.opcode) {
                TEXTE -> {
                    val charge = String(trame.charge, Charsets.UTF_8)
                    RichPresence.traiterCharge(charge, id, RichPresence.Transport.SOCKET) {
                        ecrireTrame(sortie, TEXTE, it.toByteArray(Charsets.UTF_8))
                    }
                }

                PING -> ecrireTrame(sortie, PONG, trame.charge)
                PONG -> Unit
                FERMETURE -> {
                    ecrireTrame(sortie, FERMETURE, ByteArray(0))
                    return
                }
            }
        }
    }

    /**
     * discord is behind us: we are a wire, and we read what goes past
     *
     * one thread for both directions, and that is not a style choice. the pipe handle is
     * synchronous, so a thread parked reading it would make a write from another thread queue
     * behind that read and never run — see `TuyauIpc.Canal.enAttente`, which is where that
     * lesson is written down in full. so: ask each side whether it has anything, take it,
     * hand it over, go round again
     */
    private fun ponter(
        entree: InputStream,
        sortie: OutputStream,
        discord: TuyauIpc.Canal,
        clientId: String?,
        id: Long,
    ) {
        // the handshake the websocket client never had to send. discord refuses to say a word
        // before it, and it starts counting the moment we connect, so it goes first
        val poignee = buildJsonObject {
            put("v", JsonPrimitive(1))
            put("client_id", JsonPrimitive(clientId.orEmpty()))
        }.toString()
        TuyauIpc.ecrireTrame(discord, TuyauIpc.OP_HANDSHAKE, poignee)

        try {
            while (fil != null) {
                var bouge = false

                // `available` on a buffered socket stream is a fair question: a frame arrives in
                // one packet, so one byte present means the rest is a microsecond behind
                if (entree.available() > 0) {
                    val trame = lireMessage(entree) ?: return
                    when (trame.opcode) {
                        TEXTE -> {
                            val charge = String(trame.charge, Charsets.UTF_8)
                            // read, do not reply: the answer is discord's, and two answers to
                            // one nonce is worse than none
                            RichPresence.traiterCharge(
                                charge,
                                id,
                                RichPresence.Transport.SOCKET,
                                null,
                            )
                            TuyauIpc.ecrireTrame(discord, TuyauIpc.OP_TRAME, charge)
                        }

                        PING -> ecrireTrame(sortie, PONG, trame.charge)
                        PONG -> Unit
                        FERMETURE -> return
                    }
                    bouge = true
                }

                val dela = discord.enAttente()
                if (dela < 0) return
                if (dela > 0) {
                    val trame = TuyauIpc.lireTrame(discord)
                    // the pipe's opcodes do not survive the trip: a websocket carries no
                    // handshake and no close frame of discord's own, only messages. everything
                    // discord says is json, and json is what the client is waiting for
                    ecrireTrame(sortie, TEXTE, trame.charge.toByteArray(Charsets.UTF_8))
                    bouge = true
                }

                if (!bouge) Thread.sleep(PAUSE_POMPE_MS)
            }
        } finally {
            runCatching { discord.close() }
        }
    }

    /** same ten milliseconds as the pipe relay, for the same reason: nobody can see it */
    private const val PAUSE_POMPE_MS = 10L

    private class Requete(val entetes: Map<String, String>, val clientId: String?)

    /**
     * the upgrade request, up to the blank line
     *
     * hand rolled rather than brought in: this parses exactly one request, from the loopback,
     * before switching to a protocol that is not http at all. a dependency for that would be a
     * dependency to keep updated forever
     */
    private fun lireRequete(entree: InputStream): Requete? {
        val brut = StringBuilder()
        // the ceiling matters. without it a client that never sends a blank line is a slow
        // memory leak with a socket attached
        while (brut.length < 8 * 1024) {
            val o = entree.read()
            if (o < 0) return null
            brut.append(o.toChar())
            if (brut.endsWith("\r\n\r\n")) break
        }
        val lignes = brut.toString().split("\r\n")
        val premiere = lignes.firstOrNull() ?: return null
        val entetes = lignes.drop(1).mapNotNull { ligne ->
            val i = ligne.indexOf(':')
            if (i <= 0) null else ligne.take(i).trim().lowercase() to ligne.drop(i + 1).trim()
        }.toMap()
        // `GET /?v=1&client_id=1234&encoding=json HTTP/1.1`
        val requise = premiere.split(' ').getOrNull(1).orEmpty()
        val clientId = requise.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("client_id=") }
            ?.removePrefix("client_id=")
            ?.takeIf { it.isNotBlank() }
        return Requete(entetes, clientId)
    }

    /** sha-1 of the key plus the magic string, in base64. rfc 6455 says so, and that is all */
    private fun accepter(cle: String): String {
        val empreinte = MessageDigest.getInstance("SHA-1")
            .digest((cle + MAGIE).toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(empreinte)
    }

    private fun ecrireTexteBrut(sortie: OutputStream, texte: String) {
        synchronized(sortie) {
            sortie.write(texte.toByteArray(Charsets.ISO_8859_1))
            sortie.flush()
        }
    }

    private class TrameWs(val opcode: Int, val charge: ByteArray)

    /**
     * one whole message, reassembled
     *
     * a sender may cut a message into pieces, and nothing says a small one will not be: the
     * choice belongs to whoever is writing, not to how big the thing is. a reader that assumes
     * one frame per message works perfectly until the day it does not
     */
    private fun lireMessage(entree: InputStream): TrameWs? {
        var opcode = -1
        val assemble = ByteArrayOutputStream()
        while (true) {
            val trame = lireTrame(entree) ?: return null
            // a control frame can arrive in the middle of a fragmented message, and must be
            // answered on its own rather than glued into it
            if (trame.opcode and 0x8 != 0) return TrameWs(trame.opcode, trame.charge)
            if (opcode == -1) opcode = trame.opcode
            assemble.write(trame.charge)
            if (trame.fin) return TrameWs(opcode, assemble.toByteArray())
            if (assemble.size() > MAX_CHARGE) throw IOException("message websocket trop gros")
        }
    }

    private class Brute(val fin: Boolean, val opcode: Int, val charge: ByteArray)

    private fun lireTrame(entree: InputStream): Brute? {
        val b0 = entree.read()
        if (b0 < 0) return null
        val b1 = entree.read()
        if (b1 < 0) return null
        val fin = b0 and 0x80 != 0
        val opcode = b0 and 0x0F
        val masque = b1 and 0x80 != 0
        var taille = (b1 and 0x7F).toLong()
        if (taille == 126L) {
            taille = ((octet(entree) shl 8) or octet(entree)).toLong()
        } else if (taille == 127L) {
            taille = 0
            repeat(8) { taille = (taille shl 8) or octet(entree).toLong() }
        }
        // never trust a length somebody else wrote, same rule as the pipe
        if (taille < 0 || taille > MAX_CHARGE) throw IOException("trame websocket de $taille octets")
        val cle = if (masque) ByteArray(4) { octet(entree).toByte() } else null
        val charge = ByteArray(taille.toInt())
        var place = 0
        while (place < charge.size) {
            val lus = entree.read(charge, place, charge.size - place)
            if (lus <= 0) return null
            place += lus
        }
        // a client must mask, a server must not. unmasking is a four byte xor, and the mask is
        // there to stop a hostile page fooling an old proxy into seeing a second http request
        cle?.let {
            for (i in charge.indices) charge[i] = (charge[i].toInt() xor it[i % 4].toInt()).toByte()
        }
        return Brute(fin, opcode, charge)
    }

    private fun octet(entree: InputStream): Int {
        val o = entree.read()
        if (o < 0) throw IOException("trame websocket coupee")
        return o
    }

    /**
     * writes one whole message, never fragmented
     *
     * synchronised because the relay has two threads with an opinion: the one pumping discord's
     * answers back, and the one answering a ping that arrived mid sentence. two interleaved
     * frames on one socket is not a protocol anybody can parse
     */
    private fun ecrireTrame(sortie: OutputStream, opcode: Int, charge: ByteArray) {
        val tete = ByteArrayOutputStream()
        tete.write(0x80 or opcode)
        when {
            charge.size < 126 -> tete.write(charge.size)
            charge.size <= 0xFFFF -> {
                tete.write(126)
                tete.write(charge.size shr 8 and 0xFF)
                tete.write(charge.size and 0xFF)
            }

            else -> {
                tete.write(127)
                for (i in 0 until 8) tete.write((charge.size.toLong() shr (56 - 8 * i)).toInt() and 0xFF)
            }
        }
        synchronized(sortie) {
            sortie.write(tete.toByteArray())
            sortie.write(charge)
            sortie.flush()
        }
    }
}

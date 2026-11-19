package li.velvetchat.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.velvetchat.core.model.LienInvitation
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * what the user's server says it offers, read when the session opens
 *
 * dictation, the discord import, the update channel, gifs, the cosmetics catalogue, invite
 * links, push and voice used to be baked into each build from a private config file. a build
 * without that file (the one on the site, or anybody's own) then had none of it, voice included.
 * they belong to the server and not to the app, so the server says them, in two places:
 *
 * - its `.well-known/matrix/client`, public: the standard `org.matrix.msc4143.rtc_foci` for voice,
 *   and a `li.velvetchat` block (synapse's `extra_well_known_client_content`). that block can carry
 *   every field below, but on a real server it only names a room: `{"salon": "#velvet-installation:example.org"}`
 * - that room, created with `m.federate: false` so only this server's accounts can join it, holds
 *   the rest in a `li.velvetchat.installation` state event, readable by members only (see
 *   [completer]). the gif key is a secret, which is why it lives there and not in public
 *
 * ```json
 * {
 *   "dictee": "wss://…/dictee", "import": "http://192.168.1.10:8181",
 *   "catalogue": "#velvet-cosmetiques:example.org", "intendant": "@admin:example.org",
 *   "invitations": "https://example.org/rejoindre#", "gifs": "<klipy key>",
 *   "push": "http://sygnal:5000/_matrix/push/v1/notify", "lan": "http://192.168.1.10:8008"
 * }
 * ```
 *
 * the room also carries the version announcements (`li.velvetchat.release`), which used to need a
 * whole space everybody had to sit in
 *
 * every field is optional. what the server does not say falls back on what the build was given,
 * and what neither says stays off, which is how a server without any of it behaves
 *
 * the last answer is kept in the session directory, so a phone that starts offline still knows
 * where its services are. it goes away with the session, on logout
 */
@Serializable
data class ConfigServeur(
    val vocal: String = "",
    val dictee: String = "",
    val import: String = "",
    val misesAJour: String = "",
    val catalogue: String = "",
    val intendant: String = "",
    val invitations: String = "",
    val gifs: String = "",
    val push: String = "",
    val lan: String = "",
    /** the room holding the members-only half, see [Installation.completer] */
    val salon: String = "",
)

object Installation {
    private val json = Json { ignoreUnknownKeys = true }
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val etat = MutableStateFlow(ConfigServeur())

    /** the current server's config. empty fields mean the server did not say */
    val config: StateFlow<ConfigServeur> = etat.asStateFlow()

    /** the server's value, otherwise the build's */
    fun ou(serveur: ConfigServeur.() -> String, build: String): String =
        etat.value.serveur().ifBlank { build }

    /**
     * reads the cached answer at once, then asks the server again
     *
     * @param attendre wait for the server's answer. true at login, where there is no cache yet
     *   and the app reads the update channel right after; false on restore, where the cache is
     *   good enough and an offline start must not hang
     */
    suspend fun decouvrir(dossier: String, userId: String, attendre: Boolean) {
        val fichier = File(dossier, "installation.json")
        runCatching { poser(json.decodeFromString(ConfigServeur.serializer(), fichier.readText())) }
        val travail = suspend {
            lire(userId.substringAfter(':'))?.let { publique ->
                val c = etat.value
                fun String.ou(a: String) = ifBlank { a }
                val neuve = publique.copy(
                    dictee = publique.dictee.ou(c.dictee), import = publique.import.ou(c.import),
                    misesAJour = publique.misesAJour.ou(c.misesAJour), catalogue = publique.catalogue.ou(c.catalogue),
                    intendant = publique.intendant.ou(c.intendant), invitations = publique.invitations.ou(c.invitations),
                    gifs = publique.gifs.ou(c.gifs), push = publique.push.ou(c.push), lan = publique.lan.ou(c.lan),
                )
                poser(neuve)
                runCatching { fichier.writeText(json.encodeToString(ConfigServeur.serializer(), neuve)) }
            }
        }
        if (attendre) withContext(Dispatchers.IO) { travail() } else portee.launch { travail() }
    }

    /** the state event type carrying the members-only half */
    const val TYPE_ETAT = "li.velvetchat.installation"

    /**
     * the half only members may read
     *
     * the `.well-known` is public, which is fine for addresses but not for an api key. so a
     * server can keep any of the same fields in a [TYPE_ETAT] state event, in a room created with
     * `m.federate: false`: only accounts of this server can join it, and the app joins it at
     * login. what the room says wins over the `.well-known`
     *
     * @param contenu the state event's content
     */
    fun completer(dossier: String, contenu: String) {
        val prive = analyser("{\"li.velvetchat\":$contenu}")
        val c = etat.value
        fun String.ou(a: String) = ifBlank { a }
        val fusion = c.copy(
            dictee = prive.dictee.ou(c.dictee), import = prive.import.ou(c.import),
            misesAJour = prive.misesAJour.ou(c.misesAJour), catalogue = prive.catalogue.ou(c.catalogue),
            intendant = prive.intendant.ou(c.intendant), invitations = prive.invitations.ou(c.invitations),
            gifs = prive.gifs.ou(c.gifs), push = prive.push.ou(c.push), lan = prive.lan.ou(c.lan),
        )
        poser(fusion)
        runCatching { File(dossier, "installation.json").writeText(json.encodeToString(ConfigServeur.serializer(), fusion)) }
    }

    /** back to nothing, on logout. the cache file goes with the session directory */
    fun oublier() = poser(ConfigServeur())

    private fun poser(c: ConfigServeur) {
        etat.value = c
        if (c.invitations.isNotBlank()) LienInvitation.base = c.invitations
    }

    /** null when the server could not be reached, which keeps the previous answer */
    private fun lire(domaine: String): ConfigServeur? = runCatching {
        val cnx = (URI.create("https://$domaine/.well-known/matrix/client").toURL()
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        val texte = try {
            if (cnx.responseCode == 404) return ConfigServeur()
            cnx.inputStream.bufferedReader().readText()
        } finally {
            cnx.disconnect()
        }
        analyser(texte)
    }.getOrNull()

    /** the parsing alone, kept apart so a test can feed it a document */
    fun analyser(texte: String): ConfigServeur {
        val racine = json.parseToJsonElement(texte).jsonObject
        val v = racine["li.velvetchat"] as? JsonObject
        fun champ(nom: String) = v?.get(nom)?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val vocal = (racine["org.matrix.msc4143.rtc_foci"] as? JsonArray)
            ?.map { it.jsonObject }
            ?.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "livekit" }
            ?.get("livekit_service_url")?.jsonPrimitive?.contentOrNull
            ?.removeSuffix("/")?.let { "$it/sfu/get" }
            .orEmpty()
        return ConfigServeur(
            vocal = vocal,
            dictee = champ("dictee"),
            import = champ("import").removeSuffix("/"),
            misesAJour = champ("mises_a_jour"),
            catalogue = champ("catalogue"),
            intendant = champ("intendant"),
            invitations = champ("invitations"),
            gifs = champ("gifs"),
            push = champ("push"),
            lan = champ("lan").removeSuffix("/"),
            salon = champ("salon"),
        )
    }
}

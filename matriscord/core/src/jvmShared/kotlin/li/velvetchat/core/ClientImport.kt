package li.velvetchat.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import li.velvetchat.core.model.ApercuImport
import li.velvetchat.core.model.ArbreImport
import li.velvetchat.core.model.DemandeImport
import li.velvetchat.core.model.EtatImport
import li.velvetchat.core.model.PaquetImport
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * the client for the discord import service (see `tools/service-import-discord.py`)
 *
 * deliberately in the clear on the local network and nowhere else: the service writes in the name
 * of the people in the house, it has no business on the internet. the session's matrix token is
 * the proof, and the service checks it against synapse, like the dictation gateway does
 *
 * hand rolled and dependency free. an import is measured in hours, the http handshake is not what
 * costs
 */
class ClientImport(private val fixe: String = DEFAUT) {

    /** the address given, otherwise the one the server advertises (see [Installation]) */
    private val base: String get() = fixe.ifBlank { Installation.config.value.import }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun paquets(jeton: String): List<PaquetImport> =
        json.decodeFromString<Paquets>(get(jeton, "/paquets")).paquets

    suspend fun arbre(jeton: String, paquet: String): ArbreImport =
        json.decodeFromString(get(jeton, "/arbre?paquet=" + encode(paquet)))

    suspend fun apercu(jeton: String, paquet: String, salon: String): ApercuImport =
        json.decodeFromString(get(jeton, "/apercu?paquet=" + encode(paquet) + "&salon=" + encode(salon)))

    suspend fun lancer(jeton: String, demande: DemandeImport): String =
        json.decodeFromString<Tache>(post(jeton, "/import", json.encodeToString(DemandeImport.serializer(), demande))).tache

    suspend fun etat(jeton: String, tache: String): EtatImport =
        json.decodeFromString(get(jeton, "/etat?tache=" + encode(tache)))

    suspend fun arreter(jeton: String, tache: String) {
        post(jeton, "/arreter", """{"tache":"$tache"}""")
    }

    /** is the service answering? used to say "nas unreachable" rather than keep anyone waiting */
    suspend fun disponible(jeton: String): Boolean =
        runCatching { get(jeton, "/sante") }.isSuccess

    private suspend fun get(jeton: String, chemin: String): String = appel(jeton, chemin, null)

    private suspend fun post(jeton: String, chemin: String, corps: String): String =
        appel(jeton, chemin, corps)

    private suspend fun appel(jeton: String, chemin: String, corps: String?): String =
        withContext(Dispatchers.IO) {
            check(base.isNotBlank()) { tr("Ce serveur n'annonce pas de service d'import", "This server advertises no import service") }
            val cnx = (URI.create(base + chemin).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = if (corps == null) "GET" else "POST"
                setRequestProperty("Authorization", "Bearer $jeton")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10_000
                readTimeout = 120_000
                doOutput = corps != null
            }
            try {
                corps?.let { cnx.outputStream.use { flux -> flux.write(it.toByteArray()) } }
                val code = cnx.responseCode
                val texte = (if (code < 400) cnx.inputStream else cnx.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code >= 400) error("service d'import : $code ${texte.take(200)}")
                texte
            } finally {
                cnx.disconnect()
            }
        }

    private fun encode(valeur: String) = URLEncoder.encode(valeur, "UTF-8")

    @kotlinx.serialization.Serializable
    private data class Paquets(val paquets: List<PaquetImport> = emptyList())

    @kotlinx.serialization.Serializable
    private data class Tache(val tache: String)

    companion object {
        /**
         * no address by default, on purpose
         *
         * it used to carry the author's own machine, which is both wrong for anybody else and
         * a description of somebody's home network shipped in the source. the address belongs
         * to the server, which advertises it
         */
        const val DEFAUT = ""
    }
}

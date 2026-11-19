package li.velvetchat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * importing a discord export, as the apps see it
 *
 * the work itself runs on the nas (`tools/service-import-discord.py`). reposting a message at its
 * original date needs an application service, whose token must not leave the server. the app picks
 * which bundle, which conversations, with or without media, between which dates, and watches the
 * progress
 */
@Serializable
data class PaquetImport(
    val nom: String,
    /** how many conversations the bundle holds */
    val salons: Int = 0,
    /** when it was dropped there, in ms */
    val modifie: Long = 0,
    /** whose it is. the import writes in that person's name */
    val proprietaire: String? = null,
)

@Serializable
data class SalonImport(
    val id: String,
    val nom: String,
    val type: String = "",
    /** the message file's size, an order of magnitude before the preview */
    val octets: Long = 0,
    /** a group conversation rather than a one to one */
    val groupe: Boolean = false,
)

@Serializable
data class ServeurImport(val id: String, val nom: String, val salons: List<SalonImport> = emptyList())

@Serializable
data class ArbreImport(
    val serveurs: List<ServeurImport> = emptyList(),
    val mp: List<SalonImport> = emptyList(),
)

/** what a conversation actually holds. read on demand, since the file can be enormous */
@Serializable
data class ApercuImport(
    val messages: Int = 0,
    val pieces: Int = 0,
    val debut: Long? = null,
    val fin: Long? = null,
)

/** the request sent to the service: what to import, where, and between which bounds */
@Serializable
data class DemandeImport(
    val paquet: String,
    /** the discord conversation ids that were picked */
    val conversations: List<String>,
    /** the matrix room on the receiving end */
    val salon: String,
    /**
     * whose name to write in. a discord export only contains that person's own messages, so it is
     * whoever the bundle belongs to. empty means whoever asked for the import
     */
    val auteur: String? = null,
    val medias: Boolean = true,
    /** the period's bounds, in ms. null means unbounded */
    val du: Long? = null,
    val au: Long? = null,
)

@Serializable
data class EtatImport(
    val phase: String = "inconnue",
    val faits: Int = 0,
    val total: Int = 0,
    @SerialName("evenements") val evenements: Int = 0,
    @SerialName("medias_rates") val mediasRates: Int = 0,
    val vitesse: Double = 0.0,
    val erreur: String? = null,
    val dernier: String? = null,
) {
    val fini: Boolean get() = phase == "fini" || phase == "échec" || phase == "arrêté"
}

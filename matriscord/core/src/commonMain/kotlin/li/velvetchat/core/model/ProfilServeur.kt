package li.velvetchat.core.model

/** one trait on a server profile: an emoji and a few words */
data class Particularite(val emoji: String, val texte: String)

/**
 * a server's profile, as the server profile screen shows it
 *
 * name, description and icon are the standard states (`m.room.name`, `m.room.topic`,
 * `m.room.avatar`), so other matrix clients see them. the banner and the traits have no
 * equivalent and live in a state of our own
 *
 * @param banniere one of the [BANNIERES] gradients, by position
 * @param creeLe the `m.room.create` timestamp, null when it cannot be read
 */
data class ProfilServeur(
    val nom: String,
    val description: String?,
    val avatarUrl: String?,
    val banniere: Int = 0,
    val particularites: List<Particularite> = emptyList(),
    val membres: Int = 0,
    val creeLe: Long? = null,
) {
    companion object {
        const val BANNIERES = 10
        const val PARTICULARITES_MAX = 5
    }
}

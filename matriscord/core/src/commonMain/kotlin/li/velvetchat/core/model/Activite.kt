package li.velvetchat.core.model

/**
 * what somebody is up to right now, which discord calls rich presence and we call a nuisance we
 * had to build anyway
 *
 * ## matrix has never heard of this
 *
 * no activity in the spec, no msc, not even a hopeful draft. so we pick a hiding place. the two
 * defensible ones are an ephemeral event or the extended profile, and the profile wins because
 * an activity has to be visible to somebody who shares no room with you — which is the entire
 * job description of the extended profile and the exact opposite of an ephemeral event
 *
 * the bill for that choice, paid in full and in advance: the profile is **persistent**. a game
 * killed from the task manager never says goodbye, so without help the server would insist you
 * have been playing Skyrim since tuesday. hence the expiry below, which the *reader* enforces,
 * because the server has no idea what any of this is and we are not about to explain it
 *
 * ## one key, not nine
 *
 * the whole thing travels as compact json inside a single profile key. nine keys would be nine
 * writes every time a game changes its mind about what chapter you are on, against a server
 * that rate limits writes and does not find this funny. one key, one write, and the fields show
 * up together or not at all — which is what you want from something describing a single moment
 *
 * @param nom the application, which is the only field discord guarantees. "Celeste"
 * @param details the first line under it. "Chapter 3: Celestial Resort"
 * @param etat the second line. "1 death"
 * @param depuis when it started, in epoch milliseconds, for an elapsed counter. null shows none
 * @param grandeImage the large artwork, an `mxc://` or an http url from the game's own cdn
 * @param petiteImage the small badge in its corner, same rules
 * @param applicationId who declared it, kept for deduplication and for nothing else
 * @param publieeLe when this was written, epoch milliseconds. set by whoever publishes it, and
 *   read by [perimee] — the profile has no timestamp of its own, so the stamp rides along inside
 */
data class Activite(
    val nom: String,
    val details: String? = null,
    val etat: String? = null,
    val depuis: Long? = null,
    val grandeImage: String? = null,
    val petiteImage: String? = null,
    val applicationId: String? = null,
    val publieeLe: Long? = null,
) {
    /**
     * has this gone off
     *
     * published activities outlive the processes that published them, enthusiastically. the
     * reader decides whether to believe one, based on how long ago it was written, and the
     * writer keeps saying "still true" for as long as it is still true
     *
     * null [publieeLe] means we have no idea when this was said, and we do not believe things
     * we have no idea about
     */
    fun perimee(maintenant: Long): Boolean =
        publieeLe == null || maintenant - publieeLe > PEREMPTION_MS

    companion object {
        /**
         * how long we take somebody's word for it
         *
         * five minutes, against a heartbeat every [REFRESH_MS]. the ratio is what matters, not
         * the numbers: enough slack that one late refresh on bad wifi does not blank the thing,
         * tight enough that nobody is still publicly playing a game they closed before lunch
         */
        const val PEREMPTION_MS = 5 * 60 * 1000L

        /**
         * how often we repeat ourselves when nothing has changed
         *
         * ninety seconds. this is not a refresh rate for the display, it is a pulse: the fields
         * only go out when they actually change, and this is what keeps [perimee] saying no in
         * between
         *
         * synapse rate limits profile writes and a game will happily change its mind every two
         * seconds about which chapter you are on. so changes go out at most once per
         * [MIN_ECART_MS] and everything in between gets collapsed into the next one. a presence
         * four seconds behind has lost nothing. a presence that got the account rate limited has
         * lost rather more
         */
        const val REFRESH_MS = 90 * 1000L

        /** the floor between two writes, however fast the game changes its mind */
        const val MIN_ECART_MS = 15 * 1000L

        /** the profile key the whole thing travels in */
        const val CLE = "li.velvetchat.activity"
    }
}

/**
 * how long it has been going, as discord draws it
 *
 * under an hour it is `MM:SS`, over an hour `H:MM:SS` — the hours without a leading zero, since
 * padding them would imply we expect somebody to still be in there at four in the morning. the
 * exact cutoff was not measured against a screenshot, it is the usual convention and it reads
 * right; if the reference turns out to pad the hours, this is a one character fix
 *
 * a negative duration means the other machine's clock disagrees with ours, which happens, and is
 * clamped rather than drawn as a counter running backwards
 */
fun dureeEcoulee(millisecondes: Long): String {
    val total = (millisecondes / 1000).coerceAtLeast(0)
    val heures = total / 3600
    val minutes = (total % 3600) / 60
    val secondes = total % 60
    fun deux(n: Long) = n.toString().padStart(2, '0')
    return if (heures > 0) "$heures:${deux(minutes)}:${deux(secondes)}"
    else "${deux(minutes)}:${deux(secondes)}"
}

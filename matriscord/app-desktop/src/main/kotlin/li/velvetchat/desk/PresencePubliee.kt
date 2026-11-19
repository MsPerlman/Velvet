package li.velvetchat.desk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.essayer
import li.velvetchat.core.model.Activite
import li.velvetchat.core.model.ProfilEtendu
import li.velvetchat.core.model.versJson

/**
 * the bit that decides what actually reaches the server, and how often
 *
 * [RichPresence] collects whatever the games shout into the pipe. this is the adult in the room
 *
 * ## why there is a brake at all
 *
 * a game will change its activity whenever it feels like it. a rhythm game does it every song,
 * a roguelike every floor, and at least one of them will do it every frame because somebody
 * thought that was a good idea. every one of those is a write to the extended profile, and
 * synapse rate limits writes — the `rc_` defaults on this homeserver already cost about five
 * seconds per state write once you pass ten in a row
 *
 * so: nothing goes out faster than [Activite.MIN_ECART_MS], changes that happen during the wait
 * are collapsed into the next write, and an unchanged activity is repeated every
 * [Activite.REFRESH_MS] purely so readers can tell it is still alive
 *
 * ## what gets cleared, and when
 *
 * closing the game clears it. closing velvet clears it. the expiry in [Activite.perimee] covers
 * the third case, the one where the whole machine goes away mid game and nobody gets to clear
 * anything — readers simply stop believing it after five minutes
 *
 * the clear on shutdown is best effort and we do not wait around for it. if it fails, the expiry
 * picks up the slack five minutes later, which is exactly what it is there for
 */
internal object PresencePubliee {

    /**
     * watches the local server and publishes, forever, in the given scope
     *
     * one coroutine and not a flow collector with a `debounce`, because this has two different
     * triggers — "it changed" and "say it again anyway" — and expressing that as operators was
     * briefly attempted and read like a crossword
     */
    fun suivre(portee: CoroutineScope, moteur: MatrixEngine) {
        portee.launch {
            var dernierEnvoi = 0L
            var derniereCharge: String? = null

            while (isActive) {
                val maintenant = System.currentTimeMillis()
                val courante = RichPresence.activite.value
                // the timestamp goes in last, so two identical activities a minute apart produce
                // the same payload and get deduplicated. stamping first would make every one of
                // them unique and defeat the whole thing
                val charge = courante?.copy(publieeLe = maintenant)?.versJson()

                val aChange = (charge == null) != (derniereCharge == null) ||
                    sansHorodatage(charge) != sansHorodatage(derniereCharge)
                val assezVieux = maintenant - dernierEnvoi >= Activite.REFRESH_MS
                val leFreinEstLeve = maintenant - dernierEnvoi >= Activite.MIN_ECART_MS

                if ((aChange || (derniereCharge != null && assezVieux)) && leFreinEstLeve) {
                    essayer { moteur.setProfilEtendu(ProfilEtendu.CLE_ACTIVITE, charge) }
                        .onSuccess {
                            dernierEnvoi = maintenant
                            derniereCharge = charge
                        }
                        .onFailure {
                            // a failed write is not worth a dialog, a toast or a log storm. the
                            // worst case is that nobody sees you are playing something, which is
                            // the state the world was in until this file existed
                            grogner("presence non publiee", it)
                            dernierEnvoi = maintenant
                        }
                }
                delay(TIC_MS)
            }
        }
    }

    /** clears it on the way out, without making anybody wait for the answer */
    suspend fun effacer(moteur: MatrixEngine) {
        essayer { moteur.setProfilEtendu(ProfilEtendu.CLE_ACTIVITE, null) }
    }

    /**
     * the payload with its timestamp taken back out, for comparing
     *
     * otherwise every single tick looks like a change, we write every three seconds, and synapse
     * starts answering 429 with the patience of a tired parent
     */
    private fun sansHorodatage(charge: String?): String? =
        charge?.replace(Regex(""","w":\d+"""), "")

    /** how often we wake up to think about it. cheap, nothing happens most of the time */
    private const val TIC_MS = 3_000L
}

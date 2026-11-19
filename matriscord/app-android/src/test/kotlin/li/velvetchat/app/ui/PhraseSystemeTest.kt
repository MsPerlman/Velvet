package li.velvetchat.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.SystemKind

/**
 * the wording of system events ("x joined the room")
 *
 * tested on the jvm and not on a device: it draws nothing, it builds text. putting it here makes it
 * run in `./gradlew test`, so in the hook on every commit, instead of waiting for somebody to think
 * of plugging a phone in
 *
 * what is checked are the fallbacks, not the happy cases: that is where the rendering breaks, and
 * it is invisible as long as you only test with accounts that have a display name
 */
class PhraseSystemeTest {

    private fun systeme(
        kind: SystemKind,
        actor: String = "@marie:exemple.org",
        actorDisplayName: String? = "Marie",
        target: String? = null,
        targetDisplayName: String? = null,
        detail: String? = null,
    ) = MessageBody.System(
        kind = kind,
        actor = actor,
        actorDisplayName = actorDisplayName,
        target = target,
        targetDisplayName = targetDisplayName,
        detail = detail,
    )

    @Test
    fun utiliseLeNomDAffichageQuandIlExiste() {
        assertEquals("Marie a rejoint le salon", systeme(SystemKind.JOINED).phrase())
    }

    /**
     * with no display name we take the id's localpart, not the whole id
     *
     * the fallback matters more than the nominal case: a freshly created account, or one seen
     * before the sync brought its profile back, has no display name. showing "@marie:example.org
     * joined the room" would be technically exact and unreadable
     */
    @Test
    fun sansNomDAffichageOnTireLeLocalpart() {
        val p = systeme(SystemKind.JOINED, actorDisplayName = null).phrase()
        assertEquals("marie a rejoint le salon", p)
        assertTrue("l'identifiant brut ne doit pas fuiter : $p", "@" !in p && ":" !in p)
    }

    /**
     * the same fallback for the target, and a word rather than a hole when it is missing entirely
     */
    @Test
    fun cibleAbsenteDonneQuelquUnPlutotQuUnTrou() {
        assertEquals("Marie a exclu quelqu'un", systeme(SystemKind.KICKED).phrase())
    }

    @Test
    fun cibleSansNomDAffichageTireAussiLeLocalpart() {
        val p = systeme(SystemKind.KICKED, target = "@paul:exemple.org").phrase()
        assertEquals("Marie a exclu paul", p)
    }

    /**
     * a rename carries the new name when it is known, and settles for the bare sentence otherwise,
     * rather than showing "to null" or empty quotes
     */
    @Test
    fun renommageAvecEtSansDetail() {
        assertEquals(
            "Marie a renommé le salon en « cuisine »",
            systeme(SystemKind.ROOM_NAME_CHANGED, detail = "cuisine").phrase(),
        )
        assertEquals(
            "Marie a renommé le salon",
            systeme(SystemKind.ROOM_NAME_CHANGED, detail = null).phrase(),
        )
    }

    /** encryption speaks of the room, not of who turned it on: the sentence names nobody */
    @Test
    fun leChiffrementNeNommePersonne() {
        val p = systeme(SystemKind.ENCRYPTION_ENABLED).phrase()
        assertEquals("Le chiffrement est actif dans ce salon", p)
        assertTrue("Marie" !in p)
    }

    /**
     * no gender assumed. the sentences have to stay valid for everybody: no agreed participle, no
     * he/she. checking it here stops a well meaning rewording introducing an agreement that
     * excludes half the people using it
     */
    @Test
    fun aucunePhraseNeSupposeUnGenre() {
        val suspects = listOf(" il ", " elle ", "venue", "venu ", "parti ", "partie ")
        SystemKind.entries.forEach { kind ->
            val p = systeme(kind).phrase().lowercase()
            suspects.forEach { mot ->
                assertTrue("« $mot » dans la phrase de $kind : $p", mot !in p)
            }
        }
    }
}

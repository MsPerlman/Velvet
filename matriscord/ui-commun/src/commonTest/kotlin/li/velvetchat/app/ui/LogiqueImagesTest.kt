package li.velvetchat.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * the decisions images and emoji make, with no screen involved
 *
 * these cases lived in the android module's `LogiqueInterfaceTest`, because their subject lived
 * there. `initialesDe` and `extraireEmojis` followed `Avatar.kt` and `Emotes.kt` to this side of
 * the seam, and the tests follow, not only out of symmetry: leaving them over there would have
 * meant opening those functions up to `public` so another module could reach them, which is
 * widening an api for a test's convenience
 *
 * each case matches a rule written in the production code, and several match a bug that really
 * happened
 */
class LogiqueImagesTest {

    @Test
    fun deuxMotsDonnentLeursDeuxInitiales() {
        assertEquals("MD", initialesDe("Marie Dupont"))
    }

    /**
     * the regression `initialesDe`'s comment describes. splitting on the dash as well gave both
     * test accounts the same two letters, so two identical tiles for two people
     */
    @Test
    fun deuxComptesQuiNeDifferentQueParLaFinRestentDistincts() {
        assertEquals("C1", initialesDe("compte-essai1"))
        assertEquals("C2", initialesDe("compte-essai2"))
    }

    /** a matrix id loses its sigil and its server before being reduced */
    @Test
    fun unIdentifiantMatrixEstNettoyeAvantReduction() {
        assertEquals("CE", initialesDe("@camille:exemple.org"))
    }

    @Test
    fun uneSeuleLettreNeCasseRien() {
        assertEquals("A", initialesDe("a"))
    }

    /** never returns empty: a tile with no letter tells nobody apart */
    @Test
    fun unNomVideRetombeSurUnPointDInterrogation() {
        assertEquals("?", initialesDe(""))
        assertEquals("?", initialesDe("@"))
    }

    @Test
    fun unEmojiDuServeurDevientUneMarqueEtSonAdresse() {
        val (texte, adresses) = extraireEmojis("""bonjour <img src="mxc://exemple.org/abc"> !""")
        assertEquals("bonjour $MARQUE_EMOJI !", texte)
        assertEquals(listOf("mxc://exemple.org/abc"), adresses)
    }

    /**
     * the addresses come back in the text's order, which is what puts them back in the right place
     */
    @Test
    fun lOrdreDesAdressesSuitCeluiDuTexte() {
        val (_, adresses) = extraireEmojis(
            """<img src="mxc://s/un"/>x<img src="mxc://s/deux"/>"""
        )
        assertEquals(listOf("mxc://s/un", "mxc://s/deux"), adresses)
    }

    /**
     * the trust boundary. an `<img>` that is not an `mxc://` came from a message somebody wrote,
     * and loading it would report the read to the host its author chose. so it gets removed,
     * replaced by its `alt`, and does not enter the list to load
     */
    @Test
    fun uneImageExterieureNEstJamaisChargee() {
        val (texte, adresses) = extraireEmojis(
            """a <img src="https://pisteur.example/px.gif" alt=":clin:"> b"""
        )
        assertEquals("a :clin: b", texte)
        assertTrue(adresses.isEmpty())
    }

    @Test
    fun uneImageExterieureSansAltDisparaitSansLaisserDeTrou() {
        val (texte, adresses) = extraireEmojis("""a <img src="https://x.example/p.gif"> b""")
        assertEquals("a  b", texte)
        assertTrue(adresses.isEmpty())
    }

    @Test
    fun unMessageSansImageRessortIntact() {
        val html = "<b>gras</b> et <i>italique</i>"
        val (texte, adresses) = extraireEmojis(html)
        assertEquals(html, texte)
        assertTrue(adresses.isEmpty())
    }

    // ------------------------------------------------- the homeserver address

    @Test
    fun uneAdresseSansSchemaEstCompleteeEnHttps() {
        assertEquals("https://exemple.org", adresseServeur("exemple.org"))
        assertEquals("https://exemple.org", adresseServeur("  exemple.org  "))
        assertEquals("https://exemple.org", adresseServeur("exemple.org/"))
    }

    /** an already complete address passes as it is, `http` included, see the function */
    @Test
    fun uneAdresseDejaCompleteNEstPasTouchee() {
        assertEquals("https://exemple.org", adresseServeur("https://exemple.org"))
        assertEquals("http://192.168.0.10:8008", adresseServeur("http://192.168.0.10:8008/"))
    }
}

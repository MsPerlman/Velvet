package li.velvetchat.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * what decides to open a link from a message, attacked on purpose
 *
 * a message comes from elsewhere. the text it shows and the address it really carries are two
 * independent strings, and that is the simplest lie anyone can write in a room. these
 * functions decided all of it **without a single test**
 *
 * two guards, tested here separately:
 *
 *   · [schemeSur]: only `http`, `https` and `mailto` open without asking. everything else goes
 *     through a confirmation, which closes `intent:`, `file:`, `data:`, `javascript:` and the
 *     schemes of other installed apps
 *   · [lienTrompeur]: does the displayed text announce a different destination from the address
 */
class LiensSecuriteTest {

    // ------------------------------------------------------------------ the schemes

    @Test
    fun `seuls trois schemes s ouvrent sans demander`() {
        listOf("https://a.example", "http://a.example", "mailto:x@a.example")
            .forEach { assertTrue(schemeSur(it), "devrait etre sur : $it") }
    }

    @Test
    fun `les schemes dangereux sont refuses`() {
        listOf(
            "javascript:alert(1)",
            "JaVaScRiPt:alert(1)",
            "data:text/html;base64,PHNjcmlwdD4=",
            "file:///etc/passwd",
            "file://C:/Windows/win.ini",
            "intent://scan/#Intent;scheme=zxing;end",
            "content://com.autre.appli/donnees",
            "vbscript:msgbox(1)",
            "jar:http://a.example!/x",
            "market://details?id=x",
            "tel:+33600000000",
            "sms:+33600000000",
            "ms-msdt:/id",
            "smb://serveur/partage",
        ).forEach { assertFalse(schemeSur(it), "devrait etre refuse : $it") }
    }

    /**
     * an invisible character before the scheme must not work as a pass
     *
     * `trim()` strips spaces, which is wanted — a link copied by hand often trails one. it does
     * not strip the null byte, so a string starting with one matches no safe scheme: it goes to
     * a confirmation, which is the right outcome
     */
    @Test
    fun `un prefixe invisible ne fait pas passer un scheme dangereux`() {
        listOf(
            "\u0000javascript:alert(1)",
            "\u200Bjavascript:alert(1)",
            "\njavascript:alert(1)",
            " javascript:alert(1)",
        ).forEach { assertFalse(schemeSur(it), "devrait etre refuse : $it") }
    }

    @Test
    fun `un scheme presque juste ne passe pas`() {
        listOf("https:/a.example", "https:a.example", "htt ps://a.example", "https//a.example")
            .forEach { assertFalse(schemeSur(it), "devrait etre refuse : $it") }
    }

    @Test
    fun `la partie identifiants ne fait pas l hote`() {
        assertEquals("piege.example", hoteDe("https://banque.example@piege.example/"))
        assertEquals("piege.example", hoteDe("https://a@b@piege.example/"))
        assertEquals("banque.example", hoteDe("https://user@banque.example/"))
    }

    @Test
    fun `un arobase apres la barre reste dans le chemin`() {
        assertEquals("banque.example", hoteDe("https://banque.example/page@piege.example"))
    }

    /**
     * the order of the splitting matters: `/`, `?`, `#`, **then** the last `@`
     *
     * splitting on the at sign first would read `piege.example` where a browser reads
     * `banque.example`, so a warning on an honest link — and, worse, the reverse reasoning
     * somewhere else
     */
    @Test
    fun `un arobase dans le fragment ou la requete ne change pas l hote`() {
        assertEquals("banque.example", hoteDe("https://banque.example#@piege.example/"))
        assertEquals("banque.example", hoteDe("https://banque.example?x=@piege.example"))
    }

    @Test
    fun `le port et le www ne sont pas la destination`() {
        assertEquals("banque.example", hoteDe("https://banque.example:8443/x"))
        assertEquals("banque.example", hoteDe("https://www.banque.example/"))
    }

    @Test
    fun `une chaine qui n annonce pas d hote rend null`() {
        listOf("cliquez ici", "bonjour", "", "   ", "localhost", "un mot.avec espace")
            .forEach { assertNull(hoteDe(it), "ne devrait annoncer aucun hote : $it") }
    }

    // ------------------------------------------------------------------ the lie

    @Test
    fun `un texte qui annonce un autre hote est trompeur`() {
        assertTrue(lienTrompeur("banque.example", "https://piege.example/"))
        assertTrue(lienTrompeur("https://banque.example", "https://banque.example@piege.example/"))
        assertTrue(lienTrompeur("banque.example", "https://banque.example.piege.example/"))
        // a subdomain that looks alike, but is not the same one
        assertTrue(lienTrompeur("banque.example", "https://banque-example.piege.example/"))
    }

    @Test
    fun `un hote en caracteres cyrilliques ne passe pas pour l original`() {
        assertTrue(lienTrompeur("apple.com", "https://\u0430pple.com/"))
        assertTrue(lienTrompeur("apple.com", "https://xn--80ak6aa92e.com/"))
    }

    @Test
    fun `un texte honnete n avertit pas`() {
        assertFalse(lienTrompeur("banque.example", "https://banque.example/page"))
        assertFalse(lienTrompeur("https://www.banque.example", "https://banque.example/"))
        assertFalse(lienTrompeur("BANQUE.example", "https://banque.example/"))
        // a text that claims nothing cannot lie
        assertFalse(lienTrompeur("cliquez ici", "https://banque.example/"))
    }

    /**
     * an address whose host we cannot read **always** asks
     *
     * it is the default direction that matters: in doubt we ask, we do not open
     */
    @Test
    fun `une cible illisible fait toujours demander`() {
        assertTrue(lienTrompeur("cliquez ici", "javascript:alert(1)"))
        assertTrue(lienTrompeur("cliquez ici", "https://localhost/"))
        assertTrue(lienTrompeur("n importe quoi", ""))
    }

    /**
     * the backslash: browsers treat it as a slash, we do not
     *
     * the disagreement leans the right way — we read a longer host than the browser, so we warn
     * where it would go elsewhere. pinned so the opposite does not settle in one day
     */
    @Test
    fun `un antislash penche vers l avertissement`() {
        assertTrue(lienTrompeur("banque.example", "https://banque.example\\@piege.example/"))
        assertTrue(lienTrompeur("banque.example", "https://banque.example\\.piege.example/"))
    }

    @Test
    fun `un domaine nu est complete en https`() {
        assertEquals("https://a.example", completerScheme("a.example"))
        assertEquals("http://a.example", completerScheme("http://a.example"))
        assertEquals("mailto:x@a.example", completerScheme("mailto:x@a.example"))
    }
}

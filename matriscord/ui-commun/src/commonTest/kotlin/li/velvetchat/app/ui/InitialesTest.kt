package li.velvetchat.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * the initials are drawn for every person without an avatar, from a display name the server
 * hands over. so the input is not ours, and the two rules have to hold on names nobody meant
 * to be reasonable
 */
class InitialesTest {

    @Test
    fun `deux mots donnent les deux premieres lettres`() {
        assertEquals("MD", initialesDe("Marie Dupont"))
    }

    @Test
    fun `un seul mot donne la premiere et la derniere`() {
        assertEquals("NR", initialesDe("Nestor"))
    }

    @Test
    fun `l identifiant matrix est ramene a sa partie locale`() {
        assertEquals("VE", initialesDe("@vitrine:example.org"))
    }

    // ---- what the `Char` version broke ----

    /**
     * the regression this file was written for
     *
     * `propre.first()` and `propre.last()` returned the two halves of the pair, so a name made
     * of one emoji came out as two broken glyphes. it also took the "first and last" branch,
     * since `length` counts a single emoji as two
     */
    @Test
    fun `un nom d une seule emoji reste entier`() {
        assertEquals("😀", initialesDe("😀"))
    }

    @Test
    fun `une emoji en tete de mot n est pas coupee`() {
        // "<rocket> Bob" -> the rocket whole, then B
        assertEquals("🚀B", initialesDe("🚀 Bob"))
    }

    @Test
    fun `premiere et derniere emoji d un mot de deux`() {
        assertEquals("😀🚀", initialesDe("😀🚀"))
    }

    // ---- the edges ----

    @Test
    fun `un nom vide donne un point d interrogation`() {
        assertEquals("?", initialesDe(""))
        assertEquals("?", initialesDe("   "))
        assertEquals("?", initialesDe("@:example.org"))
    }

    @Test
    fun `une seule lettre se suffit`() {
        assertEquals("A", initialesDe("a"))
    }

    /** several spaces in a row used to produce empty words, and `first()` threw on those */
    @Test
    fun `des espaces multiples ne font pas de mot vide`() {
        assertEquals("MD", initialesDe("  Marie    Dupont  "))
    }

    /** a lone high surrogate is not a character, and must not take the function down */
    @Test
    fun `un demi surrogate isole ne fait pas tomber`() {
        assertEquals("\uD83D", initialesDe("\uD83D"))
    }
}

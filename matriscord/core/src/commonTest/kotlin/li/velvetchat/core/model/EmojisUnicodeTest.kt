package li.velvetchat.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * unicode emoji: their shortcodes, their skin tones, and the jumbo rule
 *
 * three rules that decide what actually leaves in a message, or how big it draws, and that are
 * invisible on a reread. an untranslated shortcode goes out as written and nobody notices until
 * the message is already on everybody else's screen
 */
class EmojisUnicodeTest {

    @Test
    fun `un raccourci connu devient son caractere`() {
        assertEquals("bravo 🎉 !", remplacerRaccourcisUnicode("bravo :tada: !"))
    }

    @Test
    fun `plusieurs raccourcis dans la meme phrase`() {
        assertEquals("😂👍", remplacerRaccourcisUnicode(":joy::thumbsup:"))
    }

    /** a name we do not know stays written. we do not eat text at random */
    @Test
    fun `un nom inconnu traverse intact`() {
        assertEquals("il est :inconnu: ce truc", remplacerRaccourcisUnicode("il est :inconnu: ce truc"))
    }

    /**
     * how the two families are arbitrated. a server that publishes an emoji called `fire` did so
     * on purpose, and replacing it with the unicode one would take away the image they picked
     */
    @Test
    fun `un emoji du serveur gagne sur le raccourci Unicode`() {
        assertEquals(":fire:", remplacerRaccourcisUnicode(":fire:", exclus = setOf("fire")))
        assertEquals("🔥", remplacerRaccourcisUnicode(":fire:"))
    }

    /** a clock time is not a shortcode. the commonest false positive there is */
    @Test
    fun `une heure n est pas touchee`() {
        assertEquals("rendez-vous à 18:30:00", remplacerRaccourcisUnicode("rendez-vous à 18:30:00"))
    }

    @Test
    fun `la teinte s applique a ce qui l accepte`() {
        assertEquals("👍🏽", remplacerRaccourcisUnicode(":thumbsup:", teinte = TeintePeau.MOYENNE))
    }

    /**
     * a face takes no skin tone. it is yellow by convention, and applying the modifier anyway
     * would draw the face followed by an orphan coloured square
     */
    @Test
    fun `un visage reste intact malgre la teinte`() {
        assertEquals("😂", remplacerRaccourcisUnicode(":joy:", teinte = TeintePeau.FONCEE))
    }

    @Test
    fun `la teinte par defaut n ajoute rien`() {
        assertEquals("👍", remplacerRaccourcisUnicode(":thumbsup:", teinte = TeintePeau.DEFAUT))
    }

    // ------------------------------------------------------------------ jumbo

    @Test
    fun `un emoji seul compte pour un`() {
        assertEquals(1, emojisSeuls("👍"))
    }

    @Test
    fun `les espaces ne cassent pas la regle`() {
        assertEquals(3, emojisSeuls("😂 😂  ❤️"))
    }

    /** one word is enough to cancel it. the message then says something other than the emoji */
    @Test
    fun `du texte autour annule le jumbo`() {
        assertEquals(0, emojisSeuls("bravo 🎉"))
        assertEquals(0, emojisSeuls("🎉 !"))
    }

    @Test
    fun `un message vide ou blanc ne vaut pas jumbo`() {
        assertEquals(0, emojisSeuls(""))
        assertEquals(0, emojisSeuls("   "))
    }

    /** a custom emoji's placeholder counts as an emoji, because it is one */
    @Test
    fun `un emoji personnalise compte`() {
        assertEquals(2, emojisSeuls("￼￼"))
        assertEquals(2, emojisSeuls("￼ 👍"))
    }

    /**
     * a family is one emoji, not three. `ZWJ` joiners sew several codepoints into a single glyph,
     * and counting them separately would push a message of ten families over the ceiling, so it
     * would stay small for no reason anyone could see
     */
    @Test
    fun `une sequence liee ne compte qu une fois`() {
        assertEquals(1, emojisSeuls("👨‍👩‍👧"))
    }

    @Test
    fun `un emoji teinte compte pour un`() {
        assertEquals(1, emojisSeuls("👍🏽"))
    }

    /**
     * `©`, `®` and `™` are technically emoji. accepting them would render "© 2026" in forty point
     * characters, which is the exact opposite of the point
     */
    @Test
    fun `les symboles de texte ordinaire sont refuses`() {
        assertEquals(0, emojisSeuls("©"))
        assertEquals(0, emojisSeuls("™"))
    }

    // ------------------------------------------------- end to end, on sending

    /**
     * the body and the html both carry the character
     *
     * the body is what clients ignoring `formatted_body` read, so substituting only in the html
     * would leave them a message spelled out as `:joy:`
     */
    @Test
    fun `un raccourci tape d un trait part en caractere`() {
        val r = composerMessage(
            "trop drôle :joy:",
            emptyList(),
            emptyList(),
            emptyList(),
        )
        assertEquals("trop drôle 😂", r.corps)
        assertTrue(r.html == null || "😂" in r.html!!, "le HTML ne doit pas garder le raccourci")
    }

    /**
     * the composer colours exactly what it will substitute
     *
     * the two live apart. `estRaccourciConnu` decides the colour while you type,
     * `remplacerRaccourcisUnicode` decides the text on send. nothing in the code stops one moving
     * without the other, and the gap would be tiresome: a coloured shortcode that goes out spelled
     * in full teaches people to stop believing the colour
     */
    @Test
    fun `ce qui se colore est ce qui se substitue`() {
        CATALOGUE_UNICODE.forEach {
            assertTrue(estRaccourciConnu(it.raccourci), "non reconnu : ${it.raccourci}")
            val avant = ":${it.raccourci}:"
            assertTrue(
                remplacerRaccourcisUnicode(avant) != avant,
                "coloré mais pas substitué : ${it.raccourci}",
            )
        }
        assertTrue(!estRaccourciConnu("raccourci-qui-n-existe-pas"))
    }

    /**
     * a composed sequence does not get cut in half
     *
     * a flag and a family are several codepoints held together by zero width joiners. a span
     * stopping at the first piece would leave the rest outside the style, which for what this is
     * used for means half an emoji upright and the other half leaning
     */
    @Test
    fun `une portee d emoji couvre la sequence entiere`() {
        val texte = "avant 👨‍👩‍👧 apres"
        val p = porteesEmojis(texte)
        assertEquals(1, p.size, "une seule portee attendue, obtenu $p")
        assertEquals("👨‍👩‍👧", texte.substring(p[0].first, p[0].last + 1))
    }

    @Test
    fun `le texte sans emoji ne donne aucune portee`() {
        assertTrue(porteesEmojis("juste des mots, et 3 chiffres").isEmpty())
    }

    @Test
    fun `deux emojis separes donnent deux portees`() {
        assertEquals(2, porteesEmojis("a 🎉 b 🔥 c").size)
    }

    @Test
    fun `le catalogue n a pas deux fois le meme raccourci`() {
        val doublons = CATALOGUE_UNICODE.groupBy { it.raccourci }.filter { it.value.size > 1 }
        assertTrue(doublons.isEmpty(), "raccourcis en double : ${doublons.keys}")
    }
}

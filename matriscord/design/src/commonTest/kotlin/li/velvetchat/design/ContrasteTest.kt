package li.velvetchat.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * the contrast ratio the role colour picker reports
 *
 * the anchors are the published ones, so a wrong formula cannot pass: black on white is exactly
 * 21, a colour on itself is exactly 1
 */
class ContrasteTest {

    private val surface = 0x0D0E11
    private val surfaceRaised = 0x232428

    @Test
    fun `les bornes publiees`() {
        assertEquals(21.0, contraste(0xFFFFFF, 0x000000), 0.01)
        assertEquals(21.0, contraste(0x000000, 0xFFFFFF), 0.01, "le rapport est symetrique")
        assertEquals(1.0, contraste(0x5865F2, 0x5865F2), 0.001, "une couleur sur elle-meme")
    }

    /**
     * the linearisation is the step a hand written version drops, so it is pinned by a value that
     * only comes out right with it
     *
     * mid grey #777777 on white is 4.48 with the srgb curve. without it, weighting the raw
     * channels, it comes out near 2.1 and the check would pass colours it should flag
     */
    @Test
    fun `la linearisation srgb est bien appliquee`() {
        assertEquals(4.48, contraste(0x777777, 0xFFFFFF), 0.02)
    }

    /** the alpha byte is not part of the colour here, and must not shift the answer */
    @Test
    fun `l alpha est ignore`() {
        assertEquals(
            contraste(0x5865F2, surface),
            contraste(0xFF5865F2.toInt(), surface),
            0.0001,
        )
    }

    /**
     * **the measurement that changed the design**
     *
     * the first version of `Contraste.kt` refused anything under the floor and claimed the floor
     * had been picked so as not to reject the shipped palette. it did reject it. this test is that
     * claim, pinned, so nobody turns the report back into a refusal without seeing the cost
     */
    @Test
    fun `le plancher refuserait quatre couleurs de la palette livree`() {
        val palette = listOf(
            0x5865F2, 0x7C4DFF, 0xB84DFF, 0xEC407A, 0xEF5350,
            0x3C45A5, 0x5335B3, 0x7E32B3, 0xA32B58, 0xA83A37,
            0xFF7043, 0xFFA726, 0xFFD54F, 0x66BB6A, 0x26A69A,
            0x29B6F6, 0x5C6BC0, 0x78909C,
        )
        assertEquals(18, palette.size, "la palette des roles, telle que RolesDecoratifs la porte")
        val sousLePlancher = palette.filterNot { contrasteSuffisant(it, surface) }
        assertEquals(
            listOf(0x3C45A5, 0x5335B3, 0x7E32B3, 0xA32B58),
            sousLePlancher.sorted(),
        )
        // and one more on the raised surface, which is what a nickname sits on in a card
        assertEquals(5, palette.count { !contrasteSuffisant(it, surfaceRaised) })
    }

    @Test
    fun `le plancher lui-meme`() {
        assertEquals(3.0, CONTRASTE_PLANCHER, 0.0)
        // #5335B3 is the worst of the palette on `surface`, and the lowest one it reports
        assertFalse(contrasteSuffisant(0x5335B3, surface))
        assertTrue(contraste(0x5335B3, surface) < CONTRASTE_PLANCHER)
        // white always clears it, which is the sanity end of the scale
        assertTrue(contrasteSuffisant(0xFFFFFF, surface))
    }

    /** an out of range channel is clamped rather than producing a meaningless ratio */
    @Test
    fun `les canaux hors bornes sont ramenes`() {
        assertEquals(luminance(255, 255, 255), luminance(300, 400, 999), 0.0001)
        assertEquals(luminance(0, 0, 0), luminance(-5, -1, -200), 0.0001)
    }
}

package li.velvetchat.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * the decode ceiling, the one place a decompression bomb is stopped
 *
 * the bytes reaching `decoderMedia` are not ours. they come from `mediaBytes`, so from whatever
 * anyone posted in a room, and from link previews, so from whatever a remote site chose to
 * serve. a png declaring 60000 by 60000 fits in a few kilobytes and asks for fourteen gigabytes
 * once decoded
 *
 * real images are already covered by `DecodageJvmTest`, which decodes for real. here we test
 * the rule, and above all the two cases a test written the obvious way would have let through
 */
class SurfaceRefuseeTest {

    @Test
    fun `une photo ordinaire passe`() {
        assertFalse(surfaceRefusee(4000, 3000))      // a phone
        assertFalse(surfaceRefusee(7680, 4320))      // 8K
        assertFalse(surfaceRefusee(12000, 9000))     // 108 megapixels, the top of the market
        assertFalse(surfaceRefusee(1, 1))
    }

    /**
     * the case the file is named after
     *
     * 60000 x 60000 is 3.6 billion, so fourteen gigabytes once decoded
     */
    @Test
    fun `une bombe carree est refusee`() {
        assertTrue(surfaceRefusee(60_000, 60_000))
    }

    /**
     * **the hole that actually existed**
     *
     * android's subsampling factor was computed from the width alone. an image 100 wide kept a
     * factor of one whatever its height, and was decoded whole. the rule has to look at the
     * surface, not at one side
     */
    @Test
    fun `une image etroite et tres haute est refusee`() {
        assertTrue(surfaceRefusee(100, 100_000_000))
        assertTrue(surfaceRefusee(100_000_000, 100))
        // and it does pass the width test that was not enough on its own
        assertTrue(100 <= 1440 * 2, "the premise of the hole: width alone says nothing")
    }

    /**
     * **the second trap, and a silent one**
     *
     * `60000 * 60000` as an `Int` comes back negative. a surface check written without `Long`
     * therefore concludes the image is tiny, and lets through exactly the files it exists to
     * stop. pinned here so nobody "simplifies" the rule by dropping the conversions
     */
    @Test
    fun `le produit en Int deborde et serait negatif`() {
        val enInt = 60_000 * 60_000
        assertTrue(enInt < 0, "if this breaks, Int changed size and the comment is lying")
        assertTrue(surfaceRefusee(60_000, 60_000), "the rule must hold despite that overflow")
    }

    @Test
    fun `une dimension nulle ou negative est refusee`() {
        assertTrue(surfaceRefusee(0, 100))
        assertTrue(surfaceRefusee(100, 0))
        assertTrue(surfaceRefusee(-1, 100))
        // `outWidth` is -1 when BitmapFactory did not recognise the format
        assertTrue(surfaceRefusee(-1, -1))
    }

    /**
     * the ceiling sits at 150 megapixels, and this test is why it is there
     *
     * it was at 100. `une photo ordinaire passe` failed on 12000 x 9000, the 108 megapixels of
     * a top end phone: a holiday photograph would have been refused outright. the comment, for
     * its part, claimed to cover that case. it was wrong and the test said so
     */
    @Test
    fun `le plafond lui-meme est inclusif`() {
        assertFalse(surfaceRefusee(15_000, 10_000), "150 megapixels exactly must pass")
        assertTrue(surfaceRefusee(15_001, 10_000))
    }
}

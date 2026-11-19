package li.velvetchat.app.ui

import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * the desktop decoder, actually verified
 *
 * this test exists because nothing else runs that code yet: the desktop shell comes later, and
 * until then the skia `actual` would only be compiled. compiling does not say a gif comes out
 * animated, nor that a large image comes out reduced, it only says the names exist. on android the
 * same machinery has been running in the app for months, and on the jvm these few cases are all
 * there is
 *
 * the images are built here rather than read from disk: a sample file gets lost, gets replaced, and
 * makes the test depend on what somebody copied one day
 */
class DecodageJvmTest {

    @Test
    fun unGifAnimeRessortAnimeEtAvecSesDimensions() = runBlocking {
        val decode = assertNotNull(decoderPourAffichage(gifAnime(trames = 4, l = 60, h = 40)))
        assertTrue(decode.animee, "un GIF de quatre trames doit ressortir animé")
        // an animated decode carries no still image, which is what tells the caller not to cache
        // it, playback state not being shareable
        assertNull(decode.bitmapFixe)
        assertEquals(60, decode.largeur)
        assertEquals(40, decode.hauteur)
    }

    @Test
    fun unePngFixeRessortFixeEtPrêteÀMettreEnCache() = runBlocking {
        val decode = assertNotNull(decoderPourAffichage(png(30, 20)))
        assertTrue(!decode.animee)
        val bitmap = assertNotNull(decode.bitmapFixe)
        assertEquals(30, bitmap.width)
        assertEquals(20, bitmap.height)
    }

    @Test
    fun desOctetsQuiNeSontPasUneImageNeLevaientPas() = runBlocking {
        assertNull(decoderPourAffichage(ByteArray(64) { it.toByte() }))
        assertNull(decoderMedia(ByteArray(64) { it.toByte() }, 100))
    }

    /**
     * [decoderMedia]'s contract: bring it down to twice the requested width, and do not enlarge
     * what is already smaller
     */
    @Test
    fun uneGrandeImageEstRameneeAuDoubleDeLaLargeurUtile() {
        val reduite = assertNotNull(decoderMedia(png(1200, 600), largeurCible = 100))
        assertEquals(200, reduite.width)
        assertEquals(100, reduite.height)

        val petite = assertNotNull(decoderMedia(png(80, 40), largeurCible = 100))
        assertEquals(80, petite.width, "une image déjà petite ne doit pas être agrandie")
    }

    private fun png(l: Int, h: Int): ByteArray {
        val img = BufferedImage(l, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.ORANGE
        g.fillRect(0, 0, l, h)
        g.dispose()
        val sortie = java.io.ByteArrayOutputStream()
        ImageIO.write(img, "png", sortie)
        return sortie.toByteArray()
    }

    private fun gifAnime(trames: Int, l: Int, h: Int): ByteArray {
        val ecrivain = ImageIO.getImageWritersByFormatName("gif").next()
        val sortie = java.io.ByteArrayOutputStream()
        MemoryCacheImageOutputStream(sortie).use { flux ->
            ecrivain.output = flux
            ecrivain.prepareWriteSequence(null)
            repeat(trames) { t ->
                val img = BufferedImage(l, h, BufferedImage.TYPE_INT_RGB)
                val g = img.createGraphics()
                g.color = Color.getHSBColor(t.toFloat() / trames, 0.8f, 0.9f)
                g.fillRect(0, 0, l, h)
                g.dispose()
                ecrivain.writeToSequence(IIOImage(img, null, null), null)
            }
            ecrivain.endWriteSequence()
        }
        ecrivain.dispose()
        return sortie.toByteArray()
    }
}

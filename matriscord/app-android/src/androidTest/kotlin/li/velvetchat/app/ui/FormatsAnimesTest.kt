package li.velvetchat.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import androidx.test.platform.app.InstrumentationRegistry
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.loader.ByteBufferLoader
import java.nio.ByteBuffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * which animated formats can this android actually decode?
 *
 * the question is not academic: the avatar decorations and the profile effects are published as
 * apng, measured on the server (`acTL` present, 661 and 352 objects). on screen they did not move,
 * and the explanation was in one line of documentation you can misread, `ImageDecoder` announcing
 * "animated GIF and animated WebP", apng not appearing in it. assuming it would have been enough to
 * write the fix, and measuring it avoids writing the wrong one a second time
 *
 * this test decodes two eight pixel files, one apng and one animated webp, and looks at the class
 * returned. it stays in place as a guard: the day the platform learns apng, it will fail, and that
 * will be the signal that we can stop converting
 */
class FormatsAnimesTest {

    private fun decode(nom: String): Any {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val octets = ctx.assets.open(nom).use { it.readBytes() }
        return ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(ByteBuffer.wrap(octets)),
        )
    }

    /** animated webp animates, and it is the conversion target, so it has to hold */
    @Test
    fun leWebpAnimeDonneUnDrawableAnime() {
        assertTrue(decode("anime.webp") is AnimatedImageDrawable)
    }

    /**
     * apng does not animate: `ImageDecoder` returns a still image, its first frame
     *
     * for a decoration that is a frozen ring, and for a profile effect, whose first frame is empty,
     * it is a transparent image, so "the effect is not visible". one missing decoder, two reported
     * faults
     */
    @Test
    fun lApngNeDonnePasUnDrawableAnime() {
        assertTrue(decode("anime.png") !is AnimatedImageDrawable)
    }

    /**
     * and the real decoration does move, once put through the right decoder
     *
     * the two attempts above are on eight pixels made here, and this one takes a file from the
     * published catalogue, 68 kb, as the phone receives it from the server
     *
     * the proof is not that `isRunning` answers `true`: a flag turns `true` without anything
     * moving. it is that two successive renders differ. it is the only measurement that would have
     * failed before this fix, and it is the one the batch was missing, where "animated decorations"
     * was written, compiled, and never seen to move
     */
    @Test
    fun uneDecorationDuCatalogueChangeDImage() {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        // a real shop decoration is not ours to publish, so a public clone has none: skipped there
        val octets = runCatching { ctx.assets.open("decoration-reelle.png").use { it.readBytes() } }
            .getOrNull()
        org.junit.Assume.assumeTrue("decoration-reelle.png absent", octets != null)
        octets!!
        val dessin = APNGDrawable(
            object : ByteBufferLoader() {
                override fun getByteBuffer(): ByteBuffer = ByteBuffer.wrap(octets)
            },
        )
        val cote = 96
        dessin.setBounds(0, 0, cote, cote)
        dessin.start()

        fun rendu(): ByteArray {
            val bmp = Bitmap.createBitmap(cote, cote, Bitmap.Config.ARGB_8888)
            dessin.draw(Canvas(bmp))
            val tampon = ByteBuffer.allocate(bmp.byteCount)
            bmp.copyPixelsToBuffer(tampon)
            return tampon.array()
        }

        // let the decoder produce a first frame before comparing: on the very first call it has
        // decoded nothing yet, and two empty canvases would be equal for the wrong reason
        var premier = rendu()
        var essais = 0
        while (premier.all { it == 0.toByte() } && essais++ < 40) {
            Thread.sleep(50)
            premier = rendu()
        }
        assertFalse("la première image est restée vide", premier.all { it == 0.toByte() })

        var bouge = false
        repeat(40) {
            Thread.sleep(50)
            if (!rendu().contentEquals(premier)) {
                bouge = true
                return@repeat
            }
        }
        assertTrue("la décoration n'a jamais changé d'image en deux secondes", bouge)
    }
}

package li.velvetchat.app.ui

import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * the desktop's apng decoder
 *
 * it exists because skia stops at the default image, measured on a real decoration: `frameCount`
 * answers 1 while the `acTL` chunk is right there. the symptom on the desktop was an avatar
 * decoration frozen at zero pixels of difference over three seconds
 *
 * the test apng is assembled here, and that is what makes the test honest: the pixels come from
 * `ImageIO` and the animation chunks are written by hand from the spec. so the decoder faces a file
 * it did not produce, unlike what you would get by reusing its own writing primitives
 *
 * keeping a real apng as a resource would have put a megabyte in the repo to check the same thing
 */
class ApngTest {

    @Test
    fun unApngDeTroisTramesRessortAnime() = runBlocking {
        val octets = apngDeTest()
        assertTrue(estApngJvm(octets), "le fichier doit porter son bloc acTL")

        val decode = assertNotNull(decoderPourAffichage(octets))
        assertTrue(decode.animee, "trois trames, donc animé")
        assertEquals(16, decode.largeur)
        assertEquals(16, decode.hauteur)
    }

    /**
     * the case that motivated the whole file: without the detour through the apng decoder, skia
     * returned the default image and the animation was lost with no error
     */
    @Test
    fun sansLeDetourSkiaNAnnonceQuUneSeuleTrame() {
        val codec = org.jetbrains.skia.Codec.makeFromData(
            org.jetbrains.skia.Data.makeFromBytes(apngDeTest()),
        )
        assertEquals(1, codec.frameCount, "si Skia apprend l'APNG, ce test tombe et le détour peut partir")
    }

    @Test
    fun lesTramesSontDifferentesEntreElles() {
        val decode = assertNotNull(decoderApng(apngDeTest()))
        assertEquals(3, decode.trames.size)
        // three flat fields of different colours: two identical frames would mean the composition
        // placed nothing, which is the classic fault of an apng reader
        val pixels = decode.trames.map { trame -> trame.toPixelMap()[8, 8] }
        assertEquals(3, pixels.toSet().size, "les trois trames doivent différer")
    }

    @Test
    fun lesDureesSuiventLeFichier() {
        val decode = assertNotNull(decoderApng(apngDeTest()))
        // 20/1000 s declared per frame gives 20 ms
        assertTrue(decode.durees.all { it == 20 }, "durées lues : ${decode.durees.toList()}")
    }

    @Test
    fun unPngOrdinaireNEstPasPrisPourUnApng() {
        assertTrue(!estApngJvm(pngUni(Color.BLUE)))
    }

    private fun apngDeTest(): ByteArray {
        val couleurs = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val trames = couleurs.map { blocsDe(pngUni(it)) }
        val premier = trames.first()

        val sortie = ByteArrayOutputStream()
        sortie.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        ecrire(sortie, "IHDR", premier.first { it.first == "IHDR" }.second)
        // acTL: three frames, infinite loop. it precedes the idat, as the spec requires
        ecrire(sortie, "acTL", entier(3) + entier(0))

        var sequence = 0
        trames.forEachIndexed { i, blocs ->
            ecrire(sortie, "fcTL", controle(sequence++, largeur = 16, hauteur = 16))
            blocs.filter { it.first == "IDAT" }.forEach { (_, donnees) ->
                if (i == 0) {
                    // the first frame is the default image: its `fcTL` precedes it, so it belongs
                    // to the animation
                    ecrire(sortie, "IDAT", donnees)
                } else {
                    ecrire(sortie, "fdAT", entier(sequence++) + donnees)
                }
            }
        }
        ecrire(sortie, "IEND", ByteArray(0))
        return sortie.toByteArray()
    }

    private fun controle(sequence: Int, largeur: Int, hauteur: Int): ByteArray =
        entier(sequence) + entier(largeur) + entier(hauteur) + entier(0) + entier(0) +
            byteArrayOf(0, 20, 3, -24) +
            byteArrayOf(0, 0)

    private fun pngUni(couleur: Color): ByteArray {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = couleur
        g.fillRect(0, 0, 16, 16)
        g.dispose()
        val sortie = ByteArrayOutputStream()
        ImageIO.write(img, "png", sortie)
        return sortie.toByteArray()
    }

    private fun blocsDe(png: ByteArray): List<Pair<String, ByteArray>> {
        val blocs = mutableListOf<Pair<String, ByteArray>>()
        var i = 8
        while (i + 8 <= png.size) {
            val taille = ((png[i].toInt() and 0xFF) shl 24) or ((png[i + 1].toInt() and 0xFF) shl 16) or
                ((png[i + 2].toInt() and 0xFF) shl 8) or (png[i + 3].toInt() and 0xFF)
            val type = String(png, i + 4, 4, Charsets.US_ASCII)
            blocs += type to png.copyOfRange(i + 8, i + 8 + taille)
            if (type == "IEND") break
            i += 12 + taille
        }
        return blocs
    }

    private fun ecrire(sortie: ByteArrayOutputStream, type: String, donnees: ByteArray) {
        sortie.write(entier(donnees.size))
        val nom = type.toByteArray(Charsets.US_ASCII)
        sortie.write(nom)
        sortie.write(donnees)
        sortie.write(entier(CRC32().apply { update(nom); update(donnees) }.value.toInt()))
    }

    private fun entier(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )
}

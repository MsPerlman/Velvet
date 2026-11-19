package li.velvetchat.sonde

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.random.Random

private const val GRAINE = 20260909L

fun imagesDeTest(combien: Int): List<ImageBitmap> {
    val alea = Random(GRAINE)
    return List(combien) { i ->
        val (l, h) = when (i % 3) {
            0 -> 960 to 540
            1 -> 640 to 800
            else -> 400 to 300
        }
        val img = BufferedImage(l, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) {
            for (x in 0 until l) {
                val bruit = alea.nextInt(24)
                img.setRGB(
                    x, y,
                    Color(
                        (x * 255 / l + bruit).coerceIn(0, 255),
                        (y * 255 / h + bruit).coerceIn(0, 255),
                        ((x + y) * 127 / (l + h) + bruit).coerceIn(0, 255),
                    ).rgb,
                )
            }
        }
        decoderParSkia(encoder(img, "png"))
    }
}

fun animationDeTest(trames: Int = 24, cote: Int = 400): List<ImageBitmap> {
    val octets = encoderAnime(trames, cote, cote * 3 / 4)
    val donnees = org.jetbrains.skia.Data.makeFromBytes(octets)
    val codec = org.jetbrains.skia.Codec.makeFromData(donnees)
    val info = codec.imageInfo
    return List(codec.frameCount) { i ->
        val bitmap = org.jetbrains.skia.Bitmap()
        bitmap.allocPixels(info)
        codec.readPixels(bitmap, i)
        bitmap.setImmutable()
        org.jetbrains.skia.Image.makeFromBitmap(bitmap).toComposeImageBitmap()
    }
}

fun codecDeTest(trames: Int = 24, cote: Int = 400): org.jetbrains.skia.Codec =
    org.jetbrains.skia.Codec.makeFromData(
        org.jetbrains.skia.Data.makeFromBytes(encoderAnime(trames, cote, cote * 3 / 4)),
    )

fun poidsAnimation(trames: Int = 24, cote: Int = 400): Int =
    encoderAnime(trames, cote, cote * 3 / 4).size

private fun encoderAnime(trames: Int, l: Int, h: Int): ByteArray {
    val ecrivain = ImageIO.getImageWritersByFormatName("gif").next()
    val sortie = java.io.ByteArrayOutputStream()
    MemoryCacheImageOutputStream(sortie).use { flux ->
        ecrivain.output = flux
        ecrivain.prepareWriteSequence(null)
        repeat(trames) { t ->
            val img = BufferedImage(l, h, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            val phase = t.toFloat() / trames
            for (y in 0 until h step 4) {
                val teinte = ((y.toFloat() / h + phase) % 1f)
                g.color = Color.getHSBColor(teinte, 0.7f, 0.9f)
                g.fillRect(0, y, l, 4)
            }
            g.color = Color.WHITE
            g.fillOval((phase * (l - 60)).toInt(), h / 2 - 30, 60, 60)
            g.dispose()
            ecrivain.writeToSequence(IIOImage(img, null, null), null)
        }
        ecrivain.endWriteSequence()
    }
    ecrivain.dispose()
    return sortie.toByteArray()
}

private fun encoder(img: BufferedImage, format: String): ByteArray {
    val sortie = java.io.ByteArrayOutputStream()
    ImageIO.write(img, format, sortie)
    return sortie.toByteArray()
}

private fun decoderParSkia(octets: ByteArray): ImageBitmap =
    org.jetbrains.skia.Image.makeFromEncoded(octets).toComposeImageBitmap()

package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO

fun main(): Unit = runBlocking {
    val creds = SendImage::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")
    val roomId = creds.getProperty("testRoomId") ?: error("testRoomId absent")

    val work = File("build/media-work").apply { mkdirs() }
    val fichier = File(work, "photo-test-${System.currentTimeMillis()}.png")
    ImageIO.write(imageDeTest(1600, 1200), "png", fichier)
    println("Image écrite : ${fichier.name} (${fichier.length() / 1024} Ko)")

    val dir = File("build/engine-session")
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dir.apply { mkdirs() }.absolutePath,
        cachePath = File("build/engine-session-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()
    engine.restore(WindowsSessionStore(dir).load() ?: error("lancer d'abord verifyEngine"))
    engine.startSync()
    delay(12_000)

    val formats = listOf(
        1200 to 1600,
        1600 to 900,
        1000 to 1000,
        900 to 1600,
        1600 to 1000,
        1400 to 1050,   // 4:3
    )
    formats.forEachIndexed { i, (l, h) ->
        val f = File(work, "galerie-${i + 1}-${System.currentTimeMillis()}.png")
        ImageIO.write(imageDeTest(l, h), "png", f)
        engine.sendMedia(roomId, f.absolutePath, "Image ${i + 1} — ${l}×$h")
        println("  ✓ ${i + 1}/${formats.size} — ${l}×$h")
        delay(1_200)
    }
    println("✓ galerie envoyée dans $roomId")

    delay(15_000)
    engine.stopSync()
}

private fun imageDeTest(largeur: Int, hauteur: Int): BufferedImage {
    val img = BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    for (y in 0 until hauteur) {
        g.color = Color(31, 182 - y * 120 / hauteur, 168 + y * 60 / hauteur)
        g.drawLine(0, y, largeur, y)
    }
    g.color = Color(224, 87, 91)
    g.fillOval(largeur / 6, hauteur / 4, largeur / 3, hauteur / 3)
    g.color = Color(217, 164, 65)
    g.fillRect(largeur / 2, hauteur / 2, largeur / 3, hauteur / 4)
    g.color = Color.WHITE
    g.font = Font("SansSerif", Font.BOLD, hauteur / 12)
    g.drawString("Matriscord", largeur / 12, hauteur / 6)
    g.dispose()
    return img
}

private object SendImage

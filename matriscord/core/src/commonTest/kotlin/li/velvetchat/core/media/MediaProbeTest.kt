package li.velvetchat.core.media

import li.velvetchat.core.model.MediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * what these tests protect: the type and dimensions we announce decide how other clients draw the
 * attachment. a mistake here is invisible on your own screen
 */
class MediaProbeTest {

    @Test
    fun `reconnaît un PNG et lit ses dimensions`() {
        val png = png(width = 1920, height = 1080)
        assertEquals("image/png", MediaProbe.mimeType(png, "peu-importe.bin"))
        assertEquals(MediaProbe.Dimensions(1920, 1080), MediaProbe.dimensions(png))
    }

    @Test
    fun `reconnaît un GIF et lit ses dimensions en petit-boutiste`() {
        val gif = gif(width = 320, height = 240)
        assertEquals("image/gif", MediaProbe.mimeType(gif, "x"))
        assertEquals(MediaProbe.Dimensions(320, 240), MediaProbe.dimensions(gif))
    }

    /**
     * the case that justifies the parser: a jpeg's size is not at a fixed offset. this file puts a
     * 16 byte APP0 segment before the SOF, and a reader assuming a fixed position would read bytes
     * out of the jfif table and return imaginary dimensions
     */
    @Test
    fun `traverse les segments d'un JPEG pour atteindre le SOF`() {
        val jpeg = jpegWithApp0(width = 800, height = 600)
        assertEquals("image/jpeg", MediaProbe.mimeType(jpeg, "x"))
        assertEquals(MediaProbe.Dimensions(800, 600), MediaProbe.dimensions(jpeg))
    }

    @Test
    fun `reconnaît un WebP étendu, celui des images animées`() {
        val webp = webpVp8x(width = 512, height = 256)
        assertEquals("image/webp", MediaProbe.mimeType(webp, "x"))
        assertEquals(MediaProbe.Dimensions(512, 256), MediaProbe.dimensions(webp))
    }

    @Test
    fun `distingue WAV de WebP, qui partagent le conteneur RIFF`() {
        assertEquals("audio/wav", MediaProbe.mimeType(riff("WAVE"), "x"))
        assertEquals("image/webp", MediaProbe.mimeType(riff("WEBP"), "x"))
    }

    /** a renamed image is still an image, which is the entire point of sniffing the content */
    @Test
    fun `le contenu l'emporte sur l'extension`() {
        assertEquals("image/png", MediaProbe.mimeType(png(10, 10), "document.txt"))
    }

    /** but a `.md` and a `.txt` have the same bytes, and there only the name can decide */
    @Test
    fun `retombe sur l'extension quand le contenu ne dit rien`() {
        val texte = "# titre\n".encodeToByteArray()
        assertEquals("text/markdown", MediaProbe.mimeType(texte, "notes.md"))
        assertEquals("text/plain", MediaProbe.mimeType(texte, "notes.txt"))
    }

    @Test
    fun `retombe sur un flux d'octets quand rien n'est reconnaissable`() {
        val inconnu = byteArrayOf(0x42, 0x13, 0x37, 0x00, 0x01)
        assertEquals("application/octet-stream", MediaProbe.mimeType(inconnu, "truc.zzz"))
        assertEquals(MediaKind.FILE, MediaProbe.kindOf(MediaProbe.mimeType(inconnu, "truc.zzz")))
    }

    @Test
    fun `classe les types MIME par catégorie de rendu`() {
        assertEquals(MediaKind.IMAGE, MediaProbe.kindOf("image/png"))
        assertEquals(MediaKind.VIDEO, MediaProbe.kindOf("video/mp4"))
        assertEquals(MediaKind.AUDIO, MediaProbe.kindOf("audio/mpeg"))
        assertEquals(MediaKind.FILE, MediaProbe.kindOf("application/pdf"))
    }

    /**
     * a truncated header has to give "i do not know" rather than an exception. the engine reads a
     * chunk of a file, and nothing guarantees the marker it is after is inside it
     */
    @Test
    fun `ne lève pas sur un en-tête tronqué`() {
        assertNull(MediaProbe.dimensions(png(10, 10).copyOf(12)))
        assertNull(MediaProbe.dimensions(byteArrayOf()))
        assertNull(MediaProbe.dimensions(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }

    /** zero pixels is not a dimension. better to announce nothing than to announce 0 */
    @Test
    fun `refuse une dimension nulle`() {
        assertNull(MediaProbe.dimensions(png(0, 100)))
    }

    // -- header builders --------------------------------------------------------------------

    private fun png(width: Int, height: Int) = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
    ) + be32(width) + be32(height) + ByteArray(8)

    private fun gif(width: Int, height: Int) =
        "GIF89a".encodeToByteArray() + le16(width) + le16(height) + ByteArray(4)

    private fun jpegWithApp0(width: Int, height: Int) =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
            byteArrayOf(0xFF.toByte(), 0xE0.toByte()) + be16(16) + ByteArray(14) +
            byteArrayOf(0xFF.toByte(), 0xC0.toByte()) + be16(17) + byteArrayOf(8) +
            be16(height) + be16(width) + ByteArray(10)

    private fun webpVp8x(width: Int, height: Int) =
        "RIFF".encodeToByteArray() + ByteArray(4) + "WEBP".encodeToByteArray() +
            "VP8X".encodeToByteArray() + ByteArray(8) +
            le24(width - 1) + le24(height - 1) + ByteArray(4)

    private fun riff(form: String) =
        "RIFF".encodeToByteArray() + ByteArray(4) + form.encodeToByteArray() + ByteArray(20)

    private fun be32(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )
    private fun be16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())
    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte())
    private fun le24(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte())
}

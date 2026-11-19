package li.velvetchat.core.media

import li.velvetchat.core.model.MediaKind

/**
 * works out what a file is from its bytes, not its name
 *
 * why this exists instead of a platform call: the engine compiles for android and for desktop
 * from one source. `javax.imageio` isn't on android, `BitmapFactory` isn't on the jvm, and
 * `Files.probeContentType` answers from the extension, so it's wrong the moment a file is badly
 * named. reading the headers ourselves is the only answer that gives the same result on both
 * sides
 *
 * why it's worth it: the type we announce decides how other clients will show the attachment,
 * and the dimensions let them reserve the space before downloading anything. getting it wrong
 * breaks nothing here, it breaks the display for everyone else, which is far harder to notice
 *
 * what isn't covered, on purpose: formats missing from the table fall back to the extension,
 * then to `application/octet-stream`, which shows as a file. a readable degradation, not a bug
 */
object MediaProbe {

    /** enough for the header of every format we know, jpeg sof included */
    const val HEADER_BYTES: Int = 64 * 1024

    /**
     * mime type from the content, falling back to the extension
     *
     * @param header the first bytes of the file, [HEADER_BYTES] is plenty
     */
    fun mimeType(header: ByteArray, filename: String): String =
        sniff(header) ?: byExtension(filename) ?: "application/octet-stream"

    /** category derived from the mime type, and it is what picks the rendering */
    fun kindOf(mimeType: String): MediaKind = when {
        mimeType.startsWith("image/") -> MediaKind.IMAGE
        mimeType.startsWith("video/") -> MediaKind.VIDEO
        mimeType.startsWith("audio/") -> MediaKind.AUDIO
        else -> MediaKind.FILE
    }

    /**
     * size in pixels, or null when the format is not recognised or the header is truncated
     *
     * null is not a failure, it means we do not know, and the caller should then send the media
     * without dimensions rather than invent some
     */
    fun dimensions(header: ByteArray): Dimensions? = when {
        header.startsWith(PNG) -> pngDimensions(header)
        header.startsWith(GIF87) || header.startsWith(GIF89) -> gifDimensions(header)
        header.startsWith(JPEG) -> jpegDimensions(header)
        header.isRiff("WEBP") -> webpDimensions(header)
        header.startsWith(BMP) -> bmpDimensions(header)
        else -> null
    }

    data class Dimensions(val width: Int, val height: Int)

    // type sniffing

    private fun sniff(h: ByteArray): String? = when {
        h.startsWith(PNG) -> "image/png"
        h.startsWith(JPEG) -> "image/jpeg"
        h.startsWith(GIF87) || h.startsWith(GIF89) -> "image/gif"
        h.isRiff("WEBP") -> "image/webp"
        h.startsWith(BMP) -> "image/bmp"
        h.isRiff("WAVE") -> "audio/wav"
        h.startsWith(OGG) -> "audio/ogg"
        h.startsWith(FLAC) -> "audio/flac"
        h.startsWith(ID3) -> "audio/mpeg"
        h.isMp3FrameSync() -> "audio/mpeg"
        h.startsWith(MATROSKA) -> matroskaMime(h)
        h.hasFtyp() -> ftypMime(h)
        h.startsWith(PDF) -> "application/pdf"
        h.startsWith(ZIP) -> "application/zip"
        h.startsWith(GZIP) -> "application/gzip"
        else -> null
    }

    /**
     * mp4 and quicktime share a structure, and the `ftyp` brand is what tells them apart. it
     * does not sit at the very start of the file, it comes after the size
     */
    private fun ByteArray.hasFtyp(): Boolean = size >= 12 && asciiAt(4, "ftyp")

    private fun ftypMime(h: ByteArray): String = when {
        h.asciiAt(8, "qt  ") -> "video/quicktime"
        h.asciiAt(8, "M4A ") || h.asciiAt(8, "M4B ") -> "audio/mp4"
        else -> "video/mp4"
    }

    /** webm is a matroska profile: same container, same ebml header, different mime */
    private fun matroskaMime(h: ByteArray): String =
        if (h.containsAscii("webm", within = 64)) "video/webm" else "video/x-matroska"

    /** an mp3 with no id3 tag starts straight on the frame sync, eleven bits set */
    private fun ByteArray.isMp3FrameSync(): Boolean =
        size >= 2 && this[0] == 0xFF.toByte() && (this[1].toInt() and 0xE0) == 0xE0

    private fun byExtension(filename: String): String? =
        BY_EXTENSION[filename.substringAfterLast('.', "").lowercase()]

    // dimensions

    /** ihdr is always the first chunk, so width and height sit at a fixed offset */
    private fun pngDimensions(h: ByteArray): Dimensions? =
        if (h.size < 24) null
        else Dimensions(h.beInt(16), h.beInt(20)).takeIf { it.valid() }

    private fun gifDimensions(h: ByteArray): Dimensions? =
        if (h.size < 10) null
        else Dimensions(h.leShort(6), h.leShort(8)).takeIf { it.valid() }

    /** dib header: signed ints, height negative when the image is stored top down */
    private fun bmpDimensions(h: ByteArray): Dimensions? =
        if (h.size < 26) null
        else Dimensions(h.leInt(18), kotlin.math.abs(h.leInt(22))).takeIf { it.valid() }

    /**
     * jpeg has no fixed offset: the size lives in an sof marker, preceded by a variable number
     * of segments (exif, thumbnail, quantisation table), so they have to be walked. that is also
     * why [HEADER_BYTES] is generous, an exif thumbnail can push the sof tens of kilobytes in
     */
    private fun jpegDimensions(h: ByteArray): Dimensions? {
        var i = 2
        while (i + 9 < h.size) {
            if (h[i] != 0xFF.toByte()) { i++; continue }
            val marker = h[i + 1].toInt() and 0xFF
            when {
                // padding between segments, a run of 0xff is legal
                marker == 0xFF -> { i++; continue }
                // markers with no payload
                marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7 -> { i += 2; continue }
                // sof0..sof15 carry the size, except dht (c4), jpg (c8) and dac (cc), which
                // fall in the same range without being sof markers
                marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC ->
                    return Dimensions(h.beShort(i + 7), h.beShort(i + 5)).takeIf { it.valid() }
                else -> {
                    val len = h.beShort(i + 2)
                    if (len < 2) return null
                    i += 2 + len
                }
            }
        }
        return null
    }

    /**
     * webp has three encodings and each one keeps the size somewhere else: vp8 (lossy), vp8l
     * (lossless) and vp8x (extended, meaning animation and transparency). an animated file is a
     * vp8x, so skipping that case would mean never sizing a gif converted to webp, which is
     * most of the webp out there
     */
    private fun webpDimensions(h: ByteArray): Dimensions? {
        if (h.size < 30) return null
        return when {
            h.asciiAt(12, "VP8 ") -> Dimensions(h.leShort(26) and 0x3FFF, h.leShort(28) and 0x3FFF)
            h.asciiAt(12, "VP8L") -> {
                // fourteen bits per dimension, packed with no byte alignment
                val bits = (h[21].i() or (h[22].i() shl 8) or (h[23].i() shl 16) or (h[24].i() shl 24))
                Dimensions((bits and 0x3FFF) + 1, ((bits ushr 14) and 0x3FFF) + 1)
            }
            h.asciiAt(12, "VP8X") -> Dimensions(h.le24(24) + 1, h.le24(27) + 1)
            else -> null
        }?.takeIf { it.valid() }
    }

    /** a zero or absurd dimension means we do not know, better to announce nothing */
    private fun Dimensions.valid(): Boolean =
        width in 1..65_535 && height in 1..65_535

    // byte reading

    private fun Byte.i(): Int = toInt() and 0xFF
    private fun ByteArray.beInt(o: Int): Int =
        (this[o].i() shl 24) or (this[o + 1].i() shl 16) or (this[o + 2].i() shl 8) or this[o + 3].i()
    private fun ByteArray.leInt(o: Int): Int =
        (this[o + 3].i() shl 24) or (this[o + 2].i() shl 16) or (this[o + 1].i() shl 8) or this[o].i()
    private fun ByteArray.beShort(o: Int): Int = (this[o].i() shl 8) or this[o + 1].i()
    private fun ByteArray.leShort(o: Int): Int = (this[o + 1].i() shl 8) or this[o].i()
    private fun ByteArray.le24(o: Int): Int =
        this[o].i() or (this[o + 1].i() shl 8) or (this[o + 2].i() shl 16)

    private fun ByteArray.startsWith(magic: ByteArray): Boolean {
        if (size < magic.size) return false
        for (i in magic.indices) if (this[i] != magic[i]) return false
        return true
    }

    /**
     * ascii comparison at a given offset
     *
     * written by hand rather than with `String(bytes, offset, len, charset)`, because that
     * constructor only exists on the jvm and this file has to compile as common code
     */
    private fun ByteArray.asciiAt(offset: Int, text: String): Boolean {
        if (offset + text.length > size) return false
        for (i in text.indices) if (this[offset + i].i() != text[i].code) return false
        return true
    }

    private fun ByteArray.containsAscii(text: String, within: Int): Boolean {
        val last = minOf(size, within) - text.length
        for (o in 0..last) if (asciiAt(o, text)) return true
        return false
    }

    /** riff is the container webp and wav share, and the form at offset 8 decides which */
    private fun ByteArray.isRiff(form: String): Boolean =
        size >= 12 && asciiAt(0, "RIFF") && asciiAt(8, form)

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun ascii(text: String) = ByteArray(text.length) { text[it].code.toByte() }

    private val PNG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG = bytes(0xFF, 0xD8, 0xFF)
    private val GIF87 = ascii("GIF87a")
    private val GIF89 = ascii("GIF89a")
    private val BMP = ascii("BM")
    private val OGG = ascii("OggS")
    private val FLAC = ascii("fLaC")
    private val ID3 = ascii("ID3")
    private val MATROSKA = bytes(0x1A, 0x45, 0xDF, 0xA3)
    private val PDF = ascii("%PDF-")
    private val ZIP = bytes(0x50, 0x4B, 0x03, 0x04)
    private val GZIP = bytes(0x1F, 0x8B)

    /**
     * fallback for formats we cannot recognise from content. text types live here rather than in
     * the sniffer, because a `.md` and a `.txt` have exactly the same bytes and only the name
     * tells them apart
     */
    private val BY_EXTENSION = mapOf(
        "txt" to "text/plain",
        "md" to "text/markdown",
        "csv" to "text/csv",
        "json" to "application/json",
        "xml" to "application/xml",
        "html" to "text/html",
        "svg" to "image/svg+xml",
        "opus" to "audio/opus",
        "aac" to "audio/aac",
        "avi" to "video/x-msvideo",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "apk" to "application/vnd.android.package-archive",
    )
}

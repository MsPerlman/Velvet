package li.velvetchat.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * apng, which skia cannot animate either
 *
 * measured on a real decoration, 944 kb, 288 by 288, `acTL` chunk clearly present:
 * `Codec.makeFromData(...).frameCount` answers 1. exactly android's blind spot, on the other
 * platform and for the same reason: both decoders read the png and stop at the default image
 *
 * the symptom is the same too: a frozen avatar decoration, and an invisible profile effect, its
 * first frame being empty. on the desktop the member column showed a motionless ring, measured at
 * zero pixels of difference over three seconds
 *
 * what this file does, and why it fits in so few lines
 *
 * an apng is a png: same chunks, same header. the animation lives in three extra chunks, `acTL` for
 * how many frames, `fcTL` for where, when and how to compose the next one, and `fdAT` for its
 * pixels, in exactly an `IDAT`'s format preceded by a sequence number
 *
 * rebuilding a frame therefore needs no decoding at all: we copy the file's header with the
 * dimensions corrected, glue the frame's data onto it, and get an ordinary png that skia decodes
 * knowing nothing about apng. the work comes down to cutting up chunks and recomputing three
 * checksums
 *
 * the composition cannot be dodged though: a frame is often a partial rectangle laid over the
 * previous one, with a dispose instruction and a blend instruction. ignoring them gives trails, the
 * classic bug of apng readers written too quickly
 *
 * nothing changes on android: the dedicated decoder has been in place there for a while and is
 * proven on a device. two implementations for one format is not ideal, and replacing a working one
 * for elegance would be less so
 */
internal class ApngDecode(
    val trames: List<ImageBitmap>,
    /** each frame's duration, in milliseconds */
    val durees: IntArray,
    val largeur: Int,
    val hauteur: Int,
)

/** a png's signature. an apng carries the identical one, which is the whole principle */
private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

/** true when these bytes carry an `acTL`, so an animation */
internal fun estApngJvm(octets: ByteArray): Boolean {
    if (octets.size < 16 || !octets.copyOfRange(0, 8).contentEquals(SIGNATURE)) return false
    // the chunk always precedes the `IDAT` per the spec, so there is no point sweeping a megabyte
    val fin = minOf(octets.size - 4, 8192)
    for (i in 8 until fin) {
        if (octets[i] == 0x61.toByte() && octets[i + 1] == 0x63.toByte() &&
            octets[i + 2] == 0x54.toByte() && octets[i + 3] == 0x4C.toByte()
        ) return true
    }
    return false
}

/** a png chunk, as written in the file */
private class Bloc(val type: String, val donnees: ByteArray)

/** what an `fcTL` says about the frame that follows */
private class Controle(
    val largeur: Int,
    val hauteur: Int,
    val x: Int,
    val y: Int,
    val delaiMs: Int,
    val dispose: Int,
    val blend: Int,
)

/**
 * decodes an apng into already composed frames
 *
 * null when the bytes are not a usable apng, and the caller then falls back on the ordinary decode,
 * which will return the default image. we degrade, we do not hide
 */
internal fun decoderApng(octets: ByteArray): ApngDecode? = runCatching {
    val blocs = lireBlocs(octets) ?: return@runCatching null
    val ihdr = blocs.firstOrNull { it.type == "IHDR" } ?: return@runCatching null
    val largeur = entier(ihdr.donnees, 0)
    val hauteur = entier(ihdr.donnees, 4)
    if (largeur <= 0 || hauteur <= 0) return@runCatching null

    // everything that has to accompany the pixels for them to read: palette, transparency, gamma,
    // colour profile. forgetting them gives frames in the wrong colours
    val accompagnants = blocs.filter {
        it.type !in setOf("IHDR", "IDAT", "IEND", "acTL", "fcTL", "fdAT")
    }

    // the frames, in file order. an `fcTL` opens a frame, and the `IDAT`/`fdAT` that follow belong
    // to it
    val controles = mutableListOf<Controle>()
    val donneesParTrame = mutableListOf<MutableList<ByteArray>>()
    var courante: MutableList<ByteArray>? = null

    for (bloc in blocs) {
        when (bloc.type) {
            "fcTL" -> {
                controles += Controle(
                    largeur = entier(bloc.donnees, 4),
                    hauteur = entier(bloc.donnees, 8),
                    x = entier(bloc.donnees, 12),
                    y = entier(bloc.donnees, 16),
                    // the delay is a fraction. a zero denominator means 100 per the spec, and a
                    // zero delay means "as fast as possible", which browsers have always rendered
                    // as 100 ms
                    delaiMs = run {
                        val num = court(bloc.donnees, 20)
                        val den = court(bloc.donnees, 22).let { if (it == 0) 100 else it }
                        val ms = num * 1000 / den
                        if (ms <= 0) 100 else ms
                    },
                    dispose = bloc.donnees[24].toInt(),
                    blend = bloc.donnees[25].toInt(),
                )
                courante = mutableListOf()
                donneesParTrame += courante
            }

            // the default image. it only belongs to the animation if an `fcTL` preceded it,
            // otherwise it is the still meant for readers that ignore apng, and it must not become
            // a frame
            "IDAT" -> courante?.add(bloc.donnees)

            // `fdAT` is a four byte sequence number then exactly an idat's payload
            "fdAT" -> courante?.add(bloc.donnees.copyOfRange(4, bloc.donnees.size))
        }
    }

    if (controles.isEmpty()) return@runCatching null

    val surface = Surface.makeRasterN32Premul(largeur, hauteur)
    val toile = surface.canvas
    val trames = mutableListOf<ImageBitmap>()
    val durees = IntArray(controles.size)
    // the previous frame, kept for `dispose = 2`, put back what was there before me
    var avant: org.jetbrains.skia.Image? = null

    controles.forEachIndexed { i, c ->
        val donnees = donneesParTrame.getOrNull(i).orEmpty()
        if (donnees.isEmpty()) return@forEachIndexed
        val png = reconstruire(ihdr.donnees, c, accompagnants, donnees)
        val image = org.jetbrains.skia.Image.makeFromEncoded(png)

        if (c.dispose == 2) avant = surface.makeImageSnapshot()

        val cible = Rect.makeXYWH(
            c.x.toFloat(), c.y.toFloat(), c.largeur.toFloat(), c.hauteur.toFloat(),
        )
        // `blend = 0` (SOURCE) replaces the area, transparency included, and `blend = 1` (OVER)
        // lays over it. conflating them leaves the previous frame's pixels showing through this
        // one's transparent parts
        if (c.blend == 0) {
            toile.save()
            toile.clipRect(cible)
            toile.clear(0x00000000)
            toile.restore()
        }
        toile.drawImageRect(image, cible)

        trames += surface.makeImageSnapshot().toComposeImageBitmap()
        durees[i] = c.delaiMs

        when (c.dispose) {
            // 1: clear this frame's area before the next one
            1 -> {
                toile.save()
                toile.clipRect(cible)
                toile.clear(0x00000000)
                toile.restore()
            }
            // 2: restore what was there before it
            2 -> avant?.let {
                toile.clear(0x00000000)
                toile.drawImageRect(it, Rect.makeWH(largeur.toFloat(), hauteur.toFloat()), Paint().apply {
                    blendMode = BlendMode.SRC
                })
            }
            // 0: do nothing, the frame stays as the next one's background
        }
    }

    if (trames.isEmpty()) null
    else ApngDecode(trames, durees.copyOf(trames.size), largeur, hauteur)
}.getOrNull()

/** cuts the file into chunks. null when the signature is not a png's */
private fun lireBlocs(octets: ByteArray): List<Bloc>? {
    if (octets.size < 12 || !octets.copyOfRange(0, 8).contentEquals(SIGNATURE)) return null
    val blocs = mutableListOf<Bloc>()
    var i = 8
    while (i + 8 <= octets.size) {
        val taille = entier(octets, i)
        if (taille < 0 || i + 12 + taille > octets.size) break
        val type = String(octets, i + 4, 4, Charsets.US_ASCII)
        blocs += Bloc(type, octets.copyOfRange(i + 8, i + 8 + taille))
        if (type == "IEND") break
        i += 12 + taille
    }
    return blocs.takeIf { it.isNotEmpty() }
}

/**
 * rebuilds a standalone png for one frame
 *
 * the header is the file's, with the frame's dimensions: that is the only thing that changes,
 * because a partial frame is a smaller png that then gets placed in the right spot
 */
private fun reconstruire(
    ihdr: ByteArray,
    c: Controle,
    accompagnants: List<Bloc>,
    donnees: List<ByteArray>,
): ByteArray {
    val entete = ihdr.copyOf()
    ecrireEntier(entete, 0, c.largeur)
    ecrireEntier(entete, 4, c.hauteur)

    val sortie = ByteArrayOutputStream()
    sortie.write(SIGNATURE)
    ecrireBloc(sortie, "IHDR", entete)
    accompagnants.forEach { ecrireBloc(sortie, it.type, it.donnees) }
    donnees.forEach { ecrireBloc(sortie, "IDAT", it) }
    ecrireBloc(sortie, "IEND", ByteArray(0))
    return sortie.toByteArray()
}

private fun ecrireBloc(sortie: ByteArrayOutputStream, type: String, donnees: ByteArray) {
    val taille = ByteArray(4)
    ecrireEntier(taille, 0, donnees.size)
    sortie.write(taille)
    val nom = type.toByteArray(Charsets.US_ASCII)
    sortie.write(nom)
    sortie.write(donnees)
    val crc = CRC32().apply { update(nom); update(donnees) }.value
    val somme = ByteArray(4)
    ecrireEntier(somme, 0, crc.toInt())
    sortie.write(somme)
}

private fun entier(o: ByteArray, i: Int): Int =
    ((o[i].toInt() and 0xFF) shl 24) or ((o[i + 1].toInt() and 0xFF) shl 16) or
        ((o[i + 2].toInt() and 0xFF) shl 8) or (o[i + 3].toInt() and 0xFF)

private fun court(o: ByteArray, i: Int): Int =
    ((o[i].toInt() and 0xFF) shl 8) or (o[i + 1].toInt() and 0xFF)

private fun ecrireEntier(o: ByteArray, i: Int, v: Int) {
    o[i] = (v ushr 24).toByte()
    o[i + 1] = (v ushr 16).toByte()
    o[i + 2] = (v ushr 8).toByte()
    o[i + 3] = v.toByte()
}

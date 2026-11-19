package li.velvetchat.desk

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * our ogg writer against our ogg reader
 *
 * a hand written container is either exactly right or quietly unplayable — wrong checksum,
 * missing header page, bad segment table, all of them give you a file that opens and plays
 * nothing. encoding a tone we know and reading it back with `decoderVocal`, the same code that
 * plays voice messages in the app, is the cheapest way to find out
 */
class VocalAllerRetourTest {

    private val taux = 48_000

    /** 440 hz, the note a tuning fork gives */
    private fun tonalite(secondes: Double): ShortArray {
        val n = (taux * secondes).toInt()
        return ShortArray(n) { i ->
            (sin(2 * PI * 440 * i / taux) * 12_000).toInt().toShort()
        }
    }

    @Test
    fun `un vocal encode se relit`() {
        val source = tonalite(1.0)
        val ogg = EnregistreurVocal.encoderPourEssai(source)

        assertEquals("OggS", String(ogg, 0, 4), "le fichier doit commencer par une page Ogg")
        assertTrue(ogg.size > 1_000, "un fichier d'une seconde ne peut pas peser ${ogg.size} octets")

        val relu = decoderVocal(ogg)
        assertEquals(1, relu.canaux)
        // opus works in 20 ms packets and drops its pre-skip on the way out, so the length
        // comes back close but not exact. a tenth of a second of slack still catches a stream
        // that got truncated
        assertTrue(
            relu.dureeMs in 900..1_100,
            "durée relue ${relu.dureeMs} ms, attendue autour de 1000",
        )

        // silence decodes to the right length too, so check there's actually something in it
        val creux = relu.pcm.asSequence().chunked(2).count { (b, h) ->
            val v = ((b.toInt() and 0xFF) or (h.toInt() shl 8)).toShort().toInt()
            v > 2_000
        }
        assertTrue(creux > 100, "le son relu est muet : $creux échantillons au-dessus du seuil")
    }

    @Test
    fun `la waveform est normalisee`() {
        val barres = EnregistreurVocal.waveformPourEssai(tonalite(2.0))
        assertTrue(barres.isNotEmpty())
        assertTrue(barres.all { it in 0f..1f }, "une barre sort de 0..1 : $barres")
        assertTrue(barres.max() > 0.9f, "la plus forte barre devrait toucher 1 : ${barres.max()}")
    }

    @Test
    fun `un silence ne divise pas par zero`() {
        val barres = EnregistreurVocal.waveformPourEssai(ShortArray(taux))
        assertTrue(barres.all { it == 0f }, "un silence doit donner des barres nulles : $barres")
    }
}

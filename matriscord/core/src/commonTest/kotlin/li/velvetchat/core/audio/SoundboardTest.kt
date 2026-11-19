package li.velvetchat.core.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * the soundboard mixing, checked with no device involved
 *
 * a bug here is only audible in a call between two phones, and it presents as "the sound is weird",
 * which is the worst possible description to debug. each case below matches one precise failure
 * mode: pitch drift, a clipping crack, a sound that drowns the voice, or one that never stops
 */
class SoundboardTest {

    @Test
    fun `un son faible est remonte au niveau de la parole`() {
        // peak at 0.031 needs a gain of about 4, so under the ceiling. otherwise this test would
        // be measuring the ceiling rather than the normalisation, which is what the first version
        // did with a peak at 0.01 wanting a gain of twelve
        val faible = ShortArray(100) { if (it == 50) 1000 else 0 }
        val gain = gainDeNormalisation(faible)
        val crete = normaliser(faible, gain).maxOf { abs(it.toInt()) } / 32768f
        assertTrue(abs(crete - CRETE_PAROLE) < 0.01f, "crête obtenue : $crete")
    }

    @Test
    fun `un son fort est redescendu`() {
        val fort = ShortArray(100) { if (it == 10) 32000 else 0 }
        val crete = normaliser(fort, gainDeNormalisation(fort)).maxOf { abs(it.toInt()) } / 32768f
        assertTrue(crete <= CRETE_PAROLE + 0.01f, "crête obtenue : $crete")
    }

    /**
     * the ceiling. without it an almost silent recording would get multiplied by a hundred and we
     * would bring its hiss up with it
     */
    @Test
    fun `un son quasi silencieux n est pas amplifie sans limite`() {
        val murmure = ShortArray(100) { if (it == 5) 10 else 0 }
        assertEquals(8f, gainDeNormalisation(murmure, gainMax = 8f))
    }

    @Test
    fun `un son vide ne donne aucun gain`() {
        assertEquals(0f, gainDeNormalisation(ShortArray(0)))
        assertEquals(0f, gainDeNormalisation(ShortArray(64)))
    }

    // ---------------------------------------------------------------- mixing

    @Test
    fun `le son est ajoute et non substitue`() {
        val son = LectureSon(ShortArray(4) { 100 }, frequence = 48_000)
        val sortie = ShortArray(4) { 50 }
        son.melanger(sortie, canaux = 1, frequenceSortie = 48_000)
        assertTrue(sortie.all { it.toInt() == 150 }, "obtenu : ${sortie.toList()}")
    }

    /** two sounds fired back to back have to overlap, like on the reference client */
    @Test
    fun `deux sons se superposent`() {
        val sortie = ShortArray(4)
        LectureSon(ShortArray(4) { 100 }, 48_000).melanger(sortie, 1, 48_000)
        LectureSon(ShortArray(4) { 30 }, 48_000).melanger(sortie, 1, 48_000)
        assertTrue(sortie.all { it.toInt() == 130 }, "obtenu : ${sortie.toList()}")
    }

    /**
     * clipping and not wrapping. an overflow that wraps round produces a crack, while saturating
     * only flattens the top of the wave
     */
    @Test
    fun `la somme sature au lieu de reboucler`() {
        val sortie = ShortArray(2) { 30000 }
        LectureSon(ShortArray(2) { 30000 }, 48_000).melanger(sortie, 1, 48_000)
        assertTrue(sortie.all { it.toInt() == 32767 }, "obtenu : ${sortie.toList()}")
    }

    @Test
    fun `un son mono se repartit sur les deux canaux`() {
        val sortie = ShortArray(6)
        LectureSon(ShortArray(3) { 200 }, 48_000).melanger(sortie, canaux = 2, frequenceSortie = 48_000)
        assertTrue(sortie.all { it.toInt() == 200 }, "obtenu : ${sortie.toList()}")
    }

    // ------------------------------------------------------ sample rates

    /**
     * pitch drift, the bug you only hear after a few seconds
     *
     * a sound at 24 kHz mixed into a 48 kHz capture has to last twice as many output samples. if
     * the position advanced a whole frame per output frame it would play twice too fast, and a
     * position rounded on every buffer would accumulate the same error more discreetly
     */
    @Test
    fun `un son plus lent que la capture dure deux fois plus longtemps`() {
        val son = LectureSon(ShortArray(100) { 1000 }, frequence = 24_000)
        var images = 0
        val tampon = ShortArray(10)
        while (son.melanger(tampon, 1, 48_000)) {
            images += 10
            if (images > 1000) break
        }
        // 100 source frames at 24 kHz is 200 at 48 kHz, give or take one buffer
        assertTrue(images in 190..210, "images consommées : $images")
    }

    @Test
    fun `un son plus rapide que la capture dure moins longtemps`() {
        val son = LectureSon(ShortArray(96) { 1000 }, frequence = 48_000)
        var images = 0
        val tampon = ShortArray(8)
        while (son.melanger(tampon, 1, 24_000)) {
            images += 8
            if (images > 1000) break
        }
        assertTrue(images in 40..56, "images consommées : $images")
    }

    @Test
    fun `un son termine ne joue plus rien`() {
        val son = LectureSon(ShortArray(2) { 500 }, 48_000)
        val premier = ShortArray(8)
        son.melanger(premier, 1, 48_000)
        assertTrue(son.fini)

        val second = ShortArray(8)
        assertFalse(son.melanger(second, 1, 48_000))
        assertTrue(second.all { it.toInt() == 0 }, "un son fini a écrit quelque chose")
    }

    /** absurd parameters must not take down the audio thread, which has no net under it */
    @Test
    fun `des parametres absurdes ne plantent pas`() {
        val son = LectureSon(ShortArray(4) { 100 }, 48_000)
        assertFalse(son.melanger(ShortArray(4), canaux = 0, frequenceSortie = 48_000))
        assertFalse(son.melanger(ShortArray(4), canaux = 1, frequenceSortie = 0))
        assertFalse(son.fini)
    }
}

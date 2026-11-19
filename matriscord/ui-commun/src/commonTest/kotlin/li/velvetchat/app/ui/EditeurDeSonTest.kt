package li.velvetchat.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * the six second cap, and the cases where it would otherwise leak
 *
 * this is the one rule of the sound editor that has to hold whatever happens above it: a clip
 * that goes out longer than the cap plays over somebody talking, and it is already published by
 * the time anyone notices. the window in the ui is what makes the cap pleasant, and this is what
 * makes it true
 */
class EditeurDeSonTest {

    private val hz = 48_000
    /** what six seconds is, in samples, at the rate used everywhere here */
    private val plafond = hz * SON_DUREE_MAX_MS / 1000

    @Test
    fun `un fichier plus court part en entier`() {
        // two seconds: there is nothing to trim, and a cap that cut here would be a bug of the
        // opposite sign
        val total = hz * 2
        assertEquals(0 until total, bornesDecoupe(total, hz, 0, total))
    }

    @Test
    fun `un fichier plus long est ramene au plafond`() {
        val total = hz * 60
        val garde = bornesDecoupe(total, hz, 0, total)
        assertEquals(plafond, garde.last + 1 - garde.first)
    }

    @Test
    fun `une fenetre courte est gardee telle quelle`() {
        // the point of the whole change: six seconds is a ceiling, and a sound that wants to be
        // eight tenths of a second stays eight tenths of a second
        val total = hz * 30
        val debut = hz * 5
        val longueur = hz * 8 / 10
        val garde = bornesDecoupe(total, hz, debut, debut + longueur)
        assertEquals(debut until debut + longueur, garde)
    }

    @Test
    fun `une fenetre sous le plancher est etiree vers la droite`() {
        // the handles can be dragged onto each other. the floor stretches the window rather than
        // refusing it, and to the right, because the start is where somebody aimed
        val total = hz * 30
        val debut = hz * 5
        val garde = bornesDecoupe(total, hz, debut, debut + 10)
        assertEquals(debut, garde.first)
        assertEquals(hz * SON_DUREE_MIN_MS / 1000, garde.last + 1 - garde.first)
    }

    @Test
    fun `sous le plancher contre la fin du fichier, c est le depart qui recule`() {
        // no room to the right, so the only way to honour the floor is to take from the left
        val total = hz * 30
        val garde = bornesDecoupe(total, hz, total - 10, total)
        assertEquals(hz * SON_DUREE_MIN_MS / 1000, garde.last + 1 - garde.first)
        assertEquals(total, garde.last + 1)
    }

    @Test
    fun `un fichier plus court que le plancher part en entier`() {
        // a 50 ms clip cannot meet the floor, and refusing it would be worse than letting it
        // through: somebody chose that file on purpose
        val total = hz * 50 / 1000
        assertEquals(0 until total, bornesDecoupe(total, hz, 0, total))
    }

    @Test
    fun `la fenetre garde sa position quand elle est ramenee`() {
        // the cut takes the end, never the start: somebody placed that start deliberately, and
        // moving it would hand back a different passage from the one they aimed at
        val total = hz * 60
        val depart = hz * 10
        val garde = bornesDecoupe(total, hz, depart, total)
        assertEquals(depart, garde.first)
        assertEquals(plafond, garde.last + 1 - garde.first)
    }

    @Test
    fun `une fenetre qui deborde la fin s arrete a la fin`() {
        // a leftover window from a longer previous file, applied to a shorter one. without the
        // clamp this is a `copyOfRange` past the end, so a crash on the add button
        val total = hz * 3
        val garde = bornesDecoupe(total, hz, hz * 2, hz * 90)
        assertEquals(hz * 2 until total, garde)
    }

    @Test
    fun `une fenetre a l envers ne rend rien`() {
        assertTrue(bornesDecoupe(hz * 10, hz, 5_000, 1_000).isEmpty())
    }

    @Test
    fun `un depart negatif est ramene a zero plutot que de lever`() {
        val garde = bornesDecoupe(hz * 10, hz, -4_000, hz * 2)
        assertEquals(0, garde.first)
    }

    @Test
    fun `un fichier vide ou une frequence absurde ne rendent rien`() {
        // both come from a decoder that answered something odd rather than null. returning an
        // empty range sends the caller down its "send the original" path, which is a working
        // outcome, where an exception here would be a crash in a form
        assertTrue(bornesDecoupe(0, hz, 0, 100).isEmpty())
        assertTrue(bornesDecoupe(1_000, 0, 0, 100).isEmpty())
    }

    @Test
    fun `le plafond suit la frequence du fichier`() {
        // 8 kHz is what a phone recording can come back as. the cap is six seconds, not a
        // number of samples, and a fixed sample count would cut it at forty five seconds
        val bas = 8_000
        val garde = bornesDecoupe(bas * 60, bas, 0, bas * 60)
        assertEquals(bas * SON_DUREE_MAX_MS / 1000, garde.last + 1 - garde.first)
    }
}

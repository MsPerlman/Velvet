package li.velvetchat.core.fonds

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * the store keeps images and a history, and the two mistakes it can make are both silent:
 * deleting a file still on screen, or letting a name out of the folder
 */
class MagasinFondsTest {

    private val racine: File = Files.createTempDirectory("fonds").toFile()
    private val magasin = MagasinFonds(racine)

    @AfterTest
    fun nettoyer() {
        racine.deleteRecursively()
    }

    private fun image(n: Int) = "image numero $n".toByteArray()

    @Test
    fun `ranger rend une empreinte et relit les memes octets`() {
        val e = magasin.ranger(image(1))!!
        assertTrue(MagasinFonds.valide(e))
        assertContentEqualsHelper(image(1), magasin.lire(e))
    }

    @Test
    fun `la meme image deux fois ne fait qu'une entree`() {
        val a = magasin.ranger(image(1))!!
        val b = magasin.ranger(image(1))!!
        assertEquals(a, b)
        assertEquals(1, magasin.recents().size)
    }

    @Test
    fun `le dernier pose est en tete`() {
        val a = magasin.ranger(image(1))!!
        val b = magasin.ranger(image(2))!!
        assertEquals(listOf(b, a), magasin.recents().map { it.empreinte })
    }

    @Test
    fun `l historique s arrete a vingt et le plus ancien est efface`() {
        val toutes = (1..MagasinFonds.MAX + 3).map { magasin.ranger(image(it))!! }
        val recents = magasin.recents().map { it.empreinte }
        assertEquals(MagasinFonds.MAX, recents.size)
        assertEquals(toutes.last(), recents.first())
        // the three oldest are out, and their files with them
        toutes.take(3).forEach { assertFalse(magasin.existe(it), "devrait avoir disparu : $it") }
    }

    /**
     * the case that matters: an image still used by the theme drops out of the history, and
     * its file must stay — otherwise the wallpaper on screen vanishes at the twenty first change
     */
    @Test
    fun `une empreinte protegee survit a sa sortie de l historique`() {
        val gardee = magasin.ranger(image(0))!!
        repeat(MagasinFonds.MAX + 2) { i ->
            magasin.ranger(image(i + 1), protegees = setOf(gardee))
        }
        assertFalse(gardee in magasin.recents().map { it.empreinte }, "sortie de la liste")
        assertTrue(magasin.existe(gardee), "mais son fichier doit rester")
    }

    @Test
    fun `oublier retire de la liste et efface`() {
        val e = magasin.ranger(image(1))!!
        magasin.oublier(e)
        assertTrue(magasin.recents().isEmpty())
        assertFalse(magasin.existe(e))
    }

    @Test
    fun `oublier n efface pas une empreinte protegee`() {
        val e = magasin.ranger(image(1))!!
        magasin.oublier(e, protegees = setOf(e))
        assertTrue(magasin.recents().isEmpty())
        assertTrue(magasin.existe(e))
    }

    @Test
    fun `un nom qui n est pas une empreinte est refuse`() {
        listOf(
            "../../../etc/passwd",
            "..",
            "/etc/passwd",
            "C:\\Windows\\win.ini",
            "AAAA",
            "",
            "z".repeat(64),
            "ABCDEF" + "0".repeat(58),
        ).forEach {
            assertFalse(MagasinFonds.valide(it), "devrait etre refuse : $it")
            assertNull(magasin.lire(it), "ne doit rien lire pour : $it")
        }
    }

    @Test
    fun `une ligne d index abimee est sautee sans faire tomber la liste`() {
        val e = magasin.ranger(image(1))!!
        File(racine, "fonds-recents.txt").writeText(
            "pas une empreinte 123\n$e 1700000000\nautre chose\n",
        )
        assertEquals(listOf(e), magasin.recents().map { it.empreinte })
    }

    @Test
    fun `le menage efface ce que plus rien ne nomme`() {
        val garde = magasin.ranger(image(1))!!
        val orphelin = MagasinFonds.empreinteDe(image(99))
        File(racine, "fonds").mkdirs()
        File(racine, "fonds/$orphelin.img").writeBytes(image(99))
        assertEquals(1, magasin.menage(protegees = emptySet()))
        assertTrue(magasin.existe(garde))
        assertFalse(magasin.existe(orphelin))
    }

    private fun assertContentEqualsHelper(attendu: ByteArray, obtenu: ByteArray?) {
        assertTrue(obtenu != null && attendu.contentEquals(obtenu), "octets differents")
    }
}

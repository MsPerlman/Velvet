package li.velvetchat.core.gif

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * these test a security rule, not a formatter
 *
 * what [LienGif.dans] returns decides whether the device will reach a third party machine because
 * a message told it to. so half of these tests are refusals, and they are the ones that matter. a
 * false positive here is not a failed render, it is a working tracker
 */
class LienGifTest {

    @Test
    fun `un lien de gif seul est reconnu`() {
        assertEquals(
            "https://media.klipy.com/gifs/abc.gif",
            LienGif.dans("https://media.klipy.com/gifs/abc.gif"),
        )
    }

    @Test
    fun `les espaces autour ne genent pas`() {
        assertEquals(
            "https://tenor.com/x/y.gif",
            LienGif.dans("  https://tenor.com/x/y.gif\n"),
        )
    }

    @Test
    fun `un mp4 est accepte, la plupart des fournisseurs servent des videos`() {
        assertEquals(
            "https://media.giphy.com/media/xyz/giphy.mp4",
            LienGif.dans("https://media.giphy.com/media/xyz/giphy.mp4"),
        )
    }

    @Test
    fun `une requete apres le chemin ne masque pas l'extension`() {
        assertEquals(
            "https://media.klipy.com/a.gif?token=42",
            LienGif.dans("https://media.klipy.com/a.gif?token=42"),
        )
    }

    // --- the refusals ---

    @Test
    fun `un lien noye dans une phrase n'est pas charge`() {
        assertNull(LienGif.dans("regarde ça https://media.klipy.com/a.gif"))
        assertNull(LienGif.dans("https://media.klipy.com/a.gif mdr"))
    }

    @Test
    fun `un hote quelconque est refuse, meme avec une extension d'image`() {
        // the classic tracker: a one pixel image on someone's own host, timestamping every read
        assertNull(LienGif.dans("https://tracker.exemple.invalid/pixel.gif"))
    }

    @Test
    fun `un suffixe qui imite un hote autorise est refuse`() {
        // `endsWith` on its own would let this one through, and it is not klipy's at all
        assertNull(LienGif.dans("https://klipy.com.exemple.invalid/a.gif"))
    }

    @Test
    fun `un userinfo qui imite un hote autorise est refuse`() {
        // reads like klipy, connects to somewhere else entirely. that is the spoof, and a host
        // list that does not test for it protects nothing
        assertNull(LienGif.dans("https://media.klipy.com@exemple.invalid/a.gif"))
    }

    @Test
    fun `le http en clair est refuse`() {
        assertNull(LienGif.dans("http://media.klipy.com/a.gif"))
    }

    @Test
    fun `un lien sans extension d'image est refuse`() {
        assertNull(LienGif.dans("https://tenor.com/view/quelque-chose-12345"))
    }

    @Test
    fun `un hote nu sans chemin est refuse`() {
        assertNull(LienGif.dans("https://klipy.com"))
    }

    @Test
    fun `un texte ordinaire rend null sans se plaindre`() {
        assertNull(LienGif.dans("bonjour"))
        assertNull(LienGif.dans(""))
        assertNull(LienGif.dans("   "))
    }

    @Test
    fun `la casse de l'hote et de l'extension ne compte pas`() {
        assertEquals(
            "https://MEDIA.KLIPY.COM/A.GIF",
            LienGif.dans("https://MEDIA.KLIPY.COM/A.GIF"),
        )
    }

    @Test
    fun `un port explicite ne masque pas l'hote`() {
        assertEquals(
            "https://media.klipy.com:443/a.gif",
            LienGif.dans("https://media.klipy.com:443/a.gif"),
        )
    }

    @Test
    fun `une page klipy n'est pas un fichier`() {
        // this url renders "Just a moment…", cloudflare's interstitial. no client can get an
        // image out of it, hence the refusal both on display and on send (see `Klipy.versGif`)
        assertNull(LienGif.dans("https://klipy.com/gifs/blair-waldorf-blairhive-1"))
        kotlin.test.assertFalse(
            LienGif.ressembleAUnFichier("https://klipy.com/gifs/blair-waldorf-blairhive-1")
        )
    }

    @Test
    fun `un point dans le domaine ne fait pas une extension`() {
        // the naive version's trap: `substringAfterLast('.')` on the whole url gives "com" for a
        // path with no dot, and "gif" for a host that happens to end in .gif
        kotlin.test.assertFalse(LienGif.ressembleAUnFichier("https://klipy.com/gifs/sans-point"))
        kotlin.test.assertTrue(LienGif.ressembleAUnFichier("https://media.klipy.com/a/b.webp"))
    }

    @Test
    fun `hoteAutorise exige l'egalite ou le point`() {
        kotlin.test.assertTrue(LienGif.hoteAutorise("klipy.com"))
        kotlin.test.assertTrue(LienGif.hoteAutorise("cdn.media.klipy.com"))
        kotlin.test.assertFalse(LienGif.hoteAutorise("notklipy.com"))
        kotlin.test.assertFalse(LienGif.hoteAutorise("klipy.com.evil.invalid"))
    }
}

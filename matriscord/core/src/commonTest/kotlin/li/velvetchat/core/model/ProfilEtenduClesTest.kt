package li.velvetchat.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * the key to field mapping the profile editor saves through
 *
 * the editor keeps a draft of what you changed, keyed by profile key, and writes only those keys
 * on save. a key that [ProfilEtendu.avec] does not know would therefore be editable on screen and
 * silently unsaveable, which is the one failure nobody would report as a bug — it looks like the
 * value simply did not take
 */
class ProfilEtenduClesTest {

    /**
     * **the guard that matters**
     *
     * every key of [ProfilEtendu.CLES] has to round trip: write it, read it back. add a field and
     * forget its branch, and this fails instead of the save doing nothing
     */
    @Test
    fun `toutes les cles font un aller-retour`() {
        ProfilEtendu.CLES.forEach { cle ->
            val pose = ProfilEtendu().avec(cle, "valeur-$cle")
            assertEquals("valeur-$cle", pose.valeur(cle), cle)
        }
    }

    @Test
    fun `les treize cles sont la`() {
        assertEquals(13, ProfilEtendu.CLES.size)
        assertEquals(ProfilEtendu.CLES.distinct().size, ProfilEtendu.CLES.size, "aucun doublon")
        // pronouns is the one that is not ours: the spec reserves `m.`
        assertTrue(ProfilEtendu.CLE_PRONOMS.startsWith("m."))
        ProfilEtendu.CLES.filterNot { it == ProfilEtendu.CLE_PRONOMS }.forEach {
            assertTrue(it.startsWith(Identifiants.PREFIXE), it)
        }
    }

    /** null is a value: it is how a field is removed, and it must not read back as the old one */
    @Test
    fun `null efface le champ`() {
        val plein = ProfilEtendu(bio = "bonjour", decoration = "mxc://a/b")
        assertNull(plein.avec(ProfilEtendu.CLE_BIO, null).bio)
        assertEquals("mxc://a/b", plein.avec(ProfilEtendu.CLE_BIO, null).decoration)
    }

    /** a key that is not ours changes nothing, rather than landing in some field by accident */
    @Test
    fun `une cle inconnue ne touche a rien`() {
        val avant = ProfilEtendu(bio = "bonjour")
        assertSame(avant, avant.avec("com.example.autre", "x"))
        assertNull(avant.valeur("com.example.autre"))
    }

    /**
     * **the case the merge exists for**
     *
     * an image upload writes at once and the profile comes back from the server while a draft is
     * open. the new banner has to appear, and the bio being edited must not be put back to what
     * the server still holds
     */
    @Test
    fun `la fusion garde le brouillon et prend le reste du serveur`() {
        val serveur = ProfilEtendu(bio = "ancienne", banniere = "mxc://nouvelle/banniere")
        val brouillon = ProfilEtendu(bio = "en cours de frappe", banniere = null)
        val fusion = serveur.fusionner(brouillon, setOf(ProfilEtendu.CLE_BIO))
        assertEquals("en cours de frappe", fusion.bio, "le champ touche vient du brouillon")
        assertEquals("mxc://nouvelle/banniere", fusion.banniere, "le reste vient du serveur")
    }

    @Test
    fun `une fusion sans cle touchee rend le serveur tel quel`() {
        val serveur = ProfilEtendu(bio = "du serveur")
        assertEquals(serveur, serveur.fusionner(ProfilEtendu(bio = "perdue"), emptySet()))
    }

    /** the tag travels as two keys, and half a tag is what the editor must never save */
    @Test
    fun `les deux cles du tag sont distinctes`() {
        val tag = ProfilEtendu()
            .avec(ProfilEtendu.CLE_TAG_EMOJI, "🏷")
            .avec(ProfilEtendu.CLE_TAG_TEXTE, "VLVT")
        assertEquals("🏷", tag.tagEmoji)
        assertEquals("VLVT", tag.tagTexte)
        assertNull(ProfilEtendu().tagEmoji, "et rien ne fuit sur un profil vide")
    }
}

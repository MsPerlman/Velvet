package li.velvetchat.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * the `formatted_body` parser, tested case by case
 *
 * these tests carry the burden of proof. the file they replace leaned on `Html.fromHtml`, so the
 * platform did the parsing and there was nothing to test but the translation of its spans. here the
 * parsing is ours, which moves the risk: it is no longer "does the platform render what we think?"
 * but "do we read what the protocol sends?"
 *
 * each case matches something observed: a tag matrix's markdown really produces, html other clients
 * send, or a bug that existed
 */
class AnalyseHtmlTest {

    private fun marques(html: String, balise: Balise) =
        analyserHtml(html).marques.filter { it.balise == balise }

    /** a mark's text, as it comes out. the most direct check there is */
    private fun porte(html: String, balise: Balise): List<String> {
        val a = analyserHtml(html)
        return a.marques.filter { it.balise == balise }.map { a.texte.substring(it.debut, it.fin) }
    }

    // ------------------------------------------------------------------ the basics

    @Test
    fun lesBalisesDeStyleDonnentLeursPortees() {
        assertEquals(listOf("gras"), porte("du <b>gras</b> ici", Balise.GRAS))
        assertEquals(listOf("penché"), porte("du <em>penché</em>", Balise.ITALIQUE))
        assertEquals(listOf("barré"), porte("<del>barré</del>", Balise.BARRE))
        assertEquals(listOf("x = 1"), porte("<code>x = 1</code>", Balise.CHASSE_FIXE))
    }

    /**
     * `<strong>` and `<b>` say the same thing, and both arrive depending on the client at the other
     * end
     */
    @Test
    fun lesSynonymesDuProtocoleSontTraitesPareil() {
        assertEquals(porte("<b>a</b>", Balise.GRAS), porte("<strong>a</strong>", Balise.GRAS))
        assertEquals(porte("<i>a</i>", Balise.ITALIQUE), porte("<em>a</em>", Balise.ITALIQUE))
    }

    @Test
    fun lImbricationDonneDeuxPorteesEtNonUne() {
        val a = analyserHtml("<b>gras <i>et penché</i></b>")
        assertEquals("gras et penché", a.texte)
        assertEquals(listOf("et penché"), porte("<b>gras <i>et penché</i></b>", Balise.ITALIQUE))
        assertEquals(listOf("gras et penché"), porte("<b>gras <i>et penché</i></b>", Balise.GRAS))
        assertEquals(2, a.marques.size)
    }

    // what has already broken

    /**
     * bullets have to exist in the text
     *
     * on android they were drawn by the text engine and absent from the string, so on screen a list
     * became a run of lines with no visible connection
     */
    @Test
    fun uneListeGardeSesPuces() {
        val a = analyserHtml("<ul><li>un</li><li>deux</li></ul>")
        assertEquals("• un\n• deux", a.texte)
    }

    /**
     * `<code>` and `<pre>` are the only two code tags matrix's markdown produces, and
     * `Html.fromHtml` knew neither, it only recognises `<tt>`
     */
    @Test
    fun preEtCodeExistentTousLesDeux() {
        assertEquals(listOf("bloc"), porte("<pre>bloc</pre>", Balise.CHASSE_FIXE))
        assertEquals(listOf("en ligne"), porte("<code>en ligne</code>", Balise.CHASSE_FIXE))
    }

    /** `<pre><code>` is a code block's real shape: two tags, one useful span */
    @Test
    fun unBlocDeCodeImbriqueNeCasseRien() {
        val a = analyserHtml("<pre><code>fun main()</code></pre>")
        assertEquals("fun main()", a.texte)
        assertTrue(a.marques.all { it.balise == Balise.CHASSE_FIXE })
    }

    /**
     * the spoiler is read from its attribute, which was impossible with `Html.fromHtml`: it never
     * passes attributes to its `TagHandler`, hence the upstream renaming this test replaces
     */
    @Test
    fun unSpoilerEstReconnuSurSonAttribut() {
        assertEquals(
            listOf("la fin"),
            porte("""avant <span data-mx-spoiler="">la fin</span> après""", Balise.SPOILER),
        )
        // the attribute with a reason is the standard's full form
        assertEquals(
            listOf("il meurt"),
            porte("""<span data-mx-spoiler="fin du film">il meurt</span>""", Balise.SPOILER),
        )
    }

    @Test
    fun deuxSpoilersRestentSepares() {
        val a = analyserHtml("<span data-mx-spoiler>un</span> et <span data-mx-spoiler>deux</span>")
        assertEquals("un et deux", a.texte)
        assertEquals(listOf("un", "deux"), porte(
            "<span data-mx-spoiler>un</span> et <span data-mx-spoiler>deux</span>",
            Balise.SPOILER,
        ))
    }

    /**
     * the protocol puts `<span>` everywhere, nickname colours, mentions. taking them for spoilers
     * would hide whole messages
     */
    @Test
    fun unSpanOrdinaireNEstPasUnSpoiler() {
        assertTrue(marques("<span>texte</span>", Balise.SPOILER).isEmpty())
        assertEquals("texte", analyserHtml("<span>texte</span>").texte)
    }

    @Test
    fun unLienGardeSonAdresseEtSonLibelle() {
        val a = analyserHtml("""voir <a href="https://exemple.fr/page">la page</a>""")
        val lien = a.marques.single { it.balise == Balise.LIEN }
        assertEquals("https://exemple.fr/page", lien.valeur)
        assertEquals("la page", a.texte.substring(lien.debut, lien.fin))
    }

    /** a mention is a `matrix.to` link: that is how the protocol expresses it */
    @Test
    fun uneMentionEstUnLienMatrixTo() {
        val a = analyserHtml("""<a href="https://matrix.to/#/@marie:exemple.org">Marie</a>""")
        val lien = a.marques.single { it.balise == Balise.LIEN }
        assertTrue(lien.valeur!!.startsWith("https://matrix.to/#/@marie"))
        assertEquals("Marie", a.texte.substring(lien.debut, lien.fin))
    }

    /**
     * an address written in the clear is recognised without the message wrapping it in an `<a>`:
     * matrix does not guarantee that, and without this pass it would draw as ordinary text
     */
    @Test
    fun uneAdresseNueEstReperee() {
        val texte = "regarde https://exemple.fr/x maintenant"
        val portees = liensNus(texte)
        assertEquals(listOf("https://exemple.fr/x"), portees.map { texte.substring(it) })
    }

    /**
     * trailing punctuation belongs to the sentence and not to the address, otherwise the link is
     * dead
     */
    @Test
    fun laPonctuationFinaleNEstPasAvalee() {
        val texte = "voir https://exemple.fr."
        assertEquals(listOf("https://exemple.fr"), liensNus(texte).map { texte.substring(it) })
        val autre = "(https://exemple.fr/a_(b))"
        assertEquals(
            listOf("https://exemple.fr/a_(b)"),
            liensNus(autre).map { autre.substring(it) },
        )
    }

    /**
     * with no scheme, it is not one
     *
     * stricter than the old `Linkify`, deliberately: a sentence that happens to contain a domain
     * like fragment must not produce a link. a false positive turns text into something clickable,
     * which is the wrong direction for the mistake
     */
    @Test
    fun unNomDeDomaineSeulNEstPasUnLien() {
        assertTrue(liensNus("c'est mon avis.fr de rien").isEmpty())
    }

    // entities and robustness

    @Test
    fun lesEntitesSontDecodees() {
        assertEquals("a & b", analyserHtml("a &amp; b").texte)
        assertEquals("<tag>", analyserHtml("&lt;tag&gt;").texte)
        assertEquals("\"guillemets\"", analyserHtml("&quot;guillemets&quot;").texte)
        assertEquals("é", analyserHtml("&#233;").texte)
        assertEquals("é", analyserHtml("&#xe9;").texte)
    }

    /** an emoji outside the basic plane: two code units, one entity */
    @Test
    fun uneEntiteHorsDuPlanDeBaseSeReconstitue() {
        assertEquals("😀", analyserHtml("&#128512;").texte)
    }

    /** a lone `&` stays a `&`. hunting for a semicolon too far away would swallow the sentence */
    @Test
    fun unEsperluetteIsoleeSurvit() {
        assertEquals("Pierre & Marie, et le reste ; voilà", analyserHtml("Pierre & Marie, et le reste ; voilà").texte)
    }

    /** badly closed html genuinely arrives. it has to degrade, not make the message disappear */
    @Test
    fun duHtmlMalFermeResteLisible() {
        assertEquals("gras et penché", analyserHtml("<b><i>gras et penché</b>").texte)
        assertEquals("texte", analyserHtml("<b>texte").texte)
        assertEquals("a < b", analyserHtml("a < b").texte)
    }

    /** an unknown tag is walked through: its content shows, its name does not */
    @Test
    fun uneBaliseInconnueEstTraversee() {
        assertEquals("contenu", analyserHtml("<inconnue>contenu</inconnue>").texte)
    }

    @Test
    fun lesBlocsSeparentLesLignes() {
        assertEquals("un\ndeux", analyserHtml("<p>un</p><p>deux</p>").texte)
        assertEquals("un\ndeux", analyserHtml("un<br>deux").texte)
        // no blank line between two blocks: it would grow every message in the middle of a dense
        // conversation
        assertEquals("un\ndeux", analyserHtml("<p>un</p>\n<p>deux</p>").texte)
    }

    @Test
    fun unTitreEstRangeDansLUnDesTroisNiveaux() {
        assertEquals(listOf("Grand"), porte("<h1>Grand</h1>", Balise.TITRE1))
        assertEquals(listOf("Moyen"), porte("<h2>Moyen</h2>", Balise.TITRE2))
        assertEquals(listOf("Petit"), porte("<h3>Petit</h3>", Balise.TITRE3))
        // `<h5>` comes from elsewhere: it falls back to the third level rather than becoming
        // smaller than body text
        assertEquals(listOf("Venu d'ailleurs"), porte("<h5>Venu d'ailleurs</h5>", Balise.TITRE3))
    }

    @Test
    fun uneCouleurEstRetenueTelleQuEcrite() {
        val a = analyserHtml("""<span data-mx-color="#ff0000">rouge</span>""")
        assertEquals("#ff0000", a.marques.single { it.balise == Balise.COULEUR_TEXTE }.couleur())
    }

    /** images are stripped upstream, and the ones that remain are not loaded */
    @Test
    fun uneImageNeLaissePasDeTrou() {
        assertEquals("avant après", analyserHtml("""avant <img src="x.png"> après""").texte)
    }

    @Test
    fun unHtmlVideNeDonneRien() {
        assertEquals("", analyserHtml("").texte)
        assertEquals("", analyserHtml("<p></p>").texte)
        assertNull(analyserHtml("<p></p>").marques.firstOrNull())
    }
}

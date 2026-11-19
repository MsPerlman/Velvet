package li.velvetchat.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.RoleTier

/**
 * the decisions the interface makes with no screen
 *
 * four pure functions the timeline, the autocomplete and the avatars call dozens of times a second.
 * they do not need an emulator to be checked, so they are checked here: these tests run in the pre
 * commit hook, where the instrumented ones want a device plugged in
 *
 * each case below matches a rule written in the production code, and several a bug that really
 * existed. a test that only restates the implementation is no use, and these fail if somebody
 * "simplifies" one of those rules without seeing why it was there
 */
class LogiqueInterfaceTest {

    private val paliers = listOf(
        RoleTier(power = 100L, name = "Fondateur", colorArgb = null, icon = null),
        RoleTier(power = 50L, name = "Modérateur", colorArgb = null, icon = null),
        RoleTier(power = 0L, name = "Membre", colorArgb = null, icon = null),
    )

    @Test
    fun unNiveauExactPrendSonPalier() {
        assertEquals("Modérateur", palierDe(50L, paliers)?.name)
    }

    /**
     * a threshold table describes bands, not exact values. 75 is declared nowhere and has to
     * inherit the tier below, not end up with no badge
     */
    @Test
    fun unNiveauIntermediaireHeriteDuPalierInferieur() {
        assertEquals("Modérateur", palierDe(75L, paliers)?.name)
    }

    @Test
    fun unNiveauAbsentEstTraiteCommeZero() {
        assertEquals("Membre", palierDe(null, paliers)?.name)
    }

    /** with no tier declared at the floor, nobody has a badge, and above all nothing crashes */
    @Test
    fun sansPalierAuSolOnNeRendRien() {
        assertNull(palierDe(10L, listOf(paliers[0], paliers[1])))
        assertNull(palierDe(0L, emptyList()))
    }

    // autocomplete

    private fun membre(nom: String) =
        Member(userId = "@${nom.lowercase()}:exemple.org", displayName = nom, avatarUrl = null, role = null)

    /** the trailing space is the contract: without it two suggestions run together */
    @Test
    fun unePropositionRetenueEstSuivieDUneEspace() {
        assertEquals("salut @Papa ", completer("salut @pa", Suggestion.Personne(membre("Papa"))))
    }

    /**
     * we complete what is being typed, not a trigger already set earlier in the sentence: the `@`
     * of "@Papa" must not be rewritten when you type the `#`
     */
    @Test
    fun seulLeDernierDeclencheurEstRemplace() {
        assertEquals(
            "@Papa regarde @room ",
            completer("@Papa regarde @ro", Suggestion.ToutLeSalon),
        )
    }

    /**
     * with no trigger at the start of a word, the text comes back as it was rather than mutilated
     */
    @Test
    fun unTexteSansDeclencheurNEstPasTouche() {
        val texte = "courriel: quelquun@example.com"
        assertEquals(texte, completer(texte, Suggestion.ToutLeSalon))
    }

    // ------------------------------------------------- grouping the messages

    private val fenetre = 420_000L

    private fun msg(
        id: String,
        auteur: String,
        ts: Long,
        repondA: String? = null,
        corps: MessageBody = MessageBody.Text("bonjour"),
    ) = Message(
        id = id,
        sender = auteur,
        senderDisplayName = auteur,
        timestamp = ts,
        isOwn = false,
        isEdited = false,
        body = corps,
        replyTo = repondA,
    )

    // ------------------------------------------------- geometry of the crop

    /**
     * with no zoom and no offset, we keep the square centred
     *
     * a square image is taken whole, and a landscape image gives its middle square. that is the
     * default framing, the one the app already applied silently, and the difference is that you can
     * now get out of it
     */
    @Test
    fun sansGesteLeCadreEstCeluiDuCentre() {
        assertEquals(Cadre(0, 0, 1000), rectSource(1000, 1000, 300f, 1f, 0f, 0f))
        assertEquals(Cadre(500, 0, 1000), rectSource(2000, 1000, 300f, 1f, 0f, 0f))
        assertEquals(Cadre(0, 500, 1000), rectSource(1000, 2000, 300f, 1f, 0f, 0f))
    }

    /**
     * dragging the image to the right uncovers its left
     *
     * the direction is the whole point: inverted, the cropping still works, it simply shows the
     * other end of the photo, and you take it for a display fault. 150 px of screen are 500 px of
     * image here, the cover scale being 0.3
     */
    @Test
    fun glisserVersLaDroiteDecouvreLaGauche() {
        assertEquals(Cadre(0, 0, 1000), rectSource(2000, 1000, 300f, 1f, 150f, 0f))
        assertEquals(Cadre(1000, 0, 1000), rectSource(2000, 1000, 300f, 1f, -150f, 0f))
    }

    @Test
    fun leZoomRetrecitLeCadreAutourDuCentre() {
        assertEquals(Cadre(250, 250, 500), rectSource(1000, 1000, 300f, 2f, 0f, 0f))
    }

    /**
     * an excessive offset is brought back inside the image, never past it
     *
     * `Bitmap.createBitmap` throws as soon as an edge goes out, so an unbounded frame would crash
     * the confirmation on a slightly wide gesture, which is to say at the worst moment
     */
    @Test
    fun leCadreNeSortJamaisDeLImage() {
        val gauche = rectSource(1000, 1000, 300f, 2f, 99_999f, 99_999f)
        assertEquals(Cadre(0, 0, 500), gauche)
        val droite = rectSource(1000, 1000, 300f, 2f, -99_999f, -99_999f)
        assertEquals(Cadre(500, 500, 500), droite)
    }

    /** a degenerate image does not crash: it returns an empty frame, and the caller sees */
    @Test
    fun uneImageVideRendUnCadreVide() {
        assertEquals(Cadre(0, 0, 0), rectSource(0, 0, 300f, 1f, 0f, 0f))
    }

    private fun media(mime: String) = MessageBody.Media(
        caption = "",
        kind = li.velvetchat.core.model.MediaKind.IMAGE,
        media = li.velvetchat.core.model.MediaRef(
            source = "mxc://exemple.org/x", filename = "x", mimeType = mime, sizeBytes = 1,
        ),
    )

    /**
     * a photo is glued back into a sheet, a gif is not
     *
     * reported: three gifs sent in a row arrived condensed into one message. they met the gallery's
     * conditions, same author, same window, `m.image`, and a gallery thumbnail does not animate.
     * the only discriminator available is the mime type, hence this test on both sides: without the
     * second, too broad an exclusion would also have broken the photo sheets, and nothing would
     * have said so
     */
    @Test
    fun unGifNeSeRecollePasEnGalerie() {
        assertFalse(estImage(msg("1", "@a", 0, corps = media("image/gif"))))
        assertTrue(estImage(msg("2", "@a", 0, corps = media("image/jpeg"))))
    }

    @Test
    fun deuxMessagesRapprochesDuMemeAuteurSeGroupent() {
        assertTrue(
            continueLeGroupe(msg("1", "@a", 0), msg("2", "@a", 60_000), fenetre)
        )
    }

    /**
     * the regression that chopped up the timeline
     *
     * a message following a reply, from the same author and within the window, groups. the previous
     * one's quote is above the previous one, it does not slot in between the two, and nothing
     * justifies showing the avatar, the nickname and the time again in the same minute
     */
    @Test
    fun unMessageQuiSuitUneReponseSeGroupeQuandMeme() {
        val reponse = msg("1", "@a", 0, repondA = "\$origine")
        assertTrue(continueLeGroupe(reponse, msg("2", "@a", 30_000), fenetre))
    }

    /** a reply, though, always opens a group: its quote needs a header */
    @Test
    fun uneReponseOuvreToujoursUnGroupe() {
        assertFalse(
            continueLeGroupe(msg("1", "@a", 0), msg("2", "@a", 30_000, repondA = "\$x"), fenetre)
        )
    }

    @Test
    fun unChangementDAuteurCasseLeGroupe() {
        assertFalse(continueLeGroupe(msg("1", "@a", 0), msg("2", "@b", 1_000), fenetre))
    }

    /**
     * seven minutes is the window: past that, two messages are no longer the same turn of speech
     */
    @Test
    fun auDelaDeLaFenetreLeGroupeRepart() {
        assertFalse(continueLeGroupe(msg("1", "@a", 0), msg("2", "@a", fenetre + 1), fenetre))
    }

    @Test
    fun unLienQuiAnnonceUnHoteEtEnViseUnAutreEstTrompeur() {
        assertTrue(lienTrompeur("banque.fr", "https://piege.ru/connexion"))
    }

    @Test
    fun leTraquenardDesIdentifiantsDUrlEstDejoue() {
        // `http://banque.fr@piege.ru/` goes to piege.ru. everything before the last `@` is a
        // userinfo, not a host, and it is the part the eye reads first
        assertEquals("piege.ru", hoteDe("http://banque.fr@piege.ru/connexion"))
        assertTrue(lienTrompeur("banque.fr", "http://banque.fr@piege.ru/"))
    }

    @Test
    fun unLienDontLeTexteEstSaPropreAdresseNeDemandeRien() {
        val url = "https://github.com/Vencord/Vesktop"
        assertFalse(lienTrompeur(url, url))
    }

    @Test
    fun unTexteQuiNAnnonceAucunHoteNeDemandeRien() {
        // warning on "click here" would amount to warning on everything, so on nothing
        assertFalse(lienTrompeur("clique ici", "https://exemple.fr"))
        assertFalse(lienTrompeur("le compte rendu", "https://exemple.fr/doc"))
    }

    @Test
    fun leWwwNEstPasUneDifferenceDeDestination() {
        assertFalse(lienTrompeur("github.com", "https://www.github.com/quelquun"))
    }

    @Test
    fun lePortEtLeCheminNeChangentPasLHote() {
        assertEquals("exemple.fr", hoteDe("https://exemple.fr:8443/page?a=1#b"))
    }

    @Test
    fun seulsLesSchemasWebEtCourrielSontOuverts() {
        assertTrue(schemeSur("https://exemple.fr"))
        assertTrue(schemeSur("http://exemple.fr"))
        assertTrue(schemeSur("mailto:a@exemple.fr"))
        // these launch something other than a page
        assertFalse(schemeSur("javascript:alert(1)"))
        assertFalse(schemeSur("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(schemeSur("content://media/external/images/1"))
        assertFalse(schemeSur("file:///data/data/li.velvetchat.app"))
    }

    @Test
    fun uneAdresseNueRecoitHttps() {
        assertEquals("https://exemple.fr", completerScheme("exemple.fr"))
        assertEquals("https://exemple.fr", completerScheme("https://exemple.fr"))
        assertEquals("mailto:a@exemple.fr", completerScheme("mailto:a@exemple.fr"))
    }

    @Test
    fun lAllerRetourTsvRendLaMemeCouleur() {
        // the conversion replaces `android.graphics.Color` to make the screen portable. a round
        // trip that drifted would slide a role's colour on every opening of the picker, a slow
        // fault, so invisible on review
        listOf(
            androidx.compose.ui.graphics.Color(0xFF1FB6A8), // accent
            androidx.compose.ui.graphics.Color(0xFFE0575B), // danger
            androidx.compose.ui.graphics.Color(0xFF5B8DEF), // role1
            androidx.compose.ui.graphics.Color(0xFFD9A441), // warning
            androidx.compose.ui.graphics.Color(0xFF000000),
            androidx.compose.ui.graphics.Color(0xFFFFFFFF),
        ).forEach { couleur ->
            val retour = depuisTsv(versTsv(couleur))
            assertEquals(couleur.red, retour.red, 0.002f)
            assertEquals(couleur.green, retour.green, 0.002f)
            assertEquals(couleur.blue, retour.blue, 0.002f)
        }
    }

    @Test
    fun lesTeintesPrimairesTombentOuLaConventionAndroidLesMettait() {
        // the same convention as `colorToHSV`: hue in degrees, saturation and value between 0 and
        // 1. the callers had nothing to change, and this test is what guarantees it
        val (hr, sr, vr) = versTsv(androidx.compose.ui.graphics.Color(0xFFFF0000))
        assertEquals(0f, hr, 0.01f); assertEquals(1f, sr, 0.01f); assertEquals(1f, vr, 0.01f)

        val (hv, _, _) = versTsv(androidx.compose.ui.graphics.Color(0xFF00FF00))
        assertEquals(120f, hv, 0.01f)

        val (hb, _, _) = versTsv(androidx.compose.ui.graphics.Color(0xFF0000FF))
        assertEquals(240f, hb, 0.01f)
    }

    @Test
    fun unGrisNAPasDeTeinte() {
        // zero, like android: without that convention the slider would jump when passing through
        // white or black, where the hue is undefined
        val (h, s, _) = versTsv(androidx.compose.ui.graphics.Color(0xFF808080))
        assertEquals(0f, h, 0.01f)
        assertEquals(0f, s, 0.01f)
    }

    @Test
    fun uneSaisieIncompleteLaisseLaCouleurOuElleEst() {
        assertNull(couleurOuNull("#1FB"))
        assertNull(couleurOuNull("#"))
        assertNull(couleurOuNull("rouge"))
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFF1FB6A8),
            couleurOuNull("#1FB6A8"),
        )
    }
}

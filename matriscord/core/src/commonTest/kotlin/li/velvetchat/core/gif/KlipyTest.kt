package li.velvetchat.core.gif

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * what these tests protect: reading a klipy response, with no key and no network
 *
 * possible because the decoding was split from the transport from the start. without that split,
 * this whole file would be waiting on an api key the project does not have yet, and the code would
 * ship unverified
 *
 * the ad filter is the most sensitive part. a false negative sends an advert into a private
 * conversation, a false positive only hides one result. so that is where most of the tests are
 */
class KlipyTest {

    private fun objet(json: String) = Json.parseToJsonElement(json) as JsonObject

    // ------------------------------------------------------------------ the ads

    @Test
    fun `une entree de type ad est ecartee`() {
        assertTrue(Klipy.estUneAnnonce(objet("""{"type":"ad","id":"1"}""")))
        assertTrue(Klipy.estUneAnnonce(objet("""{"type":"Advertisement","id":"1"}""")))
        assertTrue(Klipy.estUneAnnonce(objet("""{"type":"SPONSORED","id":"1"}""")))
    }

    @Test
    fun `un drapeau booleen suffit`() {
        assertTrue(Klipy.estUneAnnonce(objet("""{"is_ad":true,"id":"1"}""")))
        assertTrue(Klipy.estUneAnnonce(objet("""{"sponsored":true,"id":"1"}""")))
        assertFalse(Klipy.estUneAnnonce(objet("""{"is_ad":false,"id":"1"}""")))
    }

    @Test
    fun `une adresse de destination trahit une annonce`() {
        // an ordinary gif has no reason to carry an address you are meant to click
        assertTrue(Klipy.estUneAnnonce(objet("""{"id":"1","click_url":"https://x"}""")))
        assertTrue(Klipy.estUneAnnonce(objet("""{"id":"1","ad_url":"https://x"}""")))
    }

    @Test
    fun `un gif ordinaire n est pas pris pour une annonce`() {
        assertFalse(Klipy.estUneAnnonce(objet("""{"id":"1","type":"gif","title":"chat"}""")))
    }

    @Test
    fun `le filtre retire l annonce et garde le reste`() {
        val reponse = """
            {"result":true,"data":{"data":[
              {"id":"a","type":"gif","file":{"md":{"gif":{"url":"https://x/a.gif"}}}},
              {"id":"pub","type":"ad","file":{"md":{"gif":{"url":"https://x/p.gif"}}}},
              {"id":"b","type":"gif","file":{"md":{"gif":{"url":"https://x/b.gif"}}}}
            ]}}
        """.trimIndent()
        assertEquals(listOf("a", "b"), Klipy.lire(reponse, sansPublicite = true).map { it.id })
        assertEquals(3, Klipy.lire(reponse, sansPublicite = false).size)
    }

    @Test
    fun `l enveloppe est traversee quelle que soit sa profondeur`() {
        // klipy wraps twice, and has wrapped differently before. following a fixed path would
        // break in silence, returning "no results" instead of an error
        val plate = """{"data":[{"id":"a","file":{"md":{"gif":{"url":"https://x/a.gif"}}}}]}"""
        val double = """{"result":true,"data":{"data":[{"id":"a","file":{"md":{"gif":{"url":"https://x/a.gif"}}}}]}}"""
        assertEquals(1, Klipy.lire(plate, sansPublicite = true).size)
        assertEquals(1, Klipy.lire(double, sansPublicite = true).size)
    }

    @Test
    fun `une reponse illisible rend une liste vide, jamais une exception`() {
        // a gif picker that takes the conversation down with it would be a far worse bug than no
        // gif at all
        assertEquals(emptyList(), Klipy.lire("pas du json", sansPublicite = true))
        assertEquals(emptyList(), Klipy.lire("", sansPublicite = true))
        assertEquals(emptyList(), Klipy.lire("""{"data":{}}""", sansPublicite = true))
    }

    // ------------------------------------------- the sizes, against the real shape

    /**
     * a faithful extract of a real response, actual byte sizes included
     *
     * the two tests this block replaces were written against the documentation: dimensions at the
     * entry level, and a size called `gif`. the real response does neither. they went green
     * verifying an api that does not exist
     */
    private val REPONSE_REELLE = """
        {"result":true,"data":{"data":[{
          "id":6795335047828096,"slug":"bed-time-sleep-8","title":"Muppet Yells Go To Bed",
          "type":"gif",
          "file":{
            "xs":{"gif":{"url":"https://static.klipy.com/x/a.gif","width":120,"height":90,"size":115712},
                  "webp":{"url":"https://static.klipy.com/x/a.webp","width":90,"height":68,"size":79222}},
            "sm":{"gif":{"url":"https://static.klipy.com/s/a.gif","width":220,"height":165,"size":322560},
                  "webp":{"url":"https://static.klipy.com/s/a.webp","width":220,"height":165,"size":273408}},
            "md":{"gif":{"url":"https://static.klipy.com/m/a.gif","width":640,"height":480,"size":2498560},
                  "webp":{"url":"https://static.klipy.com/m/a.webp","width":640,"height":480,"size":1184650}},
            "hd":{"gif":{"url":"https://static.klipy.com/h/a.gif","width":498,"height":373,"size":5332811},
                  "webp":{"url":"https://static.klipy.com/h/a.webp","width":498,"height":374,"size":342016}}
          }}]},"meta":{"item_min_width":80}}
    """.trimIndent()

    @Test
    fun `l envoi prend le rendu le plus leger, pas le premier de la liste`() {
        val gif = Klipy.lire(REPONSE_REELLE, sansPublicite = true).single()
        // `hd.webp` at 342 kb beats `md.webp` at 1 157 kb and `md.gif` at 2 440 kb. an ordering
        // rule written by hand would have taken `md`, which is what the previous version did, and
        // it sent about seven times what was needed
        assertEquals("https://static.klipy.com/h/a.webp", gif.complet)
    }

    @Test
    fun `l apercu prend sm, et non le xs que KLIPY designe`() {
        val gif = Klipy.lire(REPONSE_REELLE, sansPublicite = true).single()
        // fixed on screen. `meta.item_min_width` is 80 and points at `xs`, but our two column
        // grid makes cells of about 420 px. `xs` is 90 across, and the text burned into gifs,
        // which is how anyone recognises them, came out unreadable
        assertEquals("https://static.klipy.com/s/a.webp", gif.apercu)
    }

    @Test
    fun `les dimensions viennent du rendu et non de l entree`() {
        val gif = Klipy.lire(REPONSE_REELLE, sansPublicite = true).single()
        // the entry has no `width` field at all, so reading them off it always gave null, so a
        // grid with no space reserved. the preview's are the ones that matter, since the preview
        // is what the grid draws
        assertEquals(220, gif.largeur)
        assertEquals(165, gif.hauteur)
    }

    @Test
    fun `le titre et l identifiant numerique sont lus`() {
        val gif = Klipy.lire(REPONSE_REELLE, sansPublicite = true).single()
        assertEquals("Muppet Yells Go To Bed", gif.description)
        // `id` arrives as a number and not a string. a strictly typed read would have given null
        // and made the id fall back on the url
        assertEquals("6795335047828096", gif.id)
    }

    @Test
    fun `la forme plate file taille url reste lue`() {
        // kept as a fallback, since it is the shape the documentation shows and nothing says the
        // response will not go back to it
        val reponse = """{"data":[{"id":"a","file":{
            "sm":{"url":"https://x/sm.gif"},"md":{"url":"https://x/md.gif"}}}]}"""
        val gif = Klipy.lire(reponse, sansPublicite = true).single()
        assertEquals("https://x/sm.gif", gif.apercu)
        assertEquals("https://x/md.gif", gif.complet)
    }

    // ---------------------------------------------- what we refuse to send

    @Test
    fun `une entree sans fichier est ecartee au lieu de retomber sur sa page`() {
        // the shape that produced the bug: no usable `file` block, but a `url` that is the
        // address of the klipy page. the old fallback made that the gif's full url, so the
        // message's body, and that page renders "Just a moment…" from cloudflare, so the message
        // was permanently undisplayable for everybody
        val reponse = """
            { "data": { "data": [
              { "id": "1", "url": "https://klipy.com/gifs/blair-waldorf-blairhive-1" }
            ] } }
        """.trimIndent()
        assertTrue(Klipy.lire(reponse, sansPublicite = true).isEmpty())
    }

    @Test
    fun `une entree dont le fichier n est pas un fichier est ecartee`() {
        val reponse = """
            { "data": { "data": [
              { "id": "1", "file": { "md": { "url": "https://klipy.com/gifs/une-page" } } }
            ] } }
        """.trimIndent()
        assertTrue(Klipy.lire(reponse, sansPublicite = true).isEmpty())
    }

    @Test
    fun `une entree avec un vrai fichier passe toujours`() {
        // the counter test. without it, the two refusals above would be satisfied by a decoder
        // that rejects everything
        val reponse = """
            { "data": { "data": [
              { "id": "1", "url": "https://klipy.com/gifs/une-page",
                "file": { "md": { "gif": { "url": "https://media.klipy.com/a.gif" } } } }
            ] } }
        """.trimIndent()
        val gif = Klipy.lire(reponse, sansPublicite = true).single()
        assertEquals("https://media.klipy.com/a.gif", gif.complet)
    }

    @Test
    fun `les mots-cles sont encodes sans URLEncoder`() {
        // written by hand so this file stays testable with no platform under it
        assertEquals("chat", Klipy.encoder("chat"))
        assertEquals("chat%20qui%20tape", Klipy.encoder("chat qui tape"))
        assertEquals("caf%C3%A9", Klipy.encoder("café"))
        assertEquals("a-b_c.d~e", Klipy.encoder("a-b_c.d~e"))
        // the case that matters: an unencoded `&` would add a parameter to the request
        assertEquals("a%26b%3Dc", Klipy.encoder("a&b=c"))
    }

    @Test
    fun `les chemins portent la cle et la pagination`() {
        assertTrue(Klipy.cheminTendances("CLE", 2, 24).endsWith("/CLE/gifs/trending?page=2&per_page=24"))
        assertTrue(Klipy.cheminRecherche("CLE", "chat noir", 1, 24)
            .endsWith("/CLE/gifs/search?q=chat%20noir&page=1&per_page=24"))
    }
}

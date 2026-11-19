package li.velvetchat.core.gif

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * reading a klipy response, kept apart from transporting it on purpose
 *
 * this whole file is arithmetic on text: no network, no platform, no `HttpURLConnection`. so it
 * lives in `commonMain` and tests without a server. the last time logic like this stayed behind
 * a network call it cost three compile cycles for two bugs a test finds in a second
 *
 * the transport lives in the android module, where it has a network
 */
object Klipy {

    /** `https://api.klipy.com/api/v1/{key}`. the key goes in the path, not in a header */
    const val BASE = "https://api.klipy.com/api/v1"

    fun cheminTendances(cle: String, page: Int, parPage: Int) =
        "$BASE/$cle/gifs/trending?page=$page&per_page=$parPage"

    fun cheminRecherche(cle: String, requete: String, page: Int, parPage: Int) =
        "$BASE/$cle/gifs/search?q=${encoder(requete)}&page=$page&per_page=$parPage"

    /**
     * turns a klipy response into showable gifs
     *
     * ads get dropped here and not higher up, because they arrive mixed into the results as
     * entries of a different type in the same list. there is no separate call to avoid, there
     * are items not to keep
     *
     * @param sansPublicite when true, entries marked as ads are removed. see `PrefsGif` for the
     *   trade off: the api is free because of those ads, and filtering them is a choice that
     *   concerns their terms of use
     */
    fun lire(corps: String, sansPublicite: Boolean): List<Gif> = runCatching {
        val racine = kotlinx.serialization.json.Json.parseToJsonElement(corps) as? JsonObject
            ?: return emptyList()

        // klipy wraps twice: `{ "result": true, "data": { "data": [ … ] } }`. we look for the
        // first array rather than follow a hardcoded path, because the wrapper already changed
        // once between two versions of their docs, and a fixed path breaks silently by handing
        // back an empty list you would read as "no results"
        val liste = premierTableau(racine) ?: return emptyList()

        liste.mapNotNull { element ->
            val objet = element as? JsonObject ?: return@mapNotNull null
            if (sansPublicite && estUneAnnonce(objet)) return@mapNotNull null
            versGif(objet)
        }
    }.getOrDefault(emptyList())

    /**
     * is this entry an ad
     *
     * three markers and not one, because none of them is guaranteed: the declared type, a
     * boolean flag, or the presence of a destination url. an ad that got through would look
     * like an ordinary gif and go out into a private conversation, so a false negative costs far
     * more than a false positive, which only hides a result
     */
    internal fun estUneAnnonce(objet: JsonObject): Boolean {
        val type = texte(objet, "type")?.lowercase()
        if (type == "ad" || type == "advertisement" || type == "sponsored") return true
        if (booleen(objet, "is_ad") == true) return true
        if (booleen(objet, "sponsored") == true) return true
        // an ordinary gif has no reason to carry somewhere to click through to
        return objet["ad_url"] != null || objet["click_url"] != null
    }

    /**
     * an entry we cannot get a file out of is dropped, never fallen back onto its page. the
     * original fallback ended on `texte(objet, "url")`, which on a klipy entry is the site page
     * and not the file — harmless while the response matched the docs, and the day it differs it
     * hands back a gif whose `complet` is a page, `complet` being what goes into the message
     *
     * `https://klipy.com/gifs/<slug>` returns "just a moment", the cloudflare interstitial. no
     * matrix client and no preview server gets an image out of that, so the message is permanently
     * undisplayable for everyone while having looked like a successful send
     *
     * a gif we cannot display is not a gif: one entry fewer in the grid beats a dead message in a
     * conversation. same rule as the ad filter
     */
    private fun versGif(objet: JsonObject): Gif? {
        val fichiers = objet["file"] as? JsonObject

        // what goes into the message: the lightest version still big enough for the screen
        //
        // the response gives the weight of every rendition, so there is nothing to guess, and
        // the spread is enormous. for the same image `md.gif` came to 2440 kb and `md.webp` to
        // 1157, while `hd.webp` was 334. picking `md.gif`, as the previous version did, sent
        // seven times what was needed, for every recipient to load on every open
        //
        // we compare rather than rank. `hd` is not reliably heavier than `md` at klipy, their
        // sizes are renditions and not a scale, and any hand written ordering would have been
        // wrong in one direction or the other
        val complet = ENVOI.mapNotNull { (taille, format) -> rendu(fichiers, taille, format) }
            .filter { LienGif.ressembleAUnFichier(it.url) }
            .minByOrNull { it.poids ?: Long.MAX_VALUE }
            ?: return null

        // the thumbnail, see APERCU below for why it is the size it is
        val apercu = APERCU.firstNotNullOfOrNull { (taille, format) -> rendu(fichiers, taille, format) }
            ?: complet

        return Gif(
            id = texte(objet, "id") ?: texte(objet, "slug") ?: complet.url,
            apercu = apercu.url,
            complet = complet.url,
            // the dimensions live on the rendition, not on the entry. reading them off the
            // object always returned null, so the grid reserved no space and jumped every time
            // a thumbnail landed. nothing crashes, the layout just moves
            largeur = apercu.largeur,
            hauteur = apercu.hauteur,
            description = texte(objet, "title")?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * the candidate renditions for sending. the order here is just a list, weight decides
     *
     * no `mp4` and no `webm`, even though they are often the lightest. the message body is an
     * address other clients will show as a link, and `.mp4` reads as a video there, which is a
     * different thing from a gif. animated `webp` decodes in every modern browser and down the
     * same path as a gif on android
     */
    private val ENVOI = listOf(
        "md" to "webp", "hd" to "webp", "md" to "gif", "hd" to "gif",
    )

    /**
     * the candidate renditions for the grid, first one found. `sm` and not `xs`, a correction made
     * by looking at it: klipy's `meta.item_min_width` of 80 points aims at `xs` and i followed it,
     * but it is meant for a far denser grid than ours — two columns on a phone gives a cell near
     * 420 pixels where `xs` is 90, so the text burned into gifs, which is most of what you
     * recognise them by, came out unreadable
     *
     * the price is real: `sm.webp` weighs 267 kb against 77, so roughly six megabytes per page of
     * twenty four instead of two. accepted, a grid you cannot make out being useless, and nobody
     * opens it by accident
     */
    private val APERCU = listOf(
        "sm" to "webp", "sm" to "gif", "xs" to "webp", "xs" to "gif", "md" to "webp",
    )

    /** one version of a gif: its address, its dimensions, its weight */
    private data class Rendu(
        val url: String,
        val largeur: Int?,
        val hauteur: Int?,
        val poids: Long?,
    )

    /**
     * `file.<size>.<format>`, plus the flat `file.<size>.url` shape the docs also show, kept as
     * a fallback in case the response goes back to it
     */
    private fun rendu(fichiers: JsonObject?, taille: String, format: String): Rendu? {
        val niveau = fichiers?.get(taille) as? JsonObject ?: return null
        (niveau[format] as? JsonObject)?.let { f ->
            val url = texte(f, "url") ?: return@let
            return Rendu(url, entier(f, "width"), entier(f, "height"), entier(f, "size")?.toLong())
        }
        if (format != "gif") return null
        val url = texte(niveau, "url") ?: return null
        return Rendu(url, entier(niveau, "width"), entier(niveau, "height"), null)
    }

    /**
     * the first array found on the way down, however deep the wrapping goes
     *
     * capped at four levels. past that it is not a wrapper any more but a different response,
     * and descending forever would eventually find an array with nothing to do with anything
     */
    internal fun premierTableau(element: JsonElement, profondeur: Int = 0): JsonArray? {
        if (profondeur > 4) return null
        if (element is JsonArray) return element
        if (element !is JsonObject) return null
        // `data` and `results` first: when both exist, that is the one we want
        listOf("data", "results", "items", "gifs").forEach { cle ->
            element[cle]?.let { premierTableau(it, profondeur + 1)?.let { t -> return t } }
        }
        element.values.forEach { premierTableau(it, profondeur + 1)?.let { t -> return t } }
        return null
    }

    private fun texte(objet: JsonObject, cle: String): String? =
        (objet[cle] as? JsonPrimitive)?.contentOrNull

    private fun entier(objet: JsonObject, cle: String): Int? =
        (objet[cle] as? JsonPrimitive)?.intOrNull

    private fun booleen(objet: JsonObject, cle: String): Boolean? =
        (objet[cle] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

    /**
     * encodes a keyword for a query string
     *
     * written by hand rather than with `URLEncoder`, which is a java class, and this file lives
     * in `commonMain` precisely to stay testable without a platform. the unreserved character
     * set comes from rfc 3986
     */
    internal fun encoder(valeur: String): String = buildString {
        valeur.encodeToByteArray().forEach { octet ->
            val c = octet.toInt().toChar()
            when {
                c.isLetterOrDigit() && octet.toInt() in 0..127 -> append(c)
                c == '-' || c == '_' || c == '.' || c == '~' -> append(c)
                else -> append('%').append(
                    (octet.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
                )
            }
        }
    }
}

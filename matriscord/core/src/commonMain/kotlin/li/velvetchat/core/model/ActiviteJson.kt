package li.velvetchat.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * the activity, to and from the one string it travels in
 *
 * written by hand rather than with `@Serializable`, for the same reason the themes are: what
 * arrives is **somebody else's profile**, so it is untrusted input, and a parser that throws on
 * a surprise would take the screen down with it. every field is optional, every bad field is
 * dropped, and a payload that makes no sense gives null rather than an exception
 *
 * the only field that is not optional is the name, because an activity without one says nothing
 * and there would be nothing to draw
 */

/** the fields, as they are named inside the profile key. short, because they are written often */
private const val F_NOM = "n"
private const val F_DETAILS = "d"
private const val F_ETAT = "e"
private const val F_DEPUIS = "t"
private const val F_GRANDE = "g"
private const val F_PETITE = "p"
private const val F_APP = "a"
private const val F_PUBLIEE = "w"

/** how long a field may be before it is cut. a profile is not a place to store a novel */
private const val MAX_TEXTE = 128

/**
 * reads an activity out of the profile key
 *
 * @return null when the string is absent, unparseable, or carries no name
 */
fun activiteDepuisJson(brut: String?): Activite? {
    val texte = brut?.takeIf { it.isNotBlank() } ?: return null
    val objet = runCatching { Json.parseToJsonElement(texte) as? JsonObject }.getOrNull()
        ?: return null
    fun texteDe(cle: String) = (objet[cle] as? JsonPrimitive)?.contentOrNull
        ?.takeIf { it.isNotBlank() }?.take(MAX_TEXTE)
    val nom = texteDe(F_NOM) ?: return null
    return Activite(
        nom = nom,
        details = texteDe(F_DETAILS),
        etat = texteDe(F_ETAT),
        // a timestamp from elsewhere is not to be trusted either: a negative one, or one in the
        // far future, would draw a counter running backwards
        depuis = (objet[F_DEPUIS] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 },
        grandeImage = texteDe(F_GRANDE),
        petiteImage = texteDe(F_PETITE),
        applicationId = texteDe(F_APP),
        publieeLe = (objet[F_PUBLIEE] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 },
    )
}

/** writes an activity into the profile key. null fields are left out rather than written empty */
fun Activite.versJson(): String = buildJsonObject {
    put(F_NOM, JsonPrimitive(nom.take(MAX_TEXTE)))
    details?.let { put(F_DETAILS, JsonPrimitive(it.take(MAX_TEXTE))) }
    etat?.let { put(F_ETAT, JsonPrimitive(it.take(MAX_TEXTE))) }
    depuis?.takeIf { it > 0 }?.let { put(F_DEPUIS, JsonPrimitive(it)) }
    grandeImage?.let { put(F_GRANDE, JsonPrimitive(it.take(MAX_TEXTE))) }
    petiteImage?.let { put(F_PETITE, JsonPrimitive(it.take(MAX_TEXTE))) }
    applicationId?.let { put(F_APP, JsonPrimitive(it.take(MAX_TEXTE))) }
    publieeLe?.takeIf { it > 0 }?.let { put(F_PUBLIEE, JsonPrimitive(it)) }
}.toString()

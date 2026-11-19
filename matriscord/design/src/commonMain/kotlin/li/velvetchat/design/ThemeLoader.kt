package li.velvetchat.design

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * loading a theme from json
 *
 * guiding principle: a theme must never be able to break the app. a file that is missing,
 * truncated, malformed or written for a future version falls back on the default theme. that is
 * what makes hot reloading safe, you can edit the file while the app runs without risking a black
 * screen
 *
 * the module does not read a file itself, the platform supplies the text. that keeps it entirely in
 * common code, testable with no filesystem
 */
object ThemeLoader {

    private val json = Json {
        // a theme written for a newer version of the client has to stay loadable: unknown keys are
        // ignored rather than failing the read
        ignoreUnknownKeys = true
        // missing fields take the model's default value: a theme can therefore redefine only the
        // accent colour, which is the most common use
        isLenient = true
    }

    /**
     * velvet's theme when nobody has chosen one: velvet curfew
     *
     * the text below is an exact copy of `design/themes/velvet-curfew.json`, and `tools/verifier-
     * themes.py` fails when they diverge. copied rather than read: this module is common and reads
     * no file
     */
    val DEFAULT: Theme = json.decodeFromString<Theme>(VELVET_CURFEW)

    /**
     * @return the theme read, or [DEFAULT] when the text is unusable, along with the reason, so it
     *   can be shown in the settings rather than failing silently
     */
    fun parse(text: String?): ThemeResult {
        if (text.isNullOrBlank()) {
            return ThemeResult(DEFAULT, ThemeStatus.Default)
        }
        return try {
            // a partial theme is laid over the default theme, not over the model's values: a file
            // changing only the accent has to keep curfew's background, not a pure black
            val fichier = json.parseToJsonElement(text).jsonObject
            val base = json.encodeToJsonElement(Theme.serializer(), DEFAULT).jsonObject
            ThemeResult(json.decodeFromJsonElement(Theme.serializer(), fusion(base, fichier)), ThemeStatus.Loaded)
        } catch (t: Throwable) {
            ThemeResult(DEFAULT, ThemeStatus.Invalid(t.message ?: "unreadable JSON"))
        }
    }

    /**
     * `dessus` wins, object by object: `colors.accent` alone does not replace the whole of `colors`
     */
    private fun fusion(dessous: JsonObject, dessus: JsonObject): JsonObject = JsonObject(
        dessous + dessus.mapValues { (cle, valeur) ->
            val bas = dessous[cle]
            if (valeur is JsonObject && bas is JsonObject) fusion(bas, valeur) else valeur
        },
    )

    /**
     * the export format, built once
     *
     * `Json { }` recompiles its serialisers on every instance, the compiler says so, and here the
     * object never changes
     */
    private val joli = Json { prettyPrint = true }

    /** serialises a theme, to export the current one as an editable starting point */
    fun serialize(theme: Theme): String = joli.encodeToString(theme)
}

// start of velvet-curfew.json
private const val VELVET_CURFEW = """
{
  "name": "Velvet Curfew",
  "dark": true,
  "colors": {
    "background": "#151311",
    "surface": "#1C1917",
    "surfaceRaised": "#2A2321",
    "chatBackground": "#131110",
    "border": "#2E2724",
    "accent": "#7A3D4C",
    "accentMuted": "#4B262F",
    "textPrimary": "#EED3BA",
    "textSecondary": "#C4AC97",
    "textMuted": "#8C7A6C",
    "unreadBadge": "#7A3D4C",
    "unreadDot": "#EED3BA",
    "mentionHighlight": "#2A211A",
    "mentionBar": "#EED3BA",
    "traitCitation": "#3A322E",
    "danger": "#E0575B",
    "warning": "#D9A441",
    "success": "#3FB950",
    "scrimAlpha": 0.7
  }
}
"""

data class ThemeResult(
    val theme: Theme,
    val status: ThemeStatus,
)

sealed interface ThemeStatus {
    /** no theme supplied: the default one applies */
    data object Default : ThemeStatus

    data object Loaded : ThemeStatus

    /**
     * the theme is unusable and the default one applies. the reason is kept so it can be shown to
     * the user: a theme silently ignored is far more puzzling than a theme refused with a reason
     */
    data class Invalid(val reason: String) : ThemeStatus
}

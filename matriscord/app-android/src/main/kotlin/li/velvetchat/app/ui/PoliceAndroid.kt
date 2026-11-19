package li.velvetchat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import li.velvetchat.app.R

/**
 * velvet's font, and its emoji chain, everything the theme cannot carry
 *
 * split out of `Theme.kt` for the desktop plan's first batch. the theme became portable in the same
 * gesture: it receives its font family instead of building it
 *
 * this is not a mere convenience move. the chain built here rests on three things that only exist
 * here: `AndroidFont`, `Typeface.CustomFallbackBuilder`, and `R.font` resources. none has a common
 * compose equivalent, and an `expect`/`actual` would have forced the shared module to carry android
 * resources, for a setting each platform knows better than it does
 *
 * the rule that follows: the theme describes what has to be shown, and the platform supplies what
 * paints it. see `VelvetTheme(theme, famille)`
 */

/**
 * the app's font
 *
 * four weights, and that is what counts. without them compose synthesises the bold by thickening
 * the regular's strokes, which shows immediately on nicknames and titles, which look smudged.
 * supplying `Medium` and `SemiBold` on top of the usual pair also stops `FontWeight.SemiBold`, used
 * by unread room names, falling back on full bold and crushing the hierarchy it was there to
 * establish
 *
 * worth knowing if the app ever goes beyond its first users: `gg sans` belongs to discord and is not
 * redistributable. the replacement happens here, by changing the four lines below, `Inter` (ofl
 * licence) being the closest. nothing else needs touching: the font is supplied by `LocalTextStyle`
 * and inherited by every text
 *
 * `.woff2` files do not work: it is a web format, which android's font loader does not know
 */
@Composable
internal fun policeVelvet(): FontFamily {
    val contexte = androidx.compose.ui.platform.LocalContext.current
    return remember(contexte) { familleAvecEmojis(contexte) }
}

private fun graissesDeGgSans(contexte: android.content.Context): List<Pair<Int, Int>>? {
    val voulues = listOf(
        "gg_sans_regular" to 400,
        "gg_sans_medium" to 500,
        "gg_sans_semibold" to 600,
        "gg_sans_bold" to 700,
    )
    return voulues.map { (nom, poids) ->
        val id = contexte.resources.getIdentifier(nom, "font", contexte.packageName)
        if (id == 0) return null
        id to poids
    }
}

/**
 * the text font followed by twemoji, before the system font
 *
 * compose's `FontFamily(a, b, c)` is not a fallback chain: its entries are told apart by weight and
 * style, one kept per (weight, italic) pair, so stacking `gg sans` and an emoji font in it would
 * have changed nothing — the system fills missing glyphs, with its own emoji font. a fallback chain
 * is a platform notion, and [android.graphics.Typeface.CustomFallbackBuilder] is what builds one:
 * added families are consulted in order and `setSystemFallback` comes last, so any emoji, being a
 * character missing from `gg sans`, is looked for in twemoji before noto color emoji gets a say
 *
 * one switch point. every text inherits from `LocalTextStyle`, the picker's boxes and the bodies of
 * received messages alike, so changing emoji set happens here and nowhere else — including for
 * emoji typed from another client, which are only unicode characters like any other
 *
 * **one font per weight, not a family of four.** the first version put all four weights in a single
 * `Typeface` inside `FontFamily(typeface)`. it worked for the emoji and lost every bold: measured,
 * `PoliceEmojisTest` rendered "Velvet" normal and bold pixel for pixel identical. a single entry
 * family gets declined through `Typeface.create(family, weight, italic)`, and that does not find
 * the variants inside a `Typeface` built by `CustomFallbackBuilder`. so the weight is made visible
 * rather than guessed: four [AndroidFont], one per weight, each loading its own chain
 *
 * **the floor is api 29**, where `CustomFallbackBuilder` appeared, and nothing else sets it: the
 * embedded file is cbdt/cblc, exactly noto color emoji's format, the one android has always
 * painted. that was not obvious — the first file tried was mozilla's colr/cpal build, which android
 * paints only from 13 (api 33) and renders as black glyphs below, a silent failure and so worse
 * than an absence. and the chain is built before being promised: an unreadable resource drops the
 * whole app back to the system font, less pretty and never silent
 *
 * **the build changed, and the number that decided it is 61.** until then
 * `~whynothugo/twemoji.ttf`, jdecked's svgs through noto's pipeline, a cbdt, so images — chosen
 * because mozilla's colr reputedly needed a recent android and because `13rac1/twemoji-color-font`
 * carries only the `SVG ` table, which android does not paint. what that reasoning never looked at
 * was the size of those images: the `CBLC` table holds one strike, 61 by 61 pixels, where noto has
 * one at 136. on a density 3 screen a message emoji renders at 45 px and a jumbo past 110, so we
 * were enlarging a 61 pixel thumbnail. hence the blurriness reported in use, and the diagnosis fit
 * in one byte of the file, which only had to be read
 *
 * since then: twemoji mozilla 0.7.0, `COLR`/`CPAL`, outlines and colour layers, sharp at any size,
 * 1.4 mb instead of 3.4. the cost, which has to be known: that build is from 2022, where the cbdt
 * stood at 16.0.1, so emoji that appeared after it fall back on the system font — which is exactly
 * what every emoji did until today
 *
 * no network. the file sits in the apk and is read by `Font.Builder(res, …)`, and the criticism
 * made of twemoji on the web, `twemoji.js` fetching each image from a cdn, does not concern the
 * font format, which has nothing to ask anybody
 *
 * licence cc-by 4.0, so attribution, carried by the "about" screen and by the font's own `name`
 * ("Twitter, Inc and other contributors"), which removing would be the one way to break it. that is
 * the substantive difference with `gg sans`, redistributable under no condition: here the condition
 * exists and it is met
 */
internal fun familleAvecEmojis(contexte: android.content.Context): FontFamily {
    val graisses = graissesDeGgSans(contexte) ?: run {
        android.util.Log.w(
            "Velvet",
            "gg sans absente — la police du système prend le relais. Déposer les quatre .ttf " +
                "dans app-android/src/main/res/font/ pour la fidélité, voir NOTICE",
        )
        return FontFamily.Default
    }

    // the probe serves twice: it decides the fallback, and it warms the cache, so the first text
    // shown does not pay for parsing the font's 1.4 mb
    if (ChaineTwemoji.typeface(contexte.resources, graisses[0].first, 400) == null) {
        // this fallback was silent, and that is what let the fault last. the "about" screen
        // announces twemoji, the app shows the system's emojis, and nothing anywhere says which of
        // the two is true. see the same fix on the desktop side
        android.util.Log.w(
            horsUi.connexionTitre,
            "Twemoji indisponible — les emojis viennent de la police système",
        )
        return familleSansChaine(graisses)
    }
    return FontFamily(graisses.map { (id, poids) -> PoliceAvecEmojis(id, poids) })
}

/**
 * one weight of `gg sans`, followed by twemoji, followed by the system font
 *
 * `FontLoadingStrategy.Blocking` and not asynchronous: the chain is built from resources already in
 * the apk, with no network and no slow disk. an async load would flash every screen at startup to
 * save a handful of milliseconds once
 */
private class PoliceAvecEmojis(val police: Int, val poids: Int) : androidx.compose.ui.text.font.AndroidFont(
    androidx.compose.ui.text.font.FontLoadingStrategy.Blocking,
    ChargeurAvecEmojis,
    androidx.compose.ui.text.font.FontVariation.Settings(),
) {
    override val weight = FontWeight(poids)
    override val style = androidx.compose.ui.text.font.FontStyle.Normal
}

private object ChargeurAvecEmojis : androidx.compose.ui.text.font.AndroidFont.TypefaceLoader {
    override fun loadBlocking(
        context: android.content.Context,
        font: androidx.compose.ui.text.font.AndroidFont,
    ): android.graphics.Typeface? {
        val notre = font as? PoliceAvecEmojis ?: return null
        return ChaineTwemoji.typeface(context.resources, notre.police, notre.poids)
    }

    override suspend fun awaitLoad(
        context: android.content.Context,
        font: androidx.compose.ui.text.font.AndroidFont,
    ): android.graphics.Typeface? = loadBlocking(context, font)
}

/**
 * the built `Typeface`s, kept per weight
 *
 * without this cache, every recomposition resolving a font would reread the emoji font. four
 * entries are enough: that is the number of weights, and it will not move
 */
private object ChaineTwemoji {
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, android.graphics.Typeface>()

    @Volatile
    private var emojis: android.graphics.fonts.FontFamily? = null

    fun typeface(
        res: android.content.res.Resources,
        police: Int,
        poids: Int,
    ): android.graphics.Typeface? {
        // `CustomFallbackBuilder`'s floor, and nothing else: cbdt paints below it
        if (android.os.Build.VERSION.SDK_INT < 29) {
            android.util.Log.w("Velvet", "API ${android.os.Build.VERSION.SDK_INT} < 29 : pas de chaîne de repli")
            return null
        }
        cache[police]?.let { return it }
        val construite = runCatching {
            val repli = emojis ?: android.graphics.fonts.FontFamily.Builder(
                android.graphics.fonts.Font.Builder(res, R.font.twemoji).build()
            ).build().also { emojis = it }
            val texte = android.graphics.fonts.FontFamily.Builder(
                android.graphics.fonts.Font.Builder(res, police).setWeight(poids).build()
            ).build()
            android.graphics.Typeface.CustomFallbackBuilder(texte)
                .addCustomFallback(repli)
                .setSystemFallback("sans-serif")
                .build()
        }.onFailure {
            android.util.Log.w("Velvet", "chaîne Twemoji impossible à construire", it)
        }.getOrNull() ?: return null
        cache[police] = construite
        return construite
    }
}

/** `gg sans` alone, emojis left to the system. the fallback for devices before android 13 */
private fun familleSansChaine(graisses: List<Pair<Int, Int>>) =
    FontFamily(graisses.map { (id, poids) -> Font(id, FontWeight(poids)) })

/**
 * twemoji alone, with no chain and no fallback
 *
 * it is the family we apply on emoji spans, see `LocalPoliceEmoji`. it contains one font and serves
 * only that: asking it for a latin letter would give a box, which is fine since we never will
 */
internal val POLICE_TWEMOJI: FontFamily = FontFamily(Font(R.font.twemoji))

package li.velvetchat.desk

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * the house sans, loaded from the classpath
 *
 * the desktop counterpart of `PoliceAndroid.kt`. the difference is one line: android built an
 * explicit fallback chain to twemoji, because its system font does not render every emoji in
 * colour. skia hands over to the system's font manager, and on windows that is `Segoe UI Emoji`,
 * which renders them in colour
 *
 * what that costs, and it is worth watching: an emoji will therefore not be the same drawing as on
 * the phone. the house rule wants twemoji everywhere, and here the reference is windows'. bundling
 * twemoji and registering it with skia is doable, `FontMgr` accepts a loaded font, but it is a
 * separate job, and a readable system font beats a half written fallback chain
 *
 * if the four weights fail to load we return the system font rather than throw: an app that opens
 * with the wrong font stays usable, one that does not open is not
 */
fun policeVelvet(): FontFamily = runCatching {
    FontFamily(
        graisse("gg_sans_regular", FontWeight.Normal),
        graisse("gg_sans_medium", FontWeight.Medium),
        graisse("gg_sans_semibold", FontWeight.SemiBold),
        graisse("gg_sans_bold", FontWeight.Bold),
    )
}.getOrElse { erreur ->
    // the fallback speaks. it was silent, and a silent fallback on a project whose rule is pixel
    // perfect is the worst of both worlds: the app opens, everything looks normal, and the
    // typography is wrong with nothing saying so
    //
    // on standard error and not in a dialog: whoever launches velvet has no use for this, whoever
    // develops it does
    System.err.println(
        "velvet: gg sans n'a pas pu être chargée (${erreur::class.simpleName}: ${erreur.message})" +
            " — repli sur la police système",
    )
    FontFamily.Default
}

private fun graisse(nom: String, poids: FontWeight) = Font(
    identity = nom,
    data = octets("polices/$nom.ttf"),
    weight = poids,
)

private fun octets(chemin: String): ByteArray =
    checkNotNull(Donnees::class.java.classLoader.getResourceAsStream(chemin)) {
        "police absente du paquet : $chemin"
    }.use { it.readBytes() }

/**
 * twemoji on its own, to apply over the emoji spans, see `LocalPoliceEmoji`
 *
 * null when the file is missing: the text then keeps the theme's font and the emoji come from the
 * system. we degrade, we do not hide
 */
fun policeEmoji(): FontFamily? = runCatching {
    FontFamily(Font(identity = "twemoji", data = octets("polices/twemoji.ttf")))
}.getOrElse { erreur ->
    System.err.println("velvet: Twemoji absente (${erreur.message}) — emojis du système")
    null
}

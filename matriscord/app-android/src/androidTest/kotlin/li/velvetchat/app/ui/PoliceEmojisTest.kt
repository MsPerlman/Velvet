package li.velvetchat.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * the emoji set does come from twemoji, not from the system
 *
 * this test exists because the wiring has three ways of failing silently, and none produces an
 * exception: the fallback chain may not be consulted (we then keep android's emojis without
 * knowing), the `runCatching` may swallow a failed load, and a colr font on a device that cannot
 * paint it returns black glyphs. rereading the code tells none of these cases apart, only the image
 * does
 *
 * the method is comparative rather than absolute: you cannot describe what twemoji looks like in an
 * assertion, but you can require that the same character, rendered once with our family and once
 * with the system's, does not give the same image. one pixel of difference would happen from
 * antialiasing alone, so we ask for a clear divergence
 */
class PoliceEmojisTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lEmojiRenduDiffereDeCeluiDuSysteme() {
        val (notre, systeme) = rendreLesDeux()
        val total = min(notre.width, systeme.width) * min(notre.height, systeme.height)
        var differents = 0
        for (x in 0 until min(notre.width, systeme.width)) {
            for (y in 0 until min(notre.height, systeme.height)) {
                if (notre[x, y] != systeme[x, y]) differents++
            }
        }
        assertTrue(
            "Les deux rendus sont identiques : la chaîne de repli n'a pas pris, " +
                "l'application affiche encore les emojis du système " +
                "($differents pixels différents sur $total)",
            differents > total / 20,
        )
    }

    /**
     * the glyph is painted in colour, and not as a black fill
     *
     * it is the failure mode specific to colr fonts on a system that does not handle them: the font
     * loads, the glyphs exist, and everything comes out black. the previous test would pass, two
     * black images differing from a colour one, so this one is needed as well, and not instead
     */
    @Test
    fun leGlypheEstPeintEnCouleur() {
        val (notre, _) = rendreLesDeux()
        var colores = 0
        for (x in 0 until notre.width) {
            for (y in 0 until notre.height) {
                val p = notre[x, y]
                if (p.alpha < 0.5f) continue
                val ecart = max(max(p.red, p.green), p.blue) - min(min(p.red, p.green), p.blue)
                if (ecart > 0.15f) colores++
            }
        }
        assertTrue(
            "Aucun pixel saturé : le glyphe est rendu en noir, donc la police COLR " +
                "n'est pas peinte par cet appareil",
            colores > 40,
        )
    }

    /**
     * the weight has to survive the fallback chain
     *
     * `Typeface.create(family, weight, italic)` is what compose calls to honour a `FontWeight` on
     * an already built family, and if it lost the variants on the way, the whole app would go to
     * normal weight, a discreet fault and everywhere at once. so two renders of the same word,
     * normal and bold, must differ
     */
    @Test
    fun lesGraissesSurviventALaChaine() {
        lateinit var normal: androidx.compose.ui.graphics.ImageBitmap
        lateinit var gras: androidx.compose.ui.graphics.ImageBitmap
        compose.setContent {
            val famille = familleAvecEmojis(LocalContext.current)
            Row {
                Text(
                    "Velvet", fontSize = 40.sp, fontFamily = famille,
                    modifier = Modifier.testTag("normal"),
                )
                Text(
                    "Velvet", fontSize = 40.sp, fontFamily = famille,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.testTag("gras"),
                )
            }
        }
        normal = compose.onNodeWithTag("normal").captureToImage()
        gras = compose.onNodeWithTag("gras").captureToImage()
        assertTrue(
            "Le gras rend exactement la même image que le normal : les graisses ont été " +
                "perdues en construisant la chaîne de repli",
            normal.width != gras.width || !identiques(normal, gras),
        )
    }

    private fun identiques(
        a: androidx.compose.ui.graphics.ImageBitmap,
        b: androidx.compose.ui.graphics.ImageBitmap,
    ): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        val pa = a.toPixelMap()
        val pb = b.toPixelMap()
        for (x in 0 until a.width) for (y in 0 until a.height) {
            if (pa[x, y] != pb[x, y]) return false
        }
        return true
    }

    /**
     * the same emoji side by side, once with our family and once with the system's
     *
     * 😂 rather than any character: it is the most used, it is present in every set, and its flat
     * yellows and blues make the colour comparison clear
     */
    private fun rendreLesDeux(): Pair<
        androidx.compose.ui.graphics.PixelMap, androidx.compose.ui.graphics.PixelMap
        > {
        compose.setContent {
            Row {
                Text(
                    "😂", fontSize = 64.sp,
                    fontFamily = familleAvecEmojis(LocalContext.current),
                    modifier = Modifier.testTag("notre"),
                )
                Text(
                    "😂", fontSize = 64.sp,
                    fontFamily = FontFamily.Default,
                    modifier = Modifier.testTag("systeme"),
                )
            }
        }
        return compose.onNodeWithTag("notre").captureToImage().toPixelMap() to
            compose.onNodeWithTag("systeme").captureToImage().toPixelMap()
    }

    /** used to frame the colour gap above without depending on an external utility */
    @Suppress("unused")
    private fun ecartAbsolu(a: Float, b: Float) = abs(a - b)
}

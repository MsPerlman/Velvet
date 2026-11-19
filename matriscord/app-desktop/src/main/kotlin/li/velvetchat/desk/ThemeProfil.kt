package li.velvetchat.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import li.velvetchat.app.ui.toColor
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.ProfilEtendu

/**
 * a profile card's colours, derived from its theme: the popover, a member's card, the edit profile
 * preview. measured to the pixel off the references, on a purple to indigo theme
 *
 * | what is measured           | value                           | rule recovered
 * | |----------------------------|---------------------------------|-------------------------------
 * --------| | body, top / bottom         | (208,184,233) / (178,165,214)   | the theme faded 78%
 * towards (211,204,232) | | 2 px border, top / bottom  | (199,113,236) / (53,25,146)     | the
 * theme as it is, as a gradient      | | action blocks              | (213,197,234) on
 * (200,179,227)  | white at 24%                           | | block rules                |
 * (194,181,214)                   | black at 8.5%                          | | nickname, id, tag
 * | (9,8,10)                        |                                        | | bio
 * | (36,39,62)                      |                                        | | action labels and
 * icons    | (74,79,107)                     |                                        | | "send a
 * message"           | (155,97,185)                    | the theme's top darkened by 22%        | |
 * square buttons             | (243,236,249), icon (40,40,45)  | white at 80%
 * |
 *
 * a light theme, meaning a bright top, takes the dark ink: that is what the reference does and what
 * velvet did not, it wrote white on pale lilac. a dark theme takes the app's black veil and light
 * ink. with no theme, the reference's default dark card
 */
internal class CouleursProfil(
    val fond: Brush,
    /** the body's colour just under the banner: the avatar's ring and its dot take it */
    val fondHaut: Color,
    val bordure: Brush,
    val clair: Boolean,
    val texteFort: Color,
    val texte: Color,
    val texteDoux: Color,
    val bloc: Color,
    val filet: Color,
    val pastille: Color,
    val bordPastille: Color,
    val champ: Color,
    val boutonPrincipal: Color,
    val boutonSecondaire: Color,
    val iconeBouton: Color,
    val bulle: Color,
    val texteBulle: Color,
    /** the fill of a banner with no image */
    val teinteBanniere: Color = Color.Transparent,
)

private val VOILE_CLAIR = Color(0xFFD3CCE8)
private val VOILE_SOMBRE = Color(0xFF111214)

internal fun couleursProfil(etendu: ProfilEtendu?): CouleursProfil {
    val haut = etendu?.couleurBasse?.let { runCatching { it.toColor() }.getOrNull() }
    val bas = etendu?.couleurHaute?.let { runCatching { it.toColor() }.getOrNull() } ?: haut

    if (haut == null || bas == null) {
        // the reference's default card: a flat dark fill, with no theme border
        val fond = Color(0xFF232428)
        return CouleursProfil(
            fond = SolidColor(fond), fondHaut = fond, bordure = SolidColor(Color(0xFF2E3035)), clair = false,
            texteFort = Color(0xFFF2F3F5), texte = Color(0xFFDBDEE1), texteDoux = Color(0xFFB5BAC1),
            bloc = Color(0xFF2B2D31), filet = Color.White.copy(alpha = 0.06f),
            pastille = Color.White.copy(alpha = 0.08f), bordPastille = Color.White.copy(alpha = 0.10f),
            champ = Color(0xFF1E1F22), boutonPrincipal = Color(0xFF5865F2),
            boutonSecondaire = Color.White.copy(alpha = 0.10f), iconeBouton = Color(0xFFF2F3F5),
            bulle = Color(0xFF2B2D31), texteBulle = Color(0xFFDBDEE1), teinteBanniere = Color(0xFF5865F2),
        )
    }

    val clair = 0.299f * haut.red + 0.587f * haut.green + 0.114f * haut.blue > 0.5f
    return if (clair) {
        val h = lerp(haut, VOILE_CLAIR, 0.78f)
        CouleursProfil(
            fond = Brush.verticalGradient(listOf(h, lerp(bas, VOILE_CLAIR, 0.78f))), fondHaut = h,
            bordure = Brush.verticalGradient(listOf(haut, bas)), clair = true,
            texteFort = Color(0xFF09080A), texte = Color(0xFF24273E), texteDoux = Color(0xFF4A4F6B),
            bloc = Color.White.copy(alpha = 0.24f), filet = Color.Black.copy(alpha = 0.085f),
            pastille = Color.White.copy(alpha = 0.30f), bordPastille = Color.Black.copy(alpha = 0.08f),
            champ = Color.Black.copy(alpha = 0.08f), boutonPrincipal = lerp(haut, Color.Black, 0.22f),
            boutonSecondaire = Color.White.copy(alpha = 0.80f), iconeBouton = Color(0xFF28282D),
            bulle = Color(0xFFFFFDFE), texteBulle = Color(0xFF4F5160), teinteBanniere = haut,
        )
    } else {
        val h = lerp(haut, VOILE_SOMBRE, 0.6f)
        CouleursProfil(
            fond = Brush.verticalGradient(listOf(h, lerp(bas, VOILE_SOMBRE, 0.6f))), fondHaut = h,
            bordure = Brush.verticalGradient(listOf(haut, bas)), clair = false,
            texteFort = Color(0xFFF2F3F5), texte = Color(0xFFDBDEE1), texteDoux = Color(0xFFB5BAC1),
            bloc = Color.Black.copy(alpha = 0.22f), filet = Color.White.copy(alpha = 0.08f),
            pastille = Color.Black.copy(alpha = 0.30f), bordPastille = Color.White.copy(alpha = 0.08f),
            champ = Color.Black.copy(alpha = 0.22f), boutonPrincipal = lerp(haut, Color.Black, 0.22f),
            boutonSecondaire = Color.White.copy(alpha = 0.12f), iconeBouton = Color(0xFFF2F3F5),
            bulle = Color(0xFF2B2D31), texteBulle = Color(0xFFDBDEE1), teinteBanniere = haut,
        )
    }
}

/**
 * the reference's status icon rather than a plain dot: a green circle for online, a yellow moon for
 * idle, a barred red circle for do not disturb, a grey ring for invisible or offline. the cutouts
 * are real holes, the background shows through, at the reference's mask proportions
 */
@Composable
internal fun IconePresence(presence: Presence, taille: Dp, silence: Boolean = false, modifier: Modifier = Modifier) {
    // the drawing moved into `ui-commun` (`IconeEtat`): the avatar needs it to place the icon in
    // its notch, and two drawings of the same thing would eventually drift
    li.velvetchat.app.ui.IconeEtat(presence, taille, silence, modifier)
}

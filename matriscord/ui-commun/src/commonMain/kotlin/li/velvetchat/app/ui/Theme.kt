package li.velvetchat.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import li.velvetchat.design.Theme

/**
 * the bridge between the `design` module's tokens and compose
 *
 * the tokens are deliberately text (`"#1FB6A8"`) and integers, with no dependency on compose or
 * android, which is what lets them be reloaded from a file at runtime and tested with no platform.
 * so the translation into compose's types lives here, in one place
 *
 * what is not delegated to `MaterialTheme`: material 3's roles (primary, secondary, tertiary and
 * the rest) do not match a messaging client's vocabulary. we fill the scheme so material components
 * have consistent colours, but the ui reads [LocalTokens]. talking about `surfaceRaised` and
 * `unreadBadge` rather than `surfaceContainerHighest` keeps the direct link with the theme file
 */
val LocalTokens = staticCompositionLocalOf { li.velvetchat.design.ThemeLoader.DEFAULT }

@Composable
fun VelvetTheme(
    theme: Theme = li.velvetchat.design.ThemeLoader.DEFAULT,
    /**
     * the font comes from the platform, not from the theme
     *
     * it used to be built here, as a chain from the house face through twemoji to the system one,
     * using `Typeface.CustomFallbackBuilder` and `R.font` resources, three things that only exist
     * on android. that single call was what held this whole file on that side, and with it the
     * twenty four screens that depend on `LocalTokens`
     *
     * the theme now describes what to display and each platform supplies what paints it. on
     * android, see `policeVelvet()` in `PoliceAndroid.kt`
     *
     * the default is the system font: a caller that supplies none, an instrumented test for
     * instance, gets readable text rather than a crash
     */
    famille: FontFamily = FontFamily.Default,
    /**
     * the emoji font, applied over their spans rather than hoped for as a fallback
     *
     * it goes through the theme for the same reason as [famille]: this is the single point where
     * the app's typography is decided, and an omission elsewhere would leave one screen with the
     * system's emoji. see `LocalPoliceEmoji` for the three fallback attempts that failed before
     */
    policeEmoji: FontFamily? = null,
    content: @Composable () -> Unit,
) {
    val c = theme.colors
    val scheme = if (theme.dark) {
        darkColorScheme(
            primary = c.accent.toColor(),
            onPrimary = c.background.toColor(),
            background = c.background.toColor(),
            onBackground = c.textPrimary.toColor(),
            surface = c.surface.toColor(),
            onSurface = c.textPrimary.toColor(),
            surfaceVariant = c.surfaceRaised.toColor(),
            onSurfaceVariant = c.textSecondary.toColor(),
            outline = c.border.toColor(),
            error = c.danger.toColor(),
        )
    } else {
        lightColorScheme(
            primary = c.accent.toColor(),
            background = c.background.toColor(),
            surface = c.surface.toColor(),
            error = c.danger.toColor(),
        )
    }

    CompositionLocalProvider(
        LocalTokens provides theme,
        // the single point where the whole app's font is decided
        //
        // `LocalTextStyle` is inherited by every `Text` that does not name its own family, which is
        // all of ours. doing it here saves repeating `fontFamily` in a hundred places and, above
        // all, stops one omission leaving a screen in another font
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = famille),
        LocalPoliceEmoji provides policeEmoji,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * `#RRGGBB` or `#AARRGGBB` to a compose colour
 *
 * tolerant by design: a theme is a file somebody can edit by hand, and a badly written colour must
 * not take the app down. it falls back to magenta, which is visible immediately, and that beats a
 * plausible hue that would go unnoticed
 */
fun String.toColor(): Color = runCatching {
    val hex = removePrefix("#")
    when (hex.length) {
        6 -> Color(0xFF000000L or hex.toLong(16))
        8 -> Color(hex.toLong(16))
        else -> error("longueur inattendue")
    }
}.getOrElse { Color.Magenta }

// reading shortcuts: `tokens.accent` rather than `LocalTokens.current.colors.accent`
val Theme.accent get() = colors.accent.toColor()
val Theme.bg get() = colors.background.toColor()
val Theme.surface get() = colors.surface.toColor()
val Theme.surfaceRaised get() = colors.surfaceRaised.toColor()
val Theme.border get() = colors.border.toColor()
val Theme.textPrimary get() = colors.textPrimary.toColor()
val Theme.textSecondary get() = colors.textSecondary.toColor()
val Theme.textMuted get() = colors.textMuted.toColor()
val Theme.danger get() = colors.danger.toColor()
val Theme.unreadBadge get() = colors.unreadBadge.toColor()

/** the gold background of a row where you are named. declared from the start, used only recently */
val Theme.mentionHighlight get() = colors.mentionHighlight.toColor()
val Theme.mentionBar get() = colors.mentionBar.toColor()
val Theme.traitCitation get() = colors.traitCitation.toColor()
val Theme.unreadDot get() = colors.unreadDot.toColor()
val Theme.chatBg get() = colors.chatBackground.toColor()
val Theme.warning get() = colors.warning.toColor()
val Theme.success get() = colors.success.toColor()

val Theme.radiusSmall get() = radii.small.dp
val Theme.radiusMedium get() = radii.medium.dp
val Theme.radiusLarge get() = radii.large.dp

// the spacing scale, finally named. `tokens.espXs` rather than `4.dp`
val Theme.espXs get() = spacing.xs.dp
val Theme.espS get() = spacing.s.dp
val Theme.espM get() = spacing.m.dp
val Theme.espL get() = spacing.l.dp
val Theme.espXl get() = spacing.xl.dp
val Theme.espEcran get() = spacing.ecran.dp

val Theme.tailleIconeServeur get() = tailles.iconeServeur.dp
val Theme.tailleBadge get() = tailles.badge.dp
val Theme.tailleBoutonEnvoi get() = tailles.boutonEnvoi.dp

/**
 * the theme's curves, translated into compose `Easing`
 *
 * `motion.enabled == false` does not change the curve but the duration: bringing the durations to
 * zero is what actually stops the movement, a curve applied over zero milliseconds having no
 * effect. hence [dureeNormale] and friends rather than reading the fields directly, since otherwise
 * every caller would have to remember to test the switch, and one of them would eventually forget
 */
val Theme.easingEntree: Easing
    get() = motion.enterCurve.let { CubicBezierEasing(it.x1, it.y1, it.x2, it.y2) }

val Theme.easingSortie: Easing
    get() = motion.exitCurve.let { CubicBezierEasing(it.x1, it.y1, it.x2, it.y2) }

val Theme.dureeRapide get() = if (motion.enabled) motion.fastMs else 0
val Theme.dureeNormale get() = if (motion.enabled) motion.normalMs else 0
val Theme.dureeLente get() = if (motion.enabled) motion.slowMs else 0

/** the spring for gestures, set by the tokens rather than by compose's constants */
fun <T> Theme.ressort(): SpringSpec<T> =
    spring(dampingRatio = motion.springDamping, stiffness = motion.springStiffness)

/**
 * the drawer's spring, stiffer and better damped than the one for short gestures
 *
 * see `Motion.drawerSpringStiffness` for why: the same stiffness gives the same duration whatever
 * the distance, so the setting that makes a message row springy makes a full screen panel feel limp
 */
fun <T> Theme.ressortTiroir(): SpringSpec<T> =
    spring(dampingRatio = motion.drawerSpringDamping, stiffness = motion.drawerSpringStiffness)

/**
 * one page sliding over another, on a curve measured off the reference
 *
 * a `tween` and not a spring, and that is the point: the travel is triggered by a tap, not released
 * by a finger. a spring is only interesting for picking up existing velocity, and there is none
 * here, so it would only add a shape nobody chose
 *
 * honours the motion switch like the durations do: at zero, the page is simply there
 */
fun <T> Theme.glissementPage(): TweenSpec<T> = tween(
    durationMillis = if (motion.enabled) motion.pageSlideMs else 0,
    easing = motion.pageSlideCurve.let { CubicBezierEasing(it.x1, it.y1, it.x2, it.y2) },
)

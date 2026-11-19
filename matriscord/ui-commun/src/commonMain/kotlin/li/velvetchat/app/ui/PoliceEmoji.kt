package li.velvetchat.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/**
 * twemoji, applied explicitly rather than hoped for as a fallback
 *
 * three attempts failed before this one, and each was measured
 *
 * 1. android's `CustomFallbackBuilder` chain builds with no error, and the glyphs stay the system's
 * 2. `androidx.emoji2` disabled: 0 pixels of difference out of 475 200 3. twemoji added to the
 * desktop's family: 0 pixels out of 67 200
 *
 * what those three failures say together: glyph fallback is not ours to control. both platforms
 * pick a font for an emoji before our family gets a say, and no portable api reverses that order
 *
 * what is left, and works because it asks nobody for anything: put the font on the span. we know
 * where the emoji are, `porteesEmojis` already says so and `RichText` has been calling it for weeks
 * to keep an emoji upright. the same sweep now carries the family too
 *
 * the file itself was checked rather than assumed: cbdt/cblc, 172 `cmap` ranges, the emoji we care
 * about covered. the font was never the problem
 *
 * null means the platform supplies none and the text keeps the theme's font, which is the case in
 * tests and previews
 */
val LocalPoliceEmoji = staticCompositionLocalOf<FontFamily?> { null }

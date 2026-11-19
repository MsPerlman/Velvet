package li.velvetchat.design

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * is this colour readable on that background
 *
 * ## why this exists
 *
 * the role colour picker was a fixed palette on purpose, and the reason was written down twice: a
 * free colour lets you make a role unreadable on the dark theme, and checking that "would want a
 * contrast check, so one more screen". this is the check, so the picker can be opened without the
 * objection standing
 *
 * ## it reports, it does not refuse, and that is a measurement not a preference
 *
 * the first version of this file blocked anything under 3:1 and claimed in its own comment that
 * the floor was chosen so as not to reject the palette we already ship. measured against that
 * palette on `surface` (#0D0E11): **four of the eighteen are under 3:1**, #5335B3 at 2.32, #3C45A5
 * at 2.38, #7E32B3 at 2.75 and #A32B58 at 2.80. on `surfaceRaised` (#232428) it is five, the
 * lowest at 1.87. so the guard would have refused colours the app offers in its own grid
 *
 * lowering the floor until they pass would put it near 1.8, which passes very nearly everything
 * and measures nothing. so the floor stays at a number that means something and the picker shows
 * the ratio rather than enforcing it: you are told the colour is hard to read, and you decide. the
 * objection was that nobody could tell, and now they can
 *
 * ## the formula, and why this one
 *
 * wcag 2.1 relative luminance and contrast ratio. not a preference: it is the only contrast
 * measure with a published threshold anybody agrees on, which is what makes "hard to read"
 * reportable instead of a matter of taste
 *
 * ## where the floor comes from
 *
 * 3:1 is wcag's large text level, and a role colour is drawn on a nickname in bold, which is large
 * text by the standard's own definition. 4.5:1, the body text level, would flag eight of the
 * eighteen
 */
public const val CONTRASTE_PLANCHER: Double = 3.0

/** the wcag relative luminance of an srgb colour, channels in 0..255 */
public fun luminance(rouge: Int, vert: Int, bleu: Int): Double {
    fun canal(valeur: Int): Double {
        val v = valeur.coerceIn(0, 255) / 255.0
        // the linearisation, which is the step a hand written version drops. without it a mid grey
        // reads far lighter than it looks and the check passes colours it should stop
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * canal(rouge) + 0.7152 * canal(vert) + 0.0722 * canal(bleu)
}

/**
 * the wcag contrast ratio between two colours, from 1.0 (identical) to 21.0 (black on white)
 *
 * both are `0xAARRGGBB`, and the alpha is ignored: a half transparent role colour is composited
 * over the background before it is read, and this is asked before anything is drawn. so the
 * answer is for the colour at full strength, which is the worst case in the direction that
 * matters, a pale colour only gets paler
 */
public fun contraste(couleur: Int, fond: Int): Double {
    val a = luminance((couleur shr 16) and 0xFF, (couleur shr 8) and 0xFF, couleur and 0xFF)
    val b = luminance((fond shr 16) and 0xFF, (fond shr 8) and 0xFF, fond and 0xFF)
    val clair = max(a, b)
    val sombre = min(a, b)
    return (clair + 0.05) / (sombre + 0.05)
}

/**
 * does [couleur] clear [CONTRASTE_PLANCHER] on [fond]
 *
 * false is a warning to show, never a refusal: see the note above on the palette this would have
 * rejected
 */
public fun contrasteSuffisant(couleur: Int, fond: Int): Boolean =
    contraste(couleur, fond) >= CONTRASTE_PLANCHER

package li.velvetchat.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * the free colour picker, modelled on the reference's
 *
 * ## what it replaces
 *
 * twelve prepared gradients, and nothing else. the reasoning of the time, that a colour wheel
 * mostly offers the chance to compose an unreadable gradient, holds for a role colour, which you
 * read on an imposed background. it does not hold for your own profile theme: that is exactly where
 * you want your colour, not one of the twelve we were kind enough to foresee. said outright, and
 * rightly: the colour pickers are too restricted and not practical
 *
 * ## the four pieces, and why each
 *
 *     - the hexadecimal field, because a colour is passed on in writing. it is the only way of taking
 *       exactly the one from somewhere else, and it is bidirectional, typing `#c871ec` moves the cursor,
 *       and moving the cursor rewrites the field
 *     - the derived shades, six variants of the current hue. the reference recomputes them on every
 *       change rather than offering a frozen palette: they are shortcuts to "the same colour, darker /
 *       paler", which is what you want nine times out of ten
 *     - the saturation by value square, where you aim
 *     - the hue slider, which picks the family
 *
 * ## why hsv and not rgb
 *
 * the red green blue triplet cannot be handled by hand: lightening a colour there means moving
 * three numbers at once, in proportions that depend on the starting colour. hue, saturation and
 * value separate the three questions you actually ask, which colour, how vivid, how light, and that
 * is why every picker chooses it, the reference's included
 */
@Composable
fun FeuilleCouleur(
    titre: String = textes().choisirUneCouleur,
    couleurInitiale: String?,
    /**
     * the second colour, when what you are composing is a gradient
     *
     * the profile theme's two colours were chosen one after the other, each in its own sheet, so
     * you never saw the gradient while composing it, which is the whole object of the setting.
     * having two colour pickers side by side would be useful, and it is. when supplied, it adds the
     * preview and the two dots at the top, and "select" sets both at once through [onChoisirDeux]
     */
    secondeInitiale: String? = null,
    onChoisirDeux: ((String, String) -> Unit)? = null,
    onChoisir: (String) -> Unit = {},
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current

    // the state lives in hsv and not in hexadecimal: converting on every gesture would make the hue
    // of a nearly black colour drift, its red, green and blue no longer carrying it
    var tsv by remember {
        mutableStateOf(versTsv(couleurInitiale?.let { couleurOuNull(it) } ?: Color(0xFF5865F2)))
    }
    // the field's text is separate from the colour: while you are typing "#c8", the value is not
    // readable yet, and jumping to a colour on every character would make typing maddening
    var texte by remember { mutableStateOf(enHexa(depuisTsv(tsv))) }

    // a gradient is composed of two: the other colour lives here, and the dots say which one the
    // picker is changing
    val deux = onChoisirDeux != null
    var autre by remember {
        mutableStateOf(versTsv(secondeInitiale?.let { couleurOuNull(it) } ?: Color(0xFF9B59B6)))
    }
    var surPremiere by remember { mutableStateOf(true) }

    fun poser(nouveau: Triple<Float, Float, Float>) {
        tsv = nouveau
        texte = enHexa(depuisTsv(nouveau))
    }

    /** switching from one colour to the other: the one you leave is set aside as it is */
    fun basculer(versPremiere: Boolean) {
        if (versPremiere == surPremiere) return
        val garde = tsv
        tsv = autre
        autre = garde
        surPremiere = versPremiere
        texte = enHexa(depuisTsv(tsv))
    }

    val couleur = depuisTsv(tsv)

    FeuilleBasse(onFermer = onFermer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = tokens.espS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                titre,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .clickable {
                        if (onChoisirDeux != null) {
                            val premiere = if (surPremiere) couleur else depuisTsv(autre)
                            val seconde = if (surPremiere) depuisTsv(autre) else couleur
                            onChoisirDeux(enHexa(premiere), enHexa(seconde))
                        } else {
                            onChoisir(enHexa(couleur))
                        }
                        onFermer()
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    t.selectionner,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }

        // the preview and the two colours
        if (deux) {
            val premiere = if (surPremiere) couleur else depuisTsv(autre)
            val seconde = if (surPremiere) depuisTsv(autre) else couleur
            Box(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espXl).height(64.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(Brush.verticalGradient(listOf(premiere, seconde))),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PastilleCouleur(t.primaire, premiere, surPremiere, Modifier.weight(1f)) { basculer(true) }
                PastilleCouleur(t.secondaire, seconde, !surPremiere, Modifier.weight(1f)) { basculer(false) }
            }
        }

        // the hexadecimal field
        Box(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espXl)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .border(1.dp, tokens.border, RoundedCornerShape(tokens.radiusMedium))
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            BasicTextField(
                value = texte,
                onValueChange = { saisi ->
                    texte = saisi.take(7)
                    // we only move the colour when the input is complete. a half typed value is not
                    // a colour, and reading it would make the cursor jump on every character
                    couleurOuNull(texte)?.let { tsv = versTsv(it) }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(tokens.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // the derived shades
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            nuancesDe(tsv).forEach { candidate ->
                val choisie = enHexa(depuisTsv(candidate)) == enHexa(couleur)
                Box(
                    Modifier.weight(1f).height(46.dp)
                        .clip(RoundedCornerShape(tokens.radiusMedium))
                        .background(depuisTsv(candidate))
                        .clickable { poser(candidate) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (choisie) {
                        Text("✓", color = Color(0xFF101010), fontSize = 20.sp)
                    }
                }
            }
        }

        // the saturation by value square
        //
        // two superimposed gradients: white to pure hue horizontally, transparent to black
        // vertically. it is the usual construction, and it gives the right result without having to
        // compute a colour per pixel
        Box(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espXl)
                .aspectRatio(1.05f)
                .clip(RoundedCornerShape(tokens.radiusMedium)),
        ) {
            val teintePure = depuisTsv(Triple(tsv.first, 1f, 1f))
            // at the square's dimensions, and not 1000 points high
            //
            // the canvas measured `height(1000.dp)` inside a box that cropped it: the gradient
            // towards black therefore fell out of view, and above all `size.height` was a thousand
            // points, so a press anywhere in the visible square returned a value close to 1. you
            // could neither darken nor aim, hence the picker being reported as not working
            Canvas(
                Modifier.matchParentSize()
                    .pointerInput(Unit) {
                        // the tap and the drag: aiming at a colour then adjusting it are two
                        // gestures, and offering only the second would force you to start from
                        // where you are
                        detectTapGestures { position ->
                            poser(depuisPosition(position, size.width, size.height, tsv.first))
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { changement, _ ->
                            changement.consume()
                            poser(
                                depuisPosition(
                                    changement.position, size.width, size.height, tsv.first,
                                )
                            )
                        }
                    },
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, teintePure)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                // the sight: a ring and not a filled dot, so you can see the colour you are
                // pointing at through it
                val x = tsv.second * size.width
                val y = (1f - tsv.third) * size.height
                drawCircle(Color.White, radius = 26f, center = Offset(x, y), style = anneau())
                drawCircle(Color.Black, radius = 20f, center = Offset(x, y), style = anneau(3f))
            }
        }

        // ------------------------------------------------------------- the hue slider
        Box(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 18.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(tokens.radiusMedium)),
        ) {
            Canvas(
                Modifier.fillMaxWidth().height(34.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { position ->
                            poser(tsv.copy(first = (position.x / size.width * 360f).coerceIn(0f, 360f)))
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { changement, _ ->
                            changement.consume()
                            poser(
                                tsv.copy(
                                    first = (changement.position.x / size.width * 360f)
                                        .coerceIn(0f, 360f)
                                )
                            )
                        }
                    },
            ) {
                drawRect(Brush.horizontalGradient(ARC_EN_CIEL))
                val x = tsv.first / 360f * size.width
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(x - 14f, 0f),
                    size = androidx.compose.ui.geometry.Size(28f, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
                    style = anneau(6f),
                )
            }
        }
    }
}

private fun anneau(largeur: Float = 5f) =
    androidx.compose.ui.graphics.drawscope.Stroke(width = largeur)

private val ARC_EN_CIEL = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/** the position in the square, in saturation and value. the hue does not move */
private fun depuisPosition(
    position: Offset,
    largeur: Int,
    hauteur: Int,
    teinte: Float,
): Triple<Float, Float, Float> = Triple(
    teinte,
    (position.x / largeur.coerceAtLeast(1)).coerceIn(0f, 1f),
    1f - (position.y / hauteur.coerceAtLeast(1)).coerceIn(0f, 1f),
)

/**
 * six shades of the current hue, the row the reference recomputes on every change
 *
 * these are not six random colours: they are shortcuts to "the same colour, darker" and "the same,
 * paler", which is what you want nine times out of ten. the last is the current colour itself, so
 * that a box always looks ticked
 */
private fun nuancesDe(tsv: Triple<Float, Float, Float>): List<Triple<Float, Float, Float>> {
    val (teinte, _, _) = tsv
    return listOf(
        Triple(teinte, 0.85f, 0.28f),
        Triple(teinte, 0.75f, 0.55f),
        Triple(teinte, 0.60f, 0.80f),
        Triple(teinte, 0.35f, 0.95f),
        Triple(teinte, 0.15f, 1.00f),
        tsv,
    )
}

/**
 * rgb to hsv, in kotlin rather than in `android.graphics`
 *
 * the formula is every textbook's and fits in eight lines, and `colorToHSV` did nothing more.
 * replacing it removes the only android dependency of an otherwise entirely portable screen, see
 * `PLAN-DESKTOP.md`: what counts is not the number of android calls but the number of files they
 * hold on this side
 *
 * hue in degrees (0 to 360), saturation and value between 0 and 1, exactly like android's version:
 * the callers have nothing to change
 */
internal fun versTsv(couleur: Color): Triple<Float, Float, Float> {
    val r = couleur.red
    val v = couleur.green
    val b = couleur.blue
    val max = maxOf(r, v, b)
    val min = minOf(r, v, b)
    val ecart = max - min

    val teinte = when {
        // a grey has no hue. zero is android's convention, kept as is so the slider does not jump
        // when passing through white or black
        ecart == 0f -> 0f
        max == r -> 60f * (((v - b) / ecart) % 6f)
        max == v -> 60f * ((b - r) / ecart + 2f)
        else -> 60f * ((r - v) / ecart + 4f)
    }
    return Triple(
        if (teinte < 0f) teinte + 360f else teinte,
        if (max == 0f) 0f else ecart / max,
        max,
    )
}

/** hsv to rgb. the exact inverse of [versTsv] */
internal fun depuisTsv(tsv: Triple<Float, Float, Float>): Color {
    val (teinte, saturation, valeur) = tsv
    val c = valeur * saturation
    val x = c * (1f - kotlin.math.abs(((teinte / 60f) % 2f) - 1f))
    val m = valeur - c
    val (r, v, b) = when {
        teinte < 60f -> Triple(c, x, 0f)
        teinte < 120f -> Triple(x, c, 0f)
        teinte < 180f -> Triple(0f, c, x)
        teinte < 240f -> Triple(0f, x, c)
        teinte < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, v + m, b + m)
}

private fun enHexa(couleur: Color): String = String.format(
    "#%02x%02x%02x",
    (couleur.red * 255).toInt(),
    (couleur.green * 255).toInt(),
    (couleur.blue * 255).toInt(),
)

/**
 * a `#RRGGBB` colour, or `null` when it is unreadable
 *
 * `parseColor` throws on an invalid string, and these values come from a field being typed, so
 * invalid most of the time, by construction. an incomplete input has to leave the colour where it
 * is, never bring the screen down
 */
internal fun couleurOuNull(valeur: String): Color? = runCatching {
    // the same format as the theme (`String.toColor`), and deliberately no more: `parseColor` also
    // accepted "red" or "darkgray", names no field in this app produces and that nobody would type
    // into a hexadecimal picker
    val hex = valeur.trim().removePrefix("#")
    when (hex.length) {
        6 -> Color(0xFF000000L or hex.toLong(16))
        8 -> Color(hex.toLong(16))
        else -> error("longueur inattendue")
    }
}.getOrNull()

/** one of a gradient's two colours, clickable: the active one carries the accent outline */
@Composable
private fun PastilleCouleur(
    libelle: String,
    couleur: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Column(
        modifier.clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised)
            .border(
                if (active) 2.dp else 1.dp,
                if (active) tokens.accent else tokens.border,
                RoundedCornerShape(tokens.radiusMedium),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(6.dp)).background(couleur))
        Text(
            libelle,
            color = if (active) tokens.textPrimary else tokens.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

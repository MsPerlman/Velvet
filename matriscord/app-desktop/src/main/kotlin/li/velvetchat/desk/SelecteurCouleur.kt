package li.velvetchat.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.app.ui.curseurMain
import kotlin.math.roundToInt
import li.velvetchat.app.ui.textes

/**
 * velvet's own colour picker, in place of `JColorChooser`, which was judged unusable: a dark card
 * in the desktop menus' style, a saturation and lightness square, a hue slider, a preview and a
 * `#RRGGBB` field, starting swatches, cancel and apply
 *
 * no screenshot of the reference's: drawn on its principle (square plus hue plus hex), to be
 * remeasured when one turns up
 */
@Composable
internal fun SelecteurCouleur(
    titre: String,
    initiale: Color,
    /**
     * the gradient's second colour, when there is one
     *
     * a profile theme is two colours, and they were picked one after the other without ever seeing
     * the gradient they make. filled in, it adds the preview and the two swatches: the one you
     * touch is the one the picker edits, and apply sets both
     */
    seconde: Color? = null,
    onValiderDeux: ((Color, Color) -> Unit)? = null,
    onValider: (Color) -> Unit = {},
    onFermer: () -> Unit,
) {
    val t = textes()
    val depart = remember(initiale) { versHsv(initiale) }
    var teinte by remember { mutableFloatStateOf(depart[0]) }
    var saturation by remember { mutableFloatStateOf(depart[1]) }
    var valeur by remember { mutableFloatStateOf(depart[2]) }
    val couleur = Color.hsv(teinte, saturation, valeur)
    var hexa by remember { mutableStateOf(hexDe(initiale)) }
    var autre by remember(seconde) { mutableStateOf(seconde ?: Color(0xFF9B59B6)) }
    var surPremiere by remember { mutableStateOf(true) }
    val premiere = if (surPremiere) couleur else autre
    val deuxieme = if (surPremiere) autre else couleur

    /** switching from one colour to the other: the one you leave is set aside as it is */
    fun basculer(versPremiere: Boolean) {
        if (versPremiere == surPremiere) return
        val garde = couleur
        val cible = versHsv(autre)
        teinte = cible[0]; saturation = cible[1]; valeur = cible[2]
        autre = garde
        surPremiere = versPremiere
        hexa = hexDe(autre)
    }
    // the field follows the colour while you drag it, and does not rewrite what you are typing
    val hexaAffiche = if (hexDeOuNull(hexa) == couleur) hexa else hexDe(couleur)
    val sansEffet = remember { MutableInteractionSource() }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = sansEffet, indication = null, onClick = onFermer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(260.dp).clip(RoundedCornerShape(8.dp)).background(FOND_MENU)
                .border(1.dp, BORD_MENU, RoundedCornerShape(8.dp))
                .clickable(interactionSource = sansEffet, indication = null) { }
                .padding(12.dp),
        ) {
            Text(titre, color = Color(0xFFF2F3F5), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            // the theme's two colours: the gradient preview, then the swatches
            if (onValiderDeux != null) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(6.dp))
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(premiere, deuxieme))),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    PastilleDeuxCouleurs(t.primaire, premiere, surPremiere, Modifier.weight(1f)) { basculer(true) }
                    PastilleDeuxCouleurs(t.secondaire, deuxieme, !surPremiere, Modifier.weight(1f)) { basculer(false) }
                }
                Spacer(Modifier.height(10.dp))
            }

            // the square: the pure hue, whitened to the left, blackened downwards
            Box(
                Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(6.dp))
                    .drawBehind {
                        drawRect(Color.hsv(teinte, 1f, 1f))
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        val centre = Offset(saturation * size.width, (1f - valeur) * size.height)
                        drawCircle(Color.White, radius = 7.dp.toPx(), center = centre, style = Stroke(2.dp.toPx()))
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.first()
                                if (c.pressed) {
                                    saturation = (c.position.x / size.width).coerceIn(0f, 1f)
                                    valeur = 1f - (c.position.y / size.height).coerceIn(0f, 1f)
                                    c.consume()
                                }
                            }
                        }
                    },
            )
            Spacer(Modifier.height(10.dp))

            // the hue slider
            Box(
                Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
                    .drawBehind {
                        drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                        val x = teinte / 360f * size.width
                        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, size.height / 2), style = Stroke(2.dp.toPx()))
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.first()
                                if (c.pressed) {
                                    teinte = (c.position.x / size.width).coerceIn(0f, 1f) * 359.9f
                                    c.consume()
                                }
                            }
                        }
                    },
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(couleur).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = hexaAffiche,
                    onValueChange = { saisi ->
                        hexa = saisi
                        hexDeOuNull(saisi)?.let { c ->
                            val hsv = versHsv(c)
                            teinte = hsv[0]; saturation = hsv[1]; valeur = hsv[2]
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFFF2F3F5), fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFFF2F3F5)),
                    modifier = Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF111214)).padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            // some starting points: the usual role colours
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0xFF1ABC9C, 0xFF2ECC71, 0xFF3498DB, 0xFF9B59B6, 0xFFE91E63, 0xFFF1C40F, 0xFFE67E22, 0xFFE74C3C, 0xFF95A5A6)
                    .forEach { argb ->
                        val c = Color(argb)
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(c).curseurMain().clickable {
                                val hsv = versHsv(c)
                                teinte = hsv[0]; saturation = hsv[1]; valeur = hsv[2]
                                hexa = hexDe(c)
                            },
                        )
                    }
            }
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.annuler,
                    color = Color(0xFFDBDEE1),
                    fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).curseurMain().clickable(onClick = onFermer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    t.appliquer,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF5865F2))
                        .curseurMain().clickable {
                            if (onValiderDeux != null) onValiderDeux(premiere, deuxieme)
                            else onValider(couleur)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

internal fun hexDe(c: Color): String =
    String.format("#%02X%02X%02X", (c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

private fun hexDeOuNull(texte: String): Color? {
    val t = texte.trim().removePrefix("#")
    if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Color(0xFF000000 or t.toLong(16))
}

private fun versHsv(c: Color): FloatArray {
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == c.red -> 60f * (((c.green - c.blue) / delta) % 6f)
        max == c.green -> 60f * (((c.blue - c.red) / delta) + 2f)
        else -> 60f * (((c.red - c.green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return floatArrayOf(h, if (max == 0f) 0f else delta / max, max)
}

/** one of a gradient's two colours: clickable, and the current one carries the gold border */
@Composable
private fun PastilleDeuxCouleurs(
    libelle: String,
    couleur: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.06f))
            .border(
                if (active) 2.dp else 1.dp,
                if (active) DORE else BORD_MENU,
                RoundedCornerShape(6.dp),
            )
            .curseurMain().clickable(onClick = onClick).padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).background(couleur))
        Text(
            libelle,
            color = if (active) Color(0xFFF2F3F5) else Color(0xFF9AA0A6),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

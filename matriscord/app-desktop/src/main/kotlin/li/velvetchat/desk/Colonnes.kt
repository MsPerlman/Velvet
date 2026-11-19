package li.velvetchat.desk

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.awt.MouseInfo

/**
 * the channel and member column widths, dragged with the mouse
 *
 * asked for: both rails resizable, with a safety floor at the current size. so the minimum is the
 * width measured off the reference, you can widen and never go under pixel perfect. remembered from
 * one launch to the next
 */
object Largeurs {

    // 190: the reference's column dragged as narrow as it goes. 350 stays the starting width
    val SALONS_MIN = 190.dp
    val SALONS_MAX = 520.dp
    val MEMBRES_MIN = 248.dp
    val MEMBRES_MAX = 420.dp
    /** the right hand panels: thread, search results */
    val PANNEAU_MIN = 320.dp
    val PANNEAU_MAX = 900.dp

    // in the settings file and not the registry, see `Reglages`
    var salons by mutableStateOf(
        (Reglages.lire("colonnes.salons")?.toFloatOrNull() ?: COLONNE_SALONS.value).dp.coerceIn(SALONS_MIN, SALONS_MAX),
    )
    var membres by mutableStateOf(
        (Reglages.lire("colonnes.membres")?.toFloatOrNull() ?: MEMBRES_MIN.value).dp.coerceIn(MEMBRES_MIN, MEMBRES_MAX),
    )

    var panneau by mutableStateOf(
        (Reglages.lire("colonnes.panneau")?.toFloatOrNull() ?: 420f).dp.coerceIn(PANNEAU_MIN, PANNEAU_MAX),
    )

    fun retenir() {
        Reglages.ecrire("colonnes.panneau", panneau.value.toString())
        Reglages.ecrire("colonnes.salons", salons.value.toString())
        Reglages.ecrire("colonnes.membres", membres.value.toString())
    }
}

/**
 * the handle: eight invisible points laid on the column's edge, with a resize cursor
 *
 * same principle as the window's edges (`Fenetre.kt`): the mouse's absolute position at the start,
 * and every movement recomputed from there. the column widens under the cursor and the handle moves
 * with it, so a delta relative to the component would correct itself. one awt unit is one compose
 * point, java reporting the mouse already scaled
 *
 * [versLaGauche]: the handle is on the left edge (members), so dragging left widens
 */
@Composable
fun PoigneeLargeur(
    modifier: Modifier,
    versLaGauche: Boolean,
    lire: () -> Dp,
    ecrire: (Dp) -> Unit,
) {
    Box(
        modifier.width(8.dp).fillMaxHeight()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                var depart = 0
                var largeur = 0.dp
                detectHorizontalDragGestures(
                    onDragStart = {
                        depart = MouseInfo.getPointerInfo().location.x
                        largeur = lire()
                    },
                    onDragEnd = { Largeurs.retenir() },
                    onHorizontalDrag = { changement, _ ->
                        changement.consume()
                        val dx = (MouseInfo.getPointerInfo().location.x - depart).dp
                        ecrire(if (versLaGauche) largeur - dx else largeur + dx)
                    },
                )
            },
    )
}

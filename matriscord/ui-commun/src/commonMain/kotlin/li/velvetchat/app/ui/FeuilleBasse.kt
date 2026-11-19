package li.velvetchat.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * the bottom sheet, moved out of `ReglagesServeur.kt`
 *
 * fourteen callers use it, in screens that are otherwise entirely portable, and it lived in a file
 * carrying nine android ties that have nothing to do with it. that is the kind of neighbourhood
 * that holds a component on one side for no reason
 */

/**
 * a bare bottom sheet: scrim, rounded corners, anchored to the bottom. the rest comes from the
 * caller
 *
 * it closes on a downward drag, and that is not an ornament. the handle had been drawn from day one
 * and did nothing: it announced a gesture that did not exist. on the reference every sheet gets
 * pushed away with a thumb, which is the gesture people try instinctively, and it found nothing
 *
 * the threshold is double, like the drawer's: a clean fling decides on its own whatever the
 * distance travelled, and otherwise the distance decides. a half pushed sheet is a useful state for
 * nobody, so it either returns to its place or leaves
 *
 * the scrim lightens as you push, which says during the gesture that letting go will close, without
 * which you only find out afterwards whether the threshold was reached
 */
@Composable
fun FeuilleBasse(
    onFermer: () -> Unit,
    /**
     * can it be pulled up to take the whole height
     *
     * off by default: most sheets are a short list of actions, and a menu of four entries filling
     * the screen because a thumb slipped upwards is worse than one that does not move. the cards
     * that hold a whole profile want it, and they ask for it
     */
    extensible: Boolean = false,
    /**
     * clip the content to the sheet's shape. a profile card turns it off: its frame hangs above
     * the sheet, onto what is behind it, like on the reference, and the card rounds its own corners
     */
    rogner: Boolean = true,
    contenu: @Composable () -> Unit,
) {
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    val densite = LocalDensity.current

    // the back gesture closes the sheet, not what is under it
    //
    // without this it went through the sheet and closed the whole screen, so on the way back you
    // had lost the list you were working on as well as the sheet you meant to close. put here,
    // every sheet inherits it at once and none of them can forget
    GestionRetour(onRetour = onFermer)

    // the sheet rises, it does not appear. it used to pop into place, which says nothing about
    // where it came from or where it will go back to, and that is precisely what the closing
    // gesture assumes you know
    //
    // the starting point is the window's height, so the sheet is off screen on the first frame
    // whatever its size, which we do not know yet. it travels to its place on the drawer's spring,
    // the same one the panels use, so everything that enters this app enters the same way
    //
    // the window's height and not the screen's: `LocalConfiguration` only exists on android, and on
    // a desktop the distinction is real, a window rarely filling the whole screen.
    // `LocalWindowInfo` says the right thing on both platforms, already in pixels, so with no
    // conversion
    val hauteurEcran = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
        .height.toFloat()
    val pousse = remember { androidx.compose.animation.core.Animatable(hauteurEcran) }
    LaunchedEffect(Unit) { pousse.animateTo(0f, tokens.glissementPage()) }

    val seuil = with(densite) { 110.dp.toPx() }
    val lancer = with(densite) { tokens.density.flingVelocityDpS.dp.toPx() }

    // pulled up: the sheet takes the height it is given instead of the height of what it holds
    //
    // reported on the member card, which is taller than the sheet lets it be, so its foot was cut
    // and there was no way of reaching it. upward drags used to go nowhere at all, on the grounds
    // that the sheet is anchored to the bottom edge — true of closing it, and it made the gesture
    // people try first do nothing
    var etale by remember { mutableStateOf(false) }
    val hauteurEtale = with(densite) { (hauteurEcran * 0.92f).toDp() }

    Box(Modifier.fillMaxSize()) {
        // the scrim follows the gesture: halfway it has already visibly paled, which makes the
        // threshold readable during the movement rather than after letting go
        val opacite = tokens.colors.scrimAlpha *
            (1f - (pousse.value / (seuil * 2f)).coerceIn(0f, 0.75f))
        Box(
            Modifier.fillMaxSize()
                .background(tokens.bg.copy(alpha = opacite))
                .curseurMain().clickable(onClick = onFermer)
        )
        val forme = RoundedCornerShape(
            topStart = tokens.radiusLarge,
            topEnd = tokens.radiusLarge,
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .then(if (etale) Modifier.height(hauteurEtale) else Modifier)
                .offset { IntOffset(0, pousse.value.roundToInt()) }
                // **`clip` and not only `background`**
                //
                // the rounded corners were painted and then painted over. the background draws the
                // shape, it does not confine what the children draw, so a card whose header has a
                // background of its own — the profile card's gradient — laid its own square corner
                // on top. from the outside the sheet simply had sharp corners, and the shape right
                // here said otherwise
                .then(if (rogner) Modifier.clip(forme) else Modifier)
                .background(tokens.surface, forme)
                .pointerInput(seuil) {
                    val vitesse = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    var parcouru = 0f
                    detectVerticalDragGestures(
                        onDragStart = { parcouru = 0f; vitesse.resetTracking() },
                        onDragEnd = {
                            val v = vitesse.calculateVelocity().y
                            // three outcomes and not two. downwards still closes; upwards now
                            // spreads the sheet out when it is allowed to, and folds it back when
                            // it is already spread. the threshold is the same in both directions,
                            // so the gesture feels like one thing
                            val versLeHaut = v < -lancer || (v < lancer && parcouru < -seuil)
                            when {
                                v > lancer || (v > -lancer && pousse.value > seuil) -> onFermer()
                                extensible && versLeHaut && !etale -> {
                                    etale = true
                                    scope.launch { pousse.animateTo(0f, tokens.ressort()) }
                                }
                                extensible && etale && parcouru > seuil -> {
                                    etale = false
                                    scope.launch { pousse.animateTo(0f, tokens.ressort()) }
                                }
                                else -> scope.launch { pousse.animateTo(0f, tokens.ressort()) }
                            }
                        },
                        onDragCancel = { scope.launch { pousse.animateTo(0f, tokens.ressort()) } },
                    ) { changement, delta ->
                        parcouru += delta
                        vitesse.addPosition(
                            changement.uptimeMillis,
                            androidx.compose.ui.geometry.Offset(0f, parcouru),
                        )
                        // never above zero: pulling up must not lift the sheet off the edge, which
                        // is its anchor
                        scope.launch { pousse.snapTo((pousse.value + delta).coerceAtLeast(0f)) }
                    }
                }
                .imePadding()
                .padding(vertical = 14.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(tokens.border, RoundedCornerShape(2.dp))
            )
            contenu()
        }
    }
}

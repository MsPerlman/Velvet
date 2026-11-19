package li.velvetchat.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * a pointing hand over everything clickable, like the reference. compose leaves the arrow by
 * default, so this goes in front of every `clickable`, except the invisible scrims that dismiss a
 * menu. no effect under a finger on the phone
 */
fun Modifier.curseurMain(): Modifier = pointerHoverIcon(PointerIcon.Hand)

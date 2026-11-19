package li.velvetchat.app.ui

import androidx.compose.runtime.Composable

/**
 * the back gesture or key, depending on the platform
 *
 * the first `expect`/`actual` of the port, and there will not be many: most of the seams close
 * better with a parameter, the theme receives its font, the icons became code. this one cannot: it
 * is not data you pass, it is a system event that has to be intercepted where it arrives
 *
 * android has a back gesture, which the system routes to the activity and which has to be consumed
 * or it closes the whole screen. the bottom sheet already hit that: the back went through the sheet
 * and closed the list underneath
 *
 * the desktop has no back gesture. it has an escape key, which does not have the same status: a
 * system gesture is unique and takes priority, a key is a convention. so the desktop `actual` will
 * stay deliberately thin until the shell decides its keyboard policy
 */
@Composable
expect fun GestionRetour(actif: Boolean = true, onRetour: () -> Unit)

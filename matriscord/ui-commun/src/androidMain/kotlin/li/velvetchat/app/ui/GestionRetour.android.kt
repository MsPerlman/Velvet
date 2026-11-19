package li.velvetchat.app.ui

import androidx.compose.runtime.Composable

/**
 * android's back gesture, consumed while [actif]
 *
 * `BackHandler` does exactly the right thing: it registers with the activity's dispatcher and stops
 * the back from going further. that stopping is what matters, and it has no equivalent on a
 * keyboard key
 */
@Composable
actual fun GestionRetour(actif: Boolean, onRetour: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = actif, onBack = onRetour)
}

package li.velvetchat.app.ui

import androidx.compose.runtime.Composable

/**
 * on the desktop, nothing, and that is a choice rather than an oversight
 *
 * there is no back gesture on a computer. escape is the nearest convention, and it does not have
 * the same status: the android gesture is unique, takes priority and can be consumed, a key cannot,
 * since several components can listen for it and nothing says which one wins
 *
 * putting escape here without having settled the shell's keyboard policy would decide in passing
 * what deserves deciding: does escape close the sheet, or clear the search field, or leave the
 * room? an empty `actual` leaves the question open in the right place instead of answering it by
 * accident
 */
@Composable
@Suppress("UNUSED_PARAMETER")
actual fun GestionRetour(actif: Boolean, onRetour: () -> Unit) = Unit

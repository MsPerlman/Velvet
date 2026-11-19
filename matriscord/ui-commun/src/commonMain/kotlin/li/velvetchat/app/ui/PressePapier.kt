package li.velvetchat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * copying text, in one place
 *
 * sixteen screens wrote the same clipboard call, each with its own import and variable. and it is
 * not only repetition: `LocalClipboardManager` is deprecated in favour of `LocalClipboard`, whose
 * access suspends and whose content is built from a platform specific value (`ClipEntry` wraps an
 * awt `Transferable` on the desktop and a `ClipData` on android). migrating will therefore want an
 * `expect`/`actual` and sixteen places to revisit, or one, if everything goes through here
 *
 * the migration is not done today: the old api works, the new one has no common text factory yet,
 * and nothing is broken. this file is what makes it an hour rather than a day when the time comes
 *
 * usage:
 *
 *     val copier = copieur()
 *     …
 *     Bouton { copier(salon.id) }
 */
@Suppress("DEPRECATION")
@Composable
fun copieur(): (String) -> Unit {
    val presse = LocalClipboardManager.current
    return { texte -> presse.setText(AnnotatedString(texte)) }
}

/**
 * reading the clipboard. same reason to exist as [copieur]: one caller today, pasting a
 * verification code, and the same migration wall
 */
@Suppress("DEPRECATION")
@Composable
fun colleur(): () -> String {
    val presse = LocalClipboardManager.current
    return { presse.getText()?.text.orEmpty() }
}

package li.velvetchat.desk

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import java.io.File
import li.velvetchat.app.ui.horsUi

/**
 * the whole desktop's file picker: windows' modern dialog, explorer's, with its sidebar and its
 * preview, through filekit
 *
 * it replaces `java.awt.FileDialog`, which opens windows 95's old box, judged horrible, and which
 * had to be repositioned inside the window by hand. attached to velvetdesk's window, the dialog
 * opens centred on it and stays modal
 *
 * null on cancel, or when the dialog could not open
 */
internal suspend fun choisirImage(parent: java.awt.Window?, titre: String = horsUi.choisirUneImage): File? =
    runCatching {
        FileKit.openFilePicker(
            type = FileKitType.File("png", "jpg", "jpeg", "gif", "webp", "apng", "bmp"),
            title = titre,
            dialogSettings = FileKitDialogSettings(parentWindow = parent),
        )
    }.getOrNull()?.file

/** any file, for an attachment */
internal suspend fun choisirFichier(parent: java.awt.Window?): File? =
    runCatching {
        FileKit.openFilePicker(
            type = FileKitType.File(),
            title = horsUi.envoyerUnFichier,
            dialogSettings = FileKitDialogSettings(parentWindow = parent),
        )
    }.getOrNull()?.file

/**
 * picking a theme: the same dialog, filtered on `.json`
 *
 * the title says where to go rather than taking you there: filekit 0.10.0's api does take a
 * starting folder, but in a shape (`PlatformFile`) this module uses nowhere else, and a bad bet on
 * that signature would cost a compile error for a navigation convenience. the bundled themes are
 * dropped into that folder at startup (`ThemeVelvet.preparerLivres`), so they are there when it
 * opens
 */
internal suspend fun choisirTheme(
    parent: java.awt.Window?,
    titre: String = horsUi.choisirUnTheme,
): File? =
    runCatching {
        FileKit.openFilePicker(
            type = FileKitType.File("json"),
            title = titre,
            dialogSettings = FileKitDialogSettings(parentWindow = parent),
        )
    }.getOrNull()?.file

package li.velvetchat.desk

import java.io.File

/**
 * telling jna where `matrix_sdk_ffi.dll` is
 *
 * two situations, and one line separates them
 *
 * in development, the gradle `run` task already sets `jna.library.path` on the repo's `core/native`
 * folder, so there is nothing to do. inside the package, compose desktop's launcher sets
 * `compose.application.resources.dir` on the bundled resources folder, which is where packaging
 * copied the dll
 *
 * doing it here rather than through a jvm argument frozen at build time: an absolute path carved
 * into the package's `.cfg` would be wrong on somebody else's machine, and silently so, jna would
 * say nothing, look elsewhere and fail further along, on the engine's first call
 */
object Natif {
    fun preparer() {
        if (System.getProperty("jna.library.path") != null) return
        val ressources = System.getProperty("compose.application.resources.dir") ?: return
        val dossier = File(ressources)
        if (dossier.isDirectory) System.setProperty("jna.library.path", dossier.absolutePath)
    }
}

package li.velvetchat.desk

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary

/**
 * the open windows, so you can share just one
 *
 * discord offers three tabs, apps, whole screen, devices, and the first one means knowing which
 * windows exist. windows says so through `user32`, which jna can call and which the project already
 * loads for livekit: not one more dependency, four functions
 *
 * what gets ruled out, and why. `EnumWindows` returns everything: service windows, zero pixel
 * windows, untitled ones, and the desktop's program manager. showing them would give a grid of
 * fifty entries three of which matter. the filter is the taskbar's own: visible, not minimised, a
 * non empty title, a real size, not a tool window (`WS_EX_TOOLWINDOW`), and not cloaked
 *
 * cloaked is the one that was missing, and it explains half the wrong pictures. a window on another
 * virtual desktop, or a store app folded into the tray, stays visible for `IsWindowVisible` and is
 * not iconic either: it is *cloaked*, which only the desktop window manager knows. those entries
 * showed in the list and shared whatever happened to be at their coordinates. "windows input
 * experience", a system window covering the whole screen, is the clearest case: you picked it and
 * you shared your desktop
 *
 * the rectangle then serves as the capture area: see `SourcesVideo.region`, and why capturing by
 * title, which would have taken the window even when covered, could not work
 */
internal object FenetresWindows {

    data class Fenetre(
        val titre: String,
        val x: Int,
        val y: Int,
        val largeur: Int,
        val hauteur: Int,
        /**
         * the window's own handle, which is its real identity
         *
         * the list used to be keyed on the title, and a title is not an identity: a browser
         * renames its window on every tab change, so a share started on "gitlab — opera" looked
         * for a window by that name a second later, found none, and stopped. two file explorers
         * on the same folder collapsed into one entry for the same reason
         */
        val poignee: Long,

        /**
         * the process behind the window, which is what carries its sound
         *
         * windows can record one application's audio and nothing else, by process tree. that is
         * how "share this window's sound" is done, and it needs the process, not the window
         */
        val processus: Long,
    )

    private interface User32 : StdCallLibrary {
        fun EnumWindows(rappel: EnumRappel, donnees: Pointer?): Boolean
        fun IsWindowVisible(fenetre: Pointer): Boolean
        fun IsIconic(fenetre: Pointer): Boolean
        fun GetWindowTextW(fenetre: Pointer, tampon: CharArray, taille: Int): Int
        fun GetWindowRect(fenetre: Pointer, rect: RECT): Boolean
        fun GetWindowLongW(fenetre: Pointer, index: Int): Int
        fun GetWindowThreadProcessId(fenetre: Pointer, processus: IntArray): Int

        interface EnumRappel : StdCallLibrary.StdCallCallback {
            fun invoke(fenetre: Pointer, donnees: Pointer?): Boolean
        }
    }

    private interface Dwmapi : StdCallLibrary {
        fun DwmGetWindowAttribute(fenetre: Pointer, attribut: Int, valeur: IntArray, taille: Int): Int
    }

    @Suppress("PropertyName")
    class RECT : com.sun.jna.Structure() {
        @JvmField var left = 0
        @JvmField var top = 0
        @JvmField var right = 0
        @JvmField var bottom = 0
        override fun getFieldOrder() = listOf("left", "top", "right", "bottom")
    }

    private const val GWL_EXSTYLE = -20
    private const val WS_EX_TOOLWINDOW = 0x00000080
    private const val DWMWA_CLOAKED = 14

    private val user32: User32? by lazy {
        runCatching { Native.load("user32", User32::class.java) }.getOrNull()
    }

    private val dwmapi: Dwmapi? by lazy {
        runCatching { Native.load("dwmapi", Dwmapi::class.java) }.getOrNull()
    }

    /** hidden by the desktop window manager: another virtual desktop, or folded into the tray */
    private fun masquee(fenetre: Pointer): Boolean {
        val lib = dwmapi ?: return false
        val valeur = IntArray(1)
        // a non zero return means the attribute is unknown, on windows 7 for instance. we then
        // answer "not cloaked", which is what that system means anyway
        val code = runCatching { lib.DwmGetWindowAttribute(fenetre, DWMWA_CLOAKED, valeur, 4) }
            .getOrDefault(-1)
        return code == 0 && valeur[0] != 0
    }

    /**
     * the whole desktop, every screen included
     *
     * the reference frame for [dansLeBureau]. a second screen to the left of the first has
     * negative coordinates, which is normal and must not be clipped away
     */
    fun bureau(): java.awt.Rectangle = runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .map { it.defaultConfiguration.bounds }
            .reduce { a, b -> a.union(b) }
    }.getOrDefault(java.awt.Rectangle(0, 0, 1920, 1080))

    /**
     * the part of [rect] that is really on a screen
     *
     * this is the fix for the black thumbnails, and it is not a precaution: a maximised window
     * under windows 11 reaches eight points beyond each edge, its invisible resize borders, so
     * `GetWindowRect` gives 1936×1048 at (-8, -8) on a 1920×1080 desktop. `gdigrab` refuses to
     * open at all on that, "capture area extends outside window area", so the picture was black
     * and the share never started. and maximised is how people share a window
     */
    fun dansLeBureau(rect: java.awt.Rectangle): java.awt.Rectangle? {
        val coupe = rect.intersection(bureau())
        // even on both sides: a video encoder refuses an odd dimension, and cutting here rather
        // than at the ffmpeg call means the thumbnail and the share frame the same thing
        val largeur = coupe.width and 1.inv()
        val hauteur = coupe.height and 1.inv()
        if (largeur < 16 || hauteur < 16) return null
        return java.awt.Rectangle(coupe.x, coupe.y, largeur, hauteur)
    }

    /** where a window is right now, clipped to the screens, or null if it has gone */
    fun cadreDe(poignee: Long): java.awt.Rectangle? {
        val lib = user32 ?: return null
        val rect = RECT()
        val p = Pointer(poignee)
        if (!runCatching { lib.GetWindowRect(p, rect) }.getOrDefault(false)) return null
        if (runCatching { lib.IsIconic(p) }.getOrDefault(false)) return null
        return dansLeBureau(
            java.awt.Rectangle(
                rect.left,
                rect.top,
                rect.right - rect.left,
                rect.bottom - rect.top,
            ),
        )
    }

    /** the windows somebody would recognise in their taskbar */
    fun ouvertes(): List<Fenetre> {
        val lib = user32 ?: return emptyList()
        val trouvees = mutableListOf<Fenetre>()
        val rappel = object : User32.EnumRappel {
            override fun invoke(fenetre: Pointer, donnees: Pointer?): Boolean {
                runCatching {
                    if (!lib.IsWindowVisible(fenetre) || lib.IsIconic(fenetre)) return true
                    if (lib.GetWindowLongW(fenetre, GWL_EXSTYLE) and WS_EX_TOOLWINDOW != 0) return true
                    if (masquee(fenetre)) return true
                    val tampon = CharArray(512)
                    val longueur = lib.GetWindowTextW(fenetre, tampon, tampon.size)
                    if (longueur <= 0) return true
                    val titre = String(tampon, 0, longueur).trim()
                    if (titre.isEmpty() || titre == "Program Manager") return true
                    val rect = RECT()
                    if (!lib.GetWindowRect(fenetre, rect)) return true
                    val cadre = dansLeBureau(
                        java.awt.Rectangle(
                            rect.left,
                            rect.top,
                            rect.right - rect.left,
                            rect.bottom - rect.top,
                        ),
                    ) ?: return true
                    // smaller than that and it is not a window anybody shares
                    if (cadre.width < 160 || cadre.height < 120) return true
                    val pid = IntArray(1)
                    runCatching { lib.GetWindowThreadProcessId(fenetre, pid) }
                    trouvees += Fenetre(
                        titre,
                        cadre.x,
                        cadre.y,
                        cadre.width,
                        cadre.height,
                        Pointer.nativeValue(fenetre),
                        pid[0].toLong() and 0xFFFFFFFFL,
                    )
                }
                return true
            }
        }
        runCatching { lib.EnumWindows(rappel, null) }
        // by handle and not by title: two explorers on the same folder are two windows, and
        // collapsing them lost one
        return trouvees.distinctBy { it.poignee }
    }
}

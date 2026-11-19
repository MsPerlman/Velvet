package li.velvetchat.desk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import li.velvetchat.app.ui.*
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import javax.swing.JFrame

/**
 * the undecorated window, and what has to be rebuilt by hand so it stays a window
 *
 * the reference has no system title bar: the first line of the screen is the room's header, in the
 * theme, with minimise, maximise and close drawn at its right end. velvetdesk kept windows' white
 * bar above its own header
 *
 * removing the frame also removes everything it did without anybody thinking about it. this file
 * gives the four gestures back, one at a time:
 *
 * - moving, by dragging the top bar ([deplaceFenetre]) - maximising and restoring, by double
 * clicking that same bar, or through the button - resizing, by the edges and corners
 * ([BordsRedimension]) - minimising and closing ([BoutonsFenetre])
 *
 * windows' snapping, dragging against a screen edge to take half of it, comes from [Ancrage], which
 * gives the system an invisible frame back
 */
class Fenetre(val awt: ComposeWindow, val etat: WindowState)

val LocalFenetre = staticCompositionLocalOf<Fenetre?> { null }

/** the top bar's height, measured y 0 to 52 on the reference */
internal val BARRE_HAUTE = 52.dp

/**
 * dragging moves the window, double clicking maximises or restores it
 *
 * the mouse position is read on screen and not inside the component. the delta compose provides is
 * relative to the component, and the component is what moves with the window, so every step would
 * correct the previous one and the window would shake under the cursor. starting from the absolute
 * position at the beginning of the gesture and referring each movement to it tracks exactly, and
 * with no screen density arithmetic
 *
 * buttons placed in the bar keep their clicks: a clickable child consumes its gesture before the
 * bar sees it
 */
fun Modifier.deplaceFenetre(): Modifier = this.then(
    Modifier
        .pointerInput(Unit) {
            // read at gesture time and not when the modifier is built: there is no composition
            // here, only a gesture block
            detectTapGestures(onDoubleTap = { fenetreCourante?.let(::basculerAgrandie) })
        }
        .pointerInput(Unit) {
            var depart = java.awt.Point()
            var origine = java.awt.Point()
            detectDragGestures(
                onDragStart = {
                    val f = fenetreCourante ?: return@detectDragGestures
                    // windows takes the move over when it can, since it is the one that snaps
                    if (!fenetreAgrandie.value && Ancrage.deplacer(f.awt)) {
                        origine = java.awt.Point(Int.MIN_VALUE, 0)
                        return@detectDragGestures
                    }
                    // dragging a maximised window restores it first, like windows does
                    if (fenetreAgrandie.value) {
                        basculerAgrandie(f)
                        val sous = MouseInfo.getPointerInfo().location
                        f.awt.setLocation(sous.x - f.awt.width / 2, sous.y - 20)
                    }
                    depart = MouseInfo.getPointerInfo().location
                    origine = f.awt.location
                },
                onDrag = { changement, _ ->
                    changement.consume()
                    val f = fenetreCourante ?: return@detectDragGestures
                    if (origine.x == Int.MIN_VALUE) return@detectDragGestures
                    val ici = MouseInfo.getPointerInfo().location
                    f.awt.setLocation(origine.x + ici.x - depart.x, origine.y + ici.y - depart.y)
                },
            )
        },
)

/**
 * the current window, read outside the composition by the gestures
 *
 * `pointerInput` cannot read a `CompositionLocal`, and the window is unique for the life of the
 * process. one field set once by [PosageFenetre] is enough
 */
@Volatile
internal var fenetreCourante: Fenetre? = null

/** sets [fenetreCourante]. called once, inside the `Window`'s content */
@Composable
fun PosageFenetre(fenetre: Fenetre) {
    fenetreCourante = fenetre
}

/**
 * maximises or restores
 *
 * `maximizedBounds` first, otherwise the taskbar disappears. a maximised undecorated swing window
 * covers the whole screen, taskbar included: windows no longer knows where to stop, since the frame
 * is what used to tell it. so we hand it the work area of the screen the window is on, and not of
 * the primary screen, which would be wrong on a second monitor
 */
fun basculerAgrandie(f: Fenetre) {
    val w = f.awt
    // maximised by snapping, dragged against the top of the screen: windows is the one that
    // restores
    if (Ancrage.estAgrandieParWindows(w)) {
        Ancrage.restaurer(w)
        return
    }
    if (fenetreAgrandie.value) {
        bornesAvantAgrandie?.let { w.bounds = it }
        arrondirCoins(w, arrondis = true)
        fenetreAgrandie.value = false
        return
    }
    // maximised by hand, on the screen the window is on, rather than through `MAXIMIZED_BOTH`.
    // reported: impossible to maximise on the second screen. windows gives `maximizedBounds` in the
    // primary screen's coordinates, so on another monitor, another size and another origin, the
    // undecorated window refused to grow. we take the screen containing the window's centre, its
    // work area with the taskbar excluded, and put the window there
    bornesAvantAgrandie = w.bounds
    w.bounds = zoneDeTravail(w)
    arrondirCoins(w, arrondis = false)
    fenetreAgrandie.value = true
}

/**
 * the work area of the monitor the window is on, asked of windows
 *
 * `Toolkit.getScreenInsets` returns the primary screen's insets whatever configuration you hand it:
 * maximised on the second screen, the window lost the 48 points of the other screen's taskbar,
 * measured at 1920 by 1032 on a monitor that offers 1920 by 1080. `MonitorFromWindow` then
 * `GetMonitorInfo` give the real `rcWork`, monitor by monitor, through the same win32 door as the
 * rounded corners, by jna
 *
 * falls back on what awt can say when the call fails: 48 points too many beats nothing
 */
private fun zoneDeTravail(w: java.awt.Window): Rectangle {
    runCatching {
        val hwnd = com.sun.jna.Native.getWindowPointer(w)
        if (hwnd != null) {
            // MONITOR_DEFAULTTONEAREST: the monitor holding most of the window
            val moniteur = com.sun.jna.Function.getFunction("user32", "MonitorFromWindow")
                .invokePointer(arrayOf(hwnd, 2))
            val info = com.sun.jna.Memory(40).apply { setInt(0, 40) }
            val ok = com.sun.jna.Function.getFunction("user32", "GetMonitorInfoW")
                .invokeInt(arrayOf(moniteur, info))
            if (ok != 0) {
                val gauche = info.getInt(20)
                val haut = info.getInt(24)
                val droite = info.getInt(28)
                val bas = info.getInt(32)
                return Rectangle(gauche, haut, droite - gauche, bas - haut)
            }
        }
    }

    val centre = java.awt.Point(w.x + w.width / 2, w.y + w.height / 2)
    val config = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .map { it.defaultConfiguration }
        .firstOrNull { it.bounds.contains(centre) }
        ?: w.graphicsConfiguration
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val ecran = config.bounds
    val marges = Toolkit.getDefaultToolkit().getScreenInsets(config)
    return Rectangle(
        ecran.x + marges.left,
        ecran.y + marges.top,
        ecran.width - marges.left - marges.right,
        ecran.height - marges.top - marges.bottom,
    )
}

/** maximised by [basculerAgrandie]. the middle button's icon reads it */
internal val fenetreAgrandie = androidx.compose.runtime.mutableStateOf(false)
private var bornesAvantAgrandie: Rectangle? = null

/**
 * minimise, maximise, close, at the right end of the top bar
 *
 * drawn as strokes rather than taken from the icon set: they are three one pixel glyphs, and a 24
 * point vector icon scaled down to 10 would come out blurry. close goes red on hover and the other
 * two lighten, which is windows' code and the reference's
 */
@Composable
fun BoutonsFenetre(modifier: Modifier = Modifier) {
    val fenetre = LocalFenetre.current ?: return
    val agrandie = fenetreAgrandie.value
    Row(modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        BoutonFenetre(survolRouge = false, onClick = { fenetre.awt.extendedState = JFrame.ICONIFIED }) { c ->
            drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        }
        BoutonFenetre(survolRouge = false, onClick = { basculerAgrandie(fenetre) }) { c ->
            val trait = 1.dp.toPx()
            if (agrandie) {
                // restore: two offset squares, the back one showing only its two edges
                val d = size.width * 0.22f
                drawRect(c, Offset(0f, d), androidx.compose.ui.geometry.Size(size.width - d, size.height - d), style = Stroke(trait))
                drawLine(c, Offset(d, 0f), Offset(size.width, 0f), trait)
                drawLine(c, Offset(size.width, 0f), Offset(size.width, size.height - d), trait)
            } else {
                drawRect(c, Offset.Zero, size, style = Stroke(trait))
            }
        }
        BoutonFenetre(survolRouge = true, onClick = { fenetre.awt.dispatchEvent(java.awt.event.WindowEvent(fenetre.awt, java.awt.event.WindowEvent.WINDOW_CLOSING)) }) { c ->
            val trait = 1.dp.toPx()
            drawLine(c, Offset.Zero, Offset(size.width, size.height), trait)
            drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), trait)
        }
    }
}

@Composable
private fun BoutonFenetre(
    survolRouge: Boolean,
    onClick: () -> Unit,
    dessin: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val tokens = LocalTokens.current
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val survol by interaction.collectIsHoveredAsState()
    Box(
        Modifier.width(46.dp).fillMaxHeight()
            .background(
                when {
                    !survol -> Color.Transparent
                    survolRouge -> Color(0xFFE81123)
                    else -> Color.White.copy(alpha = 0.08f)
                },
            )
            .hoverable(interaction)
            .curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val couleur = if (survol && survolRouge) Color.White else tokens.textSecondary
        Canvas(Modifier.size(10.dp)) { dessin(couleur) }
    }
}

/**
 * a top bar carrying nothing but a way to move and close the window
 *
 * for the screens where nothing else occupies the top, startup, login, an empty column. without it
 * the undecorated window would neither move nor close except from the keyboard
 */
@Composable
fun BarreFenetreSeule(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(BARRE_HAUTE).deplaceFenetre()) {
        Spacer(Modifier.weight(1f))
        BoutonsFenetre()
    }
}

/**
 * the edges and corners that resize, six points thick, invisible, over everything else
 *
 * same principle as [deplaceFenetre]: we start from the mouse's absolute position and the window's
 * bounds at the beginning of the gesture, and every movement recomputes the bounds from there. a
 * corner combines the two edges that make it
 */
@Composable
fun BoxScope.BordsRedimension() {
    val epaisseur = 6.dp
    fun Modifier.bord(curseur: Int, gauche: Boolean, haut: Boolean, droite: Boolean, bas: Boolean) =
        this.pointerHoverIcon(PointerIcon(Cursor(curseur)))
            .pointerInput(Unit) {
                var depart = java.awt.Point()
                var bornes = Rectangle()
                detectDragGestures(
                    onDragStart = {
                        val f = fenetreCourante ?: return@detectDragGestures
                        depart = MouseInfo.getPointerInfo().location
                        bornes = f.awt.bounds
                    },
                    onDrag = { changement, _ ->
                        changement.consume()
                        val f = fenetreCourante ?: return@detectDragGestures
                        val ici = MouseInfo.getPointerInfo().location
                        val dx = ici.x - depart.x
                        val dy = ici.y - depart.y
                        var x = bornes.x; var y = bornes.y
                        var l = bornes.width; var h = bornes.height
                        if (droite) l = maxOf(LARGEUR_MIN, bornes.width + dx)
                        if (bas) h = maxOf(HAUTEUR_MIN, bornes.height + dy)
                        if (gauche) { l = maxOf(LARGEUR_MIN, bornes.width - dx); x = bornes.x + bornes.width - l }
                        if (haut) { h = maxOf(HAUTEUR_MIN, bornes.height - dy); y = bornes.y + bornes.height - h }
                        f.awt.setBounds(x, y, l, h)
                    },
                )
            }

    Box(Modifier.align(Alignment.CenterStart).width(epaisseur).fillMaxHeight().bord(Cursor.W_RESIZE_CURSOR, true, false, false, false))
    Box(Modifier.align(Alignment.CenterEnd).width(epaisseur).fillMaxHeight().bord(Cursor.E_RESIZE_CURSOR, false, false, true, false))
    Box(Modifier.align(Alignment.TopCenter).height(epaisseur).fillMaxWidth().bord(Cursor.N_RESIZE_CURSOR, false, true, false, false))
    Box(Modifier.align(Alignment.BottomCenter).height(epaisseur).fillMaxWidth().bord(Cursor.S_RESIZE_CURSOR, false, false, false, true))
    Box(Modifier.align(Alignment.TopStart).size(epaisseur * 2).bord(Cursor.NW_RESIZE_CURSOR, true, true, false, false))
    Box(Modifier.align(Alignment.TopEnd).size(epaisseur * 2).bord(Cursor.NE_RESIZE_CURSOR, false, true, true, false))
    Box(Modifier.align(Alignment.BottomStart).size(epaisseur * 2).bord(Cursor.SW_RESIZE_CURSOR, true, false, false, true))
    Box(Modifier.align(Alignment.BottomEnd).size(epaisseur * 2).bord(Cursor.SE_RESIZE_CURSOR, false, false, true, true))
}

/** below this, the three columns no longer fit side by side */
// the smallest the window may be, and both numbers are load bearing
//
// the three columns have fixed widths, so a narrow window does not shrink them: it starves
// the conversation and the composer, which are the point of the app. forced to 520x400 with a
// win32 call, both had vanished and only the side panels were left
//
// 940 is discord's own minimum width, which is the reference this client follows
//
// internal and not private because `Main.kt` hands the same pair to `window.minimumSize`. the
// drag handles below clamp what THIS app does, and that is not every way a window gets
// resized: snapping, a tiling tool or a restore go through the system, which only honours a
// minimum it has been told about
internal const val LARGEUR_MIN = 940
internal const val HAUTEUR_MIN = 600

/**
 * windows 11's rounded corners, which the undecorated window had lost
 *
 * asked of dwm rather than drawn: a transparent window clipped to a rounded shape would cost the
 * hardware rendering and leave the shadow square. `DWMWA_WINDOW_CORNER_PREFERENCE` (33) set to
 * `DWMWCP_ROUND` (2) gives the system's own rounding, with its shadow and its hairline, and nothing
 * when the window is maximised. on windows 10 the attribute does not exist and the call fails
 * quietly
 */
fun arrondirCoins(fenetre: java.awt.Window, arrondis: Boolean = true) {
    runCatching {
        val hwnd = com.sun.jna.Native.getWindowPointer(fenetre) ?: return
        // DWMWA_WINDOW_CORNER_PREFERENCE (33): 2 is rounded, 1 is never rounded
        //
        // square when the window is maximised. reported as a thin line of wallpaper still showing
        // along the edges. the window did fill the screen, and it was its rounded corners letting
        // the desktop through. windows does the same, a maximised window has no rounded corners
        com.sun.jna.Function.getFunction("dwmapi", "DwmSetWindowAttribute")
            .invokeInt(arrayOf(hwnd, 33, com.sun.jna.ptr.IntByReference(if (arrondis) 2 else 1), 4))
    }
}

/**
 * windows snapping (aero snap), given back to the undecorated window
 *
 * windows only snaps resizable windows, so we give the `WS_THICKFRAME` style and the minimise and
 * maximise boxes back, then remove their visible frame by answering `WM_NCCALCSIZE` that the whole
 * window is client area. dragging the top bar then hands over to the system's own move
 * (`WM_NCLBUTTONDOWN` on `HTCAPTION`), which knows how to snap left, right and top
 *
 * a window maximised by the system overhangs the screen by the frame's thickness, so we pull it
 * back inside when computing its client area, otherwise the top of the header would be cut off
 */
object Ancrage {
    private const val GWL_STYLE = -16
    private const val GWLP_WNDPROC = -4
    private const val WM_NCCALCSIZE = 0x0083
    private const val WS_THICKFRAME = 0x00040000L
    private const val WS_MINIMIZEBOX = 0x00020000L
    private const val WS_MAXIMIZEBOX = 0x00010000L
    private const val WS_CAPTION = 0x00C00000L
    private const val WS_POPUP = 0x80000000L
    /** a private message meaning "start the move", handled on the window's thread */
    private const val WM_DEPLACER = 0x8000 + 0x51

    interface ProcedureFenetre : com.sun.jna.win32.StdCallLibrary.StdCallCallback {
        fun callback(hwnd: com.sun.jna.Pointer, msg: Int, wParam: Long, lParam: Long): Long
    }

    /** kept here: a window procedure collected by the garbage collector would crash the window */
    private var procedure: ProcedureFenetre? = null
    private var ancienne: com.sun.jna.Pointer? = null
    @Volatile var actif = false
        private set

    private fun f(nom: String) = com.sun.jna.Function.getFunction("user32", nom, com.sun.jna.Function.ALT_CONVENTION)

    fun activer(fenetre: java.awt.Window) {
        if (actif || !System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        runCatching {
            val hwnd = com.sun.jna.Native.getWindowPointer(fenetre) ?: return
            val proc = object : ProcedureFenetre {
                override fun callback(hwnd: com.sun.jna.Pointer, msg: Int, wParam: Long, lParam: Long): Long {
                    if (msg == WM_NCCALCSIZE && wParam != 0L) {
                        if (f("IsZoomed").invokeInt(arrayOf(hwnd)) != 0) {
                            val metrique = f("GetSystemMetrics")
                            val bx = metrique.invokeInt(arrayOf(32)) + metrique.invokeInt(arrayOf(92))
                            val by = metrique.invokeInt(arrayOf(33)) + metrique.invokeInt(arrayOf(92))
                            val r = com.sun.jna.Pointer(lParam)
                            r.setInt(0, r.getInt(0) + bx)
                            r.setInt(4, r.getInt(4) + by)
                            r.setInt(8, r.getInt(8) - bx)
                            r.setInt(12, r.getInt(12) - by)
                        }
                        return 0L
                    }
                    if (msg == WM_DEPLACER) {
                        // on the window's thread, which holds the mouse capture: release it, then
                        // the system menu's drag to move (SC_MOVE | HTCAPTION)
                        f("ReleaseCapture").invokeInt(arrayOf<Any?>())
                        f("DefWindowProcW").invokeLong(arrayOf(hwnd, 0x0112, 0xF012L, 0L))
                        return 0L
                    }
                    return f("CallWindowProcW").invokeLong(arrayOf(ancienne, hwnd, msg, wParam, lParam))
                }
            }
            procedure = proc
            val style = f("GetWindowLongPtrW").invokeLong(arrayOf(hwnd, GWL_STYLE))
            ancienne = com.sun.jna.Pointer(f("SetWindowLongPtrW").invokeLong(arrayOf(hwnd, GWLP_WNDPROC, proc)))
            f("SetWindowLongPtrW").invokeLong(arrayOf(hwnd, GWL_STYLE, (style and WS_POPUP.inv()) or WS_CAPTION or WS_THICKFRAME or WS_MINIMIZEBOX or WS_MAXIMIZEBOX))
            // SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED: recompute the frame and
            // nothing else
            f("SetWindowPos").invokeInt(arrayOf(hwnd, null, 0, 0, 0, 0, 0x0002 or 0x0001 or 0x0004 or 0x0020))
            actif = true
        }
    }

    /** hands the drag in progress to windows' own move, the one that snaps */
    fun deplacer(fenetre: java.awt.Window): Boolean {
        if (!actif) return false
        return runCatching {
            val hwnd = com.sun.jna.Native.getWindowPointer(fenetre) ?: return false
            // posted and not sent: the move loop runs on the window's thread, not on compose's,
            // which it would block
            f("PostMessageW").invokeInt(arrayOf(hwnd, WM_DEPLACER, 0L, 0L)) != 0
        }.getOrDefault(false)
    }

    fun estAgrandieParWindows(fenetre: java.awt.Window): Boolean = actif && runCatching {
        f("IsZoomed").invokeInt(arrayOf(com.sun.jna.Native.getWindowPointer(fenetre))) != 0
    }.getOrDefault(false)

    fun restaurer(fenetre: java.awt.Window) {
        runCatching { f("ShowWindow").invokeInt(arrayOf(com.sun.jna.Native.getWindowPointer(fenetre), 9)) }
    }
}

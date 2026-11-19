package li.velvetchat.desk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Image
import li.velvetchat.app.ui.LocalMonIdentifiant
import java.awt.Color as CouleurAwt
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File

/**
 * looking at a screen without having to provoke it
 *
 * the desktop todo says "to be checked on screen" a dozen times, for a good reason: most of those
 * screens want a situation you cannot create on demand, a call between two people, somebody sharing
 * their camera, a rare profile frame. looking at them therefore meant setting the situation up for
 * real, on two devices, and taking a photo
 *
 * `ImageComposeScene` renders a composable into an image, with no window, no mouse and no session:
 * you hand it the state you want to see and it draws the exact pixels the real app would. that is
 * why [EcranAppelBureau] takes its state as a parameter instead of reading globals. testability is
 * not an extra, it is what makes a screen watchable
 *
 *     ./gradlew :app-desktop:rendreEcranAppel
 */
object RenduHorsFenetre {

    @JvmStatic
    fun main(args: Array<String>) {
        val sortie = File(args.getOrNull(0) ?: "build/ecran-appel.png")
        sortie.parentFile?.mkdirs()

        // a fabricated call: two people, one of them sharing their screen and showing their camera.
        // the busiest case, so the one that says the most about the layout
        val etat = AppelBureau.Etat(
            salonId = "!essai:exemple.org",
            salonNom = "essai-vocal",
            phase = AppelBureau.Phase.EN_APPEL,
            personnes = listOf(
                AppelBureau.Personne("@alex:exemple.org:PC", "@alex:exemple.org", parle = true),
                AppelBureau.Personne("@sam:exemple.org:TEL", "@sam:exemple.org"),
            ),
            moiParle = false,
            videos = listOf(
                AppelBureau.Video("sid-ecran", ecran = true, userId = "@alex:exemple.org"),
                AppelBureau.Video("sid-cam", ecran = false, userId = "@sam:exemple.org"),
            ),
        )
        // invented images and not a real capture: this render ends up in a conversation, and
        // putting somebody's actual screen in there would publish it without them asking
        val images = mapOf(
            "sid-ecran" to degrade(640, 360, CouleurAwt(0x1E, 0x2A, 0x4A), CouleurAwt(0x3C, 0x1E, 0x4A)),
            "sid-cam" to degrade(480, 640, CouleurAwt(0x4A, 0x2E, 0x1E), CouleurAwt(0x1E, 0x4A, 0x3C)),
        )

        // an unconnected engine rather than a fake one: `Avatar` demands one to go and fetch an
        // image, and writing a stub would mean covering the contract's hundred and forty five
        // methods for the one that is used. with no session the read fails and the avatar falls
        // back on its initials, which is exactly what the real app shows until the image arrives
        val moteurMuet = li.velvetchat.core.rust.RustMatrixEngine(
            dataPath = File(System.getProperty("java.io.tmpdir"), "velvet-rendu").apply { mkdirs() }.absolutePath,
            cachePath = File(System.getProperty("java.io.tmpdir"), "velvet-rendu-cache").apply { mkdirs() }.absolutePath,
            logToStdout = false,
        )
        val scene = ImageComposeScene(width = 1280, height = 760, density = Density(1f)) {
            CompositionLocalProvider(
                li.velvetchat.app.ui.LocalEngine provides moteurMuet,
                LocalMonIdentifiant provides "@exemple:exemple.org",
            ) {
                ThemeVelvetPourRendu {
                    Box(Modifier.fillMaxSize()) {
                        EcranAppelBureau(
                            Modifier.fillMaxSize(),
                            etat = etat,
                            horloge = 1L,
                            image = { images[it] },
                            interactif = false,
                        )
                    }
                }
            }
        }
        try {
            val rendu = scene.render()
            sortie.writeBytes(rendu.encodeToData()?.bytes ?: error("encodage impossible"))
        } finally {
            scene.close()
        }
        println(sortie.absolutePath)
    }

    /**
     * the app's theme, with no window
     *
     * the real `Racine` provides plenty more, the window, the emoji font, the settings, and none of
     * them is needed to draw. demanding them would mean standing up a whole app to look at one
     * screen
     */
    @Composable
    private fun ThemeVelvetPourRendu(contenu: @Composable () -> Unit) {
        li.velvetchat.app.ui.VelvetTheme(
            theme = ThemeVelvet.courant,
            famille = policeVelvet(),
            policeEmoji = policeEmoji(),
            content = contenu,
        )
    }

    /** a gradient, standing in for a received frame */
    private fun degrade(largeur: Int, hauteur: Int, de: CouleurAwt, vers: CouleurAwt): ImageBitmap {
        val image = BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            paint = GradientPaint(0f, 0f, de, largeur.toFloat(), hauteur.toFloat(), vers)
            fillRect(0, 0, largeur, hauteur)
            dispose()
        }
        return image.toComposeImageBitmap()
    }
}

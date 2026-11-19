package li.velvetchat.desk

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.velvetchat.app.ui.decoderMedia
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import li.velvetchat.core.fonds.MagasinFonds
import li.velvetchat.app.ui.horsUi

/**
 * velvetdesk's wallpaper, chosen by the person
 *
 * the reference is a themed discord: an image under the whole app, with translucent columns over
 * it. the wallpaper is choosable, not fixed
 *
 * the file is the setting. the chosen image is copied into the data folder
 * (`%APPDATA%\VelvetDesk\fond-ecran.img`) and its presence means the wallpaper is on. copying
 * rather than remembering a path is what makes a wallpaper survive the original file being moved or
 * deleted, where a remembered path would break silently the day somebody tidies their pictures
 * folder
 */
object FondEcran {

    /**
     * the store: one file per image, named after the hash of its contents
     *
     * replaces the single `fond-ecran.img`. three things follow, two of them asked for: the
     * history of the last twenty, deduplication when the same one is set again, and a name a
     * theme can carry without naming a path on the machine
     */
    private val magasin by lazy { MagasinFonds(Donnees.racine) }

    private val choix: File get() = File(Donnees.racine, "fond-choisi.txt")

    /**
     * incremented on every change: reads in composition depend on [version], so they recompose
     * themselves
     */
    val version = mutableIntStateOf(0)

    /**
     * what should be shown: the personal choice, otherwise what the theme offers
     *
     * a theme **offers** a wallpaper, it does not impose one. the folder could do the
     * opposite — "a theme customises everything" — but then changing theme would erase the
     * photograph somebody set, without saying so. the personal choice wins, and "remove"
     * gives it back to the theme rather than to nothing
     */
    private fun empreinteCourante(): String? {
        val perso = runCatching { choix.takeIf { it.isFile }?.readText()?.trim() }.getOrNull()
        if (!perso.isNullOrBlank() && magasin.existe(perso)) return perso
        return ThemeVelvet.courant.images.chatBackground.takeIf { magasin.existe(it) }
    }

    /** read in composition: depends on [version], so it follows changes */
    val actif: Boolean get() = version.intValue.let { empreinteCourante() != null }

    val voile: Float get() = version.intValue.let { ThemeVelvet.courant.images.chatBackgroundVeil }

    fun recents(): List<String> = version.intValue.let { magasin.recents().map { f -> f.empreinte } }

    /**
     * its own scope: the popover that opens the picker closes immediately, and its scope with it
     */
    private val portee = kotlinx.coroutines.MainScope()

    /**
     * opens windows' modern file picker ([choisirImage], filekit) and copies the chosen image.
     * awt's old `FileDialog` was judged horrible
     */
    fun choisir(parent: java.awt.Window?) {
        portee.launch {
            val source = choisirImage(parent, horsUi.choisirUnFondDEcran) ?: return@launch
            if (!source.isFile) return@launch
            val octets = runCatching { source.readBytes() }.getOrNull() ?: return@launch
            // the image has to decode, otherwise we would keep a file nothing can display
            if (decoderMedia(octets, LARGEUR_DECODAGE) == null) return@launch
            appliquer(magasin.ranger(octets, protegees = protegees()) ?: return@launch)
        }
    }

    fun appliquer(empreinte: String) {
        if (!magasin.existe(empreinte)) return
        magasin.noter(empreinte, protegees = protegees())
        runCatching { choix.writeText(empreinte) }.onSuccess { version.intValue++ }
    }

    fun oublier(empreinte: String) {
        val courant = runCatching { choix.takeIf { it.isFile }?.readText()?.trim() }.getOrNull()
        magasin.oublier(empreinte, protegees = protegees() - empreinte)
        if (courant == empreinte) choix.delete()
        version.intValue++
    }

    fun retirer() {
        choix.delete()
        version.intValue++
    }

    /**
     * what the sweep is not allowed to delete
     *
     * the image a theme names is not necessarily in the history — an imported theme arrives
     * with its own — and dropping it from the list must not make it vanish from the screen
     */
    private fun protegees(): Set<String> =
        setOfNotNull(ThemeVelvet.courant.images.chatBackground.takeIf { it.isNotBlank() })

    internal fun octets(): ByteArray? = empreinteCourante()?.let { magasin.lire(it) }

    internal fun octetsDe(empreinte: String): ByteArray? = magasin.lire(empreinte)

    /**
     * the decode's target width: `decoderMedia` scales down past twice that, so a 4k photo does not
     * keep 50 mb of pixels in memory for a background seen through columns
     */
    internal const val LARGEUR_DECODAGE = 1920
}

/**
 * a column's colour: solid with no wallpaper, translucent with one
 *
 * measured off the reference: the columns let the image through, barely darkened. a layer at 28%
 * keeps the text readable on a light image without hiding the screenshot's dark one
 *
 * the hue comes from the theme, no longer from hardcoded black. a theme applied changed almost
 * nothing on screen as soon as a wallpaper was active: the three layers that then stack, this one,
 * [fondConversation] and [CoucheFond]'s veil, painted pure black and therefore covered every
 * surface the theme defines. measured on one theme: the text did turn beige and the columns stayed
 * identical to the pixel. the colour received is already the theme's (`tokens.surface`), it was
 * only a matter of not throwing it away
 *
 * on a dark theme the result is the same as before, give or take the hue, a dark surface at 28%
 * being about the black it replaces. on a light theme, though, the layer lightens instead of
 * darkening: consistent with a light theme, but the "readable over a bright image" calibration was
 * done in dark and will want checking on screen the day somebody ports a light one
 */
@Composable
fun fondColonne(pleine: Color): Color =
    if (FondEcran.actif) pleine.copy(alpha = 0.28f) else pleine

/**
 * the conversation area's background, header and messages, when a wallpaper is on
 *
 * darker than the columns, deliberately. checked against a screenshot with the very bright image
 * the user picked, lanterns and orange walls: with the 35% layer alone, the red nicknames and the
 * times disappeared into the scenery. the reference stays readable because its image is dark to
 * begin with, and here it is the conversation that carries the darkening while the image stays
 * visible around it. transparent with no wallpaper, in which case the solid colour comes from the
 * parent row
 */
@Composable
fun fondConversation(): Color =
    if (FondEcran.actif) Color.Black.copy(alpha = 0.40f) else Color.Transparent

/**
 * the image, under everything else, and a veil for legibility
 *
 * the veil is fixed at 35% black: it is what makes light text readable over any photo, bright ones
 * included. nothing is drawn while no wallpaper is chosen
 */
@Composable
fun CoucheFond() {
    val version by FondEcran.version
    var image by remember(version) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(version) {
        image = withContext(Dispatchers.IO) {
            FondEcran.octets()?.let { decoderMedia(it, FondEcran.LARGEUR_DECODAGE) }
        }
    }
    val bitmap = image ?: return
    Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    // the veil takes the colour that pushes the image away from the text: black under a dark
    // theme whose text is light, white under a light theme whose text is dark. always black,
    // it buried the text of light themes instead of lifting it out
    val veille = if (li.velvetchat.app.ui.LocalTokens.current.dark) Color.Black else Color.White
    Box(Modifier.fillMaxSize().background(veille.copy(alpha = FondEcran.voile)))
}

package li.velvetchat.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * the square kept, in the original image's pixels
 *
 * square and not rectangular: an avatar, like a server icon, shows round or as a rounded square.
 * offering a free crop would let people choose a shape nothing will display
 */
internal data class Cadre(val x: Int, val y: Int, val cote: Int)

/**
 * the crop's geometry, isolated from everything that draws
 *
 * it is the only part of the cropping that can be wrong without showing: a sign error gives an
 * offset image, not a missing one, and you blame the photo. so it is a pure function, and it has
 * its test
 *
 * the model is the screen's: the image is first laid out as "cover", enlarged just enough to fill
 * the window, then multiplied by the person's [zoom], all around the centre, and finally moved by
 * ([decalageX], [decalageY]) screen pixels
 *
 * a source point `s` therefore lands at `windowCentre + offset + (s - sourceCentre) x scale`. we
 * invert for the window's two corners, and we clamp: the frame never leaves the image, otherwise
 * `Bitmap.createBitmap` would throw on an edge
 */
internal fun rectSource(
    largeurSource: Int,
    hauteurSource: Int,
    coteFenetre: Float,
    zoom: Float,
    decalageX: Float,
    decalageY: Float,
): Cadre {
    if (largeurSource <= 0 || hauteurSource <= 0 || coteFenetre <= 0f) return Cadre(0, 0, 0)

    val couverture = maxOf(
        coteFenetre / largeurSource,
        coteFenetre / hauteurSource,
    )
    val echelle = couverture * zoom.coerceAtLeast(1f)

    // the window's side, brought back into image pixels
    //
    // rounded and not truncated, and the test found it before the screen did: `300f / 0.3f` is
    // 999.99994, so a thousand pixel image taken whole gave back nine hundred and ninety nine. one
    // lost pixel does not show, and the half pixel offset that comes with it, on every edge and on
    // every crop, ends up showing
    val cote = kotlin.math.round(coteFenetre / echelle).toInt().coerceAtLeast(1)
        .coerceAtMost(minOf(largeurSource, hauteurSource))

    val x = kotlin.math.round(
        largeurSource / 2f + (-coteFenetre / 2f - decalageX) / echelle,
    ).toInt()
    val y = kotlin.math.round(
        hauteurSource / 2f + (-coteFenetre / 2f - decalageY) / echelle,
    ).toInt()

    return Cadre(
        x = x.coerceIn(0, largeurSource - cote),
        y = y.coerceIn(0, hauteurSource - cote),
        cote = cote,
    )
}

/**
 * how far the image can be moved without uncovering an edge, in screen pixels
 *
 * bounding the gesture rather than bounding the result: letting it slide past then forcing it back
 * gives an image that sticks to the finger then jumps, which reads as a fault
 */
internal fun decalageMax(
    largeurSource: Int,
    hauteurSource: Int,
    coteFenetre: Float,
    zoom: Float,
): Pair<Float, Float> {
    if (largeurSource <= 0 || hauteurSource <= 0) return 0f to 0f
    val echelle = maxOf(coteFenetre / largeurSource, coteFenetre / hauteurSource) *
        zoom.coerceAtLeast(1f)
    return ((largeurSource * echelle - coteFenetre) / 2f).coerceAtLeast(0f) to
        ((hauteurSource * echelle - coteFenetre) / 2f).coerceAtLeast(0f)
}

/** the maximum zoom: beyond it you are looking at enlarged pixels, not a photo */
private const val ZOOM_MAX = 6f

/** the side of the produced file. 1024 is plenty for an avatar, and weighs little to send */
private const val COTE_SORTIE = 1024

/**
 * crop before sending
 *
 * the system picker returns a whole photo, and the app showed its centre, without ever asking. a
 * landscape photo therefore gave an avatar cut at random, and the only recourse was to crop in the
 * gallery before coming back
 *
 * the gesture is the expected one: two fingers to enlarge, one to move. nothing to set, no handles
 * to grab, the window is fixed and the image moves under it
 *
 * @param rond a circular mask for a person's avatar, a rounded square for a server, because that is
 *   the shape each will show in. showing the wrong shape while cropping means cropping for nothing
 */
@Composable
internal fun EcranRecadrage(
    uri: android.net.Uri,
    rond: Boolean,
    onAnnuler: () -> Unit,
    onValider: (java.io.File) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val contexte = LocalContext.current
    var source by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var echec by remember(uri) { mutableStateOf<String?>(null) }
    var zoom by remember(uri) { mutableStateOf(1f) }
    var dx by remember(uri) { mutableStateOf(0f) }
    var dy by remember(uri) { mutableStateOf(0f) }
    var cote by remember(uri) { mutableStateOf(0f) }
    var travaille by remember(uri) { mutableStateOf(false) }
    val portee = androidx.compose.runtime.rememberCoroutineScope()

    androidx.activity.compose.BackHandler(onBack = onAnnuler)

    LaunchedEffect(uri) {
        source = withContext(Dispatchers.IO) {
            runCatching { decoderPourRecadrage(contexte, uri) }
                .onFailure { echec = t.imageIllisible }
                .getOrNull()
        }
    }

    Box(
        // opaque and not translucent: while you are framing, what is behind does not help, and a
        // dark background is what you judge an image best against
        Modifier.fillMaxSize().background(Color(0xFF0A0A0C))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxSize().padding(tokens.espXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                t.recadrer,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                t.pincezPourAgrandir,
                color = tokens.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = tokens.espXl),
            )

            val image = source
            Box(
                Modifier.size(300.dp)
                    .clip(if (rond) CircleShape else RoundedCornerShape(24.dp))
                    .background(Color(0xFF141419))
                    .onSizeChanged { cote = it.width.toFloat() }
                    .pointerInput(image, rond) {
                        detectTransformGestures { _, pan, zoomGeste, _ ->
                            val image2 = image ?: return@detectTransformGestures
                            zoom = (zoom * zoomGeste).coerceIn(1f, ZOOM_MAX)
                            val (maxX, maxY) = decalageMax(
                                image2.width, image2.height, cote, zoom,
                            )
                            dx = (dx + pan.x).coerceIn(-maxX, maxX)
                            dy = (dy + pan.y).coerceIn(-maxY, maxY)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    image != null -> Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        // `Crop` lays the cover, and the `graphicsLayer` adds what the person asks
                        // on top. it is exactly the model `rectSource` inverts, two places, one
                        // rule
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = dx,
                            translationY = dy,
                        ),
                    )

                    echec != null -> Text(echec!!, color = tokens.textMuted, fontSize = 13.sp)
                    else -> Text(t.chargement, color = tokens.textMuted, fontSize = 13.sp)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = tokens.espXl * 2),
                horizontalArrangement = Arrangement.spacedBy(tokens.espM),
            ) {
                BoutonRecadrage(t.annuler, plein = false, actif = !travaille, onAnnuler)
                BoutonRecadrage(
                    if (travaille) t.preparation else t.utiliserCetteImage,
                    plein = true,
                    actif = image != null && !travaille,
                ) {
                    val image2 = image ?: return@BoutonRecadrage
                    travaille = true
                    // off the main thread, and this is not a precaution of principle: cutting then
                    // resampling then compressing two megapixels takes several frames' worth of
                    // time. done here inline, the interface froze during the operation, and
                    // "preparing…", set just before, never got the chance to be drawn
                    //
                    // the cutting stays in this screen rather than at the caller's: this is what
                    // knows the zoom and the offset, and passing them along would mean republishing
                    // the geometry we have just isolated
                    portee.launch {
                        val cadre = rectSource(image2.width, image2.height, cote, zoom, dx, dy)
                        val fichier = withContext(Dispatchers.IO) {
                            runCatching { decouper(contexte, image2, cadre) }.getOrNull()
                        }
                        travaille = false
                        if (fichier == null) echec = t.decoupageImpossible else onValider(fichier)
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BoutonRecadrage(
    libelle: String,
    plein: Boolean,
    actif: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Box(
        Modifier.weight(1f).height(48.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(if (plein) tokens.accent else tokens.surfaceRaised)
            .clickable(enabled = actif, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            libelle,
            color = if (plein) Color(0xFF10201F) else tokens.textSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * the gallery's image, in memory and mutable
 *
 * `ImageDecoder` returns a hardware bitmap by default, which can be neither read nor cut,
 * `createBitmap` throwing on it. the software allocator is therefore compulsory here, and it is not
 * elsewhere in the app, hence this function rather than one more call
 *
 * bounded at 2048: beyond that you handle tens of megabytes to produce a 1024 square, and phone
 * photos go well past it
 */
private fun decoderPourRecadrage(
    contexte: android.content.Context,
    uri: android.net.Uri,
): android.graphics.Bitmap {
    val source = android.graphics.ImageDecoder.createSource(contexte.contentResolver, uri)
    return android.graphics.ImageDecoder.decodeBitmap(source) { decodeur, info, _ ->
        decodeur.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        decodeur.isMutableRequired = true
        val grand = maxOf(info.size.width, info.size.height)
        if (grand > 2048) {
            val f = 2048f / grand
            decodeur.setTargetSize(
                (info.size.width * f).toInt().coerceAtLeast(1),
                (info.size.height * f).toInt().coerceAtLeast(1),
            )
        }
    }
}

/** cuts out the kept square, brings it back to [COTE_SORTIE] and writes it into the sends cache */
private fun decouper(
    contexte: android.content.Context,
    source: android.graphics.Bitmap,
    cadre: Cadre,
): java.io.File {
    val carre = android.graphics.Bitmap.createBitmap(source, cadre.x, cadre.y, cadre.cote, cadre.cote)
    val sortie = if (carre.width > COTE_SORTIE) {
        android.graphics.Bitmap.createScaledBitmap(carre, COTE_SORTIE, COTE_SORTIE, true)
    } else carre

    // png when the image has transparency, jpeg otherwise: a photo as png weighs several times its
    // jpeg equivalent for nothing, and a transparent logo as jpeg gets blackened
    val alpha = sortie.hasAlpha()
    val fichier = java.io.File(
        contexte.cacheDir.resolve("envois").apply { mkdirs() },
        "recadre-${System.currentTimeMillis()}." + if (alpha) "png" else "jpg",
    )
    fichier.outputStream().use {
        sortie.compress(
            if (alpha) android.graphics.Bitmap.CompressFormat.PNG
            else android.graphics.Bitmap.CompressFormat.JPEG,
            92,
            it,
        )
    }
    return fichier
}

/**
 * where a profile image upload has got to
 *
 * there was nothing: you chose a photo, the screen did not move, and the avatar changed later, or
 * not. a failure was even more silent, `runCatching` swallowing it without a word. three states are
 * enough, and the third is the one that was missing most
 */
internal sealed interface EnvoiImage {
    data object EnCours : EnvoiImage

    data object Reussi : EnvoiImage

    data class Echec(val motif: String?) : EnvoiImage
}

/**
 * where the upload has got to, readable from any window
 *
 * a module level state rather than a composable's `var`, and this is not convenience: the app's
 * full screen screens live in a `Dialog`, so in their own window. a banner placed in the shell's
 * composition is painted under that window, and a `Popup` attaches to the current window, which is
 * the crop dialog we have just closed. both were tried, and measured silent while the trace did
 * show the state changes
 *
 * a shared value lets the same banner be shown from every window, the shell and [PleinEcran],
 * without walking the state through ten signatures
 *
 * ponytail: global state taken on for one single interface value. if a second appears, that is the
 * moment to make it a `CompositionLocal` provided by the shell
 */
internal object EnvoiImageEnCours {
    var etat by mutableStateOf<EnvoiImage?>(null)
}

/**
 * the banner saying where the upload has got to, laid above everything and blocking nothing
 *
 * a `Popup` and not a dialog box: while an image uploads you have to be able to go on reading your
 * conversations. a modal to announce a success is an obstacle that celebrates
 *
 * the success fades on its own after two and a half seconds, you saw it, it is done. the failure
 * stays six seconds, because it carries a reason you may want to read twice
 */
@Composable
internal fun BandeauEnvoiImage() {
    val t = textes()
    val tokens = LocalTokens.current
    val etat = EnvoiImageEnCours.etat ?: return

    LaunchedEffect(etat) {
        when (etat) {
            EnvoiImage.EnCours -> Unit
            EnvoiImage.Reussi -> {
                kotlinx.coroutines.delay(2_500)
                EnvoiImageEnCours.etat = null
            }

            is EnvoiImage.Echec -> {
                kotlinx.coroutines.delay(6_000)
                EnvoiImageEnCours.etat = null
            }
        }
    }

    // in the layout, not in a separate window
    //
    // the first version used a `Popup`. measured on a device: it composed correctly, the trace
    // showing `EnCours` then `Reussi` at the right moments, and it painted nowhere. a `Popup`
    // attaches to the current window, and this one was the crop dialog we had just closed in the
    // same frame
    //
    // a transparent box filling the screen does not have that problem: it is drawn by the same pass
    // as the rest, after the screens preceding it, so above them. it intercepts nothing either, a
    // `Box` with no pointer modifier letting everything through
    Box(
        Modifier.fillMaxSize().padding(bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val (texte, teinte) = when (etat) {
            EnvoiImage.EnCours -> t.envoiDeLImage to tokens.surfaceRaised
            EnvoiImage.Reussi -> t.imageMiseAJour to tokens.accent
            is EnvoiImage.Echec ->
                (t.envoiEchoue + (etat.motif?.let { " — $it" } ?: "")) to Color(0xFF7A2230)
        }
        Row(
            Modifier.padding(horizontal = tokens.espXl)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(teinte)
                .padding(horizontal = tokens.espXl, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (etat is EnvoiImage.EnCours) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    color = tokens.textSecondary,
                    strokeWidth = 2.dp,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
            }
            Text(
                texte,
                color = if (etat == EnvoiImage.Reussi) Color(0xFF10201F) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * a chosen image, the shape it will be worn in, and where to send it once framed
 *
 * one type for the personal avatar and the server icon: the two differ only by the mask and by the
 * final call. making two paths would have given two crop screens to fix the day one of them has a
 * fault
 */
internal class DemandeRecadrage(
    val uri: android.net.Uri,
    val rond: Boolean,
    val apres: suspend (java.io.File) -> Unit,
)

package li.velvetchat.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.core.model.MediaRef

/**
 * what you need to know to open a viewer: the images, which one to look at, and where they come
 * from
 *
 * the message's context is not decorative: out of the conversation you no longer know who sent what
 * or when. it is precisely what discord shows at the bottom of its lightbox, and it is what tells a
 * viewer from a plain enlargement
 */
data class DemandeVisionneuse(
    val medias: List<MediaRef>,
    val index: Int,
    val userId: String,
    val auteur: String,
    val avatarUrl: String?,
    val horodatage: Long,
    val legende: String,
)

/**
 * the lightbox
 *
 * two layers, and the whole principle is in their separation: the image takes the screen, and the
 * information lays itself over. a tap clears it, which is the expected gesture, and it is only of
 * interest if the image is already underneath. a viewer that reserved space for the bars could free
 * nothing by hiding them
 *
 * so we do not close on a tap: the tap is for clearing. closing goes through the cross or the back
 * gesture, two gestures you do not make absent mindedly
 */
@Composable
fun Lightbox(demande: DemandeVisionneuse, onFermer: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    var index by remember(demande) { mutableStateOf(demande.index.coerceIn(0, demande.medias.lastIndex)) }
    var habillage by remember(demande) { mutableStateOf(true) }

    // the zoom, and why it lives here rather than in `ImagePleinEcran`
    //
    // the scale decides what the horizontal drag does: at 1x it changes image, and zoomed in it
    // moves what you are looking at. the two gestures are the same gesture, and only the zoom state
    // tells them apart, and splitting them into two components would mean lifting the scale up
    // anyway. reset to 1 on changing image: staying zoomed on the next one, in the same place,
    // makes no sense
    var echelle by remember(demande, index) { mutableStateOf(1f) }
    var decalage by remember(demande, index) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // only an image is pinched: the video player and the pdf player have their own gestures
    val zoomable = remember(demande, index) {
        val ref = demande.medias[index]
        ref.mimeType?.startsWith("video/") != true && !ref.estPdf()
    }

    androidx.activity.compose.BackHandler(onBack = onFermer)

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        // the image, under everything else and over the whole surface
        Box(
            Modifier.fillMaxSize()
                .pointerInput(demande, index) {
                    detectTapGestures(
                        // double tap: enlarge, then come back. the gesture expected of any viewer,
                        // and the only one that lets you zoom one handed
                        onDoubleTap = { position ->
                            if (!zoomable) return@detectTapGestures
                            if (echelle > 1f) {
                                echelle = 1f
                                decalage = androidx.compose.ui.geometry.Offset.Zero
                            } else {
                                echelle = 2.5f
                                // enlarge around the finger and not around the centre: otherwise
                                // the detail you have just pointed at goes off the screen
                                val centre = androidx.compose.ui.geometry.Offset(
                                    size.width / 2f,
                                    size.height / 2f,
                                )
                                decalage = (centre - position) * (echelle - 1f)
                            }
                        },
                        onTap = { habillage = !habillage },
                    )
                }
                .pointerInput(demande, index, zoomable) {
                    // a video carries its own controls and a pdf scrolls on its own: capturing
                    // their gestures here would make them unusable
                    if (!zoomable) return@pointerInput
                    // one single detector for the pinch and the drag, because they are the same
                    // fingers: `detectTransformGestures` returns the scale change and the movement
                    // in the same event
                    detectTransformGestures { _, pan, zoom, _ ->
                        // 1x floor: below it the image would float in black
                        echelle = (echelle * zoom).coerceIn(1f, 6f)
                        decalage = if (echelle <= 1f) {
                            androidx.compose.ui.geometry.Offset.Zero
                        } else {
                            // bounded to what sticks out: without it you pull the image out of the
                            // frame and look at emptiness
                            val maxX = size.width * (echelle - 1f) / 2f
                            val maxY = size.height * (echelle - 1f) / 2f
                            val vise = decalage + pan
                            androidx.compose.ui.geometry.Offset(
                                vise.x.coerceIn(-maxX, maxX),
                                vise.y.coerceIn(-maxY, maxY),
                            )
                        }
                    }
                }
                .pointerInput(demande, index, echelle > 1f) {
                    // changing image only counts at real size. zoomed in, the horizontal drag is
                    // for moving around the image, and letting it also turn the page would make the
                    // zoom unusable
                    if (echelle > 1f) return@pointerInput
                    var parcouru = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val seuil = tokens.density.swipeReplyThresholdDp.dp.toPx()
                            if (parcouru < -seuil && index < demande.medias.lastIndex) index++
                            if (parcouru > seuil && index > 0) index--
                            parcouru = 0f
                        },
                        onDragCancel = { parcouru = 0f },
                    ) { _, delta -> parcouru += delta }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.graphicsLayer(
                    scaleX = echelle,
                    scaleY = echelle,
                    translationX = decalage.x,
                    translationY = decalage.y,
                ),
                contentAlignment = Alignment.Center,
            ) {
                MediaPleinEcran(demande.medias[index])
            }
        }

        val duree = tokens.dureeNormale
        AnimatedVisibility(
            visible = habillage,
            enter = fadeIn(tween(duree)) + slideInVertically(tween(duree)) { -it },
            exit = fadeOut(tween(duree)) + slideOutVertically(tween(duree)) { -it },
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = tokens.espL, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icone(
                    Icones.fermer,
                    tint = tokens.textPrimary,
                    taille = 24.dp,
                    description = t.fermer,
                    modifier = Modifier.clickable(onClick = onFermer),
                )
                if (demande.medias.size > 1) {
                    Box(Modifier.width(16.dp))
                    Text(
                        "${index + 1} / ${demande.medias.size}",
                        color = tokens.textSecondary,
                        fontSize = tokens.typography.captionSize.sp,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = habillage,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(duree)) + slideInVertically(tween(duree)) { it },
            exit = fadeOut(tween(duree)) + slideOutVertically(tween(duree)) { it },
        ) {
            BarreOrigine(demande, index) { index = it }
        }
    }
}

/**
 * the lightbox's footer: from whom, when, what, and the filmstrip
 *
 * an opaque background rather than a gradient: a bright image under a gradient makes the text
 * unreadable exactly when you need it, and a gradient dark enough to hold in every case amounts to
 * an opaque background with more code
 */
@Composable
private fun BarreOrigine(demande: DemandeVisionneuse, index: Int, onChoisir: (Int) -> Unit) {
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxWidth().background(tokens.surface).navigationBarsPadding()
            .padding(vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                userId = demande.userId,
                displayName = demande.auteur,
                avatarUrl = demande.avatarUrl,
                taille = 28.dp,
            )
            Box(Modifier.width(8.dp))
            Text(
                demande.auteur,
                color = tokens.accent,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.captionSize.sp,
            )
            Box(Modifier.width(8.dp))
            Text(
                remember(demande.horodatage) {
                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", localeCourante())
                        .format(java.util.Date(demande.horodatage))
                },
                color = tokens.textMuted,
                fontSize = 10.sp,
            )
        }

        if (demande.legende.isNotBlank()) {
            Text(
                demande.legende,
                color = tokens.textPrimary,
                fontSize = tokens.typography.bodySize.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = tokens.espM, vertical = 6.dp),
            )
        }

        // the filmstrip only appears when there are several images: a one item filmstrip offers no
        // choice and only shrinks the image
        if (demande.medias.size > 1) {
            LazyRow(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            ) {
                itemsIndexed(demande.medias, key = { _, m -> m.source }) { i, media ->
                    Box(
                        Modifier.size(width = 34.dp, height = 46.dp)
                            .clip(RoundedCornerShape(tokens.radiusSmall))
                            .then(
                                // the accent outline on the current thumbnail: without it the
                                // filmstrip says what you can look at but not what you are looking
                                // at
                                if (i == index) {
                                    Modifier.border(
                                        2.dp,
                                        tokens.accent,
                                        RoundedCornerShape(tokens.radiusSmall),
                                    )
                                } else Modifier
                            )
                            .clickable { onChoisir(i) }
                    ) {
                        Vignette(media)
                    }
                }
            }
        }
    }
}

/**
 * what you are looking at, depending on what it is
 *
 * the viewer only served images. and yet it is the app's only component laid above the whole
 * window, a video opened from the timeline having been cropped by the server rail, exactly the
 * fault fixed the day the lightbox moved up here. so having it carry all three cases is shorter and
 * more correct than a second full screen
 */
@Composable
private fun MediaPleinEcran(ref: MediaRef) {
    when {
        ref.mimeType?.startsWith("video/") == true -> LecteurVideo(ref)
        ref.estPdf() -> LecteurPdf(ref)
        else -> ImagePleinEcran(ref)
    }
}

@Composable
private fun ImagePleinEcran(ref: MediaRef) {
    val engine = LocalEngine.current
    var image by remember(ref.source) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(ref.source) {
        // full resolution here, and not the timeline's cache entry: that one was downsampled for a
        // thumbnail's width, and would be blurry large
        image = runCatching {
            withContext(Dispatchers.IO) { decoderMedia(engine.mediaBytes(ref), 1440) }
        }.getOrNull()
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = ref.filename,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Vignette(ref: MediaRef) {
    val engine = LocalEngine.current
    var image by remember(ref.source) { mutableStateOf(cacheMedia(ref.source)) }
    LaunchedEffect(ref.source) {
        if (image != null) return@LaunchedEffect
        image = runCatching {
            withContext(Dispatchers.IO) { decoderMedia(engine.mediaBytes(ref), 160) }
        }.getOrNull()?.also { mettreEnCacheMedia(ref.source, it) }
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

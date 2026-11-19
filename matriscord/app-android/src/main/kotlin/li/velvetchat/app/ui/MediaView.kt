package li.velvetchat.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.core.model.MediaRef

/**
 * opens the lightbox. supplied by `Shell`, the only ancestor covering the whole window
 *
 * the parameter carries the original message and not only the medium: out of the conversation, an
 * image no longer says who it comes from or when, and that context is what the lightbox shows at
 * the bottom, and losing it on the way would mean finding it again from the id
 */
val LocalOuvrirMedia = staticCompositionLocalOf<(DemandeVisionneuse) -> Unit> { {} }

/**
 * an image in the timeline
 *
 * the space is reserved before loading, from the dimensions carried by the event. that is precisely
 * what they are for: without them the row would have its text's height then jump when the image
 * arrives, moving everything the person is reading. when the event does not carry them we fall back
 * on a square, one jump beating a jump per image
 */
@Composable
fun ImageDuMessage(ref: MediaRef, modifier: Modifier = Modifier, onOuvrir: () -> Unit = {}) {
    val t = textes()
    val tokens = LocalTokens.current
    val engine = LocalEngine.current

    var image by remember(ref.source) { mutableStateOf(cacheMedia(ref.source)) }
    var anime by remember(ref.source) {
        mutableStateOf<android.graphics.drawable.AnimatedImageDrawable?>(null)
    }
    var echec by remember(ref.source) { mutableStateOf<String?>(null) }

    LaunchedEffect(ref.source) {
        if (image != null) return@LaunchedEffect
        runCatching {
            val octets = engine.mediaBytes(ref)
            withContext(Dispatchers.IO) {
                // the gif animates, and that was the most visible fault
                //
                // everything went through `BitmapFactory`, which only renders a gif's first frame.
                // a gif sent therefore arrived at the other end as a photo, reported from outside,
                // and that is indeed what the code did
                //
                // `ImageDecoder` returns an `AnimatedImageDrawable` when the source is one. the
                // same path as the animated emojis and the gif picker, and it is not cached for the
                // same reason as them, an `AnimatedImageDrawable` carrying its own playback state,
                // and two shared occurrences being synchronised on the same frame
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val source = android.graphics.ImageDecoder.createSource(
                        java.nio.ByteBuffer.wrap(octets)
                    )
                    val dessin = runCatching {
                        android.graphics.ImageDecoder.decodeDrawable(source)
                    }.getOrNull()
                    if (dessin is android.graphics.drawable.AnimatedImageDrawable) {
                        dessin.start()
                        return@withContext dessin
                    }
                }
                decoderMedia(octets, LARGEUR_TIMELINE_PX)
            }
        }.onSuccess {
            when (it) {
                null -> echec = t.formatImageInconnu
                is android.graphics.drawable.AnimatedImageDrawable -> anime = it
                is ImageBitmap -> { mettreEnCacheMedia(ref.source, it); image = it }
                else -> echec = t.formatImageInconnu
            }
        }.onFailure { echec = it.message ?: t.telechargementImpossible }
    }

    val ratio = ref.width?.toFloat()?.div((ref.height ?: 0).coerceAtLeast(1)) ?: 1f
    Box(
        // capped on the screen's width, no longer on 260 dp
        //
        // compared side by side with the reference: its gif takes nearly three quarters of the
        // width, and ours took 260 dp, so two thirds less on a large screen, and the same on a
        // small one, which is the real fault of a fixed value
        //
        // a fraction and not a number: that is what makes a wide phone show a large gif and a
        // narrow phone a proportionate one, with no two settings to keep
        modifier.widthIn(max = largeurMaxMedia()).heightIn(max = HAUTEUR_MAX_DP.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        when {
            anime != null -> {
                val dessin = anime!!
                // the cap decides which ones run. refused, the gif stays on its current frame: it
                // takes the same space and is recognisable, it simply does not move
                val tourne = creneauAnime(ref.source)
                LaunchedEffect(dessin, tourne) {
                    if (tourne) dessin.start() else dessin.stop()
                }
                // a gif takes its space, whatever the file's size
                //
                // the rule was "never enlarged", and it came from a real fault: a 120 px gif
                // stretched to the decoding width came out blurry. the fix of the time bounded it
                // to the original size, which created the opposite fault, a gif library serving
                // files of 200 to 500 px, so each arrived at a different width, and all of them
                // small. the reference puts them all at the bubble's width
                //
                // enlarging a gif does not cost what it cost a photo: it is an animation a few
                // hundred pixels across, made to be watched large, and the interpolation does not
                // show on it as it does on a face. still images keep the original rule, see the
                // next branch
                //
                // both bounds matter: without the height one, a portrait gif set to the bubble's
                // width would eat the whole screen
                val densite = androidx.compose.ui.platform.LocalDensity.current
                val largeurDp = with(densite) { dessin.intrinsicWidth.toDp() }
                val hauteurDp = with(densite) { dessin.intrinsicHeight.toDp() }
                val maxLargeur = largeurMaxMedia()
                val facteur = minOf(
                    maxLargeur.value / largeurDp.value.coerceAtLeast(1f),
                    HAUTEUR_MAX_DP / hauteurDp.value.coerceAtLeast(1f),
                )
                androidx.compose.foundation.Canvas(
                    Modifier
                        .width(largeurDp * facteur)
                        .height(hauteurDp * facteur)
                        .clickable(onClick = onOuvrir)
                ) {
                    dessin.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                    dessin.draw(drawContext.canvas.nativeCanvas)
                }
            }

            image != null -> {
                // a still image is never enlarged, and that rule stays
                //
                // `decoderMedia` decodes at the timeline's width, which would enlarge a small
                // image, and on a photo the interpolation shows. so we bound it to its real size,
                // unlike the gifs just above
                val densite = androidx.compose.ui.platform.LocalDensity.current
                val largeurDp = with(densite) { image!!.width.toDp() }
                val facteur = minOf(
                    1f,
                    largeurMaxMedia().value / largeurDp.value.coerceAtLeast(1f),
                )
                Image(
                    bitmap = image!!,
                    contentDescription = ref.filename,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(largeurDp * facteur)
                        .clickable(onClick = onOuvrir),
                )
            }

            echec != null -> Text(
                "🖼 ${ref.filename} — $echec",
                color = tokens.textMuted,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier.padding(tokens.espM),
            )

            // the waiting rectangle already occupies the final space
            else -> Box(
                Modifier.width(220.dp).height((220f / ratio.coerceIn(0.4f, 3f)).dp)
            )
        }
    }
}

/**
 * a gallery: several images sent in a row, rendered as columns of free heights, the masonry layout,
 * pinterest's
 *
 * why group at display time rather than at send time: matrix has no multi image message. each photo
 * is an event in its own right, which is the right granularity for the protocol, you can delete one
 * without touching the others, and another client will see them all. so the interface is what glues
 * them back together, exactly as it already glues one author's messages into groups
 *
 * what masonry gains over the square grid it replaces: no cropping at all. a tall photo stays tall,
 * a panorama stays a panorama. the previous grid imposed squares because uneven heights make jagged
 * rows, but in columns the unevenness of heights is no longer a fault, it is the principle
 *
 * the ratios come from the event, not from the downloaded images. a consequence worth saying: the
 * layout is computed before a single photo arrives, so the gallery does not reorganise itself
 * before your eyes as things load. same reasoning as a single image's reserved space, applied to a
 * set
 */
@Composable
fun GalerieImages(medias: List<MediaRef>, onOuvrir: (Int) -> Unit) {
    if (medias.size == 1) {
        ImageDuMessage(medias[0]) { onOuvrir(0) }
        return
    }

    val tokens = LocalTokens.current
    val colonnes = if (medias.size <= 4) 2 else 3
    val largeur = LARGEUR_GALERIE_DP.dp
    val ecart = 3.dp
    val largeurColonne = (largeur - ecart * (colonnes - 1)) / colonnes

    val repartition = remember(medias, colonnes) { repartir(medias, colonnes) }

    Row(horizontalArrangement = Arrangement.spacedBy(ecart)) {
        repartition.forEach { colonne ->
            Column(
                Modifier.width(largeurColonne),
                verticalArrangement = Arrangement.spacedBy(ecart),
            ) {
                colonne.forEach { (position, media) ->
                    val ratio = rapport(media)
                    Box(
                        Modifier
                            .width(largeurColonne)
                            .height(largeurColonne / ratio)
                            .clip(RoundedCornerShape(tokens.radiusSmall))
                            .background(tokens.surfaceRaised)
                            .clickable { onOuvrir(position) }
                    ) {
                        ImageDeGalerie(media, largeurColonne)
                    }
                }
            }
        }
    }
}

/**
 * files the images into columns, in order, each into the shortest column
 *
 * the send order is kept strictly: image `k` is always placed after image `k-1`, and the position
 * handed to the lightbox is the original position. what a masonry cannot promise, though, is that
 * the eye reads them in that order, a very tall image offsetting its column, and the next one,
 * placed beside it, therefore starting higher than it
 *
 * that is the trade off inherent to this layout, and it has no solution: guaranteeing row by row
 * reading would mean equalising the heights within each row, which is another layout (google
 * photos') and not a masonry. here we prefer having no cropping, because in a conversation you look
 * at photos, you do not read a list
 *
 * ties settled to the left: at equal heights, the case of two photos of the same format and a very
 * common one, the first goes left, and the reading order is the one you expect
 */
private fun repartir(
    medias: List<MediaRef>,
    colonnes: Int,
): List<List<Pair<Int, MediaRef>>> {
    val paniers = List(colonnes) { mutableListOf<Pair<Int, MediaRef>>() }
    val hauteurs = FloatArray(colonnes)
    medias.forEachIndexed { position, media ->
        val cible = hauteurs.indices.minByOrNull { hauteurs[it] } ?: 0
        paniers[cible] += position to media
        // the height in units of "column width": the real width is the same for every column, so it
        // cancels out of the comparison
        hauteurs[cible] += 1f / rapport(media)
    }
    return paniers
}

/**
 * width over height, bounded
 *
 * without bounds, a banner photo (10:1) or a very long screenshot (1:20) would impose its
 * extravagance on the whole column. the bounds do not deform the image, they bound the space we
 * grant it, and the cropping then only concerns extreme cases, instead of applying to everybody as
 * in a square grid
 */
private fun rapport(media: MediaRef): Float {
    val l = media.width?.toFloat() ?: return 1f
    val h = media.height?.toFloat() ?: return 1f
    if (l <= 0f || h <= 0f) return 1f
    return (l / h).coerceIn(0.5f, 2.5f)
}

@Composable
private fun ImageDeGalerie(ref: MediaRef, largeur: androidx.compose.ui.unit.Dp) {
    val engine = LocalEngine.current
    val densite = LocalDensity.current
    var image by remember(ref.source) { mutableStateOf(cacheMedia(ref.source)) }
    LaunchedEffect(ref.source) {
        if (image != null) return@LaunchedEffect
        val cible = with(densite) { largeur.roundToPx() } * 2
        image = runCatching {
            withContext(Dispatchers.IO) { decoderMedia(engine.mediaBytes(ref), cible) }
        }.getOrNull()?.also { mettreEnCacheMedia(ref.source, it) }
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = ref.filename,
            // the box already has the image's ratio: `Crop` therefore crops nothing, except in the
            // extreme cases `rapport` deliberately bounded
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** an attachment that cannot be shown inline: name, type, size */
@Composable
fun FichierDuMessage(icone: String, ref: MediaRef, legende: String) {
    val tokens = LocalTokens.current
    Row(
        Modifier.clip(RoundedCornerShape(tokens.radiusMedium)).background(tokens.surfaceRaised)
            .padding(horizontal = 10.dp, vertical = tokens.espS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icone, fontSize = 18.sp)
        Column(Modifier.padding(start = tokens.espS)) {
            Text(
                legende.ifBlank { ref.filename },
                color = tokens.textPrimary,
                fontSize = tokens.typography.bodySize.sp,
            )
            Text(
                listOfNotNull(
                    ref.mimeType,
                    ref.sizeBytes?.let { horsUi.tailleKo(it / 1024) },
                    ref.durationMs?.let { "${it / 1000} s" },
                ).joinToString(" · "),
                color = tokens.textMuted,
                fontSize = tokens.typography.captionSize.sp,
            )
        }
    }
}

/**
 * the maximum width of a medium in the timeline: three quarters of the screen
 *
 * bounded at 420 dp so as not to produce, on a tablet, an image crossing the page. the reference
 * does not do that either
 */
@Composable
private fun largeurMaxMedia(): androidx.compose.ui.unit.Dp {
    val ecran = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    return (ecran * 0.74f).coerceAtMost(420.dp)
}

/** the maximum height of a medium in the timeline, in dp */
private const val HAUTEUR_MAX_DP = 420f

private const val LARGEUR_GALERIE_DP = 264
private const val LARGEUR_TIMELINE_PX = 520
private const val LARGEUR_PLEIN_ECRAN_PX = 1440

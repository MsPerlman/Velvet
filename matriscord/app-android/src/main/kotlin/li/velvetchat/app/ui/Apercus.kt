package li.velvetchat.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.core.model.ApercuLien
import li.velvetchat.core.model.MediaRef

/**
 * video and pdf previews
 *
 * what this file fixes: a video and a pdf both showed as a grey row carrying an emoji, a filename
 * and a size, which is to say as an attachment you cannot open. "pdfs have no preview, same for
 * videos" describes that state exactly
 *
 * no library added, and that is possible because android already knows how to do everything:
 * `PdfRenderer` renders a pdf page into a bitmap, and `VideoView` plays a local file. a video
 * playback dependency, media3 and its megabytes, would add nothing for what we use here
 *
 * ## what is deliberately downloaded, and what is not
 *
 * a pdf preview requires the whole file, the format having no thumbnail. a video often has a
 * thumbnail served separately (`MediaRef.thumbnail`), but never in an encrypted room, where the
 * server only sees a blob and can therefore resize nothing
 *
 * hence the rule: we only download a whole medium for the preview under [SEUIL_APERCU]. beyond that
 * the card stays a card, browsing a conversation must not pull down forty megabytes of video by
 * surprise, on a mobile plan. playback does download them, but that is a requested gesture
 */
private const val SEUIL_APERCU = 8L * 1024 * 1024

/**
 * a video in the conversation: its first frame, and the play circle over it
 *
 * the circle is not decorative, it is what tells a video from a photo, both otherwise showing as a
 * rectangle. the duration at the bottom right is every gallery's convention, and it says before the
 * gesture what you are about to start
 */
@Composable
fun VideoDuMessage(ref: MediaRef, legende: String, onOuvrir: () -> Unit) {
    val tokens = LocalTokens.current
    val engine = LocalEngine.current
    var poster by remember(ref.source) { mutableStateOf(cacheMedia("poster:" + ref.source)) }

    LaunchedEffect(ref.source) {
        if (poster != null) return@LaunchedEffect
        poster = runCatching {
            withContext(Dispatchers.IO) {
                // 1) the thumbnail served by the server, when it can make one
                engine.mediaThumbnail(ref, 520, 520)?.let {
                    return@withContext decoderMedia(it, 520)
                }
                // 2) otherwise, in an encrypted room, a frame extracted from the file, but only
                // when it is small enough that pulling it down costs nothing visible
                if ((ref.sizeBytes ?: Long.MAX_VALUE) > SEUIL_APERCU) return@withContext null
                imageDeVideo(engine.mediaFile(ref))
            }
        }.getOrNull()?.also { mettreEnCacheMedia("poster:" + ref.source, it) }
    }

    val ratio = ref.width?.toFloat()?.div((ref.height ?: 0).coerceAtLeast(1))
        ?.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)

    Column {
        Box(
            Modifier.width(240.dp).aspectRatio(ratio.coerceIn(0.5f, 2.2f))
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised)
                .clickable(onClick = onOuvrir),
            contentAlignment = Alignment.Center,
        ) {
            poster?.let {
                Image(
                    bitmap = it,
                    contentDescription = ref.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // the play circle, on a dark ground so it stays readable on any image, the same reason
            // as the banner's veil
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Color.White, fontSize = 20.sp)
            }
            ref.durationMs?.let { ms ->
                Text(
                    duree(ms),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .clip(RoundedCornerShape(tokens.radiusSmall))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        if (legende.isNotBlank() && legende != ref.filename) {
            Text(
                legende,
                color = tokens.textSecondary,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * a pdf in the conversation: its first page, then its name
 *
 * the first page is what identifies a document, an invoice, a piece of homework, a train ticket are
 * recognised at a glance, where "document.pdf" teaches nothing. that is exactly what the previous
 * grey row did not say
 */
@Composable
fun PdfDuMessage(ref: MediaRef, legende: String, onOuvrir: () -> Unit) {
    val tokens = LocalTokens.current
    val engine = LocalEngine.current
    var page by remember(ref.source) { mutableStateOf(cacheMedia("pdf:" + ref.source)) }

    LaunchedEffect(ref.source) {
        if (page != null) return@LaunchedEffect
        if ((ref.sizeBytes ?: Long.MAX_VALUE) > SEUIL_APERCU) return@LaunchedEffect
        page = runCatching {
            withContext(Dispatchers.IO) { pagesDePdf(engine.mediaFile(ref), 0, 1, 620) }
        }.getOrNull()?.firstOrNull()?.also { mettreEnCacheMedia("pdf:" + ref.source, it) }
    }

    Column(
        Modifier.width(200.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised)
            .clickable(onClick = onOuvrir)
    ) {
        Box(
            // an a4 page's ratio: the space is therefore reserved before the render, and the
            // conversation does not jump when the first page arrives
            Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            val rendue = page
            if (rendue != null) {
                Image(
                    bitmap = rendue,
                    contentDescription = ref.filename,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    "PDF",
                    color = Color(0xFF9A9A9A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = tokens.espS)) {
            Text(
                legende.ifBlank { ref.filename },
                color = tokens.textPrimary,
                fontSize = tokens.typography.bodySize.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ref.sizeBytes?.let {
                Text(
                    horsUi.tailleEnKo(it / 1024),
                    color = tokens.textMuted,
                    fontSize = tokens.typography.captionSize.sp,
                )
            }
        }
    }
}

/**
 * the video full screen, in the viewer
 *
 * `VideoView` and not a hand written player: it already brings the playback controls
 * (`MediaController`), the progress bar and scrubbing. the file is local, `mediaFile` having
 * decrypted it and put it on disk, so no transport question arises here
 */
@Composable
fun LecteurVideo(ref: MediaRef) {
    val t = textes()
    val engine = LocalEngine.current
    var chemin by remember(ref.source) { mutableStateOf<String?>(null) }
    var echec by remember(ref.source) { mutableStateOf<String?>(null) }

    LaunchedEffect(ref.source) {
        runCatching { withContext(Dispatchers.IO) { engine.mediaFile(ref) } }
            .onSuccess { chemin = it }
            .onFailure { echec = it.message ?: t.telechargementImpossible }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val local = chemin
        when {
            local != null -> AndroidView(
                factory = { contexte ->
                    android.widget.VideoView(contexte).apply {
                        setMediaController(
                            android.widget.MediaController(contexte)
                                .also { it.setAnchorView(this) }
                        )
                        setVideoPath(local)
                        // playback starts on its own: you have just opened the video, and asking
                        // for a second gesture to start it would be one step too many
                        setOnPreparedListener { start() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            echec != null -> Text(
                ref.filename + " — " + echec,
                color = Color.White,
                fontSize = 14.sp,
            )

            else -> Text(t.telechargementEnCours, color = Color.White, fontSize = 14.sp)
        }
    }
}

/**
 * the pdf full screen: its pages, one after another, in a scrolling list
 *
 * no zoom and no search, a full reader being an app in itself. what is here covers what you open a
 * pdf in a personal messenger for: reading it
 *
 * the pages are rendered on demand by the `LazyColumn`: a hundred page document therefore does not
 * build a hundred bitmaps
 */
@Composable
fun LecteurPdf(ref: MediaRef) {
    val t = textes()
    val engine = LocalEngine.current
    var chemin by remember(ref.source) { mutableStateOf<String?>(null) }
    var nombre by remember(ref.source) { mutableStateOf(0) }
    var echec by remember(ref.source) { mutableStateOf<String?>(null) }

    LaunchedEffect(ref.source) {
        runCatching {
            withContext(Dispatchers.IO) {
                val local = engine.mediaFile(ref)
                local to nombreDePages(local)
            }
        }.onSuccess { chemin = it.first; nombre = it.second }
            .onFailure { echec = it.message ?: t.lectureImpossibleMinuscule }
    }

    val local = chemin
    if (local == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(echec ?: t.ouverture, color = Color.White, fontSize = 14.sp)
        }
        return
    }

    val tokens = LocalTokens.current
    LazyColumn(Modifier.fillMaxSize()) {
        items((0 until nombre).toList()) { index ->
            var page by remember(local, index) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(local, index) {
                page = runCatching {
                    withContext(Dispatchers.IO) { pagesDePdf(local, index, 1, 1200).firstOrNull() }
                }.getOrNull()
            }
            Box(
                Modifier.fillMaxWidth().padding(vertical = tokens.espXs)
                    .aspectRatio(1f / 1.414f).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                page?.let {
                    Image(
                        bitmap = it,
                        contentDescription = t.pageNumeroDe(index + 1),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// the file work
//
// everything below opens a file and returns a bitmap. nothing in it touches compose: these
// functions are called from `Dispatchers.IO`, and keeping them pure makes that constraint obvious
// on reading

/** a local video's first frame. `null` when the format is unreadable */
private fun imageDeVideo(chemin: String): ImageBitmap? = runCatching {
    val lecteur = android.media.MediaMetadataRetriever()
    try {
        lecteur.setDataSource(chemin)
        // `frameAtTime` with no argument takes a representative frame near the start, frame zero of
        // a video being very often black
        lecteur.frameAtTime?.asImageBitmap()
    } finally {
        lecteur.release()
    }
}.getOrNull()

private fun nombreDePages(chemin: String): Int =
    runCatching { ouvrirPdf(chemin) { it.pageCount } }.getOrDefault(0)

/**
 * renders [combien] pages from [depuis], at [largeurCible] pixels wide
 *
 * `ARGB_8888` and a white background painted by hand: `PdfRenderer` draws on transparency, and a
 * pdf on a transparent background shown in a dark theme gives black text on black
 */
private fun pagesDePdf(
    chemin: String,
    depuis: Int,
    combien: Int,
    largeurCible: Int,
): List<ImageBitmap> = runCatching {
    ouvrirPdf(chemin) { rendu ->
        (depuis until minOf(depuis + combien, rendu.pageCount)).map { index ->
            rendu.openPage(index).use { page ->
                val echelle = largeurCible.toFloat() / page.width.coerceAtLeast(1)
                val bitmap = android.graphics.Bitmap.createBitmap(
                    largeurCible,
                    (page.height * echelle).toInt().coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
                android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
                page.render(
                    bitmap,
                    null,
                    null,
                    android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                )
                bitmap.asImageBitmap()
            }
        }
    }
}.getOrDefault(emptyList())

private fun <T> ouvrirPdf(chemin: String, bloc: (android.graphics.pdf.PdfRenderer) -> T): T {
    val descripteur = android.os.ParcelFileDescriptor.open(
        java.io.File(chemin),
        android.os.ParcelFileDescriptor.MODE_READ_ONLY,
    )
    return descripteur.use { fd -> android.graphics.pdf.PdfRenderer(fd).use { bloc(it) } }
}

/** `m:ss`, or `h:mm:ss` past the hour */
private fun duree(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    fun deuxChiffres(n: Long) = if (n < 10) "0$n" else n.toString()
    return if (h > 0) "$h:${deuxChiffres(m)}:${deuxChiffres(s)}"
    else "$m:${deuxChiffres(s)}"
}

/**
 * a pdf, in the mime type sense
 *
 * the filename serves as a fallback: not everybody fills in `mimetype`, and an `application/octet-
 * stream` named `invoice.pdf` is a pdf
 */
fun MediaRef.estPdf(): Boolean =
    mimeType?.startsWith("application/pdf") == true ||
        filename.endsWith(".pdf", ignoreCase = true)

// link previews

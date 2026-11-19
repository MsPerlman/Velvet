package li.velvetchat.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import li.velvetchat.app.ui.Icone
import li.velvetchat.app.ui.Icones
import li.velvetchat.app.ui.LocalTokens
import li.velvetchat.app.ui.textes
import li.velvetchat.app.ui.*

/**
 * what was chosen for sharing, kept from one share to the next
 *
 * in the settings rather than in memory: setting the quality on every call would mean setting it
 * once and then living with the default forever
 */
internal object ReglagesPartage {
    /**
     * observable, and not merely persisted
     *
     * the first version read the setting from the file on every access. nothing in that is compose
     * state, so clicking did write to disk and no recomposition followed, which means the button
     * never lit up and people thought the click had not registered. it had, it just did not show
     */
    var qualite by androidx.compose.runtime.mutableStateOf(
        runCatching { QualitePartage.valueOf(Reglages.lire("partage.qualite").orEmpty()) }
            .getOrDefault(QualitePartage.NORMALE),
    )

    /** whose sound goes out with the picture */
    var son by androidx.compose.runtime.mutableStateOf(
        runCatching { SonDuPartage.valueOf(Reglages.lire("partage.son2").orEmpty()) }
            .getOrDefault(SonDuPartage.AUCUN),
    )

    /** call it after a change: the state lives in memory and the file follows it */
    fun retenir() {
        Reglages.ecrire("partage.qualite", qualite.name)
        // a new key, because the old one held a directshow device name and reading it back as a
        // mode would land on `AUCUN` anyway, silently. a clean break says what happened
        Reglages.ecrire("partage.son2", son.name)
    }
}

/**
 * a source's preview, the one you look at before choosing
 *
 * it is more than a nicety: with no picture, choosing between "screen 1" and "screen 2", or among
 * six windows with similar titles, is guesswork, and you find out you were wrong once everybody is
 * watching
 *
 * a still image, taken once when the picker opens. discord animates its thumbnails, and doing that
 * here would mean holding one ffmpeg process per window, ten streams to pick one. a photo is enough
 * to recognise what you are about to show, and costs one round trip instead of ten loops
 */
internal object ApercusSources {

    private const val LARGEUR = 320

    /** the thumbnails already taken, by source. cleared every time the picker opens */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

    fun oublier() = cache.clear()

    fun connu(source: SourceVideo): ImageBitmap? = cache[cle(source)]

    private fun cle(source: SourceVideo) = when (source) {
        // the handle, because two windows can carry the same title and one of them would have
        // shown the other's picture
        is SourceVideo.Fenetre -> "Fenetre:" + source.poignee
        else -> source::class.simpleName + ":" + source.libelle
    }

    /**
     * takes the thumbnail, even when we already have one
     *
     * they used to be taken once and kept, which gave a grid of photographs where the reference
     * shows what is happening. a screen recaptures through the jdk with no process, so it can be
     * done often, and a window costs an ffmpeg, so it waits its turn. see the caller for the rate
     */
    suspend fun rafraichir(source: SourceVideo): ImageBitmap? = prendre(source, force = true)

    suspend fun prendre(source: SourceVideo, force: Boolean = false): ImageBitmap? {
        if (!force) cache[cle(source)]?.let { return it }
        val image = withContext(Dispatchers.IO) {
            // anything that is a piece of desktop, screen or window, captures through the jdk:
            // instant, and with no process
            //
            // windows used to go through ffmpeg, one process per thumbnail every two seconds. with
            // ten windows open that is five launches a second, each with a six second timeout,
            // and they queued until they expired: hence the grid of black squares. `Robot` reads
            // the same pixels with no process at all
            val cadre = SourcesVideo.cadre(source)
            when {
                // a window goes through wgc when it can: the thumbnail then shows the window's
                // own content, covered or not, which is what you are choosing between. it costs
                // one short lived process, and one only, because the queue below serialises them
                source is SourceVideo.Fenetre && CaptureWgc.disponible ->
                    parWgc(source) ?: cadre?.let {
                        runCatching { reduire(robot().createScreenCapture(it)) }.getOrNull()
                    }
                cadre != null -> runCatching { reduire(robot().createScreenCapture(cadre)) }
                    .getOrNull()
                // a camera or a film has no place on the desktop: only those still need ffmpeg
                else -> uneImageParFfmpeg(source)
            }
        } ?: return null
        cache[cle(source)] = image
        return image
    }

    /**
     * one frame, as png on standard output
     *
     * `-frames:v 1` and a short timeout: a window closed meanwhile, a camera held by another app,
     * and ffmpeg would wait forever for something that will not come. an empty cell beats a frozen
     * picker
     */
    private fun uneImageParFfmpeg(source: SourceVideo): ImageBitmap? {
        val exe = SourcesVideo.ffmpeg ?: return null
        val entree = SourcesVideo.arguments(source, 5) ?: return null
        return runCatching {
            val commande = buildList {
                add(exe.absolutePath)
                addAll(listOf("-hide_banner", "-loglevel", "error"))
                addAll(entree)
                addAll(listOf("-frames:v", "1", "-vf", "scale=$LARGEUR:-2", "-f", "image2", "-c:v", "png", "-"))
            }
            val p = ProcessBuilder(commande)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val octets = p.inputStream.readBytes()
            if (!p.waitFor(6, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
            if (octets.isEmpty()) null
            else org.jetbrains.skia.Image.makeFromEncoded(octets).toComposeImageBitmap()
        }.getOrNull()
    }

    /**
     * one thumbnail at a time, whatever the grid is doing
     *
     * ten cells each refreshing on their own timer meant ten processes at once, which is what
     * made the old grid a wall of black squares. a wgc shot lives a few hundred milliseconds, so
     * queueing them costs nothing visible and never floods the machine
     */
    private val filuneImage = kotlinx.coroutines.sync.Mutex()

    private suspend fun parWgc(source: SourceVideo): ImageBitmap? = filuneImage.withLock {
        withContext(Dispatchers.IO) {
            CaptureWgc.uneImage(source, LARGEUR)?.let {
                runCatching {
                    org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()
                }.getOrNull()
            }
        }
    }

    /** one robot for the whole app: building one costs a native handle, and we take many shots */
    private val robotPartage by lazy { runCatching { java.awt.Robot() }.getOrNull() }

    private fun robot(): java.awt.Robot = robotPartage ?: error("robot indisponible")

    private fun reduire(image: java.awt.image.BufferedImage): ImageBitmap {
        val hauteur = LARGEUR * image.height / image.width
        val petite = java.awt.image.BufferedImage(LARGEUR, hauteur, java.awt.image.BufferedImage.TYPE_INT_RGB)
        petite.createGraphics().apply {
            setRenderingHint(
                java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            drawImage(image, 0, 0, LARGEUR, hauteur, null)
            dispose()
        }
        return petite.toComposeImageBitmap()
    }
}

/**
 * choosing what you share, on the reference's model: three tabs, a grid of previews, and the
 * settings bar at the bottom
 *
 * the old menu listed names. three screens called "screen 1, 2, 3" and six windows with lookalike
 * titles meant choosing blind. here you see
 */
@Composable
internal fun SelecteurDePartage(
    onFermer: () -> Unit,
    /** the tab to open on: 0 applications, 1 whole screens, 2 devices (the camera button) */
    ongletInitial: Int = 0,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val fenetre = LocalFenetre.current
    val portee = rememberCoroutineScope()

    var onglet by remember { mutableStateOf(ongletInitial) }
    val ecrans = remember { SourcesVideo.ecrans() }
    val fenetres = remember { SourcesVideo.fenetres() }
    val entrees = remember { SourcesVideo.entrees() }
    // taken again on every open: a window may have changed content since last time
    DisposableEffect(Unit) { onDispose { ApercusSources.oublier() } }

    val sources = when (onglet) {
        0 -> fenetres
        1 -> ecrans
        else -> entrees
    }

    Dialog(onDismissRequest = onFermer) {
        Column(
            Modifier.width(920.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF16171A))
                .border(1.dp, BORD_MENU, RoundedCornerShape(12.dp)),
        ) {
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(t.applications, t.ecranEntier, t.appareils).forEachIndexed { i, titre ->
                    val actif = i == onglet
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (actif) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                            .curseurMain().clickable { onglet = i }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            titre,
                            color = if (actif) tokens.textPrimary else tokens.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BORD_MENU))
            if (sources.isEmpty()) {
                Text(
                    if (SourcesVideo.ffmpeg == null) t.ffmpegAbsent else t.rienAPartager,
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sources.size) { i ->
                        val source = sources[i]
                        CaseDePartage(source) {
                            onFermer()
                            AppelBureau.basculerPartage(source)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BORD_MENU))
            BarreReglagesPartage(
                onFichier = {
                    onFermer()
                    portee.launch {
                        choisirFichier(fenetre?.awt)?.let {
                            AppelBureau.basculerPartage(SourceVideo.Fichier(it.absolutePath))
                        }
                    }
                },
            )
        }
    }
}

/** one cell: the preview, then the name. the image arrives when it arrives */
@Composable
private fun CaseDePartage(source: SourceVideo, onChoisir: () -> Unit) {
    val tokens = LocalTokens.current
    var apercu by remember(source) { mutableStateOf(ApercusSources.connu(source)) }
    // live, at a rate that depends on what it costs. screens and windows both recapture through
    // the jdk now, so twice a second goes unnoticed. only a camera or a film still wants an ffmpeg
    // per shot, and those wait their turn
    val periode = if (SourcesVideo.cadre(source) != null) 500L else 2_000L
    LaunchedEffect(source) {
        if (apercu == null) apercu = ApercusSources.prendre(source)
        while (true) {
            kotlinx.coroutines.delay(periode)
            ApercusSources.rafraichir(source)?.let { apercu = it }
        }
    }
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).curseurMain().clickable(onClick = onChoisir)
            .padding(4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            apercu?.let {
                androidx.compose.foundation.Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: Text(textes().chargement, color = tokens.textMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icone(
                when (source) {
                    is SourceVideo.Entree -> Icones.camera
                    is SourceVideo.Ecran -> Icones.partageEcran
                    else -> Icones.salon
                },
                tint = tokens.textSecondary,
                taille = 14.dp,
                description = null,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                source.libelle,
                color = tokens.textPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * the bottom bar: the quality, the sound, and the film
 *
 * the sound is only offered when there is something to take it from. on windows the desktop's sound
 * is not a directshow input, it wants a loopback device, stereo mix where a card exposes it, or a
 * virtual cable. having none does not give a greyed button but a sentence saying what to install,
 * and a film carries its own sound and needs nothing
 */
@Composable
private fun BarreReglagesPartage(onFichier: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.qualiteDuPartage, color = tokens.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            QualitePartage.entries.forEach { q ->
                val actif = q == ReglagesPartage.qualite
                Box(
                    Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (actif) DORE.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                        .curseurMain().clickable { ReglagesPartage.qualite = q; ReglagesPartage.retenir() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        q.libelle,
                        color = if (actif) DORE else tokens.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                t.unFichierVideo,
                color = tokens.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .curseurMain().clickable(onClick = onFichier)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.partagerLeSon, color = tokens.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            if (!CaptureWgc.disponible) {
                Text(t.sonSansGstreamer, color = tokens.textMuted, fontSize = 12.sp)
            } else {
                SonDuPartage.entries.forEach { mode ->
                    val actif = mode == ReglagesPartage.son
                    Box(
                        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (actif) DORE.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                            .curseurMain().clickable { ReglagesPartage.son = mode; ReglagesPartage.retenir() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            when (mode) {
                                SonDuPartage.AUCUN -> t.aucun
                                SonDuPartage.FENETRE -> t.sonDeLaFenetre
                                SonDuPartage.SYSTEME -> t.sonDuPcSaufVelvet
                            },
                            color = if (actif) DORE else tokens.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (ReglagesPartage.son == SonDuPartage.FENETRE) {
            Spacer(Modifier.height(6.dp))
            // said here rather than discovered during a call: the entry exists for a window, and
            // picking a whole screen with it selected has to resolve to something
            Text(t.sonDeLaFenetreAide, color = tokens.textMuted, fontSize = 11.sp)
        }
    }
}

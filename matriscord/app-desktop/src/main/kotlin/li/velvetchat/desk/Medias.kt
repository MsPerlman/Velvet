package li.velvetchat.desk

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.velvetchat.core.model.MediaKind
import li.velvetchat.core.model.MessageBody
// a wildcard import of the shared package: the theme shortcuts (`tokens.textPrimary`, `tokens.espL`
// and the rest) are extension properties, which only resolve when imported by name. listing them
// one by one would be twenty five import lines for one idea
import li.velvetchat.app.ui.*

/**
 * a media in the desktop's conversation, and the first time the skia decoder sees real data
 *
 * until now the jvm `actual` of the decoding seam was only exercised by its tests: a gif built by
 * `ImageIO`, an orange png. this component runs it on what comes off the server, encrypted images,
 * the gifs people send, screenshots 3000 pixels wide
 *
 * the animated decode goes through the cap ([creneauAnime]) and the still one through the cache.
 * the same policy as the phone, and for the same reason: an animated decode carries its playback
 * state so it cannot be shared, a still image can
 *
 * what it does not do yet: video, audio, pdf, which is the seam attached to no batch. they show in
 * plain text with their name rather than as an empty cell
 */
@Composable
fun ImageDeMessage(corps: MessageBody.Media) {
    val tokens = LocalTokens.current
    val moteur = LocalEngine.current
    val ref = corps.media

    if (corps.kind == MediaKind.AUDIO) {
        Column(Modifier.padding(top = 4.dp)) {
            BulleVocale(corps)
            TranscriptionVocal(corps)
        }
        return
    }
    if (corps.kind != MediaKind.IMAGE) {
        Text(
            "${corps.caption} — ${corps.kind.name.lowercase()}",
            color = tokens.textSecondary,
            fontSize = 14.sp,
        )
        return
    }

    var fixe by remember(ref.source) { mutableStateOf(cacheMedia(ref.source)) }
    var anime by remember(ref.source) { mutableStateOf<ImageDecodee?>(null) }
    val tourne = creneauAnime(ref.source)

    LaunchedEffect(ref.source) {
        if (fixe != null || anime != null) return@LaunchedEffect
        val octets = runCatching { moteur.mediaBytes(ref) }.getOrNull() ?: return@LaunchedEffect
        val decode = withContext(Dispatchers.Default) {
            decoderPourAffichage(octets)
        } ?: return@LaunchedEffect
        if (decode.animee) {
            anime = decode
        } else {
            decode.bitmapFixe?.let {
                mettreEnCacheMedia(ref.source, it)
                fixe = it
            }
        }
    }

    // the space is reserved from the dimensions the event carries, before the bytes even arrive:
    // without that the row would be the height of its text then jump when the image lands, moving
    // whatever somebody is reading
    val large = (ref.width ?: 400).coerceAtMost(400)
    val haut = ref.height?.let { h ->
        val l = ref.width ?: return@let 300
        (h.toFloat() / l * large).toInt()
    } ?: 300

    Column(Modifier.padding(top = 4.dp)) {
        Box(
            Modifier.widthIn(max = 400.dp)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .let { if (fixe == null && anime == null) it.heightIn(min = haut.dp) else it },
        ) {
            val decode = anime
            val bitmap = fixe
            when {
                decode != null -> DessinDecode(
                    decode,
                    ContentScale.Fit,
                    Modifier.widthIn(max = 400.dp)
                        .heightIn(max = (decode.hauteur.toFloat() / decode.largeur * 400).dp),
                    anime = tourne,
                )

                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = corps.caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = 400.dp),
                )
            }
        }
        corps.caption.takeIf { it.isNotBlank() && it != ref.filename }?.let {
            Text(it, color = tokens.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * "show the transcript" under a voice message, to read it without listening. the same gateway as
 * the phone ([transcrireVocal], `Dictee.kt`), a full pass on the nas. on demand, kept for the
 * length of the session
 */
@Composable
private fun TranscriptionVocal(corps: MessageBody.Media) {
    val t = textes()
    val tokens = LocalTokens.current
    val moteur = LocalEngine.current
    val source = corps.media.source
    var texte by remember(source) { mutableStateOf(transcriptions[source]) }
    var etat by remember(source) { mutableStateOf(t.voirLaTranscription) }
    val portee = androidx.compose.runtime.rememberCoroutineScope()
    val connu = texte
    if (connu != null) {
        Text(
            connu.ifBlank { t.aucuneParoleDetectee },
            color = if (connu.isBlank()) tokens.textMuted else tokens.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.widthIn(max = 520.dp).padding(top = 2.dp),
        )
        return
    }
    val libre = etat != t.transcriptionEnCours
    Text(
        etat,
        color = tokens.accent,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 2.dp).curseurMain()
            .clickable(enabled = libre) {
                etat = t.transcriptionEnCours
                portee.launch {
                    runCatching {
                        val octets = moteur.mediaBytes(corps.media)
                        transcrireVocal(moteur.currentSession().accessToken, octets)
                    }.onSuccess { transcriptions[source] = it; texte = it }
                        .onFailure { etat = t.transcriptionImpossibleReessayer }
                }
            },
    )
}

private val transcriptions = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * a voice message's bubble: play, waveform, duration, the phone's shape (`BulleVocale`,
 * `Vocal.kt`). the wave is first derived from the address, stable and meaningless, then the real
 * one, computed on the sound once it has been decoded, and the played part turns accent coloured
 */
@Composable
private fun BulleVocale(corps: MessageBody.Media) {
    val t = textes()
    val tokens = LocalTokens.current
    val moteur = LocalEngine.current
    val source = corps.media.source
    val portee = androidx.compose.runtime.rememberCoroutineScope()
    var son by remember(source) { mutableStateOf(sonsDecodes[source]) }
    var enLecture by remember(source) { mutableStateOf(false) }
    var chargement by remember(source) { mutableStateOf(false) }
    var position by remember(source) { mutableStateOf(0L) }
    var echec by remember(source) { mutableStateOf(false) }
    val duree = son?.dureeMs ?: corps.media.durationMs ?: 0L
    val onde = remember(source, son) {
        son?.let { amplitudes(it, 40) } ?: kotlin.random.Random(source.hashCode()).let { a -> List(40) { 0.25f + a.nextFloat() * 0.75f } }
    }
    androidx.compose.runtime.DisposableEffect(source) { onDispose { if (enLecture) LecteurVocal.arreter() } }

    fun lancer(s: SonDecode) {
        enLecture = true
        LecteurVocal.jouer(
            s,
            depuisMs = if (position >= s.dureeMs) 0 else position,
            onPosition = { position = it },
            onFin = { enLecture = false; if (position >= s.dureeMs - 60) position = 0 },
        )
    }

    Row(
        Modifier.clip(RoundedCornerShape(tokens.radiusLarge)).background(tokens.surfaceRaised)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (echec) tokens.textMuted else tokens.accent)
                .curseurMain().clickable {
                    if (enLecture) { LecteurVocal.arreter(); return@clickable }
                    son?.let { lancer(it); return@clickable }
                    chargement = true
                    portee.launch {
                        val decode = runCatching {
                            val octets = moteur.mediaBytes(corps.media)
                            withContext(Dispatchers.Default) { decoderVocal(octets) }
                        }.getOrNull()
                        chargement = false
                        if (decode == null) { echec = true; return@launch }
                        sonsDecodes[source] = decode
                        son = decode
                        lancer(decode)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (chargement) Text("…", color = Color.White, fontSize = 14.sp)
            else Icone(
                if (enLecture) Icones.pause else Icones.lecture,
                tint = Color.White,
                taille = 15.dp,
                description = if (echec) t.formatAudioNonSupporte else if (enLecture) t.pause else t.lire,
            )
        }
        Spacer(Modifier.width(10.dp))
        val joue = if (duree > 0) (position.toFloat() / duree).coerceIn(0f, 1f) else 0f
        Row(
            Modifier.height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            onde.forEachIndexed { i, a ->
                Box(
                    Modifier.width(2.dp).height((3 + a * 23).dp).clip(RoundedCornerShape(1.dp))
                        .background(if (i < joue * onde.size) tokens.accent else tokens.textMuted),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            formatDuree(if (enLecture || position > 0) position else duree),
            color = tokens.textSecondary,
            fontSize = 12.sp,
        )
    }
}

/** `0:07`, the shape every audio player uses */
private fun formatDuree(ms: Long) = "${ms / 60_000}:${(ms / 1000 % 60).toString().padStart(2, '0')}"

/** each slice's peak, normalised against the loudest: what the eye reads as "the voice" */
private fun amplitudes(son: SonDecode, barres: Int): List<Float> {
    val pas = 2 * son.canaux
    val echantillons = son.pcm.size / pas
    if (echantillons == 0) return List(barres) { 0f }
    val cretes = List(barres) { b ->
        val debut = b * echantillons / barres
        val fin = maxOf(debut + 1, (b + 1) * echantillons / barres)
        var max = 0
        var k = debut
        while (k < fin) {
            val v = (son.pcm[k * pas].toInt() and 0xFF) or (son.pcm[k * pas + 1].toInt() shl 8)
            max = maxOf(max, kotlin.math.abs(v))
            k += 8
        }
        max.toFloat()
    }
    val plus = cretes.max().coerceAtLeast(1f)
    return cretes.map { it / plus }
}

private val sonsDecodes = java.util.concurrent.ConcurrentHashMap<String, SonDecode>()

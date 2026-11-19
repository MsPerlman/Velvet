package li.velvetchat.app.ui

import android.media.MediaRecorder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** a finished recording, ready to go */
data class Enregistrement(
    val fichier: File,
    val dureeMs: Long,
    val waveform: List<Float>,
)

/**
 * the voice message recorder
 *
 * it replaces the mic button that had done nothing for days, exactly the dead button the emoji
 * picker argued against
 *
 * format: ogg/opus. that is what the other matrix clients expect, and it is also the lightest at
 * equal voice quality. android's `.m4a` would pass the upload but would show as an attachment for
 * those who cannot read it
 *
 * the waveform is sampled during the recording, at a fixed interval, and not recomputed afterwards.
 * two reasons: rereading the file to analyse it would mean decoding it entirely, and above all it
 * is that same sampling that makes the bar move before your eyes, the data shown being the data
 * that will be transmitted, not an approximation
 */
@Composable
fun BarreEnregistrement(
    onAnnuler: () -> Unit,
    onEnvoyer: (Enregistrement) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val contexte = androidx.compose.ui.platform.LocalContext.current

    var duree by remember { mutableStateOf(0L) }
    var amplitudes by remember { mutableStateOf(listOf<Float>()) }
    var erreur by remember { mutableStateOf<String?>(null) }

    val fichier = remember {
        File(contexte.cacheDir.resolve("vocal").apply { mkdirs() }, "voix-${System.currentTimeMillis()}.ogg")
    }
    val enregistreur = remember { mutableStateOf<MediaRecorder?>(null) }

    // the recording starts with the bar, with no second gesture: the bar only appears because you
    // touched the mic, and asking "ready?" again would lose the first two seconds of what you meant
    // to say
    LaunchedEffect(Unit) {
        val rec = runCatching {
            @Suppress("DEPRECATION")
            val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(contexte)
            else MediaRecorder()
            r.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioSamplingRate(48_000)
                setAudioEncodingBitRate(24_000)
                setOutputFile(fichier.absolutePath)
                prepare()
                start()
            }
        }.getOrElse {
            erreur = t.microIndisponible
            null
        }
        enregistreur.value = rec
        if (rec == null) return@LaunchedEffect

        // one sample every 100 ms: fine enough for the bar to live, spaced enough not to produce a
        // thousand values over a minute of recording
        //
        // `elapsedRealtime` and not `currentTimeMillis`
        //
        // the second is wall clock time: it jumps when the system resyncs on ntp or changes
        // timezone. measuring a duration with it means accepting that a four second recording
        // announces thirty when the clock realigns at the wrong moment. `elapsedRealtime` is
        // monotonic, a counter since boot that nothing moves back
        val debut = android.os.SystemClock.elapsedRealtime()
        while (enregistreur.value != null) {
            delay(100)
            duree = android.os.SystemClock.elapsedRealtime() - debut
            val brut = runCatching { rec.maxAmplitude }.getOrDefault(0)
            // `maxAmplitude` goes up to 32767. a square root rather than a division: the ear
            // perceives volume logarithmically, and a linear scale gives a nearly flat bar where
            // you can nonetheless hear that it is not silent
            amplitudes = (amplitudes + kotlin.math.sqrt(brut / 32767f).coerceIn(0f, 1f))
                .takeLast(64)
        }
    }

    // a guaranteed stop: if the screen disappears without going through either button, the mic
    // would stay open and the file would grow indefinitely
    DisposableEffect(Unit) {
        onDispose {
            runCatching { enregistreur.value?.stop() }
            runCatching { enregistreur.value?.release() }
            enregistreur.value = null
        }
    }

    fun arreter(): Boolean {
        val rec = enregistreur.value ?: return false
        enregistreur.value = null
        return runCatching { rec.stop(); rec.release() }.isSuccess
    }

    Row(
        Modifier.fillMaxWidth().background(tokens.surface)
            .padding(horizontal = tokens.espM, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (erreur != null) {
            Text(erreur!!, color = tokens.danger, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                t.fermer,
                color = tokens.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onAnnuler).padding(tokens.espS),
            )
            return@Row
        }

        // the bin cancels, and it is the only destructive gesture here: no confirmation, because a
        // recording in progress has cost nothing yet, and a confirmation dialog would go on
        // recording while you read it
        Text(
            "🗑",
            fontSize = 18.sp,
            modifier = Modifier
                .clickable {
                    arreter()
                    runCatching { fichier.delete() }
                    onAnnuler()
                }
                .padding(6.dp),
        )
        Box(Modifier.width(10.dp))

        // the blinking red dot: without it, nothing tells "i am recording" from "i left a bar open"
        var visible by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            while (true) { delay(600); visible = !visible }
        }
        Box(
            Modifier.size(9.dp).clip(CircleShape)
                .background(if (visible) tokens.danger else Color.Transparent)
        )
        Box(Modifier.width(10.dp))

        Text(
            duree.enMinutesSecondes(),
            color = tokens.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(Modifier.width(12.dp))

        FormeOnde(amplitudes, Modifier.weight(1f).height(28.dp))
        Box(Modifier.width(12.dp))

        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tokens.accent)
                .clickable {
                    if (!arreter()) { onAnnuler(); return@clickable }
                    // a half second recording is a missed tap, not a message: sending it would give
                    // an empty bubble at the other end
                    if (duree < 700 || !fichier.isFile || fichier.length() == 0L) {
                        runCatching { fichier.delete() }
                        onAnnuler()
                    } else {
                        // the duration sent comes from the file, not from the stopwatch
                        //
                        // the stopwatch measures the time spent on the recording screen, and the
                        // file contains what was actually captured. the two diverge as soon as the
                        // mic is interrupted without the loop stopping, going to the background, an
                        // incoming call, the system cutting the mic. seen on screen: a bubble
                        // announcing 0:50 for a few seconds of speech, and i had put that down to
                        // context rather than fixing it
                        //
                        // the file is the only source that cannot lie about what it contains. the
                        // stopwatch stays the fallback, for the case where the metadata reader
                        // cannot read the container
                        onEnvoyer(
                            Enregistrement(fichier, dureeReelle(fichier) ?: duree, amplitudes)
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icone(Icones.envoyer, tint = Color.White, taille = 18.dp, description = t.envoyer)
        }
    }
}

/**
 * the waveform, as vertical bars
 *
 * it scrolls right to left, the latest values pushing the earlier ones. that is the reading
 * direction of a recording in progress: what was just said is at the edge that moves
 */
@Composable
fun FormeOnde(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    couleur: Color? = null,
) {
    val tokens = LocalTokens.current
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        amplitudes.forEach { valeur ->
            Box(
                Modifier.width(2.dp)
                    // a minimum height: a zero pixel bar leaves a hole in the line, and a silence
                    // in the middle of a message is not an absence of message
                    .height((3 + valeur * 25).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(couleur ?: tokens.accent)
            )
        }
    }
}

/** `0:07`, never `7 s`: that is the shape you read on every audio player */
fun Long.enMinutesSecondes(): String {
    val total = this / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * a voice message bubble, in the timeline
 *
 * play, waveform, duration. the protocol transmits the waveform with the message, but our model
 * does not bring it up yet, so we draw one derived from the medium's address, stable and
 * meaningless. it is a lie taken on and said, a voice bubble with no wave looking like an
 * attachment, and the real one will come when `MediaRef` carries it
 *
 * reading without listening (like instagram): "show transcript" sends the file to the nas's
 * dictation gateway, which transcribes it in one full pass. on request only, every voice message
 * shown would otherwise occupy the graphics card, and kept for the session
 *
 * one player at a time for the whole app: two voice messages overlapping would be inaudible, and it
 * is the first thing you do by mistake while browsing a conversation
 */
@Composable
fun BulleVocale(ref: li.velvetchat.core.model.MediaRef) {
    val t = textes()
    val tokens = LocalTokens.current
    val engine = LocalEngine.current
    val contexte = androidx.compose.ui.platform.LocalContext.current
    var enLecture by remember(ref.source) { mutableStateOf(false) }
    var chargement by remember(ref.source) { mutableStateOf(false) }

    val onde = remember(ref.source) {
        val alea = kotlin.random.Random(ref.source.hashCode())
        List(28) { 0.25f + alea.nextFloat() * 0.75f }
    }

    DisposableEffect(ref.source) {
        onDispose { if (lecteurActif?.first == ref.source) arreterLecture() }
    }

    var transcription by remember(ref.source) { mutableStateOf(transcriptions[ref.source]) }
    var transcriptionEnCours by remember(ref.source) { mutableStateOf(false) }
    val scopeTexte = androidx.compose.runtime.rememberCoroutineScope()
    Column {
        Row(
            Modifier.clip(RoundedCornerShape(tokens.radiusLarge))
                .background(tokens.surfaceRaised)
                .padding(horizontal = tokens.espM, vertical = tokens.espS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(tokens.accent)
                    .clickable {
                        if (enLecture) {
                            arreterLecture()
                            enLecture = false
                            return@clickable
                        }
                        chargement = true
                        scope.launch {
                            val chemin = runCatching { engine.mediaFile(ref) }.getOrNull()
                            chargement = false
                            if (chemin == null) return@launch
                            arreterLecture()
                            enLecture = lireFichier(chemin, ref.source) { enLecture = false }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (chargement) {
                    Text("…", color = Color.White, fontSize = 14.sp)
                } else {
                    Icone(
                        if (enLecture) Icones.pause else Icones.lecture,
                        tint = Color.White,
                        taille = 15.dp,
                        description = if (enLecture) t.pause else t.lire,
                    )
                }
            }
            Box(Modifier.width(10.dp))
            FormeOnde(
                onde,
                Modifier.width(120.dp).height(24.dp),
                couleur = if (enLecture) tokens.accent else tokens.textMuted,
            )
            Box(Modifier.width(10.dp))
            Text(
                (ref.durationMs ?: 0L).enMinutesSecondes(),
                color = tokens.textSecondary,
                fontSize = 12.sp,
            )
        }
        val texte = transcription
        when {
            texte != null -> Text(
                texte.ifBlank { t.aucuneParoleDetectee },
                color = if (texte.isBlank()) tokens.textMuted else tokens.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.widthIn(max = 280.dp).padding(start = tokens.espS, top = 4.dp),
            )
            else -> Text(
                if (transcriptionEnCours) t.transcriptionEnCours else t.voirLaTranscription,
                color = tokens.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = tokens.espS, top = 4.dp)
                    .clickable(enabled = !transcriptionEnCours) {
                        transcriptionEnCours = true
                        scopeTexte.launch {
                            val resultat = runCatching {
                                val chemin = engine.mediaFile(ref)
                                li.velvetchat.app.Dictee.transcrireVocal(
                                    li.velvetchat.core.Installation.ou({ this.dictee }, li.velvetchat.app.BuildConfig.DICTEE_URL),
                                    engine.currentSession().accessToken,
                                    File(chemin),
                                )
                            }
                            transcriptionEnCours = false
                            resultat.onSuccess { transcriptions[ref.source] = it; transcription = it }
                            resultat.onFailure {
                                android.widget.Toast.makeText(contexte, t.transcriptionImpossible, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
            )
        }
    }
}

/**
 * the transcripts already obtained, by medium address: we do not ask the nas again on coming back
 */
private val transcriptions = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * the single player, and the source it is playing
 *
 * a file scope object rather than a compose state: it has to survive recomposition and be stoppable
 * from any bubble, including one that is not playing
 */
private var lecteurActif: Pair<String, android.media.MediaPlayer>? = null

private fun arreterLecture() {
    lecteurActif?.second?.let { runCatching { it.stop() }; runCatching { it.release() } }
    lecteurActif = null
}

private fun lireFichier(chemin: String, source: String, onFin: () -> Unit): Boolean =
    runCatching {
        android.media.MediaPlayer().apply {
            setDataSource(chemin)
            setOnCompletionListener { arreterLecture(); onFin() }
            prepare()
            start()
            lecteurActif = source to this
        }
        true
    }.getOrDefault(false)

/**
 * an audio file's real duration, read from its metadata
 *
 * `null` when the container does not carry it or the reader cannot open it, in which case the
 * caller falls back on its stopwatch. we do not throw: an approximate duration beats a voice
 * message we refuse to send
 */
private fun dureeReelle(fichier: java.io.File): Long? = runCatching {
    android.media.MediaMetadataRetriever().use { lecteur ->
        lecteur.setDataSource(fichier.absolutePath)
        lecteur.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            // zero means "i could not tell", not "the file is empty": an empty file has already
            // been ruled out by the size check just before sending
            ?.takeIf { it > 0 }
    }
}.getOrNull()

package li.velvetchat.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * how long a soundboard sound is allowed to run
 *
 * six seconds, and the bound is the feature rather than a limit on it. a sound effect fired
 * mid conversation lands on top of somebody talking: past a few seconds it stops being a
 * reaction and becomes an interruption you cannot take back. discord holds the same line, and
 * anyone who has been in a call with a thirty second clip knows why
 *
 * it is also what makes the catalogue cheap. six seconds of 48 kHz mono pcm is 576 ko, so a
 * whole soundboard weighs less than one photo, and the phone caches all of it
 */
const val SON_DUREE_MAX_MS = 6_000

/**
 * the shortest a sound is allowed to be
 *
 * a tenth of a second, which is about the shortest thing that still reads as a sound rather
 * than as a click. the bound is not there to police anybody's taste: it is there because the
 * handles can be dragged onto each other, and a window of four samples uploads a file that
 * plays as nothing at all and looks like a bug in the soundboard
 */
const val SON_DUREE_MIN_MS = 100

/**
 * decoded audio, as the editor needs to see it
 *
 * mono 16 bit, which is what the mixer plays anyway: the soundboard adds itself to the mic's
 * stream, and the capture is mono. folding here rather than at playback means the waveform
 * drawn is the waveform that will be heard
 *
 * @param pcm the samples themselves, signed 16 bit
 * @param frequence samples per second, kept from the source rather than resampled: the mixer
 *   handles the rate, and resampling to draw a picture would be work thrown away
 */
data class EchantillonsAudio(val pcm: ShortArray, val frequence: Int) {
    val dureeMs: Int get() = if (frequence <= 0) 0 else (pcm.size * 1000L / frequence).toInt()

    // `ShortArray` compares by identity, so a data class holding one gets equality wrong in a
    // way nobody notices until a `remember` key stops firing. the file we came from is not in
    // the type, so size and rate are what we have, and they are enough to tell two loads apart
    override fun equals(other: Any?) = other is EchantillonsAudio &&
        pcm.size == other.pcm.size && frequence == other.frequence
    override fun hashCode() = 31 * pcm.size + frequence
}

/**
 * what a platform has to be able to do for the editor to exist there
 *
 * a seam and not an `expect`/`actual`, for the same reason as [ChoisirFichier]: the module is
 * shared and knows no platform, and the two implementations have nothing in common beyond
 * their signature. android decodes with `MediaCodec`, which reads whatever the device reads
 *
 * `null` when the file cannot be decoded, which is a normal answer and not a failure: people
 * pick a `.txt` by accident, and codecs differ from one phone to the next
 */
fun interface DecodeurAudio {
    suspend fun decoder(chemin: String): EchantillonsAudio?
}

/**
 * writes the kept window back out as a file ready to upload
 *
 * wav, and that deserves a word since it is the unfashionable answer. we already hold the
 * decoded pcm, so a wav is a 44 byte header in front of bytes we have: no encoder, no
 * container library, nothing to get wrong, and it plays on every platform without asking.
 * encoding to opus would save four fifths of the size and would need an encoder on android and
 * an ogg muxer on the desktop, which is precisely the chantier that has kept voice messages
 * off the desktop for weeks. six seconds of mono is 576 ko, and the sound is uploaded once and
 * cached per device
 */
fun interface EncodeurWav {
    suspend fun ecrire(pcm: ShortArray, frequence: Int): String?
}

/**
 * pick the passage that matters, out of a file that lasts longer
 *
 * ## why an editor at all
 *
 * the sound people want is almost never the start of the file. it is the two seconds of a
 * three minute clip, and without a way to say which two, the only route is to open an audio
 * editor elsewhere, export, come back. that route is the reason a soundboard stays empty
 *
 * ## two handles, and the middle
 *
 * the window has a start and an end, both dragged, and the band between them dragged as a
 * whole. six seconds is a **ceiling**, not a template: most sounds worth firing are under two,
 * and a window that always took six would bolt half a second of silence onto every one of them
 *
 * pulling one handle past the other is prevented rather than allowed and then corrected: a
 * window that flips inside out, even for one frame, draws a negative band and reads as a
 * glitch. and the pair cannot close below a tenth of a second, which is the shortest thing
 * that still sounds like a sound
 *
 * the grab zones overlap the edges by a finger's width. exact edges would be unhittable on a
 * phone, and the cost of being generous is that a tap near an edge resizes instead of moving,
 * which is the more likely intent anyway
 *
 * the waveform is drawn from the samples rather than from a level meter, so the shape you aim
 * at is the sound you get. a flat stretch is silence, and being able to see it is most of what
 * makes the aim possible
 */
@Composable
fun EditeurDeSon(
    echantillons: EchantillonsAudio,
    onFenetre: (debut: Int, fin: Int) -> Unit,
    modifier: Modifier = Modifier,
    onEcouter: ((debut: Int, fin: Int) -> Unit)? = null,
) {
    val t = textes()
    val tokens = LocalTokens.current

    val total = echantillons.pcm.size
    val maximum = echelleMs(echantillons.frequence, SON_DUREE_MAX_MS)
    val minimum = min(total, echelleMs(echantillons.frequence, SON_DUREE_MIN_MS))

    // the two bounds, in samples, and not a position plus a length. the pair is what the caller
    // wants back and what the canvas draws, so keeping it in that shape means no conversion
    // anywhere in between, and no rounding drift from one gesture to the next
    var debut by remember(echantillons) { mutableIntStateOf(0) }
    var fin by remember(echantillons) { mutableIntStateOf(min(total, maximum)) }
    // which of the three zones the current gesture grabbed, decided once when the finger lands.
    // deciding it per frame would let a resize turn into a move halfway through, as soon as the
    // edge caught up with the finger
    var saisie by remember(echantillons) { mutableStateOf(Saisie.AUCUNE) }

    // the envelope, computed once per file and not per frame. a soundboard clip is a few
    // hundred thousand samples and the strip is a couple of hundred pixels wide: walking the
    // whole array on every drag frame is the one thing that would make this feel heavy
    val enveloppe = remember(echantillons) { enveloppeDe(echantillons.pcm, BARRES) }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t.choisirLePassage,
                color = tokens.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            // the kept duration, live, next to the file's. it moves while you drag, which is
            // what makes the ceiling teachable: you find out you are at six seconds because the
            // number stops going up, not because a message told you afterwards
            Text(
                t.surDuree(
                    dureeLisible(fin - debut, echantillons.frequence),
                    dureeLisible(total, echantillons.frequence),
                ),
                color = if (fin - debut >= maximum) tokens.accent else tokens.textMuted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(6.dp))

        Box(
            Modifier.fillMaxWidth().height(76.dp)
                .clip(RoundedCornerShape(tokens.radiusSmall))
                .background(tokens.surface)
                .pointerInput(echantillons) {
                    detectHorizontalDragGestures(
                        // the zone is decided where the finger lands and then held for the whole
                        // gesture. the grab radius is in pixels here because that is what the
                        // pointer speaks; `PRISE_DP` says what it means
                        onDragStart = { depart ->
                            val largeur = size.width.toFloat()
                            val prise = PRISE_DP.dp.toPx()
                            val xd = largeur * debut / max(1, total)
                            val xf = largeur * fin / max(1, total)
                            saisie = when {
                                abs(depart.x - xd) <= prise -> Saisie.DEBUT
                                abs(depart.x - xf) <= prise -> Saisie.FIN
                                depart.x in xd..xf -> Saisie.BANDE
                                // outside the window entirely: treat it as moving the band
                                // there, since the alternative is a gesture that does nothing
                                // and on a long file most of the strip is outside
                                else -> Saisie.BANDE
                            }
                        },
                        onDragEnd = { saisie = Saisie.AUCUNE },
                        onDragCancel = { saisie = Saisie.AUCUNE },
                    ) { change, delta ->
                        change.consume()
                        val largeur = size.width.toFloat()
                        if (largeur <= 0f) return@detectHorizontalDragGestures
                        // pixels to samples, through the strip which always shows the whole file
                        val pas = (delta / largeur * total).roundToInt()
                        when (saisie) {
                            // each handle is bounded by the other, never past it: the window
                            // cannot flip, and it cannot close below the minimum
                            Saisie.DEBUT -> debut = (debut + pas)
                                .coerceIn(max(0, fin - maximum), fin - minimum)
                            Saisie.FIN -> fin = (fin + pas)
                                .coerceIn(debut + minimum, min(total, debut + maximum))
                            // the band keeps its length and slides. clamping the start alone
                            // would stretch it against the far edge instead of stopping it
                            Saisie.BANDE -> {
                                val longueur = fin - debut
                                val d = (debut + pas).coerceIn(0, max(0, total - longueur))
                                debut = d
                                fin = d + longueur
                            }
                            Saisie.AUCUNE -> Unit
                        }
                        onFenetre(debut, fin)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(76.dp)) {
                val largeur = size.width
                val hauteur = size.height
                val pas = largeur / enveloppe.size
                val milieu = hauteur / 2f

                enveloppe.forEachIndexed { i, niveau ->
                    val x = i * pas
                    // a sample index for this bar, to know whether it falls inside the window.
                    // the strip shows the whole file and the window is painted over it, rather
                    // than the strip showing only the window: you cannot aim at what is not drawn
                    val echantillon = (i.toLong() * total / enveloppe.size).toInt()
                    val dedans = echantillon in debut until fin
                    val h = max(1.5f, niveau * (hauteur - 10f) / 2f)
                    drawRect(
                        color = if (dedans) tokens.accent else tokens.textMuted.copy(alpha = 0.35f),
                        topLeft = Offset(x, milieu - h),
                        size = Size(max(1f, pas - 1f), h * 2),
                    )
                }

                // the handles, and they have to look grabbable rather than merely mark a
                // boundary. a hairline says "the colour changes here", a bar with a grip says
                // "pull me", and the difference decides whether anybody discovers the resize
                val xd = largeur * debut / max(1, total)
                val xf = largeur * fin / max(1, total)
                drawRect(tokens.accent, Offset(xd, 0f), Size(LARGEUR_POIGNEE, hauteur))
                drawRect(tokens.accent, Offset(xf - LARGEUR_POIGNEE, 0f), Size(LARGEUR_POIGNEE, hauteur))
                // two notches per handle, at mid height. it is the sign every trim control uses,
                // and it costs four rectangles
                listOf(xd + LARGEUR_POIGNEE / 2f, xf - LARGEUR_POIGNEE / 2f).forEach { x ->
                    for (dy in listOf(-4f, 2f)) {
                        drawRect(
                            tokens.bg,
                            Offset(x - 1.5f, milieu + dy),
                            Size(3f, 2f),
                        )
                    }
                }
            }
        }

        if (onEcouter != null) {
            Spacer(Modifier.height(6.dp))
            // listening before saving, because a waveform tells you where a sound is and never
            // what it is. this is the only way to know the window caught the word and not the
            // breath before it
            Text(
                t.ecouterLaSelection,
                color = tokens.accent,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(tokens.radiusSmall))
                    .clickable { onEcouter(debut, fin) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * the window, brought back inside what is allowed
 *
 * the guarantee lives here and not in the editor, and that is the point: both bounds have to
 * hold for a caller that never showed the editor, for a window left over from a previous file,
 * and for a `fin` somebody passed smaller than `debut`. a bound enforced only by a composable
 * is a bound that a refactor removes without anybody noticing
 *
 * both, because the ceiling and the floor fail differently. past six seconds a sound talks over
 * people; under a tenth of a second it uploads a file that plays as nothing, which reads as the
 * soundboard being broken rather than as the window being too tight
 *
 * @return the samples to keep, or an empty range when there is nothing to keep
 */
fun bornesDecoupe(total: Int, frequence: Int, debut: Int, fin: Int): IntRange {
    if (total <= 0 || frequence <= 0) return IntRange.EMPTY
    val maximum = echelleMs(frequence, SON_DUREE_MAX_MS)
    val minimum = echelleMs(frequence, SON_DUREE_MIN_MS)
    val d = debut.coerceIn(0, total)
    val f = fin.coerceIn(d, minOf(total, d + maximum))
    if (f <= d) return IntRange.EMPTY
    // a window under the floor is stretched rather than refused, and to the right first: the
    // start is where somebody aimed, the end is where they stopped pulling. only when there is
    // no room to the right does the start move, which is the case of a handle dragged onto the
    // very end of the file
    if (f - d >= minimum) return d until f
    if (total < minimum) return 0 until total
    val fin2 = minOf(total, d + minimum)
    return (fin2 - minimum) until fin2
}

/** how many bars the strip is drawn with. enough to show a gap, few enough to stay cheap */
private const val BARRES = 120

/**
 * the loudest sample in each slice, normalised
 *
 * the peak and not the average: an average flattens a short percussive sound into nothing, and
 * a short percussive sound is most of what ends up on a soundboard. what we want to see is
 * where something happens
 */
private fun enveloppeDe(pcm: ShortArray, barres: Int): FloatArray {
    if (pcm.isEmpty()) return FloatArray(barres)
    val sortie = FloatArray(barres)
    var sommet = 1
    for (i in 0 until barres) {
        val debut = (i.toLong() * pcm.size / barres).toInt()
        val fin = min(pcm.size, ((i + 1).toLong() * pcm.size / barres).toInt())
        var pic = 0
        for (j in debut until fin) {
            val v = abs(pcm[j].toInt())
            if (v > pic) pic = v
        }
        sortie[i] = pic.toFloat()
        if (pic > sommet) sommet = pic
    }
    // normalised against the file's own peak rather than against 32767: a quiet recording would
    // otherwise draw as a flat line, and a flat line is exactly what you cannot aim at
    for (i in sortie.indices) sortie[i] = sortie[i] / sommet
    return sortie
}

/** which part of the window a gesture took hold of, decided when the finger lands */
private enum class Saisie { AUCUNE, DEBUT, FIN, BANDE }

/** how close to an edge counts as grabbing it. a finger, not a pixel */
private const val PRISE_DP = 18

/** the drawn width of a handle. wide enough to read as an object, narrow enough not to hide the sound */
private const val LARGEUR_POIGNEE = 4f

/** a duration in milliseconds, as a number of samples at this file's rate */
private fun echelleMs(frequence: Int, ms: Int): Int =
    (frequence.toLong() * ms / 1000).toInt().coerceAtLeast(1)

/** `0:07` past the minute, `3,2 s` below it: a sound effect is read in tenths, a file in minutes */
private fun dureeLisible(echantillons: Int, frequence: Int): String {
    if (frequence <= 0) return "?"
    val ms = echantillons * 1000L / frequence
    if (ms < 60_000) {
        val dixiemes = (ms + 50) / 100
        return "${dixiemes / 10},${dixiemes % 10} s"
    }
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

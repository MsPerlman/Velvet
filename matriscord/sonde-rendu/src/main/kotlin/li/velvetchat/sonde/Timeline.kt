package li.velvetchat.sonde

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

sealed interface Ligne {
    data class Texte(
        val auteur: String,
        val couleurAuteur: Color,
        val corps: AnnotatedString,
        val entete: Boolean,
        val reactions: List<Pair<String, Int>>,
    ) : Ligne

    data class Image(val bitmap: ImageBitmap, val auteur: String, val entete: Boolean) : Ligne

    data class Anime(val trames: List<ImageBitmap>, val auteur: String) : Ligne

    data class AnimeVive(val codec: org.jetbrains.skia.Codec) : Ligne

    data class Systeme(val texte: String) : Ligne

    data class Jour(val date: String) : Ligne
}

private val AUTEURS = listOf(
    "camille" to Color(0xFF5B8DEF),
    "camille" to Color(0xFF4FBF8B),
    "maman" to Color(0xFFE07BA0),
    "papa" to Color(0xFFD97757),
    "julie" to Color(0xFF8B7BD8),
)

private val MOTS = (
    "le la les un une des et ou mais donc car pour avec sans dans sur sous chez " +
        "message salon serveur vocal appel photo lien fichier emoji reaction reponse " +
        "aujourd hui demain hier matin soir week end vacances maison voiture train"
    ).split(" ")

private val EMOJIS = listOf("😂", "👍", "❤️", "🔥", "😭", "🎉", "👀", "🙏")

fun timelineDeTest(
    combien: Int,
    images: List<ImageBitmap>,
    animation: List<ImageBitmap>,
): List<Ligne> {
    val alea = Random(20260909)
    val lignes = ArrayList<Ligne>(combien)
    var auteurCourant = 0
    var depuisEntete = 99

    while (lignes.size < combien) {
        if (depuisEntete > 0 && alea.nextInt(4) == 0) {
            auteurCourant = alea.nextInt(AUTEURS.size)
            depuisEntete = 0
        } else {
            depuisEntete++
        }
        val (nom, couleur) = AUTEURS[auteurCourant]
        val entete = depuisEntete == 0

        lignes += when (alea.nextInt(100)) {
            in 0 until 62 -> Ligne.Texte(
                auteur = nom,
                couleurAuteur = couleur,
                corps = corpsRiche(alea),
                entete = entete,
                reactions = if (alea.nextInt(5) == 0) {
                    List(1 + alea.nextInt(3)) {
                        EMOJIS.random(alea) to (1 + alea.nextInt(9))
                    }
                } else emptyList(),
            )

            in 62 until 76 -> Ligne.Image(images[alea.nextInt(images.size)], nom, entete)
            in 76 until 82 -> Ligne.Anime(animation, nom)
            in 82 until 94 -> Ligne.Systeme("$nom a rejoint le salon")
            else -> Ligne.Jour("${1 + alea.nextInt(28)} septembre 2026")
        }
    }
    return lignes
}

fun timelineAdverse(combien: Int): List<Ligne> {
    val alea = Random(20260909)
    return List(combien) { i ->
        Ligne.Texte(
            auteur = AUTEURS[i % AUTEURS.size].first,
            couleurAuteur = AUTEURS[i % AUTEURS.size].second,
            corps = corpsRiche(alea, motsMin = 60, motsMax = 60, styleTousLesMots = true),
            entete = i % 4 == 0,
            reactions = List(4) { EMOJIS.random(alea) to (1 + alea.nextInt(99)) },
        )
    }
}

private fun corpsRiche(
    alea: Random,
    motsMin: Int = 3,
    motsMax: Int = 43,
    styleTousLesMots: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    val mots = motsMin + alea.nextInt((motsMax - motsMin).coerceAtLeast(1))
    repeat(mots) { i ->
        val mot = MOTS.random(alea)
        when (if (styleTousLesMots) alea.nextInt(6) else alea.nextInt(14)) {
            0 -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(mot) }
            1 -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(mot) }
            2 -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF1B2626)),
            ) { append(mot) }
            3 -> withStyle(
                SpanStyle(
                    color = Color(0xFF1FB6A8),
                    background = Color(0xFF1FB6A8).copy(alpha = 0.16f),
                    fontWeight = FontWeight.Medium,
                ),
            ) { append("@$mot") }
            4 -> withStyle(
                SpanStyle(color = Color(0xFF1FB6A8), textDecoration = TextDecoration.Underline),
            ) { append("https://exemple.org/$mot") }
            5 -> append(EMOJIS.random(alea))
            else -> append(mot)
        }
        if (i < mots - 1) append(' ')
    }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyle(
    style: SpanStyle,
    bloc: () -> Unit,
) {
    val i = pushStyle(style)
    bloc()
    pop(i)
}

@Composable
fun LigneVue(ligne: Ligne, trame: Int) {
    when (ligne) {
        is Ligne.Texte -> Message(ligne.auteur, ligne.couleurAuteur, ligne.entete) {
            Text(
                ligne.corps,
                color = Color(0xFFE6EDED),
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            if (ligne.reactions.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp)) {
                    ligne.reactions.forEach { (emoji, n) ->
                        Row(
                            Modifier
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1B2626))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(emoji, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("$n", color = Color(0xFF9AAEAE), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        is Ligne.Image -> Message(ligne.auteur, Color(0xFF5B8DEF), ligne.entete) {
            Image(
                ligne.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        is Ligne.Anime -> Message(ligne.auteur, Color(0xFFE07BA0), entete = true) {
            Image(
                ligne.trames[trame % ligne.trames.size],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        is Ligne.AnimeVive -> {
            val bitmap = androidx.compose.runtime.remember(ligne.codec) {
                org.jetbrains.skia.Bitmap().apply { allocPixels(ligne.codec.imageInfo) }
            }
            val image = androidx.compose.runtime.remember(trame) {
                ligne.codec.readPixels(bitmap, trame % ligne.codec.frameCount)
                org.jetbrains.skia.Image.makeFromBitmap(bitmap).toComposeImageBitmap()
            }
            Message("adverse", Color(0xFFE0575B), entete = true) {
                Image(
                    image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = 400.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }

        is Ligne.Systeme -> Text(
            ligne.texte,
            color = Color(0xFF5F7373),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 56.dp, top = 2.dp, bottom = 2.dp),
        )

        is Ligne.Jour -> Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF243232)))
            Text(
                ligne.date,
                color = Color(0xFF5F7373),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Box(Modifier.weight(1f).height(1.dp).background(Color(0xFF243232)))
        }
    }
}

@Composable
private fun Message(
    auteur: String,
    couleur: Color,
    entete: Boolean,
    contenu: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (entete) 12.dp else 2.dp, start = 12.dp, end = 12.dp),
    ) {
        if (entete) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(couleur),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    auteur.take(2).uppercase(),
                    color = Color(0xFF0E1414),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Spacer(Modifier.width(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (entete) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(auteur, color = couleur, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text("Aujourd'hui à 13:37", color = Color(0xFF5F7373), fontSize = 11.sp)
                }
            }
            contenu()
        }
    }
}

package li.velvetchat.sonde

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlin.system.exitProcess

private const val LIGNES = 500

private const val PAS_PX = 24f

private const val ECHAUFFEMENT = 60

private const val TRAMES_MESUREES = 600

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Velvet — sonde de rendu (lot 0)",
        state = rememberWindowState(width = 900.dp, height = 1000.dp),
    ) {
        Sonde { rapport ->
            println(rapport)
            Thread {
                Thread.sleep(5_000)
                exitProcess(0)
            }.start()
        }
    }
}

@Composable
private fun Sonde(surRapport: (String) -> Unit) {
    val monde = remember {
        val images = imagesDeTest(12)
        val animation = animationDeTest()
        Monde(
            images = images,
            animation = animation,
            realiste = timelineDeTest(LIGNES, images, animation),
            adverse = timelineAdverse(LIGNES),
            vive = codecDeTest().let { codec -> List(LIGNES) { Ligne.AnimeVive(codec) } },
        )
    }

    val etat = rememberLazyListState()
    var trame by remember { mutableIntStateOf(0) }
    var passeCourante by remember { mutableIntStateOf(0) }
    var rapport by remember { mutableStateOf<String?>(null) }

    val programme = remember(monde) {
        listOf(
            monde.realiste to Passe(
                nom = "Passe 1 — charge realiste",
                charge = "$LIGNES lignes heterogenes (62 % texte a spans, 14 % images, " +
                    "6 % animations), ${monde.images.size} images decodees",
                durees = DoubleArray(0),
            ),
            monde.adverse to Passe(
                nom = "Passe 2 — charge adverse : le texte a son maximum",
                charge = "$LIGNES lignes, 60 mots chacune, un changement de style a CHAQUE " +
                    "mot, 4 reactions sous chaque message",
                durees = DoubleArray(0),
            ),
            monde.vive to Passe(
                nom = "Passe 3 — animation decodee a la volee",
                charge = "$LIGNES lignes animees SANS cache de trames " +
                    "(${poidsAnimation() / 1024} Ko de GIF, ${monde.animation.size} trames) — " +
                    "l'implementation naive que le lot 2 doit trancher",
                durees = DoubleArray(0),
            ),
        )
    }

    LaunchedEffect(monde) {
        val resultats = ArrayList<Pair<Passe, Lecture>>()

        programme.forEachIndexed { index, (_, modele) ->
            passeCourante = index
            etat.scrollToItem(0)

            val durees = DoubleArray(TRAMES_MESUREES)
            var precedent = 0L
            var i = 0
            var vues = 0

            while (i < TRAMES_MESUREES) {
                withFrameNanos { maintenant ->
                    trame++
                    if (precedent != 0L && vues >= ECHAUFFEMENT) {
                        durees[i++] = (maintenant - precedent) / 1_000_000.0
                    }
                    precedent = maintenant
                    vues++
                }
                if (etat.canScrollForward) etat.scrollBy(PAS_PX) else etat.scrollToItem(0)
            }

            val passe = modele.copy(durees = durees)
            resultats += passe to lire(durees)
        }

        rapport = redigerRapport(resultats)
        surRapport(rapport!!)
    }

    val lignes = programme[passeCourante].first

    Box(Modifier.fillMaxSize().background(Color(0xFF0E1414))) {
        LazyColumn(Modifier.fillMaxSize(), state = etat) {
            items(lignes) { ligne -> LigneVue(ligne, trame) }
        }
        rapport?.let {
            Box(
                Modifier.fillMaxWidth().background(Color(0xF2141C1C)).padding(16.dp),
            ) {
                Text(it, color = Color(0xFFE6EDED), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private data class Monde(
    val images: List<androidx.compose.ui.graphics.ImageBitmap>,
    val animation: List<androidx.compose.ui.graphics.ImageBitmap>,
    val realiste: List<Ligne>,
    val adverse: List<Ligne>,
    val vive: List<Ligne>,
)

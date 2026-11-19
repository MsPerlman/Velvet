package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import li.velvetchat.core.model.CalqueCadre
import li.velvetchat.core.model.DecorationAvatar
import li.velvetchat.core.model.FAMILLE_CADRES

/**
 * the profile frame, drawn around the card it belongs to (place it with `matchParentSize` over the
 * card)
 *
 * the layout is the reference's, read off its client's stylesheet rather than guessed from
 * screenshots. a frame is measured for a card 1200 wide, so on a card of width L everything scales
 * by L / 1200, and the frame's own box is the card widened by its side overhang, `debordCote`, on
 * each side. every layer takes that full width and its own height from its shape
 *
 * - `staple`, anchored top: hangs `debordHaut` above the card. anchored bottom: `debordBas` below
 * - `rail`: the card's height, one image fitted to the width and pinned at its anchor, cropped
 * - `border`: the card's height, the image repeated downwards, cropped
 *
 * the card never shrinks for its frame: the frame overhangs it. this is what the earlier version
 * got backwards, pulling the card in by the overhang so that nothing stuck out, which left two
 * empty strips beside the card and a crown floating in a reserved gap. it also drew every centre
 * layer as a nine slice measured on one frame, and every top layer as a staple, so the rails of
 * twenty one frames climbed out above the card
 *
 * [derriere] draws the back layers, called before the card
 */
@Composable
fun CadreDeProfil(
    haut: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    derriere: Boolean = false,
    /**
     * asking for thumbnails on that side rather than the original layers
     *
     * a frame is four images of 1312 by 2528, so a grid of sixty one frames asked for two hundred
     * and forty of them at full size, to be decoded at once. on a hundred point cell a 256 px
     * thumbnail is indistinguishable from the original and weighs fifty times less
     */
    vignettePx: Int? = null,
) {
    val engine = LocalEngine.current
    LaunchedEffect(Unit) { chargerCatalogue(engine) }
    // nothing until we know: a frame is described by the catalogue, the profile only carries its
    // top layer's address, and half a wrong frame is worse than a late one
    val e = connus[haut]
    if (e == null && !catalogueLu) return
    // a frame the catalogue does not describe, imported by hand: its single image as a top staple
    val calques = (e?.calques ?: emptyList()).ifEmpty {
        listOfNotNull(
            CalqueCadre(haut, false, "top", 1312, 538, "staple"),
            e?.bas?.let { CalqueCadre(it, false, "bottom", 1312, 328, "staple") },
        )
    }.filter { it.derriere == derriere }
    if (calques.isEmpty()) return
    val debordHaut = e?.debordHaut ?: 126
    val debordBas = e?.debordBas ?: 116
    val debordCote = e?.debordCote ?: 56

    BoxWithConstraints(modifier) {
        val k = maxWidth / 1200f
        val largeur = maxWidth + k * (2f * debordCote)
        val carte = maxHeight
        for (c in calques) {
            val hauteur = largeur * (c.hauteur.toFloat() / c.largeur)
            val image = @Composable { EmojiEnLigne(c.mxc, contentScale = ContentScale.FillBounds, taillePx = vignettePx) }
            when (c.genre) {
                "staple" -> Box(
                    Modifier.calque(
                        if (c.ancre == "bottom") carte + k * debordBas.toFloat() - hauteur else -k * debordHaut.toFloat(),
                        largeur,
                        hauteur,
                    ),
                ) { image() }
                "rail" -> Box(Modifier.calque(0.dp, largeur, carte).clipToBounds()) {
                    val y = when (c.ancre) {
                        "top" -> 0.dp
                        "bottom" -> carte - hauteur
                        else -> (carte - hauteur) / 2
                    }
                    Box(Modifier.calque(y, largeur, hauteur)) { image() }
                }
                // border: as many copies as the card needs, plus one, like the reference
                else -> Box(Modifier.calque(0.dp, largeur, carte).clipToBounds()) {
                    val n = if (hauteur.value <= 0f) 1 else kotlin.math.ceil(carte / hauteur).toInt() + 1
                    repeat(n) { i -> Box(Modifier.calque(hauteur * i.toFloat(), largeur, hauteur)) { image() } }
                }
            }
        }
    }
}

/**
 * exactly [largeur] by [hauteur], centred across, [y] from the top of the area
 *
 * not `requiredSize` plus `offset`: a child larger than its parent gets centred on it by compose,
 * so a layer taller than the card was silently shifted up by half the difference. that was most of
 * why the tall rails sat so high
 */
private fun Modifier.calque(y: Dp, largeur: Dp, hauteur: Dp) = layout { mesurable, contraintes ->
    val p = mesurable.measure(androidx.compose.ui.unit.Constraints.fixed(largeur.roundToPx(), hauteur.roundToPx()))
    layout(contraintes.maxWidth, contraintes.maxHeight) {
        p.place((contraintes.maxWidth - p.width) / 2, y.roundToPx())
    }
}

/**
 * the top part's address, the key the profile carries, mapped to the catalogue entry
 *
 * an observable map: the catalogue arrives afterwards, and an ordinary `mutableMapOf` would
 * recompose nothing, so the frame would sit on its fallback until the next restart
 */
private val connus = androidx.compose.runtime.mutableStateMapOf<String, DecorationAvatar>()

/**
 * has the catalogue been read at least once?
 *
 * tells "this frame does not exist" from "we do not know yet", which `connus[mxc] == null`
 * conflated, and that conflation is what drew the fallback too early. observable, so the frames
 * already on screen redraw as soon as the answer arrives
 */
private var catalogueLu by mutableStateOf(false)

/**
 * reading the catalogue no longer dies with the card that asked for it
 *
 * this is the second half of the broken frame bug: they stopped showing when clicking a user in a
 * room or in the member rail. `toutesLesDecorations` does one state read per server, several
 * seconds on an account with many. it lived in the card's `LaunchedEffect`, so closing the card
 * before it finished cancelled it, `connus` stayed empty, and a global guard then forbade any new
 * attempt for ten seconds. a card you open, look at and close, which is to say the normal use of a
 * member rail, therefore never had time to get a frame, and neither did the next one
 *
 * the read now lives in its own shared scope: the first card starts it, the following ones latch
 * onto the same work, and closing a card does not interrupt it
 *
 * an empty result is not kept as an answer. `toutesLesDecorations` catches its errors server by
 * server and returns an empty list rather than a failure, so "empty" means both "nothing to show"
 * and "everything failed". we retry, gently
 */
private val porteeCatalogue = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
)
private var lecture: kotlinx.coroutines.Job? = null

private fun chargerCatalogue(engine: li.velvetchat.core.MatrixEngine) {
    if (catalogueLu || lecture?.isActive == true) return
    lecture = porteeCatalogue.launch {
        while (!catalogueLu) {
            runCatching { engine.toutesLesDecorations(FAMILLE_CADRES) }
                .getOrNull()?.takeIf { it.isNotEmpty() }?.let { lus ->
                    lus.forEach { connus[it.mxc] = it }
                    catalogueLu = true
                }
            if (!catalogueLu) kotlinx.coroutines.delay(5_000)
        }
    }
}


/**
 * how far a frame sticks out of a card [largeurCarte] wide: its sides, its top, its bottom
 *
 * the room to leave around the card, never to take out of it. a floating card keeps its width
 * and moves by this much, like the reference's popout which adds the side and bottom overhang as
 * a margin. all zero with no frame, and until the catalogue is read
 */
@Composable
fun debordsDuCadre(mxc: String?, largeurCarte: Dp): androidx.compose.foundation.layout.PaddingValues {
    val e = mxc?.let { connus[it] } ?: return androidx.compose.foundation.layout.PaddingValues(0.dp)
    val k = largeurCarte / 1200f
    return androidx.compose.foundation.layout.PaddingValues(
        start = k * e.debordCote.toFloat(),
        end = k * e.debordCote.toFloat(),
        top = k * e.debordHaut.toFloat(),
        bottom = k * e.debordBas.toFloat(),
    )
}

/**
 * the inset that fits a card and its whole frame inside an area [largeurZone] wide
 *
 * only for a card that cannot overhang its area, the dm sidebar and the editor's preview. the card
 * is then `L * 1200 / (1200 + 2 * debordCote)` wide and its frame fills exactly L
 */
@Composable
fun margesDuCadre(mxc: String?, largeurZone: Dp): androidx.compose.foundation.layout.PaddingValues {
    val cote = mxc?.let { connus[it] }?.debordCote ?: return androidx.compose.foundation.layout.PaddingValues(0.dp)
    return debordsDuCadre(mxc, largeurZone * (1200f / (1200f + 2f * cote)))
}

/**
 * a frame shown on a miniature card, like the reference's shop
 *
 * the grids used to put it round a 64 point avatar on the phone, or show it flat and alone in a
 * cell on the desktop. either way you only saw a ribbon, the top half of an object drawn to border
 * a card
 *
 * the dummy card keeps the real one's proportions, 1200 wide for 900 tall in the frame's units, and
 * leaves room all around for the overhangs, without which the cell would crop what sticks out,
 * which is precisely what makes a frame
 */
@Composable
fun ApercuCadre(mxc: String, modifier: Modifier = Modifier, vignettePx: Int? = 256) {
    val tokens = LocalTokens.current
    BoxWithConstraints(modifier) {
        // the card's width: what is left once the side overhangs are taken off the width
        val carteLargeur = maxWidth * (1200f / (1200f + 2 * 56f))
        val k = carteLargeur / 1200f
        val carteHauteur = carteLargeur * 0.75f
        Box(
            Modifier.align(Alignment.Center).requiredSize(carteLargeur, carteHauteur),
        ) {
            CadreDeProfil(mxc, Modifier.matchParentSize(), derriere = true, vignettePx = vignettePx)
            Column(
                Modifier.matchParentSize()
                    .clip(RoundedCornerShape(k * 48f))
                    .background(tokens.surface),
            ) {
                Box(Modifier.fillMaxWidth().weight(0.38f).background(tokens.surfaceRaised))
                Row(
                    Modifier.fillMaxWidth().weight(0.62f).padding(k * 60f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.requiredSize(k * 220f).clip(CircleShape).background(tokens.border))
                    Column(Modifier.padding(start = k * 50f)) {
                        Box(Modifier.requiredSize(k * 330f, k * 60f).clip(RoundedCornerShape(k * 30f)).background(tokens.border))
                        Box(
                            Modifier.padding(top = k * 40f)
                                .requiredSize(k * 220f, k * 45f)
                                .clip(RoundedCornerShape(k * 25f)).background(tokens.border.copy(alpha = 0.6f)),
                        )
                    }
                }
            }
            CadreDeProfil(mxc, Modifier.matchParentSize(), vignettePx = vignettePx)
        }
    }
}

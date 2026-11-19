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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import li.velvetchat.app.ui.*
import li.velvetchat.core.gif.FournisseurGif
import li.velvetchat.core.gif.Gif
import li.velvetchat.core.gif.Klipy

/**
 * the desktop's gif tab
 *
 * same rule as the phone: no provider until one has been chosen. opening the tab must not, on its
 * own, tell a third party anything. you turn it on with a click, and that gets remembered. the
 * klipy key comes from `local.properties` at launch (`velvet.klipy`) and is never committed
 */
object GifsBureau {
    var actif by mutableStateOf(Reglages.lire("gif.klipy") == "oui")
        private set

    val cle: String? get() = li.velvetchat.core.Installation.ou({ this.gifs }, System.getProperty("velvet.klipy").orEmpty()).takeIf { it.isNotBlank() }

    fun activer(oui: Boolean) {
        Reglages.ecrire("gif.klipy", if (oui) "oui" else "non")
        actif = oui
    }
}

/** klipy without ads, through the jdk, the same request as the phone's `ClientKlipy` */
private class KlipyBureau(private val cle: String) : FournisseurGif {
    override val nom = "KLIPY"

    override suspend fun tendances(page: Int): List<Gif> =
        appeler(Klipy.cheminTendances(cle, page, FournisseurGif.PAR_PAGE))

    override suspend fun chercher(requete: String, page: Int): List<Gif> =
        appeler(Klipy.cheminRecherche(cle, requete, page, FournisseurGif.PAR_PAGE))

    private suspend fun appeler(url: String): List<Gif> = withContext(Dispatchers.IO) {
        runCatching {
            val cnx = (java.net.URI.create(url).toURL().openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (cnx.responseCode != 200) emptyList()
                else Klipy.lire(cnx.inputStream.bufferedReader().readText(), sansPublicite = true)
            } finally {
                cnx.disconnect()
            }
        }.getOrDefault(emptyList())
    }
}

/** the reference's gold, the border of the pickers' search fields */
internal val DORE = Color(0xFFC9AB56)

@Composable
fun PanneauGifBureau(onChoisir: (Gif) -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    val cle = GifsBureau.cle

    if (cle == null || !GifsBureau.actif) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 36.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icone(Icones.gif, tint = tokens.textMuted, taille = 34.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (cle == null) t.aucuneCleKlipyBureau
                else t.klipyExplicationBureau,
                color = tokens.textMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            if (cle != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    t.utiliserKlipy,
                    color = tokens.bg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(DORE)
                        .curseurMain().clickable { GifsBureau.activer(true) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        return
    }

    val fournisseur = remember(cle) { KlipyBureau(cle) }
    var requete by remember { mutableStateOf("") }
    var resultats by remember { mutableStateOf<List<Gif>?>(null) }
    LaunchedEffect(requete) {
        if (requete.isNotBlank()) delay(FournisseurGif.DELAI_FRAPPE_MS)
        resultats = if (requete.isBlank()) fournisseur.tendances() else fournisseur.chercher(requete.trim())
    }

    Column(Modifier.fillMaxSize()) {
        // the reference's field: gold border, radius 8
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(40.dp)
                .border(1.dp, DORE, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(Icones.recherche, tint = tokens.textSecondary, taille = 16.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (requete.isEmpty()) Text(t.rechercherSurKlipy, color = tokens.textMuted, fontSize = 14.sp)
                BasicTextField(
                    value = requete,
                    onValueChange = { requete = it },
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(DORE),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            if (requete.isBlank()) t.tendancesKlipy else t.rechercheEnvoyeeAKlipy,
            color = tokens.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 18.dp, bottom = 6.dp),
        )
        val liste = resultats
        when {
            liste == null -> Text(t.chargement, color = tokens.textMuted, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
            liste.isEmpty() -> Text(t.aucunGif, color = tokens.textMuted, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
            // the reference's mosaic: two columns, radius 6
            else -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            ) {
                items(liste, key = { it.id }) { gif -> VignetteGif(gif) { onChoisir(gif) } }
            }
        }
    }
}

@Composable
private fun VignetteGif(gif: Gif, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    var decode by remember(gif.apercu) { mutableStateOf<ImageDecodee?>(null) }
    LaunchedEffect(gif.apercu) {
        val octets = octetsDistants(gif.apercu) ?: return@LaunchedEffect
        decode = withContext(Dispatchers.Default) { decoderPourAffichage(octets) }
    }
    val proportion = run {
        val l = gif.largeur ?: decode?.largeur
        val h = gif.hauteur ?: decode?.hauteur
        if (l != null && h != null && l > 0 && h > 0) (l.toFloat() / h).coerceIn(0.5f, 2.5f) else 4f / 3f
    }
    val tourne = creneauAnime(gif.apercu)
    Box(
        Modifier.fillMaxWidth().aspectRatio(proportion)
            .clip(RoundedCornerShape(6.dp))
            .background(tokens.surfaceRaised)
            .curseurMain().clickable(onClick = onClick),
    ) {
        decode?.let { DessinDecode(it, ContentScale.Crop, Modifier.fillMaxSize(), anime = tourne) }
    }
}

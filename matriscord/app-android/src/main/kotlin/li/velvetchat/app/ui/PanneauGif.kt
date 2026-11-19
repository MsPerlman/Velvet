package li.velvetchat.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import li.velvetchat.core.gif.ChoixFournisseur
import li.velvetchat.core.gif.FournisseurGif
import li.velvetchat.core.gif.Gif

/**
 * the gif tab
 *
 * it used to show a waiting pattern. it now shows, depending on the settings:
 *
 *     - no provider chosen, the default case: an explanation and the path to the settings. not an empty
 *       grid, not a load that will never finish
 *     - a provider with no key: the same, saying the key is missing
 *     - a provider ready: the trends on opening, then the search
 *
 * the trends on opening and not an empty grid: a request with no keyword says nothing other than
 * "this address opened the gif tab". it is what makes the tab usable without typing, at the lowest
 * price, and the only request we make that nobody asked for
 */
@Composable
fun PanneauGif(
    onChoisir: (Gif) -> Unit,
    onReglages: () -> Unit,
    /** the search took focus, and the caller uses that to unfold the panel */
    onChercher: () -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current
    val contexte = LocalContext.current

    // keyed on `PrefsGif.revision`: without it, choosing a provider in the settings tab changed
    // nothing here until you had left and reopened the panel
    val revision = PrefsGif.revision
    val fournisseur = remember(revision) { fournisseurCourant(contexte) }

    if (fournisseur == null) {
        GifSansCle(onReglages)
        return
    }

    var requete by remember { mutableStateOf("") }
    var resultats by remember { mutableStateOf<List<Gif>>(emptyList()) }
    var chargement by remember { mutableStateOf(true) }
    var vide by remember { mutableStateOf(false) }

    // one request per typing pause, not one per character
    //
    // without this delay, "pizza" becomes five requests, `p`, `pi`, `piz` and so on, so five data
    // points at the third party where one is enough. this is not an interface comfort, it is a
    // reduction of what we disclose, and that is why the delay is a constant of the contract and
    // not a number dropped in here
    LaunchedEffect(requete) {
        chargement = true
        vide = false
        if (requete.isNotBlank()) delay(FournisseurGif.DELAI_FRAPPE_MS)
        val trouves = if (requete.isBlank()) fournisseur.tendances()
        else fournisseur.chercher(requete.trim())
        resultats = trouves
        chargement = false
        vide = trouves.isEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espM, vertical = tokens.espS)
                .background(tokens.surfaceRaised, RoundedCornerShape(tokens.radiusMedium))
                .padding(horizontal = tokens.espM, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(Icones.recherche, tint = tokens.textMuted, taille = 17.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (requete.isEmpty()) {
                    Text(
                        t.chercherGifSur(fournisseur.nom),
                        color = tokens.textMuted,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = requete,
                    onValueChange = { requete = it },
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) onChercher() },
                )
            }
            if (requete.isNotEmpty()) {
                Icone(
                    Icones.fermer,
                    tint = tokens.textMuted,
                    taille = 16.dp,
                    description = t.effacer,
                    modifier = Modifier.clickable { requete = "" }.padding(start = 6.dp),
                )
            }
        }

        // the provider's name is written, small, permanently. it is not a legal notice: it is the
        // only thing that reminds you, at the moment you type, that what you type goes somewhere
        Text(
            if (requete.isBlank()) t.tendancesDe(fournisseur.nom)
            else t.rechercheEnvoyeeA(fournisseur.nom),
            color = tokens.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = tokens.espL, bottom = tokens.espXs),
        )

        when {
            chargement && resultats.isEmpty() -> MessageGif(t.chargement)
            vide && requete.isNotBlank() -> MessageGif(t.rienPour(requete.trim()))
            vide -> MessageGif(
                t.aucuneTendance
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = tokens.espS),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(resultats, key = { it.id }) { gif ->
                    // local `val`s: the properties come from another module, so kotlin refuses the
                    // smart cast, they could in theory change between the test and the read. same
                    // reason as `sortant.html` in the composer
                    val l = gif.largeur
                    val h = gif.hauteur
                    Box(
                        Modifier.fillMaxWidth()
                            // the space is reserved before loading. without that the grid jumps on
                            // every image that arrives, and you tap on what has just moved. the
                            // ratio comes from the provider when it gives one, and 4:3 is the
                            // fallback, close to a gif's average
                            .aspectRatio(
                                if (l != null && h != null && h > 0) l.toFloat() / h else 4f / 3f
                            )
                            .clip(RoundedCornerShape(tokens.radiusSmall))
                            .background(tokens.surfaceRaised)
                            .clickable { onChoisir(gif) },
                    ) {
                        // the same rendering as the animated emojis: `ImageDecoder` can produce an
                        // `AnimatedImageDrawable`, and we do not cache an animated decode, see
                        // `EmojiEnLigne` for the reason
                        ImageDistante(gif.apercu, gif.description)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageGif(texte: String) {
    val tokens = LocalTokens.current
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(texte, color = tokens.textMuted, fontSize = 14.sp)
    }
}

/**
 * the default state, and the most important to word well
 *
 * it does not say "unavailable", it says why, and it gives the next step. a tab that settles for
 * being empty suggests a breakage, and this one says a choice is waiting to be made, and which
 */
@Composable
private fun GifSansCle(onReglages: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icone(Icones.gif, tint = tokens.textMuted, taille = 34.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            t.cleKlipyManquante,
            color = tokens.textMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        Text(
            t.ouvrirLesReglages,
            color = tokens.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onReglages).padding(tokens.espS),
        )
    }
}

/**
 * the gif settings: this is where the trade off is made, and it has to be readable
 *
 * two options: "none" is the default, and it is first in the list. a choice is only real if the
 * "no" position is too, and if it is the one you start from
 *
 * the provider carries what it costs, not only its name. choosing without knowing that klipy funds
 * itself through ads mixed into the results is not choosing
 */
@Composable
fun FeuilleReglagesGif(onFermer: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    val contexte = LocalContext.current

    var choix by remember { mutableStateOf(PrefsGif.fournisseur(contexte)) }
    var cle by remember(choix) { mutableStateOf(PrefsGif.cle(contexte, choix).orEmpty()) }
    var pub by remember { mutableStateOf(PrefsGif.publicite(contexte)) }
    var enLigne by remember { mutableStateOf(PrefsGif.gifsEnLigne(contexte)) }

    FeuilleBasse(onFermer = onFermer) {
        Text(
            "GIF",
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espS),
        )
        Text(
            // this text described the reuploading, abandoned since. it promised on screen something
            // the code no longer does. found by going back over the app, and not otherwise: the
            // charter, the site and the email had been fixed, and this string had stayed
            t.rechercheGifExplication,
            color = tokens.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = tokens.espS),
        )

        // this setting comes first, before the provider choice, and deliberately. it concerns
        // everybody, since you receive gifs without ever searching for any, where the provider only
        // concerns those who search
        Row(
            Modifier.fillMaxWidth()
                .clickable { enLigne = !enLigne; PrefsGif.setGifsEnLigne(contexte, enLigne) }
                .padding(horizontal = tokens.espXl, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.afficherGifRecus,
                    color = tokens.textPrimary,
                    fontSize = tokens.typography.bodySize.sp,
                )
                Text(
                    t.gifRecusExplication,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Interrupteur(enLigne)
        }

        // no more provider choice: klipy is the only one, its key is shipped, and the two entry
        // list only left the tab empty until you had found it. what is left, the key and the ads,
        // describes that one service
        Text(
            t.klipyExplication,
            color = tokens.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, top = tokens.espS),
        )

        run {
            Text(
                t.cleDApi,
                color = tokens.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, top = 14.dp, bottom = 6.dp),
            )
            Box(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espXl)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .padding(horizontal = tokens.espM, vertical = tokens.espM)
            ) {
                if (cle.isEmpty()) {
                    Text(
                        t.collezLaCleIci,
                        color = tokens.textMuted,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = cle,
                    onValueChange = { cle = it; PrefsGif.setCle(contexte, choix, it) },
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (choix == ChoixFournisseur.KLIPY) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { pub = !pub; PrefsGif.setPublicite(contexte, pub) }
                    .padding(horizontal = tokens.espXl, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.afficherLesAnnonces,
                        color = tokens.textPrimary,
                        fontSize = tokens.typography.bodySize.sp,
                    )
                    // both halves of the question, said plainly, the contractual and the technical
                    // not overlapping
                    Text(
                        t.annoncesExplication,
                        color = tokens.textMuted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Interrupteur(pub)
            }
        }
    }
}


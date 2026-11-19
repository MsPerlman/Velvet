package li.velvetchat.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.core.gif.ChoixFournisseur
import li.velvetchat.core.gif.FournisseurGif
import li.velvetchat.core.gif.Gif
import li.velvetchat.core.gif.Klipy

/**
 * the gif settings, kept on the device
 *
 * same reasoning as the emoji favourites: which provider we query is a usage profile, and
 * publishing it in the account data would make it readable by the server's administrator. it is not
 * a secret, it is information with no reason to travel
 *
 * the choice is also per device, which is the right grain: you may want search on your phone and
 * not on the children's tablet
 */
object PrefsGif {

    /**
     * the revision counter, and why it exists
     *
     * `SharedPreferences` is not observable by compose: a read inside a `remember` is done once and
     * never comes back. that is exactly the failure reported, choosing a provider in the settings
     * changed nothing in the gif tab, which kept the value read at its first composition. you had
     * to leave the screen and come back for the new `remember` to reread the file, and "you have to
     * refresh the screen" is the exact description of the fault
     *
     * one state integer fixes it: every write increments it, and every reader takes it as its
     * `remember`'s key. no `DataStore`, no flow, a value compose can observe, and the rest of the
     * preferences file unchanged
     */
    var revision by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    private fun change() { revision++ }

    private const val FICHIER = "gifs"
    private const val CLE_FOURNISSEUR = "fournisseur"
    private const val CLE_KLIPY = "cle_klipy"
    private const val CLE_PUB = "publicite"

    private fun prefs(contexte: Context) =
        contexte.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    /**
     * the provider, and there is only one left
     *
     * there was a choice, "none" by default, to be turned on yourself, because there were two
     * possible services and querying a third party without saying so would have decided in the
     * person's place. since then klipy is the only one, its key is shipped, and the setting served
     * nothing but leaving the gif tab empty until you found the right box, so it does nothing given
     * we only use klipy now
     *
     * what is left of the consent is elsewhere, and it is enough: "show received gifs" (that
     * preference decides the involuntary requests) and the tab's written warning, which says where
     * the search goes
     */
    fun fournisseur(contexte: Context): ChoixFournisseur = ChoixFournisseur.KLIPY

    /**
     * the chosen provider's key
     *
     * what is typed on the device beats what is compiled in. the key from `local.properties` is
     * only a convenience default, saving copying sixty four characters on each install. somebody
     * who enters their own has to be able to replace the house's, and not see it ignored with no
     * explanation
     *
     * with no key, the gif tab says so and searches nothing
     */
    fun cle(contexte: Context, choix: ChoixFournisseur): String? = when (choix) {
        ChoixFournisseur.KLIPY -> prefs(contexte).getString(CLE_KLIPY, null)
            ?.takeIf { it.isNotBlank() }
            ?: li.velvetchat.core.Installation.ou({ this.gifs }, li.velvetchat.app.BuildConfig.KLIPY_KEY)

        ChoixFournisseur.AUCUN -> null
    }?.takeIf { it.isNotBlank() }

    fun setCle(contexte: Context, choix: ChoixFournisseur, cle: String) {
        val nom = when (choix) {
            ChoixFournisseur.KLIPY -> CLE_KLIPY
            ChoixFournisseur.AUCUN -> return
        }
        prefs(contexte).edit().putString(nom, cle.trim()).apply()
        change()
    }

    /**
     * show klipy's ads, or not
     *
     * the default is not to show them, and this setting exists so the decision is written somewhere
     * rather than hidden in the code
     *
     * what to know when touching it: klipy's api is free because of those ads. filtering them is a
     * matter for their terms of use, which i could not read, `docs.klipy.com` refusing automated
     * requests. so it is an assumed trade off and not a certainty, and it is the only point of this
     * feature to check before considering it settled
     *
     * the other half of the question is not contractual: an ad in the grid is an image loaded from
     * an ad network, so a fourth party after the provider, and that one's business is profiling
     */
    fun publicite(contexte: Context): Boolean = prefs(contexte).getBoolean(CLE_PUB, false)

    fun setPublicite(contexte: Context, afficher: Boolean) {
        prefs(contexte).edit().putBoolean(CLE_PUB, afficher).apply()
        change()
    }

    /**
     * show received gifs, or show only the link
     *
     * the setting that was missing after the arbitration: a gif goes out as a link, so the message
     * is text, so it showed as a blue link. to see it you have to go and fetch the image from the
     * host at reading time, and that is something you have somebody say yes to, not something
     * obvious
     *
     * what the "yes" position accepts, exactly: the gif provider learns that an ip address opened
     * this conversation, and when. nothing more, it does not know which room it is, nor who is
     * writing. and it cannot be just any host: see `LienGif`, which only allows a closed list,
     * precisely so the sender cannot turn a message into a tracker
     *
     * the default is "yes", unlike the provider choice. the two settings are not the same in
     * nature: choosing a provider opens a disclosure that did not exist, where this one only
     * displays what somebody already sent. and a "no" default would give a feature that does not
     * show, which reads as a breakage, not as a precaution
     */
    fun gifsEnLigne(contexte: Context): Boolean = prefs(contexte).getBoolean(CLE_EN_LIGNE, true)

    fun setGifsEnLigne(contexte: Context, afficher: Boolean) {
        prefs(contexte).edit().putBoolean(CLE_EN_LIGNE, afficher).apply()
        change()
    }

    private const val CLE_EN_LIGNE = "gifs_en_ligne"
}

/**
 * the [PrefsGif.gifsEnLigne] setting, carried down to the message bodies
 *
 * a local rather than a `SharedPreferences` read in each message: the timeline recomposes dozens of
 * bodies on every scroll, and each one would open the preferences file. the shell reads it once and
 * provides it
 */
// `LocalGifsEnLigne` and `GifDuMessage` live in `ui-commun`: the path used `ImageDecoder` directly,
// which is to say half the decoding written a second time next to `EmojiEnLigne`'s. it goes through
// the first seam like everything else

/**
 * klipy's http transport. the decoding is in `core`, see `Klipy`
 *
 * the separation is not cosmetic: it let eleven tests be written on reading the responses before
 * there was an api key, including those of the ad filter, which is the part where a mistake shows
 * in a private conversation
 */
class ClientKlipy(
    private val cle: String,
    private val sansPublicite: Boolean,
) : FournisseurGif {

    override val nom = "KLIPY"

    override suspend fun tendances(page: Int): List<Gif> =
        appeler(Klipy.cheminTendances(cle, page, FournisseurGif.PAR_PAGE))

    override suspend fun chercher(requete: String, page: Int): List<Gif> =
        appeler(Klipy.cheminRecherche(cle, requete, page, FournisseurGif.PAR_PAGE))

    private suspend fun appeler(url: String): List<Gif> = withContext(Dispatchers.IO) {
        runCatching {
            val cnx = (java.net.URI.create(url).toURL().openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                // no identifying header. no token, no device id, no home made `User-Agent`:
                // everything that tells one request from another lets them be stitched into a
                // history. the api key is already in the path, it identifies the app, not the
                // person, and that is the right granularity
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (cnx.responseCode != 200) return@runCatching emptyList()
                Klipy.lire(cnx.inputStream.bufferedReader().readText(), sansPublicite)
            } finally {
                cnx.disconnect()
            }
        }.getOrDefault(emptyList())
    }
}

/** the provider matching the settings, or `null` when none is usable */
fun fournisseurCourant(contexte: Context): FournisseurGif? {
    val choix = PrefsGif.fournisseur(contexte)
    val cle = PrefsGif.cle(contexte, choix) ?: return null
    return when (choix) {
        ChoixFournisseur.KLIPY -> ClientKlipy(cle, sansPublicite = !PrefsGif.publicite(contexte))
        ChoixFournisseur.AUCUN -> null
    }
}

/**
 * a remote image, animated when it is
 *
 * twin of `EmojiEnLigne`, with one difference that counts: this one goes and fetches an `https://`
 * address from a third party, where the other only loads `mxc://` from our server. that is exactly
 * the second layer of the arbitration, browsing the grid tells the host what you looked at, and the
 * separation exists so it is visible in the code rather than drowned
 *
 * the animated decoding is not cached, for the same reason as `EmojiEnLigne`: an
 * `AnimatedImageDrawable` carries its own playback state, and two shared occurrences would be
 * synchronised on the same image
 */
@Composable
fun ImageDistante(url: String, description: String? = null) {
    var fixe by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var anime by remember(url) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(url) {
        val octets = octetsDistants(url) ?: return@LaunchedEffect

        withContext(Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(octets))
                val dessin = runCatching { ImageDecoder.decodeDrawable(source) }.getOrNull()
                if (dessin is AnimatedImageDrawable) {
                    dessin.start()
                    anime = dessin
                    return@withContext
                }
            }
            fixe = runCatching {
                BitmapFactory.decodeByteArray(octets, 0, octets.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    val dessin = anime
    val image = fixe
    // the grid is the worst case: two columns of gifs, all of them decoding
    val tourne = creneauAnime(url)
    LaunchedEffect(dessin, tourne) {
        val a = dessin as? AnimatedImageDrawable ?: return@LaunchedEffect
        if (tourne) a.start() else a.stop()
    }
    when {
        dessin != null -> Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { toile ->
                dessin.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                dessin.draw(toile.nativeCanvas)
            }
        }
        image != null -> Image(
            bitmap = image,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}



/**
 * downloads a gif from its provider, into a temporary file ready to upload
 *
 * why go through a file rather than send the address. a gif sent as a url is not a medium: it
 * carries neither mime type nor dimensions, so it shows at an arbitrary size and without animating.
 * and it leaves in an encrypted room a link every reader will go and fetch from a third party,
 * which teaches that host who reads what and when, exactly what the room's encryption existed to
 * avoid
 *
 * uploaded to us, it becomes an ordinary `m.image`: encrypted like the rest, served by our
 * homeserver, and animated because the mime type says so
 *
 * `null` when the download fails, the caller reporting it rather than sending an empty message
 */
suspend fun telechargerGif(contexte: android.content.Context, url: String): java.io.File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val octets = octetsDistants(url) ?: return@runCatching null
            // the extension matters: it is what `MediaProbe` reads as a second resort to decide the
            // mime type, and `image/gif` is what will make the image animate at the other end
            val fichier = java.io.File.createTempFile("velvet-gif-", ".gif", contexte.cacheDir)
            fichier.writeBytes(octets)
            fichier
        }.getOrNull()
    }

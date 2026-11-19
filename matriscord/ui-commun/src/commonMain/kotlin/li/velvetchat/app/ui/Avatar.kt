package li.velvetchat.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import li.velvetchat.core.model.ProfilEtendu

/**
 * a person's avatar: their picture, or their initials on a colour that belongs to them alone
 *
 * the fallback is not an error state, it is the normal case. on a small server nobody will have
 * put a photo up on day one, and an identical grey circle for everybody would make the conversation
 * unreadable at exactly the moment it matters most. so the coloured initials do the same job as the
 * picture, telling at a glance who is speaking
 *
 * the colour is derived from the matrix id, never drawn at random or handed out in arrival order.
 * two consequences that matter: the same person keeps the same colour from one device and one
 * session to the next, and two people do not swap colours because one of them spoke first
 */
@Composable
fun Avatar(
    userId: String,
    displayName: String?,
    avatarUrl: String?,
    taille: Dp = 40.dp,
    modifier: Modifier = Modifier,
    /**
     * the decoration to draw around it (an `mxc://`), or null
     *
     * it overhangs without taking space: the component keeps exactly [taille] and the ring is drawn
     * over the top, outside the bounds. growing it would shift every list, header and message row an
     * avatar appears in, which is most of the screen, for a decoration
     *
     * animated everywhere, an explicit decision by the owner against the house rule on expensive
     * continuous animation. the cost plainly: one `AnimatedImageDrawable` per avatar on screen,
     * each with its own decode, playback state not being shareable — otherwise two occurrences
     * would synchronise and stopping one would stop the other. forty decorated members is forty
     * render loops, worth watching on a modest phone, and the fallback is the profile screen only
     *
     * it finds itself, and that is where the bug was: the paragraph above promised "everywhere"
     * while the code defaulted to null, so every caller had to remember. three did, the card, the
     * member card and the member drawer, and six did not, timeline and conversation header included
     *
     * the default does it now, from [userId]'s extended profile, which `LocalProfils` already
     * carries for the tags. anything that is not a person is not in there and gets nothing, and a
     * caller passing an explicit null keeps control — which is what the picker thumbnails do,
     * showing a bare avatar to stand for "none"
     */
    decoration: String? = LocalProfils.current[userId]?.decoration,
    /**
     * the shape of the frame. round by default, this being a person's avatar
     *
     * the exception is the server rail, where the reference draws a rounded square that gets
     * squarer on selection. a circle in there left four empty corners, and the server icon did not
     * fill its tile
     */
    forme: androidx.compose.ui.graphics.Shape = CircleShape,
    /**
     * the status to place in the bottom right corner, inside a hole cut out of the avatar, which is
     * how the reference does it: not a coloured disc laid on top but a notch the icon settles into.
     * null means no status shown
     */
    presence: li.velvetchat.core.model.Presence? = null,
    /** "do not disturb" inferred from the global mute, when presence itself says online */
    silence: Boolean = false,
) {
    val engine = LocalEngine.current
    val densite = LocalDensity.current
    val taillePx = with(densite) { taille.roundToPx() }

    var image by remember(avatarUrl) { mutableStateOf(avatarUrl?.let { CacheAvatars.get(it) }) }
    // decided from the bytes, once. see the render below
    var animee by remember(avatarUrl) { mutableStateOf(false) }

    LaunchedEffect(avatarUrl) {
        if (avatarUrl == null || image != null) return@LaunchedEffect
        // the server resizes, so we ask for twice the display size to stay sharp on a dense screen,
        // and nothing more
        val octets = runCatching {
            withContext(Dispatchers.IO) { engine.avatarBytes(avatarUrl, taillePx * 2) }
        }.getOrNull() ?: return@LaunchedEffect
        // animated? decoding would return the first frame and nothing would say there are others.
        // the question gets settled on the header, before decoding
        if (octetsAnimes(octets)) {
            animee = true
            return@LaunchedEffect
        }
        // `Default` and not `IO`: decoding is computation, and `IO` does not exist in common code.
        // `decoderMedia` subsamples on the way through, since a 40 dp avatar has no business
        // keeping a 3000 pixel wide photo in memory
        val decode = withContext(Dispatchers.Default) {
            decoderMedia(octets, taillePx * 2)
        } ?: return@LaunchedEffect
        CacheAvatars.put(avatarUrl, decode)
        image = decode
    }

    // outer box at the nominal size and with no clip: the avatar stays round and the right size,
    // and the decoration can overhang on top without shifting anything. the reference's dot is
    // about 30% of the avatar, in a hole one and a half times its diameter
    val etatVisible = presence != null && presence != li.velvetchat.core.model.Presence.INCONNU
    val pastille = taille * 0.30f
    val trou = pastille * 1.5f

    Box(modifier.size(taille), contentAlignment = Alignment.Center) {
    Box(
        Modifier.size(taille).clip(forme)
            .graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                if (etatVisible) {
                    val r = trou.toPx() / 2f
                    drawCircle(
                        Color.Black,
                        radius = r,
                        center = androidx.compose.ui.geometry.Offset(size.width - r * 0.86f, size.height - r * 0.86f),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                    )
                }
            }
            .background(if (image == null) couleurDe(userId) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        // an animated photo animates. `decoderMedia` returns a still image, the first frame, so a
        // gif avatar sat frozen while its decoration moved: two opposite rules on the same circle.
        // `EmojiEnLigne` is the path that knows how to animate, the decoration's, so we take it for
        // the photo too
        //
        // the cost is the one already accepted for decorations, one render loop per animated avatar
        // on screen. a still photo carries on through the decoded bitmap, which costs nothing to
        // leave up
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = displayName ?: userId,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(taille),
            )
        } else if (animee && avatarUrl != null) {
            // no `taillePx` here: a thumbnail asked of the server is still by construction, it only
            // returns the first frame, which would cancel precisely what we just detected
            Box(Modifier.size(taille)) {
                EmojiEnLigne(avatarUrl, contentScale = ContentScale.Crop)
            }
        } else {
            Text(
                initialesDe(displayName ?: userId),
                // dark text on a coloured tile. the fallback colours are chosen light (see
                // `couleurDe`), so it is the contrast that holds, not luck
                color = Color(0xFF10201F),
                fontWeight = FontWeight.Bold,
                fontSize = (taille.value * 0.36f).sp,
            )
        }
    }

        // over the avatar, at 1.2 times its size, which is the reference's ratio. at 1.25 it looked
        // too big on profiles
        //
        // rendered by [EmojiEnLigne], which has nothing emoji about it but the name: it is the path
        // already written to display an `mxc://` animated when it is animated, falling back to a
        // still image when the animated decode fails or the device predates android 9. writing a
        // second one would only have made a second place to fix the same bugs
        if (decoration != null) {
            // `requiredSize` and not `size`: the outer box imposes its size on its children, so the
            // decoration was being pulled back to the avatar's, it stopped overhanging and looked
            // shifted downwards. `requiredSize` ignores those constraints, and the box then stays
            // centred on the avatar
            Box(Modifier.requiredSize(taille * 1.2f)) {
                EmojiEnLigne(decoration, contentScale = ContentScale.Fit)
            }
        }

        // the status icon, placed in the notch, never over the avatar
        if (etatVisible) {
            IconeEtat(
                presence!!,
                pastille,
                silence,
                Modifier.align(Alignment.BottomEnd).offset(x = -pastille * 0.145f, y = -pastille * 0.145f),
            )
        }
    }
}

/**
 * one or two letters, taken from the display name
 *
 * initials cannot guarantee telling two people apart. the colour carries the identity and the
 * letters help read it. two rules only, and each is justified by what it avoids
 *
 * - several words gives the first letter of the first two ("Marie Dupont" becomes MD). the split is
 *     on the space and nothing else. an earlier version also split on dashes and dots, which gave the
 *     same two letters to two different test accounts: identical tiles for different people
 * - a single word gives the first and last letter, not the first two. two names sharing their start
 *     are far more common than two names sharing start and end, so two accounts differing only in
 *     their final digit come out distinct, where "the first two" would have merged them again
 */
fun initialesDe(nom: String): String {
    val propre = nom.removePrefix("@").substringBefore(':').trim()
    val mots = propre.split(' ').filter { it.isNotBlank() }
    if (mots.size >= 2) return (mots[0].premierSigne() + mots[1].premierSigne()).uppercase()
    val signes = propre.signes()
    return when {
        signes.size >= 2 -> (signes.first() + signes.last()).uppercase()
        signes.size == 1 -> signes[0].uppercase()
        else -> "?"
    }
}

/**
 * the string cut into code points rather than into utf-16 units
 *
 * `first()` and `last()` hand back a `Char`, which is half of anything outside the basic plane.
 * an emoji display name, and people do use them, came out as its two surrogates uppercased: two
 * broken glyphes instead of one picture. `length` lied for the same reason, counting a single
 * emoji as two and sending it down the "first and last letter" branch against itself
 *
 * the known ceiling: a code point is not a grapheme. a family emoji joined by zero width joiners,
 * or a thumb carrying a skin tone, still gets cut at its first code point, so the tile shows the
 * first person or the neutral thumb. kotlin common has no grapheme breaker, and the result is
 * legible rather than broken, which is where this stops
 */
private fun String.signes(): List<String> {
    val sortie = mutableListOf<String>()
    var i = 0
    while (i < length) {
        val pas = if (i + 1 < length && this[i].isHighSurrogate() && this[i + 1].isLowSurrogate()) 2 else 1
        sortie += substring(i, i + pas)
        i += pas
    }
    return sortie
}

/** the first code point, whole */
private fun String.premierSigne(): String =
    if (length >= 2 && this[0].isHighSurrogate() && this[1].isLowSurrogate()) substring(0, 2) else take(1)

/**
 * a stable colour, derived from the id
 *
 * the hue comes from the hash, but the saturation and lightness are fixed. that is what guarantees
 * every tile looks the same intensity and that the dark text on top stays readable on all of them.
 * letting the hash decide the lightness too would sooner or later produce a nearly black tile with
 * black text on it
 */
private fun couleurDe(userId: String): Color {
    val teinte = (abs(userId.hashCode()) % 360).toFloat()
    return Color.hsv(teinte, 0.45f, 0.82f)
}

/**
 * the decoded avatar cache
 *
 * bounded by entry count and not by pixels like the message cache in `Images.kt`. here every entry
 * is the same size by construction, so counting entries is counting memory. a hundred and eighty 80
 * by 80 entries weigh about 4.6 mb
 */
private object CacheAvatars {
    private const val MAX = 180
    private val entrees = LinkedHashMap<String, ImageBitmap>(0, 0.75f, true)
    private val verrou = Verrou()

    fun get(cle: String): ImageBitmap? = verrou.tenir { entrees[cle] }

    fun put(cle: String, image: ImageBitmap) = verrou.tenir {
        entrees[cle] = image
        while (entrees.size > MAX) {
            val plusAncien = entrees.keys.firstOrNull() ?: break
            entrees.remove(plusAncien)
        }
    }
}

/**
 * a profile's gradient
 *
 * two sources, in this order. what the person chose, published in their extended profile, since
 * gradients are customisable rather than derived. then the fallback derived from the id, when they
 * chose nothing: stable, unique per person, with no storage and no upload, and it cannot show a
 * broken image
 *
 * the fallback is not a stopgap, it is what gives everybody something on first open, including the
 * people who will never touch their settings. a grey profile by default would have made
 * customisation a compulsory chore
 *
 * the derived hue reuses the fallback avatar colour's machinery, so the same id gives the same hue
 * everywhere in the app
 */
@Composable
fun degradeDe(userId: String, etendu: ProfilEtendu): Brush {
    val haute = etendu.couleurHaute?.toColor()
    val basse = etendu.couleurBasse?.toColor()
    // the gradient starts from the bottom. `couleurHaute` and `couleurBasse` are the two fields'
    // original names. what the person picks is called primary and secondary on the profile screen,
    // and the reference puts the primary at the bottom, lightening upwards. measured off a voice
    // tile: distinctly more saturated at the bottom than at the top
    //
    // velvet had them the other way round, which inverted every profile on the server
    if (haute != null && basse != null) return Brush.verticalGradient(listOf(basse, haute))

    // the fallback. the id's `hashCode`, mapped onto the hue wheel, with two neighbouring colours
    // rather than opposite ones, otherwise the gradient turns into a rainbow and stops working as a
    // background
    val teinte = (userId.hashCode().toFloat() % 360f + 360f) % 360f
    return Brush.verticalGradient(
        listOf(
            Color.hsl(teinte, 0.55f, 0.42f),
            Color.hsl((teinte + 28f) % 360f, 0.50f, 0.26f),
        )
    )
}

/**
 * are these bytes an animated image?
 *
 * two formats to recognise, and only their header: a gif announces it in its signature, an animated
 * png with an `acTL` chunk, which the spec puts before the first frame, so within the first few
 * kilobytes. we read no further, this is about picking a render path and not about validating the
 * file
 */
private fun octetsAnimes(octets: ByteArray): Boolean {
    if (octets.size < 8) return false
    val gif = octets[0] == 'G'.code.toByte() && octets[1] == 'I'.code.toByte() &&
        octets[2] == 'F'.code.toByte()
    if (gif) return true
    val fin = minOf(octets.size - 4, 64 * 1024)
    for (i in 8 until fin) {
        if (octets[i] == 'a'.code.toByte() &&
            octets[i + 1] == 'c'.code.toByte() &&
            octets[i + 2] == 'T'.code.toByte() &&
            octets[i + 3] == 'L'.code.toByte()
        ) {
            return true
        }
    }
    return false
}

package li.velvetchat.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * the profile effect, whole, overhanging the banner
 *
 * the bug fitted inside one modifier's name
 *
 * reported from outside: profile effects were hidden inside the banner, so nobody saw them, when
 * they are meant to be seen on the profile
 *
 * both screens that draw it laid out a box taller than the banner, 340 points on the profile screen
 * and 220 on the floating card, believing they were overhanging. but `Modifier.height()` respects
 * the parent's constraints: the banner imposes 150, or 76, at most, so the box was silently pulled
 * back to that height. only `requiredHeight` gets past it, and nobody sees the difference reading
 * the code, it is one word and the screen shows no error
 *
 * the overhang is not an ornament: an effect measures 450 by 880. cropped into a wide strip, all
 * that survives is a ribbon taken from the middle, and the middle of a profile effect is usually
 * transparent. "you cannot see them" was literal
 *
 * what it does
 *
 * it takes all the width it is given and the height its shape asks for, without letting the banner
 * crop it. so it comes down over the identity, like on the reference
 *
 * in front and not behind. this note used to say the opposite, that it went under the avatar and
 * the nickname, on the grounds that an effect hiding the name costs more than it gives. the
 * reasoning held and the premise was wrong: the reference puts it in front, identity included. its
 * effects are cut away in the middle, which is exactly where they land, and seeing them over the
 * face and the name is what you are buying by wearing one
 *
 * so the order is not settled here but by the caller, which adds a `Modifier.zIndex(2f)`: the
 * composable stays usable in both places, and the rule is readable where it applies
 */
@Composable
fun EffetDeProfil(mxc: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // the width comes from the constraints and the height from the shape. `aspectRatio` would
        // not do, it would let itself be boxed in by the parent's maximum height too
        Box(Modifier.fillMaxWidth().requiredHeight(maxWidth * RAPPORT_EFFET)) {
            EmojiEnLigne(
                mxc,
                contentScale = ContentScale.FillWidth,
                pauseEntreBouclesMs = PAUSE_ENTRE_PASSAGES_MS,
            )
        }
    }
}

/**
 * 880 over 450, the shape of a profile effect, the catalogue's and the reference's
 *
 * an imported image that differs gets fitted to the width like the others: it will overhang a
 * little or leave a little space, which stays readable. guessing the real shape would mean decoding
 * the image before measuring the page, and a page that waits for an image to know its height jumps
 * under your eyes when that image lands
 */
private const val RAPPORT_EFFET = 880f / 450f

/**
 * the rest between two runs of the effect
 *
 * an effect lasts a second and a half. run back to back it goes past forty times a minute under the
 * eyes of somebody reading a bio, which is simply annoying. spaced out it becomes what it is
 * supposed to be, something that happens now and then
 *
 * twenty seven seconds, which is the reference's interval, asked for by name. it was thirty four,
 * a number picked because it felt about right
 */
private const val PAUSE_ENTRE_PASSAGES_MS = 27_000L

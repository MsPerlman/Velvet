package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.ProfilEtendu
import li.velvetchat.core.model.TagServeur

/**
 * the extended profiles of the people currently on screen, by id
 *
 * provided once for the whole tree, like the role colours: the server tag shows next to every
 * nickname, so resolving it message by message would mean one read per timeline row. empty until
 * something is loaded, since a missing tag is not a fault and is in fact the common case
 */
val LocalProfils = staticCompositionLocalOf<Map<String, ProfilEtendu>> { emptyMap() }

/**
 * the tag somebody wears, or null
 *
 * read from the profile and never from the server, which is the whole point: the reference shows
 * the tag even to somebody who is not a member of the server that issues it. an id to resolve would
 * only render for members, which is to say where it teaches nothing
 */
fun tagDe(profil: ProfilEtendu?): TagServeur? {
    val emoji = profil?.tagEmoji?.takeIf { it.isNotBlank() } ?: return null
    val texte = profil.tagTexte?.takeIf { it.isNotBlank() } ?: return null
    return TagServeur(emoji, texte)
}

/**
 * the server tag badge: the emoji then the text, in a pill
 *
 * what it replaces, and why it was not the same thing
 *
 * velvet used to show a role tier here, a word and a crown. reported from outside as not the
 * expected feature at all, and that is correct. a tier is computed from the person's matrix power
 * in the room you are looking at, and says "can moderate here". the reference's tag is chosen, it
 * is the same everywhere, and it is visible to people who are not members of the server issuing it
 *
 * the two coexist now: the tier stays useful where power matters, a member's card, a server's
 * member list, and the tag takes its place next to the nickname
 *
 * the emoji can be an image. a server can put a custom emoji (`mxc://`) there rather than a unicode
 * character, which is what the reference does with its icon tags. the two are told apart by the
 * prefix, and that is the only place in the component that cares
 */
@Composable
fun BadgeTag(tag: TagServeur, hauteur: Float = 18f) {
    val tokens = LocalTokens.current
    // the height is imposed, not suffered. measured: the pill was 28 dp against the reference's
    // 17.8, reported as the grey frame around the tag being too big. the padding had nothing to do
    // with it, it was the emoji's line box inflating the row. an emoji font has enormous
    // descenders, and a 10 sp `Text` takes nearly twenty points of height in one
    //
    // hence the fixed height on the row and the emoji shut inside a box its own size: the glyph can
    // overflow its frame without anybody else moving
    val cote = hauteur * 0.72f
    Row(
        Modifier.height(hauteur.dp)
            .clip(RoundedCornerShape(tokens.radiusSmall))
            .background(tokens.surfaceRaised)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(cote.dp), contentAlignment = Alignment.Center) {
            if (tag.emoji.startsWith("mxc://")) {
                EmojiEnLigne(tag.emoji)
            } else {
                // the emoji font, like everywhere else: without it the heart came out as an empty
                // square. the line height is pinned to the size so the glyph is centred in the pill
                // rather than sitting on its baseline
                Text(
                    tag.emoji,
                    fontSize = (cote * 0.95f).sp,
                    lineHeight = (cote * 0.95f).sp,
                    fontFamily = LocalPoliceEmoji.current,
                )
            }
        }
        Spacer(Modifier.width(3.dp))
        Text(
            tag.texte.take(TagServeur.LONGUEUR_MAX),
            color = tokens.textSecondary,
            fontSize = (hauteur * 0.52f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** somebody's tag badge, if they wear one. nothing at all otherwise */
@Composable
fun BadgeTagDe(userId: String, hauteur: Float = 18f) {
    tagDe(LocalProfils.current[userId])?.let { BadgeTag(it, hauteur) }
}

package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.TeintePeau
import li.velvetchat.core.model.avec

/**
 * the autocomplete panel, vertical and titled
 *
 * it replaces a horizontal scrolling strip, and that was a direct complaint. two screenshots side
 * by side showed it: the reference lists three suggestions on three lines under a heading, and
 * velvet showed one, because the next ones were off the right of the screen
 *
 * what the vertical brings is not aesthetic: in a horizontal strip nothing says there are three
 * results. you think there is one and you do not look further. a list shows what it has
 *
 * the heading names what you are searching for, with the typed fragment. that is what tells "no
 * results" from "the panel did not open", two situations that look alike when the screen says
 * nothing
 */
@Composable
fun PanneauAutocompletion(
    /** what follows the trigger: `joy` for `:joy` */
    fragment: String,
    /** the trigger itself: `:`, `@` or `#` */
    declencheur: Char,
    propositions: List<Suggestion>,
    /**
     * the highlighted index, the one enter or tab will take
     *
     * `-1` highlights none, and that is the right default for a finger: there is no current row
     * without a keyboard, and naming one would promise a key that does not exist
     */
    selection: Int = -1,
    teinte: TeintePeau,
    onChoisir: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (propositions.isEmpty()) return
    val tokens = LocalTokens.current

    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = tokens.radiusMedium, topEnd = tokens.radiusMedium))
            .background(tokens.surface),
    ) {
        Text(
            titreDe(declencheur, fragment),
            color = tokens.textMuted,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
        )
        // capped at five visible rows: past that the list eats the conversation you are reading,
        // and nobody scans twenty suggestions, they refine the word
        LazyColumn(Modifier.heightIn(max = 200.dp)) {
            itemsIndexed(propositions) { i, proposition ->
                LigneProposition(proposition, i == selection, teinte) { onChoisir(proposition) }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun LigneProposition(
    proposition: Suggestion,
    surligne: Boolean,
    teinte: TeintePeau,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth()
            .height(40.dp)
            .background(if (surligne) tokens.surfaceRaised else Color.Transparent)
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // each family carries its own marker on the left, the image for an emoji, the avatar for a
        // person, the hash for a channel. without it, three lists of grey text would look alike and
        // nothing would say what you are about to insert
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when (proposition) {
                is Suggestion.Emoji -> Box(Modifier.size(24.dp)) {
                    EmojiEnLigne(proposition.emote.url)
                }

                is Suggestion.Unicode -> Text(
                    proposition.emoji.avec(teinte),
                    fontSize = 20.sp,
                    fontFamily = LocalPoliceEmoji.current,
                )

                is Suggestion.Personne -> Avatar(
                    proposition.membre.userId,
                    proposition.membre.displayName,
                    proposition.membre.avatarUrl,
                    24.dp,
                )

                is Suggestion.Salon ->
                    Icone(Icones.salon, tint = tokens.textMuted, taille = 20.dp)

                Suggestion.ToutLeSalon ->
                    Icone(Icones.toutLeSalon, tint = tokens.warning, taille = 20.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            libelleDe(proposition),
            color = if (surligne) tokens.textPrimary else tokens.textSecondary,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * a suggestion's label
 *
 * an emoji carries its colons, `:joy:` and not `joy`: that is how it is written and it is what you
 * are typing. the reference shows them too
 */
private fun libelleDe(proposition: Suggestion): String = when (proposition) {
    is Suggestion.Emoji -> ":${proposition.emote.shortcode}:"
    is Suggestion.Unicode -> ":${proposition.emoji.raccourci}:"
    is Suggestion.Personne -> proposition.membre.displayName ?: proposition.membre.userId
    is Suggestion.Salon -> "#${proposition.salon.name}"
    Suggestion.ToutLeSalon -> horsUi.roomToutLeSalon
}

private fun titreDe(declencheur: Char, fragment: String): String = when (declencheur) {
    ':' -> horsUi.emojisCorrespondant(fragment)
    '@' -> horsUi.membresCorrespondant(fragment)
    '#' -> horsUi.salonsCorrespondant(fragment)
    else -> fragment
}

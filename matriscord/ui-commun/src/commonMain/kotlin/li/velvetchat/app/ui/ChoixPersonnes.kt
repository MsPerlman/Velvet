package li.velvetchat.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * pick several people from the conversations you already have
 *
 * ## where the list comes from, and why not from an address book
 *
 * matrix has no friend list. what it has is the conversations you are in, and the people in them.
 * so the candidates are your own one to one conversations: the people you actually talk to, which
 * is the same set the reference offers under a word we do not have
 *
 * groups are left out of the candidates. a group is not a person, and `userId` on a group row is
 * whichever member the room happened to be filed under in `m.direct` — inviting "them" would
 * invite one member of it, chosen by an accident of filing
 *
 * ## what this component is, and what it is not
 *
 * the list and nothing else: no title, no scrim, no buttons. the phone puts it in a bottom sheet
 * and the desktop in a modal card, and those two chromes have nothing in common worth sharing. the
 * rows do, which is why they live here and not twice
 */
@Composable
fun ChoixPersonnes(
    candidats: List<ConversationPrivee>,
    choisis: Set<String>,
    onBasculer: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** how tall the list may grow before it scrolls. the two platforms do not agree on it */
    hauteurMax: Dp = 320.dp,
) {
    val tokens = LocalTokens.current
    val gens = candidats.filterNot { it.groupe }
    if (gens.isEmpty()) {
        // an empty list with no sentence reads as a broken screen. this says the picker works and
        // there is simply nobody in it yet, which on a server where nobody has written is normal
        Text(
            textes().mpExplication,
            color = tokens.textMuted,
            fontSize = 13.sp,
            modifier = modifier.padding(vertical = 12.dp),
        )
        return
    }
    LazyColumn(modifier.fillMaxWidth().heightIn(max = hauteurMax)) {
        items(gens, key = { it.roomId }) { conv ->
            LignePersonne(conv, conv.userId in choisis) { onBasculer(conv.userId) }
        }
    }
}

/** one candidate: avatar, name, matrix id, and the tick on the right */
@Composable
private fun LignePersonne(conv: ConversationPrivee, choisi: Boolean, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(tokens.radiusMedium))
            .curseurMain()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(
            userId = conv.userId,
            displayName = conv.nom,
            avatarUrl = conv.avatarUrl,
            taille = 32.dp,
        )
        Column(Modifier.padding(end = 8.dp).weight(1f)) {
            Text(
                conv.nom,
                color = tokens.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // the matrix id under the name, because two people can carry the same display name and
            // the id is what you are actually inviting
            Text(
                conv.userId,
                color = tokens.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (choisi) {
            Coche(true, taille = 20.dp)
        } else {
            // an empty ring and not an empty space: the row has to say it is selectable before you
            // click it, otherwise the first tick comes as a surprise
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .border(1.5.dp, tokens.textMuted.copy(alpha = 0.6f), CircleShape),
            )
        }
    }
    Spacer(Modifier.height(2.dp))
}

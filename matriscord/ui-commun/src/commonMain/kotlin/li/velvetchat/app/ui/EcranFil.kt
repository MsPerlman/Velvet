package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody

/**
 * an open thread
 *
 * what discord puts behind "X replies": a conversation inside the conversation, which does not
 * scroll the room for everybody
 *
 * what it is, and what it is not
 *
 * a full screen and not a sheet. a sheet says look at this and come back, and a thread is somewhere
 * you settle in, write, scroll back. it deserves the whole screen and its own back
 *
 * its composer is deliberately simple: text, and that is all. no custom emoji, no gifs, no
 * attachments. that is not an oversight but an order of priority. the conversation's composer took
 * six passes to stand up, mentions, autocomplete, draft, reply, edit, and duplicating it here for
 * no reason would make a second one to maintain. the day somebody wants to send a photo in a thread
 * will be the right moment to extract it as a shared component rather than copy it now
 *
 * the root is shown at the top, detached. a thread without its starting point is a run of replies
 * to nothing
 */
@Composable
fun EcranFil(
    racine: Message,
    messages: List<Message>,
    horodateur: Horodateur,
    onEnvoyer: (String) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var champ by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(tokens.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = tokens.espM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(
                Icones.retour,
                tint = tokens.textSecondary,
                taille = 22.dp,
                description = t.retour,
                modifier = Modifier.curseurMain().clickable(onClick = onFermer).padding(end = tokens.espM),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    t.fil,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = tokens.typography.headingSize.sp,
                )
                Text(
                    // the count excludes the root, the way the sdk counts it and the way people
                    // read it
                    when (val reponses = (messages.size - 1).coerceAtLeast(0)) {
                        0 -> t.aucuneReponse
                        1 -> t.uneReponse
                        else -> t.reponsesNombre(reponses)
                    },
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                )
            }
        }

        // the root, set apart and separated by a rule: it is not a reply
        Column(Modifier.background(tokens.surface).padding(vertical = tokens.espXs)) {
            LigneDuFil(racine, horodateur)
        }
        Box(Modifier.fillMaxWidth().size(1.dp).background(tokens.border))

        val reponses = remember(messages, racine) { messages.filter { it.id != racine.id } }
        if (reponses.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    t.personneNaRepondu,
                    color = tokens.textMuted,
                    fontSize = tokens.typography.bodySize.sp,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                // no `reverseLayout` here. a thread is read start to finish, like a discussion you
                // are joining: unlike a room, you do not come to it for the last message
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
            ) {
                items(reponses, key = { it.id }) { message -> LigneDuFil(message, horodateur) }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(tokens.surface)
                .imePadding().navigationBarsPadding().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(tokens.radiusLarge))
                    .background(tokens.surfaceRaised)
                    .padding(horizontal = 14.dp, vertical = tokens.espM)
            ) {
                if (champ.isEmpty()) {
                    Text(
                        t.repondreDansLeFil,
                        color = tokens.textMuted,
                        fontSize = tokens.typography.bodySize.sp,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = champ,
                    onValueChange = { champ = it },
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        color = tokens.textPrimary,
                        fontSize = tokens.typography.bodySize.sp,
                    ),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(10.dp))
            // the button only appears with something to send, like in the conversation
            if (champ.isNotBlank()) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(tokens.accent)
                        .curseurMain().clickable {
                            onEnvoyer(champ.trim())
                            champ = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icone(Icones.envoyer, tint = androidx.compose.ui.graphics.Color.White, taille = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun LigneDuFil(message: Message, horodateur: Horodateur) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = 6.dp)) {
        Avatar(message.sender, message.senderDisplayName, message.senderAvatarUrl, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.senderDisplayName
                        ?: message.sender.removePrefix("@").substringBefore(':'),
                    color = LocalRoleColors.current[message.sender] ?: tokens.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    horodateur.horodater(message.timestamp),
                    color = tokens.textMuted,
                    fontSize = 11.sp,
                )
            }
            when (val corps = message.body) {
                is MessageBody.Text -> CorpsFormate(corps.text, corps.html)
                is MessageBody.Media -> Text(
                    corps.caption.takeIf { it.isNotBlank() } ?: corps.media.filename,
                    color = tokens.textSecondary,
                    fontSize = tokens.typography.bodySize.sp,
                )

                MessageBody.Redacted -> Text(
                    t.messageSupprimeMinuscule,
                    color = tokens.textMuted,
                    fontSize = tokens.typography.captionSize.sp,
                )

                else -> Text(
                    t.nonPrisEnChargeDansUnFil,
                    color = tokens.textMuted,
                    fontSize = tokens.typography.captionSize.sp,
                )
            }
        }
    }
}

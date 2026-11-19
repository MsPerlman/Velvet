package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.Channel
import li.velvetchat.core.model.ModeNotification

/**
 * what a long press on a channel offers
 *
 * discord puts what you want to do about a channel rather than inside it here: mark it read without
 * opening it, decide what it rings for, invite somebody. it was the only place in the list where a
 * long press did nothing
 *
 * marking read without opening is not a convenience shortcut. it is the gesture that clears a
 * channel you do not intend to read, and without it the only way to put out a badge is to open the
 * channel, so to pretend you read it
 */
@Composable
fun FeuilleSalon(
    channel: Channel,
    mode: ModeNotification?,
    peutInviter: Boolean,
    onMarquerLu: () -> Unit,
    onNotifications: () -> Unit,
    onInviter: () -> Unit,
    onFermer: () -> Unit,
    /** the server's categories. empty means the "move to" row does not appear */
    categories: List<li.velvetchat.core.model.Categorie> = emptyList(),
    /** files the channel. null for the server's root */
    onDeplacer: (li.velvetchat.core.model.Categorie?) -> Unit = {},
    /**
     * removes the channel from the server. null means the row does not appear, you do not have the
     * right
     */
    onSupprimer: (() -> Unit)? = null,
) {
    val t = textes()
    val tokens = LocalTokens.current
    // two taps, like leaving a server: a removed channel does not come back on its own
    var confirmeSuppression by remember { mutableStateOf(false) }
    FeuilleBasse(onFermer = onFermer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // the hash or the speaker, depending on what the channel is. the sheet used to show a
            // hash on a voice channel, contradicting the list right behind it
            Icone(
                if (channel.kind == li.velvetchat.core.model.ChannelKind.VOICE) {
                    Icones.salonVocal
                } else {
                    Icones.salon
                },
                tint = tokens.textMuted,
                taille = 18.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                channel.name,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        channel.topic?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = tokens.textMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = tokens.espS),
            )
        }

        // absent when there is nothing to mark. offering "mark as read" on an already read channel
        // is offering a gesture with no effect
        if (channel.unread.hasUnread) {
            ActionSalon(Icones.chronique, t.marquerCommeLu, onMarquerLu)
        }
        ActionSalon(
            Icones.notifications,
            t.notifications,
            onNotifications,
            // the current mode as the value: the question you ask opening this menu is what it is
            // today, not where you can change it
            valeur = mode?.libelle,
        )
        if (peutInviter) ActionSalon(Icones.inviter, t.inviterQuelquun, onInviter)

        // removing the channel, which existed nowhere. you could create them and never get rid of
        // one, and an experiment left one evening stayed there for everybody
        onSupprimer?.let { supprimer ->
            ActionSalon(
                Icones.supprimer,
                if (confirmeSuppression) t.confirmerLaSuppression else t.supprimerLeSalon,
                { if (confirmeSuppression) { onFermer(); supprimer() } else confirmeSuppression = true },
                couleur = tokens.danger,
            )
        }

        // filing this channel. chips and not a submenu: a small server has two or three categories, and
        // the current one reads at a glance, being highlighted
        if (categories.isNotEmpty()) {
            Text(
                t.deplacerVers,
                color = tokens.textMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = tokens.espXl, top = tokens.espM, bottom = 6.dp),
            )
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = tokens.espXl),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                (listOf(null) + categories).forEach { cible ->
                    val ici = (cible?.nom ?: "") == (channel.categorie ?: "")
                    Box(
                        Modifier
                            .background(
                                if (ici) tokens.accent else tokens.surfaceRaised,
                                RoundedCornerShape(tokens.radiusSmall),
                            )
                            // tapping the category it is already in does nothing, rather than
                            // writing two events to end up in the same place
                            .curseurMain().clickable(enabled = !ici) { onDeplacer(cible) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            cible?.nom ?: t.aucuneCategorie,
                            color = if (ici) tokens.bg else tokens.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

/**
 * choosing what a channel rings for
 *
 * it is also what gives the bell back to the user bar: it had been taken away because there was no
 * screen behind it, and there is one now
 */
@Composable
fun FeuilleNotifications(
    channel: Channel,
    mode: ModeNotification,
    onChoisir: (ModeNotification) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    FeuilleBasse(onFermer = onFermer) {
        Text(
            t.notifications,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espS),
        )
        Text(
            channel.name,
            color = tokens.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = 10.dp),
        )

        // "default" first: it is the state of nearly every channel, and the only one that follows a
        // change to the global setting
        listOf(
            ModeNotification.DEFAUT,
            ModeNotification.TOUT,
            ModeNotification.MENTIONS,
            ModeNotification.MUET,
        ).forEach { valeur ->
            Row(
                Modifier.fillMaxWidth().curseurMain().clickable { onChoisir(valeur) }
                    .padding(horizontal = tokens.espXl, vertical = tokens.espM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Coche(valeur == mode, taille = 20.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        valeur.libelle,
                        color = tokens.textPrimary,
                        fontSize = tokens.typography.bodySize.sp,
                        fontWeight = if (valeur == mode) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(valeur.detail, color = tokens.textMuted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
internal fun ActionSalon(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    libelle: String,
    onClick: () -> Unit,
    valeur: String? = null,
    /** red for what cannot be undone. null is the ordinary colour */
    couleur: androidx.compose.ui.graphics.Color? = null,
    /** false for a row that does the thing straight away. a chevron promises a screen behind it */
    chevron: Boolean = true,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = tokens.espXl, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(icone, tint = couleur ?: tokens.textPrimary, taille = 19.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            libelle,
            color = couleur ?: tokens.textPrimary,
            fontSize = tokens.typography.bodySize.sp,
            modifier = Modifier.weight(1f),
        )
        valeur?.let {
            Text(it, color = tokens.textMuted, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
        }
        if (chevron) Icone(Icones.suite, tint = tokens.textMuted, taille = 15.dp)
    }
}

/**
 * "somebody is typing…"
 *
 * discreet and at a fixed height: a row that appears and disappears pushing the conversation would
 * make the text you are reading jump every time anybody touched their keyboard. so it holds its
 * place permanently and only writes in it or not
 */
@Composable
fun LigneDeFrappe(noms: List<String>) {
    val t = textes()
    val tokens = LocalTokens.current
    val texte = when {
        noms.isEmpty() -> ""
        noms.size == 1 -> t.ecrit(noms[0])
        noms.size == 2 -> t.ecrivent(noms[0], noms[1])
        // past two, naming them takes more room than the conversation. discord says "several
        // people" and that is the right answer
        else -> t.plusieursEcrivent
    }
    Text(
        texte,
        color = tokens.textMuted,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
            .background(Color.Transparent)
            .padding(start = tokens.espL, end = tokens.espL, bottom = 2.dp),
    )
}

/**
 * "the messages from before you arrived will stay unreadable"
 *
 * the second half of the history problem, and the one we had left aside. the first was showing
 * those messages without lying (`MessageBody.Undecryptable`, which says encrypted rather than
 * leaving a hole), and this one is saying why
 *
 * it is not a fault and it is not fixable. those messages' session keys were never shared with this
 * device, which is what end to end encryption guarantees, and history sharing only covers what
 * follows an invite. no client and no manipulation will make them readable
 *
 * said once, at the top, and not on every message. repeating the explanation under each padlock
 * would fill the screen with a text everybody understood on the first line
 */
@Composable
fun NoteHistorique() {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icone(Icones.chiffrement, tint = tokens.textMuted, taille = 15.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.historiqueChiffre,
                color = tokens.textSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            Text(
                t.historiqueChiffreExplication,
                color = tokens.textMuted,
                fontSize = 11.sp,
            )
        }
    }
}

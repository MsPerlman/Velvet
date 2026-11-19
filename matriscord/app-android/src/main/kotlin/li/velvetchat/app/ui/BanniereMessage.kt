package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import li.velvetchat.core.model.AlerteSalon

/**
 * the in app notification: a new message anywhere other than the open conversation slides down at
 * the top of the screen for a few seconds, photo, nickname, where, and the start of the message. a
 * touch opens the conversation
 */
@Composable
fun BanniereMessage(alerte: AlerteSalon, onOuvrir: () -> Unit) {
    val tokens = LocalTokens.current
    Popup(alignment = Alignment.TopCenter, properties = PopupProperties(focusable = false)) {
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp)).background(tokens.surfaceRaised)
                .clickable(onClick = onOuvrir).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                alerte.auteurId ?: alerte.salonId,
                alerte.auteur ?: alerte.nom,
                alerte.avatarAuteur ?: alerte.avatarSalon,
                40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (alerte.estDirect) (alerte.auteur ?: alerte.nom) else "${alerte.auteur ?: "?"} · #${alerte.nom}",
                    color = tokens.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    alerte.apercu.orEmpty(),
                    color = tokens.textSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

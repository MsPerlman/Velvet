package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * the discord bridge page
 *
 * the single entry point of the discord to velvet migration for the user: connect your discord
 * account, then pull the custom emoji actually used in the imported dms into a server
 *
 * deliberately not wired up yet, the `onClick`s are anchor points. the shell is in place and the
 * wiring comes when the bridge's provisioning api (`serveur/mautrix-discord/`) is joined to the
 * app. the two actions will then call, respectively, the discord login and
 * `MatrixEngine.importerEmotesUtilises(salonSource, serveurCible)`
 */
@Composable
fun EcranMautrix(onFermer: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current

    Column(
        Modifier.fillMaxSize().background(tokens.bg).statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
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
            Text(
                t.passerelleDiscord,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.headingSize.sp,
            )
        }

        Text(
            t.passerelleIntro,
            color = tokens.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = tokens.espL, vertical = tokens.espXs),
        )

        Section(
            icone = Icones.lien,
            titre = t.connexionDiscord,
            corps = t.connexionDiscordAide,
            libelleAction = t.seConnecterADiscord,
            couleurAction = tokens.accent,
            onAction = { },
        )

        Section(
            icone = Icones.emoji,
            titre = t.emojisUtilises,
            corps = t.emojisUtilisesAide,
            libelleAction = t.importerEmojisUtilises,
            couleurAction = tokens.accent,
            onAction = { },
        )

        Spacer(Modifier.height(12.dp))
        Text(
            t.passerelleAvertissement,
            color = tokens.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = tokens.espL, vertical = tokens.espS),
        )
    }
}

@Composable
private fun Section(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    titre: String,
    corps: String,
    libelleAction: String,
    couleurAction: androidx.compose.ui.graphics.Color,
    onAction: () -> Unit,
) {
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = tokens.espS)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surface)
            .padding(tokens.espL),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icone(icone, tint = tokens.textSecondary, taille = 20.dp, description = null,
                modifier = Modifier.padding(end = 10.dp))
            Text(titre, color = tokens.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(corps, color = tokens.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            libelleAction,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .background(couleurAction)
                .curseurMain().clickable(onClick = onAction)
                .padding(vertical = tokens.espM),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

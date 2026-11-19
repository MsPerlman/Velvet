package li.velvetchat.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.app.ThemeLivre
import li.velvetchat.app.ThemeVelvetAndroid

/**
 * picking a theme, on the phone
 *
 * ## what this fills in
 *
 * nothing. there was nothing. the `design` module carries a full loader, its readme promises
 * changing a colour without recompiling, five themes ship — and the phone read a `theme.json`
 * that **nothing ever wrote**. the feature existed end to end except for the last metre, the
 * one you can see
 *
 * ## why a gallery and not a list of names
 *
 * a theme is a thing you look at. "Deep Arctic" says nothing; three dots showing the
 * background, the accent and the text say everything, and they come from the theme itself, so
 * they cannot lie about what you will get
 */
@Composable
internal fun ReglagesTheme() {
    val tokens = LocalTokens.current
    val t = textes()
    val contexte = LocalContext.current

    val livres = remember { ThemeVelvetAndroid.livres(contexte) }
    val actif = ThemeVelvetAndroid.actif(contexte)
    val nomActif = ThemeVelvetAndroid.nom(contexte)
    var refus by remember { mutableStateOf<String?>(null) }

    // `OpenDocument` and not `GetContent`: it gives a lasting uri and shows the system file
    // picker, so a theme dropped in the downloads can be found
    val choisirFichier = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) refus = ThemeVelvetAndroid.importer(contexte, uri)
    }

    Column(Modifier.fillMaxWidth()) {
        livres.forEach { livre ->
            LigneTheme(
                livre = livre,
                choisi = nomActif == livre.theme.name,
                onClick = {
                    ThemeVelvetAndroid.appliquer(contexte, livre.fichier)
                    refus = null
                },
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            t.importerUnTheme,
            color = tokens.accent,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMedium))
                // the mime type of a `.json` varies from device to device, and some file
                // managers give it as `application/octet-stream`: filtering strictly would
                // hide the very file that was just downloaded
                .clickable { choisirFichier.launch(arrayOf("application/json", "text/*", "*/*")) }
                .padding(vertical = 10.dp),
        )

        if (actif) {
            Text(
                t.revenirAuThemeParDefaut,
                color = tokens.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .clickable {
                        ThemeVelvetAndroid.retirer(contexte)
                        refus = null
                    }
                    .padding(vertical = 10.dp),
            )
        }

        refus?.let {
            Text(
                t.themeRefuse(it),
                color = tokens.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun LigneTheme(livre: ThemeLivre, choisi: Boolean, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    val couleurs = livre.theme.colors
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(couleurs.background.toColor())
                .border(1.dp, tokens.border, RoundedCornerShape(6.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Pastille(couleurs.accent.toColor())
            Pastille(couleurs.surfaceRaised.toColor())
            Pastille(couleurs.textPrimary.toColor())
        }
        Spacer(Modifier.width(12.dp))
        Text(
            livre.theme.name,
            color = tokens.textPrimary,
            fontSize = 15.sp,
            fontWeight = if (choisi) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (choisi) {
            Icone(
                Icones.valide,
                tint = tokens.accent,
                taille = 18.dp,
                description = null,
            )
        }
    }
}

@Composable
private fun Pastille(couleur: Color) {
    Box(Modifier.size(14.dp).clip(CircleShape).background(couleur))
}

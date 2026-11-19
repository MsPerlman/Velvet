package li.velvetchat.desk

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.app.ui.Icone
import li.velvetchat.app.ui.Icones
import li.velvetchat.app.ui.curseurMain
import li.velvetchat.app.ui.toColor

/**
 * the shipped themes, with their preview
 *
 * the desktop only offered a **windows file dialog** opened on the themes folder: you had to
 * know the folder existed, that files had been dropped in it, and guess what a name gave on
 * screen. the phone got a gallery before the desktop did, which is the wrong way round
 *
 * the dots come from the theme itself, so they cannot lie about what you will get
 */
@Composable
internal fun GalerieThemes() {
    // read once: these are resources of the binary, they do not change along the way
    val livres = remember { ThemeVelvet.livres() }
    val nomActif = ThemeVelvet.nom
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        livres.forEach { livre ->
            LigneThemeBureau(livre, choisi = nomActif == livre.theme.name) {
                ThemeVelvet.appliquer(livre.fichier)
            }
        }
    }
}

@Composable
private fun LigneThemeBureau(livre: ThemeLivreBureau, choisi: Boolean, onClick: () -> Unit) {
    val couleurs = livre.theme.colors
    Row(
        Modifier.fillMaxWidth().height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(couleurs.background.toColor())
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
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
            color = TEXTE_CLAIR,
            fontSize = 15.sp,
            fontWeight = if (choisi) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (choisi) Icone(Icones.valide, tint = DORE, taille = 18.dp, description = null)
    }
}

@Composable
private fun Pastille(couleur: Color) {
    Box(Modifier.size(13.dp).clip(CircleShape).background(couleur))
}

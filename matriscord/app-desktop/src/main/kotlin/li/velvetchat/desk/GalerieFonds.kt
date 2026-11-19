package li.velvetchat.desk

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.app.ui.Icone
import li.velvetchat.app.ui.Icones
import li.velvetchat.app.ui.curseurMain
import li.velvetchat.app.ui.decoderMedia

/**
 * the last twenty wallpapers, to take one back without hunting for it
 *
 * setting an image again meant finding it in your folders, and a picture you like alternates
 * with two others. twenty is the number that was asked for, and it is the store's
 *
 * the thumbnails decode in the background, one at a time: twenty full resolution images
 * decoded at once when the settings open is a second of freeze for a strip you do not always
 * look at
 */
@Composable
internal fun GalerieFonds() {
    val version by FondEcran.version
    val empreintes = remember(version) { FondEcran.recents() }
    if (empreintes.isEmpty()) return
    Row(
        Modifier.height(76.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        empreintes.forEach { empreinte -> VignetteFond(empreinte) }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun VignetteFond(empreinte: String) {
    var image by remember(empreinte) { mutableStateOf<ImageBitmap?>(null) }
    var survol by remember { mutableStateOf(false) }
    LaunchedEffect(empreinte) {
        image = withContext(Dispatchers.IO) {
            FondEcran.octetsDe(empreinte)?.let { decoderMedia(it, 320) }
        }
    }
    Box(
        Modifier.width(104.dp).height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .curseurMain().clickable { FondEcran.appliquer(empreinte) }
            .onPointerEvent(PointerEventType.Enter) { survol = true }
            .onPointerEvent(PointerEventType.Exit) { survol = false },
    ) {
        image?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(104.dp, 64.dp))
        }
        // removal only appears on hover: a permanent cross on twenty thumbnails is a minefield
        // for a click that only meant to change the wallpaper
        if (survol) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp)
                    .size(18.dp).clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.66f))
                    .curseurMain().clickable { FondEcran.oublier(empreinte) },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

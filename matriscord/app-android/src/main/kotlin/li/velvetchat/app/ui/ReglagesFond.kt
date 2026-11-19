package li.velvetchat.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.velvetchat.app.FondEcranAndroid

/**
 * the wallpaper and the last twenty, on the phone
 *
 * setting one again meant finding it in the gallery, and a picture you like alternates with
 * two others. twenty is the number that was asked for, and it is the store's
 */
@Composable
internal fun ReglagesFond() {
    val tokens = LocalTokens.current
    val t = textes()
    val contexte = LocalContext.current
    val version by FondEcranAndroid.version
    var refus by remember { mutableStateOf<String?>(null) }

    val choisir = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) refus = FondEcranAndroid.importer(contexte, uri)
    }

    val recents = remember(version) { FondEcranAndroid.recents(contexte) }

    Column(Modifier.fillMaxWidth()) {
        if (recents.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().height(76.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                recents.forEach { VignetteFond(it) }
            }
            Spacer(Modifier.height(6.dp))
        }

        Text(
            t.choisirUnFondDEcran,
            color = tokens.accent,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .clickable {
                    refus = null
                    choisir.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                }
                .padding(vertical = 10.dp),
        )

        if (FondEcranAndroid.actif(contexte)) {
            Text(
                t.retirerLeFondDEcran,
                color = tokens.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .clickable { FondEcranAndroid.retirer(contexte) }
                    .padding(vertical = 10.dp),
            )
        }

        refus?.let {
            Text(it, color = tokens.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun VignetteFond(empreinte: String) {
    val tokens = LocalTokens.current
    val contexte = LocalContext.current
    var image by remember(empreinte) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(empreinte) {
        image = withContext(Dispatchers.IO) {
            FondEcranAndroid.octetsDe(contexte, empreinte)?.let { decoderMedia(it, 320) }
        }
    }
    Box(
        Modifier.width(104.dp).height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.surfaceRaised)
            .border(1.dp, tokens.border, RoundedCornerShape(8.dp))
            .clickable { FondEcranAndroid.poser(contexte, empreinte) },
    ) {
        image?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // removal is a small target in the corner: on a phone a long press would be cleaner,
        // but it conflicts with scrolling the strip
        Box(
            Modifier.align(Alignment.TopEnd).padding(3.dp)
                .size(20.dp).clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.66f))
                .clickable { FondEcranAndroid.oublier(contexte, empreinte) },
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.White, fontSize = 13.sp)
        }
    }
}

/**
 * the conversation's background: the image when one is set, the flat colour otherwise
 *
 * ## why here, and not behind the whole app
 *
 * behind everything was the first try, with the fullscreen surfaces made translucent. the
 * channel list then showed **through** the conversation, as a watermark: on the phone the two
 * are stacked in the same parent, where the desktop puts them side by side. seen on screen,
 * and taken back out
 *
 * the field is called `chatBackground`, and that is the right answer: the image is painted
 * **inside** the conversation area. nothing overlaps any more, and the rest of the interface
 * keeps its flat colours
 *
 * ## the veil, and why it changes colour
 *
 * not decoration: without it the text disappears into the photograph every other time. but a
 * veil that is **always black** only works for a dark theme, whose text is light. on a light
 * theme the text is dark, so darkening the image buries it instead of lifting it out. seen on
 * screen with Blossom, unreadable
 *
 * so the veil takes the colour that pushes the image away from the text: black under a dark
 * theme, white under a light one. its opacity, on the other hand, comes from the theme
 */
@Composable
internal fun Modifier.fondConversation(pleine: Color): Modifier {
    val contexte = LocalContext.current
    val version by FondEcranAndroid.version
    var image by remember(version) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(version) {
        image = withContext(Dispatchers.IO) {
            FondEcranAndroid.octets(contexte)?.let { decoderMedia(it, 1440) }
        }
    }
    val bitmap = image ?: return this.background(pleine)
    return this
        // clipToBounds before paint, and it is not a precaution
        //
        // `ContentScale.Crop` scales the image until it covers the node, so it is larger than the
        // node on one axis, and `Modifier.paint` does not clip. that overflow is drawn OUTSIDE the
        // node's bounds
        //
        // seen on screen: the conversation is offset to the right while the drawer is open, and
        // its wallpaper spilled several hundred pixels to the left, over the whole drawer. no
        // rail, no channel list, just the image. the navigation became invisible as soon as a
        // wallpaper was set
        .clipToBounds()
        .paint(
            androidx.compose.ui.graphics.painter.BitmapPainter(bitmap),
            contentScale = ContentScale.Crop,
        )
        .background(
            (if (LocalTokens.current.dark) Color.Black else Color.White)
                .copy(alpha = FondEcranAndroid.voile(contexte)),
        )
}

/**
 * is a wallpaper set
 *
 * read by the message rows: each paints its own flat colour, which is invisible when it
 * matches the parent and becomes **stripes** the moment an image passes underneath. an
 * ordinary row has nothing to paint; a mentioned row keeps its highlight, which is precisely
 * what should still be seen
 */
@Composable
internal fun fondEcranPose(): Boolean {
    val contexte = LocalContext.current
    @Suppress("UNUSED_EXPRESSION") FondEcranAndroid.version.intValue
    return FondEcranAndroid.actif(contexte)
}

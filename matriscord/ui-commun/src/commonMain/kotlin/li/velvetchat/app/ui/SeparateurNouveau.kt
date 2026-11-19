package li.velvetchat.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** the reference's notification red (`--background-feedback-notification`) */
private val ROUGE_NOUVEAU = Color(0xFFF23F43)

/**
 * where the unread part of a conversation starts: the reference's divider, both apps
 *
 * a 1 point red rule across the conversation and, stuck to its right end, a 13 high tag reading
 * "new" in 10 bold capitals, pointed on its left side (`.divider`, `.endCap`, `.unreadPillCap`)
 */
@Composable
fun SeparateurNouveau(modifier: Modifier = Modifier) {
    val t = textes()
    Box(modifier.fillMaxWidth().height(13.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ROUGE_NOUVEAU))
        Row(Modifier.align(Alignment.CenterEnd)) {
            // the point: a triangle 8 wide, flat against the tag
            Canvas(Modifier.width(8.dp).height(13.dp)) {
                drawPath(
                    Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(0f, size.height / 2f)
                        lineTo(size.width, size.height)
                        close()
                    },
                    ROUGE_NOUVEAU,
                )
            }
            Box(
                Modifier.height(13.dp)
                    .background(ROUGE_NOUVEAU, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .padding(start = 1.dp, end = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.nouveau.uppercase(),
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

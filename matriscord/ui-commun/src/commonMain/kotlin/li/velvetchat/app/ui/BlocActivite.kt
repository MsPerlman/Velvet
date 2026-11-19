package li.velvetchat.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import li.velvetchat.core.model.Activite
import li.velvetchat.core.model.activiteDepuisJson
import li.velvetchat.core.model.dureeEcoulee

/**
 * "they are playing something", drawn the same way everywhere
 *
 * this lived inside the phone's member card, which was a mistake nobody noticed until the thing
 * was switched on: the **desktop** is what produces the presence, by pretending to be discord on
 * a named pipe, and the desktop was the one place that could not show it. you could watch the
 * activity land on the server and then go look at a card that had never heard of it
 *
 * so it comes out here, and the three cards that draw a profile call it with their own colours
 *
 * no artwork, and that is a decision rather than a shortcut. the images a game hands over are
 * keys into **discord's** cdn, resolved through an api we do not call and would not want to —
 * fetching them would mean every profile card phoning discord to ask what a stranger is playing
 *
 * @param couleurTitre the game's name, which carries the weight
 * @param couleurTexte the lines under it, and the clock
 * @param couleurIcone the little pad. usually the accent
 */
@Composable
fun BlocActivite(
    activite: Activite,
    couleurTitre: Color,
    couleurTexte: Color,
    couleurIcone: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icone(Icones.manette, tint = couleurIcone, taille = 14.dp, description = null)
            Spacer(Modifier.width(6.dp))
            Text(
                activite.nom,
                color = couleurTitre,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        listOfNotNull(activite.details, activite.etat).forEach { ligne ->
            Text(
                ligne,
                color = couleurTexte,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp),
            )
        }
        activite.depuis?.let { CompteurEcoule(it, couleurTexte) }
    }
}

/**
 * the activity of whoever this card is about, kept fresh for as long as the card is open
 *
 * ## why this does not just read the profile it was handed
 *
 * it still starts from [brut], because showing something immediately beats showing nothing. but
 * that value comes from the profile map the shell loads **once**, when the member list arrives,
 * through a cache that holds for a minute and keeps a copy on disk. perfect for a banner. for an
 * activity it means the card confidently shows what somebody was doing when you opened the app
 *
 * measured the hard way: modrinth was publishing, the server had the key, the card had nothing,
 * and reopening it changed nothing either. the profile was not stale by accident, it was stale
 * by design, and the design is right for every other field in it
 *
 * so the card reads the one volatile key itself, on open and every [RAFRAICHISSEMENT_MS] after.
 * one request, for one person, only while somebody is looking. the poll stops the moment the
 * card closes, which is what makes it affordable
 *
 * the expiry is checked **here** and not at the source, because the source is a profile on a
 * server that has no idea what an activity is and will happily keep one from last tuesday. see
 * `Activite.perimee`
 */
@Composable
fun BlocActiviteDepuisProfil(
    userId: String,
    brut: String?,
    couleurTitre: Color,
    couleurTexte: Color,
    couleurIcone: Color,
    modifier: Modifier = Modifier,
) {
    val moteur = LocalEngine.current
    var vivante by remember(userId) { mutableStateOf(brut) }
    LaunchedEffect(userId) {
        while (true) {
            // a failed read keeps what we had rather than blanking the line: a dropped wifi is
            // not somebody quitting their game
            runCatching { moteur.activiteDe(userId) }.onSuccess { vivante = it }
            delay(RAFRAICHISSEMENT_MS)
        }
    }
    val activite = activiteDepuisJson(vivante)?.takeIf { !it.perimee(System.currentTimeMillis()) }
        ?: return
    BlocActivite(activite, couleurTitre, couleurTexte, couleurIcone, modifier)
}

/**
 * how often an open card asks again
 *
 * thirty seconds, against a publisher that repeats itself every ninety and an expiry at five
 * minutes. faster would be one request per person per few seconds for a line most people glance
 * at once; slower and you would watch somebody keep playing a game they closed while you had
 * their card open
 */
private const val RAFRAICHISSEMENT_MS = 30_000L

/**
 * the clock that says how long they have been at it
 *
 * i said this one was not worth the recompositions. i was wrong about the cost and right about
 * nothing: the tick lives in **this** composable, so compose only redraws this one line of text
 * once a second and the rest of the card — avatar, banner, badges, the animated effect — never
 * hears about it. the thing i was afraid of only happens if you put the state in the parent
 *
 * nothing ticks at all when a client sends no start time, which is most of the quiet ones.
 * modrinth idling is exactly that case, so the line simply does not appear
 *
 * the delay lands on the second boundary rather than a flat 1000 ms, so the digits change when
 * the clock does instead of drifting a little further off every minute until the counter is
 * visibly out of step with everybody else's
 */
@Composable
private fun CompteurEcoule(depuis: Long, couleur: Color) {
    val t = textes()
    var maintenant by remember(depuis) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(depuis) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000)
            maintenant = System.currentTimeMillis()
        }
    }
    Text(
        t.ficheActiviteEcoulee(dureeEcoulee(maintenant - depuis)),
        color = couleur,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier.padding(start = 20.dp),
    )
}

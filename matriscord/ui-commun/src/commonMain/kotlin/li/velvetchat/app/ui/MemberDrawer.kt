package li.velvetchat.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.model.Member
import kotlin.math.roundToInt

/**
 * the member drawer
 *
 * it covers the conversation instead of pushing it, with a scrim underneath, and that difference
 * from the channel drawer is the design point. the channels and the conversation are two mutually
 * exclusive screens, you leave one for the other, hence a slide that really moves the surface. the
 * member list is a one off look over a conversation you have not left. the covering and the scrim
 * say that difference in kind: you did not change screen, you opened a layer
 *
 * the gesture comes from the right, mirroring the channel drawer, and it settles on the fling
 * exactly like it does. one mechanism for two drawers, rather than two behaviours to learn
 */
@Composable
fun TiroirMembres(
    membres: List<Member>,
    /**
     * each person's presence, by id
     *
     * passed in rather than read again here: the conversation already reads it for its "N online"
     * subtitle, and asking again would mean a second round of requests, one per member, for
     * something we already have. a member missing from the map is `INCONNU`, which draws nothing
     */
    presences: Map<String, li.velvetchat.core.model.Presence> = emptyMap(),
    ouvertureDemandee: Boolean,
    /**
     * tapping a member opens their card
     *
     * this drawer is the most natural place to want to know more about somebody, being a list of
     * people, and it was the only one that did nothing on tap. the card only opened from the server
     * settings, in other words by the admin's route
     */
    onMembre: (Member) -> Unit = {},
    onFermer: () -> Unit,
) {
    val tokens = LocalTokens.current
    val densite = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // full width, and not 280 dp, which is what the reference does: the member list there is a
        // screen and not a panel leaving the conversation visible behind. on a phone, 280 dp meant
        // truncating long names and role headers, when there is nothing useful to keep visible
        // beside them
        //
        // the `memberDrawerWidth` token stays: it will describe the panel's width on desktop, where
        // the conversation next to it does make sense
        val largeur = maxWidth
        val course = with(densite) { largeur.toPx() }

        // position measured from the right edge: `course` is closed, `0` is open. counting that way
        // avoids negative signs everywhere and makes the number match the offset actually applied
        val position = remember { Animatable(course) }
        LaunchedEffect(course) { position.updateBounds(0f, course) }
        LaunchedEffect(ouvertureDemandee) {
            position.animateTo(if (ouvertureDemandee) 0f else course, tokens.ressort())
        }

        val visible by remember(course) { derivedStateOf { position.value < course } }
        if (!visible) return@BoxWithConstraints

        fun regler(vitesse: Float) {
            val lancer = with(densite) { tokens.density.flingVelocityDpS.dp.toPx() }
            val ferme = when {
                vitesse > lancer -> true
                vitesse < -lancer -> false
                else -> position.value > course * (1f - tokens.density.drawerOpenFraction)
            }
            if (ferme) onFermer() else scope.launch { position.animateTo(0f, tokens.ressort()) }
        }

        // the scrim: `background` made translucent, like under the action sheet. its opacity
        // follows the drawer's position, so it settles in with it instead of appearing all at once,
        // which is what makes the two read as one object
        Box(
            Modifier.fillMaxSize()
                .alpha((1f - position.value / course) * tokens.colors.scrimAlpha)
                .background(tokens.bg)
        )
        Box(Modifier.fillMaxSize().curseurMain().clickable(onClick = onFermer))

        Column(
            Modifier.align(Alignment.CenterEnd)
                .offset { IntOffset(position.value.roundToInt(), 0) }
                .width(largeur)
                .fillMaxHeight()
                .background(tokens.surface)
                .statusBarsPadding()
                .pointerInput(course) {
                    // velocity is measured on the distance travelled, never on the finger's
                    // position in the local coordinate space
                    //
                    // found on screen: a slow drag opened the drawer and a flick did nothing, the
                    // opposite of what we wanted. the cause is that this surface moves itself with
                    // the finger, so in its own coordinate space the finger barely moves, and the
                    // faster the gesture the less it moves, since the surface tracks it better. the
                    // measured velocity collapsed in exactly the case where it should have been
                    // highest
                    //
                    // accumulating the deltas is immune to that: it depends on no coordinate space,
                    // only on the path the finger took
                    val vitesse = VelocityTracker()
                    var parcouru = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { parcouru = 0f; vitesse.resetTracking() },
                        onDragEnd = { regler(vitesse.calculateVelocity().x) },
                        onDragCancel = { regler(0f) },
                    ) { changement, delta ->
                        parcouru += delta
                        vitesse.addPosition(
                            changement.uptimeMillis,
                            androidx.compose.ui.geometry.Offset(parcouru, 0f),
                        )
                        scope.launch {
                            position.snapTo((position.value + delta).coerceIn(0f, course))
                        }
                    }
                }
        ) {
            Text(
                horsUi.membresNombre(membres.size),
                color = tokens.textSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier.padding(horizontal = tokens.espL, vertical = 14.dp),
            )

            ListeMembres(membres, presences, onMembre)
        }
    }
}

/**
 * the list itself, without the drawer around it
 *
 * pulled out because it now has two containers: the phone's sliding drawer, and the desktop's
 * fourth column where it sits permanently
 *
 * writing a second version would have produced two role groupings, two ways of handling an unknown
 * presence, and two places to fix the day one of them got it wrong. exactly the reason the html
 * parsing seam was removed
 */
@Composable
fun ListeMembres(
    membres: List<Member>,
    presences: Map<String, li.velvetchat.core.model.Presence> = emptyMap(),
    onMembre: (Member) -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val tokens = LocalTokens.current
    val groupes = remember(membres, presences) { grouperMembres(membres, presences) }

    LazyColumn(modifier) {
        groupes.forEach { (titre, gens) ->
            item(key = "groupe-$titre") {
                Text(
                    "${titre.uppercase()} — ${gens.size}",
                    color = tokens.textMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(
                        start = tokens.espL, end = tokens.espL, top = 14.dp, bottom = 6.dp,
                    ),
                )
            }
            items(gens, key = { it.userId }) { membre ->
                LigneMembre(
                    membre,
                    presences[membre.userId]
                        ?: li.velvetchat.core.model.Presence.INCONNU,
                    onClick = { onMembre(membre) },
                )
            }
        }
    }
}

@Composable
private fun LigneMembre(
    membre: Member,
    presence: li.velvetchat.core.model.Presence,
    onClick: () -> Unit = {},
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .curseurMain().clickable(onClick = onClick)
            // offline members are dimmed, not hidden. that is what the reference does and it is the
            // right reading: they are part of the room, you can write to them, they just will not
            // answer right away. the group on its own is not enough, since it is at the bottom of
            // the list and often off screen when you are reading a row
            .alpha(if (presence == li.velvetchat.core.model.Presence.INVISIBLE) 0.45f else 1f)
            .padding(horizontal = tokens.espM, vertical = tokens.espXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the avatar carries the presence dot, like in the direct conversation list. it had existed
        // for a while and this drawer did not use it: the one list whose whole subject is who is
        // around was the only one not saying so
        //
        // an unknown state draws nothing, see `PastillePresence`. on a server with presence turned
        // off the list is therefore exactly what it was before, with no grey dot suggesting an
        // absence somebody observed
        run {
            Avatar(
                userId = membre.userId,
                displayName = membre.displayName,
                avatarUrl = membre.avatarUrl,
                taille = 32.dp,
                // the status in the avatar's notch, like the reference
                presence = presence,
                // the decoration was worn everywhere except here, which is to say in the one list
                // whose subject is people. it comes from the profile, which is already loaded for
                // the tags
                decoration = LocalProfils.current[membre.userId]?.decoration,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                membre.displayName ?: membre.userId.removePrefix("@").substringBefore(':'),
                // the role colour dresses the name and not a dot next to it, which is what discord
                // does and what avoids a second coloured object beside the avatar, which already
                // carries its own identity colour
                //
                // read from `LocalRoleColors` and not from `membre.role`, and that is a fix:
                // `toRole()` always sets `colorArgb = null`, since the custom colours live in the
                // server's tiers (`RoleTier`), which the timeline already consulted through this
                // map and this list ignored. so it was trying to colour with a value that could
                // only be null, and drew everybody in white where the reference makes the hierarchy
                // readable at a glance
                color = LocalRoleColors.current[membre.userId]
                    ?: membre.role?.colorArgb?.let { Color(it) }
                    ?: tokens.textSecondary,
                style = brosseDuPseudo(membre.userId)?.let {
                    androidx.compose.ui.text.TextStyle(brush = it)
                } ?: androidx.compose.ui.text.TextStyle.Default,
                fontSize = tokens.typography.bodySize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // a glyph, not a text pill
        //
        // "administrator" spelled out next to the nickname repeats the group heading right above
        // the row and eats the space a long name needs. the crown says the same thing in one
        // character, which is discord's choice
        //
        // gold and not accent: the accent colour names what you can act on, and a role is not
        // clickable
        membre.role?.icon?.let { glyphe ->
            Text(glyphe, color = tokens.colors.warning.toColor(), fontSize = 15.sp)
        }
    }
}

/**
 * groups by role, highest power to lowest, and puts the offline members aside
 *
 * the role order comes from the power level and not from the name, that being the only thing the
 * protocol actually orders. sorting by name would give an alphabetical ranking whose meaning
 * changes every time a role gets renamed
 *
 * people with no particular role end up in a "members" group. they are the majority, and inventing
 * a title for them would give them an importance they do not have
 *
 * offline as a separate group, but only when we know. the reference pulls absent people out of the
 * role groups and puts them at the bottom, greyed. that is what makes the list useful: you come
 * here to find out who is around, and a sleeping moderator at the top answers a different question
 *
 * `INCONNU` is not offline, and that distinction carries the rest. matrix lets a server disable
 * presence and synapse does so by default, in which case everybody is `INCONNU`. treating them as
 * absent would empty the present list and fill an offline group with everyone on the server, a
 * spectacular bug caused by a server setting rather than by reality. only `INVISIBLE`, which the
 * protocol writes as `offline` and which is an answer from the server, goes to the bottom
 */
fun grouperMembres(
    membres: List<Member>,
    presences: Map<String, li.velvetchat.core.model.Presence>,
): List<Pair<String, List<Member>>> {
    val absent = li.velvetchat.core.model.Presence.INVISIBLE
    val (horsLigne, presents) = membres.partition { presences[it.userId] == absent }

    val parRole = presents
        .groupBy { it.role?.takeIf { r -> r.powerLevel > 0 } }
        .toList()
        .sortedByDescending { (role, _) -> role?.powerLevel ?: -1 }
        .map { (role, gens) ->
            (role?.name ?: horsUi.membres) to gens.sortedBy { (it.displayName ?: it.userId).lowercase() }
        }

    if (horsLigne.isEmpty()) return parRole
    return parRole + listOf(
        horsUi.statutHorsLigne to horsLigne.sortedBy { (it.displayName ?: it.userId).lowercase() },
    )
}

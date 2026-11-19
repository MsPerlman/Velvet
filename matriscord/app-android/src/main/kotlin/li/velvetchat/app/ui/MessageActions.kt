package li.velvetchat.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody
import kotlin.math.roundToInt

/**
 * the two surfaces of a long press on mobile
 *
 * there is no hover on a phone, so no tools pill. the equivalent gesture opens two surfaces at
 * once, and not one after the other:
 *
 * 1. a quick reactions bar stuck to the message itself, for the common use, which is reacting
 * without thinking 2. a bottom sheet listing the full actions
 *
 * why both at once, and not one single menu: reacting is by far the most frequent action. burying
 * it in a list with "delete" and "edit" would impose two gestures on what has to cost one, and
 * would put a harmless action and a destructive one on the same footing
 *
 * why the bar is anchored on the message and not at the top of the sheet: at that moment the user
 * is looking at the message they just aimed at. moving the answer elsewhere on the screen would
 * make them reread to check they aimed right, which is what the anchoring is for, not a flourish
 *
 * the closing is animated too, where the first version made it vanish at once. the reasoning of the
 * time, that an animated exit would hold the screen while the decision was already made, turned out
 * false in use: a surface that clears instantly does not read as fast, it reads as a display
 * glitch. so the exit takes the entrance's duration, and the real close is only signalled once the
 * animation has finished
 */
@Composable
fun MessageActionsOverlay(
    message: Message,
    ancreYpx: Int,
    onFermer: () -> Unit,
    onReagir: (String) -> Unit,
    onRepondre: () -> Unit,
    /** open a thread on this message, or join the one that already exists */
    onRepondreEnFil: () -> Unit = {},
    onModifier: () -> Unit,
    onSupprimer: () -> Unit,
    /** `null` when pinning is not allowed here, see [FeuilleActions] */
    estEpingle: Boolean? = null,
    onEpingler: () -> Unit = {},
    onCopierLien: () -> Unit = {},
    /** reads down to this message, and no further. null hides the entry */
    onMarquerLu: (() -> Unit)? = null,
    /** puts this message and everything after it back to unread. null hides the entry */
    onMarquerNonLu: (() -> Unit)? = null,
) {
    val tokens = LocalTokens.current
    val densite = LocalDensity.current
    val presse = copieur()
    val duree = tokens.dureeNormale

    var entre by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entre = true }
    // two curves, not one: the sheet arrives decelerating, under your eyes, and leaves
    // accelerating, once there is nothing left to read on it
    val progression by animateFloatAsState(
        targetValue = if (entre) 1f else 0f,
        animationSpec = tween(
            durationMillis = duree,
            easing = if (entre) tokens.easingEntree else tokens.easingSortie,
        ),
        label = "ouverture",
    )

    // every exit goes through here: we start the animation, then we tell the caller. doing it the
    // other way round would remove the overlay from the composition before anything had moved,
    // which is exactly the fault being fixed
    var sortie by remember { mutableStateOf(false) }
    val fermer: () -> Unit = { sortie = true }
    LaunchedEffect(sortie) {
        if (!sortie) return@LaunchedEffect
        entre = false
        kotlinx.coroutines.delay(duree.toLong())
        onFermer()
    }

    // the full picker is a second sheet replacing the first, rather than a stacked surface: two
    // superimposed sheets would ask you to work out which closes first
    var pickerOuvert by remember { mutableStateOf(false) }
    var confirmeSuppression by remember { mutableStateOf(false) }

    var hauteurFeuille by remember { mutableStateOf(0) }

    // back closes the sheet, and nothing else
    //
    // it did not: the gesture went through to the `Shell`, which reads it as "open the drawer", so
    // the room list opened behind a sheet still on screen, and tapping in it changed room without
    // anything having been closed. seen during the beta test, and it is the same omission as the
    // one already fixed on `FeuilleBasse`
    androidx.activity.compose.BackHandler(onBack = fermer)

    Box(Modifier.fillMaxSize()) {
        // the veil: `background` made translucent, not a second black (token `scrimAlpha`)
        Box(
            Modifier.fillMaxSize()
                .alpha(progression * tokens.colors.scrimAlpha)
                .background(tokens.bg)
        )
        // tapping beside it closes, on a layer separate from the veil, so the veil's animated
        // opacity does not make the clickable area depend on the animation's progress
        Box(Modifier.fillMaxSize().clickable(onClick = fermer))

        if (!pickerOuvert) {
            // the quick reactions bar, anchored just above the targeted message. we lift it by its
            // own height so it does not cover what you just aimed at, and clamp it to zero: on a
            // message right at the top of the screen it would otherwise stay out of view
            val hauteurBarre = with(densite) { tokens.density.quickReactionBarHeight.dp.roundToPx() }
            val y = (ancreYpx - hauteurBarre - with(densite) { 8.dp.roundToPx() }).coerceAtLeast(0)

            // the bar does not merely light up, it settles. eight dp of descent is enough, enough
            // for the eye to read a movement, too little for it to have to follow it
            val poseePx = with(densite) { 8.dp.roundToPx() }
            Box(
                Modifier
                    .offset { IntOffset(0, y - ((1f - progression) * poseePx).roundToInt()) }
                    .fillMaxWidth()
                    .alpha(progression),
                contentAlignment = Alignment.Center,
            ) {
                BarreReactionsRapides(
                    onReagir = { onReagir(it); fermer() },
                    onPlus = { pickerOuvert = true },
                )
            }
        }

        // the sheet comes in from the bottom: we offset it by its own height, attenuated by the
        // progress. measuring rather than guessing avoids a hard coded value that would
        // desynchronise as soon as an action row is added
        Box(
            Modifier.align(Alignment.BottomCenter)
                .onGloballyPositioned { hauteurFeuille = it.size.height }
                .offset { IntOffset(0, ((1f - progression) * hauteurFeuille).roundToInt()) }
        ) {
            if (pickerOuvert) {
                PickerEmojis(
                    // custom emojis cannot be used as reactions
                    //
                    // a matrix reaction is a text key, aggregated by the server: two people
                    // reacting with the same key are counted together. nothing in it carries an
                    // image. sending `:blahaj:` would therefore literally show ":blahaj: 2" for
                    // everybody, including in element
                    //
                    // offering them then returning text would be worse than not offering them: you
                    // would think you had put an image
                    autoriserPersonnalises = false,
                    onChoisir = { choix ->
                        if (choix is ChoixEmoji.Unicode) onReagir(choix.caractere)
                        fermer()
                    },
                    onFermer = { pickerOuvert = false },
                )
            } else {
                FeuilleActions(
                    message = message,
                    confirmeSuppression = confirmeSuppression,
                    onRepondre = { onRepondre(); fermer() },
                    onRepondreEnFil = { onRepondreEnFil(); fermer() },
                    onCopier = {
                        val texte = (message.body as? MessageBody.Text)?.text
                        if (texte != null) presse(texte)
                        fermer()
                    },
                    onCopierLien = { onCopierLien(); fermer() },
                    estEpingle = estEpingle,
                    onEpingler = { onEpingler(); fermer() },
                    onMarquerLu = onMarquerLu?.let { { it(); fermer() } },
                    onMarquerNonLu = onMarquerNonLu?.let { { it(); fermer() } },
                    onModifier = { onModifier(); fermer() },
                    onDemanderSuppression = { confirmeSuppression = true },
                    onConfirmerSuppression = { onSupprimer(); fermer() },
                    onAnnulerSuppression = { confirmeSuppression = false },
                )
            }
        }
    }
}

/**
 * six emojis and a `+`
 *
 * ## it used to be six hardcoded emoji, and the comment here admitted it
 *
 * "an accepted debt", it said, "discord offers the recently used reactions, which assumes a
 * local history we do not have yet". we have had one for a while now — [PrefsEmojis] keeps both
 * the starred ones and a usage count — so the debt is paid
 *
 * the order is favourites, then most used, then the old six as a floor. favourites first because
 * starring something is a deliberate act and deserves to beat a statistic, and the floor because
 * a brand new account would otherwise get an empty bar, which is worse than a boring one
 *
 * custom emoji are left out on purpose. this bar draws characters, and a `:blahaj:` would want
 * an image loaded from the server for a row that appears and disappears under your thumb
 */
@Composable
private fun BarreReactionsRapides(onReagir: (String) -> Unit, onPlus: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    val reglages = LocalReglagesEmojis.current
    // read once when the bar opens. re reading on every recomposition would mean touching the
    // preferences file while somebody is holding their thumb on a message
    val rapides = remember {
        val gardees = LinkedHashSet<String>()
        // `u:` prefixes a unicode emoji in the preferences, `p:` a custom one. only the first
        // kind can be drawn as text, see above
        (reglages.favoris() + reglages.frequents())
            .filter { it.startsWith("u:") }
            .forEach { gardees += it.removePrefix("u:") }
        (gardees + REACTIONS_RAPIDES).take(6)
    }
    Row(
        Modifier.padding(horizontal = tokens.espL)
            .clip(RoundedCornerShape(tokens.radiusLarge))
            .background(tokens.surfaceRaised)
            .padding(horizontal = tokens.espS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rapides.forEach { emoji ->
            Box(
                Modifier.size(tokens.density.quickReactionBarHeight.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .clickable { onReagir(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 22.sp)
            }
        }
        Box(
            Modifier.size(tokens.density.quickReactionBarHeight.dp)
                .clip(RoundedCornerShape(tokens.radiusMedium))
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) {
            Icone(Icones.ajouter, tint = tokens.textSecondary, taille = 22.dp,
                description = t.plusDEmojis)
        }
    }
}

@Composable
private fun FeuilleActions(
    message: Message,
    confirmeSuppression: Boolean,
    onRepondre: () -> Unit,
    onRepondreEnFil: () -> Unit,
    onCopier: () -> Unit,
    onCopierLien: () -> Unit,
    estEpingle: Boolean?,
    onEpingler: () -> Unit,
    onMarquerLu: (() -> Unit)?,
    onMarquerNonLu: (() -> Unit)?,
    onModifier: () -> Unit,
    onDemanderSuppression: () -> Unit,
    onConfirmerSuppression: () -> Unit,
    onAnnulerSuppression: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val estTexte = message.body is MessageBody.Text

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = tokens.radiusLarge, topEnd = tokens.radiusLarge))
            .background(tokens.surface)
            .navigationBarsPadding()
            .padding(vertical = tokens.espS)
    ) {
        // a handle: it does nothing, it says the surface is a sheet and not a screen. without it a
        // bottom sheet looks like a panel stuck to the bottom
        Box(
            Modifier.align(Alignment.CenterHorizontally)
                .padding(bottom = tokens.espS)
                .width(36.dp).height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tokens.border)
        )

        if (confirmeSuppression) {
            // a destructive action asks for a second intention, not a second reflex: the label
            // changes, so the learned gesture ("tap there") is no longer enough
            LigneAction(
                Icones.supprimer,
                t.confirmerLaSuppression,
                danger = true,
                onClick = onConfirmerSuppression,
            )
            LigneAction(Icones.fermer, t.annuler, onClick = onAnnulerSuppression)
            return@Column
        }

        // two blocks, and it is the reference's split, which has real logic
        //
        // the first group acts on the conversation: writing from this message, or correcting it.
        // the second acts on this message as an object: copying it, filing it, deleting it. the
        // list used to be continuous, and seven rows with no breathing space get reread in full
        // each time for want of a landmark
        //
        // "edit" opens the block when it is our message, as in the reference: on your own message
        // it is by far the most asked for action
        if (message.isOwn && estTexte) LigneAction(Icones.modifier, t.modifier, onClick = onModifier)
        LigneAction(Icones.repondre, t.repondre, onClick = onRepondre)
        // distinct from "reply", and the difference is not cosmetic. an ordinary reply stays in the
        // conversation and scrolls it for everybody, and a thread reply does not appear in it at
        // all. that is precisely what you want when you want to dig into a subject without drowning
        // the room
        LigneAction(Icones.messages, t.repondreDansUnFil, onClick = onRepondreEnFil)

        SeparateurActions()

        if (estTexte) LigneAction(Icones.copier, t.copierLeTexte, onClick = onCopier)
        // `null` means the room does not let us write `m.room.pinned_events`. the action is then
        // absent and not greyed out: a grey row still invites a touch, and the refusal would only
        // come after
        if (estEpingle != null) {
            LigneAction(
                if (estEpingle) Icones.epingleActif else Icones.epinglerSimple,
                if (estEpingle) t.retirerDesEpingles else t.epinglerLeMessage,
                onClick = onEpingler,
            )
        }
        // a message's link, and not only its text. it is what you paste to somebody to say "there":
        // the engine builds it on the room's alias when there is one, because an internal id is not
        // routable for anybody not already in it
        //
        // last in the block, as in the reference: it is the rarest of the three
        LigneAction(Icones.copierLien, t.copierLeLien, onClick = onCopierLien)
        // reading stops where you say, and starts again where you say. the conversation menu
        // does the whole room at once, which is not the same gesture: here you are pointing at
        // a message you did not get to, or one you want to come back to
        onMarquerLu?.let { LigneAction(Icones.valide, t.marquerCommeLu, onClick = it) }
        onMarquerNonLu?.let { LigneAction(Icones.messages, t.marquerCommeNonLu, onClick = it) }

        if (message.isOwn) {
            SeparateurActions()
            LigneAction(
                Icones.supprimer,
                t.supprimer,
                danger = true,
                onClick = onDemanderSuppression,
            )
        }
    }
}

/**
 * the rule separating two blocks of actions
 *
 * pale enough not to read as a border: it delimits nothing, it breathes. a firm rule would cut the
 * sheet into panels, which would say something else
 */
@Composable
private fun SeparateurActions() {
    val tokens = LocalTokens.current
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = tokens.espXl, vertical = 6.dp)
            .height(1.dp)
            .background(tokens.border.copy(alpha = 0.5f))
    )
}

/**
 * a row of the sheet: icon, then label
 *
 * the icon is not decorative, it is what makes the sheet scannable at a glance. six labels aligned
 * with no landmark get read one by one, and with their icon you aim directly. it is also what the
 * reference does
 */
@Composable
private fun LigneAction(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    libelle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    val couleur = if (danger) tokens.danger else tokens.textPrimary
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = tokens.espXl, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icone(icone, tint = couleur, taille = 20.dp)
        Spacer(Modifier.width(14.dp))
        Text(libelle, color = couleur, fontSize = tokens.typography.bodySize.sp)
    }
}

/** the floor, for an account that has never starred or used anything */
private val REACTIONS_RAPIDES = listOf("👍", "😂", "❤️", "😮", "😢", "🎉")

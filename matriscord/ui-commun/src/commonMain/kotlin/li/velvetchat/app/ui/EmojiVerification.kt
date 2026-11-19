package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.ComparisonItem
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.VerificationStep
import li.velvetchat.core.essayer

/**
 * verification by comparing emoji
 *
 * the default path, and the answer to retyping a 48 character key, which was never going to fly
 * with anybody. here two devices display the same run of seven emoji and the person confirms they
 * match
 *
 * the design rule that matters, and that is easy to betray without noticing: never prefill, never
 * pre validate. both runs are derived from a shared secret and an interceptor cannot produce the
 * same one. if the interface decided on the person's behalf, a preselected ok button, an automatic
 * comparison, wording that nudges towards confirming, the mechanism would protect nothing. hence
 *
 * - both buttons carry the same visual weight, and "they do not match" is not relegated to a small
 *     grey link
 * - every emoji comes with its name, because two emoji can look alike and the two devices do not
 *     necessarily have the same font
 */
@Composable
fun EmojiVerificationScreen(
    engine: MatrixEngine,
    onCancel: () -> Unit,
    onVerifie: () -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()

    var etape by remember { mutableStateOf<VerificationStep?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var enAttente by remember { mutableStateOf(true) }

    // one collection for the whole life of the screen: the flow carries the verification session,
    // and reopening it would break it
    LaunchedEffect(Unit) {
        essayer {
            engine.verification.requestVerification().collect { pas ->
                etape = pas
                enAttente = false
                // moving to the comparison is not automatic sdk side, it has to be asked for once
                // the other end accepts the request. that is a protocol step and not a decision.
                // the decision is `approve()`
                if (pas is VerificationStep.RequestAccepted) {
                    runCatching { engine.verification.startEmojiComparison() }
                }
            }
        }.onFailure { erreur = it.message ?: t.verificationImpossible }
    }

    Box(
        Modifier.fillMaxSize().background(tokens.bg).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (val e = etape) {
                is VerificationStep.Comparison -> ComparaisonPartagee(
                    items = e.items,
                    onApprouver = {
                        scope.launch { runCatching { engine.verification.approve() } }
                    },
                    onRefuser = {
                        scope.launch { runCatching { engine.verification.decline() } }
                    },
                )

                VerificationStep.Finished -> {
                    EtatVerification(
                        titre = t.appareilVerifie,
                        detail = t.historiqueAccessible,
                        couleur = tokens.accent,
                    )
                    // the screen dismisses itself without waiting for `accessFlow`
                    //
                    // found on the emulator: after a successful verification the device stayed on
                    // this screen indefinitely, still there two minutes later, and only restarting
                    // the app gave access to the rest. the cause is that the access policy is
                    // derived from an sdk listener that does not re emit after a self verification
                    //
                    // we do not bypass the policy, we tell it what we just saw: the comparison was
                    // approved on both sides, so this device is verified. waiting for a flow to
                    // confirm it means letting the screen depend on a signal that may never come
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(1_800)
                        onVerifie()
                    }
                }

                VerificationStep.Cancelled -> EtatVerification(
                    titre = t.verificationAnnulee,
                    detail = t.autreAppareilAInterrompu,
                    couleur = tokens.textSecondary,
                    onRetour = onCancel,
                )

                VerificationStep.Failed -> EtatVerification(
                    titre = t.verificationEchouee,
                    detail = t.suitesDiscordantes,
                    couleur = tokens.danger,
                    onRetour = onCancel,
                )

                else -> Attente(erreur, enAttente, onCancel)
            }
        }
    }
}

/**
 * the waiting screen says what to do on the other device
 *
 * without that sentence, somebody watches a spinner with no idea the expected action is elsewhere.
 * the classic fault of a two device verification
 */
@Composable
private fun Attente(erreur: String?, enAttente: Boolean, onCancel: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    if (erreur != null) {
        EtatVerification(t.verificationImpossible, erreur, tokens.danger, onRetour = onCancel)
        return
    }
    Text(
        t.verificationEnAttente,
        color = tokens.textPrimary,
        fontSize = tokens.typography.headingSize.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        if (enAttente) {
            t.ouvrirSurAppareilVerifie
        } else {
            t.demandeAcceptee
        },
        color = tokens.textMuted,
        fontSize = tokens.typography.captionSize.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = tokens.espM),
    )
    BoutonVerif(t.annuler, principal = false, modifier = Modifier.padding(top = 24.dp), onClick = onCancel)
}

/**
 * the comparison, shared by both sides
 *
 * the requester and the receiver see the same screen, and that is not code economy: the protection
 * rests on two humans comparing the same thing. two separate renderings would eventually diverge, a
 * different order, a name translated on one side, and the comparison would stop meaning anything
 */
@Composable
fun ComparaisonPartagee(
    items: List<ComparisonItem>,
    onApprouver: () -> Unit,
    onRefuser: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current

    Text(
        t.symbolesIdentiques,
        color = tokens.textPrimary,
        fontSize = tokens.typography.headingSize.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        t.comparerAvecLAutreAppareil,
        color = tokens.textMuted,
        fontSize = tokens.typography.captionSize.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp, bottom = tokens.espXl),
    )

    // two rows rather than one: seven symbols with their names do not fit across a phone, and
    // squeezing them would make the names unreadable, when the name is what resolves the ambiguity
    // between two emoji that look alike
    items.chunked(4).forEach { rangee ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            rangee.forEach { item ->
                Column(
                    Modifier.width(78.dp).padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(item.symbol, fontSize = 34.sp)
                    Text(
                        item.description,
                        color = tokens.textSecondary,
                        fontSize = tokens.typography.captionSize.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = tokens.espXs),
                    )
                }
            }
        }
    }

    // equal visual weight for both outcomes. putting "they do not match" in the background would
    // nudge towards confirming, which is exactly what an interceptor would want
    Row(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        BoutonVerif(
            t.caNeCorrespondPas,
            principal = false,
            danger = true,
            modifier = Modifier.weight(1f),
            onClick = onRefuser,
        )
        Box(Modifier.width(12.dp))
        BoutonVerif(
            t.ilsSontIdentiques,
            principal = true,
            modifier = Modifier.weight(1f),
            onClick = onApprouver,
        )
    }
}

@Composable
fun EtatVerification(
    titre: String,
    detail: String,
    couleur: androidx.compose.ui.graphics.Color,
    onRetour: (() -> Unit)? = null,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Text(
        titre,
        color = couleur,
        fontSize = tokens.typography.headingSize.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        detail,
        color = tokens.textMuted,
        fontSize = tokens.typography.captionSize.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = tokens.espM),
    )
    onRetour?.let {
        BoutonVerif(t.retour, principal = false, modifier = Modifier.padding(top = 24.dp), onClick = it)
    }
}

@Composable
fun BoutonVerif(
    libelle: String,
    principal: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Box(
        modifier.clip(RoundedCornerShape(tokens.radiusMedium))
            .background(if (principal) tokens.accent else tokens.surfaceRaised)
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = tokens.espL, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            libelle,
            color = when {
                principal -> tokens.bg
                danger -> tokens.danger
                else -> tokens.textSecondary
            },
            fontWeight = FontWeight.Bold,
            fontSize = tokens.typography.captionSize.sp,
            textAlign = TextAlign.Center,
        )
    }
}

package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.VerificationStep
import li.velvetchat.core.essayer

/**
 * the screen that answers a verification request
 *
 * this was the app's most serious hole: velvet could ask for a verification and never accept one.
 * all through development that role was played by a gradle runner launched from a pc, which nobody
 * on a small server is going to do. in practice nobody but the developer could add a device, since the
 * default path only worked for him
 *
 * it listens permanently, and that is the design point. a verification request does not arrive when
 * you are looking at it: it arrives while you are reading a conversation, from the other device of
 * somebody installing the app right then. a screen you would have to open yourself would miss
 * exactly what it exists for. so the listening sits at the root of the app, above everything else
 *
 * what does not change from the requesting screen: both buttons carry the same weight, every emoji
 * carries its name, nothing is preselected. answering a request is no less sensitive than making
 * one, and it is even the side an interceptor would try, betting on somebody accepting by reflex
 */
@Composable
fun EcouteVerification(engine: MatrixEngine) {
    val t = textes()
    val scope = rememberCoroutineScope()
    var etape by remember { mutableStateOf<VerificationStep?>(null) }

    // one collection for the whole life of the screen. it survives from one request to the next,
    // since reopening the flow between two verifications would reopen the sdk side session too and
    // cancel the verification in progress
    LaunchedEffect(Unit) {
        essayer {
            engine.verification.observeVerification().collect { pas ->
                etape = pas
                // moving to the comparison is not automatic sdk side, it has to be asked for once
                // the request is accepted. that is a protocol step and not a decision. the decision
                // is `approve()`, and it stays human
                if (pas is VerificationStep.RequestAccepted) {
                    runCatching { engine.verification.startEmojiComparison() }
                }
            }
        }
            .onFailure { println("velvet: verification entrante illisible - " + it.message) }
    }

    val courante = etape ?: return

    // nothing to show while there is nothing to ask anybody: protocol steps do not deserve to
    // interrupt whatever somebody is doing
    val visible = courante is VerificationStep.RequestReceived ||
        courante is VerificationStep.RequestAccepted ||
        courante is VerificationStep.ComparisonStarted ||
        courante is VerificationStep.Comparison ||
        courante is VerificationStep.Finished
    if (!visible) {
        // cancelled or failed: we clear the state to be ready for the next request. staying stuck
        // on a past failure would block the second attempt, which is precisely what people make
        // when the first one misses
        LaunchedEffect(courante) { etape = null }
        return
    }

    val tokens = LocalTokens.current
    Box(
        Modifier.fillMaxSize().background(tokens.bg).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (courante) {
                is VerificationStep.RequestReceived -> Demande(
                    appareil = courante.deviceDisplayName ?: courante.deviceId,
                    onAccepter = {
                        scope.launch { runCatching { engine.verification.acceptIncoming() } }
                    },
                    onRefuser = {
                        scope.launch { runCatching { engine.verification.decline() } }
                        etape = null
                    },
                )

                is VerificationStep.Comparison -> ComparaisonPartagee(
                    items = courante.items,
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
                        detail = t.autreAppareilPeutLire,
                        couleur = tokens.accent,
                    )
                    // it dismisses itself: staying on a success screen would mean having to dismiss
                    // it when there is nothing left to decide
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2_500)
                        etape = null
                    }
                }

                else -> EtatVerification(
                    titre = t.verificationEnCours,
                    detail = t.preparationComparaison,
                    couleur = tokens.textSecondary,
                )
            }
        }
    }

    // no `cancel()` when the screen disappears, and that is a fix
    //
    // an early version cancelled the running verification when this composable left the
    // composition, so as not to leave the other device waiting. the end to end test showed the
    // opposite: the phone accepted the request, then cancelled it two seconds later, every time.
    // the cause is that the access state churns during a verification, being what the verification
    // changes, so the composable was removed and put back, and the removal triggered the cancel.
    // the safeguard caused exactly the failure it was meant to prevent
    //
    // an abandoned verification expires on its own protocol side. a wait that eventually lapses
    // beats a certain cancellation on every attempt
}

/**
 * the request itself
 *
 * the text names the device and says what it means if you did not ask for anything. a verification
 * request arriving unprompted is the protocol's most important alarm: somebody logged into the
 * account. saying it here, at the moment the question comes up, beats documenting it elsewhere
 */
@Composable
private fun Demande(appareil: String, onAccepter: () -> Unit, onRefuser: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    Text(
        t.demandeDeVerification,
        color = tokens.textPrimary,
        fontSize = tokens.typography.headingSize.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        t.appareilDemandeVerification(appareil),
        color = tokens.textSecondary,
        fontSize = tokens.typography.bodySize.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = tokens.espM),
    )
    Text(
        t.refuserSiPasVous,
        color = tokens.danger,
        fontSize = tokens.typography.captionSize.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = tokens.espL),
    )

    Row(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        BoutonVerif(
            t.invitationRefuser,
            principal = false,
            danger = true,
            modifier = Modifier.weight(1f),
            onClick = onRefuser,
        )
        Box(Modifier.width(12.dp))
        BoutonVerif(t.invitationAccepter, principal = true, modifier = Modifier.weight(1f), onClick = onAccepter)
    }
}

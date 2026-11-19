package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.MatrixEngine

/**
 * device verification, a newcomer's first screen
 *
 * why it blocks rather than letting people through: with no verified device the encrypted history
 * stays unreadable, since earlier keys are only delivered to devices signed by their owner's
 * identity, and other matrix clients show this session as suspect. letting people in would give a
 * degraded experience without saying so
 *
 * the engine decides, through `accessFlow()`, and this screen renders it. the text explains the
 * consequence, not the procedure
 */
@Composable
fun VerificationScreen(
    engine: MatrixEngine,
    canUseRecoveryKey: Boolean,
    aUnAppareilTemoin: Boolean = true,
    /**
     * "later", and null removes the link
     *
     * this screen used to block hard, while it often needs two people coordinating in real time:
     * somebody installing the app alone one afternoon had no way forward. what they risk by coming
     * in unattested is an unreadable history, not a leak. forbidding it was out of proportion
     */
    onPlusTard: (() -> Unit)? = null,
    onVerifie: () -> Unit = {},
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()

    var cle by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    // the dead end, named. no device to ask and no backup to unlock, so both usual paths are closed
    // doors, and that is exactly the situation of somebody who just registered. this screen was
    // showing them both, with nothing behind either
    val impasse = !aUnAppareilTemoin && !canUseRecoveryKey

    // the emoji path is the default: in a household there is nearly always a second device already
    // connected, and comparing seven symbols is nothing like retyping 48 case sensitive characters
    var cheminClé by remember { mutableStateOf(false) }
    var cheminEmoji by remember { mutableStateOf(false) }

    if (cheminEmoji) {
        EmojiVerificationScreen(
            engine = engine,
            onCancel = { cheminEmoji = false },
            onVerifie = onVerifie,
        )
        return
    }

    Box(
        // safeDrawingPadding and not imePadding alone. this screen scrolls, and a scrolling
        // child passes under the status bar: at a large font scale the text collides with the
        // clock, measured on a 1080x2400 at scale 2.0. safeDrawing covers the status bar, the
        // navigation bar, the cutout and the keyboard at once, and the background stays edge to
        // edge because it is painted before the padding
        Modifier.fillMaxSize().background(tokens.bg).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(tokens.espEcran),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (impasse) t.premierAppareil else t.verifierCetAppareil,
                color = tokens.textPrimary,
                fontSize = tokens.typography.headingSize.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (impasse) {
                    t.premierAppareilExplication
                } else {
                    t.sansVerificationExplication
                },
                color = tokens.textMuted,
                fontSize = tokens.typography.captionSize.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = tokens.espM, bottom = 24.dp),
            )

            if (impasse) {
                EtablirIdentite(engine = engine, onVerifie = onVerifie)
                LienPlusTard(onPlusTard)
                return@Column
            }

            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.accent)
                    .curseurMain().clickable { cheminEmoji = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.verifierDepuisUnAutre,
                    color = tokens.bg,
                    fontWeight = FontWeight.Bold,
                    fontSize = tokens.typography.bodySize.sp,
                )
            }
            Text(
                t.septSymboles,
                color = tokens.textMuted,
                fontSize = tokens.typography.captionSize.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = tokens.espS),
            )

            // the fallback, deliberately quiet: it is the recourse when no other device is
            // reachable, not the path we offer first
            if (!cheminClé) {
                Text(
                    t.jaiUneCle,
                    color = tokens.textSecondary,
                    fontSize = tokens.typography.captionSize.sp,
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .curseurMain().clickable { cheminClé = true },
                )
            }

            if (cheminClé) {
            // paste rather than retype, and that is the whole subject
            //
            // the screen was unacceptable for a precise reason: it asked people to retype 48 case
            // sensitive characters, where `l`, `1` and `I` look alike. a recovery key nearly always
            // lives in a password manager or a note, which is to say in the clipboard, one gesture
            // away. the field stays for anybody who has it on paper, it is simply no longer the
            // only path
            val presse = colleur()
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.cleDeRecuperation,
                    color = tokens.textSecondary,
                    fontSize = tokens.typography.captionSize.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    t.coller,
                    color = tokens.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = tokens.typography.captionSize.sp,
                    modifier = Modifier
                        .curseurMain().clickable {
                            val colle = presse().trim()
                            if (colle.isBlank()) {
                                erreur = t.pressePapierVide
                            } else {
                                cle = colle
                                erreur = null
                            }
                        }
                        .padding(start = tokens.espM, top = tokens.espXs, bottom = tokens.espXs),
                )
            }

            Box(
                Modifier.fillMaxWidth().padding(top = 6.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .padding(horizontal = tokens.espM, vertical = tokens.espM)
            ) {
                if (cle.isEmpty()) {
                    Text(
                        "EsTg XtTm hALN …",
                        color = tokens.textMuted,
                        fontSize = tokens.typography.codeSize.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                BasicTextField(
                    value = cle,
                    onValueChange = { cle = it; erreur = null },
                    enabled = !enCours,
                    // monospace: a recovery key is read character by character, and a proportional
                    // font makes `l`, `1` and `I` indistinguishable
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        color = tokens.textPrimary,
                        fontSize = tokens.typography.codeSize.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // the counter, because a key truncated while copying is the commonest and quietest
            // failure. 48 characters expected, and the screen says so before refusing rather than
            // after
            val propre = cle.replace(" ", "").trim()
            if (propre.isNotEmpty() && propre.length != 48) {
                Text(
                    t.caracteresSur(propre.length, 48),
                    color = tokens.textMuted,
                    fontSize = tokens.typography.captionSize.sp,
                    modifier = Modifier.padding(top = tokens.espS),
                )
            }

            erreur?.let {
                Text(
                    it,
                    color = tokens.danger,
                    fontSize = tokens.typography.captionSize.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            val actif = cle.isNotBlank() && !enCours
            Box(
                Modifier.fillMaxWidth().padding(top = 18.dp)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(if (actif) tokens.accent else tokens.surfaceRaised)
                    .curseurMain().clickable(enabled = actif) {
                        enCours = true
                        erreur = null
                        scope.launch {
                            runCatching {
                                // the spaces are there for human reading, and the protocol does not
                                // want them. stripping them here avoids an incomprehensible failure
                                // on a key that was copied correctly
                                engine.recovery.recover(cle.replace(" ", "").trim())
                            }.onFailure {
                                erreur = t.cleRefusee
                            }
                            enCours = false
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (enCours) t.verificationPoints else t.verifier,
                    color = if (actif) tokens.bg else tokens.textMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = tokens.typography.bodySize.sp,
                )
            }

            }

            if (!canUseRecoveryKey) {
                Text(
                    t.aucuneSauvegardeActive,
                    color = tokens.textMuted,
                    fontSize = tokens.typography.captionSize.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = tokens.espXl),
                )
            }

            LienPlusTard(onPlusTard)
        }
    }
}

/**
 * the "later" link, quiet and never promoted
 *
 * quiet but present on both paths, the first device dead end as well as the ordinary route: it is
 * precisely when nothing works that you most need a way out. the text says what you lose and not
 * only that you may leave, since postponing knowingly is not the same as postponing because the
 * button was there
 */
@Composable
private fun LienPlusTard(onPlusTard: (() -> Unit)?) {
    val t = textes()
    val tokens = LocalTokens.current
    if (onPlusTard == null) return
    Text(
        t.plusTard,
        color = tokens.textSecondary,
        fontWeight = FontWeight.Bold,
        fontSize = tokens.typography.captionSize.sp,
        modifier = Modifier.padding(top = 26.dp).curseurMain().clickable(onClick = onPlusTard).padding(tokens.espS),
    )
    Text(
        t.plusTardExplication,
        color = tokens.textMuted,
        fontSize = tokens.typography.captionSize.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = tokens.espXs),
    )
}

/**
 * the way out of the dead end, see `VerificationEngine.etablirIdentite`
 *
 * the password gets asked for again because the server asks for it again: replacing an account's
 * signing keys is exactly the sort of thing that must not be triggerable on an unlocked phone left
 * on a table
 */
@Composable
private fun EtablirIdentite(engine: MatrixEngine, onVerifie: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    var motDePasse by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    Text(
        t.motDePasseDuCompte,
        color = tokens.textSecondary,
        fontSize = tokens.typography.captionSize.sp,
        modifier = Modifier.fillMaxWidth(),
    )
    Box(
        Modifier.fillMaxWidth().padding(top = 6.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised)
            .padding(horizontal = tokens.espM, vertical = tokens.espM)
    ) {
        BasicTextField(
            value = motDePasse,
            onValueChange = { motDePasse = it; erreur = null },
            enabled = !enCours,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                color = tokens.textPrimary,
                fontSize = tokens.typography.bodySize.sp,
            ),
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    erreur?.let {
        Text(
            it,
            color = tokens.danger,
            fontSize = tokens.typography.captionSize.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    val actif = motDePasse.isNotBlank() && !enCours
    Box(
        Modifier.fillMaxWidth().padding(top = 18.dp)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(if (actif) tokens.accent else tokens.surfaceRaised)
            .curseurMain().clickable(enabled = actif) {
                enCours = true
                erreur = null
                scope.launch {
                    runCatching { engine.verification.etablirIdentite(motDePasse) }
                        // `onVerifie` and not waiting on the flow: after an identity reset,
                        // `verificationState` takes a moment to flip, and the screen would stay up
                        // while the work is done. the same observation that got the comparison
                        // screen its own local latch
                        .onSuccess { onVerifie() }
                        .onFailure {
                            erreur = it.message ?: t.identiteImpossible
                            enCours = false
                        }
                }
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (enCours) t.enCours else t.etablirCetteIdentite,
            color = if (actif) tokens.bg else tokens.textMuted,
            fontWeight = FontWeight.Bold,
            fontSize = tokens.typography.bodySize.sp,
        )
    }
}

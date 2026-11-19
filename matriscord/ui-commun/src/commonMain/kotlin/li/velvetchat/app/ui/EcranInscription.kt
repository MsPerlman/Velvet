package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * the registration screen
 *
 * `registration_requires_token: true` has been set server side from the start, so registration was
 * never closed, it simply had no screen. what this file adds therefore opens nothing new, it
 * finally makes reachable what the server already accepted
 *
 * one "invite code" field and not two separate ones. the code carries the token and the space to
 * join ([li.velvetchat.core.model.CodeInvitation]), and asking for both separately would make
 * whoever invites pass along two distinct pieces of information, and whoever registers not confuse
 * them. one block to copy and paste, like any invite code anybody already knows
 *
 * it reuses [EcranConnexion]'s structure rather than inventing one: same fields, same button, same
 * place for the error. two screens doing neighbouring things and looking alike betray less than a
 * form you have to relearn
 */
@Composable
fun EcranInscription(
    serveurParDefaut: String,
    onInscription: suspend (serveur: String, identifiant: String, motDePasse: String, code: String) -> Unit,
    onRetour: () -> Unit,
    parNavigateur: suspend (serveur: String) -> Boolean = { false },
    onInscriptionNavigateur: suspend (serveur: String, code: String) -> Unit = { _, _ -> },
) {
    val t = textes()
    val pressePapier = androidx.compose.ui.platform.LocalClipboardManager.current
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()

    var serveur by remember { mutableStateOf(serveurParDefaut) }
    var identifiant by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var copie by remember { mutableStateOf(false) }
    val navigateur = navigateurPour(serveur, parNavigateur)

    val valide = serveur.isNotBlank() && code.isNotBlank() &&
        (navigateur || identifiant.isNotBlank() && motDePasse.isNotBlank())

    fun tenter() {
        if (!valide || enCours) return
        enCours = true
        erreur = null
        scope.launch {
            runCatching {
                if (navigateur) {
                    // MAS asks for the token on its own page and offers no way to pass it along:
                    // it goes to the clipboard, and the screen says so
                    li.velvetchat.core.model.CodeInvitation.decoder(code.trim())?.let {
                        pressePapier.setText(androidx.compose.ui.text.AnnotatedString(it.jeton))
                        copie = true
                    }
                    onInscriptionNavigateur(adresseServeur(serveur), code.trim())
                } else {
                    onInscription(adresseServeur(serveur), identifiant.trim(), motDePasse, code.trim())
                }
            }.onFailure {
                erreur = it.message ?: t.inscriptionImpossible
                enCours = false
            }
        }
    }

    Box(
        // safeDrawingPadding and not imePadding alone. this screen scrolls, and a scrolling
        // child passes under the status bar: at a large font scale the text collides with the
        // clock, measured on a 1080x2400 at scale 2.0. safeDrawing covers the status bar, the
        // navigation bar, the cutout and the keyboard at once, and the background stays edge to
        // edge because it is painted before the padding
        Modifier.fillMaxSize().background(tokens.bg).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                t.rejoindreVelvet,
                color = tokens.accent,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                t.creerCompteAvecCode,
                color = tokens.textMuted,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
            )

            Champ(t.champServeur, serveur, { serveur = it }, KeyboardType.Uri)
            if (!navigateur) {
                Champ(t.champIdentifiant, identifiant, { identifiant = it }, KeyboardType.Text)
                Champ(t.champMotDePasse, motDePasse, { motDePasse = it }, KeyboardType.Password, secret = true)
            }
            Champ(
                t.codeDInvitation,
                code,
                { code = it },
                KeyboardType.Ascii,
                onValider = { tenter() },
            )

            if (copie && enCours) {
                Text(
                    t.codeCopie,
                    color = tokens.textSecondary,
                    fontSize = tokens.typography.captionSize.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            erreur?.let {
                Text(
                    it,
                    color = tokens.danger,
                    fontSize = tokens.typography.captionSize.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            BoutonPlein(
                libelle = when {
                    enCours && navigateur -> t.attenteNavigateur
                    enCours -> t.inscriptionEnCours
                    navigateur -> t.continuerNavigateur
                    else -> t.creerLeCompte
                },
                actif = valide && !enCours,
                modifier = Modifier.padding(top = 22.dp),
                onClick = { tenter() },
            )

            Text(
                t.dejaUnCompte,
                color = tokens.textSecondary,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier.padding(top = 26.dp).curseurMain().clickable(onClick = onRetour),
            )
        }
    }
}

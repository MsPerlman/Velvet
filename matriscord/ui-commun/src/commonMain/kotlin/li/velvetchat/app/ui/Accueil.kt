package li.velvetchat.app.ui

import li.velvetchat.core.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch

/**
 * the login screen
 *
 * until now the credentials came from `BuildConfig`, which is to say from the build: the app
 * therefore had exactly one possible user, whoever compiled it. that was the project's real
 * ceiling, higher than verification, which already assumes you got in
 *
 * the server is asked for, not guessed. matrix is federated, and an address does not say where the
 * server is, only which domain names it. the field is prefilled with the configured one, the common case,
 * and stays editable, otherwise the app would be tied to one server by its interface alone, when
 * the engine never had that limit
 */
/**
 * completes a hand typed homeserver address
 *
 * nobody types the scheme in front of a domain name, and the engine wants a url: with no scheme the
 * connection fails with "homeserver unreachable", a message that sends people hunting a network
 * fault when six characters were missing
 *
 * `https` and nothing else. an already complete address is left alone, `http://` included, because
 * that is what a development homeserver on the local network wants. but we never fall back to it on
 * our own: guessing http for an address that does not answer on https would downgrade the transport
 * encryption with nobody asking
 */
internal fun adresseServeur(saisi: String): String {
    val propre = saisi.trim().trimEnd('/')
    return if (propre.startsWith("http://") || propre.startsWith("https://")) propre
    else "https://$propre"
}

@Composable
fun EcranConnexion(
    serveurParDefaut: String,
    onConnexion: suspend (String, String, String) -> Unit,
    onInscription: suspend (serveur: String, identifiant: String, motDePasse: String, code: String) -> Unit = { _, _, _, _ -> },
    /** does this server log in through the browser (MAS). asked while the address is typed */
    parNavigateur: suspend (serveur: String) -> Boolean = { false },
    onConnexionNavigateur: suspend (serveur: String) -> Unit = {},
    onInscriptionNavigateur: suspend (serveur: String, code: String) -> Unit = { _, _ -> },
) {
    val tokens = LocalTokens.current
    val t = textes()
    val scope = rememberCoroutineScope()

    // same gesture as "i have a recovery key" on the verification screen: a second path, revealed
    // rather than offered up front, because logging in stays the common case. most openings of the
    // app are a return, not an arrival
    var inscriptionOuverte by remember { mutableStateOf(false) }
    if (inscriptionOuverte) {
        EcranInscription(
            serveurParDefaut = serveurParDefaut,
            onInscription = onInscription,
            onRetour = { inscriptionOuverte = false },
            parNavigateur = parNavigateur,
            onInscriptionNavigateur = onInscriptionNavigateur,
        )
        return
    }

    var serveur by remember { mutableStateOf(serveurParDefaut) }
    var identifiant by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    val navigateur = navigateurPour(serveur, parNavigateur)

    val valide = serveur.isNotBlank() && (navigateur || identifiant.isNotBlank() && motDePasse.isNotBlank())

    fun tenter() {
        if (!valide || enCours) return
        enCours = true
        erreur = null
        scope.launch {
            runCatching {
                if (navigateur) onConnexionNavigateur(adresseServeur(serveur))
                else onConnexion(adresseServeur(serveur), identifiant.trim(), motDePasse)
            }
                .onFailure {
                    // the engine's message is shown as it is: "wrong password" and "server
                    // unreachable" call for two different actions, and a generic "could not
                    // connect" would conflate them
                    erreur = it.message ?: t.connexionImpossible
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
                t.connexionTitre,
                color = tokens.accent,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                t.connexionSousTitre,
                color = tokens.textMuted,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
            )

            Champ(t.champServeur, serveur, { serveur = it }, KeyboardType.Uri)
            if (navigateur) {
                Text(
                    t.explicationNavigateur,
                    color = tokens.textMuted,
                    fontSize = tokens.typography.captionSize.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                Champ(t.champIdentifiant, identifiant, { identifiant = it }, KeyboardType.Text)
                Champ(
                    t.champMotDePasse,
                    motDePasse,
                    { motDePasse = it },
                    KeyboardType.Password,
                    secret = true,
                    onValider = { tenter() },
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
                    enCours -> t.rechercheEnCours
                    navigateur -> t.continuerNavigateur
                    else -> t.seConnecter
                },
                actif = valide && !enCours,
                modifier = Modifier.padding(top = 22.dp),
                onClick = { tenter() },
            )

            Text(
                t.avertissementVerification,
                color = tokens.textMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )

            Text(
                t.pasDeCompte,
                color = tokens.textSecondary,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier.padding(top = 18.dp)
                    .curseurMain().clickable { inscriptionOuverte = true },
            )
        }
    }
}

/**
 * whether [serveur] logs in through the browser, asked half a second after typing stops. false
 * until the server has answered: the password form is the safe default, it is what a server
 * without MAS wants
 */
@Composable
internal fun navigateurPour(serveur: String, parNavigateur: suspend (String) -> Boolean): Boolean {
    var navigateur by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(serveur) {
        if (serveur.isBlank()) { navigateur = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(500)
        navigateur = runCatching { parNavigateur(adresseServeur(serveur)) }.getOrDefault(false)
    }
    return navigateur
}

/**
 * a labelled field
 *
 * the label is above and permanent, not ghost text inside the field: an indicator that disappears
 * as soon as you type makes you erase in order to remember what you were filling in. the fault is a
 * classic and costs most on a form you fill in once, where nothing has been learned yet
 */
@Composable
internal fun Champ(
    etiquette: String,
    valeur: String,
    onChange: (String) -> Unit,
    type: KeyboardType,
    secret: Boolean = false,
    onValider: () -> Unit = {},
) {
    val tokens = LocalTokens.current
    Column(Modifier.fillMaxWidth().padding(bottom = tokens.espM)) {
        Text(
            etiquette,
            color = tokens.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 5.dp),
        )
        Box(
            Modifier.fillMaxWidth()
                .background(tokens.surfaceRaised, RoundedCornerShape(tokens.radiusMedium))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            BasicTextField(
                value = valeur,
                onValueChange = onChange,
                singleLine = true,
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = tokens.textPrimary,
                    fontSize = tokens.typography.bodySize.sp,
                ),
                visualTransformation = if (secret) PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = type,
                    imeAction = if (secret) ImeAction.Go else ImeAction.Next,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onGo = { onValider() },
                ),
                cursorBrush = SolidColor(tokens.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * the primary button, filled
 *
 * disabled, it stays visible instead of disappearing: an absent button suggests the screen has no
 * way out, where a greyed one says something above it is missing
 */
@Composable
fun BoutonPlein(
    libelle: String,
    actif: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Box(
        modifier.fillMaxWidth()
            .background(
                if (actif) tokens.accent else tokens.surfaceRaised,
                RoundedCornerShape(tokens.radiusMedium),
            )
            .curseurMain().clickable(enabled = actif, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            libelle,
            color = if (actif) tokens.bg else tokens.textMuted,
            fontWeight = FontWeight.Bold,
            fontSize = tokens.typography.bodySize.sp,
        )
    }
}

/**
 * create a server, or join one
 *
 * both in the same sheet, because it is the same question asked at two moments: whoever installs
 * the app first creates, the others join. splitting them into two entries would mean knowing which
 * one you are before seeing what each does
 */
@Composable
fun FeuilleServeur(
    /**
     * returns immediately, not when the server answers
     *
     * creating a server is two round trips, the space then its general channel. waiting for them
     * behind a greyed button gave a several second hourglass, and sometimes a failure message about
     * a server that had in fact been created. the caller makes the server appear at once and pushes
     * to synapse in the background, and the sheet has nothing left to wait for and closes
     *
     * not suspending, which is what guarantees it: the sheet leaves the composition on close, and
     * any coroutine it carried would be cancelled with it
     */
    onCreer: (String) -> Unit,
    onRejoindre: suspend (String) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    var nom by remember { mutableStateOf("") }
    var adresse by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    fun lancer(action: suspend () -> Unit) {
        if (enCours) return
        enCours = true
        erreur = null
        scope.launch {
            runCatching { action() }
                .onSuccess { onFermer() }
                .onFailure { erreur = it.message ?: t.echec; enCours = false }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(tokens.bg.copy(alpha = tokens.colors.scrimAlpha))
                .curseurMain().clickable(onClick = onFermer)
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(
                    tokens.surface,
                    RoundedCornerShape(topStart = tokens.radiusLarge, topEnd = tokens.radiusLarge),
                )
                .imePadding()
                .padding(tokens.espXl)
        ) {
            Text(
                t.creerUnServeur,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.headingSize.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Champ(t.nom, nom, { nom = it }, KeyboardType.Text)
            // no more "creating…": there is nothing left to wait for here. the server appears in
            // the rail before synapse has even answered, and the sheet withdraws straight away
            BoutonPlein(t.creer, actif = nom.isNotBlank() && !enCours) {
                onCreer(nom.trim())
                onFermer()
            }

            Box(
                Modifier.fillMaxWidth().padding(vertical = 22.dp)
                    .background(tokens.border)
                    .padding(top = 1.dp)
            )

            Text(
                t.rejoindreUnServeur,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.headingSize.sp,
                modifier = Modifier.padding(bottom = tokens.espXs),
            )
            Text(
                tr("Le lien d'invitation reçu (https://velvet.exemple.org/rejoindre#…), ", "The invite link you got (https://velvet.example.org/rejoindre#…), ") +
                    tr("ou une adresse du genre #famille:exemple.org", "or an address like #friends:example.org"),
                color = tokens.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Champ(t.lienOuAdresse, adresse, { adresse = it }, KeyboardType.Text)
            BoutonPlein(
                if (enCours) t.connexionEnCours else t.rejoindre,
                actif = adresse.isNotBlank() && !enCours,
            ) {
                lancer { onRejoindre(adresse.trim()) }
            }

            erreur?.let {
                Text(
                    it,
                    color = tokens.danger,
                    fontSize = tokens.typography.captionSize.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

/**
 * inviting somebody
 *
 * by matrix id and not by link: an invite link needs a web page and a server to serve it. the id
 * works straight away, and it is what gets passed around anyway
 */
@Composable
fun FeuilleInvitation(
    nomDuServeur: String,
    onInviter: suspend (String) -> Unit,
    onFermer: () -> Unit,
    /** returns the server's invite link, and opens it to links. null hides the button */
    onLien: (suspend () -> String)? = null,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    var identifiant by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reussi by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(tokens.bg.copy(alpha = tokens.colors.scrimAlpha))
                .curseurMain().clickable(onClick = onFermer)
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(
                    tokens.surface,
                    RoundedCornerShape(topStart = tokens.radiusLarge, topEnd = tokens.radiusLarge),
                )
                .imePadding()
                .padding(tokens.espXl)
        ) {
            Text(
                t.inviterDansServeur(nomDuServeur),
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.headingSize.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Champ(t.identifiantMatrix, identifiant, { identifiant = it }, KeyboardType.Text)
            BoutonPlein(t.inviter, actif = identifiant.startsWith("@") && !enCours) {
                enCours = true
                message = null
                scope.launch {
                    runCatching { onInviter(identifiant.trim()) }
                        .onSuccess { reussi = true; message = t.invitationEnvoyee }
                        .onFailure { reussi = false; message = it.message ?: t.invitationEchouee }
                    enCours = false
                }
            }
            if (onLien != null) {
                val pressePapier = copieur()
                Box(Modifier.padding(top = 10.dp)) {
                    BoutonPlein(t.copierLienInvitation, actif = !enCours) {
                        enCours = true
                        message = null
                        scope.launch {
                            runCatching { onLien() }
                                .onSuccess {
                                    pressePapier(it)
                                    reussi = true
                                    message = t.lienCopie
                                }
                                .onFailure { reussi = false; message = t.lienReserveAdmin }
                            enCours = false
                        }
                    }
                }
            }
            message?.let {
                Text(
                    it,
                    color = if (reussi) tokens.accent else tokens.danger,
                    fontSize = tokens.typography.captionSize.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

/** the word shown when a tab has nothing to show. never a blank screen */
@Composable
fun Vide(message: String, precision: String? = null) {
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            color = tokens.textSecondary,
            fontSize = tokens.typography.bodySize.sp,
        )
        precision?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = tokens.textMuted, fontSize = 12.sp)
        }
    }
}

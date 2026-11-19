package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.CompteServeur
import li.velvetchat.core.model.JetonInvitation
import li.velvetchat.core.model.Server

/**
 * the control centre
 *
 * two panels, deliberately, no more: the invites and the accounts. server statistics, room
 * management, gdpr purging an account are genuine control centre ambitions, and not what was needed
 * to answer the day's problem, opening registration without opening it to the whole internet. they
 * will come the day they answer a real need, not because the screen's name calls for them
 *
 * only visible to somebody who already passed the server check. this screen only opens when
 * `estAdministrateurServeur()` said yes (see `EcranProfil`), and every call it makes still goes
 * through the admin api, which closes the door for real every time. that is not cautious
 * repetition, it is the same thing that already protects a room's moderate action, applied to the
 * whole server
 */
@Composable
fun EcranAdministration(
    engine: MatrixEngine,
    serveurs: List<Server>,
    onFermer: () -> Unit,
    /**
     * under MAS a login carries no admin rights, they are asked for separately, through the
     * browser. null when there is nothing to ask for
     */
    onActiverAdministration: (suspend () -> Unit)? = null,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var onglet by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(tokens.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = tokens.espM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(
                Icones.retour,
                tint = tokens.textSecondary,
                taille = 22.dp,
                description = t.retour,
                modifier = Modifier.curseurMain().clickable(onClick = onFermer).padding(end = tokens.espM),
            )
            Text(
                t.centreDeControle,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = tokens.typography.headingSize.sp,
            )
        }

        var admin by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) { admin = runCatching { engine.estAdministrateurServeur() }.getOrDefault(false) }
        if (admin == false && onActiverAdministration != null) {
            val scope = rememberCoroutineScope()
            var enCours by remember { mutableStateOf(false) }
            var erreur by remember { mutableStateOf<String?>(null) }
            Column(Modifier.fillMaxWidth().padding(tokens.espL), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    t.explicationAdministration,
                    color = tokens.textSecondary,
                    fontSize = tokens.typography.bodySize.sp,
                    modifier = Modifier.padding(bottom = tokens.espL),
                )
                erreur?.let { Text(it, color = tokens.danger, fontSize = tokens.typography.captionSize.sp) }
                BoutonPlein(
                    libelle = if (enCours) t.attenteNavigateur else t.activerAdministration,
                    actif = !enCours,
                    modifier = Modifier.padding(top = tokens.espM),
                ) {
                    enCours = true
                    erreur = null
                    scope.launch {
                        runCatching { onActiverAdministration() }
                            .onSuccess { admin = runCatching { engine.estAdministrateurServeur() }.getOrDefault(false) }
                            .onFailure { erreur = it.message }
                        enCours = false
                    }
                }
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = tokens.espM, vertical = tokens.espXs)) {
            listOf(t.invitations, t.membres).forEachIndexed { index, libelle ->
                val actif = onglet == index
                Text(
                    libelle,
                    color = if (actif) tokens.textPrimary else tokens.textMuted,
                    fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(tokens.radiusMedium))
                        .background(if (actif) tokens.surfaceRaised else Color.Transparent)
                        .curseurMain().clickable { onglet = index }
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        if (onglet == 0) OngletInvitations(engine, serveurs) else OngletMembres(engine)
    }
}

@Composable
private fun OngletInvitations(engine: MatrixEngine, serveurs: List<Server>) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()

    var jetons by remember { mutableStateOf<List<JetonInvitation>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { engine.jetonsInvitation() }
            .onSuccess { jetons = it }
            .onFailure { erreur = it.message ?: t.lectureImpossible }
    }

    var creationOuverte by remember { mutableStateOf(false) }
    if (creationOuverte) {
        FeuilleCreerInvitation(
            serveurs = serveurs,
            onCreer = { idEspace, usages, expirationJours ->
                val code = engine.creerInvitation(idEspace, usages, expirationJours)
                // the list is reread so the tab reflects the new token once the sheet closes,
                // without holding up the code we just got
                scope.launch {
                    jetons = runCatching { engine.jetonsInvitation() }.getOrNull() ?: jetons
                }
                code
            },
            onFermer = { creationOuverte = false },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(tokens.espL)) {
            BoutonPlein(t.genererUneInvitation, onClick = { creationOuverte = true })
        }

        when {
            erreur != null -> Vide(t.invitationsIllisibles, erreur)
            jetons == null -> Vide(t.chargement)
            jetons!!.isEmpty() -> Vide(
                t.aucuneInvitationActive,
                t.genererInvitationAide,
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(jetons!!, key = { it.jeton }) { jeton ->
                    LigneJeton(
                        jeton = jeton,
                        nomEspace = jeton.idEspace?.let { id ->
                            serveurs.firstOrNull { it.id == id }?.name ?: id
                        },
                        onRevoquer = {
                            scope.launch {
                                runCatching { engine.revoquerJeton(jeton.jeton) }
                                jetons = runCatching { engine.jetonsInvitation() }
                                    .getOrNull() ?: jetons
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LigneJeton(jeton: JetonInvitation, nomEspace: String?, onRevoquer: () -> Unit) {
    val t = textes()
    val tokens = LocalTokens.current
    val presse = copieur()
    var copie by remember(jeton.jeton) { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                jeton.jeton,
                color = tokens.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(
                        when (val restants = jeton.usagesRestants) {
                            null -> t.usagesIllimites
                            0 -> t.invitationEpuisee
                            else -> t.usagesRestants(restants)
                        }
                    )
                    jeton.expiration?.let {
                        append(t.expireLe(formaterDate(it)))
                    }
                    nomEspace?.let { append(" · $it") }
                },
                color = tokens.textMuted,
                fontSize = 12.sp,
            )
        }

        // copy the code again without regenerating. it only appears once, at creation, and closing
        // the sheet without copying it used to mean revoking and starting over. the button only
        // exists when the space is known: without it there is no code to rebuild, only a bare token
        jeton.idEspace?.let { idEspace ->
            Text(
                if (copie) t.copie else t.copier,
                color = if (copie) tokens.textMuted else tokens.accent,
                fontSize = 13.sp,
                modifier = Modifier.curseurMain().clickable {
                    presse(
                            li.velvetchat.core.model.CodeInvitation
                                .encoder(jeton.jeton, idEspace)
                        )
                    copie = true
                }.padding(tokens.espS),
            )
        }

        Text(
            t.revoquer,
            color = tokens.danger,
            fontSize = 13.sp,
            modifier = Modifier.curseurMain().clickable(onClick = onRevoquer).padding(tokens.espS),
        )
    }
}

/**
 * the choice `auto_join_rooms` was missing: which space this particular link joins. one token per
 * intent rather than a single rule for everybody, forever
 */
@Composable
private fun FeuilleCreerInvitation(
    serveurs: List<Server>,
    onCreer: suspend (idEspace: String, usages: Int?, expirationJours: Int?) -> String,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()
    var espace by remember { mutableStateOf(serveurs.firstOrNull()) }
    var usages by remember { mutableStateOf<Int?>(1) }
    var expirationJours by remember { mutableStateOf<Int?>(7) }
    var codeGenere by remember { mutableStateOf<String?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var erreurCreation by remember { mutableStateOf<String?>(null) }
    val presse = copieur()

    FeuilleBasse(onFermer = onFermer) {
        if (codeGenere != null) {
            Text(
                t.invitationCreee,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.padding(horizontal = tokens.espXl, vertical = 10.dp),
            )
            Text(
                t.codeEstLInvitation,
                color = tokens.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = 10.dp),
            )
            Box(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espXl)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .padding(14.dp)
            ) {
                Text(
                    codeGenere!!,
                    color = tokens.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
            Box(Modifier.fillMaxWidth().padding(tokens.espXl)) {
                BoutonPlein(
                    t.copierLeCode,
                    onClick = { presse(codeGenere!!) },
                )
            }
            return@FeuilleBasse
        }

        Text(
            t.nouvelleInvitation,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = 10.dp),
        )

        Text(
            t.espaceRejointAuto,
            color = tokens.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = tokens.espXl, bottom = 6.dp),
        )
        if (serveurs.isEmpty()) {
            Text(
                t.aucunEspaceAProposer,
                color = tokens.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = tokens.espM),
            )
        }
        serveurs.forEach { serveur ->
            Row(
                Modifier.fillMaxWidth().curseurMain().clickable { espace = serveur }
                    .padding(horizontal = tokens.espXl, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Coche(espace?.id == serveur.id, taille = 18.dp)
                Spacer(Modifier.width(12.dp))
                Text(serveur.name, color = tokens.textPrimary, fontSize = 14.sp)
            }
        }

        Text(
            t.usages,
            color = tokens.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = tokens.espXl, top = 10.dp, bottom = 6.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = tokens.espXl)) {
            listOf(t.unePersonne to 1, t.cinqPersonnes to 5, t.illimite to null).forEach { (libelle, valeur) ->
                ChoixCourt(libelle, usages == valeur, Modifier.weight(1f)) { usages = valeur }
            }
        }

        Text(
            t.expiration,
            color = tokens.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = tokens.espXl, top = 14.dp, bottom = 6.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = tokens.espXl)) {
            listOf(t.septJours to 7, t.trenteJours to 30, t.jamais to null).forEach { (libelle, valeur) ->
                ChoixCourt(libelle, expirationJours == valeur, Modifier.weight(1f)) {
                    expirationJours = valeur
                }
            }
        }

        erreurCreation?.let {
            Text(
                it,
                color = tokens.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, top = 10.dp),
            )
        }

        Box(Modifier.fillMaxWidth().padding(tokens.espXl)) {
            BoutonPlein(
                if (enCours) t.generationEnCours else t.generer,
                actif = espace != null && !enCours,
                onClick = {
                    val cible = espace ?: return@BoutonPlein
                    enCours = true
                    erreurCreation = null
                    scope.launch {
                        runCatching { onCreer(cible.id, usages, expirationJours) }
                            .onSuccess { codeGenere = it }
                            .onFailure {
                                erreurCreation = it.message ?: t.creationImpossible
                                enCours = false
                            }
                    }
                },
            )
        }
    }
}

@Composable
private fun ChoixCourt(libelle: String, actif: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    Box(
        modifier.padding(tokens.espXs)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(if (actif) tokens.accent else tokens.surfaceRaised)
            .curseurMain().clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            libelle,
            color = if (actif) tokens.bg else tokens.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun OngletMembres(engine: MatrixEngine) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()

    var comptes by remember { mutableStateOf<List<CompteServeur>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { engine.comptesServeur() }
            .onSuccess { comptes = it }
            .onFailure { erreur = it.message ?: t.lectureImpossible }
    }

    suspend fun basculer(compte: CompteServeur) {
        runCatching { engine.setCompteDesactive(compte.userId, !compte.desactive) }
        comptes = runCatching { engine.comptesServeur() }.getOrNull() ?: comptes
    }

    // disabling asks for a confirmation and re enabling does not, and that is not forgotten
    // symmetry: measured against the server, disabling erases the password. the person loses access
    // and "re enable" does not give it back. a gesture with no way back must not sit one tap away,
    // next to a name
    var aDesactiver by remember { mutableStateOf<CompteServeur?>(null) }
    aDesactiver?.let { cible ->
        FeuilleBasse(onFermer = { aDesactiver = null }) {
            Text(
                t.desactiverQui(cible.displayName ?: cible.userId),
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.padding(horizontal = tokens.espXl, vertical = 10.dp),
            )
            Text(
                t.desactivationExplication,
                color = tokens.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = 6.dp),
            )
            Box(Modifier.fillMaxWidth().padding(tokens.espXl)) {
                BoutonPlein(
                    t.desactiver,
                    onClick = {
                        aDesactiver = null
                        scope.launch { basculer(cible) }
                    },
                )
            }
        }
    }

    when {
        erreur != null -> Vide(t.comptesIllisibles, erreur)
        comptes == null -> Vide(t.chargement)
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(comptes!!, key = { it.userId }) { compte ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = tokens.espL, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(compte.userId, compte.displayName, null, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                compte.displayName
                                    ?: compte.userId.removePrefix("@").substringBefore(':'),
                                color = if (compte.desactive) tokens.textMuted else tokens.textPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (compte.administrateur) {
                                Spacer(Modifier.width(6.dp))
                                Text("👑", fontSize = 12.sp)
                            }
                        }
                        Text(
                            if (compte.desactive) t.desactive else compte.userId,
                            color = tokens.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        if (compte.desactive) t.reactiver else t.desactiver,
                        color = if (compte.desactive) tokens.accent else tokens.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.curseurMain().clickable {
                            if (compte.desactive) scope.launch { basculer(compte) }
                            else aDesactiver = compte
                        }.padding(tokens.espS),
                    )
                }
            }
        }
    }
}

private fun formaterDate(epochMs: Long): String {
    val format = java.text.SimpleDateFormat("d MMMM yyyy", localeCourante())
    return format.format(java.util.Date(epochMs))
}

package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * the settings, filed behind one single button
 *
 * what lives here was lying at the bottom of the profile screen: the storage, the problem report
 * and the legal notices piled up under the bio, between a role badge and a warning about the photo.
 * none of that is somebody's profile
 *
 * the reference files exactly these things behind settings, in the floating bar at the bottom of
 * its profile page. that is the arrangement taken here, on request: add a settings button to file
 * all the buttons into, report a problem, storage, and about
 *
 * what is not in it: everything that changes the profile. the nickname, the bio, the pronouns and
 * the cosmetics live in "edit profile", and mixing them would recreate the two competing menus we
 * have just merged
 */
@Composable
fun EcranParametres(
    stockageEnvois: String,
    stockageMoteur: String,
    onViderCache: () -> Unit,
    onFermer: () -> Unit,
    identifiant: String? = null,
    onDeconnexion: () -> Unit = {},
    /** the installed version, as we want it read: "0.55 (55)" */
    version: String = "",
    /** opens the import of a discord export (the service runs on the nas) */
    onImportDiscord: () -> Unit = {},
    /** looks for a published version and returns what there is to say */
    onChercherMaj: suspend () -> String = { "" },
) {
    val tokens = LocalTokens.current
    val t = textes()
    val moteurNotifs = LocalEngine.current
    val porteeNotifs = androidx.compose.runtime.rememberCoroutineScope()
    var modeNotifs by remember {
        mutableStateOf<li.velvetchat.core.model.ModeNotification?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        modeNotifs = runCatching { moteurNotifs.modeNotificationGlobal() }.getOrNull()
    }
    GestionRetour(onRetour = onFermer)

    val contexte = androidx.compose.ui.platform.LocalContext.current
    val portee = rememberCoroutineScope()
    var etatRapport by remember { mutableStateOf<String?>(null) }
    var frappe by remember { mutableStateOf(PrefsStatut.frappePubliee(contexte)) }

    Column(
        Modifier.fillMaxSize().background(tokens.bg).verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(tokens.espM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(
                Icones.retourFleche,
                tint = tokens.textPrimary,
                taille = 22.dp,
                description = t.retour,
                modifier = Modifier.clip(RoundedCornerShape(tokens.radiusMedium))
                    .clickable(onClick = onFermer)
                    .padding(6.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                t.parametres,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
        }

        Column(Modifier.padding(horizontal = tokens.espL)) {

            Categorie(t.stockage)
            Carte {
                Text(
                    t.copiesDEnvoi(stockageEnvois),
                    color = tokens.textSecondary,
                    fontSize = 13.sp,
                )
                Text(
                    t.stockageMoteur(stockageMoteur),
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                BoutonPlein(t.viderCopiesDEnvoi, onClick = onViderCache)
            }

            // typing
            //
            // this setting lived in the status sheet, where it had no business: it does not say how
            // you declare yourself, it says what the app emits on your behalf. the reference has no
            // equivalent, this being a question matrix asks and discord does not
            //
            // notifications
            //
            // the general setting was missing, and it is what "default" follows in each room: you
            // could be silent everywhere (the master rule) or ring for everything, but not ask for
            // "mentions only" in one go. it had to be set room by room, which nobody does
            Categorie(t.notifications)
            Carte {
                Text(
                    t.notificationsAide,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                listOf(
                    li.velvetchat.core.model.ModeNotification.TOUT to
                        (t.notifTout to t.notifToutDetail),
                    li.velvetchat.core.model.ModeNotification.MENTIONS to
                        (t.notifMentions to t.notifMentionsDetail),
                    li.velvetchat.core.model.ModeNotification.MUET to
                        (t.notifMuet to t.notifMuetDetail),
                ).forEach { (mode, libelles) ->
                    val retenu = mode == modeNotifs
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                modeNotifs = mode
                                porteeNotifs.launch {
                                    runCatching { moteurNotifs.setModeNotificationGlobal(mode) }
                                    modeNotifs = runCatching {
                                        moteurNotifs.modeNotificationGlobal()
                                    }.getOrDefault(mode)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                libelles.first,
                                color = if (retenu) tokens.accent else tokens.textPrimary,
                                fontWeight = if (retenu) FontWeight.Bold else FontWeight.Normal,
                                fontSize = tokens.typography.bodySize.sp,
                            )
                            Text(libelles.second, color = tokens.textMuted, fontSize = 12.sp)
                        }
                        if (retenu) {
                            Icone(Icones.valide, tint = tokens.accent, taille = 18.dp)
                        }
                    }
                }
            }

            Categorie(t.confidentialite)
            Carte {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        frappe = !frappe
                        PrefsStatut.setFrappePubliee(contexte, frappe)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.publierMaFrappe,
                            color = tokens.textPrimary,
                            fontSize = tokens.typography.bodySize.sp,
                        )
                        Text(
                            t.frappeAide,
                            color = tokens.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Interrupteur(frappe)
                }
            }

            // problem
            Categorie(t.probleme)
            Carte {
                Text(
                    t.rapportExplication,
                    color = tokens.textMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                BoutonPlein(t.signalerUnProbleme) {
                    portee.launch {
                        val fichier = withContext(Dispatchers.IO) { Rapport.capturer(contexte) }
                        etatRapport = if (fichier == null) {
                            t.journalIllisible
                        } else {
                            contexte.startActivity(Rapport.partage(contexte, fichier))
                            t.rapportPret(fichier.length() / 1024)
                        }
                    }
                }
                etatRapport?.let {
                    Text(
                        it,
                        color = tokens.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // the appearance
            //
            // the loader, the shipped themes and the theme file all existed; the screen was
            // what was missing. see `ReglagesTheme`
            Categorie(t.apparence)
            Carte { ReglagesTheme() }

            Categorie(t.fondDEcran)
            Carte { ReglagesFond() }

            // about
            //
            // an obligation, not a politeness. two of the three embedded sets are under licences
            // requiring attribution, cc-by for twemoji and mit for phosphor. citing them in a code
            // comment does not meet the condition: it asks that the notice accompany the work where
            // it is distributed
            Categorie(t.aProposTitre)
            Carte {
                Text(
                    t.creditEmojis,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                )
                Text(
                    t.creditIcones,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    t.creditPolice,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    t.photoEtBanniereEnClair,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Categorie(t.langue)
            Carte {
                Langue.entries.forEach { choix ->
                    val actif = choix == LocalLangue.current
                    Text(
                        when (choix) {
                            Langue.FRANCAIS -> t.langueFrancais
                            Langue.ANGLAIS -> t.langueAnglais
                        },
                        color = if (actif) tokens.textPrimary else tokens.textMuted,
                        fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { PrefsLangue.choisir(contexte, choix) }
                            .padding(vertical = 10.dp),
                    )
                }
            }

            // ------------------------------------------------------------------ import
            Categorie(t.importCourt)
            Carte {
                Text(
                    t.importExplication,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                BoutonPlein(t.importTitre, onClick = onImportDiscord)
            }

            // application
            //
            // nothing indicated the app's version, reported, and no way of asking for the update:
            // you waited for the banner to deign to appear, which says nothing when it does not
            Categorie(t.application)
            Carte {
                Text(t.velvetVersion(version), color = tokens.textPrimary, fontSize = 15.sp)
                var etatMaj by remember { mutableStateOf<String?>(null) }
                var cherche by remember { mutableStateOf(false) }
                Text(
                    etatMaj ?: t.miseAJourParLeServeur,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                BoutonPlein(
                    if (cherche) t.rechercheEnCours else t.chercherMiseAJour,
                    actif = !cherche,
                ) {
                    cherche = true
                    portee.launch {
                        etatMaj = runCatching { onChercherMaj() }.getOrElse { t.rechercheImpossible }
                        cherche = false
                    }
                }
            }

            // account
            //
            // at the bottom and in red, as everywhere else in the app: what erases does not get put
            // in the way of what you come to do every day. two taps, because logging out makes the
            // history unreadable until you log back in, and it is not a gesture to make by accident
            Categorie(t.compte)
            Carte {
                identifiant?.let {
                    Text(it, color = tokens.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(
                    t.deconnexionExplication,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                var confirme by remember { mutableStateOf(false) }
                Text(
                    if (confirme) t.confirmerDeconnexion else t.seDeconnecter,
                    color = tokens.danger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(tokens.radiusMedium))
                        .clickable { if (confirme) onDeconnexion() else confirme = true }
                        .padding(vertical = 12.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Categorie(texte: String) {
    val tokens = LocalTokens.current
    Text(
        texte.uppercase(),
        color = tokens.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = tokens.espXl, bottom = 8.dp),
    )
}

@Composable
private fun Carte(contenu: @Composable ColumnScopeLocal.() -> Unit) {
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surface)
            .padding(14.dp),
    ) { ColumnScopeLocal(this).contenu() }
}

/**
 * a minimal scope holder
 *
 * `ColumnScope` cannot be built by hand, and the cards' content only needs stacking, with no
 * `weight` and no particular alignment. an empty class is enough to give the calling block a scope
 * of its own without promising it capabilities it does not use
 */
internal class ColumnScopeLocal(private val portee: androidx.compose.foundation.layout.ColumnScope) :
    androidx.compose.foundation.layout.ColumnScope by portee

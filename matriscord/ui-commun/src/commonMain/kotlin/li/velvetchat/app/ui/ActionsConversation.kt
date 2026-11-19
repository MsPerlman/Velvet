package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.ModeNotification

/**
 * how long a conversation stays muted
 *
 * matrix has no "muted until": a push rule is on or off. the deadline is therefore ours to keep,
 * and [TOUJOURS] is the only one the protocol expresses by itself
 */
enum class DureeSourdine(val minutes: Int?) {
    QUINZE(15),
    UNE_HEURE(60),
    HUIT_HEURES(480),
    UN_JOUR(1440),
    TOUJOURS(null),
}

/**
 * everything the conversation menu can set off
 *
 * a holder and not eleven parameters: the sheet is shared by the phone's long press and the
 * desktop's right click, and a signature that long is one nobody reads before adding a twelfth
 */
class ActionsConversation(
    val onEpingler: () -> Unit,
    val onMarquerLu: () -> Unit,
    val onNotifications: () -> Unit,
    val onProfil: () -> Unit,
    val onAppeler: () -> Unit,
    val onNote: () -> Unit,
    val onSurnom: () -> Unit,
    /** invites them into that server. the menu picks which one, on both platforms */
    val onInviterServeur: (String) -> Unit,
    /** null puts the sound back. one entry point rather than a mute and an unmute */
    val onMuet: (DureeSourdine?) -> Unit,
    /**
     * the notification mode chosen for this conversation
     *
     * distinct from [onNotifications], which only opens the chooser: the phone shows a sheet, the
     * desktop replaces the menu page, and the two grammars share nothing but the write at the end.
     * this is that write
     *
     * it has a default because the phone's sheet already writes on its own
     * ([li.velvetchat.core.MatrixEngine.setModeNotification] straight from `Shell`), and giving it
     * one lets the desktop get its submenu without rewriting a path that works
     */
    val onModeNotification: (ModeNotification) -> Unit = {},
    val onFermerMp: () -> Unit,
    val onRetirerAmi: () -> Unit,
    val onBloquer: () -> Unit,
)

private fun libelleDuree(t: Traduction, duree: DureeSourdine) = when (duree) {
    DureeSourdine.QUINZE -> t.sourdine15Min
    DureeSourdine.UNE_HEURE -> t.sourdine1Heure
    DureeSourdine.HUIT_HEURES -> t.sourdine8Heures
    DureeSourdine.UN_JOUR -> t.sourdine24Heures
    DureeSourdine.TOUJOURS -> t.sourdineToujours
}

/** a hairline between two groups of actions, so twelve rows read as four ideas */
@Composable
private fun SeparateurActions() {
    val tokens = LocalTokens.current
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = tokens.espXl, vertical = 6.dp)
            .height(1.dp)
            .background(tokens.border),
    )
}

/**
 * what a long press, or a right click, on a direct conversation offers
 *
 * where this screen comes from: the long press pinned silently. an unlabelled action, announced by
 * nothing, with nothing saying it had happened other than the row moving. that is the fault this
 * project complains about everywhere else, a hidden gesture is worth less than an absent one,
 * because it fires by accident
 *
 * parity with discord would want a swipe. a long press is preferred here, and that is not laziness:
 * the horizontal swipe is already taken by replying inside a conversation, and giving it a second
 * meaning elsewhere would mean learning two rules for one gesture
 *
 * the same sheet serves the phone and the desktop. a right click and a long press ask the same
 * question, and two menus drifting apart is how one of them ends up missing the entry you need
 */
@Composable
fun FeuilleConversation(
    nom: String,
    epingle: Boolean,
    aDesNonLus: Boolean,
    /** a group has no single person behind it, so no profile, no nickname and no friendship */
    estGroupe: Boolean,
    estAmi: Boolean,
    estMuet: Boolean,
    mode: ModeNotification?,
    /** the servers you are in, as id to name, for the invite entry */
    serveurs: List<Pair<String, String>>,
    actions: ActionsConversation,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var choisitDuree by remember { mutableStateOf(false) }
    var choisitServeur by remember { mutableStateOf(false) }
    FeuilleBasse(onFermer = onFermer) {
        Text(
            when {
                choisitDuree -> t.sourdinePour
                choisitServeur -> t.inviterSurLeServeur
                else -> nom
            },
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espM),
        )
        if (choisitServeur) {
            serveurs.forEach { (id, nomServeur) ->
                ActionSalon(
                    Icones.inviter,
                    nomServeur,
                    chevron = false,
                    onClick = {
                        actions.onInviterServeur(id)
                        onFermer()
                    },
                )
            }
        } else if (choisitDuree) {
            DureeSourdine.entries.forEach { duree ->
                ActionSalon(
                    Icones.silence,
                    libelleDuree(t, duree),
                    chevron = false,
                    onClick = {
                        actions.onMuet(duree)
                        onFermer()
                    },
                )
            }
        } else {
            ActionSalon(
                if (epingle) Icones.epingleActif else Icones.epingle,
                if (epingle) t.retirerDesEpinglees else t.epinglerEnHaut,
                chevron = false,
                onClick = actions.onEpingler,
            )
            ActionSalon(
                if (aDesNonLus) Icones.valide else Icones.messages,
                if (aDesNonLus) t.marquerCommeLu else t.marquerCommeNonLu,
                chevron = false,
                onClick = actions.onMarquerLu,
            )

            SeparateurActions()
            if (!estGroupe) ActionSalon(Icones.membres, t.voirLeProfil, onClick = actions.onProfil)
            ActionSalon(Icones.appeler, t.appeler, chevron = false, onClick = actions.onAppeler)
            if (!estGroupe) {
                ActionSalon(Icones.modifier, t.ficheNote, onClick = actions.onNote)
                ActionSalon(Icones.amiValide, t.ficheSurnom, onClick = actions.onSurnom)
            }
            if (serveurs.isNotEmpty()) {
                ActionSalon(
                    Icones.inviter,
                    t.inviterSurLeServeur,
                    onClick = { choisitServeur = true },
                )
            }

            SeparateurActions()
            ActionSalon(Icones.notifications, t.notifications, valeur = mode?.libelle, onClick = actions.onNotifications)
            if (estMuet) {
                ActionSalon(
                    Icones.notificationsActif,
                    t.reactiverNotifications,
                    chevron = false,
                    onClick = {
                        actions.onMuet(null)
                        onFermer()
                    },
                )
            } else {
                ActionSalon(Icones.silence, t.rendreMuet, onClick = { choisitDuree = true })
            }

            SeparateurActions()
            ActionSalon(
                if (estGroupe) Icones.supprimer else Icones.fermer,
                if (estGroupe) t.supprimerLeGroupe else t.fermerLeMp,
                chevron = false,
                couleur = tokens.danger,
                onClick = actions.onFermerMp,
            )
            if (!estGroupe && estAmi) {
                ActionSalon(
                    Icones.depart,
                    t.ficheRetirerAmi,
                    chevron = false,
                    couleur = tokens.danger,
                    onClick = actions.onRetirerAmi,
                )
            }
            if (!estGroupe) {
                ActionSalon(Icones.exclusion, t.ficheBloquer, couleur = tokens.danger, onClick = actions.onBloquer)
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}


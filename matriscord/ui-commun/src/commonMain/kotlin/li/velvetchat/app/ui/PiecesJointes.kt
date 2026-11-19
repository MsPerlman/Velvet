package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * the attachments sheet
 *
 * the composer's plus opened the photo picker directly. two consequences, and the second is the
 * more annoying
 *
 * sending a pdf, an audio file or a document was impossible, although the engine could already do
 * it, it was the picker that was missing. and nothing said so. a button that does one thing without
 * announcing it suggests that is the only thing possible, and nobody looks further
 *
 * three entries and not four. the reference offers a fourth, "apps", which means its bots, with no
 * equivalent here, and an empty entry would be worth less than its absence
 */
@Composable
fun FeuillePiecesJointes(
    onPhotos: () -> Unit,
    /** take the photo there and then. null where there is no camera, the desktop */
    onAppareilPhoto: (() -> Unit)? = null,
    onFichier: () -> Unit,
    onSondage: () -> Unit,
    onFermer: () -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()
    FeuilleBasse(onFermer = onFermer) {
        Text(
            t.joindre,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espS),
        )
        EntreePieceJointe(
            Icones.photo,
            t.joindrePhotos,
            t.joindrePhotosDetail,
        ) { onPhotos(); onFermer() }
        onAppareilPhoto?.let { prendre ->
            EntreePieceJointe(
                Icones.camera,
                t.joindreAppareil,
                t.joindreAppareilDetail,
            ) { prendre(); onFermer() }
        }
        EntreePieceJointe(
            Icones.fichier,
            t.joindreFichier,
            t.joindreFichierDetail,
        ) { onFichier(); onFermer() }
        EntreePieceJointe(
            Icones.sondage,
            t.joindreSondage,
            t.joindreSondageDetail,
        ) { onSondage(); onFermer() }
    }
}

@Composable
private fun EntreePieceJointe(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    titre: String,
    detail: String,
    onClick: () -> Unit,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().curseurMain().clickable(onClick = onClick)
            .padding(horizontal = tokens.espXl, vertical = tokens.espM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tokens.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icone(icone, tint = tokens.textSecondary, taille = 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, color = tokens.textPrimary, fontSize = tokens.typography.bodySize.sp)
            Text(detail, color = tokens.textMuted, fontSize = 12.sp)
        }
    }
}

/**
 * composing a poll
 *
 * two answers minimum, and the send button refuses until two are filled in. a poll with one answer
 * is not a poll, and letting the send go out to fail server side would give an incomprehensible
 * error
 *
 * the secret ballot is offered, and it is irreversible. it is a property of the poll frozen at
 * creation by the protocol and not a display setting, and saying so at the moment of choosing saves
 * discovering it later while hunting for where to change it
 */
@Composable
fun FeuilleSondage(
    onCreer: (question: String, options: List<String>, secret: Boolean) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var secret by remember { mutableStateOf(false) }

    val valide = question.isNotBlank() && options.count { it.isNotBlank() } >= 2

    FeuilleBasse(onFermer = onFermer) {
        Text(
            t.nouveauSondage,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espS),
        )

        ChampSondage(question, t.votreQuestion, { question = it })

        options.forEachIndexed { index, valeur ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = tokens.espXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    ChampSondage(
                        valeur,
                        t.reponseNumero(index + 1),
                        { options[index] = it },
                        marge = 0.dp,
                    )
                }
                // the first two answers cannot be removed: below two it stops being a poll. hiding
                // the cross states the rule without explaining it, and without offering a gesture
                // that would be refused
                if (options.size > 2) {
                    Icone(
                        Icones.fermer,
                        tint = tokens.textMuted,
                        taille = 16.dp,
                        description = t.retirerCetteReponse,
                        modifier = Modifier
                            .curseurMain().clickable { options.removeAt(index) }
                            .padding(start = 10.dp),
                    )
                }
            }
        }

        if (options.size < MAX_REPONSES) {
            Row(
                Modifier.fillMaxWidth()
                    .curseurMain().clickable { options.add("") }
                    .padding(horizontal = tokens.espXl, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icone(Icones.ajouter, tint = tokens.accent, taille = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    t.ajouterUneReponse,
                    color = tokens.accent,
                    fontSize = tokens.typography.bodySize.sp,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .curseurMain().clickable { secret = !secret }
                .padding(horizontal = tokens.espXl, vertical = tokens.espM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    t.bulletinSecret,
                    color = tokens.textPrimary,
                    fontSize = tokens.typography.bodySize.sp,
                )
                Text(
                    t.bulletinSecretAide,
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                )
            }
            Interrupteur(secret)
        }

        Box(Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espS)) {
            BoutonPlein(
                libelle = t.creerLeSondage,
                actif = valide,
            ) {
                onCreer(question.trim(), options.map { it.trim() }.filter { it.isNotEmpty() }, secret)
                onFermer()
            }
        }
    }
}

@Composable
private fun ChampSondage(
    valeur: String,
    indication: String,
    onChange: (String) -> Unit,
    marge: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val tokens = LocalTokens.current
    Box(
        Modifier.fillMaxWidth().padding(horizontal = marge, vertical = tokens.espXs)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised)
            .padding(horizontal = tokens.espM, vertical = tokens.espM)
    ) {
        if (valeur.isEmpty()) {
            Text(
                indication,
                color = tokens.textMuted,
                fontSize = tokens.typography.bodySize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = valeur,
            onValueChange = { onChange(it.take(200)) },
            singleLine = true,
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                color = tokens.textPrimary,
                fontSize = tokens.typography.bodySize.sp,
            ),
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * twenty answers, the protocol's limit
 *
 * bounded here rather than left to the server: past it the send would be refused and the error
 * message would not say that the number of answers was the problem
 */
private const val MAX_REPONSES = 20

/**
 * a poll in the timeline
 *
 * each answer is a bar: the label, the count, and a proportional fill that reads before the
 * numbers. that is what lets somebody take in a result without reading, and the reference does the
 * same. a poll you have to decipher does not get looked at
 *
 * a secret ballot does not show its bars before it closes. showing them would cancel the one
 * property that makes this kind of poll different, and the engine says so through
 * `resultatsVisibles`, which the screen obeys rather than recomputing the rule
 *
 * tapping an answer you already picked removes it, like a reaction: the same gesture learned
 * elsewhere in the app, and the protocol does treat a new vote as replacing the previous one
 */
@Composable
fun CartePoll(
    poll: li.velvetchat.core.model.MessageBody.Poll,
    /**
     * has the server confirmed the poll?
     *
     * while it is still in the send queue it has no event id, it carries a transaction id, and the
     * vote would be refused by the sdk. found the hard way: creating a poll then voting immediately
     * failed, and the failure was swallowed by the caller's `runCatching`. so we disable voting
     * rather than offer it and have it do nothing
     */
    confirme: Boolean = true,
    onVoter: (List<String>) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxWidth().padding(top = tokens.espXs)
            .clip(RoundedCornerShape(tokens.radiusMedium))
            .background(tokens.surfaceRaised)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icone(Icones.sondage, tint = tokens.textMuted, taille = 15.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                if (poll.ferme) t.sondageClos else t.joindreSondage,
                color = tokens.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            poll.question,
            color = tokens.textPrimary,
            fontSize = tokens.typography.bodySize.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )

        poll.options.forEach { option ->
            val part = if (poll.totalVotes > 0) option.votes / poll.totalVotes.toFloat() else 0f
            Box(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(tokens.radiusSmall))
                    .background(tokens.bg)
                    .curseurMain().clickable(enabled = !poll.ferme && confirme) {
                        // removing your vote means sending an empty list. the protocol replaces the
                        // previous vote, so empty does mean no vote
                        onVoter(if (option.mien) emptyList() else listOf(option.id))
                    }
            ) {
                if (poll.resultatsVisibles && part > 0f) {
                    Box(
                        Modifier.fillMaxWidth(part)
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(tokens.radiusSmall))
                            .background(
                                if (option.mien) tokens.accent.copy(alpha = 0.35f)
                                else tokens.border
                            )
                            .padding(vertical = 11.dp)
                    ) { }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Coche(option.mien, taille = 17.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        option.texte,
                        color = tokens.textPrimary,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (poll.resultatsVisibles) {
                        Text(
                            "${option.votes}",
                            color = tokens.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Text(
            when {
                // say why the vote is not answering yet, rather than let it look like a fault. the
                // case lasts a second, and it is the second when somebody tries
                !confirme -> t.envoiEnCoursLong
                // the still open secret ballot case: say why there are no numbers, otherwise the
                // absence looks like a fault
                !poll.resultatsVisibles ->
                    t.bulletinSecretNote
                poll.totalVotes == 0 -> t.personneNaVote
                poll.totalVotes == 1 -> t.unVote
                else -> t.votesNombre(poll.totalVotes)
            },
            color = tokens.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = tokens.espS),
        )
    }
}

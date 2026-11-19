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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody

/**
 * searching inside a room
 *
 * ## what it is, and what it is not
 *
 * the todo's line was filed as blocked but not long, and the diagnosis was right: `/search` does
 * not see encrypted rooms, because the server cannot index what it cannot read. element answers
 * that with a local index on desktop and has no full text search on mobile at all
 *
 * what unblocks the line is giving up what it implicitly asked for. a global search over the whole
 * history wants an index, and a search in the open room, over its last thousand messages, only
 * wants paginating and filtering. the second covers the real use, "what did dad say about the
 * train", and ships today
 *
 * ## why the screen says so
 *
 * a search that lies about its scope is worse than no search: "nothing found" reads as "it does not
 * exist", when it means "not in what i looked at". so the list's footer writes the bound, every
 * time, including when there are results
 */
@Composable
fun EcranRechercheSalon(
    nomDuSalon: String,
    onChercher: suspend (String) -> List<Message>,
    onOuvrir: (String) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    var requete by remember { mutableStateOf("") }
    var resultats by remember { mutableStateOf<List<Message>>(emptyList()) }
    var enCours by remember { mutableStateOf(false) }
    var cherchee by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    androidx.activity.compose.BackHandler(onBack = onFermer)
    // the keyboard is there from the opening: you do not open a search to look at an empty field.
    // it is the only screen in the app where that is true
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    /**
     * the search runs on submit, not on keystroke
     *
     * unlike the room filter or the gif tab, each attempt paginates several pages of history here,
     * so several network round trips. firing that on every character would make ten searches for
     * one word, nine of them thrown away
     */
    suspend fun lancer() {
        val terme = requete.trim()
        if (terme.isBlank()) return
        enCours = true
        resultats = runCatching { onChercher(terme) }.getOrDefault(emptyList())
        cherchee = terme
        enCours = false
    }

    val portee = androidx.compose.runtime.rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(tokens.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(tokens.surface)
                .padding(horizontal = tokens.espS, vertical = tokens.espS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(
                Icones.retour,
                tint = tokens.textPrimary,
                taille = 22.dp,
                description = t.retour,
                modifier = Modifier.clip(RoundedCornerShape(tokens.radiusLarge))
                    .clickable(onClick = onFermer).padding(10.dp),
            )
            Row(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surfaceRaised)
                    .padding(horizontal = tokens.espM, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icone(Icones.recherche, tint = tokens.textMuted, taille = 17.dp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (requete.isEmpty()) {
                        Text(
                            t.chercherDansSalonNomme(nomDuSalon),
                            color = tokens.textMuted,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = requete,
                        onValueChange = { requete = it },
                        singleLine = true,
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = tokens.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(tokens.accent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { portee.launch { lancer() } },
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (requete.isNotEmpty()) {
                    Icone(
                        Icones.fermer,
                        tint = tokens.textMuted,
                        taille = 16.dp,
                        description = t.effacer,
                        modifier = Modifier
                            .clickable { requete = ""; resultats = emptyList(); cherchee = null }
                            .padding(start = 6.dp),
                    )
                }
            }
        }

        when {
            enCours -> EtatVide(
                t.rechercheEnCours,
                t.rechercheParPages,
            )

            cherchee == null -> EtatVide(
                t.chercherDansSalon,
                t.rechercheExplication(nomDuSalon),
            )

            resultats.isEmpty() -> EtatVide(
                t.rienTrouve,
                t.aucunMessageContenant(cherchee.orEmpty(), nomDuSalon),
            )

            else -> {
                Text(
                    t.resultatsNombre(resultats.size),
                    color = tokens.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = tokens.espXl, top = 14.dp, bottom = 6.dp),
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(resultats, key = { it.id }) { message ->
                        LigneResultat(message, cherchee.orEmpty()) { onOuvrir(message.id) }
                    }
                    item {
                        // written even when there are results: "three results" reads as "there are
                        // three", when it means "three in what i looked at". the bound is part of
                        // the result
                        Text(
                            t.rechercheLimitee,
                            color = tokens.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(tokens.espXl),
                        )
                    }
                }
            }
        }
    }
}

/**
 * a result: who, when, and the excerpt
 *
 * three lines at most, a result not being the message but enough to decide whether to go there. the
 * whole message is read by tapping it, in its context
 */
@Composable
private fun LigneResultat(message: Message, terme: String, onClick: () -> Unit) {
    val tokens = LocalTokens.current
    val texte = (message.body as? MessageBody.Text)?.text.orEmpty()
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = tokens.espL, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                message.sender,
                message.senderDisplayName,
                message.senderAvatarUrl,
                26.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message.senderDisplayName
                    ?: message.sender.removePrefix("@").substringBefore(':'),
                color = LocalRoleColors.current[message.sender] ?: tokens.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                remember(message.timestamp) {
                    java.text.SimpleDateFormat("d MMM yyyy", localeCourante())
                        .format(java.util.Date(message.timestamp))
                },
                color = tokens.textMuted,
                fontSize = 11.sp,
            )
        }
        Text(
            texte,
            color = tokens.textPrimary,
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 34.dp, top = 3.dp),
        )
    }
}

/** an empty state that explains, rather than a bare screen */
@Composable
private fun EtatVide(titre: String, detail: String) {
    val tokens = LocalTokens.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icone(Icones.recherche, tint = tokens.textMuted, taille = 36.dp)
        Box(Modifier.height(12.dp))
        Text(
            titre,
            color = tokens.textSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(6.dp))
        Text(
            detail,
            color = tokens.textMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
    }
}

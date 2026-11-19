package li.velvetchat.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import li.velvetchat.app.ui.*
import li.velvetchat.core.model.ChannelKind
import li.velvetchat.core.model.Message

/** where a message can go: a text channel of a server, or a conversation */
private data class Destination(
    val roomId: String,
    val nom: String,
    /** the server's name under a channel, nothing under a conversation */
    val sousTitre: String?,
    /** set for a conversation, whose row shows the person's avatar rather than a hash */
    val userId: String?,
    val avatarUrl: String?,
)

/** the reference lets one message go to five places at once, not more */
private const val DESTINATIONS_MAX = 5

/**
 * the reference's forward: pick up to five places, add a message if you like, send
 *
 * the destinations are read once when it opens, conversations first like the reference:
 * forwarding is mostly showing somebody something
 */
@Composable
internal fun ModaleTransfert(depuis: String, message: Message, onFermer: () -> Unit) {
    val t = textes()
    val moteur = LocalEngine.current
    val portee = rememberCoroutineScope()
    var destinations by remember { mutableStateOf<List<Destination>>(emptyList()) }
    var recherche by remember { mutableStateOf("") }
    var choisies by remember { mutableStateOf(emptyList<String>()) }
    var mot by remember { mutableStateOf("") }
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val directs = runCatching { moteur.directsFlow().first() }.getOrDefault(emptyList())
            .map { Destination(it.roomId, it.name, null, it.userId, it.avatarUrl) }
        val salons = runCatching { moteur.serversFlow().first() }.getOrDefault(emptyList())
            .flatMap { s ->
                s.channels.filter { it.kind == ChannelKind.TEXT && !it.id.startsWith("brouillon:") }
                    .map { Destination(it.id, it.name, s.name, null, null) }
            }
        destinations = directs + salons
    }

    fun envoyer() {
        if (choisies.isEmpty() || envoi) return
        envoi = true
        erreur = null
        portee.launch {
            // each place on its own: one refusal leaves the others sent, and only the failed
            // ones stay ticked for another try
            val echecs = choisies.filter { vers ->
                runCatching {
                    moteur.transferer(depuis, message.id, vers)
                    if (mot.isNotBlank()) moteur.send(vers, mot.trim())
                }.isFailure
            }
            envoi = false
            if (echecs.isEmpty()) {
                onFermer()
            } else {
                choisies = echecs
                erreur = t.transfertEchoue
            }
        }
    }

    CarteModale(largeur = 480, onFermer = onFermer) {
        Text(t.transfererVers, color = CREME_SERVEUR, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(t.transfererSousTitre, color = TEXTE_GRIS, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        ChampDore(recherche, t.rechercher, { recherche = it })
        Spacer(Modifier.height(8.dp))
        val visibles = destinations.filter {
            recherche.isBlank() || it.nom.contains(recherche, ignoreCase = true) ||
                it.sousTitre?.contains(recherche, ignoreCase = true) == true
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            items(visibles, key = { it.roomId }) { d ->
                val coche = d.roomId in choisies
                val possible = coche || choisies.size < DESTINATIONS_MAX
                Row(
                    Modifier.fillMaxWidth().height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (coche) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                        .curseurMain()
                        .clickable(enabled = possible) {
                            choisies = if (coche) choisies - d.roomId else choisies + d.roomId
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (d.userId != null) {
                        Avatar(userId = d.userId, displayName = d.nom, avatarUrl = d.avatarUrl, taille = 32.dp)
                    } else {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Icone(Icones.salon, tint = TEXTE_GRIS, taille = 20.dp, description = null)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.nom,
                            color = if (possible) TEXTE_CLAIR else TEXTE_GRIS,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        d.sousTitre?.let {
                            Text(it, color = TEXTE_GRIS, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    // the square box of a choice among several, like `EntreeMenu`'s `case`
                    Box(
                        Modifier.size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (coche) DORE else Color.Transparent)
                            .border(2.dp, if (coche) DORE else TEXTE_GRIS, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (coche) Icone(Icones.valide, tint = Color(0xFF1B1D22), taille = 14.dp, description = null)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ChampDore(mot, t.ajouterUnMessageFacultatif, { mot = it }, onEntree = { envoyer() })
        erreur?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFF23F43), fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        BoutonDore(t.envoyer, actif = choisies.isNotEmpty() && !envoi) { envoyer() }
    }
}

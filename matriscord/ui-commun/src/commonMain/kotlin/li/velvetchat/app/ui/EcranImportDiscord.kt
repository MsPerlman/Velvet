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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.velvetchat.core.ClientImport
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.ApercuImport
import li.velvetchat.core.model.ArbreImport
import li.velvetchat.core.model.DemandeImport
import li.velvetchat.core.model.EtatImport
import li.velvetchat.core.model.PaquetImport
import li.velvetchat.core.model.SalonImport

/**
 * importing your discord conversations into velvet
 *
 * what this screen does and does not do: it chooses. the work, reading the export, reposting each
 * message at its original date, uploading the attachments, runs on the nas, because dating a
 * message yesterday is reserved to matrix application services and their token has no business in
 * an app
 *
 * three decisions, in this order: which bundle, a discord export belongs to one person and contains
 * only their messages, which conversations, the tree of servers and dms, and where and between
 * which bounds, the velvet room that receives, the media, the period. then it runs, sometimes for
 * hours, and the screen says where it is up to
 */
@Composable
fun EcranImportDiscord(
    moteur: MatrixEngine,
    /**
     * the import has just started, and it is up to the phone to follow it in a notification, since
     * it lasts hours and nobody is going to sit in front of this screen (see `ServiceImport`)
     */
    onLance: (tache: String, jeton: String) -> Unit = { _, _ -> },
    onFermer: () -> Unit,
) {
    val tokens = LocalTokens.current
    val t = textes()
    val portee = rememberCoroutineScope()
    val client = remember { ClientImport() }
    val jeton = remember { runCatching { moteur.currentSession().accessToken }.getOrNull().orEmpty() }

    var paquets by remember { mutableStateOf<List<PaquetImport>>(emptyList()) }
    var paquet by remember { mutableStateOf<String?>(null) }
    var arbre by remember { mutableStateOf<ArbreImport?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var recherche by remember { mutableStateOf("") }
    var choisis by remember { mutableStateOf<Set<String>>(emptySet()) }
    var apercus by remember { mutableStateOf<Map<String, ApercuImport>>(emptyMap()) }
    var deplies by remember { mutableStateOf<Set<String>>(emptySet()) }

    var medias by remember { mutableStateOf(true) }
    var du by remember { mutableStateOf("") }
    var au by remember { mutableStateOf("") }
    var salonCible by remember { mutableStateOf("") }

    var tache by remember { mutableStateOf<String?>(null) }
    var etat by remember { mutableStateOf<EtatImport?>(null) }

    LaunchedEffect(Unit) {
        runCatching { client.paquets(jeton) }
            .onSuccess { paquets = it; paquet = paquet ?: it.firstOrNull()?.nom }
            .onFailure { erreur = t.importInjoignable(it.message.orEmpty()) }
    }
    LaunchedEffect(paquet) {
        val p = paquet ?: return@LaunchedEffect
        arbre = null
        runCatching { client.arbre(jeton, p) }
            .onSuccess { arbre = it }
            .onFailure { erreur = t.importPaquetIllisible(it.message.orEmpty()) }
    }
    // progress, once a second: the import takes hours, nothing is urgent
    LaunchedEffect(tache) {
        val t = tache ?: return@LaunchedEffect
        while (true) {
            val lu = runCatching { client.etat(jeton, t) }.getOrNull()
            if (lu != null) etat = lu
            if (lu?.fini == true) return@LaunchedEffect
            delay(1_000)
        }
    }

    Column(Modifier.fillMaxSize().background(tokens.bg).padding(horizontal = tokens.espL)) {
        Row(Modifier.fillMaxWidth().padding(vertical = tokens.espM), verticalAlignment = Alignment.CenterVertically) {
            Icone(
                Icones.retourFleche, tint = tokens.textPrimary, taille = 22.dp, description = t.retour,
                modifier = Modifier.clip(RoundedCornerShape(tokens.radiusMedium)).clickable(onClick = onFermer).padding(6.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(t.importTitre, color = tokens.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        erreur?.let {
            Text(it, color = tokens.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = tokens.espS))
        }

        etat?.let { e ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(tokens.radiusMedium))
                    .background(tokens.surface).padding(tokens.espM),
            ) {
                Text(
                    when (e.phase) {
                        "import" -> t.importEnCours
                        "fini" -> t.importTermine
                        "échec" -> t.importInterrompu
                        "arrêté" -> t.importArrete
                        else -> t.importLecture
                    },
                    color = tokens.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
                Text(
                    e.erreur ?: (
                        t.importAvancementDetail(e.faits, e.total, e.evenements) +
                            (if (e.vitesse > 0) t.importVitesse(e.vitesse.toString()) else "") +
                            (if (e.mediasRates > 0) t.importMediasRates(e.mediasRates) else "")
                        ),
                    color = if (e.erreur != null) tokens.danger else tokens.textMuted, fontSize = 12.sp,
                )
                if (!e.fini) {
                    Text(
                        t.arreter,
                        color = tokens.danger, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp).clickable {
                            tache?.let { t -> portee.launch { runCatching { client.arreter(jeton, t) } } }
                        },
                    )
                }
            }
            Spacer(Modifier.height(tokens.espM))
        }

        // ------------------------------------------------------------------- the package
        Titre(t.importPaquet)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = tokens.espS)) {
            paquets.forEach { p ->
                val actif = p.nom == paquet
                Text(
                    "${p.nom} · ${p.salons}",
                    color = if (actif) tokens.bg else tokens.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (actif) tokens.accent else tokens.surfaceRaised)
                        .clickable { paquet = p.nom; choisis = emptySet(); apercus = emptyMap() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            if (paquets.isEmpty()) {
                Text(t.importAucunPaquet, color = tokens.textMuted, fontSize = 12.sp)
            }
        }

        // --------------------------------------------------------------- the conversations
        Titre(t.importConversations)
        Champ(recherche, t.importChercher, Modifier.fillMaxWidth()) { recherche = it }
        Spacer(Modifier.height(tokens.espS))

        val a = arbre
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (a == null) {
                Text(t.importLecture, color = tokens.textMuted, fontSize = 13.sp)
            } else {
                val filtre = recherche.trim().lowercase()
                val mp = a.mp.filter { filtre.isEmpty() || it.nom.lowercase().contains(filtre) }
                LazyColumn(Modifier.fillMaxSize()) {
                    item { SousTitre(t.mpNombre(mp.size)) }
                    items(mp, key = { "mp" + it.id }) { salon ->
                        LigneImport(
                            salon = salon,
                            choisi = salon.id in choisis,
                            apercu = apercus[salon.id],
                            onChoisir = {
                                choisis = if (salon.id in choisis) choisis - salon.id else choisis + salon.id
                                if (apercus[salon.id] == null) portee.launch {
                                    runCatching { client.apercu(jeton, paquet!!, salon.id) }
                                        .onSuccess { apercus = apercus + (salon.id to it) }
                                }
                            },
                        )
                    }
                    a.serveurs.forEach { serveur ->
                        val salons = serveur.salons.filter { filtre.isEmpty() || it.nom.lowercase().contains(filtre) }
                        if (salons.isEmpty()) return@forEach
                        item(key = "s" + serveur.id) {
                            SousTitre(
                                (if (serveur.id in deplies) "▾ " else "▸ ") + serveur.nom + " — ${salons.size}",
                                Modifier.clickable {
                                    deplies = if (serveur.id in deplies) deplies - serveur.id else deplies + serveur.id
                                },
                            )
                        }
                        if (serveur.id in deplies) {
                            items(salons, key = { "c" + it.id }) { salon ->
                                LigneImport(
                                    salon = salon,
                                    choisi = salon.id in choisis,
                                    apercu = apercus[salon.id],
                                    prefixe = "#",
                                    onChoisir = {
                                        choisis = if (salon.id in choisis) choisis - salon.id else choisis + salon.id
                                        if (apercus[salon.id] == null) portee.launch {
                                            runCatching { client.apercu(jeton, paquet!!, salon.id) }
                                                .onSuccess { apercus = apercus + (salon.id to it) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------------- the options
        Titre(t.importOptions)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Box(Modifier.clickable { medias = !medias }) { Interrupteur(medias) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(t.importMedias, color = tokens.textPrimary, fontSize = 14.sp)
                Text(t.importMediasDetail, color = tokens.textMuted, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Champ(du, t.importDu, Modifier.weight(1f)) { du = it }
            Champ(au, t.importAu, Modifier.weight(1f)) { au = it }
        }
        // the author is no longer a choice: the service writes in the name of the bundle's owner
        // whatever the client asks. an export only contains its owner's messages, and posting them
        // in somebody else's name would be falsifying a conversation
        paquets.firstOrNull { it.nom == paquet }?.proprietaire?.let {
            Text(t.importAuNomDe(it), color = tokens.textMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Champ(salonCible, t.importSalonCible, Modifier.fillMaxWidth()) { salonCible = it }
        Spacer(Modifier.height(tokens.espS))

        val pret = paquet != null && choisis.isNotEmpty() && salonCible.startsWith("!")
        BoutonPlein(
            if (choisis.isEmpty()) t.importChoisirDabord else t.importLancer(choisis.size),
            actif = pret && (etat?.fini != false),
            modifier = Modifier.padding(bottom = tokens.espL),
        ) {
            erreur = null
            portee.launch {
                runCatching {
                    client.lancer(
                        jeton,
                        DemandeImport(
                            paquet = paquet!!,
                            conversations = choisis.toList(),
                            salon = salonCible.trim(),

                            medias = medias,
                            du = enMs(du, debut = true),
                            au = enMs(au, debut = false),
                        ),
                    )
                }.onSuccess { tache = it; etat = EtatImport(phase = "lecture"); onLance(it, jeton) }
                    .onFailure { erreur = t.importRefuse(it.message.orEmpty()) }
            }
        }
    }
}

/**
 * a `YYYY-MM-DD` date in milliseconds, or null. the whole day is taken, 00:00 at the start and
 * 23:59 at the end
 */
private fun enMs(texte: String, debut: Boolean): Long? {
    val parts = texte.trim().split('-')
    if (parts.size != 3) return null
    val (a, m, j) = parts.map { it.toIntOrNull() ?: return null }
    val jours = jourJulien(a, m, j) - jourJulien(1970, 1, 1)
    return jours * 86_400_000L + if (debut) 0L else 86_399_999L
}

/** days since an arbitrary epoch, enough to subtract two dates with no dependency */
private fun jourJulien(a: Int, m: Int, j: Int): Long {
    val aa = if (m <= 2) a - 1 else a
    val mm = if (m <= 2) m + 12 else m
    val siecle = aa / 100
    return (365.25 * (aa + 4716)).toLong() + (30.6001 * (mm + 1)).toLong() + j + (2 - siecle + siecle / 4) - 1524
}

@Composable
private fun Titre(texte: String) {
    val tokens = LocalTokens.current
    Text(
        texte.uppercase(),
        color = tokens.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = tokens.espM, bottom = 6.dp),
    )
}

@Composable
private fun SousTitre(texte: String, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    Text(
        texte,
        color = tokens.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun Champ(valeur: String, invite: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val tokens = LocalTokens.current
    Box(
        modifier.clip(RoundedCornerShape(tokens.radiusMedium)).background(tokens.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (valeur.isEmpty()) Text(invite, color = tokens.textMuted, fontSize = 13.sp)
        BasicTextField(
            value = valeur,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = tokens.textPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LigneImport(
    salon: SalonImport,
    choisi: Boolean,
    apercu: ApercuImport?,
    prefixe: String = "",
    onChoisir: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onChoisir).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Coche(choisi)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                prefixe + salon.nom,
                color = tokens.textPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                apercu?.let { a ->
                    t.importMessages(a.messages) + (if (a.pieces > 0) t.importPieces(a.pieces) else "")
                } ?: poids(salon.octets),
                color = tokens.textMuted,
                fontSize = 11.sp,
            )
        }
    }
}

private fun poids(octets: Long): String = when {
    octets >= 1_000_000 -> horsUi.tailleMo(octets / 1_000_000)
    octets >= 1_000 -> horsUi.tailleKo(octets / 1_000)
    else -> horsUi.tailleOctets(octets)
}

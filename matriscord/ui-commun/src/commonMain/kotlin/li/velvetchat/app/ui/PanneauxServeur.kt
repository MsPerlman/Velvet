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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import li.velvetchat.core.model.CustomEmote

/**
 * a server's admin panels, shared by both apps
 *
 * they lived in `app-android`, so the desktop's server settings had three pages where the phone has
 * seven. nothing in there was phone specific except the file picker, and it is for that alone that
 * they had not been shared earlier
 *
 * hence [ChoisirFichier]: each app opens its platform's dialog, android's picker, windows' through
 * filekit, and returns a local path, which is exactly what `uploadMedia` wants. the panels
 * themselves no longer have an opinion on the matter
 */

/** what we are asking to pick: the two dialogs do not show the same files */
enum class TypeFichier { IMAGE, SON }

/** opens the platform's picker. [suite] receives a local path, and is not called on cancel */
typealias ChoisirFichier = (type: TypeFichier, suite: (chemin: String) -> Unit) -> Unit

/**
 * the filename as a suggested name
 *
 * [raccourci] strips what cannot go into a `:shortcode:`, since a name with a space or an accent
 * would produce a spelling no autocomplete could find. a sound's name has no such constraint
 */
fun nomProposeDe(chemin: String, taille: Int = 32, raccourci: Boolean = true): String {
    val brut = chemin.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')
    val propre = if (raccourci) {
        brut.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    } else {
        brut
    }
    return propre.take(taille).ifBlank { "sans-nom" }
}

@Composable
fun PanneauMembres(
    membres: List<li.velvetchat.core.model.Member>,
    paliers: List<li.velvetchat.core.model.RoleTier>,
    onCharger: () -> Unit,
    onMembre: (li.velvetchat.core.model.Member) -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    LaunchedEffect(Unit) { onCharger() }
    if (membres.isEmpty()) {
        Text(
            t.chargement,
            color = tokens.textMuted,
            fontSize = tokens.typography.captionSize.sp,
            modifier = Modifier.padding(tokens.espXl),
        )
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = tokens.espS)) {
        membres.forEach { membre ->
            val palier = palierDe(membre.role?.powerLevel, paliers)
            Row(
                Modifier.fillMaxWidth().clickable { onMembre(membre) }
                    .padding(horizontal = tokens.espL, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    userId = membre.userId,
                    displayName = membre.displayName,
                    avatarUrl = membre.avatarUrl,
                    taille = 32.dp,
                )
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        membre.displayName ?: membre.userId,
                        // the tier's colour dresses the name and not a dot: the avatar already
                        // carries its own identity colour
                        color = palier?.colorArgb?.let { androidx.compose.ui.graphics.Color(it) }
                            ?: tokens.textPrimary,
                        fontSize = tokens.typography.bodySize.sp,
                    )
                    Text(
                        palier?.name ?: membre.userId,
                        color = tokens.textMuted,
                        fontSize = 10.sp,
                    )
                }
                (palier?.icon ?: membre.role?.icon)?.let {
                    Text(it, color = tokens.colors.warning.toColor(), fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * the server's emoji: see them, add them, remove them
 *
 * adding asks for a name, prefilled with the file's. prefilled and not imposed: a camera filename
 * would make a shortcode nobody would remember, and the moment of adding is precisely when you know
 * what you will want to call it
 *
 * removing does not delete the media from the server, only its entry in the pack. the protocol
 * offers no way to erase an uploaded media, and pretending otherwise would be a lie about what
 * actually went away
 */
/**
 * the server's avatar decoration catalogue
 *
 * same gesture as the emoji and for the same reason: an image uploaded once, named, then available
 * to everyone on the server. the difference is what you do with it, a decoration is worn rather than sent
 *
 * as with the emoji, removing an entry does not erase the media from the server, the protocol does
 * not allow it. and the people already wearing that decoration carry on wearing it, since their
 * profile stores the image and not a reference into the catalogue. that is deliberate: their choice
 * must not come undone because an admin reorganised the list
 */
@Composable
fun PanneauDecorations(
    decorations: List<li.velvetchat.core.model.DecorationAvatar>,
    monIdentifiant: String,
    onAjouter: suspend (chemin: String, nom: String) -> Unit,
    onRetirer: suspend (String) -> Unit,
    choisirFichier: ChoisirFichier,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()

    var choisie by remember { mutableStateOf<String?>(null) }
    var nom by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            t.decorationsDuServeur,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = tokens.typography.headingSize.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = 6.dp),
        )
        Text(
            t.decorationsExplication,
            color = tokens.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = 10.dp),
        )

        if (decorations.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(72.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                    .padding(horizontal = 14.dp),
            ) {
                items(decorations, key = { it.id }) { d ->
                    Column(
                        Modifier.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // shown on an avatar rather than on its own: the relation between the ring
                        // and the face is what you are judging, and an empty ring does not say it
                        Avatar(
                            userId = monIdentifiant,
                            displayName = null,
                            avatarUrl = null,
                            taille = 40.dp,
                            decoration = d.mxc,
                        )
                        Text(
                            d.nom,
                            color = tokens.textMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Text(
                            "retirer",
                            color = tokens.danger,
                            fontSize = 9.sp,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    runCatching { onRetirer(d.id) }
                                        .onFailure { message = it.message }
                                }
                            },
                        )
                    }
                }
            }
        }

        val chemin = choisie
        if (chemin == null) {
            BoutonPlein(t.ajouterUneDecoration, modifier = Modifier.padding(tokens.espXl)) {
                // the filename is the suggestion, stripped of what cannot go into a `:shortcode:`
                choisirFichier(TypeFichier.IMAGE) { chemin ->
                    choisie = chemin
                    nom = nomProposeDe(chemin)
                }
            }
        } else {
            Column(Modifier.padding(horizontal = tokens.espXl)) {
                ChampNom(nom) { nom = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        BoutonPlein(t.annuler, actif = !enCours) { choisie = null; nom = "" }
                    }
                    Box(Modifier.weight(1f)) {
                        BoutonPlein(
                            if (enCours) t.envoiEnCours else t.ajouter,
                            actif = nom.isNotBlank() && !enCours,
                        ) {
                            enCours = true
                            message = null
                            scope.launch {
                                runCatching { onAjouter(chemin, nom) }
                                    .onSuccess { choisie = null; nom = "" }
                                    .onFailure { message = it.message ?: t.ajoutImpossible }
                                enCours = false
                            }
                        }
                    }
                }
                Box(Modifier.padding(bottom = tokens.espL))
            }
        }

        message?.let {
            Text(
                it,
                color = tokens.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espS),
            )
        }
    }
}

/**
 * the kept window, written out as a file, or `null` when there is nothing to trim
 *
 * `null` is the ordinary answer and not a failure: no decoder on this platform, a file that
 * would not decode, or a clip already shorter than the cap. the caller then sends the original,
 * which is exactly what it did before the editor existed
 *
 * the cap is applied here and not only by the window, because the window's length comes from a
 * composable that a caller could in principle not show. a bound that lives in the ui is a bound
 * somebody removes by refactoring
 */
private suspend fun decoupe(
    lu: EchantillonsAudio?,
    fenetre: Pair<Int, Int>,
    encodeur: EncodeurWav?,
): String? {
    if (lu == null || encodeur == null) return null
    val garde = bornesDecoupe(lu.pcm.size, lu.frequence, fenetre.first, fenetre.second)
    if (garde.isEmpty()) return null
    return encodeur.ecrire(lu.pcm.copyOfRange(garde.first, garde.last + 1), lu.frequence)
}

/**
 * the soundboard's sound catalogue
 *
 * same pattern as [PanneauDecorations], deliberately: they are two server catalogues administered
 * the same way, and letting their screens diverge would mean relearning the second after
 * understanding the first
 *
 * the file picker is the generic one and not `PickVisualMedia`, which only shows images and videos
 * and never an audio file. `GetContent` on the generic audio type opens the system file browser,
 * which is the only path to an mp3 sitting in the downloads folder
 *
 * the emoji is optional and the form says so: a grid of sound effects is read by name first, and
 * requiring a glyph would add an obstacle to every addition for a marker that only helps you aim
 * quickly
 */
@Composable
fun PanneauSons(
    sons: List<li.velvetchat.core.model.SonSoundboard>,
    onAjouter: suspend (chemin: String, nom: String, emoji: String?) -> Unit,
    onRetirer: suspend (String) -> Unit,
    choisirFichier: ChoisirFichier,
    /**
     * renames a sound, and changes its emoji. the audio never moves
     *
     * it was missing, and the shape of the catalogue is why it is cheap: `setSonsSoundboard`
     * replaces the whole list anyway, so renaming is the same write as adding, with one entry
     * changed instead of one appended. the alternative was retiring the sound and uploading the
     * same file again under another name, which costs a round trip and leaves the old media on
     * the server
     */
    onRenommer: suspend (id: String, nom: String, emoji: String?) -> Unit = { _, _, _ -> },
    /**
     * the two halves of the trimming, supplied by the platform. both null and the panel is what
     * it was: pick a file, name it, send it whole
     *
     * optional rather than required because only android decodes arbitrary audio today. the
     * desktop keeps the untrimmed path until it has a decoder, and the panel says so instead of
     * offering a window it cannot honour
     */
    decodeur: DecodeurAudio? = null,
    encodeur: EncodeurWav? = null,
    onEcouter: ((ShortArray, Int) -> Unit)? = null,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()

    var choisi by remember { mutableStateOf<String?>(null) }
    var nom by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // the decoded file and the window kept out of it. both cleared with `choisi`, since they
    // describe that file and nothing else
    var echantillons by remember { mutableStateOf<EchantillonsAudio?>(null) }
    var fenetre by remember { mutableStateOf(0 to 0) }
    var decodage by remember { mutableStateOf(false) }

    // the sound being renamed. it shares `nom` and `emoji` with the add form on purpose: the two
    // ask the same two questions, and a second pair of fields would be a second place to fix the
    // day one of them gains a rule
    var renomme by remember { mutableStateOf<li.velvetchat.core.model.SonSoundboard?>(null) }

    fun repartir() {
        choisi = null; nom = ""; emoji = ""; echantillons = null; fenetre = 0 to 0; renomme = null
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            t.sonsDuServeur,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = tokens.typography.headingSize.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = 6.dp),
        )
        Text(
            t.sonsExplication,
            color = tokens.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = 10.dp),
        )

        sons.forEach { s ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.emoji ?: "🔈", fontSize = 20.sp)
                Spacer(Modifier.width(12.dp))
                // the name is the target, not a pencil in the corner: it is what you came to
                // change, and it is already the widest thing on the row. a separate control
                // would be a second place to aim for one action
                Text(
                    s.nom,
                    color = tokens.textPrimary,
                    fontSize = tokens.typography.bodySize.sp,
                    modifier = Modifier.weight(1f).clickable {
                        renomme = s
                        nom = s.nom
                        emoji = s.emoji.orEmpty()
                        choisi = null
                        echantillons = null
                        message = null
                    },
                )
                Text(
                    "retirer",
                    color = tokens.danger,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        scope.launch {
                            runCatching { onRetirer(s.id) }.onFailure { message = it.message }
                        }
                    },
                )
            }
        }

        val chemin = choisi
        val aRenommer = renomme
        if (aRenommer != null) {
            // the same two fields as the add form, and no editor: the audio is already on the
            // server and is not being touched. offering a window here would suggest it could be
            // recut, which would mean re uploading, which is the thing renaming exists to avoid
            Column(Modifier.padding(horizontal = tokens.espXl)) {
                ChampNom(nom, "NOM", { it.take(24) }) { nom = it }
                Spacer(Modifier.height(tokens.espS))
                ChampNom(emoji, t.emojiFacultatifMaj, { it.take(4) }) { emoji = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        BoutonPlein(t.annuler, actif = !enCours) { repartir() }
                    }
                    Box(Modifier.weight(1f)) {
                        BoutonPlein(
                            if (enCours) t.envoiEnCours else t.enregistrer,
                            actif = nom.isNotBlank() && !enCours,
                        ) {
                            enCours = true
                            message = null
                            scope.launch {
                                runCatching {
                                    onRenommer(aRenommer.id, nom, emoji.ifBlank { null })
                                }
                                    .onSuccess { repartir() }
                                    .onFailure { message = it.message ?: t.ajoutImpossible }
                                enCours = false
                            }
                        }
                    }
                }
                Box(Modifier.padding(bottom = tokens.espL))
            }
        } else if (chemin == null) {
            BoutonPlein(t.ajouterUnSon, modifier = Modifier.padding(tokens.espXl)) {
                choisirFichier(TypeFichier.SON) { chemin ->
                    choisi = chemin
                    nom = nomProposeDe(chemin, 24, raccourci = false)
                    message = null
                    // decoding is what makes the window possible, and it is also the only
                    // check that the file is audio at all. a `.txt` renamed `.mp3` gets
                    // caught here rather than at the upload, where the message would be the
                    // server's and would say nothing useful
                    if (decodeur != null) scope.launch {
                        decodage = true
                        val lu = runCatching { decodeur.decoder(chemin) }.getOrNull()
                        echantillons = lu
                        fenetre = 0 to minOf(
                            lu?.pcm?.size ?: 0,
                            ((lu?.frequence ?: 0).toLong() * SON_DUREE_MAX_MS / 1000).toInt(),
                        )
                        if (lu == null) message = t.sonIllisible
                        decodage = false
                    }
                }
            }
        } else {
            Column(Modifier.padding(horizontal = tokens.espXl)) {
                val lu = echantillons
                if (decodage) {
                    Text(
                        t.chargement,
                        color = tokens.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = tokens.espS),
                    )
                } else if (lu != null) {
                    EditeurDeSon(
                        echantillons = lu,
                        onFenetre = { d, f -> fenetre = d to f },
                        onEcouter = onEcouter?.let { ecouter ->
                            { d, f -> ecouter(lu.pcm.copyOfRange(d, f), lu.frequence) }
                        },
                    )
                    // said only when it applies. a file already under six seconds cannot run
                    // into the ceiling, and explaining a bound nobody can reach is how a
                    // notice stops being read
                    if (lu.dureeMs > SON_DUREE_MAX_MS) {
                        Text(
                            t.sonTropLong,
                            color = tokens.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = tokens.espS),
                        )
                    }
                    Spacer(Modifier.height(tokens.espS))
                }
                // a sound's name is allowed spaces: "papa laughing" reads better than an
                // underscored version in a grid you aim at with a thumb while talking
                ChampNom(nom, "NOM", { it.take(24) }) { nom = it }
                Spacer(Modifier.height(tokens.espS))
                ChampNom(emoji, t.emojiFacultatifMaj, { it.take(4) }) { emoji = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        BoutonPlein(t.annuler, actif = !enCours) { repartir() }
                    }
                    Box(Modifier.weight(1f)) {
                        BoutonPlein(
                            if (enCours) t.envoiEnCours else t.ajouter,
                            actif = nom.isNotBlank() && !enCours && !decodage,
                        ) {
                            enCours = true
                            message = null
                            scope.launch {
                                runCatching {
                                    // the trimmed file when we could decode one, the original
                                    // otherwise. the fallback is what keeps the desktop working
                                    // while it has no decoder, and it is also what happens on a
                                    // phone whose codecs refused the format
                                    val aEnvoyer = decoupe(lu, fenetre, encodeur) ?: chemin
                                    onAjouter(aEnvoyer, nom, emoji.ifBlank { null })
                                }
                                    .onSuccess { repartir() }
                                    .onFailure { message = it.message ?: t.ajoutImpossible }
                                enCours = false
                            }
                        }
                    }
                }
                Box(Modifier.padding(bottom = tokens.espL))
            }
        }

        message?.let {
            Text(
                it,
                color = tokens.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espS),
            )
        }
    }
}

@Composable
fun GestionEmojisDuServeur(
    emotes: List<CustomEmote>,
    onAjouter: suspend (chemin: String, nom: String, autocollant: Boolean) -> Unit,
    onRetirer: suspend (String) -> Unit,
    choisirFichier: ChoisirFichier,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val scope = rememberCoroutineScope()

    var choisie by remember { mutableStateOf<String?>(null) }
    var nom by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // the choice is asked for, not inferred. nothing in the file says which of the two you want,
    // neither the size nor the format. the same 128 px png makes a good emoji and a small sticker,
    // and it is the intent that decides
    var autocollant by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            t.emojisEtAutocollantsDuServeur,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = tokens.typography.headingSize.sp,
            modifier = Modifier.padding(horizontal = tokens.espXl, vertical = 6.dp),
        )
        Text(
            t.emojisExplication,
            color = tokens.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = tokens.espXl, end = tokens.espXl, bottom = 10.dp),
        )

        if (emotes.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(64.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                    .padding(horizontal = 14.dp),
            ) {
                items(emotes, key = { it.shortcode }) { emote ->
                    Column(
                        Modifier.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(40.dp)) { EmojiEnLigne(emote.url) }
                        Text(
                            // the prefix says which family the image belongs to. without it the
                            // grid mixes the two with no way to tell them apart
                            if (emote.estAutocollant) "▣ ${emote.shortcode}" else emote.shortcode,
                            color = tokens.textMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        Text(
                            "retirer",
                            color = tokens.danger,
                            fontSize = 9.sp,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    runCatching { onRetirer(emote.shortcode) }
                                        .onFailure { message = it.message }
                                }
                            },
                        )
                    }
                }
            }
        }

        val chemin = choisie
        if (chemin == null) {
            BoutonPlein(t.ajouterUneImage, modifier = Modifier.padding(tokens.espXl)) {
                // the filename is the suggestion, stripped of what cannot go into a `:shortcode:`
                choisirFichier(TypeFichier.IMAGE) { chemin ->
                    choisie = chemin
                    nom = nomProposeDe(chemin)
                }
            }
        } else {
            Column(Modifier.padding(horizontal = tokens.espXl)) {
                ChampNom(nom) { nom = it }
                // two pills rather than a switch: emoji or sticker is not an option you turn on, it
                // is a choice between two natures, and a switch labelled "sticker" would suggest
                // you can have both at once
                Row(
                    Modifier.padding(bottom = tokens.espM),
                    horizontalArrangement = Arrangement.spacedBy(tokens.espS),
                ) {
                    listOf(false to t.emoji, true to t.autocollant).forEach { (cible, libelle) ->
                        val actif = autocollant == cible
                        Text(
                            libelle,
                            color = if (actif) tokens.bg else tokens.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(tokens.radiusLarge))
                                .background(if (actif) tokens.accent else tokens.surfaceRaised)
                                .clickable { autocollant = cible }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        BoutonPlein(t.annuler, actif = !enCours) { choisie = null; nom = "" }
                    }
                    Box(Modifier.weight(1f)) {
                        BoutonPlein(
                            when {
                                enCours -> t.envoiEnCours
                                autocollant -> t.ajouterLAutocollant
                                else -> t.ajouterEmojiNomme(nom)
                            },
                            actif = nom.isNotBlank() && !enCours,
                        ) {
                            enCours = true
                            message = null
                            scope.launch {
                                runCatching { onAjouter(chemin, nom, autocollant) }
                                    .onSuccess { choisie = null; nom = "" }
                                    .onFailure { message = it.message ?: t.ajoutImpossible }
                                enCours = false
                            }
                        }
                    }
                }
                Box(Modifier.padding(bottom = tokens.espL))
            }
        }

        message?.let {
            Text(
                it,
                color = tokens.danger,
                fontSize = tokens.typography.captionSize.sp,
                modifier = Modifier.padding(horizontal = tokens.espXl, vertical = tokens.espS),
            )
        }
    }
}

/**
 * @param libelle what the field is asking for. the shortcode by default, that being the original
 *   case, a custom emoji whose name is what you type between colons
 * @param filtre applied as you type, see below. the default suits a shortcode. a sound's name is
 *   allowed spaces, and an emoji is allowed to be an emoji
 */
@Composable
fun ChampNom(
    valeur: String,
    libelle: String = "RACCOURCI",
    filtre: (String) -> String = { texte ->
        texte.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)
    },
    onChange: (String) -> Unit,
) {
    val tokens = LocalTokens.current
    Column(Modifier.fillMaxWidth().padding(vertical = tokens.espM)) {
        Text(
            libelle,
            color = tokens.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 5.dp),
        )
        Box(
            Modifier.fillMaxWidth()
                .background(tokens.surfaceRaised, RoundedCornerShape(tokens.radiusMedium))
                .padding(horizontal = 14.dp, vertical = tokens.espM)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = valeur,
                // the filter is applied as you type and not on submit: refusing afterwards would
                // mean working out what it disliked, whereas here the forbidden character simply
                // never appears
                onValueChange = { texte -> onChange(filtre(texte)) },
                singleLine = true,
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = tokens.textPrimary,
                    fontSize = tokens.typography.bodySize.sp,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

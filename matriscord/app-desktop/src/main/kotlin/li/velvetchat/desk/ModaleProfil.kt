package li.velvetchat.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.velvetchat.app.ui.*
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.DecorationAvatar
import li.velvetchat.core.model.FAMILLE_CADRES
import li.velvetchat.core.model.FAMILLE_DECORATIONS
import li.velvetchat.core.model.FAMILLE_EFFETS
import li.velvetchat.core.model.FAMILLE_PLAQUES
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.ProfilEtendu
import li.velvetchat.core.model.Profile
import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JColorChooser

/** the cream of the left panel's headings, measured */
private val CREME = Color(0xFFFBFFBD)
private val FOND_PANNEAU = Color(0xFF16191E)
private val BORD_PANNEAU = Color(0xFF222731)
private val FILET_PANNEAU = Color(0xFF242831)
private val FOND_TUILE = Color(0xFF1C1F27)
private val BORD_TUILE = Color(0xFF21252F)

/**
 * edit profile on the desktop, measured to the pixel off the reference at 1920 by 1080
 *
 * | what is measured        | value                                                            |
 * |-------------------------|------------------------------------------------------------------| |
 * modal                   | 1201 by 800 centred, scrim about 70%                              | |
 * left panel              | 241 wide, dark fill, hairline border, radius 12 on the left       | |
 * panel header            | 49 tall, "main profile" 16 cream, overflow on the right, a rule   | |
 * section headings        | 14 semibold cream, 17 then 24 above, 8 below                      | |
 * tiles                   | 208 by 42 for the plate and nickname, 100 by 100 in pairs 8 apart | |
 * theme colour swatches   | 24, radius 6, 2 point white border, 3 from the top and 4 below    | |
 * right half              | the theme's gradient, a 32 point white cross 23 from the corner   | |
 * preview                 | 400 wide, 49 from the edge and the top, banner 140, avatar 120    | |
 * preview buttons         | "send a message" 32 tall, 32 point squares, 8 apart               | |
 * preview sections        | label 12 bold, value 14, 24 between sections                      |
 *
 * removed because velvet does not have them: the board, activity and wishlist tabs and their
 * widgets, the badges, "member since" (the protocol gives no registration date), the connections,
 * the nitro marks. the tabs' place holds the chosen tile's catalogue instead, plates, decorations,
 * effects
 *
 * everything is written into the extended profile, with the same keys as the phone's editor
 */
@Composable
fun ModaleProfil(
    moteur: MatrixEngine,
    profil: Profile?,
    etendu: ProfilEtendu?,
    onProfil: (Profile) -> Unit,
    onEtendu: (ProfilEtendu) -> Unit,
    onFermer: () -> Unit,
) {
    val t = textes()
    val tokens = LocalTokens.current
    val portee = rememberCoroutineScope()
    val fenetre = LocalFenetre.current
    val profilEtendu = etendu ?: ProfilEtendu()
    val userId = profil?.userId.orEmpty()
    val couleurs = remember(etendu) { couleursProfil(etendu) }

    /** the section whose picker is open on the right: one of the catalogue families, or null */
    var rubrique by remember { mutableStateOf<String?>(null) }
    var panneauReplie by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var catalogues by remember { mutableStateOf<Map<String, List<DecorationAvatar>>>(emptyMap()) }
    LaunchedEffect(moteur) {
        catalogues = listOf(FAMILLE_PLAQUES, FAMILLE_DECORATIONS, FAMILLE_EFFETS, FAMILLE_CADRES).associateWith {
            runCatching { moteur.toutesLesDecorations(it) }.getOrDefault(emptyList())
        }
    }

    fun ecrire(vararg paires: Pair<String, String?>) {
        portee.launch {
            runCatching {
                paires.forEach { (cle, valeur) -> moteur.setProfilEtendu(cle, valeur) }
                onEtendu(moteur.profilEtendu(moteur.currentSession().userId))
                erreur = null
            }.onFailure { erreur = t.profilNonEnregistre(it.message.orEmpty()) }
        }
    }

    /** an image chosen with windows' picker, uploaded, then written under [cle] */
    fun importer(cle: String) {
        portee.launch {
            val chemin = choisirImage(fenetre?.awt)?.absolutePath ?: return@launch
            runCatching { moteur.uploadMedia(chemin) }
                .onSuccess { ecrire(cle to it) }
                .onFailure { erreur = t.imageNonEnvoyee(it.message.orEmpty()) }
        }
    }

    /** the open colour picker: title, profile key, current value */
    var choixCouleur by remember { mutableStateOf<Triple<String, String, String?>?>(null) }

    /**
     * opens velvet's own [SelecteurCouleur], java's having been judged unusable, and it writes
     * `#RRGGBB` under [cle]
     */
    fun couleur(titre: String, cle: String, actuelle: String?) {
        choixCouleur = Triple(titre, cle, actuelle)
    }

    val sansEffet = remember { MutableInteractionSource() }
    // the scrim: darkens the app, measured at about 70%, and closes on a click outside the modal
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = sansEffet, indication = null, onClick = onFermer),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f)
                .widthIn(max = 1201.dp).heightIn(max = 800.dp)
                // a click inside the modal does not close it
                .clickable(interactionSource = sansEffet, indication = null) {},
        ) {
            if (!panneauReplie) {
                Column(
                    Modifier.width(241.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        .background(FOND_PANNEAU)
                        .border(1.dp, BORD_PANNEAU, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(49.dp).padding(start = 16.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.profilPrincipal, color = CREME, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Icone(Icones.chevronBas, tint = CREME, taille = 14.dp, description = null)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).curseurMain().clickable { panneauReplie = true }, contentAlignment = Alignment.Center) {
                            Icone(Icones.doubleChevron, tint = CREME, taille = 20.dp, description = t.replierLePanneau)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(FILET_PANNEAU))

                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        TitreRubrique(t.plaqueNominative, premier = true)
                        Tuile(Modifier.fillMaxWidth().height(42.dp), actif = rubrique == FAMILLE_PLAQUES, onClick = { rubrique = FAMILLE_PLAQUES }) {
                            // cropped rather than stretched, see the direct conversation row
                            profilEtendu.plaque?.let {
                                EmojiEnLigne(it, contentScale = ContentScale.Crop)
                            }
                            // the reference's preview over it: a ghost avatar and the name bar
                            Row(Modifier.fillMaxSize().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AvatarFantome(26)
                                Spacer(Modifier.width(14.dp))
                                Box(Modifier.width(110.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.22f)))
                            }
                        }

                        TitreRubrique(t.avatarEtDecoration)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tuile(Modifier.weight(1f).aspectRatio(1f), onClick = {
                                portee.launch {
                                    val chemin = choisirImage(fenetre?.awt, t.choisirUnAvatar)?.absolutePath ?: return@launch
                                    runCatching { moteur.setAvatar(chemin); onProfil(moteur.profile()) }
                                        .onFailure { erreur = t.avatarNonChange(it.message.orEmpty()) }
                                }
                            }) {
                                Avatar(userId = userId, displayName = profil?.displayName, avatarUrl = profil?.avatarUrl, taille = 70.dp, decoration = null)
                            }
                            Tuile(Modifier.weight(1f).aspectRatio(1f), actif = rubrique == FAMILLE_DECORATIONS, onClick = { rubrique = FAMILLE_DECORATIONS }) {
                                Box(contentAlignment = Alignment.Center) {
                                    AvatarFantome(56)
                                    profilEtendu.decoration?.let {
                                        Box(Modifier.size(68.dp)) { EmojiEnLigne(it, contentScale = ContentScale.Fit) }
                                    }
                                }
                            }
                        }

                        // your own nickname, editable here, which it was nowhere on the desktop:
                        // you wore your matrix account's name with no way to change it. this is the
                        // protocol's `displayname`, so what everybody sees. the `@…` id does not
                        // move
                        TitreRubrique(t.pseudoDAffichage)
                        var pseudo by remember(profil?.displayName) { mutableStateOf(profil?.displayName.orEmpty()) }
                        Box(
                            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(8.dp))
                                .background(FOND_TUILE).border(1.dp, BORD_TUILE, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (pseudo.isEmpty()) {
                                Text(
                                    userId.removePrefix("@").substringBefore(':'),
                                    color = Color(0xFF80848E),
                                    fontSize = 14.sp,
                                )
                            }
                            BasicTextField(
                                value = pseudo,
                                onValueChange = { pseudo = it.take(64) },
                                singleLine = true,
                                textStyle = TextStyle(color = Color(0xFFF2F3F5), fontSize = 14.sp),
                                cursorBrush = SolidColor(CREME),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // saved on its own once the typing settles, like the bio
                        LaunchedEffect(pseudo) {
                            val propre = pseudo.trim()
                            if (propre.isEmpty() || propre == profil?.displayName) return@LaunchedEffect
                            delay(900)
                            runCatching { moteur.setDisplayName(propre); onProfil(moteur.profile()) }
                                .onFailure { erreur = t.pseudoNonChange(it.message.orEmpty()) }
                        }

                        // two halves: the left sets the gradient's first colour, the right the
                        // second
                        TitreRubrique(t.styleDuPseudonyme)
                        Tuile(Modifier.fillMaxWidth().height(42.dp), onClick = {}) {
                            Text(
                                profil?.displayName ?: userId.removePrefix("@").substringBefore(':'),
                                color = Color(0xFFF2F3F5),
                                style = TextStyle(brush = degradePseudo(etendu)),
                                fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxHeight().curseurMain().clickable {
                                    couleur(t.pseudoPremiereCouleur, ProfilEtendu.CLE_NOM_COULEUR_A, profilEtendu.nomCouleurA)
                                })
                                Box(Modifier.weight(1f).fillMaxHeight().curseurMain().clickable {
                                    couleur(t.pseudoSecondeCouleur, ProfilEtendu.CLE_NOM_COULEUR_B, profilEtendu.nomCouleurB ?: profilEtendu.nomCouleurA)
                                })
                            }
                        }

                        // the bio is edited here and nowhere else in the app, since there was no
                        // way to change it at all. saved on its own, like the note
                        TitreRubrique(t.aProposDeMoi)
                        // the field does not reload under your fingers
                        //
                        // the state had `profilEtendu.bio` as its `remember` key: the automatic
                        // write went out after 900 ms, the profile was read back, the key changed,
                        // and the state restarted from the server's value, so from what had been
                        // typed 900 ms earlier. everything typed within that same second vanished,
                        // cursor included, while somebody was still writing
                        //
                        // so the remote value only comes back when the field does not have focus:
                        // that is the one window where copying it over nobody's work, and it is
                        // what lets a bio changed from the phone show up here
                        var bio by remember { mutableStateOf(profilEtendu.bio.orEmpty()) }
                        var bioEnEdition by remember { mutableStateOf(false) }
                        LaunchedEffect(profilEtendu.bio) {
                            if (!bioEnEdition) bio = profilEtendu.bio.orEmpty()
                        }
                        Box(
                            Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(8.dp))
                                .background(FOND_TUILE).border(1.dp, BORD_TUILE, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                        ) {
                            if (bio.isEmpty()) Text(t.parleUnPeuDeToi, color = Color(0xFF80848E), fontSize = 13.sp)
                            BasicTextField(
                                value = bio,
                                // the same limit as the phone, and for the same reason: capping on
                                // one side and not the other means a bio silently shortened as soon
                                // as it is reopened elsewhere. what is already too long is not cut,
                                // the counter says so
                                onValueChange = { saisi ->
                                    bio = if (saisi.length <= bio.length) {
                                        saisi
                                    } else {
                                        saisi.take(maxOf(ProfilEtendu.BIO_MAX, bio.length))
                                    }
                                },
                                textStyle = TextStyle(color = Color(0xFFF2F3F5), fontSize = 13.sp, lineHeight = 18.sp),
                                cursorBrush = SolidColor(CREME),
                                modifier = Modifier.fillMaxSize()
                                    .onFocusChanged { bioEnEdition = it.isFocused },
                            )
                        }
                        Text(
                            "${bio.length} / ${ProfilEtendu.BIO_MAX}",
                            color = if (bio.length > ProfilEtendu.BIO_MAX) Color(0xFFED4245) else Color(0xFF80848E),
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                        LaunchedEffect(bio) {
                            if (bio == profilEtendu.bio.orEmpty()) return@LaunchedEffect
                            delay(900)
                            ecrire(ProfilEtendu.CLE_BIO to bio.takeIf { it.isNotBlank() })
                        }

                        TitreRubrique(t.themeEtBanniere)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val haut = profilEtendu.couleurBasse?.let { runCatching { it.toColor() }.getOrNull() }
                            val bas = profilEtendu.couleurHaute?.let { runCatching { it.toColor() }.getOrNull() }
                            Tuile(
                                Modifier.weight(1f).aspectRatio(1f),
                                fond = if (haut != null && bas != null) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(haut, bas)) else null,
                                onClick = {},
                            ) {
                                // the top square sets the top of the gradient and the bottom one
                                // the bottom, which is what you see
                                Column(Modifier.fillMaxSize().padding(top = 3.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    CarreCouleur(haut) { couleur(t.themeCouleurHaut, ProfilEtendu.CLE_COULEUR_BASSE, profilEtendu.couleurBasse) }
                                    Spacer(Modifier.weight(1f))
                                    CarreCouleur(bas) { couleur(t.themeCouleurBas, ProfilEtendu.CLE_COULEUR_HAUTE, profilEtendu.couleurHaute) }
                                }
                            }
                            Tuile(Modifier.weight(1f).aspectRatio(1f), onClick = { importer(ProfilEtendu.CLE_BANNIERE) }) {
                                profilEtendu.banniere?.let { EmojiEnLigne(it, contentScale = ContentScale.Crop) }
                                    ?: Icone(Icones.photo, tint = Color(0xFF80848E), taille = 24.dp, description = t.banniere)
                            }
                        }

                        TitreRubrique(t.effetEtCadre)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tuile(Modifier.weight(1f).aspectRatio(1f), actif = rubrique == FAMILLE_EFFETS, onClick = { rubrique = FAMILLE_EFFETS }) {
                                val effet = profilEtendu.effet
                                if (effet != null) {
                                    Box(Modifier.size(width = 60.dp, height = 90.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFF1E9DC))) {
                                        EmojiEnLigne(effet, contentScale = ContentScale.Crop)
                                    }
                                } else {
                                    CarteFantome(avecPlus = true)
                                }
                            }
                            // the frame has its own catalogue, published by the shop script, and
                            // importing an image stays offered at the top of it
                            Tuile(Modifier.weight(1f).aspectRatio(1f), actif = rubrique == FAMILLE_CADRES, onClick = { rubrique = FAMILLE_CADRES }) {
                                profilEtendu.cadre?.let { ApercuCadre(it, Modifier.fillMaxWidth().padding(6.dp)) } ?: CarteFantome(avecPlus = true)
                            }
                        }

                        erreur?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, color = tokens.danger, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // preview and picker
            val formeDroite = if (panneauReplie) RoundedCornerShape(12.dp) else RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(formeDroite)
                    .background(couleurs.fond)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), formeDroite),
            ) {
                Row(Modifier.fillMaxSize().padding(start = 49.dp, top = 49.dp, end = 49.dp)) {
                    ApercuProfil(
                        moteur, profil, etendu, couleurs,
                        Modifier.width(400.dp).fillMaxHeight(),
                        onClicTag = { rubrique = RUBRIQUE_TAG },
                    )
                    Spacer(Modifier.width(32.dp))
                    if (rubrique == RUBRIQUE_TAG) {
                        ChoixTagBureau(
                            moteur = moteur,
                            porte = tagDe(etendu),
                            couleurs = couleurs,
                            onChoisir = { tag ->
                                // both fields leave together: an emoji with no text, or the other
                                // way round, does not make a tag, and `tagDe` wants both
                                ecrire(
                                    ProfilEtendu.CLE_TAG_EMOJI to tag?.emoji,
                                    ProfilEtendu.CLE_TAG_TEXTE to tag?.texte,
                                )
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    rubrique?.takeIf { it != RUBRIQUE_TAG }?.let { famille ->
                        ChoixCatalogue(
                            titre = when (famille) {
                                FAMILLE_PLAQUES -> t.plaqueNominative
                                FAMILLE_EFFETS -> t.effetDeProfil
                                FAMILLE_CADRES -> t.cadreDeProfil
                                else -> t.decorationDAvatar
                            },
                            famille = famille,
                            couleurs = couleurs,
                            catalogue = catalogues[famille],
                            porte = when (famille) {
                                FAMILLE_PLAQUES -> profilEtendu.plaque
                                FAMILLE_EFFETS -> profilEtendu.effet
                                FAMILLE_CADRES -> profilEtendu.cadre
                                else -> profilEtendu.decoration
                            },
                            onChoisir = { mxc ->
                                ecrire(
                                    when (famille) {
                                        FAMILLE_PLAQUES -> ProfilEtendu.CLE_PLAQUE
                                        FAMILLE_EFFETS -> ProfilEtendu.CLE_EFFET
                                        FAMILLE_CADRES -> ProfilEtendu.CLE_CADRE
                                        else -> ProfilEtendu.CLE_DECORATION
                                    } to mxc,
                                )
                            },
                            onImporter = if (famille == FAMILLE_CADRES) {
                                { importer(ProfilEtendu.CLE_CADRE) }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                if (panneauReplie) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(12.dp).size(28.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.25f)).curseurMain().clickable { panneauReplie = false },
                        contentAlignment = Alignment.Center,
                    ) { Icone(Icones.doubleChevron, tint = Color.White, taille = 18.dp, description = t.deplierLePanneau) }
                }
                // the cross: a 32 point white circle, centred 23 from the corner
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(32.dp)
                        .clip(CircleShape).background(Color(0xFFEBEAEE)).curseurMain().clickable(onClick = onFermer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icone(Icones.croixGrasse, tint = Color(0xFF28282D), taille = 16.dp, description = t.fermer)
                }
            }
        }
        choixCouleur?.let { (titre, cle, actuelle) ->
            // a profile theme is picked as two colours in the same panel, and the rest, the
            // nickname's colour, keeps the simple picker
            val theme = cle == ProfilEtendu.CLE_COULEUR_HAUTE || cle == ProfilEtendu.CLE_COULEUR_BASSE
            val haute = cle == ProfilEtendu.CLE_COULEUR_HAUTE
            SelecteurCouleur(
                titre = if (theme) t.themeDeProfil else titre,
                initiale = actuelle?.let { runCatching { it.toColor() }.getOrNull() } ?: Color(0xFF5865F2),
                seconde = if (!theme) null else {
                    (if (haute) profilEtendu.couleurBasse else profilEtendu.couleurHaute)
                        ?.let { runCatching { it.toColor() }.getOrNull() } ?: Color(0xFF9B59B6)
                },
                onValiderDeux = if (!theme) null else { premiere, deuxieme ->
                    choixCouleur = null
                    if (haute) {
                        ecrire(ProfilEtendu.CLE_COULEUR_HAUTE to hexDe(premiere))
                        ecrire(ProfilEtendu.CLE_COULEUR_BASSE to hexDe(deuxieme))
                    } else {
                        ecrire(ProfilEtendu.CLE_COULEUR_BASSE to hexDe(premiere))
                        ecrire(ProfilEtendu.CLE_COULEUR_HAUTE to hexDe(deuxieme))
                    }
                },
                onValider = { choisie ->
                    choixCouleur = null
                    ecrire(cle to hexDe(choisie))
                },
                onFermer = { choixCouleur = null },
            )
        }
    }
}

@Composable
private fun TitreRubrique(titre: String, premier: Boolean = false) {
    Text(
        titre,
        color = CREME,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = if (premier) 17.dp else 24.dp, bottom = 8.dp),
    )
}

/** a left panel tile: radius 8, dark fill, accent border when its section is open */
@Composable
private fun Tuile(
    modifier: Modifier,
    actif: Boolean = false,
    fond: androidx.compose.ui.graphics.Brush? = null,
    onClick: () -> Unit,
    contenu: @Composable () -> Unit,
) {
    val tokens = LocalTokens.current
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(fond ?: SolidColor(FOND_TUILE))
            .border(if (actif) 2.dp else 1.dp, if (actif) tokens.accent else BORD_TUILE, RoundedCornerShape(8.dp))
            .curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { contenu() }
}

/**
 * the colour square for one end of the theme: 24, radius 6, a 2 point translucent white border
 */
@Composable
private fun CarreCouleur(couleur: Color?, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
            .background(couleur ?: Color(0xFF3F4248))
            .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .curseurMain().clickable(onClick = onClick),
    )
}

/** the faceless grey avatar the reference puts in the preview tiles */
@Composable
private fun AvatarFantome(cote: Int) {
    Box(Modifier.size(cote.dp).clip(CircleShape).background(Color(0xFF4E5058)), contentAlignment = Alignment.Center) {
        Box(Modifier.size((cote * 0.42f).dp).clip(RoundedCornerShape((cote * 0.12f).dp)).background(Color(0xFF6D6F78)))
    }
}

/** the dotted miniature card of the frame tile, with its pink plus */
@Composable
private fun CarteFantome(avecPlus: Boolean) {
    Box(
        Modifier.size(width = 75.dp, height = 76.dp).drawBehind {
            drawRoundRect(
                Color.White.copy(alpha = 0.22f),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.size(width = 60.dp, height = 56.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF2B2D33)).padding(6.dp)) {
            Box(Modifier.fillMaxWidth().height(10.dp).background(Color(0xFF3A3D44)))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF4E5058)))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(34.dp).height(3.dp).background(Color(0xFF3A3D44)))
            Spacer(Modifier.height(3.dp))
            Box(Modifier.width(24.dp).height(3.dp).background(Color(0xFF3A3D44)))
        }
        if (avecPlus) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFEBEBEF)), contentAlignment = Alignment.Center) {
                Icone(Icones.plusGras, tint = Color(0xFFE600C8), taille = 13.dp, description = null)
            }
        }
    }
}

/**
 * the live preview: the card as other people will see it. banner 140, a 120 avatar ringed and
 * lifted by 60, the bubble, the nickname at 24, the id and tag with a chevron, the buttons, then
 * the bio and the note (only visible to you)
 */
@Composable
private fun ApercuProfil(
    moteur: MatrixEngine,
    profil: Profile?,
    etendu: ProfilEtendu?,
    couleurs: CouleursProfil,
    modifier: Modifier,
    onClicTag: (() -> Unit)? = null,
) {
    val t = textes()
    val userId = profil?.userId.orEmpty()
    var statut by remember { mutableStateOf(li.velvetchat.core.model.Statut.INCONNU) }
    var note by remember { mutableStateOf<String?>(null) }
    var noteLue by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(userId) {
        if (userId.isEmpty()) return@LaunchedEffect
        runCatching { moteur.presence(userId) }.onSuccess { statut = it }
        noteLue = runCatching { moteur.note(userId) }.getOrNull().orEmpty()
        note = noteLue
    }
    // the note saves itself once the typing settles
    LaunchedEffect(note) {
        val texte = note ?: return@LaunchedEffect
        if (texte == noteLue) return@LaunchedEffect
        delay(800)
        runCatching { moteur.setNote(userId, texte.takeIf { it.isNotBlank() }) }.onSuccess { noteLue = texte }
    }

    // the clipping moved down a level. it used to sit on the box that also holds the frame, so the
    // frame's overhangs, the reference's cat head, paws and wings, were cut at the card's edge,
    // which is everything that makes a profile frame. only the content is clipped now
    BoxWithConstraints(modifier) {
        val marges = margesDuCadre(etendu?.cadre, maxWidth)
        Box(Modifier.fillMaxSize().padding(marges)) {
        etendu?.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize(), derriere = true) }
        Box(
            Modifier.matchParentSize()
                .shadow(10.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(couleurs.fond),
        ) {
        // the effect plays over the whole card, avatar and text included, which is the reference.
        // `zIndex` puts it on top without changing the order, and it takes no clicks
        etendu?.effet?.let {
            // clipped to the card: the effect used to bleed onto whatever was around it
            Box(Modifier.matchParentSize().clipToBounds().zIndex(2f)) { EffetDeProfil(it) }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            EnteteCarteProfil(
                userId = userId,
                nom = profil?.displayName,
                avatarUrl = profil?.avatarUrl,
                etendu = etendu,
                couleurs = couleurs,
                presence = statut.presence,
                message = statut.message,
                banniere = 140.dp,
                avatar = 120.dp,
                anneau = 8.dp,
                decalage = 60.dp,
                pastille = 24.dp,
                gauche = 32.dp,
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                Spacer(Modifier.height(16.dp))
                NomCarteProfil(profil?.displayName ?: userId.removePrefix("@").substringBefore(':'), etendu, couleurs, taille = 24)
                Spacer(Modifier.height(8.dp))
                LigneIdentifiantProfil(userId, etendu, couleurs, chevronTag = true, onClicTag = onClicTag)

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(couleurs.boutonPrincipal)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icone(Icones.bullePleine, tint = Color.White, taille = 16.dp, description = null)
                        Spacer(Modifier.width(6.dp))
                        Text(t.envoyerUnMessage, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(9.dp))
                    CarreApercu(Icones.groupePlein, couleurs)
                    Spacer(Modifier.width(8.dp))
                    CarreApercu(Icones.troisPoints, couleurs)
                }

                etendu?.bio?.takeIf { it.isNotBlank() }?.let {
                    RubriqueApercu(t.bio, couleurs) {
                        ApercuAvecEmotes(texte = it, couleur = couleurs.texteFort, taille = 14.sp, maxLines = 40, lineHeight = 20.sp)
                    }
                }

                RubriqueApercu(t.noteVisibleParToi, couleurs) {
                    Box {
                        if (note.isNullOrEmpty()) {
                            Text(t.cliquePourAjouterNote, color = couleurs.texteDoux, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = note.orEmpty(),
                            onValueChange = { note = it },
                            textStyle = TextStyle(color = couleurs.texteFort, fontSize = 14.sp),
                            cursorBrush = SolidColor(couleurs.texteFort),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
        }
        // the frame over the whole card, overhangs included, see [CadreDeProfil]
        etendu?.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize()) }
        }
    }
}

@Composable
private fun RubriqueApercu(libelle: String, couleurs: CouleursProfil, contenu: @Composable ColumnScope.() -> Unit) {
    Column {
        Spacer(Modifier.height(24.dp))
        Text(libelle, color = couleurs.texteFort, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        contenu()
    }
}

/** a square button in the preview: 32, radius 8, a white veil, a dark icon */
@Composable
private fun CarreApercu(icone: androidx.compose.ui.graphics.vector.ImageVector, couleurs: CouleursProfil) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(couleurs.boutonSecondaire),
        contentAlignment = Alignment.Center,
    ) { Icone(icone, tint = couleurs.iconeBouton, taille = 18.dp, description = null) }
}

/** picking from one catalogue family: "none" first, then each item, with the worn one ringed */
@Composable
private fun ChoixCatalogue(
    titre: String,
    famille: String,
    couleurs: CouleursProfil,
    catalogue: List<DecorationAvatar>?,
    porte: String?,
    onChoisir: (String?) -> Unit,
    modifier: Modifier,
    /** an "import an image…" button at the top of the catalogue, for the frames */
    onImporter: (() -> Unit)? = null,
) {
    val t = textes()
    // search by name, accents ignored, and a sort. asked for on all three catalogues
    var recherche by remember(famille) { mutableStateOf("") }
    var tri by remember(famille) { mutableStateOf(0) }
    val affiches = remember(catalogue, recherche, tri) {
        val cle = recherche.trim().sansAccents()
        val trouves = catalogue.orEmpty().filter {
            // the search covers the collection too: you look for a theme, not for one ring's exact
            // name. that is how the reference's shop gets browsed
            cle.isEmpty() ||
                it.nom.sansAccents().contains(cle) ||
                it.pack.orEmpty().sansAccents().contains(cle) ||
                it.theme.orEmpty().sansAccents().contains(cle)
        }
        when (tri) {
            1 -> trouves.sortedByDescending { it.nom.lowercase() }
            2 -> trouves.sortedWith(compareByDescending<DecorationAvatar> { it.anime }.thenBy { it.nom.lowercase() })
            // by collection, with items that have none, from catalogues published earlier, at the
            // end
            3 -> trouves.sortedWith(
                compareBy<DecorationAvatar> { libelleCollection(it) ?: "￿" }
                    .thenBy { it.nom.lowercase() },
            )
            else -> trouves.sortedBy { it.nom.lowercase() }
        }
    }
    val rangeParCollection = tri == 3 && affiches.any { libelleCollection(it) != null }
    Column(modifier) {
        Text(titre, color = couleurs.texteFort, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(couleurs.champ)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icone(Icones.recherche, tint = couleurs.texteDoux, taille = 16.dp, description = null)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (recherche.isEmpty()) Text(t.rechercherNomOuCollection, color = couleurs.texteDoux, fontSize = 14.sp)
                    BasicTextField(
                        value = recherche,
                        onValueChange = { recherche = it },
                        singleLine = true,
                        textStyle = TextStyle(color = couleurs.texteFort, fontSize = 14.sp),
                        cursorBrush = SolidColor(couleurs.texteFort),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            onImporter?.let { importerImage ->
                Spacer(Modifier.width(8.dp))
                Text(
                    t.importerPoints,
                    color = couleurs.texteFort,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(couleurs.bloc)
                        .curseurMain().clickable(onClick = importerImage)
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                listOf("A → Z", "Z → A", t.animesDabord, t.parCollection)[tri],
                color = couleurs.texteFort,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(couleurs.bloc)
                    .curseurMain().clickable { tri = (tri + 1) % 4 }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            )
        }
        if (catalogue != null) {
            Text(t.surTotal(affiches.size, catalogue.size), color = couleurs.texteDoux, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        when {
            catalogue == null -> Text(t.chargementDuCatalogue, color = couleurs.texteDoux, fontSize = 13.sp)
            else -> LazyVerticalGrid(
                // two plates per row, at their own shape (448 by 84, so 5.33). one per line, they
                // were stretched across the full width, blurry and deformed
                columns = if (famille == FAMILLE_PLAQUES) GridCells.Fixed(3) else GridCells.Adaptive(88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "aucun") {
                    CaseCatalogue(famille, couleurs, porte == null, onClick = { onChoisir(null) }) {
                        Text(t.aucun, color = couleurs.texteDoux, fontSize = 13.sp)
                    }
                }
                if (rangeParCollection) {
                    // one section per collection, its name across the full width, which is the
                    // shop's filing
                    parCollection(affiches)
                        .forEach { (collection, objets) ->
                            item(key = "pack-$collection", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "$collection — ${objets.size}",
                                    color = couleurs.texteFort,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                )
                            }
                            items(objets, key = { it.id }) { objet ->
                                VignetteCatalogue(famille, couleurs, porte, objet, onChoisir)
                            }
                        }
                } else {
                    items(affiches, key = { it.id }) { objet ->
                        VignetteCatalogue(famille, couleurs, porte, objet, onChoisir)
                    }
                }
            }
        }
    }
}

/** one catalogue thumbnail, the same whether things are grouped by collection or not */
@Composable
private fun VignetteCatalogue(
    famille: String,
    couleurs: CouleursProfil,
    porte: String?,
    objet: DecorationAvatar,
    onChoisir: (String?) -> Unit,
) {
    CaseCatalogue(famille, couleurs, porte == objet.mxc, onClick = { onChoisir(objet.mxc) }) {
        // a frame shows around a card and not flat: on its own you only saw its top half, taken for
        // the whole object
        if (famille == FAMILLE_CADRES) {
            ApercuCadre(objet.mxc, Modifier.fillMaxWidth().padding(6.dp))
        } else {
            // a plate takes the original image, the scaled up thumbnail being blurry, and keeps its
            // proportions: the cell already has its shape, so `Fit` does not deform it
            EmojiEnLigne(
                if (famille == FAMILLE_PLAQUES) objet.mxc else objet.apercu ?: objet.mxc,
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun CaseCatalogue(famille: String, couleurs: CouleursProfil, porte: Boolean, onClick: () -> Unit, contenu: @Composable () -> Unit) {
    val tokens = LocalTokens.current
    Box(
        Modifier.fillMaxWidth()
            // a plate is 448 by 84, so the cell takes that ratio and the image never stretches
            .then(if (famille == FAMILLE_PLAQUES) Modifier.aspectRatio(448f / 84f) else Modifier.aspectRatio(
                    when (famille) {
                        FAMILLE_EFFETS -> 0.51f
                        FAMILLE_CADRES -> 0.62f
                        else -> 1f
                    },
                ))
            .clip(RoundedCornerShape(8.dp))
            .background(couleurs.bloc)
            .border(if (porte) 2.dp else 1.dp, if (porte) tokens.accent else couleurs.filet, RoundedCornerShape(8.dp))
            .curseurMain().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { contenu() }
}

/** the right panel section that is not a catalogue family */
private const val RUBRIQUE_TAG = "tag"

/**
 * picking the server tag on the desktop, which only existed on the phone
 *
 * the chip's chevron was drawn and led nowhere: you could see a choice existed without being able
 * to open it. one tag at a time, like the reference, since wearing three would make all of them
 * unreadable, and a tag exists to say where you come from at a glance
 *
 * the server's name sits next to each chip: two servers can offer the same emoji, and without it
 * you would not know which one you are wearing
 */
@Composable
private fun ChoixTagBureau(
    moteur: MatrixEngine,
    porte: li.velvetchat.core.model.TagServeur?,
    couleurs: CouleursProfil,
    onChoisir: (li.velvetchat.core.model.TagServeur?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = textes()
    var tags by remember {
        mutableStateOf<List<Triple<String, String, li.velvetchat.core.model.TagServeur>>>(
            emptyList(),
        )
    }
    LaunchedEffect(moteur) {
        tags = runCatching { moteur.tagsDisponibles() }.getOrDefault(emptyList())
    }
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(t.tagDuServeur, color = couleurs.texteFort, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(t.tagAide, color = couleurs.texteDoux, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        LigneTagBureau(couleurs, choisi = porte == null, onClick = { onChoisir(null) }) {
            Text(t.aucun, color = couleurs.texte, fontSize = 14.sp)
        }
        tags.forEach { (_, nomServeur, tag) ->
            val ici = porte?.emoji == tag.emoji && porte.texte == tag.texte
            LigneTagBureau(couleurs, choisi = ici, onClick = { onChoisir(tag) }) {
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    if (tag.emoji.startsWith("mxc://")) {
                        EmojiEnLigne(tag.emoji)
                    } else {
                        Text(
                            tag.emoji,
                            fontSize = 13.sp,
                            lineHeight = 13.sp,
                            fontFamily = LocalPoliceEmoji.current,
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(tag.texte, color = couleurs.texte, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    nomServeur,
                    color = couleurs.texteDoux,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (tags.isEmpty()) {
            Text(t.aucunTagDisponible, color = couleurs.texteDoux, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LigneTagBureau(
    couleurs: CouleursProfil,
    choisi: Boolean,
    onClick: () -> Unit,
    contenu: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(couleurs.bloc)
            .border(
                if (choisi) 2.dp else 0.dp,
                if (choisi) couleurs.texteFort else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .curseurMain().clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        contenu()
    }
}

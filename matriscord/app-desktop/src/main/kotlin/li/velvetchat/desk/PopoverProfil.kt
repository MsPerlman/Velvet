package li.velvetchat.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import li.velvetchat.app.ui.*
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.Presence
import li.velvetchat.core.model.Profile
import li.velvetchat.core.model.ProfilEtendu
import li.velvetchat.core.model.Statut

/**
 * the popover for your own profile, opened from the mini bar, measured to the pixel off the
 * reference
 *
 * | what is measured          | value                                              |
 * |---------------------------|----------------------------------------------------| | card
 * | 306 wide, radius 8, 2 point border in the theme    | | banner                    | 105
 * | | avatar                    | 80, ring 6, lifted 44 into the banner              | | status dot
 * | a 16 icon in a circle of about 26                  | | status bubble             | 8 from the
 * ring, centred 15 under the banner       | | nickname / id plus tag    | 20 bold / 14, tag chip 22
 * | | bio                       | 14, three lines, then "show full bio" at 12        | | blocks
 * | 16 margins, radius 8, 14 apart, 49 point rows      |
 *
 * colours: [couleursProfil]. removed because velvet does not have them: badges, the collection
 */
@Composable
fun PopoverProfil(
    moteur: MatrixEngine,
    profil: Profile?,
    etendu: ProfilEtendu?,
    statut: Statut,
    silence: Boolean,
    onStatut: (Statut, Boolean) -> Unit,
    onFermer: () -> Unit,
    /** null while the editor is not wired up: the entry does not appear rather than being dead */
    onModifierProfil: (() -> Unit)? = null,
) {
    val t = textes()
    val portee = rememberCoroutineScope()
    val pressePapier = copieur()
    var sousMenu by remember { mutableStateOf(false) }
    var copie by remember { mutableStateOf(false) }
    var bioComplete by remember { mutableStateOf(true) }
    val couleurs = remember(etendu) { couleursProfil(etendu) }
    val userId = profil?.userId.orEmpty()
    val sansEffet = remember { MutableInteractionSource() }

    // the scrim paints nothing: the reference does not darken the window, it only serves to dismiss
    Box(Modifier.fillMaxSize().clickable(interactionSource = sansEffet, indication = null, onClick = onFermer)) {
        Row(
            // 10 above the mini bar, like on the reference
            Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = MINI_BARRE + 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // the frame overhangs the card, which keeps its width. its overhang is a margin around
            // the popover, as on the reference, so the frame stays inside the window
            Box(Modifier.padding(debordsDuCadre(etendu?.cadre, 306.dp)).width(306.dp)) {
            Box(Modifier.fillMaxWidth()) {
            etendu?.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize(), derriere = true) }
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(couleurs.fond)
                    .border(2.dp, couleurs.bordure, RoundedCornerShape(8.dp))
                    .padding(2.dp)
                    // a click inside the card does not close it
                    .clickable(interactionSource = sansEffet, indication = null, onClick = { }),
            ) {
              // the profile effect over the whole card, like the reference, but clipped to the
              // card: it used to bleed onto the desktop around it
              etendu?.effet?.let {
                  Box(Modifier.matchParentSize().clipToBounds().zIndex(2f)) { EffetDeProfil(it) }
              }
              Column {
                EnteteCarteProfil(
                    userId = userId,
                    nom = profil?.displayName,
                    avatarUrl = profil?.avatarUrl,
                    etendu = etendu,
                    couleurs = couleurs,
                    presence = statut.presence,
                    message = statut.message,
                    banniere = 105.dp,
                    avatar = 80.dp,
                    anneau = 6.dp,
                    decalage = 44.dp,
                    pastille = 16.dp,
                    silence = silence,
                )
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Spacer(Modifier.height(12.dp))
                    NomCarteProfil(profil?.displayName ?: userId.removePrefix("@").substringBefore(':'), etendu, couleurs)
                    LigneIdentifiantProfil(userId, etendu, couleurs)

                    // yes, your own card shows your own activity. the reference does it, and it
                    // is the only way to check what everybody else is seeing without asking them
                    BlocActiviteDepuisProfil(
                        userId = userId,
                        brut = etendu?.activite,
                        couleurTitre = couleurs.texte,
                        couleurTexte = couleurs.texteDoux,
                        couleurIcone = couleurs.texte,
                        modifier = Modifier.padding(top = 10.dp),
                    )

                    etendu?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                        Spacer(Modifier.height(12.dp))
                        ApercuAvecEmotes(texte = bio, couleur = couleurs.texte, taille = 14.sp, maxLines = if (bioComplete) 20 else 3, lineHeight = 18.sp)
                        // ponytail: "long" is estimated from lines and characters, since the
                        // preview does not say whether it truncated
                        if (!bioComplete && (bio.lines().size > 3 || bio.length > 110)) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                if (bioComplete) t.reduireLaBio else t.afficherLaBioComplete,
                                color = couleurs.texte,
                                fontSize = 12.sp,
                                modifier = Modifier.curseurMain().clickable { bioComplete = !bioComplete },
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    BlocCarte(couleurs) {
                        onModifierProfil?.let { ouvrir ->
                            LigneCarte(
                                t.modifierLeProfil,
                                couleurs,
                                icone = { Icone(Icones.modifierPlein, tint = couleurs.texteDoux, taille = 18.dp, description = null) },
                                onClick = { onFermer(); ouvrir() },
                            )
                            FiletCarte(couleurs)
                        }
                        LigneCarte(
                            libelleEtat(statut, silence),
                            couleurs,
                            chevron = true,
                            icone = { IconePresence(statut.presence, 14.dp, silence) },
                            onClick = { sousMenu = !sousMenu },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    BlocCarte(couleurs) {
                        LigneCarte(
                            if (copie) t.identifiantCopie else t.copierIdUtilisateur,
                            couleurs,
                            icone = { IconeId(couleurs) },
                            onClick = {
                                if (userId.isNotEmpty()) {
                                    pressePapier(userId)
                                    copie = true
                                }
                            },
                        )
                        // the wallpaper: velvet's own, filed here with the id
                        FiletCarte(couleurs)
                        val fenetreFond = LocalFenetre.current
                        LigneCarte(
                            if (FondEcran.actif) t.changerLeFondDEcran else t.fondDEcranPoints,
                            couleurs,
                            icone = { Icone(Icones.photo, tint = couleurs.texteDoux, taille = 18.dp, description = null) },
                            onClick = {
                                onFermer()
                                FondEcran.choisir(fenetreFond?.awt)
                            },
                        )
                        if (FondEcran.actif) {
                            FiletCarte(couleurs)
                            LigneCarte(
                                t.retirerLeFondDEcran,
                                couleurs,
                                icone = { Icone(Icones.croixGrasse, tint = couleurs.texteDoux, taille = 16.dp, description = null) },
                                onClick = { FondEcran.retirer() },
                            )
                        }
                        // the theme, right under the wallpaper: they are the two appearance
                        // settings, and people look for them in the same place. same gesture as the
                        // wallpaper, you pick a file, and the file is the setting
                        FiletCarte(couleurs)
                        LigneCarte(
                            ThemeVelvet.nom?.let { t.themeEst(it) } ?: t.themeMenu,
                            couleurs,
                            // the lightbulb: there is no palette in the icon set, and it says
                            // appearance without doubling the photo of the wallpaper just above
                            icone = { Icone(Icones.objets, tint = couleurs.texteDoux, taille = 18.dp, description = null) },
                            onClick = {
                                onFermer()
                                ThemeVelvet.choisir(fenetreFond?.awt)
                            },
                        )
                        if (ThemeVelvet.actif) {
                            FiletCarte(couleurs)
                            LigneCarte(
                                t.revenirAuThemeParDefaut,
                                couleurs,
                                icone = { Icone(Icones.croixGrasse, tint = couleurs.texteDoux, taille = 16.dp, description = null) },
                                onClick = { ThemeVelvet.retirer() },
                            )
                        }
                    }
                }
              }
            }
            // the frame over the card, overhangs included. outside the card's box, which clips to
            // its rounded corners: inside it the head and paws were cut off
            etendu?.cadre?.let { CadreDeProfil(it, Modifier.matchParentSize()) }
            }
            }

            // the submenu sticks to the card's right edge, bottom against bottom, like the
            // reference
            if (sousMenu) {
                MenuEtat(
                    statut = statut,
                    silence = silence,
                    onChoisir = { presence, avecSilence ->
                        portee.launch {
                            runCatching {
                                moteur.setSilence(avecSilence)
                                moteur.setPresence(presence, statut.message)
                            }
                        }
                        onStatut(statut.copy(presence = presence), avecSilence)
                        sousMenu = false
                    },
                )
            }
        }
    }
}

/** the nickname gradient chosen in the profile, or null for the theme's colour */
internal fun degradePseudo(etendu: ProfilEtendu?): androidx.compose.ui.graphics.Brush? =
    etendu?.nomCouleurA?.let { a ->
        runCatching {
            androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(a.toColor(), (etendu.nomCouleurB ?: a).toColor()),
            )
        }.getOrNull()
    }

/** the cream of the reference's menus, measured */
private val CREME_MENU = Color(0xFFFBFFBD)

/**
 * the submenu of the four states, measured at 3x off the reference: 286 wide, a translucent dark
 * fill at radius 8, online (44) then a rule, idle (44), do not disturb and invisible with their 12
 * point subtitles. a 12 icon 8 from the edge, text at 32. the duration chevrons ("for 15 minutes"
 * and so on) are not reproduced, velvet has no timed state
 *
 * do not disturb is not a presence, which is why it looks like one. matrix has three (`online`,
 * `unavailable`, `offline`), and the fourth comes from the global mute, which is account data and
 * therefore follows every device
 */
@Composable
private fun MenuEtat(
    statut: Statut,
    silence: Boolean,
    onChoisir: (Presence, Boolean) -> Unit,
) {
    val t = textes()
    Column(
        Modifier.width(286.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xF0202328))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        EntreeEtat(Presence.EN_LIGNE, false, t.statutEnLigne, null, onChoisir)
        Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
        EntreeEtat(Presence.ABSENT, false, t.statutInactif, null, onChoisir)
        // the state is published (`org.matrix.msc3026.busy`) and mutes the notifications: other
        // people see it, and nothing rings here. before, only the mute existed and everybody saw
        // you as online
        EntreeEtat(Presence.NE_PAS_DERANGER, true, t.statutNePasDeranger, t.pasDeNotificationsBureau, onChoisir)
        EntreeEtat(Presence.INVISIBLE, false, t.statutInvisible, t.apparaitraHorsLigne, onChoisir)
    }
}

@Composable
private fun EntreeEtat(
    presence: Presence,
    avecSilence: Boolean,
    libelle: String,
    sousTitre: String?,
    onChoisir: (Presence, Boolean) -> Unit,
) {
    val survol = remember { MutableInteractionSource() }
    val survolee by survol.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .background(if (survolee) Color.White.copy(alpha = 0.05f) else Color.Transparent)
            .hoverable(survol)
            .curseurMain().clickable { onChoisir(presence, avecSilence) }
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconePresence(presence, 12.dp, avecSilence)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(libelle, color = CREME_MENU, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            sousTitre?.let {
                Text(it, color = CREME_MENU.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

/** what the status row shows: the fourth state beats the presence carrying it */
private fun libelleEtat(statut: Statut, silence: Boolean): String = when {
    silence || statut.presence == Presence.NE_PAS_DERANGER -> horsUi.statutNePasDeranger
    statut.presence == Presence.EN_LIGNE -> horsUi.statutEnLigne
    statut.presence == Presence.ABSENT -> horsUi.statutInactif
    statut.presence == Presence.INVISIBLE -> horsUi.statutInvisible
    else -> horsUi.etatInconnu
}

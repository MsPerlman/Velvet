package li.velvetchat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.velvetchat.core.model.DecorationAvatar

/**
 * searching and sorting a cosmetics catalogue, the same rule on both sides
 *
 * six hundred decorations cannot be scanned by eye, they have to be searched and sorted. the
 * desktop got it and the phone did not, hence this shared file rather than a second filter written
 * next to it, which would have diverged at the first addition
 *
 * the search covers the name and the collection: you search for a theme, not a ring's exact name.
 * accents do not count, in either direction
 */
enum class TriCatalogue {
    NOM, NOM_INVERSE, ANIMES, COLLECTION,
    ;

    // read at display time and not at construction: an enum constructor argument is evaluated once,
    // when the class loads, long before any language is chosen
    val libelle: String get() = when (this) {
        NOM -> "A → Z"
        NOM_INVERSE -> "Z → A"
        ANIMES -> horsUi.animesDabord
        COLLECTION -> horsUi.parCollection
    }

    fun suivant(): TriCatalogue = entries[(ordinal + 1) % entries.size]
}

/**
 * accents stripped, lowercased, without going through `java.text`, which does not exist in common
 * code. the table covers the latin we actually meet: this is a cosmetics catalogue, not a search
 * engine
 */
fun String.sansAccents(): String {
    val avec = "àâäáãåçéèêëíìîïñóòôöõúùûüýÿšž"
    val sans = "aaaaaaceeeeiiiinooooouuuuyysz"
    val sortie = StringBuilder(length)
    for (c in lowercase()) {
        val i = avec.indexOf(c)
        when {
            i >= 0 -> sortie.append(sans[i])
            c == 'œ' -> sortie.append("oe")
            c == 'æ' -> sortie.append("ae")
            c == 'ß' -> sortie.append("ss")
            else -> sortie.append(c)
        }
    }
    return sortie.toString()
}

/** the catalogue filtered then sorted. the search string is already accent stripped here */
fun filtrerCatalogue(
    catalogue: List<DecorationAvatar>,
    recherche: String,
    tri: TriCatalogue,
): List<DecorationAvatar> {
    val cle = recherche.trim().sansAccents()
    val trouves = catalogue.filter {
        cle.isEmpty() ||
            it.nom.sansAccents().contains(cle) ||
            it.pack.orEmpty().sansAccents().contains(cle) || it.theme.orEmpty().sansAccents().contains(cle)
    }
    return when (tri) {
        TriCatalogue.NOM -> trouves.sortedBy { it.nom.lowercase() }
        TriCatalogue.NOM_INVERSE -> trouves.sortedByDescending { it.nom.lowercase() }
        TriCatalogue.ANIMES -> trouves.sortedWith(
            compareByDescending<DecorationAvatar> { it.anime }.thenBy { it.nom.lowercase() },
        )
        // items with no collection, from catalogues published before we recorded one, go to the end
        TriCatalogue.COLLECTION -> trouves.sortedWith(
            compareBy<DecorationAvatar> { libelleCollection(it) ?: "￿" }
                .thenBy { it.nom.lowercase() },
        )
    }
}

/**
 * an item's section: theme then pack when the shop gives a theme, otherwise the pack. a pack that
 * is only the item itself does not make a section, and those go together under "misc". null means
 * no collection known
 */
fun libelleCollection(o: DecorationAvatar): String? {
    val theme = o.theme?.takeIf { it.isNotBlank() }
    val pack = o.pack?.takeIf { it.isNotBlank() }
    return when {
        theme != null && pack != null && pack != o.nom -> "$theme · $pack"
        theme != null -> theme
        pack != null && pack != o.nom -> pack
        pack != null -> horsUi.divers
        else -> null
    }
}

/** items grouped by theme and pack, for a sectioned grid. empty when none of them carries one */
fun parCollection(objets: List<DecorationAvatar>): List<Pair<String, List<DecorationAvatar>>> {
    if (objets.none { libelleCollection(it) != null }) return emptyList()
    return objets.groupBy { libelleCollection(it) ?: horsUi.sansCollection }.toList()
}

/**
 * the phone's bar: a search field, a sort button, and the count
 *
 * the desktop has its own, in the profile theme's colours, and this one takes the app's tokens.
 * what they share, the filtering rule and the sort order, is above
 */
@Composable
fun BarreCatalogue(
    recherche: String,
    onRecherche: (String) -> Unit,
    tri: TriCatalogue,
    onTri: () -> Unit,
    affiches: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val t = textes()
    val tokens = LocalTokens.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = tokens.espXl, vertical = tokens.espS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icone(Icones.recherche, tint = tokens.textMuted, taille = 16.dp, description = null)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (recherche.isEmpty()) {
                    Text(t.nomOuCollection, color = tokens.textMuted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = recherche,
                    onValueChange = onRecherche,
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (recherche.isNotEmpty()) {
                Box(Modifier.curseurMain().clickable { onRecherche("") }) {
                    Icone(Icones.fermer, tint = tokens.textMuted, taille = 16.dp, description = t.effacer)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            tri.libelle,
            color = tokens.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(tokens.radiusMedium))
                .background(tokens.surfaceRaised)
                .curseurMain().clickable(onClick = onTri)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
    Text(
        if (affiches == total) t.objetsNombre(total) else t.surTotal(affiches, total),
        color = tokens.textMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = tokens.espXl, bottom = tokens.espS),
    )
}

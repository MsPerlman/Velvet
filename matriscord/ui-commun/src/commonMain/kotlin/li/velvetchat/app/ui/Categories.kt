package li.velvetchat.app.ui

import li.velvetchat.core.model.Categorie
import li.velvetchat.core.model.Channel

/**
 * groups channels by category, in the server's order
 *
 * channels with no category come first and with no heading, which is where the reference puts them,
 * above the first category. inventing a title for them would add a line that exists nowhere
 *
 * empty categories are part of it, and that is the point
 *
 * reported from outside: creating a category did nothing. that was true on screen and false in the
 * protocol. the category really was created, but a category only existed here as a label carried by
 * its channels, and a brand new one has none, so it had no existence at all: you created it and
 * nothing appeared
 *
 * [categories] comes from the space's hierarchy ([li.velvetchat.core.model.Server]) and
 * therefore carries the empty ones too. they then show as a heading on their own, which is exactly
 * what the reference does, and what lets somebody file a channel into one afterwards
 *
 * the order is the server's and not the alphabet: sorting would reshuffle somebody else's filing on
 * every display
 */
fun grouperSalons(
    salons: List<Channel>,
    categories: List<Categorie> = emptyList(),
): List<Pair<String?, List<Channel>>> {
    val sans = salons.filter { it.categorie.isNullOrBlank() }
    val avec = salons.filterNot { it.categorie.isNullOrBlank() }.groupBy { it.categorie!! }

    // the known categories first, in the space's order, then the ones only the channels announce. a
    // channel can carry a label whose sub space has not been read back yet
    val noms = categories.map { it.nom }.filter { it.isNotBlank() }
    val nomsOrdonnes = noms + avec.keys.filterNot { it in noms }

    return (if (sans.isEmpty()) emptyList() else listOf<Pair<String?, List<Channel>>>(null to sans)) +
        nomsOrdonnes.distinct().map { nom -> nom as String? to avec[nom].orEmpty() }
}

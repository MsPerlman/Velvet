package li.velvetchat.app.ui

import androidx.compose.ui.graphics.Color
import li.velvetchat.core.model.Decorations
import li.velvetchat.core.model.Member
import li.velvetchat.core.model.RoleTier

/**
 * a server's nickname colours, computed once
 *
 * the timeline only has sender ids, so without this map colouring a nickname would mean asking the
 * server on every row
 *
 * the decorative role beats the tier, like on the card: that is the reference's rule, and without
 * it handing out a coloured role would change nothing in the conversation, which is where people
 * look at it most
 *
 * moved out of `Shell.kt` so the desktop can use it too. a second version would have given two
 * colour hierarchies for one server, visible side by side
 */
fun couleursDeRole(
    membres: List<Member>,
    paliers: List<RoleTier>,
    decorations: Decorations,
): Map<String, Color> = membres.mapNotNull { membre ->
    val couleur = decorations.de(membre.userId).firstNotNullOfOrNull { it.colorArgb }
        ?: palierDe(membre.role?.powerLevel, paliers)?.colorArgb
    couleur?.let { membre.userId to Color(it) }
}.toMap()

/**
 * each member's full tier, for the badges
 *
 * kept apart from the colour map because that one is read on every timeline row and has to stay
 * dumb
 */
fun paliersParMembre(
    membres: List<Member>,
    paliers: List<RoleTier>,
): Map<String, RoleTier> = membres.mapNotNull { membre ->
    palierDe(membre.role?.powerLevel, paliers)?.let { membre.userId to it }
}.toMap()

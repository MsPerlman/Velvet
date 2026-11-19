package li.velvetchat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * a person's name, as it should be written today
 *
 * a message carries the name its author had at the time they wrote it, which is how matrix files
 * things and is correct, and is not what anybody expects from a messenger. you renamed yourself and
 * the whole conversation carried on showing the old nickname, as did the member list until it was
 * read again
 *
 * the room's member list is authoritative when it knows the person, and it already carries the
 * nickname we gave them, the engine puts it there. otherwise we fall back on the message's name,
 * then on the id
 */
@Composable
fun nomAffiche(userId: String, nomDuMessage: String? = null): String =
    LocalMembres.current.firstOrNull { it.userId == userId }?.displayName?.takeIf { it.isNotBlank() }
        ?: nomDuMessage?.takeIf { it.isNotBlank() }
        ?: userId.removePrefix("@").substringBefore(':')

/**
 * the gradient somebody chose for their nickname, or null
 *
 * it only applied on their own profile card: everywhere else, messages, the member list, the
 * nickname stayed in its role colour, so the setting showed nowhere. a single colour gives a
 * gradient from itself to itself, which is what the reference does and which saves having two paths
 */
@Composable
fun brosseDuPseudo(userId: String): Brush? {
    val etendu = LocalProfils.current[userId] ?: return null
    val a = etendu.nomCouleurA?.let { couleurHexa(it) } ?: return null
    val b = etendu.nomCouleurB?.let { couleurHexa(it) } ?: a
    return Brush.horizontalGradient(listOf(a, b))
}

/** `#rrggbb` to a colour, or null when the string is not one */
internal fun couleurHexa(hexa: String): Color? {
    val propre = hexa.trim().removePrefix("#")
    if (propre.length != 6) return null
    val valeur = propre.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or valeur)
}

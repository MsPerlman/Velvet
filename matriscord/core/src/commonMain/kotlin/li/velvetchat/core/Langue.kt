package li.velvetchat.core

/**
 * the language of the sentences the engine itself produces
 *
 * the screens get their texts from `Traduction` in ui-commun, but core cannot see that module, and
 * its error messages, notification modes and role names end up on screen all the same. so core
 * keeps one flag, which ui-commun flips whenever the app's language changes
 */
object LangueMoteur {
    var anglais = false
}

/**
 * a sentence written in both languages, right where it is used
 *
 * both arguments are required, so a sentence cannot be added in one language only. the logs stay
 * in french and do not go through here
 */
fun tr(fr: String, en: String): String = if (LangueMoteur.anglais) en else fr

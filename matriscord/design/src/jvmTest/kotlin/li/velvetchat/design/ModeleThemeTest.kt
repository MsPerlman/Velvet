package li.velvetchat.design

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `design/themes/modele.json` is the theme with every field written out, and it is generated
 *
 * ## why generated and not written by hand
 *
 * a template is a promise about what you are allowed to set. written by hand it is wrong the
 * first time a field is added, and wrong silently: somebody copies it, sets the fields it lists,
 * and never learns about the ones it forgot. the readme already promises a theme can redefine
 * anything, so the list has to be the real one
 *
 * so the file is the serialised default theme, every field included, and this test fails when
 * the two drift. when it fails it writes what it expected next to itself, so fixing it is a copy
 *
 * ## why the default theme and not an empty one
 *
 * a template you can paste straight back in has to be a **valid, complete theme**. serialising
 * the default gives every key with a value that works, so it doubles as the starting point the
 * readme talks about: delete what you do not want to change, keep the rest
 */
class ModeleThemeTest {

    private val format = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    @Test
    fun `le modele liste tous les champs du theme`() {
        val attendu = format.encodeToString(Theme.serializer(), ThemeLoader.DEFAULT).trim()
        val fichier = fichierModele()

        if (!fichier.isFile || fichier.readText().replace("\r\n", "\n").trim() != attendu) {
            val secours = File("build/modele-attendu.json").apply {
                parentFile?.mkdirs()
                writeText(attendu + "\n")
            }
            assertEquals(
                attendu,
                fichier.takeIf { it.isFile }?.readText()?.replace("\r\n", "\n")?.trim().orEmpty(),
                "design/themes/modele.json n'est plus à jour. Le contenu attendu vient d'être " +
                    "écrit dans ${secours.absolutePath} : recopiez-le.",
            )
        }
    }

    @Test
    fun `le modele se recharge a l identique`() {
        val texte = fichierModele().readText()
        val relu = ThemeLoader.parse(texte)
        assertEquals(ThemeStatus.Loaded, relu.status)
        assertEquals(ThemeLoader.DEFAULT, relu.theme)
    }

    /**
     * the test runs from `design/`, but not always: depending on how it is invoked the current
     * directory is the repository root
     */
    private fun fichierModele(): File =
        listOf(File("themes/modele.json"), File("design/themes/modele.json"))
            .firstOrNull { it.isFile }
            ?: File("themes/modele.json")
}

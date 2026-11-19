package li.velvetchat.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * these tests cover one promise: a theme must never be able to break the app. it is that guarantee
 * that makes hot reloading usable, and without it, editing a file while the client runs would be a
 * gamble
 */
class ThemeLoaderTest {

    @Test
    fun `absence de theme donne le theme par defaut`() {
        listOf(null, "", "   ").forEach { input ->
            val result = ThemeLoader.parse(input)
            assertEquals(ThemeLoader.DEFAULT, result.theme)
            assertIs<ThemeStatus.Default>(result.status)
        }
    }

    @Test
    fun `un theme partiel ne redefinit que ce qu'il change`() {
        val result = ThemeLoader.parse(
            """{ "name": "Violet", "colors": { "accent": "#8B7BD8" } }"""
        )

        assertIs<ThemeStatus.Loaded>(result.status)
        assertEquals("Violet", result.theme.name)
        assertEquals("#8B7BD8", result.theme.colors.accent)

        // everything else keeps its default value: that is what lets you write a three line theme
        // rather than copying out every token
        assertEquals(ThemeLoader.DEFAULT.colors.background, result.theme.colors.background)
        assertEquals(ThemeLoader.DEFAULT.radii.serverIcon, result.theme.radii.serverIcon)
        assertEquals(ThemeLoader.DEFAULT.density.serverRailWidth, result.theme.density.serverRailWidth)
        assertTrue(result.theme.dark)
    }

    @Test
    fun `une cle inconnue est ignoree plutot que de faire echouer la lecture`() {
        // a real case: a theme written for a newer version of the client has to stay loadable,
        // otherwise any evolution of the tokens breaks the existing themes
        val result = ThemeLoader.parse(
            """{ "name": "Futur", "tokensDuFutur": { "quoi": 1 }, "colors": { "accent": "#112233" } }"""
        )

        assertIs<ThemeStatus.Loaded>(result.status)
        assertEquals("#112233", result.theme.colors.accent)
    }

    @Test
    fun `un theme malforme retombe sur le defaut en donnant la raison`() {
        val result = ThemeLoader.parse("{ ceci n'est pas du JSON")

        assertEquals(ThemeLoader.DEFAULT, result.theme)
        val status = assertIs<ThemeStatus.Invalid>(result.status)
        // the reason is kept so it can be shown in the settings: a theme silently ignored is more
        // puzzling than a theme refused with a reason
        assertTrue(status.reason.isNotBlank())
    }

    @Test
    fun `un theme serialise se relit a l'identique`() {
        val original = ThemeLoader.DEFAULT.copy(
            name = "Aller-retour",
            colors = ThemeLoader.DEFAULT.colors.copy(accent = "#010203"),
        )

        val reread = ThemeLoader.parse(ThemeLoader.serialize(original))

        assertIs<ThemeStatus.Loaded>(reread.status)
        assertEquals(original, reread.theme)
    }
}

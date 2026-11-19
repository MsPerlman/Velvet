package li.velvetchat.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * what these tests protect: the only barrier before an install
 *
 * this decoder decides what a phone will agree to download and hand to the installer. any slack
 * here, a fingerprint accepted too short, a plain http url let through, produces no visible error.
 * it produces an update nobody can verify. so half of these tests are refusals
 */
class ReleaseTest {

    private fun json(texte: String) = Json.parseToJsonElement(texte) as JsonObject

    private val EMPREINTE = "a".repeat(64)

    private fun valide(
        code: Long = 2,
        url: String = "https://velvet.exemple.org/dl/v2-arm64.apk",
        sha: String = EMPREINTE,
    ) = json(
        """
        {"versionCode":$code,"versionName":"0.2","notes":"des choses",
         "variantes":{"arm64-v8a":{"url":"$url","sha256":"$sha","taille":78123456}}}
        """.trimIndent()
    )

    @Test
    fun `une publication complete est lue`() {
        val release = Release.depuisJson(valide())!!
        assertEquals(2, release.versionCode)
        assertEquals("0.2", release.versionName)
        assertEquals("des choses", release.notes)
        assertEquals(78123456, release.variantes["arm64-v8a"]!!.taille)
    }

    // ------------------------------------------------------------------ the refusals

    @Test
    fun `une adresse en clair est ecartee`() {
        // the bytes would still be checkable by their fingerprint, but the request would tell the
        // whole network which version is being installed and when
        assertNull(Release.depuisJson(valide(url = "http://velvet.exemple.org/v2.apk")))
    }

    @Test
    fun `une empreinte mal formee est ecartee`() {
        assertNull(Release.depuisJson(valide(sha = "trop-court")))
        assertNull(Release.depuisJson(valide(sha = "z".repeat(64))))
        assertNull(Release.depuisJson(valide(sha = "a".repeat(63))))
    }

    @Test
    fun `une variante sans empreinte est retiree, pas installee sans verification`() {
        val racine = json(
            """
            {"versionCode":2,"versionName":"0.2","variantes":{
              "arm64-v8a":{"url":"https://x/a.apk"},
              "x86_64":{"url":"https://x/b.apk","sha256":"${"b".repeat(64)}"}}}
            """.trimIndent()
        )
        val release = Release.depuisJson(racine)!!
        assertEquals(setOf("x86_64"), release.variantes.keys)
    }

    @Test
    fun `une publication dont aucune variante ne survit est ignoree`() {
        val racine = json(
            """{"versionCode":2,"versionName":"0.2","variantes":{"arm64-v8a":{"url":"https://x/a.apk"}}}"""
        )
        assertNull(Release.depuisJson(racine))
    }

    @Test
    fun `un etat absent ou vide ne plante pas`() {
        assertNull(Release.depuisJson(null))
        assertNull(Release.depuisJson(json("{}")))
        assertNull(Release.depuisJson(json("""{"versionCode":2}""")))
    }

    // ------------------------------------------------------------------ the choice

    @Test
    fun `l ordre de preference de l appareil l emporte sur le notre`() {
        val racine = json(
            """
            {"versionCode":2,"versionName":"0.2","variantes":{
              "armeabi-v7a":{"url":"https://x/32.apk","sha256":"${"c".repeat(64)}"},
              "arm64-v8a":{"url":"https://x/64.apk","sha256":"${"d".repeat(64)}"}}}
            """.trimIndent()
        )
        val release = Release.depuisJson(racine)!!
        // `Build.SUPPORTED_ABIS` puts the most capable first. an arm64 device can run
        // armeabi-v7a, but there is no reason to inflict that on it
        assertEquals(
            "https://x/64.apk",
            release.pourAbis(listOf("arm64-v8a", "armeabi-v7a"))!!.url,
        )
    }

    @Test
    fun `aucune variante pour cette machine rend null plutot qu une au hasard`() {
        val release = Release.depuisJson(valide())!!
        assertNull(release.pourAbis(listOf("riscv64")))
    }

    @Test
    fun `le jeton ne part que vers son propre homeserver`() {
        val media = VarianteRelease("https://exemple.org/_matrix/client/v1/media/download/exemple.org/abc", "a".repeat(64), 1)
        assertEquals("Bearer j", media.autorisation("https://exemple.org/", "j"))
        assertNull(media.autorisation("https://ailleurs.example", "j"))
        assertNull(VarianteRelease("https://exemple.org.evil.example/_matrix/x", "a".repeat(64), 1).autorisation("https://exemple.org", "j"))
        assertNull(VarianteRelease("https://velvet.exemple.org/dl/a.apk", "a".repeat(64), 1).autorisation("https://exemple.org", "j"))
    }

    @Test
    fun `c est le code qui compare, pas le nom`() {
        val release = Release.depuisJson(valide(code = 10))!!
        assertTrue(release.plusRecenteQue(9))
        assertFalse(release.plusRecenteQue(10))
        assertFalse(release.plusRecenteQue(11))
    }

    @Test
    fun `une version du bureau se compare en entier, pas en texte`() {
        assertEquals(1_000_000L, Release.codeDeVersion("1.0.0"))
        // 1.10.0 is newer than 1.9.0, and string order would say the opposite
        assertTrue(Release.codeDeVersion("1.10.0")!! > Release.codeDeVersion("1.9.0")!!)
        assertNull(Release.codeDeVersion("1.0"))
        assertNull(Release.codeDeVersion("1.0.x"))
        assertNull(Release.codeDeVersion("1.1000.0"))
    }
}

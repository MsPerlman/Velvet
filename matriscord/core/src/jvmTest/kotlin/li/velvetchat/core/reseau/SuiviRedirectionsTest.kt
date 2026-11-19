package li.velvetchat.core.reseau

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * the token must not follow a redirect out of its own house
 *
 * two servers play the homeserver and a third party. the homeserver redirects to the third
 * party, and the test looks at what the third party **received**: that is the only way to
 * prove the leak, because it shows neither in the calling code nor in the result
 */
class SuiviRedirectionsTest {

    private val recu = mutableMapOf<String, String?>()
    private var contacte = false
    private lateinit var maison: HttpServer
    private lateinit var tiers: HttpServer

    private fun demarrer(nom: String, routes: HttpServer.() -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            routes()
            executor = null
            start()
        }

    private fun HttpServer.base() = "http://127.0.0.1:${address.port}"

    @AfterTest
    fun arreter() {
        if (::maison.isInitialized) maison.stop(0)
        if (::tiers.isInitialized) tiers.stop(0)
    }

    private fun monter(redirige: Boolean) {
        tiers = demarrer("tiers") {
            createContext("/") { ech ->
                recu["tiers"] = ech.requestHeaders.getFirst("Authorization")
                contacte = true
                val corps = "contenu du tiers".toByteArray()
                ech.sendResponseHeaders(200, corps.size.toLong())
                ech.responseBody.use { it.write(corps) }
            }
        }
        maison = demarrer("maison") {
            createContext("/_matrix/") { ech ->
                recu["maison"] = ech.requestHeaders.getFirst("Authorization")
                if (redirige) {
                    ech.responseHeaders.add("Location", tiers.base() + "/ailleurs")
                    ech.sendResponseHeaders(302, -1)
                } else {
                    val corps = "contenu de la maison".toByteArray()
                    ech.sendResponseHeaders(200, corps.size.toLong())
                    ech.responseBody.use { it.write(corps) }
                }
                ech.close()
            }
        }
    }

    private fun autorisationPour(base: String): (String) -> String? = { adresse ->
        if (adresse.startsWith("$base/_matrix/")) "Bearer SECRET" else null
    }

    @Test
    fun `sans redirection le jeton part bien au homeserver`() {
        monter(redirige = false)
        val cnx = SuiviRedirections.ouvrir(
            maison.base() + "/_matrix/fichier",
            autorisationPour(maison.base()),
        )
        assertEquals(200, cnx.responseCode)
        assertEquals("contenu de la maison", cnx.inputStream.bufferedReader().readText())
        assertEquals("Bearer SECRET", recu["maison"])
    }

    @Test
    fun `le jeton ne suit pas une redirection vers un tiers`() {
        monter(redirige = true)
        val cnx = SuiviRedirections.ouvrir(
            maison.base() + "/_matrix/fichier",
            autorisationPour(maison.base()),
        )
        assertEquals(200, cnx.responseCode, "la redirection est bien suivie")
        assertEquals("contenu du tiers", cnx.inputStream.bufferedReader().readText())
        assertEquals("Bearer SECRET", recu["maison"], "le homeserver l'a eu")
        assertTrue(contacte, "le tiers doit avoir ete contacte, sinon le test ne prouve rien")
        assertNull(recu["tiers"], "le tiers ne doit RIEN avoir reçu")
    }

    @Test
    fun `une boucle de redirections s arrete au lieu de tourner`() {
        lateinit var boucle: HttpServer
        boucle = demarrer("boucle") {
            createContext("/") { ech ->
                ech.responseHeaders.add("Location", "/encore")
                ech.sendResponseHeaders(302, -1)
                ech.close()
            }
        }
        try {
            val leve = runCatching {
                SuiviRedirections.ouvrir(boucle.base() + "/depart", autorisationPour = { null })
            }.exceptionOrNull()
            assertTrue(leve is java.io.IOException, "devrait s'arreter, pas tourner")
        } finally {
            boucle.stop(0)
        }
    }
}

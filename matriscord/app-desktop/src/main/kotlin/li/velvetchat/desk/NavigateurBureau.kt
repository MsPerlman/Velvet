package li.velvetchat.desk

import li.velvetchat.core.tr

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.URI

/**
 * the way back from the browser after a login on MAS, on the desktop
 *
 * a native app's redirect is a loopback address (rfc 8252): a one shot http server on 127.0.0.1,
 * on a port the system picks, which receives `/callback?code=…` and stops. MAS accepts any port
 * on a loopback redirect
 */
object NavigateurBureau {

    /** runs [bloc] with a callback address and an `ouvrir` that opens the system browser */
    suspend fun <T> allerRetour(bloc: suspend (retour: String, ouvrir: suspend (String) -> String) -> T): T {
        val recu = CompletableDeferred<String>()
        val serveur = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = serveur.address.port
        serveur.createContext("/callback") { echange ->
            recu.complete("http://127.0.0.1:$port${echange.requestURI}")
            val page = "<!doctype html><meta charset=utf-8><title>Velvet</title>" +
                "<body style=\"background:#000;color:#ccc;font:16px sans-serif;text-align:center;padding-top:20vh\">" +
                tr("Connexion terminée, vous pouvez revenir à Velvet.</body>", "Signed in, you can go back to Velvet.</body>")
            val octets = page.toByteArray()
            echange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            echange.sendResponseHeaders(200, octets.size.toLong())
            echange.responseBody.use { it.write(octets) }
        }
        serveur.start()
        try {
            return bloc("http://127.0.0.1:$port/callback") { url ->
                java.awt.Desktop.getDesktop().browse(URI(url))
                // nobody tells us the tab was closed: past ten minutes the login gives up
                withTimeout(10 * 60_000L) { recu.await() }
            }
        } finally {
            serveur.stop(0)
        }
    }
}

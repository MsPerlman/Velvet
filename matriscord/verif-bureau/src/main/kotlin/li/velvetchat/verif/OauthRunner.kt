package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import li.velvetchat.core.model.Session
import li.velvetchat.core.rust.RustMatrixEngine
import java.io.File
import java.net.CookieManager
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * the whole oauth path against a homeserver behind MAS, with no browser: a small robot fills
 * MAS's login and consent pages the way a person would
 *
 * args: homeserver, username, password. the account must be allowed to ask for admin rights,
 * and the server's MAS should run with a short `access_token_ttl` (60 s) so the refresh is
 * actually exercised
 */
object OauthRunner {

    private const val RETOUR = "http://127.0.0.1:47123/callback"

    /** MAS's forms: hidden fields plus the credentials, posted back until it redirects to us */
    private fun robot(identifiant: String, motDePasse: String, jeton: String? = null): suspend (String) -> String = { depart ->
        val http = HttpClient.newBuilder()
            .cookieHandler(CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
        var url = depart
        var resultat: String? = null
        var etapes = 0
        var requete = HttpRequest.newBuilder(URI.create(url)).GET().build()
        while (resultat == null) {
            check(++etapes < 20) { "MAS tourne en rond, dernière page : $url" }
            val r = http.send(requete, HttpResponse.BodyHandlers.ofString())
            val suite = r.headers().firstValue("location").orElse(null)
            if (r.statusCode() in 300..399 && suite != null) {
                url = URI.create(url).resolve(suite).toString()
                if (url.startsWith(RETOUR)) { resultat = url; continue }
                requete = HttpRequest.newBuilder(URI.create(url)).GET().build()
                continue
            }
            check(r.statusCode() == 200) { "MAS ${r.statusCode()} sur $url : ${r.body().take(300)}" }
            val page = r.body()
            val forme = Regex("<form[^>]*method=\"(?:post|POST)\"[^>]*>(.*?)</form>", RegexOption.DOT_MATCHES_ALL)
                .find(page)
            if (forme == null) {
                // the sign up form is drawn by javascript, and posts back to the same address
                val csrf = Regex("data-csrf-token=\"([^\"]+)\"").find(page)?.groupValues?.get(1)
                    ?: error("aucun formulaire sur $url : ${page.take(400)}")
                val corps = listOf("csrf" to csrf, "username" to identifiant, "email" to "",
                    "password" to motDePasse, "password_confirm" to motDePasse)
                    .joinToString("&") { it.first + "=" + URLEncoder.encode(it.second, "UTF-8") }
                println("    robot : ${URI.create(url).path} → POST (formulaire javascript)")
                requete = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(corps)).build()
                continue
            }
            val action = Regex("action=\"([^\"]*)\"").find(forme.value.substringBefore('>'))?.groupValues?.get(1)
            val champs = linkedMapOf<String, String>()
            Regex("<input([^>]*)>").findAll(forme.value).forEach { m ->
                val nom = Regex("name=\"([^\"]+)\"").find(m.groupValues[1])?.groupValues?.get(1) ?: return@forEach
                champs[nom] = Regex("value=\"([^\"]*)\"").find(m.groupValues[1])?.groupValues?.get(1).orEmpty()
            }
            if ("username" in champs) champs["username"] = identifiant
            if ("password" in champs) champs["password"] = motDePasse
            if ("password_confirm" in champs) champs["password_confirm"] = motDePasse
            if ("token" in champs && jeton != null) champs["token"] = jeton
            if ("display_name" in champs) champs["display_name"] = identifiant
            Regex("<button([^>]*)>").findAll(forme.value)
                .mapNotNull { b ->
                    val nom = Regex("name=\"([^\"]+)\"").find(b.groupValues[1])?.groupValues?.get(1)
                    val valeur = Regex("value=\"([^\"]*)\"").find(b.groupValues[1])?.groupValues?.get(1)
                    if (nom != null && valeur != null) nom to valeur else null
                }
                .firstOrNull { it.second !in setOf("cancel", "deny", "logout") }
                ?.let { champs[it.first] = it.second }
            val corps = champs.entries.joinToString("&") {
                URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
            }
            val cible = if (action.isNullOrBlank()) url else URI.create(url).resolve(action.replace("&amp;", "&")).toString()
            println("    robot : ${URI.create(url).path} → POST ${champs.keys.filter { it != "csrf" }}")
            requete = HttpRequest.newBuilder(URI.create(cible))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corps)).build()
            url = cible
        }
        resultat!!
    }

    private var echecs = 0
    private fun verifier(quoi: String, ok: Boolean, detail: Any? = null) {
        if (!ok) echecs++
        println("${if (ok) "OK  " else "ÉCHEC"} $quoi${detail?.let { " — $it" } ?: ""}")
    }

    private fun moteur(nom: String): RustMatrixEngine {
        val dir = File("build/oauth-$nom-${System.currentTimeMillis()}").apply { mkdirs() }
        return RustMatrixEngine(
            dataPath = dir.absolutePath,
            cachePath = File(dir, "cache").apply { mkdirs() }.absolutePath,
            logToStdout = false,
        ).also { it.initPlatform() }
    }

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val hs = args.getOrNull(0) ?: error("homeserver manquant")
        val identifiant = args.getOrNull(1) ?: error("identifiant manquant")
        val motDePasse = args.getOrNull(2) ?: error("mot de passe manquant")
        val ouvrir = robot(identifiant, motDePasse)

        val m = moteur("a")
        verifier("le serveur délègue au navigateur", m.connexionParNavigateur(hs))
        verifier("un serveur sans MAS ne le prétend pas", !m.connexionParNavigateur("https://exemple.org"))

        var sauvee: Session? = null
        var renouvellements = 0
        m.surSessionRenouvelee { sauvee = it; renouvellements++ }

        val s = m.loginNavigateur(hs, RETOUR, creation = false, ouvrir = ouvrir)
        sauvee = s
        verifier("session oauth ouverte", s.oauthData != null && s.refreshToken != null, s.userId)
        verifier("pas d'admin sans le demander", !m.estAdministrateurServeur())
        verifier("MAS dit que ce compte peut demander l'admin", m.peutDemanderAdmin())

        val s2 = m.activerAdministration(RETOUR, ouvrir)
        sauvee = s2
        verifier("même appareil après élévation", s2.deviceId == s.deviceId, s2.deviceId)
        verifier("jeton remplacé", s2.accessToken != s.accessToken)
        verifier("administrateur après élévation", m.estAdministrateurServeur())

        m.startSync()
        val avant = m.jetonsInvitation().size
        val code = m.creerInvitation("!espace:banc", usages = 2, expirationJours = 3)
        val apres = m.jetonsInvitation()
        verifier("une invitation créée, une seule", apres.size == avant + 1, "$avant → ${apres.size}")
        val jeton = li.velvetchat.core.model.CodeInvitation.decoder(code)!!.jeton
        val lu = apres.firstOrNull { it.jeton == jeton }
        verifier("l'invitation se relit", lu?.usagesRestants == 2 && lu.expiration != null, lu)
        m.revoquerJeton(jeton)
        verifier("révoquée", m.jetonsInvitation().none { it.jeton == jeton })

        val comptes = m.comptesServeur()
        verifier("comptes lus", comptes.isNotEmpty(), comptes.map { it.userId })
        comptes.firstOrNull { it.userId.startsWith("@bancmas:") }?.let { c ->
            m.setCompteDesactive(c.userId, true)
            verifier("compte verrouillé", m.comptesServeur().first { it.userId == c.userId }.desactive)
            m.setCompteDesactive(c.userId, false)
            verifier("compte déverrouillé", !m.comptesServeur().first { it.userId == c.userId }.desactive)
        }

        println("attente de l'expiration du jeton (75 s)…")
        delay(75_000)
        verifier("admin encore joignable après expiration", m.estAdministrateurServeur())
        verifier("session renouvelée transmise", renouvellements > 0 && sauvee!!.accessToken != s2.accessToken, "$renouvellements")

        // what the next launch does: a fresh engine on the same store with the saved session
        val sessionDisque = sauvee!!
        m.stopSync()
        val dossier = File("build").listFiles()!!.filter { it.name.startsWith("oauth-a-") }.maxBy { it.lastModified() }
        val m2 = RustMatrixEngine(dossier.absolutePath, File(dossier, "cache").absolutePath, logToStdout = false)
        runCatching { m2.restore(sessionDisque) }.onFailure { verifier("restauration", false, it.message) }
        verifier("session restaurée encore admin", m2.estAdministrateurServeur())

        // sign up through MAS with an invite code, the way a newcomer does
        val nouveau = "banc${System.currentTimeMillis() % 100000}"
        val invitation = m2.creerInvitation("!espace:banc", usages = 1, expirationJours = 1)
        val jetonInvit = li.velvetchat.core.model.CodeInvitation.decoder(invitation)!!.jeton
        val m3 = moteur("inscription")
        val s3 = m3.loginNavigateur(hs, RETOUR, creation = true, ouvrir = robot(nouveau, "Essai-Banc-${nouveau}9", jetonInvit))
        verifier("compte créé avec le code", s3.userId.startsWith("@$nouveau:"), s3.userId)
        verifier("le nouveau n'est pas admin", !m3.peutDemanderAdmin())
        verifier("code consommé", m2.jetonsInvitation().none { it.jeton == jetonInvit })
        val refus = runCatching { moteur("refus").loginNavigateur(hs, RETOUR, creation = true, ouvrir = robot("${nouveau}b", "Essai-Banc-${nouveau}9", "faux-jeton")) }
        verifier("faux code refusé", refus.isFailure, refus.exceptionOrNull()?.message?.take(120))
        println("compte de test créé : ${s3.userId} (à supprimer)")
        m3.deconnecter()

        m2.deconnecter()
        println(if (echecs == 0) "\nTOUT EST VERT" else "\n$echecs ÉCHEC(S)")
        if (echecs > 0) kotlin.system.exitProcess(1)
    }
}

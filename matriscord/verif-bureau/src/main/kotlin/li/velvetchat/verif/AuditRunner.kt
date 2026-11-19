package li.velvetchat.verif

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.model.Presence
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val creds = AuditMarqueur::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val checks = Checks()
    println("=".repeat(72))
    println("  Audit — ce qui a été livré, relu depuis le serveur")
    println("=".repeat(72))
    println()

    val dir = File("build/audit")
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dir.apply { mkdirs() }.absolutePath,
        cachePath = File("build/audit-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()

    val store = WindowsSessionStore(dir)
    val stockee = store.load()
    if (stockee != null) engine.restore(stockee) else {
        store.save(
            engine.login(
                homeserverUrl = creds.getProperty("homeserver"),
                username = creds.getProperty("username") ?: error("username absent"),
                password = creds.getProperty("password") ?: error("password absent"),
            )
        )
    }
    engine.startSync()

    val session = engine.currentSession()
    val moi = session.userId
    println("Connecté : $moi")

    val serveur = engine.servers().firstOrNull()
        ?: run { checks.fail("serveur", "aucun serveur joignable"); checks.report(); exitProcess(1) }
    val salon = serveur.channels.firstOrNull { it.name == "general" }
        ?: serveur.channels.firstOrNull()
        ?: run { checks.fail("salon", "aucun salon"); checks.report(); exitProcess(1) }
    println("Serveur : ${serveur.name} (${serveur.id})")
    println("Salon   : ${salon.name} (${salon.id})")
    println()

    println("① Présence")
    val statutInitial = engine.presence(moi)
    checks.note("présence initiale", "${statutInitial.presence} « ${statutInitial.message} »")

    engine.setPresence(Presence.ABSENT, "audit en cours")
    val relu = engine.presence(moi)
    if (relu.presence == Presence.ABSENT) {
        checks.pass("présence écrite", "le serveur rend bien `unavailable`")
    } else {
        checks.inconclusive(
            "présence écrite",
            "relu ${relu.presence} — présence probablement désactivée côté Synapse",
        )
    }
    if (relu.message == "audit en cours") {
        checks.pass("status_msg", "le texte libre fait l'aller-retour")
    } else {
        checks.inconclusive("status_msg", "relu « ${relu.message} »")
    }
    checks.note("rc_presence", "attente de 11 s avant remise en place — limite Synapse")
    delay(11_000)
    engine.setPresence(
        statutInitial.presence.takeIf { it != Presence.INCONNU } ?: Presence.EN_LIGNE,
        statutInitial.message,
    )
    val remis = engine.presence(moi)
    if (remis.message == statutInitial.message) {
        checks.pass("présence remise", "l'état d'origine est revenu")
    } else {
        checks.fail("présence remise", "laissé à « ${remis.message} » au lieu de « ${statutInitial.message} »")
    }
    println()

    println("② Silence des notifications")
    val silenceInitial = engine.silence()
    checks.note("silence initial", if (silenceInitial) "actif" else "inactif")

    engine.setSilence(true)
    if (engine.silence()) {
        checks.pass("silence activé", ".m.rule.master est bien `enabled: true` côté serveur")
    } else {
        checks.fail("silence activé", "la règle maîtresse n'a pas pris")
    }
    engine.setSilence(false)
    if (!engine.silence()) {
        checks.pass("silence retiré", "la règle est revenue à `enabled: false`")
    } else {
        checks.fail("silence retiré", "la règle est restée active — état laissé sale")
    }
    engine.setSilence(silenceInitial)
    println()

    println("③ Épingles")
    val marque = System.currentTimeMillis().toString().takeLast(5)
    engine.send(salon.id, "Audit $marque — message à épingler")
    val aEpingler = withTimeoutOrNull(45_000) {
        engine.messagesFlow(salon.id).first { liste ->
            liste.any {
                it.id.startsWith("$") &&
                    (it.body as? MessageBody.Text)?.text?.contains(marque) == true
            }
        }.first {
            it.id.startsWith("$") &&
                (it.body as? MessageBody.Text)?.text?.contains(marque) == true
        }
    }
    if (aEpingler == null) {
        checks.fail("épingles", "le message d'essai n'est jamais revenu par le flux")
    } else {
        engine.send(salon.id, "Audit $marque — témoin d'épingle")
        val temoin = withTimeoutOrNull(45_000) {
            engine.messagesFlow(salon.id).first { liste ->
                liste.any {
                    it.id.startsWith("$") &&
                        (it.body as? MessageBody.Text)?.text?.contains("témoin d'épingle") == true
                }
            }.first {
                it.id.startsWith("$") &&
                    (it.body as? MessageBody.Text)?.text?.contains("témoin d'épingle") == true
            }
        }
        if (temoin != null) {
            engine.setMessagePinned(salon.id, temoin.id, true)
            delay(1_500)
        } else {
            checks.inconclusive("témoin d'épingle", "jamais revenu — la préservation ne sera pas exercée")
        }

        val avant = engine.pinnedMessages(salon.id)
        engine.setMessagePinned(salon.id, aEpingler.id, true)
        delay(1_500)

        val brut = lireEtatBrut(session.homeserverUrl, session.accessToken, salon.id)
        if (brut != null && brut.contains(aEpingler.id)) {
            checks.pass(
                "épingle posée",
                "m.room.pinned_events du serveur contient l'événement — donc Element le verra",
            )
        } else {
            checks.fail("épingle posée", "l'état brut du serveur ne porte pas l'épingle : $brut")
        }

        val apres = engine.pinnedMessages(salon.id)
        if (apres.firstOrNull() == aEpingler.id) {
            checks.pass("ordre des épingles", "la plus récente arrive en tête")
        } else {
            checks.fail("ordre des épingles", "attendu ${aEpingler.id} en tête, obtenu $apres")
        }

        when {
            avant.isEmpty() -> checks.inconclusive(
                "épingles préservées",
                "aucune épingle antérieure — rien à préserver, donc rien de prouvé",
            )
            avant.all { it in apres } -> checks.pass(
                "épingles préservées",
                "${avant.size} épingle(s) antérieure(s) intacte(s) après une seconde écriture",
            )
            else -> checks.fail(
                "épingles préservées",
                "des épingles antérieures ont disparu : $avant → $apres",
            )
        }

        engine.setMessagePinned(salon.id, aEpingler.id, false)
        temoin?.let { engine.setMessagePinned(salon.id, it.id, false) }
        delay(1_000)
        val restantes = engine.pinnedMessages(salon.id)
        if (aEpingler.id !in restantes && temoin?.id !in restantes) {
            checks.pass("épingles retirées", "l'état est revenu à son contenu d'origine")
        } else {
            checks.fail("épingles retirées", "il reste $restantes — état laissé sale")
        }
        engine.delete(salon.id, aEpingler.id)
        temoin?.let { engine.delete(salon.id, it.id) }
    }
    println()

    println("④ Lien de message")
    val lien = engine.messageLink(salon.id, "\$exemple")

    val partieSalon = lien.substringAfter("/#/", "").substringBefore("/")
    val decode = java.net.URLDecoder.decode(partieSalon, "UTF-8")
    when {
        decode.startsWith("#") -> checks.pass("lien sur alias", "$lien → $decode")
        decode.startsWith("!") -> checks.note(
            "lien sur identifiant",
            "$decode — ce salon n'a pas d'alias publique, c'est le repli documenté",
        )
        else -> checks.fail("lien", "ni alias ni identifiant après /#/ : « $decode »")
    }
    println()

    println("⑤ Sondage")
    engine.createPoll(salon.id, "Audit $marque ?", listOf("Oui", "Non"), secret = false)
    val sondage = withTimeoutOrNull(45_000) {
        engine.messagesFlow(salon.id).first { liste ->
            liste.any {
                it.id.startsWith("$") &&
                    (it.body as? MessageBody.Poll)?.question?.contains(marque) == true
            }
        }.first {
            it.id.startsWith("$") &&
                (it.body as? MessageBody.Poll)?.question?.contains(marque) == true
        }
    }
    if (sondage == null) {
        checks.fail("sondage créé", "jamais revenu par le flux")
    } else {
        val corps = sondage.body as MessageBody.Poll
        if (corps.options.size == 2 && corps.options.map { it.texte } == listOf("Oui", "Non")) {
            checks.pass("sondage créé", "question et deux réponses relues du serveur")
        } else {
            checks.fail("sondage créé", "réponses relues : ${corps.options.map { it.texte }}")
        }
        if (corps.totalVotes == 0) {
            checks.pass("dépouillement initial", "aucun vote, aucune barre")
        } else {
            checks.fail("dépouillement initial", "${corps.totalVotes} vote(s) sur un sondage neuf")
        }

        engine.votePoll(salon.id, sondage.id, listOf(corps.options[0].id))
        val vote = withTimeoutOrNull(45_000) {
            engine.messagesFlow(salon.id).first { liste ->
                (liste.firstOrNull { it.id == sondage.id }?.body as? MessageBody.Poll)
                    ?.totalVotes == 1
            }.first { it.id == sondage.id }
        }
        val apresVote = vote?.body as? MessageBody.Poll
        when {
            apresVote == null -> checks.fail("vote", "le décompte n'est jamais passé à 1")
            apresVote.options[0].mien && apresVote.options[0].votes == 1 ->
                checks.pass("vote compté", "la réponse choisie porte 1 voix et se sait mienne")
            else -> checks.fail(
                "vote compté",
                "mien=${apresVote.options[0].mien} votes=${apresVote.options[0].votes}",
            )
        }

        engine.endPoll(salon.id, sondage.id)
        val clos = withTimeoutOrNull(30_000) {
            engine.messagesFlow(salon.id).first { liste ->
                (liste.firstOrNull { it.id == sondage.id }?.body as? MessageBody.Poll)?.ferme == true
            }
        }
        if (clos != null) checks.pass("sondage clos", "`ferme` remonte du serveur")
        else checks.inconclusive("sondage clos", "la clôture n'est pas revenue dans le délai")
    }
    println()

    println("⑥ Citation de réponse")
    engine.send(salon.id, "Audit $marque — message cité")
    val cible = withTimeoutOrNull(45_000) {
        engine.messagesFlow(salon.id).first { liste ->
            liste.any {
                it.id.startsWith("$") &&
                    (it.body as? MessageBody.Text)?.text?.contains("message cité") == true
            }
        }.first {
            it.id.startsWith("$") &&
                (it.body as? MessageBody.Text)?.text?.contains("message cité") == true
        }
    }
    if (cible == null) {
        checks.fail("citation", "la cible fabriquée n'est jamais revenue confirmée")
    } else {
        engine.reply(salon.id, cible.id, "Audit $marque — réponse")
        val reponse = withTimeoutOrNull(45_000) {
            engine.messagesFlow(salon.id).first { liste ->
                liste.any {
                    (it.body as? MessageBody.Text)?.text?.contains("$marque — réponse") == true
                }
            }.first { (it.body as? MessageBody.Text)?.text?.contains("$marque — réponse") == true }
        }
        when {
            reponse == null -> checks.fail("citation", "la réponse n'est jamais revenue")
            reponse.replyTo != cible.id ->
                checks.fail("citation", "replyTo=${reponse.replyTo}, attendu ${cible.id}")
            reponse.citation == null ->
                checks.fail(
                    "citation",
                    "relation présente mais extrait absent — ni le SDK ni `avecCitations` n'ont résolu la cible",
                )
            reponse.citation!!.auteurId != cible.sender ->
                checks.fail("citation", "auteur cité ${reponse.citation!!.auteurId}, attendu ${cible.sender}")
            reponse.citation!!.apercu.isBlank() ->
                checks.fail("citation", "extrait vide")
            else -> checks.pass(
                "citation",
                "« ${reponse.citation!!.apercu.take(40)} » de ${reponse.citation!!.auteur}",
            )
        }
        reponse?.let { engine.delete(salon.id, it.id) }
        engine.delete(salon.id, cible.id)
    }
    println()

    println("⑦ Catégories")
    val nomCategorie = "Audit $marque"
    val categorieId = runCatching { engine.createCategory(serveur.id, nomCategorie) }
        .onFailure { checks.fail("catégorie créée", it.message ?: "échec") }
        .getOrNull()

    if (categorieId != null) {
        val salonRange = runCatching {
            engine.createChannel(serveur.id, "audit-$marque", categorieId = categorieId)
        }.onFailure { checks.fail("salon rangé", it.message ?: "échec") }.getOrNull()

        if (salonRange != null) {
            delay(3_000)
            val relus = engine.servers().firstOrNull { it.id == serveur.id }?.channels.orEmpty()
            val trouve = relus.firstOrNull { it.id == salonRange.id }
            when {
                trouve == null -> checks.fail(
                    "salon d'une catégorie visible",
                    "le salon rangé dans un sous-space n'apparaît pas — c'est exactement le défaut corrigé le 24/08",
                )
                trouve.categorie != nomCategorie -> checks.fail(
                    "catégorie portée",
                    "categorie=${trouve.categorie}, attendu « $nomCategorie »",
                )
                else -> checks.pass(
                    "catégories",
                    "le salon rangé remonte bien, portant « ${trouve.categorie} »",
                )
            }
            val racine = relus.filter { it.categorie == null }
            if (racine.isNotEmpty()) {
                checks.pass("racine préservée", "${racine.size} salon(s) hors catégorie, toujours là")
            } else {
                checks.fail("racine préservée", "les salons de la racine ont disparu")
            }
            runCatching { engine.leaveChannel(salonRange.id) }
        }
        runCatching { engine.leaveChannel(categorieId) }
        checks.note("nettoyage", "salon et catégorie d'audit quittés")
    }
    println()

    println("⑧ Silence — l'effet, et non plus l'écriture")

    val second = File("build/audit-second")
    val autre: MatrixEngine = RustMatrixEngine(
        dataPath = second.apply { mkdirs() }.absolutePath,
        cachePath = File("build/audit-second-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    val magasinSecond = WindowsSessionStore(second)
    val sessionSeconde = runCatching {
        magasinSecond.load()?.let { autre.restore(it); it } ?: autre.login(
            homeserverUrl = creds.getProperty("homeserver"),
            username = creds.getProperty("username2") ?: error("username2 absent"),
            password = creds.getProperty("password2") ?: error("password2 absent"),
        ).also { magasinSecond.save(it) }
    }.getOrNull()

    if (sessionSeconde == null) {
        checks.inconclusive("silence — effet", "second compte indisponible")
    } else {
        autre.startSync()

        suspend fun compteur(): Int =
            engine.servers().firstOrNull { it.id == serveur.id }
                ?.channels?.firstOrNull { it.id == salon.id }
                ?.unread?.notifications ?: -1

        engine.markRead(salon.id)
        delay(3_000)
        val depart = compteur()
        if (depart != 0) {
            checks.inconclusive(
                "silence — effet",
                "le compteur ne retombe pas à zéro (=$depart) : la mesure ne voudrait rien dire",
            )
        } else {
            engine.setSilence(true)
            delay(1_000)
            autre.send(salon.id, "Audit $marque — pendant le silence")
            delay(8_000)
            val silencieux = compteur()
            if (silencieux == 0) {
                checks.pass(
                    "silence — effet",
                    "un message reçu pendant le silence n'incrémente pas le compteur",
                )
            } else {
                checks.fail(
                    "silence — effet",
                    "compteur passé à $silencieux malgré .m.rule.master actif — la règle est écrite mais sans effet",
                )
            }

            engine.setSilence(false)
            delay(1_000)
            autre.send(salon.id, "Audit $marque — après le silence")
            delay(8_000)
            val bruyant = compteur()
            if (bruyant > 0) {
                checks.pass(
                    "contre-essai",
                    "silence retiré, le compteur repart ($bruyant) — la mesure est donc sensible",
                )
            } else {
                checks.fail(
                    "contre-essai",
                    "le compteur reste à $bruyant sans silence : la mesure ne prouve rien",
                )
            }
        }

        engine.markRead(salon.id)
        engine.setSilence(silenceInitial)
        autre.stopSync()
    }
    println()

    engine.stopSync()
    checks.report()
    exitProcess(0)
}

private fun lireEtatBrut(homeserver: String, token: String, roomId: String): String? = runCatching {
    val url = java.net.URL(
        homeserver.trimEnd('/') +
            "/_matrix/client/v3/rooms/" +
            java.net.URLEncoder.encode(roomId, "UTF-8") +
            "/state/m.room.pinned_events/"
    )
    val cnx = (url.openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Authorization", "Bearer $token")
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    try {
        if (cnx.responseCode != 200) null
        else cnx.inputStream.bufferedReader().readText()
    } finally {
        cnx.disconnect()
    }
}.getOrNull()

private class AuditMarqueur

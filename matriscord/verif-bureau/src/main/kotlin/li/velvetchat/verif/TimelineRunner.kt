package li.velvetchat.verif

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val creds = TimelineRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val roomId = creds.getProperty("testRoomId")

    println("=".repeat(72))
    println("  Lots 4 & 5 — pagination, édition, réponse, réactions, suppression")
    println("=".repeat(72))
    println()

    val dir = File("build/engine-session")
    val engine: MatrixEngine = RustMatrixEngine(
        dataPath = dir.apply { mkdirs() }.absolutePath,
        cachePath = File("build/engine-session-cache").apply { mkdirs() }.absolutePath,
        logToStdout = false,
    )
    engine.initPlatform()
    engine.restore(WindowsSessionStore(dir).load() ?: error("lancer d'abord verifyEngine"))
    engine.startSync()
    println("Session ${engine.currentSession().userId} / ${engine.currentSession().deviceId}")
    println()

    val state = MutableStateFlow<List<Message>>(emptyList())
    val scope = CoroutineScope(Dispatchers.Default)
    val job = scope.launch { engine.messagesFlow(roomId).collect { state.value = it } }

    val checks = Checks()

    println("── Lot 4 : pagination ──")
    val loaded = state.await("historique initial", 30_000) { it.isNotEmpty() }
    if (loaded == null) {
        println("✗ Aucun message : le flux n'a rien émis. Scénario interrompu.")
        job.cancel(); engine.stopSync(); exitProcess(1)
    }
    val before = loaded.size
    println("   $before message(s) après la page initiale")

    val hasMore = engine.loadMore(roomId, count = 20)
    val grown = state.await("page plus ancienne", 30_000) { it.size > before }
    when {
        grown != null ->
            checks.pass("pagination", "$before → ${grown.size} messages ; reste-t-il à charger : $hasMore")
        !hasMore ->
            checks.inconclusive("pagination", "début de conversation déjà atteint ($before messages en tout)")
        else ->
            checks.fail("pagination", "le SDK annonce de l'historique restant, mais le flux n'a pas grandi")
    }
    println()

    println("── Lot 5 : actions sur message ──")
    val stamp = System.currentTimeMillis()
    val original = "Lot 5 — message de travail $stamp"
    engine.send(roomId, original)

    val sent = state.await("confirmation serveur de l'envoi", 45_000) { msgs ->
        msgs.any { it.text() == original && it.id.startsWith("$") }
    }
    if (sent == null) {
        println("✗ Le message de travail n'a jamais été confirmé. Scénario interrompu.")
        job.cancel(); engine.stopSync(); exitProcess(1)
    }
    val eventId = sent.first { it.text() == original }.id
    println("   message de travail : $eventId")

    val editedText = "Lot 5 — message MODIFIÉ $stamp"
    engine.edit(roomId, eventId, editedText)
    val edited = state.await("édition", 45_000) { msgs ->
        msgs.any { it.id == eventId && it.text() == editedText && it.isEdited }
    }
    if (edited != null) {
        checks.pass("édition", "le contenu a changé et le drapeau « modifié » est porté par l'événement")
    } else {
        val seen = state.value.firstOrNull { it.id == eventId }
        checks.fail("édition", "texte vu : « ${seen?.text()} », isEdited=${seen?.isEdited}")
    }

    val replyText = "Lot 5 — réponse $stamp"
    engine.reply(roomId, eventId, replyText)
    val replied = state.await("réponse", 45_000) { msgs ->
        msgs.any { it.text() == replyText && it.replyTo == eventId }
    }
    if (replied != null) {
        checks.pass("réponse", "le lien de citation remonte dans replyTo, sans traitement côté UI")
    } else {
        val seen = state.value.firstOrNull { it.text() == replyText }
        checks.fail(
            "réponse",
            if (seen == null) "la réponse n'est jamais apparue"
            else "réponse présente mais replyTo=${seen.replyTo} au lieu de $eventId",
        )
    }

    val emoji = "👍"
    val addedReturn = engine.toggleReaction(roomId, eventId, emoji)
    val reacted = state.await("réaction ajoutée", 45_000) { msgs ->
        msgs.any { m -> m.id == eventId && m.reactions.any { it.emoji == emoji && it.mine } }
    }
    if (reacted != null && addedReturn) {
        val r = reacted.first { it.id == eventId }.reactions.first { it.emoji == emoji }
        checks.pass("réaction (ajout)", "count=${r.count}, mine=${r.mine} — reconnue comme la sienne")
    } else {
        checks.fail(
            "réaction (ajout)",
            "retour de toggleReaction=$addedReturn ; réactions vues : " +
                "${state.value.firstOrNull { it.id == eventId }?.reactions}",
        )
    }

    val removedReturn = engine.toggleReaction(roomId, eventId, emoji)
    val unreacted = state.await("réaction retirée", 45_000) { msgs ->
        msgs.any { m -> m.id == eventId && m.reactions.none { it.emoji == emoji && it.mine } }
    }
    if (unreacted != null && !removedReturn) {
        checks.pass("réaction (retrait)", "le second appel retire au lieu d'ajouter, comme sur Discord")
    } else {
        checks.fail(
            "réaction (retrait)",
            "retour de toggleReaction=$removedReturn ; réactions vues : " +
                "${state.value.firstOrNull { it.id == eventId }?.reactions}",
        )
    }

    engine.delete(roomId, eventId, reason = "vérification du lot 5")
    val redacted = state.await("suppression", 45_000) { msgs ->
        msgs.any { it.id == eventId && it.body is MessageBody.Redacted }
    }
    if (redacted != null) {
        checks.pass("suppression", "le contenu a disparu, l'événement subsiste en Redacted (comportement du protocole)")
    } else {
        checks.fail("suppression", "corps vu : ${state.value.firstOrNull { it.id == eventId }?.body}")
    }

    println()
    job.cancel()
    engine.stopSync()
    checks.report()
}

// ---------------------------------------------------------------------------------------

private fun Message.text(): String? = (body as? MessageBody.Text)?.text

private suspend fun MutableStateFlow<List<Message>>.await(
    label: String,
    timeoutMs: Long,
    predicate: (List<Message>) -> Boolean,
): List<Message>? {
    val hit = withTimeoutOrNull(timeoutMs) { first(predicate) }
    if (hit == null) println("   … « $label » non observé dans les ${timeoutMs / 1000} s")
    return hit
}

private object TimelineRunner

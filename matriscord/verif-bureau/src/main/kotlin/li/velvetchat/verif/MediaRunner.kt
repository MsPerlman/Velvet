package li.velvetchat.verif

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.MediaKind
import li.velvetchat.core.model.Message
import li.velvetchat.core.model.MessageBody
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.random.Random
import kotlin.system.exitProcess

fun main(): Unit = runBlocking {
    val creds = MediaRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val roomId = creds.getProperty("testRoomId")

    println("=".repeat(72))
    println("  Lot 6 — médias")
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

    val work = File("build/media-work").apply { mkdirs() }
    val stamp = System.currentTimeMillis()

    val png = File(work, "matriscord-$stamp.png")
    png.writeBytes(noisyPng(width = 640, height = 360, seed = stamp))
    println("Image de test : ${png.name} — 640×360, ${png.length()} octets")

    val txt = File(work, "notes-$stamp.md")
    txt.writeText("# Vérification lot 6\n\nFichier non-image, envoyé dans la foulée.\n")
    println("Fichier de test : ${txt.name} — ${txt.length()} octets")
    println()

    val state = MutableStateFlow<List<Message>>(emptyList())
    val scope = CoroutineScope(Dispatchers.Default)
    val job = scope.launch { engine.messagesFlow(roomId).collect { state.value = it } }

    val checks = Checks()

    val caption = "Lot 6 — image de vérification $stamp"
    val uploadMs = measure { engine.sendMedia(roomId, png.absolutePath, caption) }
    println("① sendMedia rendu en $uploadMs ms pour ${png.length()} octets")

    val seen = state.awaitMedia("l'image dans le flux") { m, media ->
        media.media.filename == png.name && m.id.startsWith("$")
    }
    if (seen == null) {
        println("✗ L'image n'est jamais revenue par le flux. Scénario interrompu.")
        job.cancel(); engine.stopSync(); exitProcess(1)
    }
    val (imgMsg, img) = seen
    val ref = img.media

    if (img.kind == MediaKind.IMAGE && ref.mimeType == "image/png") {
        checks.pass("type annoncé", "kind=IMAGE, mime=${ref.mimeType} — déduit des octets, pas de l'extension")
    } else {
        checks.fail("type annoncé", "kind=${img.kind}, mime=${ref.mimeType}")
    }

    if (ref.width == 640 && ref.height == 360) {
        checks.pass("dimensions", "${ref.width}×${ref.height} — un client destinataire peut réserver la place avant de télécharger")
    } else {
        checks.fail("dimensions", "${ref.width}×${ref.height} au lieu de 640×360")
    }

    if (ref.sizeBytes == png.length()) {
        checks.pass("taille annoncée", "${ref.sizeBytes} octets")
    } else {
        checks.fail("taille annoncée", "${ref.sizeBytes} au lieu de ${png.length()}")
    }

    if (img.caption == caption) {
        checks.pass("légende", "« ${img.caption} »")
    } else {
        checks.fail("légende", "« ${img.caption} »")
    }

    val original = png.readBytes()
    val downloaded = engine.mediaBytes(ref)
    if (downloaded.contentEquals(original)) {
        checks.pass(
            "aller-retour chiffré",
            "${downloaded.size} octets identiques à l'octet près — chiffrement, upload, sync, download",
        )
    } else {
        checks.fail(
            "aller-retour chiffré",
            "${downloaded.size} octets reçus contre ${original.size} envoyés, contenus différents",
        )
    }

    val path = engine.mediaFile(ref)
    val onDisk = File(path)
    if (onDisk.isFile && onDisk.readBytes().contentEquals(original)) {
        checks.pass("écriture sur disque", "ouvrable par une autre application : $path")
    } else {
        checks.fail("écriture sur disque", "chemin $path, ${onDisk.length()} octets")
    }

    val thumb = engine.mediaThumbnail(ref, 96, 96)
    when {
        thumb == null -> checks.note(
            "vignette",
            "absente — l'UI affichera le blurhash puis l'image complète",
        )
        thumb.contentEquals(original) -> checks.note(
            "vignette",
            "AUCUNE réduction : le SDK a renvoyé l'image entière (${thumb.size} octets). " +
                "En salon chiffré le serveur ne voit qu'un blob et ne peut rien redimensionner. " +
                "À NE PAS appeler pour une grille d'aperçus — le coût est celui du téléchargement complet.",
        )
        thumb.size < original.size -> checks.pass(
            "vignette",
            "${thumb.size} octets contre ${original.size} — réellement réduite par le serveur",
        )
        else -> checks.fail(
            "vignette",
            "${thumb.size} octets, ni identique à l'original ni plus petite — contenu inattendu",
        )
    }

    val big = File(work, "gros-$stamp.png")
    big.writeBytes(noisyPng(width = 2400, height = 1600, seed = stamp + 1))
    println()
    println("② Fichier volumineux : ${big.length()} octets (${big.length() / png.length()}× l'image précédente)")

    val bigMs = measure { engine.sendMedia(roomId, big.absolutePath) }
    val stateAtReturn = state.value
        .firstOrNull { (it.body as? MessageBody.Media)?.media?.filename == big.name }
        ?.sendState
    println("   sendMedia rendu en $bigMs ms, message dans l'état : ${stateAtReturn ?: "pas encore d'écho local"}")

    if (stateAtReturn is li.velvetchat.core.model.SendState.Sending || stateAtReturn == null) {
        checks.note(
            "sémantique de sendMedia",
            "l'appel rend la main AVANT la fin du téléversement (${bigMs} ms pour ${big.length()} octets, " +
                "message ${if (stateAtReturn == null) "pas encore visible" else "encore Sending"}). " +
                "Comme send() : c'est « pris en charge », pas « envoyé ». La progression se lit dans sendState.",
        )
    } else {
        checks.pass(
            "sémantique de sendMedia",
            "l'appel a bien attendu la fin du téléversement (${bigMs} ms, état $stateAtReturn)",
        )
    }

    val bigSeen = state.awaitMedia("le gros fichier confirmé par le serveur") { m, media ->
        media.media.filename == big.name && m.id.startsWith("$")
    }
    if (bigSeen != null) {
        checks.pass(
            "gros fichier acheminé",
            "${big.length()} octets confirmés — la file du SDK termine le travail après le retour de l'appel",
        )
        runCatching { engine.delete(roomId, bigSeen.first.id, "vérification lot 6") }
    } else {
        checks.fail("gros fichier acheminé", "jamais confirmé par le serveur")
    }

    engine.sendMedia(roomId, txt.absolutePath)
    val fileSeen = state.awaitMedia("le fichier dans le flux") { _, media ->
        media.media.filename == txt.name
    }
    if (fileSeen != null) {
        val (_, f) = fileSeen
        if (f.kind == MediaKind.FILE && f.media.mimeType == "text/markdown") {
            checks.pass("fichier non-image", "kind=FILE, mime=${f.media.mimeType}, légende = nom de fichier « ${f.caption} »")
        } else {
            checks.fail("fichier non-image", "kind=${f.kind}, mime=${f.media.mimeType}")
        }
    } else {
        checks.fail("fichier non-image", "jamais revenu par le flux")
    }

    runCatching { engine.delete(roomId, imgMsg.id, "vérification lot 6") }
    fileSeen?.let { runCatching { engine.delete(roomId, it.first.id, "vérification lot 6") } }

    println()
    job.cancel()
    engine.stopSync()
    checks.report()
}

// ---------------------------------------------------------------------------------------------

private suspend fun measure(block: suspend () -> Unit): Long {
    val t0 = System.currentTimeMillis()
    block()
    return System.currentTimeMillis() - t0
}

private suspend fun MutableStateFlow<List<Message>>.awaitMedia(
    label: String,
    timeoutMs: Long = 90_000,
    predicate: (Message, MessageBody.Media) -> Boolean,
): Pair<Message, MessageBody.Media>? {
    val hit = withTimeoutOrNull(timeoutMs) {
        var found: Pair<Message, MessageBody.Media>? = null
        first { msgs ->
            found = msgs.firstNotNullOfOrNull { m ->
                (m.body as? MessageBody.Media)?.takeIf { predicate(m, it) }?.let { m to it }
            }
            found != null
        }
        found
    }
    if (hit == null) println("   … « $label » non observé dans les ${timeoutMs / 1000} s")
    return hit
}

private fun noisyPng(width: Int, height: Int, seed: Long): ByteArray {
    val rng = Random(seed)
    val raw = ByteArray(height * (1 + width * 3))
    var i = 0
    repeat(height) {
        raw[i++] = 0
        repeat(width * 3) { raw[i++] = rng.nextInt(256).toByte() }
    }

    val deflater = Deflater(Deflater.BEST_SPEED)
    deflater.setInput(raw)
    deflater.finish()
    val compressed = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (!deflater.finished()) compressed.write(buf, 0, deflater.deflate(buf))
    deflater.end()

    val out = java.io.ByteArrayOutputStream()
    out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    out.writeChunk(
        "IHDR",
        be32(width) + be32(height) + byteArrayOf(8, 2, 0, 0, 0),
    )
    out.writeChunk("IDAT", compressed.toByteArray())
    out.writeChunk("IEND", ByteArray(0))
    return out.toByteArray()
}

private fun java.io.ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
    write(be32(data.size))
    val typed = type.toByteArray(Charsets.US_ASCII) + data
    write(typed)
    write(be32(CRC32().apply { update(typed) }.value.toInt()))
}

private fun be32(v: Int) = byteArrayOf(
    (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
)

private object MediaRunner

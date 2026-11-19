package li.velvetchat.verif

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import li.velvetchat.core.AccessState
import li.velvetchat.core.MatrixEngine
import li.velvetchat.core.model.ChannelKind
import li.velvetchat.core.rust.RustMatrixEngine
import li.velvetchat.core.session.WindowsSessionStore
import java.io.File
import java.util.Properties

fun main(): Unit = runBlocking {
    val creds = ContractRunner::class.java.classLoader
        .getResourceAsStream("matriscord-credentials.properties")
        ?.use { Properties().apply { load(it) } }
        ?: error("identifiants absents — renseigner local.properties")

    val roomId = creds.getProperty("testRoomId")

    println("=".repeat(72))
    println("  Contrat — membres, politique d'accès, type de salon")
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

    val checks = Checks()

    val avantSync = withTimeoutOrNull(15_000) { engine.accessFlow().first() }
    if (avantSync != null && avantSync != AccessState.SignedOut) {
        checks.pass(
            "état d'accès disponible d'emblée",
            "$avantSync — l'UI sait quoi afficher avant même la première synchronisation",
        )
    } else {
        checks.fail("état d'accès disponible d'emblée", "état vu : $avantSync")
    }

    engine.startSync()

    val apresSync = withTimeoutOrNull(60_000) { engine.accessFlow().first() }
    when (apresSync) {
        is AccessState.Ready -> checks.pass(
            "politique d'accès",
            "Ready — appareil vérifié, usage normal autorisé",
        )
        is AccessState.VerificationRequired -> checks.pass(
            "politique d'accès",
            "VerificationRequired(canUseRecoveryKey=${apresSync.canUseRecoveryKey}) — " +
                "le moteur BLOQUE au lieu de laisser l'UI improviser une règle. C'est la " +
                "décision figée : sans vérification, l'historique chiffré resterait illisible.",
        )
        else -> checks.fail("politique d'accès", "état inattendu : $apresSync")
    }

    val membres = engine.members(roomId)
    if (membres.isEmpty()) {
        checks.fail("liste des membres", "aucun membre — le moteur ne remplit toujours pas cette partie")
    } else {
        checks.pass(
            "liste des membres",
            membres.joinToString(", ") { "${it.displayName ?: it.userId} (${it.role?.name})" },
        )

        val pouvoirs = membres.map { it.role?.powerLevel ?: 0L }
        if (pouvoirs == pouvoirs.sortedDescending()) {
            checks.pass("tri des membres", "par pouvoir décroissant : ${pouvoirs.joinToString(" ≥ ")}")
        } else {
            checks.fail("tri des membres", "ordre obtenu : $pouvoirs")
        }

        val sansRole = membres.filter { it.role == null }
        if (sansRole.isEmpty()) {
            checks.pass("rôle dérivé du power level", "tous les membres en ont un")
        } else {
            checks.fail("rôle dérivé du power level", "sans rôle : ${sansRole.map { it.userId }}")
        }
    }

    val salons = engine.servers().flatMap { it.channels }
    if (salons.isEmpty()) {
        checks.inconclusive("type de salon", "aucun salon listé")
    } else {
        val parType = salons.groupingBy { it.kind }.eachCount()
        checks.pass("type de salon renseigné", "$parType")
        checks.note(
            "portée de la distinction",
            "Matrix n'a pas de « salon vocal » standard : la détection repose sur le type de " +
                "room custom et sur la présence d'un appel en cours. Heuristique de " +
                "convention, pas lecture du protocole — elle dégrade vers ${ChannelKind.TEXT}.",
        )
    }

    println()
    engine.stopSync()
    checks.report()
}

private object ContractRunner

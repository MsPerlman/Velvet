import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(key: String): String = localProps.getProperty(key).orEmpty()

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines.core)
}

application {
    mainClass.set("li.velvetchat.verif.MainKt")
}

val generateCredentials by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/credentials")
    outputs.dir(outDir)
    val hs = localProp("velvet.homeserver")
    val user = localProp("velvet.username")
    val pass = localProp("velvet.password")
    val user2 = localProp("velvet.username2")
    val pass2 = localProp("velvet.password2")
    val room = localProp("velvet.testRoomId")
    val gw = localProp("velvet.pushGateway")
    val epHost = localProp("velvet.pushEndpointHost")
    inputs.property("homeserver", hs)
    inputs.property("username", user)
    inputs.property("username2", user2)
    inputs.property("testRoomId", room)
    inputs.property("pushGateway", gw)
    inputs.property("pushEndpointHost", epHost)
    doLast {
        val f = outDir.get().file("matriscord-credentials.properties").asFile
        f.parentFile.mkdirs()
        f.writeText(
            buildString {
                appendLine("homeserver=$hs")
                appendLine("username=$user")
                appendLine("password=$pass")
                appendLine("username2=$user2")
                appendLine("password2=$pass2")
                appendLine("testRoomId=$room")
                appendLine("pushGateway=$gw")
                appendLine("pushEndpointHost=$epHost")
            }
        )
    }
}

tasks.register<JavaExec>("verifyEngine") {
    group = "matriscord"
    description = "Vérifie le moteur contre le vrai homeserver (session chiffrée + déchiffrement)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.EngineRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyRecovery") {
    group = "matriscord"
    description = "Vérifie la sauvegarde de clés et la récupération (A-bis.1)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.RecoveryRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyDevices") {
    group = "matriscord"
    description = "Vérifie la vérification d'appareil entre deux sessions (A-bis.2)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.VerificationRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifySend") {
    group = "matriscord"
    description = "Vérifie l'envoi de message et l'écho local (lot 2)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.SendRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyFlows") {
    group = "matriscord"
    description = "Vérifie la réactivité des flux (lot 3)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.FlowRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyTimeline") {
    group = "matriscord"
    description = "Vérifie pagination et actions sur message (lots 4 et 5)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.TimelineRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyMentions") {
    group = "matriscord"
    description = "Vérifie les mentions de bout en bout, depuis le second compte"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.MentionRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyMedia") {
    group = "matriscord"
    description = "Vérifie l'envoi et le téléchargement de médias chiffrés (lot 6)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.MediaRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyReadState") {
    group = "matriscord"
    description = "Vérifie les non-lus et le typage des événements d'état (lot 7)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.ReadStateRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyServer") {
    group = "matriscord"
    description = "Vérifie la création de serveur, restricted et l'auto-join (lot 8)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.ServerRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyNotifications") {
    group = "matriscord"
    description = "Vérifie les notifications déchiffrées depuis le core (lot 9)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.NotificationRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyResilience") {
    group = "matriscord"
    description = "Vérifie la reprise automatique de la synchronisation (lot 10)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.ResilienceRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyHistory") {
    group = "matriscord"
    description = "Partage des clés Megolm antérieures (MSC4268), deux chemins d'entrée"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.HistoryRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
    if (project.hasProperty("debug")) systemProperty("velvet.debug", "1")
    if (project.hasProperty("recoveryKey")) {
        systemProperty("velvet.recoveryKey", project.property("recoveryKey").toString())
    }
}

tasks.register<JavaExec>("verifyPushChain") {
    group = "matriscord"
    description = "Chaîne push de bout en bout, hors réveil Android"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.PushChainRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("recoveryKey") {
    group = "matriscord"
    description = "État de la récupération ; -Preset pour (re)générer une clé"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.RecoveryKeyRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
    if (project.hasProperty("reset")) systemProperty("velvet.resetRecovery", "1")
}

tasks.register<JavaExec>("verifyResponder") {
    group = "matriscord"
    description = "Répond à une demande de vérification ; -Papprouver / -Prefuser"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.ResponderRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
    if (project.hasProperty("approuver")) systemProperty("velvet.approuver", "1")
    if (project.hasProperty("refuser")) systemProperty("velvet.refuser", "1")
}

tasks.register<JavaExec>("verifyContract") {
    group = "matriscord"
    description = "Membres, politique d'accès et type de salon"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.ContractRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("requestVerification") {
    group = "matriscord"
    description = "Demande une vérification, à accepter depuis le téléphone"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.RequestRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyDirects") {
    group = "matriscord"
    description = "Ouvre une conversation privée et la relit depuis m.direct"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.DirectRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("inviter") {
    group = "matriscord"
    description = "Invite quelqu'un dans les espaces du compte de publication"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.InviterRunner")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
    args((project.findProperty("qui") ?: "").toString())
}

tasks.register<JavaExec>("verifierOrdre") {
    group = "matriscord"
    description = "L'ordre des salons ecrit dans m.space.child.order est-il relu"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.OrdreRunner")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
    args(
        (project.findProperty("identifiant") ?: "").toString(),
        (project.findProperty("motDePasse") ?: "").toString(),
    )
}

tasks.register<JavaExec>("verifierAcces") {
    group = "matriscord"
    description = "Ce que VelvetDesk voit juste apres une connexion (AccessState)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.AccesRunner")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
    args(
        (project.findProperty("identifiant") ?: "").toString(),
        (project.findProperty("motDePasse") ?: "").toString(),
    )
}

tasks.register<JavaExec>("publierRelease") {
    group = "matriscord"
    description = "Publie une version de Velvet dans l'etat du serveur (canal de mise a jour)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.PublierReleaseKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
    args(
        (project.findProperty("versionCode") ?: "").toString(),
        (project.findProperty("versionName") ?: "").toString(),
        (project.findProperty("baseUrl") ?: "matrix").toString(),
        (project.findProperty("notes") ?: "").toString(),
        (project.findProperty("bureau") ?: "false").toString(),
    )
}

tasks.register<JavaExec>("sondeServeur") {
    group = "matriscord"
    description = "Mesure ce que le homeserver declare et accepte, et l'ecrit dans serveur/CAPACITES.md"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.SondeServeurKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("audit") {
    group = "matriscord"
    description = "Relit depuis le serveur tout ce qui a ete livre : presence, silence, epingles, sondages, citations, categories"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.AuditRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyRoles") {
    group = "matriscord"
    description = "Écrit et relit les paliers de rôle, puis promeut et rétrograde"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.RoleRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifyEmotes") {
    group = "matriscord"
    description = "Pose les emojis de NESTSUITE/emoji et les relit"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.EmoteRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("sendImage") {
    group = "matriscord"
    description = "Envoie une image de test dans le salon, sans la supprimer"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.SendImageRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("sendTestMessage") {
    group = "matriscord"
    description = "Envoie un message chiffré dans le salon de test (spike S0.5)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.SenderKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

sourceSets.main.get().output.dir(generateCredentials.map { it.outputs.files.singleFile })

tasks.named<JavaExec>("run") {
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
    standardInput = System.`in`
}

tasks.register<JavaExec>("sondeCreation") {
    group = "matriscord"
    description = "Chronomètre createServer / createCategory / createChannel"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.CreationRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("sondeSuppression") {
    group = "matriscord"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.SuppressionRunnerKt")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
}

tasks.register<JavaExec>("verifierOauth") {
    group = "matriscord"
    description = "Connexion par MAS sans navigateur : login, élévation admin, invitations, rafraîchissement"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("li.velvetchat.verif.OauthRunner")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
    args(
        (project.findProperty("serveur") ?: "").toString(),
        (project.findProperty("identifiant") ?: "").toString(),
        (project.findProperty("motDePasse") ?: "").toString(),
    )
}

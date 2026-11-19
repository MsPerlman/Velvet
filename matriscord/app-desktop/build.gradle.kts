import java.net.URI
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
    sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("generated/prive"))
}

val privProps = Properties().apply {
    listOf("prive.properties", "prive.properties.exemple")
        .map { rootProject.file(it) }
        .firstOrNull { it.exists() }
        ?.inputStream()?.use { load(it) }
    // `-Pvelvet.public=true`: the build offered for download keeps only the code signing keys
    if (providers.gradleProperty("velvet.public").orNull == "true") {
        keys.removeIf { !(it as String).startsWith("velvet.signature.") }
    }
}
val priveCatalogue = privProps.getProperty("velvet.catalogueCosmetiques").orEmpty()
val priveIntendant = privProps.getProperty("velvet.intendant").orEmpty()
val priveCanalMaj = privProps.getProperty("velvet.canalMiseAJour").orEmpty()
val priveServeur = privProps.getProperty("velvet.serveurParDefaut").orEmpty()
val priveDictee = privProps.getProperty("velvet.dictee").orEmpty()
val priveJetonVocal = privProps.getProperty("velvet.jetonVocal").orEmpty()
val priveInvitation = privProps.getProperty("velvet.baseInvitation").orEmpty()
val priveEditeur = privProps.getProperty("velvet.editeur").orEmpty().ifBlank { "Velvet" }

val SAUT: String = System.lineSeparator()

val engendrerPrive by tasks.registering {
    inputs.property("catalogue", priveCatalogue)
    inputs.property("intendant", priveIntendant)
    inputs.property("canal", priveCanalMaj)
    inputs.property("serveur", priveServeur)
    inputs.property("dictee", priveDictee)
    inputs.property("jetonVocal", priveJetonVocal)
    inputs.property("invitation", priveInvitation)
    val sortie = layout.buildDirectory.dir("generated/prive")
    outputs.dir(sortie)
    val catalogue = priveCatalogue
    val intendant = priveIntendant
    val canal = priveCanalMaj
    val serveur = priveServeur
    val dictee = priveDictee
    val jetonVocal = priveJetonVocal
    val invitation = priveInvitation
    val saut = SAUT
    doLast {
        sortie.get().asFile.deleteRecursively()
        val dossier = sortie.get().asFile.resolve("li/velvetchat/desk")
        dossier.mkdirs()
        dossier.resolve("Prive.kt").writeText(
            listOf(
                "package li.velvetchat.desk",
                "",
                "/** Engendré depuis `prive.properties` — ne pas modifier à la main. */",
                "internal object Prive {",
                "    const val CATALOGUE_COSMETIQUES = \"$catalogue\"",
                "    const val INTENDANT = \"$intendant\"",
                "    const val CANAL_MAJ = \"$canal\"",
                "    const val SERVEUR_PAR_DEFAUT = \"$serveur\"",
                "    const val DICTEE = \"$dictee\"",
                "    const val JETON_VOCAL = \"$jetonVocal\"",
                "    const val BASE_INVITATION = \"$invitation\"",
                "}",
                "",
            ).joinToString(separator = saut),
        )
    }
}

tasks.named("compileKotlin") { dependsOn(engendrerPrive) }

tasks.register<JavaExec>("rendreEcranAppel") {
    group = "velvet"
    description = "Dessine l'ecran d'appel dans une image, sans fenetre ni session"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("li.velvetchat.desk.RenduHorsFenetre")
    systemProperty("jna.library.path", rootProject.file("core/native").absolutePath)
    args((project.findProperty("sortie") ?: "build/ecran-appel.png").toString())
}

dependencies {
    implementation(project(":ui-commun"))
    implementation(project(":core"))
    implementation(libs.jna)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.concentus)
    implementation(files("libs/livekit-proto.jar"))
    implementation("com.google.protobuf:protobuf-javalite:4.31.1")
    // `-Pvelvet.linux=true` swaps in linux's skia, for `archiveLinux` built from any machine
    if (providers.gradleProperty("velvet.linux").orNull == "true") implementation(compose.desktop.linux_x64)
    else implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    implementation("io.github.vinceglb:filekit-dialogs:0.10.0")

    // The module had no test source set. The Ogg muxer is the reason it needs one: a container
    // written by hand is either exactly right or silently unplayable.
    testImplementation(kotlin("test"))
}

tasks.processResources {
    from(rootProject.file("design/themes")) {
        into("themes")
        exclude("README.md")
    }
}

compose.desktop {
    application {
        mainClass = "li.velvetchat.desk.MainKt"
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }
            .get().metadata.installationPath.asFile.absolutePath

        rootProject.file("local.properties").takeIf { it.exists() }?.readLines()
            ?.firstOrNull { it.trim().startsWith("velvet.klipyKey=") }
            ?.substringAfter('=')?.trim()?.takeIf { it.isNotBlank() }
            ?.let { jvmArgs += "-Dvelvet.klipy=$it" }

        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "VelvetDesk"
            packageVersion = providers.gradleProperty("velvet.version").get()
            modules("java.instrument", "java.net.http", "jdk.security.auth", "jdk.unsupported", "jdk.crypto.ec", "java.naming", "jdk.httpserver")
            description = "Velvet, sur le bureau"
            vendor = priveEditeur

            appResourcesRootDir.set(layout.projectDirectory.dir("ressources-natives"))

            windows {
                menu = true
                shortcut = true
                upgradeUuid = "6e0b6f4a-3a2e-4b6f-9a4a-2f3f1d9a7c11"
                perUserInstall = true
                menuGroup = "Velvet"
                iconFile.set(project.file("icone/velvet.ico"))
            }
        }
    }
}

val copierNatif by tasks.registering(Copy::class) {
    from(rootProject.file("core/native")) { include("*.dll") }
    into(layout.projectDirectory.dir("ressources-natives/windows-x64"))
}

val temoinGstreamer = rootProject.file("core/gstreamer/bin/gst-launch-1.0.exe")

val copierGstreamer by tasks.registering(Copy::class) {
    from(rootProject.file("core/gstreamer"))
    into(layout.projectDirectory.dir("ressources-natives/windows-x64/gstreamer"))
    val present = temoinGstreamer.isFile
    doFirst {
        if (!present) {
            logger.warn(
                "GStreamer absent de core/gstreamer : cette construction n'aura ni partage de " +
                    "fenêtre ni son de partage. `pwsh tools/gstreamer-embarque.ps1` le prépare.",
            )
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(copierGstreamer) }

tasks.matching { it.name.startsWith("package") }.configureEach {
    val present = temoinGstreamer.isFile
    doFirst {
        if (!present) {
            error(
                "GStreamer manque : lancez `pwsh tools/gstreamer-embarque.ps1` avant d'empaqueter. " +
                    "Sans lui, l'installeur livre une application sans partage de fenêtre ni son.",
            )
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(copierNatif) }

tasks.matching { it.name == "run" }.configureEach {
    (this as JavaExec).systemProperty(
        "jna.library.path",
        rootProject.file("core/native").absolutePath,
    )
    rootProject.file("local.properties").takeIf { it.exists() }?.readLines()
        ?.firstOrNull { it.trim().startsWith("velvet.klipyKey=") }
        ?.substringAfter('=')?.trim()?.takeIf { it.isNotBlank() }
        ?.let { (this as JavaExec).systemProperty("velvet.klipy", it) }
    project.findProperty("donnees")?.let { (this as JavaExec).systemProperty("velvet.donnees", it.toString()) }
}

val bundleInstalleur by tasks.registering {
    group = "compose desktop"
    description = "Enveloppe le MSI dans l'assistant d'installation de Velvet, en français et en anglais"
    dependsOn("packageMsi")

    val source = layout.projectDirectory.dir("installeur")
    val dossierMsi = layout.buildDirectory.dir("compose/binaries/main/msi")
    val sortie = layout.buildDirectory.dir("compose/binaries/main/bundle")
    val wix = rootProject.layout.buildDirectory.dir("wix311").get().asFile
    val icone = layout.projectDirectory.file("icone/velvet.ico").asFile
    val version = providers.gradleProperty("velvet.version").get()

    val certificat = providers.gradleProperty("velvet.signature.pfx").orNull
        ?: privProps.getProperty("velvet.signature.pfx").orEmpty().takeIf { it.isNotBlank() }
    val motDePasseCert = privProps.getProperty("velvet.signature.motDePasse").orEmpty()
    val horodateur = privProps.getProperty("velvet.signature.horodateur").orEmpty()
        .ifBlank { "http://timestamp.digicert.com" }

    inputs.dir(source)
    inputs.dir(dossierMsi)
    outputs.dir(sortie)

    doLast {
        val racine = sortie.get().asFile
        val msi = dossierMsi.get().asFile.listFiles { f: File -> f.extension == "msi" }?.firstOrNull()
            ?: error("Aucun MSI dans ${dossierMsi.get().asFile} : `packageMsi` a-t-il tourné ?")
        val candle = File(wix, "candle.exe")
        val light = File(wix, "light.exe")
        val bal = File(wix, "WixBalExtension.dll")
        if (!candle.isFile) {
            error(
                "WiX absent de $wix. Il arrive avec `packageMsi` : lancez-le une fois d'abord, " +
                    "le plugin Compose le télécharge.",
            )
        }

        fun lancer(dans: File, vararg args: String) {
            val p = ProcessBuilder(*args).directory(dans).redirectErrorStream(true).start()
            val trace = p.inputStream.bufferedReader().readText()
            if (p.waitFor() != 0) error("${args.first()} a échoué :" + System.lineSeparator() + trace)
        }

        listOf("fr" to "VelvetSetup.exe", "en" to "VelvetSetup-en.exe").forEach { (langue, nom) ->
            val out = File(racine, langue)
            out.deleteRecursively()
            out.mkdirs()
            source.asFile.listFiles()
                ?.filter { it.isFile }
                ?.forEach { it.copyTo(File(out, it.name), overwrite = true) }
            File(source.asFile, langue).listFiles()
                ?.forEach { it.copyTo(File(out, it.name), overwrite = true) }
            icone.copyTo(File(out, icone.name), overwrite = true)
            msi.copyTo(File(out, msi.name), overwrite = true)

            val loc = if (langue == "fr") "velvet.wxl" else "velvet-en.wxl"
            lancer(
                out, candle.absolutePath, "-nologo", "-ext", bal.absolutePath,
                "-dVersion=$version.0", "-dMsi=${msi.name}", "-dLoc=$loc",
                "-out", "bundle.wixobj", "bundle.wxs",
            )
            lancer(
                out, light.absolutePath, "-nologo", "-ext", bal.absolutePath,
                "-out", nom, "bundle.wixobj",
            )
            File(out, nom).copyTo(File(racine, nom), overwrite = true)
            logger.lifecycle("Assistant prêt : ${File(racine, nom)}")
        }

        if (certificat.isNullOrBlank()) {
            logger.lifecycle(
                "Pas de signature de code : `velvet.signature.pfx` absent de prive.properties. " +
                    "SmartScreen avertira. Voir le commentaire au-dessus pour ce qu'il faut.",
            )
            return@doLast
        }
        val pfx = File(certificat)
        if (!pfx.isFile) error("Certificat introuvable : $certificat")

        val signtool = sequenceOf(
            System.getenv("SIGNTOOL"),
            "C:/Program Files (x86)/Windows Kits/10/bin",
        ).filterNotNull().mapNotNull { base ->
            val f = File(base)
            when {
                f.isFile -> f
                f.isDirectory -> f.walkTopDown().maxDepth(3)
                    .filter { it.name == "signtool.exe" && it.parentFile.name == "x64" }
                    .maxByOrNull { it.absolutePath }
                else -> null
            }
        }.firstOrNull() ?: error(
            "signtool.exe introuvable. Il vient du Windows SDK ; posez son chemin dans la " +
                "variable d'environnement SIGNTOOL.",
        )

        val aSigner = listOf(msi) +
            listOf("VelvetSetup.exe", "VelvetSetup-en.exe").map { File(racine, it) }
        aSigner.filter { it.isFile }.forEach { fichier ->
            lancer(
                racine, signtool.absolutePath, "sign",
                "/f", pfx.absolutePath, "/p", motDePasseCert,
                "/fd", "sha256", "/td", "sha256", "/tr", horodateur,
                "/d", "Velvet", fichier.absolutePath,
            )
            logger.lifecycle("Signé : ${fichier.name}")
        }
    }
}

// ---------------------------------------------------------------------------- linux
//
// a plain archive rather than a .deb: jpackage only packages for the system it runs on, and this
// one is built on windows. so a linux jre, the jars, the two native libraries and a script.
// run with -Pvelvet.linux=true, which swaps compose's skia for linux's
//
//     ./gradlew :app-desktop:archiveLinux -Pvelvet.linux=true -Pvelvet.public=true
//
// natives come from core/native/linux-x64 (libmatrix_sdk_ffi.so, liblivekit_ffi.so), the jre is
// downloaded once into build/jre-linux

val jreLinux = layout.buildDirectory.dir("jre-linux")

val telechargerJreLinux by tasks.registering {
    val dossier = jreLinux.get().asFile
    val archive = layout.buildDirectory.file("jre-linux.tar.gz").get().asFile
    outputs.dir(dossier)
    onlyIf { !dossier.resolve("bin/java").exists() }
    doLast {
        URI("https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jre/hotspot/normal/eclipse")
            .toURL().openStream().use { entree -> archive.outputStream().use { entree.copyTo(it) } }
        dossier.deleteRecursively(); dossier.mkdirs()
        // tar is on windows 10 and up, and it keeps the executable bits gradle's copy would lose
        val code = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", dossier.absolutePath, "--strip-components=1")
            .inheritIO().start().waitFor()
        check(code == 0) { "tar a échoué ($code)" }
    }
}

val distLinux by tasks.registering(Sync::class) {
    val linux = providers.gradleProperty("velvet.linux").orNull == "true"
    doFirst { check(linux) { "ajouter -Pvelvet.linux=true, sinon l'archive embarque le skia de windows" } }
    dependsOn(telechargerJreLinux)
    into(layout.buildDirectory.dir("linux/velvetdesk"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(tasks.named("jar")) { into("lib") }
    // prefixed by group: androidx and jetbrains both publish a `runtime-desktop-1.12.0.jar`, and
    // in one folder the second overwrote the first, which left the app without compose's runtime
    val artefacts = configurations.named("runtimeClasspath").map { c ->
        c.incoming.artifacts.artifacts.map { a ->
            val id = a.id.componentIdentifier
            a.file to ((id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)?.group?.plus("-").orEmpty() + a.file.name)
        }
    }
    artefacts.get().forEach { (fichier, nom) -> from(fichier) { into("lib"); rename { nom } } }
    from(rootProject.file("core/native/linux-x64")) { include("*.so"); into("natives") }
    from(jreLinux) { into("jre") }
    from(project.file("icone")) { include("*.png"); into("icone") }
    from(project.file("linux/velvetdesk")) { filePermissions { unix("rwxr-xr-x") } }
    from(project.file("linux/velvetdesk.desktop"))
    from(project.file("linux/installer.sh")) { filePermissions { unix("rwxr-xr-x") } }
}

tasks.register<Tar>("archiveLinux") {
    group = "distribution"
    description = "velvetdesk-linux-x64.tar.gz: jre, jars, natives, launcher"
    from(distLinux) { into("velvetdesk") }
    archiveFileName.set("velvetdesk-linux-x64.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("linux"))
    compression = Compression.GZIP
    // keep the jre's executables executable
    filePermissions { unix("rw-r--r--") }
    eachFile {
        if (path.contains("/jre/bin/") || path.contains("/jre/lib/jspawnhelper") || path.endsWith("/velvetdesk") || path.endsWith("installer.sh"))
            permissions { unix("rwxr-xr-x") }
    }
}

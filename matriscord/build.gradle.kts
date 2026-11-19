plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(
                rootProject.layout.projectDirectory.file("compose-stabilite.conf")
            )
        }
    }
}

val verify by tasks.registering {
    group = "verification"
    description = "Valide le moteur sur les deux cibles et l'application Android"
    dependsOn(
        "checkDesktopSdk",
        ":core:compileKotlinJvm",
        ":core:assembleAndroidMain",
        ":design:compileKotlinJvm",
        ":design:assembleAndroidMain",
        ":design:jvmTest",
        ":core:jvmTest",
        ":banc-android:assembleDebug",
        ":app-android:assembleDebug",
    )
}

val verifyPortable by tasks.registering {
    group = "verification"
    description = "Vérifications exécutables sans artefact natif desktop (CI)"
    dependsOn(
        ":core:assembleAndroidMain",
        ":design:assembleAndroidMain",
        ":core:testAndroidHostTest",
        ":design:testAndroidHostTest",
        ":ui-commun:compileKotlinJvm",
        ":ui-commun:testAndroidHostTest",
        ":ui-commun:jvmTest",
        ":app-desktop:compileKotlin",
        ":banc-android:assembleDebug",
        ":app-android:assembleDebug",
        ":app-android:testDebugUnitTest",
    )
}

val checkBindingsDesktop by tasks.registering {
    group = "verification"
    description = "Vérifie la présence des bindings Kotlin desktop (nécessaires pour compiler)"

    val bindings = file("core/src/jvmMain/generated/kotlin/org/matrix/rustcomponents/sdk")

    doLast {
        if (!bindings.exists()) {
            throw GradleException(
                """
                |Les liaisons Kotlin du SDK desktop sont absentes.
                |
                |  attendu : ${bindings.path}
                |
                |Elles **sont** versionnées depuis le 2026-10-13, donc ce message ne doit
                |normalement jamais sortir : si vous le voyez, le dossier a été supprimé.
                |
                |  git checkout -- core/src/jvmMain/generated
                |
                |Et si elles ont vraiment disparu, les reproduire prend onze minutes de Rust
                |sous Windows : `pwsh tools/build-desktop-sdk.ps1`.
                """.trimMargin()
            )
        }
    }
}

val NATIF_URL = "https://velvet-chat.li/dl/velvet-natif-windows.zip"
val NATIF_SHA256 = "00fc55e365853f4220c178081851e9e064f5390d8505a8d106cd702c057d2306"

val recupererNatifDesktop by tasks.registering {
    group = "velvet"
    description = "Télécharge les DLL natives du bureau et vérifie leur empreinte"

    val dossier = file("core/native")
    val url = NATIF_URL
    val attendu = NATIF_SHA256

    outputs.files(file("core/native/matrix_sdk_ffi.dll"), file("core/native/livekit_ffi.dll"))
    onlyIf {
        !File(dossier, "matrix_sdk_ffi.dll").exists() || !File(dossier, "livekit_ffi.dll").exists()
    }

    doLast {
        dossier.mkdirs()
        val archive = File(dossier, "natif.zip.part")
        logger.lifecycle("Téléchargement de $url (~48 Mo)…")
        java.net.URI(url).toURL().openStream().use { entree ->
            archive.outputStream().use { entree.copyTo(it) }
        }

        val obtenu = java.security.MessageDigest.getInstance("SHA-256").let { d ->
            archive.inputStream().use { flux ->
                val tampon = ByteArray(1 shl 16)
                while (true) {
                    val lu = flux.read(tampon)
                    if (lu <= 0) break
                    d.update(tampon, 0, lu)
                }
            }
            d.digest().joinToString("") { "%02x".format(it) }
        }
        if (obtenu != attendu) {
            archive.delete()
            throw GradleException(
                """
                |L'archive native n'a pas l'empreinte attendue, et elle est donc refusée.
                |
                |  attendu : $attendu
                |  obtenu  : $obtenu
                |
                |Soit le téléchargement s'est abîmé en route, soit ce n'est pas le fichier que
                |ce dépôt attend. Dans les deux cas on ne déballe pas une DLL qu'on ne
                |reconnaît pas : relancer, et si ça persiste, la reconstruire soi-même avec
                |`pwsh tools/build-desktop-sdk.ps1`.
                """.trimMargin()
            )
        }

        java.util.zip.ZipFile(archive).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.forEach { entree ->
                val nom = File(entree.name).name
                zip.getInputStream(entree).use { flux ->
                    File(dossier, nom).outputStream().use { flux.copyTo(it) }
                }
                logger.lifecycle("  posé : $nom")
            }
        }
        archive.delete()
    }
}

val checkNatifDesktop by tasks.registering {
    group = "verification"
    description = "Vérifie la présence de la lib native desktop (nécessaire pour lancer)"

    val native = file("core/native/matrix_sdk_ffi.dll")

    doLast {
        if (!native.exists()) {
            throw GradleException(
                """
                |La bibliothèque native du SDK desktop est absente.
                |
                |  attendu : ${native.path}
                |
                |Elle ne sert pas à compiler — les liaisons JNA se compilent sans elle — mais
                |l'application ne démarre pas sans, et l'empaquetage la met dans l'installeur.
                |
                |Une commande, ~48 Mo, quelques secondes :
                |
                |  ./gradlew recupererNatifDesktop
                |
                |Elle télécharge l'archive servie par velvet-chat.li et refuse de la déballer
                |si son empreinte ne correspond pas à celle épinglée dans `build.gradle.kts`.
                |
                |La reconstruire soi-même reste possible, et c'est onze minutes de Rust sous
                |Windows : `pwsh tools/build-desktop-sdk.ps1`.
                """.trimMargin()
            )
        }
    }
}

val checkDesktopSdk by tasks.registering {
    group = "verification"
    description = "Vérifie bindings et lib native desktop"
    dependsOn(checkBindingsDesktop, checkNatifDesktop)
}

project(":core").tasks.matching { it.name == "compileKotlinJvm" }.configureEach {
    dependsOn(checkBindingsDesktop)
}
project(":app-desktop").tasks
    .matching { it.name in setOf("packageMsi", "packageDistributionForCurrentOS", "run") }
    .configureEach { dependsOn(checkNatifDesktop) }

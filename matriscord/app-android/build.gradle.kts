import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * the compose compiler's own report on what can and cannot skip
 *
 *     ./gradlew :app-android:assembleRelease -PmetriquesCompose=true
 *
 * it writes, per module, which composables are restartable and skippable and which parameters
 * it considers unstable. that last list is the one that matters: an unstable parameter means a
 * child that recomposes every time its parent does, whatever the value actually did
 *
 * behind a property because it slows the build and writes files nobody needs otherwise. it is
 * here rather than in a note because the alternative is guessing at which of six hundred
 * composables is the expensive one
 */
composeCompiler {
    if (project.findProperty("metriquesCompose") == "true") {
        metricsDestination.set(layout.buildDirectory.dir("metriques-compose"))
        reportsDestination.set(layout.buildDirectory.dir("metriques-compose"))
    }
}

val configFirebase = file("google-services.json")

val idsFirebase = listOf(
    "li.velvetchat",
    "li.velvetchat.debug",
)

// `-Pvelvet.aosp=true`: the build for phones without google services (aosp, grapheneos, lineage
// without gapps). no firebase, so the app keeps its background service for notifications
// `-Pvelvet.public=true`: the build offered for download. nothing private goes in (server, lan
// addresses, api keys), only the signing keys and the push gateway, whose address is the generic
// `sygnal` one. firebase stays: that is what wakes the phone
val publique = providers.gradleProperty("velvet.public").orNull == "true"
val aosp = providers.gradleProperty("velvet.aosp").orNull == "true"

val firebasePret = !aosp && configFirebase.exists() &&
    configFirebase.readText().let { json -> idsFirebase.all { "\"$it\"" in json } }

if (firebasePret) {
    apply(plugin = "com.google.gms.google-services")
} else if (!aosp && configFirebase.exists()) {
    throw GradleException(
        "google-services.json présent mais incomplet : il lui manque " +
            idsFirebase.filterNot { "\"$it\"" in configFirebase.readText() }.joinToString(", ") +
            ". Sans lui la release part sans Firebase, donc sans notifications."
    )
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    if (publique) keys.removeIf { !(it as String).startsWith("velvet.key") && it != "velvet.pushGateway" }
}
fun localProp(key: String): String = localProps.getProperty(key).orEmpty()

val privProps = Properties().apply {
    listOf("prive.properties", "prive.properties.exemple")
        .map { rootProject.file(it) }
        .firstOrNull { it.exists() }
        ?.inputStream()?.use { load(it) }
    if (publique) clear()
}
fun privProp(key: String): String = privProps.getProperty(key).orEmpty()

android {
    signingConfigs {
        create("release") {
            val fichier = localProp("velvet.keystoreFile")
            val trouve = fichier.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
            if (trouve?.exists() == true) {
                storeFile = trouve
                storePassword = localProp("velvet.keystorePassword")
                keyAlias = localProp("velvet.keyAlias")
                keyPassword = localProp("velvet.keyPassword")
            }
        }
    }

    namespace = "li.velvetchat.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "li.velvetchat"
        minSdk = 26
        targetSdk = 36
        versionCode = 90
        versionName = "0.90"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "HOMESERVER", "\"${localProp("velvet.homeserver")}\"")
        buildConfigField("String", "HOMESERVER_LOCAL", "\"${localProp("velvet.homeserver.local")}\"")
        buildConfigField("String", "DICTEE_URL", "\"${localProp("velvet.dictee")}\"")

        buildConfigField(
            "String",
            "CATALOGUE_COSMETIQUES",
            "\"${privProp("velvet.catalogueCosmetiques")}\"",
        )

        buildConfigField("String", "INTENDANT", "\"${privProp("velvet.intendant")}\"")
        buildConfigField("String", "JETON_VOCAL", "\"${privProp("velvet.jetonVocal")}\"")

        buildConfigField("String", "CANAL_MAJ", "\"${privProp("velvet.canalMiseAJour")}\"")

        buildConfigField("String", "BASE_INVITATION", "\"${privProp("velvet.baseInvitation")}\"")

        buildConfigField("String", "KLIPY_KEY", "\"${localProp("velvet.klipyKey")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("boolean", "FIREBASE", if (firebasePret) "true" else "false")

        buildConfigField(
            "String",
            "PUSH_GATEWAY",
            "\"${localProp("velvet.pushGateway")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = project.findProperty("minifierRelease") == "true"
            isShrinkResources = isMinifyEnabled
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-velvet.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":design"))
    implementation(project(":ui-commun"))

    implementation(files("libs/rustls-platform-verifier-0.1.1.aar"))

    implementation(libs.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(libs.livekit.android)
    implementation(libs.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.androidx.fragment)
    implementation(libs.apng)
    implementation(libs.vectordrawable.animated)

    testImplementation(libs.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}

abstract class PolitiqueReseau : DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val hote: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val dossier: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun ecrire() {
        val h = hote.get()
        val exception = if (h.isBlank()) "" else """
            |    <domain-config cleartextTrafficPermitted="true">
            |        <domain includeSubdomains="false">$h</domain>
            |    </domain-config>
        """.trimMargin()
        val cible = dossier.get().asFile.resolve("xml").apply { mkdirs() }
        cible.resolve("network_security_config.xml").writeText(
            """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!--
            |    ENGENDRÉ par `engendrerPolitiqueReseau`. Ne pas modifier à la main : la source
            |    est `velvet.homeserver.local` dans `local.properties`.
            |
            |    Par défaut le trafic en clair est INTERDIT : tout passe par HTTPS. La seule
            |    exception, quand elle existe, est le homeserver joint en direct sur le réseau
            |    local — voir `RustMatrixEngine.sondeLan()` et `BuildConfig.HOMESERVER_LOCAL`. Le
            |    jeton d'accès transite alors en clair sur ce LAN, et c'est le compromis assumé
            |    de la bascule locale. Sans adresse locale configurée : aucune exception.
            |-->
            |<network-security-config>
            |    <base-config cleartextTrafficPermitted="false" />
            |$exception
            |</network-security-config>
            |
            """.trimMargin(),
        )
    }
}

val engendrerPolitiqueReseau by tasks.registering(PolitiqueReseau::class) {
    hote.set(
        localProp("velvet.homeserver.local")
            .removePrefix("http://").removePrefix("https://")
            .substringBefore(':').substringBefore('/'),
    )
}

androidComponents {
    onVariants { variante ->
        variante.sources.res?.addGeneratedSourceDirectory(
            engendrerPolitiqueReseau,
            PolitiqueReseau::dossier,
        )
    }
}

abstract class ThemesLivresAssets : DefaultTask() {
    @get:org.gradle.api.tasks.InputDirectory
    abstract val source: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val dossier: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun engendrer() {
        val cible = dossier.get().asFile.resolve("themes")
        cible.deleteRecursively()
        cible.mkdirs()
        source.get().asFile.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.forEach { it.copyTo(cible.resolve(it.name), overwrite = true) }
    }
}

val engendrerThemesLivres by tasks.registering(ThemesLivresAssets::class) {
    source.set(rootProject.layout.projectDirectory.dir("design/themes"))
}

androidComponents {
    onVariants { variante ->
        variante.sources.assets?.addGeneratedSourceDirectory(
            engendrerThemesLivres,
            ThemesLivresAssets::dossier,
        )
    }
}

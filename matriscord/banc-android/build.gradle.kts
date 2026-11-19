import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(key: String): String = localProps.getProperty(key).orEmpty()

android {
    namespace = "li.velvetchat.banc"
    compileSdk = 37

    defaultConfig {
        applicationId = "li.velvetchat.banc"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "s0.1"

        buildConfigField("String", "HOMESERVER", "\"${localProp("velvet.homeserver")}\"")
        buildConfigField("String", "USERNAME", "\"${localProp("velvet.username")}\"")
        buildConfigField("String", "PASSWORD", "\"${localProp("velvet.password")}\"")

        buildConfigField(
            "boolean",
            "INSECURE_TLS",
            localProps.getProperty("velvet.insecureTls", "false"),
        )

        buildConfigField("String", "PUSH_GATEWAY", "\"${localProp("velvet.pushGateway")}\"")

        buildConfigField("String", "TEST_ROOM_ID", "\"${localProp("velvet.testRoomId")}\"")
        buildConfigField("String", "PUSH_ENDPOINT_HOST", "\"${localProp("velvet.pushEndpointHost")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.matrix.rust.sdk)
    implementation(libs.coroutines.android)

    implementation(files("../app-android/libs/rustls-platform-verifier-0.1.1.aar"))
}

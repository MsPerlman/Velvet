plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    androidLibrary {
        namespace = "li.velvetchat.design"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

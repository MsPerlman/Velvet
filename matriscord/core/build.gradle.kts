plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    androidLibrary {
        namespace = "li.velvetchat.core"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val androidMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
            dependencies {
                implementation(libs.matrix.rust.sdk)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            kotlin.srcDir("src/jvmShared/kotlin")
            kotlin.srcDir("src/jvmMain/generated/kotlin")
            dependencies {
                implementation(libs.jna)
                implementation(libs.jna.platform)
            }
        }
    }
}

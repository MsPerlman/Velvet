plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)

    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    androidLibrary {
        namespace = "li.velvetchat.ui"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                api(project(":design"))
                api(project(":core"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.activity.compose)
                implementation(libs.apng)
                implementation(libs.vectordrawable.animated)
            }
        }

        val commonTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

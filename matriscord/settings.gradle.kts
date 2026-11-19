pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "velvet"

include(":core")
include(":design")

include(":ui-commun")

include(":app-android")

include(":app-desktop")

include(":banc-android")
include(":verif-bureau")

include(":sonde-rendu")

include(":baselineprofile")

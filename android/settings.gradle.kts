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
        // Garmin Connect IQ Maven repository or local aar
        maven { url = java.net.URI("https://connectiq.garmin.com/android-sdk") }
    }
}

rootProject.name = "GarminMapsNavi"
include(":app")


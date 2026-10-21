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
        maven { url = uri("https://maven.fabricmc.net") }
        maven { url = uri("https://maven.quiltmc.org/repository/release") }
        maven { url = uri("https://maven.neoforged.net/releases") }
    }
}

rootProject.name = "NexusLauncherPro"
include(":app")

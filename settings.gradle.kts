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
        // NewPipeExtractor is published to JitPack — pinned to an exact tag in libs.versions.toml.
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "karalo"

include(
    ":app",
    ":core-common",
    ":core-network",
    ":core-ui",
    ":core-testing",
    ":youtube-client",
    ":feature-search",
    ":feature-player",
    ":feature-home",
    ":baselineprofile",
)

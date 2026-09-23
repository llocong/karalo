pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // NewPipeExtractor is published to JitPack — same pinned coordinate the Android app uses
        // (see ../gradle/libs.versions.toml's `newpipeExtractor` entry) so both stay in lockstep.
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "karalo-backend"

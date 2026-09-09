// Top-level build file — plugins are declared here (apply false) and applied per-module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
}

// Aggregate coverage across every module so `./gradlew koverXmlReport`/`koverVerify` at the root
// reflect the whole app, not just :app.
dependencies {
    kover(project(":core-common"))
    kover(project(":core-network"))
    kover(project(":core-ui"))
    kover(project(":youtube-client"))
    kover(project(":feature-search"))
    kover(project(":feature-player"))
    kover(project(":feature-home"))
    kover(project(":app"))
}

kover {
    reports {
        verify {
            rule {
                minBound(60) // ratchet up as coverage grows — see CONTRIBUTING.md
            }
        }
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(libs.versions.ktlintCore.get())
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
            txt.required.set(false)
            sarif.required.set(false)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

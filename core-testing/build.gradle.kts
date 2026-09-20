plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.karalo.core.testing"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // core-testing has no androidTest sources of its own, but as an Android library it still
    // builds an (empty) androidTest APK, which fails to package because the JUnit5 jars pulled
    // in transitively each bundle their own META-INF/LICENSE.md etc.
    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    api(project(":core-common"))
    api(project(":core-karaoke"))
    api(project(":youtube-client"))

    api(libs.junit5.jupiter.api)
    api(libs.mockk)
    api(libs.kotlinx.coroutines.test)
}

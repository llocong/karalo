plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Helpers for Compose UI (instrumented) tests, shared by every module's androidTest sources.
// Kept apart from :core-testing (JUnit5 unit-test fakes) so it doesn't drag the app's data
// modules into core-ui's own tests.
android {
    namespace = "com.karalo.core.ui.testing"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui.test.junit4)
    implementation(libs.compose.ui)
    implementation(libs.androidx.test.ext.junit)
}

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.karalo.baselineprofile"
    compileSdk = 35

    defaultConfig {
        // BaselineProfileRule's on-device profile collection requires API 28+ -- unrelated to
        // :app's own minSdk 24, which is what actually ships to users.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // This module doesn't ship its own app code -- it drives :app's real APK via UiAutomator to
    // record which classes/methods run during the flows below, then hands that profile back to
    // :app to bundle. See BaselineProfileGenerator's own doc for which flows and why.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// No managed devices are configured here -- this repo's CI has no device/emulator available (see
// app/build.gradle.kts's baselineProfile { automaticGenerationDuringBuild = false }), so profiles
// are generated manually against a connected device/emulator and the result is committed. See
// CONTRIBUTING.md "Performance" for the exact command.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

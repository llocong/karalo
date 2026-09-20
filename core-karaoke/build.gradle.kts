plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.karalo.core.karaoke"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        // Local-LAN dev defaults -- see NetworkConfig's own doc for the override mechanism.
        // Mirrors the RELEASE_KEYSTORE_* env-var-or-gradle-property precedent in
        // app/build.gradle.kts. Deliberately NOT a production URL -- see the karaoke ADR.
        val backendBaseUrl =
            System.getenv("KARALO_BACKEND_BASE_URL")
                ?: providers.gradleProperty("KARALO_BACKEND_BASE_URL").orNull
                ?: "http://192.168.1.100:8080"
        val backendWsUrl =
            System.getenv("KARALO_BACKEND_WS_URL")
                ?: providers.gradleProperty("KARALO_BACKEND_WS_URL").orNull
                ?: "ws://192.168.1.100:8080"
        buildConfigField("String", "DEFAULT_BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("String", "DEFAULT_BACKEND_WS_URL", "\"$backendWsUrl\"")
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":core-common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(project(":core-testing"))
    testImplementation(libs.junit5.jupiter.api)
    testRuntimeOnly(libs.junit5.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

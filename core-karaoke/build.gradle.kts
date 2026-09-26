import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Unlike sdk.dir, Gradle doesn't read local.properties for arbitrary keys on its own -- this is
// the KARALO_BACKEND_BASE_URL/KARALO_BACKEND_WS_URL fallback's actual source, loaded once here.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

android {
    namespace = "com.karalo.core.karaoke"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        // Local-LAN dev defaults -- see NetworkConfig's own doc for the override mechanism.
        // Mirrors the RELEASE_KEYSTORE_* env-var-or-gradle-property precedent in
        // app/build.gradle.kts, plus a local.properties fallback (gitignored, so it's the
        // per-machine default -- set it once and every plain `installDebug` picks it up,
        // instead of silently reverting to the placeholder IP below if the env var isn't
        // exported in that particular shell). Deliberately NOT a production URL -- see the
        // karaoke ADR.
        val backendBaseUrl =
            System.getenv("KARALO_BACKEND_BASE_URL")
                ?: providers.gradleProperty("KARALO_BACKEND_BASE_URL").orNull
                ?: localProperties.getProperty("KARALO_BACKEND_BASE_URL")
                ?: "http://192.168.1.100:8080"
        val backendWsUrl =
            System.getenv("KARALO_BACKEND_WS_URL")
                ?: providers.gradleProperty("KARALO_BACKEND_WS_URL").orNull
                ?: localProperties.getProperty("KARALO_BACKEND_WS_URL")
                ?: "ws://192.168.1.100:8080"
        // Only needed against a backend with KARALO_TV_REGISTRATION_KEY set (an internet-exposed
        // one); empty means "don't send the header", which a home-LAN backend doesn't require.
        val tvRegistrationKey =
            System.getenv("KARALO_TV_REGISTRATION_KEY")
                ?: providers.gradleProperty("KARALO_TV_REGISTRATION_KEY").orNull
                ?: localProperties.getProperty("KARALO_TV_REGISTRATION_KEY")
                ?: ""
        buildConfigField("String", "DEFAULT_BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("String", "DEFAULT_BACKEND_WS_URL", "\"$backendWsUrl\"")
        buildConfigField("String", "TV_REGISTRATION_KEY", "\"$tvRegistrationKey\"")
        // The app's version (same source as app/build.gradle.kts), sent to the backend so it can
        // count which versions TVs are running.
        val appVersionName =
            Regex(""""\."\s*:\s*"([^"]+)"""")
                .find(rootProject.file(".release-please-manifest.json").readText())
                ?.groupValues
                ?.get(1)
                .orEmpty()
        buildConfigField("String", "APP_VERSION", "\"$appVersionName\"")
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

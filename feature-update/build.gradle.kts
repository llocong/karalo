import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Same backend URL sources as :core-karaoke (env var, Gradle property, then local.properties):
// the app checks for updates at <backend>/download/latest.json, where deploy/publish-apk.sh
// puts each release.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
val backendBaseUrl: String =
    System.getenv("KARALO_BACKEND_BASE_URL")
        ?: providers.gradleProperty("KARALO_BACKEND_BASE_URL").orNull
        ?: localProperties.getProperty("KARALO_BACKEND_BASE_URL")
        ?: "http://192.168.1.100:8080"

// The self-updater, on by default. Play policy forbids one, so a Play build passes
// -Pkaralo.selfUpdate=false: that drops the install permission and FileProvider from the manifest
// and turns every update check into a no-op (see .github/workflows/release.yml).
val selfUpdate: Boolean = providers.gradleProperty("karalo.selfUpdate").orNull?.toBoolean() ?: true

android {
    namespace = "com.karalo.feature.update"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"${backendBaseUrl.trimEnd('/')}/download/latest.json\"")
        buildConfigField("boolean", "SELF_UPDATE", selfUpdate.toString())
    }

    sourceSets {
        getByName("main") {
            if (!selfUpdate) manifest.srcFile("src/noSelfUpdate/AndroidManifest.xml")
        }
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core-ui"))
    implementation(project(":core-network"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.tv.material)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core-testing"))
    testImplementation(libs.junit5.jupiter.api)
    testRuntimeOnly(libs.junit5.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

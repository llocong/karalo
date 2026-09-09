plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.karalo.youtubeclient"
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
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-network"))

    implementation(libs.okhttp)

    // Unofficial YouTube extraction — pinned to an exact tag (see gradle/libs.versions.toml and
    // docs/adr/0002-unofficial-youtube-extraction.md). This is the ONLY module allowed to
    // reference org.schabi.newpipe.extractor.* directly; everything else in the app talks to
    // YouTubeClient.
    implementation(libs.newpipe.extractor)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit5.jupiter.api)
    testRuntimeOnly(libs.junit5.jupiter.engine)
    testImplementation(libs.junit5.jupiter.params)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

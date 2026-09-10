plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.karalo.karalo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.karalo.karalo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.karalo.karalo.HiltTestRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath =
                System.getenv("RELEASE_KEYSTORE_PATH") ?: providers.gradleProperty("RELEASE_KEYSTORE_PATH").orNull
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword =
                    System.getenv("RELEASE_KEYSTORE_PASSWORD")
                        ?: providers.gradleProperty("RELEASE_KEYSTORE_PASSWORD").orNull
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
                keyPassword =
                    System.getenv("RELEASE_KEY_PASSWORD") ?: providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only applies the release signing config when secrets are actually present locally/in CI
            // (see README "Secrets" section) — an unsigned build is still useful for local testing.
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    // androidTestImplementation(:core-testing) pulls in JUnit5, and several of its jars each
    // bundle their own META-INF/LICENSE.md — same fix as core-testing/build.gradle.kts.
    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // NewPipeExtractor calls java.util.stream.Collectors APIs (e.g. toUnmodifiableList())
        // that ART only implements on API 33+; desugaring backports them for our minSdk 24.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-ui"))
    implementation(project(":feature-search"))
    implementation(project(":feature-player"))
    implementation(project(":feature-home"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.tv.material)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    testImplementation(project(":core-testing"))
    testImplementation(libs.junit5.jupiter.api)
    testRuntimeOnly(libs.junit5.jupiter.engine)
    testImplementation(libs.mockk)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(project(":core-testing"))
    androidTestImplementation(project(":youtube-client"))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}

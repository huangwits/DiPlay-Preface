plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
}

apply(from = rootProject.file("scripts/seal-bluetooth.gradle"))

android {
    namespace = "com.shilapi.xcertplay.host"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 22
    }

    buildFeatures { compose = true }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // The UI suite covers several SDKs and locale-specific resource sandboxes.
        unitTests.all { it.maxHeapSize = "1g" }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    api(project(":shared"))
    implementation(project(":vehicle-probe"))
    implementation(libs.androidx.activity)
    // Compose 1.7 / Material 3 1.3 retain API-21 support for the E01/API-22 build.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation("com.google.zxing:core:3.5.3")
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation(project(":jmdns"))
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Opt-in release verification consumes the actual APK without checking credentials into Git.
val validationApk = providers.environmentVariable("DIPLAY_VALIDATION_APK")
tasks.withType<Test>().configureEach {
    systemProperty("diplay.validationApk", validationApk.getOrElse(""))
    if (validationApk.isPresent) inputs.file(validationApk)
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.shihab.diplay.diagnostics"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.shihab.diplay.diagnostics"
        minSdk = 22
        targetSdk = 37
        versionCode = 3
        versionName = "0.3-e01-interface"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    lint {
        abortOnError = true
    }
}

dependencies {
    implementation("androidx.core:core:1.6.0")
    testImplementation(libs.junit)
}

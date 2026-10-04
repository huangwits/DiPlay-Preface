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
        versionCode = 2
        versionName = "0.2-e01"
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
    testImplementation(libs.junit)
}

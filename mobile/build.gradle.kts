import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.cert.CertificateFactory
import java.security.interfaces.ECPublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
}

// Optional local-only input. CI and ordinary source builds contain no accessory identity.
val localAuthenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.shihab.diplay"
        minSdk = 22
        targetSdk = 37
        versionCode = 41
        versionName = "0.2.12"

    }


    localAuthenticationAssets?.let { sourceSets.getByName("main").assets.srcDir(it) }

    signingConfigs {
        create("release") {
            storeFile = file(
                providers.environmentVariable("ANDROID_KEYSTORE_PATH")
                    .getOrElse("missing-release-keystore.jks"),
            )
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").getOrElse("")
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").getOrElse("")
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").getOrElse("")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".hudtest"
            versionNameSuffix = "-hud-test"
        }
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
        }
        create("e01") {
            initWith(getByName("release"))
            applicationIdSuffix = ".e01legacy"
            versionNameSuffix = "-e01.12-geely-android51"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
            resValue("bool", "config_e01_default", "true")
            // Keep the upstream Android Bluetooth path unless the user selects a vendor backend.
            resValue("bool", "config_factory_bluetooth_default", "false")
            resValue("string", "app_name", "DiPlay E01 Legacy")
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { resValues = true }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}

// No implicit import. Only the two explicitly selected local runtime assets are allowed.
val credentialAssets = files(android.sourceSets.flatMap { source ->
    source.assets.directories.map { directory ->
        fileTree(directory) {
            include("**/offline-mfi/**", "**/*.pk8", "**/*.p7b", "**/*.key",
                "**/*.pem", "**/*.p12", "**/*.pfx", "**/*.jks", "**/*.keystore")
        }
    }
})
val rejectBundledCredentials by tasks.registering {
    group = "verification"
    description = "Reject unexpected credential files in APK assets."
    val filesToCheck = credentialAssets
    val allowed = localAuthenticationAssets?.let { dir ->
        listOf("identity.pk8", "certificate.p7b").map { dir.resolve("offline-mfi/$it").canonicalFile }.toSet()
    } ?: emptySet()
    inputs.files(filesToCheck)
    doLast {
        check(allowed.all { it.isFile }) { "Explicit local authentication assets are incomplete" }
        val unexpected = filesToCheck.files.filter { it.canonicalFile !in allowed }
        check(unexpected.isEmpty()) { "Unexpected credential files in APK assets" }
    }
}
tasks.named("preBuild") { dependsOn(rejectBundledCredentials) }

// Car-test packages must be standalone. Keep ordinary source/CI builds identity-free.
val verifyStandaloneAuthentication by tasks.registering {
    group = "verification"
    description = "Require the explicit runtime authentication input for a standalone car-test APK."
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) {
            "Standalone car builds require DIPLAY_AUTH_ASSETS_DIR; assembleDebug alone is source-only."
        }
        check(listOf("identity.pk8", "certificate.p7b").all {
            directory.resolve("offline-mfi/$it").let { file -> file.isFile && file.length() > 0 }
        }) { "Standalone CarPlay authentication files are missing or empty" }
        val keyBytes = directory.resolve("offline-mfi/identity.pk8").readBytes()
        val certificateBytes = directory.resolve("offline-mfi/certificate.p7b").readBytes()
        check(keyBytes.size <= 16 * 1024 && certificateBytes.size <= 16 * 1024) {
            "Standalone authentication input exceeds the runtime size limit"
        }
        val privateKey = try {
            KeyFactory.getInstance("EC").generatePrivate(
                PKCS8EncodedKeySpec(keyBytes),
            )
        } finally { keyBytes.fill(0) }
        val certificates = CertificateFactory.getInstance("X.509")
            .generateCertificates(certificateBytes.inputStream())
        check(certificates.size == 1) { "Expected one accessory certificate" }
        val publicKey = certificates.single().publicKey as? ECPublicKey
            ?: error("Expected an EC accessory certificate")
        check(publicKey.params.order.toString(16) ==
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551") {
            "Expected a P-256 accessory certificate"
        }
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        val signature = Signature.getInstance("NONEwithECDSA").run {
            initSign(privateKey); update(challenge); sign()
        }
        check(Signature.getInstance("NONEwithECDSA").run {
            initVerify(publicKey); update(challenge); verify(signature)
        }) { "Standalone private key does not match certificate" }
    }
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneAuthentication) }
tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Build a standalone car-test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleDebug")
}

tasks.register("assembleStandaloneE01") {
    group = "build"
    description = "Build the legacy E01 test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleE01")
    val packagedApk = layout.buildDirectory.file("outputs/apk/e01/mobile-e01.apk")
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) { "Standalone authentication input is required" }
        ZipFile(packagedApk.get().asFile).use { apk ->
            for (name in listOf("identity.pk8", "certificate.p7b")) {
                val entry = apk.getEntry("assets/offline-mfi/$name")
                    ?: error("Standalone E01 APK is missing runtime authentication")
                val bundled = apk.getInputStream(entry).use { it.readBytes() }
                val expected = directory.resolve("offline-mfi/$name").readBytes()
                try {
                    check(MessageDigest.isEqual(expected, bundled)) {
                        "Standalone E01 APK authentication differs from the verified input"
                    }
                } finally { bundled.fill(0); expected.fill(0) }
            }
        }
    }
}

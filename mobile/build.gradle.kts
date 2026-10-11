import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.cert.CertificateFactory
import java.security.interfaces.ECPublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.net.URI
import java.util.Base64
import java.security.spec.X509EncodedKeySpec
import java.security.interfaces.RSAPublicKey

plugins {
    alias(libs.plugins.android.application)
}

// Optional local-only input. CI and ordinary source builds contain no accessory identity.
val localAuthenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }

// Public issuer key and HTTPS origin only. Issuer private key and admin token stay on the server.
val localLicenseAssets = providers.environmentVariable("DIPLAY_LICENSE_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }
val licensePreview = providers.environmentVariable("DIPLAY_LICENSE_PREVIEW").map { it == "true" }.getOrElse(false)

// Local personal E01 payload; never inferred or downloaded by a build.
val localGocAssets = providers.environmentVariable("DIPLAY_GOC_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }
localGocAssets?.let { directory ->
    val payload = directory.resolve("e01-goc/gocsdk-spp-uuid128-v2")
    require(payload.isFile && payload.length() == 2841668L) { "Missing E01 GOC payload" }
    val digest = MessageDigest.getInstance("SHA-256").digest(payload.readBytes()).joinToString("") { "%02x".format(it) }
    require(digest == "e2b71f11ae17f701469f3b60982dbc7a273bcab947f18fd1979e43df6428c54c") { "E01 GOC payload hash mismatch" }
}

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.shihab.diplay"
        minSdk = 22
        targetSdk = 37
        versionCode = 58
        // Carlito upstream version plus the Preface maintenance revision; see docs/VERSIONING.md.
        versionName = "0.2.16.14"

    }


    androidResources { localeFilters += listOf("zh-rCN") }

    // No Picnic, CertPathReviewer or desktop coroutine agent is used by the app.
    // R8 removes their code but dependency resources otherwise survive in the APK.
    packaging.resources.excludes += setOf(
        "org/bouncycastle/pqc/crypto/picnic/lowmcL1.bin.properties",
        "org/bouncycastle/pqc/crypto/picnic/lowmcL3.bin.properties",
        "org/bouncycastle/pqc/crypto/picnic/lowmcL5.bin.properties",
        "org/bouncycastle/x509/CertPathReviewerMessages.properties",
        "org/bouncycastle/x509/CertPathReviewerMessages_de.properties",
        "DebugProbesKt.bin",
    )

    localAuthenticationAssets?.let { sourceSets.getByName("main").assets.srcDir(it) }

    for (variant in listOf("e01", "e01Licensed")) {
        localGocAssets?.let { sourceSets.maybeCreate(variant).assets.srcDir(it) }
    }
    localLicenseAssets?.let { sourceSets.maybeCreate("e01Licensed").assets.srcDir(it) }

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
                enable = true
            }
            signingConfig = signingConfigs.getByName("release")
        }
        create("e01") {
            initWith(getByName("release"))
            // Owner's local build remains unminified and usable without activation.
            optimization { enable = false }
            applicationIdSuffix = ".preface"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
            // Keep the upstream Android Bluetooth path unless the user selects a vendor backend.
            resValue("bool", "config_factory_bluetooth_default", "false")
            resValue("string", "app_name", "DiPlay 星瑞")
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        }
        create("e01Public") {
            initWith(getByName("e01"))
            matchingFallbacks += listOf("e01", "release")
            optimization { enable = true }
            resValue("bool", "config_offline_license", "true")
            resValue("bool", "config_online_license", "false")
        }
        create("e01Licensed") {
            initWith(getByName("e01"))
            matchingFallbacks += listOf("e01", "release")
            optimization { enable = true }
            resValue("bool", "config_online_license", "true")
            resValue("string", "app_name", if (licensePreview) "DiPlay 授权预览" else "DiPlay 星瑞")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { resValues = true }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // jmdns uses SLF4J 1.7; select its explicit no-op binding instead of a missing reflective binder.
    runtimeOnly("org.slf4j:slf4j-nop:${libs.versions.slf4j.get()}")
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

val verifyOfflineLicenseConfiguration by tasks.registering {
    group = "verification"
    val directory = layout.projectDirectory.dir("src/e01Public/assets")
    inputs.dir(directory)
    doLast {
        val configuration = directory.file("offline-license/public.json").asFile
        check(directory.asFile.walkTopDown().filter { it.isFile }.toSet() == setOf(configuration)) {
            "Public offline licensing assets must contain only offline-license/public.json"
        }
        val values = groovy.json.JsonSlurper().parse(configuration) as Map<*, *>
        check(values.keys == setOf("version", "package", "signer", "contact", "publicKey"))
        check(values["version"] == 1 && values["package"] == "com.shihab.diplay.preface" && values["contact"] == "starts181004")
        check(values["signer"] == "db0dc2a34dc06f22db7a3d10103fa011167712ebe61985ca2103084ff542cb82")
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(
            Base64.getDecoder().decode(values["publicKey"] as String))) as RSAPublicKey
        check(key.modulus.bitLength() in 3072..4096)
    }
}
tasks.matching { it.name == "preE01PublicBuild" }.configureEach { dependsOn(verifyOfflineLicenseConfiguration) }

tasks.register("assembleStandaloneE01Public") {
    group = "build"
    description = "Build the minified, offline-activated public APK without personal helper payloads."
    dependsOn(verifyStandaloneAuthentication, "assembleE01Public")
    val packagedApk = layout.buildDirectory.file("outputs/apk/e01Public/mobile-e01Public.apk")
    val authentication = localAuthenticationAssets
    doLast {
        check(authentication != null)
        ZipFile(packagedApk.get().asFile).use { apk ->
            for (name in listOf("identity.pk8", "certificate.p7b")) {
                val entry = checkNotNull(apk.getEntry("assets/offline-mfi/$name"))
                val actual = apk.getInputStream(entry).use { it.readBytes() }
                check(MessageDigest.isEqual(actual, authentication.resolve("offline-mfi/$name").readBytes()))
            }
            check(apk.getEntry("assets/offline-license/public.json") != null)
            for (name in listOf("assets/e01-goc/gocsdk-spp-uuid128-v2", "assets/e01-bluetooth/mtk-su", "assets/license/server.json")) {
                check(apk.getEntry(name) == null) { "Public APK contains a personal-only payload or online license configuration" }
            }
        }
    }
}

val verifyLicenseConfiguration by tasks.registering {
    group = "verification"
    val directory = localLicenseAssets
    val preview = licensePreview
    inputs.files(directory?.let { fileTree(it) } ?: files())
    inputs.property("preview", preview)
    doLast {
        check(directory != null) { "DIPLAY_LICENSE_ASSETS_DIR is required for the licensed APK" }
        val configuration = directory.resolve("license/server.json")
        check(directory.walkTopDown().filter { it.isFile }.map { it.canonicalFile }.toSet() == setOf(configuration.canonicalFile)) {
            "Only license/server.json may be bundled; never include issuer secrets"
        }
        val values = groovy.json.JsonSlurper().parse(configuration) as Map<*, *>
        check(values.keys == setOf("url", "publicKey", "package", "signer", "contact")) { "Unexpected client configuration fields" }
        check(values["contact"] == "starts181004")
        val origin = values["url"] as String
        if (preview) check(origin.isEmpty()) { "Preview requires an empty URL and cannot activate" }
        else {
            val uri = URI(origin)
            check(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null && uri.path in listOf("", "/")) {
                "Production licensing requires an HTTPS origin"
            }
        }
        check(values["package"] == "com.shihab.diplay.preface")
        check(values["signer"] == "db0dc2a34dc06f22db7a3d10103fa011167712ebe61985ca2103084ff542cb82")
        val issuer = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(
            Base64.getDecoder().decode(values["publicKey"] as String))) as RSAPublicKey
        check(issuer.modulus.bitLength() in 3072..4096) { "Issuer RSA key must be 3072 to 4096 bits" }
    }
}

tasks.register("assembleStandaloneE01Licensed") {
    group = "build"
    description = "Build the optimized, device-activated E01 APK; empty origin is allowed only in explicit preview mode."
    dependsOn(verifyStandaloneAuthentication, verifyLicenseConfiguration, "assembleE01Licensed")
    val packagedApk = layout.buildDirectory.file("outputs/apk/e01Licensed/mobile-e01Licensed.apk")
    val authentication = localAuthenticationAssets
    val licensing = localLicenseAssets
    doLast {
        check(authentication != null && licensing != null)
        ZipFile(packagedApk.get().asFile).use { apk ->
            for ((name, file) in listOf(
                "assets/offline-mfi/identity.pk8" to authentication.resolve("offline-mfi/identity.pk8"),
                "assets/offline-mfi/certificate.p7b" to authentication.resolve("offline-mfi/certificate.p7b"),
                "assets/license/server.json" to licensing.resolve("license/server.json"))) {
                val bytes = apk.getInputStream(checkNotNull(apk.getEntry(name))).use { it.readBytes() }
                check(MessageDigest.isEqual(bytes, file.readBytes())) { "Packaged input differs from selected local input" }
                bytes.fill(0)
            }
        }
    }
}
tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Build a standalone car-test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleDebug")
}

tasks.register("assembleStandaloneE01") {
    group = "build"
    description = "Build the unminified local E01 APK without activation, with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleE01")
    val packagedApk = layout.buildDirectory.file("outputs/apk/e01/mobile-e01.apk")
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) { "Standalone authentication input is required" }
        ZipFile(packagedApk.get().asFile).use { apk ->
            check(apk.entries().asSequence().none { it.name.startsWith("assets/e01-bluetooth/") }) {
                "Retired Bluetooth switching assets must not be packaged"
            }
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

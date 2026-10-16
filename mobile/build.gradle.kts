import java.security.KeyFactory
import java.security.KeyStore
import java.security.spec.PKCS8EncodedKeySpec
import java.security.cert.CertificateFactory
import java.security.interfaces.ECPublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.MessageDigest
import java.io.File
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.net.URI
import java.util.Base64
import java.security.spec.X509EncodedKeySpec
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

private val e01MapConfigNames = listOf("C04010181159_MultipleScreen.json", "MultipleScreen.json")

abstract class SealE01MapConfigAssetsTask : DefaultTask() {
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val masterKeyFile: RegularFileProperty

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @get:Input
    abstract val configNames: ListProperty<String>

    @get:Input
    abstract val signer: Property<String>

    @get:Input
    abstract val purpose: Property<String>

    @TaskAction
    fun seal() {
        val destination = destinationDirectory.get().asFile
        destination.deleteRecursively()
        val source = sourceDirectory.orNull?.asFile ?: return
        val keyFile = masterKeyFile.orNull?.asFile ?: error("Local encrypted-asset key file was not configured")
        check(keyFile.isFile) { "Local encrypted-asset key was not generated" }
        val suppliedFiles = source.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(source).invariantSeparatorsPath }.toSet()
        check(suppliedFiles == configNames.get().toSet()) {
            "Only the two inspected FS11 map configurations may be staged"
        }
        val master = keyFile.readBytes()
        check(master.size == 32) { "Local encrypted-asset key is invalid" }
        try {
            for (name in configNames.get()) {
                val sourceFile = source.resolve(name)
                check(sourceFile.isFile) { "Missing inspected FS11 map configuration: $name" }
                val plain = sourceFile.readBytes()
                check(plain.isNotEmpty() && plain.size <= MAX_CONFIG_BYTES) { "Invalid FS11 map configuration size: $name" }
                val envelope = encrypt(plain, name, master)
                val decoded = decrypt(envelope, name, master)
                try {
                    check(MessageDigest.isEqual(plain, decoded)) { "Encrypted FS11 map configuration failed verification: $name" }
                    val output = destination.resolve("e01-map/$name.enc")
                    output.parentFile.mkdirs()
                    output.writeBytes(envelope)
                } finally {
                    plain.fill(0)
                    envelope.fill(0)
                    decoded.fill(0)
                }
            }
        } finally {
            master.fill(0)
        }
    }

    private fun context(name: String) = "${purpose.get()}\u0000${signer.get()}\u0000$name".toByteArray(Charsets.UTF_8)

    private fun encrypt(plain: ByteArray, name: String, master: ByteArray): ByteArray {
        val packed = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(plain) } }.toByteArray()
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val aad = context(name)
        val key = MessageDigest.getInstance("SHA-256").run { update(master); digest(aad) }
        val encrypted = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad)
                doFinal(packed)
            }
        } finally {
            aad.fill(0)
            key.fill(0)
            packed.fill(0)
        }
        return try {
            ByteArrayOutputStream().apply {
                write("DPG1".toByteArray(Charsets.US_ASCII))
                write(nonce)
                write(encrypted)
            }.toByteArray()
        } finally {
            nonce.fill(0)
            encrypted.fill(0)
        }
    }

    private fun decrypt(envelope: ByteArray, name: String, master: ByteArray): ByteArray {
        check(envelope.size in 32..(MAX_CONFIG_BYTES + ENVELOPE_OVERHEAD))
        check(envelope.copyOfRange(0, 4).contentEquals("DPG1".toByteArray(Charsets.US_ASCII)))
        val aad = context(name)
        val key = MessageDigest.getInstance("SHA-256").run { update(master); digest(aad) }
        val packed = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, envelope.copyOfRange(4, 16)))
                updateAAD(aad)
                doFinal(envelope, 16, envelope.size - 16)
            }
        } finally {
            aad.fill(0)
            key.fill(0)
        }
        return try {
            GZIPInputStream(packed.inputStream()).use { it.readNBytes(MAX_CONFIG_BYTES + 1) }.also {
                check(it.size <= MAX_CONFIG_BYTES) { "Factory map configuration exceeds its limit" }
            }
        } finally {
            packed.fill(0)
        }
    }

    private companion object {
        const val MAX_CONFIG_BYTES = 1_048_576
        const val ENVELOPE_OVERHEAD = 65_536
    }
}

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
val localE01MapConfigAssets = providers.environmentVariable("DIPLAY_E01_MAP_CONFIG_DIR")
    .orNull?.let { file(it).canonicalFile }
val sealedE01MapConfigAssets = layout.buildDirectory.dir("generated/sealedE01MapConfigAssets")
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
        versionCode = 83
        // Original DiPlay upstream version plus the Preface revision; see docs/VERSIONING.md.
        versionName = "0.2.20.1"

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

    // GOC runtime payloads are generated as authenticated encrypted assets below.
    localLicenseAssets?.let { sourceSets.maybeCreate("e01Licensed").assets.srcDir(it) }
    localE01MapConfigAssets?.let {
        sourceSets.maybeCreate("e01Licensed").assets.srcDir(sealedE01MapConfigAssets.get().asFile)
    }

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
            // E01 is an installable update for the existing Preface package. It must
            // use the owner-selected release key; never silently fall back to Debug.
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            matchingFallbacks += listOf("release")
            // This vehicle build starts with factory Bluetooth; an explicit choice still wins.
            resValue("bool", "config_factory_bluetooth_default", "true")
            resValue("string", "app_name", "GeelyPlay")
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
            resValue("string", "app_name", "GeelyPlay")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { resValues = true }
}

val sealE01MapConfigAssets = tasks.register<SealE01MapConfigAssetsTask>("sealE01MapConfigAssets") {
    destinationDirectory.set(sealedE01MapConfigAssets)
    configNames.set(e01MapConfigNames)
    signer.set("8accfdd43476ee44404140964c3bd5c38edde9fd3d47e76bdd7e48c2c394e418")
    purpose.set("GeelyPlay FS11 flyscreen v1")
    localE01MapConfigAssets?.let {
        sourceDirectory.set(it)
        masterKeyFile.set(rootProject.layout.projectDirectory.file("common/build/generated/sealedBluetoothKey/key.bin"))
        dependsOn(":common:sealBluetoothAssets")
    }
}
tasks.named("preBuild") { dependsOn(sealE01MapConfigAssets) }
tasks.matching { it.name == "rejectBundledCredentials" }.configureEach {
    dependsOn(sealE01MapConfigAssets)
}

// The last delivered Preface APK (.25) used this certificate. Check the local
// release keystore before any E01 variant is assembled so a wrong or missing key
// fails the build instead of producing an APK that cannot update the car install.
abstract class VerifyE01SigningTask : DefaultTask() {
    @TaskAction
    fun verify() {
        val pathText = System.getenv("ANDROID_KEYSTORE_PATH")
            ?: error("ANDROID_KEYSTORE_PATH is required for E01 builds; Debug signing is forbidden.")
        val storePath = File(pathText).canonicalFile
        check(storePath.isFile) { "E01 signing keystore does not exist: $storePath" }
        val storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            ?: error("ANDROID_KEYSTORE_PASSWORD is required for E01 builds.")
        val alias = System.getenv("ANDROID_KEY_ALIAS")
            ?: error("ANDROID_KEY_ALIAS is required for E01 builds.")
        val keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            ?: error("ANDROID_KEY_PASSWORD is required for E01 builds.")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        FileInputStream(storePath).use { input -> keyStore.load(input, storePassword.toCharArray()) }
        check(keyStore.containsAlias(alias)) { "E01 signing alias is missing from the configured keystore." }
        check(keyStore.getKey(alias, keyPassword.toCharArray()) != null) {
            "E01 signing key password or alias is invalid."
        }
        val certificate = keyStore.getCertificate(alias)
            ?: error("E01 signing certificate is missing from the configured keystore.")
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        check(actual.equals("8accfdd43476ee44404140964c3bd5c38edde9fd3d47e76bdd7e48c2c394e418", ignoreCase = true)) {
            "Wrong E01 signing certificate: $actual; expected replacement Preface signer 8accfdd43476ee44404140964c3bd5c38edde9fd3d47e76bdd7e48c2c394e418."
        }
    }
}

val verifyE01Signing = tasks.register<VerifyE01SigningTask>("verifyE01Signing") {
    group = "verification"
    description = "Require the existing Preface Android signing certificate for E01 builds."
}

tasks.configureEach {
    if (name.startsWith("preE01") && name.endsWith("Build")) {
        dependsOn(verifyE01Signing)
    }
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

apply(from = rootProject.file("scripts/seal-bluetooth.gradle"))

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
        check(values["signer"] == "8accfdd43476ee44404140964c3bd5c38edde9fd3d47e76bdd7e48c2c394e418")
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
            for (name in listOf("assets/e01-goc/gocsdk-spp-uuid128-v2", "assets/e01-goc/component.enc", "assets/e01-bluetooth/mtk-su", "assets/license/server.json")) {
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
        check(values["signer"] == "8accfdd43476ee44404140964c3bd5c38edde9fd3d47e76bdd7e48c2c394e418")
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
    val namedApk = layout.buildDirectory.file("outputs/apk/e01Licensed/GeelyPlay-v0.2.20.1.apk")
    val deliveryApk = rootProject.layout.projectDirectory.file("../../outputs/GeelyPlay-0.2.20.1-20261016/GeelyPlay-v0.2.20.1.apk")
    val authentication = localAuthenticationAssets
    val licensing = localLicenseAssets
    val factoryMapConfigs = localE01MapConfigAssets
    doLast {
        check(authentication != null && licensing != null && factoryMapConfigs != null)
        ZipFile(packagedApk.get().asFile).use { apk ->
            for ((name, file) in listOf(
                "assets/offline-mfi/identity.pk8" to authentication.resolve("offline-mfi/identity.pk8"),
                "assets/offline-mfi/certificate.p7b" to authentication.resolve("offline-mfi/certificate.p7b"),
                "assets/license/server.json" to licensing.resolve("license/server.json"))) {
                val bytes = apk.getInputStream(checkNotNull(apk.getEntry(name))).use { it.readBytes() }
                check(MessageDigest.isEqual(bytes, file.readBytes())) { "Packaged input differs from selected local input" }
                bytes.fill(0)
            }
            for (name in e01MapConfigNames) {
                val plaintextAsset = "assets/$name"
                check(apk.getEntry(plaintextAsset) == null) { "Plaintext flyscreen configuration was packaged" }
                val encryptedName = "assets/e01-map/$name.enc"
                val envelope = apk.getInputStream(checkNotNull(apk.getEntry(encryptedName))).use { it.readBytes() }
                val expected = sealedE01MapConfigAssets.get().asFile.resolve("e01-map/$name.enc").readBytes()
                try {
                    check(envelope.size > 32 && envelope.copyOfRange(0, 4).contentEquals("DPG1".toByteArray()))
                    check(MessageDigest.isEqual(envelope, expected)) { "Packaged flyscreen configuration differs from encrypted build output" }
                } finally {
                    envelope.fill(0)
                    expected.fill(0)
                }
            }
        }
        packagedApk.get().asFile.copyTo(namedApk.get().asFile, overwrite = true)
        deliveryApk.asFile.parentFile.mkdirs()
        packagedApk.get().asFile.copyTo(deliveryApk.asFile, overwrite = true)
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

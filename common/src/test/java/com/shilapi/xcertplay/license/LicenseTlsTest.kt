package com.shilapi.xcertplay.license

import com.shilapi.xcertplay.host.R
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class LicenseTlsTest {
    private val missingRoots = object : X509TrustManager {
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            throw CertificateException("Legacy root store lacks this CA")
        }
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            throw CertificateException("Client certificate not trusted")
        }
        override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
    }
    private fun certificate(name: String): X509Certificate = javaClass.classLoader!!
        .getResourceAsStream("license-tls/$name.der")!!.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
    private fun manager() = LicenseTls.SupplementalTrustManager(missingRoots,
        LicenseTls.trustedRoots(listOf(certificate("approved-root"))))

    @Test fun supplementsMissingRootWithFullCertificateChainValidation() {
        val chain = arrayOf(certificate("valid"), certificate("approved-root"))
        try { missingRoots.checkServerTrusted(chain, "RSA"); fail("Old store must reject this CA") }
        catch (_: CertificateException) { }
        manager().checkServerTrusted(chain, "RSA")
    }

    @Test fun rejectsAnUnrelatedIssuer() {
        reject(arrayOf(certificate("untrusted"), certificate("unrelated-root")))
    }

    @Test fun rejectsExpiredAndNotYetValidServerCertificates() {
        for (name in listOf("expired", "future"))
            reject(arrayOf(certificate(name), certificate("approved-root")))
    }

    @Test fun rejectsTamperedServerSignature() {
        val encoded = certificate("valid").encoded
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        val forged = CertificateFactory.getInstance("X.509").generateCertificate(encoded.inputStream()) as X509Certificate
        reject(arrayOf(forged, certificate("approved-root")))
    }

    @Test fun existingSystemTrustStillWorksAndClientTrustIsNotExpanded() {
        val system = LicenseTls.trustedRoots(listOf(certificate("unrelated-root")))
        LicenseTls.SupplementalTrustManager(system, missingRoots).checkServerTrusted(
            arrayOf(certificate("untrusted"), certificate("unrelated-root")), "RSA")
        try { manager().checkClientTrusted(arrayOf(certificate("valid")), "RSA"); fail("No new client trust") }
        catch (_: CertificateException) { }
    }

    @Test @Config(sdk = [23, 28, 33]) fun staleOemStoresAtAnyApiUseOnlyThisConnectionFactoryAndKeepHostnameVerification() {
        val defaults = HttpsURLConnection.getDefaultSSLSocketFactory()
        val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
        val connection = URL("https://license.example.test").openConnection() as HttpsURLConnection
        val other = URL("https://other.example.test").openConnection() as HttpsURLConnection
        LicenseTls.configure(RuntimeEnvironment.getApplication(), connection)
        assertNotSame(defaults, connection.sslSocketFactory)
        assertSame(verifier, connection.hostnameVerifier)
        assertSame(defaults, other.sslSocketFactory)
        assertSame(defaults, HttpsURLConnection.getDefaultSSLSocketFactory())
        assertSame(verifier, HttpsURLConnection.getDefaultHostnameVerifier())
    }

    @Test fun packagedAnchorsMatchTheOfficialIsrgCertificates() {
        val app = RuntimeEnvironment.getApplication()
        for ((id, expected) in listOf(
            R.raw.license_isrg_root_x1 to "96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6",
            R.raw.license_isrg_root_x2 to "69729b8e15a86efc177a57afb7171dfc64add28c2fca8cf1507e34453ccb1470",
            R.raw.license_isrg_root_ye to "e14ffcad5b0025731006caa43a121a22d8e9700f4fb9cf852f02a708aa5d5666",
        )) {
            val bytes = app.resources.openRawResource(id).use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(expected, digest)
            val certificate = CertificateFactory.getInstance("X.509").generateCertificate(bytes.inputStream()) as X509Certificate
            assertTrue(certificate.basicConstraints >= 0)
            certificate.verify(certificate.publicKey)
        }
    }

    @Test fun capturedProductionChainValidatesWithoutSystemRootsWhenProvided() {
        val path = System.getenv("DIPLAY_TLS_CHAIN")
        org.junit.Assume.assumeTrue("Provide a freshly captured public server chain", !path.isNullOrBlank())
        val parser = CertificateFactory.getInstance("X.509")
        val chain = java.io.File(path!!).inputStream().use { input ->
            parser.generateCertificates(input).map { it as X509Certificate }.toTypedArray()
        }
        val app = RuntimeEnvironment.getApplication()
        LicenseTls.SupplementalTrustManager(missingRoots, LicenseTls.trustedRoots(LicenseTls.packagedRoots(app)))
            .checkServerTrusted(chain, "ECDHE_ECDSA")
        // A short chain ending directly at the new YE anchor must work even with an empty system store.
        LicenseTls.SupplementalTrustManager(missingRoots, LicenseTls.trustedRoots(LicenseTls.packagedRoots(app)))
            .checkServerTrusted(chain.take(2).toTypedArray(), "ECDHE_ECDSA")
    }

    @Test fun certificateErrorsAreReadableAndApprovalFailuresKeepTheirMeaning() {
        val expired = SSLHandshakeException("handshake").apply { initCause(CertificateExpiredException()) }
        assertTrue(LicenseFailureMessage.describe(expired).contains("日期"))
        assertTrue(LicenseFailureMessage.describe(CertificateNotYetValidException()).contains("时区"))
        val untrusted = SSLHandshakeException("java.security.cert.CertPathValidatorException: Trust anchor")
        assertFalse(LicenseFailureMessage.describe(untrusted).contains("Exception"))
        assertTrue(LicenseFailureMessage.describe(untrusted).contains("安全证书"))
        assertEquals("授权未通过", LicenseFailureMessage.describe(java.io.IOException("授权未通过")))
    }

    @Test @Config(sdk = [23, 28]) fun liveLicenseChallengeWorksWithOnlyPackagedRootsWhenRequested() {
        val origin = System.getenv("DIPLAY_TLS_LIVE_ORIGIN")
        org.junit.Assume.assumeTrue("Opt in to a live license challenge", !origin.isNullOrBlank())
        val app = RuntimeEnvironment.getApplication()
        for (emptySystemStore in listOf(false, true)) {
            val connection = URL(origin!!.trimEnd('/') + "/v1/challenge").openConnection() as HttpsURLConnection
            LicenseTls.configure(app, connection)
            if (emptySystemStore) {
                val trust = LicenseTls.SupplementalTrustManager(missingRoots,
                    LicenseTls.trustedRoots(LicenseTls.packagedRoots(app)))
                connection.sslSocketFactory = javax.net.ssl.SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(trust), null)
                }.socketFactory
            }
            connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(2)
            try {
                connection.outputStream.use { it.write("{}".toByteArray()) }
                assertEquals(200, connection.responseCode)
                val reply = connection.inputStream.use { org.json.JSONObject(String(it.readBytes(), Charsets.UTF_8)) }
                assertTrue(reply.getString("id").matches(Regex("[0-9a-f]{48}")))
                assertTrue(reply.getString("nonce").matches(Regex("[0-9a-f]{64}")))
            } finally { connection.disconnect() }
        }
    }

    private fun reject(chain: Array<X509Certificate>) {
        try { manager().checkServerTrusted(chain, "RSA"); fail("Invalid chain accepted") }
        catch (_: CertificateException) { }
    }
}

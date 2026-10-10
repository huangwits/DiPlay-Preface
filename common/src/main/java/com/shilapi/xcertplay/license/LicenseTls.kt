package com.shilapi.xcertplay.license

import android.content.Context
import com.shilapi.xcertplay.host.R
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal object LicenseTls {
    @Volatile private var cachedFactory: SSLSocketFactory? = null

    fun configure(context: Context, connection: HttpsURLConnection) {
        connection.sslSocketFactory = socketFactory(context)
        // Keep HttpsURLConnection's hostname verifier; never change process-wide TLS defaults.
    }

    @Synchronized private fun socketFactory(context: Context): SSLSocketFactory {
        cachedFactory?.let { return it }
        val manager = SupplementalTrustManager(trustManager(null), trustedRoots(packagedRoots(context)))
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }
            .socketFactory.also { cachedFactory = it }
    }

    internal fun packagedRoots(context: Context): List<X509Certificate> {
        val parser = CertificateFactory.getInstance("X.509")
        // The Mozilla bundle covers normal public CA rotation; Root YE covers the current
        // Let's Encrypt generation used by the service. All entries remain public CA roots.
        val roots = mutableListOf<X509Certificate>()
        context.resources.openRawResource(R.raw.license_mozilla_roots).use { input ->
            roots += parser.generateCertificates(input).filterIsInstance<X509Certificate>()
        }
        listOf(R.raw.license_isrg_root_x1, R.raw.license_isrg_root_x2, R.raw.license_isrg_root_ye).forEach { id ->
            context.resources.openRawResource(id).use { roots += parser.generateCertificate(it) as X509Certificate }
        }
        return roots
    }

    internal fun trustedRoots(roots: List<X509Certificate>): X509TrustManager {
        require(roots.isNotEmpty())
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            roots.forEachIndexed { index, certificate ->
                require(certificate.basicConstraints >= 0)
                setCertificateEntry("license-ca-$index", certificate)
            }
        }
        return trustManager(store)
    }

    private fun trustManager(store: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            .trustManagers.filterIsInstance<X509TrustManager>().single()

    internal class SupplementalTrustManager(
        private val system: X509TrustManager,
        private val supplemental: X509TrustManager,
    ) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                system.checkServerTrusted(chain, authType)
            } catch (systemFailure: CertificateException) {
                try { supplemental.checkServerTrusted(chain, authType) }
                catch (failure: CertificateException) {
                    failure.addSuppressed(systemFailure)
                    throw failure
                }
            }
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            system.checkClientTrusted(chain, authType)

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            system.acceptedIssuers + supplemental.acceptedIssuers
    }
}

internal object LicenseFailureMessage {
    fun describe(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.take(12).toList()
        if (causes.any { it is CertificateExpiredException || it is CertificateNotYetValidException }) {
            return "无法验证授权服务器证书的有效期，请先检查车机日期、时间和时区是否正确，再刷新状态。"
        }
        if (causes.any { it is CertPathValidatorException || it is SSLHandshakeException || it is CertificateException }) {
            return "无法验证授权服务器的安全证书。请检查车机日期和网络，并升级到最新版 DiPlay 后刷新状态。"
        }
        return error.message ?: "授权校验失败，请检查网络后重试"
    }
}

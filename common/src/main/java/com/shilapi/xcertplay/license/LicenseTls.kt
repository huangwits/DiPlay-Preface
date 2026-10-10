package com.shilapi.xcertplay.license

import android.content.Context
import android.os.Build
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

/** Supplemental public CAs for the license connection on older OEM Android trust stores. */
internal object LicenseTls {
    @Volatile private var cachedFactory: SSLSocketFactory? = null

    fun configure(context: Context, connection: HttpsURLConnection) {
        if (Build.VERSION.SDK_INT > 25) return
        connection.sslSocketFactory = socketFactory(context)
        // Keep HttpsURLConnection's hostname verifier; never change process-wide TLS defaults.
    }

    @Synchronized private fun socketFactory(context: Context): SSLSocketFactory {
        cachedFactory?.let { return it }
        val parser = CertificateFactory.getInstance("X.509")
        val roots = listOf(R.raw.license_isrg_root_x1, R.raw.license_isrg_root_x2).map { id ->
            context.resources.openRawResource(id).use { parser.generateCertificate(it) as X509Certificate }
        }
        val manager = SupplementalTrustManager(trustManager(null), trustedRoots(roots))
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }
            .socketFactory.also { cachedFactory = it }
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

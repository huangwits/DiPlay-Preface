package com.shilapi.xcertplay.license

import android.content.Context
import android.content.pm.PackageManager
import android.security.KeyPairGeneratorSpec
import android.util.Base64
import com.shilapi.xcertplay.host.R
import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.util.Date
import javax.security.auth.x500.X500Principal

internal object OfflineLicense {
    private const val ALIAS = "diplay-offline-device-v1"
    private const val PREFS = "diplay-offline-license"
    const val CONTACT = "starts181004"
    fun enabled(context: Context) = context.resources.getBoolean(R.bool.config_offline_license)

    fun canStart(context: Context): Boolean {
        if (!enabled(context)) return true
        val token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", null) ?: return false
        return runCatching { verify(context, token); true }.getOrDefault(false)
    }

    fun deviceLabel(context: Context): String {
        settings(context)
        return "DP-DEVICE1-" + deviceId(context, create = true)
    }

    fun activate(context: Context, text: String) {
        check(enabled(context)) { "此版本无需激活" }
        val token = normalize(text)
        verify(context, token)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("token", token).commit()) {
            "无法保存激活状态，请重试"
        }
    }

    private fun normalize(text: String): String {
        require(text.length <= 4096) { "激活码过长" }
        return text.filterNot { it.isWhitespace() }.also {
            require(Regex("DP-ACT1\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").matches(it)) { "请输入完整激活码，或导入激活码文件" }
        }
    }

    private fun verify(context: Context, text: String) {
        val config = settings(context)
        val parts = normalize(text).split('.')
        OfflineLicenseProtocol.verify(decode(parts[1]), decode(parts[2]),
            Base64.decode(config.getString("publicKey"), Base64.DEFAULT), deviceId(context, create = false),
            context.packageName, config.getString("signer"))
    }

    private fun decode(text: String) = Base64.decode(text, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun settings(context: Context): JSONObject {
        val bytes = context.assets.open("offline-license/public.json").use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val size = stream.read(buffer); if (size < 0) break
                check(output.size() + size <= 4096) { "授权配置过大" }; output.write(buffer, 0, size)
            }
            output.toByteArray()
        }
        val config = JSONObject(String(bytes, Charsets.UTF_8))
        check(config.getInt("version") == 1 && config.getString("package") == context.packageName &&
            config.getString("contact") == CONTACT) { "授权配置无效" }
        @Suppress("DEPRECATION")
        val signatures = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        check(signatures != null && signatures.size == 1 &&
            LicenseProtocol.hash(signatures.single().toByteArray()) == config.getString("signer")) { "安装包签名不匹配" }
        return config
    }

    @Synchronized
    private fun deviceId(context: Context, create: Boolean): String {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            check(create) { "本机密钥已变化，请重新获取设备码和激活码" }
            @Suppress("DEPRECATION")
            val spec = KeyPairGeneratorSpec.Builder(context).setAlias(ALIAS).setKeyType("RSA").setKeySize(2048)
                .setSubject(X500Principal("CN=DiPlay offline installation")).setSerialNumber(BigInteger.ONE)
                .setStartDate(Date(0)).setEndDate(Date(4102444800000L)).build()
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
        }
        val entry = store.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: error("无法读取本机密钥")
        // A copied preferences file or certificate alone cannot prove possession of this key.
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(entry.privateKey); update(challenge); sign()
        }
        check(Signature.getInstance("SHA256withRSA").run {
            initVerify(entry.certificate.publicKey); update(challenge); verify(signature)
        }) { "本机密钥校验失败" }
        return LicenseProtocol.hash(entry.certificate.publicKey.encoded)
    }
}

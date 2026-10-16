package com.geelyplay.preface.license

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.os.SystemClock
import android.security.KeyPairGeneratorSpec
import android.util.Base64
import com.shilapi.xcertplay.host.R
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.Date
import javax.net.ssl.HttpsURLConnection
import javax.security.auth.x500.X500Principal

/** Grants only new-session admission. It never stops an active session or a recovery operation. */
object OnlineLicense {
    private const val ALIAS = "diplay-license-device-v1"
    @Volatile private var validUntilElapsed = 0L
    @Volatile private var lastPromptElapsed = -10000L

    fun enabled(context: Context): Boolean = context.resources.getBoolean(R.bool.config_online_license)
    fun canStart(context: Context): Boolean = !enabled(context) ||
        SystemClock.elapsedRealtime() < validUntilElapsed || hasSavedApproval(context)

    /** Old versions wrote activated only after verifying an issuer-signed approval.
     * The explicit decision takes precedence so a later denial survives restarts/upgrades.
     * Neither the car's clock nor a network outage expires this installation's approval.
     */
    internal fun hasSavedApproval(context: Context): Boolean {
        val prefs = context.getSharedPreferences("diplay-license", Context.MODE_PRIVATE)
        return prefs.getBoolean("authorized", prefs.getBoolean("activated", false))
    }

    internal fun renewalDue(): Boolean = validUntilElapsed - SystemClock.elapsedRealtime() <= 60_000L

    fun requireActivation(activity: Activity): Boolean {
        if (canStart(activity)) return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastPromptElapsed >= 1500 && !activity.isFinishing) {
            lastPromptElapsed = now
            activity.startActivity(Intent(activity, LicenseActivity::class.java))
        }
        return true
    }

    internal fun configured(context: Context): Boolean = runCatching { config(context).getString("url").isNotBlank() }.getOrDefault(false)

    private fun config(context: Context): JSONObject = context.assets.open("license/server.json").use {
        JSONObject(String(it.readBytes(), Charsets.UTF_8))
    }

    @Synchronized
    internal fun refresh(context: Context, requestActivation: Boolean = false): Result {
        check(Looper.myLooper() != Looper.getMainLooper()) { "授权校验须在后台执行" }
        check(enabled(context)) { "此版本未启用联网授权" }
        // Requests queued by another screen reuse a fresh, verified in-memory lease.
        if (!requestActivation && !renewalDue()) return Result(true, requestLabel(context))
        val settings = config(context)
        val origin = settings.getString("url")
        check(origin.isNotBlank()) { "这是授权预览版，服务地址尚未配置。" }
        val url = URL(origin)
        check(url.protocol == "https" && url.host.isNotEmpty() && url.userInfo == null && url.query == null && url.ref == null && url.path in listOf("", "/")) {
            "授权服务地址无效"
        }
        val packageName = context.packageName
        @Suppress("DEPRECATION")
        val signatures = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        check(signatures != null && signatures.size == 1) { "无法核验安装包签名" }
        val signer = LicenseProtocol.hash(signatures.single().toByteArray())
        check(packageName == settings.getString("package") && signer == settings.getString("signer")) { "安装包签名与授权版本不匹配" }
        val (publicBytes, privateKey) = deviceKey(context)
        val device = StableDeviceIdentity.id(context)
        val action = if (requestActivation) "request" else "refresh"
        val began = SystemClock.elapsedRealtime()
        try {
            val challenge = post(context, origin, "/v1/challenge", JSONObject())
            val id = challenge.getString("id")
            val proof = LicenseProtocol.deviceProof(action, id, challenge.getString("nonce"), device, publicBytes,
                packageName, signer)
            val signed = Signature.getInstance("SHA256withRSA").run { initSign(privateKey); update(proof); sign() }
            val request = JSONObject().put("challengeId", id).put("deviceId", device).put("identityVersion", 2)
                .put("publicKey", encode(publicBytes)).put("signature", encode(signed))
                .put("package", packageName).put("signer", signer)
            val response = post(context, origin, "/v1/$action", request)
            val requestId = response.getString("requestId")
            check(Regex("[0-9A-F]{12}").matches(requestId)) { "授权申请号无效" }
            if (response.optString("status") == "pending") {
                saveDecision(context, false, requestId, device)
                return Result(false, requestId)
            }
            check(response.optString("status") == "approved") { "授权状态无效" }
            val lease = LicenseProtocol.verify(decode(response.getString("payload")), decode(response.getString("signature")),
                decode(settings.getString("publicKey")), device, packageName, signer, id)
            // The nonce proves freshness; monotonic elapsed time avoids trusting a car's wall clock.
            val deadline = began + lease
            check(deadline > SystemClock.elapsedRealtime()) { "网络响应超时，请重新校验" }
            saveDecision(context, true, requestId, device)
            validUntilElapsed = deadline
            return Result(true, requestId)
        } catch (error: Exception) {
            refreshFailed(context, error)
            throw error
        }
    }

    internal class AdmissionDenied : IOException("授权未通过：请确认后台已批准且授权未过期或停用。")

    internal fun responseFailure(code: Int, path: String, reason: String?): IOException =
        if (code == 403 && path in listOf("/v1/request", "/v1/refresh") &&
            reason in listOf("license_unavailable", "request_required")) AdmissionDenied()
        else IOException("授权服务暂不可用（$code），请稍后重试")

    internal fun refreshFailed(context: Context, error: Exception) {
        // Only an explicit server refusal changes an existing approval. Outages, TLS errors,
        // malformed responses and invalid signatures cannot grant or revoke local admission.
        if (error is AdmissionDenied) saveDecision(context, false)
    }

    internal fun saveDecision(context: Context, approved: Boolean, requestId: String? = null, device: String? = null) {
        validUntilElapsed = 0
        val edit = context.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).edit()
            .putBoolean("authorized", approved)
        if (approved) edit.putBoolean("activated", true)
        if (requestId != null) edit.putBoolean("requested", true).putString("requestId", requestId)
        if (device != null) edit.putString("device", device)
        // Complete the durable write on the refresh worker before reporting approval.
        check(edit.commit()) { "无法保存授权状态，请重试" }
    }

    internal data class Result(val approved: Boolean, val requestId: String)
    internal fun wasRequested(context: Context): Boolean = context.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).getBoolean("requested", false)
    internal fun requestLabel(context: Context): String = context.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).getString("requestId", "尚未申请").orEmpty()
    internal fun wasActivated(context: Context): Boolean = context.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).getBoolean("activated", false)
    internal fun deviceLabel(context: Context): String = context.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).getString("device", "尚未激活").orEmpty()

    /** Reuses installation identity without requesting activation or changing admission. */
    internal fun diagnosticIdentity(context: Context): DiagnosticIdentity {
        val settings = config(context)
        val origin = settings.getString("url")
        val url = URL(origin)
        check(url.protocol == "https" && url.host.isNotEmpty() && url.userInfo == null &&
            url.query == null && url.ref == null && url.path in listOf("", "/")) { "报告服务地址无效" }
        @Suppress("DEPRECATION")
        val signatures = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        check(signatures != null && signatures.size == 1) { "无法核验安装包签名" }
        val signer = LicenseProtocol.hash(signatures.single().toByteArray())
        check(context.packageName == settings.getString("package") && signer == settings.getString("signer")) { "安装包签名不匹配" }
        val (publicKey, privateKey) = deviceKey(context)
        return DiagnosticIdentity(origin, context.packageName, signer, StableDeviceIdentity.id(context), publicKey, privateKey)
    }

    internal data class DiagnosticIdentity(val origin: String, val packageName: String, val signer: String,
        val deviceId: String, val publicKey: ByteArray, val privateKey: PrivateKey)

    @Synchronized
    private fun deviceKey(context: Context): Pair<ByteArray, PrivateKey> {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            @Suppress("DEPRECATION")
            val spec = KeyPairGeneratorSpec.Builder(context).setAlias(ALIAS).setKeyType("RSA").setKeySize(2048)
                .setSubject(X500Principal("CN=DiPlay installation")).setSerialNumber(BigInteger.ONE)
                .setStartDate(Date(0)).setEndDate(Date(4102444800000L)).build()
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
        }
        val entry = store.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: error("无法读取本机授权密钥")
        return entry.certificate.publicKey.encoded to entry.privateKey
    }

    private fun post(context: Context, origin: String, path: String, body: JSONObject): JSONObject {
        val connection = URL(origin.trimEnd('/') + path).openConnection() as HttpsURLConnection
        LicenseTls.configure(context, connection)
        connection.connectTimeout = 10000; connection.readTimeout = 10000
        connection.instanceFollowRedirects = false; connection.requestMethod = "POST"; connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(bytes.size)
        try {
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code != 200) {
                // 403 also covers an expired challenge or invalid device proof. Those are not
                // an administrator's license decision and must not erase a saved approval.
                val reason = runCatching {
                    connection.errorStream?.use { readResponse(it).optString("error") }
                }.getOrNull()
                throw responseFailure(code, path, reason)
            }
            return connection.inputStream.use(::readResponse)
        } finally { connection.disconnect() }
    }

    private fun readResponse(input: java.io.InputStream): JSONObject {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        while (true) {
            val count = input.read(buffer); if (count < 0) break
            check(result.size() + count <= 16384) { "授权响应过大" }
            result.write(buffer, 0, count)
        }
        return JSONObject(String(result.toByteArray(), Charsets.UTF_8))
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.DEFAULT)
}

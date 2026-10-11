package com.shilapi.xcertplay

import android.content.Context
import android.os.Looper
import android.util.Base64
import com.shilapi.xcertplay.license.LicenseProtocol
import com.shilapi.xcertplay.license.LicenseTls
import com.shilapi.xcertplay.license.OnlineLicense
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.security.Signature
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/** Explicit, private diagnostics for the configured Preface service; no activation side effects. */
internal object CloudDiagnosticUpload {
    const val MAX_DESCRIPTION_LENGTH = 2000
    const val MAX_REPORT_BYTES = 512 * 1024
    private const val PREFS = "diplay-cloud-report"
    private val uploading = AtomicBoolean(false)
    private val receiptPattern = Regex("DPR-[0-9A-F]{16}")
    private val sensitive = Regex("(?i)(pass(word|phrase)?|token|private.?key|certificate|pair.?record|ssid|body|payload|hex)\\s*[=:]|-----BEGIN|-----END")
    private val personal = Regex("(?i)(latitude|longitude|coordinates?|next.?road|road.?name|lyrics|artist|album|song.?title|(phone|device|peer|host)?name)\\s*[=:]")
    private val ip = Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b")
    private val email = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val opaque = Regex("[A-Za-z0-9+/]{80,}={0,2}")
    private val control = Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]")
    data class Receipt(val id: String, val expiresAt: Long)
    class Failure(message: String) : IOException(message)

    fun lastReceipt(context: Context): Receipt? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString("id", "").orEmpty()
        return if (receiptPattern.matches(id)) Receipt(id, prefs.getLong("expiresAt", 0)) else null
    }

    fun upload(context: Context, description: String, report: String): Receipt {
        check(Looper.myLooper() != Looper.getMainLooper())
        if (!uploading.compareAndSet(false, true)) throw Failure("已有报告正在上传，请稍后查看报告编号。")
        try {
            val identity = OnlineLicense.diagnosticIdentity(context)
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            val receipt = submit(identity, document(version, description, report)) { path, body -> post(context, identity.origin, path, body) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("id", receipt.id).putLong("expiresAt", receipt.expiresAt).commit()
            return receipt
        } finally { uploading.set(false) }
    }

    internal fun sanitize(value: String): String = value.replace("\r\n", "\n").replace('\r', '\n').lineSequence().map { line ->
        when {
            sensitive.containsMatchIn(line) -> "[filtered sensitive content]"
            personal.containsMatchIn(line) -> "[filtered personal content]"
            else -> DiagnosticRedactor.redact(line)?.replace(ip, "[ip]")?.replace(email, "[email]")
                ?.replace(opaque, "[opaque]")?.replace(control, "")
        }
    }.filterNotNull().joinToString("\n")

    internal fun boundedReport(value: String): String {
        val safe = sanitize(value)
        if (safe.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES) return safe
        // Preserve the device/display summary and the end of the newest own-app log.
        val lines = safe.split('\n')
        val head = mutableListOf<String>(); var used = 0; var index = 0
        while (index < lines.size) {
            val size = lines[index].toByteArray(Charsets.UTF_8).size + 1
            if (used + size > 32 * 1024) break
            head += lines[index++]; used += size
        }
        val marker = "\n--- Older log lines omitted for cloud upload; full report remains available locally ---\n"
        used += marker.toByteArray(Charsets.UTF_8).size
        val tail = java.util.ArrayDeque<String>()
        for (last in lines.lastIndex downTo index) {
            val size = lines[last].toByteArray(Charsets.UTF_8).size + 1
            if (used + size > MAX_REPORT_BYTES) break
            tail.addFirst(lines[last]); used += size
        }
        return head.joinToString("\n") + marker + tail.joinToString("\n")
    }

    internal fun document(version: String, description: String, report: String): String {
        require(Regex("\\d+(?:\\.\\d+){2,4}").matches(version) && version.length <= 40)
        require(description.isNotBlank() && description.length <= MAX_DESCRIPTION_LENGTH)
        return JSONObject().put("schema", 1).put("version", version)
            .put("description", sanitize(description).ifBlank { "[filtered description]" }.take(MAX_DESCRIPTION_LENGTH))
            .put("report", boundedReport(report).ifBlank { "[no diagnostic lines available]" }).toString()
    }

    internal fun submit(identity: OnlineLicense.DiagnosticIdentity, document: String,
        post: (String, JSONObject) -> JSONObject): Receipt {
        val challenge = post("/v1/challenge", JSONObject())
        val id = challenge.getString("id"); val nonce = challenge.getString("nonce")
        check(Regex("[0-9a-f]{48}").matches(id) && Regex("[0-9a-f]{64}").matches(nonce)) { "报告服务响应无效" }
        val device = LicenseProtocol.hash(identity.publicKey)
        val action = "report:" + LicenseProtocol.hash(document.toByteArray(Charsets.UTF_8))
        val proof = listOf("DP-LIC1", action, id, nonce, device, device, "", identity.packageName, identity.signer).joinToString("\n")
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(identity.privateKey); update(proof.toByteArray(Charsets.UTF_8)); sign()
        }
        val result = post("/v1/diagnostics", JSONObject().put("challengeId", id).put("deviceId", device)
            .put("publicKey", Base64.encodeToString(identity.publicKey, Base64.NO_WRAP))
            .put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))
            .put("package", identity.packageName).put("signer", identity.signer).put("document", document))
        val receipt = result.optString("receipt"); val expires = result.optLong("expiresAt", 0)
        check(result.optBoolean("ok") && receiptPattern.matches(receipt) && expires in 1..253402300799L) { "云端未返回有效报告编号，请重试。" }
        return Receipt(receipt, expires)
    }

    private fun post(context: Context, origin: String, path: String, body: JSONObject): JSONObject {
        val connection = URL(origin.trimEnd('/') + path).openConnection() as HttpsURLConnection
        LicenseTls.configure(context, connection)
        connection.connectTimeout = 15000; connection.readTimeout = 30000
        connection.instanceFollowRedirects = false; connection.requestMethod = "POST"; connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(bytes.size)
        try {
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code != 200) throw Failure(when (code) {
                429 -> "上传次数较多，请稍后重试（每台车机每天最多 5 份）。"
                401, 403 -> "报告身份校验失败，请检查车机时间和安装包后重试。"
                413 -> "报告过大，请保存到本机后分享。"
                else -> "报告服务暂不可用（$code），请稍后重试。"
            })
            val result = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(2048)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    check(result.size() + count <= 16384) { "报告服务响应过大" }
                    result.write(buffer, 0, count)
                }
            }
            return JSONObject(String(result.toByteArray(), Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}

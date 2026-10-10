package com.shilapi.xcertplay.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// Based on the upstream manual updater; bounded downloads and legacy HTTP APIs for API 22.
internal class UpdateCancellation {
    @Volatile private var cancelled = false
    private var active: HttpURLConnection? = null
    @Synchronized fun attach(connection: HttpURLConnection) { check(); active = connection }
    @Synchronized fun detach(connection: HttpURLConnection) { if (active === connection) active = null }
    @Synchronized fun cancel() { cancelled = true; active?.disconnect(); active = null }
    fun check() { if (cancelled || Thread.currentThread().isInterrupted) throw InterruptedIOException("操作已取消") }
}

internal class UpdateClient(private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    companion object {
        private val hosts = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
        fun trusted(url: URL): Boolean = url.protocol == "https" && url.host.lowercase() in hosts &&
            url.userInfo == null && (url.port == -1 || url.port == 443)
        fun sha256(file: File): String = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }

    private fun open(address: String, cancellation: UpdateCancellation): HttpURLConnection {
        var url = URL(address)
        repeat(6) {
            cancellation.check()
            if (!trusted(url)) throw IOException("更新地址不受信任，已停止下载。")
            val connection = connect(url)
            try {
                cancellation.attach(connection)
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "DiPlay-Preface-Updater")
                connection.setRequestProperty("Accept", if (url.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
                connection.setRequestProperty("Accept-Encoding", "identity")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: throw IOException("下载地址跳转失败")
                    url = URL(url, location)
                } else {
                    if (status != 200) throw IOException(if (status == 403 || status == 429)
                        "更新服务暂时限制访问，请稍后重试。" else "更新服务访问失败（$status），请稍后重试。")
                    return connection
                }
            } catch (failure: Throwable) {
                cancellation.detach(connection); connection.disconnect(); throw failure
            }
            cancellation.detach(connection); connection.disconnect()
        }
        throw IOException("更新地址跳转过多，请稍后重试。")
    }

    fun latest(cancellation: UpdateCancellation): UpdateRelease {
        val connection = open(UpdateCatalog.RELEASES_URL, cancellation)
        try {
            val data = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    cancellation.check()
                    val n = input.read(buffer); if (n < 0) break
                    if (data.size() + n > 4 * 1024 * 1024) throw IOException("更新信息过大")
                    data.write(buffer, 0, n)
                }
            }
            return UpdateCatalog.latest(data.toString("UTF-8"))
        } finally { cancellation.detach(connection); connection.disconnect() }
    }

    fun download(release: UpdateRelease, destination: File, cancellation: UpdateCancellation,
        progress: (Int) -> Unit) {
        val connection = open(release.apkUrl, cancellation)
        try {
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
            if (declared != null && declared != release.size) throw IOException("安装包大小不匹配，请重新检查更新。")
            var written = 0L
            var percent = -1
            connection.inputStream.use { input -> destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    cancellation.check()
                    val n = input.read(buffer); if (n < 0) break
                    written += n
                    if (written > release.size) throw IOException("安装包大小超过预期")
                    output.write(buffer, 0, n)
                    val next = (written * 100 / release.size).toInt()
                    if (next != percent) { percent = next; progress(next) }
                }
            } }
            cancellation.check()
            if (written != release.size || sha256(destination) != release.sha256)
                throw IOException("安装包下载不完整或校验失败，请重新下载。")
        } catch (failure: Throwable) {
            destination.delete(); throw failure
        } finally { cancellation.detach(connection); connection.disconnect() }
    }
}

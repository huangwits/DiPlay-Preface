package com.shilapi.xcertplay.license

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A foreground-only, bounded HTTP form receiver; the existing verifier is the only activation path. */
internal class PhoneActivationServer(
    address: InetAddress,
    private val device: String,
    private val contact: String,
    private val activate: (String) -> Unit,
    private val onActivated: () -> Unit = {},
    private val onClosed: () -> Unit = {},
    lifetimeMillis: Long = TimeUnit.MINUTES.toMillis(20),
) : Closeable {
    private val lock = Any()
    @Volatile private var closed = false
    private var activated = false
    private val clients = mutableSetOf<Socket>()
    private val server: ServerSocket
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(2),
        { job -> Thread(job, "diplay-activation-http").apply { isDaemon = true } })
    private val timer = Executors.newSingleThreadScheduledExecutor { job ->
        Thread(job, "diplay-activation-expiry").apply { isDaemon = true }
    }
    private val path = "/activate/${randomToken()}/"
    private val csrf = randomToken()
    private val authority: String
    val url: String
    val isClosed: Boolean get() = closed

    init {
        require(address is Inet4Address && (address.isSiteLocalAddress || address.isLoopbackAddress))
        require(Regex("DP-DEVICE1-[0-9a-fA-F]{64}").matches(device))
        require(lifetimeMillis in 1..TimeUnit.MINUTES.toMillis(20))
        server = ServerSocket(0, 4, address)
        authority = "${address.hostAddress}:${server.localPort}"
        url = "http://$authority$path"
        timer.schedule({ close() }, lifetimeMillis, TimeUnit.MILLISECONDS)
        Thread({ accept() }, "diplay-activation-listener").apply { isDaemon = true; start() }
    }

    private fun accept() {
        try {
            while (!closed) {
                val socket = server.accept()
                synchronized(lock) {
                    if (closed) { socket.close(); return }
                    clients += socket
                }
                try { workers.execute { serve(socket) } }
                catch (_: java.util.concurrent.RejectedExecutionException) { release(socket) }
            }
        } catch (_: java.io.IOException) {
            // Shutdown and network loss invalidate this QR instead of leaving a stale listener.
        } finally { close() }
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val input = BufferedInputStream(socket.getInputStream())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            var headerBytes = 0
            fun line(): String {
                val data = ByteArrayOutputStream()
                while (true) {
                    require(System.nanoTime() < deadline && ++headerBytes <= 8192)
                    val next = input.read()
                    require(next >= 0)
                    if (next == 10) break
                    require(next == 13 || next in 32..126)
                    data.write(next)
                }
                return String(data.toByteArray(), Charsets.US_ASCII).removeSuffix("\r")
            }
            val first = line().split(' ')
            require(first.size == 3 && first[2] in listOf("HTTP/1.0", "HTTP/1.1"))
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = line(); if (line.isEmpty()) break
                val colon = line.indexOf(':'); require(colon > 0)
                val name = line.substring(0, colon).lowercase(java.util.Locale.ROOT)
                require(Regex("[a-z0-9-]+").matches(name) && name !in headers)
                headers[name] = line.substring(colon + 1).trim()
            }
            if (headers["host"] != authority || headers.containsKey("transfer-encoding")) {
                respond(socket, 400, "请求无效，请重新扫描车机二维码。"); return
            }
            if (first[1] != path && first[1] != path + "copy.js") {
                respond(socket, 404, "入口不存在，请扫描车机当前二维码。"); return
            }
            if (closed) return
            if (first[0] == "GET") {
                if (first[1].endsWith("copy.js")) respond(socket, 200, PhoneActivationPage.copyScript, "application/javascript; charset=utf-8")
                else respond(socket, 200, page())
                return
            }
            if (first[0] != "POST" || first[1] != path) {
                respond(socket, 405, "请使用页面上的提交按钮。"); return
            }
            if ((headers["origin"] != null && headers["origin"] != "http://$authority") ||
                headers["sec-fetch-site"] in listOf("cross-site", "same-site") ||
                headers["content-type"]?.substringBefore(';')?.trim() != "application/x-www-form-urlencoded") {
                respond(socket, 403, "提交来源无效，请重新扫描车机二维码。"); return
            }
            val length = headers["content-length"]?.toIntOrNull()
            if (length == null || length !in 1..12500) {
                respond(socket, 413, "提交内容过长或格式无效。"); return
            }
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                require(System.nanoTime() < deadline)
                val count = input.read(bytes, offset, length - offset); require(count > 0); offset += count
            }
            val fields = mutableMapOf<String, String>()
            String(bytes, Charsets.UTF_8).split('&').forEach {
                val pair = it.split('=', limit = 2); require(pair.size == 2)
                val key = URLDecoder.decode(pair[0], "UTF-8")
                require(key !in fields)
                fields[key] = URLDecoder.decode(pair[1], "UTF-8")
            }
            if (fields.keys != setOf("csrf", "code") || fields["csrf"] != csrf) {
                respond(socket, 403, "入口已失效或提交无效，请重新扫码。"); return
            }
            val code = fields.getValue("code")
            if (code.length > 4096) { respond(socket, 413, "激活码过长。"); return }
            var newlyActivated = false
            val result = runCatching {
                synchronized(lock) {
                    check(!closed) { "入口已关闭，请重新扫码。" }
                    if (!activated) {
                        activate(code)
                        activated = true
                        newlyActivated = true
                    }
                }
            }
            if (newlyActivated) runCatching(onActivated)
            respond(socket, if (result.isSuccess) 200 else 422,
                page(result.exceptionOrNull()?.message?.take(240) ?: "永久授权已保存，无需再次输入激活码。"))
        } catch (_: Exception) {
            runCatching { respond(socket, 400, "请求未完成，请重新打开扫码页面后提交。") }
        } finally { release(socket) }
    }

    private fun page(message: String = "") = synchronized(lock) {
        PhoneActivationPage.html(path, csrf, device, contact, message, activated)
    }

    private fun respond(socket: Socket, status: Int, content: String, type: String = "text/html; charset=utf-8") {
        val body = content.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $status Result\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            // same-origin keeps the form's Origin intact while never leaking the QR URL to other sites.
            "Connection: close\r\nCache-Control: no-store\r\nReferrer-Policy: same-origin\r\n" +
            "X-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\n" +
            "Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'\r\n\r\n"
        socket.getOutputStream().apply { write(headers.toByteArray(Charsets.US_ASCII)); write(body); flush() }
    }

    private fun release(socket: Socket) {
        synchronized(lock) { clients -= socket }
        runCatching { socket.close() }
    }

    override fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            clients.toList().also { clients.clear() }
        }
        runCatching { server.close() }
        pending.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        timer.shutdownNow()
        runCatching(onClosed)
    }

    companion object {
        private fun randomToken() = ByteArray(24).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it.toInt() and 255) }

        /** Explicit Wi-Fi/hotspot/Ethernet addresses only; never advertise cellular or VPN interfaces. */
        fun localAddresses(): List<InetAddress> = Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { iface ->
            runCatching {
                if (!iface.isUp || iface.isLoopback || !localInterface(iface.name)) emptyList()
                else Collections.list(iface.inetAddresses).filter { it is Inet4Address && it.isSiteLocalAddress }
            }.getOrDefault(emptyList())
        }.distinctBy { it.hostAddress }

        internal fun localInterface(name: String) = listOf("wlan", "swlan", "wifi", "ap", "softap", "p2p", "eth", "en")
            .any { name.startsWith(it, ignoreCase = true) }
    }
}

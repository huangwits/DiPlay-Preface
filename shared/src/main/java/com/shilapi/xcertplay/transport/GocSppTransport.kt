package com.shilapi.xcertplay.transport

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.IOException
import java.util.Locale

internal interface GocSocketAccess : BluetoothRfcommSocketAccess {
    fun connect()
}

/** GOC's reserved local socket takes a 12-byte Bluetooth address before the iAP2 byte stream. */
object GocSppTransport {
    fun addressHeader(address: String): ByteArray {
        val normalized = address.uppercase(Locale.US)
        require(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}").matches(normalized)) { "Invalid Bluetooth address" }
        return normalized.replace(":", "").toByteArray(Charsets.US_ASCII)
    }

    fun connect(address: String, cancelled: () -> Boolean = { false },
                log: (String) -> Unit = {}): BlockingDuplexByteStream = connectSocket(address, cancelled, log, {
        object : GocSocketAccess {
            private val socket = LocalSocket()
            override fun connect() = socket.connect(LocalSocketAddress("goc_spp", LocalSocketAddress.Namespace.RESERVED))
            override fun inputStream() = socket.inputStream
            override fun outputStream() = socket.outputStream
            override fun close() = socket.close()
        }
    }, Thread::sleep)

    internal fun connectSocket(address: String, cancelled: () -> Boolean, log: (String) -> Unit,
                               create: () -> GocSocketAccess, pause: (Long) -> Unit): BlockingDuplexByteStream {
        val header = addressHeader(address)
        var failure: IOException? = null
        repeat(10) {
            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("GOC SPP cancelled")
            val socket = create()
            try {
                socket.connect()
            } catch (error: Throwable) {
                runCatching { socket.close() }
                if (error !is IOException) throw error
                failure = error
                pause(200)
                return@repeat
            }
            // Do not retry a partially sent header on the same connection.
            try {
                val output = socket.outputStream() ?: throw IOException("GOC SPP output unavailable")
                output.write(header)
                output.flush()
                if (cancelled()) throw IOException("GOC SPP cancelled")
                // Ownership transfers to the bounded stream; it cleans up any getter failures.
            } catch (error: Throwable) {
                runCatching { socket.close() }
                throw error
            }
            return attach(socket, log)
        }
        throw IOException("E01 原厂蓝牙通道未就绪，请先运行兼容测试或检查已安装组件", failure)
    }

    // Share the already-tested bounded reader, close/cancellation and backpressure implementation.
    internal fun attach(socket: BluetoothRfcommSocketAccess, log: (String) -> Unit = {}): BlockingDuplexByteStream =
        BluetoothRfcommDuplexStream(socket) { log(it.replace("Bluetooth RFCOMM", "GOC SPP")) }
}

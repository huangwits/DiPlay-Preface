package com.shilapi.xcertplay.transport

import android.content.Context
import java.io.IOException

enum class FactoryBluetoothBackend { ECARX }

interface ConnectingBluetoothStream : BlockingDuplexByteStream {
    fun connect(timeoutMillis: Long = 15_000)
}

interface PreparedVendorBluetooth {
    val phone: FactoryBluetoothPhone
    val localAddress: String
    fun stream(): ConnectingBluetoothStream
}

/** Explicit backend selection; never fall through to a different vendor after a connection attempt. */
object FactoryBluetoothTransport {
    @Volatile var diagnosticSummary = "Factory backend not queried"
        private set

    fun pairedPhones(context: Context, backend: FactoryBluetoothBackend): List<FactoryBluetoothPhone> {
        diagnosticSummary = "Selected backend=$backend; checking factory paired list"
        return try { EcarxSppTransport.pairedPhones(context) }
            finally { diagnosticSummary = "ECARX: ${EcarxSppTransport.diagnosticSummary}" }
    }

    fun prepare(context: Context, backend: FactoryBluetoothBackend, address: String?, log: (String) -> Unit): PreparedVendorBluetooth {
        return try { EcarxSppTransport.prepare(context, address, log) }
            finally { diagnosticSummary = "ECARX: ${EcarxSppTransport.diagnosticSummary}" }
    }

}

/** Registered with the controller before binding; closing also cancels a connection still in flight. */
internal class DeferredBluetoothStream(
    private val connector: (cancelled: () -> Boolean) -> BlockingDuplexByteStream,
) : ConnectingBluetoothStream {
    private val lock = Any()
    @Volatile private var closed = false
    private var started = false
    private var delegate: BlockingDuplexByteStream? = null

    override fun connect(timeoutMillis: Long) {
        synchronized(lock) {
            if (closed || started) throw IOException("Factory Bluetooth connection already started or closed")
            started = true
        }
        val connected = try { connector { closed } } catch (error: Throwable) {
            close()
            throw error
        }
        val accepted = synchronized(lock) {
            if (closed) false else { delegate = connected; true }
        }
        if (!accepted) {
            connected.close()
            throw IOException("Factory Bluetooth connection cancelled")
        }
    }

    private fun active(): BlockingDuplexByteStream = synchronized(lock) {
        if (closed) throw IOException("Factory Bluetooth stream closed")
        delegate ?: throw IOException("Factory Bluetooth stream not connected")
    }
    override fun send(data: ByteArray) = active().send(data)
    override fun recv(maxBytes: Int, timeoutMillis: Long): ByteArray? =
        if (closed) byteArrayOf() else active().recv(maxBytes, timeoutMillis)
    override fun close() {
        val stream = synchronized(lock) {
            closed = true
            delegate.also { delegate = null }
        }
        stream?.close()
    }
}

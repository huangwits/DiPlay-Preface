package com.shilapi.xcertplay.transport

import android.content.Context
import java.io.IOException
import java.util.UUID

enum class FactoryBluetoothBackend { ECARX, H52_ANW }

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
        return when (backend) {
            FactoryBluetoothBackend.ECARX -> try { EcarxSppTransport.pairedPhones(context) }
                finally { diagnosticSummary = "ECARX: ${EcarxSppTransport.diagnosticSummary}" }
            FactoryBluetoothBackend.H52_ANW -> anwCall("E01-H01") {
                AnwBluetoothBackend(context).pairedDevices().mapNotNull { device ->
                    normalizeFactoryAddress(device.address)?.let { FactoryBluetoothPhone(it, device.name) }
                }.also { diagnosticSummary = "H52 ANW descriptor and paired-list protocol matched; count=${it.size}; connectivity unverified" }
            }
        }
    }

    fun prepare(context: Context, backend: FactoryBluetoothBackend, address: String?, log: (String) -> Unit): PreparedVendorBluetooth {
        if (backend == FactoryBluetoothBackend.ECARX) {
            return try { EcarxSppTransport.prepare(context, address, log) }
                finally { diagnosticSummary = "ECARX: ${EcarxSppTransport.diagnosticSummary}" }
        }
        val selected = normalizeFactoryAddress(address)
            ?: throw FactoryBluetoothException("E01-H02", "Choose a phone from the H52 ANW paired list")
        val phone = pairedPhones(context, backend).singleOrNull { it.address == selected }
            ?: throw FactoryBluetoothException("E01-H02", "Selected phone is absent or ambiguous in H52 ANW paired list")
        val client = AnwBluetoothBackend(context)
        val local = anwCall("E01-H01") { normalizeFactoryAddress(client.localAddress()) }
            ?: throw FactoryBluetoothException("E01-H01", "H52 ANW local address is unavailable")
        log("factory Bluetooth: selected H52 ANW; matching service responded; iAP2 connectivity remains unverified")
        return object : PreparedVendorBluetooth {
            override val phone = phone
            override val localAddress = local
            override fun stream(): ConnectingBluetoothStream = DeferredBluetoothStream { cancelled ->
                anwCall("E01-H03") {
                    client.connect(phone.address, UUID.fromString("00000000-deca-fade-deca-deafdecacafe"), log, cancelled)
                }
            }
        }
    }

    private fun <T> anwCall(code: String, action: () -> T): T = try { action() } catch (error: Exception) {
        diagnosticSummary = "H52 ANW: ${error.javaClass.simpleName}: ${error.message.orEmpty().take(240)}"
        if (error is FactoryBluetoothException) throw error
        throw FactoryBluetoothException(code, "H52 ANW: ${error.message ?: error.javaClass.simpleName}", error)
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

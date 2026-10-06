package com.shilapi.xcertplay.transport

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import dalvik.system.PathClassLoader
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class FactoryBluetoothException(val code: String, detail: String, cause: Throwable? = null) :
    IOException("[$code] $detail", cause)

data class FactoryBluetoothPhone(val address: String, val name: String)

/** An experimental client of the installed SDK, never a replacement firmware or guessed Binder contract. */
object EcarxSppTransport {
    private const val BT = "com.ecarx.xui.adaptapi.bt.Bt"
    private val worker by lazy { HandlerThread("e01-factory-bluetooth").apply { start() } }
    private val handler by lazy { Handler(worker.looper) }
    internal val calls = FactorySdkCalls { handler.post(it) }
    private var installedBt: Endpoint? = null // Accessed only on the SDK worker.
    @Volatile var diagnosticSummary: String = "ECARX SDK not queried"
        private set

    fun pairedPhones(context: Context): List<FactoryBluetoothPhone> = settings(context).phones()

    fun prepare(context: Context, address: String?, log: (String) -> Unit): PreparedFactoryBluetooth {
        log("factory Bluetooth: loading installed ECARX SDK")
        val settings = settings(context)
        val phones = settings.phones()
        val selected = normalizeFactoryAddress(address)
            ?: throw FactoryBluetoothException("E01-F04", "Choose an iPhone from factory paired devices")
        val phone = phones.singleOrNull { it.address == selected }
            ?: throw FactoryBluetoothException("E01-F04", "Selected iPhone is not in the factory paired list")
        val local = normalizeFactoryAddress(settings.call("getBtLocalAddress") as? String)
            ?: throw FactoryBluetoothException("E01-F03", "Factory Bluetooth did not provide its local address")
        val spp = awaitEndpoint(context, "getSpp", "E01-F02")
        val api = calls.call("E01-F02") { ReflectiveSppApi(spp) }
        log("factory Bluetooth: SDK data methods available; vendor selects SPP service (no UUID argument)")
        return PreparedFactoryBluetooth(phone, local, api, calls, log)
    }

    private fun settings(context: Context): Endpoint {
        val endpoint = awaitEndpoint(context, "getBtSettings", "E01-F03")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (endpoint.call("isBluetoothServiceReady") != true) {
            if (System.nanoTime() >= deadline) throw FactoryBluetoothException("E01-F03", "Factory Bluetooth service is not ready")
            Thread.sleep(100)
        }
        if (endpoint.call("isBtEnabled") != true) throw FactoryBluetoothException("E01-F03", "Turn on Bluetooth in the factory phone application")
        return endpoint
    }

    private fun awaitEndpoint(context: Context, getter: String, code: String): Endpoint {
        val app = context.applicationContext
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (true) {
            val endpoint = calls.call(code) {
                val bt = installedBt ?: try {
                    loadBt(app).also { installedBt = it }
                } catch (error: Exception) {
                    diagnosticSummary = "Bt SDK load failed: ${error.javaClass.simpleName}"
                    throw FactoryBluetoothException("E01-F01", "Installed ECARX SDK could not be loaded: ${error.javaClass.simpleName}", error)
                } catch (error: LinkageError) {
                    diagnosticSummary = "Bt SDK linkage failed: ${error.javaClass.simpleName}"
                    throw FactoryBluetoothException("E01-F01", "Installed ECARX SDK linkage failed", error)
                }
                val method = bt.type.getMethod(getter)
                method.invoke(bt.value)?.let {
                    diagnosticSummary = "${bt.type.name}.$getter: ${method.returnType.name}; methods=" +
                        method.returnType.methods.map { method -> method.name }.distinct().sorted().take(40).joinToString(",")
                    Endpoint(method.returnType, it, code)
                }
            }
            if (endpoint != null) return endpoint
            if (System.nanoTime() >= deadline) throw FactoryBluetoothException(code, "Installed ECARX $getter returned no interface")
            Thread.sleep(100)
        }
    }

    private fun loadBt(context: Context): Endpoint {
        val type = try {
            Class.forName(BT, true, context.classLoader)
        } catch (_: ClassNotFoundException) {
            // These are the system framework paths recorded in this vehicle's capture.
            val paths = listOf("/system/framework/ecarx-adapter.jar", "/system/framework/ecarx.openapi.impl.jar")
                .filter { File(it).isFile }
            if (paths.isEmpty()) throw FactoryBluetoothException("E01-F01", "Installed ECARX SDK was not found")
            Class.forName(BT, true, PathClassLoader(paths.joinToString(File.pathSeparator), context.classLoader))
        }
        val value = type.getMethod("create", Context::class.java).invoke(null, context)
            ?: throw FactoryBluetoothException("E01-F01", "ECARX Bt.create returned null")
        if (!type.isInstance(value)) throw FactoryBluetoothException("E01-F01", "Unexpected ECARX SDK instance")
        diagnosticSummary = "${type.name}; getters=" + type.methods.filter { it.name.startsWith("get") }
            .map { it.name }.distinct().sorted().take(30).joinToString(",")
        return Endpoint(type, value, "E01-F01")
    }

    internal class Endpoint(val type: Class<*>, val value: Any, private val code: String) {
        fun call(name: String): Any? = calls.call(code) { type.getMethod(name).invoke(value) }
        fun phones(): List<FactoryBluetoothPhone> = calls.call("E01-F04") {
            val devices = type.getMethod("reqBtPairedDevices").invoke(value) as? List<*>
                ?: throw FactoryBluetoothException("E01-F04", "Factory paired-device list is unavailable")
            devices.mapNotNull { item ->
                if (item == null) return@mapNotNull null
                val address = normalizeFactoryAddress(item.javaClass.getMethod("getAddress").invoke(item) as? String)
                    ?: return@mapNotNull null
                val name = item.javaClass.getMethod("getName").invoke(item) as? String
                FactoryBluetoothPhone(address, name?.takeIf { it.isNotBlank() } ?: "iPhone")
            }.distinctBy { it.address }.sortedBy { it.name }
        }
    }
}

internal fun normalizeFactoryAddress(value: String?): String? = value?.uppercase(Locale.US)
    ?.takeIf { it.matches(Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")) }
    ?.takeUnless { it in setOf("00:00:00:00:00:00", "02:00:00:00:00:00", "FF:FF:FF:FF:FF:FF") }

/** One worker per process; a stuck vendor call prevents new calls, while late cleanup remains queued. */
internal class FactorySdkCalls(private val post: (Runnable) -> Boolean) {
    private val stalled = AtomicBoolean(false)
    fun <T> call(code: String, timeoutMillis: Long = 2_000, action: () -> T): T {
        if (stalled.get()) throw FactoryBluetoothException("E01-F09", "Factory SDK is unresponsive; restart DiPlay before retrying")
        val task = FutureTask(action)
        if (!post(task)) throw FactoryBluetoothException(code, "Factory SDK worker is unavailable")
        return try {
            task.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            stalled.set(true)
            // Do not pretend cancellation can interrupt a Binder driver. Cleanup follows the real task.
            throw FactoryBluetoothException("E01-F09", "Factory SDK call timed out; restart DiPlay before retrying", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw FactoryBluetoothException("E01-F09", "Factory SDK wait was interrupted", error)
        } catch (error: ExecutionException) {
            var cause = error.cause ?: error
            while (cause is InvocationTargetException && cause.cause != null) cause = cause.cause!!
            if (cause is FactoryBluetoothException) throw cause
            throw FactoryBluetoothException(code, "Factory SDK ${cause.javaClass.simpleName}", cause)
        }
    }

    fun cleanup(action: () -> Unit) {
        post(Runnable { try { action() } catch (_: Exception) { /* No blocking retry during teardown. */ } })
    }
}

internal interface FactorySppApi {
    fun ready(): Boolean
    fun connected(address: String): Boolean
    fun register(callback: (String, Array<out Any?>) -> Unit): Boolean
    fun unregister()
    fun connect(address: String): Boolean
    fun disconnect(address: String)
    fun send(address: String, data: ByteArray)
}

internal class ReflectiveSppApi(
    endpoint: EcarxSppTransport.Endpoint,
) : FactorySppApi {
    private val type = endpoint.type
    private val value = endpoint.value
    private val ready = type.getMethod("isSppServiceReady")
    private val connected = type.getMethod("isSppConnected", String::class.java)
    private val connect = type.getMethod("reqSppConnect", String::class.java)
    private val disconnect = type.getMethod("reqSppDisconnect", String::class.java)
    private val send = type.getMethod("reqSppSendData", String::class.java, ByteArray::class.java)
    private val register = type.methods.single { it.name == "registerSppCallback" && it.parameterTypes.size == 1 }
    private val callbackType = register.parameterTypes.single()
    private val unregister = type.getMethod("unregisterSppCallback", callbackType)
    private var callback: Any? = null

    init {
        require(callbackType.isInterface && !android.os.IInterface::class.java.isAssignableFrom(callbackType)) {
            "ECARX SPP callback is not the supported SDK listener interface"
        }
        callbackType.getMethod("onSppDataReceived", String::class.java, ByteArray::class.java)
        callbackType.getMethod("onSppStateChanged", String::class.java, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        require(callbackType.methods.all { it.returnType == Void.TYPE }) { "Unexpected SDK listener return type" }
        for (method in listOf(ready, connected, connect, register)) {
            require(method.returnType == Boolean::class.javaPrimitiveType) { "Unexpected SDK return type: ${method.name}" }
        }
        require(send.returnType == Void.TYPE) { "Unexpected SDK send contract" }
    }

    override fun ready() = ready.invoke(value) == true
    override fun connected(address: String) = connected.invoke(value, address) == true
    override fun connect(address: String) = connect.invoke(value, address) == true
    override fun disconnect(address: String) { disconnect.invoke(value, address) }
    override fun send(address: String, data: ByteArray) { send.invoke(value, address, data) }
    override fun register(callback: (String, Array<out Any?>) -> Unit): Boolean {
        val listener = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "DiPlayEcarxSppCallback"
                else -> { callback(method.name, args ?: emptyArray()); null }
            }
        }
        this.callback = listener // Retain for unregister even if registration throws after taking effect.
        return register.invoke(value, listener) == true
    }
    override fun unregister() { callback?.let { unregister.invoke(value, it) }; callback = null }
}

class PreparedFactoryBluetooth internal constructor(
    override val phone: FactoryBluetoothPhone,
    override val localAddress: String,
    private val api: FactorySppApi,
    private val calls: FactorySdkCalls,
    private val log: (String) -> Unit,
) : PreparedVendorBluetooth {
    override fun stream(): FactorySppDuplexStream = FactorySppDuplexStream(phone.address, api, calls, log)
}

/** Bounded binary callback transport. State integers are logged, never guessed to mean connected. */
class FactorySppDuplexStream internal constructor(
    private val address: String,
    private val api: FactorySppApi,
    private val calls: FactorySdkCalls,
    private val log: (String) -> Unit = {},
) : ConnectingBluetoothStream {
    private val lock = Object()
    private val sendLock = Any()
    private val pending = ArrayDeque<ByteArray>()
    private var pendingBytes = 0
    @Volatile private var closed = false
    @Volatile private var verified = false
    private var registrationAttempted = false // SDK worker only.
    private var requested = false // SDK worker only.
    private var failure: FactoryBluetoothException? = null

    override fun connect(timeoutMillis: Long) {
        try {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (!call("E01-F02") { api.ready() }) {
                if (System.nanoTime() >= deadline) throw FactoryBluetoothException("E01-F02", "Factory SPP service is not ready")
                Thread.sleep(100)
            }
            if (call("E01-F05") { api.connected(address) }) {
                throw FactoryBluetoothException("E01-F05", "Selected phone already has a factory SPP session; cannot take ownership")
            }
            if (!call("E01-F05") { registrationAttempted = true; api.register(::callback) }) {
                throw FactoryBluetoothException("E01-F05", "Factory SPP callback registration was refused")
            }
            if (!call("E01-F06") { requested = true; api.connect(address) }) {
                throw FactoryBluetoothException("E01-F06", "Factory SPP connection request was refused")
            }
            while (!call("E01-F06") { api.connected(address) }) {
                if (System.nanoTime() >= deadline) throw FactoryBluetoothException("E01-F06", "Factory SPP did not connect before timeout")
                Thread.sleep(100)
            }
            checkOpen()
            verified = true
            log("factory SPP connected: verified by isSppConnected; iAP2 not yet verified")
        } catch (error: Exception) {
            close()
            if (error is InterruptedException) Thread.currentThread().interrupt()
            throw error
        }
    }

    override fun send(data: ByteArray) = synchronized(sendLock) {
        checkOpen()
        if (!verified) throw FactoryBluetoothException("E01-F07", "Factory SPP is not connected")
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + 1_024, data.size)
            val chunk = data.copyOfRange(offset, end)
            call("E01-F07") { api.send(address, chunk) }
            offset = end
        }
    }

    override fun recv(maxBytes: Int, timeoutMillis: Long): ByteArray? {
        require(maxBytes > 0 && timeoutMillis >= 0)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        synchronized(lock) {
            while (true) {
                failure?.let { throw it }
                val chunk = pending.pollFirst()
                if (chunk != null) {
                    pendingBytes -= chunk.size
                    if (chunk.size <= maxBytes) return chunk
                    val tail = chunk.copyOfRange(maxBytes, chunk.size)
                    pending.addFirst(tail); pendingBytes += tail.size
                    return chunk.copyOf(maxBytes)
                }
                if (closed) return ByteArray(0)
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) return null
                try { lock.wait(remaining / 1_000_000, (remaining % 1_000_000).toInt()) }
                catch (_: InterruptedException) { Thread.currentThread().interrupt(); return null }
            }
        }
    }

    private fun callback(name: String, args: Array<out Any?>) {
        if (closed) return
        val peer = normalizeFactoryAddress(args.firstOrNull() as? String)
        if (peer != address) return
        when (name) {
            "onSppDataReceived" -> {
                val data = args.getOrNull(1) as? ByteArray ?: return
                synchronized(lock) {
                    if (closed || data.isEmpty()) return
                    if (data.size > 262_144 - pendingBytes) {
                        failure = FactoryBluetoothException("E01-F07", "Factory SPP receive buffer overflow")
                    } else {
                        pending.addLast(data.copyOf()); pendingBytes += data.size
                    }
                    lock.notifyAll()
                }
            }
            "onSppErrorResponse" -> fail("Factory SPP error callback code=${args.getOrNull(1)}")
            "onSppStateChanged" -> {
                log("factory SPP state callback previous=${args.getOrNull(2)} current=${args.getOrNull(3)}")
                if (verified) calls.cleanup {
                    if (!closed) try {
                        if (!api.connected(address)) fail("Factory SPP disconnected")
                    } catch (_: Exception) { fail("Factory SPP state query failed") }
                }
            }
            "onSppSendData" -> log("factory SPP send callback code=${args.getOrNull(1)} (vendor semantics unverified)")
            "onSppAppleIapAuthenticationRequest" -> log("factory SPP Apple authentication event; iAP2 support remains unverified")
        }
    }

    private fun fail(detail: String) = synchronized(lock) {
        if (!closed && failure == null) failure = FactoryBluetoothException("E01-F07", detail)
        lock.notifyAll()
    }

    private fun checkOpen() = synchronized(lock) {
        failure?.let { throw it }
        if (closed) throw FactoryBluetoothException("E01-F07", "Factory SPP stream is closed")
    }

    private fun <T> call(code: String, action: () -> T): T = calls.call(code) { checkOpen(); action() }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            pending.clear(); pendingBytes = 0
            lock.notifyAll()
        }
        // This runs after an in-flight connect/register even if the caller's bounded wait expired.
        calls.cleanup {
            try { if (requested) api.disconnect(address) }
            finally { if (registrationAttempted) api.unregister() }
        }
    }
}

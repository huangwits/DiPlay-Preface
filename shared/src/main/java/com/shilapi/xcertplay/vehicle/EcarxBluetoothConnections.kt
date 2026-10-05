package com.shilapi.xcertplay.vehicle

import android.os.IBinder
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Read-only nFore/ECARX connection evidence. Never treats the vendor radio as an RFCOMM adapter. */
internal object EcarxBluetoothConnections {
    private const val DESCRIPTOR = "com.nforetek.bt.aidl.UiCommand"
    private val query = BoundedBluetoothQuery { readAddresses() }

    fun connectedAddresses(): Set<String> = query.snapshot()

    private fun readAddresses(): Set<String> {
        val manager = Class.forName("android.os.ServiceManager")
        val binder = manager.getMethod("getService", String::class.java)
            .invoke(null, "ecarx_btuiservice") as? IBinder ?: return emptySet()
        if (binder.interfaceDescriptor != DESCRIPTOR) return emptySet()
        val api = Class.forName(DESCRIPTOR)
        val stub = Class.forName(DESCRIPTOR + "\$Stub")
        val remote = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        if (!api.isInstance(remote)) return emptySet()
        return buildSet {
            for (profile in listOf("A2dp", "Hfp")) {
                // Some firmware exposes only one of the profiles.
                runCatching {
                    if (api.getMethod("is${profile}Connected").invoke(remote) == true) {
                        val address = api.getMethod("get${profile}ConnectedAddress").invoke(remote) as? String
                        normalizeBluetoothAddress(address)?.let { add(it) }
                    }
                }
            }
        }
    }
}

internal fun normalizeBluetoothAddress(address: String?): String? = address
    ?.takeIf { it.matches(Regex("(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")) }
    ?.uppercase(Locale.US)
    ?.takeUnless { it == "00:00:00:00:00:00" || it == "02:00:00:00:00:00" || it == "FF:FF:FF:FF:FF:FF" }

/** At most one outstanding Binder query, even if the driver ignores interruption forever. */
internal class BoundedBluetoothQuery(
    private val waitMillis: Long = 250,
    private val read: () -> Set<String>,
) {
    private val lock = Any()
    private var inFlight: FutureTask<Set<String>>? = null

    init { require(waitMillis > 0) }

    fun snapshot(): Set<String> {
        val task = synchronized(lock) {
            // Do not consume late evidence from an earlier connection attempt.
            if (inFlight?.isDone == true) inFlight = null
            inFlight ?: FutureTask { read() }.also {
                inFlight = it
                Thread(it, "ecarx-bluetooth-query").apply { isDaemon = true; start() }
            }
        }
        return try {
            task.get(waitMillis, TimeUnit.MILLISECONDS)
                .mapNotNull(::normalizeBluetoothAddress).toSet()
        } catch (_: TimeoutException) {
            emptySet()
        } catch (_: ExecutionException) {
            emptySet()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            emptySet()
        } finally {
            synchronized(lock) {
                if (task.isDone && inFlight === task) inFlight = null
            }
        }
    }
}

/** Vendor evidence may select only a currently bonded device; ambiguity must remain explicit. */
internal fun vendorConnectedBondedAddresses(bonded: Collection<String>, connected: Set<String>): Set<String> {
    val normalized = connected.mapNotNull(::normalizeBluetoothAddress).toSet()
    return bonded.mapNotNull(::normalizeBluetoothAddress).filterTo(mutableSetOf()) { it in normalized }
}

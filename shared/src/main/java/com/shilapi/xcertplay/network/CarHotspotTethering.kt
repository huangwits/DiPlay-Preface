package com.shilapi.xcertplay.network

import com.geelyplay.preface.compat.systemService
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Build
import android.os.ResultReceiver
import android.provider.Settings
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import com.geelyplay.preface.network.GkuiLegacySoftAp

/** Uses the car's saved hotspot configuration. This never stops or reconfigures the hotspot. */
object CarHotspotTethering {
    private val startupLock = ReentrantLock()
    enum class Result(val diagnostic: String) {
        READY("Car hotspot is on"),
        PERMISSION_REQUIRED("Hotspot control permission is missing"),
        UNSUPPORTED("This firmware does not support automatic hotspot startup"),
        NOT_ELIGIBLE("Hotspot auto-start is not eligible for this launch"),
        FAILED("The car could not start its hotspot"),
        TIMED_OUT("Timed out waiting for the car hotspot"),
        CANCELLED("Hotspot startup was cancelled"),
    }

    fun permitted(context: Context): Boolean = com.geelyplay.preface.compat.ContextCompat.canWriteSettings(context)

    fun gkuiLegacySoftApSupported(context: Context): Boolean = GkuiLegacySoftAp.supported(context)

    /** Android 8.0–10 support the app-owned tethering path without an ADB shell. */
    fun appStartSupported(): Boolean = PlatformTetheringStart.supported()

    /** Blocking; serialize startup and connection requests, checking cancellation after acquiring the lock. */
    fun enable(
        context: Context,
        isCancelled: () -> Boolean,
        timeoutMillis: Long = WirelessStartupPolicy.HOTSPOT_READY_MILLIS,
        log: (String) -> Unit,
    ): Result {
        val gkuiLegacySoftAp = GkuiLegacySoftAp.supported(context)
        // carlito | A separately managed factory AP must not be replaced by Android tethering
        // or an ADB fallback. The existing manual manager reads its state and verifies its route.
        if (!gkuiLegacySoftAp && EcarxHotspotReader.available(context) &&
            CarHotspotStatus.androidEnabled(context) != true) {
            return (if (isCancelled()) Result.CANCELLED else Result.UNSUPPORTED).also {
                log("car hotspot auto-enable: factory AP remains owned by the car settings")
            }
        }
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        val observedStartedState = AtomicReference<Boolean?>()
        val readHotspotState = {
            if (gkuiLegacySoftAp) CarHotspotStatus.androidEnabled(context)
            else CarHotspotStatus.isEnabled(context)
        }
        val startReflection: (ResultReceiver) -> Unit = { receiver ->
            if (gkuiLegacySoftAp) {
                val wifi = context.systemService(WifiManager::class.java, "wifi")
                    ?: throw NoSuchMethodException("Wi-Fi service unavailable")
                val remaining = ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(1L)
                if (!GkuiLegacySoftAp.start(
                        wifi, remaining, isCancelled, { CarHotspotStatus.androidEnabled(context) }, log,
                    )) throw LegacyHotspotRejectedException()
                observedStartedState.set(true)
            } else if (Build.VERSION.SDK_INT < 26) {
                val wifi = context.systemService(WifiManager::class.java, "wifi")
                    ?: throw NoSuchMethodException("Wi-Fi service unavailable")
                // Some Android 5.1 vendor services return false even when their
                // advertised tethering command can start the saved AP.
                if (!startLegacyHotspot(wifi)) throw LegacyHotspotRejectedException()
            } else {
                PlatformTetheringStart.start(context, receiver, log)
            }
        }
        val startAdb: () -> Boolean = {
            val client = AtomicReference<LocalAdb?>()
            CarHotspotAdbFallback.bounded(deadline, isCancelled,
                abort = { client.get()?.cancelPendingOperations() }) {
                val adb = LocalAdb(AdbKeys.load(context))
                client.set(adb)
                adb.use {
                    if (isCancelled() || Thread.currentThread().isInterrupted || System.nanoTime() >= deadline) false
                    else if (adb.connect(mayAsk = false) != LocalAdb.Access.READY) false
                    else CarHotspotAdbFallback.start(deadline, isCancelled,
                        state = readHotspotState, shell = adb::shell, log = log)
                        .also { if (it) observedStartedState.set(true) }
                }
            }
        }
        return enableUntil(
            deadline,
            isCancelled,
            { gkuiLegacySoftAp || permitted(context) },
            stateWithAdbObservation(readHotspotState, observedStartedState),
            startFallback = startAdb,
            start = startReflection,
            startWhenUnknown = canAttemptDirectStartBeforeAdb(
                sdkInt = Build.VERSION.SDK_INT,
                gkuiLegacySoftAp = gkuiLegacySoftAp,
            ),
        ).also { log("car hotspot auto-enable: ${it.diagnostic}") }
    }

    // Generic Android 5.1 fallback for firmware without the GKUI saved-configuration API.
    internal fun startLegacyHotspot(wifi: Any): Boolean = wifi.javaClass.getMethod(
        "setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType,
    ).invoke(wifi, null, true) == true

    internal class LegacyHotspotRejectedException : Exception()

    internal fun canAttemptDirectStartBeforeAdb(sdkInt: Int, gkuiLegacySoftAp: Boolean): Boolean =
        gkuiLegacySoftAp || sdkInt < Build.VERSION_CODES.O || PlatformTetheringStart.supported(sdkInt)

    /** An ADB observation supplements hidden platform status, but never overrides a current off state. */
    internal fun stateWithAdbObservation(
        platformState: () -> Boolean?,
        observation: AtomicReference<Boolean?>,
    ): () -> Boolean? = { platformState() ?: observation.get() }

    internal fun enable(
        timeoutMillis: Long,
        isCancelled: () -> Boolean,
        canWrite: () -> Boolean,
        isEnabled: () -> Boolean?,
        start: (ResultReceiver) -> Unit,
    ): Result = enable(timeoutMillis, isCancelled, canWrite, isEnabled, null, start)

    internal fun enable(
        timeoutMillis: Long,
        isCancelled: () -> Boolean,
        canWrite: () -> Boolean,
        isEnabled: () -> Boolean?,
        startFallback: (() -> Boolean)?,
        start: (ResultReceiver) -> Unit,
        startWhenUnknown: Boolean = false,
    ): Result = enableUntil(System.nanoTime() + timeoutMillis * 1_000_000L,
        isCancelled, canWrite, isEnabled, startFallback, start, startWhenUnknown)

    private fun enableUntil(
        deadline: Long,
        isCancelled: () -> Boolean,
        canWrite: () -> Boolean,
        isEnabled: () -> Boolean?,
        startFallback: (() -> Boolean)?,
        start: (ResultReceiver) -> Unit,
        startWhenUnknown: Boolean = false,
    ): Result {
        fun stopped(): Result? = when {
            isCancelled() || Thread.currentThread().isInterrupted -> Result.CANCELLED
            System.nanoTime() >= deadline -> Result.TIMED_OUT
            else -> null
        }
        while (true) {
            stopped()?.let { return it }
            val remaining = (deadline - System.nanoTime()) / 1_000_000L
            if (remaining <= 0) return Result.TIMED_OUT
            try {
                if (startupLock.tryLock(minOf(250L, remaining), TimeUnit.MILLISECONDS)) break
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Result.CANCELLED
            }
        }
        try {
            stopped()?.let { return it }
            val initialState = isEnabled()
            stopped()?.let { return it }
            if (initialState == true) return Result.READY
            val permission = canWrite()
            stopped()?.let { return it }
            if (!permission) return Result.PERMISSION_REQUIRED
            fun fallback(): Boolean {
                stopped()?.let { return false }
                return try { startFallback?.invoke() == true } catch (error: Exception) {
                    if (error is InterruptedException) Thread.currentThread().interrupt()
                    false
                }
            }
            val unknown = initialState == null
            if (unknown && !startWhenUnknown) {
                if (!fallback()) return stopped() ?: Result.UNSUPPORTED
            }
            val response = AtomicInteger(-1)
            try {
                stopped()?.let { return it }
                if (!unknown || startWhenUnknown) {
                    start(object : ResultReceiver(null) {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            response.set(resultCode)
                        }
                    })
                }
            } catch (error: Exception) {
                val adbRecovered = ((error is LegacyHotspotRejectedException) ||
                    (error is ReflectiveOperationException && error !is InvocationTargetException) ||
                    error is SecurityException ||
                    (error is InvocationTargetException && error.targetException is SecurityException)) &&
                    fallback()
                if (!adbRecovered) {
                    stopped()?.let { return it }
                    return when {
                        error is InvocationTargetException -> if (error.targetException is SecurityException) Result.PERMISSION_REQUIRED else Result.FAILED
                        error is ReflectiveOperationException -> Result.UNSUPPORTED
                        error is SecurityException -> Result.PERMISSION_REQUIRED
                        else -> Result.FAILED
                    }
                }
            }
            while (true) {
                stopped()?.let { return it }
                val enabled = isEnabled()
                stopped()?.let { return it }
                if (enabled == true) return Result.READY
                if (response.get() > 0) return Result.FAILED
                val remainingMillis = (deadline - System.nanoTime()) / 1_000_000L
                if (remainingMillis <= 0) return Result.TIMED_OUT
                try {
                    Thread.sleep(minOf(250L, remainingMillis))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return Result.CANCELLED
                }
            }
        } finally {
            startupLock.unlock()
        }
    }
}

package com.geelyplay.preface.network

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

/** Starts GKUI's saved Android 5.1 SoftAP through its legacy WifiManager API. */
internal object GkuiLegacySoftAp {
    private const val GKUI_BLUETOOTH_PACKAGE = "ecarx.bluetooth.service"

    fun supported(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt < Build.VERSION_CODES.O && runCatching {
            val flags = context.packageManager.getApplicationInfo(GKUI_BLUETOOTH_PACKAGE, 0).flags
            flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.getOrDefault(false)

    fun start(
        wifi: Any,
        timeoutMillis: Long,
        cancelled: () -> Boolean,
        apEnabled: () -> Boolean?,
        log: (String) -> Unit,
        sleep: (Long) -> Unit = Thread::sleep,
    ): Boolean {
        require(timeoutMillis > 0)
        val deadline = System.nanoTime() + timeoutMillis * NANOS_PER_MILLI
        fun stopped() = cancelled() || Thread.currentThread().isInterrupted || System.nanoTime() >= deadline
        fun wifiEnabled() = runCatching { callNoArgs(wifi, "isWifiEnabled") as? Boolean }.getOrNull()
        fun pause(): Boolean = try {
            sleep(minOf(POLL_MILLIS, remainingMillis(deadline)))
            !stopped()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        if (wifiEnabled() == true) {
            if (stopped()) return false
            log("GKUI SoftAP: disabling Wi-Fi station mode")
            val accepted = runCatching { callBoolean(wifi, "setWifiEnabled", false) as? Boolean == true }
                .getOrDefault(false)
            if (!accepted) return false
            while (!stopped() && wifiEnabled() == true) if (!pause()) return false
            if (stopped() || wifiEnabled() == true) return false
        }

        if (stopped()) return false
        if (apEnabled() == true) return !stopped()
        val configuration = readConfiguration(wifi)
        if (!invokeSetWifiApEnabled(wifi, configuration, true) || stopped()) return false
        log("GKUI SoftAP: legacy start request accepted; waiting for AP enabled state")
        while (!stopped()) {
            if (apEnabled() == true && !stopped()) {
                log("GKUI SoftAP: AP enabled state confirmed")
                return true
            }
            if (!pause()) return false
        }
        return false
    }

    internal fun readConfiguration(wifi: Any): Any? {
        val method = wifi.javaClass.methods.firstOrNull {
            it.name == "getWifiApConfiguration" && it.parameterCount == 0
        } ?: wifi.javaClass.declaredMethods.firstOrNull {
            it.name == "getWifiApConfiguration" && it.parameterCount == 0
        }?.apply { isAccessible = true } ?: return null
        return runCatching { method.invoke(wifi) }.getOrNull()
    }

    internal fun invokeSetWifiApEnabled(wifi: Any, configuration: Any?, enabled: Boolean): Boolean {
        val method = wifi.javaClass.methods.firstOrNull {
            it.name == "setWifiApEnabled" && it.parameterCount == 2 &&
                it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        } ?: return false
        return runCatching {
            when (val result = method.invoke(wifi, configuration, enabled)) {
                is Boolean -> result
                else -> true
            }
        }.getOrDefault(false)
    }

    private fun callNoArgs(target: Any, name: String): Any? = target.javaClass.methods
        .firstOrNull { it.name == name && it.parameterCount == 0 }
        ?.invoke(target)
        ?: throw NoSuchMethodException(name)

    private fun callBoolean(target: Any, name: String, value: Boolean): Any? = target.javaClass.methods
        .firstOrNull {
            it.name == name && it.parameterCount == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }?.invoke(target, value) ?: throw NoSuchMethodException(name)

    private fun remainingMillis(deadline: Long): Long =
        ((deadline - System.nanoTime()) / NANOS_PER_MILLI).coerceAtLeast(1L)

    private const val POLL_MILLIS = 200L
    private const val NANOS_PER_MILLI = 1_000_000L
}

package com.geelyplay.preface.e01goc

import android.os.IBinder
import androidx.annotation.Keep
import kotlin.system.exitProcess

/** Fixed root helper for the FS11 saved SoftAP. It accepts no command-line input. */
@Keep
object E01RootHotspotMain {
    private const val ENABLED = 13

    @JvmStatic fun main(args: Array<String>) {
        // app_process invokes this as root through ExtraUtilsService. Never run a caller-provided
        // command; the only mutation is the firmware's existing saved AP configuration.
        val result = runCatching {
            require(args.isEmpty())
            val service = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java).invoke(null, "wifi") as IBinder
            val stub = Class.forName("android.net.wifi.IWifiManager\$Stub")
            val wifi = stub.getMethod("asInterface", IBinder::class.java)
                .invoke(null, service) ?: error("wifi service unavailable")
            val state = wifi.javaClass.getMethod("getWifiApEnabledState")
                .invoke(wifi) as? Int
            if (state != ENABLED) {
                val config = Class.forName("android.net.wifi.WifiConfiguration")
                wifi.javaClass.getMethod("setWifiApEnabled", config, Boolean::class.javaPrimitiveType)
                    .invoke(wifi, null, true)
            }
            val deadline = System.nanoTime() + 15_000_000_000L
            while (System.nanoTime() < deadline) {
                val current = wifi.javaClass.getMethod("getWifiApEnabledState")
                    .invoke(wifi) as? Int
                if (current == ENABLED) return@runCatching true
                Thread.sleep(250)
            }
            false
        }.getOrDefault(false)
        println(if (result) "DIPLAY_HOTSPOT_ROOT:ready" else "DIPLAY_HOTSPOT_ROOT:failed")
        exitProcess(0)
    }
}

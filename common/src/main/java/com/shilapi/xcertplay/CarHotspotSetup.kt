package com.shilapi.xcertplay

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb

/** Each grant is requested explicitly from settings; startup never calls this authorization path. */
internal object CarHotspotSetup {
    enum class Permission(val appOp: String) {
        HOTSPOT("WRITE_SETTINGS"), BOOT_LAUNCH("SYSTEM_ALERT_WINDOW");

        fun granted(context: Context): Boolean = when (this) {
            HOTSPOT -> com.geelyplay.preface.compat.ContextCompat.canWriteSettings(context)
            BOOT_LAUNCH -> com.geelyplay.preface.compat.ContextCompat.canDrawOverlays(context)
        }
    }

    fun check(context: Context, adb: LocalAdb = LocalAdb(AdbKeys.load(context))): LocalAdb.Access = adb.use {
        it.connect(mayAsk = false)
    }

    fun grant(context: Context, permissions: List<Permission>, adb: LocalAdb = LocalAdb(AdbKeys.load(context))): LocalAdb.Access =
        adb.use {
            val access = it.connect(mayAsk = true)
            Log.i("DiPlay-ADB", "switch connection: $access")
            if (access == LocalAdb.Access.READY) {
                for (permission in permissions) {
                    if (!permission.granted(context)) {
                        Log.i("DiPlay-ADB", "request permission: ${permission.appOp}")
                        it.shell("appops set ${context.packageName} ${permission.appOp} allow")
                    }
                    val granted = permission.granted(context)
                    Log.i("DiPlay-ADB", "permission ${permission.appOp}: granted=$granted")
                    if (!granted) break
                }
            }
            access
        }

    fun startupEligibility(context: Context, hasSession: Boolean): String {
        if (hasSession) return "active-session"
        val mode = AirPlayPersistence.loadWirelessHotspotMode(context)
        if (mode != com.shilapi.xcertplay.orchestration.WirelessHotspotMode.AUTOMATIC &&
            mode != com.shilapi.xcertplay.orchestration.WirelessHotspotMode.MANUAL) {
            return "car-hotspot-not-selected"
        }
        return "eligible"
    }

    fun shouldStartOnLaunch(context: Context, hasSession: Boolean): Boolean =
        startupEligibility(context, hasSession) == "eligible"
}

package com.shilapi.xcertplay

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.util.Log
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.network.CarHotspotTethering
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/** Each grant is requested explicitly from settings; startup never calls this authorization path. */
internal object CarHotspotSetup {
    fun isBydHeadUnit(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo("com.byd.carsettings", 0).flags and
            ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

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

    /** The hotspot switch is independent of opening DiPlay and selected wireless backend. */
    fun startupEligibility(context: Context, hasSession: Boolean): String {
        if (hasSession) return "active-session"
        if (!CarHotspotSettings.enabled(context)) return "auto-start-disabled"
        // This switch controls the vehicle AP itself. It must still run when the
        // last selected CarPlay transport was USB or an existing external Wi-Fi.
        if (!CarHotspotTethering.gkuiLegacySoftApSupported(context) &&
            AirPlayPersistence.loadWirelessHotspotMode(context) == WirelessHotspotMode.MANUAL &&
            ManualHotspotValidation.error(AirPlayPersistence.loadManualHotspotSsid(context),
                AirPlayPersistence.loadManualHotspotPassphrase(context)) != null
        ) return "manual-config-invalid"
        return "eligible"
    }

    fun shouldStartOnLaunch(context: Context, hasSession: Boolean): Boolean =
        startupEligibility(context, hasSession) == "eligible"
}

package com.shilapi.xcertplay.license

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Only wireless admission is licensed; USB, existing sessions and Bluetooth recovery stay independent. */
object AppLicense {
    @Volatile private var lastPrompt = -10000L
    fun enabled(context: Context) = OfflineLicense.enabled(context) || OnlineLicense.enabled(context)
    fun offline(context: Context) = OfflineLicense.enabled(context)
    fun canStart(context: Context) = if (offline(context)) OfflineLicense.canStart(context) else OnlineLicense.canStart(context)
    fun activationActivity(context: Context): Class<out Activity> = if (offline(context)) OfflineLicenseActivity::class.java else LicenseActivity::class.java
    fun requireActivation(activity: Activity, wireless: Boolean? = null): Boolean {
        val requestedWireless = wireless ?: com.shilapi.xcertplay.AirPlayPersistence.loadWirelessEnabled(activity)
        if (!requestedWireless) return false
        if (canStart(activity)) return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastPrompt >= 1500 && !activity.isFinishing) {
            lastPrompt = now
            activity.startActivity(Intent(activity, activationActivity(activity))
                .putExtra("license_wireless", true))
        }
        return true
    }
}

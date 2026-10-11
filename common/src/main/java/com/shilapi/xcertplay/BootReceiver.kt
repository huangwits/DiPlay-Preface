package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts DiPlay after boot when the user enabled app startup or car-hotspot auto-start. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appLaunchEnabled = AirPlayPersistence.loadAutoStartOnBoot(context)
        // A hotspot request also needs DiPlay's foreground startup path. This keeps the
        // existing explicit permission gate while removing the extra manual hotspot step.
        val hotspotLaunchEnabled = CarHotspotSetup.shouldStartOnLaunch(context, CarPlayBackgroundSession.hasSession())
        val launchEnabled = appLaunchEnabled || hotspotLaunchEnabled
        StartupDiagnosticSnapshot.received(context, launchEnabled)
        if (!launchEnabled) return

        val launch = Intent(context, DiPlayActivity::class.java).apply {
            putExtra("boot_hotspot", hotspotLaunchEnabled)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        try {
            context.startActivity(launch)
            StartupDiagnosticSnapshot.launchResult(context)
        } catch (error: RuntimeException) {
            StartupDiagnosticSnapshot.launchResult(context, error)
            Log.w(TAG, "Boot auto-start could not launch DiPlayActivity", error)
        }
    }

    private companion object {
        const val TAG = "xcertplay-boot"
    }
}

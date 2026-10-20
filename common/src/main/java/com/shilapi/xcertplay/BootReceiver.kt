package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts DiPlay and its saved automatic hotspot after boot when the owner left auto-start enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED
        val powerOn = intent.action == ACTION_POWER_ON &&
            intent.getIntExtra(EXTRA_C3_POWER_FLAG, 0) == POWER_FLAG_ACC_ON
        if (!boot && !powerOn) return
        val appLaunchEnabled = AirPlayPersistence.loadAutoStartOnBoot(context)
        val hotspotLaunchEnabled = appLaunchEnabled &&
            CarHotspotSetup.shouldStartOnLaunch(context, CarPlayBackgroundSession.hasSession())
        val launchEnabled = appLaunchEnabled
        StartupDiagnosticSnapshot.received(context, launchEnabled, hotspotLaunchEnabled)
        com.geelyplay.preface.network.BootHotspotJobService.schedule(context)
        if (!launchEnabled) return

        val launch = Intent(context, DiPlayActivity::class.java).apply {
            putExtra("boot_hotspot", hotspotLaunchEnabled)
            putExtra("power_on_hotspot", powerOn && hotspotLaunchEnabled)
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
        const val ACTION_POWER_ON = "android.intent.action.POWER_ON"
        const val EXTRA_C3_POWER_FLAG = "android.intent.extra.c3_power_flag"
        const val POWER_FLAG_ACC_ON = 2
    }
}

package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.network.CarHotspotTethering

/** Only the latest received boot event and launch result; no Intent extras or device identifiers. */
internal object StartupDiagnosticSnapshot {
    private const val PREFS = "diplay_startup_diagnostics"

    enum class RootHotspotResult(val value: String) {
        PENDING("pending"), CONFIRMED("confirmed"), FALLBACK("fallback"),
        CANCELLED("cancelled"), NOT_REQUESTED("not-requested"),
    }

    fun received(context: Context, launchEnabled: Boolean, hotspotLaunchEnabled: Boolean = false) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("receivedAt", System.currentTimeMillis())
                .putBoolean("launchEnabled", launchEnabled)
                .putBoolean("hotspotLaunchEnabled", hotspotLaunchEnabled)
                .putBoolean("hotspotAutoStartSetting", CarHotspotSettings.enabled(context))
                .putBoolean("wirelessEnabledAtBoot", AirPlayPersistence.loadWirelessEnabled(context))
                .putString("hotspotModeAtBoot", AirPlayPersistence.loadWirelessHotspotMode(context).name)
                .putString("hotspotEligibility", if (hotspotLaunchEnabled) "eligible" else
                    CarHotspotSetup.startupEligibility(context, CarPlayBackgroundSession.hasSession()))
                .putString("hotspotRootResult", if (hotspotLaunchEnabled) RootHotspotResult.PENDING.value
                    else RootHotspotResult.NOT_REQUESTED.value)
                .putString("launchResult", if (launchEnabled) "pending" else "disabled")
                .putString("hotspotResult", if (hotspotLaunchEnabled) "pending" else "not-eligible")
                .remove("failureClass")
                .remove("hotspotApEnabled").apply()
        }
    }

    fun hotspotRootResult(context: Context, result: RootHotspotResult) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("hotspotRootResult", result.value).apply()
        }
    }

    fun hotspotResult(context: Context, result: CarHotspotTethering.Result, apEnabled: Boolean?) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("hotspotResult", result.name)
                .putString("hotspotApEnabled", apEnabled?.toString() ?: "unknown")
                .apply()
        }
    }

    fun launchResult(context: Context, failure: RuntimeException? = null) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("launchResult", if (failure == null) "startActivity-returned" else "failed")
                .putString("failureClass", failure?.javaClass?.simpleName?.take(80)
                    ?.replace(Regex("[^A-Za-z0-9_$]"), "?")).apply()
        }
    }

    fun report(context: Context): String = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val receivedAt = prefs.getLong("receivedAt", 0)
        if (receivedAt == 0L) {
            "Boot diagnostics received=false; no boot delivery has been recorded by this version."
        } else {
            "Boot diagnostics received=true receivedAtEpochMs=$receivedAt " +
                "launchEnabledAtBoot=${prefs.getBoolean("launchEnabled", false)} " +
                "launchResult=${prefs.getString("launchResult", "unknown")} " +
                "failureClass=${prefs.getString("failureClass", "none") ?: "none"}; " +
                "hotspotLaunchAtBoot=${prefs.getBoolean("hotspotLaunchEnabled", false)} " +
                "hotspotAutoStartSetting=${prefs.getBoolean("hotspotAutoStartSetting", false)} " +
                "wirelessEnabledAtBoot=${prefs.getBoolean("wirelessEnabledAtBoot", false)} " +
                "hotspotModeAtBoot=${prefs.getString("hotspotModeAtBoot", "unknown")} " +
                "hotspotEligibility=${prefs.getString("hotspotEligibility", "unknown")} " +
                "hotspotRootResult=${prefs.getString("hotspotRootResult", "unknown")} " +
                "hotspotResult=${prefs.getString("hotspotResult", "unknown")} " +
                "hotspotApEnabled=${prefs.getString("hotspotApEnabled", "unknown") ?: "unknown"}; " +
                "startActivity-returned does not prove the OEM displayed the app."
        }
    }.getOrDefault("Boot diagnostics unavailable.")
}

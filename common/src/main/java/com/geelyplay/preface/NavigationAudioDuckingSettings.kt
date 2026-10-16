package com.geelyplay.preface

import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings

/** GKUI's mixer owns the native source; other devices use this app's focus/volume policy. */
internal object NavigationAudioDuckingSettings {
    const val SYSTEM_KEY = "NAVI_MIX_SWITCH"
    @Suppress("DEPRECATION")
    fun usesFactoryMixer(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo("ecarx.bluetooth.service", 0).flags and
            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)

    fun load(context: Context, factory: Boolean = usesFactoryMixer(context)): Boolean = if (factory) {
        runCatching { Settings.System.getInt(context.contentResolver, SYSTEM_KEY, 1) == 1 }.getOrDefault(true)
    } else context.getSharedPreferences("audio_stream_routes", Context.MODE_PRIVATE).getBoolean("navigation_ducking", true)

    fun save(context: Context, enabled: Boolean, factory: Boolean = usesFactoryMixer(context)): Boolean {
        if (factory) return runCatching {
            Settings.System.putInt(context.contentResolver, SYSTEM_KEY, if (enabled) 1 else 0) && load(context, true) == enabled
        }.getOrDefault(false)
        context.getSharedPreferences("audio_stream_routes", Context.MODE_PRIVATE).edit().putBoolean("navigation_ducking", enabled).apply()
        return true
    }
}

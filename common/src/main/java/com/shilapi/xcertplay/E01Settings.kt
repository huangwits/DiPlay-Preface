package com.shilapi.xcertplay

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.airplay.E01Performance
import com.shilapi.xcertplay.host.R

object E01Settings {
    private const val PREFS = "diplay_performance"
    private const val KEY_ENABLED = "e01_enabled"

    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, context.resources.getBoolean(R.bool.config_e01_default) ||
            E01Performance.matchesHardware(Build.HARDWARE, Build.BOARD, Build.MODEL))

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun deviceSummary(context: Context): String {
        val memory = ActivityManager.MemoryInfo()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.getMemoryInfo(memory)
        val dm = context.resources.displayMetrics
        return "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}; " +
            "RAM ${memory.totalMem / (1024 * 1024)} MiB, available ${memory.availMem / (1024 * 1024)} MiB; " +
            "ABI ${if (Build.VERSION.SDK_INT >= 21) Build.SUPPORTED_ABIS.joinToString() else Build.CPU_ABI}; ${dm.widthPixels}×${dm.heightPixels}; " +
            "hardware=${Build.HARDWARE}; board=${Build.BOARD}; E01=${enabled(context)}"
    }
}

package com.shilapi.xcertplay.network

import android.content.Context
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

object CarHotspotSettings {
    // The removed opt-in is intentionally disabled for existing installs and ignored here.
    @Suppress("UNUSED_PARAMETER")
    fun enabled(context: Context): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun setEnabled(context: Context, enabled: Boolean) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun shouldEnable(context: Context, wireless: Boolean, mode: WirelessHotspotMode): Boolean =
        wireless && mode == WirelessHotspotMode.MANUAL
}

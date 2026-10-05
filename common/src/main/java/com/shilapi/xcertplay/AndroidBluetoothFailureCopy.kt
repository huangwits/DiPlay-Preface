package com.shilapi.xcertplay

import android.content.Context

/** Preserve the difference between a missing radio, an off switch and a failed connection. */
internal object AndroidBluetoothFailureCopy {
    fun forControllerMessage(context: Context, message: String): String? = when (message) {
        "Bluetooth adapter is unavailable" ->
            context.getString(com.shilapi.xcertplay.host.R.string.android_bluetooth_transport_unavailable)
        "Bluetooth is not enabled" -> context.getString(com.shilapi.xcertplay.host.R.string.android_bluetooth_disabled)
        "Bluetooth is turning on", "Bluetooth is turning off" ->
            context.getString(com.shilapi.xcertplay.host.R.string.android_bluetooth_transitioning)
        "Android Bluetooth permission is missing" -> context.getString(com.shilapi.xcertplay.host.R.string.android_bluetooth_permission_missing)
        "Could not read Android Bluetooth state" -> context.getString(com.shilapi.xcertplay.host.R.string.android_bluetooth_state_unknown)
        "Bluetooth RFCOMM connection failed" -> context.getString(com.shilapi.xcertplay.host.R.string.android_bluetooth_rfcomm_failed)
        else -> null
    }
}

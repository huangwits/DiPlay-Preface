package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothAdapter
import java.io.IOException

/** Standard Android transport only; a vendor service reporting ON is not a substitute. */
internal object BluetoothPreflight {
    fun requireReady(permissionGranted: Boolean, readState: () -> Int?) {
        if (!permissionGranted) throw IOException("Android Bluetooth permission is missing")
        val state = try {
            readState()
        } catch (error: SecurityException) {
            throw IOException("Android Bluetooth permission is missing", error)
        } catch (error: RuntimeException) {
            throw IOException("Could not read Android Bluetooth state", error)
        }
        when (state) {
            null -> throw IOException("Bluetooth adapter is unavailable")
            BluetoothAdapter.STATE_OFF -> throw IOException("Bluetooth is not enabled")
            BluetoothAdapter.STATE_TURNING_ON -> throw IOException("Bluetooth is turning on")
            BluetoothAdapter.STATE_TURNING_OFF -> throw IOException("Bluetooth is turning off")
            BluetoothAdapter.STATE_ON -> Unit
            else -> throw IOException("Could not read Android Bluetooth state")
        }
    }
}

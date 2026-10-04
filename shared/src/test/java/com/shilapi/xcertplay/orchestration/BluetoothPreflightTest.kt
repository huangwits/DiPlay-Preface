package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothAdapter
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class BluetoothPreflightTest {
    @Test fun onlyAnOnStandardAdapterMayProceed() {
        BluetoothPreflight.requireReady(true) { BluetoothAdapter.STATE_ON }
        val cases = mapOf(
            null to "Bluetooth adapter is unavailable",
            BluetoothAdapter.STATE_OFF to "Bluetooth is not enabled",
            BluetoothAdapter.STATE_TURNING_ON to "Bluetooth is turning on",
            BluetoothAdapter.STATE_TURNING_OFF to "Bluetooth is turning off",
            -1 to "Could not read Android Bluetooth state",
        )
        cases.forEach { (state, expected) ->
            assertEquals(expected, assertThrows(IOException::class.java) {
                BluetoothPreflight.requireReady(true) { state }
            }.message)
        }
    }

    @Test fun deniedPermissionDoesNotCallThePlatform() {
        val failure = assertThrows(IOException::class.java) {
            BluetoothPreflight.requireReady(false) { error("Must not call platform") }
        }
        assertEquals("Android Bluetooth permission is missing", failure.message)
    }

    @Test fun vendorAndSecurityFailuresAreNotReportedAsRadioOff() {
        for (cause in listOf(SecurityException("denied"), IllegalStateException("vendor unavailable"))) {
            val failure = assertThrows(IOException::class.java) {
                BluetoothPreflight.requireReady(true) { throw cause }
            }
            assertSame(cause, failure.cause)
            assertNotEquals("Bluetooth is not enabled", failure.message)
        }
    }
}

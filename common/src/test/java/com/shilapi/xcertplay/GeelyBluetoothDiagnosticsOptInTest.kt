package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class GeelyBluetoothDiagnosticsOptInTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clearPreferences() {
        context.getSharedPreferences("xcertplay_airplay", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun vendorCallsAreGatedByPersistedOptInAndDefaultOff() {
        var vendorCalls = 0
        assertFalse(GeelyBluetoothDiagnosticsOptIn.enabled(context))
        assertNull(GeelyBluetoothDiagnosticsOptIn.runIfEnabled(context) { vendorCalls++; "queried" })
        assertEquals(0, vendorCalls)

        AirPlayPersistence.saveGeelyBluetoothDiagnosticsEnabled(context, true)
        assertTrue(GeelyBluetoothDiagnosticsOptIn.enabled(context))
        assertEquals("queried", GeelyBluetoothDiagnosticsOptIn.runIfEnabled(context) { vendorCalls++; "queried" })
        assertEquals(1, vendorCalls)

        AirPlayPersistence.saveGeelyBluetoothDiagnosticsEnabled(context, false)
        assertFalse(GeelyBluetoothDiagnosticsOptIn.enabled(context))
        assertNull(GeelyBluetoothDiagnosticsOptIn.runIfEnabled(context) { vendorCalls++; "queried" })
        assertEquals(1, vendorCalls)
    }

    @Test fun strictControllerAdapterFailuresHaveLocalizedTransportCopyOnly() {
        val expected = context.getString(com.shilapi.xcertplay.host.R.string.android_bluetooth_transport_unavailable)
        assertEquals(expected, AndroidBluetoothFailureCopy.forControllerMessage(context, "Bluetooth adapter is unavailable"))
        val disabled = context.getString(com.shilapi.xcertplay.host.R.string.android_bluetooth_disabled)
        assertEquals(disabled, AndroidBluetoothFailureCopy.forControllerMessage(context, "Bluetooth is not enabled"))
        assertFalse(expected == disabled)
        assertNull(AndroidBluetoothFailureCopy.forControllerMessage(context, "Could not connect RFCOMM to device"))
    }

    @Test fun connectionFailureAndPermissionFailureHaveDistinctActions() {
        assertEquals(context.getString(com.shilapi.xcertplay.host.R.string.android_bluetooth_rfcomm_failed),
            AndroidBluetoothFailureCopy.forControllerMessage(context, "Bluetooth RFCOMM connection failed"))
        assertEquals(context.getString(com.shilapi.xcertplay.host.R.string.android_bluetooth_permission_missing),
            AndroidBluetoothFailureCopy.forControllerMessage(context, "Android Bluetooth permission is missing"))
        assertNull(AndroidBluetoothFailureCopy.forControllerMessage(context, "STEP bt/rfcomm: connecting"))
    }
}

package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class WheelAssignmentsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun noManufacturerKeyTableIsActiveOnFirstInstall() {
        for (role in WheelZoomSettings.Role.entries) {
            assertFalse(WheelZoomSettings.assigned(context, role))
            assertNull(WheelZoomSettings.roleOf(context, WheelZoomSettings.key(context, role)))
        }
        assertTrue(WheelZoomSettings.bridgeKeys(context).isEmpty())
    }

    @Test fun onlyTheExplicitlyLearnedKeyIsMatchedAndRetained() {
        val key = WheelKey(12345, 77, "test-wheel")
        WheelZoomSettings.assign(context, WheelZoomSettings.Role.MODE, key)
        assertTrue(WheelZoomSettings.assigned(context, WheelZoomSettings.Role.MODE))
        assertEquals(key, WheelZoomSettings.key(context, WheelZoomSettings.Role.MODE))
        assertEquals(WheelZoomSettings.Role.MODE, WheelZoomSettings.roleOf(context, key))
        assertNull(WheelZoomSettings.roleOf(context, key.copy(device = "another-wheel")))
    }
}

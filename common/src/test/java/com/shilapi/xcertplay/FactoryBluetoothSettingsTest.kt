package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class FactoryBluetoothSettingsTest {
    @Test fun optOutIsPersistedAndDoesNotChangeTheSavedPhone() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        DiPlayPreferences.savePhone(context, "AA:BB:CC:DD:EE:01", "My iPhone")
        FactoryBluetoothSettings.setEnabled(context, true)
        assertTrue(FactoryBluetoothSettings.enabled(context))
        FactoryBluetoothSettings.setEnabled(context, false)
        assertFalse(FactoryBluetoothSettings.enabled(context))
        assertEquals("AA:BB:CC:DD:EE:01", DiPlayPreferences.phoneAddress(context))
    }

    @Test @Config(qualifiers = "zh-rCN") fun stageCodesRemainVisibleWithChineseExplanation() {
        val context = RuntimeEnvironment.getApplication()
        val text = FactoryBluetoothSettings.failureCopy(context, "Failed: [E01-F08] Socket ended")!!
        assertTrue(text.startsWith("[E01-F08]"))
        assertTrue(text.contains("握手"))
        assertNull(FactoryBluetoothSettings.failureCopy(context, "Generic Bluetooth failure"))
    }
}

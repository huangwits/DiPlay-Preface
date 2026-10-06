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
    @Test fun freshInstallUsesSystemBluetoothWithoutSelectingAVendor() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(FactoryBluetoothSettings.Choice.SYSTEM, FactoryBluetoothSettings.choice(context))
        assertFalse(FactoryBluetoothSettings.enabled(context))
    }

    @Test fun eachManualTransportChangeClearsOnlyThePreviousPhone() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        DiPlayPreferences.saveAutoConnect(context, true)
        for (choice in listOf(FactoryBluetoothSettings.Choice.E01, FactoryBluetoothSettings.Choice.SYSTEM)) {
            DiPlayPreferences.savePhone(context, "11:22:33:44:55:66", "Previous interface phone")
            assertTrue(FactoryBluetoothSettings.select(context, choice))
            assertEquals(choice, FactoryBluetoothSettings.choice(context))
            assertEquals(choice.backend != null, FactoryBluetoothSettings.enabled(context))
            choice.backend?.let { assertEquals(it, FactoryBluetoothSettings.backend(context)) }
            assertNull(DiPlayPreferences.phoneAddress(context))
            assertTrue(DiPlayPreferences.autoConnect(context))
        }
    }

    @Test fun selectingTheCurrentInterfaceKeepsItsPairedPhone() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        FactoryBluetoothSettings.select(context, FactoryBluetoothSettings.Choice.E01)
        DiPlayPreferences.savePhone(context, "11:22:33:44:55:66", "E01 phone")
        assertFalse(FactoryBluetoothSettings.select(context, FactoryBluetoothSettings.Choice.E01))
        assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(context))
    }

    @Test fun overlayUpdateDropsRemovedH52SelectionAndPhone() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("factory_bluetooth_enabled", true)
            .putString("factory_bluetooth_backend", "H52_ANW").commit()
        DiPlayPreferences.savePhone(context, "11:22:33:44:55:66", "Saved phone")
        E01Settings.setEnabled(context, false)
        assertEquals(FactoryBluetoothSettings.Choice.SYSTEM, FactoryBluetoothSettings.choice(context))
        assertNull(DiPlayPreferences.phoneAddress(context))
        assertFalse(FactoryBluetoothSettings.enabled(context))
    }

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

    @Test fun overlayUpdateRetainsE01SelectionAndPhone() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("factory_bluetooth_enabled", true)
            .putString("factory_bluetooth_backend", "ECARX").commit()
        DiPlayPreferences.savePhone(context, "11:22:33:44:55:66", "Saved phone")
        assertEquals(FactoryBluetoothSettings.Choice.E01, FactoryBluetoothSettings.choice(context))
        assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(context))
    }
}

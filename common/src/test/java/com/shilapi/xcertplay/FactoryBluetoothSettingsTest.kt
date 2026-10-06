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
        for (choice in listOf(FactoryBluetoothSettings.Choice.H52,
            FactoryBluetoothSettings.Choice.E01, FactoryBluetoothSettings.Choice.SYSTEM)) {
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
        FactoryBluetoothSettings.select(context, FactoryBluetoothSettings.Choice.H52)
        DiPlayPreferences.savePhone(context, "11:22:33:44:55:66", "H52 phone")
        assertFalse(FactoryBluetoothSettings.select(context, FactoryBluetoothSettings.Choice.H52))
        assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(context))
    }

    @Test fun overlayUpdateHonorsPreviouslySavedVendorSelection() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("factory_bluetooth_enabled", true)
            .putString("factory_bluetooth_backend", "H52_ANW").commit()
        DiPlayPreferences.savePhone(context, "11:22:33:44:55:66", "Saved phone")
        E01Settings.setEnabled(context, false)
        assertEquals(FactoryBluetoothSettings.Choice.H52, FactoryBluetoothSettings.choice(context))
        assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(context))
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

    @Test fun choosingAnotherBackendClearsOnlyThePreviousPhoneSelection() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("diplay", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        DiPlayPreferences.savePhone(context, "11:22:33:44:55:66", "Phone")
        DiPlayPreferences.saveAutoConnect(context, true)
        assertEquals(com.shilapi.xcertplay.transport.FactoryBluetoothBackend.ECARX, FactoryBluetoothSettings.backend(context))
        FactoryBluetoothSettings.setBackend(context, com.shilapi.xcertplay.transport.FactoryBluetoothBackend.H52_ANW)
        assertNull(DiPlayPreferences.phoneAddress(context))
        assertTrue(DiPlayPreferences.autoConnect(context))
        assertEquals(com.shilapi.xcertplay.transport.FactoryBluetoothBackend.H52_ANW, FactoryBluetoothSettings.backend(context))
        DiPlayPreferences.savePhone(context, "22:33:44:55:66:77", "Factory phone")
        FactoryBluetoothSettings.setBackend(context, com.shilapi.xcertplay.transport.FactoryBluetoothBackend.H52_ANW)
        assertEquals("22:33:44:55:66:77", DiPlayPreferences.phoneAddress(context))
    }

    @Test @Config(qualifiers = "zh-rCN") fun missingAnwServiceKeepsItsOwnErrorCode() {
        val text = FactoryBluetoothSettings.failureCopy(RuntimeEnvironment.getApplication(), "[E01-H01] Wrong descriptor")!!
        assertTrue(text.contains("E01-H01"))
        assertTrue(text.contains("ANW"))
    }
}

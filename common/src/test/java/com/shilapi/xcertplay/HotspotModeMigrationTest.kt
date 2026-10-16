package com.shilapi.xcertplay

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class HotspotModeMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Test fun oldLocalSelectionMigratesWithoutLosingCarHotspotDetails() {
        prefs.edit().putString("wireless_hotspot_mode", "LOCAL_ONLY_HOTSPOT").apply()
        AirPlayPersistence.saveManualHotspotSsid(context, "Test car")
        AirPlayPersistence.saveManualHotspotPassphrase(context, "test-password")
        assertEquals(WirelessHotspotMode.AUTOMATIC, AirPlayPersistence.loadWirelessHotspotMode(context))
        assertEquals("AUTOMATIC", prefs.getString("wireless_hotspot_mode", null))
        assertEquals("Test car", AirPlayPersistence.loadManualHotspotSsid(context))
        assertEquals("test-password", AirPlayPersistence.loadManualHotspotPassphrase(context))
        // A saved automatic choice remains available after reopening connection settings.
        prefs.edit().putString("wireless_hotspot_mode", "AUTOMATIC").apply()
        assertEquals(WirelessHotspotMode.AUTOMATIC, AirPlayPersistence.loadWirelessHotspotMode(context))
        assertEquals("AUTOMATIC", prefs.getString("wireless_hotspot_mode", null))
        assertEquals("Test car", AirPlayPersistence.loadManualHotspotSsid(context))
        assertEquals("test-password", AirPlayPersistence.loadManualHotspotPassphrase(context))
    }

    @Test fun savingAutomaticModeKeepsItForTheNextConnection() {
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.AUTOMATIC)
        assertEquals(WirelessHotspotMode.AUTOMATIC, AirPlayPersistence.loadWirelessHotspotMode(context))
        assertEquals("AUTOMATIC", prefs.getString("wireless_hotspot_mode", null))
    }

    @Test fun connectionSettingsExposeAndSaveAutomaticHotspotMode() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        ReflectionHelpers.setField(activity, "page", "connection")
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
        val automatic = descendants(activity.window.decorView).filterIsInstance<Button>()
            .singleOrNull { it.text.toString().trimStart('✓', ' ').startsWith(activity.getString(R.string.hotspot_mode_auto)) }
        assertNotNull(automatic)
        automatic!!.performClick()
        assertEquals(WirelessHotspotMode.AUTOMATIC, AirPlayPersistence.loadWirelessHotspotMode(activity))
        controller.pause().stop().destroy()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    @Test fun freshInstallDefaultsToAutomaticConnection() {
        prefs.edit().clear().apply()
        assertEquals(WirelessHotspotMode.AUTOMATIC, AirPlayPersistence.loadWirelessHotspotMode(context))
    }

    @Test fun oldManualDefaultMigratesOnceAndKeepsSavedHotspot() {
        prefs.edit().clear().putString("wireless_hotspot_mode", "MANUAL")
            .putString("manual_hotspot_ssid", "Saved car")
            .putString("manual_hotspot_passphrase", "12345678").commit()
        assertEquals(WirelessHotspotMode.AUTOMATIC, AirPlayPersistence.loadWirelessHotspotMode(context))
        assertEquals("Saved car", AirPlayPersistence.loadManualHotspotSsid(context))
        assertEquals("12345678", AirPlayPersistence.loadManualHotspotPassphrase(context))
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        assertEquals(WirelessHotspotMode.MANUAL, AirPlayPersistence.loadWirelessHotspotMode(context))
        assertEquals(WirelessHotspotMode.MANUAL, AirPlayPersistence.loadWirelessHotspotMode(context))
    }

    @Test fun explicitManualChoiceInTheNewVersionIsPreserved() {
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        assertEquals(WirelessHotspotMode.MANUAL, AirPlayPersistence.loadWirelessHotspotMode(context))
    }

    @Test fun automaticButtonUsesRequestedChineseLabel() {
        assertEquals("自动连接", context.getString(R.string.hotspot_mode_auto))
    }

    @Test fun existingWifiDirectSelectionIsPreserved() {
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.WIFI_P2P)
        assertEquals(WirelessHotspotMode.WIFI_P2P, AirPlayPersistence.loadWirelessHotspotMode(context))
    }

    @Test @Config(sdk = [28]) fun olderAndroidFallsBackFromUnsupportedWifiDirectToAutomatic() {
        prefs.edit().putString("wireless_hotspot_mode", "WIFI_P2P").apply()
        assertEquals(WirelessHotspotMode.AUTOMATIC, AirPlayPersistence.loadWirelessHotspotMode(context))
    }
}

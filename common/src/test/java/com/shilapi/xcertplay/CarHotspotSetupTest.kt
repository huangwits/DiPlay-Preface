package com.shilapi.xcertplay

import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarHotspotSetupTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        context.getSharedPreferences("diplay_car_hotspot", 0).edit().clear().commit()
        context.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
    }

    @Test fun legacySwitchValueDoesNotControlWirelessCarHotspotStartup() {
        CarHotspotSettings.setEnabled(context, true)

        assertFalse(CarHotspotSettings.enabled(context))
        assertTrue(CarHotspotSettings.shouldEnable(context, true, WirelessHotspotMode.MANUAL))

        CarHotspotSettings.setEnabled(context, false)
        assertTrue(CarHotspotSettings.shouldEnable(context, true, WirelessHotspotMode.MANUAL))
    }

    @Test fun onlyWirelessManualModeUsesTheCarHotspot() {
        for (mode in WirelessHotspotMode.entries) {
            assertTrue("mode=$mode", CarHotspotSettings.shouldEnable(context, true, mode) ==
                (mode == WirelessHotspotMode.MANUAL))
            assertFalse("wired mode=$mode", CarHotspotSettings.shouldEnable(context, false, mode))
        }
    }

    @Test fun savedCarHotspotModeAlsoStartsOnAppLaunch() {
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveManualHotspotSsid(context, "Car hotspot")
        AirPlayPersistence.saveManualHotspotPassphrase(context, "12345678")
        CarHotspotSettings.setEnabled(context, true)

        assertTrue(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = false))
        assertFalse(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = true))
    }

    @Test fun otherWifiModesNeverSwitchTheRadioToTheCarHotspot() {
        for (mode in listOf(WirelessHotspotMode.WIFI_P2P, WirelessHotspotMode.EXISTING_WIFI)) {
            AirPlayPersistence.saveWirelessHotspotMode(context, mode)
            assertFalse(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = false))
        }
    }

    @Test fun openingAutomaticModeStartsSavedHotspotIndependentlyOfBootStartup() {
        assertTrue(AirPlayPersistence.loadAutoStartOnBoot(context))
        assertTrue(AirPlayPersistence.loadWirelessHotspotMode(context) == WirelessHotspotMode.AUTOMATIC)
        assertTrue(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = false))
        assertFalse(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = true))
        AirPlayPersistence.saveAutoStartOnBoot(context, false)
        assertTrue(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = false))
    }

}

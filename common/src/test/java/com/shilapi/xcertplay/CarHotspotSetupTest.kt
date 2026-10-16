package com.shilapi.xcertplay

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
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

    @Test fun hotspotIsNotStartedIndependentlyAtAppLaunch() {
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveManualHotspotSsid(context, "Car hotspot")
        AirPlayPersistence.saveManualHotspotPassphrase(context, "12345678")
        CarHotspotSettings.setEnabled(context, true)

        assertFalse(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = false))
        assertFalse(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = true))
    }

    @Test fun defaultAutomaticModeStartsSavedHotspotWhenBootStartupIsEnabled() {
        assertTrue(AirPlayPersistence.loadAutoStartOnBoot(context))
        assertTrue(AirPlayPersistence.loadWirelessHotspotMode(context) == WirelessHotspotMode.AUTOMATIC)
        assertTrue(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = false))
        assertFalse(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = true))
        AirPlayPersistence.saveAutoStartOnBoot(context, false)
        assertFalse(CarHotspotSetup.shouldStartOnLaunch(context, hasSession = false))
    }

    @Test fun onlyTheSystemCarSettingsPackageIdentifiesTheBydHeadUnit() {
        installPackage("com.byd.carsettings", system = false)
        assertFalse(CarHotspotSetup.isBydHeadUnit(context))

        installPackage("com.byd.carsettings", system = true)
        assertTrue(CarHotspotSetup.isBydHeadUnit(context))
    }

    private fun installPackage(name: String, system: Boolean) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = name
            applicationInfo = ApplicationInfo().apply {
                packageName = name
                flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
            }
        })
    }
}

package com.shilapi.xcertplay.network

import android.net.wifi.WifiConfiguration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class LegacyHotspotStartupTest {
    class LegacyWifi {
        var accepted = true
        var requested = false
        var config: WifiConfiguration? = WifiConfiguration()
        fun setWifiApEnabled(value: WifiConfiguration?, enabled: Boolean): Boolean {
            config = value; requested = enabled; return accepted
        }
    }

    @Test fun savedConfigurationIsUsedAndSystemRejectionIsNotSuccess() {
        val wifi = LegacyWifi()
        assertTrue(CarHotspotTethering.startLegacyHotspot(wifi))
        assertNull(wifi.config)
        assertTrue(wifi.requested)
        wifi.accepted = false
        assertFalse(CarHotspotTethering.startLegacyHotspot(wifi))
    }
}

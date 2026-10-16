package com.shilapi.xcertplay.network

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.wifi.WifiConfiguration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.geelyplay.preface.network.GkuiLegacySoftAp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class GkuiLegacySoftApTest {
    class WifiStub {
        var stationOn = true
        var apOn = false
        var acceptStationChange = true
        var acceptApChange = true
        val saved = WifiConfiguration()
        var appliedConfiguration: Any? = null
        val calls = mutableListOf<String>()

        fun isWifiEnabled() = stationOn
        fun setWifiEnabled(enabled: Boolean): Boolean {
            calls += "station:$enabled"
            if (!acceptStationChange) return false
            stationOn = enabled
            return true
        }
        fun getWifiApConfiguration(): WifiConfiguration = saved
        fun getWifiApState(): Int = if (apOn) 13 else 11
        fun setWifiApEnabled(configuration: WifiConfiguration?, enabled: Boolean): Boolean {
            calls += "ap:$enabled"
            appliedConfiguration = configuration
            if (!acceptApChange) return false
            apOn = enabled
            return true
        }
    }

    @Test
    fun gkuiSystemPackageEnablesTheLegacyPathButAnUpdatedSystemPackageIsAlsoAccepted() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(GkuiLegacySoftAp.supported(context, sdkInt = 22))
        installGkuiSystemPackage(ApplicationInfo.FLAG_SYSTEM)
        assertTrue(GkuiLegacySoftAp.supported(context, sdkInt = 22))
        assertFalse(GkuiLegacySoftAp.supported(context, sdkInt = 26))

        installGkuiSystemPackage(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
        assertTrue(GkuiLegacySoftAp.supported(context, sdkInt = 22))
    }

    @Test
    fun savedConfigurationIsPassedAfterStationWifiStopsAndApStateIsConfirmed() {
        val wifi = WifiStub()
        val logs = mutableListOf<String>()

        assertTrue(GkuiLegacySoftAp.start(wifi, 1_000, { false }, { wifi.getWifiApState() == 13 }, logs::add))

        assertFalse(wifi.stationOn)
        assertTrue(wifi.apOn)
        assertSame(wifi.saved, wifi.appliedConfiguration)
        assertTrue(wifi.calls.indexOf("station:false") < wifi.calls.indexOf("ap:true"))
        assertTrue(logs.any { it.contains("AP enabled state confirmed") })
    }

    @Test
    fun failedStationShutdownDoesNotAttemptToReplaceTheHotspot() {
        val wifi = WifiStub().apply { acceptStationChange = false }

        assertFalse(GkuiLegacySoftAp.start(wifi, 1_000, { false }, { wifi.apOn }, {}))

        assertFalse(wifi.apOn)
        assertTrue(wifi.calls.none { it == "ap:true" })
    }

    private fun installGkuiSystemPackage(flags: Int) {
        val name = "ecarx.bluetooth.service"
        shadowOf(RuntimeEnvironment.getApplication().packageManager).installPackage(PackageInfo().apply {
            packageName = name
            applicationInfo = ApplicationInfo().apply {
                packageName = name
                this.flags = flags
            }
        })
    }
}

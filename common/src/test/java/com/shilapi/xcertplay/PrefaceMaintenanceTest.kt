package com.shilapi.xcertplay

import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import com.geelyplay.preface.e01goc.E01GocActivity
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import com.geelyplay.preface.ConnectionWaitingView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-rCN", manifest = Config.NONE)
class PrefaceMaintenanceTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        for (name in listOf("diplay_car_hotspot", "xcertplay_airplay", "diplay"))
            app.getSharedPreferences(name, 0).edit().clear().commit()
        AirPlayPersistence.saveAutoStartOnBoot(app, false)
        CarPlayBackgroundSession.clear()
    }

    @Test fun savedHotspotDoesNotStartDiPlayAtBootWithoutAppStartupEnabled() {
        CarHotspotSettings.setEnabled(app, true)
        AirPlayPersistence.saveWirelessEnabled(app, true)
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveManualHotspotSsid(app, "Car WiFi")
        AirPlayPersistence.saveManualHotspotPassphrase(app, "12345678")
        var launch: Intent? = null
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { launch = intent }
        }
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(launch)
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(app))
        val report = StartupDiagnosticSnapshot.report(app)
        assertTrue(report.contains("hotspotLaunchAtBoot=false"))
        assertTrue(report.contains("hotspotAutoStartSetting=false"))
    }

    @Test fun factoryFailureIsPlacedWithSetupBeforeTheDailyConnectionAction() {
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val panel = ReflectionHelpers.getField<ConnectionWaitingView>(host.get(), "panel")
            ReflectionHelpers.callInstanceMethod<Unit>(host.get(), "showStatus",
                ReflectionHelpers.ClassParameter.from(String::class.java, "未完成：未发现 E01 原厂蓝牙服务"))
            assertEquals(View.VISIBLE, panel.failure.visibility)
            val controls = panel.controls
            assertTrue(controls.indexOfChild(panel.failure) >= 0)
            assertNotNull(panel.findViewWithTag<View>("bluetooth-connect"))
        } finally { host.pause().stop().destroy() }
    }
}

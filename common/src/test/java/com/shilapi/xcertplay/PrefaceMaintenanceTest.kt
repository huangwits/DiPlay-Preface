package com.shilapi.xcertplay

import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import com.shilapi.xcertplay.e01goc.E01GocActivity
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

    @Test fun savedHotspotCanStartAtBootIndependentlyOfAppStartupAndIgnoresOtherModes() {
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
        assertTrue(launch!!.getBooleanExtra("boot_hotspot", false))
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(app))
        launch = null
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.EXISTING_WIFI)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(launch)
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveManualHotspotSsid(app, "")
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(launch)
    }

    @Test fun factoryFailureIsPlacedWithSetupBeforeTheDailyConnectionAction() {
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val panel = ReflectionHelpers.getField<ConnectionWaitingView>(host.get(), "panel")
            ReflectionHelpers.callInstanceMethod<Unit>(host.get(), "showStatus",
                ReflectionHelpers.ClassParameter.from(String::class.java, "未完成：未发现 E01 原厂蓝牙服务"))
            assertEquals(View.VISIBLE, panel.failure.visibility)
            val controls = panel.controls
            assertTrue(controls.indexOfChild(panel.failure) >
                controls.indexOfChild(panel.findViewWithTag<View>("bluetooth-setup-hint")))
            assertTrue(controls.indexOfChild(panel.failure) <
                controls.indexOfChild(panel.findViewWithTag<View>("bluetooth-connect")))
        } finally { host.pause().stop().destroy() }
    }
}

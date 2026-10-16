package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.os.Looper
import com.geelyplay.preface.e01goc.E01RootBridge
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.network.CarHotspotTethering
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

/** Delayed factory replies must respect startup ordering and the activity's cancellation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29], manifest = Config.NONE,
    shadows = [HotspotStartupLifecycleTest.RootStartup::class, HotspotStartupLifecycleTest.FallbackStartup::class])
@LooperMode(LooperMode.Mode.PAUSED)
class HotspotStartupLifecycleTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private var controller: ActivityController<DiPlayActivity>? = null
    private var stopped = false

    @Before fun setUp() {
        for (name in listOf("diplay", "xcertplay_airplay", "diplay_car_hotspot", "diplay_startup_diagnostics"))
            app.getSharedPreferences(name, 0).edit().clear().commit()
        CarPlayBackgroundSession.clear()
        CarHotspotSettings.setEnabled(app, true)
        AirPlayPersistence.saveMfiTarget(app, MfiTarget.USB_CH341)
        AirPlayPersistence.saveManualHotspotSsid(app, "Car hotspot")
        AirPlayPersistence.saveManualHotspotPassphrase(app, "12345678")
        DiPlayPreferences.saveAutoConnect(app, false)
        DiPlayPreferences.saveDefaultConnectionMode(app, DefaultConnectionMode.USB)
        app.getSharedPreferences("diplay", 0).edit().putBoolean("notification_asked", true).commit()
        RootStartup.entered = CountDownLatch(1)
        RootStartup.release = CountDownLatch(1)
        RootStartup.worker = null
        RootStartup.ready = true
        FallbackStartup.calls = 0
    }

    @After fun tearDown() {
        finishRoot()
        controller?.let { if (!stopped) it.pause().stop(); it.destroy() }
        CarPlayBackgroundSession.clear()
    }

    @Test fun explicitManualModeDoesNotStartTheCarHotspotIndependently() {
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.MANUAL)
        val activity = launch()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(RootStartup.entered.await(0, TimeUnit.MILLISECONDS))
        assertEquals(0, FallbackStartup.calls)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun disabledBootStartupDoesNotStartTheCarHotspot() {
        AirPlayPersistence.saveAutoStartOnBoot(app, false)
        val activity = launch(Intent().putExtra("boot_hotspot", true))
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(RootStartup.entered.await(0, TimeUnit.MILLISECONDS))
        assertEquals(0, FallbackStartup.calls)
        assertFalse(activity.intent.hasExtra("boot_hotspot"))
    }

    @Test fun bootReceiverLaunchesDefaultAutomaticModeAndStartsSavedHotspot() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        val boot = shadowOf(app).nextStartedActivity
        assertNotNull(boot)
        assertTrue(boot.getBooleanExtra("boot_hotspot", false))
        val activity = launch(boot)
        assertTrue("boot did not attempt saved hotspot startup", RootStartup.entered.await(5, TimeUnit.SECONDS))
        assertFalse(activity.intent.hasExtra("boot_hotspot"))
        finishRoot()
        assertEquals(0, FallbackStartup.calls)
    }

    @Test fun automaticModeAtAppLaunchStartsSavedHotspotWithoutWaitingForWirelessConnect() {
        launch()
        assertTrue(RootStartup.entered.await(5, TimeUnit.SECONDS))
        finishRoot()
        assertEquals(0, FallbackStartup.calls)
    }

    @Test fun failedFactoryStartupTriesTheExistingTetheringFallback() {
        RootStartup.ready = false
        launch(Intent().putExtra("boot_hotspot", true))
        assertTrue(RootStartup.entered.await(5, TimeUnit.SECONDS))
        finishRoot()
        assertEquals(1, FallbackStartup.calls)
    }

    @Test fun disabledStartupAlsoPreventsBootReceiverLaunch() {
        AirPlayPersistence.saveAutoStartOnBoot(app, false)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun bootHotspotRequestSurvivesLauncherTakingTheForeground() {
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        launch(shadowOf(app).nextStartedActivity)
        assertTrue(RootStartup.entered.await(5, TimeUnit.SECONDS))
        controller!!.pause().stop()
        stopped = true
        finishRoot()
        assertEquals(0, FallbackStartup.calls)
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("hotspotRootResult=confirmed"))
    }

    @Test fun ordinaryLaunchCancelsLateFactoryReplyAfterTheUserLeaves() {
        RootStartup.ready = false
        launch()
        assertTrue(RootStartup.entered.await(5, TimeUnit.SECONDS))
        controller!!.pause().stop()
        stopped = true
        finishRoot()
        assertEquals(0, FallbackStartup.calls)
    }

    @Test fun anExistingCarPlaySessionPreventsIndependentHotspotStartup() {
        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", { done: () -> Unit -> done() })
        launch(Intent().putExtra("boot_hotspot", true))
        assertFalse(RootStartup.entered.await(0, TimeUnit.MILLISECONDS))
        assertEquals(0, FallbackStartup.calls)
    }

    @Test fun aSessionStartedDuringTheFactoryCallPreventsFallbackHotspotChanges() {
        RootStartup.ready = false
        launch(Intent().putExtra("boot_hotspot", true))
        assertTrue(RootStartup.entered.await(5, TimeUnit.SECONDS))
        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", { done: () -> Unit -> done() })
        finishRoot()
        assertEquals(0, FallbackStartup.calls)
    }

    @Test fun bootDeliveryToAnExistingActivityAlsoStartsTheAutomaticHotspot() {
        AirPlayPersistence.saveAutoStartOnBoot(app, false)
        launch()
        assertFalse(RootStartup.entered.await(0, TimeUnit.MILLISECONDS))
        AirPlayPersistence.saveAutoStartOnBoot(app, true)
        controller!!.newIntent(Intent().putExtra("boot_hotspot", true))
        assertTrue(RootStartup.entered.await(5, TimeUnit.SECONDS))
        finishRoot()
        assertEquals(0, FallbackStartup.calls)
    }

    private fun launch(intent: Intent = Intent(Intent.ACTION_MAIN)): DiPlayActivity {
        controller = Robolectric.buildActivity(DiPlayActivity::class.java, intent).create().start().resume()
        return controller!!.get()
    }

    private fun finishRoot() {
        RootStartup.release.countDown()
        RootStartup.worker?.join(5000)
        assertFalse("hotspot startup worker did not stop", RootStartup.worker?.isAlive == true)
    }

    @Implements(E01RootBridge::class, isInAndroidSdk = false)
    class RootStartup {
        @Suppress("UNUSED_PARAMETER")
        @Implementation fun startSavedHotspot(context: Context, isCancelled: () -> Boolean): Boolean {
            worker = Thread.currentThread()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            // A vendor Binder can reply successfully after the caller has cancelled.
            return ready
        }

        companion object {
            var entered = CountDownLatch(1)
            var release = CountDownLatch(1)
            @Volatile var worker: Thread? = null
            var ready = true
        }
    }

    @Implements(CarHotspotTethering::class, isInAndroidSdk = false)
    class FallbackStartup {
        @Suppress("UNUSED_PARAMETER")
        @Implementation fun enable(context: Context, isCancelled: () -> Boolean,
            timeoutMillis: Long, log: (String) -> Unit): CarHotspotTethering.Result {
            calls++
            return if (isCancelled()) CarHotspotTethering.Result.CANCELLED else CarHotspotTethering.Result.UNSUPPORTED
        }
        companion object { @Volatile var calls = 0 }
    }
}

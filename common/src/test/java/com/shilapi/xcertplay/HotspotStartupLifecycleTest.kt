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
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveManualHotspotSsid(app, "Car hotspot")
        AirPlayPersistence.saveManualHotspotPassphrase(app, "12345678")
        DiPlayPreferences.saveAutoConnect(app, true)
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

    @Test fun automaticConnectionWaitsForTheRootHotspotReply() {
        val activity = launch()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(activity).nextStartedActivity)

        finishRoot()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(CarPlayHostActivity::class.java.name,
            shadowOf(activity).nextStartedActivity?.component?.className)
        assertEquals(0, FallbackStartup.calls)
        assertFalse(AirPlayPersistence.loadWirelessEnabled(app))
    }

    @Test fun leavingTheActivityCancelsAutomaticConnectionDespiteALateRootSuccess() {
        val activity = launch()
        controller!!.pause().stop()
        stopped = true

        finishRoot()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(0, FallbackStartup.calls)
    }

    @Test fun cancellationAfterTheReplyWasQueuedStillPreventsAutomaticConnection() {
        val activity = launch()
        finishRoot()
        controller!!.pause().stop()
        stopped = true

        shadowOf(Looper.getMainLooper()).idle()

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun manualConnectionDoesNotGetRepeatedWhenTheRootReplyArrives() {
        val activity = launch()
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "connect",
            ReflectionHelpers.ClassParameter(Boolean::class.javaPrimitiveType!!, false))
        assertEquals(CarPlayHostActivity::class.java.name,
            shadowOf(activity).nextStartedActivity?.component?.className)

        finishRoot()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(0, FallbackStartup.calls)
    }

    @Test fun bootHotspotCanFinishWhenTheOemLauncherStopsTheActivity() {
        DiPlayPreferences.saveAutoConnect(app, false)
        StartupDiagnosticSnapshot.received(app, launchEnabled = true, hotspotLaunchEnabled = true)
        val activity = launch(Intent().putExtra("boot_hotspot", true))
        controller!!.pause().stop()
        stopped = true

        finishRoot()
        shadowOf(Looper.getMainLooper()).idle()

        val report = StartupDiagnosticSnapshot.report(app)
        assertTrue(report, report.contains("hotspotRootResult=confirmed hotspotResult=READY"))
        assertEquals(0, FallbackStartup.calls)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    private fun launch(intent: Intent = Intent(Intent.ACTION_MAIN)): DiPlayActivity {
        controller = Robolectric.buildActivity(DiPlayActivity::class.java, intent).create().start().resume()
        assertTrue("hotspot startup did not enter the root path", RootStartup.entered.await(5, TimeUnit.SECONDS))
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

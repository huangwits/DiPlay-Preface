package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.orchestration.CarPlayController
import java.io.Closeable
import java.io.File
import java.util.concurrent.ExecutorService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import android.view.View
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CarPlayConnectionDiagnosticLogTest {
    private lateinit var activity: CarPlayHostActivity
    private lateinit var listener: AirPlaySessionListener
    private val log get() = File(activity.filesDir, "logs/diplay.log").readText()

    @Before fun prepareOldControllerListener() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        activity.javaClass.getDeclaredMethod("initializeSessionLog").apply { isAccessible = true }.invoke(activity)
        activity.javaClass.getDeclaredField("restartGeneration").apply { isAccessible = true }.set(activity, 2)
        listener = activity.javaClass.getDeclaredMethod("createSessionListener", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, 1) as AirPlaySessionListener
    }

    @After fun cleanup() {
        assertTrue(AsyncDiagnosticLog.awaitIdle(2_000))
        activity.javaClass.getDeclaredField("sessionLog").apply { isAccessible = true }
            .get(activity).let { (it as Closeable).close() }
        for (field in listOf("teardownExecutor", "airPlayCommandExecutor")) {
            (activity.javaClass.getDeclaredField(field).apply { isAccessible = true }
                .get(activity) as ExecutorService).shutdownNow()
        }
    }

    @Test fun oldTeardownEvidenceSurvivesWithoutAcceptingOtherOldControllerLogs() {
        listener.onDebugLog("${CarPlayController.CONNECTION_DIAGNOSTIC_PREFIX} attempt=1 phase=CONTROL teardown end elapsedMs=117 executorTerminated=true")
        listener.onDebugLog("old controller ordinary state")
        assertTrue(AsyncDiagnosticLog.awaitIdle(2_000))
        assertTrue(log.contains("teardown end elapsedMs=117"))
        assertFalse(log.contains("old controller ordinary state"))
        assertEquals(2, activity.javaClass.getDeclaredField("restartGeneration").apply { isAccessible = true }.get(activity))
    }

    @Test fun theDiagnosticPrefixDoesNotBypassCredentialRedaction() {
        listener.onDebugLog("${CarPlayController.CONNECTION_DIAGNOSTIC_PREFIX} attempt=1 token=private-token")
        assertTrue(AsyncDiagnosticLog.awaitIdle(2_000))
        assertFalse(log.contains("private-token"))
    }

    @Test fun currentConnectionLogsReachThePanelWithoutOldGenerationOrCredentialLines() {
        val panel = buildPanel()
        val current = activity.javaClass.getDeclaredMethod("createSessionListener", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, 2) as AirPlaySessionListener
        current.onDebugLog("${CarPlayController.CONNECTION_DIAGNOSTIC_PREFIX} attempt=2 phase=USB_DISCOVERY")
        listener.onDebugLog("${CarPlayController.CONNECTION_DIAGNOSTIC_PREFIX} attempt=1 stale teardown")
        current.onDebugLog("token=private-token")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertTrue(panel.logText.text.contains("phase=USB_DISCOVERY"))
        assertFalse(panel.logText.text.contains("stale teardown"))
        assertFalse(panel.logText.text.contains("private-token"))
    }

    @Test fun lastFailureSurvivesTheNextWaitingStageAndLogsHideWithVideo() {
        val panel = buildPanel()
        @Suppress("UNCHECKED_CAST")
        val report = activity.javaClass.getDeclaredMethod("createStatusReporter", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, 2) as (CarPlayStatus) -> Unit
        report(CarPlayStatus.Failed("UsbManager could not open the iPhone", wifiResetRequired = true))
        report(CarPlayStatus.WaitingForIphone)
        assertTrue(panel.failure.text.contains("UsbManager could not open"))
        assertEquals(View.VISIBLE, panel.failure.visibility)
        assertTrue(panel.stage.text.contains("USB"))
        @Suppress("UNCHECKED_CAST")
        val streams = activity.javaClass.getDeclaredField("activeScreenStreamTypes")
            .apply { isAccessible = true }.get(activity) as MutableSet<Int>
        streams.add(110)
        val refresh = activity.javaClass.getDeclaredMethod("updateDebugOverlays").apply { isAccessible = true }
        refresh.invoke(activity)
        assertEquals(View.GONE, panel.visibility)
        streams.clear()
        refresh.invoke(activity)
        assertEquals(View.VISIBLE, panel.visibility)
        assertTrue(panel.failure.text.contains("UsbManager could not open"))
    }

    @Test fun highVolumeLogsRetainBoundedRecentHistory() {
        val panel = buildPanel()
        val append = activity.javaClass.getDeclaredMethod("appendLog", String::class.java).apply { isAccessible = true }
        repeat(500) { append.invoke(activity, "line-$it " + "x".repeat(300)) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertTrue(panel.logText.text.contains("line-499"))
        assertFalse(panel.logText.text.contains("line-0 "))
        assertTrue(panel.logText.text.length < 66_000)
    }

    private fun buildPanel(): ConnectionWaitingView {
        activity.javaClass.getDeclaredMethod("buildContentView").apply { isAccessible = true }.invoke(activity)
        return activity.javaClass.getDeclaredField("connectionWaitingView")
            .apply { isAccessible = true }.get(activity) as ConnectionWaitingView
    }
}

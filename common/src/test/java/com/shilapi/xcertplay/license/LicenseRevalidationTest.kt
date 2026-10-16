package com.shilapi.xcertplay.license

import android.net.Network
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import com.geelyplay.preface.license.LicenseRevalidation

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class LicenseRevalidationTest {
    private var online = false
    private var allowed = true
    private var calls = 0
    private var failure = false
    private val work = ArrayDeque<() -> Unit>()
    private val monitor = LicenseRevalidation(RuntimeEnvironment.getApplication(),
        refresh = { calls++; if (failure) throw java.io.IOException("no internet") },
        eligible = { allowed }, networkAvailable = { online }, execute = { work.add(it) })
    @After fun cleanup() { monitor.close() }
    private fun networkEvent() {
        monitor.networkCallback.onAvailable(mock(Network::class.java))
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun complete() { work.removeFirst().invoke(); shadowOf(Looper.getMainLooper()).idle() }
    private fun minute() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60)) }

    @Test fun doesNotRunUntilCarPlayStartsAndANetworkIsAvailable() {
        online = true; networkEvent()
        assertTrue(work.isEmpty())
        online = false; monitor.start(); minute()
        assertTrue(work.isEmpty())
        online = true; networkEvent()
        assertEquals(1, work.size)
        assertEquals(0, calls) // The connection/UI thread never does network work.
        complete(); assertEquals(1, calls)
    }

    @Test fun activeCarPlayWithInternetQueuesOneCheckDespiteDuplicateSignals() {
        online = true; monitor.start()
        repeat(5) { monitor.start(); networkEvent(); minute() }
        assertEquals(1, work.size)
        complete()
        networkEvent(); assertTrue(work.isEmpty())
        minute(); assertEquals(1, work.size)
    }

    @Test fun unavailableInternetRetriesInBackgroundWithoutAConnectAction() {
        online = true; failure = true; monitor.start(); complete()
        repeat(5) { networkEvent() }
        assertTrue(work.isEmpty())
        minute(); complete(); assertEquals(2, calls)
        failure = false; minute(); complete(); assertEquals(3, calls)
    }

    @Test fun periodicFallbackFindsNetworkEvenWithoutASystemCallback() {
        monitor.start(); online = true; minute()
        assertEquals(1, work.size)
    }

    @Test fun closeSuppressesQueuedSignalsAndLateCompletion() {
        online = true; monitor.start(); monitor.close(); complete()
        networkEvent(); minute()
        assertTrue(work.isEmpty()); assertEquals(1, calls)
    }

    @Test fun reconnectDoesNotOverlapAnEarlierRefresh() {
        online = true; monitor.start(); monitor.close(); monitor.start()
        networkEvent(); assertEquals(1, work.size)
        complete(); minute(); assertEquals(1, work.size)
    }

    @Test fun ordinaryUnlicensedBuildDoesNotScheduleChecks() {
        allowed = false; online = true; monitor.start(); networkEvent(); minute()
        assertTrue(work.isEmpty())
    }

    @Test fun hostStartsChecksOnWirelessSessionAndStopsThemAfterDisconnect() {
        val host = org.robolectric.Robolectric.buildActivity(com.shilapi.xcertplay.CarPlayHostActivity::class.java).get()
        org.robolectric.util.ReflectionHelpers.setField(host, "licenseRevalidation\$delegate", lazyOf(monitor))
        org.robolectric.util.ReflectionHelpers.setField(host, "wirelessEnabled", true)
        org.robolectric.util.ReflectionHelpers.setField(host, "menuOpen", true)
        val listener = org.robolectric.util.ReflectionHelpers.callInstanceMethod<com.shilapi.xcertplay.airplay.AirPlaySessionListener>(
            host, "createSessionListener", org.robolectric.util.ReflectionHelpers.ClassParameter(Int::class.javaPrimitiveType, 0))
        val session = mock(com.shilapi.xcertplay.airplay.AirPlaySession::class.java)
        try {
            online = true; assertTrue(work.isEmpty())
            listener.onSessionActive(session); shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, work.size)
            complete()
            listener.onSessionEnded(session); shadowOf(Looper.getMainLooper()).idle()
            networkEvent(); minute(); assertTrue(work.isEmpty())
        } finally {
            monitor.close()
            com.shilapi.xcertplay.CarPlayBackgroundSession.clear()
            for (field in listOf("airPlayCommandExecutor", "teardownExecutor")) {
                org.robolectric.util.ReflectionHelpers.getField<java.util.concurrent.ExecutorService>(host, field).shutdownNow()
            }
        }
    }
}

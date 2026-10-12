package com.shilapi.xcertplay

import android.content.Context
import android.os.Looper
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.network.CarHotspotTethering
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, shadows = [CarHotspotStartupOrderTest.Tethering::class])
class CarHotspotStartupOrderTest {
    @Before fun reset() {
        CarPlayBackgroundSession.clear()
        CarHotspotSettings.setEnabled(RuntimeEnvironment.getApplication(), true)
        Tethering.entered = CountDownLatch(1)
        Tethering.release = CountDownLatch(1)
    }

    @Test fun connectionContinuationWaitsForHotspotCompletion() = checkContinuation(cancel = false)

    @Test fun cancelledStartupDoesNotStartALateConnection() = checkContinuation(cancel = true)

    private fun checkContinuation(cancel: Boolean) {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        DiPlayActivity::class.java.getDeclaredField("page").apply { isAccessible = true }.set(activity, "settings")
        var continued = false
        val callback: () -> Unit = { continued = true }
        DiPlayActivity::class.java.getDeclaredMethod("startCarHotspotOnLaunch", Function0::class.java)
            .apply { isAccessible = true }.invoke(activity, callback)
        try {
            assertTrue(Tethering.entered.await(3, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(continued)
            if (cancel) DiPlayActivity::class.java.getDeclaredField("startupHotspotCancelled")
                .apply { isAccessible = true }.setBoolean(activity, true)
        } finally {
            Tethering.release.countDown()
            Tethering.worker?.join(3_000)
        }
        assertFalse(Tethering.worker!!.isAlive)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(!cancel, continued)
    }

    @Implements(CarHotspotTethering::class, isInAndroidSdk = false)
    class Tethering {
        @Implementation fun enable(context: Context, isCancelled: () -> Boolean, timeoutMillis: Long,
            log: (String) -> Unit): CarHotspotTethering.Result {
            worker = Thread.currentThread()
            entered.countDown()
            check(release.await(3, TimeUnit.SECONDS))
            return CarHotspotTethering.Result.READY
        }
        companion object {
            var entered = CountDownLatch(1)
            var release = CountDownLatch(1)
            @Volatile var worker: Thread? = null
        }
    }
}

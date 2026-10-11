package com.shilapi.xcertplay

import android.os.Looper
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.host.R
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.ExecutorService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29], manifest = Config.NONE)
class E01MapProjectionTest {
    private lateinit var activity: CarPlayHostActivity

    @Before fun prepare() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        for (key in listOf("vehicle_map_projection", "geely_projection_layout"))
            activity.getSharedPreferences(key, 0).edit().clear().commit()
        AirPlayPersistence.saveClusterMapEnabled(activity, false)
    }

    @After fun cleanup() {
        VehicleMapProjection.close(null)
        shadowOf(Looper.getMainLooper()).idle()
        for (field in listOf("teardownExecutor", "airPlayCommandExecutor"))
            ReflectionHelpers.getField<ExecutorService>(activity, field).shutdownNow()
    }

    @Test fun firstSwipeAfterPhoneReconnectIsNotDiscardedByTheDisplayPoll() {
        ShadowSettings.setCanDrawOverlays(true)
        ShadowDisplayManager.addDisplay("w1280dp-h480dp")
        VehicleMapSettings.select(activity, GeelyHudProjection.availableDisplays(activity).single())
        VehicleMapSettings.setEnabled(activity, true)
        val current = mock(CarPlayController::class.java)
        `when`(current.clusterProjectionSessionToken()).thenReturn(Any())
        VehicleMapProjection.attach(activity, current, mock(AndroidMediaSink::class.java), VehicleMapSettings.plan(activity))
        shadowOf(Looper.getMainLooper()).idle()
        val reconnected = Any()
        `when`(current.clusterProjectionSessionToken()).thenReturn(reconnected)
        VehicleMapProjection.flyNavigation(true)
        assertSame(reconnected, ReflectionHelpers.getField<Any>(VehicleMapProjection, "phone"))
        assertTrue(ReflectionHelpers.getField(VehicleMapProjection, "manual"))
        ShadowSettings.setCanDrawOverlays(false)
        assertEquals(R.string.projection_navigation_hidden, VehicleMapProjection.flyNavigation(false))
        assertFalse(ReflectionHelpers.getField(VehicleMapProjection, "manual"))
        assertNull(ReflectionHelpers.getField<Any?>(VehicleMapProjection, "root"))
    }

    @Test fun selectedInstrumentIsNegotiatedAndDoesNotReplaceMainVideo() {
        ShadowDisplayManager.addDisplay("w1280dp-h480dp")
        val screen = GeelyHudProjection.availableDisplays(activity).single()
        VehicleMapSettings.select(activity, screen)
        VehicleMapSettings.setEnabled(activity, true)
        val config = ReflectionHelpers.callInstanceMethod<AirPlayDisplayConfig>(activity, "clusterDisplayConfig")
        assertEquals(screen.width, config.widthPixels)
        assertEquals(screen.height, config.heightPixels)
        assertNotNull(ReflectionHelpers.getField<VehicleMapPlan?>(activity, "vehicleMapPlan"))
        val sink = mock(AndroidMediaSink::class.java)
        val surface = mock(Surface::class.java)
        ReflectionHelpers.setField(activity, "sink", sink)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "attachSurface",
            ReflectionHelpers.ClassParameter.from(Surface::class.java, surface))
        verify(sink).setSurface(110, surface)
        verify(sink, never()).setSurface(111, surface)
        ShadowDisplayManager.removeDisplay(screen.id)
        assertNull(ReflectionHelpers.callInstanceMethod<AirPlayDisplayConfig?>(activity, "clusterDisplayConfig"))
    }

    @Test fun missingDisplayNeverAdvertisesACompleteMap() {
        VehicleMapSettings.setEnabled(activity, true)
        assertNull(ReflectionHelpers.callInstanceMethod<AirPlayDisplayConfig?>(activity, "clusterDisplayConfig"))
        assertNull(ReflectionHelpers.getField<VehicleMapPlan?>(activity, "vehicleMapPlan"))
    }

    @Suppress("DEPRECATION")
    @Test fun e01UsesALegacyOverlayWindow() {
        assertEquals(WindowManager.LayoutParams.TYPE_PHONE, VehicleMapProjection.windowType(22))
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, VehicleMapProjection.windowType(26))
    }

    @Test fun horizontalAndUpwardGesturesAreDistinctFromSettingsAndSmallMovement() {
        assertEquals(NavigationFlyGesture.SHOW, NavigationFlyGesture.detect(-100f, 5f, 72f))
        assertEquals(NavigationFlyGesture.HIDE, NavigationFlyGesture.detect(100f, -5f, 72f))
        assertEquals(NavigationFlyGesture.TOGGLE, NavigationFlyGesture.detect(5f, -100f, 72f))
        assertNull(NavigationFlyGesture.detect(5f, 100f, 72f))
        assertNull(NavigationFlyGesture.detect(10f, 15f, 72f))
        assertNull(NavigationFlyGesture.detect(100f, 100f, 72f))
    }

    @Test fun threeFingerSwipeIsConsumedOnceUntilAllFingersLift() {
        val view = View(activity)
        fun touch(action: Int, count: Int, x: Float) {
            val properties = Array(count) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(count) { MotionEvent.PointerCoords().apply { this.x = x; y = 100f + it * 20; pressure = 1f; size = 1f } }
            val event = MotionEvent.obtain(1, 2, action, count, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
            try { ReflectionHelpers.callInstanceMethod<Boolean>(activity, "onHostTouch",
                ReflectionHelpers.ClassParameter.from(View::class.java, view),
                ReflectionHelpers.ClassParameter.from(MotionEvent::class.java, event)) }
            finally { event.recycle() }
        }
        touch(MotionEvent.ACTION_DOWN, 1, 500f)
        touch(MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 3, 500f)
        touch(MotionEvent.ACTION_MOVE, 3, 0f)
        assertTrue(ReflectionHelpers.getField(activity, "navigationGestureUsed"))
        assertFalse(ReflectionHelpers.getField(activity, "gestureTracking"))
        touch(MotionEvent.ACTION_MOVE, 3, 500f)
        assertTrue(ReflectionHelpers.getField(activity, "gestureSequenceActive"))
        touch(MotionEvent.ACTION_UP, 1, 500f)
        assertFalse(ReflectionHelpers.getField(activity, "gestureSequenceActive"))
        touch(MotionEvent.ACTION_DOWN, 1, 500f)
        assertFalse(ReflectionHelpers.getField(activity, "navigationGestureUsed"))
    }
}

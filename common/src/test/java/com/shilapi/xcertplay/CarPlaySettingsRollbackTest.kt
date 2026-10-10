package com.shilapi.xcertplay

import android.os.Handler
import android.view.View
import android.view.Surface
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.ExecutorService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28])
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlaySettingsRollbackTest {
    private lateinit var activity: CarPlayHostActivity
    private var layoutPosts = 0

    @Before fun setup() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        set("videoView", object : View(activity) {
            override fun post(action: Runnable): Boolean { layoutPosts++; return true }
        })
        invoke<Unit>("loadPersistedSettings")
        set("settingsBaseline", invoke<Any>("captureSettingsBaseline"))
    }

    @After fun close() {
        ReflectionHelpers.getField<Handler>(activity, "mainHandler").removeCallbacksAndMessages(null)
        for (name in listOf("teardownExecutor", "airPlayCommandExecutor"))
            ReflectionHelpers.getField<ExecutorService>(activity, name).shutdownNow()
    }

    @Test fun unchangedMenuDoesNotScheduleDisplayRenegotiation() {
        assertFalse(invoke<Boolean>("hasPendingSettingsChanges"))
        invoke<Unit>("restoreSettingsBaseline")
        assertEquals(0, layoutPosts)
    }

    @Test fun editingAndRevertingValuesClearsPendingChanges() {
        val old = ReflectionHelpers.getField<Int>(activity, "fps")
        set("fps", old + 5)
        assertTrue(invoke<Boolean>("hasPendingSettingsChanges"))
        set("fps", old)
        assertFalse(invoke<Boolean>("hasPendingSettingsChanges"))
    }

    @Test fun cancellingBarPreviewRestoresBarsAndRemeasures() {
        val saved = AirPlayPersistence.loadHideTopBar(activity)
        set("hideTopBar", !saved)
        assertTrue(invoke<Boolean>("hasPendingSettingsChanges"))
        invoke<Unit>("restoreSettingsBaseline")
        assertEquals(saved, ReflectionHelpers.getField<Boolean>(activity, "hideTopBar"))
        assertEquals(1, layoutPosts)
    }

    @Test fun squareCanvasUsesOriginalWindowAspectAndStillDetectsRealChanges() {
        AirPlayPersistence.saveAdaptPipResolution(activity, true)
        val top = ReflectionHelpers.getField<Boolean>(activity, "hideTopBar")
        val bottom = ReflectionHelpers.getField<Boolean>(activity, "hideBottomBar")
        set("sessionDisplay", CarPlaySessionDisplay(1280, 1280, Surface.ROTATION_0, top, bottom, 1280, 720))
        assertFalse(layoutChanged(1280, 720))
        assertTrue(layoutChanged(640, 720))
        set("hideTopBar", !top)
        assertTrue(layoutChanged(1280, 720))
    }

    private fun layoutChanged(width: Int, height: Int): Boolean {
        val type = Class.forName("com.shilapi.xcertplay.CarPlayHostActivity\$DisplaySize")
        val size = type.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.newInstance(width, height)
        return activity.javaClass.getDeclaredMethod("displayLayoutChanged", type)
            .apply { isAccessible = true }.invoke(activity, size) as Boolean
    }
    private fun set(name: String, value: Any) = ReflectionHelpers.setField(activity, name, value)
    private fun <T> invoke(name: String): T = ReflectionHelpers.callInstanceMethod(activity, name)
}

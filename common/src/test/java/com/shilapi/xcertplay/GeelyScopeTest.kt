package com.shilapi.xcertplay

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33], qualifiers = "zh-rCN")
class GeelyScopeTest {
    @Test fun settingsKeepGeelyControlsAndIgnoreSavedVendorOptions() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay_byd_outputs", 0).edit()
            .putBoolean("enabled", true).putBoolean("battery_to_iphone", true).commit()
        app.getSharedPreferences("diplay_carplay_rotation", 0).edit().putBoolean("enabled", true).commit()
        DiPlayPreferences.saveAutoConnect(app, false)
        val host = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(app, DiPlayActivity::class.java).putExtra("page", "settings")).setup()
        try {
            val activity = host.get()
            val texts = descendants(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
            assertFalse(texts.any { it.contains("应用语言") || it.contains("App language") })
            assertTrue(texts.contains(activity.getString(R.string.geely_vehicle)))
            assertTrue(texts.contains(activity.getString(R.string.steering_identification)))
            assertFalse(texts.any { it.contains("BYD", true) || it.contains("DiLink", true) || it.contains("比亚迪") })
            for (name in listOf("BydNavigationOutputs", "BydBatteryStatus", "BydParkedState")) {
                assertTrue(runCatching { Class.forName("com.shilapi.xcertplay.hud.$name") }.isFailure)
            }
        } finally {
            host.pause().stop().destroy()
        }
    }

    @Test fun oldDefaultBrandMigratesButCustomCarButtonLabelSurvives() {
        val app = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveOemLabel(app, "BYD")
        assertNotEquals("BYD", AirPlayPersistence.loadOemLabel(app))
        AirPlayPersistence.saveOemLabel(app, "我的星瑞")
        assertEquals("我的星瑞", AirPlayPersistence.loadOemLabel(app))
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}

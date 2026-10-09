package com.shilapi.xcertplay

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.e01goc.E01GocActivity
import com.shilapi.xcertplay.orchestration.MfiTarget
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "zh-rCN")
class E01FactoryBluetoothUiTest {
    @Test fun connectionSettingsKeepFactoryToolAndPhoneWithoutTheRetiredSwitch() {
        val app = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveMfiTarget(app, MfiTarget.USB_CH341)
        DiPlayPreferences.saveAutoConnect(app, false)
        DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "Saved phone")
        val host = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(app, DiPlayActivity::class.java).putExtra("page", "connection")).setup()
        try {
            val labels = views(host.get().window.decorView).filterIsInstance<TextView>().toList()
            assertFalse(labels.any { "mtk-su" in it.text || "备用蓝牙" in it.text })
            labels.single { it.text.toString() == "蓝牙工具" }.performClick()
            assertEquals(E01GocActivity::class.java.name,
                shadowOf(host.get()).nextStartedActivity.component?.className)
            assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(app))
        } finally { host.pause().stop().destroy() }
    }

    @Test fun factoryToolDiscardsOldHistoryAndDoesNotCreateANewLog() {
        val app = RuntimeEnvironment.getApplication()
        val history = java.io.File(app.filesDir, "e01-goc-last-result.txt")
        val retiredHistory = java.io.File(app.filesDir, "last-result.txt")
        history.writeText("old diagnostic history")
        retiredHistory.writeText("retired fallback history")
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val labels = views(host.get().window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
            assertFalse(labels.any { "日志" in it || "诊断" in it || "old diagnostic history" in it })
            assertFalse(history.exists())
            assertFalse(retiredHistory.exists())
            assertFalse(java.io.File(app.filesDir, "e01-goc/manage.sh").exists())
            assertFalse(app.getFileStreamPath("adb.private").exists())
            E01GocActivity::class.java.getDeclaredMethod("showStatus", String::class.java)
                .apply { isAccessible = true }.invoke(host.get(), "已选择 iPhone")
            assertTrue(views(host.get().window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "已选择 iPhone" })
            assertFalse(history.exists())
        } finally { host.pause().stop().destroy() }
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }
}

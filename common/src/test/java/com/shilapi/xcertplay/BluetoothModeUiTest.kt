package com.shilapi.xcertplay

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.shilapi.xcertplay.e01goc.E01GocActivity
import com.shilapi.xcertplay.e01goc.E01GocPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "zh-rCN")
class BluetoothModeUiTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test fun openingAndResumingOnlyShowsControlsForTheSelectedModeWithoutMaintenance() {
        E01GocPreferences.select(app, false)
        DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "我的 iPhone")
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            assertFalse(visible(activity).any { it == "连接测试" || it == "安装适配" || it == "还原备份" })
            assertTrue("打开安卓蓝牙设置" in visible(activity))
            assertTrue("当前模式：安卓蓝牙（切换）" in visible(activity))
            assertTrue("更换手机 · 我的 iPhone" in visible(activity))
            assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(app))
            host.pause()
            E01GocPreferences.select(app, true)
            host.resume()
            assertTrue("连接测试" in visible(activity))
            assertTrue("当前模式：原厂蓝牙（切换）" in visible(activity))
            assertFalse("打开安卓蓝牙设置" in visible(activity))
            assertFalse(visible(activity).any { "安卓" in it })
            host.pause()
            E01GocPreferences.select(app, false)
            host.resume()
            assertFalse(visible(activity).any { "原厂" in it || "安装适配" in it })
            assertFalse(java.io.File(app.filesDir, "e01-goc/manage.sh").exists())
        } finally { host.pause().stop().destroy() }
    }

    @Test fun switchRequiresConfirmationThenClearsThePreviousPhoneAndRefreshesControls() {
        E01GocPreferences.select(app, false)
        DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "旧手机")
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            fun chooseFactory(): AlertDialog {
                activity.window.decorView.findViewWithTag<Button>("bluetooth-mode").performClick()
                val picker = ShadowAlertDialog.getLatestAlertDialog()
                picker.listView.performItemClick(null, 1, 1)
                shadowOf(Looper.getMainLooper()).idle()
                return ShadowAlertDialog.getLatestAlertDialog()
            }
            chooseFactory().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(E01GocPreferences.enabled(app))
            assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(app))
            chooseFactory().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            awaitWork(activity)
            assertTrue(visible(activity).joinToString("\n"), E01GocPreferences.enabled(app))
            assertNull(DiPlayPreferences.phoneAddress(app))
            assertTrue("连接测试" in visible(activity))
            assertFalse("打开安卓蓝牙设置" in visible(activity))
            assertFalse(java.io.File(app.filesDir, "e01-goc/manage.sh").exists())
        } finally { host.pause().stop().destroy() }
    }

    @Test fun androidPickerSavesTheBondedPhoneWithoutSelectingFactoryTransport() {
        E01GocPreferences.select(app, false)
        val adapter = BluetoothAdapter.getDefaultAdapter()
        shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
        val phone = adapter.getRemoteDevice("11:22:33:44:55:66")
        shadowOf(phone).setName("我的 iPhone")
        shadowOf(adapter).setBondedDevices(setOf(phone))
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            activity.window.decorView.findViewWithTag<Button>("bluetooth-phone").performClick()
            val picker = ShadowAlertDialog.getLatestAlertDialog()
            picker.listView.performItemClick(null, 0, 0)
            awaitWork(activity)
            assertEquals(phone.address, DiPlayPreferences.phoneAddress(app))
            assertFalse(E01GocPreferences.enabled(app))
            assertTrue("更换手机 · 我的 iPhone" in visible(activity))
            assertFalse(java.io.File(app.filesDir, "e01-goc/manage.sh").exists())
        } finally { host.pause().stop().destroy() }
    }

    private fun awaitWork(activity: E01GocActivity) {
        for (attempt in 0 until 1000) {
            if (!ReflectionHelpers.getField<Boolean>(activity, "working")) return
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(10))
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        val workers = Thread.getAllStackTraces().filterKeys { it.name == "diplay-e01-maintenance" }
            .map { (thread, stack) -> "${thread.state}: ${stack.joinToString("\n")}" }.joinToString("\n")
        assertFalse("operation completed: $workers", ReflectionHelpers.getField<Boolean>(activity, "working"))
    }

    private fun visible(activity: E01GocActivity) = views(activity.window.decorView)
        .filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }.map { it.text.toString() }.toList()
    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }
}

package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.e01switch.E01BluetoothSwitchActivity
import com.shilapi.xcertplay.orchestration.MfiTarget
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "zh-rCN")
class E01BluetoothSwitchUiTest {
    @Test fun connectionSettingsOpenInternalToolWithoutChangingPhoneSelection() {
        val app = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveMfiTarget(app, MfiTarget.USB_CH341)
        DiPlayPreferences.saveAutoConnect(app, false)
        DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "Saved phone")
        val host = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(app, DiPlayActivity::class.java).putExtra("page", "connection")).setup()
        try {
            views(host.get().window.decorView).filterIsInstance<TextView>()
                .single { it.text.toString() == "打开蓝牙切换工具" }.performClick()
            assertEquals(E01BluetoothSwitchActivity::class.java.name,
                shadowOf(host.get()).nextStartedActivity.component?.className)
            assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(app))
        } finally { host.pause().stop().destroy() }
    }

    @Test fun api22ToolOpensWithActionsAndScriptsWithoutExecutingCommands() {
        val sdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        try {
            val host = Robolectric.buildActivity(E01BluetoothSwitchActivity::class.java).setup()
            try {
                val labels = views(host.get().window.decorView).filterIsInstance<TextView>()
                    .map { it.text.toString() }.toList()
                for (label in listOf("检查连接", "尝试切换", "恢复原厂", "复制结果")) {
                    assertTrue(label, labels.contains(label))
                }
                for (asset in listOf("switch.sh", "restore.sh")) {
                    val script = host.get().assets.open("e01-bluetooth/$asset").bufferedReader().readText()
                    assertTrue(script.contains("id -u"))
                }
                assertFalse(host.get().getFileStreamPath("adb.private").exists())
            } finally { host.pause().stop().destroy() }
        } finally { ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk) }
    }

    @Test fun successfulSwitchClearsLegacyBackendSelectionAndKeepsOtherPreferences() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("factory_bluetooth_enabled", true)
            .putString("factory_bluetooth_backend", "e01").commit()
        DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "Old vendor phone")
        DiPlayPreferences.saveAutoConnect(app, true)
        E01Settings.setEnabled(app, true)
        E01BluetoothSwitchIntegration.useSystem(app)
        assertFalse(prefs.getBoolean("factory_bluetooth_enabled", true))
        assertFalse(prefs.contains("factory_bluetooth_backend"))
        assertNull(DiPlayPreferences.phoneAddress(app))
        assertTrue(DiPlayPreferences.autoConnect(app))
        assertTrue(E01Settings.enabled(app))
    }

    @Test fun successfulSwitchKeepsAnExistingSystemBluetoothPhone() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit()
            .putBoolean("factory_bluetooth_enabled", false).commit()
        DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "System phone")
        E01BluetoothSwitchIntegration.useSystem(app)
        assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(app))
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }
}

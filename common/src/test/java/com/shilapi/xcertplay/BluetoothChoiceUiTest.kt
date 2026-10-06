package com.shilapi.xcertplay

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.transport.FactoryBluetoothTransport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33], qualifiers = "zh-rCN")
@LooperMode(LooperMode.Mode.PAUSED)
class BluetoothChoiceUiTest {
    @Test fun allInterfacesCanBeSelectedWithoutEnablingE01PerformanceOrQueryingAVendor() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        E01Settings.setEnabled(app, false)
        AirPlayPersistence.saveMfiTarget(app, MfiTarget.USB_CH341)
        DiPlayPreferences.saveAutoConnect(app, false)
        val before = FactoryBluetoothTransport.diagnosticSummary
        val host = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(app, DiPlayActivity::class.java).putExtra("page", "connection")).setup()
        val activity = host.get()
        try {
            assertEquals(FactoryBluetoothSettings.Choice.SYSTEM, FactoryBluetoothSettings.choice(app))
            for (selected in listOf(FactoryBluetoothSettings.Choice.H52,
                FactoryBluetoothSettings.Choice.E01, FactoryBluetoothSettings.Choice.SYSTEM)) {
                DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "Previous phone")
                descendants(activity.window.decorView).filterIsInstance<TextView>()
                    .single { it.text.startsWith(activity.getString(R.string.factory_bt_backend) + " · ") }
                    .performClick()
                val dialog = ShadowAlertDialog.getLatestAlertDialog()
                val list = dialog.listView
                assertEquals(3, list.count)
                assertEquals(activity.getString(R.string.factory_bt_system), list.adapter.getItem(0))
                assertEquals(activity.getString(R.string.factory_bt_ecarx), list.adapter.getItem(1))
                assertEquals(activity.getString(R.string.factory_bt_anw), list.adapter.getItem(2))
                list.performItemClick(null, selected.ordinal, selected.ordinal.toLong())
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(selected, FactoryBluetoothSettings.choice(app))
                assertNull(DiPlayPreferences.phoneAddress(app))
                assertEquals(before, FactoryBluetoothTransport.diagnosticSummary)
                assertFalse(E01Settings.enabled(app))
            }
        } finally {
            host.pause().stop().destroy()
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}

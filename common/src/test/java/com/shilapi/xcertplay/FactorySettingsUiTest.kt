package com.shilapi.xcertplay

import android.app.AlertDialog
import android.content.Intent
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import com.geelyplay.preface.AudioStreamPreferences
import com.geelyplay.preface.FactoryKeyAction
import com.geelyplay.preface.FactorySteeringKeys
import com.geelyplay.preface.SoundRole

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "zh-rCN-w800dp-h480dp")
class FactorySettingsUiTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private var screen: DiPlayActivity? = null
    @After fun cleanup() {
        ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
        screen?.finish()
        listOf("audio_stream_routes", "factory_steering_keys", "xcertplay_airplay", "diplay").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
    }
    private fun open(): DiPlayActivity = Robolectric.buildActivity(DiPlayActivity::class.java,
        Intent(app, DiPlayActivity::class.java).putExtra("page", "settings"))
        .setup().get().also { screen = it }
    private fun texts(view: View): Sequence<TextView> = sequence {
        if (view is TextView) yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(texts(view.getChildAt(i)))
    }
    @Test fun channelSelectionSavesRealZeroAndCancelPreservesIt() {
        val activity = open()
        val parent = LinearLayout(activity)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "soundChannelControl",
            ReflectionHelpers.ClassParameter(LinearLayout::class.java, parent),
            ReflectionHelpers.ClassParameter(SoundRole::class.java, SoundRole.PHONE))
        parent.getChildAt(0).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        texts(dialog.window!!.decorView).single { it.text.toString() == "0" }.performClick()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, AudioStreamPreferences.load(app, SoundRole.PHONE))
        assertTrue((parent.getChildAt(0) as TextView).text.endsWith("0"))
        parent.getChildAt(0).performClick()
        val cancelled = ShadowAlertDialog.getLatestAlertDialog()
        texts(cancelled.window!!.decorView).single { it.text.toString() == "恢复默认 · 6" }.performClick()
        cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, AudioStreamPreferences.load(app, SoundRole.PHONE))
        parent.getChildAt(0).performClick()
        val restored = ShadowAlertDialog.getLatestAlertDialog()
        texts(restored.window!!.decorView).single { it.text.toString() == "恢复默认 · 6" }.performClick()
        restored.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(6, AudioStreamPreferences.load(app, SoundRole.PHONE))
    }
    @Test fun learnedKeyRequiresSaveAndCanBeDisabledOrReset() {
        val activity = open()
        val parent = LinearLayout(activity)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "factorySteeringControls",
            ReflectionHelpers.ClassParameter(LinearLayout::class.java, parent))
        val control = texts(parent).single { it.text.startsWith("上一曲 ·") }
        control.performClick()
        val cancelled = ShadowAlertDialog.getLatestAlertDialog()
        assertFalse(cancelled.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        cancelled.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, 90))
        assertTrue(cancelled.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf(89, 88), FactorySteeringKeys.keys(app, FactoryKeyAction.PREVIOUS))
        control.performClick()
        val saved = ShadowAlertDialog.getLatestAlertDialog()
        saved.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, 90))
        saved.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf(90), FactorySteeringKeys.keys(app, FactoryKeyAction.PREVIOUS))
        control.performClick()
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(FactorySteeringKeys.keys(app, FactoryKeyAction.PREVIOUS).isEmpty())
        texts(parent).single { it.text.toString() == "恢复默认按键" }.performClick()
        assertEquals(setOf(89, 88), FactorySteeringKeys.keys(app, FactoryKeyAction.PREVIOUS))
    }
}

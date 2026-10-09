package com.shilapi.xcertplay.license

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.ConnectionWaitingView
import com.shilapi.xcertplay.e01goc.E01GocActivity
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowResources
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-rCN-w960dp-h360dp-mdpi", shadows = [BluetoothLicenseLayoutTest.LicensedResources::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BluetoothLicenseLayoutTest {
    @Implements(Resources::class)
    class LicensedResources : ShadowResources() {
        @RealObject private lateinit var actual: Resources
        @Implementation fun getBoolean(id: Int): Boolean = if (id == R.bool.config_online_license) true
            else Shadow.directlyOn(actual, Resources::class.java, "getBoolean", ClassParameter.from(Int::class.javaPrimitiveType, id))
    }

    @Test fun homeNeverShowsAuthorizationWhenBluetoothChanges() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val host = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        try {
            for (state in listOf(BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_ON)) {
                shadowOf(adapter).setState(state)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
                assertNull(host.get().window.decorView.findViewWithTag<View>("online-license-panel"))
            }
        } finally { host.pause().stop().destroy() }
    }

    @Test fun factoryToolsKeepAuthorizationOnTheRightAcrossWindowSizes() {
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val row = root.findViewWithTag<LinearLayout>("bluetooth-license-columns")
            val left = row.getChildAt(0) as ConnectionWaitingView
            val right = root.findViewWithTag<LicensePanel>("online-license-panel")
            for ((width, height) in listOf(1280 to 480, 960 to 360, 480 to 800)) {
                layout(root, width, height)
                assertTrue(left.right < right.left)
                assertEquals(left.height, right.height)
                assertTrue(right.right <= row.width && right.bottom <= row.height)
                assertNull(left.logPanel.parent)
                val labels = views(left).filterIsInstance<Button>().map { it.text.toString() }.toList()
                for (text in listOf("检查状态", "选择手机", "连接测试", "安装适配", "还原备份", "标准蓝牙", "返回")) assertTrue(text in labels)
                assertFalse(labels.any { it.contains("原厂已连接") || it.contains("临时兼容测试") })
                assertFalse(views(root).filterIsInstance<TextView>().any { "微信" in it.text || "starts181004" in it.text })
                screenshot(root, "bluetooth-${width}x${height}", width, height)
                right.isSmoothScrollingEnabled = false
                right.fullScroll(View.FOCUS_DOWN)
                val card = right.findViewWithTag<CommunityCard>("license-community-card")
                assertTrue(card.bottom <= right.height + right.scrollY)
                val caption = card.findViewWithTag<TextView>("community-caption")
                val image = views(card).filterIsInstance<ImageView>().single()
                assertEquals("QQ群 892351951\nQQ 扫码 · 点图放大", caption.text.toString())
                assertTrue(caption.top >= image.bottom)
                assertEquals(14f, caption.textSize / activity.resources.displayMetrics.scaledDensity, 0.1f)
                assertEquals(1, views(card).filterIsInstance<TextView>().count())
                assertFalse(views(card).any { it is Button })
                screenshot(root, "community-${width}x${height}", width, height)
                right.scrollTo(0, 0)
            }
            assertFalse(File(activity.filesDir, "e01-goc/manage.sh").exists())
            assertTrue(ReflectionHelpers.getField<Boolean>(right, "foreground"))
            host.pause()
            assertFalse(ReflectionHelpers.getField<Boolean>(right, "foreground"))
            host.resume()
            assertTrue(ReflectionHelpers.getField<Boolean>(right, "foreground"))
            left.applyTheme(true); right.applyTheme(true)
            root.setBackgroundColor(com.shilapi.xcertplay.WaitingScreenColors.of(true).background)
            layout(root, 960, 360)
            screenshot(root, "bluetooth-night", 960, 360)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun authorizedReturnHidesTheWholeRightColumnAndKeepsDailyConnectionOnTheLeft() {
        ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", Long.MAX_VALUE)
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val row = root.findViewWithTag<LinearLayout>("bluetooth-license-columns")
            val left = row.getChildAt(0) as ConnectionWaitingView
            val right = row.getChildAt(1) as LicensePanel
            assertEquals(View.GONE, right.visibility)
            val connect = left.findViewWithTag<Button>("bluetooth-connect")
            assertTrue(connect.isEnabled)
            assertTrue(left.findViewWithTag<TextView>("bluetooth-license-status").text.contains("授权已通过"))
            for ((width,height) in listOf(1280 to 480, 960 to 360, 480 to 800)) {
                layout(root,width,height)
                assertTrue(left.width >= row.width - row.paddingLeft - row.paddingRight)
                screenshot(root,"authorized-${width}x${height}",width,height)
            }
            connect.performClick()
            val next = shadowOf(activity).nextStartedActivity
            assertEquals("wireless", next.getStringExtra("authorized_connection"))
            assertEquals(DiPlayActivity::class.java.name, next.component!!.className)
        } finally {
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            host.pause().stop().destroy()
        }
    }

    @Test fun authorizationLossReopensThePanelAndDisablesTheDailyConnectButton() {
        ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", Long.MAX_VALUE)
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val root = host.get().window.decorView
            val right = root.findViewWithTag<LicensePanel>("online-license-panel")
            assertEquals(View.GONE,right.visibility)
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            right.refreshAdmission()
            assertEquals(View.VISIBLE,right.visibility)
            assertFalse(root.findViewWithTag<Button>("bluetooth-connect").isEnabled)
            assertNull(shadowOf(host.get()).nextStartedActivity)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun detailedGuideExplainsFirstSetupDailyUseAndRestoreWithoutStartingMaintenance() {
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            views(activity.window.decorView).filterIsInstance<Button>().single { it.text == "完整操作说明" }.performClick()
            val dialog = ShadowDialog.getLatestDialog() as android.app.AlertDialog
            val text = dialog.findViewById<TextView>(android.R.id.message).text.toString()
            for (part in listOf("日常使用", "第一次使用", "只验证握手", "只有测试通过", "重新连接车机蓝牙", "不会卸载适配", "USB 连接无需软件激活")) assertTrue(part,text.contains(part))
            assertFalse(File(activity.filesDir,"e01-goc/manage.sh").exists())
            assertFalse(ReflectionHelpers.getField<Boolean>(activity,"working"))
            dialog.dismiss()
        } finally { host.pause().stop().destroy() }
    }

    @Test fun groupCaptionStaysBelowArtworkWithoutCopyAndViewerStillEnlarges() {
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            val card = activity.window.decorView.findViewWithTag<CommunityCard>("license-community-card")
            assertFalse(views(card).any { it is Button })
            assertFalse(views(card).filterIsInstance<TextView>().any { "星瑞交流群" in it.text || "复制群号" in it.text })
            views(card).filterIsInstance<ImageView>().single().performClick()
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            val root = dialog.window!!.decorView.findViewWithTag<View>("community-qr-dialog")
            layout(root, 960, 360)
            screenshot(root, "community-enlarged", 960, 360)
            val image = views(root).filterIsInstance<ImageView>().single()
            assertTrue(image.drawable.intrinsicWidth >= 900)
            host.pause()
            assertFalse(dialog.isShowing)
        } finally { host.stop().destroy() }
    }

    @Test fun usbConnectUsesOrdinaryProjectionWithoutActivationAndDoesNotUnlockWireless() {
        val host = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        try {
            val activity = host.get()
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            ReflectionHelpers.setStaticField(AppLicense::class.java, "lastPrompt", -10000L)
            assertFalse(AppLicense.canStart(activity))
            assertFalse(AppLicense.requireActivation(activity, false))
            assertNull(shadowOf(activity).nextStartedActivity)
            ReflectionHelpers.setField(activity, "setupError", null)
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "connect", ClassParameter.from(Boolean::class.javaPrimitiveType, false))
            val next = shadowOf(activity).nextStartedActivity
            assertEquals(com.shilapi.xcertplay.CarPlayHostActivity::class.java.name, next.component!!.className)
            assertFalse(com.shilapi.xcertplay.AirPlayPersistence.loadWirelessEnabled(activity))
            // The host's second admission check uses the persisted transport and must also allow USB.
            assertFalse(AppLicense.requireActivation(activity))
            assertNull(shadowOf(activity).nextStartedActivity)
            assertTrue(AppLicense.requireActivation(activity, true))
            assertEquals(LicenseActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun homeRequiresWirelessActivationEvenWhenAnIdleBackgroundControllerExists() {
        val host = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        try {
            val activity = host.get()
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            ReflectionHelpers.setStaticField(AppLicense::class.java, "lastPrompt", -10000L)
            com.shilapi.xcertplay.AirPlayPersistence.saveWirelessEnabled(activity, true)
            val stop: ((() -> Unit) -> Unit) = { it() }
            ReflectionHelpers.setStaticField(com.shilapi.xcertplay.CarPlayBackgroundSession::class.java, "stopAction", stop)
            com.shilapi.xcertplay.CarPlayBackgroundSession.active = false
            val button = ReflectionHelpers.getField<Button>(activity, "connectButton")
            button.performClick()
            val next = shadowOf(activity).nextStartedActivity
            assertEquals(LicenseActivity::class.java.name, next.component!!.className)
            assertTrue(next.getBooleanExtra("license_wireless", false))
            assertNull(shadowOf(activity).nextStartedActivity)
            // Returning to a live CarPlay remains independent of admission expiry.
            com.shilapi.xcertplay.CarPlayBackgroundSession.active = true
            button.performClick()
            assertEquals(com.shilapi.xcertplay.CarPlayHostActivity::class.java.name,
                shadowOf(activity).nextStartedActivity.component!!.className)
        } finally {
            com.shilapi.xcertplay.CarPlayBackgroundSession.clear()
            host.pause().stop().destroy()
        }
    }

    @Test fun controllerChecksActualWirelessModeEvenWhenSavedPreferenceStillSaysUsb() {
        val activity = Robolectric.buildActivity(com.shilapi.xcertplay.CarPlayHostActivity::class.java).get()
        com.shilapi.xcertplay.CarPlayBackgroundSession.clear()
        com.shilapi.xcertplay.AirPlayPersistence.saveWirelessEnabled(activity, false)
        ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
        ReflectionHelpers.setStaticField(AppLicense::class.java, "lastPrompt", -10000L)
        ReflectionHelpers.setField(activity, "wirelessEnabled", true)
        val sizeType = Class.forName("com.shilapi.xcertplay.CarPlayHostActivity\$DisplaySize")
        val size = sizeType.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.newInstance(960, 360)
        activity.javaClass.getDeclaredMethod("startCarPlay", sizeType).apply { isAccessible = true }.invoke(activity, size)
        assertEquals(LicenseActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
        assertNull(ReflectionHelpers.getField<Any?>(activity, "controller"))
    }

    @Test fun connectionSettingsHaveNoSoftwareAuthorizationEntry() {
        val host = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        try {
            val root = LinearLayout(host.get()).apply { orientation = LinearLayout.VERTICAL }
            ReflectionHelpers.callInstanceMethod<Unit>(host.get(), "connectionSetup", ClassParameter.from(LinearLayout::class.java, root))
            val labels = views(root).filterIsInstance<TextView>().map { it.text.toString() }.toList()
            assertFalse(labels.any { "软件授权" in it || "打开授权页面" in it || "授权管理" in it })
            assertTrue("蓝牙工具" in labels)
            assertTrue(host.get().getString(R.string.connect_with_usb) in labels)
            layout(root, 960, 1600)
            screenshot(root, "connection-settings", 960, 1600)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun approvedWirelessReturnsToWirelessAndMaintenanceBlocksLeaving() {
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", Long.MAX_VALUE)
            ReflectionHelpers.setField(activity, "working", true)
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "continueConnection")
            assertNull(shadowOf(activity).nextStartedActivity)
            ReflectionHelpers.setField(activity, "working", false)
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "continueConnection")
            val next = shadowOf(activity).nextStartedActivity
            assertEquals(DiPlayActivity::class.java.name, next.component!!.className)
            assertEquals("wireless", next.getStringExtra("authorized_connection"))
            assertTrue(next.getBooleanExtra("authorization_return", false))
            assertTrue(activity.isFinishing)
        } finally {
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            host.pause().stop().destroy()
        }
    }

    @Test fun staleUsbAuthorizationIntentReturnsToOrdinaryUsbWithoutShowingAPanel() {
        val host = Robolectric.buildActivity(LicenseActivity::class.java,
            Intent().putExtra("license_wireless", false)).create()
        try {
            val activity = host.get()
            val next = shadowOf(activity).nextStartedActivity
            assertEquals(DiPlayActivity::class.java.name, next.component!!.className)
            assertEquals("usb", next.getStringExtra("authorized_connection"))
            assertTrue(next.getBooleanExtra("authorization_return", false))
            assertTrue(activity.isFinishing)
            assertNull(activity.window.decorView.findViewWithTag<View>("online-license-panel"))
        } finally { host.destroy() }
    }

    @Test fun connectionTestRequiresLicenseBeforeConfirmationAndRechecksBeforeMaintenance() {
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            com.shilapi.xcertplay.e01goc.E01GocPreferences.select(activity, true)
            com.shilapi.xcertplay.DiPlayPreferences.savePhone(activity, "00:11:22:33:44:55", "iPhone")
            val buttons = views(activity.window.decorView).filterIsInstance<Button>().toList()
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            buttons.single { it.text == "连接测试" }.performClick()
            assertNull(ShadowDialog.getLatestDialog())
            assertFalse(ReflectionHelpers.getField<Boolean>(activity, "working"))
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { "连接测试需要激活" in it.text })
            assertFalse(File(activity.filesDir, "e01-goc/manage.sh").exists())
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", Long.MAX_VALUE)
            buttons.single { it.text == "连接测试" }.performClick()
            val confirmation = ShadowDialog.getLatestDialog() as android.app.AlertDialog
            assertTrue(confirmation.isShowing)
            // An expired lease while the confirmation was open must not start maintenance.
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            confirmation.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
            assertFalse(ReflectionHelpers.getField<Boolean>(activity, "working"))
            assertFalse(File(activity.filesDir, "e01-goc/manage.sh").exists())
            buttons.single { it.text == "还原备份" }.performClick()
            assertTrue(ShadowDialog.getLatestDialog().isShowing)
            ShadowDialog.getLatestDialog().dismiss()
        } finally {
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            host.pause().stop().destroy()
        }
    }

    private fun layout(view: View, width: Int, height: Int) {
        repeat(3) {
            views(view).forEach { it.forceLayout() }
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
    }
    private fun screenshot(view: View, name: String, width: Int, height: Int) {
        System.getenv("DIPLAY_LICENSE_SCREENSHOTS")?.let { directory ->
            File(directory).mkdirs()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }
}

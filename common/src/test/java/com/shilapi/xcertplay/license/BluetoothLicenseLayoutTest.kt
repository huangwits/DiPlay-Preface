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
import com.geelyplay.preface.ConnectionWaitingView
import com.geelyplay.preface.e01goc.E01GocActivity
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
import com.geelyplay.preface.license.AppLicense
import com.geelyplay.preface.license.CommunityCard
import com.geelyplay.preface.license.LicenseActivity
import com.geelyplay.preface.license.LicensePanel
import com.geelyplay.preface.license.OnlineLicense

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
        com.geelyplay.preface.e01goc.E01GocPreferences.select(org.robolectric.RuntimeEnvironment.getApplication(), true)
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val row = root.findViewWithTag<LinearLayout>("bluetooth-license-columns")
            val left = row.getChildAt(0) as ConnectionWaitingView
            val right = root.findViewWithTag<LicensePanel>("online-license-panel")
            for ((width, height) in listOf(1280 to 480, 960 to 360, 480 to 800)) {
                layout(root, width, height)
                if (width > height) {
                    assertTrue(left.right < right.left)
                    assertEquals(left.height, right.height)
                } else {
                    assertTrue(left.bottom < right.top)
                    assertEquals(left.width, right.width)
                }
                assertTrue(right.right <= row.width && right.bottom <= row.height)
                assertNull(left.logPanel.parent)
                val labels = views(left).filterIsInstance<Button>().map { it.text.toString() }.toList()
                for (text in listOf("选择手机", "还原备份", "切换蓝牙模式", "返回")) assertTrue(text in labels)
                assertFalse(labels.any { it in listOf("检查状态", "连接测试", "安装适配") })
                assertSame(left.back, left.controls.getChildAt(left.controls.childCount - 1))
                assertEquals(1, views(root).filterIsInstance<TextView>().count { it.isShown && it.text == "无线 CarPlay" })
                views(root).filterIsInstance<Button>().filter { it.isShown }.forEach {
                    assertEquals(48, it.height)
                    assertEquals(14f, it.textSize, 0.1f)
                }
                assertEquals("原厂蓝牙", left.findViewWithTag<TextView>("bluetooth-mode-status").text.toString())
                assertTrue(left.controls.indexOfChild(left.findViewWithTag<Button>("bluetooth-mode")) >
                    left.controls.indexOfChild(left.findViewWithTag<Button>("bluetooth-connect")))
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

    @Test fun authorizedReturnKeepsTheGroupQrAndDailyConnectionAvailable() {
        com.geelyplay.preface.e01goc.E01GocPreferences.select(org.robolectric.RuntimeEnvironment.getApplication(), true)
        ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", Long.MAX_VALUE)
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val row = root.findViewWithTag<LinearLayout>("bluetooth-license-columns")
            val left = row.getChildAt(0) as ConnectionWaitingView
            val right = row.getChildAt(1) as LicensePanel
            assertEquals(View.VISIBLE, right.visibility)
            assertEquals(View.VISIBLE, right.findViewWithTag<CommunityCard>("license-community-card").visibility)
            assertEquals("激活免费，审核仅用于防止滥用。",
                right.findViewWithTag<TextView>("license-free-notice").text.toString())
            val connect = left.findViewWithTag<Button>("bluetooth-connect")
            assertTrue(connect.isEnabled)
            assertTrue(left.findViewWithTag<TextView>("bluetooth-license-status").text.contains("无线 CarPlay 已激活"))
            for ((width,height) in listOf(1280 to 480, 960 to 360, 480 to 800)) {
                layout(root,width,height)
                assertTrue(left.width > 0)
                assertNull(root.findViewWithTag<View>("bluetooth-setup-steps"))
                val card = right.findViewWithTag<View>("license-community-card")
                assertTrue(card.isShown)
                assertTrue(card.bottom <= right.height)
                assertFalse(views(right).filterIsInstance<Button>().any { it.isShown && it.text == "申请激活" })
                screenshot(root,"authorized-${width}x${height}",width,height)
            }
            com.shilapi.xcertplay.CarPlayBackgroundSession.active = true
            connect.performClick()
            com.shilapi.xcertplay.CarPlayBackgroundSession.active = false
            val next = shadowOf(activity).nextStartedActivity
            assertEquals("wireless", next.getStringExtra("authorized_connection"))
            assertEquals(DiPlayActivity::class.java.name, next.component!!.className)
        } finally {
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            host.pause().stop().destroy()
        }
    }

    @Test fun authorizationLossRestoresApplicationControlsAndDisablesDailyConnection() {
        ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", Long.MAX_VALUE)
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val root = host.get().window.decorView
            val right = root.findViewWithTag<LicensePanel>("online-license-panel")
            assertEquals(View.VISIBLE,right.visibility)
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            right.refreshAdmission()
            assertEquals(View.VISIBLE,right.visibility)
            assertFalse(root.findViewWithTag<Button>("bluetooth-connect").isEnabled)
            assertTrue(views(right).filterIsInstance<Button>().single { it.text == "刷新状态" }.isShown)
            assertNull(shadowOf(host.get()).nextStartedActivity)
        } finally { host.pause().stop().destroy() }
    }

    @Test fun approvalReturnsToDailyConnectionAndKeepsQrVisible() {
        com.geelyplay.preface.e01goc.E01GocPreferences.select(org.robolectric.RuntimeEnvironment.getApplication(), true)
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val root = host.get().findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val row = root.findViewWithTag<LinearLayout>("bluetooth-license-columns")
            val left = row.getChildAt(0) as ConnectionWaitingView
            val right = row.getChildAt(1) as LicensePanel
            layout(root, 960, 360)
            left.controlScroll.scrollTo(0, 200)
            right.scrollTo(0, 200)
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", Long.MAX_VALUE)
            right.refreshAdmission()
            layout(root, 960, 360)
            assertEquals(0, left.controlScroll.scrollY)
            assertEquals(0, right.scrollY)
            assertNull(root.findViewWithTag<View>("bluetooth-setup-hint"))
            val primary = ReflectionHelpers.getField<Button>(left, "primaryAction")
            assertEquals("一键连接 CarPlay", primary.text.toString())
            val card = right.findViewWithTag<View>("license-community-card")
            assertTrue(card.isShown && card.bottom <= right.height)
            assertFalse(File(host.get().filesDir, "e01-goc/manage.sh").exists())
        } finally {
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            host.pause().stop().destroy()
        }
    }

    @Test fun moreOptionsShowsRecoveryDirectlyWithoutNumberedStepsOrInstructions() {
        com.geelyplay.preface.e01goc.E01GocPreferences.select(org.robolectric.RuntimeEnvironment.getApplication(), true)
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            activity.window.decorView.findViewWithTag<Button>("bluetooth-more").performClick()
            val root = activity.window.decorView
            assertTrue(root.findViewWithTag<Button>("bluetooth-restore").isShown)
            assertNull(root.findViewWithTag<View>("bluetooth-recovery-toggle"))
            assertNull(root.findViewWithTag<View>("bluetooth-instructions"))
            assertNull(root.findViewWithTag<View>("bluetooth-setup-steps"))
            assertFalse(views(root).filterIsInstance<TextView>().any { it.text.toString() in listOf("第 1 步", "第 2 步", "第 3 步", "完整操作说明") })
            assertFalse(File(activity.filesDir,"e01-goc/manage.sh").exists())
            assertFalse(ReflectionHelpers.getField<Boolean>(activity,"working"))
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
            assertTrue("无线 CarPlay" in labels)
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

    @Test fun quickConnectionRequiresLicenseWhileRecoveryRemainsAvailable() {
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            com.geelyplay.preface.e01goc.E01GocPreferences.select(activity, true)
            com.shilapi.xcertplay.DiPlayPreferences.savePhone(activity, "00:11:22:33:44:55", "iPhone")
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "updateModeLayout")
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "quickConnect")
            assertNull(ShadowDialog.getLatestDialog())
            assertFalse(ReflectionHelpers.getField<Boolean>(activity, "working"))
            assertNull(shadowOf(activity).nextStartedActivity)
            assertFalse(File(activity.filesDir, "e01-goc/manage.sh").exists())
            activity.window.decorView.findViewWithTag<Button>("bluetooth-more").performClick()
            activity.window.decorView.findViewWithTag<Button>("bluetooth-restore").performClick()
            assertTrue(ShadowDialog.getLatestDialog().isShowing)
            ShadowDialog.getLatestDialog().dismiss()
        } finally {
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            host.pause().stop().destroy()
        }
    }

    @Test fun managerRejectsExpiredAdmissionWithoutManualStepButtons() {
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            com.geelyplay.preface.e01goc.E01GocPreferences.select(activity, true)
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            assertFalse(ReflectionHelpers.getField<Boolean>(activity, "working"))
            val manager = com.geelyplay.preface.e01goc.E01GocManager(activity) {}
            assertThrows(IllegalStateException::class.java) { manager.install() }
            assertThrows(IllegalStateException::class.java) { manager.test("00:11:22:33:44:55") }
            assertFalse(File(activity.filesDir, "e01-goc/manage.sh").exists())
            assertFalse(com.geelyplay.preface.e01goc.E01GocManager.isBusy())
        } finally {
            ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", 0L)
            host.pause().stop().destroy()
        }
    }

    @Test fun moreOptionsKeepsRecoveryAndModeSwitchWithoutManualSetupSteps() {
        com.geelyplay.preface.e01goc.E01GocPreferences.select(org.robolectric.RuntimeEnvironment.getApplication(), true)
        ReflectionHelpers.setStaticField(OnlineLicense::class.java, "validUntilElapsed", Long.MAX_VALUE)
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val activity = host.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val buttons = views(root).filterIsInstance<Button>().associateBy { it.text.toString() }
            assertFalse(buttons.keys.any { it in listOf("检查状态", "连接测试", "安装适配") })
            val restore = root.findViewWithTag<Button>("bluetooth-restore")
            assertEquals(View.GONE, restore.visibility)
            root.findViewWithTag<Button>("bluetooth-more").performClick()
            assertEquals(View.VISIBLE, restore.visibility)
            assertFalse(File(activity.filesDir, "e01-goc/manage.sh").exists())
            assertTrue(root.findViewWithTag<Button>("bluetooth-mode").isShown)
            for ((width,height) in listOf(960 to 360, 1280 to 480, 480 to 800)) {
                layout(root,width,height)
                val daily = root.findViewWithTag<Button>("bluetooth-connect")
                assertTrue(daily.width > 0)
                screenshot(root,"setup-ready-${width}x${height}",width,height)
            }
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

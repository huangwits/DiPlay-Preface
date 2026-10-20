package com.shilapi.xcertplay

import android.content.Intent
import android.content.DialogInterface
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.RadioButton
import android.widget.TextView
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.util.concurrent.ExecutorService
import org.junit.After
import android.app.AlertDialog
import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedConstruction
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.android.util.concurrent.PausedExecutorService
import com.geelyplay.preface.CarPlayShutdownGate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayHostSettingsTest {
    private lateinit var activity: CarPlayHostActivity
    private lateinit var controllers: MockedConstruction<CarPlayController>

    @Before fun setUp() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        controllers = mockConstruction(CarPlayController::class.java)
        (field("teardownExecutor") as ExecutorService).shutdownNow()
        setField("teardownExecutor", PausedExecutorService())
        setField("airPlayIdentity", AirPlayIdentity.generate())
        val sizeClass = Class.forName("com.shilapi.xcertplay.CarPlayHostActivity\$DisplaySize")
        val size = sizeClass.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.newInstance(1920, 990)
        setField("activeDisplaySize", size)
        CarPlayBackgroundSession::class.java.getDeclaredField("owner").apply { isAccessible = true }
            .set(CarPlayBackgroundSession, activity)
        AirPlayPersistence.saveMfiTarget(activity, MfiTarget.USB_CH341)
        AirPlayPersistence.saveWirelessHotspotMode(activity, WirelessHotspotMode.WIFI_P2P)
        invoke("loadPersistedSettings")
        invoke("buildContentView")
    }

    @After fun tearDown() {
        (field("shuttingDown") as CarPlayShutdownGate).begin(terminate = false)
        (field("mainHandler") as Handler).removeCallbacksAndMessages(null)
        (field("teardownExecutor") as ExecutorService).shutdownNow()
        (field("airPlayCommandExecutor") as ExecutorService).shutdownNow()
        CarPlayBackgroundSession.clear()
        controllers.close()
    }

    @Test fun disabledGesturePersistsAndDoesNotOpenTheMenu() {
        AirPlayPersistence.saveSettingsGestureFingers(activity, 0)
        invoke("loadPersistedSettings")
        assertEquals(0, AirPlayPersistence.loadSettingsGestureFingers(activity))
        for (fingers in 2..4) {
            gesture(fingers)
            assertFalse(field("menuOpen") as Boolean)
            assertFalse(field("gestureSequenceActive") as Boolean)
        }
        invoke("openSettingsMenu")
        val button = views(menu()).filterIsInstance<Button>()
            .first { it.text == activity.getString(R.string.settings_gesture_disabled_action) }
        button.performClick()
        assertEquals(2, field("gestureFingerCount"))
        invoke("cancelSettingsEdits")
        assertEquals(0, field("gestureFingerCount"))
    }

    @Test fun menuStaysCenteredAndUsesAvailableWidthAcrossWindowSizes() {
        invoke("openSettingsMenu")
        val overlay = menu() as ViewGroup
        val panel = overlay.getChildAt(0)
        val density = activity.resources.displayMetrics.density
        for (widthDp in listOf(320, 600, 1024, 1600)) {
            val width = (widthDp * density + 0.5f).toInt()
            val height = (480 * density + 0.5f).toInt()
            repeat(3) {
                overlay.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                overlay.layout(0, 0, width, height)
            }
            val expected = (minOf(720, widthDp - 32) * density + 0.5f).toInt()
            assertEquals(expected, panel.width)
            assertTrue(kotlin.math.abs(panel.left - (width - panel.right)) <= 1)
            val title = views(panel).filterIsInstance<TextView>()
                .first { it.text == activity.getString(R.string.carplay_settings) }
            val close = views(panel).filterIsInstance<Button>().first { it.text == activity.getString(R.string.close) }
            val titleBounds = android.graphics.Rect(0, 0, title.width, title.height)
            (panel as ViewGroup).offsetDescendantRectToMyCoords(title, titleBounds)
            val closeBounds = android.graphics.Rect(0, 0, close.width, close.height)
            panel.offsetDescendantRectToMyCoords(close, closeBounds)
            assertTrue(kotlin.math.abs(titleBounds.exactCenterY() - closeBounds.exactCenterY()) <= 1f)
            assertTrue(panel.height < height)
        }
    }

    @Test fun originalAuthorMenuUsesOneScrollAndKeepsDraftEdits() {
        invoke("openSettingsMenu")
        val root = menu() as ViewGroup
        assertTrue(views(root).filterIsInstance<android.widget.LinearLayout>()
            .filter { it.tag in setOf("connection", "display", "navigation", "vehicle", "diagnostics", "advanced", "more") }
            .all { it.visibility == View.VISIBLE })
        val original = AirPlayPersistence.loadDisplayScalePercent(activity)
        resolutionSlider().progress = 0
        val edited = field("displayScalePercent")
        assertEquals(edited, field("displayScalePercent"))
        assertEquals(original, AirPlayPersistence.loadDisplayScalePercent(activity))
        val save = views(root).filterIsInstance<Button>().single { it.text == activity.getString(R.string.save_and_reconnect) }
        assertFalse(generateSequence(save.parent) { it.parent }.any { it is android.widget.ScrollView })
        invoke("cancelSettingsEdits")
        assertEquals(original, field("displayScalePercent"))
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun fixedSaveAndLastScrollableActionStayAboveBottomNavigation() =
        assertMenuFitsSystemBars(1280, 480, left = 0, top = 24, right = 0, bottom = 96)

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun portraitAndSideNavigationKeepTheWholeMenuReachable() {
        assertMenuFitsSystemBars(480, 800, left = 0, top = 24, right = 0, bottom = 96)
        assertMenuFitsSystemBars(1280, 480, left = 0, top = 24, right = 96, bottom = 0)
    }

    @Test @Config(sdk = [23])
    fun legacyHeadUnitInsetsAlsoKeepSaveAboveNavigation() =
        assertMenuFitsSystemBars(1280, 480, left = 0, top = 24, right = 0, bottom = 96)

    private fun assertMenuFitsSystemBars(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int) {
        invoke("openSettingsMenu")
        val overlay = menu() as ViewGroup
        val save = views(overlay).filterIsInstance<Button>()
            .single { it.text == activity.getString(R.string.save_and_reconnect) }
        val footer = save.parent as View
        val scroll = field("settingsMenuContentScroll") as android.widget.ScrollView
        val close = views(overlay).filterIsInstance<Button>().single { it.text == activity.getString(R.string.close) }
        val before = field("restartGeneration")
        for (navVisible in listOf(true, true, false, true)) {
            val system = androidx.core.graphics.Insets.of(if (navVisible) left else 0, top,
                if (navVisible) right else 0, if (navVisible) bottom else 0)
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(), system).build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(overlay, insets)
            assertEquals(system.bottom, overlay.paddingBottom)
            assertEquals(system.right, overlay.paddingRight)
            repeat(3) {
                views(overlay).forEach { it.forceLayout() }
                overlay.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                overlay.layout(0, 0, width, height)
            }
            val bounds = android.graphics.Rect(0, 0, save.width, save.height)
            overlay.offsetDescendantRectToMyCoords(save, bounds)
            assertTrue("Save must be fully above visible navigation: $bounds / $system", bounds.bottom <= height - system.bottom)
            assertTrue("Save must remain inside horizontal safe bounds", bounds.left >= system.left && bounds.right <= width - system.right)
            assertTrue("Save needs a full touch target", save.height >= 48 * activity.resources.displayMetrics.density)
            val closeBounds = android.graphics.Rect(0, 0, close.width, close.height)
            overlay.offsetDescendantRectToMyCoords(close, closeBounds)
            assertTrue("Close must remain below status bar", closeBounds.top >= system.top)
            scroll.scrollTo(0, scroll.getChildAt(0).height)
            val exit = views(scroll).filterIsInstance<Button>().single { it.text == activity.getString(R.string.exit_application) }
            val lastBounds = android.graphics.Rect(0, 0, exit.width, exit.height)
            overlay.offsetDescendantRectToMyCoords(exit, lastBounds)
            val footerBounds = android.graphics.Rect(0, 0, footer.width, footer.height)
            overlay.offsetDescendantRectToMyCoords(footer, footerBounds)
            assertTrue("Last action must scroll fully above the fixed footer", lastBounds.bottom <= footerBounds.top)
            assertTrue("Content viewport must remain usable", scroll.height > 0)
            assertEquals("System bars must not restart CarPlay", before, field("restartGeneration"))
            if (navVisible) System.getenv("DIPLAY_LAYOUT_PREVIEW_DIR")?.let { directory ->
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                try {
                    val canvas = android.graphics.Canvas(bitmap)
                    overlay.draw(canvas)
                    val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(30, 35, 40) }
                    if (bottom > 0) canvas.drawRect(0f, (height - bottom).toFloat(), width.toFloat(), height.toFloat(), paint)
                    if (right > 0) canvas.drawRect((width - right).toFloat(), 0f, width.toFloat(), height.toFloat(), paint)
                    val output = java.io.File(directory).apply { mkdirs() }
                    java.io.File(output, "swipe-menu-${width}x${height}-bottom$bottom-right$right.png")
                        .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
            }
        }
        invoke("cancelSettingsEdits")
    }
    @Test fun configuredFingerCountsOpenTheMountedMenuWithoutLeavingCarPlay() {
        assertEquals(3, AirPlayPersistence.loadSettingsGestureFingers(activity))
        for (fingers in 2..4) {
            AirPlayPersistence.saveSettingsGestureFingers(activity, fingers)
            invoke("loadPersistedSettings")
            gesture(fingers)
            assertTrue(field("menuOpen") as Boolean)
            assertEquals(View.VISIBLE, menu().visibility)
            assertNotNull(menu().parent)
            assertEquals(View.GONE, (field("gestureOverlay") as View).visibility)
            assertNull(shadowOf(activity).nextStartedActivity)
            invoke("cancelSettingsEdits")
        }
    }

    @Test fun wrongFingerCountsAndNonDownwardSwipesDoNotOpenTheMenu() {
        for (configured in 2..4) {
            AirPlayPersistence.saveSettingsGestureFingers(activity, configured)
            invoke("loadPersistedSettings")
            for (actual in 2..4) {
                if (actual == configured) continue
                gesture(actual)
                assertFalse(field("menuOpen") as Boolean)
            }
            gesture(configured, x = 1000f, y = 130f)
            assertFalse(field("menuOpen") as Boolean)
            gesture(configured, y = -500f)
            assertFalse(field("menuOpen") as Boolean)
        }
    }

    @Test fun liftingAFingerCancelsTrackingUntilTheNextGesture() {
        touch(MotionEvent.ACTION_DOWN, 1, 100f)
        touch(MotionEvent.ACTION_POINTER_DOWN, 2, 100f)
        touch(MotionEvent.ACTION_POINTER_DOWN, 3, 100f)
        touch(MotionEvent.ACTION_POINTER_UP, 3, 100f)
        touch(MotionEvent.ACTION_POINTER_DOWN, 3, 100f)
        touch(MotionEvent.ACTION_MOVE, 3, 700f)
        assertFalse(field("menuOpen") as Boolean)
        touch(MotionEvent.ACTION_UP, 1, 700f)
        gesture(3)
        assertTrue(field("menuOpen") as Boolean)
    }

    @Test fun fullSettingsShortcutWaitsForDiscardConfirmationWithoutRestartingTheSession() {
        val controller = attachController()
        invoke("openSettingsMenu")
        val original = AirPlayPersistence.loadDisplayScalePercent(activity)
        resolutionSlider().progress = 0
        fullSettingsButton().performClick()
        assertTrue(field("menuOpen") as Boolean)
        assertNull(shadowOf(activity).nextStartedActivity)
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(field("menuOpen") as Boolean)
        assertNull(field("settingsBaseline"))
        assertEquals(original, field("displayScalePercent"))
        assertEquals(original, AirPlayPersistence.loadDisplayScalePercent(activity))
        assertSame(controller, field("controller"))
        assertEquals(0, field("restartGeneration"))
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(DiPlayActivity::class.java.name, intent.component!!.className)
        assertEquals("settings", intent.getStringExtra("page"))
    }

    @Test fun continueEditingPreservesPendingChangesAndDoesNotNavigate() {
        val controller = attachController()
        invoke("openSettingsMenu")
        resolutionSlider().progress = 0
        fullSettingsButton().performClick()
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            .getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(field("menuOpen") as Boolean)
        assertEquals(CarPlayDisplayScale.MIN_PERCENT, field("displayScalePercent"))
        assertSame(controller, field("controller"))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun confirmingSaveNavigatesOnlyAfterSuccessfulValidation() {
        attachController()
        invoke("openSettingsMenu")
        resolutionSlider().progress = 0
        fullSettingsButton().performClick()
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            .getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(field("menuOpen") as Boolean)
        assertEquals(CarPlayDisplayScale.MIN_PERCENT, AirPlayPersistence.loadDisplayScalePercent(activity))
        assertEquals(1, field("restartGeneration"))
        assertEquals("settings", shadowOf(activity).nextStartedActivity.getStringExtra("page"))
    }

    @Test fun failedSaveFromLeaveDialogKeepsMenuOpenWithoutNavigation() {
        attachController()
        invoke("openSettingsMenu")
        views(menu()).filterIsInstance<RadioButton>().first { it.tag == MfiTarget.LOCAL }.performClick()
        fullSettingsButton().performClick()
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            .getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(field("menuOpen") as Boolean)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(0, field("restartGeneration"))
    }

    @Test fun returningFromFullSettingsReloadsSavedConnectionPreferences() {
        attachController()
        invoke("openSettingsMenu")
        fullSettingsButton().performClick()
        AirPlayPersistence.saveMfiTarget(activity, MfiTarget.LOCAL)
        AirPlayPersistence.saveWirelessHotspotMode(activity, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveManualHotspotSsid(activity, "Updated in full settings")
        invoke("onResume")
        assertEquals(MfiTarget.LOCAL, field("mfiTarget"))
        assertEquals(WirelessHotspotMode.MANUAL, field("wirelessHotspotMode"))
        assertEquals("Updated in full settings", field("manualHotspotSsid"))
    }

    @Test fun openingAndCancellingKeepsTheCurrentControllerAndRestoresControls() {
        val controller = attachController()
        invoke("openSettingsMenu")
        val original = AirPlayPersistence.loadDisplayScaleTenths(activity)
        resolutionSlider().progress = 0
        gestureButton().performClick()
        assertEquals(3, AirPlayPersistence.loadSettingsGestureFingers(activity))
        invoke("cancelSettingsEdits")
        assertSame(controller, field("controller"))
        assertEquals(0, field("restartGeneration"))
        assertEquals(original, field("displayScaleTenths"))
        assertEquals(3, field("gestureFingerCount"))
        invoke("openSettingsMenu")
        assertEquals(original * 10 - CarPlayDisplayScale.MIN_PERCENT, resolutionSlider().progress)
        assertEquals(activity.getString(R.string.settings_gesture_fingers, 3), gestureButton().text)
    }

    @Test fun customResolutionSurvivesCancelAndUnrelatedSettingsSave() {
        for (percent in listOf(73, 157, 160)) {
            AirPlayPersistence.saveDisplayScalePercent(activity, percent)
            invoke("openSettingsMenu")
            val slider = resolutionSlider()
            assertEquals(percent - CarPlayDisplayScale.MIN_PERCENT, slider.progress)
            val listener = SeekBar::class.java.getDeclaredField("mOnSeekBarChangeListener")
                .apply { isAccessible = true }.get(slider) as SeekBar.OnSeekBarChangeListener
            listener.onProgressChanged(slider, 0, true)
            assertEquals(percent, AirPlayPersistence.loadDisplayScalePercent(activity))
            invoke("cancelSettingsEdits")
            assertEquals(percent, field("displayScalePercent"))
            assertEquals(percent, AirPlayPersistence.loadDisplayScalePercent(activity))
            invoke("openSettingsMenu")
            invoke("persistMenuSettings")
            assertEquals(percent, AirPlayPersistence.loadDisplayScalePercent(activity))
            invoke("cancelSettingsEdits")
        }
    }

    @Test fun resolutionEndpointLabelsMatchTheActualSliderRange() {
        invoke("openSettingsMenu")
        val labels = views(menu()).filterIsInstance<TextView>().map { it.text.toString() }.toList()
        assertTrue(labels.contains(activity.getString(R.string.custom_resolution_summary, 30)))
        assertTrue(labels.contains(activity.getString(R.string.custom_resolution_summary, 160)))
        assertFalse(labels.contains("2.0x"))
    }

    @Test fun resumingWithTheMenuOpenPreservesUnsavedConnectionEdits() {
        invoke("openSettingsMenu")
        setField("wirelessHotspotMode", WirelessHotspotMode.MANUAL)
        setField("manualHotspotSsid", "Draft hotspot")
        setField("mfiTarget", MfiTarget.LOCAL)
        invoke("onResume")
        assertEquals(WirelessHotspotMode.MANUAL, field("wirelessHotspotMode"))
        assertEquals("Draft hotspot", field("manualHotspotSsid"))
        assertEquals(MfiTarget.LOCAL, field("mfiTarget"))
        invoke("cancelSettingsEdits")
        assertEquals(WirelessHotspotMode.WIFI_P2P, field("wirelessHotspotMode"))
        assertEquals(MfiTarget.USB_CH341, field("mfiTarget"))
    }

    @Test fun lightAppearanceRepaintsAnOpenMenuWithoutLosingDraftState() {
        invoke("openSettingsMenu")
        setField("manualHotspotSsid", "Unsaved hotspot")
        val oldMenu = menu()
        val scroll = views(oldMenu).filterIsInstance<android.widget.ScrollView>().single()
        scroll.scrollTo(0, 120)

        AirPlayPersistence.saveAppAppearance(activity, AppAppearance.LIGHT)
        invoke("refreshAppAppearance")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertNotSame(oldMenu, menu())
        assertEquals("Unsaved hotspot", field("manualHotspotSsid"))
        assertEquals(false, field("appNight"))
        assertEquals(0xB3000000.toInt(), (menu().background as android.graphics.drawable.ColorDrawable).color)
        val heading = views(menu()).filterIsInstance<TextView>()
            .first { it.text == activity.getString(R.string.carplay_settings) }
        assertEquals(DiPlayPalette.LIGHT.overlayPrimaryText, heading.currentTextColor)
    }

    @Test fun savingPersistsSettingsAndRestartsOnce() {
        attachController()
        invoke("openSettingsMenu")
        resolutionSlider().progress = 0
        gestureButton().performClick()
        invoke("saveSettingsAndReconnect")
        assertFalse(field("menuOpen") as Boolean)
        assertEquals(CarPlayDisplayScale.MIN_TENTHS, AirPlayPersistence.loadDisplayScaleTenths(activity))
        assertEquals(4, AirPlayPersistence.loadSettingsGestureFingers(activity))
        assertEquals(1, field("restartGeneration"))
        assertTrue(field("handshakeResetInProgress") as Boolean)
    }

    @Test fun failedStartupWhileMenuIsOpenRecoversOnCancel() {
        attachController()
        invoke("openSettingsMenu")
        report(CarPlayStatus.Failed("Authentication failed"))
        assertEquals(0, field("restartGeneration"))
        invoke("cancelSettingsEdits")
        assertEquals(1, field("restartGeneration"))
    }

    @Test fun transportLossWhileMenuIsOpenRecoversOnCancel() {
        attachController()
        invoke("openSettingsMenu")
        val listener = activity.javaClass.getDeclaredMethod("createSessionListener", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, 0) as AirPlaySessionListener
        listener.onTransportError("transport lost")
        invoke("cancelSettingsEdits")
        assertEquals(1, field("restartGeneration"))
    }

    @Test fun wifiResetFailureRequiresManualRecoveryAfterCancel() {
        attachController()
        invoke("openSettingsMenu")
        report(CarPlayStatus.Failed("Wi-Fi needs a reset", wifiResetRequired = true))
        invoke("cancelSettingsEdits")
        assertEquals(View.VISIBLE, (field("wifiRecoveryButton") as View).visibility)
        assertEquals(0, field("restartGeneration"))
        assertFalse(field("reconnectScheduled") as Boolean)
    }

    @Test fun savingWirelessAfterWifiResetFailureStillRequiresManualRecovery() {
        attachController()
        AirPlayPersistence.saveWirelessEnabled(activity, true)
        invoke("openSettingsMenu")
        report(CarPlayStatus.Failed("Wi-Fi needs a reset", wifiResetRequired = true))
        invoke("saveSettingsAndReconnect")
        assertEquals(View.VISIBLE, (field("wifiRecoveryButton") as View).visibility)
        assertEquals(0, field("restartGeneration"))
    }

    @Test fun savingWiredModeAfterWifiResetFailureRestartsWithTheNewSettings() {
        attachController()
        AirPlayPersistence.saveWirelessEnabled(activity, true)
        invoke("openSettingsMenu")
        report(CarPlayStatus.Failed("Wi-Fi needs a reset", wifiResetRequired = true))
        setField("wirelessEnabled", false)
        invoke("saveSettingsAndReconnect")
        assertFalse(AirPlayPersistence.loadWirelessEnabled(activity))
        assertEquals(1, field("restartGeneration"))
    }

    @Test fun staleFailureDoesNotRestartWhenTheMenuCloses() {
        val controller = attachController()
        invoke("openSettingsMenu")
        report(CarPlayStatus.Failed("old failure"), generation = -1)
        invoke("cancelSettingsEdits")
        assertSame(controller, field("controller"))
        assertEquals(0, field("restartGeneration"))
    }

    @Test fun authenticationChoicesOnlyExposeLocalAndCh341() {
        invoke("openSettingsMenu")
        val options = views(menu()).filterIsInstance<RadioButton>().filter { it.tag is MfiTarget }.toList()
        assertEquals(listOf(MfiTarget.LOCAL, MfiTarget.USB_CH341), options.map { it.tag })
        options.first().performClick()
        invoke("cancelSettingsEdits")
        assertEquals(MfiTarget.USB_CH341, AirPlayPersistence.loadMfiTarget(activity))
        assertEquals(MfiTarget.USB_CH341, field("mfiTarget"))
    }

    @Test fun selectingLocalWithoutIdentityKeepsTheMenuAndSavedUsbChoice() {
        attachController()
        invoke("openSettingsMenu")
        views(menu()).filterIsInstance<RadioButton>().first { it.tag == MfiTarget.LOCAL }.performClick()
        invoke("saveSettingsAndReconnect")
        assertTrue(field("menuOpen") as Boolean)
        assertEquals(MfiTarget.USB_CH341, AirPlayPersistence.loadMfiTarget(activity))
        assertEquals(View.VISIBLE, (field("mfiErrorView") as View).visibility)
        assertEquals(0, field("restartGeneration"))
    }

    @Test fun switchingToUsbPersistsAndPassesUsbToTheRuntime() {
        AirPlayPersistence.saveMfiTarget(activity, MfiTarget.LOCAL)
        attachController()
        invoke("openSettingsMenu")
        views(menu()).filterIsInstance<RadioButton>().first { it.tag == MfiTarget.USB_CH341 }.performClick()
        invoke("saveSettingsAndReconnect")
        assertFalse(field("menuOpen") as Boolean)
        assertEquals(MfiTarget.USB_CH341, AirPlayPersistence.loadMfiTarget(activity))
        val config = invoke("createRuntimeConfig") as CarPlayRuntimeConfig
        assertEquals(MfiTarget.USB_CH341, config.mfiTarget)
        assertEquals(listOf(com.shilapi.xcertplay.transport.UsbDeviceId(0x1a86, 0x5512)), config.ch341Devices)
        assertEquals(1, field("restartGeneration"))
    }

    @Test fun localRuntimeDoesNotRequestCh341Devices() {
        AirPlayPersistence.saveMfiTarget(activity, MfiTarget.LOCAL)
        invoke("loadPersistedSettings")
        val config = invoke("createRuntimeConfig") as CarPlayRuntimeConfig
        assertEquals(MfiTarget.LOCAL, config.mfiTarget)
        assertTrue(config.ch341Devices.isEmpty())
    }

    @Test fun ch341AttachmentKeepsTheWirelessSession() {
        val controller = attachController()
        AirPlayPersistence.saveWirelessEnabled(activity, true)
        invoke("loadPersistedSettings")
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x1a86)
        `when`(device.productId).thenReturn(0x5512)
        val intent = Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).putExtra(UsbManager.EXTRA_DEVICE, device)
        activity.javaClass.getDeclaredMethod("onNewIntent", Intent::class.java)
            .apply { isAccessible = true }.invoke(activity, intent)
        assertTrue(AirPlayPersistence.loadWirelessEnabled(activity))
        assertTrue(field("wirelessEnabled") as Boolean)
        assertSame(controller, field("controller"))
        assertFalse((field("shuttingDown") as CarPlayShutdownGate).get())
        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun iphoneAttachmentSwitchesToWiredTransportWithoutFinishing() {
        org.robolectric.shadows.ShadowVpnService.setPrepareResult(null)
        attachController()
        AirPlayPersistence.saveWirelessEnabled(activity, true)
        invoke("loadPersistedSettings")
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x05ac)
        val intent = Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).putExtra(UsbManager.EXTRA_DEVICE, device)
        activity.javaClass.getDeclaredMethod("onNewIntent", Intent::class.java)
            .apply { isAccessible = true }.invoke(activity, intent)
        assertFalse(AirPlayPersistence.loadWirelessEnabled(activity))
        assertFalse(field("wirelessEnabled") as Boolean)
        assertTrue(field("vpnReady") as Boolean)
        assertFalse((field("shuttingDown") as CarPlayShutdownGate).get())
        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun switchingToUsbRequestsVpnConsentAndDoesNotRepeatPendingConsent() {
        org.robolectric.shadows.ShadowVpnService.setPrepareResult(Intent("test.VPN_CONSENT"))
        attachController()
        AirPlayPersistence.saveWirelessEnabled(activity, true)
        invoke("loadPersistedSettings")
        setField("vpnReady", true) // Previously granted permission may have been revoked.
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x05ac)
        val intent = Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).putExtra(UsbManager.EXTRA_DEVICE, device)
        val method = activity.javaClass.getDeclaredMethod("onNewIntent", Intent::class.java).apply { isAccessible = true }
        method.invoke(activity, intent)
        assertTrue(field("awaitingVpnConsent") as Boolean)
        assertFalse(field("vpnReady") as Boolean)
        assertNull(field("controller"))
        assertEquals("test.VPN_CONSENT", shadowOf(activity).nextStartedActivityForResult.intent.action)
        method.invoke(activity, intent)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        assertFalse(activity.isFinishing)
    }

    @Test fun usbAttachmentClosesSettingsAndRestartsInPlace() {
        org.robolectric.shadows.ShadowVpnService.setPrepareResult(null)
        attachController()
        AirPlayPersistence.saveWirelessEnabled(activity, true)
        invoke("loadPersistedSettings")
        invoke("openSettingsMenu")
        assertTrue(field("menuOpen") as Boolean)
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x05ac)
        val intent = Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).putExtra(UsbManager.EXTRA_DEVICE, device)
        activity.javaClass.getDeclaredMethod("onNewIntent", Intent::class.java).apply { isAccessible = true }
            .invoke(activity, intent)
        assertFalse(field("menuOpen") as Boolean)
        assertFalse(field("wirelessEnabled") as Boolean)
        assertTrue(field("vpnReady") as Boolean)
        assertNull(field("controller"))
        assertEquals(1, field("restartGeneration"))
        assertFalse(activity.isFinishing)
    }

    @Test fun onlyIphoneAttachmentSelectsWiredTransport() {
        val method = activity.javaClass.getDeclaredMethod("isIphoneUsbAttachment", Intent::class.java)
            .apply { isAccessible = true }
        val device = mock(UsbDevice::class.java)
        `when`(device.vendorId).thenReturn(0x05ac)
        val attached = Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).putExtra(UsbManager.EXTRA_DEVICE, device)
        assertEquals(true, method.invoke(activity, attached))
        assertEquals(false, method.invoke(activity, Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED)))
        assertEquals(false, method.invoke(activity, Intent(UsbManager.ACTION_USB_DEVICE_DETACHED).putExtra(UsbManager.EXTRA_DEVICE, device)))
    }

    @Test fun cancelRestoresSafeAreaResetAfterTheWindowChangesSize() {
        val original = SafeAreaRect(20, 20, 1800, 900)
        AirPlayPersistence.saveSafeAreaRect(activity, 1920, 942, original)
        invoke("openSettingsMenu")
        resizeWindow(1920, 942)
        invoke("resetSafeAreaForCurrentSize")
        assertNull(AirPlayPersistence.loadSafeAreaRect(activity, 1920, 942))

        invoke("cancelSettingsEdits")

        assertEquals(original, AirPlayPersistence.loadSafeAreaRect(activity, 1920, 942))
    }

    @Test fun cancelRemovesNewSafeAreaSavedAtAnotherWindowSize() {
        invoke("openSettingsMenu")
        resizeWindow(1920, 942)
        invoke("openSafeAreaEditor")
        val editor = field("safeAreaEditorView") as SafeAreaEditorView
        editor.setRect(SafeAreaRect(20, 20, 1800, 900), 1920, 942)
        invoke("saveSafeAreaEditor")
        assertNotNull(AirPlayPersistence.loadSafeAreaRect(activity, 1920, 942))

        invoke("cancelSettingsEdits")

        assertNull(AirPlayPersistence.loadSafeAreaRect(activity, 1920, 942))
    }

    @Test fun savingKeepsSafeAreaEditsMadeAfterAWindowResize() {
        invoke("openSettingsMenu")
        resizeWindow(1920, 942)
        invoke("openSafeAreaEditor")
        val edited = SafeAreaRect(20, 20, 1800, 900)
        (field("safeAreaEditorView") as SafeAreaEditorView).setRect(edited, 1920, 942)
        invoke("saveSafeAreaEditor")

        invoke("saveSettingsAndReconnect")

        assertFalse(field("menuOpen") as Boolean)
        assertEquals(edited, AirPlayPersistence.loadSafeAreaRect(activity, 1920, 942))
    }

    private fun resizeWindow(width: Int, height: Int) {
        val sizeClass = Class.forName("com.shilapi.xcertplay.CarPlayHostActivity\$DisplaySize")
        val size = sizeClass.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.newInstance(width, height)
        setField("activeDisplaySize", size)
    }

    private fun gesture(fingers: Int, x: Float = 400f, y: Float = 700f) {
        touch(MotionEvent.ACTION_DOWN, 1, 100f)
        for (count in 2..fingers) touch(MotionEvent.ACTION_POINTER_DOWN, count, 100f)
        touch(MotionEvent.ACTION_MOVE, fingers, y, x)
        touch(MotionEvent.ACTION_UP, 1, y, x)
    }

    private fun touch(action: Int, count: Int, y: Float, x: Float = 400f) {
        val pointers = Array(count) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val positions = Array(count) { i -> MotionEvent.PointerCoords().apply { this.x = x + i * 50f; this.y = y; pressure = 1f; size = 1f } }
        val indexedAction = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)
            action or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) else action
        val event = MotionEvent.obtain(0, 10, indexedAction, count, pointers, positions, 0, 0, 1f, 1f, 0, 0, 0, 0)
        try {
            activity.javaClass.getDeclaredMethod("onHostTouch", View::class.java, MotionEvent::class.java)
                .apply { isAccessible = true }.invoke(activity, field("gestureOverlay"), event)
        } finally { event.recycle() }
    }

    private fun attachController(): CarPlayController = CarPlayController(
        activity, CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL,
            identification = Iap2IdentificationConfig("test", "test", "test", "test", "1", "1", 3)),
        AirPlayConfig("test", "02:00:00:00:00:02", "02:00:00:00:00:01", "1", AirPlayDisplayConfig(1920, 990)),
        AirPlayIdentity.generate(), PairingStore(), object : AirPlaySessionListener {}, object : AirPlayMediaHandler {}, {},
    ).also { setField("controller", it) }

    @Suppress("UNCHECKED_CAST")
    private fun report(status: CarPlayStatus, generation: Int = 0) {
        val reporter = activity.javaClass.getDeclaredMethod("createStatusReporter", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, generation) as (CarPlayStatus) -> Unit
        reporter(status)
    }

    private fun menu() = field("settingsMenu") as View
    private fun resolutionSlider() = views(menu()).filterIsInstance<SeekBar>()
        .first { it.max == CarPlayDisplayScale.MAX_PERCENT - CarPlayDisplayScale.MIN_PERCENT }
    private fun gestureButton() = views(menu()).filterIsInstance<Button>()
        .first { it.text == activity.getString(R.string.settings_gesture_fingers, field("gestureFingerCount")) }
    private fun fullSettingsButton() = views(menu()).filterIsInstance<Button>()
        .first { it.text == activity.getString(R.string.app_name) + " " + activity.getString(R.string.settings) }
    /** Answers the "Discard your changes?" dialog that an exit with staged edits now shows. */
    private fun discardPendingEdits() {
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
            ?: error("leaving the menu with a pending edit should ask before discarding")
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
    private fun invoke(name: String): Any? {
        val method = if (name == "createRuntimeConfig") {
            activity.javaClass.getDeclaredMethod(name, Boolean::class.javaPrimitiveType)
        } else activity.javaClass.getDeclaredMethod(name)
        return method.apply { isAccessible = true }.let {
            if (name == "createRuntimeConfig") it.invoke(activity, false) else it.invoke(activity)
        }
    }
    private fun field(name: String): Any? = activity.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)
    private fun setField(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}

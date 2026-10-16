package com.shilapi.xcertplay

import android.view.View
import android.view.ViewGroup
import android.view.InputDevice
import android.view.MotionEvent
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HomeCompactLayoutTest {
    @Config(qualifiers = "en-w900dp-h400dp-land")
    @Test fun compactLandscapeKeepsWirelessAndUsbInSeparateColumns() {
        verifyCompactArrangement(landscape = true)
    }

    @Config(qualifiers = "en-w400dp-h900dp-port")
    @Test fun compactPortraitStacksWirelessBeforeUsb() {
        verifyCompactArrangement(landscape = false)
    }

    @Config(sdk = [23], qualifiers = "zh-rCN-w1280dp-h480dp-land")
    @Test fun shortCompactHomeKeepsItsActionsReachable() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        ReflectionHelpers.setField(activity, "setupError", null)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "refreshStatus")
        val root = activity.window.decorView
        val metrics = activity.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec((1280 * metrics.density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((480 * metrics.density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        assertFalse("A short landscape window should use the compact home", descendants(root)
            .filterIsInstance<TextView>().any { it.text == activity.getString(R.string.a_familiar_drive) })
        val picker = descendants(root).filterIsInstance<Button>()
            .single { it.text == activity.getString(R.string.choose_iphone) }
        val usb = descendants(root).filterIsInstance<Button>()
            .single { it.text == activity.getString(R.string.connect_with_usb) }
        val settings = descendants(root).filterIsInstance<Button>()
            .single { it.text == activity.getString(R.string.settings) }
        listOf(picker, usb, settings).forEach { action ->
            val visible = android.graphics.Rect()
            assertTrue("${action.text} must remain reachable on the short home", action.getGlobalVisibleRect(visible))
            assertEquals("${action.text} must not be clipped", action.height, visible.height())
        }
        val card = picker.parent as ViewGroup
        assertTrue("Phone picker bottom ${picker.bottom} exceeds card content ${card.height - card.paddingBottom}",
            picker.bottom <= card.height - card.paddingBottom)
        val idleStatus = descendants(card).filterIsInstance<TextView>()
            .single { it.text == activity.getString(R.string.ready_when_you_are) }
        assertEquals(View.GONE, idleStatus.visibility)
        card.measure(View.MeasureSpec.makeMeasureSpec(card.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((96 * metrics.density).toInt(), View.MeasureSpec.AT_MOST))
        card.layout(card.left, card.top, card.right, card.top + card.measuredHeight)
        assertTrue("OEM height limit must not crop the picker", picker.bottom <= card.height - card.paddingBottom)
        root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((250 * metrics.density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val scroll = descendants(root).filterIsInstance<ScrollView>().single()
        assertTrue("Overflowing home must scroll", scroll.getChildAt(0).height > scroll.height)
        scroll.scrollTo(0, scroll.getChildAt(0).height - scroll.height)
        val visible = android.graphics.Rect()
        assertTrue(picker.getGlobalVisibleRect(visible))
        assertEquals("Scroll must reveal the full picker", picker.height, visible.height())
        controller.pause().stop().destroy()
    }

    @Config(sdk = [23], qualifiers = "zh-rCN-w1280dp-h480dp-land")
    @Test fun overflowingCompactHomeRespondsToFingerDrag() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.window.decorView
        val metrics = activity.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec((1280 * metrics.density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((200 * metrics.density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        val scroll = descendants(root).filterIsInstance<ScrollView>().single()
        assertTrue("A constrained home must have vertical overflow", scroll.getChildAt(0).height > scroll.height)
        val scrollLocation = IntArray(2).also(scroll::getLocationInWindow)
        val x = scrollLocation[0] + scroll.width / 2f
        val downY = scrollLocation[1] + scroll.height * 0.8f
        val upY = scrollLocation[1] + scroll.height * 0.2f
        val downTime = SystemClock.uptimeMillis()
        fun touch(action: Int, eventTime: Long, y: Float) = MotionEvent.obtain(
            downTime, eventTime, action, x, y, 0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        root.dispatchTouchEvent(touch(MotionEvent.ACTION_DOWN, downTime, downY))
        root.dispatchTouchEvent(touch(MotionEvent.ACTION_MOVE, downTime + 120, upY))
        root.dispatchTouchEvent(touch(MotionEvent.ACTION_UP, downTime + 180, upY))

        assertTrue("A finger drag must move an overflowing home", scroll.scrollY > 0)
        controller.pause().stop().destroy()
    }

    @Test fun selectedPhoneAndSetupErrorsKeepTheirStatus() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        ReflectionHelpers.setField(activity, "setupError", null)
        DiPlayPreferences.savePhone(activity, "AA:BB:CC:DD:EE:01", "iPhone")
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "refreshStatus")
        val status = ReflectionHelpers.getField<TextView>(activity, "status")
        assertEquals(View.VISIBLE, status.visibility)
        assertTrue(status.text.toString().endsWith("iPhone"))
        ReflectionHelpers.setField(activity, "setupError", "fixture")
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "refreshStatus")
        assertEquals(View.VISIBLE, status.visibility)
        assertEquals(activity.getString(R.string.setup_needs_attention), status.text)
        controller.pause().stop().destroy()
    }

    private fun verifyCompactArrangement(landscape: Boolean) {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.window.decorView
        val metrics = activity.resources.displayMetrics
        val config = activity.resources.configuration
        val width = (config.screenWidthDp * metrics.density).toInt()
        val height = (config.screenHeightDp * metrics.density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        fun text(id: Int) = descendants(root).filterIsInstance<TextView>()
            .single { it.text == activity.getString(id) }
        val connect = text(R.string.connect_phone)
        val usb = text(R.string.connect_with_usb)
        val connectPosition = IntArray(2).also(connect::getLocationOnScreen)
        val usbPosition = IntArray(2).also(usb::getLocationOnScreen)
        if (landscape) assertTrue(usbPosition[0] >= connectPosition[0] + connect.width)
        else assertTrue(usbPosition[1] >= connectPosition[1] + connect.height)
        assertFalse(descendants(root).filterIsInstance<Button>().any { it.text == "无线 CarPlay" })
        assertTrue(text(R.string.car_home).visibility == View.VISIBLE)
        assertFalse(descendants(root).filterIsInstance<TextView>().any {
            it.text == activity.getString(R.string.a_familiar_drive) ||
                it.text == activity.getString(R.string.plug_your_iphone_into_a_usb_data_port_allow_carplay_when_y)
        })
        controller.pause().stop().destroy()
    }

    @Config(qualifiers = "en-w2667dp-h1333dp")
    @Test fun fullSizeMultiWindowKeepsTheFullHome() {
        assertFalse(homeIsCompact(multiWindow = true))
    }

    @Config(qualifiers = "en-w1333dp-h400dp")
    @Test fun shortWindowUsesTheCompactHome() {
        assertTrue(homeIsCompact(multiWindow = true))
    }

    @Config(qualifiers = "en-w500dp-h800dp")
    @Test fun narrowWindowUsesTheCompactHome() {
        assertTrue(homeIsCompact(multiWindow = false))
    }

    @Test fun homeOffersAnExplicitExitAction() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).setup().get()
        assertTrue(descendants(activity.window.decorView)
            .filterIsInstance<Button>()
            .any { it.text == activity.getString(R.string.exit_application) })
    }

    @Test fun oneTapOnHomeExitClosesTheTaskWithoutAConfirmationDialog() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).setup().get()
        val exit = descendants(activity.window.decorView).filterIsInstance<Button>()
            .single { it.text == activity.getString(R.string.exit_application) }

        assertTrue(exit.performClick())
        assertTrue(activity.isFinishing)
        assertTrue(ReflectionHelpers.getField<Boolean>(activity, "exitRequested"))
    }

    @Test fun confirmedExitFinishesImmediatelyAndRepeatedRequestsAreIgnored() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).setup().get()
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")
        assertTrue(activity.isFinishing)
        assertTrue(ReflectionHelpers.getField<Boolean>(activity, "exitRequested"))
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")
        assertTrue(activity.isFinishing)
    }

    @Test fun confirmedExitClosesHomeBeforeStoppingAnActiveSession() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).setup().get()
        CarPlayBackgroundSession.clear()
        var stopRequests = 0
        val stop: ((() -> Unit) -> Unit) = { completion ->
            assertTrue("Home task must close before OEM session teardown starts", activity.isFinishing)
            stopRequests++
            completion()
        }
        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", stop)

        try {
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")

            assertTrue(activity.isFinishing)
            assertEquals(1, stopRequests)
        } finally {
            CarPlayBackgroundSession.clear()
        }
    }

    @Test fun confirmedExitReusesTheCarPlayHostsFullApplicationExitAction() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).setup().get()
        CarPlayBackgroundSession.clear()
        var stopRequests = 0
        var exitRequests = 0
        val stop: ((() -> Unit) -> Unit) = { completion -> stopRequests++; completion() }
        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", stop)
        ReflectionHelpers.setField(CarPlayBackgroundSession, "applicationExitAction") {
            assertTrue("Home task must close before host shutdown starts", activity.isFinishing)
            exitRequests++
        }

        try {
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")

            assertEquals(1, exitRequests)
            assertEquals(0, stopRequests)
        } finally {
            CarPlayBackgroundSession.clear()
        }
    }

    @Config(qualifiers = "en-w900dp-h400dp-land")
    @Test fun landscapeHomeKeepsUsbAndSettingsBelowAndExitAfterCarHome() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).setup().get()
        val buttons = descendants(activity.window.decorView).filterIsInstance<Button>()
            .associateBy { it.text.toString() }
        val usb = buttons.getValue(activity.getString(R.string.connect_with_usb))
        val exit = buttons.getValue(activity.getString(R.string.exit_application))
        assertTrue("USB action must remain visible in landscape", usb.isShown)
        val carHome = buttons.getValue(activity.getString(R.string.car_home))
        val header = carHome.parent as ViewGroup
        assertSame(header, exit.parent)
        assertEquals(header.indexOfChild(carHome) + 1, header.indexOfChild(exit))
        val wiredColumn = usb.parent as ViewGroup
        assertFalse(buttons.containsKey("无线 CarPlay"))
        val expected = (54 * activity.resources.displayMetrics.density).toInt()
        listOf(
            activity.getString(R.string.connect_phone),
            activity.getString(R.string.connect_with_usb),
            activity.getString(R.string.settings),
        ).forEach { title ->
            val button = buttons.getValue(title)
            assertEquals(title, expected, (button.layoutParams as LinearLayout.LayoutParams).height)
        }
    }

    private fun homeIsCompact(multiWindow: Boolean): Boolean {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).setup().get()
        shadowOf(activity).setInMultiWindowMode(multiWindow)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
        val hero = activity.getString(R.string.a_familiar_drive)
        return descendants(activity.window.decorView).filterIsInstance<TextView>().none { it.text == hero }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}

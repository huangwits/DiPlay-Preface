package com.shilapi.xcertplay

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.shilapi.xcertplay.ui.home.PrefaceHomeScreen
import com.shilapi.xcertplay.ui.theme.XcertplayTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-w1280dp-h480dp-land")
class HomeCompactLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @org.junit.Before fun useManualUiClock() { compose.mainClock.autoAdvance = false }
    private var host: ActivityController<out ComponentActivity>? = null
    private val actions = mutableListOf<String>()

    @After fun cleanup() {
        host?.pause()?.stop()?.destroy()
        CarPlayBackgroundSession.clear()
    }

    private fun showHome(ready: Boolean = true, running: Boolean = false, status: String = "准备就绪") {
        compose.setContent {
            XcertplayTheme(darkTheme = true, scaled = false) {
                PrefaceHomeScreen(status, "0.2.20.31", running, ready,
                    { actions += "connect" }, { actions += "usb" }, { actions += "phone" },
                    { actions += "disconnect" }, { actions += "settings" }, { actions += "car-home" },
                    { actions += "exit" }, { actions += "appearance" }, { actions += "wireless-settings" })
            }
        }
        compose.waitForIdle()
    }

    private fun scrollToBottom(label: String) {
        compose.onNodeWithText(label).onAncestors().filterToOne(hasScrollAction())
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 10000f) }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
    }

    @Test fun shortLandscapeKeepsTheMainActionsVisibleAndUsbBeforeSettings() {
        showHome()
        compose.onAllNodesWithText("连接 CarPlay").filterToOne(hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("选择手机").assertIsDisplayed()
        val usb = compose.onNodeWithText("USB CarPlay").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithText("设置").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val phone = compose.onNodeWithText("选择手机").fetchSemanticsNode().boundsInRoot
        assertTrue(usb.left > phone.right)
        val wireless = compose.onNodeWithText("无线设置").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(usb.top < wireless.top && wireless.top < settings.top)
    }

    @Config(qualifiers = "zh-rCN-w960dp-h360dp-land")
    @Test fun lowHeadUnitWindowKeepsWirelessSettingsAndGeneralSettingsVisible() {
        showHome()
        compose.onNodeWithText("USB CarPlay").assertIsDisplayed()
        compose.onNodeWithText("无线设置").assertIsDisplayed()
        compose.onNodeWithText("设置").assertIsDisplayed()
    }

    @Config(qualifiers = "zh-rCN-w400dp-h800dp-port")
    @Test fun portraitStacksWirelessBeforeUsbAndScrollRevealsSettings() {
        showHome()
        val connect = compose.onAllNodesWithText("连接 CarPlay").filterToOne(hasClickAction()).fetchSemanticsNode().boundsInRoot
        val usb = compose.onNodeWithText("USB CarPlay").fetchSemanticsNode().boundsInRoot
        assertTrue(connect.top < usb.top)
        scrollToBottom("设置")
        compose.onNodeWithText("设置").assertIsDisplayed().performClick()
        assertEquals(listOf("settings"), actions)
    }

    @Config(qualifiers = "zh-rCN-w700dp-h250dp-land")
    @Test fun veryShortWindowCanScrollToPhoneSelection() {
        showHome()
        scrollToBottom("选择手机")
        compose.onNodeWithText("选择手机").assertIsDisplayed().performClick()
        assertEquals(listOf("phone"), actions)
    }

    @Test fun carHomeIsFollowedByExitAndEachActionHasItsOwnCallback() {
        showHome()
        val home = compose.onNodeWithText("车机主页").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val exit = compose.onNodeWithText("退出").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(home.right <= exit.left)
        compose.onNodeWithText("USB CarPlay").performClick()
        compose.onAllNodesWithText("连接 CarPlay").filterToOne(hasClickAction()).performClick()
        compose.onNodeWithText("车机主页").performClick()
        compose.onNodeWithText("退出").performClick()
        assertEquals(listOf("usb", "connect", "car-home", "exit"), actions)
    }

    @Test fun connectionStatusIsSeparateFromThePrimaryAction() {
        showHome(status = "准备就绪")
        val status = compose.onNodeWithText("准备就绪").fetchSemanticsNode().boundsInRoot
        val connect = compose.onAllNodesWithText("连接 CarPlay").filterToOne(hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(status.bottom < connect.top)
    }

    @Test fun setupFailureDisablesConnectionAndKeepsItsExplanation() {
        showHome(ready = false, status = "连接配置需要处理")
        compose.onNodeWithText("连接配置需要处理").assertIsDisplayed()
        compose.onAllNodesWithText("连接 CarPlay").filterToOne(hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("USB CarPlay").assertIsEnabled()
    }

    @Test fun activeSessionOffersOpenAndDisconnect() {
        showHome(running = true, status = "CarPlay 已连接")
        compose.onNodeWithText("打开 CarPlay").performClick()
        scrollToBottom("断开连接")
        compose.onNodeWithText("断开连接").assertIsDisplayed().performClick()
        assertEquals(listOf("connect", "disconnect"), actions)
    }

    @Test fun selectedPhoneNameAppearsOnlyOnceOnHome() {
        showHome(status = "已准备连接：我的 iPhone")
        compose.onNodeWithText("已准备连接：我的 iPhone").assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithText("我的 iPhone", substring = true).fetchSemanticsNodes().size)
    }


    @Test fun wirelessSettingsRemainsAvailableAfterApprovalAndDuringAnActiveSession() {
        showHome(running = true)
        compose.onNodeWithText("无线设置").performClick()
        assertEquals(listOf("wireless-settings"), actions)
    }

    @Test fun repeatedExitRequestsAreIgnored() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup().visible()
        host = controller
        val activity = controller.get()
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")
        assertTrue(activity.isFinishing)
        assertTrue(ReflectionHelpers.getField<Boolean>(activity, "exitRequested"))
    }

    @Test fun exitClosesHomeBeforeStoppingAnActiveSession() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup().visible()
        host = controller
        val activity = controller.get()
        var stops = 0
        val stop: ((() -> Unit) -> Unit) = { completion ->
            assertTrue(activity.isFinishing); stops++; completion()
        }
        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", stop)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")
        assertEquals(1, stops)
    }

    @Test fun exitUsesTheExistingHostsFullShutdownAction() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup().visible()
        host = controller
        val activity = controller.get()
        var exits = 0
        var stops = 0
        val stop: ((() -> Unit) -> Unit) = { completion -> stops++; completion() }
        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", stop)
        ReflectionHelpers.setField(CarPlayBackgroundSession, "applicationExitAction") {
            assertTrue(activity.isFinishing); exits++
        }
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "exitApplication")
        assertEquals(1, exits)
        assertEquals(0, stops)
    }
}

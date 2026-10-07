package com.shilapi.xcertplay

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import com.shilapi.xcertplay.e01switch.E01BluetoothSwitchActivity
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-rCN-mdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreparationLayoutTest {
    @Test fun landscapeKeepsControlsBesideLogsAndPortraitStacksWithoutClipping() {
        val panel = sample()
        for ((width, height) in listOf(1920 to 720, 960 to 360, 640 to 240, 360 to 640)) {
            layout(panel, width, height)
            val left = panel.controlScroll
            val right = panel.logPanel
            assertTrue(right.width > 0 && panel.logScroll.height > 0)
            if (width > height) {
                assertTrue("$width: columns overlap", left.right < right.left)
                assertEquals(height, left.height)
                assertTrue(right.width > left.width)
            } else {
                assertTrue(left.bottom < right.top)
            }
            assertTrue(right.right <= width && right.bottom <= height)
            panel.controlScroll.fullScroll(View.FOCUS_DOWN)
            layout(panel, width, height)
            assertTrue("return button reachable", panel.back.bottom <= panel.controlScroll.height + panel.controlScroll.scrollY)
        }
    }

    @Test fun scrollingBackDoesNotJumpWhenANewLineArrivesAndCopyUsesVisibleHistory() {
        val panel = sample()
        panel.showLogs((1..90).joinToString("\n") { "13:30:00  测试日志 $it" })
        layout(panel, 960, 360)
        shadowOf(Looper.getMainLooper()).idle()
        panel.logScroll.scrollTo(0, 30)
        panel.logScroll.viewTreeObserver.javaClass.getDeclaredMethod("dispatchOnScrollChanged")
            .apply { isAccessible = true }.invoke(panel.logScroll.viewTreeObserver)
        val before = panel.logScroll.scrollY
        val updated = panel.logText.text.toString() + "\n13:30:01  新日志"
        panel.showLogs(updated)
        layout(panel, 960, 360)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before, panel.logScroll.scrollY)
        buttons(panel).single { it.text == "复制日志" }.performClick()
        val clipboard = panel.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(updated, clipboard.primaryClip!!.getItemAt(0).text.toString())
        buttons(panel).single { it.text == "跟随最新" }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(panel.logScroll.canScrollVertically(1))
    }

    @Test fun rendersDayAndNightUsingActualAndroidViews() {
        val panel = sample()
        for (night in listOf(false, true)) {
            panel.applyTheme(night)
            layout(panel, 1280, 480)
            assertEquals(WaitingScreenColors.of(night).text, panel.stage.currentTextColor)
            System.getenv("DIPLAY_LAYOUT_PREVIEW_DIR")?.let { directory ->
                val bitmap = Bitmap.createBitmap(1280, 480, Bitmap.Config.ARGB_8888)
                panel.draw(Canvas(bitmap))
                File(directory).mkdirs()
                File(directory, if (night) "waiting-night.png" else "waiting-day.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }

    @Test @Config(qualifiers = "zh-rCN-night-mdpi")
    fun bluetoothToolSharesTheLayoutAndDisplaysResultsWithoutStartingACheck() {
        val host = Robolectric.buildActivity(E01BluetoothSwitchActivity::class.java).setup()
        try {
            val panel = E01BluetoothSwitchActivity::class.java.getDeclaredField("panel")
                .apply { isAccessible = true }.get(host.get()) as ConnectionWaitingView
            E01BluetoothSwitchActivity::class.java.getDeclaredMethod("say", String::class.java)
                .apply { isAccessible = true }.invoke(host.get(), "示例：等待用户点击检查连接。\n尚未执行蓝牙切换。\ntoken=private-token")
            layout(panel, 1280, 480)
            assertTrue(panel.controlScroll.right < panel.logPanel.left)
            assertTrue(panel.logText.text.contains("尚未执行蓝牙切换"))
            assertTrue(panel.logText.text.contains("先点“检查连接”"))
            assertFalse(panel.logText.text.contains("private-token"))
            val labels = buttons(panel).map { it.text.toString() }
            for (label in listOf("检查连接", "尝试切换", "恢复原厂", "蓝牙设置", "复制日志")) {
                assertTrue(label, labels.contains(label))
            }
            assertFalse(host.get().getFileStreamPath("adb.private").exists())
            System.getenv("DIPLAY_LAYOUT_PREVIEW_DIR")?.let { directory ->
                val bitmap = Bitmap.createBitmap(1280, 480, Bitmap.Config.ARGB_8888)
                panel.draw(Canvas(bitmap))
                File(directory, "bluetooth-night.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally { host.pause().stop().destroy() }
    }

    private fun sample() = ConnectionWaitingView(RuntimeEnvironment.getApplication()).apply {
        stage.text = "正在等待 USB 连接的 iPhone"
        instructions.text = "请用 USB 数据线连接并解锁 iPhone。"
        confirmed.text = "最近确认节点：MFi 认证已就绪"
        confirmed.visibility = View.VISIBLE
        showFailure("UsbManager could not open the iPhone")
        showLogs(listOf(
            "13:30:00.000  阶段：MFi 认证已就绪",
            "13:30:00.100  STEP usb/discovery: searching for an iPhone USB device",
            "13:30:00.200  阶段：正在请求 iPhone USB 权限",
            "13:30:00.300  iPhone USB permission granted",
            "13:30:00.400  阶段：正在选择 CarPlay 配置",
            "13:30:00.500  ERROR UsbManager could not open the iPhone",
            "13:30:02.500  Reconnecting after USB failure",
            "13:30:02.600  阶段：正在查找 iPhone",
            "13:30:02.700  阶段：正在等待 USB 连接的 iPhone",
        ).joinToString("\n"))
    }

    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun layout(view: View, width: Int, height: Int) {
        fun invalidate(v: View) {
            v.forceLayout()
            if (v is ViewGroup) (0 until v.childCount).forEach { invalidate(v.getChildAt(it)) }
        }
        repeat(3) {
            invalidate(view)
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
    }
}

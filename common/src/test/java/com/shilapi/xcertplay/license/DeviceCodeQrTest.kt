package com.shilapi.xcertplay.license

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "zh-rCN-mdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceCodeQrTest {
    private val label = "DP-DEVICE1-" + "0123456789abcdef".repeat(4)

    @Test fun renderedQrDecodesToTheExactExistingDeviceCodeAtSmallAndLargeSizes() {
        val view = DeviceCodeQrView(RuntimeEnvironment.getApplication())
        verifyQr(view, label) { view.setDeviceCode(label) }
    }

    @Test fun phoneUrlQrDecodesIncludingTheCompleteTemporaryToken() {
        val view = DeviceCodeQrView(RuntimeEnvironment.getApplication())
        val url = "http://192.168.43.1:54321/activate/" + "abcdef0123456789".repeat(3) + "/"
        verifyQr(view, url) { view.setWebAddress(url) }
    }

    private fun verifyQr(view: DeviceCodeQrView, expected: String, set: () -> Unit) {
        set()
        for (size in listOf(160, 240, 320)) {
            view.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels))))
            assertEquals(expected, decoded.text)
            assertEquals(android.graphics.Color.WHITE, pixels[0])
            bitmap.recycle()
        }
    }

    @Test fun phoneEntryIsClosedWhenTheCarActivityLeavesTheForeground() {
        val host = Robolectric.buildActivity(OfflineLicenseActivity::class.java).setup()
        val session = PhoneActivationServer(java.net.InetAddress.getByName("127.0.0.1"), label, "starts181004", {})
        try {
            val activity = host.get()
            activity.javaClass.getDeclaredMethod("showDeviceCode", String::class.java).apply { isAccessible = true }.invoke(activity, label)
            activity.javaClass.getDeclaredMethod("attachPhoneSession", PhoneActivationServer::class.java).apply { isAccessible = true }.invoke(activity, session)
            val qr = activity.javaClass.getDeclaredField("deviceQr").apply { isAccessible = true }.get(activity) as DeviceCodeQrView
            assertTrue(qr.contentDescription.toString().contains("手机协助"))
            assertFalse(session.isClosed)
            host.pause().stop()
            assertTrue(session.isClosed)
            assertTrue(qr.contentDescription.toString().contains("设备码"))
            host.start().resume()
            assertNull(activity.javaClass.getDeclaredField("phoneSession").apply { isAccessible = true }.get(activity))
        } finally { session.close(); host.pause().stop().destroy() }
    }

    @Test fun authorizationPageShowsScannableCodeAndRetainsActivationImport() {
        val host = Robolectric.buildActivity(OfflineLicenseActivity::class.java).setup()
        try {
            val activity = host.get()
            activity.javaClass.getDeclaredMethod("showDeviceCode", String::class.java).apply { isAccessible = true }.invoke(activity, label)
            val qr = activity.javaClass.getDeclaredField("deviceQr").apply { isAccessible = true }.get(activity) as DeviceCodeQrView
            assertEquals(View.VISIBLE, qr.visibility)
            assertTrue(qr.contentDescription.toString().contains("扫一扫"))
            val device = activity.javaClass.getDeclaredField("device").apply { isAccessible = true }.get(activity) as TextView
            assertEquals(label, device.text.toString())
            System.getenv("DIPLAY_LAYOUT_PREVIEW_DIR")?.let { directory ->
                val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
                val width = 1280; val height = 480
                content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                content.layout(0, 0, width, height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                content.draw(Canvas(bitmap))
                File(directory).mkdirs()
                File(directory, "offline-device-qr.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally { host.pause().stop().destroy() }
    }
}

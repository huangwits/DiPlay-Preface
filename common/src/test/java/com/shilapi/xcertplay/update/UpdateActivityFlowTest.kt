package com.shilapi.xcertplay.update

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.DiPlayActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContextImpl
import org.robolectric.shadows.ShadowAlertDialog
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "zh-rCN", shadows = [UpdateActivityFlowTest.PrefaceContext::class])
@Suppress("DEPRECATION")
class UpdateActivityFlowTest {
    @Implements(className = "android.app.ContextImpl", isInAndroidSdk = false)
    class PrefaceContext : ShadowContextImpl() {
        @Implementation fun getPackageName(): String = UpdateCatalog.PACKAGE
    }
    @Before fun configureInstalledApp() {
        val app = RuntimeEnvironment.getApplication()
        val installed = app.packageManager.getPackageInfo(app.applicationInfo.packageName, 0)
        installed.packageName = UpdateCatalog.PACKAGE
        installed.versionName = "0.2.15.4"; installed.versionCode = 44
        installed.signatures = arrayOf(Signature("0123456789abcdef"))
        shadowOf(app.packageManager).installPackage(installed)
        CarPlayBackgroundSession.active = false
    }

    @Test fun aboutPageExposesUpdateAndOpeningItDoesNotStartDownload() {
        val app = RuntimeEnvironment.getApplication()
        val host = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(app, DiPlayActivity::class.java).putExtra("page", "about")).setup()
        try {
            views(host.get().window.decorView).filterIsInstance<Button>().single { it.text == "检查更新" }.performClick()
            assertEquals(UpdateActivity::class.java.name, shadowOf(host.get()).nextStartedActivity.component?.className)
        } finally { host.pause().stop().destroy() }
        val updates = Robolectric.buildActivity(UpdateActivity::class.java).setup()
        try {
            val button = updates.get().window.decorView.findViewWithTag<Button>("update-action")
            assertTrue(button.isEnabled); assertEquals("检查更新", button.text)
            assertNull(shadowOf(updates.get()).nextStartedActivity)
            assertFalse(File(updates.get().filesDir, "app-update/install.sh").exists())
        } finally { updates.pause().stop().destroy() }
    }

    @Test fun checksDownloadsCachesRevalidatesAndOpensLegacyInstallerWithoutAdb() {
        val host = Robolectric.buildActivity(UpdateActivity::class.java).setup()
        val activity = host.get()
        try {
            val bytes = "fixture payload, package identity supplied by PackageManager shadow".toByteArray()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val name = "DiPlay-Preface-v0.2.16.1.apk"
            val info = PackageInfo().apply {
                packageName = UpdateCatalog.PACKAGE; versionName = "0.2.16.1"; versionCode = 45
                signatures = arrayOf(Signature("0123456789abcdef"))
            }
            val metadata = JSONArray().put(JSONObject().put("tag_name", "v0.2.16.1").put("body", "检查更新")
                .put("assets", JSONArray().put(JSONObject().put("name", name).put("size", bytes.size)
                    .put("digest", "sha256:$digest").put("browser_download_url", "${UpdateCatalog.REPOSITORY}/releases/download/v0.2.16.1/$name")))).toString().toByteArray()
            var requests = 0
            activity.client = UpdateClient { url -> object : HttpURLConnection(url) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getInputStream(): ByteArrayInputStream {
                    requests++
                    if (url.host == "api.github.com") return ByteArrayInputStream(metadata)
                    File(activity.cacheDir, "update").listFiles().orEmpty().filter { it.extension == "part" }.forEach {
                        shadowOf(activity.packageManager).setPackageArchiveInfo(it.absolutePath, info)
                    }
                    return ByteArrayInputStream(bytes)
                }
            } }
            val button = activity.window.decorView.findViewWithTag<Button>("update-action")
            button.performClick(); await { button.text == "下载 0.2.16.1" }
            assertEquals(1, requests)
            button.performClick(); await { button.text == "安装 0.2.16.1" }
            val saved = File(activity.cacheDir, "update/$digest.apk")
            assertArrayEquals(bytes, saved.readBytes())
            shadowOf(activity.packageManager).setPackageArchiveInfo(saved.absolutePath, info)
            assertTrue(File(activity.cacheDir, "update/pending.json").isFile)
            assertEquals(2, requests)
            CarPlayBackgroundSession.active = true
            button.performClick()
            assertNull(shadowOf(activity).nextStartedActivity)
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.contains("请先结束 CarPlay") })
            CarPlayBackgroundSession.active = false
            button.performClick()
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            var installer: Intent? = null
            await { installer = shadowOf(activity).peekNextStartedActivity(); installer != null }
            assertEquals(Intent.ACTION_VIEW, installer!!.action)
            assertEquals("file", installer!!.data?.scheme)
            val external = File(installer!!.data!!.path!!)
            assertArrayEquals(bytes, external.readBytes())
            assertTrue(external.path.startsWith(activity.getExternalFilesDir("updates")!!.path))
            assertFalse(File(activity.filesDir, "adb.private").exists())
        } finally { CarPlayBackgroundSession.active = false; host.pause().stop().destroy() }
    }

    @Test
    @Config(sdk = [28], qualifiers = "zh-rCN-w960dp-h360dp-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun downloadedUpdateFitsShortAndPortraitWindows() {
        val app = RuntimeEnvironment.getApplication()
        val directory = File(app.cacheDir, "update").apply { mkdirs() }
        val sha = "c".repeat(64)
        File(directory, "$sha.apk").writeText("cached")
        val release = UpdateRelease("0.2.16.1", "DiPlay-Preface-v0.2.16.1.apk",
            "${UpdateCatalog.REPOSITORY}/releases/download/v0.2.16.1/DiPlay-Preface-v0.2.16.1.apk", 6, sha,
            "新增应用内检查更新和下载。\n支持 E01 车机覆盖安装，保留设置和授权信息。")
        File(directory, "pending.json").writeText(release.json().toString())
        val host = Robolectric.buildActivity(UpdateActivity::class.java).setup()
        try {
            val root = host.get().findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            for ((width, height) in listOf(1280 to 480, 960 to 360, 480 to 800)) {
                repeat(3) {
                    views(root).forEach { it.forceLayout() }
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, width, height)
                }
                val button = root.findViewWithTag<Button>("update-action")
                assertEquals("安装 0.2.16.1", button.text)
                assertTrue(button.width > 0 && button.right <= (button.parent as View).width)
                System.getenv("DIPLAY_UPDATE_SCREENSHOTS")?.let { output ->
                    val folder = File(output).apply { mkdirs() }
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                    File(folder, "update-${width}x${height}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            assertNull(shadowOf(host.get()).nextStartedActivity)
        } finally { host.pause().stop().destroy() }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Update UI did not reach the expected state")
    }
    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }
}

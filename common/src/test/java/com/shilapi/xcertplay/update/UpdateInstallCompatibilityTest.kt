package com.shilapi.xcertplay.update

import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.shilapi.xcertplay.DiPlayActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.geelyplay.preface.update.UpdateInstaller

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 28, 33])
class UpdateInstallCompatibilityTest {
    @Test fun installationUsesOnlySupportedPermissionApis() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        val apk = File(activity.cacheDir, "update/test/DiPlay.apk").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val intent = UpdateInstaller.intent(activity, apk)
        assertEquals(if (Build.VERSION.SDK_INT < 26) Intent.ACTION_VIEW
            else Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        if (Build.VERSION.SDK_INT < 26) {
            assertEquals("content", intent.data!!.scheme)
            assertEquals("application/vnd.android.package-archive", intent.type)
        }
    }
}

package com.shilapi.xcertplay.update

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28])
@Suppress("DEPRECATION")
class UpdateInstallerTest {
    private fun info(version: String, code: Int) = PackageInfo().apply {
        packageName = UpdateCatalog.PACKAGE; versionName = version; versionCode = code
        signatures = arrayOf(Signature("0123456789abcdef"))
    }

    @Test fun permitsOnlyMatchingPackageSignerAndStrictlyNewerVersionCode() {
        val installed = info("0.2.15.4", 44)
        UpdateInstaller.validateIdentity(installed, info("0.2.16.1", 45), "0.2.16.1")
        for (bad in listOf(
            info("0.2.16.1", 45).apply { packageName = "com.shihab.diplay" },
            info("0.2.16.1", 45).apply { signatures = arrayOf(Signature("fedcba9876543210")) },
            info("0.2.16.1", 45).apply { signatures = emptyArray() },
            info("0.2.16.1", 44), info("0.2.16.2", 46), info("0.2.15.3", 43)
        )) assertThrows(IOException::class.java) { UpdateInstaller.validateIdentity(installed, bad, "0.2.16.1") }
    }

    @Test fun legacyInstallerUsesFileUriAndOreoRequestsInstallPermission() {
        val app = RuntimeEnvironment.getApplication()
        val file = File(app.getExternalFilesDir("updates"), "DiPlay.apk").apply { parentFile!!.mkdirs(); writeText("fixture") }
        val intent = UpdateInstaller.intent(app, file)
        if (Build.VERSION.SDK_INT < 24) {
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("file", intent.data!!.scheme)
            assertEquals("application/vnd.android.package-archive", intent.type)
        } else assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
    }

    @Test fun tamperedFileIsRejectedBeforePackageManagerOrInstaller() {
        val app = RuntimeEnvironment.getApplication()
        val file = File(app.cacheDir, "bad.apk").apply { writeText("damaged") }
        val release = UpdateRelease("0.2.16.1", "DiPlay-Preface-v0.2.16.1.apk", "", 7, "a".repeat(64), "")
        assertThrows(IOException::class.java) { UpdateInstaller.validate(app, release, file) }
    }
}

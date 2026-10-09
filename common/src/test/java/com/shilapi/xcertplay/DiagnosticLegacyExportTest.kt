package com.shilapi.xcertplay

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class DiagnosticLegacyExportTest {
    @Test fun android51UsesInstallTimePermissionAndWritesTheCompleteReport() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun checkSelfPermission(permission: String): Int =
                throw AssertionError("Android 5.1 has no checkSelfPermission")

            override fun checkPermission(permission: String, pid: Int, uid: Int): Int {
                assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE, permission)
                return PackageManager.PERMISSION_GRANTED
            }
        }
        val originalSdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        try {
            val report = "Preface diagnostic report\nUSB: ready\n"
            val saved = DiagnosticExportStore.saveWithoutPicker(context, "legacy.txt", report, shareable = false)
            assertTrue(saved.savedToDownloads)
            assertFalse(saved.savedInApp)
            val file = File(saved.savedPath!!)
            assertEquals(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DiPlay"), file.parentFile)
            assertEquals(report, file.readText())
            file.delete()
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", originalSdk)
        }
    }
}

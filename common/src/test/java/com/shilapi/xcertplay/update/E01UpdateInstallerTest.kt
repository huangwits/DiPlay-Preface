package com.shilapi.xcertplay.update

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class E01UpdateInstallerTest {
    @Test fun onlyPrivatePrefacePathsAndExactDigestsAreRendered() {
        val base = "/data/user/0/${UpdateCatalog.PACKAGE}/files/app-update"
        val sha = "a".repeat(64)
        val apk = "/data/user/0/${UpdateCatalog.PACKAGE}/cache/update/$sha.apk"
        val template = RuntimeEnvironment.getApplication().assets.open("app-update/install.sh").bufferedReader().use { it.readText() }
        val script = E01UpdateInstaller.render(template, base, apk, sha)
        assertTrue(script.contains("APK='$apk'"))
        assertFalse(script.contains("__SHA__"))
        assertTrue(script.contains("SYSTEM_APP='0'"))
        assertTrue(E01UpdateInstaller.render(template, base, apk, sha, true).contains("SYSTEM_APP='1'"))
        for (invalid in listOf("$base;reboot", base.replace("preface", "other"), "/data/../$base"))
            assertThrows(IllegalArgumentException::class.java) { E01UpdateInstaller.render(template, invalid, apk, sha) }
        assertThrows(IllegalArgumentException::class.java) { E01UpdateInstaller.render(template, base, "$apk;reboot", sha) }
        assertThrows(IllegalArgumentException::class.java) { E01UpdateInstaller.render(template, base, apk, "x".repeat(64)) }
    }

    @Test fun diagnosticUsesTheInstalledSystemFlagNotRootAvailabilityOrPathGuessing() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.applicationInfo
        info.flags = 0
        info.sourceDir = "/system/looks-like-a-system-app.apk"
        assertFalse(E01UpdateInstaller.systemInstalled(app))
        info.flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM
        info.sourceDir = "/data/app/updated-system-app/base.apk"
        assertTrue(E01UpdateInstaller.systemInstalled(app))
        assertTrue(E01UpdateInstaller.diagnostics(app).contains("systemApp=true"))
    }

    @Test fun previousResultIsReplacedAndStaleProgressDoesNotBlockRetries() {
        val app = RuntimeEnvironment.getApplication()
        val folder = File(app.filesDir, "app-update").apply { mkdirs() }
        val result = File(folder, "result").apply { writeText("success\n") }
        E01UpdateInstaller.writeStatus(folder, "queued")
        assertEquals("queued", result.readText().trim())
        assertTrue(E01UpdateInstaller.busy(app))
        assertTrue(result.setLastModified(System.currentTimeMillis() - 6 * 60_000))
        assertFalse(E01UpdateInstaller.busy(app))
        E01UpdateInstaller.writeStatus(folder, "launch-failed")
        assertFalse(E01UpdateInstaller.busy(app))
        assertFalse(folder.listFiles().orEmpty().any { it.extension == "tmp" })
    }
}

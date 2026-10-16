package com.shilapi.xcertplay.e01goc

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.AirPlayPersistence
import com.geelyplay.preface.E01BluetoothSwitchIntegration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.geelyplay.preface.e01goc.E01GocActivity
import com.geelyplay.preface.e01goc.E01GocManager
import com.geelyplay.preface.e01goc.E01GocPreferences
import com.geelyplay.preface.e01goc.E01RootBridge
import com.geelyplay.preface.e01goc.GocSnapshot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "zh-rCN")
class E01GocTest {
    private val original = "a".repeat(64)
    private val candidate = E01GocManager.CANDIDATE_SHA

    @Test fun firstUseRecognizesFactoryBluetoothAndKeepsAnExplicitAndroidChoice() {
        val app = RuntimeEnvironment.getApplication()
        val saved = app.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        saved.edit().clear().commit()
        assertFalse(E01GocPreferences.enabled(app))
        val pkg = com.geelyplay.preface.network.E01ConnectedPhones.PACKAGE
        val info = ApplicationInfo().apply { packageName = pkg; flags = 0 }
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = info })
        assertFalse("A user-installed namesake is not factory Bluetooth", E01GocPreferences.enabled(app))
        info.flags = ApplicationInfo.FLAG_SYSTEM
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = info })
        assertTrue(E01GocPreferences.enabled(app))
        DiPlayPreferences.savePhone(app, "10:11:22:33:44:55", "Factory phone")
        E01GocPreferences.select(app, true)
        assertEquals("10:11:22:33:44:55", DiPlayPreferences.phoneAddress(app))
        assertTrue(saved.contains("e01_goc_enabled"))
        E01GocPreferences.select(app, false)
        assertFalse(E01GocPreferences.enabled(app))
        assertNull(DiPlayPreferences.phoneAddress(app))
    }

    @Test fun selectingTheDefaultAndroidModeStillRecordsTheExplicitChoice() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        E01GocPreferences.select(app, false)
        val pkg = com.geelyplay.preface.network.E01ConnectedPhones.PACKAGE
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg; flags = ApplicationInfo.FLAG_SYSTEM }
        })
        assertFalse(E01GocPreferences.enabled(app))
    }

    @Test fun factoryMaintenanceExcludesConcurrentOperationsAndReleasesOnFailure() {
        val manager = E01GocManager(RuntimeEnvironment.getApplication()) { }
        try {
            manager.exclusive {
                assertTrue(E01GocManager.isBusy())
                try { manager.exclusive { fail("Concurrent maintenance ran") }; fail() }
                catch (_: IllegalStateException) { }
                throw java.io.IOException("fixture")
            }
            fail()
        } catch (_: java.io.IOException) { }
        assertFalse(E01GocManager.isBusy())
        manager.exclusive { assertTrue(E01GocManager.isBusy()) }
        assertFalse(E01GocManager.isBusy())
    }

    @Test fun installRequiresTheExactTestedPairAndCompatibleBackup() {
        val system = GocSnapshot(original, "", "", "running", false)
        assertFalse(system.installable(""))
        assertFalse(system.installable("${"b".repeat(64)}:$candidate"))
        assertFalse(system.installable("$original:${"b".repeat(64)}"))
        assertTrue(system.installable("$original:$candidate"))
        assertFalse(system.copy(backup = "b".repeat(64)).installable("$original:$candidate"))
        assertTrue(system.copy(backup = original, backupRecord = original).installable("$original:$candidate"))
        assertFalse(system.copy(current = candidate).installable("$candidate:$candidate"))
    }

    @Test fun corruptOrMissingBackupCannotBeRestored() {
        val installed = GocSnapshot(candidate, original, original, "running", true)
        assertTrue(installed.recoverable)
        assertFalse(installed.copy(backup = "").recoverable)
        assertFalse(installed.copy(backupRecord = "b".repeat(64)).recoverable)
        assertFalse(installed.copy(current = original).recoverable)
        assertFalse(installed.copy(backup = candidate, backupRecord = candidate).recoverable)
    }

    @Test fun binderCommandIsBoundedAndRejectsShellInjection() {
        val path = "/data/user/0/com.shihab.diplay.preface/files/e01-goc/manage.sh"
        for (action in listOf("check", "test", "install", "restore")) {
            assertTrue(E01RootBridge.command(path, action, original, 99999, true).toByteArray().size <= 220)
        }
        for (pathValue in listOf("/tmp/x;id", "/tmp/../x", "/tmp/x\n")) {
            try { E01RootBridge.command(pathValue, "test", original, 1, true); fail(pathValue) }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals("current=abc", E01RootBridge.parse("current=abc\n__DIPLAY_RC:0\n"))
        for (bad in listOf("uid=0", "__DIPLAY_RC:", "__DIPLAY_RC:1")) {
            try { E01RootBridge.parse(bad); fail(bad) } catch (_: java.io.IOException) { }
        }
    }

    @Test fun selectingModeClearsOnlyPhoneAndPreservesPerformanceSettings() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        AirPlayPersistence.saveFps(app, 60)
        DiPlayPreferences.savePhone(app, "00:11:22:33:44:55", "System phone")
        E01GocPreferences.select(app, true)
        assertTrue(E01GocPreferences.enabled(app)); assertNull(DiPlayPreferences.phoneAddress(app))
        assertEquals(60, AirPlayPersistence.loadFps(app))
        DiPlayPreferences.savePhone(app, "10:11:22:33:44:55", "Factory phone")
        E01GocPreferences.select(app, true)
        assertEquals("10:11:22:33:44:55", DiPlayPreferences.phoneAddress(app))
        E01GocPreferences.select(app, false)
        assertNull(DiPlayPreferences.phoneAddress(app))
    }

    @Test fun openingToolHasAllActionsAndDoesNotRunPrivilegedMaintenance() {
        val activity = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        try {
            val labels = views(activity.get().window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
            for (label in listOf("无线 CarPlay", "还原备份", "取消操作")) assertTrue(label, label in labels)
            for (label in listOf("检查状态", "连接测试", "安装适配")) assertFalse(label, label in labels)
            assertFalse("测试 Root 权限" in labels)
            assertFalse(java.io.File(activity.get().filesDir, "e01-goc/manage.sh").exists())
        } finally { activity.pause().stop().destroy() }
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }
}

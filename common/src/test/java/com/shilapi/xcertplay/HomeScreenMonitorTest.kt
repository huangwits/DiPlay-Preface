package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeScreenMonitorTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test fun undeclaredVendorLaunchersAreNotAssumedToBeHome() {
        assertFalse(HomeScreenMonitor.queryHomePackages(context).contains("com.byd.mycar"))
        assertFalse(HomeScreenMonitor.queryHomePackages(context).contains("com.smg.dydesktop"))
    }

    @Test
    fun externalForegroundNotificationDispatchesVisibilityChanges() {
        var observedVisibility: Boolean? = null
        val monitor = HomeScreenMonitor(context) { visible ->
            observedVisibility = visible
        }

        val info = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply { packageName = "com.geely.launcher"; name = "Home" }
        }
        org.robolectric.Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(
            android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), info)
        monitor.start()
        assertTrue(monitor.running)

        // Switch to NetEase music -> should hide (false)
        HomeScreenMonitor.notifyForegroundPackage("com.netease.cloudmusic")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(false, observedVisibility)

        // Switch to DiYou Desktop -> should show (true)
        HomeScreenMonitor.notifyForegroundPackage("com.geely.launcher")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(true, observedVisibility)

        // Switch to 360 Panoramic Camera -> should hide (false)
        HomeScreenMonitor.notifyForegroundPackage("com.byd.panoramic")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(false, observedVisibility)

        monitor.stop()
        assertFalse(monitor.running)
    }
    @Test fun stoppedMonitorReleasesForegroundListener() {
        val monitor = HomeScreenMonitor(context) {}
        monitor.start()
        monitor.stop()
        val companion = HomeScreenMonitor::class.java.getDeclaredField("foregroundListener").apply { isAccessible = true }
        assertEquals(null, companion.get(null))
    }

    @Test fun unrelatedAccessibilityServiceDoesNotClaimForegroundAccess() {
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.app.AppOpsManager::class.java))
            .setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(),
                context.packageName, android.app.AppOpsManager.MODE_IGNORED)
        android.provider.Settings.Secure.putString(context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "${context.packageName}/UnrelatedService")
        assertFalse(HomeScreenMonitor.hasAccess(context))
    }
}

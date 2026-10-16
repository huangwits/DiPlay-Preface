package com.shilapi.xcertplay.license

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.geelyplay.preface.license.AppLicense
import com.geelyplay.preface.license.LicenseActivity
import com.geelyplay.preface.license.LicensePanel
import com.geelyplay.preface.license.LicensePanelClient
import com.geelyplay.preface.license.LicensePanelState
import com.geelyplay.preface.license.OfflineLicenseActivity
import com.geelyplay.preface.license.OnlineLicense

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class LicenseUiTest {
    @Test fun sourceBuildStaysUsableAndPreviousApprovalMigratesToOfflineAdmission() {
        val app = RuntimeEnvironment.getApplication()
        assertFalse(AppLicense.enabled(app))
        assertTrue(AppLicense.canStart(app))
        assertTrue(OnlineLicense.canStart(app))
        app.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).edit().putBoolean("activated", true).commit()
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(context.resources).thenReturn(resources)
        `when`(resources.getBoolean(R.bool.config_online_license)).thenReturn(true)
        `when`(context.getSharedPreferences("diplay-license", Context.MODE_PRIVATE))
            .thenReturn(app.getSharedPreferences("diplay-license", Context.MODE_PRIVATE))
        assertTrue(OnlineLicense.canStart(context))
    }

    @Test fun offlineGateRejectsAnActivatedFlagWithoutASignedToken() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("diplay-offline-license", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("activated", true).commit()
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(context.resources).thenReturn(resources)
        `when`(resources.getBoolean(R.bool.config_offline_license)).thenReturn(true)
        `when`(context.getSharedPreferences("diplay-offline-license", Context.MODE_PRIVATE)).thenReturn(prefs)
        assertTrue(AppLicense.offline(context))
        assertFalse(AppLicense.canStart(context))
        assertEquals(OfflineLicenseActivity::class.java, AppLicense.activationActivity(context))
        verify(context, never()).assets
    }

    @Test fun offlinePageExplainsNoServerAndProvidesCopyAndFileImport() {
        val controller = Robolectric.buildActivity(OfflineLicenseActivity::class.java).setup()
        try {
            val labels = views(controller.get().window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
            assertTrue(labels.any { it.contains("无需授权服务器") })
            assertFalse(labels.any { it.contains("微信") || it.contains("starts181004") })
            assertTrue("复制设备码" in labels)
            assertTrue("从文件导入激活码" in labels)
            assertTrue(labels.any { it.contains("此版本无需激活") })
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun legacyEntryOpensTheBluetoothTools() {
        val controller = Robolectric.buildActivity(LicenseActivity::class.java).create()
        try {
            val intent = org.robolectric.Shadows.shadowOf(controller.get()).nextStartedActivity
            assertEquals(com.geelyplay.preface.e01goc.E01GocActivity::class.java.name, intent.component!!.className)
            assertTrue(intent.getBooleanExtra("license_wireless", false))
            assertTrue(controller.get().isFinishing)
        } finally { controller.destroy() }
    }

    @Test fun missingServiceConfigurationShowsDisabledInlinePreviewWithoutContact() {
        val panel = LicensePanel(RuntimeEnvironment.getApplication(), {})
        panel.start()
        try {
            val labels = views(panel).filterIsInstance<TextView>().map { it.text.toString() }.toList()
            assertTrue(labels.any { it.contains("激活服务尚未配置") })
            assertFalse(labels.any { it.contains("微信") || it.contains("starts181004") })
            assertTrue("申请激活" in labels)
            assertFalse("复制申请号" in labels)
            assertFalse("继续连接" in labels)
            assertFalse(views(panel).any { it is android.widget.EditText })
            assertFalse(views(panel).filterIsInstance<android.widget.Button>()
                .first { it.text.toString() == "申请激活" }.isEnabled)
        } finally { panel.stop() }
    }

    @Test fun stoppedPanelDiscardsAnInFlightResultAndDoesNotContinuePolling() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val returned = java.util.concurrent.CountDownLatch(1)
        val client = object : LicensePanelClient {
            override val configured = true
            override val requested = false
            override val valid = false
            override val requestId = "123456ABCDEF"
            override fun refresh(request: Boolean): OnlineLicense.Result {
                entered.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                returned.countDown()
                return OnlineLicense.Result(true, requestId)
            }
        }
        val panel = LicensePanel(RuntimeEnvironment.getApplication(), {}, client)
        panel.start()
        val buttons = views(panel).filterIsInstance<android.widget.Button>().toList()
        buttons.first { it.text == "申请激活" }.performClick()
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        panel.stop()
        release.countDown()
        assertTrue(returned.await(5, java.util.concurrent.TimeUnit.SECONDS))
        // Join the worker so its main-loop callback is definitely queued before draining it.
        Thread.getAllStackTraces().keys.filter { it.name == "diplay-license" }.forEach { it.join(5000) }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(buttons.first { it.text == "连接 CarPlay" }.isEnabled)
        assertFalse(views(panel).filterIsInstance<TextView>().any { it.text.contains("已授权") })
    }
    @Test fun pendingRequestsPollOncePerMinuteAndManualRefreshDoesNotWait() {
        val count = java.util.concurrent.atomic.AtomicInteger()
        val client = object : LicensePanelClient {
            override val configured = true
            override val requested = false
            override val valid = false
            override val requestId = "123456ABCDEF"
            override fun refresh(request: Boolean): OnlineLicense.Result {
                count.incrementAndGet()
                return OnlineLicense.Result(false, requestId)
            }
        }
        val panel = LicensePanel(RuntimeEnvironment.getApplication(), {}, client)
        val main = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        fun settle() {
            Thread.getAllStackTraces().keys.filter { it.name == "diplay-license" }.forEach { it.join(5000) }
            main.idle()
        }
        val buttons = views(panel).filterIsInstance<android.widget.Button>().toList()
        panel.start()
        try {
            buttons.single { it.text == "申请激活" }.performClick(); settle()
            assertEquals(1, count.get())
            main.idleFor(java.time.Duration.ofSeconds(59)); settle()
            assertEquals(1, count.get())
            main.idleFor(java.time.Duration.ofSeconds(1)); settle()
            assertEquals(2, count.get())
            buttons.single { it.text == "刷新状态" }.performClick(); settle()
            assertEquals(3, count.get())
            panel.stop()
            main.idleFor(java.time.Duration.ofMinutes(2)); settle()
            assertEquals(3, count.get())
        } finally { panel.stop() }
    }

    private class ReturningClient : LicensePanelClient {
        override val configured = true
        override val requested = true
        override val activated = true
        @Volatile override var valid = false
        override val requestId = "123456ABCDEF"
        val requests = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
        var failure = false
        var entered: java.util.concurrent.CountDownLatch? = null
        var release: java.util.concurrent.CountDownLatch? = null
        override fun refresh(request: Boolean): OnlineLicense.Result {
            requests += request
            entered?.countDown()
            release?.let { check(it.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
            if (failure) throw java.io.IOException("网络不可用，请检查后重试")
            valid = true
            return OnlineLicense.Result(true, requestId)
        }
    }

    private fun settleLicense() {
        Thread.getAllStackTraces().keys.filter { it.name == "diplay-license" }.forEach { it.join(5000) }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test fun firstApprovalCollapsesApplicationUiImmediately() {
        val requests = mutableListOf<Boolean>()
        val client = object : LicensePanelClient {
            override val configured = true
            override val requested = false
            override var valid = false
            override val requestId = "123456ABCDEF"
            override fun refresh(request: Boolean): OnlineLicense.Result {
                requests += request; valid = true
                return OnlineLicense.Result(true, requestId)
            }
        }
        val panel = LicensePanel(RuntimeEnvironment.getApplication(), {}, client)
        assertTrue(panel.needsAttention)
        panel.start()
        try {
            views(panel).filterIsInstance<android.widget.Button>().single { it.text == "申请激活" }.performClick()
            settleLicense()
            assertEquals(listOf(true), requests)
            assertEquals(LicensePanelState.AUTHORIZED, panel.state)
            assertFalse(panel.needsAttention)
        } finally { panel.stop() }
    }

    @Test fun returningInstallationRevalidatesWithoutReapplyingOrShowingTheApplicationPanel() {
        val client = ReturningClient().apply {
            entered = java.util.concurrent.CountDownLatch(1)
            release = java.util.concurrent.CountDownLatch(1)
        }
        val changes = mutableListOf<LicensePanelState>()
        lateinit var panel: LicensePanel
        panel = LicensePanel(RuntimeEnvironment.getApplication(), {}, client, { changes += panel.state })
        assertFalse(panel.needsAttention)
        panel.start()
        try {
            assertTrue(client.entered!!.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(LicensePanelState.CHECKING, panel.state)
            assertFalse(panel.needsAttention)
            assertFalse(views(panel).filterIsInstance<android.widget.Button>().single { it.text == "连接 CarPlay" }.isEnabled)
            client.release!!.countDown(); settleLicense()
            assertEquals(listOf(false), client.requests)
            assertEquals(LicensePanelState.AUTHORIZED, panel.state)
            assertFalse(panel.needsAttention)
            assertTrue(LicensePanelState.AUTHORIZED in changes)
        } finally { client.release!!.countDown(); panel.stop() }
    }

    @Test fun returningInstallationShowsRecoveryOnFailureAndCollapsesAgainAfterManualRetry() {
        val client = ReturningClient().apply { failure = true }
        val panel = LicensePanel(RuntimeEnvironment.getApplication(), {}, client)
        panel.start(); settleLicense()
        try {
            assertEquals(LicensePanelState.ERROR, panel.state)
            assertTrue(panel.needsAttention)
            assertFalse(client.valid)
            assertTrue(views(panel).filterIsInstance<TextView>().any { "网络不可用" in it.text })
            client.failure = false
            views(panel).filterIsInstance<android.widget.Button>().single { it.text == "刷新状态" }.performClick()
            settleLicense()
            assertEquals(listOf(false, false), client.requests)
            assertFalse(panel.needsAttention)
            assertEquals(LicensePanelState.AUTHORIZED, panel.state)
        } finally { panel.stop() }
    }

    @Test fun cachedAdmissionIsRenewedInForegroundWithoutTreatingHistoryAsPermission() {
        val client = ReturningClient().apply { valid = true }
        val panel = LicensePanel(RuntimeEnvironment.getApplication(), {}, client)
        panel.start()
        try {
            assertTrue(client.requests.isEmpty())
            client.valid = false; client.failure = true
            panel.refreshAdmission(); settleLicense()
            assertEquals(listOf(false), client.requests)
            assertEquals(LicensePanelState.ERROR, panel.state)
            assertTrue(panel.needsAttention)
            repeat(3) { panel.refreshAdmission() }; settleLicense()
            assertEquals(1, client.requests.size)
            assertFalse(views(panel).filterIsInstance<android.widget.Button>().single { it.text == "连接 CarPlay" }.isEnabled)
        } finally { panel.stop() }
    }

    @Test fun returningCheckKeepsCompactUiAndResumesOneQueuedConnection() {
        val client = ReturningClient().apply {
            entered = java.util.concurrent.CountDownLatch(1)
            release = java.util.concurrent.CountDownLatch(1)
        }
        var connections = 0
        val panel = LicensePanel(RuntimeEnvironment.getApplication(), {}, client, compactWhenApproved = true)
        panel.start()
        try {
            assertTrue(client.entered!!.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(View.GONE, views(panel).filterIsInstance<android.widget.Button>().single { it.text == "申请激活" }.visibility)
            panel.whenAdmitted { connections++ }
            panel.whenAdmitted { connections++ }
            assertEquals(0, connections)
            client.release!!.countDown(); settleLicense()
            assertEquals(1, connections)
            assertEquals(listOf(false), client.requests)
        } finally { client.release!!.countDown(); panel.stop() }
    }

    @Test fun leavingDuringAuthorizationCancelsTheQueuedConnection() {
        val client = ReturningClient().apply {
            entered = java.util.concurrent.CountDownLatch(1)
            release = java.util.concurrent.CountDownLatch(1)
        }
        var connections = 0
        val panel = LicensePanel(RuntimeEnvironment.getApplication(), {}, client)
        panel.start()
        assertTrue(client.entered!!.await(5, java.util.concurrent.TimeUnit.SECONDS))
        panel.whenAdmitted { connections++ }
        panel.stop()
        client.release!!.countDown(); settleLicense()
        assertEquals(0, connections)
    }

    @Test fun approvedPanelDoesNotQueryTheNetworkBeforeCarPlayConnects() {
        val count = java.util.concurrent.atomic.AtomicInteger()
        val client = object : LicensePanelClient {
            override val configured = true
            override val requested = true
            override val activated = true
            override val valid = true
            override val requestId = "123456ABCDEF"
            override fun refresh(request: Boolean): OnlineLicense.Result {
                count.incrementAndGet(); throw java.io.IOException("offline")
            }
        }
        val panel = LicensePanel(RuntimeEnvironment.getApplication(), {}, client, compactWhenApproved = true)
        panel.start()
        try {
            panel.refreshAdmission()
            assertEquals(LicensePanelState.AUTHORIZED, panel.state)
            settleLicense()
            assertEquals(LicensePanelState.AUTHORIZED, panel.state)
            repeat(3) { panel.refreshAdmission() }; settleLicense()
            assertEquals(0, count.get())
        } finally { panel.stop() }
    }

    @Test fun savedApprovalReadDoesNotWaitForTheNetworkRefreshMonitor() {
        val held = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val returned = java.util.concurrent.CountDownLatch(1)
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).edit().putBoolean("authorized", true).commit()
        val network = Thread {
            synchronized(OnlineLicense) {
                held.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        val caller = Thread { assertTrue(OnlineLicense.hasSavedApproval(app)); returned.countDown() }
        network.start()
        try {
            assertTrue(held.await(5, java.util.concurrent.TimeUnit.SECONDS))
            caller.start()
            assertTrue("Offline admission must not wait for refresh", returned.await(2, java.util.concurrent.TimeUnit.SECONDS))
        } finally { release.countDown(); network.join(5000); caller.join(5000) }
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(views(view.getChildAt(i)))
    }
}

package com.shilapi.xcertplay.e01goc

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.widget.Button
import com.shilapi.xcertplay.DiPlayPreferences
import com.geelyplay.preface.network.E01Phone
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.util.concurrent.CopyOnWriteArrayList
import com.geelyplay.preface.e01goc.E01GocActivity
import com.geelyplay.preface.e01goc.E01GocManager
import com.geelyplay.preface.e01goc.E01GocPreferences
import com.geelyplay.preface.e01goc.E01QuickSetup
import com.geelyplay.preface.e01goc.GocSnapshot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class E01QuickConnectUiTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val phone = E01Phone("11:22:33:44:55:66", "我的 iPhone")
    private fun settle() {
        repeat(1500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (Thread.getAllStackTraces().keys.none { t -> t.name == "diplay-e01-maintenance" && t.isAlive }) {
                shadowOf(Looper.getMainLooper()).idle()
                if (Thread.getAllStackTraces().keys.none { t -> t.name == "diplay-e01-maintenance" && t.isAlive }) return
            }
            Thread.sleep(5)
        }
        fail("Maintenance worker did not finish")
    }

    private fun withActivity(installedInitially: Boolean, run: (E01GocActivity, MutableList<String>) -> Unit) {
        E01GocPreferences.select(app, true)
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        val events = CopyOnWriteArrayList<String>()
        var installed = installedInitially
        host.get().quickSetup = E01QuickSetup({
            events += "inspect"
            GocSnapshot(if (installed) E01GocManager.CANDIDATE_SHA else "a".repeat(64), "", "", "running", true)
        }, { events += "test" }, { events += "install"; installed = true }, { events += "admission" }, { false })
        host.get().quickPhoneLookup = { listOf(phone) }
        try { run(host.get(), events) } finally { host.pause().stop().destroy() }
    }

    @Test fun dailyClickAutoSelectsTheOnlyPhoneAndConnectsWithoutInstalling() = withActivity(true) { activity, events ->
        val root = activity.window.decorView
        assertNull(root.findViewWithTag<View>("bluetooth-setup-steps"))
        root.findViewWithTag<Button>("bluetooth-connect").performClick()
        settle()
        assertEquals(phone.address, DiPlayPreferences.phoneAddress(app))
        assertEquals("wireless", shadowOf(activity).nextStartedActivity.getStringExtra("authorized_connection"))
        assertFalse("test" in events || "install" in events)
    }

    @Test fun firstSetupRequiresOneConfirmationThenContinuesToConnection() = withActivity(false) { activity, events ->
        activity.window.decorView.findViewWithTag<Button>("bluetooth-connect").performClick()
        settle()
        assertFalse("test" in events || "install" in events)
        assertNull(shadowOf(activity).nextStartedActivity)
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        settle()
        assertEquals(1, events.count { it == "test" })
        assertEquals(1, events.count { it == "install" })
        assertEquals("wireless", shadowOf(activity).nextStartedActivity.getStringExtra("authorized_connection"))
    }

    @Test fun cancellingFirstConfirmationLeavesComponentsAndPhoneUntouched() = withActivity(false) { activity, events ->
        activity.window.decorView.findViewWithTag<Button>("bluetooth-connect").performClick()
        settle()
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        settle()
        assertNull(DiPlayPreferences.phoneAddress(app))
        assertFalse("test" in events || "install" in events)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun multiplePhonesRequireSelectionWithoutStartingMaintenance() = withActivity(false) { activity, events ->
        activity.quickPhoneLookup = { listOf(phone, E01Phone("22:33:44:55:66:77", "另一台")) }
        activity.window.decorView.findViewWithTag<Button>("bluetooth-connect").performClick()
        settle()
        assertEquals(2, ShadowAlertDialog.getLatestAlertDialog().listView.count)
        assertFalse("test" in events || "install" in events)
        assertNull(DiPlayPreferences.phoneAddress(app))
    }

    @Test fun leavingAndReturningBeforeLookupCompletesCannotResumeTheOldAttempt() {
        E01GocPreferences.select(app, true)
        val host = Robolectric.buildActivity(E01GocActivity::class.java).setup()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val activity = host.get()
        activity.quickSetup = E01QuickSetup({ GocSnapshot("a".repeat(64), "", "", "running", true) },
            { error("must not test") }, { error("must not install") }, {}, { false })
        activity.quickPhoneLookup = {
            entered.countDown()
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            listOf(phone)
        }
        try {
            activity.window.decorView.findViewWithTag<Button>("bluetooth-connect").performClick()
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            host.pause().stop().start().resume()
            release.countDown(); settle()
            assertNull(org.robolectric.util.ReflectionHelpers.getField<AlertDialog?>(activity, "quickDialog"))
            assertNull(DiPlayPreferences.phoneAddress(app))
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally { release.countDown(); host.pause().stop().destroy() }
    }
}

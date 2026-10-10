package com.shilapi.xcertplay.license

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayHostActivity
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.e01goc.E01GocPreferences
import com.shilapi.xcertplay.orchestration.MfiTarget
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "zh-rCN", shadows = [BluetoothLicenseLayoutTest.LicensedResources::class])
class AndroidDirectConnectionTest {
    @org.robolectric.annotation.Implements(android.bluetooth.BluetoothManager::class)
    class NoAdapterBluetoothManager {
        @org.robolectric.annotation.Implementation fun getAdapter(): BluetoothAdapter? = null
    }
    private lateinit var host: ActivityController<DiPlayActivity>
    private val app get() = RuntimeEnvironment.getApplication()
    private val activity get() = host.get()

    @Before fun setup() {
        lease(false)
        ReflectionHelpers.setStaticField(AppLicense::class.java, "lastPrompt", -10000L)
        E01GocPreferences.select(app, false)
        AirPlayPersistence.saveMfiTarget(app, MfiTarget.USB_CH341)
        DiPlayPreferences.saveAutoConnect(app, false)
        DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "我的 iPhone")
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_ON)
        app.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).edit()
            .putBoolean("activated", true).putBoolean("requested", true).commit()
        host = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
    }

    @After fun cleanup() {
        host.pause().stop().destroy()
        lease(false)
    }

    @Test fun validAndroidAdmissionConnectsDirectlyWithoutRefreshingOrOpeningTools() {
        lease(true)
        activity.refreshWirelessAdmission = { error("Existing lease needs no refresh") }
        connect()
        assertProjection(true)
        assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(app))
        assertFalse(java.io.File(app.filesDir, "e01-goc/manage.sh").exists())
    }

    @Test fun expiredAndroidAdmissionRefreshesOnceAndAutomaticallyContinues() {
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        activity.refreshWirelessAdmission = {
            calls.incrementAndGet(); entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)); lease(true); true
        }
        connect()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        connect()
        assertNull(shadowOf(activity).nextStartedActivity)
        release.countDown(); awaitWorker()
        assertEquals(1, calls.get())
        assertProjection(true)
    }

    @Test fun pendingOrFailedRenewalReturnsToAuthorizationWithoutStartingProjection() {
        activity.refreshWirelessAdmission = { false }
        connect(); awaitWorker()
        assertAuthorization()
    }

    @Test fun networkFailureReturnsToAuthorization() {
        activity.refreshWirelessAdmission = { throw java.io.IOException("offline") }
        connect(); awaitWorker()
        assertAuthorization()
    }

    @Test fun approvedCallbackWithoutARealLeaseCannotBypassAdmission() {
        activity.refreshWirelessAdmission = { true }
        connect(); awaitWorker()
        assertAuthorization()
    }

    @Test fun newInstallationStillRequiresActivation() {
        app.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).edit().clear().commit()
        activity.refreshWirelessAdmission = { error("No existing activation") }
        connect()
        assertAuthorization()
    }

    @Test fun factoryModeKeepsItsExistingAuthorizationFlowAndSavedTransport() {
        E01GocPreferences.select(app, true)
        activity.refreshWirelessAdmission = { error("Factory flow owns its admission") }
        connect()
        assertAuthorization()
        assertTrue(E01GocPreferences.enabled(app))
    }

    @Test fun cancellingTheCheckDiscardsLateApproval() = withBlockedApproval { release ->
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        release.countDown(); awaitWorker()
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun stoppedActivityDiscardsLateApproval() = withBlockedApproval { release ->
        host.pause().stop()
        release.countDown(); awaitWorker()
        assertNull(shadowOf(activity).nextStartedActivity)
        host.start().resume()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun usbSupersedesAnInFlightWirelessCheck() = withBlockedApproval { release ->
        connect(false)
        assertProjection(false)
        release.countDown(); awaitWorker()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertFalse(AirPlayPersistence.loadWirelessEnabled(app))
    }

    @Test fun disabledAndroidBluetoothOffersSystemSettingsEvenWithASavedPhone() {
        lease(true)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_OFF)
        connect()
        assertNull(shadowOf(activity).nextStartedActivity)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        assertFalse(E01GocPreferences.enabled(app))
    }

    @Test fun firstPhoneIsSelectedInTheNormalAndroidPickerThenConnects() {
        lease(true)
        app.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().remove("phone_address").commit()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val phone = adapter.getRemoteDevice("11:22:33:44:55:66")
        shadowOf(phone).setName("我的 iPhone")
        shadowOf(adapter).setBondedDevices(setOf(phone))
        connect()
        assertNull(shadowOf(activity).nextStartedActivity)
        ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        assertProjection(true)
        assertEquals(phone.address, DiPlayPreferences.phoneAddress(app))
    }

    @Test
    @Config(shadows = [NoAdapterBluetoothManager::class])
    fun missingAndroidBluetoothOpensToolsWithoutChangingModeOrRunningMaintenance() {
        lease(true)
        connect()
        val next = shadowOf(activity).nextStartedActivity
        assertEquals(com.shilapi.xcertplay.e01goc.E01GocActivity::class.java.name, next?.component?.className)
        assertTrue(next.getBooleanExtra("android_bluetooth_unavailable", false))
        assertFalse(E01GocPreferences.enabled(app))
        assertFalse(java.io.File(app.filesDir, "e01-goc/manage.sh").exists())
    }

    private fun withBlockedApproval(action: (CountDownLatch) -> Unit) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        activity.refreshWirelessAdmission = {
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); lease(true); true
        }
        connect()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); action(release) }
        finally { release.countDown(); awaitWorker() }
    }

    private fun connect(wireless: Boolean = true) = ReflectionHelpers.callInstanceMethod<Unit>(
        activity, "connect", ClassParameter.from(Boolean::class.javaPrimitiveType, wireless))

    private fun lease(valid: Boolean) = ReflectionHelpers.setStaticField(
        OnlineLicense::class.java, "validUntilElapsed", if (valid) Long.MAX_VALUE else 0L)

    private fun awaitWorker() {
        Thread.getAllStackTraces().keys.filter { it.name == "diplay-wireless-admission" }.forEach {
            it.join(5000); assertFalse("Admission worker finished", it.isAlive)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertProjection(wireless: Boolean) {
        assertEquals(CarPlayHostActivity::class.java.name, shadowOf(activity).nextStartedActivity?.component?.className)
        assertEquals(wireless, AirPlayPersistence.loadWirelessEnabled(app))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    private fun assertAuthorization() {
        assertEquals(LicenseActivity::class.java.name, shadowOf(activity).nextStartedActivity?.component?.className)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertFalse(AppLicense.canStart(activity))
    }
}

package com.shilapi.xcertplay.license

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayHostActivity
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.DiPlayPreferences
import com.geelyplay.preface.e01goc.E01GocPreferences
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
import com.geelyplay.preface.license.AppLicense
import com.geelyplay.preface.license.LicenseActivity
import com.geelyplay.preface.license.OnlineLicense

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
        // These tests exercise admission and phone selection, before a controller starts the
        // hotspot. An unconfigured manual hotspot intentionally stops at its setup page.
        AirPlayPersistence.saveWirelessHotspotMode(app,
            com.shilapi.xcertplay.orchestration.WirelessHotspotMode.WIFI_P2P)
        com.shilapi.xcertplay.CarPlayBackgroundSession.clear()
        DiPlayPreferences.saveAutoConnect(app, false)
        DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "我的 iPhone")
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_ON)
        app.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).edit()
            .clear().putBoolean("requested", true).commit()
        host = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
    }

    @After fun cleanup() {
        host.pause().stop().destroy()
        lease(false)
    }

    @Test fun approvedAndroidInstallationConnectsOfflineAfterColdRestart() {
        OnlineLicense.saveDecision(app, true, "ABCDEF123456", "installation")
        lease(false) // Process-memory cache has gone away.
        activity.refreshWirelessAdmission = { error("Approved connection must not access network") }
        connect()
        assertProjection(true)
    }

    @Test fun upgradedFactoryInstallationConnectsOfflineWithoutARefresh() {
        app.getSharedPreferences("diplay-license", Context.MODE_PRIVATE).edit().putBoolean("activated", true).commit()
        E01GocPreferences.select(app, true)
        DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "我的 iPhone")
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_OFF)
        activity.refreshWirelessAdmission = { error("Existing approval must survive upgrade") }
        connect()
        assertProjection(true)
    }

    @Test fun validAndroidAdmissionConnectsDirectlyWithoutRefreshingOrOpeningTools() {
        lease(true)
        activity.refreshWirelessAdmission = { error("Existing lease needs no refresh") }
        connect()
        assertProjection(true)
        assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(app))
        assertFalse(java.io.File(app.filesDir, "e01-goc/manage.sh").exists())
    }

    @Test fun pendingAndroidApplicationRefreshesOnceAndAutomaticallyContinues() {
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

    @Test fun factoryModeRenewsAdmissionAndConnectsWithTheAndroidAdapterOff() {
        E01GocPreferences.select(app, true)
        DiPlayPreferences.savePhone(app, "11:22:33:44:55:66", "我的 iPhone")
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_OFF)
        activity.refreshWirelessAdmission = { lease(true); true }
        connect()
        awaitWorker()
        assertProjection(true)
        assertTrue(E01GocPreferences.enabled(app))
        assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(app))
        assertFalse(java.io.File(app.filesDir, "e01-goc/manage.sh").exists())
    }

    @Test fun factoryPhoneSelectionOnHomeContinuesConnectionWithoutOpeningToolsOrAndroidBluetooth() {
        lease(true)
        E01GocPreferences.select(app, true)
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_OFF)
        val read = CountDownLatch(1)
        activity.factoryPhoneLookup = {
            read.countDown()
            listOf(com.geelyplay.preface.network.E01Phone("11:22:33:44:55:66", "我的 iPhone"))
        }
        connect()
        assertTrue(read.await(5, TimeUnit.SECONDS))
        awaitFactoryPicker()
        assertNull(shadowOf(activity).nextStartedActivity)
        ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        assertProjection(true)
        assertTrue(E01GocPreferences.enabled(app))
        assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(app))
        assertFalse(java.io.File(app.filesDir, "e01-goc/manage.sh").exists())
    }

    @Test fun leavingHomeDiscardsALateFactoryLookupWithoutSavingOrConnecting() {
        lease(true)
        E01GocPreferences.select(app, true)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        activity.factoryPhoneLookup = {
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
            listOf(com.geelyplay.preface.network.E01Phone("11:22:33:44:55:66", "我的 iPhone"))
        }
        connect()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        host.pause().stop()
        release.countDown(); awaitFactoryPicker()
        assertNull(DiPlayPreferences.phoneAddress(app))
        assertNull(shadowOf(activity).nextStartedActivity)
        assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
        host.start().resume()
    }

    @Test fun usbCancelsFactoryPhoneSelectionAndPreventsALateWirelessStart() {
        lease(true)
        E01GocPreferences.select(app, true)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        activity.factoryPhoneLookup = {
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
            listOf(com.geelyplay.preface.network.E01Phone("11:22:33:44:55:66", "我的 iPhone"))
        }
        connect()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        connect(false)
        assertProjection(false)
        release.countDown(); awaitFactoryPicker()
        assertNull(DiPlayPreferences.phoneAddress(app))
        assertNull(shadowOf(activity).nextStartedActivity)
        assertFalse(AirPlayPersistence.loadWirelessEnabled(app))
    }

    private fun awaitFactoryPicker() {
        for (attempt in 0 until 1000) {
            shadowOf(Looper.getMainLooper()).idle()
            if (Thread.getAllStackTraces().keys.none { it.name == "diplay-factory-phone-lookup" && it.isAlive }) {
                shadowOf(Looper.getMainLooper()).idle()
                return
            }
            Thread.sleep(5)
        }
        fail("Factory lookup did not finish")
    }

    private fun installFactoryBluetooth() {
        val pkg = com.geelyplay.preface.network.E01ConnectedPhones.PACKAGE
        shadowOf(app.packageManager).installPackage(android.content.pm.PackageInfo().apply {
            packageName = pkg
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                packageName = pkg; flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM
            }
        })
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_OFF)
        activity.factoryPhoneLookup = {
            listOf(com.geelyplay.preface.network.E01Phone("22:33:44:55:66:77", "原厂 iPhone"))
        }
    }

    @Test fun oldAndroidSettingWithFactoryBluetoothSelectsPhoneAndResumesConnection() {
        lease(true)
        installFactoryBluetooth()
        connect()
        awaitFactoryPicker()
        assertFalse(E01GocPreferences.enabled(app))
        assertNull(shadowOf(activity).nextStartedActivity)
        ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(E01GocPreferences.enabled(app))
        assertEquals("22:33:44:55:66:77", DiPlayPreferences.phoneAddress(app))
        assertProjection(true)
        assertFalse(java.io.File(app.filesDir, "e01-goc/manage.sh").exists())
    }

    @Test fun cancellingFactoryFallbackKeepsThePreviousModeAndPhone() {
        lease(true)
        installFactoryBluetooth()
        connect()
        awaitFactoryPicker()
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(E01GocPreferences.enabled(app))
        assertEquals("11:22:33:44:55:66", DiPlayPreferences.phoneAddress(app))
        assertFalse(ReflectionHelpers.getField(activity, "pendingWireless"))
        assertNull(shadowOf(activity).nextStartedActivity)
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

    @Test
    @Config(sdk = [31])
    fun stoppingForAndroidBluetoothPermissionPreservesThePendingAndroidConnection() {
        lease(true)
        shadowOf(app).denyPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        connect()
        assertTrue(ReflectionHelpers.getField(activity, "pendingWireless"))
        assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", shadowOf(activity).nextStartedActivity?.action)
        host.pause().stop()
        assertTrue(ReflectionHelpers.getField(activity, "pendingWireless"))
        host.start().resume()
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
        assertEquals(com.geelyplay.preface.e01goc.E01GocActivity::class.java.name, next?.component?.className)
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

package com.shilapi.xcertplay

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE, shadows = [CarHotspotSetupShadow::class,
    CarHotspotAdbGrantTest.WritePermission::class])
class CarHotspotSwitchTest {
    private lateinit var activity: DiPlayActivity
    private lateinit var controls: LinearLayout

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        for (name in listOf("diplay_byd_outputs", "diplay_byd_vehicle_fields", "diplay_car_hotspot", "xcertplay_airplay", "diplay")) {
            app.getSharedPreferences(name, 0).edit().clear().commit()
        }
        CarPlayBackgroundSession.clear()
        CarHotspotAdbGrantTest.WritePermission.allowed = false
        ShadowSettings.setCanDrawOverlays(false)
        CarHotspotSetupShadow.reset()
        activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        AirPlayPersistence.saveWirelessHotspotMode(activity, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveAutoStartOnBoot(activity, false)
        controls = LinearLayout(activity)
        DiPlayActivity::class.java.getDeclaredMethod("renderHotspotAdbControls", LinearLayout::class.java,
            LocalAdb.Access::class.java).apply { isAccessible = true }.invoke(activity, controls, LocalAdb.Access.READY)
        activity.setContentView(controls)
    }

    @After fun tearDown() {
        CarHotspotSetupShadow.release.countDown()
        CarHotspotSetupShadow.worker?.join(3_000)
        shadowOf(Looper.getMainLooper()).idle()
        CarPlayBackgroundSession.clear()
    }

    @Test fun enablingDirectlyRequestsPermissionWithoutAnotherDialog() {
        hotspotSwitch().isChecked = true
        awaitGrant()
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertFalse(CarHotspotSettings.enabled(activity))
        assertFalse(hotspotSwitch().isEnabled)
        assertSame(controls, activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
        assertNull(ShadowToast.getLatestToast())
        completeGrant()
        assertSame(controls, activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
        assertEquals(activity.getString(R.string.hotspot_permission_granted), ShadowToast.getTextOfLatestToast())
        assertTrue(CarHotspotSettings.enabled(activity))
        assertTrue(hotspotSwitch().isChecked)
        assertTrue(hotspotSwitch().isEnabled)
        assertEquals(listOf(CarHotspotSetup.Permission.HOTSPOT, CarHotspotSetup.Permission.BOOT_LAUNCH), CarHotspotSetupShadow.requested)
        assertTrue(CarHotspotSetup.Permission.BOOT_LAUNCH.granted(activity))
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(activity))
    }

    @Test fun adbApprovalFailureLeavesTheSwitchOffAndShowsTheReason() {
        CarHotspotSetupShadow.access = LocalAdb.Access.NOT_APPROVED
        hotspotSwitch().isChecked = true
        awaitGrant(); completeGrant()
        assertFalse(CarHotspotSettings.enabled(activity))
        assertFalse(hotspotSwitch().isChecked)
        assertTrue(hotspotSwitch().isEnabled)
        assertEquals(activity.getString(R.string.adb_not_approved), ShadowToast.getTextOfLatestToast())
    }

    @Test fun adbSuccessWithoutActualPermissionDoesNotEnableTheSwitch() {
        CarHotspotSetupShadow.allowed = emptySet()
        hotspotSwitch().isChecked = true
        awaitGrant(); completeGrant()
        assertFalse(CarHotspotSettings.enabled(activity))
        assertEquals(activity.getString(R.string.hotspot_permission_failed), ShadowToast.getTextOfLatestToast())
    }

    @Test fun anExistingPermissionStillWaitsForAdbBeforeEnabling() {
        CarHotspotAdbGrantTest.WritePermission.allowed = true
        hotspotSwitch().isChecked = true
        awaitGrant()
        assertFalse(CarHotspotSettings.enabled(activity))
        completeGrant()
        assertTrue(CarHotspotSettings.enabled(activity))
        assertTrue(hotspotSwitch().isChecked)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }


    @Test fun selectingBootFirstGrantsBothRequiredPermissionsFromTheHotspotSwitch() {
        AirPlayPersistence.saveAutoStartOnBoot(activity, true)
        hotspotSwitch().isChecked = true
        awaitGrant(); completeGrant()
        assertEquals(listOf(CarHotspotSetup.Permission.HOTSPOT, CarHotspotSetup.Permission.BOOT_LAUNCH), CarHotspotSetupShadow.requested)
        assertTrue(CarHotspotSettings.enabled(activity))
        assertTrue(CarHotspotSetup.Permission.BOOT_LAUNCH.granted(activity))
    }

    @Test fun incompleteBootPermissionKeepsTheHotspotSwitchOff() {
        AirPlayPersistence.saveAutoStartOnBoot(activity, true)
        CarHotspotSetupShadow.allowed = setOf(CarHotspotSetup.Permission.HOTSPOT)
        hotspotSwitch().isChecked = true
        awaitGrant(); completeGrant()
        assertTrue(CarHotspotSetup.Permission.HOTSPOT.granted(activity))
        assertFalse(CarHotspotSetup.Permission.BOOT_LAUNCH.granted(activity))
        assertFalse(CarHotspotSettings.enabled(activity))
    }

    @Test fun selectingBootLaterRequestsOnlyBootPermission() {
        CarHotspotSettings.setEnabled(activity, true)
        CarHotspotAdbGrantTest.WritePermission.allowed = true
        bootSwitch().isChecked = true
        awaitGrant()
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(activity))
        assertFalse(bootSwitch().isEnabled)
        completeGrant()
        assertEquals(listOf(CarHotspotSetup.Permission.BOOT_LAUNCH), CarHotspotSetupShadow.requested)
        assertTrue(AirPlayPersistence.loadAutoStartOnBoot(activity))
        assertTrue(CarHotspotSettings.enabled(activity))
    }

    @Test fun aFailedBootGrantPreservesHotspotAndDoesNotEnableBootLaunch() {
        CarHotspotSettings.setEnabled(activity, true)
        CarHotspotAdbGrantTest.WritePermission.allowed = true
        CarHotspotSetupShadow.allowed = emptySet()
        bootSwitch().isChecked = true
        awaitGrant(); completeGrant()
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(activity))
        assertTrue(CarHotspotSettings.enabled(activity))
    }

    @Test fun turningTheSwitchOffDoesNotRequestOrRevokePermissions() {
        CarHotspotSettings.setEnabled(activity, true)
        CarHotspotAdbGrantTest.WritePermission.allowed = true
        refreshControls()
        hotspotSwitch().isChecked = false
        assertFalse(CarHotspotSettings.enabled(activity))
        assertTrue(CarHotspotSetup.Permission.HOTSPOT.granted(activity))
        assertTrue(CarHotspotSetupShadow.requested.isEmpty())
    }

    @Test fun vehicleControlsAreNotDuplicatedInTheHotspotPermissionCard() {
        assertEquals(1, switches(controls).size)
        assertTrue(CarHotspotSetupShadow.requested.isEmpty())
    }


    @Test fun unexpectedAdbFailureRestoresTheSameSwitch() {
        CarHotspotSetupShadow.fail = true
        val control = hotspotSwitch()
        control.isChecked = true
        awaitGrant(); completeGrant()
        assertFalse(control.isChecked)
        assertTrue(control.isEnabled)
        assertFalse(CarHotspotSettings.enabled(activity))
        assertEquals(activity.getString(R.string.adb_off), ShadowToast.getTextOfLatestToast())
    }

    @Test fun returningFromApprovalBeforeCompletionKeepsThePage() {
        hotspotSwitch().isChecked = true
        awaitGrant()
        prepareResume()
        lifecycle("onPause")
        lifecycle("onResume")
        assertSame(controls, activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
        completeGrant()
        assertTrue(hotspotSwitch().isChecked)
        assertTrue(hotspotSwitch().isEnabled)
        lifecycle("onPause")
    }

    @Test fun returningFromApprovalAfterCompletionKeepsThePage() {
        hotspotSwitch().isChecked = true
        awaitGrant()
        prepareResume()
        lifecycle("onPause")
        completeGrant()
        lifecycle("onResume")
        assertSame(controls, activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
        assertTrue(hotspotSwitch().isChecked)
        assertTrue(hotspotSwitch().isEnabled)
        lifecycle("onPause")
    }

    private fun lifecycle(name: String) {
        DiPlayActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
    }

    private fun prepareResume() {
        for ((name, value) in mapOf("initialLaunch" to false, "page" to "settings")) {
            DiPlayActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
        }
    }


    private fun awaitGrant() {
        if (!CarHotspotSetupShadow.entered.await(3, TimeUnit.SECONDS)) {
            fail("Permission worker did not reach the grant fixture: " +
                ShadowLog.getLogsForTag("DiPlay-Hotspot").joinToString("\n") {
                    it.msg + (it.throwable?.stackTraceToString() ?: "")
                })
        }
    }

    private fun completeGrant() {
        CarHotspotSetupShadow.release.countDown()
        CarHotspotSetupShadow.worker!!.join(3_000)
        assertFalse(CarHotspotSetupShadow.worker!!.isAlive)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun hotspotSwitch(): Switch = switchFor(R.string.auto_car_hotspot_title)

    private fun switchFor(title: Int): Switch = switches(controls).single {
        it.contentDescription == activity.getString(title)
    }

    private fun refreshControls() {
        DiPlayActivity::class.java.getDeclaredMethod("updateAdbSwitches").apply {
            isAccessible = true
        }.invoke(activity)
    }

    private fun bootSwitch(): Switch {
        switches(controls).firstOrNull {
            it.contentDescription == activity.getString(R.string.open_after_the_car_starts)
        }?.let { return it }
        val bootControls = LinearLayout(activity)
        DiPlayActivity::class.java.getDeclaredMethod("connectionSettings", LinearLayout::class.java).apply {
            isAccessible = true
        }.invoke(activity, bootControls)
        controls.addView(bootControls)
        return switches(controls).single { it.contentDescription == activity.getString(R.string.open_after_the_car_starts) }
    }

    private fun switches(view: View): List<Switch> = buildList {
        if (view is Switch) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(switches(view.getChildAt(i)))
    }

}

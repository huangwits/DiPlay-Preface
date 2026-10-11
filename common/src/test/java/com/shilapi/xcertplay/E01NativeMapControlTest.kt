package com.shilapi.xcertplay

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Looper
import com.shilapi.xcertplay.hud.CarPlayHudGuidance
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class E01NativeMapControlTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var activity: Activity
    private var control: E01NativeMapControl? = null
    private var connected = true
    private val gestures = mutableListOf<Boolean>()
    private val turn = CarPlayHudGuidance(180, 2, "人民路", 2)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun messages() = shadowOf(app).broadcastIntents.filter { it.component != null }
    private fun modeMessages() = messages().filter { it.action == E01NativeMapControl.ACTION }
    private fun gesture(direction: String) {
        app.sendBroadcast(Intent(E01NativeMapControl.ACTION).putExtra(E01NativeMapControl.DIRECTION, direction))
        idle()
    }

    @Before fun prepare() {
        for (key in listOf("vehicle_map_projection", "e01_navigation", "geely_projection_layout"))
            app.getSharedPreferences(key, 0).edit().clear().commit()
        AirPlayPersistence.saveGeelyHudEnabled(app, false)
        activity = Robolectric.buildActivity(Activity::class.java).get()
        val pkg = "com.neusoft.extraservice"
        val info = ApplicationInfo().apply { packageName = pkg; enabled = true }
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg; applicationInfo = info
            receivers = arrayOf("AutoNaviReceiver", "ThreeFingerReceiver").map { receiver ->
                ActivityInfo().apply {
                    packageName = pkg; name = "$pkg.receiver.$receiver"
                    applicationInfo = info; exported = true; enabled = true
                }
            }.toTypedArray()
        })
        VehicleMapSettings.setEnabled(app, true)
        VehicleMapSettings.setVehicleMode(app, true)
        GeelyHudProjection.attach(activity)
        idle()
    }

    @After fun cleanup() {
        control?.close()
        VehicleMapProjection.close(null)
        GeelyHudProjection.detach(activity)
        idle()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 23)
    }

    private fun start() {
        control = E01NativeMapControl(app, prepareGesture = {
            control?.sync(connected); connected
        }, onGesture = { gestures.add(it) })
        control!!.sync(true)
    }

    @Test fun realGuidanceAndFirstFrameAreRequiredAndModeClosesBeforeRouteEnds() {
        start()
        control!!.request(true)
        assertFalse(control!!.frameReady())
        assertTrue(messages().isEmpty())
        GeelyHudProjection.update(turn); idle()
        assertEquals(listOf(8, -1), messages().map { it.getIntExtra("EXTRA_STATE", -1) })
        assertTrue(modeMessages().isEmpty())
        assertTrue(control!!.frameReady())
        assertTrue(control!!.frameReady())
        assertEquals(listOf("left"), modeMessages().map { it.getStringExtra("direction") })
        assertEquals("MODE_REQUESTED", control!!.stage)
        GeelyHudProjection.update(null); idle()
        assertEquals("right", messages().takeLast(2).first().getStringExtra("direction"))
        assertEquals(9, messages().last().getIntExtra("EXTRA_STATE", -1))
        assertFalse(control!!.desired)
        assertFalse(control!!.frameReady())
    }

    @Test fun systemGesturesAreFollowedWithoutEchoAndStopAfterDisconnect() {
        start()
        gesture("left")
        assertTrue(gestures.isEmpty())
        GeelyHudProjection.update(turn); idle()
        gesture("left")
        assertEquals(listOf(true), gestures)
        assertTrue(control!!.frameReady())
        assertTrue(modeMessages().isEmpty())
        gesture("right")
        assertEquals(listOf(true, false), gestures)
        assertFalse(control!!.frameReady())
        assertTrue(modeMessages().isEmpty())
        gesture("top")
        connected = false
        gesture("left")
        assertEquals(listOf(true, false), gestures)
        assertFalse(control!!.usesFactoryGestures())
        control!!.close()
        connected = true
        gesture("left")
        assertEquals(2, gestures.size)
    }

    @Test fun disablingNativeModeClosesItWithoutStoppingIndependentFactoryGuidance() {
        E01NavigationOutput.setEnabled(app, true)
        GeelyHudProjection.update(turn); idle()
        start()
        control!!.request(true)
        assertTrue(control!!.frameReady())
        VehicleMapSettings.setVehicleMode(app, false)
        control!!.sync(true)
        assertEquals("right", messages().last().getStringExtra("direction"))
        assertEquals(1, messages().count { it.getIntExtra("EXTRA_STATE", -1) == 8 })
        assertFalse(messages().any { it.getIntExtra("EXTRA_STATE", -1) == 9 })
        GeelyHudProjection.update(null); idle()
        assertEquals(9, messages().last().getIntExtra("EXTRA_STATE", -1))
    }

    @Test fun originalGestureAfterPhoneReplacementIsRetainedWithoutAnEcho() {
        ShadowBuild.setDevice("FS11GQJ")
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(app).grantPermissions(android.Manifest.permission.SYSTEM_ALERT_WINDOW)
        ShadowDisplayManager.addDisplay("w1280dp-h480dp")
        VehicleMapSettings.select(app, GeelyHudProjection.availableDisplays(app).single())
        GeelyHudProjection.update(turn); idle()
        val current = mock(CarPlayController::class.java)
        `when`(current.clusterProjectionSessionToken()).thenReturn(Any())
        VehicleMapProjection.attach(app, current, mock(AndroidMediaSink::class.java), VehicleMapSettings.plan(app))
        idle()
        assertTrue(VehicleMapProjection.usesFactoryGestures())
        assertNull(ReflectionHelpers.getField<Any?>(VehicleMapProjection, "bridge"))
        `when`(current.clusterProjectionSessionToken()).thenReturn(Any())
        gesture("left")
        assertTrue(ReflectionHelpers.getField(VehicleMapProjection, "manual"))
        assertTrue(ReflectionHelpers.getField<E01NativeMapControl>(VehicleMapProjection, "native").desired)
        assertTrue(modeMessages().isEmpty())
        gesture("right")
        assertNull(ReflectionHelpers.getField<Any?>(VehicleMapProjection, "root"))
        assertTrue(modeMessages().isEmpty())
    }

    @Test fun capabilityIsRestrictedToTheInspectedFirmwareAndExportedReceivers() {
        ShadowBuild.setDevice("FS11GQJ")
        assertFalse(E01NativeMapControl.supported(app))
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
        assertTrue(E01NativeMapControl.supported(app))
        ShadowBuild.setDevice("OTHER_E01")
        assertFalse(E01NativeMapControl.supported(app))
        ShadowBuild.setDevice("FS11GQJ")
        shadowOf(app.packageManager).removePackage("com.neusoft.extraservice")
        assertFalse(E01NativeMapControl.supported(app))
    }
}

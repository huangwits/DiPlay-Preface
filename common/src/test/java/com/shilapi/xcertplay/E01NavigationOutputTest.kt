package com.shilapi.xcertplay

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.shilapi.xcertplay.hud.CarPlayHudGuidance
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29], manifest = Config.NONE)
class E01NavigationOutputTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val turn = CarPlayHudGuidance(180, 2, "人民路", 2, remainingSeconds = 600, remainingMeters = 4200)

    private fun installReceiver(exported: Boolean = true) {
        val pkg = "com.neusoft.extraservice"
        val app = ApplicationInfo().apply { packageName = pkg; enabled = true }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = app
            receivers = arrayOf(ActivityInfo().apply {
                packageName = pkg; name = "$pkg.receiver.AutoNaviReceiver"; enabled = true
                this.exported = exported; applicationInfo = app
            })
        })
    }

    @Test fun missingOrUnexportedServiceProducesNoBroadcasts() {
        E01NavigationOutput.setEnabled(context, true)
        val output = E01NavigationOutput(context)
        output.update(turn)
        assertTrue(shadowOf(context).broadcastIntents.isEmpty())
        installReceiver(exported = false)
        output.update(turn)
        assertFalse(E01NavigationOutput.available(context))
        assertTrue(shadowOf(context).broadcastIntents.isEmpty())
    }

    @Test fun optInStartsGuidanceOnceAndEndClearsOnlyOurOutput() {
        installReceiver()
        val output = E01NavigationOutput(context)
        output.clear()
        output.update(turn)
        assertTrue(shadowOf(context).broadcastIntents.isEmpty())
        E01NavigationOutput.setEnabled(context, true)
        output.update(turn)
        output.update(turn)
        output.update(null)
        output.clear()
        val messages = shadowOf(context).broadcastIntents
        assertEquals(3, messages.size)
        assertEquals(8, messages[0].getIntExtra("EXTRA_STATE", -1))
        assertEquals(10001, messages[1].getIntExtra("KEY_TYPE", -1))
        assertEquals(9, messages[2].getIntExtra("EXTRA_STATE", -1))
        assertEquals(10019, messages[2].getIntExtra("KEY_TYPE", -1))
        assertTrue(messages.all { it.component == ComponentName("com.neusoft.extraservice", "com.neusoft.extraservice.receiver.AutoNaviReceiver") })
    }

    @Test fun turningOffDuringNavigationClearsFactoryDisplay() {
        installReceiver()
        E01NavigationOutput.setEnabled(context, true)
        val output = E01NavigationOutput(context)
        output.update(turn)
        E01NavigationOutput.setEnabled(context, false)
        output.update(turn)
        assertEquals(9, shadowOf(context).broadcastIntents.last().getIntExtra("EXTRA_STATE", -1))
    }

    @Test fun protocolUsesAmapIconsAndPreservesDistanceUnits() {
        val intent = E01NavigationOutput.guidanceIntent(turn)
        assertEquals("AUTONAVI_STANDARD_BROADCAST_SEND", intent.action)
        assertEquals(3, intent.getIntExtra("ICON", -1))
        assertEquals(180, intent.getIntExtra("SEG_REMAIN_DIS", -1))
        assertEquals(4200, intent.getIntExtra("ROUTE_REMAIN_DIS", -1))
        assertEquals(600, intent.getIntExtra("ROUTE_REMAIN_TIME", -1))
        assertEquals("人民路", intent.getStringExtra("NEXT_ROAD_NAME"))
        assertEquals(-1, intent.getIntExtra("LIMITED_SPEED", 0))
        val roundabout = E01NavigationOutput.guidanceIntent(turn.copy(appleManeuver = 30, drivingSide = 1, remainingMeters = Long.MAX_VALUE))
        assertEquals(17, roundabout.getIntExtra("ICON", -1))
        assertEquals(3, roundabout.getIntExtra("ROUNG_ABOUT_NUM", -1))
        assertEquals(Int.MAX_VALUE, roundabout.getIntExtra("ROUTE_REMAIN_DIS", -1))
    }
}

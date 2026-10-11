package com.shilapi.xcertplay

import android.content.Intent
import android.os.Looper
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29], manifest = Config.NONE)
class E01VoiceKeyTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var controller: CarPlayController

    @Before fun prepare() {
        controller = mock(CarPlayController::class.java)
        `when`(controller.hasActiveAirPlayAttachment()).thenReturn(true)
        `when`(controller.requestSiri()).thenReturn(true)
        AirPlayPersistence.saveGeelySteeringEnabled(app, false)
        AirPlayPersistence.saveSteeringSiriEnabled(app, true)
        CarPlayMediaKeys.attach(app, controller)
    }

    @After fun cleanup() {
        CarPlayMediaKeys.detach(controller)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun successfulOrderedFactoryEventWakesSiriAndSuppressesDuplicateAndroidDelivery() {
        val receiver = deliver()
        assertTrue(receiver)
        assertTrue(CarPlayMediaKeys.requestSteeringSiri("android_voice_key"))
        verify(controller, times(1)).requestSiri()
    }

    @Test fun disabledDisconnectedOrFailedSiriLeavesFactoryAssistantAlone() {
        AirPlayPersistence.saveSteeringSiriEnabled(app, false)
        assertFalse(deliver())
        verify(controller, never()).requestSiri()
        AirPlayPersistence.saveSteeringSiriEnabled(app, true)
        `when`(controller.hasActiveAirPlayAttachment()).thenReturn(false)
        assertFalse(deliver())
        verify(controller, never()).requestSiri()
        `when`(controller.hasActiveAirPlayAttachment()).thenReturn(true)
        `when`(controller.requestSiri()).thenReturn(false)
        assertFalse(deliver())
        assertFalse(deliver())
        verify(controller, times(2)).requestSiri()
    }

    @Test fun malformedAndUnorderedEventsCannotInterceptTheWheel() {
        assertFalse(deliver(ordered = false))
        assertFalse(deliver(event = 200087))
        assertFalse(deliver(press = 2))
        assertFalse(deliver(action = "unrelated"))
        verify(controller, never()).requestSiri()
    }

    private fun deliver(ordered: Boolean = true, event: Int = 200231, press: Int = 0,
        action: String = E01VoiceKeyReceiver.ACTION): Boolean {
        val receiver = E01VoiceKeyReceiver()
        receiver.register(app)
        return try {
            val intent = Intent(action).addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra(E01VoiceKeyReceiver.EVENT, event).putExtra(E01VoiceKeyReceiver.PRESS, press)
            if (ordered) app.sendOrderedBroadcast(intent, null) else app.sendBroadcast(intent)
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(receiver).isBroadcastAborted
        } finally { app.unregisterReceiver(receiver) }
    }
}

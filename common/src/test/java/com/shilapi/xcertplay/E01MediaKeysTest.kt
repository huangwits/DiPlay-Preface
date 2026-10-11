package com.shilapi.xcertplay

import android.content.Intent
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowMediaSession

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29], manifest = Config.NONE)
class E01MediaKeysTest {
    @Test fun bothE01PreviousCodesReachCarPlay() {
        val sent = mutableListOf<Int>()
        val callback = CarPlayMediaCallback({ index, _ -> sent += index })
        for (code in listOf(89, 88, 87)) {
            assertTrue(callback.dispatchHardwareKey(KeyEvent(10, code.toLong(), KeyEvent.ACTION_DOWN, code, 0)))
        }
        assertEquals(listOf(5, 5, 4), sent)
    }

    @Test fun foregroundAndMediaSessionDeliveryOfOnePressDoNotSkipTwice() {
        val sent = mutableListOf<Int>()
        val callback = CarPlayMediaCallback({ index, _ -> sent += index })
        val press = KeyEvent(100, 100, KeyEvent.ACTION_DOWN, 87, 0)
        callback.dispatchHardwareKey(press)
        callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, press))
        callback.dispatchHardwareKey(KeyEvent(100, 150, KeyEvent.ACTION_DOWN, 87, 1))
        callback.dispatchHardwareKey(KeyEvent(100, 200, KeyEvent.ACTION_UP, 87, 0))
        callback.dispatchHardwareKey(KeyEvent(250, 250, KeyEvent.ACTION_DOWN, 87, 0))
        assertEquals(listOf(CarPlayMediaButton.NEXT, CarPlayMediaButton.NEXT), sent)
    }

    @Test fun inactiveSessionLeavesKeysWithTheCar() {
        val callback = CarPlayMediaCallback({ _, _ -> fail("Inactive session sent a key") }, active = { false })
        assertFalse(callback.dispatchHardwareKey(KeyEvent(KeyEvent.ACTION_DOWN, 87)))
        assertFalse(callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON)
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, 89))))
        callback.onSkipToNext()
        callback.onSkipToPrevious()
        callback.onPlay()
        callback.onPause()
    }

    @Test fun voiceKeyUsesTheSameSwitchForForegroundAndMediaSession() {
        var enabled = true
        var requests = 0
        val callback = CarPlayMediaCallback({ _, _ -> fail("Voice key became a media key") },
            siriEnabled = { enabled }, siri = { requests++; true })
        for (code in listOf(219, 231)) {
            assertTrue(callback.dispatchHardwareKey(KeyEvent(1, 1, KeyEvent.ACTION_DOWN, code, 0)))
            assertTrue(callback.dispatchHardwareKey(KeyEvent(1, 2, KeyEvent.ACTION_DOWN, code, 1)))
            val release = KeyEvent(1, 3, KeyEvent.ACTION_UP, code, 0)
            assertTrue(callback.dispatchHardwareKey(release))
            assertTrue(callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, release)))
        }
        assertEquals(2, requests)
        enabled = false
        assertFalse(callback.dispatchHardwareKey(KeyEvent(4, 4, KeyEvent.ACTION_DOWN, 231, 0)))
        assertFalse(callback.dispatchHardwareKey(KeyEvent(4, 5, KeyEvent.ACTION_UP, 231, 0)))
        assertEquals(2, requests)
    }

    @Test fun learnedMappingTakesPriorityAndBridgeOwnedKeysDoNotRepeat() {
        var custom = 0
        val callback = CarPlayMediaCallback({ _, _ -> fail("Default action duplicated custom mapping") },
            consumesKey = { it == 87 || it == 88 }, routeKey = { if (it.keyCode == 87) { custom++; true } else false })
        val press = KeyEvent(KeyEvent.ACTION_DOWN, 87)
        assertTrue(callback.dispatchHardwareKey(press))
        assertTrue(callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, press)))
        assertTrue(callback.dispatchHardwareKey(KeyEvent(KeyEvent.ACTION_DOWN, 88)))
        assertEquals(1, custom)
    }

    @Test
    @Config(shadows = [RecordingSession::class])
    fun oldAndroidSessionAdvertisesHardwareAndTransportControls() {
        val session = createMediaKeySession(RuntimeEnvironment.getApplication(), object : MediaSession.Callback() {}, Handler(Looper.getMainLooper()))
        // Robolectric's MediaController does not share the session's stub Binder state.
        try { assertEquals(3, Shadow.extract<RecordingSession>(session).requestedFlags) } finally { session.release() }
    }

    @Test fun androidLearnedProfileRoundTripsWithoutLogPermission() {
        val profile = SteeringProfile("星瑞", "E01", bindings = listOf(SteeringBinding("previous", 89, 0, "android")))
        assertEquals(profile, SteeringProfile.fromJson(profile.json()))
    }

    @Implements(MediaSession::class)
    class RecordingSession : ShadowMediaSession() {
        var requestedFlags = 0
        @Implementation fun setFlags(flags: Int) { requestedFlags = flags }
    }
}

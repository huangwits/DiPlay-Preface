package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class MediaAudioBufferSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clearSavedBuffer() {
        context.getSharedPreferences("xcertplay_airplay", 0).edit()
            .remove("media_buffer_ms").remove("wired_media_buffer_ms").commit()
    }

    @Test fun installationsWithoutAnExplicitChoiceUseThe300msDefault() {
        assertEquals(300, AirPlayPersistence.loadMediaBufferMillis(context))
        assertEquals(300, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
        assertEquals(300, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
    }

    @Test fun explicitSharedBufferChoiceAppliesToBothTransports() {
        AirPlayPersistence.saveMediaBufferMillis(context, 500)
        assertEquals(500, AirPlayPersistence.loadMediaBufferMillis(context))
        assertEquals(500, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
        assertEquals(500, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
    }

    @Test fun explicit100msChoiceSurvivesTheDefaultIncrease() {
        AirPlayPersistence.saveMediaBufferMillis(context, 100)
        assertEquals(100, AirPlayPersistence.loadMediaBufferMillis(context))
        assertEquals(100, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
        assertEquals(100, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
    }

    @Test fun transportSpecificCompatibilityApiUsesOneSharedBuffer() {
        AirPlayPersistence.saveMediaBufferMillis(context, wireless = false, millis = 1000)
        assertEquals(1000, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
        AirPlayPersistence.saveMediaBufferMillis(context, wireless = true, millis = 300)
        assertEquals(300, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
    }
}

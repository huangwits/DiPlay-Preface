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

    @Test fun installationsWithoutAnExplicitChoiceUseTheMoreResilientMusicBuffer() {
        assertEquals(500, AirPlayPersistence.loadMediaBufferMillis(context))
    }

    @Test fun explicitMusicBufferChoicesSurviveTheDefaultChange() {
        AirPlayPersistence.saveMediaBufferMillis(context, 100)
        assertEquals(100, AirPlayPersistence.loadMediaBufferMillis(context))
    }

    @Test fun anOlderSavedChoiceSeedsBothTransportsBeforeTheyCanDiverge() {
        AirPlayPersistence.saveMediaBufferMillis(context, 700)
        assertEquals(700, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
        assertEquals(700, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
        AirPlayPersistence.saveMediaBufferMillis(context, wireless = true, millis = 900)
        assertEquals(900, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
        assertEquals(700, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
        AirPlayPersistence.saveMediaBufferMillis(context, wireless = false, millis = 200)
        assertEquals(900, AirPlayPersistence.loadMediaBufferMillis(context, wireless = true))
        assertEquals(200, AirPlayPersistence.loadMediaBufferMillis(context, wireless = false))
    }
}

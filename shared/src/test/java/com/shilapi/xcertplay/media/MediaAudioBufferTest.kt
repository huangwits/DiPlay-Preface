package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAudioBufferTest {
    @Test
    fun `default music buffer covers ordinary wireless gaps without delaying prompts`() {
        assertEquals(500, MediaAudioBuffer.DEFAULT_MILLIS)

        val music = MediaAudioBuffer.plan(true, 48_000, 2, minBufferBytes = 7_680,
            mediaMillis = MediaAudioBuffer.DEFAULT_MILLIS)
        assertEquals(500, music.startBytes * 1000L / (48_000 * 4))
        assertEquals(700, music.trackBufferBytes * 1000L / (48_000 * 4))

        val prompt = MediaAudioBuffer.plan(false, 16_000, 1, minBufferBytes = 1_280,
            mediaMillis = MediaAudioBuffer.DEFAULT_MILLIS)
        assertEquals(4 * 1024, prompt.startBytes)
        assertEquals(16 * 1024, prompt.trackBufferBytes)
    }

    @Test
    fun `music buffers the chosen delay plus headroom`() {
        // 48 kHz stereo 16-bit = 192 000 bytes/s.
        val plan = MediaAudioBuffer.plan(true, 48_000, 2, minBufferBytes = 7_680, mediaMillis = 500)
        assertEquals(96_000, plan.startBytes)
        assertEquals(134_400, plan.trackBufferBytes)
    }

    @Test
    fun `all streams mapped to media receive the music buffer`() {
        val selection = AudioChannelMapper.map(
            "compatibility", 100, AudioChannelMappingMode.AUTOMOTIVE_BUS,
        )
        val plan = MediaAudioBuffer.plan(selection.channel == AudioChannel.MEDIA,
            48_000, 2, minBufferBytes = 7_680, mediaMillis = 500)
        assertEquals(96_000, plan.startBytes)
    }

    @Test
    fun `calls and prompts keep the low-latency buffer`() {
        val plan = MediaAudioBuffer.plan(false, 16_000, 1, minBufferBytes = 1_280, mediaMillis = 1000)
        assertEquals(4 * 1024, plan.startBytes)
        assertEquals(16 * 1024, plan.trackBufferBytes)
        val guidance = AudioChannelMapper.map("default", 100, AudioChannelMappingMode.AUTOMOTIVE_BUS)
        assertEquals(plan, MediaAudioBuffer.plan(guidance.channel == AudioChannel.MEDIA,
            16_000, 1, minBufferBytes = 1_280, mediaMillis = 1000))
    }

    @Test
    fun `unknown delay falls back to the default`() {
        assertEquals(MediaAudioBuffer.DEFAULT_MILLIS, MediaAudioBuffer.sanitize(250))
        assertEquals(96_000, MediaAudioBuffer.plan(true, 48_000, 2, 7_680, mediaMillis = 42).startBytes)
    }

    @Test fun `all hundred millisecond steps reach the renderer without falling back`() {
        for (duration in 100..1000 step 100) {
            assertEquals(duration * 192, MediaAudioBuffer.plan(true, 48_000, 2, 7_680, duration).startBytes)
        }
    }

    @Test
    fun `start level stays below a smaller granted buffer`() {
        assertEquals(98_000, MediaAudioBuffer.startBytesFor(192_000, 100_000, 2_048 - 48))
        assertEquals(57_600, MediaAudioBuffer.startBytesFor(57_600, 134_400, 2_048))
        assertEquals(57_600, MediaAudioBuffer.startBytesFor(57_600, 0, 2_048))
    }
}

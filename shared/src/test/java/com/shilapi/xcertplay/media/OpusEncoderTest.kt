package com.shilapi.xcertplay.media

import org.concentus.OpusDecoder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class OpusEncoderTest {
    @Test fun encodesDecodable20MsSpeechFramesAndReleasesOnClose() {
        val encoder = OpusEncoder(48_000)
        val decoder = OpusDecoder(48_000, 1)
        assertTrue(encoder.available)
        repeat(10) { frame ->
            val pcm = ByteArray(OpusEncoder.FRAME_BYTES)
            repeat(OpusEncoder.FRAME_SAMPLES) { i ->
                val value = (8000 * sin(2 * PI * 220 * (frame * 960 + i) / 48_000)).toInt()
                pcm[2 * i] = value.toByte()
                pcm[2 * i + 1] = (value shr 8).toByte()
            }
            val packet = encoder.encode(pcm).single()
            val decoded = ShortArray(960)
            assertEquals(960, decoder.decode(packet, 0, packet.size, decoded, 0, 960, false))
        }
        encoder.close()
        assertFalse(encoder.available)
        assertTrue(encoder.encode(ByteArray(1920)).isEmpty())
    }

    @Test fun invalidFramesDoNotPreventTheNextValidFrame() {
        OpusEncoder(48_000).use { encoder ->
            for (size in listOf(0, 1, 960, 1918, 1922, 3840)) assertTrue(encoder.encode(ByteArray(size)).isEmpty())
            assertEquals(1, encoder.encode(ByteArray(1920)).size)
        }
    }

    @Test fun probeUsesWorkingBundledEncoderWithoutPlatformOpus() {
        assertTrue(OpusEncoderSupport.isAvailable())
        assertTrue(OpusEncoderSupport.isAvailable())
    }
}

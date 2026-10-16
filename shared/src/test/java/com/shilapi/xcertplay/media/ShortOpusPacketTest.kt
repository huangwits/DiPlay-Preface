package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class ShortOpusPacketTest {
    @Test fun validThreeByteSilenceIsDecodedByTheActualRendererInsteadOfDropped() {
        val renderer = audioRendererFixture(AudioFormat(AudioCodecKind.OPUS, 48_000, 1, 96, "speechrecognition"), 100)
        val type = renderer.javaClass
        val packetType = Class.forName("com.shilapi.xcertplay.media.AudioRenderer\$AudioPacket")
        val packet = packetType.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(ByteArray(12) + byteArrayOf(0xf8.toByte(), 0xff.toByte(), 0xfe.toByte()), 0)
        val decoder = SoftwareOpusDecoder(48_000, 1)
        assertEquals(1920, decoder.decode(byteArrayOf(0xf8.toByte(), 0xff.toByte(), 0xfe.toByte())))
        type.getDeclaredField("softwareOpusDecoder").apply { isAccessible = true }.set(renderer, decoder)
        type.getDeclaredMethod("handle", packetType).apply { isAccessible = true }.invoke(renderer, packet)
        fun count(name: String) = type.getDeclaredField(name).apply { isAccessible = true }.getInt(renderer)
        assertEquals(0, count("shortOpusPackets"))
        assertEquals(0, count("inputDropped"))
        assertEquals(1, count("outputBuffers"))
    }
}

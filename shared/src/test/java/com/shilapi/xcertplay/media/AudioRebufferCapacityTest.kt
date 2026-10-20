package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioTrack
import android.os.Build
import java.nio.ByteBuffer
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 28, 33], manifest = Config.NONE, shadows = [AudioRebufferCapacityTest.CapacityTrack::class])
class AudioRebufferCapacityTest {
    @Test @Config(sdk = [23])
    fun api22RecoversAStarvedTrackAndPreservesPcmWhenItsActualCapacityIsSmaller() {
        val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().build())
            .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(48_000)
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(4096).build()
        val type = Class.forName("com.shilapi.xcertplay.media.AudioRenderer")
        val renderer = audioRendererFixture(AudioFormat(AudioCodecKind.LPCM, 48_000, 2, 96, "media"), 300)
        fun set(name: String, value: Any) = type.getDeclaredField(name).apply { isAccessible = true }.set(renderer, value)
        fun get(name: String) = type.getDeclaredField(name).apply { isAccessible = true }.get(renderer)
        try {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 22)
            CapacityTrack.capacity = 4096; CapacityTrack.queued = 0; CapacityTrack.head = 1000
            CapacityTrack.playing = true; CapacityTrack.blocked = false; CapacityTrack.written = 0
            set("track", track); set("mappedChannel", AudioChannel.MEDIA); set("playbackStarted", true)
            set("startThresholdBytes", 16_000); set("lastPcmWriteNs", System.nanoTime())
            (get("bufferProgress") as AudioBufferProgress).written(4000)
            type.getDeclaredMethod("maintainPlaybackBuffer").apply { isAccessible = true }.invoke(renderer)
            assertFalse("API 22 never entered rebuffering", CapacityTrack.playing)
            val pcm = ByteArray(20_000)
            type.getDeclaredMethod("writePcm", ByteArray::class.java, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(renderer, pcm, 0, pcm.size)
            assertTrue(CapacityTrack.playing)
            assertFalse("Paused prebuffer used a blocking write", CapacityTrack.blocked)
            assertEquals("PCM was lost when prebuffer filled", pcm.size, CapacityTrack.written)
        } finally {
            CapacityTrack.head = 0
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 23)
            track.release()
        }
    }

    @Test fun residualAudioCountsTowardRestartBeforeAPausedTrackFills() {
        for (millis in listOf(500, 1000)) {
            val plan = MediaAudioBuffer.plan(true, 48_000, 2, 4096, millis)
            val threshold = MediaAudioBuffer.startBytesFor(plan.startBytes, plan.trackBufferBytes, 4096)
            CapacityTrack.capacity = plan.trackBufferBytes
            CapacityTrack.queued = threshold / 2
            CapacityTrack.playing = true
            CapacityTrack.blocked = false
            CapacityTrack.head = 0
            val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().build())
                .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(48_000)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(plan.trackBufferBytes).build()
            val type = Class.forName("com.shilapi.xcertplay.media.AudioRenderer")
            val renderer = audioRendererFixture(AudioFormat(AudioCodecKind.LPCM, 48_000, 2, 96, "media"), millis)
            fun set(name: String, value: Any) = type.getDeclaredField(name).apply { isAccessible = true }.set(renderer, value)
            fun get(name: String) = type.getDeclaredField(name).apply { isAccessible = true }.get(renderer)
            try {
                set("track", track)
                set("mappedChannel", AudioChannel.MEDIA)
                set("playbackStarted", true)
                set("startThresholdBytes", threshold)
                set("lastPcmWriteNs", System.nanoTime())
                (get("bufferProgress") as AudioBufferProgress).written(CapacityTrack.queued)
                type.getDeclaredMethod("maintainPlaybackBuffer").apply { isAccessible = true }.invoke(renderer)
                assertFalse(CapacityTrack.playing)
                type.getDeclaredMethod("writePcm", ByteArray::class.java, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType).apply { isAccessible = true }
                    .invoke(renderer, ByteArray(threshold), 0, threshold)
                assertFalse("${millis}ms buffer blocked before play()", CapacityTrack.blocked)
                assertTrue("${millis}ms buffer never resumed", CapacityTrack.playing)
            } finally { track.release() }
        }
    }

    @Implements(AudioTrack::class)
    class CapacityTrack {
        @Implementation fun getPlaybackHeadPosition() = head
        @Implementation fun getUnderrunCount() = 1
        @Implementation fun pause() { playing = false }
        @Implementation fun play() { playing = true }
        @Implementation fun write(bytes: ByteArray, offset: Int, size: Int, mode: Int): Int {
            if (!playing && queued + size > capacity) {
                blocked = true
                return 0 // A real blocking AudioTrack.write waits here indefinitely.
            }
            if (!playing) queued += size
            written += size
            return size
        }
        @Implementation fun write(bytes: ByteBuffer, size: Int, mode: Int): Int {
            if (mode != AudioTrack.WRITE_NON_BLOCKING && !playing && queued + size > capacity) {
                blocked = true
                return 0
            }
            val accepted = if (playing) size else minOf(size, capacity - queued).coerceAtLeast(0)
            if (!playing) queued += accepted
            written += accepted
            bytes.position(bytes.position() + accepted)
            return accepted
        }
        companion object {
            var capacity = 0
            var queued = 0
            var playing = true
            var blocked = false
            var head = 0
            var written = 0
        }
    }
}

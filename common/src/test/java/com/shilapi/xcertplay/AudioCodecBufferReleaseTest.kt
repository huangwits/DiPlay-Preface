package com.shilapi.xcertplay

import android.media.AudioTrack
import android.media.MediaCodec
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.media.AndroidMediaSink
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class AudioCodecBufferReleaseTest {
    @Test fun outputIsReleasedBeforePlaybackReceivesTheCopiedSamples() = withRenderer { renderer ->
        val codec = codec()
        val pcm = byteArrayOf(11, 12, 13, 14)
        val output = ByteBuffer.wrap(byteArrayOf(0, 0) + pcm)
        `when`(codec.getOutputBuffer(0)).thenReturn(output)
        var released = false
        doAnswer { released = true; output.put(2, 99); null }.`when`(codec).releaseOutputBuffer(0, false)
        val track = mock(AudioTrack::class.java)
        `when`(track.write(any(ByteArray::class.java), anyInt(), anyInt())).thenAnswer { call ->
            assertTrue("Codec output must be returned before potentially blocking AudioTrack.write", released)
            assertArrayEquals(pcm, call.getArgument<ByteArray>(0).copyOfRange(0, 4))
            4
        }
        ReflectionHelpers.setField(renderer, "track", track)
        ReflectionHelpers.setField(renderer, "fadeApplied", true)
        ReflectionHelpers.setField(renderer, "playbackStarted", true)
        drain(renderer, codec)
        verify(track).write(any(ByteArray::class.java), eq(0), eq(4))
        verify(codec, times(1)).releaseOutputBuffer(0, false)
    }

    @Test fun failedCopyStillReturnsTheCodecBuffer() = withRenderer { renderer ->
        val codec = codec()
        `when`(codec.getOutputBuffer(0)).thenReturn(ByteBuffer.allocate(1))
        try { drain(renderer, codec); fail("Expected invalid output range") }
        catch (e: InvocationTargetException) { assertTrue(e.cause is IllegalArgumentException) }
        verify(codec, times(1)).releaseOutputBuffer(0, false)
    }

    @Test fun nullOutputIsReleasedWithoutWritingStalePcm() = withRenderer { renderer ->
        val codec = codec()
        val track = mock(AudioTrack::class.java)
        ReflectionHelpers.setField(renderer, "track", track)
        drain(renderer, codec)
        verify(codec, times(1)).releaseOutputBuffer(0, false)
        verify(track, never()).write(any(ByteArray::class.java), anyInt(), anyInt())
    }

    private fun codec(): MediaCodec {
        val codec = mock(MediaCodec::class.java)
        `when`(codec.dequeueOutputBuffer(any(MediaCodec.BufferInfo::class.java), anyLong())).thenAnswer { call ->
            call.getArgument<MediaCodec.BufferInfo>(0).set(2, 4, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            0
        }
        return codec
    }
    private fun drain(renderer: Any, codec: MediaCodec) {
        renderer.javaClass.getDeclaredMethod("drainCodec", MediaCodec::class.java)
            .apply { isAccessible = true }.invoke(renderer, codec)
    }
    private fun withRenderer(test: (Any) -> Unit) {
        val sink = AndroidMediaSink()
        try {
            val renderer = sink.javaClass.getDeclaredMethod("audioRenderer", AudioStreamId::class.java, AudioFormat::class.java)
                .apply { isAccessible = true }.invoke(sink, AudioStreamId(100, "default"),
                    AudioFormat(AudioCodecKind.AAC_LC, 48_000, 2, 100, "default"))
            test(renderer)
        } finally { sink.close() }
    }
}

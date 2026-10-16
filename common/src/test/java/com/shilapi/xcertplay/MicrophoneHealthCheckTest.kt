package com.shilapi.xcertplay

import android.media.MediaRecorder
import com.geelyplay.preface.media.MicrophoneCaptureConfiguration
import com.geelyplay.preface.media.MicrophoneEffects
import com.geelyplay.preface.media.MicrophoneHealthCheck
import com.geelyplay.preface.media.MicrophoneHealthStatus
import com.geelyplay.preface.media.MicrophoneProbeBackend
import com.geelyplay.preface.media.MicrophoneProbeRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneHealthCheckTest {
    @Test fun permissionAndSessionGatesNeverOpenARecorder() {
        val backend = FakeBackend()

        assertEquals(
            MicrophoneHealthStatus.PERMISSION_REQUIRED,
            MicrophoneHealthCheck.run(backend, permissionGranted = false) { true }.status,
        )
        assertEquals(
            MicrophoneHealthStatus.SESSION_ACTIVE,
            MicrophoneHealthCheck.run(backend, permissionGranted = true) { false }.status,
        )
        assertTrue(backend.openedSources.isEmpty())
    }

    @Test fun checkPrefersCommunicationSourceAndAlwaysReleasesTheRecorder() {
        val backend = FakeBackend(sample = 3_000)

        val result = MicrophoneHealthCheck.run(backend, permissionGranted = true) { true }

        assertEquals(MicrophoneHealthStatus.SIGNAL_DETECTED, result.status)
        assertEquals(
            MicrophoneCaptureConfiguration(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16_000),
            result.selectedConfiguration,
        )
        assertEquals(listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION), backend.openedSources)
        assertTrue(backend.recorders.single().stopped)
        assertTrue(backend.recorders.single().released)
        assertEquals(750L, result.metrics?.durationMillis)
    }

    @Test fun lowSignalAndClippingAreReportedAsMeasurements() {
        assertEquals(
            MicrophoneHealthStatus.LOW_SIGNAL,
            MicrophoneHealthCheck.run(FakeBackend(sample = 100), true) { true }.status,
        )
        val clipping = MicrophoneHealthCheck.run(FakeBackend(sample = 32_767), true) { true }
        assertEquals(MicrophoneHealthStatus.CLIPPING, clipping.status)
        assertTrue((clipping.metrics?.clippingPercent ?: 0.0) >= 99.0)
    }

    @Test fun unusableConfigurationsAreBoundedAndReported() {
        val backend = FakeBackend(openFailure = true)
        val result = MicrophoneHealthCheck.run(backend, permissionGranted = true) { true }

        assertEquals(MicrophoneHealthStatus.UNAVAILABLE, result.status)
        assertEquals(12, result.attempts.size)
        assertTrue(result.attempts.all { it.failure == "start/IllegalStateException" })
        assertFalse(backend.recorders.any { it.recording })
    }

    @Test fun sessionStartingDuringCaptureStopsAndReleasesTheRecorder() {
        val backend = FakeBackend(sample = 3_000)
        var checks = 0

        val result = MicrophoneHealthCheck.run(backend, permissionGranted = true) {
            checks++ < 4
        }

        assertEquals(MicrophoneHealthStatus.SESSION_ACTIVE, result.status)
        assertTrue(backend.recorders.single().stopped)
        assertTrue(backend.recorders.single().released)
    }

    private class FakeBackend(
        private val sample: Short? = null,
        private val openFailure: Boolean = false,
    ) : MicrophoneProbeBackend {
        val openedSources = mutableListOf<Int>()
        val recorders = mutableListOf<FakeRecorder>()

        override fun minimumBufferBytes(sampleRate: Int) = sampleRate * 2 / 10

        override fun open(source: Int, sampleRate: Int, bufferBytes: Int): MicrophoneProbeRecorder {
            openedSources += source
            if (openFailure) throw IllegalStateException("not supported")
            return FakeRecorder(sample ?: 3_000).also(recorders::add)
        }

        override fun effects() = MicrophoneEffects(aec = true, noiseSuppressor = false, agc = null)
    }

    private class FakeRecorder(private val sample: Short) : MicrophoneProbeRecorder {
        private var samplesRead = 0
        private var recordingNow = false
        var stopped = false
        var released = false
        override val initialized = true
        override val recording get() = recordingNow

        override fun start() { recordingNow = true }

        override fun read(samples: ShortArray): Int {
            if (!recordingNow) return -3
            val remaining = 12_000 - samplesRead
            if (remaining <= 0) return 0
            val count = minOf(samples.size, remaining)
            samples.fill(sample, 0, count)
            samplesRead += count
            return count
        }

        override fun stop() { stopped = true; recordingNow = false }
        override fun release() { released = true; recordingNow = false }
    }
}

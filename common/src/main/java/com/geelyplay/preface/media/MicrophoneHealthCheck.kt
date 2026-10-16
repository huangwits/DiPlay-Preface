package com.geelyplay.preface.media

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

internal enum class MicrophoneHealthStatus {
    SIGNAL_DETECTED,
    LOW_SIGNAL,
    CLIPPING,
    PERMISSION_REQUIRED,
    SESSION_ACTIVE,
    UNAVAILABLE,
    READ_FAILED,
}

internal data class MicrophoneHealthMetrics(
    val sampleCount: Int,
    val durationMillis: Long,
    val rmsDbfs: Double?,
    val peakPercent: Double,
    val clippingPercent: Double,
)

internal data class MicrophoneSampleRateCheck(
    val sampleRate: Int,
    val minBufferBytes: Int?,
    val failure: String? = null,
)

internal data class MicrophoneCaptureConfiguration(val source: Int, val sampleRate: Int)
internal data class MicrophoneProbeAttempt(val configuration: MicrophoneCaptureConfiguration, val failure: String)
internal data class MicrophoneEffects(val aec: Boolean?, val noiseSuppressor: Boolean?, val agc: Boolean?)

internal data class MicrophoneHealthSnapshot(
    val status: MicrophoneHealthStatus,
    val permissionGranted: Boolean,
    val sampleRates: List<MicrophoneSampleRateCheck>,
    val selectedConfiguration: MicrophoneCaptureConfiguration?,
    val metrics: MicrophoneHealthMetrics?,
    val effects: MicrophoneEffects,
    val attempts: List<MicrophoneProbeAttempt>,
)

internal interface MicrophoneProbeRecorder {
    val initialized: Boolean
    val recording: Boolean
    fun start()
    fun read(samples: ShortArray): Int
    fun stop()
    fun release()
}

internal interface MicrophoneProbeBackend {
    fun minimumBufferBytes(sampleRate: Int): Int
    fun open(source: Int, sampleRate: Int, bufferBytes: Int): MicrophoneProbeRecorder
    fun effects(): MicrophoneEffects
}

internal object MicrophoneHealthCheck {
    val candidateSampleRates = listOf(16_000, 24_000, 48_000, 44_100)
    val candidateSources = listOf(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.MIC,
    )

    const val TARGET_CAPTURE_MILLIS = 750L
    const val MIN_CAPTURE_MILLIS = 200L
    const val MAX_EMPTY_READS = 8
    private const val CLIPPING_MAGNITUDE = 32_700
    private const val LOW_SIGNAL_DBFS = -50.0
    private const val MIN_PEAK_PERCENT = 2.0
    private const val CLIPPING_PERCENT = 0.5

    fun run(
        backend: MicrophoneProbeBackend,
        permissionGranted: Boolean,
        captureAllowed: () -> Boolean,
    ): MicrophoneHealthSnapshot {
        val effects = runCatching { backend.effects() }.getOrDefault(MicrophoneEffects(null, null, null))
        if (!permissionGranted) return snapshot(MicrophoneHealthStatus.PERMISSION_REQUIRED, false, effects)
        if (!captureAllowed()) return snapshot(MicrophoneHealthStatus.SESSION_ACTIVE, true, effects)

        val rates = candidateSampleRates.map { rate ->
            runCatching { backend.minimumBufferBytes(rate) }
                .fold(
                    onSuccess = { bytes -> MicrophoneSampleRateCheck(rate, bytes.takeIf { it > 0 }) },
                    onFailure = { error -> MicrophoneSampleRateCheck(rate, null, error.javaClass.simpleName) },
                )
        }
        val attempts = mutableListOf<MicrophoneProbeAttempt>()
        var readFailed = false
        for (rate in rates.filter { it.minBufferBytes != null }) {
            for (source in candidateSources) {
                if (!captureAllowed()) return snapshot(
                    MicrophoneHealthStatus.SESSION_ACTIVE, true, effects, rates, attempts = attempts,
                )
                val configuration = MicrophoneCaptureConfiguration(source, rate.sampleRate)
                val bufferBytes = max(rate.minBufferBytes!! * 2, rate.sampleRate * 2 / 5)
                var recorder: MicrophoneProbeRecorder? = null
                var started = false
                try {
                    recorder = backend.open(source, rate.sampleRate, bufferBytes)
                    if (!recorder.initialized) {
                        attempts += MicrophoneProbeAttempt(configuration, "initialize")
                        continue
                    }
                    recorder.start()
                    started = recorder.recording
                    if (!started) {
                        attempts += MicrophoneProbeAttempt(configuration, "start/state")
                        continue
                    }
                    when (val capture = capture(recorder, rate.sampleRate, captureAllowed)) {
                        CaptureResult.SessionActive -> return snapshot(
                            MicrophoneHealthStatus.SESSION_ACTIVE, true, effects, rates, attempts = attempts,
                        )
                        is CaptureResult.Failure -> {
                            readFailed = true
                            attempts += MicrophoneProbeAttempt(configuration, capture.reason)
                        }
                        is CaptureResult.Success -> return snapshot(
                            classify(capture.metrics), true, effects, rates, configuration,
                            capture.metrics, attempts,
                        )
                    }
                } catch (error: Exception) {
                    attempts += MicrophoneProbeAttempt(
                        configuration,
                        "${if (started) "read" else "start"}/${error.javaClass.simpleName}",
                    )
                } finally {
                    if (started) runCatching { recorder?.stop() }
                    runCatching { recorder?.release() }
                }
            }
        }
        return snapshot(
            if (readFailed) MicrophoneHealthStatus.READ_FAILED else MicrophoneHealthStatus.UNAVAILABLE,
            true, effects, rates, attempts = attempts,
        )
    }

    private fun capture(
        recorder: MicrophoneProbeRecorder,
        sampleRate: Int,
        captureAllowed: () -> Boolean,
    ): CaptureResult {
        val accumulator = SignalAccumulator()
        val buffer = ShortArray(max(256, sampleRate / 20))
        val deadline = System.nanoTime() + TARGET_CAPTURE_MILLIS * 1_000_000L
        var emptyReads = 0
        while (System.nanoTime() < deadline && accumulator.durationMillis(sampleRate) < TARGET_CAPTURE_MILLIS) {
            if (!captureAllowed()) return CaptureResult.SessionActive
            val count = recorder.read(buffer)
            if (count < 0) return CaptureResult.Failure("read/$count")
            if (count == 0) {
                emptyReads++
                if (emptyReads >= MAX_EMPTY_READS) break
                Thread.sleep(20L)
                continue
            }
            if (count > buffer.size) return CaptureResult.Failure("read/invalid-count")
            accumulator.add(buffer, count)
        }
        val metrics = accumulator.metrics(sampleRate)
        return if (metrics.durationMillis >= MIN_CAPTURE_MILLIS) CaptureResult.Success(metrics)
        else CaptureResult.Failure("read/insufficient-audio")
    }

    private fun classify(metrics: MicrophoneHealthMetrics): MicrophoneHealthStatus = when {
        metrics.clippingPercent >= CLIPPING_PERCENT -> MicrophoneHealthStatus.CLIPPING
        metrics.rmsDbfs == null || metrics.rmsDbfs < LOW_SIGNAL_DBFS || metrics.peakPercent < MIN_PEAK_PERCENT ->
            MicrophoneHealthStatus.LOW_SIGNAL
        else -> MicrophoneHealthStatus.SIGNAL_DETECTED
    }

    private fun snapshot(
        status: MicrophoneHealthStatus,
        permissionGranted: Boolean,
        effects: MicrophoneEffects,
        rates: List<MicrophoneSampleRateCheck> = emptyList(),
        selected: MicrophoneCaptureConfiguration? = null,
        metrics: MicrophoneHealthMetrics? = null,
        attempts: List<MicrophoneProbeAttempt> = emptyList(),
    ) = MicrophoneHealthSnapshot(status, permissionGranted, rates, selected, metrics, effects, attempts)

    private sealed interface CaptureResult {
        data object SessionActive : CaptureResult
        data class Failure(val reason: String) : CaptureResult
        data class Success(val metrics: MicrophoneHealthMetrics) : CaptureResult
    }

    private class SignalAccumulator {
        private var count = 0
        private var squareSum = 0.0
        private var peak = 0
        private var clipping = 0

        fun add(samples: ShortArray, length: Int) {
            for (index in 0 until length) {
                val sample = samples[index].toInt()
                val magnitude = if (sample == Short.MIN_VALUE.toInt()) 32_768 else abs(sample)
                squareSum += sample.toDouble() * sample
                peak = max(peak, magnitude)
                if (magnitude >= CLIPPING_MAGNITUDE) clipping++
            }
            count += length
        }

        fun metrics(sampleRate: Int): MicrophoneHealthMetrics {
            val rms = if (count == 0) 0.0 else sqrt(squareSum / count)
            return MicrophoneHealthMetrics(
                sampleCount = count,
                durationMillis = count * 1000L / sampleRate,
                rmsDbfs = rms.takeIf { it > 0.0 }?.let { 20.0 * log10(it / 32_768.0) },
                peakPercent = peak * 100.0 / 32_768.0,
                clippingPercent = if (count == 0) 0.0 else clipping * 100.0 / count,
            )
        }

        fun durationMillis(sampleRate: Int): Long = count * 1000L / sampleRate
    }
}

internal class AndroidMicrophoneProbeBackend : MicrophoneProbeBackend {
    override fun minimumBufferBytes(sampleRate: Int): Int = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
    )

    override fun open(source: Int, sampleRate: Int, bufferBytes: Int): MicrophoneProbeRecorder {
        val record = AudioRecord(
            source, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferBytes,
        )
        return object : MicrophoneProbeRecorder {
            override val initialized get() = record.state == AudioRecord.STATE_INITIALIZED
            override val recording get() = record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            override fun start() = record.startRecording()
            override fun read(samples: ShortArray) = record.read(samples, 0, samples.size)
            override fun stop() = record.stop()
            override fun release() = record.release()
        }
    }

    override fun effects() = MicrophoneEffects(
        available { AcousticEchoCanceler.isAvailable() },
        available { NoiseSuppressor.isAvailable() },
        available { AutomaticGainControl.isAvailable() },
    )

    private fun available(read: () -> Boolean): Boolean? = runCatching(read).getOrNull()
}

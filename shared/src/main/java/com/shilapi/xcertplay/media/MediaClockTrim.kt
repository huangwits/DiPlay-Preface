package com.shilapi.xcertplay.media

import kotlin.math.exp
import kotlin.math.roundToInt

/** Slowly corrects music playback rate when the head unit and phone audio clocks drift apart. */
internal class MediaClockTrim(
    private val sampleRate: Int,
    private val targetFrames: Long,
    private var integral: Double = learnedTrim,
) {
    private var level: Double? = null
    private var lastSampleNs = 0L
    private var lastUpdateNs: Long? = null

    var rate = sampleRate
        private set

    fun sample(queuedFrames: Long, nowNs: Long) {
        if (targetFrames <= 0L) return
        val error = (queuedFrames - targetFrames).toDouble() / targetFrames
        val previous = level
        level = if (previous == null || nowNs - lastSampleNs > STALE_NS) error
        else previous + (error - previous) * (1.0 - exp(-(nowNs - lastSampleNs).toDouble() / AVERAGE_NS))
        lastSampleNs = nowNs
    }

    fun rateDue(nowNs: Long): Int? {
        val error = level ?: return null
        if (nowNs - lastSampleNs > STALE_NS) return null
        val previousUpdate = lastUpdateNs
        if (previousUpdate != null && nowNs - previousUpdate < UPDATE_NS) return null
        val seconds = if (previousUpdate == null) 0.0
            else ((nowNs - previousUpdate) / 1e9).coerceAtMost(MAX_STEP_SECONDS)
        lastUpdateNs = nowNs
        integral = (integral + KI * error * seconds).coerceIn(-MAX_TRIM, MAX_TRIM)
        learnedTrim = integral
        val next = (sampleRate * (1.0 + (KP * error + integral).coerceIn(-MAX_TRIM, MAX_TRIM))).roundToInt()
        if (next == rate) return null
        rate = next
        return next
    }

    internal companion object {
        const val MAX_TRIM = 0.015
        const val KP = 0.03
        const val KI = 0.001
        const val AVERAGE_NS = 5_000_000_000.0
        const val UPDATE_NS = 1_000_000_000L
        const val STALE_NS = 250_000_000L
        const val MAX_STEP_SECONDS = 2.0

        @Volatile var learnedTrim = 0.0
    }
}

package com.shilapi.xcertplay.media

import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaClockTrimTest {
    @Before fun forgetLearnedTrim() { MediaClockTrim.learnedTrim = 0.0 }
    @After fun clearLearnedTrim() { MediaClockTrim.learnedTrim = 0.0 }

    private class Drive(val drift: Double, val correctClock: Boolean, seed: Int = 7) {
        private val target = 14_400L
        private val trim = MediaClockTrim(48_000, target)
        private val random = Random(seed)
        private var queued = target.toDouble()
        var starved = 0
            private set
        var lowest = queued
            private set
        var rate = 48_000
            private set

        fun run(seconds: Int) {
            var nextPacketMs = 0.0
            var pending = 0
            var gapUntilMs = -1.0
            var played = 0.0
            for (ms in 0 until seconds * 1000) {
                val nowNs = ms * 1_000_000L
                if (random.nextDouble() < 0.4 / 1000) gapUntilMs = ms + 50 + random.nextDouble() * 100
                while (nextPacketMs <= ms) { pending += 352; nextPacketMs += 352 / 48.0 }
                if (ms >= gapUntilMs && pending > 0) {
                    queued += pending
                    pending = 0
                    if (correctClock) {
                        trim.sample(queued.toLong(), nowNs)
                        trim.rateDue(nowNs)?.let { rate = it }
                    }
                }
                played += rate * (1 + drift) / 1000.0
                while (played >= 1024) {
                    played -= 1024
                    if (queued >= 1024) queued -= 1024 else { starved++; queued = 0.0 }
                }
                lowest = minOf(lowest, queued)
            }
        }
    }

    @Test fun correctingClockPreventsASevenTenthPercentFastTrackFromRunningDry() {
        val drive = Drive(0.0076, correctClock = true)
        drive.run(300)
        assertEquals(0, drive.starved)
        assertTrue(drive.lowest > 0.2 * 14_400)
        assertEquals(47_638.0, drive.rate.toDouble(), 15.0)
        assertEquals(-0.0075, MediaClockTrim.learnedTrim, 0.0003)
    }

    @Test fun aSlowHeadUnitIsTrimmedInTheOppositeDirection() {
        val drive = Drive(-0.0076, correctClock = true)
        drive.run(300)
        assertEquals(0, drive.starved)
        assertEquals(48_368.0, drive.rate.toDouble(), 15.0)
    }

    @Test fun anAccurateClockNeedsNoMeaningfulCorrection() {
        val drive = Drive(0.0, correctClock = true)
        drive.run(300)
        assertEquals(0, drive.starved)
        assertEquals(48_000.0, drive.rate.toDouble(), 5.0)
    }

    @Test fun trimIsBoundedToOnePointFivePercent() {
        val trim = MediaClockTrim(48_000, 14_400)
        for (second in 0..600) {
            val now = second * 1_000_000_000L
            trim.sample(0, now)
            trim.rateDue(now)
        }
        assertEquals(47_280, trim.rate)
    }

    @Test fun aPauseDoesNotContinueIntegratingStaleQueueLevels() {
        val trim = MediaClockTrim(48_000, 14_400)
        trim.sample(7_200, 0)
        assertEquals(47_280, trim.rateDue(0))
        assertNull(trim.rateDue(2_000_000_000L))
        assertEquals(47_280, trim.rate)
        trim.sample(14_400, 2_100_000_000L)
        assertEquals(48_000, trim.rateDue(2_100_000_000L))
    }
}

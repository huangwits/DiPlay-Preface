package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class E01PerformanceTest {
    @Test fun hardwareDetectionDoesNotConfuseBitnessOrModelNames() {
        assertTrue(E01Performance.matchesHardware("mt6735"))
        assertTrue(E01Performance.matchesHardware("unknown", "MT6735M"))
        assertTrue(E01Performance.matchesHardware("board_mt6735"))
        assertFalse(E01Performance.matchesHardware("E01", "arm64-v8a", "MT6739", "MT67350"))
    }

    @Test fun fullHdNegotiatesQuarterThePixelsAtThirtyFrames() {
        val requested = AirPlayDisplayConfig(1920, 1080, 200, 113, 60, primaryInputDevice = 3)
        assertEquals(requested.copy(widthPixels = 960, heightPixels = 540, fps = 30), E01Performance.display(requested))
    }

    @Test fun smallScreensAreNotUpscaledAndPortraitRotatesBudget() {
        assertEquals(AirPlayDisplayConfig(800, 480, fps = 30), E01Performance.display(AirPlayDisplayConfig(800, 480)))
        assertEquals(AirPlayDisplayConfig(540, 960, fps = 30), E01Performance.display(AirPlayDisplayConfig(1080, 1920)))
    }

    @Test fun dimensionsFitBudgetKeepAspectAndPreservePhysicalSize() {
        for ((w, h) in listOf(1024 to 600, 1280 to 720, 1920 to 720, 1920 to 978, 3840 to 2160)) {
            val result = E01Performance.display(AirPlayDisplayConfig(w, h, 200, 110))
            assertTrue(maxOf(result.widthPixels, result.heightPixels) <= 960)
            assertTrue(minOf(result.widthPixels, result.heightPixels) <= 540)
            assertEquals(0, result.widthPixels % 2)
            assertEquals(0, result.heightPixels % 2)
            assertEquals(w.toDouble() / h, result.widthPixels.toDouble() / result.heightPixels, 0.015)
            assertEquals(200, result.widthPhysicalMm)
            assertEquals(110, result.heightPhysicalMm)
        }
    }

    @Test fun safeAreaUsesReducedCanvasCoordinates() {
        val display = E01Performance.display(AirPlayDisplayConfig(1920, 1080))
        val insets = AirPlaySafeArea.toInsets(SafeAreaRect(96, 54, 1824, 1026),
            1920, 1080, display.widthPixels, display.heightPixels)
        assertEquals(AirPlayInsets(top = 27, bottom = 27, left = 48, right = 48), insets)
    }
}

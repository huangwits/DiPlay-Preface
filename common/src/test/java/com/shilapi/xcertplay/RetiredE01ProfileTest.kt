package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33])
class RetiredE01ProfileTest {
    @Test fun oldEnabledProfileAndE01HardwareNoLongerOverrideSavedQuality() {
        val app = RuntimeEnvironment.getApplication()
        ShadowBuild.setHardware("mt6735")
        app.getSharedPreferences("diplay_performance", Context.MODE_PRIVATE).edit().putBoolean("e01_enabled", true).commit()
        AirPlayPersistence.saveFps(app, 60)
        AirPlayPersistence.saveHevcEnabled(app, true)
        AirPlayPersistence.saveHevcSoftwareDecoderEnabled(app, true)
        AirPlayPersistence.saveClusterMapEnabled(app, true)
        assertEquals(60, AirPlayPersistence.loadFps(app))
        assertTrue(AirPlayPersistence.loadHevcEnabled(app))
        assertTrue(AirPlayPersistence.loadHevcSoftwareDecoderEnabled(app))
        assertTrue(AirPlayPersistence.loadClusterMapEnabled(app))
    }
}

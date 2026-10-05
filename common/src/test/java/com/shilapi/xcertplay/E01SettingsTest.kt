package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33])
class E01SettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clear() {
        context.getSharedPreferences("diplay_performance", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun detectedHardwareDefaultsOnButExplicitOffSurvivesReload() {
        ShadowBuild.setHardware("mt6735")
        assertTrue(E01Settings.enabled(context))
        E01Settings.setEnabled(context, false)
        assertFalse(E01Settings.enabled(context))
    }

    @Test fun e01SuppressesSquareCanvasWithoutErasingRotationPreference() {
        E01Settings.setEnabled(context, false)
        CarPlayRotation.setEnabled(context, true)
        assertTrue(CarPlayRotation.enabled(context))
        E01Settings.setEnabled(context, true)
        assertFalse(CarPlayRotation.enabled(context))
        E01Settings.setEnabled(context, false)
        assertTrue(CarPlayRotation.enabled(context))
    }

    @Test fun modeLimitsEffectiveSettingsWithoutErasingUserChoices() {
        E01Settings.setEnabled(context, false)
        AirPlayPersistence.saveFps(context, 60)
        AirPlayPersistence.saveHevcEnabled(context, true)
        AirPlayPersistence.saveHevcSoftwareDecoderEnabled(context, true)
        AirPlayPersistence.saveClusterMapEnabled(context, true)
        E01Settings.setEnabled(context, true)
        assertEquals(30, AirPlayPersistence.loadFps(context))
        assertFalse(AirPlayPersistence.loadHevcEnabled(context))
        assertFalse(AirPlayPersistence.loadHevcSoftwareDecoderEnabled(context))
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(context))
        E01Settings.setEnabled(context, false)
        assertEquals(60, AirPlayPersistence.loadFps(context))
        assertTrue(AirPlayPersistence.loadHevcEnabled(context))
        assertTrue(AirPlayPersistence.loadHevcSoftwareDecoderEnabled(context))
        assertTrue(AirPlayPersistence.loadClusterMapEnabled(context))
    }
}

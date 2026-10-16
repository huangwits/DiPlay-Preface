package com.shilapi.xcertplay.media

import android.media.MediaRecorder
import org.junit.Assert.assertEquals
import org.junit.Test

class MicrophoneInputSourceOrderTest {
    @Test
    fun fs11CallsPreferTheStandardCommunicationSourceAndKeepTheFactoryFallback() {
        assertEquals(
            listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.CAMCORDER),
            microphoneSourceCandidates(
                factorySource = MediaRecorder.AudioSource.CAMCORDER,
                androidSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                preferAndroidPhoneSource = true,
                legacyFs11Speech = false,
            ),
        )
    }

    @Test
    fun otherVehiclesKeepTheFactoryPhoneSourceFirst() {
        assertEquals(
            listOf(MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.VOICE_COMMUNICATION),
            microphoneSourceCandidates(
                factorySource = MediaRecorder.AudioSource.CAMCORDER,
                androidSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                preferAndroidPhoneSource = false,
                legacyFs11Speech = false,
            ),
        )
    }

    @Test
    fun fs11SiriKeepsItsMicWorkaroundAndCommunicationFallback() {
        assertEquals(
            listOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            ),
            microphoneSourceCandidates(
                factorySource = null,
                androidSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
                preferAndroidPhoneSource = false,
                legacyFs11Speech = true,
            ),
        )
    }
}

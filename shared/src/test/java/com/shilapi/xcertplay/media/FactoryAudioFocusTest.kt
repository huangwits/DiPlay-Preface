package com.shilapi.xcertplay.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.After
import org.junit.Before
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowAudioManager
import com.geelyplay.preface.media.AudioStreamRoutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE, shadows = [FactoryAudioFocusTest.Requests::class])
class FactoryAudioFocusTest {
    @Before fun resetFocusRequests() { Requests.resetRequests() }
    private val tracks = mutableListOf<AudioTrack>()
    private fun track() = AudioTrack(AudioManager.STREAM_MUSIC, 48_000,
        android.media.AudioFormat.CHANNEL_OUT_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT,
        16_384, AudioTrack.MODE_STREAM).also { tracks += it }
    @After fun releaseTracks() { tracks.forEach { it.release() } }
    private val app get() = RuntimeEnvironment.getApplication()
    private val attrs get() = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    @Test fun promptsUseTheirSelectedChannelAndRestoreMusicFocusWithoutReleasingOwnership() {
        val ownership = mutableListOf<Boolean>()
        val focus = AudioFocusCoordinator(app, true, onOwnershipChanged = ownership::add)
        val music = track()
        val navigation = track()
        try {
            focus.acquire(music, AudioChannel.MEDIA, attrs, 3)
            focus.acquire(navigation, AudioChannel.NAVIGATION, attrs, 10)
            assertEquals(listOf(3 to 1, 10 to 3), Requests.values)
            assertEquals(listOf(true), ownership)
            focus.release(navigation)
            assertEquals(3 to 1, Requests.values.last())
        } finally { focus.close() }
        assertEquals(false, ownership.last())
    }
    @Test fun directNavigationDoesNotRequestFocusAndCallsRetainPriority() {
        val focus = AudioFocusCoordinator(app, true, navigationDucking = { false }, streamRoutes = AudioStreamRoutes())
        val navigation = track()
        try {
            focus.acquire(navigation, AudioChannel.NAVIGATION, attrs, 10)
            assertTrue(Requests.values.isEmpty())
            focus.setMicrophones(phone = true, assistant = false)
            assertEquals(6 to 2, Requests.values.last())
            focus.setMicrophones(phone = false, assistant = true)
            assertEquals(9 to 2, Requests.values.last())
        } finally { focus.close() }
    }
    @Test fun actualVoiceCallStreamZeroIsNotAutomaticMusic() {
        val focus = AudioFocusCoordinator(app, true)
        try {
            focus.acquire(track(), AudioChannel.PHONE, attrs, 0)
            assertEquals(0 to 2, Requests.values.single())
        } finally { focus.close() }
    }
    @Test fun factoryMixerKeepsNavigationFocusEvenInDirectMixingMode() {
        val focus = AudioFocusCoordinator(app, true, navigationDucking = { false }, navigationFactoryMixer = true)
        try {
            focus.acquire(track(), AudioChannel.NAVIGATION, attrs, 10)
            assertEquals(10 to 3, Requests.values.single())
        } finally { focus.close() }
    }
    @Implements(AudioManager::class)
    class Requests : ShadowAudioManager() {
        @Implementation override fun requestAudioFocus(listener: AudioManager.OnAudioFocusChangeListener?, stream: Int, gain: Int): Int {
            values += stream to gain
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        companion object {
            val values = mutableListOf<Pair<Int, Int>>()
            @JvmStatic @Resetter fun resetRequests() { values.clear() }
        }
    }
}

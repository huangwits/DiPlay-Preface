package com.shilapi.xcertplay.media

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.media.AudioManager
import com.geelyplay.preface.network.E01ConnectedPhones
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowAudioManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29], manifest = Config.NONE, shadows = [NativeCallAudioTest.FocusRequests::class])
class NativeCallAudioTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val manager get() = app.getSystemService(AudioManager::class.java)

    @Before fun reset() { FocusRequests.resetRequests() }

    private fun installFactoryPhone(system: Boolean = true) {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = E01ConnectedPhones.PACKAGE
            applicationInfo = ApplicationInfo().apply {
                packageName = E01ConnectedPhones.PACKAGE
                flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
            }
        })
    }

    @Test fun nativeCallStartingBetweenPollsCannotLoseFocusToNewCarPlayAudio() {
        val route = CommunicationAudioRoute(app) {}
        val focus = AudioFocusCoordinator(app, true, isExternalCall = route::externalCall)
        try {
            manager.mode = AudioManager.MODE_IN_CALL
            // No periodic setExternalCall() callback has run yet.
            focus.setMicrophones(phone = true, assistant = false)
            assertEquals(0, FocusRequests.count)
            assertFalse(focus.captureAllowed())
            assertFalse(route.acquire(null, null))
            assertEquals(AudioManager.MODE_IN_CALL, manager.mode)

            manager.mode = AudioManager.MODE_NORMAL
            focus.setExternalCall(false)
            assertEquals(1, FocusRequests.count)
            assertTrue(focus.captureAllowed())
        } finally { focus.close(); route.close() }
    }

    @Test fun factoryRingingCannotBeTakenOverByCarPlayMicrophone() {
        installFactoryPhone()
        val route = CommunicationAudioRoute(app) {}
        val focus = AudioFocusCoordinator(app, true, isExternalCall = route::externalCall)
        try {
            manager.mode = AudioManager.MODE_RINGTONE
            focus.setMicrophones(phone = true, assistant = false)
            assertTrue(route.externalCall())
            assertFalse(route.acquire(null, null))
            assertEquals(0, FocusRequests.count)
            assertEquals(AudioManager.MODE_RINGTONE, manager.mode)
        } finally { focus.close(); route.close() }
    }

    @Test fun ordinaryCarPlayRingingStillAllowsCommunication() {
        installFactoryPhone(system = false)
        val route = CommunicationAudioRoute(app) {}
        try {
            manager.mode = AudioManager.MODE_RINGTONE
            assertFalse(route.externalCall())
            assertTrue(route.acquire(null, null))
            assertEquals(AudioManager.MODE_IN_COMMUNICATION, manager.mode)
            route.release()
            assertEquals(AudioManager.MODE_RINGTONE, manager.mode)
        } finally { route.close() }
    }

    @Test fun lateAudioCallbacksCannotReacquireFocusDuringNativeCall() {
        val route = CommunicationAudioRoute(app) {}
        val focus = AudioFocusCoordinator(app, true, isExternalCall = route::externalCall)
        try {
            focus.setMicrophones(phone = true, assistant = false)
            assertEquals(1, FocusRequests.count)
            manager.mode = AudioManager.MODE_IN_CALL
            focus.onMediaPlaying(true)
            focus.setMicrophones(phone = false, assistant = true)
            assertEquals(1, FocusRequests.count)
            assertFalse(focus.captureAllowed())
            assertEquals(AudioManager.MODE_IN_CALL, manager.mode)
        } finally { focus.close(); route.close() }
    }

    @Implements(AudioManager::class)
    class FocusRequests : ShadowAudioManager() {
        @Implementation override fun requestAudioFocus(listener: AudioManager.OnAudioFocusChangeListener?, stream: Int, gain: Int): Int {
            count++
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        @Implementation(minSdk = 26) override fun requestAudioFocus(request: android.media.AudioFocusRequest): Int {
            count++
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        companion object {
            var count = 0
            @JvmStatic @Resetter fun resetRequests() { count = 0 }
        }
    }
}

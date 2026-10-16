package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.provider.Settings
import android.view.KeyEvent
import com.geelyplay.preface.media.AudioStreamRoutes
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import com.geelyplay.preface.AudioStreamPreferences
import com.geelyplay.preface.FactoryKeyAction
import com.geelyplay.preface.FactorySteeringKeys
import com.geelyplay.preface.NavigationAudioDuckingSettings
import com.geelyplay.preface.SoundRole

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class FactorySoundAndKeysTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        listOf("audio_stream_routes", "xcertplay_airplay", "vehicle_audio_routes", "factory_steering_keys").forEach {
            app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @Test fun streamsHaveIndependentDefaultsAndPreserveLegacyAuto() {
        assertEquals(AudioStreamRoutes(3, 6, 9, 10), AudioStreamPreferences.routes(app))
        AirPlayPersistence.saveMediaAudioChannel(app, 0)
        AirPlayPersistence.saveNavigationAudioChannel(app, 20)
        assertEquals(AudioStreamRoutes(-1, 6, 9, 20), AudioStreamPreferences.routes(app))
        AudioStreamPreferences.save(app, SoundRole.PHONE, 0)
        assertEquals(0, AudioStreamPreferences.routes(app).phone)
        assertEquals(-1, AudioStreamPreferences.routes(app).media)
        AudioStreamPreferences.save(app, SoundRole.PHONE, 6)
        assertEquals(6, AudioStreamPreferences.routes(app).phone)
    }
    @Test fun nativeMixerUsesSystemSettingAndOtherDevicesUseLocalPreference() {
        assertFalse(NavigationAudioDuckingSettings.usesFactoryMixer(app))
        assertNull(AudioStreamPreferences.previewFocusGain(app, SoundRole.MEDIA))
        assertEquals(2, AudioStreamPreferences.previewFocusGain(app, SoundRole.PHONE))
        assertEquals(2, AudioStreamPreferences.previewFocusGain(app, SoundRole.ASSISTANT))
        assertEquals(3, AudioStreamPreferences.previewFocusGain(app, SoundRole.NAVIGATION))
        assertTrue(NavigationAudioDuckingSettings.save(app, false))
        assertFalse(NavigationAudioDuckingSettings.load(app))
        assertNull(AudioStreamPreferences.previewFocusGain(app, SoundRole.NAVIGATION))
        val pkg = "ecarx.bluetooth.service"
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg; flags = ApplicationInfo.FLAG_SYSTEM }
        })
        assertTrue(NavigationAudioDuckingSettings.usesFactoryMixer(app))
        Settings.System.putInt(app.contentResolver, NavigationAudioDuckingSettings.SYSTEM_KEY, 1)
        assertTrue(NavigationAudioDuckingSettings.load(app))
        assertTrue(NavigationAudioDuckingSettings.save(app, false))
        assertEquals(0, Settings.System.getInt(app.contentResolver, NavigationAudioDuckingSettings.SYSTEM_KEY, -1))
        assertEquals(3, AudioStreamPreferences.previewFocusGain(app, SoundRole.NAVIGATION))
        Settings.System.putInt(app.contentResolver, NavigationAudioDuckingSettings.SYSTEM_KEY, 1)
        assertTrue(NavigationAudioDuckingSettings.load(app))
    }
    @Test fun learningMovesKeyAndDisablingDoesNotFallBackToStandardAction() {
        FactorySteeringKeys.assign(app, FactoryKeyAction.NEXT, 88)
        assertEquals(FactoryKeyAction.NEXT, FactorySteeringKeys.action(app, 88))
        assertEquals(setOf(89), FactorySteeringKeys.keys(app, FactoryKeyAction.PREVIOUS))
        FactorySteeringKeys.assign(app, FactoryKeyAction.NEXT, null)
        assertNull(FactorySteeringKeys.action(app, 88))
        val sent = mutableListOf<FactoryKeyAction>()
        assertTrue(FactorySteeringKeys.dispatch(app, KeyEvent(0, 1, 0, 88, 0), true, sent::add))
        assertTrue(sent.isEmpty())
        FactorySteeringKeys.reset(app)
        assertEquals(setOf(89, 88), FactorySteeringKeys.keys(app, FactoryKeyAction.PREVIOUS))
    }
    @Test fun foregroundAndMediaSessionShareMappingAndDeduplicateDownEvents() {
        val sent = mutableListOf<FactoryKeyAction>()
        var active = true
        val callback = CarPlayMediaCallback(send = { _, _ -> fail("Duplicate standard route") },
            active = { active }, routeKey = { FactorySteeringKeys.dispatch(app, it, true, sent::add) })
        val down = KeyEvent(1, 2, KeyEvent.ACTION_DOWN, 89, 0)
        callback.dispatchHardwareKey(down)
        callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, down))
        callback.dispatchHardwareKey(KeyEvent(1, 3, KeyEvent.ACTION_DOWN, 89, 1))
        callback.dispatchHardwareKey(KeyEvent(1, 4, KeyEvent.ACTION_UP, 89, 0))
        assertEquals(listOf(FactoryKeyAction.PREVIOUS), sent)
        active = false
        assertFalse(callback.dispatchHardwareKey(KeyEvent(2, 5, 0, 87, 0)))
        assertEquals(1, sent.size)
    }
    @Test fun protectedKeysCannotBeLearnedAndSiriCanStayWithVehicle() {
        for (key in listOf(3, 4, 24, 25, 26, 164)) assertFalse(FactorySteeringKeys.learnable(key))
        assertFalse(FactorySteeringKeys.dispatch(app, KeyEvent(0, 2, 0, 231, 0), false) { fail() })
    }

    @Test fun okTogglesFlyscreenOnceAcrossForegroundMediaAndRepeatsAndCanBeReassigned() {
        FactorySteeringKeys.reset(app)
        val sent = mutableListOf<FactoryKeyAction>()
        var active = true
        val callback = CarPlayMediaCallback(send = { _, _ -> fail("OK became a music key") },
            active = { active }, routeKey = { FactorySteeringKeys.dispatch(app, it, true, sent::add) })
        val down = KeyEvent(10, 10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)
        assertTrue(callback.dispatchHardwareKey(down))
        assertTrue(callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, down)))
        assertTrue(callback.dispatchHardwareKey(KeyEvent(10, 20, KeyEvent.ACTION_DOWN, 23, 1)))
        assertTrue(callback.dispatchHardwareKey(KeyEvent(10, 30, KeyEvent.ACTION_UP, 23, 0)))
        assertEquals(listOf(FactoryKeyAction.FLYSCREEN), sent)
        active = false
        assertFalse(callback.dispatchHardwareKey(KeyEvent(40, 40, KeyEvent.ACTION_DOWN, 23, 0)))
        FactorySteeringKeys.assign(app, FactoryKeyAction.FLYSCREEN, 66)
        assertNull(FactorySteeringKeys.action(app, 23))
        assertEquals(FactoryKeyAction.FLYSCREEN, FactorySteeringKeys.action(app, 66))
        active = true
        callback.dispatchHardwareKey(KeyEvent(50, 50, KeyEvent.ACTION_DOWN, 66, 0))
        assertEquals(2, sent.size)
        FactorySteeringKeys.reset(app)
    }
}

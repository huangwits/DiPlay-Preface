package com.geelyplay.preface

import android.content.Context
import com.geelyplay.preface.media.AudioStreamRoutes
import com.shilapi.xcertplay.VehicleAudioRole
import com.shilapi.xcertplay.VehicleAudioRoutes

internal enum class SoundRole(val title: String, val defaultStream: Int, val deviceRole: VehicleAudioRole) {
    MEDIA("音乐输出通道", 3, VehicleAudioRole.MEDIA),
    PHONE("电话输出通道", 6, VehicleAudioRole.PHONE),
    ASSISTANT("Siri 输出通道", 9, VehicleAudioRole.ASSISTANT),
    NAVIGATION("导航输出通道", 10, VehicleAudioRole.NAVIGATION),
}

internal object AudioStreamPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("audio_stream_routes", Context.MODE_PRIVATE)

    fun previewFocusGain(context: Context, role: SoundRole): Int? = when (role) {
        SoundRole.MEDIA -> null
        SoundRole.PHONE, SoundRole.ASSISTANT -> android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        SoundRole.NAVIGATION -> if (NavigationAudioDuckingSettings.usesFactoryMixer(context) ||
            NavigationAudioDuckingSettings.load(context)) android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK else null
    }

    fun load(context: Context, role: SoundRole): Int {
        val values = prefs(context)
        if (values.contains(role.name)) return values.getInt(role.name, role.defaultStream)
            .takeIf(AudioStreamRoutes::valid) ?: role.defaultStream
        val old = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)
        val key = when (role) {
            SoundRole.MEDIA -> "media_audio_channel"
            SoundRole.NAVIGATION -> if (old.contains("navigation_audio_channel")) "navigation_audio_channel" else "navigation_stream_type"
            else -> null
        }
        if (key != null && old.contains(key)) {
            val value = old.getInt(key, 0)
            return if (value in 1..20) value else AudioStreamRoutes.AUTO
        }
        return role.defaultStream
    }

    fun save(context: Context, role: SoundRole, stream: Int) {
        require(AudioStreamRoutes.valid(stream))
        prefs(context).edit().putInt(role.name, stream).apply()
        VehicleAudioRoutes.set(context, role.deviceRole, null)
        if (role == SoundRole.PHONE) VehicleAudioRoutes.set(context, VehicleAudioRole.RINGTONE, null)
    }

    fun routes(context: Context) = AudioStreamRoutes(
        load(context, SoundRole.MEDIA), load(context, SoundRole.PHONE),
        load(context, SoundRole.ASSISTANT), load(context, SoundRole.NAVIGATION),
    )
}

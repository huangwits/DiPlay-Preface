package com.geelyplay.preface.media
import com.shilapi.xcertplay.media.AudioChannel


/** Explicit legacy streams. AUTO is separate from Android's valid voice-call stream 0. */
data class AudioStreamRoutes(
    val media: Int = 3,
    val phone: Int = 6,
    val assistant: Int = 9,
    val navigation: Int = 10,
) {
    internal fun stream(channel: AudioChannel): Int = when (channel) {
        AudioChannel.MEDIA -> media
        AudioChannel.PHONE, AudioChannel.RINGTONE -> phone
        AudioChannel.ASSISTANT -> assistant
        AudioChannel.NAVIGATION -> navigation
    }

    companion object {
        const val AUTO = -1
        val selectable = (0..18).toList()
        // Keep the extended channels already supported by previous Preface versions.
        fun valid(value: Int): Boolean = value in AUTO..20
    }
}

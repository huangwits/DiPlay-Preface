package com.shilapi.xcertplay.media

/** Probe the encoder actually used by this build without opening a microphone or audio focus. */
object OpusEncoderSupport {
    private val available: Boolean by lazy {
        runCatching {
            OpusEncoder(48_000).use { encoder ->
                encoder.available && encoder.encode(ByteArray(OpusEncoder.FRAME_BYTES)).any { it.isNotEmpty() }
            }
        }.getOrDefault(false)
    }

    fun isAvailable(): Boolean = available
}

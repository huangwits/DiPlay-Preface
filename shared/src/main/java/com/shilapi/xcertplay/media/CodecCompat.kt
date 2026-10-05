package com.shilapi.xcertplay.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList

/** Decoder enumeration supported by Android 5.1 and later. */
object CodecCompat {
    fun decoders(): List<MediaCodecInfo> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList()

    fun videoDecoderFor(mime: String): MediaCodecInfo? = decoders().firstOrNull { info ->
        !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
    }
}

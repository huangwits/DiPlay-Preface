package com.shilapi.xcertplay.airplay

import android.view.KeyEvent

/** Standard Android media keys mapped to CarPlay HID; vehicle-specific keys use learned profiles. */
object CarPlayMediaButton {
    const val PLAY = 1
    const val PAUSE = 2
    const val PLAY_PAUSE = 3
    const val NEXT = 4
    const val PREVIOUS = 5

    fun opensSiri(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == KeyEvent.KEYCODE_ASSIST

    fun forKeyCode(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> NEXT
        // E01 delivers its previous-track button as MEDIA_REWIND on some firmware.
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> PREVIOUS
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> PLAY_PAUSE
        else -> null
    }
}

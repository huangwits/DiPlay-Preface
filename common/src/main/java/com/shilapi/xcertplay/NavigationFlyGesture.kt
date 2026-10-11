package com.shilapi.xcertplay

import kotlin.math.abs

/** Horizontal flying and the existing upward shortcut leave downward swipes for settings. */
internal enum class NavigationFlyGesture {
    SHOW, HIDE, TOGGLE;

    companion object {
        fun detect(dx: Float, dy: Float, threshold: Float): NavigationFlyGesture? = when {
            abs(dx) >= threshold && abs(dx) >= abs(dy) * 1.15f -> if (dx < 0) SHOW else HIDE
            -dy >= threshold && -dy >= abs(dx) * 1.15f -> TOGGLE
            else -> null
        }
    }
}

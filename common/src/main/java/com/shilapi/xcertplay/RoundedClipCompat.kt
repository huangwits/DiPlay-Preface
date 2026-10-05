package com.shilapi.xcertplay

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider

/** Rounded clipping shared by the floating map views. */
internal object RoundedClipCompat {
    fun apply(view: View, radius: Float) {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(target: View, outline: Outline) {
                outline.setRoundRect(0, 0, target.width, target.height, radius)
            }
        }
        view.clipToOutline = true
    }
}

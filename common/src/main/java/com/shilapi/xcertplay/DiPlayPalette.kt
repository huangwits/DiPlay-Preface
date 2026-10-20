package com.shilapi.xcertplay

import android.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.shilapi.xcertplay.ui.theme.LavenderDark
import com.shilapi.xcertplay.ui.theme.LavenderLight

/** Semantic colors for DiPlay-owned chrome. Media canvases and cluster-specific palettes stay separate. */
internal data class DiPlayPalette(
    val background: Int,
    val surface: Int,
    val button: Int,
    val outline: Int,
    val railSelected: Int,
    val railSelectedOutline: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val accent: Int,
    val onAccent: Int,
    val accentTrack: Int,
    val trackOff: Int,
    val warning: Int,
    val success: Int,
    val danger: Int,
    val ripple: Int,
    val focusRing: Int,
    val systemBar: Int,
    val systemBarIconsAreDark: Boolean,
    val overlayBackground: Int,
    val overlayPrimaryText: Int,
    val overlaySecondaryText: Int,
    val overlayAccent: Int,
    val overlayOnAccent: Int,
    val overlayAccentTrack: Int,
    val overlayTrackOff: Int,
    val overlayDanger: Int,
) {
    companion object {
        fun of(night: Boolean): DiPlayPalette = if (night) DARK else LIGHT

        private val previousDark = DiPlayPalette(
            background = Color.rgb(12, 17, 27),
            surface = Color.rgb(21, 30, 44),
            button = Color.rgb(31, 43, 61),
            outline = Color.rgb(42, 56, 75),
            railSelected = Color.rgb(24, 54, 92),
            railSelectedOutline = Color.rgb(42, 82, 130),
            primaryText = Color.rgb(241, 245, 252),
            secondaryText = Color.rgb(168, 182, 202),
            accent = Color.rgb(166, 200, 255),
            onAccent = Color.rgb(12, 17, 27),
            accentTrack = Color.rgb(50, 88, 140),
            trackOff = Color.rgb(42, 56, 75),
            warning = Color.rgb(255, 196, 128),
            success = Color.rgb(127, 205, 154),
            danger = Color.rgb(190, 45, 45),
            ripple = 0x336F9FD9,
            focusRing = Color.rgb(166, 200, 255),
            systemBar = Color.rgb(12, 17, 27),
            systemBarIconsAreDark = false,
            overlayBackground = Color.rgb(12, 17, 27),
            overlayPrimaryText = Color.rgb(241, 245, 252),
            overlaySecondaryText = Color.rgb(168, 182, 202),
            overlayAccent = Color.rgb(166, 200, 255),
            overlayOnAccent = Color.rgb(12, 17, 27),
            overlayAccentTrack = Color.rgb(50, 88, 140),
            overlayTrackOff = Color.rgb(42, 56, 75),
            overlayDanger = Color.rgb(190, 45, 45),
        )

        private val previousLight = DiPlayPalette(
            background = Color.rgb(233, 238, 246),
            surface = Color.rgb(248, 250, 253),
            button = Color.rgb(220, 229, 241),
            outline = Color.rgb(167, 181, 199),
            railSelected = Color.rgb(215, 229, 247),
            railSelectedOutline = Color.rgb(145, 173, 208),
            primaryText = Color.rgb(28, 28, 30),
            secondaryText = Color.rgb(90, 100, 116),
            accent = Color.rgb(36, 90, 156),
            onAccent = Color.WHITE,
            accentTrack = Color.rgb(36, 90, 156),
            trackOff = Color.rgb(167, 181, 199),
            warning = Color.rgb(122, 69, 0),
            success = Color.rgb(40, 107, 63),
            danger = Color.rgb(155, 48, 48),
            ripple = 0x33245A9C,
            focusRing = Color.rgb(36, 90, 156),
            systemBar = Color.rgb(233, 238, 246),
            systemBarIconsAreDark = true,
            overlayBackground = Color.rgb(244, 247, 250),
            overlayPrimaryText = Color.rgb(28, 28, 30),
            overlaySecondaryText = Color.rgb(75, 86, 101),
            overlayAccent = Color.rgb(35, 100, 58),
            overlayOnAccent = Color.WHITE,
            overlayAccentTrack = Color.rgb(35, 100, 58),
            overlayTrackOff = Color.rgb(168, 180, 190),
            overlayDanger = Color.rgb(155, 48, 48),
        )

        private fun material(base: DiPlayPalette, scheme: ColorScheme) = base.copy(
            background = scheme.surface.toArgb(), surface = scheme.surface.toArgb(),
            button = scheme.surfaceContainerHigh.toArgb(), outline = scheme.outlineVariant.toArgb(),
            railSelected = scheme.secondaryContainer.toArgb(), railSelectedOutline = scheme.outline.toArgb(),
            primaryText = scheme.onSurface.toArgb(), secondaryText = scheme.onSurfaceVariant.toArgb(),
            accent = scheme.primary.toArgb(), onAccent = scheme.onPrimary.toArgb(),
            accentTrack = scheme.primary.toArgb(), trackOff = scheme.outlineVariant.toArgb(),
            ripple = (scheme.primary.toArgb() and 0x00ffffff) or 0x33000000,
            danger = scheme.error.toArgb(), focusRing = scheme.primary.toArgb(), systemBar = scheme.surface.toArgb(),
            overlayBackground = scheme.surfaceContainer.toArgb(), overlayPrimaryText = scheme.onSurface.toArgb(),
            overlaySecondaryText = scheme.onSurfaceVariant.toArgb(), overlayAccent = scheme.primary.toArgb(),
            overlayOnAccent = scheme.onPrimary.toArgb(), overlayAccentTrack = scheme.primary.toArgb(),
            overlayTrackOff = scheme.outlineVariant.toArgb(), overlayDanger = scheme.error.toArgb(),
        )
        val DARK = material(previousDark, LavenderDark)
        val LIGHT = material(previousLight, LavenderLight)
    }
}

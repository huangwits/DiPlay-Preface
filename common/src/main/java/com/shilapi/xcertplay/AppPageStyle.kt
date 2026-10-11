package com.shilapi.xcertplay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button
import com.shilapi.xcertplay.settings.SettingsTheme
import kotlin.math.roundToInt

/** Shared DiPlay page surfaces and action hierarchy, independent of the projected CarPlay theme. */
internal object AppPageStyle {
    val background = Color.rgb(12, 17, 27)
    val surface = Color.rgb(21, 30, 44)
    val button = Color.rgb(31, 43, 61)
    val border = Color.rgb(42, 56, 75)
    val accent = SettingsTheme.CARD.accent
    val text = SettingsTheme.CARD.textPrimary
    val muted = SettingsTheme.CARD.textSecondary

    fun card(context: Context) = rounded(context, surface, border)
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    private fun rounded(context: Context, color: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, 20).toFloat(); setStroke(dp(context, 1), stroke)
    }
    fun action(view: Button, primary: Boolean = false) {
        view.apply {
            isAllCaps = false; textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            backgroundTintList = null
            val fill = StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled), rounded(context, button, border))
                addState(intArrayOf(), rounded(context, if (primary) accent else button, if (primary) accent else border))
            }
            background = RippleDrawable(ColorStateList.valueOf(0x336F9FD9), fill, null)
            setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(muted, if (primary) AppPageStyle.background else AppPageStyle.text)))
            minHeight = dp(context, 48); minimumHeight = dp(context, 48)
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
            stateListAnimator = null
        }
    }
}

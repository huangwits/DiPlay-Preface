package com.shilapi.xcertplay.license

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.WaitingScreenColors
import com.shilapi.xcertplay.host.R

/** Owner-provided QQ artwork, cropped without regenerating its code. No network or QQ launch. */
internal class CommunityCard(context: Context) : LinearLayout(context) {
    private val caption = label("QQ群 $GROUP_NUMBER\nQQ 扫码 · 点图放大", 14f).apply {
        tag = "community-caption"; gravity = Gravity.CENTER
    }
    private val thumbnail = ImageView(context).apply {
        setImageResource(R.drawable.preface_qq_group)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "QQ 群二维码，点按放大"
        isFocusable = true
        setOnClickListener { showQr() }
    }
    private var dialog: Dialog? = null

    init {
        tag = "license-community-card"
        orientation = VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        gravity = Gravity.CENTER_HORIZONTAL
        addView(thumbnail, LayoutParams(-1, dp(160)))
        addView(caption, LayoutParams(-1, -2).apply { topMargin = dp(8) })
        applyTheme(true)
    }

    fun applyTheme(night: Boolean) {
        val colors = WaitingScreenColors.of(night)
        background = GradientDrawable().apply {
            setColor(if (night) com.shilapi.xcertplay.AppPageStyle.button else Color.rgb(238, 244, 252))
            cornerRadius = dp(12).toFloat()
        }
        caption.setTextColor(colors.secondary)
    }

    private fun showQr() {
        if (dialog != null) return
        val viewer = Dialog(context).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val root = LinearLayout(context).apply {
            orientation = VERTICAL; setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(12)); tag = "community-qr-dialog"
        }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("QQ 扫码加群 · $GROUP_NUMBER", 16f, true).apply { setTextColor(Color.BLACK) }, LayoutParams(0, -2, 1f))
        header.addView(Button(context).apply {
            text = "关闭"; isAllCaps = false; setTextColor(Color.BLACK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(225, 234, 246))
            setOnClickListener { viewer.dismiss() }
        }, LayoutParams(-2, dp(48)))
        root.addView(header, LayoutParams(-1, -2))
        root.addView(ImageView(context).apply {
            setImageResource(R.drawable.preface_qq_group)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "QQ 群完整二维码，群号 $GROUP_NUMBER"
        }, LayoutParams(-1, 0, 1f))
        viewer.setContentView(root)
        viewer.setOnDismissListener { if (dialog === viewer) dialog = null }
        dialog = viewer
        viewer.show()
        viewer.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    fun closeViewer() { dialog?.dismiss(); dialog = null }
    internal fun fitThumbnail(height: Int) {
        if (thumbnail.layoutParams.height != height) thumbnail.layoutParams = thumbnail.layoutParams.apply { this.height = height }
    }
    override fun onDetachedFromWindow() { closeViewer(); super.onDetachedFromWindow() }
    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setPadding(0, 0, 0, dp(6))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { const val GROUP_NUMBER = "892351951" }
}

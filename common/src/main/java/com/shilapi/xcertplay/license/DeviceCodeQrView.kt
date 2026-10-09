package com.shilapi.xcertplay.license

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Local text QR: exact existing device code, with a four-module quiet zone and crisp modules. */
internal class DeviceCodeQrView(context: Context) : View(context) {
    private var modules: BitMatrix? = null
    private val ink = Paint().apply { color = Color.BLACK; isAntiAlias = false }

    init {
        contentDescription = "设备码二维码，用手机扫一扫后复制结果"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setBackgroundColor(Color.WHITE)
    }

    fun setDeviceCode(value: String) {
        require(Regex("DP-DEVICE1-[0-9a-fA-F]{64}").matches(value))
        contentDescription = "设备码二维码，用手机扫一扫后复制结果"
        setContent(value)
    }

    fun setWebAddress(value: String) {
        require(value.startsWith("http://") && value.length <= 512)
        contentDescription = "手机协助激活二维码，用手机扫码打开网页"
        setContent(value)
    }

    private fun setContent(value: String) {
        modules = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 0, 0, mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
        ))
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightDp = resources.configuration.screenHeightDp
        val edgeDp = if (heightDp > 0) minOf(260, (heightDp - 80).coerceAtLeast(160)) else 260
        val desired = (edgeDp * resources.displayMetrics.density).toInt()
        val edge = minOf(resolveSize(desired, widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
        setMeasuredDimension(edge, edge)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val grid = modules ?: return
        val scale = minOf(width / grid.width, height / grid.height)
        if (scale < 1) return
        val left = (width - grid.width * scale) / 2
        val top = (height - grid.height * scale) / 2
        for (y in 0 until grid.height) for (x in 0 until grid.width) {
            if (grid[x, y]) canvas.drawRect((left + x * scale).toFloat(), (top + y * scale).toFloat(),
                (left + (x + 1) * scale).toFloat(), (top + (y + 1) * scale).toFloat(), ink)
        }
    }
}

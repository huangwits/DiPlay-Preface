package com.shilapi.xcertplay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.roundToInt

/** Full-width connection controls; USB explicitly opts into the diagnostic panel. */
internal class ConnectionWaitingView(context: Context, private var showLogPanel: Boolean = false,
    statusTitle: String = "连接状态", private val preparation: Boolean = false) : FrameLayout(context) {
    val columns = LinearLayout(context)
    val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val controlScroll = ScrollView(context).apply { isFillViewport = true; addView(controls) }
    val logPanel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val stage = text("正在准备 CarPlay", 20f)
    val instructions = text("", 14f)
    val confirmed = text("", 13f).apply { visibility = GONE }
    val failure = text("", 13f).apply { visibility = GONE; setTextIsSelectable(true) }
    val retry = action("重试连接")
    val settings = action("CarPlay 设置")
    val recovery = action("重置 CarPlay Wi-Fi").apply { visibility = GONE }
    val back = action("返回 DiPlay")
    val gestureHint = text("", 12f)
    val logText = text("等待连接日志…", 13f).apply {
        typeface = Typeface.MONOSPACE
        gravity = Gravity.TOP or Gravity.START
        setTextIsSelectable(true)
        setPadding(dp(12), dp(8), dp(12), dp(12))
    }
    val logScroll = ScrollView(context).apply {
        isFillViewport = true
        addView(logText, LayoutParams(-1, -2))
    }
    val title = text("DiPlay", 26f)
    private val carplayIcon = ImageView(context).apply {
        setImageResource(com.shilapi.xcertplay.host.R.drawable.ic_carplay)
        contentDescription = "CarPlay"
        visibility = GONE
    }
    private val statusLabel = text(statusTitle, 13f)
    val logTitle = text("实时连接日志", 16f)
    private val follow = action("跟随最新")
    private val copy = action("复制日志")
    private val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val extraButtons = mutableListOf<Button>()
    private val extraLabels = mutableListOf<TextView>()
    private val extraInputs = mutableListOf<EditText>()
    private var themedNight = false
    private var appStyle = false
    private var primaryAction: Button? = null
    private var following = true
    private var positioning = false
    private var renderedLogs = ""
    private var wide: Boolean? = null
    private var compact: Boolean? = null
    private var layoutWidth = -1
    private var layoutHeight = -1

    init {
        isClickable = true
        controls.setPadding(dp(16), dp(12), dp(16), dp(12))
        controls.addView(carplayIcon, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(12) })
        for (view in listOf(title, statusLabel, stage, instructions, confirmed, failure,
            retry, settings, recovery, back, gestureHint)) {
            controls.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        failure.setPadding(dp(10), dp(10), dp(10), dp(10))
        header.setPadding(dp(12), 0, dp(8), 0)
        header.addView(logTitle, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(follow, LinearLayout.LayoutParams(-2, dp(48)))
        header.addView(copy, LinearLayout.LayoutParams(-2, dp(48)))
        logPanel.addView(header, LinearLayout.LayoutParams(-1, -2))
        logPanel.addView(logScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        columns.addView(controlScroll)
        if (showLogPanel) columns.addView(logPanel)
        addView(columns, LayoutParams(-1, -1))
        logScroll.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                following = false
                follow.isSelected = false
            }
            false
        }
        logScroll.viewTreeObserver.addOnScrollChangedListener {
            if (!positioning) {
                following = !logScroll.canScrollVertically(1)
                follow.isSelected = following
            }
        }
        follow.setOnClickListener { following = true; follow.isSelected = true; scrollToLatest() }
        logText.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scrollToLatest() }
        logScroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scrollToLatest() }
        follow.isSelected = true
        copy.setOnClickListener {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("DiPlay 连接日志", renderedLogs))
            Toast.makeText(context, "已复制连接日志", Toast.LENGTH_SHORT).show()
        }
        applyTheme(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val height = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val landscape = width >= dp(600) && width > height
        val short = height < dp(420)
        val centered = preparation && !showLogPanel
        if (wide != landscape || compact != short) {
            wide = landscape; compact = short
            columns.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            title.textSize = if (short) 22f else 26f
            stage.textSize = if (short) 18f else 20f
            gestureHint.visibility = if (short) GONE else VISIBLE
        }
        val leftWidth = (width * .34f).roundToInt().coerceIn(dp(240), dp(400))
        if (layoutWidth != width || layoutHeight != height) {
            layoutWidth = width; layoutHeight = height
            controls.gravity = if (centered) Gravity.CENTER else Gravity.TOP or Gravity.START
            carplayIcon.visibility = if (centered) VISIBLE else GONE
            statusLabel.visibility = if (centered || appStyle) GONE else VISIBLE
            val contentWidth = if (centered) minOf(dp(560), (width - dp(32)).coerceAtLeast(1)) else -1
            for (i in 0 until controls.childCount) {
                val child = controls.getChildAt(i)
                val params = child.layoutParams as LinearLayout.LayoutParams
                if (child === carplayIcon) {
                    params.width = dp(if (short) 48 else 72)
                    params.height = params.width
                } else params.width = if (centered && child is Button) minOf(dp(300), contentWidth) else contentWidth
                child.layoutParams = params
                if (child is TextView && child !is Button)
                    child.gravity = if (centered) Gravity.CENTER else Gravity.CENTER_VERTICAL
            }
            if (!showLogPanel) {
                controlScroll.layoutParams = LinearLayout.LayoutParams(-1, -1)
            } else if (landscape) {
                controlScroll.layoutParams = LinearLayout.LayoutParams(leftWidth, -1)
                logPanel.layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(8) }
            } else {
                controlScroll.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
                logPanel.layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun showConnection(wireless: Boolean, phoneName: String, factory: Boolean) {
        val heading = if (wireless) "连接 $phoneName" else "USB CarPlay"
        val help = when {
            !wireless -> "请用 USB 数据线连接并解锁 iPhone，允许手机上的信任提示。"
            factory -> "保持车机电话和音乐已连接，开启 iPhone 的蓝牙和 Wi-Fi。"
            else -> "保持 iPhone 靠近车机，开启蓝牙和 Wi-Fi，并允许手机上的 CarPlay 提示。"
        }
        if (title.text.toString() != heading) title.text = heading
        if (instructions.text.toString() != help) instructions.text = help
        setLogPanelVisible(!wireless)
    }

    fun showLogs(value: String) {
        if (!showLogPanel) return
        if (renderedLogs == value) return
        renderedLogs = value
        logText.text = value.ifEmpty { "等待连接日志…" }
        if (following) scrollToLatest()
    }

    fun setLogPanelVisible(visible: Boolean) {
        if (showLogPanel == visible) return
        showLogPanel = visible
        if (visible) columns.addView(logPanel) else {
            columns.removeView(logPanel)
            renderedLogs = ""
            logText.text = "等待连接日志…"
        }
        layoutWidth = -1
        layoutHeight = -1
        requestLayout()
    }

    fun scrollToLatest() {
        if (!following) return
        positioning = true
        logScroll.scrollTo(0, (logText.height - logScroll.height).coerceAtLeast(0))
        positioning = false
    }

    fun showFailure(message: String) {
        failure.text = "最近一次失败：\n$message"
        failure.visibility = VISIBLE
        retry.visibility = VISIBLE
    }

    fun addControlAction(label: String): Button = action(label).also {
        extraButtons += it
        controls.addView(it, controls.indexOfChild(gestureHint), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        applyTheme(themedNight)
    }

    fun addControlText(label: String, size: Float = 13f): TextView = text(label, size).also {
        extraLabels += it
        controls.addView(it, controls.indexOfChild(gestureHint), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        applyTheme(themedNight)
    }

    fun addPortInput(label: String, value: String): EditText {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val caption = text(label, 14f)
        extraLabels += caption
        row.addView(caption, LinearLayout.LayoutParams(0, -2, 1f))
        val input = EditText(context).apply {
            setText(value); textSize = 14f; inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true); contentDescription = label; minHeight = dp(48)
        }
        extraInputs += input
        row.addView(input, LinearLayout.LayoutParams(dp(96), -2))
        controls.addView(row, controls.indexOfChild(retry), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        applyTheme(themedNight)
        return input
    }

    fun useAppStyle() {
        appStyle = true
        statusLabel.visibility = GONE
        controls.background = AppPageStyle.card(context)
        controls.setPadding(dp(16), dp(16), dp(16), dp(16))
        title.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        applyTheme(true)
    }

    fun setPrimaryAction(button: Button?) {
        if (primaryAction === button) return
        primaryAction = button
        if (appStyle) extraButtons.forEach { AppPageStyle.action(it, it === button) }
    }

    fun applyTheme(night: Boolean) {
        if (appStyle && !night) { applyTheme(true); return }
        themedNight = night
        val palette = WaitingScreenColors.of(night)
        setBackgroundColor(palette.background)
        listOf(title, stage, logTitle, logText).forEach { it.setTextColor(palette.text) }
        (listOf(statusLabel, instructions, gestureHint) + extraLabels).forEach { it.setTextColor(palette.secondary) }
        confirmed.setTextColor(if (night) Color.rgb(144, 220, 171) else Color.rgb(24, 113, 63))
        failure.setTextColor(if (night) Color.rgb(255, 205, 205) else Color.rgb(158, 25, 35))
        failure.background = rounded(if (night) Color.rgb(72, 28, 35) else Color.rgb(255, 230, 231))
        extraInputs.forEach {
            it.setTextColor(palette.text)
            it.setHintTextColor(palette.secondary)
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(palette.secondary)
        }
        logPanel.background = rounded(if (night) Color.rgb(19, 28, 42) else Color.WHITE)
        (listOf(retry, settings, recovery, back, copy, follow) + extraButtons).forEach {
            it.setTextColor(palette.text)
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (night) Color.rgb(42, 56, 77) else Color.rgb(213, 225, 243))
        }
        if (appStyle) extraButtons.forEach { AppPageStyle.action(it, it === primaryAction) }
    }

    private fun action(label: String) = Button(context).apply {
        text = label; textSize = 14f; isAllCaps = false
        minHeight = dp(48); minimumHeight = dp(48)
    }
    private fun text(value: String, size: Float) = TextView(context).apply {
        text = value; textSize = size; gravity = Gravity.CENTER_VERTICAL
    }
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(12).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

package com.shilapi.xcertplay.license

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

internal interface LicensePanelClient {
    val configured: Boolean
    val requested: Boolean
    val valid: Boolean
    val requestId: String
    val activated: Boolean get() = false
    fun refresh(request: Boolean): OnlineLicense.Result
}

private class OnlinePanelClient(private val context: Context) : LicensePanelClient {
    override val configured get() = OnlineLicense.configured(context)
    override val requested get() = OnlineLicense.wasRequested(context) || OnlineLicense.wasActivated(context)
    override val valid get() = OnlineLicense.enabled(context) && OnlineLicense.canStart(context)
    override val requestId get() = OnlineLicense.requestLabel(context)
    override val activated get() = OnlineLicense.wasActivated(context)
    override fun refresh(request: Boolean) = OnlineLicense.refresh(context, request)
}

/** The host owns foreground polling; detached/stopped panels cannot publish late results. */
internal enum class LicensePanelState { REQUIRED, CHECKING, PENDING, AUTHORIZED, ERROR }

internal class LicensePanel(
    context: Context,
    private val onContinue: () -> Unit,
    private val client: LicensePanelClient = OnlinePanelClient(context.applicationContext),
    private val onStateChanged: () -> Unit = {},
    showConnectionAction: Boolean = true,
) : ScrollView(context) {
    private var previouslyApproved = client.activated || client.valid
    var state = when {
        client.valid -> LicensePanelState.AUTHORIZED
        client.activated && client.configured -> LicensePanelState.CHECKING
        else -> LicensePanelState.REQUIRED
    }
        private set
    val needsAttention get() = state != LicensePanelState.AUTHORIZED &&
        !(state == LicensePanelState.CHECKING && previouslyApproved)
    val admissionMessage get() = when (state) {
        LicensePanelState.AUTHORIZED -> "授权已通过，可以连接 CarPlay。"
        LicensePanelState.CHECKING -> if (previouslyApproved) "正在核验已有授权，请稍候…" else "正在查询授权，请稍候…"
        LicensePanelState.PENDING -> "申请已提交，等待管理员批准。"
        LicensePanelState.ERROR -> "授权校验未通过，请查看右侧提示并重试。"
        LicensePanelState.REQUIRED -> "首次使用请在右侧申请激活。"
    }
    private val handler = Handler(Looper.getMainLooper())
    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val networkActions = mutableListOf<Button>()
    private val message: TextView
    private val identifier: TextView
    private val continueButton: Button
    private val labels = mutableListOf<Pair<TextView, Boolean>>()
    private val buttons = mutableListOf<Button>()
    private lateinit var community: CommunityCard
    private var foreground = false
    private var working = false
    private var generation = 0
    private var pollUntil = 0L
    private val poll = Runnable {
        if (SystemClock.elapsedRealtime() < pollUntil) checkLicense(false)
        else showPollingStopped()
    }
    private val primary = Color.rgb(241, 245, 252)
    private val secondary = Color.rgb(168, 182, 202)

    init {
        tag = "online-license-panel"
        isFillViewport = true
        background = GradientDrawable().apply { setColor(Color.rgb(22, 30, 43)); cornerRadius = dp(18).toFloat() }
        column.setPadding(dp(20), dp(20), dp(20), dp(20))
        addView(column, LayoutParams(-1, -2))
        text("软件授权", 24f, true)
        text("首次申请：点击申请激活 → 联系管理员核对申请号 → 等待批准。", 15f)
        message = text(if (client.configured) "申请激活或刷新授权。" else "授权预览版：未配置服务，暂不可激活。", 16f)
        message.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        identifier = text("申请号：${client.requestId}", 20f, true).apply { setTextIsSelectable(true) }
        button("申请激活", true) { restartPolling(true) }
        button("刷新状态", true) { restartPolling(false) }
        continueButton = button("连接 CarPlay") { onContinue() }.apply {
            isEnabled = client.valid
            visibility = if (showConnectionAction) VISIBLE else GONE
        }
        community = CommunityCard(context)
        column.addView(community, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(14) })
        text("每分钟查询一次，也可手动刷新。离开页面或等待 20 分钟后停止。", 12f)
        text("已激活的设备下次会自动核验，无需重新申请。网络异常时可重试；授权到期或被停用时请联系管理员。", 12f)
        applyTheme(true)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, bold: Boolean = false): TextView = TextView(context).apply {
        text = value; textSize = size; setTextColor(if (bold) primary else secondary)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(14))
        column.addView(this, LinearLayout.LayoutParams(-1, -2))
        labels += this to bold
    }
    private fun button(label: String, network: Boolean = false, action: () -> Unit): Button = Button(context).apply {
        text = label; textSize = 16f; isAllCaps = false; minHeight = dp(48)
        setTextColor(primary)
        background = GradientDrawable().apply { setColor(Color.rgb(42, 56, 75)); cornerRadius = dp(12).toFloat() }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { action() }
        column.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        buttons += this
        if (network) { networkActions += this; isEnabled = client.configured }
    }

    fun start() {
        if (foreground) return
        foreground = true
        if (client.valid) {
            previouslyApproved = true
            setState(LicensePanelState.AUTHORIZED)
            message.text = "已授权，可以连接 CarPlay。"
            continueButton.isEnabled = true
        } else if (client.configured && client.requested) restartPolling(false)
        else { setState(LicensePanelState.REQUIRED); continueButton.isEnabled = false }
    }

    fun stop() {
        foreground = false; generation++; working = false
        handler.removeCallbacks(poll)
        community.closeViewer()
        networkActions.forEach { it.isEnabled = client.configured }
    }

    fun applyTheme(night: Boolean) {
        val palette = com.shilapi.xcertplay.WaitingScreenColors.of(night)
        background = GradientDrawable().apply {
            setColor(if (night) Color.rgb(22, 30, 43) else Color.WHITE); cornerRadius = dp(18).toFloat()
        }
        labels.forEach { (view, bold) -> view.setTextColor(if (bold) palette.text else palette.secondary) }
        buttons.forEach { view ->
            view.setTextColor(android.content.res.ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(palette.secondary, palette.text)))
            view.background = GradientDrawable().apply {
                setColor(if (night) Color.rgb(42, 56, 75) else Color.rgb(213, 225, 243)); cornerRadius = dp(12).toFloat()
            }
        }
        community.applyTheme(night)
    }

    fun refreshAdmission() {
        if (working) return
        if (client.valid) {
            previouslyApproved = true
            continueButton.isEnabled = true
            setState(LicensePanelState.AUTHORIZED)
        } else if (state == LicensePanelState.AUTHORIZED) {
            continueButton.isEnabled = false
            if (foreground && client.configured) restartPolling(false)
            else {
                setState(LicensePanelState.ERROR)
                message.text = "请联网刷新已有授权，无需重新申请。"
            }
        }
    }

    private fun setState(next: LicensePanelState) {
        if (state == next) return
        state = next
        onStateChanged()
    }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    private fun showPollingStopped() {
        setState(LicensePanelState.ERROR)
        message.text = "已停止查询，点击刷新状态可继续。"
    }

    private fun restartPolling(request: Boolean) {
        if (working || !foreground) return
        pollUntil = SystemClock.elapsedRealtime() + 20 * 60 * 1000
        checkLicense(request)
    }

    private fun checkLicense(request: Boolean) {
        if (working || !foreground || !client.configured) return
        handler.removeCallbacks(poll)
        working = true; networkActions.forEach { it.isEnabled = false }
        continueButton.isEnabled = false
        setState(LicensePanelState.CHECKING)
        val current = generation
        message.text = if (request) "正在提交申请…" else "正在联网校验…"
        Thread({
            val result = runCatching { client.refresh(request) }
            handler.post {
                if (!foreground || generation != current) return@post
                working = false; networkActions.forEach { it.isEnabled = true }
                result.fold(onSuccess = {
                    identifier.text = "申请号：${it.requestId}"
                    continueButton.isEnabled = it.approved
                    if (it.approved) {
                        previouslyApproved = true
                        message.text = "已授权，可以连接 CarPlay。"
                        setState(LicensePanelState.AUTHORIZED)
                    }
                    else if (SystemClock.elapsedRealtime() < pollUntil) {
                        message.text = "等待批准，每分钟查询一次；可手动刷新。"
                        setState(LicensePanelState.PENDING)
                        handler.postDelayed(poll, 60_000)
                    } else showPollingStopped()
                }, onFailure = {
                    message.text = it.message ?: "授权校验失败，请检查网络后重试"
                    setState(LicensePanelState.ERROR)
                })
            }
        }, "diplay-license").start()
    }
}

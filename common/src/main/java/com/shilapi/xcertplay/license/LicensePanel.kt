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
    private val compactWhenApproved: Boolean = false,
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
    private var networkRow: LinearLayout? = null
    private val heading: TextView
    private val applicationHelp: TextView
    private val footnotes = mutableListOf<TextView>()
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
        column.setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(column, LayoutParams(-1, -2))
        heading = text("软件授权", 22f, true)
        text("授权免费，审核仅用于防止滥用，不收取激活费用。", 14f).tag = "license-free-notice"
        applicationHelp = text("申请激活后，联系管理员核对申请号并等待批准。", 14f)
        message = text(if (client.configured) "申请激活或刷新授权。" else "授权预览版：未配置服务，暂不可激活。", 16f)
        message.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        identifier = text("申请号：${client.requestId}", 16f, true).apply { setTextIsSelectable(true) }
        button("申请激活", true) { restartPolling(true) }
        button("刷新状态", true) { restartPolling(false) }
        if (compactWhenApproved) {
            val row = LinearLayout(context).also { networkRow = it }
            networkActions.forEachIndexed { index, view ->
                column.removeView(view)
                row.addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin = dp(8) })
            }
            column.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        continueButton = button("连接 CarPlay") { onContinue() }.apply {
            isEnabled = client.valid
            visibility = if (showConnectionAction) VISIBLE else GONE
        }
        community = CommunityCard(context)
        column.addView(community, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(14) })
        footnotes += text("每分钟查询一次，也可手动刷新。离开页面或等待 20 分钟后停止。", 12f)
        footnotes += text("已激活的设备下次会自动核验，无需重新申请。网络异常时可重试；授权到期或被停用时请联系管理员。", 12f)
        applyTheme(true)
        applyAuthorizedPresentation()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        community.fitThumbnail(dp(160))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (compactWhenApproved && state == LicensePanelState.AUTHORIZED && measuredHeight > 0) {
            val overflow = (column.measuredHeight - measuredHeight).coerceAtLeast(0)
            if (overflow > 0) {
                community.fitThumbnail((dp(160) - overflow).coerceAtLeast(dp(96)))
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }
    private fun text(value: String, size: Float, bold: Boolean = false): TextView = TextView(context).apply {
        text = value; textSize = size; setTextColor(if (bold) primary else secondary)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(10))
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
            message.text = if (compactWhenApproved) "可连接 CarPlay，也可扫码加入交流群。" else "已授权，可以连接 CarPlay。"
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
        background = com.shilapi.xcertplay.AppPageStyle.card(context)
        labels.forEach { (view, bold) -> view.setTextColor(if (bold) palette.text else palette.secondary) }
        buttons.forEach { com.shilapi.xcertplay.AppPageStyle.action(it, it === networkActions.firstOrNull()) }
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
        applyAuthorizedPresentation()
        onStateChanged()
    }

    private fun applyAuthorizedPresentation() {
        if (!compactWhenApproved) return
        val approved = state == LicensePanelState.AUTHORIZED
        networkActions.forEach { it.visibility = if (approved) GONE else VISIBLE }
        networkRow?.visibility = if (approved) GONE else VISIBLE
        identifier.visibility = if (approved) GONE else VISIBLE
        applicationHelp.visibility = if (approved) GONE else VISIBLE
        message.visibility = if (approved) GONE else VISIBLE
        footnotes.forEach { it.visibility = if (approved) GONE else VISIBLE }
        heading.text = if (approved) "授权已通过" else "软件授权"
        if (approved) message.text = "可连接 CarPlay，也可扫码加入交流群。"
        scrollTo(0, 0)
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
                        message.text = if (compactWhenApproved) "可连接 CarPlay，也可扫码加入交流群。" else "已授权，可以连接 CarPlay。"
                        setState(LicensePanelState.AUTHORIZED)
                    }
                    else if (SystemClock.elapsedRealtime() < pollUntil) {
                        message.text = "等待批准，每分钟查询一次；可手动刷新。"
                        setState(LicensePanelState.PENDING)
                        handler.postDelayed(poll, 60_000)
                    } else showPollingStopped()
                }, onFailure = {
                    message.text = LicenseFailureMessage.describe(it)
                    setState(LicensePanelState.ERROR)
                })
            }
        }, "diplay-license").start()
    }
}

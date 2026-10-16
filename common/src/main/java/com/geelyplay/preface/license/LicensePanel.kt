package com.geelyplay.preface.license

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
    private val actionHeightDp: Int = 48,
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
        LicensePanelState.AUTHORIZED -> "无线 CarPlay 已激活，可以连接。"
        LicensePanelState.CHECKING -> if (previouslyApproved) "已激活，正在自动核验…" else "正在核验激活状态，请稍候…"
        LicensePanelState.PENDING -> "激活申请已提交，等待审核。"
        LicensePanelState.ERROR -> "激活核验未通过，请查看提示后重试。"
        LicensePanelState.REQUIRED -> "无线 CarPlay 未激活。"
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
    private var pendingAction: (() -> Unit)? = null
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
        heading = text("无线 CarPlay", 22f, true).apply { visibility = if (compactWhenApproved) GONE else VISIBLE }
        text("激活免费，审核仅用于防止滥用。", 14f).tag = "license-free-notice"
        applicationHelp = text("提交申请后，将申请号发给管理员。审核通过后即可连接。", 14f)
        message = text(if (client.configured) "" else "激活服务尚未配置，当前不能申请激活。", 14f)
        message.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        identifier = text("申请号：${client.requestId}", 14f, true).apply { setTextIsSelectable(true) }
        button("申请激活", true) { restartPolling(true) }
        button("刷新状态", true) { restartPolling(false) }
        if (compactWhenApproved) {
            val row = LinearLayout(context).also { networkRow = it }
            networkActions.forEachIndexed { index, view ->
                column.removeView(view)
                row.addView(view, LinearLayout.LayoutParams(0, dp(actionHeightDp), 1f).apply { if (index > 0) leftMargin = dp(8) })
            }
            column.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        continueButton = button("连接 CarPlay") { onContinue() }.apply {
            isEnabled = client.valid
            visibility = if (showConnectionAction) VISIBLE else GONE
        }
        community = CommunityCard(context)
        column.addView(community, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(14) })
        footnotes += text("每分钟查询一次，也可手动刷新。离开页面或等待 20 分钟后停止。", 14f)
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
        column.addView(this, LinearLayout.LayoutParams(-1, dp(actionHeightDp)).apply { bottomMargin = dp(10) })
        buttons += this
        if (network) { networkActions += this; isEnabled = client.configured }
    }

    fun start() {
        if (foreground) return
        foreground = true
        if (client.valid) {
            previouslyApproved = true
            setState(LicensePanelState.AUTHORIZED)
            message.text = if (compactWhenApproved) "可连接 CarPlay，也可扫码加入交流群。" else "无线 CarPlay 已激活，可以连接。"
            continueButton.isEnabled = true
        } else if (client.configured && client.requested) restartPolling(false)
        else { setState(LicensePanelState.REQUIRED); continueButton.isEnabled = false }
    }

    fun stop() {
        foreground = false; generation++; working = false
        pendingAction = null
        handler.removeCallbacks(poll)
        community.closeViewer()
        networkActions.forEach { it.isEnabled = client.configured }
    }

    fun applyTheme(night: Boolean) {
        val palette = com.shilapi.xcertplay.WaitingScreenColors.of(night)
        background = com.geelyplay.preface.AppPageStyle.card(context)
        labels.forEach { (view, bold) -> view.setTextColor(if (bold) palette.text else palette.secondary) }
        buttons.forEach { com.geelyplay.preface.AppPageStyle.action(it, it === networkActions.firstOrNull()) }
        buttons.forEach { it.textSize = 14f }
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
                message.text = "请联网刷新激活状态，无需重新申请。"
            }
        }
    }

    /** Approved installations continue immediately; pending applications wait for approval. */
    fun whenAdmitted(action: () -> Unit) {
        if (!foreground) return
        if (client.valid) { action(); return }
        pendingAction = action
        if (client.requested) restartPolling(false)
        else scrollTo(0, 0)
    }

    private fun setState(next: LicensePanelState) {
        if (state == next) return
        state = next
        applyAuthorizedPresentation()
        onStateChanged()
    }

    private fun applyAuthorizedPresentation() {
        if (!compactWhenApproved) return
        val approved = state == LicensePanelState.AUTHORIZED ||
            (state == LicensePanelState.CHECKING && previouslyApproved)
        networkActions.forEach { it.visibility = if (approved) GONE else VISIBLE }
        networkRow?.visibility = if (approved) GONE else VISIBLE
        identifier.visibility = if (approved) GONE else VISIBLE
        applicationHelp.visibility = if (approved) GONE else VISIBLE
        message.visibility = if (approved || state == LicensePanelState.REQUIRED && client.configured) GONE else VISIBLE
        footnotes.forEach { it.visibility = if (approved) GONE else VISIBLE }
        // During a cold-start check keep the compact presentation without claiming admission.
        heading.text = if (state == LicensePanelState.CHECKING && previouslyApproved) "无线 CarPlay 自动核验中" else if (approved) "无线 CarPlay 已激活" else "无线 CarPlay"
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
        continueButton.isEnabled = client.valid
        if (!client.valid) setState(LicensePanelState.CHECKING)
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
                        message.text = if (compactWhenApproved) "可连接 CarPlay，也可扫码加入交流群。" else "无线 CarPlay 已激活，可以连接。"
                        setState(LicensePanelState.AUTHORIZED)
                        if (client.valid) {
                            val action = pendingAction
                            pendingAction = null
                            action?.invoke()
                        }
                    }
                    else if (SystemClock.elapsedRealtime() < pollUntil) {
                        message.text = "等待审核，每分钟自动查询一次，也可手动刷新。"
                        setState(LicensePanelState.PENDING)
                        handler.postDelayed(poll, 60_000)
                    } else showPollingStopped()
                }, onFailure = {
                    if (client.valid) {
                        continueButton.isEnabled = true
                        setState(LicensePanelState.AUTHORIZED)
                    } else {
                        pendingAction = null
                        continueButton.isEnabled = false
                        message.text = LicenseFailureMessage.describe(it)
                        setState(LicensePanelState.ERROR)
                    }
                })
            }
        }, "diplay-license").start()
    }
}

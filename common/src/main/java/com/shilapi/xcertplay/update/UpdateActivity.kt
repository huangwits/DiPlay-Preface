package com.shilapi.xcertplay.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.ConnectionPageAppearance
import com.shilapi.xcertplay.ConnectionWaitingView
import com.shilapi.xcertplay.e01goc.E01GocManager
import java.io.File
import java.io.IOException

/** User-initiated updates only. Network work never starts just by opening this page. */
class UpdateActivity : Activity() {
    private lateinit var panel: ConnectionWaitingView
    private lateinit var appearance: ConnectionPageAppearance
    private lateinit var action: Button
    private lateinit var cancel: Button
    private lateinit var systemInstall: Button
    private lateinit var notes: TextView
    private lateinit var progress: ProgressBar
    private var stage = Stage.IDLE
    private var release: UpdateRelease? = null
    private var apk: File? = null
    private var cancellation: UpdateCancellation? = null
    private var generation = 0
    private var awaitingPermission = false
    private val handler = Handler(Looper.getMainLooper())
    private val installTick = object : Runnable {
        override fun run() {
            if (stage != Stage.INSTALLING || isFinishing || isDestroyed) return
            if (E01UpdateInstaller.busy(this@UpdateActivity)) handler.postDelayed(this, 1000)
            else {
                stage = if (apk != null) Stage.READY else Stage.IDLE
                val state = E01UpdateInstaller.status(this@UpdateActivity)
                render(if (state in setOf("queued", "installing")) "暂未收到安装结果，请确认当前版本后重试。"
                    else E01UpdateInstaller.message(state))
            }
        }
    }
    internal var client = UpdateClient()
    private val directory get() = File(cacheDir, "update").apply { mkdirs() }
    private val marker get() = File(directory, "pending.json")
    private val installedVersion get() = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    private enum class Stage { IDLE, CHECKING, AVAILABLE, DOWNLOADING, VERIFYING, READY, INSTALLING }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        panel = ConnectionWaitingView(this, statusTitle = "更新状态")
        panel.title.text = "应用更新"
        panel.instructions.text = "当前版本：$installedVersion\n连接互联网后检查星瑞版更新，下载完成后可直接打开安装。"
        panel.gestureHint.text = "覆盖安装可保留设置和授权信息，请在停车后操作。"
        for (view in listOf(panel.retry, panel.settings, panel.recovery, panel.back)) view.visibility = View.GONE
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE; contentDescription = "更新下载进度"
        }
        panel.controls.addView(progress, panel.controls.indexOfChild(panel.instructions) + 1)
        action = panel.addControlAction("检查更新").apply { tag = "update-action"; setOnClickListener { primaryAction() } }
        systemInstall = panel.addControlAction("使用系统安装").apply {
            visibility = View.GONE; setOnClickListener { confirmInstall(factory = false) }
        }
        cancel = panel.addControlAction("取消下载").apply { visibility = View.GONE; setOnClickListener { cancelWork() } }
        notes = panel.addControlText("").apply { tag = "update-notes"; visibility = View.GONE }
        panel.addControlAction("返回").setOnClickListener { finish() }
        appearance = ConnectionPageAppearance(this, panel)
        setContentView(panel)
        val pending = runCatching { UpdateCatalog.restore(marker.readText()) }.getOrNull()
        if (pending != null && UpdateVersion.parse(installedVersion) != null &&
            UpdateVersion.compare(pending.version, installedVersion) > 0) {
            val saved = File(directory, pending.sha256 + ".apk")
            if (saved.isFile && saved.length() == pending.size) {
                release = pending; apk = saved; stage = Stage.READY
            }
        }
        if (E01UpdateInstaller.busy(this)) {
            stage = Stage.INSTALLING
            render(E01UpdateInstaller.message("installing"))
        } else render(if (stage == Stage.READY) {
            val status = E01UpdateInstaller.status(this)
            if (status.isNotEmpty()) E01UpdateInstaller.message(status)
            else "已保留上次下载的安装包，安装前会再次校验。"
        } else "点击“检查更新”获取最新版本。")
    }

    private fun primaryAction() = when (stage) {
        Stage.IDLE -> checkForUpdates()
        Stage.AVAILABLE -> downloadUpdate()
        Stage.READY -> confirmInstall(factory = E01UpdateInstaller.supported(this))
        else -> Unit
    }

    private fun render(message: String) {
        panel.stage.text = message
        val busy = stage in setOf(Stage.CHECKING, Stage.DOWNLOADING, Stage.VERIFYING, Stage.INSTALLING)
        action.isEnabled = !busy && packageName == UpdateCatalog.PACKAGE
        action.text = when (stage) {
            Stage.AVAILABLE -> "下载 ${release?.version}"
            Stage.READY -> "安装 ${release?.version}"
            Stage.CHECKING -> "正在检查…"
            Stage.DOWNLOADING -> "正在下载…"
            Stage.VERIFYING -> "正在校验…"
            Stage.INSTALLING -> "正在安装…"
            Stage.IDLE -> "检查更新"
        }
        cancel.visibility = if (busy && stage != Stage.INSTALLING) View.VISIBLE else View.GONE
        systemInstall.visibility = if (stage == Stage.READY && E01UpdateInstaller.supported(this)) View.VISIBLE else View.GONE
        cancel.text = if (stage == Stage.DOWNLOADING) "取消下载" else "取消"
        progress.visibility = if (stage == Stage.DOWNLOADING) View.VISIBLE else View.GONE
        notes.text = release?.notes.orEmpty()
        notes.visibility = if (notes.text.isNotEmpty() && stage != Stage.IDLE) View.VISIBLE else View.GONE
    }

    private fun checkForUpdates() {
        stage = Stage.CHECKING; release = null; apk = null
        render("正在检查更新…")
        work({ signal, _ -> client.latest(signal) }) { latest ->
            if (UpdateVersion.compare(latest.version, installedVersion) > 0) {
                release = latest; stage = Stage.AVAILABLE
                render("发现新版 ${latest.version}（${latest.size / 1024 / 1024} MB）")
            } else { stage = Stage.IDLE; render("当前已是最新可用版本（$installedVersion）。") }
        }
    }

    private fun downloadUpdate() {
        val target = release ?: return
        stage = Stage.DOWNLOADING; progress.progress = 0
        render("正在下载 ${target.version}…")
        work({ signal, epoch ->
            val temporary = File.createTempFile("download-", ".part", directory)
            try {
                client.download(target, temporary, signal) { percent ->
                    ui(epoch) { progress.progress = percent; panel.stage.text = "正在下载 ${target.version} · $percent%" }
                }
                ui(epoch) { stage = Stage.VERIFYING; render("正在校验安装包…") }
                UpdateInstaller.validate(this, target, temporary)
                signal.check()
                val saved = File(directory, target.sha256 + ".apk")
                if (!temporary.renameTo(saved)) throw IOException("保存安装包失败，请检查车机存储空间。")
                marker.writeText(target.json().toString())
                saved
            } finally { temporary.delete() }
        }) { saved -> apk = saved; stage = Stage.READY; render("${target.version} 已下载并校验通过，可以安装。") }
    }

    private fun installationAllowed(): Boolean {
        if (CarPlayBackgroundSession.active || CarPlayBackgroundSession.hasSession() || E01GocManager.isBusy()) {
            render("请先结束 CarPlay 连接或蓝牙维护，再安装更新。")
            return false
        }
        return true
    }

    private fun confirmInstall(factory: Boolean) {
        if (!installationAllowed()) return
        AlertDialog.Builder(this).setTitle("安装 ${release?.version}")
            .setMessage(if (factory) "车机将直接覆盖更新，完成后自动返回 DiPlay。设置和授权信息会保留，请在停车后完成安装。"
                else "将打开系统安装界面并覆盖更新，保留设置和授权信息。请在停车后完成安装。")
            .setNegativeButton("稍后", null).setPositiveButton("继续安装") { _, _ -> installUpdate(factory) }.show()
    }

    private fun installUpdate(factory: Boolean) {
        val target = release ?: return
        val file = apk ?: return
        if (!installationAllowed()) return
        stage = Stage.VERIFYING; render("正在检查安装包…")
        if (factory) {
            stage = Stage.INSTALLING; render("正在准备车机安装…")
            work({ signal, _ -> E01UpdateInstaller.start(this, target, file, signal) }) {
                stage = Stage.INSTALLING
                render(E01UpdateInstaller.message("installing"))
                handler.post(installTick)
            }
            return
        }
        work({ signal, _ -> signal.check(); UpdateInstaller.prepare(this, target, file, signal) }) { verified ->
            stage = Stage.READY
            if (!installationAllowed()) return@work
            try {
                val intent = UpdateInstaller.intent(this, verified)
                awaitingPermission = intent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                startActivity(intent)
                render(if (awaitingPermission) "请允许本应用安装更新，返回后点击安装。" else
                    "请在系统界面确认安装；若提示未知来源，请先允许后再次点击安装。")
            } catch (_: Exception) {
                awaitingPermission = false
                render("车机未能打开安装界面。安装包已保留，可稍后重试。")
            }
        }
    }

    private fun <T> work(task: (UpdateCancellation, Int) -> T, success: (T) -> Unit) {
        cancellation?.cancel()
        val signal = UpdateCancellation().also { cancellation = it }
        val epoch = ++generation
        Thread({
            val result = runCatching { task(signal, epoch) }
            ui(epoch) {
                cancellation = null
                result.fold(success) { error ->
                    stage = if (apk != null) Stage.READY else if (release != null) Stage.AVAILABLE else Stage.IDLE
                    val message = error.message.orEmpty()
                    render(if (error is IOException && message.any { it in '\u4e00'..'\u9fff' }) message
                        else "更新失败，请检查网络和可用存储空间后重试。")
                }
            }
        }, "preface-update").start()
    }

    private fun ui(epoch: Int, action: () -> Unit) = runOnUiThread {
        if (epoch == generation && !isFinishing && !isDestroyed) action()
    }

    private fun cancelWork() {
        generation++; cancellation?.cancel(); cancellation = null
        stage = if (apk != null) Stage.READY else if (release != null) Stage.AVAILABLE else Stage.IDLE
        render("已取消，可稍后重试。")
    }

    override fun onResume() {
        super.onResume(); appearance.resume()
        handler.removeCallbacks(installTick)
        if (stage == Stage.INSTALLING) handler.post(installTick)
        if (awaitingPermission) { awaitingPermission = false; render("安装包已保留，点击安装继续。") }
    }
    override fun onPause() { handler.removeCallbacks(installTick); appearance.pause(); super.onPause() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); appearance.configurationChanged()
    }
    override fun onDestroy() {
        generation++; cancellation?.cancel(); handler.removeCallbacks(installTick); super.onDestroy()
    }
}

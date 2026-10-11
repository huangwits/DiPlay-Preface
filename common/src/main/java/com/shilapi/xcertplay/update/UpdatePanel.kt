package com.shilapi.xcertplay.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.AppPageStyle
import com.shilapi.xcertplay.e01goc.E01GocManager
import java.io.File
import java.io.IOException

/** One update operation and view, retained while the host rebuilds its settings cards. */
internal class UpdatePanel(private val activity: Activity) {
    val view = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; tag = "update-panel" }
    private val status = TextView(activity).apply {
        textSize = 15f; setTextColor(AppPageStyle.muted); tag = "update-status"
    }
    private var closed = false
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
            if (stage != Stage.INSTALLING || activity.isFinishing || activity.isDestroyed || closed) return
            if (E01UpdateInstaller.busy(activity)) handler.postDelayed(this, 1000)
            else {
                stage = if (apk != null) Stage.READY else Stage.IDLE
                val state = E01UpdateInstaller.status(activity)
                render(if (state in setOf("queued", "installing")) "暂未收到安装结果，请确认当前版本后重试。"
                    else E01UpdateInstaller.message(state))
            }
        }
    }
    internal var client = UpdateClient()
    private val directory get() = File(activity.cacheDir, "update").apply { mkdirs() }
    private val marker get() = File(directory, "pending.json")
    private val installedVersion get() = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
    private enum class Stage { IDLE, CHECKING, AVAILABLE, DOWNLOADING, VERIFYING, READY, PREPARING, INSTALLING }

    init {
        action = button("检查更新").apply { tag = "update-action"; setOnClickListener { primaryAction() } }
        AppPageStyle.action(action, primary = true)
        add(status)
        progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE; contentDescription = "更新下载进度"
            progressTintList = android.content.res.ColorStateList.valueOf(AppPageStyle.accent)
        }
        add(progress)
        notes = TextView(activity).apply {
            textSize = 14f; setTextColor(AppPageStyle.muted); tag = "update-notes"
        }
        add(notes)
        systemInstall = button("使用系统安装").apply { setOnClickListener { confirmInstall(factory = false) } }
        cancel = button("取消下载").apply { setOnClickListener { cancelWork() } }
        val pending = runCatching { UpdateCatalog.restore(marker.readText()) }.getOrNull()
        if (pending != null && UpdateVersion.parse(installedVersion) != null &&
            UpdateVersion.compare(pending.version, installedVersion) > 0) {
            val saved = File(directory, pending.sha256 + ".apk")
            if (saved.isFile && saved.length() == pending.size) {
                release = pending; apk = saved; stage = Stage.READY
            }
        }
        if (E01UpdateInstaller.busy(activity)) {
            stage = Stage.INSTALLING
            render(E01UpdateInstaller.message("installing"))
        } else render(if (stage == Stage.READY) {
            val status = E01UpdateInstaller.status(activity)
            if (status.isNotEmpty()) E01UpdateInstaller.message(status)
            else "已保留上次下载的安装包，安装前会再次校验。"
        } else "")
        resume()
    }

    fun attachTo(parent: LinearLayout) {
        (view.parent as? ViewGroup)?.removeView(view)
        parent.addView(view, LinearLayout.LayoutParams(-1, -2))
    }

    private fun add(child: View) {
        view.addView(child, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (10 * activity.resources.displayMetrics.density).toInt()
        })
    }

    private fun button(label: String) = Button(activity).apply {
        text = label; AppPageStyle.action(this); add(this)
    }

    private fun primaryAction() = when (stage) {
        Stage.IDLE -> checkForUpdates()
        Stage.AVAILABLE -> downloadUpdate()
        Stage.READY -> confirmInstall(factory = E01UpdateInstaller.supported(activity))
        else -> Unit
    }

    private fun render(message: String) {
        status.text = message
        status.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
        val busy = stage in setOf(Stage.CHECKING, Stage.DOWNLOADING, Stage.VERIFYING, Stage.PREPARING, Stage.INSTALLING)
        action.isEnabled = !busy && activity.packageName == UpdateCatalog.PACKAGE
        action.text = when (stage) {
            Stage.AVAILABLE -> "下载 ${release?.version}"
            Stage.READY -> "安装 ${release?.version}"
            Stage.CHECKING -> "正在检查…"
            Stage.DOWNLOADING -> "正在下载…"
            Stage.VERIFYING -> "正在校验…"
            Stage.PREPARING -> "正在准备安装…"
            Stage.INSTALLING -> "正在安装…"
            Stage.IDLE -> "检查更新"
        }
        cancel.visibility = if (busy && stage !in setOf(Stage.PREPARING, Stage.INSTALLING)) View.VISIBLE else View.GONE
        systemInstall.visibility = if (stage == Stage.READY && E01UpdateInstaller.supported(activity)) View.VISIBLE else View.GONE
        systemInstall.isEnabled = !busy
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
                    ui(epoch) { progress.progress = percent; status.text = "正在下载 ${target.version} · $percent%" }
                }
                ui(epoch) { stage = Stage.VERIFYING; render("正在校验安装包…") }
                UpdateInstaller.validate(activity, target, temporary)
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
        AlertDialog.Builder(activity).setTitle("安装 ${release?.version}")
            .setMessage(if (factory) "应用将自动检查安装权限并尝试覆盖更新，保留设置和授权信息。请在停车后完成安装。"
                else "将打开系统安装界面并覆盖更新，保留设置和授权信息。请在停车后完成安装。")
            .setNegativeButton("稍后", null).setPositiveButton("继续安装") { _, _ -> installUpdate(factory) }.show()
    }

    private fun installUpdate(factory: Boolean) {
        val target = release ?: return
        val file = apk ?: return
        if (!installationAllowed()) return
        stage = Stage.VERIFYING; render("正在检查安装包…")
        if (factory) {
            stage = Stage.PREPARING
            render("正在准备安装…")
            work({ signal, _ -> E01UpdateInstaller.start(activity, target, file, signal) }) {
                stage = Stage.INSTALLING
                render(E01UpdateInstaller.message("installing"))
                handler.post(installTick)
            }
            return
        }
        work({ signal, _ -> signal.check(); UpdateInstaller.prepare(activity, target, file, signal) }) { verified ->
            stage = Stage.READY
            if (!installationAllowed()) return@work
            try {
                val intent = UpdateInstaller.intent(activity, verified)
                awaitingPermission = intent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                activity.startActivity(intent)
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

    private fun ui(epoch: Int, action: () -> Unit) = activity.runOnUiThread {
        if (epoch == generation && !closed && !activity.isFinishing && !activity.isDestroyed) action()
    }

    private fun cancelWork() {
        generation++; cancellation?.cancel(); cancellation = null
        stage = if (apk != null) Stage.READY else if (release != null) Stage.AVAILABLE else Stage.IDLE
        render("已取消，可稍后重试。")
    }

    fun resume() {
        handler.removeCallbacks(installTick)
        if (stage == Stage.INSTALLING) handler.post(installTick)
        if (awaitingPermission) { awaitingPermission = false; render("安装包已保留，点击安装继续。") }
    }
    fun pause() { handler.removeCallbacks(installTick) }
    fun close() {
        closed = true; generation++; cancellation?.cancel(); handler.removeCallbacks(installTick)
    }
}

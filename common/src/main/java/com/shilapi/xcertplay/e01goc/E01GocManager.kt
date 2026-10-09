package com.shilapi.xcertplay.e01goc

import android.content.Context
import com.shilapi.xcertplay.E01BluetoothSwitchIntegration
import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.network.E01ConnectedPhones
import com.shilapi.xcertplay.transport.GocSppTransport
import java.io.File
import java.io.IOException
import java.security.MessageDigest

object E01GocPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("e01_goc_enabled", false)
    fun select(context: Context, enabled: Boolean) {
        if (enabled == enabled(context)) return
        prefs(context).edit().putBoolean("e01_goc_enabled", enabled)
            .remove("phone_address").remove("phone_name").apply()
    }
}

internal data class GocSnapshot(val current: String, val backup: String, val backupRecord: String,
                                val service: String, val listener: Boolean, val maintenance: String = "idle") {
    val installed get() = current == E01GocManager.CANDIDATE_SHA
    val recoverable get() = installed && Regex("[0-9a-f]{64}").matches(backup) &&
        backup != E01GocManager.CANDIDATE_SHA && backup == backupRecord
    fun installable(proof: String) = Regex("[0-9a-f]{64}").matches(current) && !installed &&
        proof == "$current:${E01GocManager.CANDIDATE_SHA}" &&
        (backup.isEmpty() && backupRecord.isEmpty() || backup == current && backupRecord == current)
}

/** One worker owns each operation; the independent root watchdog restores temporary tests. */
internal class E01GocManager(context: Context, private val log: (String) -> Unit) {
    private val app = context.applicationContext
    private val base = File(app.filesDir, "e01-goc")
    private val script = File(base, "manage.sh")
    @Volatile var cancelled = false

    fun <T> exclusive(block: () -> T): T {
        check(E01BluetoothSwitchIntegration.beginFactory()) { "另一个 E01 蓝牙维护操作正在进行" }
        try { return block() } finally { E01BluetoothSwitchIntegration.endFactory() }
    }

    fun inspect(): GocSnapshot {
        check(E01ConnectedPhones.supported(app)) { "未发现 E01 原厂蓝牙系统服务" }
        prepareScript()
        val values = fields(E01RootBridge.execute(script, "check"))
        val current = values["current"].orEmpty()
        check(Regex("[0-9a-f]{64}").matches(current)) { "无法校验原厂蓝牙服务文件" }
        return GocSnapshot(current, values["backup"].orEmpty(), values["backup_record"].orEmpty(),
            values["service"].orEmpty(), values["listener"] == "yes", values["maintenance"] ?: "busy")
    }

    fun test(address: String) {
        File(base, "proof").delete()
        val before = inspect()
        check(before.maintenance != "busy") { "上次维护仍在运行，请等待恢复完成" }
        check(!before.installed) { "候选组件已经安装；可直接选择原厂模式连接，或先恢复备份再测试" }
        prepareCandidate()
        check(!cancelled) { "测试已取消" }
        check(E01BluetoothSwitchIntegration.prepare()) { "CarPlay 尚未停止，请重试" }
        var linkReady = false
        var restoreConfirmed = false
        check(!cancelled) { "测试已取消" }
        try {
            launch("test", before.current)
            await(45_000, stopOnCancel = true) { it["stage"] == "ready" }
            log("临时服务已启动，等待 iPhone 重新连接原厂蓝牙电话和音乐…")
            val deadline = System.nanoTime() + 90_000_000_000L
            while (!cancelled && System.nanoTime() < deadline && !linkReady) {
                val phones = E01ConnectedPhones(app).use { it.connected(2000) }
                if (phones.any { it.address.equals(address, true) }) {
                    log("正在验证 iAP2 握手…")
                    try {
                        GocSppTransport.connect(address, { cancelled }).use { stream ->
                            Iap2Session.openWireless(stream, traceContext = "e01-compatibility").use { channel ->
                                linkReady = channel.awaitReady(10_000)
                            }
                        }
                    } catch (error: IOException) { log("等待 iPhone 完成握手…") }
                }
                if (!linkReady) Thread.sleep(1000)
            }
            check(!cancelled && linkReady) { if (cancelled) "测试已取消" else "iAP2 握手未通过，未授权替换组件" }
        } finally {
            log("正在结束临时测试并恢复原厂服务…")
            File(base, "stop").writeText("stop")
            // Cancellation must not skip the restoration wait or turn an unverified result into a pass.
            await(45_000, stopOnCancel = false) { it["stage"] == "completed" }
            val after = inspect()
            restoreConfirmed = after.current == before.current && after.service == "running"
            check(restoreConfirmed) { "原厂服务恢复尚未确认，请重启车机后检查" }
        }
        if (linkReady && restoreConfirmed) {
            File(base, "proof").writeText("${before.current}:$CANDIDATE_SHA")
            log("兼容测试通过：iAP2 已握手，原厂服务已恢复。下一步点击“安装适配”。这尚不代表 Wi-Fi 音视频已验证。")
        }
    }

    fun install() {
        val snapshot = inspect()
        check(snapshot.maintenance != "busy") { "上次维护仍在运行，请等待完成" }
        check(snapshot.installable(File(base, "proof").takeIf { it.isFile }?.readText().orEmpty())) {
            "请先通过临时兼容测试；系统文件变化或备份冲突时需重新检查"
        }
        prepareCandidate()
        check(E01BluetoothSwitchIntegration.prepare()) { "CarPlay 尚未停止，请重试" }
        launch("install", snapshot.current)
        await(150_000, false) { it["stage"] == "completed" }
        val after = inspect()
        check(after.installed && after.recoverable && after.listener && after.service == "running") { "安装后的服务或备份核验失败" }
        E01GocPreferences.select(app, true)
        log("安装完成，原版备份已核验。已选择 E01 原厂蓝牙模式，请重新选择 iPhone 后连接。")
    }

    fun restore() {
        val snapshot = inspect()
        check(snapshot.maintenance != "busy") { "上次维护仍在运行，请等待完成" }
        check(snapshot.recoverable) { "当前组件或原版备份无法核验，已停止恢复" }
        check(E01BluetoothSwitchIntegration.prepare()) { "CarPlay 尚未停止，请重试" }
        launch("restore", snapshot.current)
        await(150_000, false) { it["stage"] == "completed" }
        val after = inspect()
        check(after.current == snapshot.backup && after.service == "running") { "恢复后的原版校验失败" }
        File(base, "proof").delete()
        E01GocPreferences.select(app, false)
        log("原版已恢复，备份继续保留。原厂电话和音乐服务已启动。")
    }

    fun describe(snapshot: GocSnapshot) = "系统组件：${if (snapshot.installed) "已安装兼容组件" else "未安装兼容组件（需测试）"}\n" +
        "原厂服务：${if (snapshot.service == "running") "运行中" else "未运行"}；数据通道：${if (snapshot.listener) "已就绪" else "未就绪"}\n" +
        "可校验原版备份：${if (snapshot.recoverable) "有" else "无"}"

    private fun prepareScript() {
        check(base.isDirectory || base.mkdirs()) { "无法准备维护目录" }
        // Do not overwrite an active watchdog's script or state.
        if (!File(base, "lock").exists()) {
            app.assets.open("e01-goc/manage.sh").use { input -> script.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun prepareCandidate() {
        val bytes = try { app.assets.open("e01-goc/gocsdk-spp-uuid128-v2").use { it.readBytes() } }
        catch (error: IOException) { throw IOException("此安装包未包含本地 E01 兼容组件", error) }
        check(bytes.size == 2_841_668 && sha(bytes) == CANDIDATE_SHA) { "E01 兼容组件校验失败" }
        val candidate = File(base, "gocsdk-spp-uuid128-v2")
        candidate.writeBytes(bytes)
        check(candidate.setExecutable(true, true) && sha(candidate.readBytes()) == CANDIDATE_SHA) { "无法准备 E01 兼容组件" }
    }

    private fun launch(action: String, current: String) {
        // inspect() rejects a live owner; the root script atomically reclaims a recorded dead owner.
        File(base, "state").delete()
        File(base, "stop").delete()
        E01RootBridge.execute(script, action, current, background = true)
    }

    private fun await(timeout: Long, stopOnCancel: Boolean, done: (Map<String, String>) -> Boolean) {
        val deadline = System.nanoTime() + timeout * 1_000_000L
        while (System.nanoTime() < deadline) {
            val text = runCatching { File(base, "state").readText() }.getOrDefault("")
            val state = fields(text)
            if (state["stage"] == "failed") throw IOException("维护失败：${state["detail"]}")
            if (done(state)) return
            if (stopOnCancel && cancelled) throw IOException("测试已取消")
            Thread.sleep(250)
        }
        throw IOException("等待维护结果超时；请等待恢复完成，勿连续执行替换")
    }

    companion object {
        const val CANDIDATE_SHA = "e2b71f11ae17f701469f3b60982dbc7a273bcab947f18fd1979e43df6428c54c"
        fun isBusy(): Boolean = E01BluetoothSwitchIntegration.maintenanceBusy()
        private fun fields(text: String) = text.lineSequence().filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=').trim() }
        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

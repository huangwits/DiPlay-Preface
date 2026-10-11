package com.shilapi.xcertplay.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.e01goc.E01GocManager
import com.shilapi.xcertplay.e01goc.E01RootBridge
import com.shilapi.xcertplay.e01goc.E01RootAccess
import com.shilapi.xcertplay.network.E01ConnectedPhones
import java.io.File
import java.io.IOException

internal object E01UpdateInstaller {
    fun supported(context: Context) = context.packageName == UpdateCatalog.PACKAGE && E01ConnectedPhones.supported(context)
    private fun base(context: Context) = File(context.filesDir, "app-update")

    fun start(context: Context, release: UpdateRelease, apk: File, signal: UpdateCancellation) {
        if (!supported(context)) throw IOException("此车机不支持原厂安装方式，请使用系统安装。")
        UpdateInstaller.validate(context, release, apk)
        val expectedFile = File(context.cacheDir, "update/${release.sha256}.apk").canonicalFile
        require(apk.canonicalFile == expectedFile)
        signal.check()
        val access = E01RootBridge.probeRoot()
        signal.check()
        if (access != E01RootAccess.AVAILABLE) throw IOException(access.message + " 安装包已保留。")
        val folder = base(context).apply { mkdirs() }.canonicalFile
        val script = context.assets.open("app-update/install.sh").bufferedReader().use { it.readText() }
        val prepared = render(script, folder.path, expectedFile.path, release.sha256, systemInstalled(context))
        signal.check()
        val target = File(folder, "install.sh")
        target.writeText(prepared)
        if (CarPlayBackgroundSession.active || CarPlayBackgroundSession.hasSession() || E01GocManager.isBusy())
            throw IOException("请先结束 CarPlay 连接或蓝牙维护，再安装更新。")
        signal.check()
        writeStatus(folder, "queued")
        try { E01RootBridge.startAppUpdate(target) }
        catch (failure: Throwable) { writeStatus(folder, "launch-failed"); throw failure }
    }

    // The root service owns its result file. Replace the directory entry rather than
    // opening that root-owned file for writing on the next update attempt.
    internal fun writeStatus(folder: File, status: String) {
        val temporary = File.createTempFile("status-", ".tmp", folder)
        try {
            temporary.writeText("$status\n")
            val destination = File(folder, "result")
            if (!temporary.renameTo(destination) &&
                (!destination.delete() || !temporary.renameTo(destination)))
                throw IOException("无法保存安装状态，请重试。")
        } finally { temporary.delete() }
    }

    internal fun systemInstalled(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    fun diagnostics(context: Context) = "updateInstall device=${Build.DEVICE} systemApp=${systemInstalled(context)} " +
        "source=${context.applicationInfo.sourceDir} result=${status(context)}"

    internal fun render(template: String, base: String, apk: String, sha: String, systemApp: Boolean = false): String {
        for (path in listOf(base, apk)) {
            require(Regex("/[A-Za-z0-9_./-]+").matches(path) && !path.split('/').contains(".."))
        }
        require(base.endsWith("/com.shihab.diplay.preface/files/app-update"))
        require(Regex("[0-9a-f]{64}").matches(sha))
        require(apk.endsWith("/com.shihab.diplay.preface/cache/update/$sha.apk"))
        return template.replace("__BASE__", base).replace("__APK__", apk).replace("__SHA__", sha)
            .replace("__SYSTEM_APP__", if (systemApp) "1" else "0")
    }

    fun status(context: Context): String = runCatching { File(base(context), "result").readText().trim() }.getOrDefault("")
    fun busy(context: Context): Boolean {
        val result = File(base(context), "result")
        return status(context) in setOf("queued", "installing") && System.currentTimeMillis() - result.lastModified() < 5 * 60_000
    }

    fun message(status: String): String = when (status) {
        "queued", "installing" -> "车机正在安装，完成后会自动返回 DiPlay。"
        "success" -> "安装已完成。"
        "permission-denied", "launch-failed" -> "车机未提供可用的系统安装权限，请尝试系统安装。"
        "unsupported-car", "unsupported-platform" -> "此车机不支持原厂安装方式，请尝试系统安装。"
        "hash-mismatch", "hash-unavailable" -> "车机安装前校验未通过，请重新下载或尝试系统安装。"
        "install-busy" -> "另一次安装尚未结束，请稍后再试。"
        "install-read-only" -> "系统返回文件系统只读，尚未满足安装所需的挂载条件。安装包已保留。"
        "install-denied" -> "Root 通道可用，但系统拒绝安装权限。安装包已保留。"
        "install-space" -> "车机存储空间不足，请清理后重试。"
        "install-signature" -> "系统发现安装签名不一致，已停止更新。"
        "install-version" -> "系统拒绝安装较旧版本，已停止更新。"
        "install-oem-auth" -> "系统拒绝此安装包，可能受原厂安装授权限制；Root 不会跳过这项检查。安装包已保留。"
        "install-invalid" -> "系统认为安装包无效，安装未完成。安装包已保留，请检查安装限制或重新下载。"
        "create-failed" -> "系统未能创建安装会话，安装包已保留。"
        "write-failed" -> "系统未能写入安装包，请检查存储空间和访问权限。"
        "commit-failed" -> "系统未能完成覆盖安装，安装包已保留。"
        else -> "车机安装未完成，安装包已保留，可尝试系统安装。"
    }
}

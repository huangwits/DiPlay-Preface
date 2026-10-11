package com.shilapi.xcertplay.e01goc

import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import java.io.File
import java.io.IOException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal enum class E01RootAccess(val message: String) {
    AVAILABLE("Root 通道可用。安装更新时仍需核验系统安装权限。"),
    NOT_ROOT("原厂通道可用，但没有 Root 权限。"),
    UNAVAILABLE("车机未提供原厂 Root 通道。"),
    FAILED("Root 检测未通过，原厂通道拒绝请求或返回异常。"),
    TIMED_OUT("Root 检测超时，请稍后重试。")
}

/** Explicit maintenance commands through the E01 system service, never through a downloaded helper. */
internal object E01RootBridge {
    private const val MARKER = "__DIPLAY_RC:"
    private var pendingProbe: FutureTask<E01RootAccess>? = null
    private class ServiceUnavailable : IOException("车机未提供 E01 系统权限接口")

    /** Read only. A stuck vendor Binder must not freeze the UI or spawn unlimited callers. */
    fun probeRoot(timeoutMs: Long = 5000): E01RootAccess {
        check(Looper.myLooper() != Looper.getMainLooper())
        val probe = synchronized(this) {
            pendingProbe?.takeUnless { it.isDone } ?: FutureTask {
                try {
                    val output = transact("{ /system/bin/id; } 2>&1; rc=\$?; echo $MARKER\$rc")
                    val uid = Regex("^uid=([0-9]+)(?:\\(|\\s|$)").find(output)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    when (uid) { 0 -> E01RootAccess.AVAILABLE; null -> E01RootAccess.FAILED; else -> E01RootAccess.NOT_ROOT }
                } catch (_: ServiceUnavailable) { E01RootAccess.UNAVAILABLE }
                catch (_: Exception) { E01RootAccess.FAILED }
            }.also {
                pendingProbe = it
                Thread(it, "diplay-root-probe").apply { isDaemon = true }.start()
            }
        }
        return try { probe.get(timeoutMs, TimeUnit.MILLISECONDS) }
        catch (_: TimeoutException) { E01RootAccess.TIMED_OUT }
        catch (_: InterruptedException) { Thread.currentThread().interrupt(); E01RootAccess.FAILED }
    }
    fun execute(script: File, action: String, current: String = "none", background: Boolean = false): String {
        check(Looper.myLooper() != Looper.getMainLooper())
        val command = command(script.absolutePath, action, current, android.os.Process.myPid(), background)
        return transact(command)
    }

    /** Launch only our private, locally generated self-update script; no downloaded shell code. */
    fun startAppUpdate(script: File): String {
        check(Looper.myLooper() != Looper.getMainLooper())
        val path = script.canonicalPath
        require(Regex("/[A-Za-z0-9_./-]+").matches(path))
        require(path.endsWith("/com.shihab.diplay.preface/files/app-update/install.sh"))
        val command = "/system/bin/sh $path </dev/null >/dev/null 2>&1 & rc=\$?; echo $MARKER\$rc"
        require(command.toByteArray(Charsets.UTF_8).size <= 220)
        return transact(command)
    }

    private fun transact(command: String): String {
        val binder = runCatching {
            Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "ExtraUtilsService") as? IBinder
        }.getOrNull() ?: throw ServiceUnavailable()
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken("com.neusoft.alfus.os.IExtraUtilsService")
            request.writeInt(8)
            request.writeInt(0)
            request.writeByteArray(command.toByteArray(Charsets.UTF_8))
            if (!binder.transact(6, request, reply, 0)) throw IOException("车机拒绝维护请求")
            reply.readException()
            return parse(String(reply.createByteArray() ?: ByteArray(0), Charsets.UTF_8))
        } finally { request.recycle(); reply.recycle() }
    }

    internal fun command(path: String, action: String, current: String, owner: Int, background: Boolean): String {
        require(Regex("/[A-Za-z0-9_./-]+").matches(path) && !path.split('/').contains(".."))
        require(action in listOf("check", "test", "install", "restore"))
        require(current == "none" || Regex("[0-9a-f]{64}").matches(current))
        require(owner > 0)
        val call = "/system/bin/sh $path $action $current $owner"
        val wrapped = if (background) "$call </dev/null >/dev/null 2>&1 & rc=\$?; echo $MARKER\$rc"
            else "{ $call; } 2>&1; rc=\$?; echo $MARKER\$rc"
        require(wrapped.toByteArray(Charsets.UTF_8).size <= 220) { "E01 维护命令过长" }
        return wrapped
    }

    internal fun parse(output: String): String {
        val marker = output.lastIndexOf(MARKER)
        if (marker < 0) throw IOException("E01 权限接口未返回执行结果")
        val code = output.substring(marker + MARKER.length).trim().toIntOrNull()
        if (code != 0) throw IOException("E01 维护命令失败：${code ?: "未知"}")
        return output.substring(0, marker).trim()
    }
}

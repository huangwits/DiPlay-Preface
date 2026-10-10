package com.shilapi.xcertplay.e01goc

import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import java.io.File
import java.io.IOException

/** Explicit maintenance commands through the E01 system service, never through a downloaded helper. */
internal object E01RootBridge {
    private const val MARKER = "__DIPLAY_RC:"
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
        }.getOrNull() ?: throw IOException("车机未提供 E01 系统权限接口")
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

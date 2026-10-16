package com.geelyplay.preface.e01goc

/** Runs on the maintenance worker. Installation consent covers this attempt only. */
internal class E01QuickSetup(
    private val inspect: () -> GocSnapshot,
    private val test: (String) -> Unit,
    private val install: () -> Unit,
    private val admission: () -> Unit,
    private val cancelled: () -> Boolean,
) {
    fun ready(): Boolean {
        check(!cancelled()) { "操作已取消" }
        val snapshot = inspect()
        check(snapshot.maintenance != "busy") { "上次维护仍在运行，请等待恢复完成" }
        if (!snapshot.installed) return false
        check(snapshot.service == "running" && snapshot.listener) { "适配已安装，但蓝牙服务尚未就绪，请检查原车蓝牙后重试" }
        return true
    }

    fun prepare(address: String, installationConfirmed: Boolean) {
        if (ready()) return
        check(installationConfirmed) { "首次适配需要确认" }
        admission()
        check(!cancelled()) { "操作已取消" }
        test(address) // Must return only after a successful handshake AND verified restoration.
        check(!cancelled()) { "操作已取消，未安装适配" }
        admission()
        check(!cancelled()) { "操作已取消，未安装适配" }
        install() // Rechecks tested hashes, backup and admission at the actual launch boundary.
        check(ready()) { "适配尚未就绪" }
    }
}

package com.geelyplay.preface.e01goc

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.geelyplay.preface.network.E01Phone

/** Read the factory phone profiles without enabling Android Bluetooth or running maintenance. */
internal class FactoryPhonePicker(
    private val activity: Activity,
    private val lookup: () -> List<E01Phone>,
    private val onSelected: (E01Phone) -> Unit,
    private val onCancelled: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var querying = false
    private var active = false
    private var dialog: AlertDialog? = null

    fun show() {
        if (querying || dialog != null || !available()) return
        val attempt = ++generation
        active = true
        querying = true
        dialog = AlertDialog.Builder(activity).setMessage("正在查找原厂蓝牙已连接的手机…")
            .setNegativeButton("取消") { _, _ -> close() }.setOnCancelListener { close() }.show()
        Thread({
            val result = runCatching { lookup() }
            main.post {
                querying = false
                if (attempt != generation) return@post
                dialog?.dismiss(); dialog = null
                if (!available()) { close(); return@post }
                val phones = result.getOrDefault(emptyList())
                val builder = AlertDialog.Builder(activity).setTitle("选择原厂已连接的 iPhone")
                    .setNegativeButton("取消") { _, _ -> close() }.setOnCancelListener { close() }
                if (phones.isEmpty()) {
                    builder.setMessage(if (result.isFailure) "无法读取原厂蓝牙连接，请检查车机蓝牙后重试。"
                        else "请先在原车蓝牙中连接 iPhone 的电话和音乐，再返回选择手机。")
                } else builder.setItems(phones.map {
                    "${it.name.ifBlank { "iPhone" }} · ${it.address.takeLast(5)}"
                }.toTypedArray()) { _, index ->
                    dialog = null
                    if (attempt != generation || !available()) { close(); return@setItems }
                    // Save only after the previous session has actually closed. A late stop
                    // callback must not select a phone after leaving or switching transport.
                    CarPlayBackgroundSession.stop {
                        main.post {
                            if (attempt == generation && available()) {
                                generation++
                                active = false
                                onSelected(phones[index])
                            }
                        }
                    }
                }
                dialog = builder.show()
            }
        }, "diplay-factory-phone-lookup").start()
    }

    fun close() {
        generation++
        dialog?.dismiss(); dialog = null
        if (active) {
            active = false
            onCancelled()
        }
    }

    private fun available() = !activity.isFinishing && !activity.isDestroyed &&
        (E01GocPreferences.enabled(activity) || com.geelyplay.preface.network.E01ConnectedPhones.supported(activity)) &&
        !E01GocManager.isBusy()
}

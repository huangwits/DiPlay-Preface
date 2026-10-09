package com.shilapi.xcertplay.license

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OfflineLicenseActivity : Activity() {
    private lateinit var message: TextView
    private lateinit var device: TextView
    private lateinit var code: EditText
    private lateinit var deviceQr: DeviceCodeQrView
    private lateinit var scanHint: TextView
    private lateinit var phoneButton: Button
    private lateinit var page: ScrollView
    private var phoneSession: PhoneActivationServer? = null
    @Volatile private var phoneEpoch = 0
    @Volatile private var pageVisible = false
    private val actions = mutableListOf<Button>()
    private var deviceCode = ""
    private var working = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
        }
        fun text(value: String, size: Float = 16f) = TextView(this).apply {
            text = value; textSize = size; setPadding(0, 8, 0, 18)
            column.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        fun button(label: String, action: () -> Unit): Button {
            val control = Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } }
            column.addView(control, LinearLayout.LayoutParams(-1, -2)); actions += control
            return control
        }
        text("DiPlay 离线激活", 26f)
        text("推荐开启“手机协助激活”：手机扫码后可复制设备码，粘贴管理员签发的激活码并直接提交。无需授权服务器。")
        phoneButton = button("开启手机协助激活") {
            if (phoneSession != null) stopPhoneSession() else preparePhoneSession()
        }
        message = text("正在读取本机设备码…")
        deviceQr = DeviceCodeQrView(this).apply { visibility = android.view.View.GONE }
        column.addView(deviceQr, LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL })
        scanHint = text("手机扫码 → 复制设备码 → 申请激活码", 14f).apply {
            visibility = android.view.View.GONE
        }
        device = text("", 14f).apply { setTextIsSelectable(true) }
        button("复制设备码") { copy("设备码", deviceCode) }
        code = EditText(this).apply {
            hint = "粘贴完整激活码"; minLines = 2; maxLines = 5
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        }
        column.addView(code, LinearLayout.LayoutParams(-1, -2))
        button("粘贴激活码") {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val value = clip.getItemAt(0).coerceToText(this).toString()
                if (value.length <= 4096) code.setText(value) else message.text = "激活码内容过长"
            }
        }
        button("从文件导入激活码") {
            runCatching { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
            }, 1) }.onFailure { message.text = "没有可用的文件选择器，请粘贴激活码" }
        }
        button("激活本机") {
            val value = code.text.toString()
            background {
                OfflineLicense.activate(applicationContext, value)
                "永久授权已保存。返回 DiPlay 即可连接，无需联网校验。"
            }
        }
        button("返回 DiPlay") { finish() }
        text("激活码仅用于本次安装。卸载、清除数据或更换设备后可能需要重新签发；正常覆盖更新会保留授权。原厂蓝牙恢复工具仍可使用。", 13f)
        page = ScrollView(this).apply { addView(column) }
        setContentView(page)
        if (!OfflineLicense.enabled(this)) {
            phoneButton.visibility = android.view.View.GONE
            message.text = "此版本无需激活，返回 DiPlay 即可使用。"
            return
        }
        background {
            val label = OfflineLicense.deviceLabel(applicationContext)
            runOnUiThread { if (!isDestroyed) showDeviceCode(label) }
            if (OfflineLicense.canStart(applicationContext)) "本机已永久激活，无需联网。" else "设备码已就绪，请向管理员申请激活码。"
        }
    }

    private fun showDeviceCode(label: String) {
        deviceCode = label
        device.text = label
        scanHint.text = "此二维码只复制设备码。手机直接提交激活码，请点上方“开启手机协助激活”。"
        // Copy/file import remain usable if QR rendering is unavailable on a vendor build.
        runCatching { deviceQr.setDeviceCode(label) }.onSuccess {
            deviceQr.visibility = android.view.View.VISIBLE
            scanHint.visibility = android.view.View.VISIBLE
        }.onFailure {
            deviceQr.visibility = android.view.View.GONE
            scanHint.visibility = android.view.View.GONE
        }
    }

    private fun preparePhoneSession() {
        if (!OfflineLicense.enabled(this) || working) return
        if (deviceCode.isBlank()) { message.text = "设备码尚未就绪，请稍后重试。"; return }
        if (android.os.Build.VERSION.SDK_INT >= 37 &&
            checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"), 2)
            return
        }
        val addresses = runCatching { PhoneActivationServer.localAddresses() }.getOrDefault(emptyList())
        if (addresses.isEmpty()) {
            message.text = "请先让手机和车机连接同一 Wi-Fi，或让手机连接车机热点，再开启手机协助激活。"
            return
        }
        if (addresses.size == 1) startPhoneSession(addresses.single())
        else android.app.AlertDialog.Builder(this).setTitle("选择手机能访问的车机网络")
            .setItems(addresses.map { it.hostAddress }.toTypedArray()) { _, index -> startPhoneSession(addresses[index]) }
            .setNegativeButton("取消", null).show()
    }

    private fun startPhoneSession(address: java.net.InetAddress) {
        val epoch = ++phoneEpoch
        background {
            val session = PhoneActivationServer(address, deviceCode, OfflineLicense.CONTACT,
                activate = { value ->
                    check(pageVisible && phoneEpoch == epoch) { "车机授权页已关闭，请重新开启入口。" }
                    OfflineLicense.activate(applicationContext, value)
                },
                onActivated = { runOnUiThread {
                    if (!isDestroyed && phoneEpoch == epoch) {
                        code.text.clear()
                        message.text = "手机提交成功，本机已永久激活。返回 DiPlay 即可连接。"
                        page.smoothScrollTo(0, 0)
                    }
                } },
                onClosed = { runOnUiThread {
                    if (!isDestroyed && phoneEpoch == epoch) stopPhoneSession("手机入口已关闭，请重新开启并扫码。")
                } })
            runOnUiThread {
                if (!pageVisible || phoneEpoch != epoch || isDestroyed || session.isClosed) session.close()
                else attachPhoneSession(session)
            }
            "手机入口已开启。保持双方同一网络，用手机扫码后在浏览器中打开。"
        }
    }

    private fun attachPhoneSession(session: PhoneActivationServer) {
        phoneSession = session
        val rendered = runCatching { deviceQr.setWebAddress(session.url) }.isSuccess
        deviceQr.visibility = if (rendered) android.view.View.VISIBLE else android.view.View.GONE
        scanHint.visibility = android.view.View.VISIBLE
        scanHint.text = "手机扫码打开网页 → 复制设备码申请激活码 → 收到激活码后回网页粘贴提交。\n20 分钟有效；请保持此页打开。请使用手机浏览器打开。"
        device.text = session.url
        phoneButton.text = "关闭手机协助激活"
        page.post { if (phoneSession === session && rendered) page.smoothScrollTo(0, (deviceQr.top - 12).coerceAtLeast(0)) }
    }

    private fun stopPhoneSession(status: String = "手机入口已关闭。仍可扫描设备码或导入激活文件。") {
        phoneEpoch++
        val session = phoneSession
        phoneSession = null
        session?.close()
        phoneButton.text = "开启手机协助激活"
        if (deviceCode.isNotBlank()) showDeviceCode(deviceCode)
        message.text = status
    }

    override fun onStart() {
        super.onStart()
        pageVisible = true
    }

    override fun onStop() {
        pageVisible = false
        stopPhoneSession()
        super.onStop()
    }

    @Deprecated("Android permission compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 2 || !pageVisible) return
        if (grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) preparePhoneSession()
        else message.text = "未授予本地网络权限。可以重新开启并授权，或使用文件导入。"
    }

    private fun copy(label: String, value: String) {
        if (value.isBlank()) { message.text = "设备码尚未就绪"; return }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
        message.text = "已复制$label"
    }

    private fun background(action: () -> String) {
        if (working) return
        working = true; actions.forEach { it.isEnabled = false }; code.isEnabled = false
        Thread({
            val result = runCatching(action)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                working = false; actions.forEach { it.isEnabled = true }; code.isEnabled = true
                message.text = result.getOrElse { it.message ?: "离线校验失败，请重试" }
            }
        }, "diplay-offline-license").start()
    }

    @Deprecated("Android activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        background {
            val value = contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val size = input.read(buffer); if (size < 0) break
                    check(output.size() + size <= 4096) { "激活码文件过大" }; output.write(buffer, 0, size)
                }
                String(output.toByteArray(), Charsets.UTF_8).removePrefix("\uFEFF")
            } ?: error("无法读取激活码文件")
            runOnUiThread { if (!isDestroyed) code.setText(value) }
            "激活码已导入，请点击“激活本机”。"
        }
    }
}

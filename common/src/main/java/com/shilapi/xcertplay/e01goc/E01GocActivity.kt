package com.shilapi.xcertplay.e01goc

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.widget.Button
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.license.AppLicense
import com.shilapi.xcertplay.license.LicensePanel
import com.shilapi.xcertplay.license.OnlineLicense
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.E01BluetoothSwitchIntegration
import com.shilapi.xcertplay.network.E01ConnectedPhones

/** Explicit, foreground E01 maintenance. Opening this screen executes no privileged command. */
class E01GocActivity : Activity() {
    private lateinit var manager: E01GocManager
    private lateinit var panel: com.shilapi.xcertplay.ConnectionWaitingView
    private lateinit var appearance: com.shilapi.xcertplay.ConnectionPageAppearance
    private val actions = mutableListOf<Button>()
    private val factoryViews = mutableListOf<View>()
    private val androidViews = mutableListOf<View>()
    private lateinit var modeButton: Button
    private lateinit var phoneButton: Button
    private var working = false
    private var temporary = false
    private var licensePanel: LicensePanel? = null
    private lateinit var connectButton: Button
    private lateinit var licenseStatus: TextView
    private lateinit var cancelButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val licenseTick = object : Runnable {
        override fun run() {
            licensePanel?.refreshAdmission()
            updateLicenseLayout()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = E01GocManager(this, ::showStatus)
        panel = com.shilapi.xcertplay.ConnectionWaitingView(this, showLogPanel = false)
        panel.title.text = "星瑞蓝牙"
        panel.stage.text = "就绪"
        panel.retry.visibility = android.view.View.GONE
        panel.settings.visibility = android.view.View.GONE
        panel.recovery.visibility = android.view.View.GONE
        panel.back.visibility = android.view.View.GONE
        fun button(title: String, action: () -> Unit) {
            val view = panel.addControlAction(title)
            view.setOnClickListener { action() }
            actions += view
            factoryViews += view
        }
        fun factoryText(text: String, size: Float = 13f) {
            factoryViews += panel.addControlText(text, size)
        }
        modeButton = panel.addControlAction("").apply {
            tag = "bluetooth-mode"
            setOnClickListener { chooseMode() }
            actions += this
        }
        licenseStatus = panel.addControlText("").apply { tag = "bluetooth-license-status" }
        connectButton = panel.addControlAction("连接 CarPlay").apply {
            tag = "bluetooth-connect"
            setOnClickListener { continueConnection() }
            actions += this
        }
        phoneButton = panel.addControlAction("选择手机").apply {
            tag = "bluetooth-phone"
            setOnClickListener { choosePhone() }
            actions += this
        }
        panel.addControlAction("完整操作说明").setOnClickListener { showInstructions() }
        androidViews += panel.addControlAction("打开安卓蓝牙设置").apply {
            setOnClickListener { openAndroidBluetooth() }
            actions += this
        }
        factoryText("首次适配 · 按顺序操作", 16f)
        factoryText("① 检查状态：已安装且服务正常时，选择手机后直接连接。")
        button("检查状态") { work {
            val snapshot = manager.inspect()
            showStatus(manager.describe(snapshot) + if (snapshot.installed)
                "\n下一步：重新连接车机蓝牙，选择手机，再点击“连接 CarPlay”。无需重复测试或安装。"
            else "\n下一步：选择手机 → 连接测试。测试通过并恢复原服务后，才可安装适配。")
        } }
        factoryText("② 连接测试：选择手机后验证兼容性，结束后恢复原服务。")
        button("连接测试") {
            val address = DiPlayPreferences.phoneAddress(this)
            if (!allowConnectionTest()) return@button
            if (!E01GocPreferences.enabled(this) || address == null) showStatus("请先选择手机")
            else confirm("连接测试", "将暂停 CarPlay 并临时重启蓝牙，结束后恢复原服务。测试不替换系统程序，但可能更新配对数据。请保持手机蓝牙开启，完成后检查电话和音乐。") {
                if (!allowConnectionTest()) return@confirm
                temporary = true
                work { manager.test(address) }
            }
        }
        factoryText("③ 安装适配：测试通过后备份并安装，完成后重新连接车机蓝牙。")
        button("安装适配") {
            confirm("安装适配", "将备份并替换蓝牙组件，需先通过连接测试。备份仅包含原程序；电话、音乐和重启后的效果需另行确认。安装期间请保持供电，不要重启。") { work { manager.install() } }
        }
        factoryText("恢复 · 仅在需要时使用", 16f)
        factoryText("还原备份会恢复原版系统蓝牙组件。")
        button("还原备份") {
            confirm("还原备份", "将暂停 CarPlay，还原已校验的原版组件并重启蓝牙。") { work { manager.restore() } }
        }
        cancelButton = panel.addControlAction("停止测试").apply { visibility = View.GONE }
        cancelButton.setOnClickListener {
            if (temporary) { manager.cancelled = true; showStatus("正在结束测试并恢复蓝牙…") }
        }
        panel.addControlAction("返回").setOnClickListener { onBackPressed() }
        val root: View = if (OnlineLicense.enabled(this)) {
            val authorization = LicensePanel(this, ::continueConnection,
                onStateChanged = ::updateLicenseLayout, showConnectionAction = false).also { licensePanel = it }
            val gap = (8 * resources.displayMetrics.density).toInt()
            LinearLayout(this).apply {
                tag = "bluetooth-license-columns"
                orientation = LinearLayout.HORIZONTAL
                setPadding(gap, gap, gap, gap)
                addView(panel, LinearLayout.LayoutParams(0, -1, 1f))
                addView(authorization, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = gap })
            }
        } else panel
        appearance = com.shilapi.xcertplay.ConnectionPageAppearance(this, panel) { night ->
            licensePanel?.applyTheme(night)
            root.setBackgroundColor(com.shilapi.xcertplay.WaitingScreenColors.of(night).background)
        }
        setContentView(root)
        updateModeLayout()
        updateLicenseLayout()
        if (intent.getBooleanExtra("android_bluetooth_unavailable", false)) {
            showStatus("未检测到安卓蓝牙。请切换到原厂蓝牙模式，按提示完成连接准备。")
        }
        // Discard the retired diagnostic history when upgrading an existing installation.
        for (name in listOf("e01-goc-last-result.txt", "last-result.txt")) {
            runCatching { java.io.File(filesDir, name).delete() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        licensePanel?.refreshAdmission()
        updateModeLayout()
        updateLicenseLayout()
    }

    private fun updateModeLayout() {
        val factory = E01GocPreferences.enabled(this)
        factoryViews.forEach { it.visibility = if (factory) View.VISIBLE else View.GONE }
        androidViews.forEach { it.visibility = if (factory) View.GONE else View.VISIBLE }
        modeButton.text = "${if (factory) "原厂蓝牙" else "安卓蓝牙"} · 切换模式"
        panel.instructions.text = if (factory)
            "先连接车机蓝牙的电话和音乐，再连接 CarPlay。已完成适配时无需重复测试或安装。"
        else "在安卓蓝牙设置中配对 iPhone，选择手机后连接 CarPlay。保持手机蓝牙和 Wi-Fi 开启。"
        panel.gestureHint.text = "已选手机会自动保存，下次可直接连接。"
        val selected = DiPlayPreferences.phoneAddress(this) != null
        phoneButton.text = if (selected) "更换手机 · ${DiPlayPreferences.phoneName(this)}" else "选择手机"
    }

    private fun chooseMode() {
        if (working || E01GocManager.isBusy()) return
        val current = E01GocPreferences.enabled(this)
        AlertDialog.Builder(this).setTitle("蓝牙模式")
            .setSingleChoiceItems(arrayOf("安卓蓝牙", "原厂蓝牙"), if (current) 1 else 0) { dialog, index ->
                dialog.dismiss()
                val factory = index == 1
                if (factory == current) return@setSingleChoiceItems
                confirm("切换蓝牙模式", "将停止当前 CarPlay 连接，切换后需重新选择手机。此操作不会安装或卸载蓝牙适配。") {
                    work {
                        check(E01BluetoothSwitchIntegration.prepare()) { "CarPlay 尚未停止" }
                        E01GocPreferences.select(applicationContext, factory)
                        showStatus("已切换${if (factory) "原厂蓝牙" else "安卓蓝牙"}，请选择手机。")
                    }
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun updateLicenseLayout() {
        val authorization = licensePanel
        authorization?.visibility = if (authorization?.needsAttention == true) View.VISIBLE else View.GONE
        val message = authorization?.admissionMessage ?: when {
            !AppLicense.enabled(this) -> "此版本无需软件激活。"
            AppLicense.canStart(this) -> "授权已通过，可以连接 CarPlay。"
            else -> "请先完成本机离线激活。"
        }
        if (licenseStatus.text.toString() != message) licenseStatus.text = message
        connectButton.isEnabled = !working && !E01GocManager.isBusy() && AppLicense.canStart(this)
    }

    private fun showInstructions() {
        if (!E01GocPreferences.enabled(this)) {
            AlertDialog.Builder(this).setTitle("安卓蓝牙连接")
                .setMessage("1. 打开安卓蓝牙设置，与 iPhone 配对。\n2. 返回本页选择手机，完成授权后连接 CarPlay。\n3. 下次保持手机蓝牙和 Wi-Fi 开启，直接连接即可。\n\nUSB 连接无需软件激活，请从首页选择 USB。")
                .setPositiveButton("知道了", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("蓝牙连接完整步骤")
            .setMessage("日常使用（已经完成授权和适配）\n" +
                "1. 在车机系统蓝牙中连接 iPhone，确认电话和音乐已连接。\n" +
                "2. 等待已有授权自动核验。成功后右侧授权区会收起；无需重复申请。\n" +
                "3. 首次或更换 iPhone 时点击“选择手机”，之后点击“连接 CarPlay”。\n\n" +
                "第一次使用原厂蓝牙适配\n" +
                "1. 先完成系统蓝牙配对；在右侧申请激活，联系管理员核对申请号并等待批准。每分钟自动查询，也可手动刷新。\n" +
                "2. 点击“检查状态”。如果已安装兼容组件且服务正常，跳过测试和安装，直接选择手机连接。\n" +
                "3. 尚未安装时，点击“选择手机”，再点击“连接测试”。它会临时重启蓝牙，只验证握手，不会开始投屏。等待测试结束并恢复原服务。\n" +
                "4. 只有测试通过后才能点击“安装适配”。安装期间保持供电，不要重启或离开。\n" +
                "5. 安装完成后重新连接车机蓝牙，再次选择手机，点击“连接 CarPlay”。最后检查投屏、电话、音乐和重启后的连接。\n\n" +
                "遇到问题\n" +
                "• 授权核验失败：检查网络后在右侧刷新；到期或停用请联系管理员，不必重复申请。\n" +
                "• 测试中可点击“停止测试”，等待蓝牙恢复完成。\n" +
                "• “还原备份”恢复原版系统组件；切换蓝牙模式不会卸载适配。\n" +
                "• USB 连接无需软件激活，请从首页选择 USB。")
            .setPositiveButton("知道了", null).show()
    }

    private fun continueConnection() {
        if (working || E01GocManager.isBusy()) { showStatus("请等待蓝牙操作完成"); return }
        if (!AppLicense.canStart(this)) { licensePanel?.refreshAdmission(); updateLicenseLayout(); return }
        startActivity(Intent(this, DiPlayActivity::class.java)
            .putExtra("authorized_connection", "wireless")
            .putExtra("authorization_return", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun allowConnectionTest(): Boolean {
        if (AppLicense.canStart(this)) return true
        licensePanel?.let { it.scrollTo(0, 0); it.refreshAdmission() }
            ?: AppLicense.requireActivation(this, true)
        updateLicenseLayout()
        showStatus(if (licensePanel?.needsAttention == false) "正在核验已有授权，完成后请再次点击连接测试。"
            else "连接测试需要激活，请在右侧申请或刷新授权。")
        return false
    }

    private fun choosePhone() {
        if (working || E01GocManager.isBusy()) return
        if (!E01GocPreferences.enabled(this)) { chooseAndroidPhone(); return }
        chooseFactoryPhone()
    }

    private fun openAndroidBluetooth() {
        runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .onFailure { showStatus("未完成：无法打开蓝牙设置，请从车机系统设置中打开。") }
    }

    private fun chooseAndroidPhone() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 71)
            return
        }
        try {
            val adapter = (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                AlertDialog.Builder(this).setTitle("请先开启安卓蓝牙")
                    .setMessage("开启车机蓝牙并与 iPhone 配对，再返回选择手机。")
                    .setPositiveButton("打开蓝牙设置") { _, _ -> openAndroidBluetooth() }
                    .setNegativeButton("取消", null).show()
                return
            }
            val devices = adapter.bondedDevices.sortedBy { it.name ?: "" }
            if (devices.isEmpty()) {
                AlertDialog.Builder(this).setTitle("请先配对 iPhone")
                    .setMessage("在 iPhone 的设置 → 蓝牙中与车机配对，再返回选择手机。")
                    .setPositiveButton("打开蓝牙设置") { _, _ -> openAndroidBluetooth() }
                    .setNegativeButton("取消", null).show()
                return
            }
            val phones = devices.map { it.address to (it.name ?: "iPhone") }
            AlertDialog.Builder(this).setTitle("选择你的 iPhone")
                .setItems(devices.map { device ->
                    val name = device.name ?: "已配对手机"
                    if (devices.count { it.name == device.name } > 1) "$name · ${device.address.takeLast(5)}" else name
                }.toTypedArray()) { _, index ->
                    val (address, name) = phones[index]
                    work {
                        check(E01BluetoothSwitchIntegration.prepare()) { "CarPlay 尚未停止" }
                        DiPlayPreferences.savePhone(applicationContext, address, name)
                        showStatus("已选择 $name，可连接 CarPlay。")
                    }
                }.setNeutralButton("配对其他手机") { _, _ -> openAndroidBluetooth() }
                .setNegativeButton("取消", null).show()
        } catch (_: SecurityException) { showStatus("未完成：请在系统设置中允许蓝牙权限后重试。") }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 71 || isFinishing || isDestroyed || E01GocPreferences.enabled(this)) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) choosePhone()
        else showStatus("未完成：选择手机需要蓝牙权限，请在系统设置中允许后重试。")
    }

    private fun chooseFactoryPhone() = work {
        val phones = E01ConnectedPhones(applicationContext).use { it.connected() }
        if (phones.isEmpty()) showStatus("未找到手机，请先连接车机电话和音乐。")
        else runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            AlertDialog.Builder(this).setTitle("选择你的 iPhone")
                .setItems(phones.map { "${it.name.ifBlank { "已连接手机" }} · ${it.address}" }.toTypedArray()) { _, index ->
                    val phone = phones[index]
                    work {
                        check(E01BluetoothSwitchIntegration.prepare()) { "CarPlay 尚未停止" }
                        E01GocPreferences.select(applicationContext, true)
                        DiPlayPreferences.savePhone(applicationContext, phone.address, phone.name.ifBlank { "iPhone" })
                        showStatus("已选择 ${phone.name.ifBlank { "iPhone" }}。\n已安装适配时点击“连接 CarPlay”；尚未安装时先做连接测试。")
                    }
                }.setNegativeButton("取消", null).show()
        }
    }

    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("开始$title") { _, _ -> action() }.setNegativeButton("取消", null).show()
    }

    private fun work(action: () -> Unit) {
        if (working) return
        working = true
        cancelButton.visibility = if (temporary) View.VISIBLE else View.GONE
        manager.cancelled = false
        actions.forEach { it.isEnabled = false }
        panel.stage.text = "处理中…"
        panel.confirmed.text = ""
        panel.confirmed.visibility = android.view.View.GONE
        panel.failure.visibility = android.view.View.GONE
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Thread({
            try { manager.exclusive(action) }
            catch (error: Exception) { showStatus("未完成：${error.message ?: error.javaClass.simpleName}") }
            finally { runOnUiThread {
                working = false; temporary = false; actions.forEach { it.isEnabled = true }
                cancelButton.visibility = View.GONE
                panel.stage.text = "就绪"
                updateModeLayout()
                updateLicenseLayout()
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } }
        }, "diplay-e01-maintenance").start()
    }

    private fun showStatus(message: String) = runOnUiThread {
        if (isDestroyed) return@runOnUiThread
        val failed = message.startsWith("未完成：")
        val result = if (failed) panel.failure else panel.confirmed
        result.text = com.shilapi.xcertplay.DiagnosticRedactor.redact(message)
        result.visibility = android.view.View.VISIBLE
        (if (failed) panel.confirmed else panel.failure).visibility = android.view.View.GONE
    }

    override fun onResume() {
        super.onResume(); appearance.resume(); licensePanel?.start(); updateModeLayout(); updateLicenseLayout()
        if (licensePanel != null) { handler.removeCallbacks(licenseTick); handler.post(licenseTick) }
    }
    override fun onPause() {
        handler.removeCallbacks(licenseTick); licensePanel?.stop(); appearance.pause(); super.onPause()
    }
    override fun onConfigurationChanged(config: android.content.res.Configuration) {
        super.onConfigurationChanged(config); appearance.configurationChanged()
    }
    override fun onBackPressed() {
        if (working) {
            if (temporary) manager.cancelled = true
            showStatus("请等待维护或恢复完成后返回")
            return
        }
        super.onBackPressed()
    }
    override fun onStop() {
        if (temporary) manager.cancelled = true
        super.onStop()
    }
}

package com.geelyplay.preface.e01goc

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
import com.geelyplay.preface.license.AppLicense
import com.geelyplay.preface.license.LicensePanel
import com.geelyplay.preface.license.OnlineLicense
import com.shilapi.xcertplay.DiPlayPreferences
import com.geelyplay.preface.E01BluetoothSwitchIntegration
import com.geelyplay.preface.network.E01ConnectedPhones
import com.geelyplay.preface.network.E01Phone

/** Explicit, foreground E01 maintenance. Opening this screen executes no privileged command. */
class E01GocActivity : Activity() {
    private lateinit var manager: E01GocManager
    private lateinit var panel: com.geelyplay.preface.ConnectionWaitingView
    private val actions = mutableListOf<Button>()
    private val factoryViews = mutableListOf<View>()
    private val androidViews = mutableListOf<View>()
    private lateinit var modeButton: Button
    private lateinit var modeStatus: TextView
    private lateinit var phoneButton: Button
    private var working = false
    private var temporary = false
    private var licensePanel: LicensePanel? = null
    private lateinit var connectButton: Button
    private lateinit var licenseStatus: TextView
    private lateinit var cancelButton: Button
    private lateinit var recoveryButton: Button
    private var hadAdmission = false
    private var advanced = false
    private var foreground = false
    private var quickDialog: AlertDialog? = null
    private var quickPending = false
    private var operationGeneration = 0
    internal var quickPhoneLookup: () -> List<E01Phone> = { E01ConnectedPhones(applicationContext).use { it.connected() } }
    internal var quickSetup: E01QuickSetup? = null
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
        quickSetup = E01QuickSetup(manager::inspect, manager::test, manager::install, {
            if (!AppLicense.canStart(this) && OnlineLicense.enabled(this)) OnlineLicense.refresh(applicationContext)
            check(AppLicense.canStart(this)) { "授权暂不可用，请联网后重试" }
        }, { manager.cancelled })
        panel = com.geelyplay.preface.ConnectionWaitingView(this, showLogPanel = false)
        panel.title.text = "无线 CarPlay"
        panel.stage.visibility = View.GONE
        panel.retry.visibility = android.view.View.GONE
        panel.settings.visibility = android.view.View.GONE
        panel.recovery.visibility = android.view.View.GONE
        panel.back.visibility = android.view.View.GONE
        modeStatus = panel.addControlText("").apply { tag = "bluetooth-mode-status" }
        licenseStatus = panel.addControlText("").apply { tag = "bluetooth-license-status" }
        connectButton = panel.addControlAction("连接 CarPlay").apply {
            tag = "bluetooth-connect"
            setOnClickListener { quickConnect() }
            actions += this
        }
        phoneButton = panel.addControlAction("选择手机").apply {
            tag = "bluetooth-phone"
            setOnClickListener { choosePhone() }
            actions += this
        }
        panel.addControlAction("更多选项").apply {
            tag = "bluetooth-more"
            setOnClickListener {
                advanced = !advanced
                text = if (advanced) "收起更多选项" else "更多选项"
                updateModeLayout()
            }
        }
        androidViews += panel.addControlAction("打开安卓蓝牙设置").apply {
            setOnClickListener { openAndroidBluetooth() }
            actions += this
        }
        recoveryButton = panel.addControlAction("还原备份").apply {
            tag = "bluetooth-restore"
            actions += this
            factoryViews += this
            setOnClickListener {
                confirm("还原备份", "将暂停 CarPlay，还原已校验的原版组件并重启蓝牙。") {
                    work { manager.restore() }
                }
            }
        }
        cancelButton = panel.addControlAction("取消操作").apply { visibility = View.GONE }
        cancelButton.setOnClickListener {
            if (temporary) { manager.cancelled = true; showStatus("正在取消，请等待当前操作和必要的恢复完成…") }
        }
        modeButton = panel.addControlAction("切换蓝牙模式").apply {
            tag = "bluetooth-mode"
            setOnClickListener { chooseMode() }
            actions += this
        }
        panel.useAppStyle()
        panel.setBottomNavigation("返回") { onBackPressed() }
        panel.standardizeActionLayout()
        panel.controls.removeView(phoneButton)
        panel.controls.addView(phoneButton, panel.controls.indexOfChild(connectButton))
        val root: View = if (OnlineLicense.enabled(this)) {
            val authorization = LicensePanel(this, ::continueConnection,
                onStateChanged = ::updateLicenseLayout, showConnectionAction = false, compactWhenApproved = true,
                actionHeightDp = 48).also { licensePanel = it }
            val gap = (8 * resources.displayMetrics.density).toInt()
            BluetoothToolColumns(this).apply {
                tag = "bluetooth-license-columns"
                setPadding(gap, gap, gap, gap)
                addView(panel, LinearLayout.LayoutParams(0, -1, 1f))
                addView(authorization, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = gap })
            }
        } else panel
        root.setBackgroundColor(com.geelyplay.preface.AppPageStyle.background)
        licensePanel?.applyTheme(true)
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
        factoryViews.forEach { it.visibility = if (factory && advanced) View.VISIBLE else View.GONE }
        androidViews.forEach { it.visibility = if (factory) View.GONE else View.VISIBLE }
        modeStatus.text = if (factory) "原厂蓝牙" else "安卓蓝牙"
        modeButton.text = "切换蓝牙模式"
        modeButton.visibility = if (advanced) View.VISIBLE else View.GONE
        recoveryButton.visibility = if (factory && advanced) View.VISIBLE else View.GONE
        panel.instructions.visibility = if (factory) View.GONE else View.VISIBLE
        panel.instructions.text = "配对 iPhone 后选择手机，保持蓝牙和 Wi-Fi 开启。"
        panel.gestureHint.text = if (factory) "手机先连上原车蓝牙；重装后也会检查并跳过已安装的适配。" else "手机保持蓝牙和 Wi-Fi 开启。"
        connectButton.text = if (factory) "一键连接 CarPlay" else "连接 CarPlay"
        val selected = DiPlayPreferences.phoneAddress(this) != null
        phoneButton.text = if (selected) "更换手机 · ${DiPlayPreferences.phoneName(this)}" else "选择手机"
        updateActionState()
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
        // Keep the authorization column mounted after approval so the group QR code and
        // current authorization status remain available for daily users.
        authorization?.visibility = View.VISIBLE
        val message = authorization?.admissionMessage ?: when {
            !AppLicense.enabled(this) -> "此版本无需软件激活。"
            AppLicense.canStart(this) -> "无线 CarPlay 已激活，可以连接。"
            else -> "请先完成本机离线激活。"
        }
        if (licenseStatus.text.toString() != message) licenseStatus.text = message
        val admitted = AppLicense.canStart(this)
        connectButton.isEnabled = !working && !quickPending && !E01GocManager.isBusy() &&
            (admitted || OnlineLicense.wasActivated(this))
        if (admitted && !hadAdmission && E01GocPreferences.enabled(this)) {
            panel.controlScroll.scrollTo(0, 0)
        }
        hadAdmission = admitted
        updateActionState()
    }

    private fun updateActionState() {
        val idle = !working && !quickPending && !E01GocManager.isBusy()
        val allowed = AppLicense.canStart(this)
        panel.setPrimaryAction(if (idle && allowed) connectButton else null)
        panel.standardizeActionLayout()
    }

    /** Keeps the two-column layout on wide screens and stacks the panels on portrait/compact screens. */
    private class BluetoothToolColumns(context: android.content.Context) : LinearLayout(context) {
        private val gap get() = (8 * resources.displayMetrics.density).toInt()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val wide = width >= (700 * resources.displayMetrics.density).toInt() && width > height
            val next = if (wide) HORIZONTAL else VERTICAL
            if (orientation != next) orientation = next
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                val params = child.layoutParams as? LayoutParams ?: continue
                params.width = if (wide) 0 else -1
                params.height = if (wide) -1 else 0
                params.weight = if (wide && index == 0) 1.4f else 1f
                params.leftMargin = if (wide && index > 0) gap else 0
                params.topMargin = if (!wide && index > 0) gap else 0
                child.layoutParams = params
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun quickConnect() {
        if (working || quickPending || E01GocManager.isBusy()) return
        if (!AppLicense.canStart(this)) {
            val authorization = licensePanel
            if (authorization == null) { AppLicense.requireActivation(this, true); return }
            authorization.whenAdmitted { quickConnect() }
            showStatus("授权更新后会自动继续连接。")
            return
        }
        if (!E01GocPreferences.enabled(this) || com.shilapi.xcertplay.CarPlayBackgroundSession.active) {
            continueConnection(); return
        }
        var ready = false
        var phones = emptyList<E01Phone>()
        work(onSuccess = {
            val saved = DiPlayPreferences.phoneAddress(this)
            val selected = phones.firstOrNull { it.address.equals(saved, true) } ?: phones.singleOrNull()
            if (selected != null) prepareSelectedPhone(selected, ready)
            else {
                quickPending = true
                updateLicenseLayout()
                quickDialog = AlertDialog.Builder(this).setTitle("选择你的 iPhone")
                    .setItems(phones.map { "${it.name.ifBlank { "iPhone" }} · ${it.address.takeLast(5)}" }.toTypedArray()) { _, index ->
                        quickPending = false; quickDialog = null
                        prepareSelectedPhone(phones[index], ready)
                    }.setNegativeButton("取消", null).create().also { dialog ->
                        dialog.setOnDismissListener { quickPending = false; updateLicenseLayout() }
                        dialog.show()
                    }
            }
        }) {
            showStatus("正在检查蓝牙和已连接的 iPhone…")
            ready = requireNotNull(quickSetup).ready()
            phones = quickPhoneLookup()
            check(phones.isNotEmpty()) { "请先在原车蓝牙中连接 iPhone 的电话和音乐，再点一键连接" }
        }
    }

    private fun prepareSelectedPhone(phone: E01Phone, ready: Boolean) {
        if (!foreground || isFinishing || isDestroyed) return
        if (ready) { runQuickSetup(phone, false); return }
        quickPending = true
        updateLicenseLayout()
        quickDialog = AlertDialog.Builder(this).setTitle("首次蓝牙适配")
            .setMessage("将为 ${phone.name.ifBlank { "iPhone" }} 自动测试、恢复原服务、备份并安装适配，然后继续连接。期间蓝牙会重启，请保持供电；测试失败会停止，不会继续安装。")
            .setPositiveButton("适配并连接") { _, _ ->
                quickPending = false; quickDialog = null
                runQuickSetup(phone, true)
            }.setNegativeButton("取消", null).create().also { dialog ->
                dialog.setOnDismissListener { quickPending = false; updateLicenseLayout() }
                dialog.show()
            }
    }

    private fun runQuickSetup(phone: E01Phone, consent: Boolean) {
        temporary = true
        work(onSuccess = { continueConnection() }) {
            check(E01BluetoothSwitchIntegration.prepare()) { "CarPlay 尚未停止" }
            check(!manager.cancelled) { "操作已取消" }
            E01GocPreferences.select(applicationContext, true)
            DiPlayPreferences.savePhone(applicationContext, phone.address, phone.name.ifBlank { "iPhone" })
            requireNotNull(quickSetup).prepare(phone.address, consent)
            if (consent) {
                showStatus("适配已完成，正在等待 iPhone 重连原车蓝牙…")
                val deadline = System.nanoTime() + 45_000_000_000L
                var connected = false
                while (!manager.cancelled && System.nanoTime() < deadline && !connected) {
                    connected = quickPhoneLookup().any { it.address.equals(phone.address, true) }
                    if (!connected) Thread.sleep(1000)
                }
                check(!manager.cancelled) { "连接已取消；已完成的适配会保留" }
                check(connected) { "适配已完成，请将 iPhone 重新连上原车蓝牙，再点一键连接" }
            }
        }
    }

    private fun continueConnection() {
        if (working || E01GocManager.isBusy()) { showStatus("请等待蓝牙操作完成"); return }
        if (!AppLicense.canStart(this)) { licensePanel?.whenAdmitted { continueConnection() }; updateLicenseLayout(); return }
        startActivity(Intent(this, DiPlayActivity::class.java)
            .putExtra("authorized_connection", "wireless")
            .putExtra("authorization_return", true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
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
                        showStatus("已选择 ${phone.name.ifBlank { "iPhone" }}，点击“一键连接 CarPlay”即可。")
                    }
                }.setNegativeButton("取消", null).show()
        }
    }

    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("开始$title") { _, _ -> action() }.setNegativeButton("取消", null).show()
    }

    private fun work(onSuccess: () -> Unit = {}, action: () -> Unit) {
        if (working) return
        val generation = operationGeneration
        working = true
        cancelButton.visibility = if (temporary) View.VISIBLE else View.GONE
        manager.cancelled = false
        actions.forEach { it.isEnabled = false }
        panel.stage.text = "处理中…"
        panel.stage.visibility = View.VISIBLE
        panel.controlScroll.scrollTo(0, 0)
        panel.confirmed.text = ""
        panel.confirmed.visibility = android.view.View.GONE
        panel.failure.visibility = android.view.View.GONE
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Thread({
            var success = false
            try { manager.exclusive(action); success = true }
            catch (error: Exception) { showStatus("未完成：${error.message ?: error.javaClass.simpleName}") }
            finally { runOnUiThread {
                working = false; temporary = false; actions.forEach { it.isEnabled = true }
                cancelButton.visibility = View.GONE
                panel.stage.visibility = View.GONE
                updateModeLayout()
                updateLicenseLayout()
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (success && generation == operationGeneration && foreground && !isFinishing && !isDestroyed && !manager.cancelled) onSuccess()
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
        super.onResume(); foreground = true; licensePanel?.start(); updateModeLayout(); updateLicenseLayout()
        if (licensePanel != null) { handler.removeCallbacks(licenseTick); handler.post(licenseTick) }
    }
    override fun onPause() {
        foreground = false
        handler.removeCallbacks(licenseTick); licensePanel?.stop(); super.onPause()
    }
    override fun onConfigurationChanged(config: android.content.res.Configuration) {
        super.onConfigurationChanged(config); panel.requestLayout()
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
        operationGeneration++
        if (temporary) manager.cancelled = true
        quickDialog?.dismiss(); quickDialog = null; quickPending = false
        super.onStop()
    }
}

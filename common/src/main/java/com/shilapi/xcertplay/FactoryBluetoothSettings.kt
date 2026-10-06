package com.shilapi.xcertplay

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.transport.FactoryBluetoothTransport
import com.shilapi.xcertplay.transport.FactoryBluetoothBackend
import com.shilapi.xcertplay.transport.FactoryBluetoothPhone
import java.util.concurrent.atomic.AtomicBoolean

internal object FactoryBluetoothSettings {
    fun backend(context: Context): FactoryBluetoothBackend = FactoryBluetoothBackend.entries.firstOrNull {
        it.name == context.getSharedPreferences("diplay", Context.MODE_PRIVATE).getString("factory_bluetooth_backend", null)
    } ?: FactoryBluetoothBackend.ECARX

    fun setBackend(context: Context, backend: FactoryBluetoothBackend) {
        if (backend(context) == backend) return
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit()
            .putString("factory_bluetooth_backend", backend.name)
            .remove("phone_address").remove("phone_name").apply()
    }

    fun enabled(context: Context): Boolean = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        .getBoolean("factory_bluetooth_enabled", context.resources.getBoolean(R.bool.config_factory_bluetooth_default))

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit()
            .putBoolean("factory_bluetooth_enabled", enabled).apply()
    }

    fun recordResult(context: Context, result: String) {
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit()
            .putString("factory_bluetooth_last_result", result.take(600))
            .putString("factory_bluetooth_sdk", FactoryBluetoothTransport.diagnosticSummary).apply()
    }

    fun diagnostics(context: Context): String {
        val prefs = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        return "FactoryBluetooth enabled=${enabled(context)} backend=${backend(context)}\n" +
            "Result=${prefs.getString("factory_bluetooth_last_result", "not attempted")}\n" +
            "SDK=${prefs.getString("factory_bluetooth_sdk", "not queried")}"
    }

    fun failureCopy(context: Context, message: String): String? {
        val anwCode = Regex("E01-H0[1-3]").find(message)?.value
        if (anwCode != null) {
            val resource = when (anwCode) {
                "E01-H01" -> R.string.factory_anw_unavailable
                "E01-H02" -> R.string.factory_anw_phone
                else -> R.string.factory_anw_connection
            }
            return "[$anwCode] ${context.getString(resource)}"
        }
        val code = Regex("E01-F0[1-9]").find(message)?.value ?: return null
        val resource = when (code) {
            "E01-F01" -> R.string.factory_bt_error_sdk
            "E01-F02" -> R.string.factory_bt_error_spp
            "E01-F03" -> R.string.factory_bt_error_settings
            "E01-F04" -> R.string.factory_bt_error_phone
            "E01-F05" -> R.string.factory_bt_error_callback
            "E01-F06" -> R.string.factory_bt_error_connect
            "E01-F07" -> R.string.factory_bt_error_data
            "E01-F08" -> R.string.factory_bt_error_iap2
            else -> R.string.factory_bt_error_timeout
        }
        return "[$code] ${context.getString(resource)}"
    }
}

/** Read-only device selection stays off the UI thread and ignores replies after cancellation. */
internal class FactoryBluetoothPicker(
    private val activity: Activity,
    private val onPick: (FactoryBluetoothPhone) -> Unit,
    private val onCancel: () -> Unit,
) {
    private val active = AtomicBoolean(true)
    private var dialog: AlertDialog? = null

    fun show() {
        val app = activity.applicationContext
        val backend = FactoryBluetoothSettings.backend(app)
        dialog = AlertDialog.Builder(activity).setTitle(R.string.factory_bt_title)
            .setMessage(R.string.factory_bt_reading)
            .setNegativeButton(R.string.cancel) { _, _ -> cancel() }
            .setOnCancelListener { cancel() }.show()
        Thread({
            val result = runCatching { FactoryBluetoothTransport.pairedPhones(app, backend) }
            FactoryBluetoothSettings.recordResult(app, result.exceptionOrNull()?.message ?: "Factory paired list read")
            activity.runOnUiThread {
                if (!active.get() || activity.isFinishing || activity.isDestroyed || backend != FactoryBluetoothSettings.backend(app)) return@runOnUiThread
                dialog?.dismiss()
                val phones = result.getOrNull()
                if (phones.isNullOrEmpty()) {
                    val message = result.exceptionOrNull()?.message?.let { FactoryBluetoothSettings.failureCopy(activity, it) }
                        ?: activity.getString(R.string.factory_bt_error_phone)
                    dialog = AlertDialog.Builder(activity).setTitle(R.string.factory_bt_title)
                        .setMessage(message).setPositiveButton(R.string.got_it) { _, _ -> cancel() }
                        .setOnCancelListener { cancel() }.show()
                } else {
                    dialog = AlertDialog.Builder(activity).setTitle(R.string.choose_your_iphone)
                        .setItems(phones.map { "${it.name} · ${it.address.takeLast(5)}" }.toTypedArray()) { _, index ->
                            if (active.compareAndSet(true, false)) onPick(phones[index])
                        }.setNegativeButton(R.string.cancel) { _, _ -> cancel() }
                        .setOnCancelListener { cancel() }.show()
                }
            }
        }, "e01-factory-phone-list").apply { isDaemon = true; start() }
    }

    fun cancel() {
        if (active.compareAndSet(true, false)) onCancel()
        dialog?.dismiss()
        dialog = null
    }
}

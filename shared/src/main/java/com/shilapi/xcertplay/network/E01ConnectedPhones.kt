package com.shilapi.xcertplay.network

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.os.Looper
import java.io.Closeable
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class E01Phone(val address: String, val name: String)

/** Reads the factory profiles without enabling Android Bluetooth or changing pairing state. */
class E01ConnectedPhones(context: Context) : Closeable {
    private val app = context.applicationContext
    private val connections = mutableListOf<Profile>()
    private val loader by lazy {
        check(supported(app)) { "未发现 E01 原厂蓝牙系统服务" }
        app.createPackageContext(PACKAGE, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY).classLoader
    }

    fun connected(timeoutMillis: Long = 3000): List<E01Phone> {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Factory Bluetooth lookup requires a worker" }
        val hfp = bind("hfp.HeadsetService")
        val a2dp = bind("a2dp.A2dpService")
        val adapter = bind("btservice.AdapterService")
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        for (p in connections) p.ready.await((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
        return intersectProfiles(query(hfp.api), query(a2dp.api)).map { phone ->
            if (phone.name.isNotBlank()) phone else phone.copy(name = runCatching {
                adapter.api?.javaClass?.getMethod("getBtRemoteDeviceName", String::class.java)
                    ?.invoke(adapter.api, phone.address)?.toString()?.trim().orEmpty()
            }.getOrDefault(""))
        }
    }

    private fun bind(suffix: String): Profile {
        val classLoader = loader
        val profile = Profile(classLoader)
        connections += profile
        profile.bound = app.bindService(Intent().setComponent(ComponentName(PACKAGE, "$PACKAGE.$suffix")),
            profile, Context.BIND_AUTO_CREATE)
        if (!profile.bound) profile.ready.countDown()
        return profile
    }

    override fun close() {
        connections.filter { it.bound }.forEach { runCatching { app.unbindService(it) }; it.bound = false }
        connections.clear()
    }

    private class Profile(val loader: ClassLoader) : ServiceConnection {
        val ready = CountDownLatch(1)
        var bound = false
        @Volatile var api: Any? = null
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            api = runCatching {
                loader.loadClass(binder.interfaceDescriptor + "\$Stub")
                    .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            }.getOrNull()
            ready.countDown()
        }
        override fun onServiceDisconnected(name: ComponentName) { api = null }
    }

    companion object {
        const val PACKAGE = "ecarx.bluetooth.service"
        fun supported(context: Context): Boolean = runCatching {
            context.packageManager.getApplicationInfo(PACKAGE, 0).flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.getOrDefault(false)

        internal fun intersectProfiles(hfp: List<E01Phone>, a2dp: List<E01Phone>): List<E01Phone> =
            hfp.distinctBy { it.address }.mapNotNull { phone ->
                a2dp.firstOrNull { it.address == phone.address }?.let {
                    phone.copy(name = phone.name.ifBlank { it.name })
                }
            }.sortedBy { it.name.lowercase(Locale.US) + it.address }

        private fun query(api: Any?): List<E01Phone> = runCatching {
            if (api == null) return emptyList()
            val raw = api.javaClass.getMethod("getConnectedDevices").invoke(api)
            val devices = when (raw) { is Iterable<*> -> raw.toList(); is Array<*> -> raw.toList(); else -> emptyList() }
            devices.mapNotNull { device ->
                if (device == null) return@mapNotNull null
                val address = (if (device is String) device else
                    device.javaClass.getMethod("getAddress").invoke(device)?.toString())?.uppercase(Locale.US)
                if (address == null || !Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}").matches(address)) return@mapNotNull null
                val name = runCatching { device.javaClass.getMethod("getName").invoke(device)?.toString().orEmpty() }.getOrDefault("")
                E01Phone(address, name)
            }
        }.getOrDefault(emptyList())
    }
}

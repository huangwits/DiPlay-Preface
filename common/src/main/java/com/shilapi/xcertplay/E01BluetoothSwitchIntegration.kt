package com.shilapi.xcertplay

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The tool shares the app's session and Bluetooth preferences, not a second installation. */
object E01BluetoothSwitchIntegration {
    private val maintenanceOwner = java.util.concurrent.atomic.AtomicReference<String?>(null)
    internal fun beginFactory(): Boolean = maintenanceOwner.compareAndSet(null, "factory")
    internal fun endFactory() { maintenanceOwner.compareAndSet("factory", null) }
    internal fun maintenanceBusy(): Boolean = maintenanceOwner.get() != null

    /** Called on the tool worker: await teardown before touching shared Bluetooth hardware. */
    fun prepare(): Boolean {
        check(Looper.myLooper() != Looper.getMainLooper())
        val stopped = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { CarPlayBackgroundSession.stop { stopped.countDown() } }
        return stopped.await(15, TimeUnit.SECONDS)
    }

}

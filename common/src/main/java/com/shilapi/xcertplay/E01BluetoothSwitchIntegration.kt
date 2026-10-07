package com.shilapi.xcertplay

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The tool shares the app's session and Bluetooth preferences, not a second installation. */
object E01BluetoothSwitchIntegration {
    /** Called on the tool worker: await teardown before touching shared Bluetooth hardware. */
    @JvmStatic fun prepare(): Boolean {
        check(Looper.myLooper() != Looper.getMainLooper())
        val stopped = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { CarPlayBackgroundSession.stop { stopped.countDown() } }
        return stopped.await(15, TimeUnit.SECONDS)
    }

    @JvmStatic fun useSystem(context: Context) {
        // This Geely baseline uses Android Bluetooth. Clear the previous fork's optional backend.
        val prefs = context.getSharedPreferences("diplay", Context.MODE_PRIVATE)
        if (prefs.getBoolean("factory_bluetooth_enabled", false)) {
            prefs.edit().putBoolean("factory_bluetooth_enabled", false)
                .remove("factory_bluetooth_backend").remove("phone_address").remove("phone_name").apply()
        }
    }
}

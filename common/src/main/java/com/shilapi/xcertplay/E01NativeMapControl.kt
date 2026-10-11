package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build

/** FS11's own receiver retains its navigation, reversing, radar and eCall checks. */
internal class E01NativeMapControl(
    private val context: Context,
    private val prepareGesture: () -> Boolean,
    private val onGesture: (Boolean) -> Unit,
) {
    var desired = false
        private set
    var stage = "IDLE"
        private set
    private var active = false
    private var modeRequested = false
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION || !prepareGesture() || !active || !GeelyHudProjection.threeFingerEnabled(context) ||
                GeelyHudProjection.currentGuidance() == null) return
            val show = when (intent.getStringExtra(DIRECTION)) {
                "left" -> true
                "right" -> false
                else -> return
            }
            // The system delivered this same gesture to Extraservice. Never echo it back.
            desired = show
            modeRequested = show
            stage = if (show) "SYSTEM_SHOW" else "SYSTEM_HIDE"
            onGesture(show)
        }
    }

    init {
        runCatching {
            val filter = IntentFilter(ACTION)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            registered = true
        }.onFailure { stage = "RECEIVER_UNAVAILABLE" }
    }

    fun sync(connected: Boolean) {
        val enabled = connected && registered && VehicleMapSettings.enabled(context) && VehicleMapSettings.vehicleMode(context)
        if (enabled == active) return
        active = enabled
        if (enabled) GeelyHudProjection.claimFactoryNavigation(this, ::navigationEnded)
        else {
            GeelyHudProjection.releaseFactoryNavigation(this)
            desired = false
        }
    }

    fun usesFactoryGestures() = active

    fun request(show: Boolean) {
        desired = show
        if (!show) closeMode()
    }

    /** A broadcast is a request, not proof that the instrument accepted a mode change. */
    fun frameReady(): Boolean {
        if (!active || !desired || GeelyHudProjection.currentGuidance() == null) return false
        if (!modeRequested) {
            modeRequested = send(true)
            stage = if (modeRequested) "MODE_REQUESTED" else "REQUEST_FAILED"
        }
        return modeRequested
    }

    fun outputLost() = closeMode()

    private fun navigationEnded() {
        // Called before the shared navigation sender emits its end-of-route event.
        closeMode()
        desired = false
    }

    private fun closeMode() {
        if (modeRequested) {
            val sent = send(false)
            modeRequested = false
            stage = if (sent) "CLOSE_REQUESTED" else "CLOSE_FAILED"
        }
    }

    private fun send(show: Boolean): Boolean = runCatching {
        context.sendBroadcast(Intent(ACTION).setComponent(RECEIVER)
            .putExtra(DIRECTION, if (show) "left" else "right"))
        true
    }.getOrDefault(false)

    fun close() {
        GeelyHudProjection.releaseFactoryNavigation(this)
        closeMode()
        active = false
        desired = false
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    companion object {
        const val ACTION = "com.neusoft.alfus.three_finger"
        const val DIRECTION = "direction"
        private val RECEIVER = ComponentName("com.neusoft.extraservice", "com.neusoft.extraservice.receiver.ThreeFingerReceiver")

        @Suppress("DEPRECATION")
        fun supported(context: Context): Boolean = Build.VERSION.SDK_INT == 22 &&
            Build.DEVICE.orEmpty().uppercase().contains("FS11G") && E01NavigationOutput.available(context) && runCatching {
                val receiver = context.packageManager.getReceiverInfo(RECEIVER, 0)
                receiver.enabled && receiver.exported && receiver.applicationInfo.enabled &&
                    (receiver.permission == null || context.checkCallingOrSelfPermission(receiver.permission) == PackageManager.PERMISSION_GRANTED)
            }.getOrDefault(false)
    }
}

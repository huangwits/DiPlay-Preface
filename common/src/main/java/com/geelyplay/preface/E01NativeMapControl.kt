package com.geelyplay.preface

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.shilapi.xcertplay.DisplayDiagnosticSnapshot
import com.shilapi.xcertplay.GeelyHudProjection
import com.shilapi.xcertplay.VehicleMapSettings

/** FS11's own receiver retains its navigation, reversing, radar and eCall checks. */
internal class E01NativeMapControl(
    private val context: Context,
    private val prepareGesture: () -> Boolean,
    private val onGesture: (Boolean) -> Unit,
    private val enabled: () -> Boolean = { VehicleMapSettings.enabled(context) && VehicleMapSettings.vehicleMode(context) },
) {
    private val diagnosticAttempt = DisplayDiagnosticSnapshot.currentAttempt(context)
    var desired = false
        private set
    var stage = "IDLE"
        private set
    private var active = false
    private var modeRequested = false
    private var registered = false
    private var lastSystemGestureAt = -1L
    private var lastSystemGestureShow: Boolean? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            val show = when (intent.getStringExtra(DIRECTION)) {
                "left" -> true
                "right" -> false
                else -> return
            }
            val prepared = prepareGesture()
            val enabled = GeelyHudProjection.threeFingerEnabled(context)
            record("Map system gesture: direction=${if (show) "left" else "right"} prepared=$prepared active=$active enabled=$enabled")
            if (!prepared || !active || !enabled) return
            lastSystemGestureAt = SystemClock.elapsedRealtime()
            lastSystemGestureShow = show
            // The system delivered this same gesture to Extraservice. Never echo it back.
            desired = show
            // The OEM rejects a show gesture while navigation has not started yet.
            // Retain that pending request; after real route state and a frame arrive,
            // frameReady can request the mode once. Accepted gestures are never echoed.
            modeRequested = show && GeelyHudProjection.navigationActive()
            stage = if (show) "SYSTEM_SHOW" else "SYSTEM_HIDE"
            recordState()
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
        }.onFailure { stage = "RECEIVER_UNAVAILABLE"; recordState() }
    }

    fun sync(connected: Boolean) {
        val enabled = connected && registered && enabled()
        if (enabled == active) {
            if (active) GeelyHudProjection.refreshFactoryNavigation()
            return
        }
        active = enabled
        stage = if (enabled) "READY" else "INACTIVE"
        recordState()
        if (enabled) GeelyHudProjection.claimFactoryNavigation(this, ::navigationEnded)
        else {
            GeelyHudProjection.releaseFactoryNavigation(this)
            desired = false
        }
    }

    fun usesFactoryGestures() = active

    /** A confirmed factory show gesture may display map pixels without inventing route data. */
    fun mapOnlyOutputAllowed() = active && desired && lastSystemGestureShow == true &&
        !GeelyHudProjection.navigationActive()

    fun receivedSystemGesture(show: Boolean, since: Long) =
        lastSystemGestureAt >= since && lastSystemGestureShow == show

    fun request(show: Boolean) {
        desired = show
        if (!show) lastSystemGestureShow = false
        if (!show) closeMode()
    }

    /** Called after a real map frame. Delivery does not prove the instrument accepted the request. */
    fun frameReady(): Boolean {
        if (!active || !desired) return false
        // ThreeFingerReceiver rejects requests before navigation starts. Keep the
        // gesture pending until real route state arrives; video alone cannot start
        // factory navigation. Optional maneuver details can still arrive later.
        if (!GeelyHudProjection.navigationActive()) {
            if (stage != "WAITING_NAVIGATION") {
                stage = "WAITING_NAVIGATION"
                recordState()
            }
            return false
        }
        if (!modeRequested) {
            modeRequested = send(true)
            stage = if (modeRequested) "MODE_REQUESTED" else "REQUEST_FAILED"
            recordState()
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
            recordState()
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
        lastSystemGestureShow = null
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    private fun record(message: String) = DisplayDiagnosticSnapshot.record(context, diagnosticAttempt, message)
    private fun recordState() = record("Map native: stage=$stage active=$active desired=$desired modeRequested=$modeRequested")

    companion object {
        const val ACTION = "com.neusoft.alfus.three_finger"
        const val DIRECTION = "direction"
        private val RECEIVER = ComponentName("com.neusoft.extraservice", "com.neusoft.extraservice.receiver.ThreeFingerReceiver")

        /** Package inspection only: collecting a report must not start or bind OEM services. */
        fun diagnosticReport(context: Context): String {
            fun serviceStatus(component: ComponentName): String = runCatching {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getServiceInfo(component, 0)
                when {
                    !info.enabled || !info.applicationInfo.enabled -> "disabled"
                    !info.exported -> "not-exported"
                    info.permission != null && context.checkCallingOrSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED -> "permission-denied"
                    else -> "declared-accessible"
                }
            }.getOrDefault("missing")
            return "e01NativeMap supported=${supported(context)} capability=${capabilityReason(context)} " +
                "navigationReceiver=${E01NavigationOutput.available(context)} " +
                "dimNaviService=${serviceStatus(ComponentName("com.neusoft.extraservice", "com.ecarx.xui.adaptapi.diminteraction.NaviService"))} " +
                "xcNaviService=${serviceStatus(ComponentName("ecarx.naviservice", "ecarx.naviservice.service.NaviService"))}"
        }

        @Suppress("DEPRECATION")
        fun supported(context: Context): Boolean = capabilityReason(context) == "READY"

        @Suppress("DEPRECATION")
        fun capabilityReason(context: Context): String {
            if (Build.VERSION.SDK_INT != 22) return "SDK_UNSUPPORTED"
            if (!Build.DEVICE.orEmpty().uppercase().contains("FS11G")) return "FIRMWARE_UNSUPPORTED"
            return runCatching {
                val receiver = context.packageManager.getReceiverInfo(RECEIVER, 0)
                when {
                    !receiver.enabled || !receiver.applicationInfo.enabled -> "RECEIVER_DISABLED"
                    !receiver.exported -> "RECEIVER_NOT_EXPORTED"
                    receiver.permission != null && context.checkCallingOrSelfPermission(receiver.permission) != PackageManager.PERMISSION_GRANTED -> "RECEIVER_PERMISSION_DENIED"
                    else -> "READY"
                }
            }.getOrElse { "RECEIVER_UNAVAILABLE_${it.javaClass.simpleName}" }
        }
    }
}

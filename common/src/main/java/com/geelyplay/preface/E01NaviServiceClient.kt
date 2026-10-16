package com.geelyplay.preface

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import com.shilapi.xcertplay.hud.CarPlayHudGuidance
import java.util.ArrayList
import java.util.HashMap

/** Producer-side client for the exported NaviService protocol present on FS11G/API 22. */
internal class E01NaviServiceClient(context: Context) {
    private val app = context.applicationContext
    private val workerThread = if (supported(app)) HandlerThread("DiPlay-E01NaviService").apply {
        isDaemon = true
        start()
    } else null
    private val worker = workerThread?.let { Handler(it.looper) }
    private var bound = false
    private var remote: IBinder? = null
    private var routeStarted = false
    private var destinationReached = false
    private var lastGuidance: CarPlayHudGuidance? = null
    private var bindAttemptAt = Long.MIN_VALUE

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            worker?.post {
                remote = service
                Log.i(TAG, "NaviService connected")
                replayRoute()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            worker?.post {
                remote = null
                Log.w(TAG, "NaviService disconnected")
            }
        }
    }

    fun update(guidance: CarPlayHudGuidance?, routeActive: Boolean, arrived: Boolean) {
        worker?.post { updateOnWorker(guidance, routeActive, arrived) }
    }

    fun clear() {
        worker?.post(::clearOnWorker)
    }

    fun close() {
        worker?.post {
            clearOnWorker()
            workerThread?.quitSafely()
        }
    }

    private fun updateOnWorker(guidance: CarPlayHudGuidance?, routeActive: Boolean, arrived: Boolean) {
        if (arrived) {
            bind()
            if (!routeStarted) {
                routeStarted = true
                sendStart()
            }
            if (!destinationReached) {
                destinationReached = true
                sendDestinationReached("")
            }
            return
        }
        if (!routeActive) {
            clearOnWorker()
            return
        }
        if (remote == null) bind()
        if (!routeStarted) {
            routeStarted = true
            sendStart()
        }
        if (lastGuidance != guidance) {
            lastGuidance = guidance
            if (guidance != null) sendNavigationInfo(guidance, active = true)
        }
    }

    private fun clearOnWorker() {
        if (!routeStarted) return
        sendNavigationInfo(null, active = false)
        sendStop()
        routeStarted = false
        destinationReached = false
        lastGuidance = null
        unbind()
    }

    private fun replayRoute() {
        if (!routeStarted) return
        sendStart()
        if (destinationReached) sendDestinationReached("")
        else lastGuidance?.let { sendNavigationInfo(it, active = true) }
    }

    private fun sendStart() = transact(E01NaviServiceProtocol.TRANSACTION_TURN_BY_TURN_STARTED)
    private fun sendStop() = transact(E01NaviServiceProtocol.TRANSACTION_TURN_BY_TURN_STOPPED)
    private fun sendDestinationReached(message: String) =
        transact(E01NaviServiceProtocol.TRANSACTION_DESTINATION_REACHED) { writeString(message) }

    private fun sendNavigationInfo(guidance: CarPlayHudGuidance?, active: Boolean) =
        transact(E01NaviServiceProtocol.TRANSACTION_UPDATE_NAVIGATION_INFO) {
            writeInt(1)
            E01NaviServiceProtocol.writeNavigationInfo(this, guidance, active)
        }

    private fun transact(code: Int, payload: Parcel.() -> Unit = {}): Boolean {
        val service = remote ?: run { bind(); return false }
        return runCatching { E01NaviServiceProtocol.transact(service, code, payload) }
            .onFailure { error ->
                Log.w(TAG, "NaviService transaction $code failed", error)
            }.getOrDefault(false)
    }

    private fun bind() {
        if (bound || !supported(app)) return
        val now = SystemClock.elapsedRealtime()
        if (bindAttemptAt != Long.MIN_VALUE && now - bindAttemptAt < BIND_RETRY_MS) return
        bindAttemptAt = now
        bound = runCatching {
            app.bindService(Intent().setComponent(SERVICE), connection, Context.BIND_AUTO_CREATE)
        }.onFailure { Log.w(TAG, "NaviService bind failed", it) }.getOrDefault(false)
        if (!bound) Log.w(TAG, "NaviService bind was rejected")
    }

    private fun unbind() {
        remote = null
        if (!bound) return
        runCatching { app.unbindService(connection) }
            .onFailure { Log.w(TAG, "NaviService unbind failed", it) }
        bound = false
    }

    companion object {
        private const val TAG = "DiPlay-E01NaviService"
        private const val BIND_RETRY_MS = 3_000L
        private val SERVICE = ComponentName(
            "com.neusoft.extraservice",
            "com.ecarx.xui.adaptapi.diminteraction.NaviService",
        )

        fun supported(context: Context): Boolean = Build.VERSION.SDK_INT == 22 &&
            Build.DEVICE.orEmpty().contains("FS11G", ignoreCase = true) &&
            serviceStatus(context) == "declared-accessible"

        fun serviceStatus(context: Context): String = runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getServiceInfo(SERVICE, 0)
            when {
                !info.enabled || !info.applicationInfo.enabled -> "disabled"
                !info.exported -> "not-exported"
                info.permission != null && context.checkCallingOrSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED -> "permission-denied"
                else -> "declared-accessible"
            }
        }.getOrDefault("missing")
    }
}

/** Transaction IDs and the exact NaviInfoModel wire order recovered from the FS11 firmware. */
internal object E01NaviServiceProtocol {
    const val DESCRIPTOR = "com.ecarx.xui.adaptapi.diminteraction.INaviTransListener"
    const val TRANSACTION_UPDATE_NAVIGATION_INFO = 1
    const val TRANSACTION_DESTINATION_REACHED = 2
    const val TRANSACTION_TURN_BY_TURN_STARTED = 5
    const val TRANSACTION_TURN_BY_TURN_STOPPED = 6

    fun transact(remote: IBinder, code: Int, payload: Parcel.() -> Unit = {}): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.payload()
            if (!remote.transact(code, data, reply, 0)) return false
            reply.readException()
            true
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    fun writeNavigationInfo(parcel: Parcel, guidance: CarPlayHudGuidance?, active: Boolean) {
        val turn = guidance?.let { navigationTurnId(it.appleManeuver, it.drivingSide) } ?: 0
        parcel.writeInt(if (active) 1 else 0)
        parcel.writeInt(turn)
        parcel.writeString("")
        parcel.writeString(guidance?.road.orEmpty())
        parcel.writeLong(guidance?.distanceMeters?.coerceAtLeast(0)?.toLong() ?: -1L)
        parcel.writeLong(guidance?.remainingMeters?.coerceAtLeast(0) ?: -1L)
        parcel.writeLong(-1L)
        parcel.writeInt(-1)
        parcel.writeInt(-1)
        parcel.writeInt(0)
        parcel.writeList(ArrayList<Any?>())
        parcel.writeMap(HashMap<Any?, Any?>())
        parcel.writeString("")
        parcel.writeString("")
        parcel.writeInt(-1)
        parcel.writeInt(-1)
        parcel.writeInt(-1)
        parcel.writeString("")
        parcel.writeInt(-1)
        parcel.writeInt(-1)
        parcel.writeInt(-1)
        parcel.writeInt(-1)
        parcel.writeInt(-1)
    }

    /** NaviBean subtracts 100 from IDs 101..140 before passing them to its E01 HUD mapper. */
    internal fun navigationTurnId(appleManeuver: Int, drivingSide: Int): Int {
        val gaode = when (appleManeuver) {
            1, 20 -> 1
            2, 21 -> 2
            13, 22, 49, 52 -> 3
            14, 23, 50, 53 -> 4
            47 -> 7
            48 -> 8
            4, 18, 19, 26 -> if (drivingSide == 1) 10 else 9
            6 -> 13
            7 -> 24
            in 28..46 -> {
                val exit = appleManeuver - 27
                if (exit > 10) 13 else (if (drivingSide == 1) 34 else 24) + exit
            }
            10, 12, 24, 25, 27 -> 48
            3, 5, 8, 9, 11, 51 -> 11
            else -> 0
        }
        return if (gaode in 1..12 || gaode in 17..40) 100 + gaode else 0
    }
}

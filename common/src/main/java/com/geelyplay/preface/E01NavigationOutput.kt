package com.geelyplay.preface

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.shilapi.xcertplay.hud.CarPlayHudGuidance
import com.shilapi.xcertplay.GeelyHudProjection

/** Sends navigation to the E01 factory display service; no overlay or firmware change is needed. */
internal class E01NavigationOutput(private val context: Context) {
    private val naviService = E01NaviServiceClient(context.applicationContext)
    private var started = false
    private var previous: CarPlayHudGuidance? = null
    private var arrivalShowing = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val finishArrival = Runnable { clear() }

    fun update(guidance: CarPlayHudGuidance?, requiredByMap: Boolean = false, routeActive: Boolean = guidance != null, arrived: Boolean = false) {
        if (!enabled(context) && !requiredByMap) { clear(); return }
        if (!available(context)) { clear(); return }
        if (arrived) {
            naviService.update(guidance, routeActive, arrived = true)
            if (arrivalShowing) return
            if (!started && !send(stateIntent(8))) return
            started = true
            if (!send(stateIntent(39))) { clear(); return }
            arrivalShowing = true
            previous = null
            handler.postDelayed(finishArrival, 5_000)
            return
        }
        if (!routeActive) { clear(); return }
        if (arrivalShowing) clear()
        naviService.update(guidance, routeActive = true, arrived = false)
        if (!started) {
            if (!send(stateIntent(8))) return
            started = true
        }
        // An active route may arrive without maneuver data; never fabricate a turn.
        if (guidance == null) { previous = null; return }
        if (previous == guidance) return
        if (send(guidanceIntent(guidance))) previous = guidance else clear()
    }

    fun clear() {
        handler.removeCallbacks(finishArrival)
        naviService.clear()
        if (started) send(stateIntent(9))
        started = false
        arrivalShowing = false
        previous = null
    }

    fun close() {
        clear()
        naviService.close()
    }

    private fun send(intent: Intent): Boolean = runCatching {
        context.sendBroadcast(intent)
        true
    }.onFailure { Log.w("DiPlay-E01Navigation", "Factory navigation output unavailable", it) }.getOrDefault(false)

    companion object {
        private const val PACKAGE = "com.neusoft.extraservice"
        private val RECEIVER = ComponentName(PACKAGE, "$PACKAGE.receiver.AutoNaviReceiver")
        private fun preferences(context: Context) = context.getSharedPreferences("e01_navigation", Context.MODE_PRIVATE)
        fun enabled(context: Context): Boolean = preferences(context).getBoolean("enabled", FactorySteeringKeys.enabled(context))
        fun setEnabled(context: Context, enabled: Boolean) {
            preferences(context).edit().putBoolean("enabled", enabled).apply()
            GeelyHudProjection.applyLayout()
        }

        @Suppress("DEPRECATION")
        fun available(context: Context): Boolean = runCatching {
            val info = context.packageManager.getReceiverInfo(RECEIVER, 0)
            info.enabled && info.exported && info.applicationInfo.enabled &&
                (info.permission == null || context.checkCallingOrSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
        }.getOrDefault(false)

        fun naviServiceStatus(context: Context): String = E01NaviServiceClient.serviceStatus(context)

        private fun intent() = Intent("AUTONAVI_STANDARD_BROADCAST_SEND").setComponent(RECEIVER)
        internal fun stateIntent(state: Int): Intent = intent().putExtra("KEY_TYPE", 10019).putExtra("EXTRA_STATE", state)

        internal fun guidanceIntent(value: CarPlayHudGuidance): Intent = intent().apply {
            putExtra("KEY_TYPE", 10001)
            putExtra("ICON", navigationIcon(value.appleManeuver, value.drivingSide))
            putExtra("SEG_REMAIN_DIS", value.distanceMeters.coerceAtLeast(0))
            putExtra("NEXT_ROAD_NAME", value.road)
            putExtra("ROUTE_REMAIN_DIS", bounded(value.remainingMeters))
            putExtra("ROUTE_REMAIN_TIME", bounded(value.remainingSeconds))
            putExtra("ROUNG_ABOUT_NUM", if (value.appleManeuver in 28..46) value.appleManeuver - 27 else 0)
            // Unknown values must not invent speed limits, camera warnings or service areas.
            for (key in listOf("CAR_DIRECTION", "SEG_REMAIN_TIME", "ROAD_TYPE", "LIMITED_SPEED",
                    "CAMERA_DIST", "ROUTE_ALL_DIS", "CAMERA_TYPE", "SAPA_DIST", "SAPA_TYPE")) putExtra(key, -1)
            putExtra("SAPA_NAME", "")
        }

        private fun bounded(value: Long?): Int = value?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: -1

        /** AMap broadcast icon numbers differ from the secondary-display arrow vocabulary. */
        internal fun navigationIcon(maneuver: Int, drivingSide: Int): Int = when (maneuver) {
            1, 20 -> 2
            2, 21 -> 3
            13, 22, 49, 52 -> 4
            14, 23, 50, 53 -> 5
            47 -> 6
            48 -> 7
            4, 18, 26 -> if (drivingSide == 1) 19 else 8
            3 -> 9
            6, 19, in 28..46 -> if (drivingSide == 1) 17 else 11
            7 -> if (drivingSide == 1) 18 else 12
            10, 12, 24, 25, 27 -> 15
            5, 8, 9, 15, 16, 17, 51 -> 20
            else -> 1
        }
    }
}

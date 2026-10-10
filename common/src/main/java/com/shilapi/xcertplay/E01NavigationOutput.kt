package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.shilapi.xcertplay.hud.CarPlayHudGuidance

/** Sends navigation to the E01 factory display service; no overlay or firmware change is needed. */
internal class E01NavigationOutput(private val context: Context) {
    private var started = false
    private var previous: CarPlayHudGuidance? = null

    fun update(guidance: CarPlayHudGuidance?) {
        if (!enabled(context) || guidance == null) { clear(); return }
        if (!available(context)) { clear(); return }
        if (started && previous == guidance) return
        if (!started) {
            if (!send(stateIntent(8))) return
            started = true
        }
        if (send(guidanceIntent(guidance))) previous = guidance else clear()
    }

    fun clear() {
        if (started) send(stateIntent(9))
        started = false
        previous = null
    }

    private fun send(intent: Intent): Boolean = runCatching {
        context.sendBroadcast(intent)
        true
    }.onFailure { Log.w("DiPlay-E01Navigation", "Factory navigation output unavailable", it) }.getOrDefault(false)

    companion object {
        private const val PACKAGE = "com.neusoft.extraservice"
        private val RECEIVER = ComponentName(PACKAGE, "$PACKAGE.receiver.AutoNaviReceiver")
        private fun preferences(context: Context) = context.getSharedPreferences("e01_navigation", Context.MODE_PRIVATE)
        fun enabled(context: Context): Boolean = preferences(context).getBoolean("enabled", false)
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

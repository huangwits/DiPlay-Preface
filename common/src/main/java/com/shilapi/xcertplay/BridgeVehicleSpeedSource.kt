// carlito | Generic calibrated motion data; OEM channels stay in the separate vehicle bridge.
package com.shilapi.xcertplay

import android.content.Context
import android.os.SystemClock
import com.shilapi.xcertplay.vehicle.VehicleGear
import com.shilapi.xcertplay.transport.VehicleSpeedReading
import com.shilapi.xcertplay.transport.VehicleSpeedSource

internal class BridgeVehicleSpeedSource(private val context: Context) : VehicleSpeedSource {
    private var reader: ProjectionVehicleReader? = null
    private var generation = 0L
    private var sample: VehicleSpeedReading? = null

    @Synchronized private fun start() {
        if (reader != null) return
        sample = null
        val lease = ++generation
        reader = ProjectionVehicleReader(context) { frame -> accept(lease, frame) }
    }

    @Synchronized private fun accept(lease: Long, frame: ProjectionVehicleFrame) {
        if (reader == null || generation != lease) return
        val speed = frame.value("SPEED")?.metersPerSecond()
        // PRND is the public normalized contract: 0=P, 1=R, 2=N, 3=D.
        // A raw integer with an unknown encoding must never be declared as a gear.
        val nextGear = frame.value("GEAR")?.takeIf { it.unit == "PRND" }?.number?.let {
            when (it) { 0.0 -> VehicleGear.PARK; 1.0 -> VehicleGear.REVERSE
                2.0 -> VehicleGear.NEUTRAL; 3.0 -> VehicleGear.DRIVE; else -> null }
        }
        if (speed == null || nextGear == null) {
            sample = null
            return
        }
        sample = VehicleSpeedReading(frame.sampledAt, speed, nextGear)
    }

    @Synchronized override fun latestSpeed(): VehicleSpeedReading? {
        if (reader == null) return null
        val now = SystemClock.elapsedRealtime()
        return sample?.takeIf { now - it.elapsedMillis in 0L..3_000L }
    }

    override fun requestSpeed(enabled: Boolean) { if (enabled) start() else stop() }

    @Synchronized private fun stop() {
        ++generation
        reader?.close(); reader = null
        sample = null
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("vehicle_bridge_motion", Context.MODE_PRIVATE)
        fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
        fun setEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("enabled", enabled).apply() }
    }
}

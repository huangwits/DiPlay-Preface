package com.shilapi.xcertplay.vehicle

import com.shilapi.xcertplay.transport.CarPlayLocationFix
import com.shilapi.xcertplay.transport.Iap2LocationProvider
import com.shilapi.xcertplay.transport.NmeaLocationEncoder

/**
 * Features with a concrete iAP2 mapping; vendor properties are never protocol field IDs.
 * [fromVehicle] features come from the selected vehicle data entry; the others come from the
 * phone-side location fix and are always available.
 */
enum class VehicleDataFeature(val label: String, val description: String, val defaultEnabled: Boolean, val fromVehicle: Boolean = false) {
    ALTITUDE("GPS 高度", "仅在定位来源实际提供时发送高度", true),
    GPS_SPEED("GPS 速度", "发送 GPS 测得的地面速度，与车轮速度独立", true),
    COURSE("GPS 方向", "仅在定位来源实际提供时发送行进方向", true),
    ACCURACY("GPS 精度", "按定位提供的水平精度估算 HDOP", true),
    PRECISE_POSITIONING("详细定位信息", "使用系统实际提供的定位质量、卫星数量及海拔信息", false),
    WHEEL_SPEED("车速与档位", "需要实际可读取的车速、档位及有效测量时间", false, true),
    OUTSIDE_TEMPERATURE("车外温度", "仅在已选来源实际提供时发送车外温度", false, true),
    BAROMETRIC_PRESSURE("大气压", "仅在已选来源实际提供时发送大气压", false, true),
    PASSENGER_SEAT("乘客座椅状态", "仅在已选来源实际提供时发送乘客座椅状态", false, true),
    WIPERS("雨刷状态", "仅在已选来源实际提供时发送雨刷状态", false, true),
}

data class VehicleDataConfig(val platform: VehiclePlatform = VehiclePlatform.STANDARD_ANDROID, val enabled: Set<VehicleDataFeature> = VehicleDataFeature.entries
    .filter { it.defaultEnabled }.toSet(),
    /** The selected [com.shilapi.xcertplay.vehicle.source.VehicleDataEntry]. */
    val sourceId: String = com.shilapi.xcertplay.vehicle.source.VehicleDataEntries.DEFAULT_ID) {
    fun isEnabled(feature: VehicleDataFeature) = feature in enabled
    fun with(feature: VehicleDataFeature, value: Boolean) = copy(
        enabled = if (value) enabled + feature else enabled - feature,
    )
    fun filter(fix: CarPlayLocationFix) = fix.copy(
        altitudeMeters = fix.altitudeMeters.takeIf { isEnabled(VehicleDataFeature.ALTITUDE) },
        speedMetersPerSecond = fix.speedMetersPerSecond.takeIf { isEnabled(VehicleDataFeature.GPS_SPEED) },
        bearingDegrees = fix.bearingDegrees.takeIf { isEnabled(VehicleDataFeature.COURSE) },
        accuracyMeters = fix.accuracyMeters.takeIf { isEnabled(VehicleDataFeature.ACCURACY) },
        fixQuality = fix.fixQuality.takeIf { isEnabled(VehicleDataFeature.PRECISE_POSITIONING) },
        satellitesUsed = fix.satellitesUsed.takeIf { isEnabled(VehicleDataFeature.PRECISE_POSITIONING) },
        geoidSeparationMeters = fix.geoidSeparationMeters.takeIf {
            isEnabled(VehicleDataFeature.ALTITUDE) && isEnabled(VehicleDataFeature.PRECISE_POSITIONING)
        },
    )
}

/** A read-only source. Timestamps use elapsed time, so wall-clock corrections cannot revive data. */
interface VehicleLocationSource : AutoCloseable {
    fun start(): Boolean
    fun stop()
    fun location(): VehicleLocationSample?
    override fun close() = stop()
}

data class VehicleLocationSample(val fix: CarPlayLocationFix, val elapsedRealtimeMillis: Long)
enum class VehicleDataState { DISABLED, UNAVAILABLE, STALE, NO_PHONE_SUBSCRIPTION, ACTIVE }

/** Reusable source → policy → iAP2 adapter. Never sends a fix before the phone subscribes. */
class VehicleLocationProvider(
    private val source: VehicleLocationSource,
    private val config: VehicleDataConfig,
    private val elapsedRealtimeMillis: () -> Long,
    private val maxAgeMillis: Long = 30_000,
) : Iap2LocationProvider {
    private var subscribed = false
    private var sourceStarted = false
    override fun prewarm(): Boolean {
        if (!sourceStarted) sourceStarted = source.start()
        return sourceStarted
    }
    override fun start(): Boolean {
        subscribed = prewarm()
        return subscribed
    }
    override fun stop() {
        if (sourceStarted) source.stop()
        sourceStarted = false
        subscribed = false
    }
    fun state(): VehicleDataState {
        if (!subscribed) return VehicleDataState.NO_PHONE_SUBSCRIPTION
        val sample = source.location() ?: return VehicleDataState.UNAVAILABLE
        val age = elapsedRealtimeMillis() - sample.elapsedRealtimeMillis
        return if (age < 0 || age > maxAgeMillis) VehicleDataState.STALE else VehicleDataState.ACTIVE
    }
    override fun latestNmea(): String? {
        if (state() != VehicleDataState.ACTIVE) return null
        val sample = source.location() ?: return null
        val age = elapsedRealtimeMillis() - sample.elapsedRealtimeMillis
        if (age !in 0..maxAgeMillis) return null
        return NmeaLocationEncoder.encode(config.filter(sample.fix))
    }
    override fun close() {
        stop()
        source.close()
    }
}

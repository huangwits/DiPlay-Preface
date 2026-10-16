// carlito | Profile codec for existing read-only vehicle bridge consumers. GPL-3.0.
package com.shilapi.xcertplay.vehicleprobe

import org.json.JSONArray
import org.json.JSONObject

internal enum class VehicleField(val title: String, vararg val symbols: String) {
    SPEED("车速", "SENSOR_TYPE_CAR_SPEED", "SignalId_VehSpdLgtA", "car_speed"),
    RPM("发动机转速", "SENSOR_TYPE_RPM", "SignalId_EngNSafeEngN"),
    ODOMETER("总里程", "SENSOR_TYPE_ODOMETER"),
    RANGE("续航里程", "SENSOR_TYPE_ENDURANCE_MILEAGE"),
    BATTERY("电池电量", "SENSOR_TYPE_EV_BATTERY_LEVEL"),
    AMBIENT_TEMPERATURE("车外温度", "SENSOR_TYPE_TEMPERATURE_AMBIENT"),
    CABIN_TEMPERATURE("车内温度", "SENSOR_TYPE_TEMPERATURE_INDOOR"),
    HIGH_BEAM("远光灯", "SignalId_ExtrLtgStsHiBeam"),
    LOW_BEAM("近光灯", "SignalId_ExtrLtgStsLoBeam"),
    LEFT_INDICATOR("左转向灯", "SignalId_ExtrLtgStsTurnIndrLe"),
    RIGHT_INDICATOR("右转向灯", "SignalId_ExtrLtgStsTurnIndrRi"),
    STEERING_BUTTON("方向盘按键属性", "SignalId_SteerWhlBtnPsd"),
    GEAR("挡位"),
    GEAR_STAGE("变速箱挡位级别"),
    POSITION_LIGHT("示宽灯"),
    REAR_FOG_LIGHT("后雾灯"),
    DOOR_FRONT_LEFT("左前车门"),
    DOOR_FRONT_RIGHT("右前车门"),
    DOOR_REAR_LEFT("左后车门"),
    DOOR_REAR_RIGHT("右后车门"),
    DOOR_LOCK("车门锁"),
    WINDOW_FRONT_LEFT("左前车窗"),
    WINDOW_FRONT_RIGHT("右前车窗"),
    WINDOW_REAR_LEFT("左后车窗"),
    WINDOW_REAR_RIGHT("右后车窗"),
    MIRROR_FOLDED("后视镜折叠"),
    TEMPERATURE_STATUS("空调温度状态"),
    TIRE_FRONT_LEFT("左前胎压"),
    TIRE_FRONT_RIGHT("右前胎压"),
    TIRE_REAR_LEFT("左后胎压"),
    TIRE_REAR_RIGHT("右后胎压"),
    RADAR_FRONT_LEFT("左前雷达"),
    RADAR_FRONT_RIGHT("右前雷达"),
    RADAR_REAR_LEFT_OUTER("左后外侧雷达"),
    RADAR_REAR_LEFT_INNER("左后内侧雷达"),
    RADAR_REAR_RIGHT_INNER("右后内侧雷达"),
    RADAR_REAR_RIGHT_OUTER("右后外侧雷达"),
    ;

    val category: String get() = when {
        this == STEERING_BUTTON -> "方向盘按键"
        name.startsWith("TIRE_") -> "胎压"
        name.startsWith("RADAR_") -> "泊车雷达"
        name.startsWith("DOOR_") || name.startsWith("WINDOW_") || this == MIRROR_FOLDED -> "车门与车窗"
        this in setOf(HIGH_BEAM, LOW_BEAM, LEFT_INDICATOR, RIGHT_INDICATOR, POSITION_LIGHT, REAR_FOG_LIGHT) -> "灯光"
        this in setOf(AMBIENT_TEMPERATURE, CABIN_TEMPERATURE, TEMPERATURE_STATUS) -> "温度"
        else -> "行驶数据"
    }
}

/** An address and its read channel, never a replay of the report's stale reading. */
internal data class VehiclePropertyBinding(
    val field: VehicleField,
    val chain: String,
    val kind: String,
    val area: String,
    val propertyId: Int,
    val name: String,
    val origin: String,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
    val unit: String = "原始值",
    val encoding: String = "LINEAR",
) {
    val hexId: String get() = "0x${propertyId.toUInt().toString(16).uppercase()}"
}

internal data class VehiclePropertyProfile(val model: String, val bindings: List<VehiclePropertyBinding>)

internal object VehiclePropertyProfiles {
    fun decode(text: String): VehiclePropertyProfile {
        val json = JSONObject(text)
        require(json.getInt("schema") == 1)
        val rows = json.getJSONArray("bindings")
        val bindings = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            VehiclePropertyBinding(VehicleField.valueOf(row.getString("field")), row.getString("chain"),
                row.getString("kind"), row.getString("area"), row.getInt("propertyId"),
                row.getString("name"), row.getString("origin"), row.getDouble("scale"),
                row.getDouble("offset"), row.getString("unit"),
                row.optString("encoding", "LINEAR"))
        }
        return VehiclePropertyProfile(json.getString("model"), bindings).also(::validate)
    }

    fun encode(profile: VehiclePropertyProfile): String {
        validate(profile)
        val rows = JSONArray()
        profile.bindings.forEach { value -> rows.put(JSONObject().apply {
            put("field", value.field.name); put("chain", value.chain); put("kind", value.kind)
            put("area", value.area); put("propertyId", value.propertyId); put("name", value.name)
            put("origin", value.origin); put("scale", value.scale); put("offset", value.offset); put("unit", value.unit)
            put("encoding", value.encoding)
        }) }
        val json = JSONObject().put("schema", 1).put("author", "carlito")
            .put("model", profile.model.trim()).put("bindings", rows)
        return json.toString()
    }

    private fun validate(profile: VehiclePropertyProfile) {
        require(profile.model.trim().length in 1..80) { "请填写车型名称" }
        require(profile.bindings.isNotEmpty() && profile.bindings.size <= VehicleField.entries.size)
        require(profile.bindings.map { it.field }.distinct().size == profile.bindings.size)
        require(profile.bindings.all { it.scale.isFinite() && it.scale > 0 && it.offset.isFinite() && it.unit.length <= 16 })
    }
}

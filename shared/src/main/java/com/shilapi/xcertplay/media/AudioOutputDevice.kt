package com.shilapi.xcertplay.media

import android.os.Build

import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.json.JSONObject

/** A saved output preference. Device IDs alone may change when the audio service restarts. */
data class AudioOutputDevice(val id: Int, val type: Int, val address: String, val name: String) {
    fun resolve(manager: AudioManager?): AudioDeviceInfo? {
        return resolveFrom(outputs(manager))
    }
    // carlito | Input and output preferences share the same stable device identity representation.
    fun resolveInput(manager: AudioManager?): AudioDeviceInfo? = resolveFrom(inputs(manager))
    private fun resolveFrom(devices: List<AudioDeviceInfo>): AudioDeviceInfo? =
        if (Build.VERSION.SDK_INT < 23) null else if (address.isNotBlank()) devices.firstOrNull { it.id == id && it.type == type && deviceAddress(it) == address && it.productName.toString() == name }
            ?: devices.filter { it.type == type && deviceAddress(it) == address }.singleOrNull()
        else devices.firstOrNull { it.id == id && it.type == type && it.productName.toString() == name }
            ?: devices.filter { it.type == type && it.productName.toString() == name }.singleOrNull()

    fun encode(): String = JSONObject().put("id", id).put("type", type)
        .put("address", address).put("name", name).toString()

    companion object {
        private fun deviceAddress(device: AudioDeviceInfo): String = if (Build.VERSION.SDK_INT >= 28) device.address else ""
        fun outputs(manager: AudioManager?): List<AudioDeviceInfo> =
            if (Build.VERSION.SDK_INT < 23) emptyList() else runCatching { manager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty() }
                .getOrDefault(emptyList())
        fun inputs(manager: AudioManager?): List<AudioDeviceInfo> =
            if (Build.VERSION.SDK_INT < 23) emptyList() else runCatching { manager?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.toList().orEmpty() }.getOrDefault(emptyList())

        @android.annotation.TargetApi(23)
        fun from(device: AudioDeviceInfo) = AudioOutputDevice(
            device.id, device.type, deviceAddress(device), device.productName.toString(),
        )

        fun decode(value: String?): AudioOutputDevice? = runCatching {
            if (value == null) null else JSONObject(value).let {
                AudioOutputDevice(it.getInt("id"), it.getInt("type"), it.getString("address"), it.getString("name"))
            }
        }.getOrNull()
    }
}

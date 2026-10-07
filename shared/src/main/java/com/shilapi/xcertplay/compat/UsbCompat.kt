package com.shilapi.xcertplay.compat

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.util.Log

/** Typed USB access on API 22+, with explicit control transfers for descriptor-selected settings. */
object UsbCompat {
    fun configurationCount(device: UsbDevice): Int =
        device.configurationCount

    fun configuration(device: UsbDevice, index: Int): UsbConfiguration? =
        device.getConfiguration(index)

    fun configurationId(configuration: UsbConfiguration): Int =
        configuration.id

    fun interfaceCount(configuration: UsbConfiguration): Int =
        configuration.interfaceCount

    fun usbInterface(configuration: UsbConfiguration, index: Int): UsbInterface? =
        configuration.getInterface(index)

    fun alternateSetting(usbInterface: UsbInterface): Int =
        usbInterface.alternateSetting

    /** Selects a typed USB configuration. */
    fun setConfiguration(
        connection: UsbDeviceConnection,
        configuration: UsbConfiguration,
    ): Boolean =
        runCatching { connection.setConfiguration(configuration) }.getOrDefault(false)

    /**
     * Selects a configuration by id, which is the only form available once the layout has been
     * resolved from descriptors rather than from a platform UsbConfiguration.
     */
    fun setConfigurationById(connection: UsbDeviceConnection, configurationId: Int): Boolean =
        sendSetConfiguration(connection, configurationId)

    /** Standard USB SET_CONFIGURATION: bmRequestType 0x00, bRequest 0x09, wValue = configuration id. */
    private fun sendSetConfiguration(connection: UsbDeviceConnection, configurationId: Int): Boolean {
        val value = configurationId and 0xFF
        val result = connection.controlTransfer(0x00, 0x09, value, 0, null, 0, SET_CONFIGURATION_TIMEOUT_MS)
        if (result < 0) {
            Log.w(TAG, "SET_CONFIGURATION $configurationId failed code=$result")
            return false
        }
        return true
    }

    /** Selects the alternate setting represented by the platform interface. */
    fun setInterface(connection: UsbDeviceConnection, usbInterface: UsbInterface): Boolean =
        setInterface(connection, usbInterface, alternateSetting(usbInterface))

    /** Selects a descriptor-derived alternate with a control request when necessary. */
    fun setInterface(
        connection: UsbDeviceConnection,
        usbInterface: UsbInterface,
        alternateSetting: Int,
    ): Boolean {
        if (alternateSetting == alternateSetting(usbInterface)) {
            return runCatching { connection.setInterface(usbInterface) }.getOrDefault(false)
        }
        // SET_INTERFACE: bmRequestType=0x01, bRequest=0x0B, wValue=alternateSetting,
        // wIndex=interface id. Putting the alternate in wIndex instead selects alt 0.
        val result = connection.controlTransfer(
            0x01, 0x0B,
            alternateSetting and 0xFF,
            usbInterface.id and 0xFF,
            null, 0, SET_CONFIGURATION_TIMEOUT_MS,
        )
        if (result < 0) {
            Log.w(TAG, "SET_INTERFACE ${usbInterface.id}/$alternateSetting failed code=$result")
            return false
        }
        return true
    }

    private const val TAG = "xcertplay-usb"
    private const val SET_CONFIGURATION_TIMEOUT_MS = 2_000

}

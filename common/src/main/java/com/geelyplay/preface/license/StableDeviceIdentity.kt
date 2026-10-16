package com.geelyplay.preface.license

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.Locale

/** Uses the head unit's hardware serial when the OS exposes it; otherwise uses Android ID. */
internal object StableDeviceIdentity {
    private const val DOMAIN = "DP-DEVICE1"

    fun id(context: Context): String {
        val serial = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            Build.SERIAL
        } else null
        return id(
            hardwareSerial = serial,
            androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID),
            packageName = context.packageName,
        )
    }

    internal fun id(hardwareSerial: String?, androidId: String?, packageName: String): String {
        val normalizedSerial = hardwareSerial?.trim()?.uppercase(Locale.ROOT)
            ?.takeIf { it.matches(Regex("[A-Z0-9._-]{1,64}")) && it !in setOf(Build.UNKNOWN, "0", "UNKNOWN_SERIAL") }
        val normalized = androidId?.trim()?.lowercase(Locale.ROOT)
        val source = when {
            normalizedSerial != null -> "serial:$normalizedSerial"
            !normalized.isNullOrBlank() && normalized != "9774d56d682e549c" -> "android-id:$normalized"
            else -> null
        }
        require(source != null) {
            "无法读取稳定设备标识，请联系管理员"
        }
        require(packageName.isNotBlank() && '\n' !in packageName && '\r' !in packageName)
        return LicenseProtocol.hash("$DOMAIN\n$packageName\n$source".toByteArray(Charsets.US_ASCII))
    }
}

package com.geelyplay.preface

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.geelyplay.preface.license.AppLicense
import com.geelyplay.preface.e01goc.BluetoothAssetKey
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Reads the encrypted local firmware fallback; installed OEM assets remain untouched. */
internal object E01MapConfigAssets {
    private const val SIGNER = "8accfdd43476ee44404140964c3bd5c38edde9fd3d47e76bdd7e48c2c394e418"
    private const val MAX_CONFIG_BYTES = 1_048_576
    private const val ASSET_DIRECTORY = "e01-map/"
    private val names = setOf("C04010181159_MultipleScreen.json", "MultipleScreen.json")

    fun read(context: Context, name: String): String {
        require(name in names) { "Unsupported factory map configuration" }
        val asset = "$ASSET_DIRECTORY$name.enc"
        val envelope = context.assets.open(asset).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= MAX_CONFIG_BYTES + 65_536) { "Factory map configuration is oversized" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val plain = try {
            decode(envelope, name, verifiedSigner(context))
        } catch (error: Exception) {
            throw IOException("Factory map configuration validation failed", error)
        } finally {
            envelope.fill(0)
        }
        return try {
            plain.toString(Charsets.UTF_8)
        } finally {
            plain.fill(0)
        }
    }

    private fun verifiedSigner(context: Context): String {
        if (!AppLicense.enabled(context)) return SIGNER
        @Suppress("DEPRECATION")
        val signatures = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        check(context.packageName == "com.shihab.diplay.preface" &&
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 && signatures?.size == 1) {
            "安装包身份校验失败，请安装正式版本"
        }
        return MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray())
            .joinToString("") { "%02x".format(it) }.also {
                check(it == SIGNER) { "安装包签名不匹配，请安装正式版本" }
            }
    }

    internal fun decode(envelope: ByteArray, name: String, signer: String): ByteArray {
        require(name in names)
        require(envelope.size in 32..(MAX_CONFIG_BYTES + 65_536))
        require(envelope.copyOfRange(0, 4).contentEquals("DPG1".toByteArray(Charsets.US_ASCII)))
        val context = "GeelyPlay FS11 flyscreen v1\u0000$signer\u0000$name".toByteArray(Charsets.UTF_8)
        val master = BluetoothAssetKey.material()
        val key = try {
            MessageDigest.getInstance("SHA-256").run { update(master); digest(context) }
        } finally {
            master.fill(0)
        }
        val compressed = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, envelope.copyOfRange(4, 16)))
                updateAAD(context)
                doFinal(envelope, 16, envelope.size - 16)
            }
        } finally {
            key.fill(0)
            context.fill(0)
        }
        try {
            return GZIPInputStream(compressed.inputStream()).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_CONFIG_BYTES) { "Factory map configuration expands beyond its limit" }
                    output.write(buffer, 0, count)
                }
                buffer.fill(0)
                output.toByteArray()
            }
        } finally {
            compressed.fill(0)
        }
    }
}

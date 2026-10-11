package com.shilapi.xcertplay.e01goc

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.shilapi.xcertplay.license.AppLicense
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Encrypted packaging raises extraction cost; it cannot keep a client-side implementation secret. */
internal object BluetoothAssets {
    private const val SIGNER = "db0dc2a34dc06f22db7a3d10103fa011167712ebe61985ca2103084ff542cb82"
    private val limits = mapOf("e01-goc/manage.enc" to 65_536, "e01-goc/component.enc" to 2_841_668)

    fun read(context: Context, asset: String): ByteArray {
        val signer = if (AppLicense.enabled(context)) {
            @Suppress("DEPRECATION")
            val signatures = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            check(context.packageName == "com.shihab.diplay.preface" &&
                context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 && signatures?.size == 1) {
                "安装包身份校验失败，请安装正式版本"
            }
            MessageDigest.getInstance("SHA-256").digest(signatures.single().toByteArray())
                .joinToString("") { "%02x".format(it) }.also {
                    check(it == SIGNER) { "安装包签名不匹配，请安装正式版本" }
                }
        } else SIGNER // Retain identity-free source tests and the owner's unlicensed local build.
        val limit = checkNotNull(limits[asset])
        val envelope = context.assets.open(asset).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= limit + 4096) { "蓝牙资源大小异常" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return try { decode(envelope, asset, signer) }
        catch (error: Exception) { throw IOException("蓝牙资源校验失败，请重新安装正式版本", error) }
        finally { envelope.fill(0) }
    }

    internal fun decode(envelope: ByteArray, asset: String, signer: String): ByteArray {
        val limit = requireNotNull(limits[asset])
        require(envelope.size in 32..(limit + 4096) && envelope.copyOfRange(0, 4).contentEquals("DPG1".toByteArray()))
        val context = "DiPlay Bluetooth v1\u0000$signer\u0000$asset".toByteArray(Charsets.UTF_8)
        val master = BluetoothAssetKey.material()
        val key = try {
            MessageDigest.getInstance("SHA-256").run { update(master); digest(context) }
        } finally { master.fill(0) }
        val compressed = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, envelope.copyOfRange(4, 16)))
                updateAAD(context)
                doFinal(envelope, 16, envelope.size - 16)
            }
        } finally { key.fill(0) }
        try {
            return GZIPInputStream(compressed.inputStream()).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= limit) { "蓝牙资源解压大小异常" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray().also { require(it.isNotEmpty()) }
            }
        } finally { compressed.fill(0) }
    }
}

package com.shilapi.xcertplay.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

class UpdateApkProvider : FileProvider()

@Suppress("DEPRECATION")
internal object UpdateInstaller {
    fun validate(context: Context, release: UpdateRelease, file: File) {
        if (file.length() != release.size || UpdateClient.sha256(file) != release.sha256)
            throw IOException("安装包校验失败，请重新下载。")
        val installed = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
            ?: throw IOException("无法读取安装包，或它不兼容当前车机。")
        validateIdentity(installed, candidate, release.version)
        if (Build.VERSION.SDK_INT >= 24 && (candidate.applicationInfo?.minSdkVersion ?: 0) > Build.VERSION.SDK_INT)
            throw IOException("此版本不支持当前车机系统。")
    }

    internal fun validateIdentity(installed: PackageInfo, candidate: PackageInfo, expectedVersion: String) {
        if (installed.packageName != UpdateCatalog.PACKAGE || candidate.packageName != installed.packageName)
            throw IOException("安装包不是星瑞适配版，已停止安装。")
        if (candidate.versionName != expectedVersion || candidate.versionCode <= installed.versionCode ||
            UpdateVersion.compare(expectedVersion, installed.versionName.orEmpty()) <= 0)
            throw IOException("安装包版本与更新信息不一致，或不是更高版本。")
        val currentSignatures = installed.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        val newSignatures = candidate.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        if (currentSignatures.isEmpty() || newSignatures != currentSignatures)
            throw IOException("安装包签名与当前应用不一致，已停止安装。")
    }

    fun prepare(context: Context, release: UpdateRelease, source: File, cancellation: UpdateCancellation): File {
        validate(context, release, source)
        if (Build.VERSION.SDK_INT >= 24) return source
        // AOSP 5.1 PackageInstallerActivity rejects content://. Keep only the public APK
        // in the app's external files directory, readable by the legacy system installer.
        val folder = context.getExternalFilesDir("updates")
            ?: throw IOException("车机存储不可用，无法准备安装包。")
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("无法创建更新目录")
        val temporary = File.createTempFile("install-", ".part", folder)
        try {
            source.inputStream().use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    cancellation.check()
                    val n = input.read(buffer); if (n < 0) break
                    output.write(buffer, 0, n)
                }
            } }
            if (temporary.length() != release.size || UpdateClient.sha256(temporary) != release.sha256)
                throw IOException("安装包复制校验失败，请检查存储空间。")
            val destination = File(folder, release.apkName)
            if (!temporary.renameTo(destination)) throw IOException("无法保存安装文件")
            return destination
        } finally { temporary.delete() }
    }

    fun intent(context: Context, file: File): Intent {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls())
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        val uri = if (Build.VERSION.SDK_INT < 24) Uri.fromFile(file)
            else FileProvider.getUriForFile(context, "${context.packageName}.update-apks", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply {
                clipData = ClipData.newRawUri("DiPlay 更新", uri)
            }
    }
}

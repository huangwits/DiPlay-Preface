package com.shilapi.xcertplay.update

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger

// Adapted from shihabal3amri/DiPlay and carlito12345/DiPlay's manual update catalog.
internal object UpdateVersion {
    private val pattern = Regex("v?[0-9]+(?:\\.[0-9]+){2,7}")
    fun parse(value: String): List<BigInteger>? = value.takeIf(pattern::matches)
        ?.removePrefix("v")?.split('.')?.map { BigInteger(it) }

    fun compare(a: String, b: String): Int {
        val left = requireNotNull(parse(a)) { "无法识别新版版本号" }
        val right = requireNotNull(parse(b)) { "无法识别当前版本号" }
        for (i in 0 until maxOf(left.size, right.size)) {
            val compared = (left.getOrNull(i) ?: BigInteger.ZERO).compareTo(right.getOrNull(i) ?: BigInteger.ZERO)
            if (compared != 0) return compared
        }
        return 0
    }
}

internal data class UpdateRelease(val version: String, val apkName: String, val apkUrl: String,
    val size: Long, val sha256: String, val notes: String) {
    fun json(): JSONObject = JSONObject().put("version", version).put("apkName", apkName)
        .put("apkUrl", apkUrl).put("size", size).put("sha256", sha256).put("notes", notes)
}

internal object UpdateCatalog {
    const val PACKAGE = "com.shihab.diplay.preface"
    const val REPOSITORY = "https://github.com/huangwits/DiPlay-Preface"
    const val RELEASES_URL = "https://api.github.com/repos/huangwits/DiPlay-Preface/releases?per_page=20"
    const val MAX_APK_SIZE = 128L * 1024 * 1024

    fun latest(json: String): UpdateRelease {
        val entries = JSONArray(json)
        val stable = (0 until entries.length()).mapNotNull(entries::optJSONObject)
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") &&
                UpdateVersion.parse(it.optString("tag_name")) != null }
            .sortedWith { a, b -> UpdateVersion.compare(b.getString("tag_name"), a.getString("tag_name")) }
        val release = stable.firstOrNull() ?: throw IOException("暂未找到可用的星瑞正式版，请稍后重试。")
        val tag = release.getString("tag_name")
        val version = tag.removePrefix("v")
        val name = "DiPlay-Preface-v$version.apk"
        val assets = release.optJSONArray("assets") ?: throw IOException("新版安装包尚未准备好，请稍后重试。")
        val matches = (0 until assets.length()).mapNotNull(assets::optJSONObject).filter { it.optString("name") == name }
        val asset = matches.singleOrNull() ?: throw IOException("新版安装包尚未准备好，请稍后重试。")
        val digest = asset.optString("digest")
        if (!Regex("sha256:[0-9a-fA-F]{64}").matches(digest))
            throw IOException("新版安装包缺少校验信息，请稍后重试。")
        return validate(UpdateRelease(version, name, asset.getString("browser_download_url"),
            asset.getLong("size"), digest.substringAfter(':').lowercase(), release.optString("body").take(12_000)), tag)
    }

    fun restore(json: String): UpdateRelease = JSONObject(json).let {
        validate(UpdateRelease(it.getString("version"), it.getString("apkName"), it.getString("apkUrl"),
            it.getLong("size"), it.getString("sha256"), it.optString("notes").take(12_000)))
    }

    private fun validate(release: UpdateRelease, tag: String = "v${release.version}"): UpdateRelease {
        require(UpdateVersion.parse(release.version) != null && !release.version.startsWith("v"))
        require(release.apkName == "DiPlay-Preface-v${release.version}.apk")
        require(release.apkUrl == "$REPOSITORY/releases/download/$tag/${release.apkName}")
        require(release.size in 1..MAX_APK_SIZE && Regex("[0-9a-f]{64}").matches(release.sha256))
        return release
    }
}

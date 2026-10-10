# DiPlay 星瑞 0.2.16.6

## 中文

授权连接现在内置 Mozilla 公共根证书集合，并保留当前服务使用的 ISRG Root YE。应用先使用车机系统信任，旧车机缺少系统根证书时使用应用内公共根证书集合；仍会校验授权域名、证书签名和有效期。以后授权服务在常见公共 CA 之间续签或切换时，不需要单独更新一张根证书。

本版也包含安卓蓝牙直接连接和应用内更新下载修复。完整 APK 覆盖安装可保留设置和授权信息。

APK：`DiPlay-Preface-v0.2.16.6.apk`，versionCode `50`，包名 `com.shihab.diplay.preface`，最低 Android 5.1 / API 22。

## English

License connections now include the Mozilla public root set plus the current ISRG Root YE. The app tries the vehicle's system trust first and uses its bundled public roots when an older head unit is missing them; hostname, certificate signature and validity checks remain enforced. Renewals or moves among common public CAs should no longer require adding one service-specific root.

This release also includes direct Android Bluetooth connection and in-app update download fixes. Install the full APK over the existing app to keep settings and authorization data.

APK: `DiPlay-Preface-v0.2.16.6.apk`, versionCode `50`, package `com.shihab.diplay.preface`, minimum Android 5.1 / API 22.

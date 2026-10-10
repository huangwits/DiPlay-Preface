# DiPlay 星瑞 0.2.16.4

## 中文

本版修复应用内更新下载失败时的回退路径。更新目录同时保存 GitHub 官方资产接口地址；浏览器下载地址无法建立连接时，会自动尝试官方资产接口，并继续校验文件大小、SHA-256、包名、版本和既有签名。

APK：`DiPlay-Preface-v0.2.16.4.apk`，versionCode `48`，包名 `com.shihab.diplay.preface`，最低 Android 5.1 / API 22。请使用 Release 中的完整 APK 覆盖安装，设置和授权信息会保留。

## English

This release adds a fallback through GitHub's official release-asset API when the browser download URL cannot be opened. The downloaded APK is still checked for its size, SHA-256 digest, package identity, version and existing signing certificate before installation.

APK: `DiPlay-Preface-v0.2.16.4.apk`, versionCode `48`, package `com.shihab.diplay.preface`, minimum Android 5.1 / API 22. Install the full APK from the Release over the existing app to keep settings and authorization data.

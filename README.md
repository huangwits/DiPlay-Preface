# DiPlay Preface · 星瑞 E01

## 中文

当前版本为 0.2.14。修复 USB 配置检查误判与切换后漏通知导致的持续等待，移除 H52 适配；保留简体中文、短屏和日夜模式。

本版聚焦吉利星瑞，已移除 BYD / DiLink 专用功能与旋转屏设置。[功能范围](docs/GEELY-PREFACE-SCOPE.md)。

以 [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay) 为代码基准与默认更新来源，保留原作者 [DiPlay](https://github.com/shihabal3amri/DiPlay) 及其他贡献者署名。当前同步 carlito `8f53b27`，适配 Android 5.1 / API 22 及以上。

[下载 0.2.14 完整安装包](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.14) · [所有发布](https://github.com/huangwits/DiPlay-Preface/releases) · [0.2.14 说明](docs/releases/PREFACE-0.2.14-USB.md)

在 **连接设置 → 蓝牙连接接口 → 选择蓝牙接口** 中手动选择：

| 接口 | 适用方式 |
| --- | --- |
| 系统蓝牙（默认） | 安卓手机或提供标准 Android 蓝牙接口的车机；从系统配对列表选择 iPhone。 |
| E01 车机蓝牙（ECARX） | 具有相应 ECARX 数据接口的车机；先在原厂电话应用配对。 |

切换接口后重新选择 iPhone。原厂接口不会自动启用，也不会在失败后自动切换到其他接口。项目最低系统为 Android 5.1。H52 适配已移除；旧 H52 选择会重置为系统蓝牙并清除该接口保存的手机。E01 实车 CarPlay 连接仍待验证。

安装包名称为 `DiPlay-Preface-v0.2.14.apk`。发布附件只保留安装包、源码包、安装说明和 SHA-256 校验文件，后续沿用[发布规范](docs/RELEASE-POLICY.md)。

只安装完整 APK。包名 `com.shihab.diplay.e01legacy`、原签名保留，可以覆盖升级；新版本为 `0.2.12-e01.14-usb-android51` / versionCode 43。原系统蓝牙和 ECARX 选择继续有效。E01 保留 H.264、30 fps、长边 960 / 短边 540 的低负载显示配置，可在设置中调整性能模式。

源码验证包不含运行认证材料，不用于独立连接 iPhone。旧发布按仓库所有者要求清理，备份保留在维护者本地；Git 源码历史和第三方署名保留。

[构建说明](docs/BUILD.md) · [更新策略](docs/UPSTREAM_SYNC.md) · [来源清单](docs/maintenance-sources.json) · [第三方声明](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

## English

Current release: 0.2.14. Fix USB configuration inspection and rediscovery after missing attach broadcasts, and remove H52 support. Chinese UI, short-screen layout and day/night mode remain.

Focused on Geely Preface: BYD/DiLink integrations and rotating-screen settings removed. [Scope](docs/GEELY-PREFACE-SCOPE.md).

Based on [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay), the default update source, with attribution retained for original [DiPlay](https://github.com/shihabal3amri/DiPlay) and other contributors. Current carlito baseline: `8f53b27`. Minimum Android version: 5.1 / API 22.

[Download the full 0.2.14 package](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.14) · [All releases](https://github.com/huangwits/DiPlay-Preface/releases) · [0.2.14 notes](docs/releases/PREFACE-0.2.14-USB.md)

Open **Connection setup → Bluetooth connection → Choose Bluetooth interface**:

| Interface | Use |
| --- | --- |
| System Bluetooth (default) | Android phones or head units with the standard Android Bluetooth stack. Select the iPhone from the system paired list. |
| E01 car Bluetooth (ECARX) | Head units exposing the matching ECARX data interface. Pair in the factory phone app first. |

Reselect the iPhone after switching interfaces. Vendor transports require manual selection and never silently fall through to another backend. H52 support has been removed; a saved H52 selection resets to system Bluetooth and clears its saved phone. Actual E01 CarPlay connectivity remains unverified.

The installable asset is `DiPlay-Preface-v0.2.14.apk`. Releases contain the APK, source archive, installation guide and SHA-256 checksums; see the [release format](docs/RELEASE-POLICY.md).

Install only the full APK. Version `0.2.12-e01.14-usb-android51` / code 43 retains package `com.shihab.diplay.e01legacy`, signing identity, saved settings and in-place upgrades. The E01 performance profile retains H.264, 30 fps and a proportional 960-by-540 bounding box. Existing system Bluetooth and ECARX selections remain effective.

Source-check APKs have no runtime authentication and cannot independently connect to an iPhone. Previous releases are being removed at the owner's request, with local maintenance backups. Source history and third-party attribution are retained.

[Build](docs/BUILD.md) · [Update policy](docs/UPSTREAM_SYNC.md) · [Sources](docs/maintenance-sources.json) · [Notices](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

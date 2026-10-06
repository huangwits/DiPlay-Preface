# DiPlay Preface · 星瑞 E01 / H52

## 中文

应用仅保留简体中文。E01.13 修复缺少系统 NSD 服务时的接口 mDNS 启动，并让等待连接画面跟随日夜模式；保留短屏适配和手动重试。

本版聚焦吉利星瑞，已移除 BYD / DiLink 专用功能与旋转屏设置。[功能范围](docs/GEELY-PREFACE-SCOPE.md)。

以 [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay) 为代码基准与默认更新来源，保留原作者 [DiPlay](https://github.com/shihabal3amri/DiPlay) 及其他贡献者署名。当前同步 carlito `8f53b27`，适配 Android 5.1 / API 22 及以上。

[下载最新完整安装包](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.13-zhcn) · [所有发布](https://github.com/huangwits/DiPlay-Preface/releases) · [E01.13 说明](docs/releases/PREFACE-0.2.12-E01.13-ZHCN.md)

在 **连接设置 → 蓝牙连接接口 → 选择蓝牙接口** 中手动选择：

| 接口 | 适用方式 |
| --- | --- |
| 系统蓝牙（默认） | 安卓手机或提供标准 Android 蓝牙接口的车机；从系统配对列表选择 iPhone。 |
| E01 车机蓝牙（ECARX） | 具有相应 ECARX 数据接口的车机；先在原厂电话应用配对。 |
| H52 车机蓝牙（ANW） | 具有匹配 ANW 服务的 H52 车机；先在原厂电话应用配对。 |

切换接口后重新选择 iPhone。原厂接口不会自动启用，也不会在失败后自动切换到其他接口。H52 移植范围是蓝牙数据接口，项目最低系统仍为 Android 5.1，不包含 Android 4.3 整套兼容层。E01 / H52 实车 CarPlay 连接仍待验证。

只安装完整 APK。包名 `com.shihab.diplay.e01legacy`、原签名保留，可以覆盖升级；新版本为 `0.2.12-e01.13-zhcn-android51` / versionCode 42。已明确保存的接口选择继续有效。E01 保留 H.264、30 fps、长边 960 / 短边 540 的低负载显示配置，可在设置中调整性能模式。

源码验证包不含运行认证材料，不用于独立连接 iPhone。旧发布按仓库所有者要求清理，备份保留在维护者本地；Git 源码历史和第三方署名保留。

[构建说明](docs/BUILD.md) · [更新策略](docs/UPSTREAM_SYNC.md) · [来源清单](docs/maintenance-sources.json) · [第三方声明](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

## English

The app interface is Simplified Chinese only. E01.13 defers NSD access for interface mDNS and follows day/night mode on the waiting screen, preserving short-screen layout and manual retry.

Focused on Geely Preface: BYD/DiLink integrations and rotating-screen settings removed. [Scope](docs/GEELY-PREFACE-SCOPE.md).

Based on [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay), the default update source, with attribution retained for original [DiPlay](https://github.com/shihabal3amri/DiPlay) and other contributors. Current carlito baseline: `8f53b27`. Minimum Android version: 5.1 / API 22.

[Download the latest full package](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.13-zhcn) · [All releases](https://github.com/huangwits/DiPlay-Preface/releases) · [E01.13 notes](docs/releases/PREFACE-0.2.12-E01.13-ZHCN.md)

Open **Connection setup → Bluetooth connection → Choose Bluetooth interface**:

| Interface | Use |
| --- | --- |
| System Bluetooth (default) | Android phones or head units with the standard Android Bluetooth stack. Select the iPhone from the system paired list. |
| E01 car Bluetooth (ECARX) | Head units exposing the matching ECARX data interface. Pair in the factory phone app first. |
| H52 car Bluetooth (ANW) | H52 units with a matching ANW service. Pair in the factory phone app first. |

Reselect the iPhone after switching interfaces. Vendor transports require manual selection and never silently fall through to another backend. The H52 import covers its Bluetooth transport, not the full Android 4.3 compatibility layer. Actual E01/H52 CarPlay connectivity remains unverified.

Install only the full APK. Version `0.2.12-e01.13-zhcn-android51` / code 42 retains package `com.shihab.diplay.e01legacy`, signing identity, saved settings and in-place upgrades. The E01 performance profile retains H.264, 30 fps and a proportional 960-by-540 bounding box. Explicit saved interface choices remain effective.

Source-check APKs have no runtime authentication and cannot independently connect to an iPhone. Previous releases are being removed at the owner's request, with local maintenance backups. Source history and third-party attribution are retained.

[Build](docs/BUILD.md) · [Update policy](docs/UPSTREAM_SYNC.md) · [Sources](docs/maintenance-sources.json) · [Notices](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

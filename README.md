# DiPlay Preface · 星瑞 E01

面向 **2020 款吉利星瑞 / GKUI / 亿咖通 E01 / Android 5.1** 的 CarPlay 接收端适配项目。

[下载 E01.8 原厂蓝牙实验版完整 APK](https://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.12-preface-e01.8-factorybt/DiPlay-Preface-0.2.12-E01.8-FactoryBT-Android51-full.apk) · [版本说明](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.8-factorybt) · [历史版本](https://github.com/huangwits/DiPlay-Preface/releases)

## 中文

本地候选 [E01.10](docs/releases/PREFACE-0.2.12-E01.10-SYSTEMBT.md) 恢复系统蓝牙默认路径；原厂 ECARX / H52 改为手动启用，保留已保存的选择。手机与实车连接待复测，尚未发布到下载链接。

### 项目与维护

以原作者 [DiPlay](https://github.com/shihabal3amri/DiPlay) 为代码基线，持续跟进原作者更新；按需引入 [carlito 吉利版](https://github.com/carlito12345/DiPlay) 的车型适配，维护 Android 5.1 / API 22 和 E01 车机兼容性。

`main` 为长期维护分支，上游更新经兼容性检查后合入。适配重点包括音频、视频、系统服务、权限、USB 和车机连接。已有代码的来源与许可记录保留在[第三方声明](docs/THIRD_PARTY_NOTICES.md)。

### 安装

1. 下载完整 APK，复制到车机后通过文件管理器安装。
2. 应用名为 **DiPlay E01 Legacy**，包名 `com.shihab.diplay.e01legacy`。已安装 E01.2 / E01.3 / E01.4 / E01.5 可直接覆盖升级，保留设置。
3. 当前版本为 `0.2.12-e01.8-factorybt-android51` / versionCode 37，最低 Android 5.1 / API 22，支持 ARMv7 / ARM64。

安装请使用完整 APK；普通 CI 源码检查包不用于独立车测，GitHub 的 Source code 压缩包也不能安装。

### 适配状态

- [E01.8 原厂蓝牙实验版](docs/releases/PREFACE-0.2.12-E01.8-FACTORYBT.md) 同步原作者主线 `e6477e1`，包含音频缓冲、触控延迟、Wi-Fi/USB 修复及设置重构；保留 API 22 兼容性，默认尝试通过 ECARX SPP 接口连接。新增可选 H52 ANW 数据后端，默认仍为 ECARX；可在连接设置中选择接口并重新读取原厂配对列表。先在原厂电话应用配对 iPhone，再在 DiPlay 重新选择手机。实车连接尚未验证；失败时记录 `E01-Fxx` 错误码。
- E01.4 改为原作者主线维护，保留所需吉利功能与 E01 修复，清理 Android 4.3 专用兼容代码。
- E01 默认 H.264、30 fps，画布长边不超过 960、短边不超过 540，保持比例。
- E01.3 修复旧预览包缺少认证资料造成的启动失败，已通过认证加载和覆盖升级验证。
- nFore/ECARX 原车连接识别依赖实际固件接口，不能替代蓝牙数据通道；实车蓝牙、iPhone 握手及音视频兼容性仍待验证。

[构建说明](docs/BUILD.md) · [发布说明](docs/releases/PREFACE-0.2.12-E01.8-FACTORYBT.md) · [第三方声明](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

---

## English

A CarPlay receiver adaptation for the **2020 Geely Preface / GKUI / ECARX E01 / Android 5.1**.

[Download the E01.8 factory Bluetooth experimental full APK](https://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.12-preface-e01.8-factorybt/DiPlay-Preface-0.2.12-E01.8-FactoryBT-Android51-full.apk) · [Release notes](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.8-factorybt) · [Release history](https://github.com/huangwits/DiPlay-Preface/releases)

### Project and maintenance

Local candidate [E01.10](docs/releases/PREFACE-0.2.12-E01.10-SYSTEMBT.md) restores Android Bluetooth by default and keeps ECARX / H52 as explicit options, preserving saved choices. Phone and vehicle connectivity remain unverified; the download links still point to the published release.

Based directly on the original [DiPlay project](https://github.com/shihabal3amri/DiPlay), this project follows original-author updates and selectively imports vehicle adaptations from [carlito’s Geely fork](https://github.com/carlito12345/DiPlay), maintaining Android 5.1 / API 22 and E01 compatibility.

`main` is the long-lived maintenance branch. Upstream updates are integrated after compatibility checks. Adaptation covers audio, video, system services, permissions, USB and head-unit connectivity. Existing code provenance and licenses are retained in the [third-party notices](docs/THIRD_PARTY_NOTICES.md).

### Installation

1. Download the full APK, transfer it to the head unit and install it with the file manager.
2. The app is **DiPlay E01 Legacy**, package `com.shihab.diplay.e01legacy`. E01.2 / E01.3 / E01.4 / E01.5 users can update in place and retain settings.
3. The current version is `0.2.12-e01.8-factorybt-android51` / versionCode 37, requiring Android 5.1 / API 22 or later, with ARMv7 / ARM64 support.

Use the full APK for installation. Ordinary CI source-check packages are not standalone car-test builds, and GitHub Source code archives are not installable.

### Adaptation status

- [E01.8 factory Bluetooth experiment](docs/releases/PREFACE-0.2.12-E01.8-FACTORYBT.md) integrates original main `e6477e1`, including buffered audio, touch latency, Wi-Fi/USB fixes and modular settings, while retaining API 22 compatibility and the default ECARX SPP experiment. An optional H52 ANW transport can be selected in Connection setup; ECARX remains the default. Pair in the factory phone app and reselect the iPhone in DiPlay. Vehicle connectivity remains unverified; record any `E01-Fxx` error.
- E01.4 follows the original-author baseline, retains selected Geely integrations and E01 fixes, and removes Android 4.3-only compatibility code.
- E01 defaults to H.264 at 30 fps, preserving aspect ratio within a 960-pixel long edge and a 540-pixel short edge.
- E01.3 fixes the missing-authentication startup failure in earlier previews. Authentication loading and in-place upgrade checks passed.
- nFore/ECARX factory connection detection depends on firmware interfaces and does not replace the Bluetooth data channel. Vehicle Bluetooth, iPhone handshakes and audiovisual compatibility still require vehicle testing.

[Build instructions](docs/BUILD.md) · [Release notes](docs/releases/PREFACE-0.2.12-E01.8-FACTORYBT.md) · [Third-party notices](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

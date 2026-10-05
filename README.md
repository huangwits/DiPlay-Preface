# DiPlay Preface · 星瑞 E01

面向 **2020 款吉利星瑞 / GKUI / 亿咖通 E01 / Android 5.1** 的 CarPlay 接收端适配项目。

[下载 E01.4 完整 APK](https://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.12-preface-e01.4/DiPlay-Preface-0.2.12-E01.4-Android51-full.apk) · [版本说明](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.4) · [历史版本](https://github.com/huangwits/DiPlay-Preface/releases)

## 中文

### 项目与维护

以原作者 [DiPlay](https://github.com/shihabal3amri/DiPlay) 为代码基线，持续跟进原作者更新；按需引入 [carlito 吉利版](https://github.com/carlito12345/DiPlay) 的车型适配，维护 Android 5.1 / API 22 和 E01 车机兼容性。

`main` 为长期维护分支，上游更新经兼容性检查后合入。适配重点包括音频、视频、系统服务、权限、USB 和车机连接。已有代码的来源与许可记录保留在[第三方声明](docs/THIRD_PARTY_NOTICES.md)。

### 安装

1. 下载完整 APK，复制到车机后通过文件管理器安装。
2. 应用名为 **DiPlay E01 Legacy**，包名 `com.shihab.diplay.e01legacy`。已安装 E01.2 / E01.3 可直接覆盖升级，保留设置。
3. 当前版本为 `0.2.12-e01.4-android51` / versionCode 33，最低 Android 5.1 / API 22，支持 ARMv7 / ARM64。

安装请使用完整 APK；普通 CI 源码检查包不用于独立车测，GitHub 的 Source code 压缩包也不能安装。

### 适配状态

- E01.4 改为原作者主线维护，保留所需吉利功能与 E01 修复，清理 Android 4.3 专用兼容代码。
- E01 默认 H.264、30 fps，画布长边不超过 960、短边不超过 540，保持比例。
- E01.3 修复旧预览包缺少认证资料造成的启动失败，已通过认证加载和覆盖升级验证。
- nFore/ECARX 原车连接识别依赖实际固件接口，不能替代蓝牙数据通道；实车蓝牙、iPhone 握手及音视频兼容性仍待验证。

[构建说明](docs/BUILD.md) · [发布说明](docs/releases/PREFACE-0.2.12-E01.4.md) · [第三方声明](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

---

## English

A CarPlay receiver adaptation for the **2020 Geely Preface / GKUI / ECARX E01 / Android 5.1**.

[Download the E01.4 full APK](https://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.12-preface-e01.4/DiPlay-Preface-0.2.12-E01.4-Android51-full.apk) · [Release notes](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.4) · [Release history](https://github.com/huangwits/DiPlay-Preface/releases)

### Project and maintenance

Based directly on the original [DiPlay project](https://github.com/shihabal3amri/DiPlay), this project follows original-author updates and selectively imports vehicle adaptations from [carlito’s Geely fork](https://github.com/carlito12345/DiPlay), maintaining Android 5.1 / API 22 and E01 compatibility.

`main` is the long-lived maintenance branch. Upstream updates are integrated after compatibility checks. Adaptation covers audio, video, system services, permissions, USB and head-unit connectivity. Existing code provenance and licenses are retained in the [third-party notices](docs/THIRD_PARTY_NOTICES.md).

### Installation

1. Download the full APK, transfer it to the head unit and install it with the file manager.
2. The app is **DiPlay E01 Legacy**, package `com.shihab.diplay.e01legacy`. E01.2 / E01.3 users can update in place and retain settings.
3. The current version is `0.2.12-e01.4-android51` / versionCode 33, requiring Android 5.1 / API 22 or later, with ARMv7 / ARM64 support.

Use the full APK for installation. Ordinary CI source-check packages are not standalone car-test builds, and GitHub Source code archives are not installable.

### Adaptation status

- E01.4 follows the original-author baseline, retains selected Geely integrations and E01 fixes, and removes Android 4.3-only compatibility code.
- E01 defaults to H.264 at 30 fps, preserving aspect ratio within a 960-pixel long edge and a 540-pixel short edge.
- E01.3 fixes the missing-authentication startup failure in earlier previews. Authentication loading and in-place upgrade checks passed.
- nFore/ECARX factory connection detection depends on firmware interfaces and does not replace the Bluetooth data channel. Vehicle Bluetooth, iPhone handshakes and audiovisual compatibility still require vehicle testing.

[Build instructions](docs/BUILD.md) · [Release notes](docs/releases/PREFACE-0.2.12-E01.4.md) · [Third-party notices](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

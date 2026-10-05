# DiPlay Preface 0.2.12 · E01.4

## 中文

以原作者 DiPlay 0.2.12 为维护基线，按需保留吉利车型功能，集中维护 Android 5.1 / API 22 和 E01。新更新流程默认跟进原作者，吉利修复以指定提交单独引入。

### 变化

- 迁移并保留 E01 音频、视频、USB、网络、厂商连接识别和完整认证加载修复。
- 清理 Android 4.3 专用探针、启动依赖、主题和旧 API 分支；保留 Android 5.1 必需的回退。
- README 和发布说明统一为中文在前、英文在后。

### 安装

下载 `DiPlay-Preface-0.2.12-E01.4-Android51-full.apk`，传到车机后通过文件管理器安装。应用为 **DiPlay E01 Legacy**，包名 `com.shihab.diplay.e01legacy`；版本 `0.2.12-e01.4-android51`，versionCode 33，最低 API 22，ARMv7 / ARM64。与 E01.3 使用相同包名和签名，可覆盖升级并保留设置。

本包包含已有的实验性本地认证资料。实车蓝牙、iPhone 握手及音视频兼容性仍待验证；本次维护迁移不代表已修复厂商蓝牙数据通道。旧版本与源码标签保留以便追溯。

---

## English

The maintenance baseline is now original DiPlay 0.2.12, with selected Geely integrations and focused Android 5.1 / API 22 support for E01. Updates follow the original author by default; Geely fixes are selected as individual commits.

### Changes

- Retain E01 audio, video, USB, networking, factory connection detection and complete authentication-loading fixes.
- Remove Android 4.3-only probes, startup dependencies, themes and obsolete API branches while keeping Android 5.1 fallbacks.
- Keep Chinese above English in one README and in release descriptions.

### Installation

Download `DiPlay-Preface-0.2.12-E01.4-Android51-full.apk`, transfer it to the head unit and install with the file manager. App: **DiPlay E01 Legacy**; package: `com.shihab.diplay.e01legacy`; version: `0.2.12-e01.4-android51`; versionCode: 33; minimum API 22; ARMv7 / ARM64. Its package and signer match E01.3 for in-place upgrades that retain settings.

The build contains the existing experimental local authentication input. Vehicle Bluetooth, iPhone handshakes and audiovisual compatibility remain unverified. This migration does not establish a working vendor Bluetooth data channel. Previous releases and source tags remain available for traceability.

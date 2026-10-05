# DiPlay Preface 0.2.11 · E01.1

## 中文

适用目标：2020 款吉利星瑞 / GKUI / 亿咖通 E01（MT6735）/ Android 5.1。

**公开 APK 为源码验证包，不含配件认证材料，不能独立完成 iPhone 连接。实车连接及音视频兼容性尚未完成验证。**

### 下载

展开下方 **Assets**，直接下载主程序 APK，或下载 ZIP 后解压。

| 文件 | 说明 |
| --- | --- |
| `DiPlay-Preface-0.2.11-E01-Android51-source-only.apk` | 主程序，最低 API 22，支持 ARMv7 / ARM64。 |
| `DiPlay-Preface-v0.2.11-preface-e01.1-installers.zip` | 主程序 APK 与中文安装说明。 |
| `INSTALL-README.zh-CN.md` | 安装说明与版本信息。 |
| `SHA256SUMS.txt` | 下载文件的 SHA-256 校验值。 |

GitHub 自动生成的 Source code 压缩包是源码，不能作为 APK 安装。

### 安装

停车后，通过可用的 U 盘或文件传输方式将 APK 放入车机，用文件管理器安装。应用名为 **DiPlay E01 Legacy**，包名 `com.shihab.diplay.e01legacy`。与同包名、同签名的旧版兼容覆盖升级。

### 版本内容

基于 carlito 0.2.11，补齐旧安卓兼容路径与 E01 低负载设置。此为历史预览版，建议优先查看 0.2.12。

E01 默认 H.264、30 fps，画布长边不超过 960、短边不超过 540，保持比例。依赖车型的功能以固件实际支持为准。

### 验证

主程序 833 项本地测试、API 兼容检查及 APK 签名验证通过。主程序 APK 保持原发布文件不变，本次只整理说明与安装合集。


---

## English

Historical source preview for the **2020 Geely Preface / GKUI / ECARX E01 (MT6735) / Android 5.1**. Based on carlito 0.2.11, with legacy Android compatibility and E01 performance settings.

**This source-only APK does not include accessory authentication and cannot independently complete an iPhone connection. For installation, use the [E01.3 full build](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.3).**

### Assets and installation

Assets contain the source-only APK, an installation ZIP, Chinese installation notes and SHA-256 checksums. GitHub Source code archives cannot be installed as APKs.

The app is **DiPlay E01 Legacy**, package `com.shihab.diplay.e01legacy`, with Android API 22 minimum and ARMv7 / ARM64 support. Matching package names and signing certificates allow in-place upgrades. Transfer an installable APK to the parked head unit and use its file manager.

### Scope and validation

E01 defaults to H.264 at 30 fps with an aspect-preserving 960-pixel long-edge and 540-pixel short-edge limit. Firmware-dependent functions require actual head-unit support.

The original build passed 833 local tests, Android API checks and APK signature verification. The APK remains unchanged. Vehicle connections and audiovisual compatibility have not been validated.

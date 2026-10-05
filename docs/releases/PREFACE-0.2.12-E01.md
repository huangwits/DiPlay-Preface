# DiPlay Preface 0.2.12 · E01 Android 5.1 预览版

适用目标：2020 款吉利星瑞 / GKUI / 亿咖通 E01（MT6735）/ Android 5.1。

**公开 APK 为源码验证包，不含配件认证材料，不能独立完成 iPhone 连接。实车连接及音视频兼容性尚未完成验证。**

## 下载

展开下方 **Assets**，直接下载主程序 APK，或下载 ZIP 后解压。

| 文件 | 说明 |
| --- | --- |
| `DiPlay-Preface-0.2.12-E01-Android51-source-only.apk` | 主程序，最低 API 22，支持 ARMv7 / ARM64。 |
| `DiPlay-Preface-v0.2.12-preface-e01.1-installers.zip` | 主程序 APK 与中文安装说明。 |
| `INSTALL-README.zh-CN.md` | 安装说明与版本信息。 |
| `SHA256SUMS.txt` | 下载文件的 SHA-256 校验值。 |

GitHub 自动生成的 Source code 压缩包是源码，不能作为 APK 安装。

## 安装

停车后，通过可用的 U 盘或文件传输方式将 APK 放入车机，用文件管理器安装。应用名为 **DiPlay E01 Legacy**，包名 `com.shihab.diplay.e01legacy`。与同包名、同签名的旧版兼容覆盖升级。

## 版本内容

同步上游 0.2.12，保留 Android 5.1 兼容路径与 E01 低负载设置，更新连接流程、分辨率、方向盘、仪表显示及吉利音频 / HUD。

E01 默认 H.264、30 fps，画布长边不超过 960、短边不超过 540，保持比例。依赖车型的功能以固件实际支持为准。

## 验证

主程序 1,264 项本地测试通过，API 兼容检查与 APK 签名验证通过。[对应源码的 GitHub 构建检查](https://github.com/huangwits/DiPlay-Preface/actions/runs/37245790738)。

APK 构建源码：`17e526fe1e169b82fe1230cb700c4fc2f9065911`。发布标签另包含 README 与分发配置整理，主程序 APK 内容保持不变。

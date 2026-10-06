# E01.13 · 简体中文与连接界面修复 / Chinese interface and connection fixes

## 中文

版本 `0.2.12-e01.13-zhcn-android51`，versionCode 42。

- 应用仅保留简体中文，移除语言选择入口及其他翻译。旧版本保存的应用语言迁移为中文，蓝牙、配对、显示等其他设置保留。
- 修复缺少 Android NSD 服务的车机在构造接口 mDNS 时失败的问题。系统 NSD 路径仍明确报告缺失服务，不伪装连接成功。
- 等待连接画面跟随现有日夜模式即时刷新背景和文字，保留固定日间/夜间与自动模式、短屏布局、手动重试。
- 保留系统蓝牙默认、E01 ECARX / H52 ANW 手动接口、吉利 HUD / 方向盘和 API 22 低负载适配。

直接覆盖安装完整 `-full.apk`；保留包名 `com.shihab.diplay.e01legacy` 与原签名，最低 Android 5.1，支持 ARMv7 / ARM64。本次不改成原作者完整 0.2.13，只移植已审查的两个修复及配套测试。原作者来源：`32550b2`、`690945d`、`1240f8e`。

实车与 iPhone 连接仍需验证，桌面回归不代表实车连接成功。各项构建结果见附件 `validation.json` 和 `apk-verification.json`。

## English

Version `0.2.12-e01.13-zhcn-android51`, code 42. The app now uses Simplified Chinese only and migrates old language preferences while retaining unrelated settings. Interface mDNS no longer eagerly requires Android NSD. Waiting-screen colors follow the existing day/night mode without replacing short-screen layout or manual retry.

System Bluetooth remains default; manual E01 ECARX/H52 ANW, Geely integrations and API 22 performance adaptations remain. This release selectively ports original commits `32550b2`, `690945d`, and `1240f8e`; it is not a wholesale upstream 0.2.13 update.

Install the full APK over the existing app. Package/signing identity is preserved, minimum Android is 5.1, ARMv7/ARM64 are included. Vehicle/iPhone connectivity is not established by desktop tests. See the attached validation records.

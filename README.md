# GeelyPlay 星瑞 / Preface

面向吉利星瑞 E01（MT6735）车机的 CarPlay 适配版，支持 Android 5.1，界面为简体中文。整体界面与基础功能以原作者 DiPlay 为基准，并加入吉利车机适配。

[下载安装包](https://github.com/huangwits/DiPlay-Preface/releases/latest) · [0.2.20.39 更新说明](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.20.39)

## 安装与升级

当前版本 **0.2.20.39**，修复部分 iPhone 在连接初期因空视频配置导致 CarPlay 退出并反复重连的问题，并包含此前的首页、无线申请入口及按钮样式改进。

- 已安装相同签名的 **0.2.20.25–0.2.20.38** 本地版本，可直接覆盖升级，保留设置和有效授权。已安装 0.2.20.39 无需重复安装。
- 从旧公开版 **0.2.16.20** 升级时，因签名不同，需要先卸载旧版；本地设置会清除，请按页面提示重新完成无线设置与授权。
- 安装包为 `GeelyPlay-v0.2.20.39.apk`，适用于 Android 5.1 / API 22 及以上的目标车机。请在安全停车后操作。

## 连接

- **USB**：连接 iPhone 后按界面提示操作，无需软件激活。
- **无线**：点击首页“连接 CarPlay”；未授权设备直接进入申请页面。首页“无线设置”可随时查看申请状态、选择手机及使用蓝牙工具。
- 已确认的有效无线授权支持离线连接。连接后有网络时会在后台核验；待审、撤销或到期的授权仍需按页面提示处理。

首次使用无线 CarPlay 时按应用内说明完成蓝牙工具准备；以后使用首页连接入口即可。

## 使用须知

仪表地图仍可能出现与中控视角不同、行驶中地图与仪表交替显示的问题；**本版不宣称解决这两项问题**。兼容性须按具体车机、固件及 iPhone 组合验证。

这是社区维护的非官方项目，与 Apple、吉利没有隶属、授权或认证关系。软件按现状提供，不保证持续可用或未来系统兼容。蓝牙维护涉及系统组件，操作前请确认备份和恢复方法。

[完整免责声明](docs/DISCLAIMER.md) · [第三方声明](docs/THIRD_PARTY_NOTICES.md)

## 从源码构建

使用 JDK 25、Android SDK 37、Build Tools 37 和 NDK 28.2.13676358。开发检查可运行 `./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:assembleDebug`；普通 debug 包不含独立运行所需的认证身份。

完整车机包需要自行提供源码之外的本地认证、蓝牙组件、地图配置、授权公共配置及签名输入，对应 `DIPLAY_AUTH_ASSETS_DIR`、`DIPLAY_GOC_ASSETS_DIR`、`DIPLAY_E01_MAP_CONFIG_DIR`、`DIPLAY_LICENSE_ASSETS_DIR` 和 `ANDROID_KEYSTORE_*` 环境变量。配置后运行 `./gradlew :mobile:assembleE01Licensed`；升级安装必须保持原签名。私钥及生成的运行文件不要提交到仓库。

## 致谢与许可

基于原作者 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)，并参考 [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay) 的吉利适配。感谢 [xcertplay](https://github.com/shilapi/xcertplay) 与 [DiAuto](https://github.com/shihabal3amri/DiAuto) 的原作者及贡献者。

源码保留在本仓库，使用、修改和再分发请遵守 [GPL-3.0](LICENSE) 及适用组件的 [AGPL-3.0](docs/licenses/DiAuto-AGPL-3.0.txt)，保留原作者署名和第三方声明。CarPlay 及相关商标属于各自权利人。

## English

GeelyPlay is a community CarPlay adaptation for Geely E01 / MT6735 head units running Android 5.1, with a Simplified Chinese interface. **0.2.20.39** fixes repeated session termination when an iPhone sends an empty video configuration during startup, and includes the updated home screen and wireless setup controls.

The APK can update same-signer local versions 0.2.20.25–0.2.20.38 while preserving settings and valid approval. Public 0.2.16.20 uses a different signer and must be uninstalled first; local settings are cleared. Follow the in-app wireless setup and approval prompts afterward. USB requires no software activation; confirmed wireless approval supports offline startup.

Instrument-map camera synchronization and intermittent switching between map and gauges remain unresolved. Compatibility depends on the vehicle, firmware and iPhone. Download from [Releases](https://github.com/huangwits/DiPlay-Preface/releases/latest), read the [release notes](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.20.39), and perform setup while parked.

Source, upstream attribution and applicable GPL-3.0 / AGPL-3.0 licenses are preserved. This is an unofficial project with no Apple or Geely affiliation or certification.

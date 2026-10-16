# GeelyPlay 星瑞 / Preface

面向吉利星瑞 E01（MT6735）车机的 CarPlay 适配版，支持 Android 5.1，界面为简体中文。整体界面与基础功能以原作者 DiPlay 为基准，仅加入吉利车机适配和本地维护功能。

[下载安装包](https://github.com/huangwits/DiPlay-Preface/releases/latest) · [更新说明](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.20.1)

## 安装

当前版本 **0.2.20.1**，以原作者 DiPlay **0.2.20** 为基线并保留星瑞 API 22 适配。此为选择性同步，不代表所有上游 API 25+ 功能均已合入。

**重要：** 本版签名已更换，不能直接覆盖公开版 0.2.16.20。安装前先卸载旧版，再安装本版；旧版的本地设置会清除，无线授权需重新申请。请在安全停车后操作。

## 连接

- **USB**：连接 iPhone 后按界面提示操作，无需软件激活。
- **无线**：进入“连接 → 蓝牙工具”申请授权，通过后点击“连接 CarPlay”。已授权设备再次进入会自动联网核验，无需重复申请。本次重装后需重新申请一次。

首次使用无线 CarPlay 时按页面提示完成一次蓝牙工具准备；日常使用直接点击一键连接。更多选项只保留维护和模式切换操作。

## 使用须知

本项目主要适配星瑞 E01，不保证兼容其他车型或固件。连接、音频、通话和 Siri 等功能仍需按实际车机与 iPhone 组合验证。

这是社区维护的非官方项目，与 Apple、吉利没有隶属、授权或认证关系。软件按现状提供，不保证持续可用或未来系统兼容。蓝牙维护涉及系统组件，操作前请确认备份和恢复方法。

[完整免责声明](docs/DISCLAIMER.md) · [第三方声明](docs/THIRD_PARTY_NOTICES.md)

安装升级前请阅读 [0.2.20.1 更新说明](https://github.com/huangwits/DiPlay-Preface/releases)，特别是签名更换带来的重装要求。

## 致谢与许可

基于原作者 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)，并参考 [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay) 的吉利适配。感谢 [xcertplay](https://github.com/shilapi/xcertplay) 与 [DiAuto](https://github.com/shihabal3amri/DiAuto) 的原作者及贡献者。

源码保留在本仓库，使用、修改和再分发请遵守 [GPL-3.0](LICENSE) 及适用组件的 [AGPL-3.0](docs/licenses/DiAuto-AGPL-3.0.txt)，保留原作者署名和第三方声明。CarPlay 及相关商标属于各自权利人。

## English

DiPlay Preface is a community CarPlay adaptation for Geely E01 / MT6735 head units running Android 5.1, with a Simplified Chinese interface. Current version: **0.2.20.1**, based on original DiPlay **0.2.20** with the Preface API 22 adaptation. Upstream-only features that require API 25 or other head units are not implied to be included.

**Upgrade warning:** This version uses a different signing certificate from public 0.2.16.20 and cannot update it in place. Uninstall the previous app before installing this release. Local settings are cleared and wireless authorization must be requested again.

Download the APK from [Releases](https://github.com/huangwits/DiPlay-Preface/releases/latest). Version 0.2.20.1 uses a new signing certificate and cannot update 0.2.16.20 in place. Uninstall the old app first; local settings are cleared and wireless authorization must be requested again. Complete setup while safely parked.

USB requires no software activation. Wireless connections require approval and online revalidation. Submit a new wireless request after this required reinstall. Follow the in-app Bluetooth setup guide; the compatibility test does not start CarPlay projection.

Compatibility depends on the vehicle, firmware and iPhone. This is an unofficial project with no Apple or Geely affiliation or certification. Read the [disclaimer](docs/DISCLAIMER.md) and [third-party notices](docs/THIRD_PARTY_NOTICES.md) before use.

Based on the original DiPlay, with Geely adaptations referenced from Carlito's DiPlay; xcertplay and DiAuto remain credited. Source, upstream attribution and applicable GPL-3.0 / AGPL-3.0 licenses are preserved in this repository.

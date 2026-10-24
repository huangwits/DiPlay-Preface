# GeelyPlay · 吉利 E01

基于 DiPlay 的 CarPlay 社区适配项目，主要面向 20/21 款吉利星瑞 E01（MT6735）车机，适配 Android 5.1，提供简体中文界面。其他 E01 车型的兼容性仍需结合车机型号与固件验证。

[下载安装包](https://github.com/huangwits/DiPlay-Preface/releases/latest) · [使用说明](docs/USAGE.md) · [文档目录](docs/README.md) · [问题反馈](https://github.com/huangwits/DiPlay-Preface/issues/new/choose)

## 下载与安装

当前公开版本为 **0.2.20.39**，安装包名为 `GeelyPlay-v0.2.20.39.apk`。完整更新内容见 [Release 说明](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.20.39)。

| 项目 | 说明 |
| --- | --- |
| 目标车机 | 吉利 E01 / MT6735，主要适配星瑞 |
| 系统要求 | Android 5.1 / API 22 及以上的目标车机 |
| USB 连接 | 按应用提示连接 iPhone，无需软件激活 |
| 无线连接 | 按应用提示完成设置与授权 |

请在安全停车后安装与设置。**从旧公开版 0.2.16.20 升级时，因签名不同需要先卸载旧版，本地设置会清除。** 同签名版本的升级方式见 [安装与升级](docs/USAGE.md#安装与升级)。

## 开始使用

1. 从本仓库的 [Releases](https://github.com/huangwits/DiPlay-Preface/releases) 下载 APK 并安装。
2. USB 连接按应用内提示操作；无线连接先完成首页“无线设置”中的准备步骤。
3. 点击“连接 CarPlay”。未授权的无线设备会进入申请页面，可在“无线设置”查看状态。

连接方式、授权状态和诊断报告的说明见 [使用说明](docs/USAGE.md)。

## 已知问题

- 仪表地图可能与中控地图视角不同。
- 行驶中仍可能出现地图与仪表交替显示的问题。
- 连接、音频及车机控制表现受具体车机、固件和 iPhone / iOS 组合影响。

当前公开版尚未解决前两项问题。反馈时请提供实际安装版本和故障发生时间，具体方法见 [问题反馈](docs/USAGE.md#问题反馈)。

## 源码与文档

本仓库保留上游代码及其适配修改。部分独立新增实现已撤回，**当前公开源码不能直接构建，也不是已发布 APK 的完整对应源码**。仓库内容及构建状态见 [源码说明](SOURCE_SCOPE.md)。

文档统一收录在 [文档目录](docs/README.md)，包括使用说明、安全说明、免责声明与第三方声明。

## 致谢与许可

由 [huangwits](https://github.com/huangwits) 维护，基于 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)，并参考 [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay) 的吉利适配。感谢 [xcertplay](https://github.com/shilapi/xcertplay)、[DiAuto](https://github.com/shihabal3amri/DiAuto) 及相关贡献者。

使用、修改和再分发相关代码时，请遵守 [GPL-3.0](LICENSE) 及适用组件的 [AGPL-3.0](docs/licenses/DiAuto-AGPL-3.0.txt)，保留原作者署名和 [第三方声明](docs/THIRD_PARTY_NOTICES.md)。

本项目与 Apple、吉利及相关车厂没有隶属、授权或认证关系。名称、商标和图标属于各自权利人。使用前请阅读 [免责声明](docs/DISCLAIMER.md)。

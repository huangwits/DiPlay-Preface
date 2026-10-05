# DiPlay Preface · 星瑞 E01

面向 **2020 款吉利星瑞 / GKUI / 亿咖通 E01 / Android 5.1** 的 CarPlay 接收端适配项目。

[下载 E01.3 完整 APK](https://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.12-preface-e01.3/DiPlay-Preface-0.2.12-E01.3-Android51-full.apk) · [版本说明与附件](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.3) · [历史版本](https://github.com/huangwits/DiPlay-Preface/releases) · [English](README.en.md)

> E01.3 修复旧源码预览包启动时“无法加载 CarPlay 认证资料”的问题，包含本地实验性配件身份。实车蓝牙、iPhone 握手与音视频兼容性仍待验证。

## 安装

1. 下载上方 **full.apk**，通过手机或 U 盘复制到车机，用文件管理器安装。
2. 已装 E01.2 的用户直接覆盖安装，保留设置。应用名 **DiPlay E01 Legacy**，包名 `com.shihab.diplay.e01legacy`。
3. 新版为 `0.2.12-e01.3-android51` / versionCode 32，支持 ARMv7 / ARM64，最低 API 22。

Android43 仓库的应用使用另一包名，会与本项目并存。**旧的 source-only 包缺少独立启动所需资料，请使用上方完整包。** GitHub 自动生成的 Source code 压缩包是源码，不能安装。

Release 提供主程序 APK、含 APK 和中文说明的安装合集、SHA-256 校验文件。

## 适配与验证

- 保留 Android 5.1 的音频、系统服务、权限、USB 和视频兼容路径。
- E01 默认 H.264、30 fps，画布长边不超过 960、短边不超过 540，保持比例。
- 包含 DiPlay 0.2.12、吉利车型适配及 nFore/ECARX 原车连接识别；后者依赖实际固件接口，不能替代标准蓝牙数据通道。
- 完整包校验认证文件匹配及打包完整性；认证加载验证不代表实车连接已通过。

## 来源与开发

以 [carlito 吉利版](https://github.com/carlito12345/DiPlay) 为基础，整合原作者 [DiPlay](https://github.com/shihabal3amri/DiPlay) 与 [Android43 兼容代码](https://github.com/xikai6282/DiPlay-Geely-Android43)，参考用户提供的 EasyPlay APK 接口行为。

`main` 为长期维护分支，更新通过临时分支评审后合入。普通 Actions 构建用于源码检查，不含配件身份；独立车测使用 `assembleStandaloneE01` 和明确指定的本地认证输入。认证输入与 Android 签名密钥不进入 Git。

[构建说明](docs/BUILD.md) · [本次发布说明](docs/releases/PREFACE-0.2.12-E01.3.md) · [来源及认证材料说明](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

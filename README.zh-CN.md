# DiPlay Preface · 星瑞 E01

面向 **2020 款吉利星瑞 / GKUI / 亿咖通 E01（MT6735）/ Android 5.1** 的 CarPlay 接收端适配项目。

[下载 0.2.12 E01.2 APK](https://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.12-preface-e01.2/DiPlay-Preface-0.2.12-E01.2-Android51-source-only.apk) · [版本说明与附件](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.2) · [历史版本](https://github.com/huangwits/DiPlay-Preface/releases) · [English](README.en.md)

> 当前公开 APK 为源码验证预览版，不含配件认证材料，不能独立完成 iPhone 连接。实车连接与音视频兼容性尚未完成验证。

## 下载与安装

Release 只提供主程序及其安装说明、校验文件。

| 文件 | 用途 |
| --- | --- |
| `DiPlay-Preface-0.2.12-E01.2-Android51-source-only.apk` | 主程序，最低 Android 5.1，支持 ARMv7 / ARM64。 |
| `DiPlay-Preface-v0.2.12-preface-e01.2-installers.zip` | 主程序 APK 与中文安装说明，下载后解压。 |
| `INSTALL-README.zh-CN.md` | 安装说明与版本信息。 |
| `SHA256SUMS.txt` | 下载文件的 SHA-256 校验值。 |

1. 下载上面的 APK，或展开 Release 页的 **Assets** 获取安装合集。
2. 停车后，通过已可用的 U 盘或文件传输方式将 APK 放入车机，用文件管理器安装。
3. 应用名称为 **DiPlay E01 Legacy**，包名 `com.shihab.diplay.e01legacy`。相同包名和签名的旧版可以覆盖升级。

GitHub 自动生成的 `Source code (zip)` / `Source code (tar.gz)` 是源码，不能作为 APK 安装。

## 适配内容

- Android 5.1 / API 22 兼容路径，覆盖音频焦点、播放、录音、视频 Surface、系统服务与权限调用。
- E01 默认使用 H.264、30 fps，画布长边不超过 960、短边不超过 540，保持比例。
- 合入 0.2.12 的连接流程、分辨率设置、方向盘控制、仪表显示与吉利音频 / HUD 更新。实际支持取决于车机固件。

主程序构建、单元测试、API 兼容检查和签名验证已通过；构建通过不代表实车功能已完成验收。详见 [0.2.12 发布说明](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.2)。

## 来源与开发

以 [carlito12345/DiPlay 吉利适配版](https://github.com/carlito12345/DiPlay) 为基础，整合 [DiPlay-Geely-Android43](https://github.com/xikai6282/DiPlay-Geely-Android43) 的旧系统兼容代码；原项目为 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)。

0.2.12 基于已验证的源码提交 `7ae9e60`，包含 carlito `84050d6` 和原作者 `2fc876e`。Release 标签对应发布源码；`main` 为唯一长期维护分支，包含 Android 5.1 / E01 适配；上游更新使用临时 `sync/carlito-<sha>` 分支，通过 PR 合并后删除。

[构建说明](docs/BUILD.md) · [构建检查](https://github.com/huangwits/DiPlay-Preface/actions/workflows/android51.yml) · [署名与许可证](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

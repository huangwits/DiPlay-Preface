# DiPlay Preface · 星瑞 E01 安卓 5.1 适配

面向 **2020 款吉利星瑞 / GKUI / 亿咖通 E01（MT6735）/ Android 5.1** 的实验性 CarPlay 接收端。

[下载安装包](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.11-preface-e01.1) · [全部版本](https://github.com/huangwits/DiPlay-Preface/releases) · [构建检查](https://github.com/huangwits/DiPlay-Preface/actions/workflows/android51.yml) · [English](README.en.md)

## 下载与安装

打开上面的 Release 页面，在 **Assets（附件）** 中下载 `.apk`。`Source code (zip)` / `Source code (tar.gz)` 是源码，不能安装到车机。

| 安装包 | 用途 |
| --- | --- |
| `DiPlay-Preface-0.2.11-E01-Android51-source-only.apk` | E01 主程序源码验证安装包，最低 Android 5.1；支持 ARMv7、ARM64。**不含配件认证材料，不能作为独立连接 iPhone 的完整车测包。** |
| `DiPlay-E01-Bluetooth-Diagnostics-v02.apk` | 独立蓝牙诊断工具，可识别原厂应用与系统库，无需先连接电脑。 |
| `DiPlay-Preface-v0.2.11-preface-e01.1-installers.zip` | 两个 APK 和中文安装说明的合集；需要先解压。 |
| `SHA256SUMS.txt` | 下载文件的 SHA-256 校验值。 |

**[直接下载 APK 合集 ZIP](https://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.11-preface-e01.1/DiPlay-Preface-v0.2.11-preface-e01.1-installers.zip)**。当前公开附件不含内嵌认证材料的完整车测 APK。

停车后，将 APK 通过你已能使用的 U 盘或文件传输方式放到车机，使用车机文件管理器安装。主程序显示为 **DiPlay E01 Legacy**，包名 `com.shihab.diplay.e01legacy`，不会覆盖普通包名的 DiPlay；它会更新相同签名、相同包名的旧 E01 测试版。

## 基于哪个项目

以 [carlito12345/DiPlay 的吉利适配版](https://github.com/carlito12345/DiPlay) 为基准，保留其吉利功能，移入 [DiPlay-Geely-Android43](https://github.com/xikai6282/DiPlay-Geely-Android43) 的旧系统兼容代码，并补充 E01 适配。原作者为 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)。

本次同步候选包含 carlito 的 `14abe4f` 和原作者的 `a1f8e45`（2026-10-05 核对），保留 Android 5.1／E01 适配。页面顶部的 Release 链接仍是上一版安装包；本次候选包和验证结果见[同步记录](docs/UPSTREAM_SYNC.md)。

## 已完成的适配

- 最低系统降至 **Android 5.1 / API 22**，补齐音频焦点、播放、麦克风、视频 Surface、系统服务和权限等旧版本调用路径。
- E01 低负载模式默认开启：H.264、30 fps、画布长边不超过 960／短边不超过 540，保持比例。
- 保留吉利音频、方向盘等代码；这些功能仍受具体固件接口与权限限制。
- 蓝牙提示区分标准适配器缺失、关闭、权限问题和 RFCOMM 连接失败。
- 附带无需电脑的原厂蓝牙应用识别工具。

## 本次上游更新

- 新增[现有 Wi-Fi／同一局域网](docs/EXISTING_WIFI.md)连接模式及 IPv4／IPv6 服务发现；**仍需要标准蓝牙／iAP2 启动通路，不绕过 E01 蓝牙限制**。
- 诊断文件导出支持外部应用目录及内部存储回退，可查看、分享；以弹窗显示的实际路径为准。
- 合入方向盘地图缩放、切歌时限时显示仪表歌曲信息、显示比例与夜间模式等上游更新；依赖对应车型接口的功能仍需实车验证。
- 合入 carlito 最新的通话结束后音频恢复、HUD 边界与缩放修正，并保留旧安卓 API 路径。

## 蓝牙现状与下一步

**E01 厂商无线蓝牙尚未实现，当前版本不承诺无线 CarPlay 可连接。**

已知实车情况：原厂蓝牙电话和音乐正常，但 Android 没有向 DiPlay 提供标准蓝牙适配器，系统蓝牙开关开启后立即关闭。这不能直接解释为硬件损坏，也不能仅靠修改应用开关解决。

下一步请安装诊断工具，点击 **“识别 E01 原厂蓝牙应用（无需电脑）”**，拍下结果。工具读取固件标识、候选原厂应用包名／版本／APK 路径及相关系统库名称；该按钮不启动原厂服务、不改变蓝牙开关。需要依据实车接口继续核实连接、读写与权限。

## 验证范围

- 本次候选本地 **1,081 项测试通过**（common 478、shared 597、诊断工具 6 项），无失败、错误或跳过。
- common/shared/mobile 的 Android NewApi 专项扫描通过；不是所有 lint 项目零告警。
- Robolectric 不提供 API 22 运行环境；API 22 分支在 API 23 沙箱中模拟验证，不能替代安卓 5.1 实机验收。
- [上一版首次 GitHub Actions 构建通过](https://github.com/huangwits/DiPlay-Preface/actions/runs/37213429657)。
- 尚未完成 E01 实车安装、音视频、USB、无线连接和重连验收；构建通过不等于实车兼容已经完成。

## 后续更新

`android51-e01` 是默认适配分支；`main` 保留 Fork 时的上游快照。

在 **Actions → Review carlito updates → Run workflow** 手动准备更新。流程先从 carlito 创建候选分支，发生冲突就停止；可合并时生成草稿 PR 并运行检查，**不会自动合入适配分支**。详见[同步说明](docs/UPSTREAM_SYNC.md)。

Actions 中的日常主程序产物是无配件认证材料的源码验证包，不能与 Release 附件的具体配置混为一谈。源码 Git 历史不提交认证私钥、证书或 APK；安装包通过 Release 附件分发。

[构建说明](docs/BUILD.md) · [E01 实现与边界](docs/ECARX-E01-ANDROID51.md) · [上游中文说明](docs/CARLITO-README.zh-CN.md) · [署名与许可证](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

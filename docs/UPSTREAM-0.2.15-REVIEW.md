# 2026-10-09 上游更新检查

原作者和 Carlito 都已有 0.2.15。本次按照用户要求检查更新，未切换 Preface 的连接基线；本地仍为 0.2.13.2 / versionCode 36、Android 5.1 / API 22。

## 已核实状态（北京时间）

| 来源 | 版本及发布时间 | 检查时最新提交 |
| --- | --- | --- |
| [原作者 shihabal3amri](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.15) | 0.2.15 公开预览，2026-10-08 16:47 | [9e244d958afe](https://github.com/shihabal3amri/DiPlay/commit/9e244d958afe6b8fd79ade49769ce25a944f397b)，要求连接问题附诊断日志 |
| [Carlito 吉利分支](https://github.com/carlito12345/DiPlay/releases/tag/v0.2.15) | 0.2.15，2026-10-08 23:50 | [61aa1a580b6d](https://github.com/carlito12345/DiPlay/commit/61aa1a580b6d77bd7fbea5191acbfcbc13c1995b)，合并上游 0.2.15 并保留吉利集成 |

原作者的版本标记为 prerelease，因此 GitHub 的 `releases/latest` 返回 404；已通过 releases 列表和具体版本页核实，不能据此误判为没有更新。

## 主要变化

- 明亮、深色和自动外观，紧凑布局、标题按钮与开关尺寸调整。
- 音乐缓冲耗尽后的恢复、主屏解码器帧率提示，以及较旧 Android 的 USB/API 兼容处理。
- Wi-Fi Direct 自动 5 GHz / 2.4 GHz 选择；选定 iPhone 蓝牙重连时打开应用的可选功能。
- 实验性的车载蓝牙音频输出、启动修复、导航小窗与更多仪表设置。
- 关于页面手动检查和下载安装上游更新。

依据：[原作者版本说明](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.15)、[Carlito 版本说明](https://github.com/carlito12345/DiPlay/releases/tag/v0.2.15)及上述固定提交。

## 与星瑞 E01 的关系

Carlito 当前 [mobile/build.gradle.kts](https://github.com/carlito12345/DiPlay/blob/61aa1a580b6d77bd7fbea5191acbfcbc13c1995b/mobile/build.gradle.kts) 为 minSdk 25、versionCode 40、versionName 0.2.15；原作者版本说明同样要求 Android 7.1 起。两者均不直接满足本项目 Android 5.1/API 22 的目标。

后续值得逐项审查的是音乐缓冲恢复、小屏界面和蓝牙重连入口；这只是候选清单，不代表已验证可在 E01 使用。USB/无线协议、车载蓝牙音频和导航窗口改动须另行比较现有适配并实车验证。上游 APK 更新器不能直接用于 Preface，否则会跨过本项目包名、签名、授权和仅公开源码的分发规则。BYD/DiLink 专用功能继续排除。

## 本次本地界面修正

按所有者后续澄清，联网授权面板位于原厂蓝牙工具页右侧；首页恢复普通布局。蓝牙工具按钮与提示已精简，确认框仍说明测试、安装和还原的影响。授权区附星瑞 QQ 交流群（892351951）的原图二维码，可复制群号或点图放大。离开工具页后停止授权查询；现有 CarPlay 与蓝牙恢复不受影响。微信相关提示保持移除。

## English

Both sources have 0.2.15. Carlito's current mobile build requires API 25; Preface still targets API 22 and retains the selected connection baseline. This is an update review, not an upstream merge. The local UI change embeds online authorization beside the factory Bluetooth tools and removes WeChat prompts while preserving licensing and existing sessions.

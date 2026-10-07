# DiPlay 星瑞 / Preface

适用于吉利星瑞 E01（MT6735）的 Android 5.1 / API 22 CarPlay 适配版，界面为简体中文。安装到车机上，包名为 `com.shihab.diplay.preface`。

## 0.2.13.2

本版从 [carlito12345/DiPlay 0.2.13.2](https://github.com/carlito12345/DiPlay/commit/586b39368b0bf7e3e9cb50a0b7838db083b382e1) 重新适配，保留它选择的连接基线。它并不包含原作者此后所有更新。

- 适配 Android 5.1 的权限、系统服务、USB、蓝牙、热点及音频接口。
- 移除 BYD/DiLink 专属车辆输出、固定方向盘键码和旋转屏方形画布。保留 Geely 投影、车辆桥接及手动按键学习；这些功能仍需相应硬件和权限。
- 在“连接”设置中加入“E01 蓝牙检查与切换”，提供检查、尝试切换、恢复原厂、蓝牙设置和复制结果，无需手动输入命令。
- E01 低负载模式使用 H.264、最高 960×540 / 30 帧和单视频流；关闭后恢复已保存的画质设置。
- 包名改为 `.preface`，与旧 `.e01legacy` 分开安装，需要重新设置权限、连接选项及本机 ADB 授权；安装签名保持一致。Android 5.1 使用系统默认音频设备；网页视频播放需要 Android 6+，桌面嵌入地图需要 Android 11+。

## 蓝牙工具

停车后进入“连接 → 打开蓝牙切换工具”，先检查连接，再尝试切换。工具使用已有 root 或已获授权的本机 ADB 通道；没有相应权限会停止并提示原因。正常 CarPlay 连接沿用 Geely 的 Android 蓝牙路径，工具不会自动参与启动。

切换前会等待 CarPlay 断开，切换期间原厂蓝牙电话和音乐会停用。工具只对已分析的 E01 / MT6735 固件 `SWFS11G1105H5182.00305`、`SWFS11G1115H7007.00018` 尝试临时服务与设备节点切换，不刷写固件。测试结束点“恢复原厂”；不能恢复或操作中断时重启车机并检查原厂电话和音乐。

代码检查、桌面测试和打包校验不能证明实车蓝牙、通话、Siri 或 CarPlay 连接成功。本版仍需实车验证。

## 构建与来源

使用 [完整包构建与验证步骤](docs/BUILD.md)。源码/CI 包不带运行认证文件，不能作为独立车机安装包交付；完整包从本地显式输入认证文件，并验证证书匹配、APK 内容与升级签名。认证文件和安装签名私钥不进入 Git 或源码压缩包。

[本版说明](docs/PREFACE-0.2.13.2.md) · [Geely 连接基线](docs/CONNECTION-BASELINE.md) · [发布格式](docs/RELEASE-POLICY.md) · [第三方声明](docs/THIRD_PARTY_NOTICES.md)

基于 [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay) 和原作者 [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay)。项目继承 [xcertplay](https://github.com/shilapi/xcertplay)（GPL-3.0）及 [DiAuto](https://github.com/shihabal3amri/DiAuto)（AGPL-3.0）的代码和界面；保留原作者署名与许可证。CarPlay 及图标属于 Apple。本项目没有 Apple 或车厂认证。

完整 APK 使用上游说明的实验性配件身份，不是为本项目签发的 MFi 身份；打入 APK 的身份材料可被提取，未来 iOS 是否继续接受仍未确定。相关来源及限制见第三方声明。

## English

DiPlay Preface targets Geely E01 / MT6735 head units running Android 5.1 (API 22), with a Simplified Chinese interface and application ID `com.shihab.diplay.preface`.

Version 0.2.13.2 is based on Carlito's Geely 0.2.13.2 commit `586b3936`, preserving its selected connection baseline. It does not merge all later original-author updates. It adapts system services, permissions, audio, USB and networking for API 22, removes BYD-specific integrations and fixed key assignments, and retains compatible Geely projection and learned steering controls.

The connection page includes an explicit E01 Bluetooth Check/Switch/Restore tool. It requires existing root or authorized local ADB, waits for CarPlay teardown, and temporarily interrupts factory Bluetooth calls/music. Switching is limited to the two documented E01 firmware builds. It does not flash firmware. Restore factory Bluetooth after testing; restart the head unit if recovery fails.

The optional E01 performance profile caps H.264 at 960×540 and 30 fps with one video stream while retaining saved quality preferences. API 22 uses default audio devices; web video requires Android 6+, and launcher map embedding requires Android 11+.

Only a validated standalone package with the existing signer is suitable for installation. The new `.preface` package installs separately from `.e01legacy`, without automatically migrating settings or permissions. Source builds exclude runtime identities. Desktop checks do not establish real-vehicle compatibility. Preserve all upstream attribution and licenses; the experimental identity and lack of Apple certification remain as documented above.

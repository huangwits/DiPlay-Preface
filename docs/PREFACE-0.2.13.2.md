# Preface 0.2.13.2

## 基底与版本

- Geely 基底：`586b39368b0bf7e3e9cb50a0b7838db083b382e1`，版本 0.2.13.2。
- 公开版本及 APK versionName：`0.2.13.2`，versionCode `36`，均与 Geely 上游一致。
- 包名：`com.shihab.diplay.preface`；最低 API 22，保留既有签名。
- 版本信息于 2026-10-07 从 Geely `03af895e36c6970b64ae48f31595d32f60bd1ffb` 核对；本适配的源码基底仍为上述 `586b3936`。

沿用 Geely 的 Android 蓝牙连接、RFCOMM、热点与会话管理基线。旧系统所需的系统服务获取、权限、USB 传输和音频接口提供版本分支。新的蓝牙切换工具只在用户按按钮时运行，切换前等待当前会话关闭。此前分支的私有 ECARX/H52 RFCOMM 通道没有迁入。

E01 默认启用低负载配置：H.264、最高 960×540、30 帧、关闭额外地图视频流。可在设置中关闭，原先保存的画质选项会保留。Geely 车辆桥接和投影功能仍取决于实车接口。方向盘功能不再带其他品牌的固定键码，只使用本车已学习的按键。

## 安装与使用

安装完整 `DiPlay-Preface-v0.2.13.2.apk`。新包名 `.preface` 会与旧 `.e01legacy` 分开安装，旧设置、权限及本机 ADB 授权不会自动迁移，需要在新应用中重新设置。测试时只运行一个 CarPlay 应用。源码包和源码 CI APK 不包含独立使用所需的认证输入，不作为车机安装包交付。

蓝牙入口：“连接 → 打开蓝牙切换工具”。按“检查连接 → 尝试切换”操作；需要系统授权时允许本机调试。默认端口为 5555，也可填车机实际端口。工具会检测 uid=0，不具备 root/ADB 管理员能力时不会切换服务。

切换仅支持已分析的 E01 / MT6735 固件 `SWFS11G1105H5182.00305` 或 `SWFS11G1115H7007.00018`，同时检查服务和设备节点状态。临时停止原厂蓝牙电话、音乐；不修改系统或固件分区。测试完点“恢复原厂”，恢复未确认时重启并检查原厂功能。遇到问题可“复制结果”。日志保存在本机，不自动上传。

## 验证范围

需要通过 common/shared 回归、API 22 lint、Geely 功能范围检查、脚本测试、源码 APK 检查和完整包认证/签名验证。完整包的两个 bootstrap 测试必须实际执行并且零跳过。详细日志与结果 JSON 保存在本地验证目录，不作为发布附件。

Android 5.1 使用默认音频设备；网页视频需要 Android 6+，外部桌面嵌入地图需要 Android 11+。尚未完成实车安装、配对、USB/无线连接和电话/Siri 验收；蓝牙报告开启不能替代这些验证。

## English

Public version and APK versionName are `0.2.13.2`, version code 36, matching Geely's metadata verified at `03af895e` on 2026-10-07. The adaptation's code baseline remains `586b3936`. The package is `com.shihab.diplay.preface`, minimum API 22, using the existing signer. It installs separately from `.e01legacy`; configure its settings, permissions and local ADB authorization again. Run only one CarPlay app at a time.

It retains the selected Geely connection baseline, adds required legacy API branches, removes BYD-specific integrations and adds an explicit E01 Bluetooth switching tool. The previous fork's private ECARX/H52 RFCOMM backends are not imported. Switch and restore require verified privileged access and run only on the documented E01 firmware, with CarPlay stopped beforehand. Factory calls/music are interrupted during switching. Restore after testing or restart if recovery cannot be confirmed.

The E01 profile limits video workload while preserving saved preferences. Geely bridge, projection and learned keys remain hardware-dependent. All local gates, including the two actual-APK bootstrap tests with zero skips, must pass before delivery. No physical vehicle/iPhone validation is claimed.

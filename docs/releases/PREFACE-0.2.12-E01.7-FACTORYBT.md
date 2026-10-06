# E01.7 上游更新与原厂蓝牙实验版 / Upstream update with factory Bluetooth

## 中文

版本 `0.2.12-e01.7-factorybt-android51` / versionCode 36，面向星瑞 E01 / Android 5.1。本版将原作者主线从 `29572a2` 更新至 `e6477e1741ad91a18452f0099a432dde63311a19`，保留 E01.6 的原厂蓝牙实验后端。同一包名 `com.shihab.diplay.e01legacy` 和升级签名，使用完整 APK 覆盖安装即可保留设置。

### 更新内容

- 同步上游音频缓冲功能（默认关闭）、Opus 麦克风时钟修复、触控事件通道低延迟设置。
- 同步 Wi-Fi 热点 IPv4 地址选择、Wi-Fi Direct 信道选择及清理修复、USB 服务缺失和 USBMUX 回复解析修复。
- 同步模块化设置、返回车机按钮自定义、分屏侧栏、仪表地图缩放及切换效果。车型专属功能仍依赖对应固件，不代表 E01 全部支持。
- 保留 E01 的 API 22、H.264、30 fps、960×540 内按比例缩放、软件 Opus、USB 和网络兼容逻辑。
- 保留原厂 ECARX SPP 蓝牙实验开关、原厂配对列表及 E01-Fxx 错误诊断。Android 9 的 Wi-Fi Direct 使用上游旧接口；低于 Android 9 的 E01 继续使用兼容的自动、原厂热点或现有 Wi-Fi 模式。
- 修复合并后返回设置页时连接配置刷新不完整，并为新增设置开关和 Base64 解码保留 Android 5.1 兼容实现。

### 安装和连接

1. 覆盖安装本版完整 APK，无需卸载旧版本。
2. 在车机原厂电话应用中配对 iPhone，保持手机蓝牙及 Wi-Fi 开启。
3. 在 DiPlay 连接设置确认“使用车机原厂蓝牙”开启，从原厂配对列表重新选择 iPhone，配置 Wi-Fi 后连接。
4. 失败时记录 E01-Fxx 错误码，可导出应用内诊断报告。

“H52 厂商蓝牙状态检测”只读状态；本版实际连接走 E01 的 ECARX 实验后端。H52 ANW 的接口未证实匹配本车，未整分支导入 Android 4.3 适配。原厂 SPP 是否支持本车 iPhone 的 iAP2 握手仍需实车确认，桌面测试不能证明无线连接已修复。

### 验证

发布前执行 common/shared 回归、API 22 NewApi lint、维护脚本测试、源码包检查；完整 APK 执行实际认证引导两项测试（零跳过）、认证资产匹配、包名/版本/API/ABI 与升级签名检查。确切结果见发布记录。

## English

Version `0.2.12-e01.7-factorybt-android51` / code 36 merges original DiPlay main through `e6477e1741ad91a18452f0099a432dde63311a19`, retaining the E01.6 factory Bluetooth experiment, Android 5.1/API 22 support, package identity and upgrade signer.

The update includes optional buffered audio (off by default), Opus microphone timing, low-latency touch events, hotspot IPv4 selection, Wi-Fi Direct channel/lifecycle fixes, USB fixes, modular settings, custom car buttons, side panels and cluster improvements. Vehicle-specific outputs still depend on supported firmware. E01 retains H.264/30 fps, proportional 960×540 limits and its audio/USB/network compatibility paths. Android 9 receives the upstream legacy Wi-Fi Direct path; older E01 uses automatic, factory-hotspot or existing-Wi-Fi modes.

Install the full APK over the previous version. Pair in the factory phone app, enable factory Bluetooth in DiPlay, reselect the paired iPhone and connect. Record any E01-Fxx error. H52 status diagnostics are separate from the ECARX SPP connection experiment; H52 ANW compatibility and E01/iPhone connectivity remain unverified.

Release gates cover common/shared regression tests, API 22 lint, maintenance tests, source-only APK checks, two actual full-APK bootstrap tests with zero skips, authentication asset matching and upgrade-signature verification. See the publication record for exact results.

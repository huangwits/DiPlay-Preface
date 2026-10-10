# preface v0.2.16.7

## 中文

修复 E01 方向盘上一曲、下一曲的接收与匹配，兼容旧 Android 媒体会话，并避免同一次按键被重复处理。按键识别支持直接学习安卓按键。

在“设置 → 车辆”中集中提供按键、三指飞屏、HUD 和仪表地图设置。新增原厂 HUD / 仪表导航输出；有兼容原厂服务时，可同步 CarPlay 的转向、距离和道路提示。完整地图仍需车机提供可用副屏，部分原厂仪表控制需要车辆数据桥。

保留授权公共根证书兼容修复、统一的蓝牙工具样式和首次适配步骤。授权通过后隐藏“申请激活”和“刷新状态”，保留通过状态与群二维码。

新增车辆功能尚未完成实车验证，具体可用性取决于车型和固件。覆盖安装可保留已有设置和授权信息。

APK：`DiPlay-Preface-v0.2.16.7.apk`，versionCode `51`，包名 `com.shihab.diplay.preface`，最低 Android 5.1 / API 22。

## English

Fixes E01 previous/next steering keys with foreground input, legacy Android media-session support and duplicate-event suppression. Android keys can also be learned directly.

Vehicle settings now group wheel controls, three-finger projection, HUD and instrument maps. Optional factory navigation output sends CarPlay turns, distances and road names to compatible E01 services. Full maps require an available secondary display; some factory instrument controls require a vehicle data bridge.

Retains bundled public CA compatibility, consistent Bluetooth tools and numbered setup steps. Approval hides the request and refresh actions while keeping the approved status and group QR.

The added vehicle functions have not yet been tested in a real car; availability depends on vehicle and firmware. Install over the existing app to preserve settings and authorization.

APK: `DiPlay-Preface-v0.2.16.7.apk`, versionCode `51`, package `com.shihab.diplay.preface`, minimum Android 5.1 / API 22.

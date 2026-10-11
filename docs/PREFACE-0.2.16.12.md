# preface v0.2.16.12

- 接入已核对的 FS11 旧版固件原厂三指飞屏控制。导航时左滑显示、右滑收回，避免与系统重复发送手势。
- 在“地图投屏”中开启完整地图和原厂仪表模式、选择仪表屏幕，然后重新连接手机。此固件使用原厂服务，无需安装 GD 车辆数据桥。
- 应用主动请求飞屏时，先等待手机地图出画面；导航结束或断开时收回地图。保留原厂对倒车、雷达和紧急通话的限制。
- 在线安装自动判断当前应用是否属于系统应用，并区分可能的原厂安装授权限制。Root 检查仍在确认安装后自动执行。

保留 Siri 开关、上一曲／下一曲、热点启动和统一界面。此版本依据旧版 FS11 固件接入，完整地图显示和在线覆盖安装仍待实车验证；不会自动修改系统挂载。

版本 `0.2.16.12` / `56`，最低 Android 5.1。本地候选，GitHub 公开版本仍为 0.2.16.7。

## English

- Connect the native three-finger control found in the inspected legacy FS11 firmware: swipe left to show and right to hide without echoing system gestures.
- Enable complete-map output and native instrument mode, select the instrument display, then reconnect the phone. This firmware uses its own service and does not require GD Vehicle Bridge.
- App-initiated requests wait for the first map frame. Navigation ending or disconnecting closes the map while retaining the factory reversing, radar and emergency-call checks.
- Update installation automatically identifies system-app status and distinguishes possible OEM installation authorization restrictions. Root access is still checked automatically after installation confirmation.

Retains Siri, track controls, hotspot startup and the unified interface. Complete-map output and online overlay installation still need vehicle verification. No automatic mount changes.

Version `0.2.16.12` / `56`, Android 5.1 minimum. Local candidate; public release remains 0.2.16.7.

# 吉利星瑞功能范围 / Geely Preface scope

## 中文

E01.12 以 carlito 为基准，聚焦吉利星瑞 Android 5.1 / API 22 及以上车机。系统蓝牙为默认；E01 ECARX、H52 ANW 保留为手动选项。切换接口后重新选择 iPhone。

保留无线/USB CarPlay、通用网络兼容处理、音频与解码、吉利 HUD、吉利方向盘识别与学习、桌面导航组件和 E01 低负载配置。桌面地图使用通用虚拟视频流；E01 性能模式继续关闭额外视频流以控制负载。

移除 BYD / DiLink 原厂 HUD 和仪表广播、SOME/IP 与 ADB 仪表控制、原厂导航应用停启、车辆电池/充电口/车速/挡位探测、通话仪表卡片、固定 BYD 方向盘键值、可旋转屏方形画布、相关设置/调试入口和专用图标。不能读取星瑞挡位，因此不启用停车视频；不会用固定值伪装停车状态。

旧 BYD 设置不再触发厂商服务。返回车机按钮的默认 BYD 标签迁移为 Geely，自定义标签保留。完整包沿用原包名和签名，可覆盖升级。实际手机与实车连接仍需验证。

原作者和第三方署名、许可证和 Git 历史保留。协议解析及网络兼容代码即使源于其他车机的缺陷报告，只要与通用 CarPlay 有关，仍保留。

## English

E01.12 keeps carlito and targets Geely Preface on Android 5.1 / API 22+. System Bluetooth remains the default; E01 ECARX and H52 ANW are manual choices. Reselect the iPhone after switching.

Wireless/USB CarPlay, shared network/decoder compatibility, Geely HUD and learned steering controls, navigation widgets, launcher maps and E01 performance remain. E01 mode still disables the extra video stream.

Removed BYD/DiLink OEM HUD/instrument outputs, vehicle-data readers, ADB instrument routing, factory navigation app control, call cards, fixed BYD keys, rotating-screen canvases, settings/debug entry points and icons. Parked-video capability is not advertised without a Geely gear provider. Saved BYD options cannot start removed services; custom car-button labels survive while the old BYD default becomes Geely.

Package identity, signer, upstream attribution, licenses and Git history are preserved. Automated validation does not establish real vehicle connectivity.

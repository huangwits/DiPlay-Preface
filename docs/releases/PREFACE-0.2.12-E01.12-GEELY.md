# E01.12 · 吉利星瑞专用整理 / Geely Preface cleanup

## 中文

版本 `0.2.12-e01.12-geely-android51`，versionCode 41。保留 carlito、系统蓝牙 / E01 ECARX / H52 ANW、吉利 HUD、方向盘学习、通用 CarPlay 和 E01 低负载配置。

移除 BYD / DiLink 原厂仪表和 HUD、车辆电池/车速/挡位探测、原厂应用控制、通话仪表卡片、固定按键、旋转屏方形画布及专用资源。旧 BYD 设置不再生效；自定义车机按钮标签保留，旧默认 BYD 标签改为 Geely。没有星瑞挡位数据时不启用停车视频。

只安装 `-full.apk` 完整包。最低 Android 5.1，保留 `com.shihab.diplay.e01legacy` 和原签名，可覆盖升级。接口切换后重新选择 iPhone。实际手机与实车连接仍需验证。

## English

Version `0.2.12-e01.12-geely-android51`, code 41. Retains carlito, System/E01 ECARX/H52 ANW Bluetooth, Geely HUD, learned steering, shared CarPlay and E01 performance.

Removes BYD/DiLink instrument/HUD integration, vehicle readers, OEM app control, call cards, fixed keys, rotating-screen canvases and dedicated assets. Old vendor preferences cannot reactivate removed integrations. Custom car-button labels survive; the former BYD default becomes Geely. Parked video remains unavailable without a Geely gear provider.

Install the full APK in place on Android 5.1+. Package and signer are unchanged. Phone and vehicle connectivity remain unverified.

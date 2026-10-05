# EasyPlay preview45 连接识别适配

分析输入为用户提供的 EasyPlay-Lynk-Android4.4-10-preview45.apk，SHA-256 为 `03e7e50f108a21410f8ec5923791517e904691c5b06b6f2f6fb3262a5159632d`。没有原始 Kotlin 源码；依据 APK 内 DEX 字节码、资源与更新说明分析，并在本项目独立实现。

## 已采用的接口行为

EasyPlay 的 LegacyVendorBluetoothHandoff.PlatformBackend 通过 `ServiceManager.getService("ecarx_btuiservice")` 获取 Binder，再调用 `com.nforetek.bt.aidl.UiCommand$Stub.asInterface`。A2DP/HFP 的连接状态及地址接口分别为 `isA2dpConnected` / `getA2dpConnectedAddress` 和 `isHfpConnected` / `getHfpConnectedAddress`。

本项目仅使用查询接口：

- 验证 Binder descriptor 和返回代理类型；只接受当前已配对的合法设备地址，排除系统占位地址。
- 手动指定手机优先；自动选择时优先使用厂商连接证据，多个设备存在歧义时要求用户选择。
- 查询最多等待 250ms；同一时刻最多一个未完成查询，不对卡住的 Binder 调用反复创建线程。
- 不复用上一连接尝试迟到的结果；接口缺失、权限拒绝、查询超时均退回已有标准蓝牙识别。
- 标准 BluetoothAdapter 预检与 RFCOMM 通道要求保持有效，厂商连接证据不代表标准数据通道可用。

## 未移植的部分与验证边界

不调用 EasyPlay 的 `reqHfpDisconnect` 或 `reqA2dpDisconnect`，保留本项目原有音频交接行为。没有将 APK 内配件认证文件导入源码或构建输入。公开测试 APK 仍为 source-only，不具备独立 iPhone 认证能力。

这项变更补齐 nFore/ECARX 原车连接识别，不能在无实车验证的情况下承诺修复 E01 蓝牙开关或 Goodocom SPP。目标固件如未提供相同服务，将走原有路径。

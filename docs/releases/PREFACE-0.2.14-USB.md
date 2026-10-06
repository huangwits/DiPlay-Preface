# USB 重识别修复与 E01 精简 / USB rediscovery and E01 scope

## 中文

公开文件版本 0.2.14；APK 内部版本 `0.2.12-e01.14-usb-android51`，versionCode 43。

USB 配置检查直接使用 Android 5.1 已提供的描述符，避免把暂时无法打开手机误判为没有 CarPlay 配置。配置切换完成并关闭控制连接后，每 500 毫秒重新扫描设备，支持遗漏插入广播及设备名复用；不将旧描述符再次送入配置切换。15 秒仍未重新识别手机则结束等待并提示重试，不再无限等待该广播。旧尝试和关闭后的扫描不会重新触发连接。

移除 H52 ANW 蓝牙、厂商状态诊断和专用音频路由。旧 H52 选择会重置为系统蓝牙并清除该接口保存的手机；保留 ECARX 手动接口、星瑞 HUD/方向盘、简体中文和 API 22 低负载设置。

保持 `com.shihab.diplay.e01legacy` 包名和原签名，完整安装包可覆盖升级。桌面检查不能证明特定车机/线材已可进入 CarPlay；E01 原厂蓝牙数据通道仍需对应固件接口验证。

## English

Version 0.2.14 uses APK version `0.2.12-e01.14-usb-android51`, code 43. Inspect the available USB configuration without opening the device, then poll fresh descriptors after the transition connection closes. Recover from missing attach broadcasts and reused device names, stop after 15 seconds, and ignore stale/cancelled attempts.

Remove H52 ANW transport, vendor-state diagnostics and dedicated audio routing. Existing H52 selections return to system Bluetooth and discard the associated phone. Keep manual ECARX selection, Geely integrations, Chinese UI, API 22 compatibility, application identity and upgrade signer. Vehicle connectivity remains unverified.

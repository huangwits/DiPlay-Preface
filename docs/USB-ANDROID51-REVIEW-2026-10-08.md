# USB 与 Android 5.1 检查（2026-10-08）

检查基于星瑞分支 `9584c700`。用户反馈：首次出现 CarPlay 相关授权，拔出并重新插入 iPhone 数据线后没有反应；授权发生在手机还是车机暂未确认。

## Android 版本结论

本分支最低 Android 5.1 / API 22，已有低版本 USB 路径：API 22–25 使用带超时的同步 `bulkTransfer` 读取，API 28 以下写入按 16 KiB 分块；API 26 以上才使用带超时的 `UsbRequest` 等待。不能把所有 USB 失败归因于系统版本过低。

已对照 [Android 5.1 UsbManager](https://android.googlesource.com/platform/frameworks/base/+/android-5.1.1_r1/core/java/android/hardware/usb/UsbManager.java) 与 [UsbDeviceConnection](https://android.googlesource.com/platform/frameworks/base/+/android-5.1.1_r1/core/java/android/hardware/usb/UsbDeviceConnection.java) 源码。`openDevice` 本身可在此系统使用，但设备服务返回空文件描述符、连接打开失败或异常都可能使它返回 null。获得 USB 授权不等于已经成功打开数据连接。旧现场记录曾出现授权后 `openDevice` 失败，仍不能仅凭它确定线材、车机 USB 服务或内核的根因。

## 确认并修正的问题

### 旧版同步读取未区分设备消失

`Iap2UsbSession.readSynchronously` 原先将非正数读取结果全部解释为暂时没有数据。手机拔出后，USBMUX 读取线程可能继续等待旧设备连接，无法及时通知上层重连。新设备再次出现也不能恢复旧连接持有的文件描述符。

现在由打开该连接的 `IphoneUsbHost` 提供原 USB 节点的存在性检查：同步读取无数据时，原节点已消失便将旧连接标记失败，由现有上层恢复流程重新连接；原节点仍在则继续等待，避免把普通超时误判为断线。正常收到数据时不增加设备枚举。

新增检查通过实际 host 构造的 session 模拟 API 22，覆盖拔出、重新插入后的不同节点、设备仍在但读取返回 -1。该修复与用户描述的卡住现象相关，但不能据此断言是本次实车故障的唯一原因。若固件仍列出失效节点，或在两次枚举之间复用完全相同的节点名称，仍需现场记录继续定位。

### Android 8.0/8.1 异步请求过大

原异步 USBMUX/NCM 路径在 API 26/27 也会先请求 64/32 KiB，再仅对显式返回 false 的情况回退。然而 [Android 8.1 UsbRequest 源码](https://android.googlesource.com/platform/frameworks/base/+/android-8.1.0_r1/core/java/android/hardware/usb/UsbRequest.java) 会对大于 16 KiB 的请求直接抛出参数异常，根本不会走到 false 回退。

现在 API 26/27 首次请求即限制到 16 KiB；API 28 以上保留原始大小、显式拒绝后的兼容回退和关闭时的并发保护。这一项是其他系统版本的兼容缺口，不是 Android 5.1 的异步路径问题。

## 验证与限制

定向 USB、USB 权限、设备重识别及主界面检查共 160 项通过。完整回归、API 22 lint、实际 APK 启动与交付核验结果存于源码树外 `usb-review-20261008/STATUS.md`，安装包附带说明与 SHA-256 校验。

API 22 分支测试运行于 Robolectric API 23 环境并选择 API 22 分支；API 26/27 分别运行对应模拟平台。桌面回归不代替 E01 内核、实际 USB 接口与 iPhone 连接测试。本轮没有实车验证；保留原包名、内部版本、签名和已有上游适配。

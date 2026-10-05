# E01.6 原厂蓝牙实验版 / Factory Bluetooth experiment

## 中文

面向 2020 款星瑞 E01 / Android 5.1。版本 `0.2.12-e01.6-factorybt-android51`，versionCode 35，保留 `com.shihab.diplay.e01legacy` 包名和现有升级签名。此次按用户要求提供可安装的原厂蓝牙连接实验；是否成功连接需要实车确认。

### 使用

1. 用完整 APK 覆盖安装，保留原有设置。
2. 在车机原厂电话应用中开启蓝牙并配对 iPhone；保持 iPhone 蓝牙和 Wi-Fi 开启。
3. 打开 DiPlay → 连接设置，确认“使用车机原厂蓝牙”已开启。本实验包默认开启，USB 模式仍可用。
4. 配好原有的 Wi-Fi/热点连接方式，在“选择 iPhone”中重新选择原厂列表里的手机，再点击连接。
5. 如果失败，记录屏幕上的 `E01-Fxx` 错误码。内置诊断报告也保存上次实验结果及 SDK 接口名称，无需另装采集工具。

“原厂蓝牙实验”开关关闭后，下一次无线连接恢复原来的 Android 蓝牙路径。切换开关不会修改车机蓝牙开关或配对记录。

### 实现及边界

实验使用车机安装的 `com.ecarx.xui.adaptapi.bt.Bt.create(Context)`、`getBtSettings()` 和 `getSpp()`。原厂模式从 `reqBtPairedDevices()` 选择目标并使用真实的 `getBtLocalAddress()`，不以 Android 配对列表、虚构本机地址或“蓝牙已打开”代替数据通道。

实际连接调用 `registerSppCallback`、`reqSppConnect`、`isSppConnected` 和 `reqSppSendData`，接收 `onSppDataReceived` 的字节数据，并交给现有 iAP2/CarPlay 握手流程。原厂模式不创建 Android RFCOMM socket；也不自动退回标准蓝牙开启流程。

SDK 的 `reqSppConnect(String)` 没有 UUID 参数，实际服务由厂商选择。因此，即使 SPP 连接成功，仍可能不能访问 iPhone 的 iAP2 服务。另一个车型参考实现的 `getSpp()` 返回 null，本版遇到相同情况会明确报错；不会把不存在的接口视为成功。

所有 SDK 调用在独立线程上执行并有等待上限。若底层调用卡死，进程内停止接受新 SDK 操作，保留排队清理，提示重启 DiPlay。连接由 `isSppConnected` 布尔查询确认，不猜测状态回调的数字含义。只接收所选设备的数据，复制回调缓冲并限制队列大小；退出时注销本次回调并断开本次请求的 SPP。已经存在的 SPP 连接会拒绝接管。

### 错误码

| 错误码 | 含义 |
| --- | --- |
| E01-F01 | 车机 SDK 无法加载或创建。 |
| E01-F02 | SPP 接口缺失、不兼容或尚未就绪。 |
| E01-F03 | 原厂蓝牙设置不可用、未开启、未就绪或本机地址不可读。 |
| E01-F04 | 无法取得配对列表或所选手机不在原厂列表中。 |
| E01-F05 | 回调被拒绝，或已有 SPP 会话不能接管。 |
| E01-F06 | SPP 连接请求被拒绝或超时。 |
| E01-F07 | SPP 收发失败、连接断开或缓冲溢出。 |
| E01-F08 | SPP 已打开，但 CarPlay iAP2 握手未完成。 |
| E01-F09 | SDK 调用超时/中断，需关闭并重新打开应用。 |

错误会停留在页面上，提供手动重试，避免连续重试占用原厂接口。保留 E01 的 H.264、30 fps 和低负载画布设置。

### 来源与验证

本车静态代码确认了 `Bt.create(Context)` 入口；SDK SPP/settings 声明参考 [StatusBar2-Coolray 固定版本](https://github.com/lugadin/StatusBar2-Coolray/blob/330832de52eca3c50ff83d74f1dc3d12d5b3a504/libs/ecarx-adapter.odex)。仅独立编写反射客户端，不分发该文件或任何车机固件。参考车型的实现不证明 E01 兼容。

针对性测试覆盖二进制顺序、跨设备数据隔离、连接判定、拒绝/超时、晚到回调、晚返回连接清理及反射监听器。完整包已通过 common/shared 回归（含实际 APK 认证引导共 1,388 项通过）、4 项维护测试、API 22 NewApi lint、源码包检查与签名比对。实际 APK 认证引导两项测试均通过且无跳过。桌面检查不证明实车无线已修复；最终构建记录在本地 `e01-factorybt-validation` 目录。

## English

This full experimental APK targets the 2020 Geely Preface E01 on Android 5.1. Version `0.2.12-e01.6-factorybt-android51` / code 35 retains the existing package and upgrade signer. Factory Bluetooth is enabled by default in this E01 build and can be disabled in Connection setup.

Pair the iPhone in the factory phone app, configure Wi-Fi in DiPlay, reselect the phone from the factory paired list, then connect. Record any `E01-Fxx` error shown. The built-in diagnostic report includes the last result and SDK interface names.

The experimental transport loads the installed ECARX SDK, registers its SPP listener, requests a connection, verifies it with the SDK's boolean query, and feeds binary callbacks into the existing iAP2 flow. It does not replace firmware, create an Android RFCOMM socket in factory mode, or claim that generic SPP selects the iPhone's iAP2 service. The SDK's connection method has no UUID parameter; null/incompatible interfaces fail explicitly.

Calls have bounded waits; teardown releases only the attempted session. Tests exercise binary data, peer isolation, rejection, timeout, cancellation and reflection. Validation passed: 1,388 Android tests including two actual-APK bootstrap tests with zero skips, four maintenance tests, API 22 NewApi lint, source-APK checks and upgrade-signature verification. Vehicle connectivity remains unverified pending the user's test.

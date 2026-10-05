# E01 实车资料核对（2026-10-05）

本次依据用户从车机导出的 v03 ZIP 和 DiPlay H52 诊断截图。ZIP 中 24 个已收录文件的大小、SHA-256 均与清单一致；另有 60 项因容量限制未收录，2 项路径不可读。固件程序只在本地作为分析输入，不随源码或安装包分发。

## 已确认的内容

- 车机为 alps E01、MT6735、Android 5.1 / API 22，固件 `SWFS11G1105H5182.00305`，支持 arm64 和 arm。
- 诊断记录显示标准 Android 适配器存在，状态为关闭（10）。这不是“硬件不存在”的证据，也不是 RFCOMM 连接测试结果。
- 原厂有 `ecarx.bluetooth.service`，程序位于 `/system/app/XCBTService/XCBTService.apk`，版本 `1.0.26-20191204.3233`。包元数据声明 `ecarx.bluetooth.service.BtService` 已导出且没有组件级权限；是否能在当前实车进程状态下调用，仍需单独验证。
- 原厂还安装了 `com.nforetek.bt`，标签为 `GocsdkServer`，代码路径为 `/system/app/Bluetooth-GocBtAPI/Bluetooth-GocBtAPI.apk`；系统库清单包含 `/system/lib64/libserial_goc.so`。
- H52 截图显示 ANW 服务不可访问或尚未运行、ECarX 状态未知。H52 的 ANW 结果不能用来排除本车的 nFore/GOC 服务。
- 用户进一步澄清：原厂车机蓝牙可以打开；Android 标准蓝牙弹出开启确认，点击开启后一直停在“启用”界面。不能将其记录成“原厂蓝牙无法打开”，也不能把再次点击标准蓝牙开关作为修复步骤。

## 已取得代码能说明什么

从 `XCBTService.odex` 和 `XCBTPhone3.odex` 中提取嵌入 DEX，并静态检查接口、常量及相关字节码；没有执行固件代码。原始 ODEX 的 ZIP 哈希校验通过，提取 DEX 的头部校验值与优化后数据不一致，反汇编使用了工具的忽略 DEX 校验选项。因此本次以可直接核对的接口声明和常量为依据，不把全部反编译内容当作已验证源代码。

`ecarx.bluetooth.IBluetoothManager` 中 `isEnabled()` 的事务号为 5，与现有只读诊断约定一致。`BtService.onBind()` 返回 `BluetoothManagerStub`。当前 H52 诊断从全局 `ServiceManager` 查找 `ecarx_bluetooth_service`；截图的“未知”只表示该检查没有得到有效状态，不能推断原厂应用不存在，也不能直接确认失败原因。

本次查看的 `IBluetoothManager`、`IBluetooth`、`IBtCommand` 接口主要提供开关、配对、电话、音乐及通讯录功能，未找到可用的 RFCOMM/SPP 二进制数据收发接口。SDK 中虽然存在 `ecarx_iap2_service`、`ecarx_carplay_service` 常量，但常量本身不证明对应服务存在或可调用。

## 仍缺的关键代码及原因

1. **GOC/nFore 服务程序和优化代码**：v03 列出了 `com.nforetek.bt`，但筛选规则未匹配其包名/标签，因此没有把程序放入 ZIP。
2. **`libserial_goc.so`**：文件名已被记录，旧规则未选中该库。
3. **系统框架优化代码**：`arm/boot.oat` 为 67,535,964 字节，`arm64/boot.oat` 为 81,996,684 字节，均被列为 `size_limit`。大量其他 ECARX 应用先占用了 192 MiB 总预算，例如 `XCMedia2.apk` 单项占 81,326,744 字节。已取得的 framework/services JAR 只有 310 字节，没有 DEX；`ecarx-adapter.jar` 也只有资源/AIDL，缺少适配实现。

## 使用现有资料继续检查

用户明确不希望再次安装采集工具，后续以已有 ZIP 和截图为输入，不要求重复安装或重复开启系统蓝牙。

进一步扫描 ZIP 内全部 18 个 APK/JAR 容器和 11 份 DEX（累计 41,452 个类定义，含重复 SDK 类）后，在 `XSFConfigService.odex` 中发现完整的 `ecarx.iap2.IAP2Manager`、`IIAP2Manager` 及回调客户端代码。它查找 `ecarx_iap2_service`，定义 6 个 Binder 调用，以及 CarPlay 消息和音视频 socket 名称。`isIAP2DeviceConnected` 对应的客户端调用可由调试字符串确认。

该配置程序内，对这些接口的外部引用仅见 SDK 常量类；未找到 iAP2 服务端实现或配置业务实际调用它的代码。客户端包含尝试启动服务并等待的逻辑，因此也不能把直接调用整个 SDK 当成无副作用的状态查询。此发现说明已有文件中包含 iAP2 客户端约定，尚不能证明服务已部署、正在运行或能替代标准 RFCOMM。

当前 DiPlay 无线启动仍使用 `android.bluetooth.BluetoothDevice.createRfcommSocketToServiceRecord`。原厂电话/音乐蓝牙可用，与该标准 Android 通道一直无法启用并不矛盾。现有离线证据尚不足以确定系统启用卡住的底层原因或实现经过验证的厂商数据通道；不把用户重复操作作为推进条件。

## 已准备但不要求安装的补采工具

本地 v06 采集工具保留无蓝牙服务查询的 v05 流程，按 GOC/nFore、蓝牙组件、系统框架、其他厂商应用排序后再应用容量限制；arm64 `boot.oat` 优先于 arm。新增对 `nforetek`、`gocbt`、`serial_goc` 的匹配，超过 256 项时也保留优先级更高的代码。

v06 安装包保留为本地备选，用户已拒绝再次安装，不再要求执行补采。若以后获得更多固件内容，再核对 GOC 服务的真实接口、权限、数据收发、通道发现与关闭行为；不绕过标准蓝牙检查、不调用未经确认的厂商事务，也不宣称无线连接已修复。

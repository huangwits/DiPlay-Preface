# E01 原厂蓝牙：ECARX SPP 接口新线索

2026-10-05。目标仍为优先经蓝牙建立无线 CarPlay，有线 USB 仅作后备。用户没有缺失的原厂固件文件，不要求重复安装诊断工具或重试卡住的 Android 蓝牙开关。

## 本车与参考代码的交点

本车已导出的 `XCBTService.odex` 中，`ecarx.bluetooth.service.BtServiceProxy` 调用 `com.ecarx.xui.adaptapi.bt.Bt.create(Context)`，再使用电话、配对、音频等代理。这个类名来自本车代码，不是根据另一车型猜测的服务名。现有本车资料仍没有提供 `Bt` 的完整实现或可确认的 SPP 通道。

本轮找到 [StatusBar2-Coolray 的 ecarx-adapter.odex](https://github.com/lugadin/StatusBar2-Coolray/blob/330832de52eca3c50ff83d74f1dc3d12d5b3a504/libs/ecarx-adapter.odex)。它保存了实际代码，不能与仅包含资源的 JAR 混为一谈。仓库名和接口相似性不能证明它来自 E01 或与本车固件兼容。

该参考文件的 SHA-256 为 `d5be212adc8084ac45074fa78515d5c1744a3f0f681fb0ff9c5b6ebf65051c5b`，大小 775,816 字节，格式为 Dalvik ODEX 036。在偏移 40 处有 692,876 字节的嵌入 DEX。静态声明索引包含 645 个类定义和 4,895 个方法标识；方法标识包含引用，不能全算成方法实现。原始文件与分析数据只留在本地，未复制到项目源码或安装包，也未执行。

## 确认存在的接口声明

`com.ecarx.xui.adaptapi.bt.Bt` 声明 `getSpp()`，返回 `com.ecarx.xui.adaptapi.bt.spp.ISpp`。

| 接口 | 已观察到的声明 | 含义与限制 |
| --- | --- | --- |
| `ISpp` | `reqSppConnect(String)`、`reqSppDisconnect(String)` | 有按地址连接/断开的接口，但连接参数没有 UUID 或 RFCOMM 通道号。 |
| `ISpp` | `reqSppSendData(String, byte[])` | 发送参数是原始字节数组；这仍不是本车已可调用或已无损传输的证据。 |
| `ISppCallback` | `onSppDataReceived(String, byte[])` | 定义二进制接收回调。 |
| `ISppCallback` | `onSppStateChanged(String, String, int, int)`、`onSppSendData(String, int)` | 有连接状态与发送结果回调；状态值的语义需要相应实现验证。 |
| `ISppCallback` | `onSppAppleIapAuthenticationRequest(String)` | 提及 Apple iAP，但不能由方法名确定是 iAP1、iAP2，或支持无线 CarPlay。 |
| `com.neusoft.geely.btphone.service.IBtPhoneManager` | `getSpp()` 返回 `com.ecarx.xui.adaptapi.bt1.spp.ISpp` | 另有 Binder 接口及 Stub/Proxy。`bt` 与 `bt1` 是不同接口类型，不可直接互换。 |

## 必须保留的反证

参考 `BtImpl.getSpp()` 的 code item 位于嵌入 DEX 偏移 154,184，指令只有 `const/4 v0, #0`、`return-object v0`（16 位字为 `0012 0011`）。也就是说，**这份具体适配实现返回 null**，并没有把 SPP 暴露出来。

该参考实现还引用 `com.neusoft.geely.btphone.service.BtPhoneManagerService`。这不是已在本车确认运行的服务；本车报告记录的 `ecarx.bluetooth.service` 与 `com.nforetek.bt` 不能因此被当作同一 Binder 合约。

系统 JAR 中发现的 `IAudioBluetoothInterface.aidl` 和 `IBluetoothInterface.aidl` 只提供 `hardKeyCallBack(int, int)`，是按键回调，不是串口数据通道。

Android 37 的 dexdump 拒绝该旧 ODEX 的优化 class flags。本轮没有修改原始代码来使校验通过，而是直接读取嵌入 DEX 的类、字符串、原型、方法标识和目标方法 code item。完整反编译与运行行为未经验证，不能把这个声明索引当作复原后的可运行源码。

## 对适配的影响

原厂蓝牙方向仍有明确的 SDK 线索，不能再把问题简化为“只能用 Android 系统蓝牙”。下一步应核对本车对应的 ECARX `getSpp()` 实现和底层连接语义，尤其是如何选择 iPhone 的 iAP2 服务、是否有可用的二进制收发，以及关闭与回调行为。

现有证据尚不足以加入一个可工作的 E01 厂商后端。仅复制接口、把配对成功当作通道就绪、或绕过标准蓝牙预检再打开原来的 Android socket，都不会实现用户要求的原厂蓝牙连接。本轮没有修改连接代码或生成新安装包，也未完成实车无线验证。

随后按用户提出的手机钥匙方向继续核对，见[吉利 App 钥匙与 CarPlay 配对](E01-PHONE-KEY-20261005.md)。Apple 存在数字钥匙配对机制，但当前 App 内钥匙、DiPlay 消息目录和可用固件资料尚不能构成 E01 的连接实现。

用户随后明确要求先提供实车尝试版本，已按这些已知 SDK 声明编写条件启用的反射后端，见 [E01.6 原厂蓝牙实验](releases/PREFACE-0.2.12-E01.6-FACTORYBT.md)。这项实验允许验证本车是否实际提供该接口，尚不改变上述兼容性和 iAP2 服务选择未验证的结论。

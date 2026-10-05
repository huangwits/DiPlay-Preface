# E01：原作者更新、H52 和 nFore 案例核对

核对日期：2026-10-05。原作者 `main` 已到 `29572a2`，最新已发布版本仍是 0.2.12。H52 分支已到 `b0fdb35`，最新 Release 为 [v0.2.10-geely-api18.3](https://github.com/xikai6282/DiPlay-Geely-Android43/releases/tag/v0.2.10-geely-api18.3)。本轮通过在线 Releases、Git 源码和 GitHub 代码搜索核对；通用网页搜索工具不可用，不能声称已穷尽互联网。

## H52 新版确实新增了数据通路

[H52 ANW 实现和限制](https://github.com/xikai6282/DiPlay-Geely-Android43/blob/4da49ab771fc1e91bc36624b3026db592d542cd9/docs/geely-android43/H52-ANW-CONNECTION.md)说明其默认关闭的实验后端绑定运行中的 `com.anwsdk.service/.AnwSdkService`，读取原厂配对记录，初始化 SPP，请求 iAP2 UUID，确认 slot/对端后通过二进制回调收发。它已经不只是状态诊断。

作者也明确记录了限制：模拟 Binder 测试不代表 H52/iPhone 实车握手通过，native UUID 字节序仍待验证。本台 E01 的导出清单确认的是 nFore/GOC，并未证实可用的 ANW 服务；不能因同为吉利车机就复制其事务号。没有整分支合入 Android 4.3 兼容代码。

## 找到的 nFore 二进制接口

| 公开案例 | 实际证据 | 本车适用性 |
| --- | --- | --- |
| [Yunduo nFore SPP 命令](https://github.com/shilapi/YunduoApkModify/blob/26c7c7409ef99e4b8fa018d4a089152e3b9f7185/Extracted/SGMWBTPhone/smali/com/nforetek/bt/aidl/INfCommandSpp.smali)及[回调](https://github.com/shilapi/YunduoApkModify/blob/26c7c7409ef99e4b8fa018d4a089152e3b9f7185/Extracted/SGMWBTPhone/smali/com/nforetek/bt/aidl/INfCallbackSpp.smali) | `reqSppSendData(String, byte[])`、`onSppDataReceived(String, byte[])`，还有状态、发送结果和 Apple iAP 认证请求回调。 | descriptor 为 `com.nforetek.bt.aidl.INfCommandSpp`。连接方法没有 UUID 参数，iAP 回调也不能单独证明 iAP2/CarPlay 支持。尚未找到与 E01 GOC 程序对应的实现。 |
| [Yecon BTSuite2 命令](https://github.com/wert3232/yecon/blob/87658d255c405802d906be389353ef2b2e828d59/packages/apps/BTSuite2/src/nfore/android/bt/servicemanager/UiServiceCommand.aidl)及[回调](https://github.com/wert3232/yecon/blob/87658d255c405802d906be389353ef2b2e828d59/packages/apps/BTSuite2/src/nfore/android/bt/servicemanager/UiSppCallback.aidl) | 存在按地址连接、`byte[]` 收发和连接状态回调。 | 命名空间为 `nfore.android.bt.servicemanager`，接口顺序与新版不同。这是另一种合约，不能混用 Binder 编号。 |
| [Haval M6 nFore 补丁](https://github.com/Anton111111/haval_m6/blob/977e2104f6149eb44a23d87fef0a2ac160a19a30/README.md) | 修改电话期间前台应用行为，使用指定 APK 哈希和签名校验。 | 对象为 `com.nforetek.bt.customer.service`；不是 E01 标准蓝牙启用修复，也没有 DiPlay iAP2 后端。未执行补丁或导入其系统文件。 |

不能再笼统说“nFore 没有二进制 SPP”。公开接口确实存在，但厂商版本、服务绑定、数据通道语义和权限必须与本车匹配。

本项目现有只读兼容代码查找 `ecarx_btuiservice` / `com.nforetek.bt.aidl.UiCommand`。这是已有适配参考，**不是本车已测得该 Binder 可用的证据**。本轮精确搜索上述 descriptor、`ecarx_btuiservice`、`Bluetooth-GocBtAPI`、`GocsdkServer` 和本车固件号，未得到对应公开实现；检索未命中不证明互联网或固件中不存在。

## 本轮原作者代码适配

- 合入无线交接 watchdog、USB 原地切换/VPN 授权、Dock/视图区、紧凑首页及仪表画面更新。
- 合并 USB 生命周期时保留本项目的传输选择入口，并清除 USB 插入 Intent 中可能残留的无线选择，避免切回旧传输。
- E01 模式即使曾保存旋转选项，也保持单屏、H.264、30 fps、960×540 像素预算；Dock 设置仍可使用。
- 新增的悬浮权限检测和剪贴板调用使用 API 22 回退；热点能力回调有 API 33 检查。
- Android 13 热点修复入口不在旧版 Android 显示，旧平台调用也会在访问 ADB/密钥前返回“不支持”。这项更新不能用来修复 E01 蓝牙。
- Windows 热点事务测试仅替换 AtomicFile 的 POSIX 原子重命名模拟，保留真实的序列化、fsync、校验和和回读检查；生产日志实现保持不变。USB 命令测试使用实际 shell 脚本文件，避免 Windows 命令行重新解释引号。

## 尚未解决的连接问题

现有导出资料仍缺 `/system/app/Bluetooth-GocBtAPI/` 中的 APK/ODEX 和 `/system/lib64/libserial_goc.so`。缺口是本车服务合约、iAP2 通道发现及收发语义，不能靠放行 Android 蓝牙检查解决。

原作者本轮无线 watchdog 修复发生在 Bluetooth/iAP2 启动之后，不会让卡在启用的标准蓝牙自动可用。**E01 无线蓝牙连接仍未修复**；本轮完成的是上游适配和案例核对，未做实车连接验证。继续使用现有资料，不要求用户重复安装采集器或反复开启系统蓝牙，也不把不匹配的厂商后端当作已修复版本交付。

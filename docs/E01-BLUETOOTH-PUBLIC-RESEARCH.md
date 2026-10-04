# E01 蓝牙公开案例核对（2026-10-05）

本轮直接检索 GitHub 的公开仓库、问题记录与源码。**找到相关平台和厂商接口线索，但没有找到可验证、可直接用于这台 E01 的 DiPlay 无线蓝牙修复。** 下列外部项目的描述是其作者或报告者的记录，不代表本项目已在车机上复现。

## 案例与适用范围

| 来源 | 查到的证据 | 对本车的意义与限制 |
| --- | --- | --- |
| [GEELY preface E01：mtk-easy-su #154](https://github.com/JunioJsv/mtk-easy-su/issues/154) | 报告日志包含 Android 5.1、MT6735、Linux 3.10.65，并出现系统命令缺失导致工具运行失败。 | 与目标平台吻合；这是提权工具故障记录，没有证明标准蓝牙、RFCOMM 或无线 CarPlay 已修复。 |
| [ECARX E02 / IHU717P 蓝牙实验](https://github.com/nearlynydev/magisk-ecarx02-ble/blob/main/README.md)及其 [service.sh](https://github.com/nearlynydev/magisk-ecarx02-ble/blob/main/service.sh) | 该 Android 9 项目替换系统蓝牙组件，并处理 `ro.ecarx.bt_ismtk`、`gocsdk` 与 MTK/STP 栈的关系。 | 提供“厂商栈与 Android 标准栈可能不同”的排查线索；不能由此断定 E01 的开关故障原因。E02 的 APK、HAL、驱动和修改脚本不适用于未经核对的 E01 Android 5.1。 |
| [公开 ECARX BluetoothAdapter 源码](https://github.com/DanilKozlov00/jaxb/blob/4b6442166873cbec211aa0d7b49f95f694b4da38/app/src/main/java/ecarx/bluetooth/BluetoothAdapter.java)与 [IAP2Manager](https://github.com/DanilKozlov00/jaxb/blob/4b6442166873cbec211aa0d7b49f95f694b4da38/app/src/main/java/ecarx/iap2/IAP2Manager.java) | 存在厂商命名空间、Binder 服务和 CarPlay 本地 socket 名称；iAP2 管理器还会在服务缺失时尝试启动服务。 | 可作为比对固件的搜索线索。检查过的接口没有建立可供 DiPlay 直接使用的通用 RFCOMM 双向流契约；固件来源、E01 匹配、访问权限及协议语义仍未验证，不能直接调用或复制接入。 |
| [Proton X50 AutoKit 案例 #76](https://github.com/xeon1989/Proton-X50-APK-Installer-ATLAS/issues/76) | 报告者及维护者反馈 AutoKit / CCPA 盒子方案可使用方向盘按钮，后续有 CarPlay 反馈。 | 是外置盒子及其应用的方案，不构成 E01 内置蓝牙供 DiPlay 使用的成功案例，也没有验证本车兼容性。 |

## 当前判断

原厂电话、音乐可用，但 DiPlay 获取不到标准 Android 适配器，**可能与厂商私有蓝牙栈有关**。这是由实车症状和其他平台源码形成的推断，尚非已确认根因。修改应用开关、将厂商 enabled 状态当作 RFCOMM 可用，或直接套用 E02 系统补丁，都不能完成已验证的修复。

接下来的有效输入是本车的系统蓝牙 APK、framework/JAR、固件版本和服务声明。收到这些资料后，可逐一核对厂商栈、iAP2 服务、连接/读/写/关闭接口及调用权限；如果固件没有向第三方暴露数据通道，需要另行评估系统层适配。

## 本次可用版本

[v03 蓝牙诊断预览版](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.3-e01-bluetooth-diagnostics) 新增 **“导出 E01 蓝牙适配资料 ZIP”**，用于收集上述接口资料。该操作不要求蓝牙先开启、iPhone 先配对、root 或电脑 ADB。导出失败或系统文件不可读会保留限制信息；没有固件内容的报告仍不足以实现数据通道。

操作见 [E01 接口资料导出说明](E01-BLUETOOTH-INTERFACE-CAPTURE.md)。本版本未包含上述第三方固件、修改脚本或厂商源码，也未更改车机蓝牙栈。

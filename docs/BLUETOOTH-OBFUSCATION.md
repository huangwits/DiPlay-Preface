# 蓝牙代码混淆的范围

本地 `e01Licensed` 开启 R8 优化和混淆。交付前检查最终 APK 的 DEX 类定义、业务方法名及对应 mapping，覆盖 E01 管理、权限接口调用、手机查询、GOC 通道和蓝牙切换代码：业务类应改名或被优化内联，Android 页面内部方法应改名或内联。Android 入口类 `E01GocActivity` 保留 manifest 所需的名称；生命周期方法也保留平台约定名称。

所有者于 2026-10-09 明确：重点是借鉴实现的蓝牙适配代码。当前检查覆盖 `E01GocManager`、`E01RootBridge`、`E01GocPreferences`、`GocSnapshot`、`E01ConnectedPhones`、`GocSppTransport`、`E01BluetoothSwitchIntegration` 及蓝牙工具页面的内部操作方法。它们对应检查、选手机、临时测试、安装、恢复与连接切换；以下脚本和二进制边界仍然适用。0.2.15.4 的最终本地 APK 已重新通过此检查，本次发布整理不改变其字节或签名。

```sh
python scripts/check_bluetooth_obfuscation.py mobile/build/outputs/apk/e01Licensed/mobile-e01Licensed.apk mobile/build/outputs/mapping/e01Licensed/mapping.txt --output /private/output/bluetooth-obfuscation.json
```

检查失败不得把该包描述为已通过蓝牙业务代码混淆检查。mapping 和配置报告保存在 APK 外，用于排错；不要把它们作为安装包附件公开分发。普通本地 `e01` 仍按既有要求不混淆，此检查用于 `e01Licensed`。

**不能保证无法反编译。** R8 改名、裁剪和内联提高分析成本，不是加密或不可逆保护。DEX 仍可被反编译，运行时也可能被调试或提取。`assets/e01-goc/manage.sh` 是可直接读取的维护脚本，R8 不处理它；本地 GOC 二进制也可从 APK 提取。公开源码按既有分发政策提供，因此不能将功能实现称为秘密或不可还原。原作者署名与许可证要求继续保留。

这里没有加入自解密脚本或声称本地密钥可阻止提取。实际报告明确列出可提取资产、保留的入口名称和已混淆的业务类。没有实车测试不等于已验证加固后的实车连接。

Android 官方将 R8 混淆描述为缩短类、字段和方法名称，见 [Android 应用优化说明](https://developer.android.com/build/shrink-code)。本项目的检查只能证明最终包采用了这些变换，不能证明不可逆向。

## English

The final e01Licensed APK is checked against its R8 mapping and DEX definitions for renamed or inlined Bluetooth business classes and internal activity methods. Android entry/lifecycle names remain. Mapping stays outside the APK. This is not a guarantee against decompilation: the DEX and local native payload remain extractable, and the maintenance shell script remains readable. Source-distribution and upstream license obligations still apply.

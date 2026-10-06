# E01.8：可选 H52 ANW 原厂蓝牙 / Optional H52 ANW factory Bluetooth

## 中文

版本 `0.2.12-e01.8-factorybt-android51` / versionCode 37，保留 E01.7 的原作者更新 `e6477e1`、Android 5.1/API 22、同一包名和覆盖安装签名。

### 新增功能

连接设置 → 原厂蓝牙实验 → 原厂蓝牙接口，可选 **ECARX（E01 实验）** 或 **H52 ANW（新增实验）**。ECARX 仍是默认值。选择 H52 后尝试读取运行中的 `com.anwsdk.service/.AnwSdkService` 配对列表；无服务、服务未运行、权限不足或 Binder descriptor 不匹配都会报错，不将这些情况当作原厂蓝牙不存在。

H52 连接使用来源固件的 ANW SPP 协议，包含本地 SPP 初始化、指定 iPhone iAP2 UUID、回调接收、分段发送、slot/对端连接确认和退出清理。不会自动启动厂商服务，不切换原厂蓝牙电源，不在失败后悄悄改用另一套接口。切换接口会清除 DiPlay 中旧的手机选择，要求从新接口的列表重选；原厂配对记录不受影响。返回已有会话时，接口变更会触发会话重建。

先在原厂电话应用开启蓝牙并配对 iPhone。覆盖安装完整 APK，在连接设置选择 H52 ANW、选择手机，保留正确 Wi-Fi 设置后连接。`E01-H01` 表示服务/接口不可用，`E01-H02` 表示手机选择无效，`E01-H03` 表示 ANW 数据连接失败。iAP2 握手失败仍显示 `E01-F08`。诊断报告记录所选后端与具体失败。

### 适用性与来源

现有参考资料标注 H52.10500 / H52.12000，无法仅据此唯一确认车型。E01 已有资料未确认 ANW 可用，因此本版按用户要求加入额外尝试路径，并不宣称这台车具备该服务。匹配服务和成功配对也不等于完整 CarPlay 已通；native UUID 字节序及 E01/iPhone 实际连接仍需实车验证。

选择性导入 [Android 4.3 分支的 ANW 模块](https://github.com/xikai6282/DiPlay-Geely-Android43/tree/b0fdb350b3a2fc4c6ebda5d62510caa043d3fc23/shared/src)，保留来源与许可，仅使用通用传输模块，项目目标仍为 API 22；未整分支合并，也未导入 OEM 固件。

发布验证包含原模块协议/收发/清理测试、接口不匹配零事务测试、连接取消与晚到结果释放测试、后端切换和错误文案测试，以及 common/shared 回归、API 22 lint、源码包检查和完整 APK 认证/签名验证。桌面测试不证明实车连接成功。

## English

Version `0.2.12-e01.8-factorybt-android51` / code 37 retains E01.7 original main `e6477e1`, API 22, package identity and upgrade signer. Connection setup now offers ECARX (default) and optional H52 ANW factory Bluetooth.

The ANW client binds only to an already running factory service, validates its Binder descriptor, reads paired devices, and uses the sourced H52 SPP contract for local initialization, iAP2 UUID connection, binary callbacks, partial writes and owned-slot teardown. It does not toggle the radio, auto-start the vendor service or silently fall back to another backend. Changing backends clears only DiPlay phone selection and rebuilds an existing session; factory pairing records remain intact.

Pair the iPhone in the factory phone app, select H52 ANW in DiPlay and choose the phone again. E01-H01 means the service/interface is unavailable; H02 means invalid selection; H03 means data connection failure. E01-F08 still identifies iAP2 bootstrap failure. Save the built-in report for details.

Modules and tests are selectively imported from the GPL-3.0 Android 4.3 fork at `b0fdb350b3a2fc4c6ebda5d62510caa043d3fc23`; see THIRD_PARTY_NOTICES.md. H52 firmware labels do not establish a unique vehicle model or E01 compatibility. UUID interpretation and full vehicle/iPhone connectivity remain unverified. Release gates cover regression, API 22 lint, source APK, actual full-APK bootstrap and upgrade signature.

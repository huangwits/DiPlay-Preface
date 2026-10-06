# E01.9：连接状态与超时恢复 / Connection status and timeout recovery

## 中文

本地候选版 `0.2.12-e01.9-connection-android51`，versionCode 38。针对 E01.7 / E01.8 在安卓手机系统蓝牙无线连接测试中停留在“正在准备 CarPlay…”的反馈排查。

已确认并修复两处代码问题：

- 连接页对已经本地化的状态再次匹配英文关键词，把中文认证、握手、网络超时、权限拒绝和停止重试原因覆盖成默认提示。现在直接显示实际状态，并在内置日志中记录状态类型。
- 系统蓝牙的无线控制循环超时、且尚未完成 CarPlay 切换时，只报告 `ControlEnded`，页面不启动恢复。现在释放本次无线资源并报告握手超时，进入现有的最多五次自动重试流程；重试耗尽后显示原因和手动重试按钮。过期连接和已经完成切换的连接不受这条超时处理影响。原厂蓝牙保留 `E01-F08` 处理。

无线控制循环的现有超时时长和 iAP2 协议参数没有变更。本次不能据此确认用户手机上的握手或 Wi-Fi 会话失败原因，也不能宣称已恢复 CarPlay 画面；还需要该次失败的内置连接日志与设备复测。

安卓手机测试时，在连接设置中关闭“使用车机原厂蓝牙”，使用系统蓝牙与 iPhone 配对。E01 车机的 ECARX / H52 选项保留。完整包使用原包名与签名，可覆盖升级，保留设置和配对记录。

无需另装采集工具：卡住后返回 DiPlay → 设置 → 诊断 → 保存诊断报告 → 查看。保留失败现场的报告，再重试；报告末尾的连接日志用于区分身份识别、认证、Wi-Fi 接入和 AirPlay 会话阶段。

验证：907 项 shared 测试、703 项 common 常规测试及 2 项实际完整 APK 启动认证测试通过；常规测试中按设计跳过的 2 项认证测试已在实际 APK 上单独运行且零跳过。另通过 4 项维护脚本测试、API 22 NewApi lint、源码包无认证材料检查及 APK 签名/认证文件校验。3 项 USB 脚本测试初次因测试机 Git shell 路径错误失败，纠正路径后重跑该测试类的 16 项全部通过，未为此改动应用代码。

## English

Local candidate `0.2.12-e01.9-connection-android51`, versionCode 38, investigated after E01.7 / E01.8 remained on the preparation screen during Android-phone system-Bluetooth wireless testing.

The host now preserves localized status and failure text instead of reclassifying it with English substring matching. Status types are recorded in the existing diagnostic log. An incomplete Android Bluetooth bootstrap that reaches its control-loop timeout now releases its wireless resources and enters the existing bounded recovery policy, with at most five automatic retries. Completed handoffs and stale generations are preserved; factory Bluetooth retains its E01-F08 error.

Existing timeout durations and iAP2 protocol parameters are unchanged. These verified code defects do not establish the cause of the reported phone failure or prove successful projection. The failed attempt's built-in connection log and device retest remain necessary.

For Android-phone testing, disable factory Bluetooth in connection settings and use system Bluetooth. The full candidate preserves the E01 package, upgrade signer and preferences. Save and view the built-in diagnostic report after a failed attempt; no separate capture application is needed.

Validation: 907 shared tests, 703 regular common tests and two actual full-APK bootstrap tests passed. The two opt-in bootstrap cases skipped by the regular run passed separately with zero skips. Four maintenance tests, API 22 lint, source APK isolation and APK authentication/signature checks also passed. Three initial USB shell test failures were caused by an incorrect host Git shell path; all 16 cases in that class passed after correcting the test environment.

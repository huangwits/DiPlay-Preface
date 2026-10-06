# E01.10：恢复系统蓝牙默认路径 / Restore system Bluetooth by default

本地候选版本 `0.2.12-e01.10-systembt-android51`，versionCode 39。

对照 carlito12345 的已审查源码 `84050d6`，E01.7–E01.9 完整 APK 的 `config_factory_bluetooth_default=true` 是一处明确的行为差异：未手动选择蓝牙接口时，E01 会调用 ECARX 原厂接口，而 carlito 使用 Android 系统蓝牙。这会使普通安卓手机进入其不具备的原厂接口路径。

本版将安装包中的默认值改为 `false`，恢复 Android 系统蓝牙。自动 Wi-Fi 的尝试顺序、协议参数和认证输入没有修改；保留 E01.9 的中文状态与超时恢复修正，以及 API 22 兼容、E01 低负载显示配置、ECARX / H52 ANW 手动选项。

覆盖安装保留原包名、签名和设置。此前仅使用安装包默认值的安装会采用系统蓝牙；明确保存过的原厂蓝牙选择仍被尊重。安卓手机测试请在连接设置中确认“使用车机原厂蓝牙”关闭；若曾开启，手动关闭，再从系统蓝牙配对列表选择 iPhone。需要原厂接口的车机可手动开启并选择对应接口。

该默认值差异可以由源码及新旧 APK 资源验证；它是否解释用户本次故障取决于失败时的实际设置。尚无本版手机/iPhone 或实车连接结果，不能将桌面验证视为投屏成功。

验证：907 项 shared、703 项 common 常规测试及 2 项实际完整 APK 启动认证测试通过，另有 4 项维护脚本测试通过。项目指定的 API 22 NewApi lint、源码 APK 无认证材料检查、完整 APK 认证材料比对、原签名及 v1 签名校验通过。额外运行的全规则 lint 未通过：shared 现有源码报告 19 项错误，包括权限标注和私有 API 访问；本版未修改这些源码，也未禁用这些规则。全规则报告单独保留。

## English

Local candidate `0.2.12-e01.10-systembt-android51`, versionCode 39.

Compared with reviewed carlito12345 source `84050d6`, E01.7–E01.9 APKs enabled the ECARX factory Bluetooth path by default. An ordinary Android phone without an explicit preference could therefore enter a vendor path it does not provide instead of Android Bluetooth.

The APK default is now false. Explicit saved choices remain effective across updates; an unset preference selects Android Bluetooth. ECARX and H52 ANW remain manual options. Automatic Wi-Fi ordering, protocol parameters, authentication inputs, API 22 support, E01 display limits and E01.9 status/recovery fixes are retained.

Update using the full APK. For Android-phone testing, turn off factory Bluetooth if it was explicitly enabled, then select the iPhone from the system paired list. Vehicle users who require a vendor backend can enable it explicitly. The source/APK default difference is verified separately from actual phone or vehicle connectivity, which remains unverified.

Validation passed: 907 shared tests, 703 regular common tests, two actual-APK authentication bootstrap tests with zero skips, four maintenance tests, required API 22 NewApi lint, source-APK isolation, packaged authentication comparison and upgrade/v1 signature checks. Additional unrestricted lint did not pass: unchanged shared sources reported 19 errors, including permission annotations and private API access. Its report is retained separately; those rules were not disabled.

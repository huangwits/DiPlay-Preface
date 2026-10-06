# E01.11：carlito 基准与三种蓝牙接口 / Carlito baseline and three Bluetooth choices

## 中文

版本 `0.2.12-e01.11-carlito-android51` / versionCode 40。合入 carlito12345/DiPlay 最新主线 `8f53b27b3168aedb661e9f9bb7122ea344b75ada`，后续默认同步 carlito。保留已有 API 22、E01 低负载配置、中文连接状态、超时恢复、完整包认证与覆盖升级修正。

连接设置现提供统一的三项选择：

- **系统蓝牙（默认）**：安卓手机和标准 Android 蓝牙车机。
- **E01 车机蓝牙（ECARX）**：手动启用 E01 的 ECARX 原厂数据接口。
- **H52 车机蓝牙（ANW）**：手动启用匹配的 H52 ANW 原厂数据接口。

三项选择与 E01 性能开关独立。选中接口并保存不会立即调用厂商服务；切换后清除旧接口选择的手机，在下一步重新选择 iPhone。其他设置和原厂配对记录保留。已明确保存的接口选择在覆盖升级后继续生效。

H52 来自 Android43 项目已审查的 ANW 数据通道，仅移植所需接口，不以该 APK 作为手机连接基准，也不引入整套 Android 4.3 兼容层。本包最低系统是 **Android 5.1 / API 22**。E01 / H52 车机连接尚未完成实车确认。

同步 carlito 的自适应视频解码和导航输出设备选项，补齐 API 22 对音频焦点释放、欠载计数和输出设备 API 的保护。导航设备选择仅在 Android 6.0 及以上显示，E01 5.1 保留原有声道配置。

完整 APK 使用 `com.shihab.diplay.e01legacy` 和原签名，可覆盖安装。只使用文件名以 `-full.apk` 结尾的完整包。旧版本 tag、Release 和附件按所有者要求清理，删除前将源码指向、发布说明和附件全部备份到维护者本地。

## English

Version `0.2.12-e01.11-carlito-android51` / code 40 merges current carlito12345/DiPlay main `8f53b27b3168aedb661e9f9bb7122ea344b75ada` and makes carlito the default update source. Existing API 22/E01 support, localized connection stages, timeout recovery, full-package authentication and upgrade identity are retained.

Connection setup offers **System Bluetooth (default)**, **E01 car Bluetooth (ECARX)** and **H52 car Bluetooth (ANW)**. The selector is independent of the E01 performance profile. Saving a choice does not query vendor services; changing interfaces clears the previous phone selection so the next step selects from the correct paired list. Other preferences and factory pairing records remain intact; explicit saved choices survive upgrades.

H52 imports the reviewed ANW transport from the Android43 project, without its full compatibility layer. The package requires **Android 5.1 / API 22**. Actual E01/H52 connectivity remains unverified. Carlito's adaptive decoding and navigation-output options are merged with API 22 fallbacks; device routing controls are available from Android 6.0.

Install the full APK in place using the preserved package and signer. The owner requested removal of the six older tags, releases and assets; local backups retain their source refs, metadata and assets before deletion.

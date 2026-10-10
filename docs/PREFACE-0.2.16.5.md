# DiPlay 星瑞 0.2.16.5

## 中文

修复部分车机申请授权时的根证书兼容性问题。

优化安卓蓝牙连接：已有授权自动核验，通过后继续连接已保存的 iPhone，无需再次进入蓝牙工具页。未选择手机时直接显示安卓配对手机列表；蓝牙关闭时提示开启，缺少安卓蓝牙时进入工具页准备原厂连接。需要申请或处理授权时仍会显示授权页面。

保留原厂蓝牙适配流程。修复在线更新备用地址的下载请求，并在下载超时后自动尝试备用地址。完整 APK 覆盖安装可保留设置和授权信息。

APK：`DiPlay-Preface-v0.2.16.5.apk`，versionCode `49`，包名 `com.shihab.diplay.preface`，最低 Android 5.1 / API 22。

## English

Improves certificate trust compatibility when requesting authorization on affected head units.

Android Bluetooth now refreshes existing authorization and continues connecting to the saved iPhone without another visit to Bluetooth tools. With no saved phone, the normal paired-device picker opens. Disabled Bluetooth prompts for system settings; unavailable Android Bluetooth opens the tools for factory setup. Authorization that needs attention still opens the authorization page.

Preserves factory Bluetooth setup. Fixes APK requests to the alternate update URL and retries through that URL after a download timeout. Install the full APK over the existing app to keep settings and authorization data.

APK: `DiPlay-Preface-v0.2.16.5.apk`, versionCode `49`, package `com.shihab.diplay.preface`, minimum Android 5.1 / API 22.

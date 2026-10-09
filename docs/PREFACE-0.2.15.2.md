# DiPlay 星瑞 0.2.15.2

本版跟随 Carlito 上游 0.2.15，追加第二次星瑞修订。APK versionName 为 `0.2.15.2`，versionCode 为 `42`，本地文件名为 `DiPlay-Preface-v0.2.15.2.apk`。沿用 `com.shihab.diplay.preface` 包名、既有签名、Android 5.1 / API 22、ARM32/ARM64 和中文资源。

## 本次修正

- USB 恢复上游普通连接与等待界面，不显示软件授权，也不经过激活检查。首页点击 USB、投屏宿主再次检查以及旧 USB 授权入口均回到正常 USB 流程。
- 连接设置移除“软件授权”区块和“打开授权页面”按钮。
- 授权保留在原厂蓝牙工具右侧；无线投屏和“连接测试”仍检查激活，恢复操作及已有 CarPlay 不受影响。
- 群号与“QQ 扫码 · 点图放大”使用统一字体放在二维码下方，移除群名和复制按钮，保留二维码放大、每分钟一次查询和手动刷新。

源码继续使用选定的 Geely 连接基线及已验证的 API 22 适配，版本号不表示完整合入上游 0.2.15。USB 的 Android 系统权限与 CarPlay 配件认证仍正常执行；本次取消的是软件授权界面和软件激活检查。

已有同签名 `.preface` 安装可直接覆盖更新，不要先卸载或清除数据。完整 APK 必须通过 [BUILD.md](BUILD.md) 的认证、签名与 API 22 检查，具体结果随本地交付保存。保留旧 0.2.15.1 产物，不改写历史记录。实车 USB/无线、音频和休眠重连仍需现场验证。

版本规则见 [VERSIONING.md](VERSIONING.md)，公开分发仅提供源码和构建文档，APK 与私人运行输入不公开上传。

蓝牙业务代码使用 R8，并增加最终 DEX 与 mapping 对照检查；Android 入口类名和可读取的维护脚本仍保留，不能保证无法反编译，详见 [混淆范围](BLUETOOTH-OBFUSCATION.md)。

## English

Preface 0.2.15.2 / code 42 restores the ordinary upstream USB connection/waiting flow without a software activation gate or authorization page, and removes the software authorization section from connection settings. Authorization remains in the factory Bluetooth tools for wireless projection and connection tests. Existing recovery/session protections, one-minute polling, manual refresh and QR enlargement remain. The group number and scan hint sit below the QR in a single text style, without a group-name heading or copy button.

Android USB permissions and accessory authentication remain intact. Preserve the existing package, signer, API 22 compatibility and private local inputs. Install over the same-signer app without clearing data. Public distribution remains source-only; real-vehicle validation is still outstanding.

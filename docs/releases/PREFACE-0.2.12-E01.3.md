# DiPlay Preface 0.2.12 · E01.3

## 中文

修正 E01.2 源码预览包缺少本地认证文件、启动提示“无法加载 CarPlay 认证资料”的打包问题。本版使用完整车测构建入口，包含本地实验性配件身份，构建时校验证书与密钥匹配，并核对最终 APK 内文件。

### 安装

下载 `DiPlay-Preface-0.2.12-E01.3-Android51-full.apk`，复制到手机或 U 盘，再传到车机，通过文件管理器覆盖安装。

- 应用：DiPlay E01 Legacy；包名：`com.shihab.diplay.e01legacy`。
- APK 版本：`0.2.12-e01.3-android51`；versionCode：32；最低 Android 5.1 / API 22；ARMv7 / ARM64。
- 与此前 E01.2 包名、签名一致，直接覆盖安装即可，通常不需要卸载或清除设置。
- E01.1 / E01.2 的 `source-only` 包不用于独立车测。GitHub 自动生成的 Source code 压缩包也不能安装。

### 范围

保留 E01 低负载设置和现有兼容代码。本次修正的是认证文件缺失造成的启动失败；车机厂商蓝牙接入、实际 iPhone 握手与音视频兼容性仍待实车验证。

认证材料使用既有本地实验输入，来源和限制见 `docs/THIRD_PARTY_NOTICES.md`。本项目未经 Apple 认证；本地证书/签名校验不代表 iPhone 一定接受该身份。

### 开发验证

完整构建任务拒绝缺失、空文件或不匹配的认证输入，并核对 APK 内两份文件与输入一致。发布测试读取实际 APK，在 Robolectric API 23 沙箱中选择 API 22 代码路径，验证首次安装和旧源码包启动失败后的覆盖升级均能加载认证并完成本地签名。该验证不等同于实车测试。


APK 构建源码：`1f9c77f9874497a24c20592b87296b702943b805`。9 项认证相关测试通过，其中 2 项读取本次实际 APK；Android 5.1 API 检查及同签名验证通过。

---

## English

This full car-test build fixes the missing local authentication files that caused E01.2 source previews to fail at startup. It includes the existing experimental accessory identity. The build checks key/certificate consistency and the final APK assets.

### Installation

Download `DiPlay-Preface-0.2.12-E01.3-Android51-full.apk`, transfer it to the head unit and install it with the file manager.

- App: **DiPlay E01 Legacy**; package: `com.shihab.diplay.e01legacy`.
- Version: `0.2.12-e01.3-android51`; versionCode: 32; Android 5.1 / API 22 minimum; ARMv7 / ARM64.
- The package and signing certificate match E01.2, allowing an in-place update that retains settings.
- Earlier source-only previews and GitHub Source code archives are not standalone installation packages.

### Scope and validation

This release retains E01 performance settings and Android compatibility work. It fixes authentication-file packaging; vendor Bluetooth access, iPhone handshakes and audiovisual compatibility still need vehicle testing.

Authentication inputs are experimental. See [third-party notices](https://github.com/huangwits/DiPlay-Preface/blob/main/docs/THIRD_PARTY_NOTICES.md) for provenance and limitations. The project is not Apple certified; local signature verification does not establish iPhone acceptance.

Nine authentication tests passed, including two reading the actual APK to exercise initial loading and recovery after upgrading a failed source-only installation. These run in a Robolectric API 23 sandbox with API 22 code paths selected. Android API and matching-signature checks passed; these are not vehicle tests.

APK source: `1f9c77f9874497a24c20592b87296b702943b805`.

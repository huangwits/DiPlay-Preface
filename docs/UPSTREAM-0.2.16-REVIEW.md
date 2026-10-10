# 2026-10-10 上游与应用更新适配

原作者 [shihabal3amri v0.2.16](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.16) 与 [Carlito v0.2.16](https://github.com/carlito12345/DiPlay/releases/tag/v0.2.16) 均已发布。Carlito 的[标签构建配置](https://github.com/carlito12345/DiPlay/blob/v0.2.16/mobile/build.gradle.kts)为 `0.2.16 / 41`、最低 API 25。Preface 本次为 `0.2.16.1 / 45`，继续最低 API 22。

Carlito 版本说明包括界面、音视频与麦克风兼容性、KX11 热点连接、语音/音乐协同和方控改进。此次只参考上游手动更新器，未整体合并这些功能；保留已选的 Geely 0.2.13.2 连接基线、E01 GOC 适配、USB 免软件激活与现有无线授权。

## 更新器

- 设置的“关于”分类和关于页面提供“检查更新”。打开页面不自动联网或安装。
- 仅读取 `huangwits/DiPlay-Preface` 正式 Release，比较全部版本段。新版必须有对应命名的 APK 和 GitHub asset SHA-256；缺失或限流时显示错误，不报告已是最新。
- HTTPS 下载限制主机、跳转次数和体积；支持进度与取消。核验大小、SHA-256、包名、完整版本、递增 versionCode 与原签名，安装前复验。
- 用户确认后，受支持 E01 通过原厂服务创建、写入并提交系统安装会话。失败会释放会话、保留下载文件；可改用系统安装。成功后返回应用。
- Android 5.1/6 系统安装使用外部应用目录的文件 URI；Android 7 起使用仅暴露更新缓存的 FileProvider；Android 8 起按需打开未知来源许可页。
- 安装前检查 CarPlay 会话与蓝牙维护。不卸载、不允许降级、不修改未知来源开关、不执行蓝牙切换。覆盖安装保留原应用数据。

## API 22 安装依据

[AOSP Android 5.1.1 Pm.java](https://android.googlesource.com/platform/frameworks/base/+/android-5.1.1_r38/cmds/pm/src/com/android/commands/pm/Pm.java) 的普通 `install` 通过 `installPackageAsUser` 传入源路径；`install-write` 在命令进程中读取文件并写入 PackageInstaller 会话。因此使用 `install-create -r`、`install-write`、`install-commit`，避免让容器服务直接访问应用私有缓存。系统安装器仍会执行签名与升级验证。

桌面回归和模拟系统服务不等于实车验证。实际固件是否允许原厂服务安装、能否自动返回应用，仍需车机验证。旧版没有更新入口，首次安装本版仍需使用原来的安装方法。

## English

Both upstream projects have 0.2.16. Carlito uses API 25 / code 41; Preface retains API 22 with 0.2.16.1 / 45. This selectively adapts the manual updater, not all upstream media and connection changes. Only Preface releases are accepted, with full-version, hash, package, increasing code and signer checks. Explicit E01 installation streams the private APK into an Android package session; system installation remains available. Actual firmware installation remains unverified.

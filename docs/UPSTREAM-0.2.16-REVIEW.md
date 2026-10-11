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

2026-10-11 补充的实车约束：所有者确认，每次安装 APK 仍需开启 WiFi ADB，再使用 mtk-su 和 mount 命令。0.2.16.9 的在线更新器尚未接入这套安装准备流程，不能据此宣称已经省去手动安装。当前原厂安装入口仅根据原厂蓝牙系统包判断是否可尝试；这不证明安装权限或所需挂载条件已满足。自动化前需核对实际成功的挂载目标与安装命令，不能直接复用蓝牙适配脚本的 `/system` 重挂载步骤。

0.2.16.10 补充：原厂蓝牙工具和更新卡片提供显式 Root 检测，通过现有 ExtraUtilsService 执行只读 `id`，仅 UID 0 判为可用；5 秒超时，未返回的事务不会重复堆积。安装前再次检测，不缓存通过结果。包管理器失败按只读、权限、空间、签名、降级及创建／写入／提交阶段显示结果；不推断或自动执行 mount。原厂通道可能省去手工 ADB / mtk-su，但 Root 可用仍不等于已验证该固件允许 APK 安装。

0.2.16.11 调整：移除两处独立 Root 测试按钮，仅在用户确认安装后自动检查权限并继续安装。保留权限探测、超时和安装失败分类；无需用户手动测试。

## 2026-10-11 复查

- [原作者 v0.2.17](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.17) 已发布，API 标记为预览版；主要新增 USB/NCM 容错、旧 iPhone 识别重试、语音消息音频修复和其他车型功能。此次仅审查，未整体合入。
- [Carlito v0.2.16](https://github.com/carlito12345/DiPlay/releases/tag/v0.2.16) 仍是最新正式版，默认分支头为 `505d34a2d6995e7194d645e96bbd1a5769135b7a`。Preface 本地编号保持 `0.2.16.11`。
- 原作者 0.2.17 的媒体键文件变动主要涉及封面与氛围灯，没有提供 E01 原厂完整地图飞屏接口。此次本地补齐了既有完整地图会话接线、旧系统窗口兼容和 Siri 控制，保留已验证的 E01 标准媒体键编号。
- 已检查的 GD Vehicle Bridge 0.11.25 FX11 APK 最低 API 28，不能直接安装到 API 22；通用副屏输出也不能证明原厂仪表已开放完整地图通道。需要实车接口证据后才能确认 E01 原厂完整飞屏。


## English

Both upstream projects have 0.2.16. Carlito uses API 25 / code 41; Preface retains API 22 with 0.2.16.1 / 45. This selectively adapts the manual updater, not all upstream media and connection changes. Only Preface releases are accepted, with full-version, hash, package, increasing code and signer checks. Explicit E01 installation streams the private APK into an Android package session; system installation remains available. Actual firmware installation remains unverified.

## 0.2.16.12 固件核对

2026-10-11 再次读取两路官方 Release 页面，原作者仍为 v0.2.17 预览版，Carlito 仍为 v0.2.16；未整体合并。旧 FS11 固件确认原厂三指广播与受保护的仪表模式接收器，本版接入该链路并共享真实导航状态；发送请求不代表仪表已确认出图。原厂地图的窗口占用和车辆实际副屏仍需验证。固件安装器另有 APK 授权检查，Root 不会跳过；已安装系统应用有独立豁免分支。本版增加安装类型诊断与失败分类，仍不猜测或修改挂载。

# Preface 0.2.13.2.1

> 历史版本记录：以下版本、低负载配置和 mtk-su 流程描述当时行为，不能用于当前构建。当前版本及安装说明见 [Preface 0.2.15.1](PREFACE-0.2.15.1.md)，编号规则见 [VERSIONING.md](VERSIONING.md)。Historical release record; use the linked current release notes for new builds.

## 基底与版本

- Geely 基底：`586b39368b0bf7e3e9cb50a0b7838db083b382e1`，版本 0.2.13.2。
- GitHub 修订：`v.0.2.13.2.1`；APK 文件：`DiPlay-Preface-v0.2.13.2.1.apk`。
- APK 内部 versionName：`0.2.13.2`，versionCode `36`，与所选 Geely 基线一致；末尾 `.1` 为公开维护修订号。
- 包名：`com.shihab.diplay.preface`；最低 API 22，保留既有签名。
- 版本信息于 2026-10-07 从 Geely `03af895e36c6970b64ae48f31595d32f60bd1ffb` 核对；本适配的源码基底仍为上述 `586b3936`。

沿用 Geely 的 Android 蓝牙连接、RFCOMM、热点与会话管理基线。旧系统所需的系统服务获取、权限、USB 传输和音频接口提供版本分支。新的蓝牙切换工具只在用户按按钮时运行，切换前等待当前会话关闭。此前分支的私有 ECARX/H52 RFCOMM 通道没有迁入。

E01 默认启用低负载配置：H.264、最高 960×540、30 帧、关闭额外地图视频流。可在设置中关闭，原先保存的画质选项会保留。Geely 车辆桥接和投影功能仍取决于实车接口。方向盘功能不再带其他品牌的固定键码，只使用本车已学习的按键。

## 安装与使用

安装完整 `DiPlay-Preface-v0.2.13.2.1.apk`。已安装同签名 `.preface` 的用户可覆盖安装以保留设置。新包名 `.preface` 会与旧 `.e01legacy` 分开安装，旧设置、权限及本机 ADB 授权不会自动迁移，需要在新应用中重新设置。测试时只运行一个 CarPlay 应用。源码包和源码 CI APK 不包含独立使用所需的认证输入，不作为车机安装包交付。

蓝牙入口：“连接 → 打开蓝牙切换工具”。按“检查连接 → 尝试切换”操作；需要系统授权时允许本机调试。默认端口为 5555，也可填车机实际端口。工具会检测 uid=0，不具备 root/ADB 管理员能力时不会切换服务。

切换仅支持已分析的 E01 / MT6735 固件 `SWFS11G1105H5182.00305` 或 `SWFS11G1115H7007.00018`，同时检查服务和设备节点状态。临时停止原厂蓝牙电话、音乐；不修改系统或固件分区。测试完点“恢复原厂”，恢复未确认时重启并检查原厂功能。遇到问题可“复制日志”。日志保存在本机，不自动上传。

## 连接与蓝牙日志界面

连接等待页仅在 USB 模式显示日志，支持复制、跟随和滚动；无线及原厂蓝牙工具不显示诊断日志。联网授权版的原厂蓝牙工具改为左侧操作、右侧授权与 QQ 群卡片，两侧分别滚动。连接失败原因在下一次等待时保留。屏幕日志有容量上限，完整连接报告沿用原有保存路径。此项为界面和诊断展示调整，不代表 USB 或蓝牙实车故障已经修复。

连接等待页与蓝牙工具共用按钮、端口输入框和文字配色。蓝牙工具同步读取 CarPlay 的跟随系统、日间、夜间、光感设置，并在恢复页面与系统配置变化时刷新；暂停时停止光感监听。

## E01 临时权限适配（本地内置测试包）

内置用户提供并已校验的 AArch64 mtk-su（原作者 diplomatic@XDA）。无需在 /sdcard 放置文件。点击“尝试切换”或“恢复原厂”时，已有校验通过的可执行文件直接复用；否则通过已授权的本机 ADB SYNC 将内置文件准备到 /data/local/tmp/mtk-su，核验字节后设置 755。传输失败、目标异常、校验失败时不执行。源码普通构建不带该文件，仍可使用用户已有的 /sdcard 文件。

兼容 E01 的完整 UID 输出。已有 su 或 root ADB 可以直接使用。mtk-su 与蓝牙脚本在同一次调用中运行，脚本读取 /proc/self/status 再次确认实际有效 UID 为 0。“检查连接”及普通 CarPlay 启动不释放文件或提权。操作前校验机型、平台和固件，不重挂载 /system，不增加常驻 root 服务。

内置二进制通过外部构建输入提供，仅用于用户要求的本地个人测试包，未加入公开源码或发布到 GitHub；保留原作者信息，不声明额外分发授权。实际提权及蓝牙切换效果仍待实车验证。

## Android 5.1 文件读取修复（本地测试）

读取 mtk-su 改用 ADB `exec:` 原始通道，避免旧版 `shell:` 伪终端转换二进制换行而造成校验失败。不回退到 shell 读取二进制。机型、平台、固件、64 位执行环境、文件类型、读取权限和执行权限失败会分别记录实际条件；文件不一致时显示读取字节数和实际 SHA-256，ADB 连接异常也不再被吞掉。仍保留原文件校验，不把错误输出当成可执行文件。

此修复基于 Android 5.1 ADB 源码及模拟传输验证。截图只能确认旧版未成功准备权限通道，不能单独证明实车失败一定由换行转换导致。

## USB 打开失败恢复（本地测试）

USB 控制请求执行前重新读取当前设备和权限，避免使用排队期间已失效的设备对象。打开失败会等待 500ms 后重新识别，最多执行两次配置请求；持续失败时日志保留设备是否仍存在、当前权限及配置数量。成功发送配置切换后，每 500ms 主动检查新设备或更新的 CarPlay 描述符，最多等待 15 秒，兼容系统漏发插入广播。切换连接关闭前不启动下一次 USB 操作；关闭或替换连接尝试会取消旧扫描。此项不修改 USB 节点权限，不代表线材、车机接口或实车连接已验证。

## 验证范围

需要通过 common/shared 回归、API 22 lint、Geely 功能范围检查、脚本测试、源码 APK 检查和完整包认证/签名验证。完整包的两个 bootstrap 测试必须实际执行并且零跳过。详细日志与结果 JSON 保存在本地验证目录，不作为发布附件。

Android 5.1 使用默认音频设备；网页视频需要 Android 6+，外部桌面嵌入地图需要 Android 11+。尚未完成实车安装、配对、USB/无线连接和电话/Siri 验收；蓝牙报告开启不能替代这些验证。

## English

The GitHub revision is `v.0.2.13.2.1`, with APK filename `DiPlay-Preface-v0.2.13.2.1.apk`. The APK versionName remains `0.2.13.2`, version code 36, matching the selected Geely metadata verified at `03af895e` on 2026-10-07. The final `.1` identifies the public maintenance revision only. Install over an existing same-signer `.preface` app to retain its settings. The adaptation's code baseline remains `586b3936`. The package is `com.shihab.diplay.preface`, minimum API 22, using the existing signer. It installs separately from `.e01legacy`; configure its settings, permissions and local ADB authorization again. Run only one CarPlay app at a time.

It retains the selected Geely connection baseline, adds required legacy API branches, removes BYD-specific integrations and adds an explicit E01 Bluetooth switching tool. The previous fork's private ECARX/H52 RFCOMM backends are not imported. Switch and restore require verified privileged access and run only on the documented E01 firmware, with CarPlay stopped beforehand. Factory calls/music are interrupted during switching. Restore after testing or restart if recovery cannot be confirmed.

The E01 profile limits video workload while preserving saved preferences. Geely bridge, projection and learned keys remain hardware-dependent. All local gates, including the two actual-APK bootstrap tests with zero skips, must pass before delivery. No physical vehicle/iPhone validation is claimed.


Connection waiting and Bluetooth tools share a split layout with controls on the left and live logs on the right. Narrow windows stack the panels. Copy and follow-latest actions are available; reading older lines pauses automatic following. The last failure remains visible during retry. These changes improve diagnostics and do not claim to fix vehicle connectivity.

Local personal test build: includes the owner's verified AArch64 mtk-su, attributed to diplomatic@XDA. Explicit Switch/Restore actions reuse a verified executable or prepare the bundled bytes via authorized local ADB SYNC, read back via raw exec, then enable mode 755. No /sdcard file is required. Normal startup and Check Connection do not deploy or execute the helper. The same-process guarded script verifies its own effective UID. No /system remount or persistent root service is added. Binary input remains outside the public source tree; no additional redistribution license or vehicle success is claimed.

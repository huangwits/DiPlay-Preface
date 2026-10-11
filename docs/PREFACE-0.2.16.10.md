# preface v0.2.16.10

- 原厂蓝牙工具和更新卡片增加“测试 Root 权限”，直接显示车机原厂通道的检测结果。
- 安装更新前重新检测权限；检测未通过时保留安装包，避免误报正在安装。
- 区分文件系统只读、安装权限不足、空间不足和覆盖安装失败等原因。
- 保留当前页面检查更新、统一深色界面及开机热点改进。

Root 检测通过只表示原厂通道能以 Root 执行命令，直接安装仍待实车确认。本版不会自动修改挂载；检测按钮不会安装永久 Root。

版本 `0.2.16.10` / `54`，最低 Android 5.1。本版为本地候选，GitHub 公开版本仍为 0.2.16.7。

## English

- Add an explicit root access test to factory Bluetooth tools and the update card.
- Recheck access before installation and keep the downloaded APK if access is unavailable.
- Distinguish read-only filesystem, permission, storage and package installation failures.
- Retain inline updates, the unified dark interface and boot hotspot improvements.

A successful root test confirms command execution through the factory service; direct installation still needs vehicle validation. This revision does not change mounts automatically or install permanent root.

Version `0.2.16.10` / `54`, Android 5.1 minimum. Local candidate; public GitHub release remains 0.2.16.7.

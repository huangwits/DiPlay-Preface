# preface v0.2.16.11

- 移除蓝牙工具和更新卡片中的“测试 Root 权限”按钮。
- 确认安装更新后，应用自动检查安装权限并继续安装，无需手动测试。
- 检查失败时说明原因并保留下载的安装包。
- 设置 → 方向盘按键中新增 Siri 唤醒开关，并接入原厂语音键事件；不影响切歌、通话和 iPhone 自身的 Siri。
- 三指左滑投出、右滑收回，上滑切换；保留设置菜单的手指数选项。
- 修复完整地图投屏没有接入 CarPlay 会话、Android 5.1 窗口类型不兼容、主屏误占仪表视频通道的问题。完整地图需先在“地图投屏”中选择仪表并重新连接。

保留此前界面、热点及安装校验改进。完整地图依赖车机开放的仪表屏幕；E01 原厂导航提示与完整地图是两种输出。原厂完整地图飞屏和直接安装仍待车机接口核对及实车确认，本版不自动修改挂载。

版本 `0.2.16.11` / `55`，最低 Android 5.1。本地候选，GitHub 公开版本仍为 0.2.16.7。

## English

- Remove manual root-test buttons from Bluetooth tools and the update card.
- Automatically check installation access after the user confirms an update, then proceed with installation.
- Explain failures and retain the downloaded APK for retry.
- Add a steering-wheel Siri switch and the factory voice-key event route, preserving music controls, calls and iPhone Siri.
- Support three-finger left/show, right/hide and up/toggle gestures while retaining the settings gesture configuration.
- Connect the selected map display to CarPlay negotiation, use a legacy overlay window on Android 5.1, and prevent the main display from taking the instrument video surface. Select the instrument in map projection settings and reconnect first.

Includes the earlier interface, hotspot and installer validation changes. Complete maps require an exposed instrument display; factory turn guidance is a separate output. Native E01 complete-map projection and factory installation still require vehicle interface review and validation. Mounts are not changed automatically.

Version `0.2.16.11` / `55`, Android 5.1 minimum. Local candidate; public release remains 0.2.16.7.

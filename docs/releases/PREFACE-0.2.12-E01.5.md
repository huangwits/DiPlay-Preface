# DiPlay Preface 0.2.12 · E01.5 更新候选

## 中文

跟进原作者截至 `29572a2` 的 0.2.12 后续主线更新，保留 Android 5.1 / API 22、E01 低负载和已选吉利功能。此版本的维护更新不代表 E01 无线蓝牙已修复。

- 更新无线交接超时和 USB 插入后的原地切换、VPN 授权处理。
- 合入 Dock 位置、视图区、紧凑首页及仪表画面更新；E01 仍限制为 H.264、30 fps、单屏及最高 960×540，禁用扩大负载的旋转方形画布。
- 修正新功能的 API 22 悬浮权限与剪贴板调用；仅限 Android 13+ 的热点修复在旧系统隐藏并拒绝执行。
- 主动核对 H52 最新 ANW 后端和两种 nFore SPP 案例，证据与限制见 [调查记录](../E01-H52-NFORE-UPDATE-20261005.md)。它们尚未提供可确认匹配本车的蓝牙实现。

完整 APK 为 `DiPlay-Preface-0.2.12-E01.5-Android51-full.apk`；包名 `com.shihab.diplay.e01legacy`，版本 `0.2.12-e01.5-android51`，versionCode 34，最低 API 22，ARMv7 / ARM64。完整包必须通过 BUILD.md 的认证资产、启动与覆盖升级签名校验后才可交付。旧版发布标签不移动。

当前没有新的实车连接验证，不要求重复安装诊断采集器。H52 的模拟测试及本项目的桌面回归不能证明 E01/iPhone 握手成功。

---

## English

This candidate follows original-author post-0.2.12 main through `29572a2`, retaining Android 5.1 / API 22, E01 performance limits and selected Geely integrations. It does not establish a working E01 vendor Bluetooth connection.

- Update wireless handoff timeouts and in-place USB attachment/VPN consent handling.
- Integrate Dock placement, view areas, compact Home and cluster picture updates. E01 retains H.264, 30 fps, one screen and a 960×540 maximum canvas; rotating square canvases remain disabled in E01 mode.
- Preserve API 22 overlay-permission and clipboard access. Hide and reject Android 13 hotspot repair on older systems.
- Review the latest H52 ANW transport and two public nFore binary SPP contracts. None is confirmed to match this E01 firmware; see the linked research record.

Full APK: `DiPlay-Preface-0.2.12-E01.5-Android51-full.apk`; package `com.shihab.diplay.e01legacy`; version `0.2.12-e01.5-android51`; code 34; minimum API 22; ARMv7 / ARM64. Delivery requires BUILD.md authentication, bootstrap, upgrade-signature and package checks. Published tags remain unchanged.

No new vehicle connection testing was performed. No repeated diagnostic-app installation is requested. Mock and desktop tests cannot establish E01/iPhone interoperability.

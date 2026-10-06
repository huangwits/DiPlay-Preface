# 原作者主线迁移 / Upstream-first migration

历史记录；2026-10-06 起改为 [carlito 基准维护](UPSTREAM_SYNC.md)。Historical record; superseded by the carlito baseline policy.

## 中文

本次从原作者 DiPlay 0.2.12 的 `2fc876e` 建立代码基线，分开提交已经审查的吉利功能与原有 E01 适配，再清理只为 API 18 保留的实现。迁移清单见 `maintenance-sources.json`。

保留 carlito `84050d6` 的吉利音频、HUD、方向盘、连接设置及其依赖；不引入其自动合并工作流、发布工作流和配套服务端。没有整仓合入后续 `e69910d` / `8f53b27`，这些提交需要单独评审其 API 22 影响。

保留 E01 默认 H.264 / 30 fps / 960×540 比例限制、视频队列优化、API 22 音频和权限处理、USB 分块与超时处理、ECARX 配对设备识别，以及完整安装包认证文件校验与覆盖升级能力。

删除 Android 4.3 专用调试探针、MultiDexApplication 依赖、Holo 基础主题、未使用的重复媒体会话实现和 API 19/21 以下的界面、系统服务、音频及 USB 分支。恢复上游使用情况监控结构和平台字符集实现。API 23 之后才提供的功能仍保留 5.1 回退。原始描述符解析保留为不完整 USB 固件信息的回退；mDNS 端口复用和 Java 8 API 回退仍保留。

提供的参考安装包用于核对厂商接口行为。现有 ECARX 识别实现与迁移前一致；尚无实车证据证明厂商 iAP2 数据通道可用。源码重构和桌面验证不代表无线连接故障已解决。

旧 main 的历史会作为迁移合并的父提交保留。main 可以快进到新维护线，已有 Release / tag 与安装文件保持可追溯。GitHub 的历史 fork 父仓库显示不决定同步来源。

## English

The migration starts at original DiPlay 0.2.12 commit `2fc876e`, applies reviewed Geely runtime integrations as a separate commit, reapplies E01 adaptations, then removes API 18-only code. Exact source commits and file selection are recorded in `maintenance-sources.json`.

Retained Geely changes from `84050d6` cover audio, HUD, steering, connection settings and dependencies. Geely automation, release workflows and the companion server are excluded. Later commits `e69910d` and `8f53b27` are not wholesale imports and require separate API 22 review.

E01 performance limits, queue optimization, audio/permission fallbacks, bounded USB transfers, ECARX paired-device detection and complete-package authentication/upgrade checks are retained. Removed code includes API 18 probes, the multidex application shim, Holo fallback, duplicate media sessions and pre-API-22 branches. Upstream usage-monitor structure and platform charsets are restored. Post-API-22 fallbacks, incomplete USB-layout handling and Android mDNS fixes remain necessary.

Factory interface behavior is checked against the locally supplied reference. ECARX detection is unchanged; there is no new vehicle proof of a vendor iAP2 channel. Desktop validation does not establish wireless vehicle compatibility.

The old main history is retained as a migration parent, allowing a fast-forward update without moving published tags. GitHub's historical fork-parent display does not control the update source.

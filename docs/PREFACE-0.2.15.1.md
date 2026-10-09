# DiPlay 星瑞 0.2.15.1

> 历史交付记录。当前 USB 恢复及设置入口调整见 [0.2.15.2](PREFACE-0.2.15.2.md)。

## 版本与来源

本版按所有者确认的 [版本规则](VERSIONING.md)，跟随 Carlito 上游 `0.2.15` 并追加星瑞修订 `.1`。APK 内部 `versionName=0.2.15.1`、`versionCode=41`，本地文件名为 `DiPlay-Preface-v0.2.15.1.apk`。包名 `com.shihab.diplay.preface`，最低 Android 5.1 / API 22，ARM32/ARM64，简体中文，沿用既有安装签名。

源码继续采用选定的 Geely `0.2.13.2` 连接基线和已完成的星瑞适配；已引入的改动见 [维护记录](MAINTENANCE-2026-10-08.md)。[0.2.15 更新评估](UPSTREAM-0.2.15-REVIEW.md) 记录后续上游差异，当前版本号不表示完整合入这些改动。

## 本次交付

- 修正内部版本、文件名和文档不对应的问题；后续同一上游版本继续递增星瑞修订号。
- USB 使用独立授权页，授权后点“开始 USB 投屏”返回 USB 流程；无线授权保留在蓝牙工具右侧，按钮为“开始无线投屏”。
- 点击蓝牙“连接测试”及确认执行前检查授权；测试开始后的恢复、还原备份和已有 CarPlay 不受许可到期影响。
- 申请号仅显示，移除复制按钮；等待批准时每分钟查询一次，保留“刷新状态”，离开页面或等待 20 分钟后停止自动查询。
- 保留原厂蓝牙工具页左侧操作、右侧线上授权的布局；首页使用普通布局。
- 保留精简后的蓝牙按钮、维护操作确认、独立滚动及日夜主题。
- 授权下方将群名称与 QQ 号 `892351951` 放在同一排，支持复制群号、点击二维码放大；界面无微信联系入口。
- 保留现有 Pages 授权配置、安装设备绑定、运行认证输入和 GOC 兼容组件。联网许可只控制新连接，不中断已有 CarPlay 或蓝牙恢复。

## 安装与验证

已有同签名 `.preface` 安装可直接覆盖升级；请勿先卸载或清除数据，以保留设置和设备授权密钥。完整本地构建与最终 APK 的认证、签名验证见 [BUILD.md](BUILD.md)。源码包不含个人运行输入，不能作为可直接安装的产品。

本次仅在本地交付 APK 和对应源码；公开分发边界见 [RELEASE-POLICY.md](RELEASE-POLICY.md)。实车 CarPlay、音频、电话/Siri、休眠重连仍需现场验证。过去版本的验证记录保留历史编号，不改写为本版结果。

## English

This local Preface release follows Carlito upstream numbering `0.2.15` with revision `.1`: APK versionName `0.2.15.1`, versionCode `41`, filename `DiPlay-Preface-v0.2.15.1.apk`. It preserves the selected Geely connection baseline, API 22 support, existing signer and private local build inputs. It does not claim a full merge of upstream 0.2.15.

The factory Bluetooth page retains controls on the left and authorization with the owner's QQ group card on the right. USB admission has its own page and resumes USB. Bluetooth testing checks admission before confirmation and execution, while recovery remains available. The group name and number share one line, request-number copying is removed, and projection actions name their transport. Pending requests refresh once per minute with a manual refresh option. Install over the existing same-signer `.preface` package without clearing data. Public distribution remains source-only. Final APK gates are required, and vehicle validation remains outstanding.

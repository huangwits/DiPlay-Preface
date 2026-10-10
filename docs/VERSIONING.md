# 版本规则 / Versioning

2026-10-09 所有者确认：版本跟随 `carlito12345/DiPlay` 上游；存在星瑞修改时，在完整上游版本末尾追加修订号。此规则替代此前将 APK 内部版本与交付修订号分开的约定。

- 上游 `x.y.z` 的首次星瑞修订为 `x.y.z.1`；同一上游版本后续交付依次使用 `.2`、`.3`。如果上游版本有四段，则在四段之后追加，不截短上游版本。
- 上游版本更新时，跟随新版本，星瑞修订号从 `.1` 开始。
- APK `versionName`、当前版本文档和交付文件名必须一致。`v` 仅为文件名或标签前缀，不放进 APK `versionName`；调试变体保留已有 `-hud-test` 后缀。
- `versionCode` 是独立的整数升级编号。新交付取已交付同包名版本与所核实上游编号的最大值加一，保证递增；不得从点分版本号直接猜算，也不得降低以匹配上游。
- 版本号不替代源码适配记录。保留已选择的 Android 5.1 连接基线，文档明确记录实际引入和未引入的更新。
- 安装包、Release 标签与对应源码采用同一修订号；Release 只附 APK，源码和构建说明保留在仓库。历史标签、安装包和验证记录保留原值。

## 当前对应关系

| 项目 | 值 |
| --- | --- |
| 上游版本 | `0.2.16` |
| 上游 versionCode | `41` |
| 星瑞修订 | `7` |
| APK versionName | `0.2.16.7` |
| APK versionCode | `51`（此前同包名交付 0.2.16.6 为 `50`） |
| 本地 APK 文件名 | `DiPlay-Preface-v0.2.16.7.apk` |
| 对应发布标签 / Release | `v0.2.16.7` / `preface v0.2.16.7` |

2026-10-10 从 [Carlito 发布记录](https://github.com/carlito12345/DiPlay/releases/tag/v0.2.16) 和 [该标签的构建配置](https://github.com/carlito12345/DiPlay/blob/v0.2.16/mobile/build.gradle.kts) 核实上游版本及编号。上游最低 API 25，本适配仍为 API 22；本次未合入全部 0.2.16 功能，范围见 [当前版本说明](PREFACE-0.2.16.7.md) 和 [更新评估](UPSTREAM-0.2.16-REVIEW.md)。

交付前从最终 APK 读取 `versionName`、`versionCode`、包名及签名，核对文件名和对应源码；执行 BUILD.md 的完整包认证与签名门禁。仅重命名旧 APK 不算修正内部版本。

## English

Follow the full Carlito upstream version and append a Preface revision: upstream `x.y.z` becomes `x.y.z.1`, then `.2`, `.3` for later deliveries. Reset the revision to `.1` when the upstream version changes. Keep APK versionName, current documentation and installer filename aligned; `v` is only a filename/tag prefix. Debug builds retain their existing suffix. Set the integer versionCode to one greater than the maximum of the previously delivered same-package code and verified upstream code. Never downgrade it.

The current local delivery is `0.2.16.7` / `51`, based on upstream numbering `0.2.16` / `41`; the selected API 22 connection baseline and documented selective adaptations remain. This supersedes the former separate APK/release version convention. Preserve historical artifacts and tags. Releases attach the validated APK only; corresponding source and build documentation remain in the repository.

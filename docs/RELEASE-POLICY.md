# 星瑞版发布规范 / Preface release format

## 中文

按所有者于 2026-10-06 调整并确认的 GitHub Release 格式发布。

| 项目 | 规则 | 当前示例 |
| --- | --- | --- |
| Release 标题 | `preface v.<公开版本>` | `preface v.0.2.13` |
| 安装包 | `DiPlay-Preface-v<公开版本>.apk` | `DiPlay-Preface-v0.2.13.apk` |
| 源码包 | 固定名称 | `DiPlay-Preface-source.zip` |
| 安装说明 | 固定名称 | `INSTALL-README.md` |
| 校验文件 | 固定名称 | `SHA256SUMS.txt` |

每次公开上传仅保留上述四个附件。验证 JSON、测试报告、签名核验输出、构建日志和中间安装包在本地审计目录留存。GitHub 自动生成的 Source code 下载入口不属于手动附件。

安装包名称不再包含 E01、Android51、ZHCN、full 等后缀，但内容仍必须通过 [BUILD.md](BUILD.md) 的完整认证、API 22、原签名和相关回归检查。当前包名为 `com.shihab.diplay.preface`，保留既有签名；与此前 `.e01legacy` 分开安装，不能覆盖或自动迁移旧设置。

先确定最终文件名，再生成 `SHA256SUMS.txt`，其中只列安装包、源码包和安装说明的当前名称与哈希。安装说明及 Release 正文使用相同名称，不引用未公开的验证附件。正文继续中文在前、英文在后，使用简洁的功能标题。

按所有者于 2026-10-07 的要求，公开版本、APK 的 versionName 和 versionCode 与 Geely 上游保持一致，不追加星瑞版序号或版本后缀。当前版本为 `0.2.13.2` / `36`，标题 `preface v.0.2.13.2`，APK `DiPlay-Preface-v0.2.13.2.apk`。这是新包名的首次发布，旧 `.e01legacy` 的 versionCode 不约束它。历史 tag、源码和附件保持不变；此规则不授权改写既有 tag 或自动删除历史发布。

已发布附件改名或修正文档前，备份当前发布信息、附件及引用；保持 APK、源码包与原发布源码对应。新增维护文档可提交到 main，不以此替换旧 tag 的源码包。

## English

Use the owner's confirmed format: title `preface v.<version>` and exactly four uploaded assets: `DiPlay-Preface-v<version>.apk`, `DiPlay-Preface-source.zip`, `INSTALL-README.md`, and `SHA256SUMS.txt`. Keep detailed validation records locally.

The short APK name still denotes a fully provisioned, validated standalone package. Use `com.shihab.diplay.preface` and the existing signer. It installs separately from `.e01legacy`, with no automatic migration of settings or permissions. Match Geely's versionName and versionCode without a custom suffix, and use that version in the title and APK filename (currently `0.2.13.2` / `36`). Checksums cover the other three assets under their final names. Release notes and installation instructions must match those names and must not refer to unpublished attachments. Keep Chinese first, followed by English.

Preserve existing tags and their matching source archives. This naming convention does not authorize history deletion or tag rewriting. Back up release metadata, assets and refs before changing published attachments. The historical `0.2.13` tag and its APK internal version remain unchanged.

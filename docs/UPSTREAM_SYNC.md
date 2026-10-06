# Carlito baseline / carlito 基准维护

## 中文

E01.13 已逐项审查移植原作者 `32550b2`（NSD 延迟加载）、`690945d`（无 NSD 回归）和 `1240f8e`（等待画面日夜模式），保留来源提交记录及本地 API 22 / 星瑞差异。应用按所有者要求仅保留简体中文；今后同步时不恢复语言选择或其他语言资源。

根据仓库所有者 2026-10-06 的要求，以 `carlito12345/DiPlay` 为代码基准和默认更新来源。当前合入 `8f53b27b3168aedb661e9f9bb7122ea344b75ada`；保留已有验证过的 API 22 / E01 修正与 Git 历史，不重写发布历史来伪装成全新分支。先前原作者主线迁移见 [历史记录](UPSTREAM-FIRST-MIGRATION.md)。

**Actions → Review upstream updates** 默认 `geely`：完整合入 carlito main 到临时审查分支。`original` 仅接受一个完整、非合并提交 SHA，验证来自原作者 main 后以 `cherry-pick -x` 保留来源。脚本不直接修改 main，不自动发布；冲突时回到原分支并报告。

本地使用：先更新 `geely/main` 和 `upstream/main`，从干净工作树运行 `python scripts/prepare_upstream_update.py`；原作者单项更新用 `--source original --commit <full-sha>`。保留 `upstream` 指向原作者、`geely` 指向 carlito。

H52 仅引入匹配 ANW 协议的数据后端，最低系统仍为 Android 5.1。系统蓝牙默认启用；E01 ECARX、H52 ANW 都是明确手动选项，与 E01 性能模式独立。切换接口必须重新选择手机。不同厂商的事务号和服务不能混用。

提交前运行 common/shared 测试、维护脚本测试、API 22 NewApi lint、源码 APK 无认证检查。完整安装包另需通过 [BUILD.md](BUILD.md) 的认证与原签名检查。自动化测试不代表实车连接成功。历史 tag/Release 仅按所有者明确要求删除，先备份源码指向、发布说明和全部附件。

## English

E01.13 selectively ports original commits `32550b2`, `690945d` and `1240f8e`, retaining provenance and API 22/Preface behavior. The owner selected a Simplified-Chinese-only app; do not restore locale pickers or foreign translations during synchronization.

At the owner's request on 2026-10-06, carlito12345/DiPlay is the baseline and default update source. Current merge: `8f53b27b3168aedb661e9f9bb7122ea344b75ada`. Verified API 22/E01 changes and source history remain intact. The earlier original-author migration is retained as a historical record.

The update workflow defaults to `geely`, merging carlito main into a review branch. `original` requires one full non-merge SHA from original-author main and uses `cherry-pick -x`. Conflicts abort back to the starting branch; the script does not update main or publish releases. Locally run `python scripts/prepare_upstream_update.py` after fetching both remotes, or `--source original --commit <full-sha>` for a selected original-author change.

System Bluetooth is the default. E01 ECARX and H52 ANW are explicit choices independent of the E01 performance profile. Changing interfaces clears only the previous phone selection. The H52 transport import does not lower the minimum Android version below API 22. Keep vendor contracts separate.

Require common/shared and maintenance tests, API 22 lint and source-package isolation checks; full deliverables additionally require the authentication and upgrade-signature gates in BUILD.md. Vehicle connectivity remains a separate validation. Delete historical tags/releases only on explicit owner instruction after backing up refs, metadata and all assets.

# Updating the Android 5.1 E01 adaptation

Maintenance follows the original DiPlay project and carlito’s Geely upstream, with Android 5.1 / API 22 and E01 as the compatibility target. The user-provided APK remains a local reference for interface behavior; it is not described in the public README. Historical Android43 contributions retain attribution in THIRD_PARTY_NOTICES.md. New compatibility work should address the E01 target rather than expand Android 4.3 support.

Keep one bilingual root README.md, Chinese first and English below. Release descriptions use the same language order; version tags remain stable identifiers.

The maintained base is `carlito12345/DiPlay`, which integrates changes from `shihabal3amri/DiPlay`. Prefer the Geely base so that original-author updates do not bypass Geely modifications.

`main` is the sole long-lived branch and owns this project's Android 5.1 / E01 compatibility changes. Release tags preserve published source versions. The initial adaptation was based on `049e080`. The integrated 2026-10-05 update includes carlito `84050d6` and original-author `2fc876e` (0.2.12), extending the earlier `14abe4f` / `a1f8e45` candidate in the retained Git history.

The **Review carlito updates** workflow is manual. It fetches carlito main and attempts a merge on `sync/carlito-<sha>`. If conflicts occur, it reports the files and pushes nothing. If successful, it pushes a candidate, explicitly dispatches **Android 5.1 E01 checks**, and opens a draft PR targeting `main`. GitHub must allow Actions to create pull requests. Delete temporary update branches after merging. No automatic merge occurs; a clean Git merge is not proof of runtime compatibility.

Use **Actions → Review carlito updates → Run workflow** after the adaptation branch is set as the repository default. Existing workflow branches are not force-pushed. Check for an existing draft before retrying. Review the actual candidate commit's workflow results before merging. PR and workflow creation may require enabling Actions in a new fork.

Before accepting an update:

1. Preserve API 22, E01 defaults and older Android API fallbacks.
2. Run public-source credential checks, unit tests, NewApi checks and source-only builds.
3. Provision private build inputs only outside the source tree when creating a standalone vehicle package.
4. Validate on the vehicle. Standard Bluetooth tests do not verify the E01 vendor data channel.

The former automatic upstream-to-main merge workflow is replaced on the adaptation branch. This repository does not automatically publish releases containing authentication materials.

## 2026-10-05: 0.2.12 update

The current merge includes carlito `84050d6`; original-author `2fc876e` is already its ancestor. All upstream changes are retained with the existing API 22 / E01 adaptation and the v03 diagnostic collector. The earlier synchronization remains below as a historical record.

New behavior includes automatic hotspot selection, stable interface sampling, bounded first-TCP recovery, optional wheel focus controls, live dashboard delivery, resolution validation up to 160%, and restored Geely settings/HUD/factory audio handoff. Factory audio handoff still uses the standard Android adapter and is not an E01 vendor RFCOMM backend.

Compatibility resolutions use legacy string-based system services for new display/hotspot calls and gate API 36 tethering access. On API 22, default-network sampling resolves one connected network matching the default network type; missing or ambiguous identities, missing link information, and changes between samples reject readiness. Wi-Fi upstream exclusion and the upstream AP ownership rules remain intact. E01 pixel/fps limits and Android Bluetooth preflight remain intact alongside the new resolution fallback and first-TCP watchdog.

本地 1,276 项测试通过（common 552、shared 712、diagnostics 12），0 失败、0 错误、0 跳过。common/shared/mobile 的 NewApi 专项扫描、诊断模块完整 lint 和主程序源码验证包构建通过。新增 7 项热点采样检查与 1 项 E01 分辨率回归检查通过。

See [0.2.12 E01 release notes](releases/PREFACE-0.2.12-E01.md). The public main APK remains source-only and contains no accessory authentication. The diagnostic v03 APK is unchanged from its independent release. Vehicle installation, E01 firmware matching and Bluetooth data connectivity remain unverified; no default-branch merge or claim of E01 wireless repair is implied by this candidate.

## 2026-10-05: earlier 0.2.11 synchronization candidate

The candidate first merges carlito `6b2b3b9`, then the original author's `a1f8e45`, then carlito's newly published `14abe4f`. It preserves the separate branch histories so later merges can recognize both sources.

Included updates: existing Wi-Fi/LAN with dual-stack AirPlay discovery, diagnostic-export storage fallbacks, wheel map zoom, short-lived dashboard song information, display/night-mode changes, and Geely call-audio recovery and HUD bounds/scale.

Compatibility resolutions retain API 22, E01 H.264/30 fps/pixel limits, disabled extra E01 map stream, legacy system-service and USB parcelable lookups, Geely audio focus/transport lifecycle, and Bluetooth diagnostics. Tests use the same vendored JmDNS as production. HUD dimensions keep an API 22-compatible fallback. Endpoint deduplication preserves later port/TXT/address refinements and distinct IPv4/IPv6 endpoints.

Validation includes full main/diagnostic regression tests, the NewApi scan and source-only APK checks. Robolectric 4.17 does not provide an API 22 runtime: tests run on supported SDK sandboxes, with explicit API 22 branch simulations for Wi-Fi and Bonjour inside an API 23 sandbox. These simulations do not establish Android 5.1 runtime or E01 vehicle compatibility. The existing public Release remains `v0.2.11-preface-e01.1`; candidate source-only packages do not contain accessory authentication. E01 vendor Bluetooth and vehicle playback/connection checks remain outstanding.

Local validation completed for the candidate:

- 1,081 unit tests passed: common 478, shared 597, diagnostics 6; zero failures, errors or skips.
- `shared:lintDebug`, `common:lintDebug` and `mobile:lintE01` passed with the NewApi-only init script.
- `diagnostics:lintRelease` passed without that script (full diagnostic-tool lint).
- `mobile:assembleE01` and `diagnostics:assembleRelease` succeeded. The main APK is `com.shihab.diplay.e01legacy`, minSdk 22, ARMv7/ARM64. Both APK signatures verify with v1/v2.
- `check_public_tree.py` and `check_source_apk.py` passed. No accessory-authentication containers are included in the source-only APK.

Regressions cover unsaved menu edits on resume, USB attachment classification, existing-Wi-Fi credential refresh, legacy network callback capabilities, IPv4/IPv6 publication and endpoint refinement/rediscovery.

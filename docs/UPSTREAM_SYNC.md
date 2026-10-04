# Updating the Android 5.1 E01 adaptation

The maintained base is `carlito12345/DiPlay`, which integrates changes from `shihabal3amri/DiPlay`. Prefer the Geely base so that original-author updates do not bypass Geely modifications.

`android51-e01` owns this project's compatibility changes. `main` retains the fork's upstream snapshot. The initial adaptation was based on `049e080`. The 2026-10-05 candidate `sync/oct05-upstream-android51` integrates carlito `14abe4f` and original-author `a1f8e45`.

The **Review carlito updates** workflow is manual. It fetches carlito main and attempts a merge on `sync/carlito-<sha>`. If conflicts occur, it reports the files and pushes nothing. If successful, it pushes a candidate, explicitly dispatches **Android 5.1 E01 checks**, and opens a draft PR targeting `android51-e01`. GitHub must allow Actions to create pull requests. No automatic merge occurs; a clean Git merge is not proof of runtime compatibility.

Use **Actions → Review carlito updates → Run workflow** after the adaptation branch is set as the repository default. Existing workflow branches are not force-pushed. Check for an existing draft before retrying. Review the actual candidate commit's workflow results before merging. PR and workflow creation may require enabling Actions in a new fork.

Before accepting an update:

1. Preserve API 22, E01 defaults and older Android API fallbacks.
2. Run public-source credential checks, unit tests, NewApi checks and source-only builds.
3. Provision private build inputs only outside the source tree when creating a standalone vehicle package.
4. Validate on the vehicle. Standard Bluetooth tests do not verify the E01 vendor data channel.

The former automatic upstream-to-main merge workflow is replaced on the adaptation branch. This repository does not automatically publish releases containing authentication materials.

## 2026-10-05 synchronization candidate

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

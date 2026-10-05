# DiPlay Preface — Android 5.1 / E01

[中文说明](README.zh-CN.md)

**[Bluetooth diagnostics v03](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.3-e01-bluetooth-diagnostics)** adds an E01 firmware-interface ZIP export without computer ADB. This is a diagnostic prerelease; E01 vendor wireless Bluetooth remains unimplemented. See the [public-source investigation](docs/E01-BLUETOOTH-PUBLIC-RESEARCH.md). The main-app download below is the 0.2.12 E01 source-only preview.

[Download APKs — v0.2.12-preface-e01.1](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.1)

Open **Assets** on the release page. Download the E01 main APK or the standalone Bluetooth diagnostics APK, not the source-code archives. The release notes specify the main APK's authentication configuration. SHA-256 checksums are included. This is an experimental prerelease, not verified vehicle support.

The published main APK is explicitly **source-only**: it contains no accessory authentication materials and is not a standalone iPhone-connection build. The diagnostic APK works independently. [Download both APKs and installation instructions as a ZIP](https://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.12-preface-e01.1/DiPlay-Preface-v0.2.12-preface-e01.1-installers.zip).

Experimental adaptation for the owner's 2020 Geely Preface, GKUI ECARX E01 / MT6735, Android 5.1 (API 22).

- Geely base: [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay), candidate includes commit `84050d6` (checked 2026-10-05).
- Original project: [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay).
- Legacy compatibility source: [xikai6282/DiPlay-Geely-Android43](https://github.com/xikai6282/DiPlay-Geely-Android43).
- Maintained adaptation branch: `android51-e01`. `main` retains the fork's upstream snapshot; it is not the Android 5.1 build.

This candidate also merges original-author commit `2fc876e`, including Existing Wi-Fi / Same LAN, dual-stack discovery, diagnostic-export fallbacks and dashboard wheel/song controls. Existing Wi-Fi still requires Bluetooth/iAP2 startup and does not bypass the E01 vendor Bluetooth limitation. The release includes automatic wireless startup/recovery, dashboard/wheel improvements and Geely integration updates. See [sync record and validation](docs/UPSTREAM_SYNC.md).

## Status

Minimum API 22; ARMv7 and ARM64 E01 APK; H.264/30 fps low-load defaults. See the sync record for 0.2.12 tests and Android NewApi checks. API 22 hotspot sampling and E01 canvas limits have dedicated regression coverage. The previous release's first GitHub Actions build passed. Robolectric has no API 22 runtime: API 22 branch checks are simulations inside an API 23 sandbox. These checks do not establish vehicle runtime compatibility.

**E01 vendor wireless Bluetooth is not implemented.** The factory telephone/music works, but the owner's standard Android adapter is unavailable. Standard RFCOMM cannot be replaced by a vendor power-state query. The included `diagnostics` app reads firmware and candidate factory Bluetooth application metadata without a computer; it does not fix or initialize the vendor stack.

## Build and downloads

Use **Releases** for tagged installation packages. For development builds, use **Actions → Android 5.1 E01 checks**. Its artifact contains:

- An E01 **source-only** APK, without accessory authentication. It must not be presented as a standalone iPhone connection package.
- A standalone Bluetooth diagnostic APK (no accessory identity needed).

Local equivalent:

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :diagnostics:testDebugUnitTest :shared:lintDebug :common:lintDebug :mobile:lintE01 :mobile:assembleE01 :diagnostics:assembleRelease -I scripts/android51-api-lint.init.gradle
```

Standalone vehicle builds use explicitly provisioned local authentication inputs and `:mobile:assembleStandaloneE01`; see [build instructions](docs/BUILD.md). No keys, certificates, local authentication assets or APKs are committed to Git history. Installation packages are distributed as Release assets with their configuration documented in the release notes.

[E01 implementation and limits](docs/ECARX-E01-ANDROID51.md) · [upstream README](docs/CARLITO-README.en.md) · [licenses and credits](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

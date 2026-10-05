# DiPlay Preface · E01

An experimental CarPlay receiver adaptation for the **2020 Geely Preface / GKUI / ECARX E01 (MT6735) / Android 5.1**.

[Download 0.2.12 APK](https://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.12-preface-e01.1/DiPlay-Preface-0.2.12-E01-Android51-source-only.apk) · [Release notes and assets](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.1) · [All releases](https://github.com/huangwits/DiPlay-Preface/releases) · [中文](README.zh-CN.md)

> The public APK is a source-only preview without accessory authentication materials. It cannot independently complete an iPhone connection. Vehicle connectivity and audiovisual compatibility have not been validated.

## Installation

Release assets contain the main APK, a ZIP with that APK and Chinese installation instructions, and SHA-256 checksums. GitHub's automatically generated source-code archives are not installable APKs.

1. Download the main APK or extract the installation ZIP.
2. While parked, transfer the APK through an available USB drive or file-transfer method and install it with the head unit's file manager.
3. The app is **DiPlay E01 Legacy**, package `com.shihab.diplay.e01legacy`. An older build with the same package and signer can be updated in place.

Minimum Android 5.1 / API 22; ARMv7 and ARM64. E01 defaults use H.264, 30 fps and an aspect-preserving canvas limited to a 960-pixel long edge and a 540-pixel short edge.

## Adaptation and validation

Legacy Android paths cover audio focus, playback, recording, video surfaces, system services and permissions. Version 0.2.12 integrates connection-flow, resolution, steering-control, dashboard, Geely audio and HUD changes. Availability depends on the installed firmware.

Main-app builds, unit tests, API compatibility checks and APK signatures were verified. These checks do not establish vehicle runtime compatibility. See the [release notes](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.1).

## Sources and development

Based on [carlito12345/DiPlay](https://github.com/carlito12345/DiPlay), with legacy compatibility from [DiPlay-Geely-Android43](https://github.com/xikai6282/DiPlay-Geely-Android43). The original project is [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay).

The 0.2.12 APK was built from verified commit `17e526f`, including carlito `84050d6` and original-author `2fc876e`. Release tags identify release source. The default `android51-e01` branch and update candidates are maintained through pull requests; `main` retains an upstream snapshot.

[Build instructions](docs/BUILD.md) · [Build checks](https://github.com/huangwits/DiPlay-Preface/actions/workflows/android51.yml) · [Credits and licenses](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

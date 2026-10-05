# DiPlay Preface · E01

A CarPlay receiver adaptation for the **2020 Geely Preface / GKUI / E01 / Android 5.1**.

[Download the E01.3 full APK](https://github.com/huangwits/DiPlay-Preface/releases/download/v0.2.12-preface-e01.3/DiPlay-Preface-0.2.12-E01.3-Android51-full.apk) · [Release notes and assets](https://github.com/huangwits/DiPlay-Preface/releases/tag/v0.2.12-preface-e01.3) · [中文](README.zh-CN.md)

E01.3 corrects the missing-authentication startup failure in the previous source-only previews. It includes the existing local experimental accessory identity. Vehicle Bluetooth, iPhone acceptance and audiovisual compatibility remain unverified.

## Installation

Transfer the full APK to the parked head unit and install it with its file manager. The app is **DiPlay E01 Legacy**, package `com.shihab.diplay.e01legacy`, version `0.2.12-e01.3-android51` / code 32, API 22+, ARMv7 / ARM64. The package and signer match E01.2, allowing an in-place update that preserves settings. Android43's hudtest package is a separate application.

Old source-only APKs and GitHub source archives are not standalone car-test packages. Release assets include the full APK, an installation ZIP and SHA-256 checksums.

## Development

The base is [carlito's Geely adaptation](https://github.com/carlito12345/DiPlay), integrating original DiPlay updates and Android43 compatibility work. E01 defaults use H.264 / 30 fps and an aspect-preserving 960×540 canvas limit. The main branch retains this project's compatibility work.

Ordinary CI builds omit runtime identities. Use `assembleStandaloneE01` with explicit local authentication inputs for car testing. The build verifies key/certificate consistency and the final APK's assets. Production bootstrap tests read the selected APK; local success does not prove vehicle or iPhone acceptance. Runtime identities and APK signing keys are excluded from Git.

[Build instructions](docs/BUILD.md) · [Identity provenance and licenses](docs/THIRD_PARTY_NOTICES.md) · [LICENSE](LICENSE)

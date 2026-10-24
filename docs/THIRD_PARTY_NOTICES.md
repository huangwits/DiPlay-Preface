# 第三方声明 / Credits and license notices

[返回项目首页](../README.md) · [文档目录](README.md)

本页保留上游代码、资源和公开 APK 所涉及组件的来源与署名。当前公开文件的范围见 [源码说明](../SOURCE_SCOPE.md)，以下许可证和版权声明继续按原文保留。

## Receiver

DiPlay is a modified version of [xcertplay by shilapi](https://github.com/shilapi/xcertplay). The upstream receiver is licensed under GNU GPL version 3; the full text is in the [project license](../LICENSE). See the [original DiPlay repository](https://github.com/shihabal3amri/DiPlay) for its current README.

Upstream credits [LIVI](https://github.com/f-io/LIVI) and [Showcase](https://github.com/amineross/showcase) for protocol research. Existing source comments and attribution are preserved.

## Home and settings UI

The Material theme, connection timeline, and UI components under `common/src/main/java/com/shilapi/xcertplay/ui/` are adapted from [xcertplay 1.4.1](https://github.com/shilapi/xcertplay), licensed under GNU GPL version 3. Preface retains its existing vehicle-specific connection, authorization and settings behavior.

The `ic_ms_*` vector resources adapt Google Material Symbols Rounded, copyright Google LLC, under Apache License 2.0. The license text is included in [Apache-2.0.txt](licenses/dependencies/Apache-2.0.txt).

`common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt` adapts the palette, visual arrangement and interface copy of the [DiAuto project](https://github.com/shihabal3amri/DiAuto). DiAuto's source is licensed under AGPL version 3. The UI file is marked AGPL-3.0-only; its license text is included in [DiAuto-AGPL-3.0.txt](licenses/DiAuto-AGPL-3.0.txt).

## CarPlay icon

The unmodified icon was obtained from [Apple's developer site](https://developer.apple.com/assets/elements/icons/carplay/carplay-96x96_2x.png).

CarPlay and the CarPlay icon are Apple Inc. marks/assets. This asset is not covered by the project's open-source code license. Its use here does not imply Apple approval or certification.

## Runtime dependencies

- SpeexDSP 1.2.1 echo cancellation subset — Xiph.Org Foundation and contributors; BSD-style license in [COPYING](../shared/src/main/jni/speexdsp/COPYING). The optional JNI integration is adapted from original-author changes f19a101a and 806bbf6f.
- AndroidX and Jetpack Compose — Android Open Source Project; Apache License 2.0.
- Kotlin standard library — JetBrains; Apache License 2.0.
- Bouncy Castle 1.79 — The Legion of the Bouncy Castle Inc.; [Bouncy Castle license](licenses/dependencies/Bouncy-Castle-LICENSE.txt) (MIT-style).
- Concentus 1.0.0 — Xiph.Org Foundation, Skype Limited, CSIRO, Microsoft Corporation, Logan Stromberg and other contributors; [BSD 3-Clause license](licenses/dependencies/Concentus-LICENSE.txt).
- JmDNS 3.6.3 — JmDNS contributors; [Apache License 2.0](licenses/dependencies/Apache-2.0.txt). The [vendoring notes](../vendor/jmdns/README.md) describe the Android compatibility changes.
- SLF4J — QOS.ch; [MIT license](licenses/dependencies/SLF4J-LICENSE.txt).
- ZXing Core 3.5.3 — ZXing authors; Apache License 2.0. Used to encode the existing offline device code as a locally rendered QR code. [Source and license](https://github.com/zxing/zxing/tree/zxing-3.5.3).

Gradle dependency declarations and the version catalog remain in the public tree. License files available in the resolved artifacts are included in the [dependency license directory](licenses/dependencies/).

## Native call echo cancellation

DiPlay vendors a subset of [SpeexDSP 1.2.1](https://github.com/xiph/speexdsp/tree/SpeexDSP-1.2.1) from Xiph.org Foundation and its contributors, including the acoustic echo canceller, preprocessor and FFT/filterbank support. It is compiled into `libspeex_echo.so` for the optional experimental **Call echo cancellation** feature.

SpeexDSP uses a BSD-style three-clause license. The full copyright notices, redistribution conditions and disclaimer are retained in [the vendored COPYING file](../shared/src/main/jni/speexdsp/COPYING). Component source headers retain their additional notices, including KISS FFT's Mark Borgerding attribution. The [vendoring notes](../shared/src/main/jni/speexdsp/README.md) identify the source version and build configuration.

## Software Opus microphone encoder

DiPlay vendors [Concentus](https://github.com/lostromb/concentus), Logan Stromberg's pure-Java port of the Opus reference library from Xiph.Org Foundation, Skype Limited, Microsoft Corporation and other contributors. It encodes the CarPlay microphone on head units whose Android has no MediaCodec Opus encoder.

Concentus uses the Opus BSD-style license. The full copyright notices, redistribution conditions and disclaimer are retained in [the vendored LICENSE file](../shared/src/main/java/org/concentus/LICENSE). The [vendoring notes](../shared/src/main/java/org/concentus/README.md) identify the source commit.

## Experimental authentication data

The public preview APK includes an accessory certificate/key pair recovered from public Carlinkit C2Air Allwinner V821 firmware during the owner's local investigation. These data are not newly generated Apple-issued credentials for DiPlay and are not relicensed as project source code. They are bundled in the preview APK to reproduce the offline experiment; continued acceptance and suitability for general distribution are unresolved. The public source tree does not contain the private key, and the separate Android APK-signing key is never distributed. See the [security notes](../SECURITY.md).

## Download website

The static site layout, CSS and generator adapt DiAuto (AGPL-3.0). The [AGPL license text](licenses/DiAuto-AGPL-3.0.txt) is included with the source.

## Upstream BYD HUD maneuver icons

Required Notice: Copyright AndyShaman (https://github.com/AndyShaman/BYDMate)

Upstream imported maneuver PNGs from BYDMate under PolyForm Noncommercial 1.0.0 terms. Those image assets are not included in the current E01-focused public tree; this attribution is retained as project provenance. The images are separate from the project code license. Upstream describes them as donor assets and their original provenance is not independently established.

## Public ISRG certificate trust anchors

License HTTPS connections retain platform trust and supplement missing OEM trust anchors with the official ISRG Root X1, ISRG Root X2 and Root YE certificates. Domain, signature and certificate-validity checks remain enabled. These public certificates are application resources, not changes to the device trust store.

Sources: [ISRG certificates](https://letsencrypt.org/certificates/) and [Root YE DER](https://letsencrypt.org/certs/gen-y/root-ye.der). Root YE DER SHA-256: `e14ffcad5b0025731006caa43a121a22d8e9700f4fb9cf852f02a708aa5d5666`.

The bundled public root set is the Mozilla CA extraction published by curl on 2026-09-25. The certificate-only resource contains 121 roots and has SHA-256 `173575594a2dd75bfbd2c47a89e8c4cc5f8522c7530c1c716ff6b0b5de252ff6`. See the [CA bundle](https://curl.se/ca/cacert.pem) and [extraction notes](https://curl.se/docs/caextract.html) for source and update information.

Mozilla certificate data is distributed under the [Mozilla Public License 2.0](licenses/Mozilla-MPL-2.0.txt). The certificate-only extraction preserves the certificates without altering their content.

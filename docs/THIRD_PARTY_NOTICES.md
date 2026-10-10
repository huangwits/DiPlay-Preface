# Credits and license notices

## Receiver

DiPlay is a modified version of [xcertplay by shilapi](https://github.com/shilapi/xcertplay). The upstream receiver is licensed under GNU GPL version 3; the full text is in `LICENSE` and the original README is retained in `docs/UPSTREAM-README.md`.

Upstream credits [LIVI](https://github.com/f-io/LIVI) and [Showcase](https://github.com/amineross/showcase) for protocol research. Existing source comments and attribution are preserved.

## Home and settings UI

`common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt` adapts the palette, visual arrangement and interface copy of the [DiAuto project](https://github.com/shihabal3amri/DiAuto). DiAuto's source is licensed under AGPL version 3. The UI file is marked AGPL-3.0-only; its license text is included in `docs/licenses/DiAuto-AGPL-3.0.txt`.

## CarPlay icon

The unmodified icon was obtained from Apple's developer site at:

https://developer.apple.com/assets/elements/icons/carplay/carplay-96x96_2x.png

CarPlay and the CarPlay icon are Apple Inc. marks/assets. This asset is not covered by the project's open-source code license. Its use here does not imply Apple approval or certification.

## Runtime dependencies

- SpeexDSP 1.2.1 echo cancellation subset — Xiph.Org Foundation and contributors; BSD-style license in `shared/src/main/jni/speexdsp/COPYING`. The optional JNI integration is adapted from original-author changes f19a101a and 806bbf6f.

- AndroidX and Jetpack Compose — Android Open Source Project; Apache License 2.0.
- Kotlin standard library — JetBrains; Apache License 2.0.
- Bouncy Castle 1.79 — The Legion of the Bouncy Castle Inc.; Bouncy Castle license (MIT-style).
- Concentus 1.0.0 — Xiph.Org Foundation, Skype Limited, CSIRO, Microsoft Corporation, Logan Stromberg and other contributors; BSD 3-Clause license.
- JmDNS 3.6.3 — JmDNS contributors; Apache License 2.0.
- SLF4J — QOS.ch; MIT license.
- ZXing Core 3.5.3 — ZXing authors; Apache License 2.0. Used to encode the existing offline device code as a locally rendered QR code. Source and license: https://github.com/zxing/zxing/tree/zxing-3.5.3.

Gradle dependency declarations and version catalog accompany the source. License files available in the resolved artifacts are included under `docs/licenses/dependencies/`.

## Experimental authentication data

The public preview APK includes an accessory certificate/key pair recovered from public Carlinkit C2Air Allwinner V821 firmware during the owner's local investigation. These data are not newly generated Apple-issued credentials for DiPlay and are not relicensed as project source code. They are bundled in the preview APK to reproduce the offline experiment; continued acceptance and suitability for general distribution are unresolved. The source archive does not contain the private key, and the separate Android APK-signing key is never distributed.

## Download website

The static site layout, CSS and generator adapt DiAuto (AGPL-3.0). The AGPL license text is included with the source.

## BYD HUD maneuver icons

Required Notice: Copyright AndyShaman (https://github.com/AndyShaman/BYDMate)

The maneuver PNGs under `shared/src/main/assets/byd-hud-icons` were imported from BYDMate. Its PolyForm Noncommercial 1.0.0 terms and required notice are included alongside the assets. These files are separate from the project code license; upstream describes them as donor assets and their original provenance is not independently established. The validated DiLink5.1 windshield path uses factory turn codes rather than these images.

## Public ISRG certificate trust anchors

License HTTPS connections retain platform trust and supplement missing OEM trust anchors with the official ISRG Root X1, ISRG Root X2 and Root YE certificates. Domain, signature and certificate-validity checks remain enabled. These public certificates are application resources, not changes to the device trust store.

Source: https://letsencrypt.org/certificates/ and https://letsencrypt.org/certs/gen-y/root-ye.der . Root YE DER SHA-256: `e14ffcad5b0025731006caa43a121a22d8e9700f4fb9cf852f02a708aa5d5666`.

The bundled public root set is the Mozilla CA extraction published by curl on 2026-09-25. The certificate-only resource contains 121 roots and has SHA-256 `173575594a2dd75bfbd2c47a89e8c4cc5f8522c7530c1c716ff6b0b5de252ff6`. Source and update information: https://curl.se/ca/cacert.pem and https://curl.se/docs/caextract.html .

Mozilla certificate data is distributed under the [Mozilla Public License 2.0](licenses/Mozilla-MPL-2.0.txt). The certificate-only extraction preserves the certificates without altering their content.

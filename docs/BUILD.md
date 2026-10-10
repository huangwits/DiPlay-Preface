# Building DiPlay

Requirements: JDK 25, Android SDK 37, NDK 28.2.13676358 and the included Gradle wrapper.

The current GitHub Release provides the validated full APK as its only uploaded asset. Source, version tags, build instructions and licenses remain in the repository. Local builders supply their own runtime/signing inputs; CI outputs are identity-free validation packages. Software licensing does not provide CarPlay accessory identities. See [RELEASE-POLICY.md](RELEASE-POLICY.md).

## Source and CI builds

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

The resulting source-only APK contains no accessory identity. Standalone CarPlay requires runtime authentication provisioning. Tests generate synthetic identities at runtime; no test private-key files are tracked.

## Local release packaging

Provide an external asset directory using `DIPLAY_AUTH_ASSETS_DIR`. The directory must contain exactly the intended runtime files under `offline-mfi/identity.pk8` and `offline-mfi/certificate.p7b`. Neither file belongs in Git. The build permits those two files only when this explicit input is set and rejects unexpected credential containers elsewhere in APK assets.

Set `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` locally for your Android signing key. Never commit these values or the keystore. Different signing keys cannot update an existing project-signed installation.

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintRelease :mobile:assembleRelease
```

Output: `mobile/build/outputs/apk/release/mobile-release.apk`. The release APK deliberately contains the experimental identity described in the notices; it is extractable by recipients. The separate Android signing key is not included. The retired build-beta.py helper is not used; this Gradle workflow uses explicit environment inputs.

Corresponding source is available from the version tag and GitHub automatic source downloads, without a duplicate uploaded ZIP. Source excludes runtime identities, signing keys, local configuration and build output.

## Standalone car-test APK

Use `:mobile:assembleStandaloneDebug` for a test APK that must connect to an iPhone:

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

This task refuses missing or empty runtime inputs. `assembleDebug` remains an identity-free
source/CI build when the explicit asset input is absent; do not install that output as a
standalone car-test package. Before delivery, verify both `assets/offline-mfi/identity.pk8`
and `assets/offline-mfi/certificate.p7b` in the APK against the selected local inputs.
For the same application ID and signer, update without uninstalling to preserve settings.
The owner-selected production ID is now `com.shihab.diplay.preface`; it installs separately
from `.e01legacy` and requires its own settings and permissions.

## E01 full car-test release gate

Do not hand `assembleE01` / source-only CI APKs to users for standalone installation.
Set `DIPLAY_AUTH_ASSETS_DIR` to the approved local runtime-asset directory and run
`:mobile:assembleStandaloneE01`. It verifies a matching P-256 certificate/key pair and checks
the packaged files. No credential data is printed or committed.

After building, set `DIPLAY_VALIDATION_APK` to the **absolute path of that APK** and run
`:common:testDebugUnitTest --tests '*StandaloneApkBootstrapTest'`.
Require both tests to pass with **zero skipped tests**. They load the production bootstrap
from the actual APK's assets, test a fresh install and an overlay update after the source-only
failure, and perform local signing. Robolectric uses API 23 with the API 22 code branch selected;
this does not replace an actual vehicle/iPhone test. Verify the requested APK package,
versionName/versionCode, signing certificate and file hash before local installation. Follow
[VERSIONING.md](VERSIONING.md): the upstream version plus local revision must match APK
versionName, current documentation and filename. Current delivery: `0.2.16.3` / code `47`,
filename `DiPlay-Preface-v0.2.16.3.apk`. Historical `v.0.2.13.2.1` artifacts retain their old
`0.2.13.2` / `36` metadata; that split convention no longer applies to new builds. Preserve
the existing signer and monotonically increase versionCode. The first `.preface` build
is a separate installation from `.e01legacy`.
Only the full APK belongs in the
user's transfer directory; preserve old source-only artifacts outside it.


## Windows unit-test shell

The USB permission command tests execute POSIX shell fixtures. On Windows, set
`DIPLAY_TEST_SH` to an existing Git for Windows `bin/sh.exe` before running Gradle.
Scripts are passed through files to preserve their quoting across Windows process creation.
Linux CI defaults to `sh`. No host shell is required by the Android application.

## Public Release format

Follow [RELEASE-POLICY.md](RELEASE-POLICY.md): upload only the validated full `DiPlay-Preface-v<version>.apk` to its Release. Keep source, build instructions and licenses in Git, and retain automatic source downloads. Do not upload duplicate source ZIPs, build README or checksum attachments. Keep APK signing/issuer keys, administrator tokens, server secrets, databases and R8 mapping private. Verify final APK hash, signer, runtime inputs, version and API compatibility before upload.

## Local E01 GOC compatibility build

Set `DIPLAY_GOC_ASSETS_DIR` to an external directory containing
`e01-goc/gocsdk-spp-uuid128-v2` and the local component notice. The build checks
2,841,668 bytes and SHA-256 `e2b71f11ae17f701469f3b60982dbc7a273bcab947f18fd1979e43df6428c54c`.
Only E01 variants that support these inputs receive this directory. The owner-approved
full release APK may bundle the component; do not commit it or upload it as a separate asset.
Keep the authentication and signer gates above.
Ordinary debug/source builds retain the optional transport/tool source but cannot
install a missing candidate. Maintenance requires explicit actions in the E01 tool;
normal wireless startup only opens the selected transport and never runs root commands.
See [the E01 GOC workflow](E01-GOC-2026-10-08.md).

## Local personal build and public offline-activated build

The following personal and historical offline variants remain available; the current public APK uses `e01Licensed`, described below:

| Variant | Code optimization/obfuscation | Activation | Personal helper binaries |
| --- | --- | --- | --- |
| `e01` | Off | None | Explicit local GOC inputs allowed |
| `e01Public` | R8 enabled | Permanent offline activation | Excluded, even if local helper environment variables are set |

Build the personal APK with `:mobile:assembleStandaloneE01` and the local licensed variant with
`:mobile:assembleStandaloneE01Public`. Both require the approved external authentication
inputs and the existing signer. The public APK path is
`mobile/build/outputs/apk/e01Public/mobile-e01Public.apk`. No licensing server or URL is used.

The public issuer config at `mobile/src/e01Public/assets/offline-license/public.json` contains
only the RSA public key, package/signer binding and contact. Keep the matching private issuer
key outside source and all APK/release archives. Use [the offline tool](../offline-license/README.md)
to sign a permanent code for the installation's device code. Clearing app data, uninstalling or
replacing the device may require a new activation. Offline permanent grants cannot be remotely revoked.

For each final APK, run the actual-APK bootstrap gate above, with both tests passing and no skips.
Check metadata, original signer, API 22, ARM32/ARM64, Chinese locales, actual authentication bytes,
and the packaged license flags. The local APK must have both flags false and retain original
application class names. The public APK must have only `config_offline_license` true and must pass:

```sh
python scripts/check_public_release_apk.py mobile/build/outputs/apk/e01Public/mobile-e01Public.apk mobile/build/outputs/mapping/e01Public/mapping.txt
```

Retain R8 mapping files privately with the exact APK hash. Source-only CI builds run both variants
without authentication files and are not installable standalone deliveries. Neither variant's APK
is uploaded publicly. The historical `e01Public` name identifies a build variant only.

## Online-licensed release build

Mobile release builds now enable AGP 9.3/R8 code and resource optimization. JNI symbols
and the native I2C exception constructor have explicit keep rules. Archive the matching
`mapping.txt` privately for crash diagnosis; do not put it in the client package. R8 is
not encryption and does not prevent decompilation or extraction of APK assets.

The separate `e01Licensed` variant enables admission checks for new CarPlay sessions;
the regular `e01` and source/debug variants remain unlicensed; `e01Public` uses offline activation. Existing sessions and
Bluetooth recovery are not stopped by license expiry or server revocation. Signed
leases last at most five minutes, and are never persisted as an offline bypass.

Use [the Workers setup tool](../license-workers/README.md) and set `DIPLAY_LICENSE_ASSETS_DIR` to its `client` directory. The new client submits an approval request rather than using the historical Python activation-code backend.
It must contain only `license/server.json`, with the HTTPS origin and public issuer key.
The APK signing certificate must remain the existing owner-selected signer. The new
licensing issuer private key/admin token must never enter APK assets or source.

Run `:mobile:assembleStandaloneE01Licensed` with the existing authentication/GOC
inputs, followed by both actual-APK bootstrap checks. Its APK is under
`mobile/build/outputs/apk/e01Licensed/mobile-e01Licensed.apk`. Repeat package, signer,
API 22, local payload and optimized-artifact checks before delivery.

An empty service URL is allowed only when `DIPLAY_LICENSE_PREVIEW=true`; that APK is
labeled “DiPlay 授权预览”, cannot activate or start CarPlay, and must not be presented as
a usable licensed car-test release. A public HTTPS deployment and a rebuilt configured
APK are required before customer activation. See [Workers deployment instructions](../license-workers/README.md).

The GPLv3 license and third-party notices still apply. The pre-existing CarPlay runtime
authentication assets remain extractable from standalone APKs; keeping the *new license
issuer key* on the server does not change that separate limitation.

## Optional local source archive

Run `python scripts/check_public_tree.py` before pushing source. If a local source archive is needed, run `python scripts/package_source.py /new/output/directory` outside this source tree; it is not uploaded as a Release asset. It audits the source and final ZIP, and creates only source, build instructions and hashes. This works with both Git checkouts and restored source snapshots. Build output, dependencies, local Workers state and logs are excluded; discovered private inputs and installable packages cause failure.


## Bluetooth obfuscation verification

For the final e01Licensed APK, run `python scripts/check_bluetooth_obfuscation.py mobile/build/outputs/apk/e01Licensed/mobile-e01Licensed.apk mobile/build/outputs/mapping/e01Licensed/mapping.txt --output /private/output/bluetooth-obfuscation.json`. Verify the report hash against the delivered APK. This checks renamed/inlined Bluetooth business code and internal activity methods; it does not claim decompilation is impossible. The Android entry name and plaintext maintenance shell script remain. Keep mapping privately outside delivery. See [scope and limitations](BLUETOOTH-OBFUSCATION.md).

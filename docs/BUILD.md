# Building DiPlay

Requirements: JDK 25, Android SDK 37, NDK 28.2.13676358 and the included Gradle wrapper.

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

The public release source archive corresponds to the tagged source and excludes runtime identities, signing keys, local configuration and build output.

## Standalone car-test APK

Use `:mobile:assembleStandaloneDebug` for a test APK that must connect to an iPhone:

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

This task refuses missing or empty runtime inputs. `assembleDebug` remains an identity-free
source/CI build when the explicit asset input is absent; do not install that output as a
standalone car-test package. Before delivery, verify both `assets/offline-mfi/identity.pk8`
and `assets/offline-mfi/certificate.p7b` in the APK against the selected local inputs.
Update the existing test app without uninstalling it to preserve its settings.

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
this does not replace an actual vehicle/iPhone test. Verify the APK package, higher version code,
same signing certificate and file hash before publishing. Only the full APK belongs in the
user's transfer directory; preserve old source-only artifacts outside it.


## Windows unit-test shell

The USB permission command tests execute POSIX shell fixtures. On Windows, set
`DIPLAY_TEST_SH` to an existing Git for Windows `bin/sh.exe` before running Gradle.
Scripts are passed through files to preserve their quoting across Windows process creation.
Linux CI defaults to `sh`. No host shell is required by the Android application.

## Public Release format

Follow [RELEASE-POLICY.md](RELEASE-POLICY.md) after all standalone/authentication/signature gates pass. Publish `DiPlay-Preface-v<version>.apk`, `DiPlay-Preface-source.zip`, `INSTALL-README.md`, and `SHA256SUMS.txt`. The APK filename has no `-full` suffix; its contents must still be the validated full standalone build. Keep validation JSON, logs and intermediate APKs in the local audit directory. Generate SHA-256 lines after choosing the final asset names, covering the APK, source ZIP and install guide.

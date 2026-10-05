# E01 Bluetooth interface capture

The owner reports working factory calls/music, no standard Android Bluetooth adapter, and an Android settings switch that immediately turns off. This does not establish a broken radio. No E01 diagnostic output or E01 firmware binary has been supplied yet, and no authorized ADB device is currently connected. Existing ANW/ECarX transaction evidence belongs to H52 firmware, not this E01.

The `0.3-e01-interface` diagnostic APK prepares the inputs needed to implement the actual E01 vendor data transport. It is **not a Bluetooth repair** and does not provide an RFCOMM/iAP2 backend. Do not bypass `BluetoothPreflight` merely because factory phone audio works or a vendor service reports enabled.

## Owner operation

1. Install `DiPlay-E01-Bluetooth-Diagnostics-v03.apk` on the parked head unit. Its package and signer match v02, so it can update that tool.
2. Press **导出 E01 蓝牙适配资料 ZIP**. Bluetooth activation, iPhone pairing, root, and computer ADB are not required for this capture.
3. Choose **保存到 U 盘／文件** or **分享 ZIP**. If the firmware has no document picker, use its file manager to copy the ZIP from the path shown in the result dialog. The usual location is `Android/data/com.shihab.diplay.diagnostics/files/Download/interface-bundles/`.
4. Return the ZIP for inspection. The next step is to identify the actual service/API and verify data connect, read, write, close, SDP/iAP2 support and caller permissions before implementing a firmware-specific backend.

## Contents and limits

- `report.txt`: firmware/API/ABI, standard adapter status, system package names and code locations, candidate service/export/permission declarations, and relevant library listings. System app enumeration avoids treating a keyword-filter miss as proof that there is no vendor stack.
- `firmware/`: readable candidate system APKs, selected code libraries and optimized code. Updated system APKs under `/data/app` are eligible; private app data directories are excluded by canonical-path checks.
- `files.tsv`: included file sizes/SHA-256 plus missing, unreadable, empty or size-limited files. Reports with no accessible firmware remain incomplete evidence.
- Files are bounded to 96 MiB each and 192 MiB total, with at most 256 source entries. A cancelled or failed write removes the partial archive.

The tool does not request Internet or broad storage permission, upload anything, read contacts/logcat/paired-device identities, invoke vendor Binder transactions, start factory services or change Bluetooth state during capture. Files are shared only through the owner's chosen destination, using a non-exported FileProvider restricted to bundle directories. Android 11+ package visibility can limit metadata; the intended vehicle runs API 22 (APK minSdk 22, targetSdk 37).

## Local validation

Twelve diagnostic-module unit tests pass, including archive content/checksums, size/missing-file handling, duplicate paths, invalid ZIP paths and cancellation cleanup. Full diagnostic lint passes with warnings; API 22 manifest and the v02-compatible APK signing certificate were checked. These checks do not establish collection success or Bluetooth connectivity on the E01 itself.

## Public research and release

The 2026-10-05 [public-case investigation](E01-BLUETOOTH-PUBLIC-RESEARCH.md) found ECARX vendor Bluetooth/iAP2 API examples and an E02 Android 9 stack-switching experiment. Neither establishes this E01's interface or a working data transport. The [v03 release notes](releases/E01-BLUETOOTH-DIAGNOSTICS-v03.md) document the diagnostic APK and its limits.

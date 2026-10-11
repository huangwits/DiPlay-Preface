#!/system/bin/sh
# Explicit E01 self-update. Paths and digest are replaced only after app-side signature checks.
umask 077
BASE='__BASE__'
APK='__APK__'
EXPECTED='__SHA__'
SYSTEM_APP='__SYSTEM_APP__'
DEVICE=$(/system/bin/getprop ro.product.device)
state() {
  printf '%s\n' "$1" > "$BASE/result.new"
  chmod 644 "$BASE/result.new"
  mv -f "$BASE/result.new" "$BASE/result"
}
fail() { state "$1"; exit 1; }
install_failure() {
  case "$result" in
    *'Read-only file system'*|*EROFS*) fail install-read-only ;;
    *'Permission denied'*|*SecurityException*|*INSTALL_FAILED_USER_RESTRICTED*|*INSTALL_FAILED_INTERNAL_ERROR*'Permission'*) fail install-denied ;;
    *INSTALL_FAILED_INSUFFICIENT_STORAGE*|*'No space left on device'*) fail install-space ;;
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*|*INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES*) fail install-signature ;;
    *INSTALL_FAILED_VERSION_DOWNGRADE*) fail install-version ;;
    *INSTALL_FAILED_INVALID_APK*)
      # FS11's ApkAuth also rejects otherwise valid user APKs with this error.
      # Root is not an exemption; already installed system apps have a separate path.
      case "$DEVICE:$SYSTEM_APP" in *FS11G*:0|*fs11g*:0) fail install-oem-auth ;; esac
      fail install-invalid ;;
    *) fail "$1" ;;
  esac
}
[ "$(id -u)" = 0 ] || fail permission-denied
case "$(/system/bin/getprop ro.product.model) $DEVICE" in
  *E01*|*e01*) ;; *) fail unsupported-car ;;
esac
case "$(/system/bin/getprop ro.board.platform)" in
  *6735*) ;; *) fail unsupported-platform ;;
esac
if ! mkdir "$BASE/lock" 2>/dev/null; then
  old=$(cat "$BASE/lock/pid" 2>/dev/null)
  case "$old" in ''|*[!0-9]*) fail install-busy ;; esac
  kill -0 "$old" 2>/dev/null && exit 1
  rm -f "$BASE/lock/pid"
  rmdir "$BASE/lock" 2>/dev/null || fail install-busy
  mkdir "$BASE/lock" 2>/dev/null || fail install-busy
fi
echo $$ > "$BASE/lock/pid"
session=''
cleanup() {
  [ -z "$session" ] || /system/bin/pm install-abandon "$session" >/dev/null 2>&1
  rm -f "$BASE/lock/pid"
  rmdir "$BASE/lock" 2>/dev/null
}
trap cleanup EXIT
state installing
digest=$(busybox sha256sum "$APK" 2>/dev/null) || fail hash-unavailable
[ "${digest%% *}" = "$EXPECTED" ] || fail hash-mismatch
# API 22's ordinary `pm install` hands the private path to a different process.
# Stream it through a package session so only this root caller needs file access.
# Never uninstall, permit downgrades, change settings, or replace Bluetooth files.
result=$(/system/bin/pm install-create -r 2>&1) || install_failure create-failed
case "$result" in *'Success: created install session ['*']'*) ;; *) install_failure create-failed ;; esac
candidate=${result##*\[}
candidate=${candidate%%\]*}
case "$candidate" in ''|*[!0-9]*) fail create-failed ;; esac
session=$candidate
result=$(/system/bin/pm install-write "$session" base.apk "$APK" 2>&1) || install_failure write-failed
case "$result" in *Success*) ;; *) install_failure write-failed ;; esac
result=$(/system/bin/pm install-commit "$session" 2>&1) || install_failure commit-failed
case "$result" in *Success*) ;; *) install_failure commit-failed ;; esac
session=''
state success
/system/bin/am start -n com.shihab.diplay.preface/com.shilapi.xcertplay.DiPlayActivity >/dev/null 2>&1
exit 0

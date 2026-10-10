#!/system/bin/sh
# Explicit E01 self-update. Paths and digest are replaced only after app-side signature checks.
umask 077
BASE='__BASE__'
APK='__APK__'
EXPECTED='__SHA__'
state() {
  printf '%s\n' "$1" > "$BASE/result.new"
  chmod 644 "$BASE/result.new"
  mv -f "$BASE/result.new" "$BASE/result"
}
fail() { state "$1"; exit 1; }
[ "$(id -u)" = 0 ] || fail permission-denied
case "$(/system/bin/getprop ro.product.model) $(/system/bin/getprop ro.product.device)" in
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
result=$(/system/bin/pm install-create -r 2>&1) || fail install-failed
case "$result" in *'Success: created install session ['*']'*) ;; *) fail install-failed ;; esac
candidate=${result##*\[}
candidate=${candidate%%\]*}
case "$candidate" in ''|*[!0-9]*) fail install-failed ;; esac
session=$candidate
result=$(/system/bin/pm install-write "$session" base.apk "$APK" 2>&1) || fail install-failed
case "$result" in *Success*) ;; *) fail install-failed ;; esac
result=$(/system/bin/pm install-commit "$session" 2>&1) || fail install-failed
case "$result" in *Success*) ;; *) fail install-failed ;; esac
session=''
state success
/system/bin/am start -n com.shihab.diplay.preface/com.shilapi.xcertplay.DiPlayActivity >/dev/null 2>&1
exit 0

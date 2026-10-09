#!/system/bin/sh
# Explicit E01 maintenance only. Ordinary connection startup never runs this script.
set -u
BASE=${0%/*}
ACTION=$1
EXPECTED_CURRENT=$2
OWNER=$3
TARGET=/system/bin/gocsdk
BACKUP=/system/bin/gocsdk.diplay-original
CANDIDATE="$BASE/gocsdk-spp-uuid128-v2"
CANDIDATE_SHA=e2b71f11ae17f701469f3b60982dbc7a273bcab947f18fd1979e43df6428c54c
STATE="$BASE/state"
LOCK="$BASE/lock"
ROLLBACK="$BASE/rollback"
STOPPED=0
REPLACED=0
RW=0
OK=0
DETAIL=operation-failed
ORIGIN=
ORIGINAL_MODE=

state() {
  { echo "stage=$1"; echo "detail=$2"; } > "$STATE.new"
  chmod 644 "$STATE.new"
  mv -f "$STATE.new" "$STATE"
}
fail() { DETAIL=$1; exit 1; }
hash() {
  result=$(busybox sha256sum "$1" 2>/dev/null) || return 1
  echo "${result%% *}"
}
pids() {
  for proc in /proc/[0-9]*; do
    exe=$(busybox readlink "$proc/exe" 2>/dev/null)
    case "$exe" in "$1"|"$1 (deleted)") echo "${proc##*/}" ;; esac
  done
}
running() { [ -n "$(pids "$1")" ]; }
listener() { busybox grep -q '/goc_spp$' /proc/net/unix; }
foreign() {
  for proc in /proc/[0-9]*; do
    exe=$(busybox readlink "$proc/exe" 2>/dev/null)
    case "$exe" in
      "$TARGET"|"$TARGET (deleted)"|"$CANDIDATE"|"$CANDIDATE (deleted)") ;;
      */gocsdk*) return 0 ;;
    esac
  done
  return 1
}
wait_stopped() {
  n=0
  while running "$TARGET" && [ "$n" -lt 12 ]; do sleep 1; n=$((n+1)); done
  ! running "$TARGET"
}
wait_started() {
  n=0
  while [ "$n" -lt 15 ]; do
    if [ "$(/system/bin/getprop init.svc.gocsdk)" = running ] && running "$TARGET"; then return 0; fi
    sleep 1; n=$((n+1))
  done
  return 1
}
mount_mode() {
  count=0
  while read dev point fs options rest; do
    if [ "$point" = /system ]; then
      ORIGIN=$dev
      case ",$options," in *,rw,*) MODE=rw ;; *) MODE=ro ;; esac
      count=$((count+1))
    fi
  done < /proc/mounts
  [ "$count" -eq 1 ] || return 1
  case "$ORIGIN" in /dev/*) return 0 ;; *) return 1 ;; esac
}
remount_rw() {
  mount_mode || return 1
  # Track the attempt as well as observed success so cleanup always restores the mount.
  RW=1
  mount -o remount,rw "$ORIGIN" /system || return 1
  mount_mode && [ "$MODE" = rw ]
}
restore_mount() {
  [ "$RW" -eq 1 ] || return 0
  sync
  mount -o "remount,$ORIGINAL_MODE" "$ORIGIN" /system || return 1
  mount_mode && [ "$MODE" = "$ORIGINAL_MODE" ] || return 1
  RW=0
}
replace() {
  # Preserve original owner/mode; validate staging before the atomic rename.
  cp -p "$TARGET" "$TARGET.diplay-new" || return 1
  cat "$1" > "$TARGET.diplay-new" || return 1
  [ "$(hash "$TARGET.diplay-new")" = "$2" ] || return 1
  mv -f "$TARGET.diplay-new" "$TARGET" || return 1
  REPLACED=1
  if [ -x /system/bin/restorecon ]; then /system/bin/restorecon "$TARGET" || return 1; fi
  sync
  [ "$(hash "$TARGET")" = "$2" ]
}
cleanup() {
  trap - 0 1 2 15
  if [ "$ACTION" = test ] && [ "$STOPPED" -eq 1 ]; then
    state restoring temporary-test-ended
    for pid in $(pids "$CANDIDATE"); do kill "$pid" 2>/dev/null || true; done
    sleep 1
    for pid in $(pids "$CANDIDATE"); do kill -9 "$pid" 2>/dev/null || true; done
    if running "$CANDIDATE"; then OK=0; DETAIL=candidate-stop-failed; fi
  fi
  if [ "$OK" -ne 1 ] && [ "$REPLACED" -eq 1 ]; then
    state rolling-back "$DETAIL"
    STOPPED=1
    if /system/bin/stop gocsdk && wait_stopped && remount_rw &&
       [ "$(hash "$ROLLBACK")" = "$EXPECTED_CURRENT" ] && replace "$ROLLBACK" "$EXPECTED_CURRENT"; then
      DETAIL="$DETAIL-rolled-back"
    else
      DETAIL=rollback-failed-backup-retained
    fi
  fi
  if ! restore_mount; then OK=0; DETAIL=mount-restore-failed; fi
  if [ "$STOPPED" -eq 1 ]; then
    if ! /system/bin/start gocsdk || ! wait_started; then OK=0; DETAIL=system-restart-failed; fi
  fi
  if [ "$OK" -eq 1 ]; then
    if [ "$ACTION" != test ]; then rm -f "$ROLLBACK"; fi
    state completed "$ACTION"
  else
    state failed "$DETAIL"
  fi
  rm -f "$LOCK/pid"
  rmdir "$LOCK" 2>/dev/null || true
  if [ "$OK" -eq 1 ]; then exit 0; else exit 1; fi
}

case "$ACTION" in check|test|install|restore) ;; *) exit 2 ;; esac
case "$OWNER" in ''|*[!0-9]*) exit 2 ;; esac
case "$(/system/bin/id)" in uid=0*) ;; *) state failed root-required; exit 1 ;; esac
[ "$(/system/bin/getprop ro.product.model)" = E01 ] &&
  [ "$(/system/bin/getprop ro.board.platform)" = mt6735 ] ||
  { state failed unsupported-platform; exit 1; }
if [ "$ACTION" = check ]; then
  echo "current=$(hash "$TARGET")"
  echo "backup=$(hash "$BACKUP")"
  echo "backup_record=$(cat "$BACKUP.sha256" 2>/dev/null)"
  echo "service=$(/system/bin/getprop init.svc.gocsdk)"
  if listener; then echo listener=yes; else echo listener=no; fi
  if [ -d "$LOCK" ]; then
    lockpid=$(cat "$LOCK/pid" 2>/dev/null)
    case "$lockpid" in
      ''|*[!0-9]*) echo maintenance=busy ;;
      *) if kill -0 "$lockpid" 2>/dev/null; then echo maintenance=busy; else echo maintenance=stale; fi ;;
    esac
  else echo maintenance=idle
  fi
  exit 0
fi
[ "${#EXPECTED_CURRENT}" -eq 64 ] || exit 2
case "$EXPECTED_CURRENT" in *[!0-9a-f]*) exit 2 ;; esac
# A live maintenance operation owns the lock. Reclaim only a recorded, dead owner.
if [ -f "$LOCK/pid" ]; then
  oldpid=$(cat "$LOCK/pid")
  case "$oldpid" in ''|*[!0-9]*) exit 3 ;; esac
  if ! kill -0 "$oldpid" 2>/dev/null; then rm -f "$LOCK/pid"; rmdir "$LOCK" 2>/dev/null; fi
fi
mkdir "$LOCK" 2>/dev/null || exit 3
echo $$ > "$LOCK/pid"
trap cleanup 0
trap 'DETAIL=interrupted; exit 1' 1 2 15
state validating "$ACTION"
[ "$(hash "$TARGET")" = "$EXPECTED_CURRENT" ] || fail system-changed
! foreign || fail foreign-goc-running
! running "$CANDIDATE" || fail candidate-already-running
if [ "$ACTION" != restore ]; then
  [ "$(hash "$CANDIDATE")" = "$CANDIDATE_SHA" ] || fail candidate-hash-mismatch
  [ "$EXPECTED_CURRENT" != "$CANDIDATE_SHA" ] || fail candidate-already-installed
fi
if [ "$ACTION" = test ]; then
  STOPPED=1
  /system/bin/stop gocsdk && wait_stopped || fail system-stop-failed
  ! listener || fail stale-listener
  state starting temporary-candidate
  "$CANDIDATE" </dev/null >/dev/null 2>&1 &
  n=0
  while [ "$n" -lt 20 ]; do
    if running "$CANDIDATE" && listener; then break; fi
    sleep 1; n=$((n+1))
  done
  running "$CANDIDATE" && listener || fail candidate-not-ready
  state ready awaiting-iphone
  n=0
  while kill -0 "$OWNER" 2>/dev/null && [ ! -f "$BASE/stop" ] && [ "$n" -lt 900 ]; do
    running "$CANDIDATE" || fail candidate-exited
    sleep 1; n=$((n+1))
  done
  OK=1
  exit 0
fi

mount_mode || fail unsupported-system-mount
ORIGINAL_MODE=$MODE
if [ "$ACTION" = install ]; then
  [ "$(cat "$BASE/proof" 2>/dev/null)" = "$EXPECTED_CURRENT:$CANDIDATE_SHA" ] || fail compatibility-test-required
  if [ -e "$BACKUP" ] || [ -e "$BACKUP.sha256" ]; then
    [ "$(hash "$BACKUP")" = "$EXPECTED_CURRENT" ] &&
      [ "$(cat "$BACKUP.sha256" 2>/dev/null)" = "$EXPECTED_CURRENT" ] || fail existing-backup-mismatch
  fi
  SOURCE=$CANDIDATE
  EXPECTED=$CANDIDATE_SHA
else
  [ "$EXPECTED_CURRENT" = "$CANDIDATE_SHA" ] || fail system-not-installed-candidate
  EXPECTED=$(cat "$BACKUP.sha256" 2>/dev/null)
  [ "${#EXPECTED}" -eq 64 ] && [ "$EXPECTED" != "$CANDIDATE_SHA" ] &&
    [ "$(hash "$BACKUP")" = "$EXPECTED" ] || fail backup-missing-or-corrupt
  SOURCE=$BACKUP
fi
cp -p "$TARGET" "$ROLLBACK" || fail rollback-copy-failed
[ "$(hash "$ROLLBACK")" = "$EXPECTED_CURRENT" ] || fail rollback-hash-mismatch
remount_rw || fail remount-failed
if [ "$ACTION" = install ] && [ ! -e "$BACKUP" ]; then
  state backing-up original
  cp -p "$TARGET" "$BACKUP.new" || fail backup-copy-failed
  [ "$(hash "$BACKUP.new")" = "$EXPECTED_CURRENT" ] || fail backup-hash-mismatch
  mv "$BACKUP.new" "$BACKUP" || fail backup-rename-failed
  echo "$EXPECTED_CURRENT" > "$BACKUP.sha256" || fail backup-record-failed
  chmod 600 "$BACKUP.sha256" || fail backup-record-mode-failed
  sync
fi
STOPPED=1
/system/bin/stop gocsdk && wait_stopped || fail system-stop-failed
state replacing "$ACTION"
replace "$SOURCE" "$EXPECTED" || fail replacement-failed
restore_mount || fail mount-restore-failed
/system/bin/start gocsdk && wait_started || fail service-start-failed
if [ "$ACTION" = install ]; then
  n=0
  while ! listener && [ "$n" -lt 20 ]; do sleep 1; n=$((n+1)); done
  listener || fail listener-start-failed
fi
STOPPED=0
OK=1
exit 0

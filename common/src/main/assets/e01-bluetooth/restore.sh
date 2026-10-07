#!/system/bin/sh
fail() { echo "ERROR: $*"; exit 1; }
[ "$(id -u)" = 0 ] || fail '需要管理员权限'
marker=/data/local/tmp/e01-btswitch-owned
[ -d "$marker" ] && [ ! -L "$marker" ] || fail '没有本工具的切换记录，无需恢复'
waitfor() { n=0; while [ "$(getprop init.svc.$1)" != "$2" ]; do n=$((n+1)); [ "$n" -lt 8 ] || return 1; sleep 1; done; }
stop mtkbt
waitfor mtkbt stopped || fail 'MTK 未停止，不能启动原厂服务；请重启车机'
start gocsdk
waitfor gocsdk running || fail '原厂服务未启动，请重启车机'
sleep 2
[ "$(getprop init.svc.gocsdk)" = running ] || fail '原厂服务退出，请重启车机'
[ -c /dev/goc_stpbt ] && [ ! -e /dev/stpbt ] || fail '原厂节点未恢复，请重启车机'
rmdir "$marker" || fail '无法清理恢复记录'
echo E01_RESTORE_READY

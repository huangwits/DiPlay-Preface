#!/system/bin/sh
fail() { echo "ERROR: $*"; exit 1; }
[ "$(id -u)" = 0 ] || fail '需要管理员权限'
[ "$(getprop ro.product.model)" = E01 ] || fail '仅支持已分析的 E01'
[ "$(getprop ro.board.platform)" = mt6735 ] || fail '平台不匹配'
case "$(getprop ro.build.display.id)" in SWFS11G1105H5182.00305|SWFS11G1115H7007.00018) ;; *) fail '固件版本未验证' ;; esac
marker=/data/local/tmp/e01-btswitch-owned
[ ! -e "$marker" ] || fail '存在上次切换记录，请先恢复原厂蓝牙'
[ "$(getprop init.svc.gocsdk)" = running ] || fail '原厂服务未运行'
[ "$(getprop init.svc.mtkbt)" = stopped ] || fail 'MTK 服务状态不符合切换条件'
[ ! -e /dev/stpbt ] && [ ! -L /dev/stpbt ] || fail '标准节点已存在，不覆盖'
[ -c /dev/goc_stpbt ] && [ ! -L /dev/goc_stpbt ] || fail '原厂节点不是预期字符设备'
mkdir "$marker" || fail '无法创建恢复记录'
waitfor() { n=0; while [ "$(getprop init.svc.$1)" != "$2" ]; do n=$((n+1)); [ "$n" -lt 8 ] || return 1; sleep 1; done; }
rollback() {
  trap - 0 1 2 15
  stop mtkbt
  if waitfor mtkbt stopped; then
    start gocsdk
    if waitfor gocsdk running; then
      sleep 2
      if [ -c /dev/goc_stpbt ] && [ ! -e /dev/stpbt ]; then rmdir "$marker"; echo '已恢复原厂服务；请检查原厂电话和音乐'; else echo '恢复节点未确认，请重启车机'; fi
    else echo '原厂服务恢复失败，请重启车机'; fi
  else echo 'MTK 未停止，请重启车机'; fi
}
trap rollback 0 1 2 15
stop gocsdk
waitfor gocsdk stopped || fail '原厂服务未停止'
for f in /proc/[0-9]*/comm; do
  [ -r "$f" ] || continue
  read name < "$f"
  [ "$name" != gocsdk ] || fail '原厂进程仍在运行'
done
[ ! -e /dev/stpbt ] && [ ! -L /dev/stpbt ] && [ -c /dev/goc_stpbt ] && [ ! -L /dev/goc_stpbt ] || fail '设备节点发生变化'
mv /dev/goc_stpbt /dev/stpbt || fail '节点交接失败'
start mtkbt
waitfor mtkbt running || fail 'MTK 启动失败'
sleep 2
[ "$(getprop init.svc.mtkbt)" = running ] || fail 'MTK 启动后退出'
trap - 0 1 2 15
echo E01_SWITCH_READY

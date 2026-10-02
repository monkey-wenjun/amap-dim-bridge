#!/usr/bin/env bash
# 把「高德地图」拖进左侧栏（Launcher3 hotseat）里原来百度地图那一格。
#
# 为什么只能拖：hotseat 是 launcher.db 里的固定记录，写它需要
# ecarx.launcher3.permission.WRITE_SETTINGS（protectionLevel=signature|privileged），
# 第三方 App 拿不到；也没有对外开放的接口。这个 build 里 INSTALL_SHORTCUT 也没在
# manifest 注册。所以只能用「长按 → 拖 → 松手」这一条路（不用 root）。
#
# 用法：
#   1) 车机 adb 连上（USB）
#   2) 在车机上先把「应用列表」那一页打开，让高德地图图标可见
#   3) ./tools/add-amap-to-hotseat.sh            # 用默认坐标
#      或 ./tools/add-amap-to-hotseat.sh <源x> <源y> <目标x> <目标y> [序列号]
#      坐标是 **逻辑坐标**（车机 wm size 覆盖成 1280x720，就按 1280x720 量）
#
# 脚本会先截一张图存到 /tmp 供核对，再合成手势。

set -euo pipefail

pick_serial() {
  adb devices | awk '/\tdevice$/{print $1}' | while read -r d; do
    m=$(adb -s "$d" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    if [ "$m" = "CS11" ]; then echo "$d"; break; fi
  done
}

if [ "$#" -ge 4 ]; then
  SRC_X=$1; SRC_Y=$2; DST_X=$3; DST_Y=$4; SER=${5:-}
else
  # 默认值按 1280x720 逻辑屏量：应用列表页里「高德地图」图标 / 左侧栏第 3 格
  SRC_X=${1:-590}; SRC_Y=${2:-382}
  DST_X=${3:-50};  DST_Y=${4:-128}
  SER=${5:-}
fi
[ -n "$SER" ] || SER=$(pick_serial)
[ -n "$SER" ] || { echo "❌ 没找到车机（ro.product.model 应为 CS11）。当前设备："; adb devices -l; exit 1; }

ADB="adb -s $SER"
echo "设备：$SER ($($ADB shell getprop ro.product.model | tr -d '\r'))"

# ---- 逻辑分辨率（车机被覆盖成 1280x720，取最后一行 = Override）----
SIZE=$($ADB shell wm size | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -1)
LOG_W=${SIZE%x*}; LOG_H=${SIZE#*x}
echo "逻辑分辨率：${LOG_W}x${LOG_H}"

# ---- 触摸设备 & 原始坐标最大值 ----
GETEV=$($ADB shell getevent -pl 2>/dev/null | tr -d '\r')
TOUCH=$(echo "$GETEV" | awk '
  /^add device .*: \/dev\/input\/event/ { dev=$NF }
  /ABS_MT_POSITION_X/ { print dev; exit }')
[ -n "$TOUCH" ] || { echo "❌ 没找到触摸设备（getevent -pl 里没有 ABS_MT_POSITION_X）"; exit 1; }
RAWMX=$(echo "$GETEV" | awk '/ABS_MT_POSITION_X/{for(i=1;i<=NF;i++) if($i=="max"){print $(i+1); exit}}' | tr -d ',')
RAWMY=$(echo "$GETEV" | awk '/ABS_MT_POSITION_Y/{for(i=1;i<=NF;i++) if($i=="max"){print $(i+1); exit}}' | tr -d ',')
# 有的驱动用 ABS_X / ABS_Y
[ -n "${RAWMX:-}" ] || RAWMX=$(echo "$GETEV" | awk '/ABS_X /{for(i=1;i<=NF;i++) if($i=="max"){print $(i+1); exit}}' | tr -d ',')
[ -n "${RAWMY:-}" ] || RAWMY=$(echo "$GETEV" | awk '/ABS_Y /{for(i=1;i<=NF;i++) if($i=="max"){print $(i+1); exit}}' | tr -d ',')
RAWMX=${RAWMX:-$LOG_W}; RAWMY=${RAWMY:-$LOG_H}
HAS_SLOT=$(echo "$GETEV" | grep -c 'ABS_MT_SLOT' || true)

echo "触摸设备：$TOUCH  原始范围：${RAWMX}x${RAWMY}  MT-SLOT:$HAS_SLOT"

rX() { echo $(( $1 * RAWMX / LOG_W )); }
rY() { echo $(( $1 * RAWMY / LOG_H )); }

SX=$(rX "$SRC_X"); SY=$(rY "$SRC_Y")
DX=$(rX "$DST_X"); DY=$(rY "$DST_Y")
echo "拖拽：逻辑($SRC_X,$SRC_Y) → ($DST_X,$DST_Y)   原始($SX,$SY) → ($DX,$DY)"

# ---- 核对截图 ----
$ADB shell screencap -p /sdcard/hotseat_before.png >/dev/null 2>&1 || true
$ADB pull /sdcard/hotseat_before.png /tmp/hotseat_before.png >/dev/null 2>&1 || true
echo "已存截图（拖之前的画面）：/tmp/hotseat_before.png"

EV() { $ADB shell "sendevent $TOUCH $*"; }

# ---- 按下并停住（长按）----
if [ "$HAS_SLOT" -gt 0 ]; then
  EV 3 47 0            # ABS_MT_SLOT 0
  EV 3 57 1            # ABS_MT_TRACKING_ID
fi
EV 3 53 "$SX"
EV 3 54 "$SY"
EV 1 330 1             # BTN_TOUCH down
EV 0 0 0
sleep 1.0

# ---- 分几步移动（Launcher3 要看到持续 move 才会进入拖拽）----
STEPS=8
for i in $(seq 1 $STEPS); do
  x=$(( SX + (DX - SX) * i / STEPS ))
  y=$(( SY + (DY - SY) * i / STEPS ))
  EV 3 53 "$x"
  EV 3 54 "$y"
  EV 0 0 0
  sleep 0.12
done
sleep 0.4

# ---- 松手 ----
EV 1 330 0
if [ "$HAS_SLOT" -gt 0 ]; then
  EV 3 57 4294967295   # tracking id -1
fi
EV 0 0 0
sleep 1.0

$ADB shell screencap -p /sdcard/hotseat_after.png >/dev/null 2>&1 || true
$ADB pull /sdcard/hotseat_after.png /tmp/hotseat_after.png >/dev/null 2>&1 || true
echo "拖之后截图：/tmp/hotseat_after.png —— 看一眼左侧栏有没有出现「高德地图」。"

#!/usr/bin/env bash
# 编译并装到车机（adb 已连上 CS11 时）
set -e
cd "$(dirname "$0")"

export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export JAVA_HOME=${JAVA_HOME:-$HOME/.local/share/mise/installs/java/temurin-17.0.20+101}

./gradlew --offline :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk

# 拉起来（服务自带前台通知，会常驻）
adb shell monkey -p com.awen.amapdim -c android.intent.category.LAUNCHER 1 >/dev/null
echo "已安装。高德导航时看仪表盘，或 adb logcat -s AmapDimBridge DimNavi"

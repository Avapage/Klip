#!/usr/bin/env bash
set -euo pipefail
adb wait-for-device
for variant in arm64 universal; do
  apk="app/build/outputs/apk/$variant/debug/app-$variant-debug.apk"
  adb uninstall com.avapage.video || true
  adb install "$apk"
  adb shell am start -W -n com.avapage.video/.MainActivity
  sleep 10
  adb shell pidof com.avapage.video
done

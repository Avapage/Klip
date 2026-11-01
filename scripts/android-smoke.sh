#!/usr/bin/env bash
set -euo pipefail
work="$(mktemp -d)"
server_pid=''
trap '[[ -z "$server_pid" ]] || kill "$server_pid" 2>/dev/null || true; rm -rf "$work"' EXIT
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc2=size=480x270:rate=24 -t 20 -c:v libx264 -preset ultrafast -y "$work/sample.mp4"
cat > "$work/server.py" <<'PY'
import http.server, time
class Slow(http.server.SimpleHTTPRequestHandler):
    def copyfile(self, source, output):
        while True:
            data=source.read(32768)
            if not data: break
            output.write(data)
            output.flush()
            time.sleep(.25)
http.server.ThreadingHTTPServer(('0.0.0.0',8765),Slow).serve_forever()
PY
(cd "$work" && python3 -u server.py >"$work/server.log" 2>&1) & server_pid=$!
adb wait-for-device
adb install -r app/build/outputs/apk/universal/debug/app-universal-debug.apk
adb shell pm grant com.avapage.video android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'http://10.0.2.2:8765/sample.mp4' -n com.avapage.video/.MainActivity
# Wait for the engine to initialize and for the start button to become enabled.
clicked=''
for attempt in $(seq 1 24); do
  sleep 5
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "$work/window.xml" >/dev/null 2>&1
  point="$(python3 - "$work/window.xml" <<'PY'
import re, sys, xml.etree.ElementTree as E
root=E.parse(sys.argv[1]).getroot()
for e in root.iter('node'):
    if e.get('text','').lower()=='mulai download' and e.get('enabled')=='true':
        match=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',e.get('bounds',''))
        if match:
            x,y,X,Y=map(int,match.groups())
            if X>x and Y>y: print((x+X)//2,(y+Y)//2)
PY
)"
  if [[ -n "$point" ]]; then
    read -r x y <<< "$point"
    if (( y >= 580 )); then adb shell input swipe 160 540 160 260 350; continue; fi
    adb shell input tap "$x" "$y"; clicked=1; break
  fi
  adb shell input swipe 160 520 160 220 300 || true
done
[[ -n "$clicked" ]] || { echo 'Download button did not become available' >&2; exit 1; }
sleep 5
adb shell uiautomator dump /sdcard/before-home.xml >/dev/null || true
adb shell cat /sdcard/before-home.xml || true
adb shell input keyevent KEYCODE_HOME
found=''
for attempt in $(seq 1 36); do
  sleep 5
  if adb shell ls /sdcard/Download/Klip/ 2>/dev/null | tr -d '\r' | grep -q '\.mp4$'; then found=1; break; fi
done
[[ -n "$found" ]] || { echo 'No MP4 appeared in Downloads/Klip after backgrounding' >&2; cat "$work/server.log" || true; adb shell am start -n com.avapage.video/.MainActivity || true; sleep 3; adb shell uiautomator dump /sdcard/failure.xml >/dev/null || true; adb shell cat /sdcard/failure.xml || true; adb logcat -d -s AndroidRuntime:E ActivityManager:W; exit 1; }
adb pull /sdcard/Download/Klip "$work/result" >/dev/null
output="$(find "$work/result" -type f -name '*.mp4' -print -quit)"
[[ -n "$output" ]] && ffprobe -v error -show_entries format=duration -of default=nw=1 "$output"
echo 'Background download smoke test passed'

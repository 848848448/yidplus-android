#!/usr/bin/env bash
# Emulator test for the native app (CI): Android 11 (API 30) without Google services, like the Qin F21.
# Drives the app with the D-pad (the Qin's keys), records which item has the focus after each key,
# takes screenshots at 480x640 (Qin F21), 1080x2400 (normal phone) and 1600x2560 (10" tablet, both ways),
# measures cold start / screen-open times and frame times, and fails on any app crash.
set -u
APK="$1"
PKG=com.yidplus.app
OUT=qa/out
mkdir -p "$OUT"
FAIL=0
log() { echo "[$(date +%T)] $*" | tee -a "$OUT/qa.log"; }
shot() { timeout 30 adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1"; }
key() { timeout 10 adb shell input keyevent "$@"; sleep 0.8; }
view() { timeout 40 adb shell am start -a android.intent.action.VIEW -d "$1" $PKG >/dev/null 2>&1; log "open $1"; }
tab() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_TAB --es tab "$1" >/dev/null 2>&1; log "tab $1"; }
alive() { adb shell pidof $PKG >/dev/null 2>&1; }
focus() {
  timeout 20 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local f; f=$(timeout 10 adb shell cat /sdcard/ui.xml 2>/dev/null | tr '>' '\n' | grep 'focused="true"' | grep -v 'class="android.widget.FrameLayout"\|class="android.view.View" text="" resource-id="" class="android.view.View" package="com.yidplus.app" content-desc="" checkable="false" checked="false" clickable="false"' | tail -1)
  local t d b
  t=$(echo "$f" | grep -o ' text="[^"]*"' | head -1); d=$(echo "$f" | grep -o 'content-desc="[^"]*"' | head -1); b=$(echo "$f" | grep -o 'bounds="[^"]*"')
  log "FOCUS after $1: ${t:- (no text)} ${d} ${b:-nothing focused}"
}
check() {
  if ! alive; then
    why=$(timeout 30 adb logcat -d -t 4000 2>/dev/null | grep -E "FATAL EXCEPTION|ANR in $PKG" | tail -1)
    log "NOT RUNNING after $1: ${why:-no reason in log}"; FAIL=1
  fi
}
size() { adb shell wm size "$1"; adb shell wm density "$2"; sleep 2; log "screen $1 @ $2 dpi"; }
restart() { adb shell am force-stop $PKG; sleep 1; timeout 60 adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep "${1:-6}"; }

adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell input keyevent 82
log "install: $(timeout 180 adb install -r "$APK" 2>&1 | tail -1)"
adb shell dumpsys package $PKG | grep -E "versionName|versionCode" | head -2 | tee -a "$OUT/qa.log"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
size 480x640 240
adb logcat -c

# ---- Cold start (first ever start, nothing cached) ----
adb shell am force-stop $PKG
timeout 60 adb shell am start -W -n $PKG/.MainActivity | tee -a "$OUT/qa.log" | grep -E "TotalTime|WaitTime" | sed 's/^/first start: /' >> "$OUT/perf.txt"
sleep 0.5; shot 00-splash
sleep 7; shot 01-start
log "bottom bar = the site's own (read from yidplus.com/api/settings): $(timeout 20 curl -s -A 'Mozilla/5.0 (Linux; Android 11)' https://yidplus.com/api/settings | grep -o '"feat_[a-z]*":"[a-z]*"' | tr '\n' ' ')"
check start
tab Home; sleep 5; shot 01-home

# ---- D-pad on Home ----
key 20; focus "DOWN"; shot 02-home-dpad
key 20; key 20; focus "DOWN DOWN"
shot 03-home-dpad-3
key 23; sleep 6; focus "OK (open channel)"; shot 04-channel
key 20; key 20; focus "DOWN DOWN in channel"; shot 05-channel-dpad
key 23; sleep 5; shot 06-channel-media
key 4; sleep 1; focus "BACK"
key 4; sleep 1; focus "BACK to list"; shot 07-back-to-home
check channel

# ---- Chats (guest: featured groups) ----
view "https://yidplus.com/chat"; sleep 6; shot 08-chats
key 20; focus "DOWN in chats"
key 23; sleep 7; shot 09-chat-room; focus "OK (open chat)"
key 4; sleep 1; shot 10-chats-back
check chats

# ---- Shorts ----
view "https://yidplus.com/shorts"; sleep 10; shot 11-shorts-hint
key 7; sleep 6; shot 12-shorts-playing
key 20; sleep 7; shot 13-shorts-next; log "DOWN = next video"
key 23; sleep 1; shot 14-shorts-paused; key 23
key 8; sleep 1; shot 15-shorts-liked; log "key 1 = like"
key 10; sleep 5; shot 16-shorts-comments; log "key 3 = comments"
key 4; sleep 1
key 17; sleep 1; key 17; sleep 8; shot 17-shorts-uploads; log "* * = Uploads tab"
key 17; sleep 1; key 17; sleep 1
check shorts
key 4; sleep 2; focus "BACK from Shorts"; shot 18-after-shorts-back

# ---- Music (switched off on the site right now: it is hidden in the app too; the QA switch shows it for the photos) ----
view "https://yidplus.com/music"; sleep 4; shot 19a-music-off-on-site; focus "open /music while the site has Music off"
timeout 20 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_FEATURES --ez all true >/dev/null 2>&1; sleep 2
view "https://yidplus.com/music"; sleep 6; shot 19-music
key 20; focus "DOWN in music"
key 23; sleep 6; shot 20-music-playing; log "OK = play"
timeout 30 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_PLAYER >/dev/null; sleep 3; shot 21-music-player; focus "player opened"
adb shell cmd statusbar expand-notifications; sleep 3; shot 22-music-notification
adb shell cmd statusbar collapse; sleep 1
key 4; sleep 1
check music

# ---- Profile / sign in / settings / updates ----
tab Profile; sleep 3; shot 23-profile-signin; focus "profile opened"
key 20; key 20; focus "DOWN DOWN in sign-in form"; shot 24-profile-dpad
for i in 1 2 3 4 5 6 7 8; do key 20; done; sleep 1; focus "DOWN x8 (settings)"; shot 25-settings
timeout 30 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.CHECK_UPDATE >/dev/null; sleep 6; shot 26-check-updates
check profile

# ---- Dark mode ----
adb shell cmd uimode night yes; sleep 3
tab Home; sleep 4; shot 27-home-dark
view "https://yidplus.com/chat"; sleep 4; shot 28-chats-dark
tab Profile; sleep 3; shot 29-profile-dark
adb shell cmd uimode night no; sleep 3

# ---- Offline (opens from the saved copies) ----
adb shell svc wifi disable; adb shell svc data disable; sleep 3
restart 6; shot 30-offline-home
view "https://yidplus.com/music"; sleep 3; shot 31-offline-music
adb shell svc wifi enable; adb shell svc data enable; sleep 12
tab Home; sleep 5; shot 32-back-online
check offline

# ---- Speed: cold starts with data cached, screen-open marks, frame times ----
for i in 1 2 3; do
  adb shell am force-stop $PKG; sleep 2
  timeout 60 adb shell am start -W -n $PKG/.MainActivity | grep -E "TotalTime" | sed "s/^/cold start $i (before profile compile): /" >> "$OUT/perf.txt"; sleep 6
done
timeout 300 adb shell cmd package compile -f -m speed-profile $PKG | tee -a "$OUT/qa.log"
for i in 1 2 3; do
  adb shell am force-stop $PKG; sleep 2
  timeout 60 adb shell am start -W -n $PKG/.MainActivity | grep -E "TotalTime" | sed "s/^/cold start $i (baseline profile compiled): /" >> "$OUT/perf.txt"; sleep 6
done
adb shell dumpsys gfxinfo $PKG reset >/dev/null
for i in $(seq 1 25); do adb shell input keyevent 20; sleep 0.3; done
for i in $(seq 1 10); do adb shell input swipe 240 520 240 200 200; sleep 0.4; done
adb shell dumpsys gfxinfo $PKG | grep -E "Total frames|Janky|percentile|Slow" >> "$OUT/perf.txt"

# ---- Normal phone 1080x2400 ----
size 1080x2400 420
restart 6; shot 40-phone-home
key 20; key 23; sleep 5; shot 41-phone-channel; key 4
view "https://yidplus.com/chat"; sleep 4; shot 42-phone-chats
view "https://yidplus.com/shorts"; sleep 9; key 7; sleep 3; shot 43-phone-shorts
view "https://yidplus.com/music"; sleep 4; shot 44-phone-music
tab Profile; sleep 3; shot 45-phone-profile
size 2400x1080 420
restart 6; shot 46-phone-landscape-home
view "https://yidplus.com/shorts"; sleep 9; key 7; sleep 3; shot 47-phone-landscape-shorts
check phone

# ---- 10" tablet, portrait and landscape ----
size 1600x2560 320
restart 6; shot 50-tablet-portrait-home
key 20; key 23; sleep 5; shot 51-tablet-portrait-channel
view "https://yidplus.com/chat"; sleep 4; shot 55-tablet-portrait-chats
view "https://yidplus.com/shorts"; sleep 9; key 7; sleep 3; shot 56-tablet-portrait-shorts
view "https://yidplus.com/music"; sleep 4; shot 57-tablet-portrait-music
size 2560x1600 320
restart 6; shot 58-tablet-landscape-home
restart 6; key 20; key 23; sleep 5; shot 52-tablet-landscape-channel
view "https://yidplus.com/chat"; sleep 5; shot 53-tablet-landscape-chats
view "https://yidplus.com/shorts"; sleep 9; key 7; sleep 3; shot 54-tablet-landscape-shorts
view "https://yidplus.com/music"; sleep 4; shot 59-tablet-landscape-music
tab Profile; sleep 3; shot 60-tablet-landscape-profile
check tablet
adb shell wm size reset; adb shell wm density reset

# ---- Instant notifications: can this phone get a Firebase token? (Google APIs image: yes; plain image: no) ----
adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_PUSH_PROBE >/dev/null; sleep 15
timeout 30 adb logcat -d -s YidplusPush | grep probe | tail -2 | sed "s/^/push: /" | tee -a "$OUT/qa.log" >> "$OUT/perf.txt"

timeout 60 adb logcat -d -t 30000 > "$OUT/logcat.txt"
grep "YidPerf" "$OUT/logcat.txt" >> "$OUT/perf.txt" || true
if grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "Process: $PKG"; then
  log "CRASH found in logcat"; grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80 | tee -a "$OUT/qa.log"; FAIL=1
fi
grep -E "FOCUS" "$OUT/qa.log" > "$OUT/dpad.txt" || true
cat "$OUT/perf.txt"
if [ "$FAIL" = 0 ]; then echo "PASS" > "$OUT/RESULT"; else echo "FAIL" > "$OUT/RESULT"; fi
cat "$OUT/RESULT"
exit 0

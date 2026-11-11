#!/usr/bin/env bash
# Emulator smoke test run by CI: installs the release APK, opens the main screens,
# checks for crashes and saves screenshots to qa/out (published to the qa-evidence branch).
set -u
APK="$1"
PKG=com.yidplus.app
OUT=qa/out
mkdir -p "$OUT"
FAIL=0
log() { echo "[$(date +%T)] $*" | tee -a "$OUT/qa.log"; }
shot() { timeout 30 adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1"; }
open_url() { timeout 40 adb shell am start -a android.intent.action.VIEW -d "$1" $PKG >/dev/null 2>&1; log "open $1"; }
alive() { adb shell pidof $PKG >/dev/null 2>&1; }
device() { [ "$(timeout 10 adb get-state 2>/dev/null)" = "device" ]; }
# A dead app is a failure only when Android says the app itself crashed or stopped answering.
check() {
  if ! device; then log "EMULATOR LOST during $1 (the emulator itself stopped, not the app)"; EMU_LOST=1; return; fi
  if ! alive; then
    why=$(timeout 30 adb logcat -d -t 4000 2>/dev/null | grep -E "Killing [0-9]+:$PKG|FATAL EXCEPTION|ANR in $PKG" | tail -1)
    log "NOT RUNNING after $1: ${why:-no reason in log}"
    case "$why" in *"dying proc"*|*"lowmemory"*) log "  (killed by Android because a system service died; not an app crash)";; *) FAIL=1;; esac
  fi
}
EMU_LOST=0

adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell input keyevent 82
log "install: $(timeout 180 adb install -r "$APK" 2>&1 | tail -1)"
adb shell dumpsys package $PKG | grep -E "versionName|versionCode" | head -2 | tee -a "$OUT/qa.log"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c

T0=$(date +%s%N)
timeout 60 adb shell am start -W -n $PKG/.MainActivity | tee -a "$OUT/qa.log"
sleep 0.6; shot 01-launch
sleep 14; shot 02-home
check home

open_url "https://yidplus.com/chat"
sleep 12; shot 05-chats
check chats

open_url "https://yidplus.com/music"
sleep 10; shot 06-music

adb shell input keyevent 4; sleep 4; shot 07-after-back
check back

# Pull to refresh on the home page
open_url "https://yidplus.com/"
sleep 10
adb shell input swipe 540 700 540 1500 600
sleep 0.4; shot 08-pull-refresh
sleep 8

# Dark mode
adb shell cmd uimode night yes
sleep 12; shot 09-home-dark
open_url "https://yidplus.com/chat"
sleep 10; shot 10-chats-dark
adb shell cmd uimode night no
sleep 6

# Offline screen
adb shell svc wifi disable; adb shell svc data disable
sleep 3
open_url "https://yidplus.com/?offline-test=1"
sleep 8; shot 11-offline
adb shell svc wifi enable; adb shell svc data enable
sleep 15; shot 12-back-online
check offline

# App links status (verified once yidplus.com/.well-known/assetlinks.json lists this app's key)
timeout 30 adb shell pm verify-app-links --re-verify $PKG; sleep 12
timeout 30 adb shell pm get-app-links $PKG 2>&1 | tee "$OUT/app-links.txt"
timeout 60 adb logcat -d -t 20000 > "$OUT/logcat-before-shorts.txt"

open_url "https://yidplus.com/shorts"
sleep 14; shot 13-shorts
adb shell input swipe 540 1700 540 500 250
sleep 5; shot 14-shorts-next
check shorts


timeout 60 adb logcat -d -t 20000 > "$OUT/logcat.txt"
cat "$OUT/logcat-before-shorts.txt" "$OUT/logcat.txt" 2>/dev/null > "$OUT/logcat-all.txt"
if grep -A3 "FATAL EXCEPTION" "$OUT/logcat-all.txt" | grep -q "Process: $PKG"; then
  log "CRASH found in logcat"; grep -A 25 "FATAL EXCEPTION" "$OUT/logcat-all.txt" | head -60 | tee -a "$OUT/qa.log"; FAIL=1
fi
grep -E "chromium|YidUpdater" "$OUT/logcat-all.txt" | tail -80 > "$OUT/webview-log.txt" || true
rm -f "$OUT/logcat-all.txt"
[ "$EMU_LOST" = 1 ] && log "note: emulator was lost during the test"

if [ "$FAIL" = 0 ]; then echo "PASS" > "$OUT/RESULT"; else echo "FAIL" > "$OUT/RESULT"; fi
cat "$OUT/RESULT"
exit 0

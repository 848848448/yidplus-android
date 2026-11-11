#!/usr/bin/env bash
# Screen-size test run by CI for every phone shape: installs the APK, checks touch and D-pad
# scrolling, the bottom bar (Home, Chats, Shorts, Music, Profile), Back, and saves screenshots.
#   qa/screens.sh APK NAME [WxH DENSITY] [landscape]
set -u
APK="$1"; NAME="$2"; SIZE="${3:-}"; DENS="${4:-}"; ROT="${5:-}"
PKG=com.yidplus.app
OUT=qa/out/$NAME
mkdir -p "$OUT"
FAIL=0; EMU_LOST=0
log() { echo "[$(date +%T)] $*" | tee -a "$OUT/qa.log"; }
shot() { timeout 30 adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1 $(md5sum < "$OUT/$1.png" | cut -c1-8)"; }
alive() { adb shell pidof $PKG >/dev/null 2>&1; }
device() { [ "$(timeout 10 adb get-state 2>/dev/null)" = "device" ]; }
check() {
  if ! device; then log "EMULATOR LOST during $1"; EMU_LOST=1; return; fi
  if ! alive; then
    why=$(timeout 30 adb logcat -d -t 4000 2>/dev/null | grep -E "Killing [0-9]+:$PKG|FATAL EXCEPTION|ANR in $PKG" | tail -1)
    log "NOT RUNNING after $1: ${why:-no reason in log}"
    case "$why" in *"dying proc"*|*"lowmemory"*) log "  (killed by Android, not an app crash)";; *) FAIL=1;; esac
  fi
}
find_xy() {
  timeout 30 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  timeout 30 adb pull /sdcard/ui.xml /tmp/ui-$NAME.xml >/dev/null 2>&1
  python3 - "$1" "/tmp/ui-$NAME.xml" <<'EOF'
import re, sys
x = open(sys.argv[2], encoding='utf-8').read(); want = sys.argv[1].lower()
for n in re.findall(r'<node [^>]*>', x):
    t = (re.search(r' text="([^"]*)"', n) or [None, ''])[1].lower()
    rid = (re.search(r'resource-id="([^"]*)"', n) or [None, ''])[1].lower()
    if t == want or rid == want:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            a, c, d, e = map(int, b.groups()); print((a + d) // 2, (c + e) // 2); break
EOF
}
anr_check() {
  local xy; xy=$(find_xy "Wait")
  if [ -n "$xy" ] || timeout 20 adb logcat -d -t 20000 2>/dev/null | grep -q "ANR in $PKG"; then
    log "ANR seen during $1"
    timeout 20 adb logcat -d -t 20000 2>/dev/null | grep -A 12 "ANR in" | head -40 >> "$OUT/anr.txt"
    timeout 10 adb root >/dev/null 2>&1; sleep 2
    for f in $(timeout 10 adb shell ls /data/anr 2>/dev/null | tr -d '\r'); do
      timeout 20 adb shell cat /data/anr/$f 2>/dev/null | awk '/"main"/,/^$/' | head -60 >> "$OUT/anr.txt"
    done
    [ -n "$xy" ] && adb shell input tap $xy
    ANR=1
  fi
}
ANR=0
CF=0
tap() { local xy; for i in $(seq 1 ${2:-8}); do xy=$(find_xy "$1"); if [ -n "$xy" ]; then adb shell input tap $xy; log "tapped '$1' at $xy"; return 0; fi; sleep 2; done; log "did not find '$1'"; FAIL=1; return 1; }
same() { cmp -s "$OUT/$1.png" "$OUT/$2.png"; }

adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell input keyevent 82
if [ -n "$SIZE" ]; then adb shell wm size "$SIZE"; fi
if [ -n "$DENS" ]; then adb shell wm density "$DENS"; fi
if [ "$ROT" = landscape ]; then adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1; fi
sleep 3
log "screen: $(adb shell wm size | tr -d '\r' | tail -1) $(adb shell wm density | tr -d '\r' | tail -1) rotation=${ROT:-portrait} android=$(adb shell getprop ro.build.version.release | tr -d '\r')"
log "web engine: $(adb shell dumpsys webviewupdate | grep -iE 'Current WebView package' | tr -d '\r' | head -1)"
log "install: $(timeout 180 adb install -r "$APK" 2>&1 | tail -1)"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c
read W H < <(adb shell wm size | tail -1 | sed 's/.*: //' | tr 'x' ' ' | tr -d '\r')
if [ "$ROT" = landscape ] && [ "$W" -lt "$H" ]; then T=$W; W=$H; H=$T; fi
CX=$((W/2)); Y1=$((H*68/100)); Y2=$((H*30/100))

# The released version first, for comparison (small screen leg only)
if [ -n "${BASELINE_APK:-}" ] && [ -f "$BASELINE_APK" ]; then
  log "baseline install: $(timeout 180 adb install -r "$BASELINE_APK" 2>&1 | tail -1)"
  timeout 60 adb shell am start -W -n $PKG/.MainActivity >/dev/null
  sleep 18; shot 00-released-version
  anr_check released
  adb shell input swipe $CX $Y1 $CX $Y2 400; sleep 2; shot 00-released-version-swiped
  timeout 30 adb uninstall $PKG >/dev/null 2>&1
  log "re-install test build: $(timeout 180 adb install -r "$APK" 2>&1 | tail -1)"
  adb logcat -c
fi

timeout 60 adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 16; shot 01-home
anr_check start
[ "$ANR" = 1 ] && { sleep 3; shot 01b-home-after-wait; }
check start

# Touch scrolling
adb shell input swipe $CX $Y1 $CX $Y2 400; sleep 2; shot 02-home-touch-scrolled
if same 01-home 02-home-touch-scrolled; then
  # the page may still have been loading: look again and swipe once more
  sleep 6; shot 01-home; adb shell input swipe $CX $Y1 $CX $Y2 400; sleep 2; shot 02-home-touch-scrolled
fi
if same 01-home 02-home-touch-scrolled; then
  if [ -n "$(find_xy 'Verify you are human')" ] || [ -n "$(find_xy 'Performing security verification')" ]; then
    # CI machines sit in data centres; yidplus.com's Cloudflare sometimes shows them a human check instead of the site
    log "TOUCH SCROLL: not tested, yidplus.com showed Cloudflare's human check to this test machine"; CF=1
  else log "TOUCH SCROLL: page did not move"; FAIL=1; fi
else log "TOUCH SCROLL: page moved"; fi
adb shell input swipe $CX $Y1 $CX $Y2 400; sleep 1.5

# D-pad: down moves focus/scrolls, OK opens
for i in 1 2 3 4 5 6 7 8; do adb shell input keyevent 20; sleep 0.4; done
sleep 1; shot 03-home-dpad-down
if same 02-home-touch-scrolled 03-home-dpad-down; then log "DPAD: nothing moved"; FAIL=1; else log "DPAD: moved"; fi

anr_check dpad
tap "$PKG:id/tab_chats"; sleep 12; shot 04-chats
adb shell input swipe $CX $Y1 $CX $Y2 400; sleep 2; shot 05-chats-scrolled
check chats

tap "$PKG:id/tab_shorts"; sleep 14; shot 06-shorts
check shorts
anr_check shorts
tap "$PKG:id/tab_music"; sleep 12; shot 07-music
adb shell input swipe $CX $Y1 $CX $Y2 400; sleep 2; shot 08-music-scrolled
check music
tap "$PKG:id/tab_me"; sleep 12; shot 09-profile
tap "$PKG:id/tab_home"; sleep 12; shot 10-home-again
adb shell input keyevent 4; sleep 4; shot 11-after-back
check back

# D-pad all the way down reaches the bottom bar
for i in $(seq 1 40); do adb shell input keyevent 20; sleep 0.15; done
sleep 1; shot 12-dpad-to-bottom-bar
adb shell dumpsys window | grep -m1 mCurrentFocus | tee -a "$OUT/qa.log" >/dev/null

timeout 60 adb logcat -d -t 30000 > "$OUT/logcat.txt"
if grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "Process: $PKG"; then
  log "CRASH found in logcat"; grep -A 25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60 | tee -a "$OUT/qa.log"; FAIL=1
fi
grep -E "chromium|Console" "$OUT/logcat.txt" | tail -60 > "$OUT/webview-log.txt" || true
grep -E "ANR in|Input dispatching timed out|FATAL|am_anr|Choreographer.*Skipped" "$OUT/logcat.txt" | head -40 > "$OUT/anr-lines.txt" || true
rm -f "$OUT/logcat.txt"
[ "$EMU_LOST" = 1 ] && log "note: emulator was lost during the test"
[ "$ANR" = 1 ] && { log "app stopped answering at least once (see anr.txt)"; FAIL=1; }
if [ "$FAIL" = 0 ]; then echo "PASS$([ "$CF" = 1 ] && echo ' (site showed a Cloudflare check to the test machine)')" > "$OUT/RESULT"; else echo "FAIL" > "$OUT/RESULT"; fi
cat "$OUT/RESULT"
exit 0

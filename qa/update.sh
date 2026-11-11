#!/usr/bin/env bash
set -u
OLD="$1"; WANT="$2"; PKG=com.yidplus.app; OUT=qa/out
mkdir -p "$OUT"
log() { echo "$*" | tee -a "$OUT/update.log"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1"; }
ver() { adb shell dumpsys package $PKG | grep -m1 versionName | sed 's/.*versionName=//' | tr -d '\r '; }
# Finds a visible element by its text (or resource id) and prints its centre
find_xy() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  python3 - "$1" <<'EOF'
import re, sys
x = open('/tmp/ui.xml', encoding='utf-8').read(); want = sys.argv[1].lower()
for n in re.findall(r'<node [^>]*>', x):
    t = (re.search(r' text="([^"]*)"', n) or [None, ''])[1].lower()
    rid = (re.search(r'resource-id="([^"]*)"', n) or [None, ''])[1].lower()
    if t == want or rid == want:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            a, c, d, e = map(int, b.groups()); print((a + d) // 2, (c + e) // 2); break
EOF
}
anr() { local w; w=$(find_xy "Wait"); [ -n "$w" ] && { adb shell input tap $w; log "dismissed an emulator 'isn't responding' box"; sleep 2; }; }
tap() { local xy; for i in $(seq 1 ${2:-20}); do anr; xy=$(find_xy "$1"); if [ -n "$xy" ]; then adb shell input tap $xy; log "tapped '$1' at $xy"; return 0; fi; sleep 2; done; log "did not find '$1'"; return 1; }

# Waits for Android's own "Update this app?" prompt (not the app's in-app Update button), confirms it, and waits for the new version
confirm_install() {
  local xy i
  for i in $(seq 1 100); do
    if timeout 20 adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | grep -q packageinstaller; then
      xy=$(find_xy "android:id/button1"); [ -z "$xy" ] && xy=$(find_xy "Update"); [ -z "$xy" ] && xy=$(find_xy "Install")
      if [ -n "$xy" ]; then shot 05-android-install-prompt; adb shell input tap $xy; log "confirmed Android's install prompt at $xy"; sleep 6; fi
    fi
    [ "$(ver)" = "$WANT" ] && return 0
    sleep 3
  done
  return 1
}

adb wait-for-device
adb shell input keyevent 82
log "install old: $(adb install -r "$OLD" 2>&1 | tail -1)"
log "installed version: $(ver)  latest release: $WANT"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb logcat -c
if [ "${NOTIFY:-0}" = 1 ]; then
  # The background check posts "YID PLUS update vX.Y.Z" with the release notes; tapping it downloads + installs.
  adb shell am start -W -n $PKG/.MainActivity -a com.yidplus.app.QA_UPDATE_NOTIFY >/dev/null
  sleep 3; adb shell input keyevent 3; sleep 12
  adb shell cmd statusbar expand-notifications; sleep 3
  for i in $(seq 1 10); do xy=$(find_xy "YID PLUS update v$WANT"); [ -n "$xy" ] && break; sleep 3; done
  shot 00-update-notification
  adb shell dumpsys notification --noredact 2>/dev/null | grep -A40 "pkg=$PKG" | grep -E "android.title=|android.text=|android.bigText=" | head -6 | sed 's/^/notification: /' | tee -a "$OUT/update.log"
  [ -n "$xy" ] && log "notification shown: YID PLUS update v$WANT" || log "notification NOT found"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/notification-ui.xml" >/dev/null 2>&1
  tap "YID PLUS update v$WANT" 5 || true
  sleep 3; shot 01-update-from-notification
else
adb shell am start -W -n $PKG/.MainActivity >/dev/null
for i in $(seq 1 20); do xy=$(find_xy "Update available"); [ -n "$xy" ] && break; sleep 2; done
sleep 1; shot 01-update-available
tap "Update" 15 || true
fi
sleep 2; shot 02-allow-updates
tap "Open settings" 5 && { sleep 4; shot 03-android-settings; }
adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow
adb shell input keyevent 4
sleep 1.5; shot 04-downloading
confirm_install || { shot 05-android-install-prompt; log "Android install prompt never confirmed"; }
NOW=$(ver); log "version after update: $NOW"
sleep 3; shot 06-after-update
adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 12; shot 07-new-version-running
adb shell cmd statusbar expand-notifications; sleep 2; shot 08-notification; adb shell cmd statusbar collapse
adb logcat -d > "$OUT/logcat.txt"
if [ "$NOW" = "$WANT" ] && ! grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then echo "PASS updated to $NOW" > "$OUT/RESULT"; else echo "FAIL now=$NOW want=$WANT" > "$OUT/RESULT"; fi
cat "$OUT/RESULT"

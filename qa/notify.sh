#!/usr/bin/env bash
# Update notification test: an app with this code, numbered as an older version (1.0.2), sees the newest release
# and must post "YID PLUS update vX" with the release notes in the status bar; tapping it downloads and installs.
set -u
APK="$1"; WANT="$2"; PKG=com.yidplus.app; OUT=qa/out/notify
mkdir -p "$OUT"
log() { echo "[$(date +%T)] $*" | tee -a "$OUT/notify.log"; }
shot() { timeout 30 adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1"; }
ver() { adb shell dumpsys package $PKG | grep -m1 versionName | sed 's/.*versionName=//' | tr -d '\r '; }
find_xy() {
  timeout 30 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  timeout 30 adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  python3 - "$1" <<'EOF'
import re, sys
x = open('/tmp/ui.xml', encoding='utf-8').read(); want = sys.argv[1].lower()
for n in re.findall(r'<node [^>]*>', x):
    t = (re.search(r' text="([^"]*)"', n) or [None, ''])[1].lower()
    if t == want or (want.endswith('*') and t.startswith(want[:-1])):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            a, c, d, e = map(int, b.groups()); print((a + d) // 2, (c + e) // 2); break
EOF
}
adb wait-for-device
adb shell input keyevent 82
log "install: $(timeout 180 adb install -r "$APK" 2>&1 | tail -1)  version $(ver), newest release $WANT"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow
adb logcat -c
timeout 60 adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 15; shot 01-app-open-update-dialog
log "background check scheduled: $(adb shell dumpsys jobscheduler 2>/dev/null | grep -A3 "$PKG" | grep -m1 -oE 'period=[^ ]+|interval=[^ ]+' || echo '?')"
adb shell dumpsys notification --noredact 2>/dev/null | grep -A40 "pkg=$PKG" | grep -E "android.title=|android.text=|android.bigText=" | head -6 | tee -a "$OUT/notify.log"
adb shell input keyevent 3; sleep 2
adb shell cmd statusbar expand-notifications; sleep 3; shot 02-notification
for i in 1 2 3; do xy=$(find_xy "YID PLUS update v$WANT"); [ -n "$xy" ] && break; sleep 2; done
if [ -n "$xy" ]; then log "NOTIFICATION: found 'YID PLUS update v$WANT'"; NOTIF=1; else log "NOTIFICATION: not found"; NOTIF=0; fi
# expand it to show the notes, then tap it
[ -n "$xy" ] && { adb shell input swipe ${xy% *} ${xy#* } ${xy% *} $(( ${xy#* } + 300 )) 300; sleep 2; shot 03-notification-expanded; }
xy=$(find_xy "YID PLUS update v$WANT")
[ -n "$xy" ] && adb shell input tap $xy && log "tapped the notification"
sleep 4; shot 04-downloading
for i in $(seq 1 30); do xy=$(find_xy "Update"); [ -n "$xy" ] && break; xy=$(find_xy "Install"); [ -n "$xy" ] && break; sleep 2; done
shot 05-android-install-prompt
[ -n "$xy" ] && adb shell input tap $xy
for i in $(seq 1 40); do [ "$(ver)" = "$WANT" ] && break; sleep 2; done
NOW=$(ver); log "version after tapping the notification: $NOW"
timeout 60 adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 12; shot 06-new-version-running
adb logcat -d > "$OUT/logcat.txt"
CR=0; grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "Process: $PKG" && CR=1
[ "$CR" = 1 ] && grep -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60 | tee -a "$OUT/notify.log"
grep -E "YidUpdate|Updater|PackageInstaller" "$OUT/logcat.txt" | tail -30 >> "$OUT/notify.log"
rm -f "$OUT/logcat.txt"
if [ "$NOTIF" = 1 ] && [ "$NOW" = "$WANT" ] && [ "$CR" = 0 ]; then echo "PASS notification shown, tapped, updated to $NOW" > "$OUT/RESULT"; else echo "FAIL notif=$NOTIF now=$NOW crash=$CR" > "$OUT/RESULT"; fi
cat "$OUT/RESULT"

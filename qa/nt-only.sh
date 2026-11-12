#!/usr/bin/env bash
# Helper NT (2.0.3) fast check, run by .github/workflows/nt3.yml on branch feat-notify3 only (never merged):
# install, fixture account on, then only qa/fx-nt.sh (480x640 and 1080x2400). Same helpers as qa/native.sh.
set -u
APK="$1"; PKG=com.yidplus.app; OUT=qa/out; mkdir -p "$OUT"; FAIL=0
log() { echo "[$(date +%T)] $*" | tee -a "$OUT/qa.log"; }
shot() { timeout 30 adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1"; }
key() { timeout 10 adb shell input keyevent "$@"; sleep 0.8; }
view() { timeout 40 adb shell am start -a android.intent.action.VIEW -d "$1" $PKG >/dev/null 2>&1; log "open $1"; }
tab() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_TAB --es tab "$1" >/dev/null 2>&1; log "tab $1"; }
alive() { adb shell pidof $PKG >/dev/null 2>&1; }
check() { if ! alive; then why=$(timeout 30 adb logcat -d -t 4000 2>/dev/null | grep -E "FATAL EXCEPTION|ANR in $PKG" | tail -1); log "NOT RUNNING after $1: ${why:-no reason in log}"; FAIL=1; fi; }
size() { adb shell wm size "$1"; adb shell wm density "$2"; sleep 2; log "screen $1 @ $2 dpi"; }
restart() { adb shell am force-stop $PKG; sleep 1; timeout 60 adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep "${1:-6}"; }
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell input keyevent 82
log "install: $(timeout 180 adb install -r "$APK" 2>&1 | tail -1)"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
size 480x640 240
source qa/signedin.sh
fx true; restart 7; shot NT-00-app-signedin; check nt-start
source qa/fx-nt.sh; check fx-nt; [ "${NTFAIL:-0}" = 0 ] || FAIL=1
timeout 60 adb logcat -d > "$OUT/logcat.txt" 2>/dev/null
grep -E "FATAL EXCEPTION" -A8 "$OUT/logcat.txt" | head -40 >> "$OUT/qa.log"
adb shell wm size reset; adb shell wm density reset
log "RESULT: $([ $FAIL = 0 ] && echo PASS || echo FAIL)"
exit $FAIL

#!/usr/bin/env bash
# Production-seam negative test (CI, emulator). $1 = QA-flag APK (-Pqa=true), $2 = production-flag APK (default build).
# 1. QA APK: QA_FIXTURE on -> signed-in fixture chat list shows "Yossi Klein" (QA-positive; the fixture state is persisted).
# 2. Production APK installed over it (same signature, data kept), cold start: persisted fixture state must not load.
# 3. Production APK, explicit external QA_FIXTURE intent, then force-stop and cold start: still no fixture.
# Fixture/emulator evidence only. Nothing is sent to the real site by the fixture.
set -u
QAAPK="$1"; PRODAPK="$2"; PKG=com.yidplus.app; OUT=qa/out; mkdir -p "$OUT"; FAIL=0
log() { echo "[$(date +%T)] $*" | tee -a "$OUT/qa.log"; }
shot() { timeout 30 adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1"; }
texts() { timeout 20 adb shell uiautomator dump /sdcard/pn.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/pn.xml 2>/dev/null > "$OUT/$1.xml"; grep -oE ' text="[^"]+"' "$OUT/$1.xml" | head -30 | tr '\n' ' '; }
fxintent() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_FIXTURE --ez on "$1" >/dev/null 2>&1; sleep 4; log "sent QA_FIXTURE on=$1"; }
cold() { adb shell am force-stop $PKG; sleep 1; timeout 60 adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 7; }
chat() { timeout 40 adb shell am start -a android.intent.action.VIEW -d "https://yidplus.com/chat" $PKG >/dev/null 2>&1; sleep 6; }
adb wait-for-device; adb shell input keyevent 82
adb shell wm size 480x640; adb shell wm density 240; sleep 2
log "install QA APK: $(timeout 180 adb install -r "$QAAPK" 2>&1 | tail -1)"
adb shell dumpsys package $PKG | grep -E "versionName|versionCode" | head -2 | tee -a "$OUT/qa.log"
fxintent true; cold; chat; shot PN1-qa-positive; T=$(texts pn1); log "PN1 texts: $T"
if echo "$T" | grep -q "Yossi Klein"; then log "PN1 PASS: QA build reaches the fixture chat list"; else log "PN1 WRONG: QA build did not reach the fixture"; FAIL=1; fi
log "install PRODUCTION APK over it: $(timeout 180 adb install -r "$PRODAPK" 2>&1 | tail -1)"
cold; chat; shot PN2-prod-persisted; T=$(texts pn2); log "PN2 texts: $T"
if echo "$T" | grep -q "Yossi Klein"; then log "PN2 WRONG: production build loaded the persisted fixture"; FAIL=1; else log "PN2 PASS: no fixture content after cold start with persisted state"; fi
fxintent true; shot PN3-prod-intent; T=$(texts pn3); log "PN3 texts: $T"
if echo "$T" | grep -q "Yossi Klein"; then log "PN3 WRONG: production build acted on QA_FIXTURE"; FAIL=1; else log "PN3 PASS: QA_FIXTURE intent ignored (no fixture content)"; fi
cold; chat; shot PN4-prod-restart; T=$(texts pn4); log "PN4 texts: $T"
if echo "$T" | grep -q "Yossi Klein"; then log "PN4 WRONG: fixture state survived restart in production build"; FAIL=1; else log "PN4 PASS: still no fixture after force-stop and cold start"; fi
adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_TAB --es tab Profile --es qa_page demo >/dev/null 2>&1; sleep 4; shot PN5-prod-qa-page; log "PN5 qa_page texts: $(texts pn5)"
log "prodneg done FAIL=$FAIL"; exit $FAIL

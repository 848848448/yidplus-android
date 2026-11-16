#!/usr/bin/env bash
# Seam negative test (CI, emulator). $1 = QA-flag APK (-Pqa=true), $2 = the PRODUCTION APK (default build; in release.yml the exact file that gets published).
# QA APK is used only for positive controls and to leave persisted fixture state behind. It is never a release artifact.
# Result lines: PASS, WRONG (production acted on a QA seam), INVALID (a positive control did not show, so the matching negative proves nothing).
set -u
QAAPK="$1"; PRODAPK="$2"; PKG=com.yidplus.app; OUT=qa/out; mkdir -p "$OUT"; FAIL=0
log() { echo "[$(date +%T)] $*" | tee -a "$OUT/qa.log"; }
shot() { timeout 30 adb exec-out screencap -p > "$OUT/$1.png"; log "shot $1"; }
texts() { timeout 20 adb shell uiautomator dump /sdcard/pn.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/pn.xml 2>/dev/null > "$OUT/$1.xml"; grep -oE ' text="[^"]+"' "$OUT/$1.xml" | head -40 | tr '\n' ' '; }
cold() { adb shell am force-stop $PKG; sleep 1; timeout 60 adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 7; }
chat() { timeout 40 adb shell am start -a android.intent.action.VIEW -d "https://yidplus.com/chat" $PKG >/dev/null 2>&1; sleep 6; }
act() { timeout 40 adb shell am start -n $PKG/.MainActivity -a "$@" >/dev/null 2>&1; sleep 3; }
gate() { timeout 30 adb logcat -d -s YidQaGate:W 2>/dev/null | grep -c "$1"; }
nrec() { timeout 30 adb shell dumpsys notification --noredact 2>/dev/null | awk -v p="pkg=$PKG" '/NotificationRecord\(/ {on=($0 ~ p)} on {print}'; }
alive() { adb shell pidof $PKG >/dev/null 2>&1; }
adb wait-for-device; adb shell input keyevent 82
adb shell wm size 480x640; adb shell wm density 240; sleep 2
log "install QA APK: $(timeout 180 adb install -r "$QAAPK" 2>&1 | tail -1)"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell dumpsys package $PKG | grep -E "versionName|versionCode" | head -2 | tee -a "$OUT/qa.log"
# --- QA APK positive controls (fixture and emulator evidence only)
act com.yidplus.app.QA_FIXTURE --ez on true; cold; chat; shot PN1-qa-fixture; T=$(texts pn1); log "PN1 texts: $T"
FXOK=0; echo "$T" | grep -q "Yossi Klein" && FXOK=1
[ $FXOK = 1 ] && log "PN1 PASS: QA build reaches the fixture chat list" || { log "PN1 INVALID: QA build did not show the fixture, so PN2..PN4 prove nothing"; FAIL=1; }
act com.yidplus.app.OPEN_TAB --es tab Profile --es qa_page demo; shot PN1b-qa-page; T=$(texts pn1b); log "PN1b texts: $T"
QPOK=0; echo "$T" | grep -qi "Edit profile" && QPOK=1
[ $QPOK = 1 ] && log "PN1b PASS: QA build shows the qa_page demo settings (Edit profile)" || { log "PN1b INVALID: qa_page demo not visible in QA build, so the PN5 text check proves nothing"; FAIL=1; }
nrec > "$OUT/nrec-qa-before.txt"; B=$(md5sum < "$OUT/nrec-qa-before.txt")
adb shell am broadcast -a com.yidplus.app.QA_CHAT_NOTIFY -n $PKG/.ChatQaReceiver >/dev/null 2>&1; sleep 4
nrec > "$OUT/nrec-qa-after.txt"; A=$(md5sum < "$OUT/nrec-qa-after.txt")
log "PN1c records before: $(grep -c 'NotificationRecord(' "$OUT/nrec-qa-before.txt") after: $(grep -c 'NotificationRecord(' "$OUT/nrec-qa-after.txt")"
if [ "$A" != "$B" ]; then log "PN1c PASS: QA build broadcast changes the app's notification records (before/after content differs), so the PN6 comparison can detect the action"; else log "PN1c INVALID: QA broadcast left the notification records identical, so PN6 proves nothing"; FAIL=1; fi
# --- PRODUCTION APK (data kept, same signature)
log "install PRODUCTION APK over it: $(timeout 180 adb install -r "$PRODAPK" 2>&1 | tail -1)"
adb shell dumpsys package $PKG | grep -E "versionName|versionCode" | head -2 | tee -a "$OUT/qa.log"
adb logcat -c
cold; chat; shot PN2-prod-persisted; T=$(texts pn2); log "PN2 texts: $T"
if echo "$T" | grep -q "Yossi Klein"; then log "PN2 WRONG: production build loaded the persisted fixture"; FAIL=1; else log "PN2 PASS: no fixture content after cold start with persisted fixture state"; fi
for A in QA_FIXTURE QA_LOOK QA_DELAY QA_FEATURES QA_OPEN QA_DEDUPE QA_UPDATE_NOTIFY QA_PUSH_PROBE; do
  adb logcat -c
  case $A in QA_FIXTURE) act com.yidplus.app.$A --ez on true;; QA_DELAY) act com.yidplus.app.$A --es ms 20000;; QA_FEATURES) act com.yidplus.app.$A --ez all true;; QA_LOOK) act com.yidplus.app.$A --es color red --ez dark true;; *) act com.yidplus.app.$A;; esac
  N=$(gate "ignored QA action com.yidplus.app.$A")
  if [ "${N:-0}" -ge 1 ]; then log "PN3 PASS: explicit $A ignored (YidQaGate logged $N)"; else log "PN3 WRONG: $A not reported as ignored"; FAIL=1; fi
  alive || { log "PN3 WRONG: app not running after $A"; FAIL=1; }
done
shot PN3-prod-after-intents; T=$(texts pn3); log "PN3 texts: $T"
echo "$T" | grep -q "Yossi Klein" && { log "PN3 WRONG: fixture content after QA_FIXTURE in production"; FAIL=1; }
cold; chat; shot PN4-prod-restart; T=$(texts pn4); log "PN4 texts: $T"
if echo "$T" | grep -q "Yossi Klein"; then log "PN4 WRONG: fixture survived force-stop and cold start"; FAIL=1; else log "PN4 PASS: still no fixture after force-stop and cold start"; fi
adb logcat -c
act com.yidplus.app.OPEN_TAB --es tab Profile --es qa_page demo; shot PN5-prod-qa-page; T=$(texts pn5); log "PN5 texts: $T"
N=$(gate "ignored extra qa_page")
[ "${N:-0}" -ge 1 ] && log "PN5 PASS: qa_page extra reported ignored (YidQaGate $N)" || { log "PN5 WRONG: qa_page not reported ignored"; FAIL=1; }
if echo "$T" | grep -qi "Edit profile"; then log "PN5 WRONG: demo settings (Edit profile) visible in production"; FAIL=1; else [ $QPOK = 1 ] && log "PN5 PASS: no demo settings text (positive control PN1b showed it in the QA build)"; fi
nrec > "$OUT/nrec-prod-before.txt"; log "PN6 records present before the production broadcast (carried over from the QA run, provenance logged in nrec-prod-before.txt): $(grep -c 'NotificationRecord(' "$OUT/nrec-prod-before.txt")"
adb logcat -c
adb shell am broadcast -a com.yidplus.app.QA_CHAT_NOTIFY -n $PKG/.ChatQaReceiver >/dev/null 2>&1; sleep 4
nrec > "$OUT/nrec-prod-after.txt"; N=$(gate "ignored QA broadcast")
if [ "${N:-0}" -ge 1 ] && cmp -s "$OUT/nrec-prod-before.txt" "$OUT/nrec-prod-after.txt"; then log "PN6 PASS: QA_CHAT_NOTIFY ignored (YidQaGate $N) and the app's notification records are byte-identical before and after"; else log "PN6 WRONG: gate lines=$N or records changed (diff in nrec-prod-*.txt)"; diff "$OUT/nrec-prod-before.txt" "$OUT/nrec-prod-after.txt" | head -20 | tee -a "$OUT/qa.log"; FAIL=1; fi
adb shell am broadcast -a com.yidplus.app.QA_PICK -p $PKG >/dev/null 2>&1; adb shell am broadcast -a com.yidplus.app.QA_GESTURE -p $PKG >/dev/null 2>&1; sleep 2
alive && log "PN7 PASS (limited): QA_PICK and QA_GESTURE broadcasts delivered, app still running; these receivers only act when Fixture.on, which is false in production" || { log "PN7 WRONG: app not running after QA_PICK/QA_GESTURE"; FAIL=1; }
log "prodneg done FAIL=$FAIL"; echo "$([ $FAIL = 0 ] && echo PASS || echo FAIL)" > "$OUT/RESULT"; exit $FAIL

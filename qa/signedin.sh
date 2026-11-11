#!/usr/bin/env bash
# Signed-in screens, photographed with the QA fixture account (assets/qa/fixtures.json, shaped like the site's API).
# While it is on, the app never sends anything to yidplus.com for the account: sign-in-only reads come from the file,
# every write is answered locally. Sourced by qa/native.sh (needs its log/shot/key/view/tab/check/restart/size helpers).
fx() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_FIXTURE --ez on "$1" >/dev/null 2>&1; sleep 4; log "fixture $1"; }
fx_block() {
  log "---- Signed in (QA fixture account) ----"
  fx true
  restart 7; shot F01-home-signedin; check fx-home
  view "https://yidplus.com/chat"; sleep 5; shot F02-chats-list
  timeout 20 adb shell uiautomator dump /sdcard/fx.xml >/dev/null 2>&1
  log "chat list texts: $(timeout 20 adb shell cat /sdcard/fx.xml 2>/dev/null | grep -oE ' text="[^"]+"' | head -40 | tr '\n' ' ')"
  key 23; sleep 6; shot F03-chat-dm; adb shell input swipe 240 260 240 580 300; sleep 1; shot F03b-chat-dm-up; key 4; sleep 2; check fx-dm
  view "https://yidplus.com/chat"; sleep 4; key 20; key 20; key 23; sleep 6; shot F04-chat-group
  adb shell input swipe 240 260 240 580 300; sleep 1; shot F04b-group-up; adb shell input swipe 240 260 240 580 300; sleep 1; shot F04c-group-top; check fx-group
  adb shell input swipe 240 400 240 400 900; sleep 2; shot F05-message-actions; key 4; sleep 1; key 4; sleep 2
  timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_NOTIFS >/dev/null 2>&1; sleep 4; shot F06-notifications; key 4; sleep 1
  tab Home; sleep 3; shot F07-home-header
  view "https://yidplus.com/shorts"; sleep 9; shot F08-shorts-signedin; check fx-shorts
  tab Profile; sleep 3; shot F09-profile-signedin; for i in 1 2 3 4 5 6 7 8; do key 20; done; sleep 1; shot F09b-profile-scrolled
  for i in 1 2 3 4 5 6 7 8; do key 19; done
  adb shell cmd uimode night yes; sleep 3; view "https://yidplus.com/chat"; sleep 4; shot F10-chats-dark; key 23; sleep 5; shot F11-chat-dark; key 4
  adb shell cmd uimode night no; sleep 3; check fx-dark
  size 1080x2400 420; restart 6; view "https://yidplus.com/chat"; sleep 4; shot F12-phone-chats; key 23; sleep 5; shot F13-phone-chat; key 4
  size 480x640 240
  fx false; restart 5; check fx-off
}
fx_finish() {
  timeout 60 adb logcat -d -t 30000 > "$OUT/logcat.txt"
  if grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "Process: $PKG"; then
    log "CRASH found in logcat"; grep -A 30 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80 | tee -a "$OUT/qa.log"; FAIL=1
  fi
  if [ "$FAIL" = 0 ]; then echo "PASS" > "$OUT/RESULT"; else echo "FAIL" > "$OUT/RESULT"; fi
  cat "$OUT/RESULT"; exit 0
}

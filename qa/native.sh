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
source qa/signedin.sh
# Quick run (only on a test branch that carries qa/QUICK): just the signed-in fixture screens.
if [ -f qa/QUICK ] || [ -n "${QA_FX:-}" ]; then fx_block; fx_finish; fi

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

# ---- Full-screen gallery: swipe to the next photo, double-tap zoom, drag down to close ----
uitext() { timeout 30 adb shell uiautomator dump /sdcard/g.xml >/dev/null 2>&1; timeout 30 adb shell cat /sdcard/g.xml 2>/dev/null | grep -oE 'text="[0-9]+ / [0-9]+"' | head -1; }
timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHANNEL --es username ZemelVIN --es title ZemelVIN >/dev/null 2>&1; sleep 7
key 20; key 23; sleep 4; G1=$(uitext); GW=$(timeout 30 adb shell cat /sdcard/g.xml 2>/dev/null | grep -c "Fetching posts"); shot 06a-gallery-open; log "gallery opened: ${G1:-no counter}"
adb shell input swipe 420 330 60 330 150; sleep 3; G2=$(uitext); shot 06b-gallery-swiped; log "after swipe left: ${G2:-no counter}"
timeout 20 adb shell 'input tap 240 330 & sleep 0.12; input tap 240 330; wait'; sleep 2; shot 06c-gallery-zoomed
adb shell input swipe 420 330 60 330 150; sleep 2; G3=$(uitext); log "zoomed + swipe (should stay): ${G3:-no counter}"
timeout 20 adb shell 'input tap 240 330 & sleep 0.12; input tap 240 330; wait'; sleep 2
key 22; sleep 2; G4=$(uitext); log "keypad RIGHT: ${G4:-no counter}"
adb shell input swipe 240 200 240 600 250; sleep 3; G5=$(uitext); shot 06d-gallery-closed; log "after drag down: ${G5:-closed}"
if [ -n "$G1" ] && [ -n "$G2" ] && [ "$G1" != "$G2" ] && [ "$G2" = "$G3" ] && [ "$G4" != "$G3" ] && [ -z "$G5" ]; then log "GALLERY PASS"; elif [ -z "$G1" ] && [ "${GW:-0}" != 0 ]; then log "GALLERY SKIP: the live channel had no posts yet (site still fetching); the gallery is tested with fixture photos in qa/fx-lk.sh"; else log "GALLERY FAIL"; FAIL=1; fi
check gallery

# ---- Chats (guest: featured groups) ----
view "https://yidplus.com/chat"; sleep 6; shot 08-chats
focus "chats opened"
key 23; sleep 7; shot 09-chat-room; focus "OK (open chat)"
key 4; sleep 1; shot 10-chats-back
check chats

# ==== Helper A (Chats 2.0.1) QA block: begin ====
# Signed out (guest): the chat list, search, a featured group's conversation, its ⋮ menu, its info page, and the long-press actions.
# Needs sign-in, so not shown here: New message / group / channel, saved messages, composer (emoji, attach, mic), forward, polls, calls.
view "https://yidplus.com/chat"; sleep 5; shot A0-chats-list
adb shell input tap 447 78; sleep 2; shot A1-chats-search
adb shell input text "Links"; sleep 5; key 4; sleep 1; shot A2-chats-search-results; check chats-search
key 4; sleep 2; shot A2b-search-closed-by-back
view "https://yidplus.com/chat"; sleep 5; key 23; sleep 7; shot A3-chat-room-bubbles; focus "OK on the first chat"
adb shell input tap 447 78; sleep 2; shot A4-chat-menu
key 4; sleep 1
adb shell input tap 200 78; sleep 5; shot A5-chat-info; check chat-info
key 4; sleep 3; shot A5b-back-in-chat
adb shell input swipe 240 420 240 420 900; sleep 2; shot A6-message-actions
key 4; sleep 1; key 4; sleep 2; shot A7-back-to-list; check chats-extras
# Chat notification like WhatsApp (sample posted by the QA receiver; a real one needs a signed-in account with new messages):
adb shell am broadcast -a com.yidplus.app.QA_CHAT_NOTIFY -n com.yidplus.app/.ChatQaReceiver >/dev/null 2>&1; sleep 3
adb shell cmd statusbar expand-notifications; sleep 3; shot A8-notification-chat
timeout 30 adb shell uiautomator dump /sdcard/n.xml >/dev/null 2>&1
EB=$(timeout 20 adb shell cat /sdcard/n.xml 2>/dev/null | grep -oiE 'resource-id="android:id/expand_button"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 | grep -oE '[0-9]+' | tail -4 | tr '\n' ' ')
HASREPLY=$(timeout 20 adb shell cat /sdcard/n.xml 2>/dev/null | grep -ciE 'text="reply"')
if [ -n "$EB" ] && [ "${HASREPLY:-0}" = "0" ]; then read X1 Y1 X2 Y2 <<< "$EB"; adb shell input tap $(( (X1 + X2) / 2 )) $(( (Y1 + Y2) / 2 )); sleep 2; fi
shot A8b-notification-expanded; log "expand button: ${EB:-not found}"
timeout 30 adb shell uiautomator dump /sdcard/n.xml >/dev/null 2>&1
log "stacked messages shown: $(timeout 20 adb shell cat /sdcard/n.xml 2>/dev/null | grep -oE 'text="(Moshe Klein|Yossi Weiss|I.ll be there at 8|📷 Photo)"' | sort | uniq -c | tr '\n' ' ')"
RB=$(timeout 20 adb shell cat /sdcard/n.xml 2>/dev/null | grep -oiE 'text="reply"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 | grep -oE '[0-9]+' | tail -4 | tr '\n' ' ')
log "notification Reply button: ${RB:-not found}"
if [ -n "$RB" ]; then read X1 Y1 X2 Y2 <<< "$RB"; adb shell input tap $(( (X1 + X2) / 2 )) $(( (Y1 + Y2) / 2 )); sleep 2; adb shell input text "Gut"; sleep 1; shot A9-notification-reply-box
  timeout 30 adb shell uiautomator dump /sdcard/n.xml >/dev/null 2>&1; log "reply box: $(timeout 20 adb shell cat /sdcard/n.xml 2>/dev/null | grep -oE 'class="android.widget.EditText"[^>]*' | grep -oE 'text="[^"]*"|hint="[^"]*"' | head -2 | tr '\n' ' ')"; fi
adb shell input keyevent 4; sleep 1; adb shell input keyevent 4; sleep 1; adb shell cmd statusbar collapse; adb shell input keyevent 3; sleep 1; check chat-notification
# ==== Helper A (Chats 2.0.1) QA block: end ====

# ---- Shorts ----
view "https://yidplus.com/shorts"; sleep 10; shot 11-shorts-hint
key 7; sleep 6; shot 12-shorts-playing
key 20; sleep 7; shot 13-shorts-next; log "DOWN = next video"
for i in 1 2 3; do adb shell input swipe 240 430 240 130 120; sleep 4; done; shot 13b-shorts-swiped; log "3 light swipes up = 3 videos on"
# ---- Shorts: no video twice (same id, same file, or the same video re-posted as a new file) in the first 200 ----
timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_DEDUPE >/dev/null 2>&1
DD=""; for i in $(seq 1 60); do DD=$(timeout 10 adb logcat -d -s YidDedupe:I 2>/dev/null | grep -o 'checked.*' | tail -1); [ -n "$DD" ] && break; sleep 3; done
log "dedupe: ${DD:-no answer in 180 s}"
echo "$DD" | grep -q "same id 0, same file 0, same picture+length 0" && echo "$DD" | grep -q "checked 200" && log "DEDUPE PASS" || { log "DEDUPE FAIL"; FAIL=1; }
key 23; sleep 1; shot 14-shorts-paused; key 23
key 8; sleep 1; shot 15-shorts-liked; log "key 1 = like"
key 10; sleep 5; shot 16-shorts-comments; log "key 3 = comments"
key 4; sleep 1
key 17; sleep 1; key 17; sleep 8; shot 17-shorts-uploads; log "* * = Uploads tab"
key 17; sleep 1; key 17; sleep 1
check shorts
key 4; sleep 2; focus "BACK from Shorts"; shot 18-after-shorts-back

# >>>>> helper B (feat-social): channels, header, shorts extras >>>>>
# Signed out, like the rest of this script: notifications, follow, join, reactions, comments and upload need sign-in and
# show their sign-in state here.
tapui() { # tap the first thing on screen whose text or content-desc is exactly $1
  local want="$1" b
  timeout 30 adb shell uiautomator dump /sdcard/b.xml >/dev/null 2>&1
  b=$(timeout 20 adb shell cat /sdcard/b.xml 2>/dev/null | tr '>' '\n' | grep -E "(text|content-desc)=\"$want\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -z "$b" ]; then log "tapui: '$want' not on screen"; return 0; fi
  set -- $b; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; log "tapui '$want'"
}
tab Home; sleep 4; shot B01-channels-telegram
tapui "Members"; sleep 5; shot B02-channels-members
tapui "Recent"; sleep 3; shot B03-channels-recent
tapui "Members"; sleep 3
timeout 10 adb shell input tap 240 250; sleep 6; shot B04-member-channel; focus "tap a member channel"
tapui "Shorts \([0-9]+\)"; sleep 1; shot B05-member-channel-shorts
tab Home; sleep 2
tapui "Telegram"; sleep 3
timeout 10 adb shell input tap 240 250; sleep 6; shot B06-tg-channel-joinbar
tapui "More"; sleep 1; shot B07-tg-channel-menu
tapui "Channel info"; sleep 4; shot B08-tg-channel-info
tab Home; sleep 2
tapui "Search"; sleep 2; shot B09-channels-search
timeout 10 adb shell input text "news"; sleep 3; shot B10-channels-search-typed
tapui "Search all of YID PLUS"; sleep 5; shot B11-global-search
tapui "Telegram"; sleep 2; shot B12-global-search-telegram
key 4; sleep 1; key 4; sleep 1
tab Home; sleep 2
tapui "Profile"; sleep 1; shot B13-avatar-menu
key 4; sleep 1
tab Home; sleep 1
check social-channels
view "https://yidplus.com/shorts"; sleep 8
key 7; sleep 2
key 15; sleep 4; shot B14-shorts-creator-page; log "key 8 = creator page"
view "https://yidplus.com/shorts"; sleep 4
key 16; sleep 3; shot B15-shorts-search; log "key 9 = search"
key 4; sleep 1; key 4; sleep 1
view "https://yidplus.com/shorts"; sleep 4
key 14; sleep 3; shot B16-shorts-upload; log "key 7 = upload (signed out: sign-in prompt)"
key 4; sleep 2
check social-shorts
# <<<<< helper B (feat-social) <<<<<

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
for i in 1 2 3 4 5 6; do key 19; done
timeout 30 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.CHECK_UPDATE >/dev/null; sleep 2.5; shot 26-check-updates
check profile

# ==== [feat-settings] Settings / profile / account / admin screens (helper C) — keep this block together ====
# QA runs signed out, so the signed-in screens are photographed with a sample profile (the qa_page "demo" extra;
# nothing is sent to the site). Real data (your own profile, devices, blocked list, admin stats) needs a sign-in.
qa_page() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_TAB --es tab Profile --es qa_page "$1" >/dev/null 2>&1; sleep "${2:-3}"; log "qa_page $1"; }
qa_page demo; shot set40-settings-signed-in
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do key 20; done; focus "DOWN x12 in settings"; shot set40b-settings-scrolled
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16; do key 19; done; key 23; sleep 2; focus "OK on first row"; shot set40c-dpad-opened; key 4; sleep 1
qa_page demo:EditProfile; shot set41-edit-profile; key 4
qa_page demo:Privacy; shot set42-privacy; key 4
qa_page demo:Security; shot set43-security; key 4
qa_page demo:NotifPrefs; shot set44-notification-prefs; key 4
qa_page demo:Blocked; shot set45-blocked; key 4
qa_page demo:FollowRequests; shot set46-follow-requests; key 4
qa_page demo:Feedback; shot set47-feedback; key 4
qa_page demo:About; shot set48-about; key 4
qa_page demo:Admin 4; shot set49-admin; for i in 1 2 3 4 5 6; do key 20; done; shot set49b-admin-features; key 4
adb shell cmd uimode night yes; sleep 3
qa_page demo; shot set50-settings-dark
qa_page demo:EditProfile; shot set51-edit-profile-dark; key 4
qa_page demo:Admin 4; shot set52-admin-dark; key 4
adb shell cmd uimode night no; sleep 3
qa_page off 2; tab Profile; sleep 2; shot set53-settings-signed-out-again
check "settings screens (feat-settings)"
# ==== [/feat-settings] ====

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
tab Home; sleep 4
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

if [ "${QA_SKIP_FX:-}" = 1 ]; then log "signed-in fixture block skipped (QA_SKIP_FX=1; run in full on the feature branch)"; else fx_block; fi

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
# A leg must not report PASS while its own log says WRONG (run 43399597399: fx4 logged "CL WRONG: only 0 rows" and still printed PASS). MISSING lines are coverage gaps: counted and listed, kept apart from PASS/FAIL.
WRONGS=$(grep -aEc " [A-Z]+ WRONG" "$OUT/qa.log" || true)
grep -aE " [A-Z]+ MISSING" "$OUT/qa.log" > "$OUT/GAPS.txt" || true
if [ "${WRONGS:-0}" -gt 0 ]; then log "RESULT: $WRONGS WRONG line(s) in this leg, forcing FAIL"; FAIL=1; fi
log "coverage gaps (MISSING lines): $(wc -l < "$OUT/GAPS.txt")"
if [ "$FAIL" = 0 ]; then echo "PASS" > "$OUT/RESULT"; else echo "FAIL" > "$OUT/RESULT"; fi
cat "$OUT/RESULT"
exit "$FAIL"

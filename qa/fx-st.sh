# ==== Helper ST (2.1): Profile / Settings / account / Saved / History — signed in with the QA fixture account ====
# Sourced by qa/signedin.sh (fx_block). Shots ST-NN-what. Every write is answered locally and logged (YidFx).
stq() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_TAB --es tab Profile --es qa_page "x:$1" >/dev/null 2>&1; sleep "${2:-4}"; log "st page $1"; }
sto() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_OPEN --es page "$1" >/dev/null 2>&1; sleep "${2:-4}"; log "st open $1"; }
stswipe() { timeout 10 adb shell input swipe 240 420 240 140 400; sleep 1.2; }
sttop() { local i; for i in 1 2 3 4 5 6 7 8; do timeout 10 adb shell input swipe 240 190 240 500 200; sleep 0.3; done; sleep 0.8; }
stlog() { timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -F "$1" | tail -1 | sed 's/^.*YidFx[^:]*: //' ; }
stseen() { timeout 20 adb shell cat /sdcard/st.xml 2>/dev/null | grep -oE '(text|content-desc)="[^"]+"' | cut -d'"' -f2 | head -40 | tr '\n' '|' | cut -c1-600; }
stdump() { timeout 30 adb shell uiautomator dump /sdcard/st.xml >/dev/null 2>&1; log "ST screen $1: $(stseen)"; }
stfind() {
  local i b
  for i in 1 2 3 4 5 6 7 8 9 10; do
    timeout 30 adb shell uiautomator dump /sdcard/st.xml >/dev/null 2>&1
    b=$(timeout 20 adb shell cat /sdcard/st.xml 2>/dev/null | tr '>' '\n' | grep -E "(text|content-desc)=\"([^\"]* )?$1[\",]" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
    if [ -n "$b" ]; then set -- $b; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; log "stfind tapped after $((i-1)) swipes"; return 0; fi
    stswipe
  done
  log "stfind: '$1' not found; screen has: $(stseen)"
}
strec() { timeout 5 adb shell rm -f /sdcard/$1.mp4 >/dev/null 2>&1; (timeout 45 adb shell screenrecord --size 480x640 --bit-rate 2000000 --time-limit ${2:-30} /sdcard/$1.mp4 >/dev/null 2>&1 &); sleep 1.5; log "rec start $1"; }
strecend() { timeout 10 adb shell pkill -INT screenrecord >/dev/null 2>&1; sleep 3; timeout 60 adb pull /sdcard/$1.mp4 "$OUT/$1.mp4" >/dev/null 2>&1; log "video $1: $(stat -c %s "$OUT/$1.mp4" 2>/dev/null || echo missing) bytes"; }
stproof() { local l; l=$(stlog "$2"); if [ -n "$l" ]; then log "PROOF $1: $l"; else log "PROOF $1: (no request logged for '$2')"; fi; }
for k in animator_duration_scale transition_animation_scale window_animation_scale; do timeout 10 adb shell settings put global $k 0 >/dev/null 2>&1; done

log "---- ST: Settings tab, every screen (signed in) ----"
tab Profile; sleep 4; sttop; shot ST-01-settings-top; stdump settings-top
for n in 02 03 04 05 06 07 08 09 10; do stswipe; shot ST-$n-settings-scroll; done
stdump settings-bottom
for p in EditProfile Privacy Security NotifPrefs Blocked FollowRequests MutedChats Feedback About ChatSettings; do
  stq $p 5; shot ST-P-$p; stdump $p; stswipe; shot ST-P-$p-2; stswipe; shot ST-P-$p-3; key 4; sleep 1
done
sto saved 5; shot ST-S-saved; stdump saved; key 4; sleep 1
sto history 5; shot ST-S-history; stdump history; key 4; sleep 1
check "ST before set"

log "---- ST 2.1 after: new pages ----"
timeout 10 adb logcat -c >/dev/null 2>&1
tab Profile; sleep 4; sttop; shot ST-20-settings-compact; stdump settings-compact
stswipe; shot ST-21-settings-2; stswipe; shot ST-22-settings-3; stswipe; shot ST-23-settings-4; stswipe; shot ST-24-settings-5; stswipe; shot ST-25-settings-6
# Download my data: 6 sections of /api/my-export, saved to Downloads
stfind "Download my data"; sleep 6; shot ST-26-export-toast; stproof export "EXPORT sections"
# Text size toast
sttop; stfind "Text size"; sleep 1
# Other people's profiles are HM's page (HmProfilePage); ST keeps only its own lists.
stq MyProfile 6; shot ST-38-my-profile; stswipe; shot ST-39-my-profile-2; key 4; sleep 1
stq Followers 5; shot ST-40-my-followers; stdump my-followers; stfind "Remove"; sleep 1; shot ST-41-remove-follower-confirm; stfind "Remove"; sleep 2; stproof remove-follower "DELETE /api/follows?remove=1"; key 4; sleep 1
# Help & Support (guided bot, self-block, live chat with the team)
stq Support 5; shot ST-42-support-start; stdump support
stfind "A problem with Chat"; sleep 1; shot ST-42b-support-chat
stfind "Block myself from Chat"; sleep 1; shot ST-43-support-selfblock-confirm
stfind "Yes, block me"; sleep 2; shot ST-44-support-selfblocked; stproof selfblock "POST /api/self-block"
key 4; sleep 1
stq Support 5; stfind "Continue your conversation"; sleep 3; shot ST-45-support-live; stdump support-live
timeout 10 adb shell input text "Thanks%sit%sworks%snow" >/dev/null 2>&1; sleep 1; shot ST-46-support-typed
stfind "Send"; sleep 2; shot ST-47-support-sent; stproof support-send "POST /api/support-chats"
key 4; sleep 1; key 4; sleep 1
stq Support 5; stfind "I can't sign in"; sleep 1; stfind "I forgot my password"; sleep 1; shot ST-48-support-reset
key 4; sleep 1
# Sign-up / forgot / reset (native, were web pages)
stq Register 5; shot ST-50-register; stdump register; stswipe; shot ST-51-register-2; key 4; sleep 1
stq Forgot 5; shot ST-52-forgot; stfind "Send reset link"; sleep 2; shot ST-53-forgot-sent; stproof forgot "request-password-reset"; key 4; sleep 1
stq Reset 5; shot ST-54-reset; key 4; sleep 1
# App lock with a PIN
stq AppLock 5; shot ST-55-applock; stfind "Lock with a PIN"; sleep 1; shot ST-56-pin-new
for d in 8 9 10 11; do key $d; sleep 0.4; done; sleep 1; shot ST-57-pin-confirm
for d in 8 9 10 11; do key $d; sleep 0.4; done; sleep 1; shot ST-58-applock-on; stproof lock "LOCK on"
stfind "Lock now"; sleep 1; shot ST-59-locked; for d in 8 9 10 11; do key $d; sleep 0.4; done; sleep 1
stfind "Lock with a PIN"; sleep 1; stproof lock-off "LOCK off"; key 4; sleep 1
# Security: recent sign-ins
stq Security 5; stswipe; stswipe; shot ST-60-recent-signins; stdump security; key 4; sleep 1
# Saved: songs now open the music player
sto saved 5; stfind "מזמור לדוד"; sleep 3; shot ST-61-saved-song; stproof saved-song "PLAY music"; key 4; sleep 1
# Settings search (WhatsApp style) and Privacy with Blocked + App lock
stq SettingsSearch 4; shot ST-62-settings-search-empty; timeout 10 adb shell input text "pass" >/dev/null 2>&1; sleep 1.5; shot ST-63-settings-search-pass; stdump settings-search; key 4; sleep 0.8; key 4; sleep 1
stq Privacy 4; shot ST-64-privacy-blocked-applock; key 4; sleep 1
log "---- ST videos ----"
# 1) D-pad only: Settings top to bottom, open App lock and back, open Help & Support and pick an answer
tab Profile; sleep 3; sttop
strec ST-flow-dpad 30
for n in 1 2 3 4 5 6 7 8 9 10 11 12; do key 20; sleep 0.6; done
key 23; sleep 2; key 4; sleep 1.5
stq Support 4; key 20; sleep 0.7; key 20; sleep 0.7; key 23; sleep 2; key 20; sleep 0.7; key 23; sleep 2
strecend ST-flow-dpad
key 4; sleep 1
# 2) touch: compact settings, followers, export, PIN lock on and off
tab Profile; sleep 3; sttop
strec ST-flow-touch 30
stswipe; stswipe; stfind "Followers and following"; sleep 2; key 4; sleep 1
stfind "Download my data"; sleep 3
stq AppLock 3; stfind "Lock with a PIN"; for d in 8 9 10 11; do key $d; sleep 0.3; done; sleep 0.8; for d in 8 9 10 11; do key $d; sleep 0.3; done; sleep 1.5
stfind "Lock with a PIN"; sleep 1.5
strecend ST-flow-touch
key 4; sleep 1
log "ST unfixtured GETs: $(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -o 'GET-unfixtured [^ ]*' | sort | uniq -c | sort -rn | head -40 | tr '\n' ';')"
check "ST after set"

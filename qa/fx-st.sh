# ==== Helper ST (2.1): Profile / Settings / account / Saved / History — signed in with the QA fixture account ====
# Sourced by qa/signedin.sh (fx_block). Shots ST-NN-what. Every write is answered locally and logged (YidFx).
stq() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_TAB --es tab Profile --es qa_page "x:$1" >/dev/null 2>&1; sleep "${2:-4}"; log "st page $1"; }
sto() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_OPEN --es page "$1" >/dev/null 2>&1; sleep "${2:-4}"; log "st open $1"; }
stswipe() { timeout 10 adb shell input swipe 240 470 240 190 400; sleep 1.2; }
sttop() { local i; for i in 1 2 3 4 5 6 7 8; do timeout 10 adb shell input swipe 240 190 240 500 200; sleep 0.3; done; sleep 0.8; }
stlog() { timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -F "$1" | tail -1 | sed 's/^.*YidFx[^:]*: //' ; }
stseen() { timeout 20 adb shell cat /sdcard/st.xml 2>/dev/null | grep -oE '(text|content-desc)="[^"]+"' | cut -d'"' -f2 | head -40 | tr '\n' '|' | cut -c1-600; }
stdump() { timeout 30 adb shell uiautomator dump /sdcard/st.xml >/dev/null 2>&1; log "ST screen $1: $(stseen)"; }
stfind() {
  local i b
  for i in 1 2 3 4 5 6 7 8 9 10; do
    timeout 30 adb shell uiautomator dump /sdcard/st.xml >/dev/null 2>&1
    b=$(timeout 20 adb shell cat /sdcard/st.xml 2>/dev/null | tr '>' '\n' | grep -E "(text|content-desc)=\"$1[\",]" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
    if [ -n "$b" ]; then set -- $b; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; log "stfind tapped after $((i-1)) swipes"; return 0; fi
    stswipe
  done
  log "stfind: '$1' not found; screen has: $(stseen)"
}
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

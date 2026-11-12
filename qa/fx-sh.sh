# ---- SH: Shorts 2.1 (helper SH), signed in with the fixture account, 480x640 ----
# Every Shorts GET is answered from assets/qa/fixtures-sh.json (no live /api reads). Shots SH-NN-what.
sh_mark() { timeout 10 adb shell log -t YidFx "SH-STEP $1" >/dev/null 2>&1; log "sh step $1"; }
sh_tapui() {
  local want="$1" bb="" n
  SH_OK=0
  for n in 1 2 3; do
    timeout 30 adb shell uiautomator dump /sdcard/sh.xml >/dev/null 2>&1
    bb=$(timeout 20 adb shell cat /sdcard/sh.xml 2>/dev/null | tr '>' '\n' | grep -E "(text|content-desc)=\"$want\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
    [ -n "$bb" ] && break; timeout 5 adb shell rm -f /sdcard/sh.xml >/dev/null 2>&1; sleep 1
  done
  if [ -z "$bb" ]; then log "sh_tapui: '$want' not on screen"; return 0; fi
  set -- $bb; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; SH_OK=1; log "sh_tapui '$want'"
}
sh_texts() { timeout 30 adb shell uiautomator dump /sdcard/sh.xml >/dev/null 2>&1; log "texts ($1): $(timeout 20 adb shell cat /sdcard/sh.xml 2>/dev/null | grep -oE '(text|content-desc)="[^"]+"' | head -${2:-40} | tr '\n' ' ')"; }
sh_dev() { timeout 10 adb shell getevent -pl 2>/dev/null | tr -d '\r' | awk '/add device/{d=$4} /ABS_MT_POSITION_X/{print d; exit}'; }
sh_max() { timeout 10 adb shell getevent -pl "$1" 2>/dev/null | tr -d '\r' | grep "$2" | grep -oE 'max [0-9]+' | head -1 | awk '{print $2}'; }
# a real double-tap (both touches inside 0.3 s)
sh_dtap() {
  local d mx my c=""; d=$(sh_dev); [ -z "$d" ] && { timeout 5 adb shell "input tap $1 $2; input tap $1 $2"; return 0; }
  mx=$(sh_max "$d" ABS_MT_POSITION_X); my=$(sh_max "$d" ABS_MT_POSITION_Y); mx=${mx:-479}; my=${my:-639}
  local x=$(( $1 * mx / 479 )) y=$(( $2 * my / 639 )) k
  # two touches ~120 ms apart (a finger's double-tap), all inside one adb shell so no round trip sits between them
  for k in 501 502; do
    c="$c sendevent $d 3 47 0; sendevent $d 3 57 $k; sendevent $d 3 53 $x; sendevent $d 3 54 $y; sendevent $d 3 58 60; sendevent $d 1 330 1; sendevent $d 0 0 0; sleep 0.03;"
    c="$c sendevent $d 3 57 -1; sendevent $d 1 330 0; sendevent $d 0 0 0;"
  done
  timeout 20 adb shell "$c"; log "double-tap at $1,$2"
}
# Back only while "$1" is still on screen (never one Back too many: that leaves the app)
sh_back_while() { local n; for n in 1 2 3; do timeout 30 adb shell uiautomator dump /sdcard/sh.xml >/dev/null 2>&1
  timeout 20 adb shell cat /sdcard/sh.xml 2>/dev/null | grep -qE "(text|content-desc)=\"$1\"" || return 0; key 4; sleep 1; done; }
# the video page at 480x640 runs from y≈50 to y≈460 (the app's bottom bar starts at ~465): swipe inside it
sh_ime_hide() { timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -q "mInputShown=true" && { key 4; sleep 1; log "keyboard hidden"; }; return 0; }
sh_up() { timeout 10 adb shell input swipe 200 420 200 170 120; sleep "${1:-4}"; }

size 480x640 240
# sendevent (real double-tap) needs a root shell on the emulator; screen videos start after this
timeout 30 adb root >/dev/null 2>&1; sleep 3; timeout 30 adb wait-for-device; log "adb root: $(timeout 10 adb shell id -u 2>/dev/null)"
# 1. For You: first video, the swipe hint, the rail (avatar+follow, like, comment, save, share, sound, more), chips
# video 1 (touch): the first ~30 s of the flow below
timeout 10 adb shell rm -f /sdcard/SH-flow.mp4 /sdcard/SH-dpad.mp4 >/dev/null 2>&1
(timeout 40 adb shell screenrecord --size 480x640 --bit-rate 2000000 --time-limit 30 /sdcard/SH-flow.mp4 >/dev/null 2>&1 &)
sh_mark foryou; view "https://yidplus.com/shorts"; sleep 4; shot SH-01-keypad-help
key 21; sleep 5; shot SH-02-foryou; sh_texts foryou 60
# 2. one tap = sound on at once (muted start); next tap pauses; a tap on paused plays
# sound starts on here (the phone's own setting); key 2 = sound off, then the first tap turns it on (does not pause)
sh_mark tap; key 9; sleep 1; shot SH-03-muted-pill; timeout 5 adb shell input tap 200 300; sleep 1; shot SH-03b-tap-sound-on
timeout 5 adb shell input tap 200 300; sleep 1; shot SH-04-tap-paused
timeout 5 adb shell input tap 200 300; sleep 2; shot SH-04b-tap-plays
# 3. double-tap = like + heart
sh_mark dtap-like; sh_dtap 200 300; sleep 0.3; shot SH-05-double-tap-heart; sleep 2; shot SH-06-liked
# 4. comments over the video (3/4 height), post one (optimistic), close
sh_mark comments; sh_tapui "Comment"; sleep 3; shot SH-07-comments
sh_tapui "Add a comment"; sleep 1; adb shell input text "Shkoyach%sgevaldig"; sleep 1; shot SH-08-comment-typing
sh_mark post-comment; sh_tapui "Post"; sleep 0.3; shot SH-08b-comment-posting; sleep 2; sh_ime_hide; sleep 1; shot SH-09-comment-posted
sh_back_while "Close comments"; sleep 2
# 5. options sheet: speeds, auto-scroll, not interested, hide creator, copy link, saved, reset; 1.5x shows a badge
sh_mark options; sh_tapui "More options"; sleep 2; shot SH-10-options
sh_tapui "1.5×"; sleep 1; shot SH-11-options-speed; key 4; sleep 2; shot SH-12-speed-badge
sh_tapui "More options"; sleep 2; sh_tapui "Normal"; key 4; sleep 1
# 6. save, share sheet (subject + text), follow from the rail
sh_mark save; sh_tapui "Save"; sleep 2; shot SH-13-saved-toast
sh_mark share; sh_tapui "Share"; sleep 3; shot SH-14-share-sheet
# close the system share sheet with one Back only if it is in front (a second Back would leave Shorts)
timeout 10 adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus' | grep -qi 'chooser\|resolver\|intentresolver' && { key 4; sleep 2; }
timeout 10 adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus' | grep -q 'com.yidplus.app' || { view "https://yidplus.com/shorts"; sleep 4; log "share: back in Shorts by link"; }
sh_mark follow-rail; sh_tapui "Follow"; sleep 2; shot SH-15-followed-from-rail
# 7. swipe on; creator name -> that creator's own videos (chips: Back, name, Follow)
sh_mark swipe; sh_up; shot SH-16-swiped; sh_up; shot SH-17-swiped2
sh_mark creator; sh_tapui "Creator videos"; sleep 4; shot SH-18-creator-videos; sh_texts creator
sh_tapui "‹ Back"; sleep 3; shot SH-19-creator-back
# 8. topics (scrolling chips)
sh_mark topic; sh_tapui "News"; sleep 4; shot SH-20-topic-news; sh_tapui "All"; sleep 3
# 9. live scrub with thumb and time
sh_mark scrub; (timeout 10 adb shell input swipe 30 450 330 450 3500 &) ; sleep 2; shot SH-21-scrubbing; sleep 3
# 10. Following: who I follow (1 from the rail), the creators list, search, Videos, Back keeps the list, Follow more sheet
sh_mark following; sh_tapui "Following feed"; sleep 6; shot SH-22-following-feed; sh_texts following
sh_tapui "Follow more"; sleep 4; shot SH-23-follow-more-sheet
sh_tapui "Search creators"; adb shell input text "Simcha"; sleep 2; shot SH-24-creators-search; sh_ime_hide; shot SH-24b-creators-found
sh_tapui "\+ Follow"; sleep 2; shot SH-25-sheet-followed; sh_back_while "Creators to follow"; sleep 3; shot SH-26-following-after-sheet
# unfollow both -> the list in the feed itself
sh_mark following-list; sh_tapui "Following feed"; sleep 3
sh_mark nobody; timeout 10 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_TAB --es tab Home >/dev/null 2>&1; sleep 1
# 11. Saved chip (what I saved), Uploads (a member's uploads, views), upload page with camera
sh_mark saved; view "https://yidplus.com/shorts"; sleep 5; sh_tapui "Saved"; sleep 4; shot SH-27-saved
sh_tapui "Saved"; sleep 2
sh_mark uploads; sh_tapui "Uploads feed"; sleep 6; shot SH-28-uploads; sh_tapui "More options"; sleep 2; shot SH-29-uploads-options; key 4; sleep 1
sh_mark upload-page; sh_tapui "Upload a Short"; sleep 3; shot SH-30-upload-page; key 4; sleep 2
# 12. the end page after the last video
sh_mark end; sh_tapui "For You feed"; sleep 4; for i in 1 2 3 4 5 6 7 8 9 10 11 12; do sh_up 2; done; sleep 2; shot SH-31-end-caught-up
# 13. a /shorts#<id> link opens that upload
sh_mark deeplink; view "https://yidplus.com/shorts#6630b5bb-e297-417b-bc25-fdd9d23836d9"; sleep 7; shot SH-32-deeplink-upload
# 13b. video 2 (D-pad / keypad): next, previous, pause, like (1), comments (3), options (#), creators (0), next tab (*)
sh_mark dpad; view "https://yidplus.com/shorts"; sleep 4; sh_tapui "For You feed"; sleep 3
(timeout 40 adb shell screenrecord --size 480x640 --bit-rate 2000000 --time-limit 30 /sdcard/SH-dpad.mp4 >/dev/null 2>&1 &)
sleep 1; key 20; sleep 3; key 20; sleep 3; key 19; sleep 2; key 23; sleep 2; key 23; sleep 1; key 8; sleep 2; shot SH-35-dpad-liked
key 10; sleep 3; shot SH-36-dpad-comments; key 4; sleep 2; key 18; sleep 2; shot SH-37-dpad-options; key 4; sleep 1
key 7; sleep 3; shot SH-38-dpad-creators; key 20; key 20; sleep 1; key 17; sleep 3; key 17; sleep 3
sleep 4; timeout 20 adb pull /sdcard/SH-flow.mp4 "$OUT/SH-flow.mp4" >/dev/null 2>&1; timeout 20 adb pull /sdcard/SH-dpad.mp4 "$OUT/SH-dpad.mp4" >/dev/null 2>&1
log "videos: $(ls -l "$OUT"/SH-*.mp4 2>/dev/null | awk '{print $5, $9}' | tr '\n' ' ')"
# 14. dark mode + a tall phone
adb shell cmd uimode night yes; sleep 3; sh_tapui "Following feed"; sleep 4; shot SH-33-following-dark; adb shell cmd uimode night no; sleep 2
size 1080x2400 420; restart 6; view "https://yidplus.com/shorts"; sleep 8; shot SH-34-phone-foryou; size 480x640 240
log "unfixtured GETs from Shorts: $(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -o 'GET-unfixtured [^ ]*' | sort | uniq -c | sort -rn | head -30 | tr '\n' ';')"
timeout 30 adb unroot >/dev/null 2>&1; sleep 3; timeout 30 adb wait-for-device
log "taps: $(timeout 20 adb logcat -d -s YidSh 2>/dev/null | grep -o 'tap dt=.*' | head -12 | tr '\n' ';')"
log "Shorts perf: $(timeout 20 adb logcat -d -s YidPerf 2>/dev/null | grep -o 'Shorts video playing [0-9]* ms' | tail -8 | tr '\n' ';')"

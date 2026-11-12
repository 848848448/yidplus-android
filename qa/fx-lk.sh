# ---- LK: the look (bars, rows, avatars, badges), Channels and Shorts, signed in with the fixture account ----
# Helper LK (2.0.2). Shots LK-NN-what. Every write lands in fx-requests.txt (tag YidFx); "LK-STEP x" lines mark which step sent it.
lk_mark() { timeout 10 adb shell log -t YidFx "LK-STEP $1" >/dev/null 2>&1; log "lk step $1"; }
# Own copy of tapui (native.sh defines it after this block runs): tap the first node whose text or content-desc matches $1.
lk_tapui() {
  local want="$1" bb="" n
  LK_OK=0
  # A playing video or a blinking cursor can make one uiautomator dump fail ("could not get idle state"): try three times.
  for n in 1 2 3; do
    timeout 30 adb shell uiautomator dump /sdcard/lk.xml >/dev/null 2>&1
    bb=$(timeout 20 adb shell cat /sdcard/lk.xml 2>/dev/null | tr '>' '\n' | grep -E "(text|content-desc)=\"$want\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
    [ -n "$bb" ] && break; timeout 5 adb shell rm -f /sdcard/lk.xml >/dev/null 2>&1; sleep 1
  done
  if [ -z "$bb" ]; then log "lk_tapui: '$want' not on screen"; return 0; fi
  set -- $bb; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; LK_OK=1; log "lk_tapui '$want'"
}
# Is Android's file picker (DocumentsUI) in front?
lk_inpicker() { timeout 10 adb shell dumpsys window 2>/dev/null | grep -E 'mCurrentFocus' | grep -qi documentsui; }
# QA_PICK: the app makes a small file itself and hands it to the open New post / Upload page (see SoQaPick in SocialKit.kt).
lk_qapick() { timeout 10 adb shell am broadcast -a com.yidplus.app.QA_PICK --es kind "$1" >/dev/null 2>&1; sleep 2; log "qa pick $1"; }
# My own channel: first row of Members (fixtures-lk.json puts it first).
lk_mine() { tab Home; sleep 3; lk_tapui "Members"; sleep 4; lk_tap 240 262 "${1:-5}"; }
lk_tap() { timeout 10 adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
lk_dev() { timeout 10 adb shell getevent -pl 2>/dev/null | tr -d '\r' | awk '/add device/{d=$4} /ABS_MT_POSITION_X/{print d; exit}'; }
lk_max() { timeout 10 adb shell getevent -pl "$1" 2>/dev/null | tr -d '\r' | grep "$2" | grep -oE 'max [0-9]+' | head -1 | awk '{print $2}'; }
# A REAL two-finger pinch-out (raw multitouch events, two slots) around ($1,$2) on the 480x640 screen.
lk_pinch() {
  local d mx my cx cy i c=""; d=$(lk_dev); [ -z "$d" ] && { log "pinch: no multitouch device"; return 0; }
  mx=$(lk_max "$d" ABS_MT_POSITION_X); my=$(lk_max "$d" ABS_MT_POSITION_Y); mx=${mx:-479}; my=${my:-639}
  cx=$1; cy=$2; local y=$(( cy * my / 639 ))
  c="$c sendevent $d 3 47 0; sendevent $d 3 57 301; sendevent $d 3 53 $(( (cx-30) * mx / 479 )); sendevent $d 3 54 $y; sendevent $d 3 58 60;"
  c="$c sendevent $d 3 47 1; sendevent $d 3 57 302; sendevent $d 3 53 $(( (cx+30) * mx / 479 )); sendevent $d 3 54 $y; sendevent $d 3 58 60;"
  c="$c sendevent $d 1 330 1; sendevent $d 0 0 0;"
  for i in 1 2 3 4 5 6 7 8 9 10; do
    c="$c sendevent $d 3 47 0; sendevent $d 3 53 $(( (cx-30-i*14) * mx / 479 )); sendevent $d 3 47 1; sendevent $d 3 53 $(( (cx+30+i*14) * mx / 479 )); sendevent $d 0 0 0;"
  done
  c="$c sendevent $d 3 47 0; sendevent $d 3 57 -1; sendevent $d 3 47 1; sendevent $d 3 57 -1; sendevent $d 1 330 0; sendevent $d 0 0 0;"
  timeout 30 adb shell "$c"; log "pinch-out at $cx,$cy on $d (max $mx x $my)"; sleep 1
}
# A real double-tap (two quick touches through the same raw device, no adb round trip between them).
lk_dtap() {
  local d mx my c=""; d=$(lk_dev); [ -z "$d" ] && { timeout 5 adb shell input tap $1 $2; timeout 5 adb shell input tap $1 $2; return 0; }
  mx=$(lk_max "$d" ABS_MT_POSITION_X); my=$(lk_max "$d" ABS_MT_POSITION_Y); mx=${mx:-479}; my=${my:-639}
  local x=$(( $1 * mx / 479 )) y=$(( $2 * my / 639 )) k
  for k in 401 402; do
    c="$c sendevent $d 3 47 0; sendevent $d 3 57 $k; sendevent $d 3 53 $x; sendevent $d 3 54 $y; sendevent $d 3 58 60; sendevent $d 1 330 1; sendevent $d 0 0 0;"
    c="$c sendevent $d 3 57 -1; sendevent $d 1 330 0; sendevent $d 0 0 0;"
  done
  timeout 20 adb shell "$c"; log "double-tap at $1,$2"; sleep 1
}
lk_glog() { log "gallery log: $(timeout 20 adb logcat -d -s YidGallery 2>/dev/null | grep -o 'pinch zoom [0-9.]*\|double-tap zoom [0-9.]*' | tail -4 | tr '\n' ' ')"; }

# Files for the pickers: a real screenshot (photo post) and a 2-second screen recording (Short upload) in Downloads.
timeout 20 adb shell screencap -p /sdcard/Download/lkqa.png; timeout 20 adb shell screenrecord --time-limit 2 /sdcard/Download/lkqa.mp4 >/dev/null 2>&1
timeout 10 adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/lkqa.png >/dev/null 2>&1
timeout 10 adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/lkqa.mp4 >/dev/null 2>&1
log "picker files: $(timeout 10 adb shell ls -l /sdcard/Download/ 2>/dev/null | grep lkqa | tr '\n' ' ')"
# 1. Channels tab: Telegram list, chips, Members (followers + verified), Joined, bottom-bar badges
tab Home; sleep 6; shot LK-01-channels
lk_tapui "Members"; sleep 5; shot LK-02-channels-members
lk_tapui "Joined"; sleep 3; shot LK-03-channels-joined
lk_tapui "Telegram"; sleep 3
# 2. A Telegram channel (cached copy first, no spinner)
lk_tap 240 262 7; shot LK-04-tg-channel
adb shell input swipe 240 520 240 260 300; sleep 2; shot LK-05-tg-channel-scrolled
lk_mark react-tg; lk_tapui "React"; sleep 1; lk_tapui "Quick reaction 1"; sleep 2; shot LK-05c-tg-reacted
lk_mark share-tg; lk_tapui "Share post"; sleep 3; shot LK-05d-tg-share-sheet; key 4; sleep 1
lk_mark join-tg; lk_tapui "Join channel"; sleep 3; shot LK-05e-tg-joined
key 4; sleep 2; lk_tap 240 262 3; shot LK-05b-tg-channel-reopen-cached; key 4; sleep 2
# 3. My own channel: like, comment, new post, photo -> gallery (pinch, double-tap, swipe)
lk_mine 6; shot LK-06-my-channel
adb shell input swipe 240 520 240 250 400; sleep 2
lk_mark like-post; lk_tapui "Like"; sleep 2; shot LK-07-my-channel-liked
lk_mark comment-post; lk_tapui "Comment"; sleep 3; shot LK-08-post-comments
adb shell input text "Shkoyach"; sleep 1; key 111; sleep 1; lk_mark send-comment; lk_tapui "Send"; sleep 2; shot LK-09-comment-sent
key 4; sleep 1; key 4; sleep 1
lk_mine 4
adb shell input swipe 240 520 240 200 400; sleep 2; shot LK-09b-my-channel-scrolled
adb shell input swipe 240 520 240 300 400; sleep 2; lk_tapui "Photo"; sleep 3; shot LK-22-gallery-open
log "touch devices: $(timeout 10 adb shell getevent -pl 2>/dev/null | tr -d '\r' | grep -E 'add device|name:' | paste - - | grep -i touch | head -4 | tr -s ' ' | tr '\n' ';')"
# (a) the app's own QA hook plays a real 2-finger MotionEvent pinch through the window (proves the gesture code)
timeout 10 adb shell am broadcast -a com.yidplus.app.QA_GESTURE --es g pinch >/dev/null 2>&1; sleep 2; shot LK-23-gallery-pinched; lk_glog
adb shell input swipe 300 330 160 330 300; sleep 1; shot LK-23b-gallery-zoomed-panned
timeout 10 adb shell am broadcast -a com.yidplus.app.QA_GESTURE --es g dtap >/dev/null 2>&1; sleep 2; shot LK-24-gallery-double-tap-reset; lk_glog
timeout 10 adb shell am broadcast -a com.yidplus.app.QA_GESTURE --es g dtap >/dev/null 2>&1; sleep 2; shot LK-25-gallery-double-tap-zoom; lk_glog
timeout 10 adb shell am broadcast -a com.yidplus.app.QA_GESTURE --es g pinchin >/dev/null 2>&1; sleep 2; lk_glog
# (b) the same through the emulator's raw touch screen (two slots)
lk_pinch 240 330; sleep 1; shot LK-25b-gallery-raw-pinch; lk_glog
lk_dtap 240 330; sleep 2; lk_glog
adb shell input swipe 420 330 80 330 160; sleep 2; shot LK-26-gallery-swiped-next
key 4; sleep 2; key 4; sleep 2
lk_mine 4
lk_mark new-post; lk_tapui "New post"; sleep 3; adb shell input text "Test%spost%sfrom%sthe%sapp"; sleep 1; shot LK-10-new-post
lk_tapui "Post"; sleep 3; shot LK-11-new-post-sent
key 4; sleep 2; lk_mine 4
lk_mark new-photo-post; lk_tapui "New post"; sleep 3; adb shell input text "Photo%spost"; sleep 1; key 111; sleep 1; lk_tapui "Photo"; sleep 4; shot LK-11b-photo-picker
lk_tapui "lkqa.png"; sleep 3
if [ "$LK_OK" != 1 ]; then lk_inpicker && { log "picker open, file row not found: back"; key 4; sleep 2; }; lk_qapick image; fi
shot LK-11c-photo-picked; lk_tapui "Post"; sleep 4; shot LK-11d-photo-post-sent
key 4; sleep 2; key 4; sleep 2
# 4. Shorts: like, comment, swipe, Uploads tab (like/save/view to the site), upload page
tab Shorts; sleep 10; shot LK-12-shorts
lk_mark like-short; lk_tapui "Like"; sleep 2; shot LK-13-short-liked
lk_mark comment-short; lk_tapui "Comment"; sleep 3; adb shell input text "Nice"; sleep 1; shot LK-14-short-comments
key 4; sleep 1; lk_tapui "Post"; sleep 2; shot LK-14b-short-comment-sent; key 4; sleep 1
lk_mark swipe-short; adb shell input swipe 240 470 240 250 120; sleep 4; shot LK-15-short-swiped
adb shell input swipe 240 470 240 250 120; sleep 4; shot LK-16-short-swiped2
lk_tapui "Uploads"; sleep 6; shot LK-16b-shorts-uploads
lk_mark like-upload; lk_tapui "Like"; sleep 2
lk_mark save-upload; lk_tapui "Save"; sleep 2; shot LK-16c-upload-liked-saved
lk_mark follow-creator; lk_tapui "More"; sleep 1; lk_tapui "@[^\"]*videos[^\"]*"; sleep 4; shot LK-16d-creator; lk_tapui "Follow"; sleep 2; shot LK-16e-creator-followed; key 4; sleep 3
lk_mark comment-upload; lk_tapui "Comment"; sleep 3; adb shell input text "Great"; sleep 1; key 111; sleep 1; lk_tapui "Post"; sleep 2; shot LK-16f-upload-comments; key 4; sleep 1
lk_mark upload-short; lk_tapui "Upload a Short"
[ "$LK_OK" != 1 ] && { timeout 10 adb shell am broadcast -a com.yidplus.app.QA_PICK --es open upload >/dev/null 2>&1; log "qa open upload page"; }
sleep 4; shot LK-17-upload-picker
lk_tapui "lkqa.mp4"; sleep 3
if [ "$LK_OK" != 1 ]; then lk_inpicker && { log "picker open, file row not found: back"; key 4; sleep 2; }; lk_qapick video; fi
shot LK-17b-upload-picked; lk_tapui "Upload"; [ "$LK_OK" != 1 ] && { log "Upload button: fixed position"; timeout 10 adb shell input tap 240 585; }
sleep 5; shot LK-17c-upload-sent
key 4; sleep 2
# 5. Signed out first open: the guest bar must be slim
fx false; restart 7; tab Chats; sleep 4; shot LK-18-guest-chats; tab Home; sleep 4; shot LK-19-guest-channels
fx true; restart 7
# 6. Dark mode: channels, my channel, shorts bar
adb shell cmd uimode night yes; sleep 3; tab Home; sleep 4; shot LK-20-channels-dark
lk_tapui "Members"; sleep 3; shot LK-20b-members-dark; lk_tapui "Telegram"; sleep 1
lk_mine 5; shot LK-21-my-channel-dark; key 4; sleep 1
adb shell cmd uimode night no; sleep 3

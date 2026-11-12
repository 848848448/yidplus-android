# ---- SM: statuses, music, photo/video viewer, uploads — signed in with the QA fixture account (helper SM, 2.1) ----
# Sourced by qa/signedin.sh fx_block (helpers from qa/native.sh: log shot key view tab check restart). Shots SM-NN-what at 480x640.
# Every write lands in qa/out/fx-requests.txt (tag YidFx); "SM-STEP x" lines mark which step sent it.
# Data: assets/qa/fixtures-sm.json (/api/music with the site's real audio files, an album, a music video; /api/statuses).
sm_mark() { timeout 10 adb shell log -t YidFx "SM-STEP $1" >/dev/null 2>&1; log "sm step $1"; }
# Pages without a menu entry yet open through QA_SM (ui/SmQa.kt), which listens while the Chats status strip or the Music tab is on screen.
sm_open() { tab Chats; sleep 3; timeout 20 adb shell am broadcast -a com.yidplus.app.QA_SM --es page "$1" >/dev/null 2>&1; sleep "${2:-4}"; log "sm open $1"; }
nw_open2() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_OPEN --es page "$1" >/dev/null 2>&1; sleep "${2:-4}"; log "qa open $1"; }
sm_tap() { # tap the first node whose text or content-desc is exactly $1 (three tries: a playing video can make one dump fail)
  local want="$1" bb="" n
  SM_OK=0
  for n in 1 2; do
    timeout 12 adb shell uiautomator dump /sdcard/sm.xml >/dev/null 2>&1
    bb=$(timeout 10 adb shell cat /sdcard/sm.xml 2>/dev/null | tr '>' '\n' | grep -F -e "text=\"$want\"" -e "content-desc=\"$want\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
    [ -n "$bb" ] && break; sleep 1
  done
  if [ -z "$bb" ]; then log "sm_tap: '$want' not on screen"; return 0; fi
  set -- $bb; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep "${2:-1.5}"; SM_OK=1; log "sm_tap '$want'"
}
sm_texts() { timeout 12 adb shell uiautomator dump /sdcard/sm.xml >/dev/null 2>&1; log "SM texts $1: $(timeout 20 adb shell cat /sdcard/sm.xml 2>/dev/null | grep -oE '(text|content-desc)="[^"]+"' | cut -d'"' -f2 | head -60 | tr '\n' '|')"; }
# Short screen videos (H.264, 480x640, <=30 s) of the main flows; pulled into qa/out at the end of each block.
sm_rec() { (timeout 45 adb shell screenrecord --size 480x640 --bit-rate 2000000 --time-limit "${2:-30}" "/sdcard/$1.mp4" >/dev/null 2>&1 &) ; sleep 1; log "sm rec $1"; }
sm_pull() { sleep "${2:-2}"; timeout 30 adb pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null 2>&1; log "sm video $1: $(stat -c %s "$OUT/$1.mp4" 2>/dev/null || echo missing) bytes"; }
sm_glog() { log "SM gallery log: $(timeout 20 adb logcat -d -s YidGallery 2>/dev/null | grep -oE '(pinch|double-tap|key) zoom [0-9.]+' | tail -4 | tr '\n' ';')"; }

# ── Music ── (the fixture's /api/settings has feat_music off, like the live site today: QA_FEATURES shows every tab)
timeout 20 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_FEATURES --ez all true >/dev/null 2>&1; sleep 2
sm_mark music-tab
tab Music; sleep 6; shot SM-01-music-tab; sm_texts music-tab
sm_tap "Albums"; sleep 2; shot SM-02-music-albums
sm_tap "Music videos"; sleep 2; shot SM-03-music-videos
sm_tap "All"; sleep 2
sm_tap "Search music"; sleep 1; timeout 10 adb shell input text "zzqq" ; sleep 2; shot SM-04-music-search-empty; key 4; key 4; sleep 1
sm_mark play
sm_rec SM-video-music 30
sm_tap "א נייע ניגון"; sleep 6; shot SM-05-player; sm_texts player
timeout 10 adb shell input swipe 240 520 240 160 400; sleep 1; shot SM-06-player-scrolled-upnext
sm_tap "Shuffle off"; sm_tap "Repeat off"; sleep 1; shot SM-07-player-shuffle-repeat
key 22; key 22; sleep 1; check sm-player-keys
key 4; sleep 2; shot SM-08-miniplayer; check sm-player
sm_pull SM-video-music 4
sm_mark track-menu
tab Music; sleep 3; sm_tap "More for שבת פרייד"; sleep 1; shot SM-09-track-menu
sm_tap "Save"; sleep 2; key 4
sm_mark album
sm_open album 5; shot SM-10-album-page; sm_tap "Shuffle"; sleep 3; shot SM-11-album-shuffle; key 4; sleep 1
sm_mark music-video
sm_open player 4; sm_tap "Video"; sleep 6; shot SM-12-player-video; key 4; sleep 1
sm_mark music-upload
sm_open upload 4; shot SM-13-music-upload-empty
timeout 20 adb shell am broadcast -a com.yidplus.app.QA_PICK --es kind video >/dev/null 2>&1; sleep 2
timeout 20 adb shell am broadcast -a com.yidplus.app.QA_PICK --es kind image >/dev/null 2>&1; sleep 2
sm_tap "Track title"; timeout 10 adb shell input text "QA%stest"; key 4; sleep 1; shot SM-14-music-upload-filled
sm_tap "Upload"; sleep 4; shot SM-15-music-upload-sent; check sm-music-upload
key 4; sleep 1

# ── Statuses ── (music paused first: a playing player keeps the UI busy and uiautomator dumps fail)
timeout 10 adb shell input keyevent 127; sleep 1
sm_mark updates
sm_open updates 5; shot SM-16-updates-page; sm_texts updates
sm_mark status-other
sm_rec SM-video-status 30
nw_open2 status 5; shot SM-17-status-other; key 23; sleep 1
timeout 10 adb shell input swipe 240 470 240 200 250; sleep 1; shot SM-18-status-react-bar
sm_tap "React 🔥"; sleep 2; shot SM-19-status-reacted
key 82; sleep 1; shot SM-20-status-menu-other
sm_tap "Save to Highlights"; sleep 2
timeout 10 adb shell input tap 400 320; sleep 2; shot SM-21-status-next-slide
timeout 10 adb shell input tap 60 320; sleep 1; timeout 10 adb shell input tap 60 320; sleep 1; shot SM-22-status-prev
shot SM-23-status-paused-ok
timeout 10 adb shell input swipe 240 200 240 560 300; sleep 2; shot SM-24-status-swiped-down-closed; check sm-status-other
sm_pull SM-video-status 2
sm_mark mute
nw_open2 status 5; key 23; key 22; key 22; sleep 1
key 82; sleep 1; sm_texts mute-menu
timeout 12 adb shell uiautomator dump /sdcard/sm.xml >/dev/null 2>&1
M=$(timeout 20 adb shell cat /sdcard/sm.xml 2>/dev/null | grep -oE 'text="Mute [^"]+"' | head -1 | cut -d'"' -f2)
[ -n "$M" ] && sm_tap "$M"; sleep 2; key 4; sleep 1
sm_open updates 5; sm_tap "Muted updates (1)"; sleep 1; shot SM-25-updates-muted
sm_open highlights 4; shot SM-26-highlights
sm_tap "Remove"; sleep 1; shot SM-27-highlights-removed; key 4
sm_mark status-mine
nw_open2 status_me 5; shot SM-28-status-mine; key 23
key 82; sleep 1; shot SM-29-status-menu-mine
sm_tap "Privacy: Followers only"; sleep 1; shot SM-30-status-privacy; key 4; sleep 1
timeout 10 adb shell input swipe 240 470 240 200 250; sleep 2; shot SM-31-status-viewers; key 4; sleep 1
key 82; sleep 1; sm_tap "Delete"; sleep 1; shot SM-32-status-delete-confirm; key 4; sleep 1; key 4
sm_mark post-text
nw_open2 post_status 4; timeout 10 adb shell input text "Gut%sMorgen"; sleep 1; shot SM-33-post-text-keyboard
key 4; sleep 1; shot SM-34-post-text-chips
sm_tap "Post status"; sleep 3; check sm-post-text
sm_mark post-media
nw_open2 post_status 4; key 4; sleep 1
timeout 20 adb shell am broadcast -a com.yidplus.app.QA_PICK --es kind image >/dev/null 2>&1; sleep 3
sm_tap "Add a caption…"; timeout 10 adb shell input text "Caption%stest"; key 4; sleep 1; shot SM-35-post-media-caption
sm_tap "Post status"; sleep 4; shot SM-36-post-media-sent; check sm-post-media

# ── Photo/video viewer ──
sm_mark gallery
sm_rec SM-video-gallery 30
sm_open gallery 6; shot SM-37-gallery-photo-chrome
timeout 10 adb shell input tap 240 330; sleep 1; shot SM-38-gallery-chrome-hidden
timeout 10 adb shell input tap 240 330; sleep 1
key 23; sleep 1; shot SM-39-gallery-key-zoom; key 22; sleep 1; key 8; sleep 1; key 23; sleep 1; sm_glog
timeout 20 adb shell am broadcast -a com.yidplus.app.QA_GESTURE --es g pinch >/dev/null 2>&1; sleep 2; shot SM-40-gallery-pinch; sm_glog
timeout 20 adb shell am broadcast -a com.yidplus.app.QA_GESTURE --es g dtap >/dev/null 2>&1; sleep 2; sm_glog
sm_tap "More options"; sleep 1; shot SM-41-gallery-menu
sm_tap "Save to phone"; sleep 2; check sm-gallery-save
key 22; sleep 3; shot SM-42-gallery-second
sm_pull SM-video-gallery 6
key 22; sleep 6; shot SM-43-gallery-video
key 22; sleep 5; shot SM-44-gallery-broken-retry
key 21; key 21; key 21; sleep 2; shot SM-45-gallery-back-first
timeout 10 adb shell input swipe 240 250 240 600 300; sleep 2; shot SM-46-gallery-dragged-closed; check sm-gallery
log "SM requests: $(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -E 'SM-|/api/(music|statuses|chat|saves|history)' | sed 's/.*YidFx *: //' | tail -40 | tr '\n' ';')"

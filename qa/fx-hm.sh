# ---- HM (2.1): Telegram channels, member channels, posts, search, notifications, public profiles — signed in with the fixture account ----
# Helper HM. Shots HM-NN-what. Every write lands in fx-requests.txt (tag YidFx); "HM-STEP x" lines mark which step sent it.
hm_mark() { timeout 10 adb shell log -t YidFx "HM-STEP $1" >/dev/null 2>&1; log "hm step $1"; }
# Tap the first node whose text or content-desc matches the regex $1 (three tries: a playing video can make one dump fail).
hm_tapui() {
  local want="$1" bb="" n
  for n in 1 2 3; do
    timeout 30 adb shell uiautomator dump /sdcard/hm.xml >/dev/null 2>&1
    bb=$(timeout 20 adb shell cat /sdcard/hm.xml 2>/dev/null | tr '>' '\n' | grep -E "(text|content-desc)=\"$want\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
    [ -n "$bb" ] && break; timeout 5 adb shell rm -f /sdcard/hm.xml >/dev/null 2>&1; sleep 1
  done
  if [ -z "$bb" ]; then log "hm_tapui: '$want' not on screen"; return 0; fi
  set -- $bb; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; log "hm_tapui '$want'"
}
# A chip in a sideways-scrolling chip row: find the row by its first chip ("All"), swipe it left until the chip shows, tap it.
hm_tapchip() {
  local want="$1" row="" y1 y2 n x hit
  for n in 1 2 3 4; do
    timeout 30 adb shell uiautomator dump /sdcard/hm.xml >/dev/null 2>&1
    x=$(timeout 20 adb shell cat /sdcard/hm.xml 2>/dev/null | tr '>' '\n')
    row=$(echo "$x" | grep -E '(text|content-desc)="All"' | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
    [ -z "$row" ] && { log "hm_tapchip: no chip row"; return 0; }
    set -- $row; y1=$2; y2=$4
    # Only a node on the chip row's own line counts (a "Users" group header further down is not the chip).
    hit=$(echo "$x" | grep -E "(text|content-desc)=\"$want\"" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | paste -d' ' - - - - | awk -v a=$y1 -v b=$y2 '{c=($2+$4)/2; if (c>=a-4 && c<=b+4) {print; exit}}')
    if [ -n "$hit" ]; then set -- $hit; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; log "hm_tapchip '$want'"; return 0; fi
    timeout 10 adb shell input swipe 420 $(( (y1 + y2) / 2 )) 80 $(( (y1 + y2) / 2 )) 500; sleep 1.5
  done
  log "hm_tapchip: '$want' not found"
}
# hm_find: like hm_tapui, but scrolls the page up (content moves up) up to 3 times until the thing shows.
hm_find() {
  local want="$1" n
  for n in 0 1 2 3; do
    timeout 30 adb shell uiautomator dump /sdcard/hm.xml >/dev/null 2>&1
    if timeout 20 adb shell cat /sdcard/hm.xml 2>/dev/null | tr '>' '\n' | grep -qE "(text|content-desc)=\"$want\""; then hm_tapui "$want"; return 0; fi
    timeout 10 adb shell input swipe 240 420 240 260 400; sleep 1.5
  done
  log "hm_find: '$want' not found after scrolling"
}
# hm_longui: long-press the first thing whose text or content-desc is exactly $1.
hm_longui() {
  local want="$1" bb
  timeout 30 adb shell uiautomator dump /sdcard/hm.xml >/dev/null 2>&1
  bb=$(timeout 20 adb shell cat /sdcard/hm.xml 2>/dev/null | tr '>' '\n' | grep -E "(text|content-desc)=\"$want\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -z "$bb" ]; then log "hm_longui: '$want' not on screen"; return 0; fi
  set -- $bb; timeout 10 adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000; sleep 2; log "hm_longui '$want'"
}
# Hide the on-screen keyboard only when it is really up (a plain Back would leave the screen otherwise).
hm_hidekb() { if timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -q 'mInputShown=true'; then key 4; sleep 1; log "hm keyboard hidden"; fi; }
hm_swipe() { timeout 10 adb shell input swipe 240 "$1" 240 "$2" 350; sleep "${3:-2}"; }

# 0. The site's Home page as the "Feed" chip: greeting + Hebrew date, announcement, tiles, composer, Hot Shorts, Popular channels, community posts.
tab Home; sleep 4
hm_tapui "Feed"; sleep 7; shot HM-30-feed-top
# The announcement first (it sits at the top), then the composer one short swipe down, then the rest.
hm_mark feed-hide-announcement; hm_tapui "Hide announcement"; sleep 2; shot HM-35-feed-announcement-hidden
hm_swipe 430 300; sleep 1
hm_tapui "What's happening\?"; sleep 1
timeout 10 adb shell input text "HM%stest%spost"; sleep 1; hm_hidekb; sleep 1; shot HM-33-feed-composer
hm_mark feed-post; hm_tapui "Post"; sleep 4; shot HM-34-feed-posted
hm_swipe 430 230; shot HM-31-feed-shorts-channels
hm_swipe 430 180; hm_swipe 430 180; shot HM-32-feed-posts
hm_tapui "Telegram"; sleep 2

# 1. A Telegram channel: Telegram order (newest at the bottom), formatting, link card, album grid, day chips, live check, Join.
tab Home; sleep 4
hm_tapui "Telegram"; sleep 3
hm_longui "יידישע נייעס"; shot HM-38-tg-row-longpress-menu
hm_mark tg-row-markread; hm_tapui "Mark as read"; sleep 2; shot HM-39-tg-row-read
hm_find "'?שיעורים יומיים"; sleep 8; shot HM-01-tg-newest-at-bottom
hm_tapui "Post options"; sleep 1; shot HM-36-tg-post-menu; key 4; sleep 1
hm_tapui "Search in channel"; sleep 1; timeout 10 adb shell input text "wikipedia"; sleep 2; shot HM-37-tg-search-in-channel
hm_hidekb; hm_tapui "Search in channel"; sleep 2
hm_swipe 220 560; shot HM-02-tg-album-linkcard
hm_swipe 200 600; hm_swipe 200 600; shot HM-03-tg-older-daychips
hm_tapui "Newest posts"; sleep 2; shot HM-04-tg-jump-newest
hm_mark tg-join; hm_tapui "Join channel"; sleep 3; shot HM-05-tg-joined
hm_tapui "More"; sleep 1; shot HM-06-tg-menu
key 4; sleep 1; key 4; sleep 2

# 2. Site search: Users chip → public profile → Follow → Followers list → menu → Report.
tab Home; sleep 3
hm_tapui "Search"; sleep 2
timeout 10 adb shell input text "moshe"; sleep 3
hm_tapui "Search all of YID PLUS"; sleep 6; hm_hidekb; sleep 1; shot HM-07-search-all
hm_tapchip "Users"; sleep 5; hm_hidekb; shot HM-08-search-users
hm_tapui "Moshe Fx"; sleep 6; shot HM-09-profile
hm_mark profile-follow; hm_find "Follow"; sleep 4; shot HM-10-profile-followed
hm_find "Message"; sleep 5; shot HM-11-profile-message; key 4; sleep 2
hm_find "Followers"; sleep 5; shot HM-12-followers-list; key 4; sleep 2
hm_tapui "More"; sleep 1; shot HM-13-profile-menu
hm_tapui "Report this user"; sleep 2
hm_tapui "Spam"; sleep 1; shot HM-14-report-dialog
hm_mark profile-report; hm_tapui "Report"; sleep 3
hm_tapui "More"; sleep 1; hm_tapui "Block this user"; sleep 2; shot HM-15-block-dialog
hm_mark profile-block; hm_tapui "Block"; sleep 3; shot HM-16-profile-blocked
key 4; sleep 2; key 4; sleep 2
# Recent searches (the empty search box now lists "moshe").
tab Home; sleep 3
hm_tapui "Search"; sleep 2; timeout 10 adb shell input text "mo"; sleep 2; hm_tapui "Search all of YID PLUS"; sleep 3
hm_tapui "Clear"; sleep 2; hm_hidekb; sleep 1; shot HM-17-recent-searches
key 4; sleep 1; key 4; sleep 2

# 3. Member channel: Message, ⋮ menu, Profile, tabs.
tab Home; sleep 3
hm_tapui "Members"; sleep 5
hm_tapui "משה'לע"; sleep 6; shot HM-18-member-channel
hm_tapui "Channel options"; sleep 1; shot HM-19-member-channel-menu
key 4; sleep 1
hm_find "Followers"; sleep 5; shot HM-25-my-followers
hm_mark follower-remove; hm_tapui "Remove"; sleep 1; shot HM-26-remove-follower-confirm
hm_tapui "Remove"; sleep 3; shot HM-27-follower-removed
key 4; sleep 3
hm_swipe 500 200; shot HM-20-member-channel-scrolled
key 4; sleep 2

# 4. Notifications page: day groups, pull to refresh, Mark all read, Open chats.
tab Home; sleep 3
hm_tapui "Notifications"; sleep 5; shot HM-21-notifications
hm_swipe 200 560 3
timeout 10 adb shell input swipe 240 200 240 200 1000; sleep 2; shot HM-24-notification-longpress
hm_mark notif-markread; hm_tapui "Mark as read"; sleep 2
hm_mark notifs-readall; hm_tapui "Mark all read"; sleep 3; shot HM-23-notifications-read
key 4; sleep 2

# 5. Two short screen videos (480x640, H.264): the Telegram channel by touch AND D-pad, then the Feed + search → profile → Follow.
hm_rec_start() { timeout -k 3 40 adb shell screenrecord --size 480x640 --bit-rate 2000000 --time-limit 28 "/sdcard/$1.mp4" >/dev/null 2>&1 & HM_REC=$!; HM_RECN="$1"; sleep 1; log "hm rec $1"; }
hm_rec_stop() { wait "$HM_REC" 2>/dev/null; sleep 1; timeout 60 adb pull "/sdcard/$HM_RECN.mp4" "$OUT/$HM_RECN.mp4" >/dev/null 2>&1; timeout 10 adb shell rm -f "/sdcard/$HM_RECN.mp4" >/dev/null 2>&1; log "hm video $HM_RECN.mp4 $(stat -c %s "$OUT/$HM_RECN.mp4" 2>/dev/null)"; }
tab Home; sleep 3; hm_tapui "Telegram"; sleep 2
hm_rec_start HM-video-telegram
hm_find "'?שיעורים יומיים"; sleep 4
key 19; key 19; key 19; sleep 1; key 20; sleep 1
key 82; sleep 2; key 4; sleep 1
hm_swipe 220 560 1; hm_swipe 220 560 1; hm_swipe 560 220 1
hm_tapui "Post options"; sleep 2; key 4; sleep 1
key 4; sleep 2
hm_longui "יידישע נייעס"; key 4
hm_rec_stop
hm_rec_start HM-video-feed-profile
tab Home; sleep 2; hm_tapui "Feed"; sleep 4
hm_swipe 430 200 1; hm_swipe 430 200 1; hm_swipe 150 450 1
hm_tapui "Search"; sleep 1; timeout 10 adb shell input text "moshe"; sleep 1
hm_tapui "Search all of YID PLUS"; sleep 3; hm_hidekb; hm_tapchip "Users"; sleep 3; hm_hidekb
hm_tapui "Moshe Fx"; sleep 4; hm_find "Followers"; sleep 3; key 4
hm_rec_stop
key 4; sleep 1; key 4; sleep 1; key 4; sleep 1
tab Home; sleep 2; hm_tapui "Telegram"; sleep 1
check hm-done

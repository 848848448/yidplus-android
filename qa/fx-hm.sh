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
hm_swipe() { timeout 10 adb shell input swipe 240 "$1" 240 "$2" 350; sleep "${3:-2}"; }

# 1. A Telegram channel: Telegram order (newest at the bottom), formatting, link card, album grid, day chips, live check, Join.
tab Home; sleep 4
hm_tapui "Telegram"; sleep 3
hm_tapui "'?שיעורים יומיים"; sleep 8; shot HM-01-tg-newest-at-bottom
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
hm_tapui "Search all of YID PLUS"; sleep 6; shot HM-07-search-all
hm_tapui "Users"; sleep 5; shot HM-08-search-users
hm_tapui "Moshe Fx"; sleep 6; shot HM-09-profile
hm_mark profile-follow; hm_tapui "Follow"; sleep 4; shot HM-10-profile-followed
hm_tapui "Message"; sleep 5; shot HM-11-profile-message; key 4; sleep 2
hm_tapui "Followers"; sleep 5; shot HM-12-followers-list; key 4; sleep 2
hm_tapui "More"; sleep 1; shot HM-13-profile-menu
hm_tapui "Report this user"; sleep 2
hm_tapui "Spam"; sleep 1; shot HM-14-report-dialog
hm_mark profile-report; hm_tapui "Report"; sleep 3
hm_tapui "More"; sleep 1; hm_tapui "Block this user"; sleep 2; shot HM-15-block-dialog
hm_mark profile-block; hm_tapui "Block"; sleep 3; shot HM-16-profile-blocked
key 4; sleep 2; key 4; sleep 2
# Recent searches (the empty search box now lists "moshe").
hm_tapui "Search"; sleep 2; hm_tapui "Search all of YID PLUS"; sleep 3; shot HM-17-recent-searches
key 4; sleep 1; key 4; sleep 2

# 3. Member channel: Message, ⋮ menu, Profile, tabs.
tab Home; sleep 3
hm_tapui "Members"; sleep 5
timeout 10 adb shell input tap 240 250; sleep 6; shot HM-18-member-channel
hm_tapui "Channel options"; sleep 1; shot HM-19-member-channel-menu
key 4; sleep 1
hm_swipe 560 260; shot HM-20-member-channel-scrolled
key 4; sleep 2

# 4. Notifications page: day groups, pull to refresh, Mark all read, Open chats.
tab Home; sleep 3
hm_tapui "Notifications"; sleep 5; shot HM-21-notifications
hm_swipe 200 560 3; shot HM-22-notifications-pulled
hm_mark notifs-readall; hm_tapui "Mark all read"; sleep 3; shot HM-23-notifications-read
key 4; sleep 2
check hm-done

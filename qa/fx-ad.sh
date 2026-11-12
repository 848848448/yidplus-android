# ==== Helper AD (2.0.2): Settings top card + sections, Notifications page, native Admin — signed in with the QA fixture account ====
# Sourced by qa/signedin.sh (fx_block). Each tap below is a real action; qa/out/fx-requests.txt shows what it would send.
adq() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_TAB --es tab Profile --es qa_page "x:$1" >/dev/null 2>&1; sleep "${2:-4}"; log "ad page $1"; }
# Swipe in the middle of the screen: at 480x640 the bottom bar starts near y=556, and a swipe that starts on it scrolls nothing.
adswipe() { timeout 10 adb shell input swipe 240 470 240 190 400; sleep 1.2; }
adtop() { local i; for i in 1 2 3 4 5 6; do timeout 10 adb shell input swipe 240 190 240 500 200; sleep 0.4; done; sleep 0.8; }
adtype() { timeout 10 adb shell input text "$1"; sleep 1; }
adlog() { timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -F "$1" | tail -1 | sed 's/^.*YidFx[^:]*: //' ; }
adhidekb() { if timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -q "mInputShown=true"; then timeout 10 adb shell input keyevent 4; sleep 1; fi; }
# Animations off: an endless skeleton pulse keeps uiautomator from ever getting an idle screen to dump.
for k in animator_duration_scale transition_animation_scale window_animation_scale; do timeout 10 adb shell settings put global $k 0 >/dev/null 2>&1; done
adseen() { timeout 20 adb shell cat /sdcard/ad.xml 2>/dev/null | grep -oE '(text|content-desc)="[^"]+"' | cut -d'"' -f2 | head -25 | tr '\n' '|' | cut -c1-400; }
# tap the first thing whose text or content-desc is exactly $1 (qa/native.sh's tapui is defined after the quick block runs)
adt() {
  local b d; d=$(timeout 30 adb shell uiautomator dump /sdcard/ad.xml 2>&1); case "$d" in *ERROR*|*rror*) log "dump failed: $d";; esac
  b=$(timeout 20 adb shell cat /sdcard/ad.xml 2>/dev/null | tr '>' '\n' | grep -E "(text|content-desc)=\"$1[\",]" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -z "$b" ]; then log "adt: '$1' not on screen; screen has: $(adseen)"; return 0; fi
  set -- $b; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; log "adt '$1'"
}
# scroll down until $1 is on screen (at most 8 swipes), then tap it
adfind() {
  local i b d
  for i in 1 2 3 4 5 6 7 8 9; do
    d=$(timeout 30 adb shell uiautomator dump /sdcard/ad.xml 2>&1); case "$d" in *ERROR*|*rror*) log "dump failed: $d";; esac
    b=$(timeout 20 adb shell cat /sdcard/ad.xml 2>/dev/null | tr '>' '\n' | grep -E "(text|content-desc)=\"$1[\",]" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
    if [ -n "$b" ]; then set -- $b; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; log "adfind tapped after $((i-1)) swipes"; return 0; fi
    adswipe
  done
  log "adfind: '$1' not found; screen has: $(adseen)"
}
# tap the thing whose text is $1, else the point $2 $3 (text fields' labels aren't always in the UI dump)
adtap() {
  local b d; d=$(timeout 30 adb shell uiautomator dump /sdcard/ad.xml 2>&1); case "$d" in *ERROR*|*rror*) log "dump failed: $d";; esac
  b=$(timeout 20 adb shell cat /sdcard/ad.xml 2>/dev/null | tr '>' '\n' | grep -F "text=\"$1\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -n "$b" ]; then set -- $b; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); log "adtap text";
  else timeout 10 adb shell input tap "$2" "$3"; log "adtap '$1' not in dump, tapped $2,$3"; fi; sleep 1.2
}
proof() { local l; l=$(adlog "$2"); if [ -n "$l" ]; then log "PROOF $1: $l"; else log "PROOF $1: (no request logged for '$2')"; fi; }

# 2.0.2 settings + old admin pages: skipped on the quick branch-only run (qa/QUICK), so the 2.1 admin block gets the time.
if [ ! -f qa/QUICK ]; then
log "---- AD: Settings (signed in) ----"
tab Profile; sleep 4; shot AD-01-settings-top
adswipe; shot AD-02-settings-reading
adswipe; shot AD-03-settings-appearance-color
adswipe; shot AD-04-settings-stats-account
adswipe; shot AD-05-settings-verify-share
adswipe; adswipe; shot AD-06-settings-bottom
check "AD settings scroll"
# Colour theme + text size (kept on the phone, like the site's localStorage)
tab Profile; sleep 3; adtop; adfind "Ocean Blue"; sleep 2; proof "colour theme" "LOOK color_theme=1"; adtop; adfind "L"; sleep 2; shot AD-07-ocean-blue-large-text; adswipe; adswipe; shot AD-07c-after-large-text-scroll
adtop; adfind "Emerald Teal"; adtop; adfind "M"; sleep 1
# Save profile (PUT /api/profile with the changed field, like theme.js saveEditField)
adq EditProfile 5; shot AD-07b-edit-profile; adfind "Brooklyn, NY"; timeout 10 adb shell input keyevent KEYCODE_MOVE_END; adtype "_Monsey"; adhidekb; adt "SAVE"; sleep 3
proof "save profile" "PUT /api/profile"
# Privacy (theme.js toggleSetting: PUT /api/profile {key: 1|0})
adq Privacy; shot AD-08-privacy; adt "Private account"; sleep 2; proof "private account" "is_private"
adfind "Read receipts"; sleep 2; proof "read receipts" "read_receipts"; key 4
# Notification prefs
adq NotifPrefs; adt "New likes"; sleep 2; proof "notif likes" "notif_likes"; shot AD-09-notif-prefs; key 4
# Chat settings (wallpaper / silent calls are local)
adq ChatSettings; adt "Mint"; sleep 1; shot AD-10-chat-settings; key 4

log "---- AD: Notifications page ----"
timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_NOTIFS >/dev/null 2>&1; sleep 5; shot AD-11-notifications
adt "Mark all read"; sleep 2; proof "mark all read" "\"read\":\"all\""; shot AD-13-notifications-all-read; key 4; sleep 1
restart 5; timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_NOTIFS >/dev/null 2>&1; sleep 5
timeout 10 adb shell input tap 240 140; sleep 4; proof "open one notification" "\"read\":\"n"; shot AD-12-notification-opened; key 4; sleep 2

log "---- AD: Admin (native) ----"
adq Admin 6; shot AD-20-admin-dashboard
adswipe; shot AD-21-admin-manage
adswipe; shot AD-22-admin-features
adfind "The Music tab and player"; sleep 2; proof "feature switch" "PUT /api/settings"; shot AD-23-admin-music-switched; adt "The Music tab and player"; sleep 2
adswipe; shot AD-24-admin-web-sections; key 4
adq AdminUsers 5; shot AD-25-admin-users
adtap "Search by nickname or email" 240 140; adtype "Yan"; sleep 2; adhidekb; shot AD-26-admin-users-search; key 4
adq AdminUser 5; shot AD-27-admin-user
adt "Verify"; sleep 2; proof "verify user" "\"verified\":true"
adtop; adfind "Mute"; sleep 2; adt "24 hours"; sleep 2; proof "mute user" "POST /api/admin/mute"
adtop; adfind "Block"; sleep 2; shot AD-28-admin-block-confirm; adt "Block"; sleep 2; proof "block user" "\"blocked\":true"
adfind "Promote to super admin"; sleep 2; adt "Yes"; sleep 2; proof "promote" "\"role\":\"admin_super\""
shot AD-29-admin-user-after; key 4
adq AdminReports 5; shot AD-30-admin-reports; adt "✓ Resolve"; sleep 2; proof "resolve report (old page)" "PUT /api/reports"
adt "Ban"; sleep 2; adt "Ban"; sleep 2; proof "ban from report" "\"blocked\":true"; key 4
adq AdminVerify 5; shot AD-31-admin-verify; adt "Approve"; sleep 2; proof "approve verify" "PUT /api/verify-request"; key 4
adq AdminChannels 6; shot AD-32-admin-channels; adt "Verify"; sleep 2; proof "verify channel" "u-chan1"; key 4
adq AdminTelegram 6; shot AD-33-admin-telegram; adtap "@channelusername" 240 190; adtype "yidplustest"; adhidekb; adt "Add channel"; sleep 2; proof "add telegram channel" "POST /api/telegram-channels"; key 4
adq AdminBroadcast 5; shot AD-34-admin-broadcast; adtap "Message" 240 200; adtype "Shalom_everyone"; adhidekb; adt "Send now"; sleep 2; shot AD-35-broadcast-confirm; adt "Send"; sleep 3; proof "broadcast" "POST /api/broadcasts"; key 4
adq AdminBadWords 5; shot AD-36-admin-word-filter; adtap "Add a word" 180 205; adtype "testbadword"; adhidekb; adt "Add"; sleep 2; proof "add bad word" "POST /api/admin/bad-words"
adt "spamword"; sleep 2; proof "remove bad word" "DELETE /api/admin/bad-words"; key 4
adq AdminWeb 5; shot AD-37-admin-web-pin; adtype "1234"; sleep 1; timeout 10 adb shell input keyevent 66; sleep 4; adt "Unlock"; sleep 1; sleep 6; proof "admin pin" "/api/admin/verify-pin"; shot AD-38-admin-web-after-pin; key 4
check "AD admin"

log "---- AD: dark mode ----"
adb shell cmd uimode night yes; sleep 3
tab Profile; sleep 3; shot AD-40-settings-dark
adq Admin 5; shot AD-41-admin-dark; key 4
timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_NOTIFS >/dev/null 2>&1; sleep 4; shot AD-42-notifications-dark; key 4
adb shell cmd uimode night no; sleep 3
timeout 20 adb logcat -d -s YidAdminWeb 2>/dev/null | tail -15 | sed 's/^/AdminWeb: /' >> "$OUT/qa.log"
fi
# ==== [/AD] ====

# ==== [AD 2.1] every site admin panel natively: home (search + 5 categories) and one real action per panel ====
adx() { timeout 40 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_TAB --es tab Profile --es qa_page "x:Admin" --es qa_ad "$1" >/dev/null 2>&1; sleep "${2:-5}"; log "ad panel $1"; }
log "---- AD 2.1: admin home ----"
adq Admin 5; shot AD-50-home-glance; adfind "Overview"; sleep 1; shot AD-51-home-overview-open
adtop; adtap "Search admin…" 240 330; adtype "att"; adhidekb; sleep 1; shot AD-52-home-search; key 4
log "---- AD 2.1: Overview ----"
adx god-mode; shot AD-53-god-mode; key 4
adx calls; shot AD-54-calls; adt "Yanky → Leah"; sleep 1; adt "OK"; sleep 2; proof "end call" "\"action\":\"end\""; key 4
adx now; shot AD-55-now; key 4
adx analytics; shot AD-56-analytics; key 4
adx growth; shot AD-57-growth; key 4
adx leaderboard; shot AD-58-leaderboard; key 4
adx health; shot AD-59-storage; key 4
log "---- AD 2.1: People ----"
adx access-control; shot AD-60-access; key 4
adx invite-codes; shot AD-61-invites; key 4
adx warnings; shot AD-62-warnings; key 4
adx sessions; shot AD-63-sessions; key 4
adx banned-devices; shot AD-64-banned; key 4
adx badges; shot AD-65-badges; key 4
adx ad-exempt; shot AD-66-ad-free; key 4
adq AdminUser 6; adfind "Devices & IPs"; shot AD-67-user-more; key 4
log "---- AD 2.1: Moderation ----"
adx reports; shot AD-68-reports; adt "Moshe K"; sleep 1; shot AD-69-report-actions; adt "✓ Resolve"; sleep 2; proof "resolve report" "PUT /api/reports"; key 4
adx feedback; shot AD-70-feedback; key 4
adx support-chats; shot AD-71-support; key 4
adx support-thread; shot AD-72-support-thread; key 4
adx chat-watch; shot AD-73-chats; key 4
adx room-read; shot AD-74-room-read; key 4
adx shorts-mod; shot AD-75-shorts-mod; key 4
adx statuses-mod; shot AD-76-statuses-mod; key 4
log "---- AD 2.1: Content ----"
adx announcements; shot AD-77-announcements; adtap "Type announcement..." 240 200; adtype "Shalom_all"; adhidekb; adt "Post announcement"; sleep 1; adt "OK"; sleep 2; proof "announcement" "POST /api/admin/announcements"; key 4
adx broadcast; shot AD-78-broadcast-schedule; key 4
adx email-blast; shot AD-79-email-blast; key 4
adx ai; shot AD-80-ai; key 4
adx email-templates; shot AD-81-email-settings; key 4
adx featured; shot AD-82-featured; adt "⭐ Feature"; sleep 2; proof "feature post" "POST /api/admin/feature"; key 4
adx telegram; shot AD-83-telegram; adswipe; adswipe; shot AD-84-telegram-more; key 4
log "---- AD 2.1: System ----"
adx features; shot AD-85-automod; key 4
adx app-settings; shot AD-86-app; key 4
adx maintenance; shot AD-87-maintenance; key 4
adx antispam; shot AD-88-antispam; key 4
adx ads; shot AD-89-ads; key 4
adx security; shot AD-90-attacks; key 4
adx diagnostics; shot AD-91-health-check; key 4
adx code-errors; shot AD-92-code-errors; key 4
adx app-config; shot AD-93-config; key 4
adx ip-logs; shot AD-94-ip-logs; key 4
adx audit-logs; shot AD-95-audit-logs; key 4
adx export; shot AD-96-export; key 4
adx nuclear; shot AD-97-nuclear; key 4
adx admin-settings; shot AD-98-admin-settings; key 4
check "AD 2.1 admin panels"
# ==== [/AD 2.1] ====

# Helper NT (2.0.3): chat notifications like WhatsApp, proved with the QA fixture account (sourced by qa/signedin.sh fx_block,
# and by qa/nt-only.sh). A QA broadcast runs the REAL NotifyWorker check against fixture rooms (a DM with a photo, a group
# with a photo, a group without one), then, at 480x640 (Qin F21) and at 1080x2400: Reply typed + sent inside the group's
# own notification, Mark as read on the DM, the next checks, a tap on a notification. Each step is photographed; the
# notification records go to NT-notifs-*.txt and the writes the app would send to NT-fx.txt right after the steps.
# Never clears logcat (that wiped the other areas' YidFx lines before).
ntb() { timeout 30 adb shell am broadcast -a com.yidplus.app.QA_CHAT_NOTIFY -n $PKG/.ChatQaReceiver "$@" >/dev/null 2>&1; }
ntdump() {
  timeout 30 adb shell dumpsys notification --noredact 2>/dev/null | grep -E "NotificationRecord.*pkg=$PKG|android\.title=|android\.text=|android\.conversationTitle=|android\.isGroupConversation=|android\.largeIcon=|actions=|shortcut=| groupKey=" > "$OUT/NT-notifs-$1.txt"
  log "NT $1: chat notifications=$(grep -E "NotificationRecord.*pkg=$PKG" "$OUT/NT-notifs-$1.txt" | grep -c 'tag=chat ') summary=$(grep -E "NotificationRecord.*pkg=$PKG" "$OUT/NT-notifs-$1.txt" | grep -c 'tag=chat-summary') rooms: $(grep -oE 'shortcut=chat_[^ ]+' "$OUT/NT-notifs-$1.txt" | sort -u | tr '\n' ' ')texts: $(grep -oE 'android\.text=String \([^)]*\)' "$OUT/NT-notifs-$1.txt" | head -4 | tr '\n' ' ')"
}
NTN=0
ntui() { # ntui ROW_TEXT TARGET [look]: tap TARGET inside the notification that shows ROW_TEXT (1 = not on screen; look = don't tap)
  local p
  NTN=$((NTN + 1))
  timeout 30 adb shell uiautomator dump /sdcard/nt.xml >/dev/null 2>&1
  timeout 20 adb shell cat /sdcard/nt.xml > "$OUT/NT-ui-$(printf %02d $NTN).xml" 2>/dev/null
  p=$(python3 qa/nt_ui.py "$1" "$2" < "$OUT/NT-ui-$(printf %02d $NTN).xml")
  if [ -z "$p" ]; then log "NT: no \"$2\" in the \"$1\" notification on screen (ui $NTN)"; return 1; fi
  [ "${3:-}" = look ] && return 0
  case "$p" in swipe*) set -- $p; timeout 10 adb shell input swipe $2 $3 $2 $(( $3 + 300 )) 300;; *) timeout 10 adb shell input tap $p;; esac
  sleep 2; log "NT tapped \"$2\" in \"$1\" ($p, ui $NTN)"
}
ntrows() { timeout 30 adb shell uiautomator dump /sdcard/nt.xml >/dev/null 2>&1; log "NT shade rows: $(timeout 20 adb shell cat /sdcard/nt.xml 2>/dev/null | python3 qa/nt_ui.py x rows | tr '\n' ' ' | cut -c1-600)"; }
ntshade() { key 3; sleep 1; timeout 10 adb shell cmd statusbar expand-notifications; sleep 3; }
ntfind() { # ntfind ROW_TEXT TARGET: get TARGET inside that notification on screen (expand it / scroll the shade), then tap it
  local i
  for i in 1 2 3 4 5 6 7; do
    ntui "$1" "$2" && return 0
    [ $i = 7 ] && break
    if [ $((i % 2)) = 1 ]; then ntui "$1" expand || ntscroll
    else ntscroll; fi
  done
  return 1
}
ntscroll() { # one upward scroll INSIDE the shade's notification list (at 480x640 it is only ~150 px under the quick settings)
  local p
  timeout 30 adb shell uiautomator dump /sdcard/nt.xml >/dev/null 2>&1
  p=$(timeout 20 adb shell cat /sdcard/nt.xml 2>/dev/null | python3 qa/nt_ui.py x scroller)
  [ -z "$p" ] && p="$(( W / 2 )) $(( H * 2 / 3 )) $(( H / 3 ))"
  set -- $p; timeout 10 adb shell input swipe $1 $2 $1 $3 600; sleep 1.5; log "NT scrolled the shade list ($p)"
}
ntopen() { # ntopen ROW_TEXT: that notification expanded (Reply visible), for the photo
  ntui "$1" Reply look && return 0
  ntui "$1" expand; sleep 1; ntui "$1" Reply look && return 0
  ntscroll; ntui "$1" Reply look
}
nt_round() { # nt_round TAG (480 or 1080)
  local S="$1"
  log "---- NT round $S ----"
  ntb --es mode reset; sleep 3; ntshade; key 4; sleep 1
  ntb --es mode poll --es rooms "/api/chat/rooms?qa=nt"; sleep 3; shot NT-$S-00-heads-up
  ntui "Family" Reply look && log "NT $S heads-up shows Reply" || true; sleep 9
  ntdump $S-1-posted
  ntshade; shot NT-$S-01-shade; ntrows
  # the group's own notification, expanded: group photo, each sender with photo/initial, Reply + Mark as read
  ntopen "Family" || true; shot NT-$S-02-group-expanded
  ntshade; ntopen "Yanky" || true; shot NT-$S-02b-dm-expanded
  # inline Reply: really sends (POST /api/chat) and my line joins the notification
  if ntfind "Family" "Reply"; then
    sleep 2; timeout 10 adb shell input text "Thanks%sI%swill%sbe%sthere"; sleep 1; shot NT-$S-03-reply-typed
    ntui x send || key 66; sleep 8; shot NT-$S-04-reply-sent
  else log "NT FAIL $S: no Reply button reachable in the group notification"; fi
  ntdump $S-2-after-reply
  if [ "$S" = 480 ]; then # a reply that fails shows "Not sent" with Retry; Retry sends it
    ntb --es mode failnext; sleep 1; ntshade
    if ntfind "Python" "Reply"; then sleep 2; timeout 10 adb shell input text "Second%smessage"; sleep 1; ntui x send || key 66; sleep 8; shot NT-$S-05-not-sent-retry
      ntfind "Python" "Retry" && { sleep 8; shot NT-$S-06-retry-sent; }; fi
    ntdump $S-3-after-retry
  fi
  # Mark as read on the DM: POST /api/chat/read and the notification goes away
  ntshade
  if ntfind "Yanky" "Mark as read"; then sleep 6; shot NT-$S-07-after-mark-read; else log "NT FAIL $S: no Mark as read reachable in the DM notification"; fi
  ntdump $S-4-after-read
  # the next checks must not bring it back (the fixture still says unread, like a spectated room or a failed read call)
  ntb --es mode poll --es rooms "/api/chat/rooms?qa=nt"; sleep 10
  ntb --es mode forget; sleep 1
  ntb --es mode poll --es rooms "/api/chat/rooms?qa=nt"; sleep 10
  ntdump $S-5-after-poll-again
  ntshade; shot NT-$S-08-after-poll-again
  # tap a notification: that chat opens (and its notification is cleared)
  ntfind "Family" body && { sleep 7; shot NT-$S-09-tap-opens-chat; }
  ntdump $S-6-after-open
  key 4; sleep 1; key 4; sleep 1
}
log "---- NT chat notifications ----"
timeout 10 adb logcat -G 16M >/dev/null 2>&1 || true
W=480; H=640; nt_round 480
size 1080x2400 420; restart 5; W=1080; H=2400; nt_round 1080
size 480x640 240; H=640
timeout 30 adb logcat -d -s YidFx > "$OUT/NT-fx.txt" 2>/dev/null
timeout 30 adb logcat -d -s YidNotify > "$OUT/NT-yidnotify.txt" 2>/dev/null
log "NT YidFx lines: $(grep -c YidFx "$OUT/NT-fx.txt"); YidNotify lines: $(wc -l < "$OUT/NT-yidnotify.txt")"
grep -E 'POST /api/chat(/read)? ' "$OUT/NT-fx.txt" | grep -E '"room_id":"nt-' | sed 's/^/NT fx: /' | while read -r l; do log "$l"; done
log "NT check: reply POSTs=$(grep -c 'POST /api/chat {"room_id":"nt-g1","type":"text","text":"Thanks I will be there"}' "$OUT/NT-fx.txt") (want 2), read POSTs dm1=$(grep -c 'POST /api/chat/read {"room_id":"nt-dm1"}' "$OUT/NT-fx.txt") (want 2)"
for S in 480 1080; do log "NT check $S: dm1 shown after mark-read=$(grep -c 'shortcut=chat_nt-dm1' "$OUT/NT-notifs-$S-4-after-read.txt"), after polls=$(grep -c 'shortcut=chat_nt-dm1' "$OUT/NT-notifs-$S-5-after-poll-again.txt") (want 0, 0); summary rows=$(grep -c 'tag=chat-summary' "$OUT/NT-notifs-$S-1-posted.txt") (want 0)"; done
ntb --es mode reset; sleep 2

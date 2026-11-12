# Helper NT: chat notifications like WhatsApp, proved with the QA fixture account (sourced by qa/signedin.sh fx_block).
# A QA broadcast runs the REAL NotifyWorker check against fixture rooms (one DM with a photo, one group with a photo,
# one group whose chat can't be read -> built from the list preview), then Reply / Not sent + Retry / Mark as read /
# poll again / tap, each photographed, with the notification records saved to qa/out/NT-notifs-*.txt.
ntb() { timeout 30 adb shell am broadcast -a com.yidplus.app.QA_CHAT_NOTIFY -n $PKG/.ChatQaReceiver "$@" >/dev/null 2>&1; }
ntdump() {
  timeout 30 adb shell dumpsys notification --noredact 2>/dev/null | grep -E "NotificationRecord.*pkg=$PKG|android\.title=|android\.text=|android\.conversationTitle=|android\.isGroupConversation=|android\.largeIcon=|actions=|shortcut=" > "$OUT/NT-notifs-$1.txt"
  log "NT $1: chat notifications=$(grep -E "NotificationRecord.*pkg=$PKG" "$OUT/NT-notifs-$1.txt" | grep -c 'tag=chat ') summary=$(grep -E "NotificationRecord.*pkg=$PKG" "$OUT/NT-notifs-$1.txt" | grep -c 'tag=chat-summary') titles: $(grep -oE 'android\.title=String \([^)]*\)' "$OUT/NT-notifs-$1.txt" | head -6 | tr '\n' ' ')"
}
ntap() { # ntap ATTR VALUE: tap the first node on screen whose ATTR is exactly VALUE (1 = not found)
  local a="$1" v="$2" b
  timeout 30 adb shell uiautomator dump /sdcard/nt.xml >/dev/null 2>&1
  b=$(timeout 20 adb shell cat /sdcard/nt.xml 2>/dev/null | tr '>' '\n' | grep -E "$a=\"$v\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -z "$b" ]; then log "NT: no $a=\"$v\" on screen"; return 1; fi
  set -- $b; timeout 10 adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5; log "NT tapped $a=\"$v\""
}
ntshade() { key 3; sleep 1; timeout 10 adb shell cmd statusbar expand-notifications; sleep 3; }
ntsend() { ntap resource-id "com.android.systemui:id/remote_input_send" || key 66; }

log "---- NT chat notifications ----"
timeout 10 adb logcat -c 2>/dev/null
ntb --es mode reset; sleep 2
ntb --es mode poll --es rooms "/api/chat/rooms?qa=nt"; sleep 15
ntdump 1-posted
ntshade; shot NT-01-shade-collapsed
ntap content-desc "Expand" || true; sleep 2; shot NT-02-shade-expanded
ntap content-desc "Expand" || true; sleep 2; shot NT-02b-second-expanded
# inline Reply: really sends (POST /api/chat) and the sent line joins the notification
ntap text "Reply" && { sleep 2; timeout 10 adb shell input text "Thanks%sI%swill%sbe%sthere"; sleep 1; shot NT-03-reply-typed; ntsend; sleep 7; shot NT-04-reply-sent; }
ntdump 2-after-reply
# a reply that fails shows "Not sent" with Retry; Retry sends it
ntb --es mode failnext; sleep 1
ntshade
ntap text "Reply" && { sleep 2; timeout 10 adb shell input text "Second%smessage"; sleep 1; ntsend; sleep 7; shot NT-05-not-sent-retry; }
ntap text "Retry" && { sleep 7; shot NT-06-retry-sent; }
ntdump 3-after-retry
# Mark as read: POST /api/chat/read and the notification goes away
ntshade
ntap text "Mark as read" && { sleep 6; shot NT-07-after-mark-read; }
ntdump 4-after-read
# the next checks must not bring it back (the fixture still says unread, like a spectated room or a failed read call)
ntb --es mode poll --es rooms "/api/chat/rooms?qa=nt"; sleep 12
ntb --es mode forget; sleep 1
ntb --es mode poll --es rooms "/api/chat/rooms?qa=nt"; sleep 12
ntdump 5-after-poll-again
ntshade; shot NT-08-after-poll-again
# tap a notification: that chat opens (and its notification is cleared)
ntap text "Yanky Weiss" && { sleep 6; shot NT-09-tap-opens-chat; }
ntdump 6-after-open
timeout 20 adb logcat -d -s YidNotify > "$OUT/NT-yidnotify.txt" 2>/dev/null; log "NT YidNotify log lines: $(wc -l < "$OUT/NT-yidnotify.txt")"
key 4; sleep 1; key 4; sleep 1
ntb --es mode reset; sleep 2

# Helper CH (chats, 2.0.2): signed-in chat QA with the fixture account. Sourced by qa/signedin.sh fx_block (helpers from qa/native.sh).
# Every write is answered locally and logged (tag YidFx); qa/out/fx-requests.txt is the proof each action matches the site's chat.js call.
chpos() { # centre of the first node whose text or content-desc CONTAINS $1 (prints "x y", or nothing)
  timeout 30 adb shell uiautomator dump /sdcard/ch.xml >/dev/null 2>&1
  local b; b=$(timeout 20 adb shell cat /sdcard/ch.xml 2>/dev/null | tr '>' '\n' | grep -F -- "$1" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$b" ] && return 0; set -- $b; echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}
chhas() { local p; p=$(chpos "$1"); if [ -n "$p" ]; then log "CH ok: '$1' on screen"; else log "CH MISSING: '$1' not on screen"; fi; }
chlong() { local p; p=$(chpos "$1"); [ -z "$p" ] && { log "chlong: '$1' not found"; return 0; }; set -- $p; adb shell input swipe $1 $2 $1 $2 900; sleep 2; log "long-press $*"; }
chopen() { # chopen ID TYPE NAME-JSON-ESCAPED [extra json]
  timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '{\"id\":\"$1\",\"type\":\"$2\",\"nick\":\"$3\"$4}'" >/dev/null 2>&1; sleep "${5:-5}"; log "open chat $1"; }
chfx() { timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -F -- "$1" | tail -1 | cut -c1-400 | sed 's/^/  YidFx: /' | tee -a "$OUT/qa.log" >/dev/null; }
chkb() { log "keyboard shown after $1: $(timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -o 'mInputShown=[a-z]*' | head -1)"; }
adb shell pm grant $PKG android.permission.RECORD_AUDIO 2>/dev/null

# 1-3, 5, 6, 13: the chat list (previews with sender, no spinner, chips fit, rows, the green new-chat button)
view "https://yidplus.com/chat"; sleep 8; shot CH-01-chat-list
timeout 20 adb shell uiautomator dump /sdcard/chl.xml >/dev/null 2>&1
log "CH list texts: $(timeout 20 adb shell cat /sdcard/chl.xml 2>/dev/null | grep -oE ' text="[^"]+"' | head -60 | tr '\n' ' ')"
adb shell input swipe 420 125 40 125 400; sleep 1; shot CH-02-chips-scrolled
adb shell input swipe 240 450 240 150 400; sleep 1; shot CH-02b-list-scrolled; adb shell input swipe 240 150 240 600 300; sleep 1
tapui "Start new chat"; sleep 2; shot CH-03-fab-menu
tapui "New group"; sleep 3; adb shell input text "QA%sGroup"; sleep 1; shot CH-03b-new-group; tapui "Create"; sleep 3; chfx "/api/chat/rooms"; key 4; sleep 1; key 4; sleep 1
view "https://yidplus.com/chat"; sleep 3; tapui "Start new chat"; sleep 2; tapui "New chat"; sleep 4; shot CH-03c-new-chat; tapui "Dovid New"; sleep 3; chfx "other_user_id"; key 4; sleep 1; key 4; sleep 1
# 10: search closes the keyboard
view "https://yidplus.com/chat"; sleep 3; tapui "Search chats"; sleep 1; adb shell input text "Yo"; sleep 2; chkb "typing in search"; key 4; sleep 2; chkb "Back from search"; shot CH-04-search-closed

# 1: instant open (second open is from the cache), 4/24: every message type, call rows centred, date chips; 22: typing in the header
chopen r-g1 group "\u05d7\u05d1\u05e8\u05d9\u05dd \u05e4\u05d5\u05df \u05e9\u05d5\u05dc" "" 6; shot CH-05-group-bottom; chhas "typing"; chhas "Missed voice call"; chfx "/api/chat/read"
adb shell input swipe 240 220 240 560 300; sleep 1; shot CH-05b-group-up; adb shell input swipe 240 220 240 560 300; sleep 1; shot CH-05c-group-top; key 4; sleep 1
chopen r-g1 group "\u05d7\u05d1\u05e8\u05d9\u05dd \u05e4\u05d5\u05df \u05e9\u05d5\u05dc" "" 0; sleep 0.3; shot CH-06-group-reopen-instant; sleep 2; key 4; sleep 1
chopen r-g2 group "Family" ",\"photo_url\":\"/api/media/user-avatars/392d8eda-e0dd-44ea-a51e-f55ead12fb0c_1785879385829.png\"" 6; shot CH-07-group-photo-header; key 4; sleep 1
chopen r-dm1 private "Yossi Klein" ",\"other_user_id\":\"u-yossi\"" 6; shot CH-08-dm-online-ticks-reacts; key 4; sleep 1
chopen r-dm2 private "Yanky Weiss" ",\"other_user_id\":\"u-yanky\"" 6; shot CH-09-dm-last-seen-voice; key 4; sleep 1
# 8: an owner viewing someone else's private chat; a group I'm not in
chopen r-spy private "Moshe Leib" ",\"other_user_id\":\"u-leib\"" 6; shot CH-10-admin-spectating; chhas "Viewing as Admin"; chhas "Join group"; key 4; sleep 1
chopen r-g4 group "Yeshiva" "" 6; shot CH-11-not-joined-group; tapui "Join group"; sleep 2; chfx "/api/chat/join"; key 4; sleep 1

# Link cards, forwarded, deleted, view once, music; swipe to reply; long-press actions; send
chopen r-links private "Berel Links" ",\"other_user_id\":\"u-berel\"" 7; shot CH-12-links-cards
adb shell input swipe 240 220 240 560 300; sleep 2; shot CH-12b-links-up; adb shell input swipe 240 220 240 560 300; sleep 2; shot CH-12c-links-top
adb shell input swipe 240 560 240 120 300; sleep 1; adb shell input swipe 240 560 240 120 300; sleep 1
p=$(chpos "See you at 8 tonight"); log "bubble at: $p"
if [ -n "$p" ]; then set -- $p; adb shell input swipe $(( $1 - 60 )) $2 $(( $1 + 160 )) $2 350; sleep 1; shot CH-13-swipe-reply; chhas "Reply to"; fi
adb shell input text "Swipe%sreply%sOK"; sleep 1; tapui "Send"; sleep 1; shot CH-13b-swipe-reply-sent; chfx "reply_to_id"
chlong "See you at 8 tonight"; shot CH-14-long-press-menu; tapui "Reply"; sleep 1; adb shell input text "Menu%sreply"; tapui "Send"; sleep 1; chfx "Menu reply"
chlong "See you at 8 tonight"; tapui "👍"; sleep 2; chfx "/api/chat/reactions"
chlong "See you at 8 tonight"; tapui "Forward"; sleep 3; shot CH-15-forward; tapui "Yossi Klein"; sleep 3; chfx "forwarded"; key 4; sleep 1
chlong "See you at 8 tonight"; tapui "Pin"; sleep 2; chfx "pinned_message_id"; shot CH-16-pinned
chlong "Swipe reply OK"; adb shell input swipe 240 520 240 200 300; sleep 1; tapui "Delete"; sleep 1; tapui "Yes"; sleep 2; chfx "DELETE /api/chat?id="
adb shell input text "Hello%sfrom%sthe%sapp"; sleep 1; tapui "Send"; sleep 1; shot CH-17-sent-text; chfx "Hello from the app"
p=$(chpos "/api/media/user-avatars/392d8eda"); [ -z "$p" ] && p=$(chpos "Photo")
if [ -n "$p" ]; then set -- $p; adb shell input tap $1 $2; sleep 3; shot CH-18-photo-viewer; key 4; sleep 1; fi
# Photo from the gallery picker, and a voice note
adb push "$OUT/CH-01-chat-list.png" /sdcard/Pictures/ch_test.png >/dev/null 2>&1; adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/ch_test.png >/dev/null 2>&1
tapui "Attach"; sleep 2; shot CH-19-attach-sheet; tapui "Gallery"; sleep 4; shot CH-19b-picker; tapui "ch_test.png"; sleep 5; shot CH-19c-photo-sent; chfx "type=media"
tapui "Record voice message"; sleep 4; shot CH-20-recording; tapui "Send voice message"; sleep 4; chfx "type=voice"; key 4; sleep 1
adb shell cmd uimode night yes; sleep 3; chopen r-links private "Berel Links" ",\"other_user_id\":\"u-berel\"" 5; shot CH-21-links-dark; key 4; sleep 1
view "https://yidplus.com/chat"; sleep 3; shot CH-22-list-dark; adb shell cmd uimode night no; sleep 2
log "CH perf: $(timeout 20 adb logcat -d -s YidPerf 2>/dev/null | grep -E 'Chat' | tail -6 | tr '\n' ' ')"
log "CH all writes: $(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -c YidFx)"

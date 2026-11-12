# Helper CH (chats, 2.0.2): signed-in chat QA with the fixture account. Sourced by qa/signedin.sh fx_block (helpers from qa/native.sh).
# Run after the shared fixture block. Every write is answered locally and logged (tag YidFx); qa/out/fx-requests.txt is the proof each action matches the site's chat.js call.
chpos() { # centre of the node whose text is exactly $1 (else whose text/desc contains it); prints "x y"; the matched node goes to the log
  timeout 30 adb shell uiautomator dump /sdcard/ch.xml >/dev/null 2>&1
  local x n b; x=$(timeout 20 adb shell cat /sdcard/ch.xml 2>/dev/null | tr '>' '\n')
  # screens underneath (the chat list behind an open chat) are in the dump too: take the tallest match, not the first
  n=$(echo "$x" | grep -E -- "(text|content-desc)=\"$(printf '%s' "$1" | sed 's/[][\.*^$/]/\\&/g')\"" | awk 'match($0,/bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/){s=substr($0,RSTART,RLENGTH); gsub(/[^0-9]+/," ",s); split(s,a," "); h=a[4]-a[2]; if(h>best){best=h; line=$0}} END{print line}')
  [ -z "$n" ] && n=$(echo "$x" | grep -F -- "$1" | awk 'match($0,/bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/){s=substr($0,RSTART,RLENGTH); gsub(/[^0-9]+/," ",s); split(s,a," "); h=a[4]-a[2]; if(h>best){best=h; line=$0}} END{print line}')
  [ -z "$n" ] && return 0
  echo "  node: $(echo "$n" | grep -oE '(text|content-desc|class|bounds)="[^"]*"' | tr '\n' ' ' | cut -c1-240)" >> "$OUT/qa.log"
  b=$(echo "$n" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$b" ] && return 0; set -- $b; echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}
chtap() { # tap the tallest node whose text/desc is exactly $1 (else contains it); tapui of native.sh is only defined after fx_block runs
  local p; p=$(chpos "$1"); if [ -z "$p" ]; then log "chtap: '$1' not on screen"; return 0; fi
  set -- $p; timeout 10 adb shell input tap $1 $2; sleep 1.5; log "chtap $1 $2"
}
chnot() { local p; p=$(chpos "$1"); if [ -n "$p" ]; then log "CH WRONG: '$1' is on screen"; else log "CH ok: '$1' not shown"; fi; }
chhas() { local p; p=$(chpos "$1"); if [ -n "$p" ]; then log "CH ok: '$1' on screen"; else log "CH MISSING: '$1' not on screen"; fi; }
chlong() { sleep 1; local p; p=$(chpos "$1"); [ -z "$p" ] && { log "chlong: '$1' not found"; return 0; }; set -- $p; adb shell input swipe $1 $2 $1 $2 900; sleep 2; log "long-press $*"; }
chopen() { # chopen ID TYPE NAME-JSON-ESCAPED [extra json]
  timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '{\"id\":\"$1\",\"type\":\"$2\",\"nick\":\"$3\"$4}'" >/dev/null 2>&1; sleep "${5:-5}"; log "open chat $1"; }
chfx() { timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -F -- "$1" | tail -1 | cut -c1-400 | sed 's/^/  YidFx: /' | tee -a "$OUT/qa.log" >/dev/null; }
chkb() { log "keyboard shown after $1: $(timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -o 'mInputShown=[a-z]*' | head -1)"; }
adb shell pm grant $PKG android.permission.RECORD_AUDIO 2>/dev/null
for ime in com.android.inputmethod.latin com.google.android.inputmethod.latin; do adb shell pm grant $ime android.permission.READ_CONTACTS 2>/dev/null; done # the keyboard's contacts prompt otherwise covers the screen

# 1-3, 5, 6, 13: the chat list (previews with sender, no spinner, chips fit, rows, the green new-chat button)
view "https://yidplus.com/chat"; sleep 8; shot CH-01-chat-list
timeout 20 adb shell uiautomator dump /sdcard/chl.xml >/dev/null 2>&1
log "CH list texts: $(timeout 20 adb shell cat /sdcard/chl.xml 2>/dev/null | grep -oE ' text="[^"]+"' | head -60 | tr '\n' ' ')"
adb shell input swipe 420 125 40 125 400; sleep 1; shot CH-02-chips-scrolled
adb shell input swipe 240 450 240 150 400; sleep 1; shot CH-02b-list-scrolled; adb shell input swipe 240 150 240 600 300; sleep 1
chtap "Start new chat"; sleep 2; shot CH-03-fab-menu
chtap "New group"; sleep 3; chtap "Group name"; sleep 1; adb shell input text "QA%sGroup"; sleep 1; shot CH-03b-new-group; chtap "Create"; sleep 3; chfx "/api/chat/rooms"; key 4; sleep 1; key 4; sleep 1
restart 5; view "https://yidplus.com/chat"; sleep 4; chtap "Start new chat"; sleep 2; chtap "New chat"; sleep 4; key 111; sleep 1; shot CH-03c-new-chat; chtap "Dovid New"; sleep 3; chfx "other_user_id"; key 4; sleep 1; key 4; sleep 1
# 10: search closes the keyboard
restart 5; view "https://yidplus.com/chat"; sleep 3; chtap "Search chats"; sleep 1; adb shell input text "Yo"; sleep 2; chkb "typing in search"; key 4; sleep 2; chkb "Back from search"; shot CH-04-search-closed

# 1: instant open (second open is from the cache), 4/24: every message type, call rows centred, date chips; 22: typing in the header
chopen r-g1 group "\u05d7\u05d1\u05e8\u05d9\u05dd \u05e4\u05d5\u05df \u05e9\u05d5\u05dc" "" 6; shot CH-05-group-bottom; chhas "typing"; chfx "/api/chat/read"
adb shell input swipe 240 220 240 560 300; sleep 1; shot CH-05b-group-up; adb shell input swipe 240 220 240 560 300; sleep 1; shot CH-05c-group-top; key 4; sleep 1
chopen r-g1 group "\u05d7\u05d1\u05e8\u05d9\u05dd \u05e4\u05d5\u05df \u05e9\u05d5\u05dc" "" 0; sleep 0.3; shot CH-06-group-reopen-instant; sleep 2; key 4; sleep 1
chopen r-g2 group "Family" ",\"photo_url\":\"/api/media/user-avatars/392d8eda-e0dd-44ea-a51e-f55ead12fb0c_1785879385829.png\"" 6; shot CH-07-group-photo-header; chhas "Group voice call"; key 4; sleep 1
chopen r-dm4 private "Shaya" ",\"other_user_id\":\"u-shaya\"" 5; shot CH-07b-dm-missed-call; chhas "Missed voice call"; key 4; sleep 1
chopen r-dm1 private "Yossi Klein" ",\"other_user_id\":\"u-yossi\"" 6; shot CH-08-dm-online-ticks-reacts; key 4; sleep 1
chopen r-dm2 private "Yanky Weiss" ",\"other_user_id\":\"u-yanky\"" 6; shot CH-09-dm-last-seen-voice; key 4; sleep 1
# 8: an owner viewing someone else's private chat; a group I'm not in
chopen r-spy private "Moshe Leib" ",\"other_user_id\":\"u-leib\"" 6; shot CH-10-admin-spectating; chhas "Viewing as Admin"; chnot "Join group"; key 4; sleep 1
chopen r-g4 group "Yeshiva" "" 6; shot CH-11-not-joined-group; chtap "Join group"; sleep 2; chfx "/api/chat/join"; key 4; sleep 1

# Link cards, forwarded, deleted, view once, music; swipe to reply; long-press actions; send
chopen r-links private "Berel Links" ",\"other_user_id\":\"u-berel\"" 7; shot CH-12-links-cards
adb shell input swipe 240 220 240 560 300; sleep 2; shot CH-12b-links-up; adb shell input swipe 240 220 240 560 300; sleep 2; shot CH-12c-links-top
key 4; sleep 1
# The typing flows run on the phone size: at 480x640 the on-screen keyboard leaves no room at all (kosher 480x640 phones type on their keypad).
size 1080x2400 420
chopen r-links private "Berel Links" ",\"other_user_id\":\"u-berel\"" 7
sleep 2; p=$(chpos "See you at 8 tonight"); log "bubble at: $p"
if [ -n "$p" ]; then set -- $p; adb shell input swipe $(( $1 - 40 )) $2 $(( $1 + 200 )) $2 400; sleep 1; shot CH-13-swipe-reply; chhas "Reply to"; fi
adb shell input text "Swipe%sreply%sOK"; sleep 1; key 111; sleep 1; chtap "Send"; sleep 1; shot CH-13b-swipe-reply-sent; chfx "reply_to_id"
chlong "See you at 8 tonight"; shot CH-14-long-press-menu; chtap "Reply"; sleep 1; adb shell input text "Menu%sreply"; sleep 1; key 111; sleep 1; chtap "Send"; sleep 1; chfx "Menu reply"
chlong "See you at 8 tonight"; chtap "&#128077;"; sleep 2; shot CH-14b-reacted; chfx "/api/chat/reactions"
chlong "See you at 8 tonight"; chtap "Forward"; sleep 3; shot CH-15-forward; chtap "Yossi Klein"; sleep 3; chfx "forwarded"; key 4; sleep 1
chlong "See you at 8 tonight"; chtap "Pin"; sleep 2; chfx "pinned_message_id"; shot CH-16-pinned
adb shell input text "Hello%sfrom%sthe%sapp"; sleep 1; key 111; sleep 1; chtap "Send"; sleep 1; shot CH-17-sent-text; chfx "Hello from the app"
# (the fixture answers a send without the message, so a just-sent line leaves on the next pull: delete is shown on the friend's line, as owner)
chlong "See you at 8 tonight"; chtap "Delete"; sleep 1; shot CH-17b-delete-confirm; chtap "Yes"; sleep 2; chfx "DELETE /api/chat"
p=$(chpos "/api/media/user-avatars/392d8eda"); [ -z "$p" ] && p=$(chpos "Photo")
if [ -n "$p" ]; then set -- $p; adb shell input tap $1 $2; sleep 3; shot CH-18-photo-viewer; key 4; sleep 1; fi
# Photo from the gallery picker, and a voice note
adb push "$OUT/CH-01-chat-list.png" /sdcard/Pictures/ch_test.png >/dev/null 2>&1; adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/ch_test.png >/dev/null 2>&1
key 111; sleep 1; chtap "Attach"; sleep 2; shot CH-19-attach-sheet; chtap "Gallery"; sleep 4; shot CH-19b-picker; chtap "ch_test.png"; sleep 5; shot CH-19c-photo-sent; chfx "type=media"
chtap "Record voice message"; sleep 4; shot CH-20-recording; chtap "Send voice message"; sleep 4; chfx "type=voice"; key 4; sleep 1
size 480x640 240
adb shell cmd uimode night yes; sleep 3; chopen r-links private "Berel Links" ",\"other_user_id\":\"u-berel\"" 5; shot CH-21-links-dark; key 4; sleep 1
view "https://yidplus.com/chat"; sleep 3; shot CH-22-list-dark; adb shell cmd uimode night no; sleep 2
log "CH perf: $(timeout 20 adb logcat -d -s YidPerf 2>/dev/null | grep -E 'Chat' | tail -6 | tr '\n' ' ')"
log "CH all writes: $(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -c YidFx)"

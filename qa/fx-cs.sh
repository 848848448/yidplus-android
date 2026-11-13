# qa/fx-cs.sh — helper CS (YID PLUS 2.1): the open chat. Sourced by qa/signedin.sh fx_block (helpers from qa/native.sh).
# Run after the shared fixture block. Every write is answered locally and logged (tag YidFx); qa/out/fx-requests.txt is the proof each action matches the site's chat.js call.
cspos() { # centre of the node whose text is exactly $1 (else whose text/desc contains it); prints "x y"; the matched node goes to the log
  timeout 30 adb shell uiautomator dump /sdcard/cs.xml >/dev/null 2>&1
  local x n b; x=$(timeout 20 adb shell cat /sdcard/cs.xml 2>/dev/null | tr '>' '\n')
  # screens underneath (the chat list behind an open chat) are in the dump too: take the tallest match, not the first
  n=$(echo "$x" | grep -E -- "(text|content-desc)=\"$(printf '%s' "$1" | sed 's/[][\.*^$/]/\\&/g')\"" | awk 'match($0,/bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/){s=substr($0,RSTART,RLENGTH); gsub(/[^0-9]+/," ",s); split(s,a," "); h=a[4]-a[2]; if(h>best){best=h; line=$0}} END{print line}')
  [ -z "$n" ] && n=$(echo "$x" | grep -F -- "$1" | awk 'match($0,/bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/){s=substr($0,RSTART,RLENGTH); gsub(/[^0-9]+/," ",s); split(s,a," "); h=a[4]-a[2]; if(h>best){best=h; line=$0}} END{print line}')
  [ -z "$n" ] && return 0
  echo "  node: $(echo "$n" | grep -oE '(text|content-desc|class|bounds)="[^"]*"' | tr '\n' ' ' | cut -c1-240)" >> "$OUT/qa.log"
  b=$(echo "$n" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$b" ] && return 0; set -- $b; echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}
cstap() { # tap the tallest node whose text/desc is exactly $1 (else contains it); tapui of native.sh is only defined after fx_block runs
  local p; p=$(cspos "$1"); if [ -z "$p" ]; then log "cstap: '$1' not on screen"; return 0; fi
  set -- $p; timeout 10 adb shell input tap $1 $2; sleep 1.5; log "cstap $1 $2"
}
csnot() { local p; p=$(cspos "$1"); if [ -n "$p" ]; then log "CS WRONG: '$1' is on screen"; else log "CS ok: '$1' not shown"; fi; }
cshas() { local p; p=$(cspos "$1"); if [ -n "$p" ]; then log "CS ok: '$1' on screen"; else log "CS MISSING: '$1' not on screen"; fi; }
cslong() { sleep 1; local p; p=$(cspos "$1"); [ -z "$p" ] && { log "cslong: '$1' not found"; return 0; }; set -- $p; adb shell input swipe $1 $2 $1 $2 900; sleep 2; log "long-press $*"; }
csopen() { # csopen ID TYPE NAME-JSON-ESCAPED [extra json]
  timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '{\"id\":\"$1\",\"type\":\"$2\",\"nick\":\"$3\"$4}'" >/dev/null 2>&1; sleep "${5:-5}"; log "open chat $1"; }
csfx() { timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -F -- "$1" | tail -1 | cut -c1-400 | sed 's/^/  YidFx: /' | tee -a "$OUT/qa.log" >/dev/null; }
cskb() { log "keyboard shown after $1: $(timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -o 'mInputShown=[a-z]*' | head -1)"; }
csdbl() { local p; p=$(cspos "$1"); [ -z "$p" ] && { log "csdbl: '$1' not found"; return 0; }; set -- $p; adb shell "input tap $1 $2 & sleep 0.12; input tap $1 $2"; sleep 2; log "double-tap $*"; }
csmic() { cspos "Record voice message"; }
csplaylast() { # tap the LOWEST "Play" node on screen: the newest voice bubble in the open chat
  timeout 30 adb shell uiautomator dump /sdcard/cs.xml >/dev/null 2>&1
  local b; b=$(timeout 20 adb shell cat /sdcard/cs.xml 2>/dev/null | tr '>' '\n' | grep -E -- '(text|content-desc)="Play"' | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ' | awk '{for(i=1;i+3<=NF;i+=4){cx=($i+$(i+2))/2; cy=($(i+1)+$(i+3))/2; if(cy>my){my=cy; bx=cx; by=cy}}} END{if(my)print bx" "by}')
  [ -z "$b" ] && { log "CS WRONG: no Play node after reopen (voice bubble not reachable)"; FAIL=1; return 0; }
  set -- $b; timeout 10 adb shell input tap $1 $2; sleep 1.5; log "csplaylast $1 $2"
  # determinism: the tapped bubble must flip to playing state (desc "Pause") at the SAME bounds
  timeout 30 adb shell uiautomator dump /sdcard/cs2.xml >/dev/null 2>&1
  local pb; pb=$(timeout 20 adb shell cat /sdcard/cs2.xml 2>/dev/null | tr '>' '\n' | grep -E -- 'content-desc="Pause"' | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ' | awk -v tx="$1" -v ty="$2" '{for(i=1;i+3<=NF;i+=4){cx=($i+$(i+2))/2; cy=($(i+1)+$(i+3))/2; dx=cx-tx; if(dx<0)dx=-dx; dy=cy-ty; if(dy<0)dy=-dy; if(dx<=40 && dy<=40)ok=1}} END{if(ok)print "ok"}')
  [ -z "$pb" ] && { log "CS WRONG: tapped Play at $1 $2 did not enter playing state (no Pause at same bounds)"; FAIL=1; return 0; }
  log "csplaylast verified playing at $1 $2"
}
csfind() { local n=0; while [ $n -lt ${2:-6} ]; do [ -n "$(cspos "$1")" ] && return 0; adb shell input swipe 240 260 240 470 400; sleep 1; n=$((n+1)); done; log "csfind: '$1' not found"; }
csfindup() { local n=0; while [ $n -lt ${2:-6} ]; do [ -n "$(cspos "$1")" ] && return 0; adb shell input swipe 240 470 240 260 400; sleep 1; n=$((n+1)); done; log "csfindup: '$1' not found"; }
adb shell pm grant $PKG android.permission.RECORD_AUDIO 2>/dev/null
adb shell pm grant $PKG android.permission.CAMERA 2>/dev/null
csrec() { (adb shell screenrecord --size 480x640 --bit-rate 2000000 --time-limit "${2:-30}" "/sdcard/$1.mp4" >/dev/null 2>&1 &); sleep 1; log "screenrecord $1 started"; }
cspull() { sleep 2; timeout 30 adb pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null 2>&1 && log "video $OUT/$1.mp4 $(stat -c %s "$OUT/$1.mp4" 2>/dev/null) bytes"; }
CSR='{"id":"r-cs","type":"group","nick":"CS QA","unread":5,"members":3}'
csroom() { timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '$CSR'" >/dev/null 2>&1; sleep "${1:-6}"; log "open chat r-cs"; }

csrec CS-flow-touch 30
# 6: opens at the first unread message under "5 unread messages"; formatting, @mention, bare domain, music card, voice, polls
csroom 7; shot CS-01-open-unread-line; cshas "5 unread messages"
# keyboard up: real IME over the open chat, composer proportions with the keyboard visible
adb shell settings put secure show_ime_with_hard_keyboard 1
cstap "Message"; sleep 1; cskb "composer tap"; shot CS-24-keyboard-up
# CS-24 hard checks with the IME up at 480x640: composer text not clipped, and at least one message stays visible above the composer.
timeout 20 adb shell uiautomator dump /sdcard/cs24.xml >/dev/null 2>&1
timeout 20 adb shell cat /sdcard/cs24.xml > "$OUT/CS-24-keyboard-up.xml" 2>/dev/null
CS24=$(tr '>' '\n' < "$OUT/CS-24-keyboard-up.xml" 2>/dev/null)
MT=$(echo "$CS24" | sed -n 's/.*text="Message".*bounds="\[[0-9]*,\([0-9]*\)\]\[[0-9]*,\([0-9]*\)\]".*/\1 \2/p' | head -1)
if [ -z "$MT" ]; then log "CS WRONG: composer not found with the keyboard up"; FAIL=1
else set -- $MT
  [ $(( $2 - $1 )) -ge 28 ] && log "CS ok: composer text height $(( $2 - $1 ))px with the keyboard up" || { log "CS WRONG: composer text clipped with the keyboard up (height $(( $2 - $1 ))px)"; FAIL=1; }
  echo "$CS24" | sed -n 's/.*text="\([^"]*\)".*bounds="\[[0-9]*,\([0-9]*\)\]\[[0-9]*,\([0-9]*\)\]".*/\1|\2|\3/p' | awk -F'|' -v top="$1" '$3+4 <= top && ($3-$2) >= 12 && $2 >= 40 && $1 != "" && $1 != "Message" && $1 != "CS QA" && $1 != "3 members" && $1 !~ /^[0-9]+$/ && $1 !~ /^(Status|All|Unread|Private|Groups|Channels|Chats|Shorts|Settings|Search chats|AI chat|Start new chat|Menu)$/ && $1 !~ /Suggest contact/ {found=1} END{if(!found) exit 1}' \
    && log "CS ok: a real message node (>=12px tall) stays visible above the composer with the keyboard up" || { log "CS WRONG: no message visible above the composer with the keyboard up (nodes under 12px tall or off-pane do not count)"; FAIL=1; }
fi
key 4; sleep 1
csfind "Niggun"; shot CS-02-formatting-voice-music; csfind "גוט מארגן"; shot CS-02b-formatting-album
for n in 1 2 3 4 5; do adb shell input swipe 240 200 240 600 250; sleep 0.6; done; sleep 3; shot CS-03-older-page-loaded; csfind "עלטערע מעסעדזש 1" 4; cshas "עלטערע מעסעדזש 1"
# 4: polls (percentages hidden until I vote; quiz with correct/wrong marks; Close Poll for the creator)
key 4; sleep 1; csroom 4; csfind "פלאפל"; shot CS-04-polls; csfindup "+ Suggest an option" 2; cshas "+ Suggest an option"
cstap "פלאפל"; sleep 2; csfx "/api/polls"; shot CS-05-poll-voted; csfindup "Close Poll" 3; shot CS-05b-quiz-close-poll; cshas "Close Poll"
# 9: voice: play, speed, seek
key 4; sleep 1; csroom 4; csfind "0:12" 6; cstap "Play"; sleep 4; shot CS-06-voice-playing; cstap "1x"; sleep 1; shot CS-06b-voice-1.5x; cstap "Pause"; sleep 1
# 1: voice note recording — tap = hands-free (pause, stop to listen, send)
key 4; csroom 5; cstap "Record voice message"; sleep 3; shot CS-07-voice-locked; cstap "Pause recording"; sleep 1; shot CS-07b-voice-paused
cstap "Resume recording"; sleep 2; cstap "Stop and listen"; sleep 1; shot CS-07c-voice-listen; cstap "Send voice message"; sleep 3; csfx "type=voice"
# reopen persistence: back to the list, reopen the room, the just-sent voice note must still be there and play
key 4; sleep 1; csroom 5; shot CS-07d-voice-after-reopen
# the reopen lands on the unread divider; the just-sent voice note is at the bottom - use the app's own jump-to-newest button, swipes as fallback
if [ -n "$(cspos "Newest")" ]; then cstap "Newest"; else for n in 1 2 3 4; do adb shell input swipe 240 180 240 540 250; sleep 0.6; done; fi; sleep 1
csplaylast; sleep 4; shot CS-07e-voice-reopen-playing
cspull CS-flow-touch
# hold = record, release = send
p=$(csmic); if [ -n "$p" ]; then set -- $p; (adb shell input swipe $1 $2 $1 $2 3500 &) ; sleep 2; shot CS-08-voice-holding; sleep 3; csfx "type=voice"; fi
# hold + slide left = cancel (nothing sent)
p=$(csmic); if [ -n "$p" ]; then set -- $p; adb shell input swipe $1 $2 $(( $1 - 260 )) $2 2500; sleep 2; shot CS-08b-voice-slid-cancel; fi
# long-press = WhatsApp's top action bar + reaction strip; ⋮ = every action; tap more messages to select several
cslong "ווער קומט היינט ביינאכט?"; shot CS-09-long-press-bar; cshas "More actions"; cstap "More actions"; sleep 1; shot CS-09b-long-press-all-actions; key 4; sleep 1
cslong "ווער קומט היינט ביינאכט?"; cstap "יא, זייער גוט"; sleep 1; shot CS-10-select-2; cshas "2"
cstap "Copy"; sleep 1; cslong "יא, זייער גוט"; cstap "זאל זיין מיט מזל"; sleep 1; cstap "Forward"; sleep 3; shot CS-11-forward-many; cstap "Yossi Klein"; sleep 3; csfx "forwarded"; key 4; sleep 1
# 10: report with details (the site's modal)
csroom 4; cslong "מיר טרעפן זיך 8:00"; cstap "More actions"; sleep 1; cstap "Report"; sleep 1; cstap "Harassment"; shot CS-12-report; cstap "Submit Report"; sleep 2; csfx "/api/reports"
# 9: double-tap = ❤️; tap a name = person card
csdbl "זאל זיין מיט מזל"; csfx "/api/chat/reactions"; shot CS-13-double-tap-heart
cstap "Yanky"; sleep 1; shot CS-14-person-card; key 4; sleep 1
# 7: message options: disappear 1 hour, then send
cstap "More"; sleep 1; cstap "Message options"; sleep 1; cstap "1 hour"; shot CS-15-message-options; cstap "Done"; sleep 1; shot CS-15b-options-badge
cstap "Message"; sleep 1; adb shell input text "QA%sdisappear"; sleep 1; cstap "Send"; sleep 3; csfx "disappear_seconds"; key 4; sleep 1
# 2/11: attach sheet, emoji panel with recent + stickers (custom), poll create
csroom 4; cstap "Attach"; sleep 1; shot CS-16-attach-sheet; cstap "Poll"; sleep 3; shot CS-17-poll-new
cstap "Ask a question"; adb shell input text "QA%spoll"; key 4; sleep 1; cstap "Option 1"; adb shell input text "A"; key 4; sleep 1; cstap "Option 2"; adb shell input text "B"; key 4; sleep 1
csfindup "Quiz Mode" 4; cstap "Quiz Mode"; sleep 1; shot CS-17b-poll-settings; cstap "Create Poll"; sleep 3; csfx "/api/polls"; key 4; sleep 1
csroom 4; cstap "Emoji"; sleep 2; shot CS-18-emoji-recent; cstap "Stickers"; sleep 3; shot CS-18b-stickers; key 4; sleep 1; key 4; sleep 1
# 12: pinned list, group call banner, copy invite (r-g1: I am group admin there)
cschopen() { timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '{\"id\":\"r-g1\",\"type\":\"group\",\"nick\":\"G1\"}'" >/dev/null 2>&1; sleep 6; log "open chat r-g1"; }
cschopen; shot CS-19-group-call-banner; cshas "Call in progress"; cstap "More"; sleep 1; shot CS-20-menu; cstap "Pinned messages"; sleep 3; shot CS-21-pinned-list; key 4; sleep 1
cstap "More"; sleep 1; cstap "Copy invite link"; sleep 1; csfx "generate_invite"; key 4; sleep 1
# D-pad / keypad video: move focus with the arrows, OK on the mic = hands-free recording, OK on Send
csroom 5; csrec CS-flow-dpad 28
for k in 20 20 20 22 22 22; do key $k; sleep 0.7; done; shot CS-22-dpad-focus
p=$(csmic); if [ -n "$p" ]; then set -- $p; adb shell input tap $1 $2; fi; sleep 3; key 22; sleep 0.6; key 23; sleep 3; shot CS-23-dpad-voice-sent; csfx "type=voice"
sleep 14; cspull CS-flow-dpad
timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -E 'r-cs|polls|reports|reactions|stickers|generate_invite' | cut -c1-400 > "$OUT/fx-cs-requests.txt"
log "CS done"

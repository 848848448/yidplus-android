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
csfx() { 
# D-pad / keypad video: move focus with the arrows, OK on the mic = hands-free recording, OK on Send
csroom 5; csrec CS-flow-dpad 28
for k in 20 20 20 22 22 22; do key $k; sleep 0.7; done; shot CS-22-dpad-focus
p=$(csmic); if [ -n "$p" ]; then set -- $p; adb shell input tap $1 $2; fi; sleep 3; key 22; sleep 0.6; key 23; sleep 3; shot CS-23-dpad-voice-sent; csfx "type=voice"
sleep 14; cspull CS-flow-dpad
timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -F -- "$1" | tail -1 | cut -c1-400 | sed 's/^/  YidFx: /' | tee -a "$OUT/qa.log" >/dev/null; }
cskb() { log "keyboard shown after $1: $(timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -o 'mInputShown=[a-z]*' | head -1)"; }
csdbl() { local p; p=$(cspos "$1"); [ -z "$p" ] && { log "csdbl: '$1' not found"; return 0; }; set -- $p; adb shell input tap $1 $2; adb shell input tap $1 $2; sleep 2; log "double-tap $*"; }
csmic() { cspos "Record voice message"; }
adb shell pm grant $PKG android.permission.RECORD_AUDIO 2>/dev/null
adb shell pm grant $PKG android.permission.CAMERA 2>/dev/null
csrec() { (adb shell screenrecord --size 480x640 --bit-rate 2000000 --time-limit "${2:-30}" "/sdcard/$1.mp4" >/dev/null 2>&1 &); sleep 1; log "screenrecord $1 started"; }
cspull() { sleep 2; timeout 30 adb pull "/sdcard/$1.mp4" "$OUT/$1.mp4" >/dev/null 2>&1 && log "video $OUT/$1.mp4 $(stat -c %s "$OUT/$1.mp4" 2>/dev/null) bytes"; }
CSR='{"id":"r-cs","type":"group","nick":"CS QA","unread":5,"members":3}'
csroom() { timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '$CSR'" >/dev/null 2>&1; sleep "${1:-6}"; log "open chat r-cs"; }

csrec CS-flow-touch 30
# 6: opens at the first unread message under "5 unread messages"; formatting, @mention, bare domain, music card, voice, polls
csroom 7; shot CS-01-open-unread-line; cshas "5 unread messages"
adb shell input swipe 240 220 240 560 300; sleep 1; shot CS-02-formatting-voice-music
adb shell input swipe 240 220 240 560 300; sleep 3; shot CS-03-older-page-loaded; cshas "עלטערע מעסעדזש 1"
# 4: polls (percentages hidden until I vote; quiz with correct/wrong marks; Close Poll for the creator)
csroom 4; adb shell input swipe 240 300 240 520 300; sleep 2; shot CS-04-polls; cshas "+ Suggest an option"; cshas "Close Poll"
cstap "פלאפל"; sleep 2; csfx "/api/polls"; shot CS-05-poll-voted
# 9: voice: play, speed, seek
cstap "Play"; sleep 4; shot CS-06-voice-playing; cstap "1x"; sleep 1; shot CS-06b-voice-1.5x; cstap "Pause"; sleep 1
# 1: voice note recording — tap = hands-free (pause, stop to listen, send)
key 4; csroom 5; cstap "Record voice message"; sleep 3; shot CS-07-voice-locked; cstap "Pause recording"; sleep 1; shot CS-07b-voice-paused
cstap "Resume recording"; sleep 2; cstap "Stop and listen"; sleep 1; shot CS-07c-voice-listen; cstap "Send voice message"; sleep 3; csfx "type=voice"
cspull CS-flow-touch
# hold = record, release = send
p=$(csmic); if [ -n "$p" ]; then set -- $p; (adb shell input swipe $1 $2 $1 $2 3500 &) ; sleep 2; shot CS-08-voice-holding; sleep 3; csfx "type=voice"; fi
# hold + slide left = cancel (nothing sent)
p=$(csmic); if [ -n "$p" ]; then set -- $p; adb shell input swipe $1 $2 $(( $1 - 260 )) $2 2500; sleep 2; shot CS-08b-voice-slid-cancel; fi
# long-press sheet (reactions row + every action), then select mode
cslong "ווער קומט היינט ביינאכט?"; shot CS-09-long-press-sheet; cshas "Select"; cstap "Select"; sleep 1; cstap "יא, זייער גוט"; sleep 1; shot CS-10-select-2
cstap "Copy"; sleep 1; cslong "יא, זייער גוט"; cstap "Select"; sleep 1; cstap "Forward"; sleep 3; shot CS-11-forward-many; cstap "Yossi Klein"; sleep 3; csfx "forwarded"; key 4; sleep 1
# 10: report with details (the site's modal)
csroom 4; cslong "מיר טרעפן זיך 8:00"; cstap "Report"; sleep 1; cstap "Harassment"; shot CS-12-report; cstap "Submit Report"; sleep 2; csfx "/api/reports"
# 9: double-tap = ❤️; tap a name = person card
csdbl "זאל זיין מיט מזל"; csfx "/api/chat/reactions"; shot CS-13-double-tap-heart
cstap "Yanky"; sleep 1; shot CS-14-person-card; key 4; sleep 1
# 7: message options: disappear 1 hour, then send
cstap "More"; sleep 1; cstap "Message options"; sleep 1; cstap "1 hour"; shot CS-15-message-options; cstap "Done"; sleep 1; shot CS-15b-options-badge
cstap "Message"; sleep 1; adb shell input text "QA%sdisappear"; sleep 1; cstap "Send"; sleep 3; csfx "disappear_seconds"; key 4; sleep 1
# 2/11: attach sheet, emoji panel with recent + stickers (custom), poll create
csroom 4; cstap "Attach"; sleep 1; shot CS-16-attach-sheet; cstap "Poll"; sleep 3; shot CS-17-poll-new
cstap "Ask a question"; adb shell input text "QA%spoll"; cstap "Option 1"; adb shell input text "A"; cstap "Option 2"; adb shell input text "B"; key 4; sleep 1
cstap "Quiz Mode"; sleep 1; shot CS-17b-poll-settings; cstap "Create Poll"; sleep 3; csfx "/api/polls"; key 4; sleep 1
csroom 4; cstap "Emoji"; sleep 2; shot CS-18-emoji-recent; cstap "Stickers"; sleep 3; shot CS-18b-stickers; key 4; sleep 1; key 4; sleep 1
# 12: pinned list, group call banner, copy invite (r-g1: I am group admin there)
cschopen() { timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '{\"id\":\"r-g1\",\"type\":\"group\",\"nick\":\"G1\"}'" >/dev/null 2>&1; sleep 6; log "open chat r-g1"; }
cschopen; shot CS-19-group-call-banner; cshas "Call in progress"; cstap "More"; sleep 1; shot CS-20-menu; cstap "Pinned messages"; sleep 3; shot CS-21-pinned-list; key 4; sleep 1
cstap "More"; sleep 1; cstap "Copy invite link"; sleep 1; csfx "generate_invite"; key 4; sleep 1
timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -E 'r-cs|polls|reports|reactions|stickers|generate_invite' | cut -c1-400 > "$OUT/fx-cs-requests.txt"
log "CS done"

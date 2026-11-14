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
cslong() { sleep 1; local p n=0 d=""; p=$(cspos "$1"); while [ -z "$p" ] && [ $n -lt 20 ]; do if [ $n -lt 6 ]; then d=older; adb shell input swipe 240 230 240 440 400; else d=newer; adb shell input swipe 240 440 240 230 400; fi; sleep 1; n=$((n+1)); p=$(cspos "$1"); done; [ -z "$p" ] && { log "CS MISSING: cslong: '$1' not found after $n swipes (6 toward older, then newer)"; return 0; }; [ $n -gt 0 ] && log "cslong: '$1' found after $n swipe(s) (last direction: $d)"; set -- $p; adb shell input swipe $1 $2 $1 $2 900; sleep 2; log "long-press $*"; }
csopen() { # csopen ID TYPE NAME-JSON-ESCAPED [extra json]
  timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '{\"id\":\"$1\",\"type\":\"$2\",\"nick\":\"$3\"$4}'" >/dev/null 2>&1; sleep "${5:-5}"; log "open chat $1"; }
csfx() { timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -F -- "$1" | tail -1 | cut -c1-400 | sed 's/^/  YidFx: /' | tee -a "$OUT/qa.log" >/dev/null; }
cskb() { log "keyboard shown after $1: $(timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -o 'mInputShown=[a-z]*' | head -1)"; }
csdbl() { local p; p=$(cspos "$1"); [ -z "$p" ] && { log "csdbl: '$1' not found"; return 0; }; set -- $p; adb shell "input tap $1 $2 & sleep 0.12; input tap $1 $2"; sleep 2; log "double-tap $*"; }
csmic() { cspos "Record voice message"; }
csdur() { # duration label of the lowest (newest) voice bubble on screen vs the recorded length ($1 = lo, $2 = hi seconds)
  timeout 30 adb shell uiautomator dump /sdcard/cd.xml >/dev/null 2>&1
  local d; d=$(timeout 20 adb shell cat /sdcard/cd.xml 2>/dev/null | tr '>' '\n' | sed -n 's/.* text="\([0-9]*:[0-9][0-9]\)".*bounds="\[[0-9]*,\([0-9]*\)\]\[.*/\2 \1/p' | sort -n | tail -1)
  [ -z "$d" ] && { log "CS WRONG: no duration label on the newest voice bubble after send (sent note not visible or label missing)"; FAIL=1; return 0; }
  local lab=${d#* }; local sec=$(( 10#${lab%%:*} * 60 + 10#${lab##*:} ))
  if [ $sec -ge $1 ] && [ $sec -le $2 ]; then log "CS ok: newest voice bubble shows $lab for a recording timed at ${3:-?}s by the clock (expected $1-$2 s)"; else log "CS WRONG: newest voice bubble shows $lab, recording timed at ${3:-?}s by the clock (expected $1-$2 s)"; FAIL=1; fi
}
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
sysbars() { # $1 = label; uiautomator does not dump the system UI, so measure the app: with the status bar hidden its first text sits at y<30, with the bar back it is at y>=40
  timeout 20 adb shell uiautomator dump /sdcard/sb.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/sb.xml > "$OUT/sysbars-$1.xml" 2>/dev/null
  TOPY=$(tr '>' '\n' < "$OUT/sysbars-$1.xml" | sed -n 's/.*text="[^"]\+".*bounds="\[[0-9]*,\([0-9]*\)\]\[[0-9]*,\([0-9]*\)\]".*/\1 \2/p' | awk '$2-$1>=12 {print $1}' | sort -n | head -1)
  if [ -n "$TOPY" ] && [ "$TOPY" -ge 40 ]; then log "CS ok: status bar back ($1; first text at y=$TOPY)"; else log "CS WRONG: status bar still hidden or screen empty ($1; first text at y=${TOPY:-none})"; FAIL=1; fi
}
csroom() { timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '$CSR'" >/dev/null 2>&1; sleep "${1:-6}"; log "open chat r-cs"; }

csrec CS-flow-touch 30
# 6: opens at the first unread message under "5 unread messages"; formatting, @mention, bare domain, music card, voice, polls
csroom 7; shot CS-01-open-unread-line; cshas "5 unread messages"
timeout 20 adb logcat -d -s YidPerf 2>/dev/null | grep -E "CHAT (open|scrollchg|anchor|stick)" | tail -30 | cut -c1-300 | sed "s/^/  scroll-diag: /" | tee -a "$OUT/qa.log" >/dev/null
timeout 20 adb shell uiautomator dump /sdcard/pre.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/pre.xml > "$OUT/CS-01-open-unread-line.xml" 2>/dev/null
bbox() { tr '>' '\n' < "$2" | grep -F "text=\"$1\"" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | head -1; }
# opening a room must leave its NEWEST message fully visible above the composer when there is no unread divider (a fix9 run opened r-cs one message above the bottom: only the name of the newest bubble peeked out behind the composer)
NEWEST="מיר טרעפן זיך 8:00"
if [ -z "$(cspos "unread messages")" ]; then
  NB=$(tr '>' '\n' < "$OUT/CS-01-open-unread-line.xml" | grep -F "text=\"$NEWEST\"" | sed -n 's/.*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,\([0-9]*\)\]".*/\1/p' | head -1)
  CT=$(tr '>' '\n' < "$OUT/CS-01-open-unread-line.xml" | sed -n 's/.*text="Message".*bounds="\[[0-9]*,\([0-9]*\)\]\[.*/\1/p' | head -1)
  if [ -z "$NB" ]; then log "CS WRONG: newest message '$NEWEST' is not on screen when the room opens (no unread divider shown)"; FAIL=1
  elif [ -n "$CT" ] && [ "$NB" -gt $(( CT - 28 )) ]; then log "CS WRONG: newest message bottom y=$NB runs under the composer (composer text top y=$CT)"; FAIL=1
  else log "CS ok: newest message fully above the composer when the room opens (bottom y=$NB, composer text top y=$CT)"; fi
fi
# keypad focus after open: the focused row must be fully readable (not a clipped sliver), D-pad UP/DOWN must move it; opening position = the unread line (this open) or the newest (the no-unread open below)
cfoc() { tr '>' '\n' < "$1" | grep 'focused="true"' | grep -v 'class="android.widget.EditText"' | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | head -1; }
csdump() { timeout 20 adb shell uiautomator dump /sdcard/f.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/f.xml > "$OUT/$1.xml" 2>/dev/null; cfoc "$OUT/$1.xml"; }
cfocdesc() { tr '>' '\n' < "$1" | grep 'focused="true"' | grep -v 'class="android.widget.EditText"' | sed -n 's/.*content-desc="\([^"]*\)".*/\1/p' | head -1; }
# csplayat LABEL: tap the Play button of the voice bubble whose duration label is LABEL (nearest Play node by y), then require Pause at the same bounds
csplayat() {
  local b tries=0
  while :; do
    timeout 30 adb shell uiautomator dump /sdcard/pa.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/pa.xml > "$OUT/csplayat.xml" 2>/dev/null
    b=$(python3 - "$OUT/csplayat.xml" "$1" <<'PYEOF'
import sys, re
import xml.etree.ElementTree as ET
label = sys.argv[2]
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception as e:
    print('ERR xml parse failed: %s' % e); sys.exit(0)
parent = {c: p for p in root.iter() for c in p}
def bounds(n):
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.get('bounds', ''))
    return tuple(map(int, m.groups())) if m else None
labels = [n for n in root.iter('node') if n.get('text') == label]
if len(labels) != 1:
    print("ERR labels=%d" % len(labels)); sys.exit(0)
lb = bounds(labels[0])
# the bubble is the nearest clickable ancestor of the label that is wide like a bubble (>=300px)
n = parent.get(labels[0]); bub = None
while n is not None:
    bb = bounds(n)
    if n.get('clickable') == 'true' and bb and bb[2] - bb[0] >= 300 and bb[0] <= lb[0] and bb[2] >= lb[2] and bb[1] <= lb[1] and bb[3] >= lb[3]:
        bub = n; break
    n = parent.get(n)
if bub is None:
    print("ERR no bubble-sized clickable ancestor of the label"); sys.exit(0)
bb = bounds(bub)
if bb[1] < 165: print("SCROLL down %d %d %d %d" % bb); sys.exit(0)   # clipped under the header: bring older content down
if bb[3] > 486: print("SCROLL up %d %d %d %d" % bb); sys.exit(0)    # clipped under the composer
plays = [d for d in bub.iter('node') if d.get('content-desc') == 'Play']
if len(plays) != 1:
    print("ERR the bubble %s has %d Play buttons inside its own bounds" % (bub.get('bounds'), len(plays))); sys.exit(0)
b = bounds(plays[0])
print("TAP %d %d %d %d %d %d" % (((b[0] + b[2]) // 2, (b[1] + b[3]) // 2) + bb))
PYEOF
)
    case "$b" in
      SCROLL\ down*) tries=$((tries+1)); [ $tries -gt 6 ] && { b="ERR could not bring the bubble fully below the header in 6 swipes"; break; }; set -- "$1"; timeout 10 adb shell input swipe 240 260 240 400 500; sleep 1 ;;
      SCROLL\ up*) tries=$((tries+1)); [ $tries -gt 6 ] && { b="ERR could not bring the bubble fully above the composer in 6 swipes"; break; }; timeout 10 adb shell input swipe 240 400 240 260 500; sleep 1 ;;
      *) break ;;
    esac
  done
  case "$b" in TAP*) log "csplayat: target bubble bounds [$(echo $b | awk '{print $4","$5"]["$6","$7}')] fully inside the list area, exactly one Play inside it"; b=$(echo $b | awk '{print $2, $3}') ;; esac
  case "$b" in ERR*|"") log "CS WRONG: csplayat '$1': ${b:-no answer}"; FAIL=1; return 0 ;; esac
  set -- $b; timeout 10 adb shell input tap $1 $2; sleep 1.5; log "csplayat tapped the Play button inside the bubble that contains the label, at $1 $2"
  timeout 30 adb shell uiautomator dump /sdcard/pa2.xml >/dev/null 2>&1
  if timeout 20 adb shell cat /sdcard/pa2.xml 2>/dev/null | tr '>' '\n' | grep -E 'content-desc="Pause"' | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | awk -v tx="$1" -v ty="$2" '{cx=($1+$3)/2; cy=($2+$4)/2; dx=cx-tx; dy=cy-ty; if(dx<0)dx=-dx; if(dy<0)dy=-dy; if(dx<30 && dy<30) f=1} END{exit f?0:1}'; then log "CS ok: that exact bubble entered playing state (Pause at the tapped bounds)"; else log "CS WRONG: the tapped bubble did not enter playing state"; FAIL=1; fi
}
csfindexact() { local n=0; while [ $n -lt ${2:-12} ]; do timeout 30 adb shell uiautomator dump /sdcard/fe.xml >/dev/null 2>&1; if timeout 20 adb shell cat /sdcard/fe.xml 2>/dev/null | grep -qF "text=\"$1\""; then return 0; fi; adb shell input swipe 240 260 240 470 400; sleep 1; n=$((n+1)); done; log "CS WRONG: csfindexact: a node whose text is exactly '$1' never appeared"; FAIL=1; return 1; }
cs_focus_check() { # $1 label, $2 xml (already dumped)
  local lbl="$1" fb h; fb=$(cfoc "$2"); if [ -z "$fb" ]; then log "CS MISSING: no focused row after opening ($lbl): keypad focus not exercised"; return 0; fi
  set -- $fb; h=$(( $4 - $2 )); if [ "$h" -lt 24 ]; then log "CS WRONG: focused row is a clipped sliver after opening ($lbl bounds $fb, height ${h}px)"; FAIL=1; else log "CS ok: focused row fully readable after opening ($lbl bounds $fb, height ${h}px)"; fi
}
F2=""; F3=""  # native.sh runs with set -u: F3 was only set inside the focused-row branch, so the bubble-OK check killed the base legs (run 43552454924, exit 1 right after the "no focused row" line)
cs_focus_check "unread open" "$OUT/CS-01-open-unread-line.xml"
F1=$(cfoc "$OUT/CS-01-open-unread-line.xml")
if [ -n "$F1" ]; then
  key 19; sleep 1; F2=$(csdump CS-01b-dpad-up); key 20; sleep 1; F3=$(csdump CS-01c-dpad-down)
  if [ -n "$F2" ] && [ "$F2" != "$F1" ]; then log "CS ok: D-pad UP moved the focus ($F1 -> $F2)"; else log "CS WRONG: D-pad UP did not move the focus (before [$F1] after [$F2])"; FAIL=1; fi
  if [ -n "$F3" ] && [ "$F3" != "$F2" ]; then log "CS ok: D-pad DOWN moved the focus again ($F2 -> $F3)"; else log "CS WRONG: D-pad DOWN did not move the focus (before [$F2] after [$F3])"; FAIL=1; fi
fi
# D-pad OK on the focused bubble opens the message action sheet (the app's own doc: "Long-press (or keypad OK) on a message"): needs a focused row first, else it stays MISSING
if [ -n "$F3" ]; then key 23; sleep 1.5; csdump CS-01e-dpad-ok-bubble >/dev/null; shot CS-01e-dpad-ok-bubble
  if grep -qE 'content-desc="Forward"|text="Forward"|content-desc="Save message"|text="Save message"' "$OUT/CS-01e-dpad-ok-bubble.xml"; then log "CS ok: D-pad OK on the focused bubble opened the message action sheet (Forward/Save message on screen, focused row was $F3)"; else log "CS WRONG: D-pad OK on the focused bubble did not open the action sheet (focused row $F3)"; FAIL=1; fi
  key 4; sleep 1; csdump CS-01f-after-sheet-close >/dev/null
  if grep -qE 'content-desc="Save message"|text="Save message"' "$OUT/CS-01f-after-sheet-close.xml"; then log "CS WRONG: the action sheet is still open after BACK"; FAIL=1; else log "CS ok: BACK closed the action sheet"; fi
else log "CS MISSING: D-pad OK on a bubble not exercised (no focused row after opening)"; fi
# no-unread open: the newest message is the open position, fully above the composer, and the focused row is readable
CSR_SAVE="$CSR"; CSR='{"id":"r-cs","type":"group","nick":"CS QA","unread":0,"members":3}'; csroom 7; shot CS-01d-open-no-unread
timeout 20 adb shell uiautomator dump /sdcard/nu.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/nu.xml > "$OUT/CS-01d-open-no-unread.xml" 2>/dev/null
NB0=$(tr '>' '\n' < "$OUT/CS-01d-open-no-unread.xml" | grep -F 'text="מיר טרעפן זיך 8:00"' | sed -n 's/.*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,\([0-9]*\)\]".*/\1/p' | head -1)
CT0=$(tr '>' '\n' < "$OUT/CS-01d-open-no-unread.xml" | sed -n 's/.*text="Message".*bounds="\[[0-9]*,\([0-9]*\)\]\[.*/\1/p' | head -1)
if [ -z "$NB0" ]; then log "CS WRONG: no-unread open: newest message not on screen"; FAIL=1
elif [ -n "$CT0" ] && [ "$NB0" -gt $(( CT0 - 28 )) ]; then log "CS WRONG: no-unread open: newest message bottom y=$NB0 runs under the composer (composer top y=$CT0)"; FAIL=1
else log "CS ok: no-unread open shows the newest message fully above the composer (bottom y=$NB0, composer top y=$CT0)"; fi
cs_focus_check "no-unread open" "$OUT/CS-01d-open-no-unread.xml"
CSR="$CSR_SAVE"; csroom 6
# the room opens at the unread divider when earlier legs left it unread (e.g. the full base leg) and at the bottom otherwise; the keyboard check needs the newest message, so jump there first and take the reference body from that dump
if [ -n "$(cspos "Newest")" ]; then cstap "Newest"; sleep 1.5; log "CS jumped to newest before the keyboard check"; fi
timeout 20 adb shell uiautomator dump /sdcard/pre.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/pre.xml > "$OUT/CS-01-open-unread-line.xml" 2>/dev/null
# the newest message body = the lowest non-time text node above the composer in the post-jump dump (a fixed string was wrong once the room's last message was a different one: "CS WRONG: last message body not on screen" while the screenshot showed the newest bubble fully visible)
BODY=$(tr '>' '\n' < "$OUT/CS-01-open-unread-line.xml" | sed -n 's/.* text="\([^"]\{4,\}\)".*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,\([0-9]*\)\]".*/\2\t\1/p' | grep -vP '\t[0-9]{1,2}:[0-9]{2}( [AP]M)?$|\t(Message|Newest.*|[0-9]+ unread messages|CS QA|[0-9]+ members)$' | awk -F'\t' '$1<=505' | sort -n | tail -1 | cut -f2)
log "CS newest message body chosen from the dump: $BODY"; [ -z "$BODY" ] && { log "CS WRONG: no newest message body found in the no-keyboard dump"; FAIL=1; BODY="__none__"; }
# keyboard up: real IME over the open chat, composer proportions with the keyboard visible
adb shell settings put secure show_ime_with_hard_keyboard 1
cstap "Message"; sleep 1; cskb "composer tap"; shot CS-24-keyboard-up
# CS-24 hard checks with the IME up at 480x640: composer text not clipped, and at least one message stays visible above the composer.
timeout 20 adb shell uiautomator dump /sdcard/cs24.xml >/dev/null 2>&1
timeout 20 adb shell cat /sdcard/cs24.xml > "$OUT/CS-24-keyboard-up.xml" 2>/dev/null
CS24=$(tr '>' '\n' < "$OUT/CS-24-keyboard-up.xml" 2>/dev/null)
PANE=0; echo "$CS24" | grep -q 'text="CS QA"' && PANE=68   # header (40dp slim bar) present: body must sit below it, unclipped; header hidden while typing: pane starts at the top
log "CS24 pane top y=$PANE"
for d in "Attach" "Record voice message"; do echo "$CS24" | grep -q "content-desc=\"$d" && log "CS ok: composer keeps '$d' with the keyboard up" || { log "CS WRONG: composer lost '$d' with the keyboard up (pushed off screen?)"; FAIL=1; }; done
MT=$(echo "$CS24" | sed -n 's/.*text="Message".*bounds="\[[0-9]*,\([0-9]*\)\]\[[0-9]*,\([0-9]*\)\]".*/\1 \2/p' | head -1)
if [ -z "$MT" ]; then log "CS WRONG: composer not found with the keyboard up"; FAIL=1
else set -- $MT
  [ $(( $2 - $1 )) -ge 28 ] && log "CS ok: composer text height $(( $2 - $1 ))px with the keyboard up" || { log "CS WRONG: composer text clipped with the keyboard up (height $(( $2 - $1 ))px)"; FAIL=1; }
  PRE=$(bbox "$BODY" "$OUT/CS-01-open-unread-line.xml"); KB=$(bbox "$BODY" "$OUT/CS-24-keyboard-up.xml")
  if [ -z "$PRE" ]; then log "CS WRONG: reference body text not found in the no-keyboard dump"; FAIL=1
  elif [ -z "$KB" ]; then log "CS WRONG: last message body not on screen with the keyboard up (nothing readable above the composer)"; FAIL=1
  else set -- $MT $PRE $KB
    H0=$(( $6 - $4 )); H1=$(( ${10} - $8 )); W0=$(( $5 - $3 )); W1=$(( $9 - $7 ))
    if [ "$H1" -ge "$H0" ] && [ "$W1" -ge "$W0" ] && [ "$8" -ge "$PANE" ] && [ "${10}" -le "$1" ]; then log "CS ok: last message body fully inside the viewport with the keyboard up (${W1}x${H1}px vs ${W0}x${H0}px unclipped; y $8-${10}, pane top $PANE, composer top $1)"
    else log "CS WRONG: last message body clipped/overlapped with the keyboard up (${W1}x${H1}px vs ${W0}x${H0}px unclipped; y $8-${10}, pane top $PANE, composer top $1)"; FAIL=1; fi
  fi
fi
# typing with the keyboard up: text lands in the field, the mic turns into Send, the controls stay on screen; then clear it again
adb shell input text hello; sleep 1
timeout 20 adb shell uiautomator dump /sdcard/ty.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/ty.xml > "$OUT/CS-24b-typed.xml" 2>/dev/null
TY=$(tr '>' '\n' < "$OUT/CS-24b-typed.xml")
echo "$TY" | grep -q 'text="hello"' && log "CS ok: typed text is in the field with the keyboard up" || { log "CS WRONG: typed text not in the field"; FAIL=1; }
for d in "Send" "Attach" "Emoji"; do echo "$TY" | grep -q "content-desc=\"$d" && log "CS ok: '$d' present while typing" || { log "CS WRONG: '$d' missing while typing"; FAIL=1; }; done
shot CS-24b-typed
# clear the typed draft (it is saved after 600 ms and restores on reopen, which swaps the mic for Send): focus the field, delete, VERIFY, retry once
# "hello" is not always exposed as a text node in the dump (google_apis run 43498008671: Send was shown while no 'hello' node existed), so the clear is verified by the MIC being back in the composer (no draft) AND no 'hello' node
CLEARED=""
for try in 1 2 3; do
  [ -n "$(cspos "hello")" ] && cstap "hello"; adb shell input keyevent KEYCODE_MOVE_END; for i in 1 2 3 4 5 6 7 8 9 10 11 12; do adb shell input keyevent KEYCODE_DEL; done; sleep 1.5
  if [ -z "$(cspos "hello")" ] && [ -n "$(cspos "Record voice message")" ]; then log "CS cleared the typed draft (verified: no 'hello' node and the mic is back in the composer, try $try)"; CLEARED=1; break; else log "CS draft not cleared after try $try (hello node: [$(cspos "hello")] mic: [$(cspos "Record voice message")])"; fi
done
[ -z "$CLEARED" ] && { log "CS WRONG: typed draft could not be cleared (mic not back in the composer), later voice steps would run against a Send button"; FAIL=1; }
sleep 1
for n in 1 2 3 4 5 6; do adb shell input keyevent 67; done; sleep 1
key 4; sleep 1; sysbars after-keyboard
csfind "Niggun"; shot CS-02-formatting-voice-music; csfind "גוט מארגן"; shot CS-02b-formatting-album
for n in 1 2 3 4 5; do adb shell input swipe 240 200 240 600 250; sleep 0.6; done; sleep 3; shot CS-03-older-page-loaded; csfind "עלטערע מעסעדזש 1" 4; cshas "עלטערע מעסעדזש 1"
# 4: polls (percentages hidden until I vote; quiz with correct/wrong marks; Close Poll for the creator)
key 4; sleep 1; sysbars after-leaving-room; csroom 4; csfind "פלאפל"; shot CS-04-polls; csfindup "+ Suggest an option" 2; cshas "+ Suggest an option"
cstap "פלאפל"; sleep 2; csfx "/api/polls"; shot CS-05-poll-voted; csfindup "Close Poll" 3; shot CS-05b-quiz-close-poll; cshas "Close Poll"
# 9: voice: play, speed, seek
key 4; sleep 1; csroom 4; csfind "0:12" 6; cstap "Play"; sleep 4; shot CS-06-voice-playing; cstap "1x"; sleep 1; shot CS-06b-voice-1.5x; cstap "Pause"; sleep 1
# WebM/Opus voice note (live voice media is audio/webm; the other fixtures are m4a): the 0:08 bubble is a generated 8 s Opus-in-WebM file. Exact-label find, structural bubble, then Pause must appear at the tapped Play bounds.
if csfindexact "0:08" 12; then csplayat "0:08"; sleep 2; shot CS-06w-webm-playing; else log "CS MISSING: the WebM voice bubble 0:08 is not on screen"; fi
csfocusnow() { timeout 20 adb shell uiautomator dump /sdcard/f.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/f.xml > "$OUT/$1.xml" 2>/dev/null; python3 - "$OUT/$1.xml" <<'PYEOF'
import sys
import xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception as e:
    print("ERR parse %s" % e); sys.exit(0)
for n in root.iter('node'):
    if n.get('focused') == 'true':  # EditText included: run 43516626433 base legs showed the composer EditText holding focus ([] paths) while the old filter hid it
        d = n.get('content-desc') or n.get('text') or ''
        if not d:
            for c in n.iter('node'):
                d = c.get('content-desc') or c.get('text') or ''
                if d: break
        print("%s|%s|%s" % (n.get('class', '').split('.')[-1], d, n.get('bounds')))
        break
PYEOF
}

# csedit: centre of the composer EditText ("x y"), from a fresh dump
csedit() { timeout 20 adb shell uiautomator dump /sdcard/ce.xml >/dev/null 2>&1; timeout 20 adb shell cat /sdcard/ce.xml 2>/dev/null | tr '>' '\n' | grep 'class="android.widget.EditText"' | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 | grep -oE '[0-9]+' | tr '\n' ' ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'; }
# csclean: leave the composer with NO draft and the mic showing, or return 1 (a draft swaps the mic for Send and would make later voice steps fail for the wrong reason)
csclean() { local try p
  for try in 1 2 3; do
    [ -n "$(cspos "Delete recording")" ] && { cstap "Delete recording"; sleep 2; }
    [ -n "$(cspos "Record voice message")" ] && { log "CS clean-state: mic present, no draft (try $try)"; return 0; }
    p=$(csedit); [ -n "$p" ] && { set -- $p; adb shell input tap $1 $2; sleep 1; }
    adb shell input keyevent KEYCODE_MOVE_END; for i in $(seq 1 30); do adb shell input keyevent KEYCODE_DEL; done; sleep 1.5
  done
  [ -n "$(cspos "Record voice message")" ] && return 0 || return 1; }
# targeted composer-escape check (clean state, no voice steps): from the composer text field, RIGHT must reach the controls beside it. Empty draft first, then with a typed draft (Send), each ending with a verified clean composer.
VOICEBAD=""
csroom 5
if ! csclean; then log "CS BLOCKED: composer could not be cleaned before the escape check; the escape check and every voice step are INVALID this run (not an app verdict)"; VOICEBAD=1; FAIL=1
else
  # focus the text field by touch, hide the keyboard, keep the focus
  p=$(csedit); [ -n "$p" ] && { set -- $p; adb shell input tap $1 $2; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1; }
  f0=$(csfocusnow CS-24e-escape-start); EP=""
  case "$f0" in EditText*) log "CS escape check start (empty draft): focus [$f0]"
    for k in 22 22 22 22; do key $k; sleep 0.5; f=$(csfocusnow CS-24e-escape-step); EP="$EP [$f]"; case "$f" in *"Record voice message"*|*Attach*|*Emoji*|*Camera*) break ;; esac; done
    case "$EP" in *"Record voice message"*|*Attach*|*Emoji*|*Camera*) log "CS ok: RIGHT from the empty composer field reached a composer control (focus path:$EP)" ;; *) log "CS WRONG: RIGHT from the empty composer field did not reach Attach/Emoji/Camera/mic (focus path:$EP)"; FAIL=1 ;; esac ;;
    *) log "CS MISSING: the composer field did not hold focus after a touch (focus: [$f0]); escape check not exercised" ;; esac
  shot CS-24e-escape-empty
  # with a typed draft
  p=$(csedit); [ -n "$p" ] && { set -- $p; adb shell input tap $1 $2; sleep 1; }
  adb shell input text ab; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
  if [ -n "$(cspos "Send")" ]; then
    # cursor editing must survive: LEFT moves the caret into the draft, the next RIGHT moves it back (focus stays in the field), only a RIGHT at the END of the draft may leave it
    key 21; sleep 0.5; fm=$(csfocusnow CS-24g-draft-caret-left); key 22; sleep 0.5; fr=$(csfocusnow CS-24g-draft-caret-right)
    case "$fm|$fr" in EditText*"|EditText"*) log "CS ok: with a typed draft, LEFT then RIGHT kept the focus in the field (caret editing preserved: [$fm] -> [$fr])" ;; *) log "CS NOTE: with a typed draft, LEFT then RIGHT left the field (focus [$fm] -> [$fr]); caret editing may not be preserved (intended behaviour not grounded)" ;; esac
    EP2=""; for k in 22 22 22 22; do key $k; sleep 0.5; f=$(csfocusnow CS-24f-escape-draft-step); EP2="$EP2 [$f]"; case "$f" in *Send*|*Attach*|*Emoji*|*Camera*) break ;; esac; done
    case "$EP2" in *Send*|*Attach*|*Emoji*|*Camera*) log "CS NOTE (not scored): RIGHT from the end of a typed draft reached a composer control (focus path:$EP2)" ;; *) log "CS NOTE (not scored): RIGHT presses from the end of a typed draft did not leave the field (focus path:$EP2); caret editing vs keypad escape intended behaviour is not grounded" ;; esac
  else log "CS MISSING: typing a draft did not turn the mic into Send; draft escape check not exercised"; fi
  shot CS-24f-escape-draft
  if csclean; then log "CS ok: composer cleaned again after the escape check (no draft, mic present)"; else log "CS BLOCKED: composer could not be cleaned after the escape check; later voice steps are INVALID this run (not an app verdict)"; VOICEBAD=1; FAIL=1; fi
fi
if [ -n "$VOICEBAD" ]; then log "CS BLOCKED: touch-start voice steps skipped (composer not clean); discarding the recording the previous line started"; [ -n "$(cspos "Delete recording")" ] && { cstap "Delete recording"; sleep 2; }; else
# 1: voice note recording — tap = hands-free (pause, stop to listen, send)
key 4; csroom 5; cstap "Record voice message"; sleep 3; shot CS-07-voice-locked
# touch-start recording: the decisive check is the ACTION, not the focus dump. After the touch start and ONE D-pad DOWN, OK must post a NEW fixture voice message and end the recording. The focus dumps are informational (uiautomator can fail to dump while the recording animates; run 43540218049 showed the gold ring on Send in the pixels while the dump said none), so each is retried and its XML kept.
VT0=$(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -c 'type=voice')
f=$(csfocusnow CS-07x-touch-start-focus); log "CS touch-start recording: focus right after the touch start: [${f:-none}] (informational)"
key 20; sleep 1.5; shot CS-07y-touch-start-after-dpad
for n in 1 2 3; do f=$(csfocusnow CS-07y-touch-start-after-dpad); case "$f" in ""|ERR*) sleep 1 ;; *) break ;; esac; done
log "CS touch-start recording: focus after one D-pad DOWN: [${f:-none}] (informational; pixels in CS-07y)"
key 23; sleep 3; shot CS-07z-touch-start-after-ok
VT1=$(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -c 'type=voice')
csdump CS-07z-touch-start-after-ok >/dev/null
if [ "$VT1" -gt "$VT0" ]; then log "CS ok: touch-start recording then ONE D-pad DOWN then OK posted a new voice message ($VT0 -> $VT1 type=voice requests)"; else log "CS WRONG: touch-start recording then one D-pad DOWN then OK did not post a voice message ($VT0 -> $VT1; focus [${f:-none}])"; FAIL=1; fi
if grep -qE 'content-desc="(Pause recording|Resume recording|Stop and listen)"' "$OUT/CS-07z-touch-start-after-ok.xml"; then log "CS WRONG: the touch-start recording is still active after OK"; FAIL=1; else log "CS ok: the touch-start recording ended after OK"; fi
# fresh touch-start recording for the pause/resume/listen/send checks below
if grep -qE 'content-desc="(Pause recording|Resume recording|Stop and listen)"' "$OUT/CS-07z-touch-start-after-ok.xml"; then cstap "Delete recording"; sleep 2; fi
cstap "Record voice message"; sleep 3
fi
if [ -n "$VOICEBAD" ]; then log "CS BLOCKED: pause/resume/listen/send, duration, reopen and hold/slide voice steps skipped (composer not clean); not app verdicts"; else
cstap "Pause recording"; sleep 1; shot CS-07b-voice-paused
cstap "Resume recording"; sleep 2; cstap "Stop and listen"; sleep 1; shot CS-07c-voice-listen; cstap "Send voice message"; sleep 3; csfx "type=voice"
# duration check on its own straight recording (no pause), timed with the clock: T0 and T1 are both taken right after a cstap returns (each includes the same 1.5 s post-tap wait), so T1-T0 is the recording length within ~1 s
key 4; csroom 5; cstap "Record voice message"; T0=$(date +%s); sleep 6; cstap "Stop and listen"; T1=$(date +%s); cstap "Send voice message"; sleep 3
REC=$(( T1 - T0 )); csdur $(( REC - 2 )) $(( REC + 2 )) "$REC"; shot CS-07a-voice-sent-duration
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
fi
# long-press = WhatsApp's top action bar + reaction strip; ⋮ = every action; tap more messages to select several
cslong "ווער קומט היינט ביינאכט?"; shot CS-09-long-press-bar; cshas "More actions"; cstap "More actions"; sleep 1; shot CS-09b-long-press-all-actions; key 4; sleep 1
cslong "ווער קומט היינט ביינאכט?"; cstap "יא, זייער גוט"; sleep 1; shot CS-10-select-2; cshas "2"
cstap "Copy"; sleep 1; cslong "יא, זייער גוט"; cstap "זאל זיין מיט מזל"; sleep 1; cstap "Forward"; sleep 3; shot CS-11-forward-many; cstap "Yossi Klein"; sleep 3; csfx "forwarded"; key 4; sleep 1
# 10: report with details (the site's modal)
csroom 4; cslong "מיר טרעפן זיך 8:00"; cstap "More actions"; sleep 1
# D-pad reachability of Report in the action sheet (keypad user, no touch scroll): DOWN until the focused row is Report
RP=""; RF=""; for n in 1 2 3 4 5 6 7 8 9 10 11 12; do f=$(csfocusnow CS-12d-dpad-report); RP="$RP [$f]"; case "$f" in *Report*) RF=1; break ;; esac; key 20; sleep 0.4; done
if [ -n "$RF" ]; then log "CS ok: D-pad reached 'Report' in the message action sheet (focus path:$RP)"; shot CS-12d-dpad-focus-report; else log "CS WRONG: D-pad could not reach 'Report' in the action sheet (focus path:$RP)"; FAIL=1; fi
for n in 1 2 3 4; do [ -n "$(cspos "Report")" ] && break; adb shell input swipe 240 470 240 250 300; sleep 0.8; done; shot CS-12a-menu-scrolled-to-report; cstap "Report"; sleep 1; cstap "Harassment"; shot CS-12-report; cstap "Submit Report"; sleep 2; csfx "/api/reports"
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
VC0=$(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -c 'type=voice')
if [ -n "$VOICEBAD" ]; then log "CS BLOCKED: D-pad voice flow skipped (composer not clean); not an app verdict"; else
# 1) reach the mic with the D-pad only: DOWN a few times, then RIGHT a few times
FOUNDM=""; PATHM=""
for k in 20 20 20 20 22 22 22 22 22 22; do f=$(csfocusnow CS-22b-focus-mic); PATHM="$PATHM [$f]"; case "$f" in *"Record voice message"*) FOUNDM=1; break ;; esac; key $k; done
[ -z "$FOUNDM" ] && { f=$(csfocusnow CS-22b-focus-mic); PATHM="$PATHM [$f]"; case "$f" in *"Record voice message"*) FOUNDM=1 ;; esac; }
if [ -n "$FOUNDM" ]; then log "CS D-pad reached the mic (focus path:$PATHM)"; key 23; sleep 3
else log "CS WRONG: D-pad could not reach the mic button (focus path:$PATHM); starting the recording by touch instead"; FAIL=1; p=$(csmic); if [ -n "$p" ]; then set -- $p; adb shell input tap $1 $2; fi; sleep 3; fi
f=$(csfocusnow CS-22c-recording); log "CS focus right after the recording started: [${f:-none}]"; shot CS-22c-recording-pixels
if ! grep -qE 'content-desc="(Pause recording|Resume recording|Stop and listen)"' "$OUT/CS-22c-recording.xml"; then log "CS WRONG: D-pad voice flow: hands-free recording did not start (no recording controls on screen)"; FAIL=1; fi
# 2) reach Send voice message with the D-pad: RIGHT first, then LEFT
FOUND=""; PATHD=""
for k in 22 22 22 22 22 22 21 21 21 21 21 21 21 21 21 21 21 21; do f=$(csfocusnow CS-22d-focus-send); PATHD="$PATHD [$f]"; case "$f" in *"Send voice message"*) FOUND=1; break ;; esac; key $k; done
if [ -n "$FOUND" ]; then log "CS D-pad reached 'Send voice message' (focus path:$PATHD)"; key 23; sleep 3; shot CS-23-dpad-voice-sent
  VC1=$(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -c 'type=voice'); if [ "$VC1" -gt "$VC0" ]; then log "CS ok: D-pad OK on Send posted a new voice message ($VC0 -> $VC1 type=voice requests); newest request: $(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep "type=voice" | tail -1 | cut -c1-200)"; else log "CS WRONG: D-pad OK on Send did not post a voice message ($VC0 -> $VC1)"; FAIL=1; fi
else log "CS WRONG: D-pad could not reach 'Send voice message' while recording (focus path:$PATHD); sending it by touch to end the recording"; FAIL=1; shot CS-23-dpad-voice-sent; p=$(cspos "Send voice message"); if [ -n "$p" ]; then set -- $p; adb shell input tap $1 $2; sleep 2; fi; fi
csdump CS-23b-after-send >/dev/null
if grep -qE 'content-desc="(Pause recording|Resume recording|Stop and listen)"' "$OUT/CS-23b-after-send.xml"; then
  log "CS WRONG: a recording is still active after the D-pad send step (it would contaminate later checks); discarding it"; FAIL=1
  cstap "Delete recording"; sleep 2; csdump CS-23c-after-discard >/dev/null
  if grep -qE 'content-desc="(Pause recording|Resume recording|Stop and listen)"' "$OUT/CS-23c-after-discard.xml"; then log "CS WRONG: the recording could not be discarded; later checks are contaminated"; else log "CS recording discarded; composer is clean again"; fi
else log "CS ok: no recording active after the D-pad send step (test state clean)"; fi
fi
sleep 14; cspull CS-flow-dpad
timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -E 'r-cs|polls|reports|reactions|stickers|generate_invite' | cut -c1-400 > "$OUT/fx-cs-requests.txt"
# ---- offline-replay resilience check (NOT a proof of containment): with the emulator network switched off and a real HTTP probe failing, the fixture chat and voice note must still work.
# It does not prove that no connection was ever attempted: that needs a socket capture, which this harness does not have.
W0=$(timeout 15 adb shell settings get global wifi_on 2>/dev/null | tr -d '\r'); D0=$(timeout 15 adb shell settings get global mobile_data 2>/dev/null | tr -d '\r')
log "CS offline-check: initial state wifi_on=$W0 mobile_data=$D0"
cshttp() { timeout 25 adb shell "printf 'GET / HTTP/1.0\r\nHost: one.one.one.one\r\n\r\n' | toybox nc -w 5 1.1.1.1 80 2>&1 | head -1; echo done" 2>&1 | tr -d '\r' | head -2 | tr '\n' ' ' | cut -c1-160; }
cshttp_ok() { case "$1" in *HTTP/1*) return 0 ;; *) return 1 ;; esac; }
PRE=$(cshttp); log "CS offline-check: HTTP probe with the network on: [$PRE]"
log "CS offline-check: routes before: $(timeout 15 adb shell 'ip route' 2>&1 | tr '\r\n' '; ' | cut -c1-200)"
if ! cshttp_ok "$PRE"; then
  log "CS MISSING: offline-replay check not run, the HTTP probe does not work even with the network on, so the network path cannot be shown to change"
else
  adb shell svc wifi disable 2>/dev/null; adb shell svc data disable 2>/dev/null; sleep 5
  OFF=$(cshttp); log "CS offline-check: HTTP probe after disabling wifi+data: [$OFF]; routes after: $(timeout 15 adb shell 'ip route' 2>&1 | tr '\r\n' '; ' | cut -c1-200)"
  if cshttp_ok "$OFF"; then
    log "CS MISSING: offline-replay check not run, the HTTP probe still succeeds after wifi+data were disabled (the network path was not removed)"
  else
    log "CS offline-check: the HTTP probe fails with wifi+data off (path removed for this probe)"
    adb logcat -c; csroom 6
    if [ -n "$(cspos "Newest")" ]; then cstap "Newest"; sleep 1; fi
    csdump CS-30a-pre-offline-replay >/dev/null
    if grep -qE 'content-desc="(Pause recording|Resume recording|Stop and listen)"' "$OUT/CS-30a-pre-offline-replay.xml"; then log "CS WRONG: offline replay skipped, a recording is still active so the state is not clean"; FAIL=1
    else csfindexact "0:12" 14 && csplayat "0:12"; shot CS-30-network-off-voice; fi
    cshas "Message"
    OUTB=$(timeout 30 adb logcat -d 2>/dev/null | grep -E "Unable to resolve host|UnknownHostException|SocketTimeoutException|ConnectException|Failed to connect to" | grep -v "YidplusPush" | cut -c1-220 | head -5)
    if [ -n "$OUTB" ]; then log "CS offline-check NOTE: connection-failure log lines while offline (the app tried something):"; echo "$OUTB" | sed 's/^/  offline-log: /' | tee -a "$OUT/qa.log" >/dev/null; else log "CS offline-check: no connection-failure lines in the log while offline (this alone does not prove nothing was attempted)"; fi
  fi
  # restore ONLY what was on before
  [ "$W0" = "1" ] && adb shell svc wifi enable 2>/dev/null; [ "$D0" = "1" ] && adb shell svc data enable 2>/dev/null; sleep 5
  log "CS offline-check: restored; HTTP probe now: [$(cshttp)] wifi_on=$(timeout 15 adb shell settings get global wifi_on 2>/dev/null | tr -d '\r') mobile_data=$(timeout 15 adb shell settings get global mobile_data 2>/dev/null | tr -d '\r')"
fi
log "CS done"

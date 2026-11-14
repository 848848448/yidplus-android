dmark() { adb shell "log -p i -t YidDiag 'script: $1'" 2>/dev/null; }
clkbshown0() { timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -o 'mInputShown=[a-z]*' | head -1; }
clinq() { log "CL probe input-queue $1: $(timeout 15 adb shell dumpsys input 2>/dev/null | grep -iE 'PendingEvent|WaitQueue|OutboundQueue|InboundQueue|FocusedApplication|FocusedWindow|Connection' | tr -s ' ' | head -12 | tr '\n' '|' | cut -c1-900)"; }
clsamp() { log "CL probe sample $1: $(for k in 1 2 3 4; do clkbshown0; sleep 0.5; done | tr '\n' ' ')"; }
clprobe() { # only when the keyboard is still up after the first Back: chevron tap, then the IME check key; screenshot after each
  case "$(clkbshown0)" in *true*) ;; *) log "CL probe: keyboard already hidden after Back, nothing to probe"; return 0 ;; esac
  log "CL probe: keyboard still up after Back at the join step"; clinq still-up; shot CL-probe-0-after-back
  adb shell input tap 84 603; sleep 2; clsamp after-chevron-tap; shot CL-probe-1-after-chevron
  case "$(clkbshown0)" in *true*) adb shell input tap 440 525; sleep 2; clsamp after-ime-check-key; shot CL-probe-2-after-check-key ;; esac
  case "$(clkbshown0)" in *true*) log "CL probe: keyboard STILL up after chevron and check key" ;; *) log "CL probe: keyboard hidden by touch" ;; esac
}
climedump() { log "CL ime-diag $1: $(timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -E "mShowRequested|mInputShown|mImeWindowVis|mCurFocusedWindow=|mCurId=|mCurTokenDisplayId|mSystemReady" | tr -s " " | tr "\n" "|" | cut -c1-600) focus: $(timeout 10 adb shell dumpsys window 2>/dev/null | grep -E "mCurrentFocus|mInputMethodTarget|mImeLayeringTarget" | tr -s " " | tr "\n" "|" | cut -c1-400) hwkb: $(adb shell settings get secure show_ime_with_hard_keyboard | tr -d "\r") ime: $(adb shell settings get secure default_input_method | tr -d "\r")"; }
# Helper CL (chat list & chat management, 2.1): signed-in QA with the fixture account at 480x640 (the Qin F21 size).
# Sourced by qa/signedin.sh fx_block (log/shot/key/view/restart/size from qa/native.sh). Every write is answered locally and logged (tag YidFx):
# each "YidFx:" line below is the exact request the app would send, to be checked against the site's chat.js call named beside it.
clpos() { # centre of the LAST node (drawn on top: an open page over the list, a dialog over its page) whose text/desc is exactly $1 (else contains it)
  timeout 30 adb shell uiautomator dump /sdcard/cl.xml >/dev/null 2>&1
  local x n b; x=$(timeout 20 adb shell cat /sdcard/cl.xml 2>/dev/null | tr '>' '\n')
  n=$(echo "$x" | grep -E -- "(text|content-desc)=\"$(printf '%s' "$1" | sed 's/[][\.*^$/]/\\&/g')\"" | awk 'match($0,/bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/){s=substr($0,RSTART,RLENGTH); gsub(/[^0-9]+/," ",s); split(s,a," "); if(a[4]>a[2]) line=$0} END{print line}')
  [ -z "$n" ] && n=$(echo "$x" | grep -F -- "$1" | awk 'match($0,/bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/){s=substr($0,RSTART,RLENGTH); gsub(/[^0-9]+/," ",s); split(s,a," "); if(a[4]>a[2]) line=$0} END{print line}')
  [ -z "$n" ] && return 0
  b=$(echo "$n" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$b" ] && return 0; set -- $b; echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}
chipw() { case "$1" in All|Unread|Private|Groups|Status|"Chat folders") [ -z "$(clpos "$1")" ] && { log "CL chips row scrolled off with the list: scrolling to the top to reach '$1'"; cltop; } ;; esac; return 0; }
cltap() { local p w="$1"; chipw "$1"; p=$(clpos "$1"); if [ -z "$p" ]; then log "CL MISSING tap: '$w' not on screen"; return 0; fi; set -- $p; timeout 10 adb shell input tap $1 $2; sleep 1.5; log "cltap '$w'"; }
cllong() { sleep 1; local p; p=$(clpos "$1"); [ -z "$p" ] && { log "CL MISSING long-press: '$1'"; return 0; }; set -- $p; adb shell input swipe $1 $2 $1 $2 900; sleep 2; log "long-press '$1'"; }
clhas() { local p; chipw "$1"; p=$(clpos "$1"); if [ -n "$p" ]; then log "CL ok: '$1' on screen"; else log "CL MISSING: '$1' not on screen"; fi; }
clmust() { local p; p=$(clpos "$1"); if [ -n "$p" ]; then log "CL ok: '$1' on screen ($2)"; else log "CL WRONG: '$1' missing ($2)"; fi; }
# titles of the visible chat rows that carry an unread badge, read from the live dump at the assertion point (no counts hard-coded: rows opened or marked read earlier simply drop out)
clbadged() { timeout 20 adb shell uiautomator dump /sdcard/clb.xml >/dev/null 2>&1
  timeout 20 adb shell cat /sdcard/clb.xml 2>/dev/null | tr '>' '\n' | sed -n 's/.* text="\([^"]*\)".*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1\t\2\t\3\t\4\t\5/p' \
   | awk -F'\t' '$1!="" && $3>=170 && $5<=540 {n++; t[n]=$1; l[n]=$2; y[n]=$3} END{for(i=1;i<=n;i++) if (t[i] ~ /^[0-9]+$/ && l[i]>=380) { best=""; by=1e9; for(j=1;j<=n;j++) if (l[j]>=100 && l[j]<380 && t[j] !~ /^[0-9]+$/ && y[j]<=y[i] && y[i]-y[j]<=80 && y[j]<by) {by=y[j]; best=t[j]} if (best!="") print best } }'; }
clrowcount() { timeout 20 adb shell cat /sdcard/clb.xml 2>/dev/null | tr '>' '\n' | grep -oE 'bounds="\[0,[0-9]+\]\[480,[0-9]+\]"' | grep -oE '[0-9]+' | paste - - - - | awk '{h=$4-$2; if (h>=70 && h<=112 && $2>=170 && $4<=545) c++} END{print c+0}'; }
clnot() { local p; p=$(clpos "$1"); if [ -n "$p" ]; then log "CL WRONG: '$1' is on screen"; else log "CL ok: '$1' not shown"; fi; }
clfx() { local l; l=$(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -F -- "$1" | tail -1 | cut -c1-400); if [ -n "$l" ]; then echo "  YidFx: $l" >> "$OUT/qa.log"; log "CL ok: sent $1"; else log "CL MISSING: no request with '$1'"; fi; }
clopen() { timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '{\"id\":\"$1\",\"type\":\"$2\",\"nick\":\"$3\"$4}'" >/dev/null 2>&1; sleep "${5:-5}"; log "open chat $1"; }
cltexts() { timeout 20 adb shell uiautomator dump /sdcard/clt.xml >/dev/null 2>&1; log "CL texts $1: $(timeout 20 adb shell cat /sdcard/clt.xml 2>/dev/null | grep -oE ' (text|content-desc)="[^"]+"' | head -70 | tr '\n' ' ' | cut -c1-1500)"; }
clup() { adb shell input swipe 240 420 240 150 400; sleep 1; }
cldown() { adb shell input swipe 240 160 240 560 300; sleep 1; }
clseek() { # scroll up (content moves up) until $1 is on screen, max 7 swipes; $2 = x of the swipe (default 240)
  local i p t0=$(date +%s); for i in 1 2 3 4 5 6 7; do p=$(clpos "$1"); [ -n "$p" ] && { echo "$p"; return 0; }; timeout 10 adb shell input swipe ${2:-240} 420 ${2:-240} 170 500 >>"$OUT/qa.log" 2>&1; sleep 1; done
  echo "  clseek '$1': 7 swipes in $(( $(date +%s) - t0 )) s; screen texts: $(timeout 20 adb shell cat /sdcard/cl.xml 2>/dev/null | grep -oE ' (text|content-desc)="[^"]+"' | head -25 | tr '\n' ' ' | cut -c1-600)" >> "$OUT/qa.log"; return 0; }
# "${2:-}": native.sh runs with set -u, and a bare "$2" (clst called with one word) killed every clst lookup at once.
clst() { local p w="$1"; p=$(clseek "$1" "${2:-}"); if [ -z "$p" ]; then log "CL MISSING tap: '$w' not found after scrolling"; return 0; fi; set -- $p; timeout 10 adb shell input tap $1 $2; sleep 1.5; log "cltap '$w' (scrolled)"; }
clsl() { local p; p=$(clseek "$1"); [ -z "$p" ] && { log "CL MISSING long-press: '$1' not found after scrolling"; return 0; }; set -- $p; adb shell input swipe $1 $2 $1 $2 900; sleep 2; log "long-press '$1'"; }
cltop() { local i; for i in 1 2 3 4 5 6; do adb shell input swipe ${1:-240} 200 ${1:-240} 470 250; done; sleep 2.5; }
# chip swipes stay 70 px away from the screen edges: with gesture navigation an edge swipe is BACK (it left the app in run 43170366208)
# clchip LABEL: tap the filter CHIP (topmost exact-text node in y < 220), not the result-section header of the same name (cltap's "last node" rule tapped the 'Photos' section header at y=235 in CL-31g of run 43516626433, so Photos stayed selected)
clchip() { local p w="$1"; timeout 30 adb shell uiautomator dump /sdcard/cl.xml >/dev/null 2>&1; p=$(timeout 20 adb shell cat /sdcard/cl.xml 2>/dev/null | python3 -c '
import sys,re
t=sys.stdin.read(); lab=sys.argv[1]
for m in re.finditer(r"<node [^>]*>", t):
    n=m.group(0)
    if re.search(r"(text|content-desc)=\"%s\"" % re.escape(lab), n):
        b=re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n)
        if b:
            x1,y1,x2,y2=map(int,b.groups())
            if y2<220 and x2>x1 and x1<480: print((x1+x2)//2, (y1+y2)//2); break
' "$1"); if [ -z "$p" ]; then log "CL MISSING tap: chip '$1' not in the chips row on screen"; return 0; fi; set -- $p; timeout 10 adb shell input tap $1 $2; sleep 1.5; log "clchip tapped the '$w' chip at $p"; }
# clcheck LABEL on|off: the chip's own node must (not) be in the selected state; uiautomator exposes selection of a chip as selected/checked on its node or a check-mark child, so this logs what the node says and does not pass on a guess
clcheck() { timeout 30 adb shell uiautomator dump /sdcard/cc.xml >/dev/null 2>&1; local l; l=$(timeout 20 adb shell cat /sdcard/cc.xml 2>/dev/null | tr '>' '\n' | grep -F "text=\"$1\"" | grep -E 'bounds="\[[0-9]+,1[0-9][0-9]\]' | head -1 | cut -c1-260); log "CL chip '$1' node after toggle (expected $2): ${l:-not found in the chips row}"; }
clchipscroll() { timeout 10 adb shell input swipe 440 162 60 162 300; sleep 0.8; timeout 10 adb shell input swipe 440 162 60 162 300; sleep 1; }
chipswipe() { local p; p=$(clpos "All"); [ -z "$p" ] && p=$(clpos "Groups"); [ -z "$p" ] && { log "CL chipswipe: no chips row on screen"; return 0; }; set -- $p; local k; for k in 1 2 3; do adb shell input swipe 440 $2 40 $2 300; sleep 0.8; done; sleep 1; }  # one swipe moved the chips only ~2 chips (CL-02 of run 43511759626 still showed Private first), so the trailing 'Chat folders' button was off screen; 3 swipes reach the end
chipback() { local p; p=$(clpos "Groups"); [ -z "$p" ] && p=$(clpos "All"); [ -z "$p" ] && { log "CL chipback: no chips row on screen"; return 0; }; set -- $p; adb shell input swipe 70 $2 410 $2 300; sleep 1; }
size 480x640 240

# ── 2.1.0 BLOCKERS at 480x640 (each shot is checked by eye): 5+ chat rows, the FAB covers no row, no pencil,
#    search empty / typing / results, a non-green theme, dark mode (list + an open chat), the D-pad, no crash ──
look() { timeout 30 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_LOOK "$@" >/dev/null 2>&1; sleep 2; }
clrows() { # full-width chat rows (redesign rows are about 100 px at 240 dpi; the old 52 dp = 78 px window made the counter read 0 on every run) wholly between the top bar and the bottom bar; and what the FAB overlaps
  timeout 30 adb shell uiautomator dump /sdcard/clr.xml >/dev/null 2>&1
  local x; x=$(timeout 20 adb shell cat /sdcard/clr.xml 2>/dev/null | tr '>' '\n')
  local fab; fab=$(echo "$x" | grep -F 'content-desc="Start new chat"' | grep -oE 'bounds="[^"]+"' | head -1 | grep -oE '[0-9]+' | tr '\n' ' ')
  local nav; nav=$(echo "$x" | grep -F 'content-desc="Chats' | grep -oE 'bounds="[^"]+"' | head -1 | grep -oE '[0-9]+' | sed -n 2p)
  local n; n=$(echo "$x" | grep -oE 'bounds="\[0,[0-9]+\]\[480,[0-9]+\]"' | grep -oE '[0-9]+' | paste - - - - | awk -v nb="${nav:-560}" '{h=$4-$2; if (h>=70 && h<=112 && $2>=110 && $4<=nb+2) c++} END{print c+0}')
  log "CL rows: $n full chat rows visible above the bottom bar (top of bottom bar y=${nav:-?}); FAB bounds: ${fab:-none} ($1)"
  case "$1" in end*) ;; *) [ "${n:-0}" -ge 3 ] && log "CL ok: ${n} comfortable full rows (each 70-112px tall) at 480x640; the count is not the point, the rows must be real, full width and unclipped (rationale: readable rows over an inherited 5-row count; the viewport is ~365px between top chrome and bottom bar)" || log "CL WRONG: only ${n:-0} full rows (70-112px, full width, above the bottom bar) at 480x640: list blank, clipped or collapsed";; esac
}
# The Qin F21 has hardware keys and no on-screen nav bar; this emulator draws a 48 dp 3-button bar. Use the thin gesture bar
# for the whole CL block so the screen height matches the phone more closely (no swipe starts within 70 px of a side edge).
timeout 20 adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1; sleep 3; log "CL nav mode: $(timeout 10 adb shell settings get secure navigation_mode 2>&1)"
look --es dark 0 --es color 0 --es bar dark; view "https://yidplus.com/chat"; sleep 7; cltop; sleep 3
shot CL-B01-list-480x640; clrows top; clnot "New chat"; clnot "Edit"; clhas "Search chats"; clhas "Start new chat"; clhas "Status"; clhas "AI chat"; cltexts blockers-list
for k in 1 2 3 4 5 6 7 8 9 10; do adb shell input swipe 200 430 200 140 200; sleep 0.5; done; sleep 2; shot CL-B02-list-end-fab-hidden-while-scrolling-down; clrows end-scrolling
adb shell input swipe 200 300 200 360 300; sleep 2; shot CL-B02b-list-end-fab-back-last-row-above-it; clrows end-fab-back
cltop; sleep 3
cltap "Search chats"; sleep 2; shot CL-B03-search-empty; cltexts search-empty; clhas "Close search"
adb shell input text "Yo"; sleep 1.5; shot CL-B04-search-typing-yo
adb shell input text "ssi"; sleep 3; key 4; sleep 1; shot CL-B05-search-results-yossi; clhas "Yossi Klein"; cltexts search-results
key 4; sleep 1; key 4; sleep 1
look --es color 1; view "https://yidplus.com/chat"; sleep 3; cltop; sleep 3; shot CL-B06-list-theme-ocean; clrows ocean
look --es color 0 --es dark 2; view "https://yidplus.com/chat"; sleep 3; cltop; sleep 3; shot CL-B07-list-dark; clrows dark
clopen r-dm1 private "Yossi Klein" ",\"other_user_id\":\"u-yossi\"" 5; shot CL-B08-chat-open-dark; key 4; sleep 1
look --es dark 0; view "https://yidplus.com/chat"; sleep 3; cltop; sleep 3
key 20; sleep 0.7; key 20; sleep 0.7; key 20; sleep 0.7; key 20; sleep 0.7; shot CL-B09-dpad-focus-row; key 23; sleep 3; shot CL-B10-dpad-ok-opened-chat; key 4; sleep 1.5
alive && log "CL ok: app alive after blockers" || log "CL WRONG: app not running after blockers"

# ── Video 1 (30 s, D-pad + touch): list → search field → chips → open a chat → back → side menu ──
adb shell rm -f /sdcard/CL-flow.mp4; (timeout 40 adb shell screenrecord --size 480x640 --bit-rate 1000000 --time-limit 30 /sdcard/CL-flow.mp4 >/dev/null 2>&1 &)
view "https://yidplus.com/chat"; sleep 4; key 20; sleep 0.6; key 20; sleep 0.6; key 20; sleep 0.6; key 19; sleep 0.6; key 23; sleep 3; key 4; sleep 2
adb shell input swipe 240 500 240 200 500; sleep 1.5; adb shell input swipe 240 200 240 520 500; sleep 1.5
cltap "Search chats"; adb shell input text "Yo"; sleep 2; key 4; sleep 0.5; key 4; sleep 1.5; cltap "Private"; sleep 1.5; cltap "All"; sleep 1; cltap "Menu"; sleep 2; key 4
sleep 6; timeout 20 adb pull /sdcard/CL-flow.mp4 "$OUT/CL-flow.mp4" >/dev/null 2>&1; log "CL video: $(ls -la "$OUT/CL-flow.mp4" 2>/dev/null | awk '{print $5}') bytes"

# ── The list: tabs + folder chips, Telegram channels as rows, empty private chats hidden, compact status + AI rows at 480x640 ──
view "https://yidplus.com/chat"; sleep 8; shot CL-01-list; cltexts list
clnot "Empty Chat"; clhas "Menu"; clhas "Status"
chipswipe; shot CL-02-chips-end; clhas "Work"; clhas "Chat folders"
cltap "Work"; sleep 2; shot CL-04-folder-work; cltexts folder; clhas "DealPoint"; clnot "Yossi Klein"; chipswipe; cltap "Work"; sleep 1; chipback
cltap "All"; sleep 1.5; cltop; BEFORE=$(clbadged); log "CL All tab, rows with an unread badge at this point: $(echo "$BEFORE" | tr '\n' ',')"
cltap "Unread"; sleep 2; shot CL-05-tab-unread; cltexts unread-tab; AFTER=$(clbadged); NR=$(clrowcount); NB=$(echo "$AFTER" | grep -c . || true)
log "CL Unread tab: $NB badged rows, $NR full rows; badged titles: $(echo "$AFTER" | tr '\n' ',')"
[ -z "$BEFORE" ] && log "CL WRONG: no badged rows found in the All tab to compare against (parser or fixture state)" 
echo "$BEFORE" | while IFS= read -r t; do [ -z "$t" ] && continue; echo "$AFTER" | grep -qxF "$t" && log "CL ok: '$t' (unread in All) is in the Unread tab" || log "CL WRONG: '$t' has an unread badge in All but is missing from the Unread tab"; done
[ "$NR" -le "$NB" ] && log "CL ok: every full row in the Unread tab has an unread badge" || log "CL WRONG: Unread tab shows $((NR-NB)) row(s) without a badge ($NR rows, $NB badged)"
cltap "All"; sleep 1
p=$(clseek "יידישע נייעס"); [ -n "$p" ] && log "CL ok: Telegram channel row in the list"; shot CL-03-list-telegram-rows; cltop

# ── Long-press selection: Pin (on this phone), Mute 8 h / 1 week / Always, Mark as read, Delete ──
clsl "Berel Links"; shot CL-06-selected; clhas "Pin"; clhas "Mark as read"
cltap "Pin"; sleep 2; cltop; shot CL-07-pinned-top; cltexts pinned
clsl "Berel Links"; cltap "Unpin"; sleep 1; log "CL unpinned again"; cltop
clsl "Yossi Klein"; cltap "Mute"; sleep 1; shot CL-08-mute-choices; cltap "8 hours"; cltap "OK"; sleep 2; clfx '"room_id":"r-dm1","muted":true'; cltop
clsl "Yossi Klein"; cltap "Mark as read"; sleep 2; clfx "/api/chat/read"; cltop
clsl "חיים"; cltap "Delete"; sleep 1; shot CL-09-delete-confirm; cltap "Delete chat"; sleep 2; clfx "DELETE /api/chat/rooms?room_id=r-dm3"; cltop
cllong "Yossi Klein"; clst "Berel Links"; shot CL-10-two-selected; key 4; sleep 1; cltop

# ── ☰ side menu: profile, Saved, Chat Folders, New Group/Channel, invite link, Chat settings, Admin Panel ──
cltap "Menu"; sleep 2; shot CL-11-drawer; cltexts drawer; p=$(clseek "Admin Panel" 100); [ -n "$p" ] && log "CL ok: 'Admin Panel' in the side menu"; shot CL-11b-drawer-bottom; key 4; sleep 1
cltap "Menu"; sleep 2; cltap "Chat Folders"; sleep 3; shot CL-12-folders; clhas "Work"
cltap "Folder name"; sleep 1; adb shell input text "Family%sand%sfriends"; key 4; clst "Python"; shot CL-12b-folder-new; cltop; cltap "Create"; sleep 2; clfx '"name":"Family and friends"'
cltap "Delete folder Work"; sleep 1; shot CL-12c-folder-delete; cltap "Delete"; sleep 2; clfx "DELETE /api/chat/folders?id=cl-f1"; key 4; sleep 1
cltap "Menu"; sleep 2; clst "Chat settings" 100; sleep 2; shot CL-13-chat-settings; cltap "Large"; sleep 1; shot CL-13b-font-large; cltap "Read receipts"; sleep 1; cltap "Medium"; key 4; sleep 1

# ── Video 2 (30 s): long-press select → mute choices → group info → edit switches ──
adb shell rm -f /sdcard/CL-manage.mp4; (timeout 40 adb shell screenrecord --size 480x640 --bit-rate 1000000 --time-limit 30 /sdcard/CL-manage.mp4 >/dev/null 2>&1 &)
view "https://yidplus.com/chat"; sleep 3; cllong "Yossi Klein"; sleep 1; cltap "Mute"; sleep 2; cltap "Cancel"; key 4; sleep 1
clopen r-g1 group "\u05d7\u05d1\u05e8\u05d9\u05dd \u05e4\u05d5\u05df \u05e9\u05d5\u05dc" "" 3; cltap "More"; cltap "Group info"; sleep 2; adb shell input swipe 240 420 240 170 600; sleep 1.5
cltap "Edit"; sleep 2; adb shell input swipe 240 420 240 170 600; sleep 1.5; key 4; sleep 1; key 4; sleep 1; key 4
sleep 5; timeout 20 adb pull /sdcard/CL-manage.mp4 "$OUT/CL-manage.mp4" >/dev/null 2>&1; log "CL video 2: $(ls -la "$OUT/CL-manage.mp4" 2>/dev/null | awk '{print $5}') bytes"

# ── Group info (r-g1, I'm its owner): photo, mute, disappearing, invite, Media/Files/Links/Voice, members with titles ──
clopen r-g1 group "\u05d7\u05d1\u05e8\u05d9\u05dd \u05e4\u05d5\u05df \u05e9\u05d5\u05dc" "" 6
cltap "More"; cltap "Group info"; sleep 3; shot CL-14-group-info; cltexts info
clst "Mute notifications"; sleep 1; shot CL-14b-mute; cltap "1 week"; cltap "OK"; sleep 2; clfx '"room_id":"r-g1","muted":true'
clst "Disappearing messages"; sleep 1; shot CL-18-disappearing; cltap "1 day"; sleep 2; clfx '"auto_delete_minutes":1440'
clst "Invite link"; sleep 2; shot CL-19-invite; cltap "Revoke link"; sleep 1.5; shot CL-19b-revoke-confirm; cltexts revoke; cltap "Revoke"; sleep 2; clfx '"revoke_invite":true'; cltap "Close"
clst "Files"; shot CL-15-info-media-tab; cltap "Files"; sleep 1; shot CL-15b-files; cltap "Links"; sleep 1; shot CL-15c-links; cltap "Voice"; sleep 1; shot CL-15d-voice; cltap "Media"
clst "Yanky Weiss"; sleep 1; shot CL-17-member-sheet; cltap "Set title"; sleep 1; adb shell input text "Gabbai"; key 4; sleep 1; shot CL-17a-title; cltap "Save"; sleep 2; clfx '"member_title":"Gabbai"'
clst "Shaya"; sleep 1; cltap "Remove from group"; sleep 1; shot CL-17b-remove-confirm; cltap "Remove"; sleep 2; clfx '"remove":true'
shot CL-16-members; if [ -z "$(clseek "Report group" "")" ]; then log "CL NOTE (not a pass): no 'Report group' for the owner after scrolling the whole page; the intended owner permission is not established from requirements"; else log "CL NOTE (not a pass): 'Report group' is shown to the owner of the group; intended owner permission not established"; fi; log "CL MISSING: 'Report group' as a NON-owner is not exercised in this fixture; owner intent unestablished"; cltop
clhas "Group info"   # still on the info page: its top bar has the Edit (pencil) button

# ── Edit group: every switch saves at once (chat.js _geToggle), Send Media sub-screen, Administrators ──
cltap "Edit"; sleep 3; shot CL-20-edit-top; cltexts edit
clst "Approve new members"; sleep 2; clfx '"approve_members":false'; shot CL-20b-edit-mid
clst "Send Messages"; sleep 2; clfx '"read_only":true'; shot CL-20c-edit-flipped
clst "Send Media"; sleep 2; shot CL-21-send-media; clst "Polls"; sleep 2; clfx '"polls":true'; key 4; sleep 1
clst "Add Users"; sleep 2; clfx '"add_users":true'
clst "Administrators"; sleep 2; shot CL-22-admins; key 4; sleep 1
clst "Members"; sleep 2; shot CL-22b-members; key 4; sleep 1; shot CL-20d-edit-bottom; key 4; sleep 1; key 4; sleep 1

# ── Report a private chat (POST /api/reports) ──
clopen r-dm1 private "Yossi Klein" ",\"other_user_id\":\"u-yossi\"" 5
cltap "More"; cltap "Contact info"; sleep 3; shot CL-23-contact-info; log "CL note: fixture me is the site owner, so online is shown (site rule: admins only)"
clst "Report Yossi Klein"; sleep 1; shot CL-24-report; cltap "Harassment"; cltap "Submit Report"; sleep 2; clfx "/api/reports"; key 4; sleep 1; key 4; sleep 1

# ── New group with photo + Everyone can write (chat.js createGroup) ──
view "https://yidplus.com/chat"; sleep 3; cltap "Start new chat"; sleep 1; cltap "New group"; sleep 2; shot CL-25-new-group; log "CL new group: Everyone can write left at its default (on)"
cltap "Group name"; adb shell input text "Q"; key 4; clst "Create group"; sleep 1; log "CL (1-letter name must not send)"; cltop
cltap "Group name"; adb shell input text "QA%sGroup"; key 4; shot CL-25b-new-group-filled; clst "Create group"; sleep 3; clfx '"name":"QA Group"'; clfx '"type":"group"'; key 4; sleep 1

# ── Join by invite link: public joins, private sends a request (GET + POST /api/invite) ──
view "https://yidplus.com/chat"; sleep 3; cltap "Start new chat"; sleep 1; cltap "Join by invite link"; sleep 2
log "CL animation scales before: $(for k in window_animation_scale transition_animation_scale animator_duration_scale; do printf '%s=%s ' $k "$(adb shell settings get global $k | tr -d '\r')"; done)"; if [ "${CL_ANIM:-off}" = on ]; then for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k 1.0; done; sleep 2; fi; log "CL animation scales after: $(for k in window_animation_scale transition_animation_scale animator_duration_scale; do printf '%s=%s ' $k "$(adb shell settings get global $k | tr -d '\r')"; done) mode ${CL_ANIM:-off}"; for t in InputMethodManager InsetsController ImeInsetsSourceConsumer ImeTracker InputMethodManagerService; do adb shell setprop log.tag.$t VERBOSE; done; log "CL wm logging: $(timeout 20 adb shell wm logging enable-text WM_DEBUG_IME 2>&1 | head -3 | tr '\n' '|' | cut -c1-200)"; dmark "before typing the link"; adb shell input text "https://yidplus.com/chat?join=clpriv"; dmark "typed the link"; sleep ${CL_JOIN_WAIT:-2}; log "CL probe join wait ${CL_JOIN_WAIT:-2}s mode ${CL_JOIN_MODE:-ctrl}"; if [ "${CL_JOIN_MODE:-ctrl}" = tapfield ]; then dmark "tap on the link field"; adb shell input tap 240 170; sleep 1; fi; clinq before-back; climedump join-before-back; timeout 20 adb shell dumpsys input_method 2>/dev/null | grep -E "mCurId|mCurClient|mCurFocusedWindow |mInputShown|mShowRequested|mCurAttribute|mCurToken|mSoftInputMode|mServedView" | head -12 > "$OUT/ime-before-back.txt"; shot CL-diag-j0-before-back; dmark "sending Back (key 4)"; key 4; dmark "Back sent"; shot CL-diag-j1-just-after-back; sleep 0.3; timeout 20 adb shell dumpsys input_method 2>/dev/null | grep -E "mCurId|mCurClient|mCurFocusedWindow |mInputShown|mShowRequested|mCurAttribute|mCurToken|mSoftInputMode|mServedView" | head -12 > "$OUT/ime-after-back.txt"; sleep 3; climedump join-after-back; dmark "before probe"; clprobe; dmark "after probe"; timeout 30 adb logcat -d -s YidDiag:I 2>/dev/null | tail -150 > "$OUT/yiddiag-join.txt"; log "CL yiddiag lines: $(wc -l < "$OUT/yiddiag-join.txt")"; shot CL-diag-join-after-back; log "CL ime-diag join-logcat: $(timeout 20 adb logcat -d -t 600 -s InputDispatcher:V InputMethodService:V LatinIME:V InputMethodManagerService:V ViewRootImpl:V InputMethodManager:V ImeInsetsSourceProvider:V 2>/dev/null | tail -14 | cut -c1-200 | tr "\n" "|")";  shot CL-26-invite-private; clhas "Request to join"; clst "Request to join"; sleep 2; clfx '"code":"clpriv"'; shot CL-26b-request-sent; key 4; sleep 1

# ── Search, then a non-green theme and dark mode (the bars follow the colour theme: DS P6) ──
view "https://yidplus.com/chat"; sleep 3; cltap "Search chats"; sleep 2; shot CL-31-search-open; chkb_cl() { log "CL keyboard after search opens: $(timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -o 'mInputShown=[a-z]*' | head -1)"; }; chkb_cl
adb shell input text "Yo"; sleep 1; shot CL-31b-search-typing; adb shell input text "ssi"; sleep 3; key 4; shot CL-31c-search-results; cltexts search-yossi
# each query in a fresh search (close it, open it again, type): no reliance on "Clear" and keyboard Back timing
clsearch() { local i; for i in 1 2 3; do clpos "Close search" | grep -q . && { cltap "Close search"; sleep 1; } ; done; cltop; cltap "Search chats"; sleep 1.5; [ -n "${1:-}" ] && { adb shell input text "$1"; sleep 3; }; sleep 1; clhidekb; }
# hide the on-screen keyboard so the results area is visible; Back first (the system gives it to the keyboard), then Escape; verify with the input-method state and say so when it is still up (CL-27b/CL-31h of run 43498008671 showed the keyboard still covering the results after one Back)
clkbshown() { timeout 10 adb shell dumpsys input_method 2>/dev/null | grep -o 'mInputShown=[a-z]*' | head -1; }
clhidekb() { local n; climedump before-hide; for n in 1 2 3; do case "$(clkbshown)" in *true*) ;; *) log "CL keyboard hidden after $((n-1)) Back/Escape press(es) ($(clkbshown))"; return 0 ;; esac; if [ $n = 2 ]; then key 111; else key 4; fi; sleep 1.5; climedump after-press-$n; case "$(clkbshown)" in *true*) log "CL ime-diag sample-after-press-$n: $(for k in 1 2 3 4 5 6; do clkbshown; sleep 0.5; done | tr "\n" " ")"; log "CL ime-diag logcat-after-press-$n: $(timeout 20 adb logcat -d -t 600 -s InputDispatcher:V InputMethodService:V LatinIME:V InputMethodManagerService:V ViewRootImpl:V InputMethodManager:V ImeInsetsSourceProvider:V 2>/dev/null | tail -14 | cut -c1-200 | tr "\n" "|")"; screenshot_cl=1; shot CL-diag-still-up-$n ;; esac; done; log "CL WRONG: the keyboard is still up after Back and Escape, so the search results area is covered ($(clkbshown))"; }
clsearch YID; shot CL-27-search; cltexts search-yid
clsearch Mendy; shot CL-27b-search-people; clhas "Contacts"; clhas "Mendy Gold"
clsearch ""; shot CL-31d-search-recent; clhas "Recent searches"
cltap "Unread"; sleep 1.5; shot CL-31e-chip-unread; cltap "Unread"; sleep 1; cltap "Photos"; sleep 1.5; shot CL-31f-chip-photos; clchip "Photos"; sleep 1; clchipscroll; clchip "Links"; sleep 1.5; shot CL-31g-chip-links; clchip "Links"; sleep 1; shot CL-31g2-links-toggled-off; clcheck "Links" off
clsearch zzqx; shot CL-31h-no-results; clhas "No results found"
clsearch Mendy; cltap "Mendy Gold"; sleep 2; clfx '"other_user_id":"u-mendy"'; key 4; sleep 1; key 4; sleep 1; key 4; sleep 1
adb shell cmd uimode night yes; sleep 3; view "https://yidplus.com/chat"; sleep 3; shot CL-28-list-dark; cltap "Menu"; sleep 1; shot CL-28b-drawer-dark; key 4; sleep 1
clopen r-g1 group "\u05d7\u05d1\u05e8\u05d9\u05dd \u05e4\u05d5\u05df \u05e9\u05d5\u05dc" "" 5; cltap "More"; cltap "Group info"; sleep 2; shot CL-28c-info-dark; key 4; key 4; adb shell cmd uimode night no; sleep 2
# a non-green colour theme (Ocean / preset 1) and the light bar: the chat bars must follow it
look() { timeout 30 adb shell am start -n $PKG/.MainActivity -a com.yidplus.app.QA_LOOK "$@" >/dev/null 2>&1; sleep 2; }
look --es color 1; view "https://yidplus.com/chat"; sleep 3; shot CL-29-list-theme1; cllong "Yossi Klein"; shot CL-29b-select-theme1; key 4
look --es bar light; sleep 1; shot CL-29c-list-lightbar; cltap "Menu"; sleep 1; shot CL-29d-drawer-lightbar; key 4; look --es bar dark --es color 0; sleep 1
size 1080x2400 420; view "https://yidplus.com/chat"; sleep 4; shot CL-30-list-1080; cltap "Menu"; sleep 1; shot CL-30b-drawer-1080; key 4; size 480x640 240; sleep 2
log "CL all writes: $(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -c YidFx)"

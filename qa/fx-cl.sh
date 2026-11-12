# Helper CL (chat list & chat management, 2.1): signed-in QA with the fixture account at 480x640 (the Qin F21 size).
# Sourced by qa/signedin.sh fx_block (log/shot/key/view/restart/size from qa/native.sh). Every write is answered locally and logged (tag YidFx):
# each "YidFx:" line below is the exact request the app would send, to be checked against the site's chat.js call named beside it.
clpos() { # centre of the tallest node whose text/desc is exactly $1 (else contains it)
  timeout 30 adb shell uiautomator dump /sdcard/cl.xml >/dev/null 2>&1
  local x n b; x=$(timeout 20 adb shell cat /sdcard/cl.xml 2>/dev/null | tr '>' '\n')
  n=$(echo "$x" | grep -E -- "(text|content-desc)=\"$(printf '%s' "$1" | sed 's/[][\.*^$/]/\\&/g')\"" | awk 'match($0,/bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/){s=substr($0,RSTART,RLENGTH); gsub(/[^0-9]+/," ",s); split(s,a," "); h=a[4]-a[2]; if(h>best){best=h; line=$0}} END{print line}')
  [ -z "$n" ] && n=$(echo "$x" | grep -F -- "$1" | awk 'match($0,/bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"/){s=substr($0,RSTART,RLENGTH); gsub(/[^0-9]+/," ",s); split(s,a," "); h=a[4]-a[2]; if(h>best){best=h; line=$0}} END{print line}')
  [ -z "$n" ] && return 0
  b=$(echo "$n" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$b" ] && return 0; set -- $b; echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}
cltap() { local p; p=$(clpos "$1"); if [ -z "$p" ]; then log "CL MISSING tap: '$1' not on screen"; return 0; fi; set -- $p; timeout 10 adb shell input tap $1 $2; sleep 1.5; log "cltap '$1'"; }
cllong() { sleep 1; local p; p=$(clpos "$1"); [ -z "$p" ] && { log "CL MISSING long-press: '$1'"; return 0; }; set -- $p; adb shell input swipe $1 $2 $1 $2 900; sleep 2; log "long-press '$1'"; }
clhas() { local p; p=$(clpos "$1"); if [ -n "$p" ]; then log "CL ok: '$1' on screen"; else log "CL MISSING: '$1' not on screen"; fi; }
clnot() { local p; p=$(clpos "$1"); if [ -n "$p" ]; then log "CL WRONG: '$1' is on screen"; else log "CL ok: '$1' not shown"; fi; }
clfx() { local l; l=$(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -F -- "$1" | tail -1 | cut -c1-400); if [ -n "$l" ]; then echo "  YidFx: $l" >> "$OUT/qa.log"; log "CL ok: sent $1"; else log "CL MISSING: no request with '$1'"; fi; }
clopen() { timeout 40 adb shell "am start -n $PKG/.MainActivity -a com.yidplus.app.OPEN_CHAT --es room '{\"id\":\"$1\",\"type\":\"$2\",\"nick\":\"$3\"$4}'" >/dev/null 2>&1; sleep "${5:-5}"; log "open chat $1"; }
cltexts() { timeout 20 adb shell uiautomator dump /sdcard/clt.xml >/dev/null 2>&1; log "CL texts $1: $(timeout 20 adb shell cat /sdcard/clt.xml 2>/dev/null | grep -oE ' (text|content-desc)="[^"]+"' | head -70 | tr '\n' ' ' | cut -c1-1500)"; }
clup() { adb shell input swipe 240 520 240 160 400; sleep 1; }
cldown() { adb shell input swipe 240 160 240 560 300; sleep 1; }
size 480x640 240

# ── The list: tabs + folder chips, Telegram channels as rows, empty private chats hidden, online only for site admins (fixture me = owner) ──
view "https://yidplus.com/chat"; sleep 8; shot CL-01-list; cltexts list
clhas "Work"; clnot "Empty Chat"; clhas "Menu"
adb shell input swipe 420 112 40 112 400; sleep 1; shot CL-02-chips-end; clhas "Chat folders"
adb shell input swipe 40 112 440 112 300; sleep 1
clup; clup; shot CL-03-list-telegram-rows; clhas "יידישע נייעס"; cldown; cldown; cldown
cltap "Work"; sleep 2; shot CL-04-folder-work; cltexts folder; clhas "DealPoint"; clnot "Yossi Klein"; cltap "Work"; sleep 1
cltap "Unread"; sleep 2; shot CL-05-tab-unread; cltap "All"; sleep 1

# ── Long-press selection: Pin (on this phone), Mute 8 h / 1 week / Always, Mark as read, Delete ──
cllong "Yanky Weiss"; shot CL-06-selected; clhas "Pin"; clhas "Mark as read"
cltap "Pin"; sleep 2; shot CL-07-pinned-top; cltexts pinned
cllong "Yanky Weiss"; cltap "Mute"; sleep 1; shot CL-08-mute-choices; cltap "8 hours"; cltap "OK"; sleep 2; clfx '"muted":true'
cllong "Yossi Klein"; cltap "Mark as read"; sleep 2; clfx "/api/chat/read"
cllong "חיים"; cltap "Delete"; sleep 1; shot CL-09-delete-confirm; cltap "Delete"; sleep 2; clfx "DELETE /api/chat/rooms?room_id=r-dm3"
cllong "Yossi Klein"; cllong "Berel Links"; shot CL-10-two-selected; key 4; sleep 1

# ── ☰ side menu: profile, Saved, Chat Folders, New Group/Channel, invite link, Chat settings, Admin Panel ──
cltap "Menu"; sleep 2; shot CL-11-drawer; cltexts drawer; clhas "Admin Panel"
cltap "Chat Folders"; sleep 3; shot CL-12-folders; clhas "Work"
cltap "Folder name"; sleep 1; adb shell input text "Family%sand%sfriends"; key 111; cltap "Family 👨"; clup; shot CL-12b-folder-new; cltap "Create"; sleep 2; clfx "/api/chat/folders"
cldown; cltap "Delete folder Work"; sleep 1; shot CL-12c-folder-delete; cltap "Delete"; sleep 2; clfx "DELETE /api/chat/folders?id=cl-f1"; key 4; sleep 1
cltap "Menu"; sleep 2; cltap "Chat settings"; sleep 2; shot CL-13-chat-settings; cltap "Large"; sleep 1; shot CL-13b-font-large; cltap "Read receipts"; sleep 1; cltap "Medium"; key 4; sleep 1

# ── Group info (r-g1, I'm its owner): photo, mute, disappearing, invite, Media/Files/Links/Voice, members with titles ──
clopen r-g1 group "\u05d7\u05d1\u05e8\u05d9\u05dd \u05e4\u05d5\u05df \u05e9\u05d5\u05dc" "" 6
cltap "חברים פון שול"; sleep 3; shot CL-14-group-info; cltexts info
clup; shot CL-15-info-media-tab; cltap "Files"; sleep 1; shot CL-15b-files; cltap "Links"; sleep 1; shot CL-15c-links; cltap "Voice"; sleep 1; shot CL-15d-voice; cltap "Media"
clup; clup; shot CL-16-members; cltap "Yanky Weiss"; sleep 1; shot CL-17-member-sheet; cltap "Set title"; sleep 1; adb shell input text "Gabbai"; key 111; cltap "Save"; sleep 2; clfx '"member_title":"Gabbai"'
cltap "Yanky Weiss"; sleep 1; cltap "Remove from group"; sleep 1; shot CL-17b-remove-confirm; cltap "Remove"; sleep 2; clfx '"remove":true'
cldown; cldown; cldown; cldown
cltap "Mute notifications"; sleep 1; cltap "1 week"; cltap "OK"; sleep 2; clfx '"room_id":"r-g1","muted":true'
cltap "Disappearing messages"; sleep 1; shot CL-18-disappearing; cltap "1 day"; sleep 2; clfx '"auto_delete_minutes":1440'
cltap "Invite link"; sleep 2; shot CL-19-invite; cltap "Revoke link"; sleep 1; cltap "Revoke"; sleep 2; clfx '"revoke_invite":true'; cltap "Close"

# ── Edit group: every switch saves at once (chat.js _geToggle), Send Media sub-screen, Administrators ──
cltap "Edit"; sleep 3; shot CL-20-edit-top; cltexts edit
clup; shot CL-20b-edit-mid; cltap "Approve new members"; sleep 2; clfx '"approve_members":false'
cltap "Send Messages"; sleep 2; clfx '"read_only":true'; shot CL-20c-edit-flipped
cltap "Send Media"; sleep 2; shot CL-21-send-media; cltap "Polls"; sleep 2; clfx '"polls":true'; key 4; sleep 1
cltap "Add Users"; sleep 2; clfx '"add_users":true'
clup; clup; shot CL-20d-edit-bottom; cltap "Administrators"; sleep 2; shot CL-22-admins; key 4; sleep 1
cltap "Members"; sleep 2; shot CL-22b-members; key 4; sleep 1; key 4; sleep 1; key 4; sleep 1

# ── Report a private chat (POST /api/reports) ──
clopen r-dm1 private "Yossi Klein" ",\"other_user_id\":\"u-yossi\"" 5
cltap "Yossi Klein"; sleep 3; shot CL-23-contact-info; clnot "online"; clup; clup; clup; cltap "Report Yossi Klein"; sleep 1; shot CL-24-report; cltap "Harassment"; cltap "Submit Report"; sleep 2; clfx "/api/reports"; key 4; sleep 1; key 4; sleep 1

# ── New group with photo + Everyone can write (chat.js createGroup) ──
view "https://yidplus.com/chat"; sleep 3; cltap "Start new chat"; sleep 1; cltap "New group"; sleep 2; key 111; shot CL-25-new-group; cltap "Everyone can write"; sleep 1
cltap "Group name"; adb shell input text "Q"; key 111; cltap "Create group"; sleep 1; log "CL (name too short must not send)"; cltap "Group name"; adb shell input text "A%sGroup"; key 111; cltap "Create group"; sleep 3; clfx '"read_only":true'; key 4; sleep 1

# ── Join by invite link: public joins, private sends a request (GET + POST /api/invite) ──
view "https://yidplus.com/chat"; sleep 3; cltap "Menu"; sleep 1; cltap "Join by invite link"; sleep 2
adb shell input text "https://yidplus.com/chat?join=clpriv"; key 111; sleep 3; shot CL-26-invite-private; clhas "Request to join"; cltap "Request to join"; sleep 2; clfx '"code":"clpriv"'; shot CL-26b-request-sent; key 4; sleep 1

# ── Search, then dark ──
cltap "Search chats"; sleep 1; adb shell input text "YID"; sleep 3; key 111; shot CL-27-search; key 4; sleep 1
adb shell cmd uimode night yes; sleep 3; view "https://yidplus.com/chat"; sleep 3; shot CL-28-list-dark; cltap "Menu"; sleep 1; shot CL-28b-drawer-dark; key 4; adb shell cmd uimode night no; sleep 2
log "CL all writes: $(timeout 20 adb logcat -d -s YidFx 2>/dev/null | grep -c YidFx)"
